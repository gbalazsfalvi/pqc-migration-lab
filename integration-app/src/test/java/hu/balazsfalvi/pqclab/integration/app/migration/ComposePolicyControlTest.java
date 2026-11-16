package hu.balazsfalvi.pqclab.integration.app.migration;

import static hu.balazsfalvi.pqclab.integration.core.MigrationPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests only Docker control ownership and receiver configuration; the actual container story remains a separate command. */
final class ComposePolicyControlTest {
    /** Advancing phases must recreate only the owned receiver, preserve the publisher and write fixed enum-only settings. */
    @Test
    void advancesOnlyTheOwnedReceiverThroughAllReviewedPolicies() throws Exception {
        var commands = new RecordingCommands();
        try (var project = new ComposeProject(commands, ComposeProject.Purpose.POLICY_REHEARSAL)) {
            project.build();
            try (var runtime = project.start()) {
                String publisher = runtime.publisherIdentity();
                String receiver = runtime.receiverIdentity();
                int before = commands.calls.size();
                assertThrows(IllegalArgumentException.class, () -> runtime.advancePolicy(BOTH_REQUIRED));
                assertEquals(before, commands.calls.size());
                for (var policy : List.of(SHADOW_PQC, BOTH_REQUIRED, PQC_REQUIRED)) {
                    runtime.advancePolicy(policy);
                    assertEquals(publisher, runtime.publisherIdentity());
                    assertNotEquals(receiver, runtime.receiverIdentity());
                    receiver = runtime.receiverIdentity();
                }
            }
        }
        assertEquals(1, commands.publisherStarts);
        assertEquals(4, commands.receiverStarts);
        assertEquals(List.of("CLASSICAL_ONLY", "SHADOW_PQC", "BOTH_REQUIRED", "PQC_REQUIRED"), commands.deployments);
        var compose = commands.calls.stream()
                .filter(command -> command.contains("compose"))
                .toList();
        String owner = compose.getFirst().get(compose.getFirst().indexOf("--project-name") + 1);
        assertTrue(owner.startsWith("pqc06b-"));
        assertTrue(
                compose.stream().allMatch(command -> owner.equals(command.get(command.indexOf("--project-name") + 1))));
        assertTrue(compose.stream().anyMatch(command -> command.contains("down") && command.contains("--volumes")));
        assertFalse(compose.stream().anyMatch(command -> command.contains("prune")));
    }

    /** The original lifecycle project cannot accidentally turn into a weaker-policy rehearsal. */
    @Test
    void preservesTheFixedPolicyLifecycleContract() throws Exception {
        var commands = new RecordingCommands();
        try (var project = new ComposeProject(commands);
                var runtime = project.start()) {
            int before = commands.calls.size();
            assertThrows(IllegalStateException.class, () -> runtime.advancePolicy(PQC_REQUIRED));
            assertEquals(before, commands.calls.size());
            assertEquals(List.of("BOTH_REQUIRED"), commands.deployments);
        }
    }

    /** Records orchestration commands and fixed startup overlays, never a synthetic HTTP or cryptographic result. */
    private static final class RecordingCommands implements ComposeProject.Commands {
        private final List<List<String>> calls = new ArrayList<>();
        private final List<String> deployments = new ArrayList<>();
        private int publisherStarts;
        private int receiverStarts;

        /** Returns controlled local-engine metadata while preserving each command's actual ownership and role selection. */
        @Override
        public String run(List<String> command, Map<String, String> environment, Duration deadline) throws IOException {
            calls.add(List.copyOf(command));
            assertTrue(deadline.isPositive());
            if (command.contains("context") && command.contains("show")) return "local-test";
            if (command.contains("context") && command.contains("inspect")) return "unix:///var/run/docker.sock";
            if (command.contains("inspect")) {
                boolean publisher = command.getLast().equals("a".repeat(64));
                return "{\"%s/tcp\":[{\"HostIp\":\"127.0.0.1\",\"HostPort\":\"%s\"}]}"
                        .formatted(
                                publisher ? "8781" : "8782",
                                environment.get(publisher ? "PQC_LAB_PUBLISHER_PORT" : "PQC_LAB_RECEIVER_PORT"));
            }
            if (command.contains("up")) {
                boolean receiverOnly = command.getLast().equals("receiver");
                if (!receiverOnly) publisherStarts++;
                receiverStarts++;
                String overlay = Files.readString(Path.of(command.get(command.lastIndexOf("--file") + 1)));
                var policy =
                        java.util.regex.Pattern.compile("--policy=([A-Z_]+)").matcher(overlay);
                assertTrue(policy.find());
                deployments.add(policy.group(1));
                if (receiverOnly) assertTrue(command.contains("--no-deps"));
            }
            if (command.contains("ps")) {
                return command.getLast().equals("publisher") ? "a".repeat(64) : "%064x".formatted(receiverStarts);
            }
            return "";
        }
    }
}
