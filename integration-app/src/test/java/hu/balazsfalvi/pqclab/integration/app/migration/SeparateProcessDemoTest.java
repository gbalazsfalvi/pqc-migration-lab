package hu.balazsfalvi.pqclab.integration.app.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies owned-project command boundaries and real native recovery without requiring Docker in unit CI. */
final class SeparateProcessDemoTest {
    @TempDir
    Path root;

    /** The full real-JVM story catches loss of already acknowledged receipts across immediate receiver termination. */
    @Test
    void provesRealIndependentProcessRecoveryAndPublicEvidence() throws Exception {
        var result = ProcessMigrationDemo.execute(root.resolve("evidence"));
        assertTrue(
                result.report().successful(),
                () -> result.report().failureCode() + " " + result.report().checks());
        assertEquals("06A", result.report().milestone());
        assertTrue(
                result.report().checks().stream().anyMatch(check -> check.id().equals("outage.safe-response")));
        assertTrue(
                result.report().checks().stream().anyMatch(check -> check.id().equals("publisher-offline.receipt")));
        assertTrue(result.report().exchanges().stream()
                .anyMatch(exchange ->
                        exchange.scenario().equals("receiver-offline-gateway") && exchange.actualStatus() == 503));
        assertTrue(Files.isRegularFile(result.directory().resolve("SHA256SUMS")));
        String evidence = Files.readString(result.directory().resolve("report.json"));
        assertFalse(evidence.contains("account.key"));
        assertFalse(evidence.contains("jvm-"));
        assertFalse(evidence.contains(root.toString().replace("\\", "\\\\")));
        assertDoesNotThrow(() -> ProcessMigrationDemo.main(new String[] {"--help"}));
        assertThrows(IllegalArgumentException.class, () -> ProcessMigrationDemo.main(new String[] {"--remote"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProcessMigrationDemo.main(new String[] {"--compose", "--compose"}));
    }

    /** The isolated Docker orchestration preserves phase volumes, recreates only known roles and cleans only its own project. */
    @Test
    void ownsAllComposeOperationsAndSeparatesPhaseCleanupFromFinalVolumeCleanup() throws Exception {
        var commands = new RecordingCommands();
        try (var project = new ComposeProject(commands)) {
            project.build();
            try (var runtime = project.start()) {
                assertNotEquals(runtime.publisherUri(), runtime.receiverUri());
                assertNotEquals(runtime.publisherIdentity(), runtime.receiverIdentity());
                runtime.stopReceiver();
                runtime.startReceiver();
                runtime.stopPublisher();
                runtime.startPublisher();
            }
            assertTrue(commands.calls.stream().anyMatch(call -> call.contains("down") && !call.contains("--volumes")));
        }
        assertTrue(commands.calls.stream().anyMatch(call -> call.contains("kill") && call.contains("SIGKILL")));
        assertTrue(commands.calls.stream().anyMatch(call -> call.contains("down") && call.contains("--volumes")));
        List<List<String>> compose =
                commands.calls.stream().filter(call -> call.contains("compose")).toList();
        assertFalse(compose.isEmpty());
        String owner = compose.getFirst().get(compose.getFirst().indexOf("--project-name") + 1);
        assertTrue(owner.startsWith("pqc06a-"));
        assertTrue(compose.stream().allMatch(call -> owner.equals(call.get(call.indexOf("--project-name") + 1))));
        assertTrue(compose.stream().allMatch(call -> call.contains("--context") && call.contains("--file")));
        assertFalse(compose.stream().anyMatch(call -> call.contains("prune")));
    }

    /** Failed startup still removes the owned project's resources; a remote Docker engine is never accepted. */
    @Test
    void failsClosedOnStartupAndRemoteContext() throws Exception {
        var startup = new RecordingCommands();
        startup.failStartup = true;
        var project = new ComposeProject(startup);
        assertThrows(IOException.class, project::start);
        assertTrue(startup.calls.stream().anyMatch(call -> call.contains("down") && call.contains("--volumes")));
        assertThrows(IllegalStateException.class, project::start);
        project.close();
        var remote = new RecordingCommands();
        remote.remote = true;
        assertThrows(IOException.class, () -> new ComposeProject(remote));
    }

    /** Actual subprocess execution bounds output, nonzero status and runaway child lifetime without a shell. */
    @Test
    void boundsNativeCommandFailuresAndTimeouts() throws Exception {
        var commands = new ComposeProject.NativeCommands(root.resolve("private-diagnostics"));
        String java = Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                .toString();
        assertTrue(commands.run(List.of(java, "-version"), Map.of(), Duration.ofSeconds(10))
                .contains("version"));
        assertThrows(
                IOException.class,
                () -> commands.run(List.of(java, "--not-a-jvm-argument"), Map.of(), Duration.ofSeconds(10)));
        String classes = Path.of(CommandFixture.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .toString();
        assertThrows(
                IOException.class,
                () -> commands.run(
                        List.of(java, "-cp", classes, CommandFixture.class.getName()),
                        Map.of(),
                        Duration.ofMillis(200)));
    }

    /** Records only Docker control contracts; it never substitutes a cryptographic or HTTP verdict. */
    private static final class RecordingCommands implements ComposeProject.Commands {
        private final List<List<String>> calls = new ArrayList<>();
        private boolean failStartup;
        private boolean remote;

        /** Returns controlled engine metadata and owned-role mappings for orchestration-only tests. */
        @Override
        public String run(List<String> command, Map<String, String> environment, Duration deadline) throws IOException {
            calls.add(List.copyOf(command));
            assertFalse(deadline.isNegative());
            if (command.contains("inspect") && command.contains("context")) {
                return remote ? "ssh://remote-engine" : "unix:///var/run/docker.sock";
            }
            if (command.contains("context") && command.contains("show")) return "local-test";
            if (command.contains("up") && failStartup) throw new IOException("Synthetic owned startup failure.");
            if (command.contains("inspect")) {
                boolean publisher = command.getLast().equals("a".repeat(64));
                String internal = publisher ? "8781" : "8782";
                String port = environment.get(publisher ? "PQC_LAB_PUBLISHER_PORT" : "PQC_LAB_RECEIVER_PORT");
                return """
                        {"%s/tcp":[{"HostIp":"127.0.0.1","HostPort":"%s"}]}
                        """.formatted(internal, port);
            }
            if (command.contains("ps")) return command.contains("publisher") ? "a".repeat(64) : "b".repeat(64);
            return "";
        }
    }

    /** Waits without dependencies so timeout cleanup is verified against a real child process. */
    public static final class CommandFixture {
        /** Prevents construction of a subprocess fixture. */
        private CommandFixture() {}

        /** Remains alive until the owned command deadline terminates this child. */
        public static void main(String[] arguments) throws InterruptedException {
            Thread.sleep(60_000);
        }
    }
}
