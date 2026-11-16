package hu.balazsfalvi.pqclab.integration.app.migration;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Owns one uniquely named local Compose project; its cleanup cannot select an interactive deployment. */
final class ComposeProject implements AutoCloseable {
    private final String project;
    private final Commands commands;
    private final List<String> prefix;
    private final Map<String, String> environment;
    private final Path policyOverride;
    private boolean closed;

    /** Uses a local-only Docker context and private diagnostic logs outside the public evidence bundle. */
    ComposeProject() throws IOException, InterruptedException {
        this(null);
    }

    /** Supplies a command boundary for ownership, failed-startup and cleanup tests without requiring Docker. */
    ComposeProject(Commands supplied) throws IOException, InterruptedException {
        this(supplied, Purpose.LIFECYCLE);
    }

    /** Chooses an isolated lifecycle trial or a four-phase policy rehearsal before allocating any project resources. */
    ComposeProject(Commands supplied, Purpose purpose) throws IOException, InterruptedException {
        java.util.Objects.requireNonNull(purpose);
        project = (purpose == Purpose.LIFECYCLE ? "pqc06a-" : "pqc06b-")
                + UUID.randomUUID().toString().replace("-", "");
        Path repository = repository();
        policyOverride = purpose == Purpose.POLICY_REHEARSAL
                ? repository.resolve(".local/compose-demo").resolve(project).resolve("policy.yaml")
                : null;
        commands = supplied == null
                ? new NativeCommands(repository.resolve(".local/compose-demo").resolve(project))
                : supplied;
        String override = System.getenv("DOCKER_HOST");
        if (override != null && !(override.startsWith("npipe://") || override.startsWith("unix://"))) {
            throw new IOException("The migration demonstration requires a local Docker engine.");
        }
        String context = commands.run(List.of("docker", "context", "show"), Map.of(), Duration.ofSeconds(15))
                .trim();
        if (!context.matches("[a-zA-Z0-9_.-]+")) throw new IOException("Invalid local Docker context.");
        String endpoint = commands.run(
                        List.of("docker", "context", "inspect", context, "--format", "{{.Endpoints.docker.Host}}"),
                        Map.of(),
                        Duration.ofSeconds(15))
                .trim();
        if (!(endpoint.startsWith("npipe://") || endpoint.startsWith("unix://"))) {
            throw new IOException("Remote Docker contexts are outside this local demonstration.");
        }
        var arguments = new ArrayList<>(List.of(
                "docker",
                "--context",
                context,
                "compose",
                "--project-name",
                project,
                "--project-directory",
                repository.toString(),
                "--file",
                repository.resolve("compose.yaml").toString(),
                "--env-file",
                emptyEnvironment(repository)));
        if (policyOverride != null) {
            arguments.addAll(List.of("--file", policyOverride.toString()));
            writePolicy(MigrationPolicy.CLASSICAL_ONLY);
        }
        prefix = List.copyOf(arguments);
        int publisherPort = freePort();
        int receiverPort;
        do {
            receiverPort = freePort();
        } while (receiverPort == publisherPort);
        environment = Map.of(
                "PQC_LAB_PUBLISHER_PORT",
                Integer.toString(publisherPort),
                "PQC_LAB_RECEIVER_PORT",
                Integer.toString(receiverPort),
                "COMPOSE_PROFILES",
                "");
    }

    /** Builds the current checked-out source into the local reference image before any event is signed. */
    void build() throws IOException, InterruptedException {
        call(Duration.ofMinutes(15), "build");
    }

    /** Recreates actual service containers while retaining only this project's independent named volumes. */
    PolicyMigrationRuntime start() throws IOException, InterruptedException {
        if (closed) throw new IllegalStateException("The owned Compose project is closed.");
        try {
            call(
                    Duration.ofMinutes(2),
                    "up",
                    "--detach",
                    "--no-build",
                    "--force-recreate",
                    "--wait",
                    "--wait-timeout",
                    "90");
            return new Containers(
                    origin("publisher", "8781"),
                    origin("receiver", "8782"),
                    policyOverride == null ? MigrationPolicy.BOTH_REQUIRED : MigrationPolicy.CLASSICAL_ONLY);
        } catch (IOException | InterruptedException | RuntimeException failure) {
            close();
            throw failure;
        }
    }

    /** Writes only a fixed receiver command in an owned overlay; interactive Compose keeps its BOTH_REQUIRED default. */
    private void writePolicy(MigrationPolicy policy) throws IOException {
        Files.createDirectories(policyOverride.getParent());
        Files.writeString(policyOverride, """
                services:
                  receiver:
                    command:
                      - --role=receiver
                      - --network=compose
                      - --data-dir=/data/receiver
                      - --trust-dir=/data/trust
                      - --policy=%s
                """.formatted(policy.name()));
    }

    /** Combines only trusted fixed subcommands with this instance's immutable owned-project prefix. */
    private String call(Duration deadline, String... arguments) throws IOException, InterruptedException {
        var command = new ArrayList<>(prefix);
        command.addAll(List.of(arguments));
        return commands.run(command, environment, deadline);
    }

    /** Reads the actual publication on an owned container, refusing wildcard or remote addresses. */
    private URI origin(String role, String internalPort) throws IOException, InterruptedException {
        String id = call(Duration.ofSeconds(15), "ps", "--quiet", "--status", "running", role)
                .trim();
        if (!id.matches("[0-9a-f]{12,64}")) throw new IOException("The owned service is not running.");
        // JSON avoids platform-dependent nested quote handling in Windows native commands.
        var inspect = new ArrayList<>(prefix.subList(0, 3));
        inspect.addAll(List.of("inspect", "--format", "{{json .NetworkSettings.Ports}}", id));
        String encoded =
                commands.run(inspect, environment, Duration.ofSeconds(15)).trim();
        var json = new hu.balazsfalvi.pqclab.integration.app.wire.StrictJson(4096);
        var ports = json.read(
                encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.util.Set.of(internalPort + "/tcp"));
        var bindings = ports.get(internalPort + "/tcp");
        if (bindings == null
                || !bindings.isArray()
                || bindings.size() != 1
                || !bindings.get(0).isObject()) {
            throw new IOException("Expected one owned service publication.");
        }
        var binding = json.read(
                json.write((com.fasterxml.jackson.databind.node.ObjectNode) bindings.get(0)),
                java.util.Set.of("HostIp", "HostPort"));
        String published = json.text(binding, "HostIp") + ":" + json.text(binding, "HostPort");
        if (!published.matches("127\\.0\\.0\\.1:[0-9]+"))
            throw new IOException("Expected a loopback published service port.");
        return MigrationTransport.loopback(URI.create("http://" + published));
    }

    /** Removes only this instance's containers, network and volumes, including after incomplete execution. */
    @Override
    public void close() throws IOException {
        if (closed) return;
        boolean interrupted = Thread.interrupted();
        try {
            try {
                call(Duration.ofSeconds(60), "down", "--volumes", "--remove-orphans", "--timeout", "10");
                closed = true;
            } catch (InterruptedException cancelled) {
                interrupted = true;
                throw new IOException("Owned Compose cleanup was interrupted.");
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /** Finds this source checkout from the documented root or its Gradle module working directory. */
    private static Path repository() throws IOException {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        for (int level = 0; level < 3 && candidate != null; level++, candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("compose.yaml"))
                    && Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) return candidate;
        }
        throw new IOException("Run the Compose demonstration from the laboratory source checkout.");
    }

    /** Excludes a checkout's optional interactive .env from the isolated demonstration. */
    private static String emptyEnvironment(Path repository) throws IOException {
        Path file = repository.resolve(".local/compose-demo/empty.env");
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) Files.writeString(file, "# Owned demo uses explicit local ports only.\n");
        return file.toString();
    }

    /** Selects an unused loopback port; Docker must still bind it successfully before endpoints are accepted. */
    private static int freePort() throws IOException {
        try (var socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    /** Represents two actual service containers, not a simulated browser-side integration. */
    private final class Containers implements PolicyMigrationRuntime {
        private final URI publisher;
        private final URI receiver;
        private MigrationPolicy policy;

        /** Retains the actual loopback mappings while independently recreated services reuse explicit host ports. */
        private Containers(URI publisher, URI receiver, MigrationPolicy policy) {
            this.publisher = publisher;
            this.receiver = receiver;
            this.policy = policy;
        }

        /** {@inheritDoc} */
        @Override
        public URI publisherUri() {
            return publisher;
        }

        /** {@inheritDoc} */
        @Override
        public URI receiverUri() {
            return receiver;
        }

        /** {@inheritDoc} */
        @Override
        public String publisherIdentity() {
            return identity("publisher");
        }

        /** {@inheritDoc} */
        @Override
        public String receiverIdentity() {
            return identity("receiver");
        }

        /** Reads the identity of exactly one running service in the owned project. */
        private String identity(String role) {
            try {
                String id = call(Duration.ofSeconds(15), "ps", "--quiet", "--status", "running", role)
                        .trim();
                if (!id.matches("[0-9a-f]{12,64}")) throw new IOException("The owned service is not running.");
                return "container-" + id;
            } catch (IOException unavailable) {
                throw new IllegalStateException("The owned service identity is unavailable.");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("The owned service observation was interrupted.");
            }
        }

        /** {@inheritDoc} */
        @Override
        public void stopPublisher() throws IOException, InterruptedException {
            stop("publisher");
        }

        /** {@inheritDoc} */
        @Override
        public void stopReceiver() throws IOException, InterruptedException {
            call(Duration.ofSeconds(30), "kill", "--signal", "SIGKILL", "receiver");
        }

        /** Stops only one known role; no other Compose project can be selected. */
        private void stop(String role) throws IOException, InterruptedException {
            call(Duration.ofSeconds(30), "stop", "--timeout", "10", role);
        }

        /** {@inheritDoc} */
        @Override
        public void startPublisher() throws IOException, InterruptedException {
            startRole("publisher");
        }

        /** {@inheritDoc} */
        @Override
        public void startReceiver() throws IOException, InterruptedException {
            startRole("receiver");
        }

        /** Changes only an owned rehearsal receiver through its fixed, enum-derived startup overlay. */
        @Override
        public void advancePolicy(MigrationPolicy next) throws IOException, InterruptedException {
            if (policyOverride == null) throw new IllegalStateException("Only a policy rehearsal can advance phases.");
            PolicyProgression.requireNext(policy, next);
            stop("receiver");
            writePolicy(next);
            startRole("receiver");
            policy = next;
        }

        /** Recreates only the selected role, preserving its independent persistent volumes and host port. */
        private void startRole(String role) throws IOException, InterruptedException {
            call(
                    Duration.ofMinutes(2),
                    "up",
                    "--detach",
                    "--no-deps",
                    "--no-build",
                    "--force-recreate",
                    "--wait",
                    "--wait-timeout",
                    "90",
                    role);
        }

        /** Removes phase containers but retains the owned named volumes for the next disk-only phase. */
        @Override
        public void close() throws IOException {
            try {
                call(Duration.ofSeconds(60), "down", "--remove-orphans", "--timeout", "10");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Owned phase cleanup was interrupted.");
            }
        }
    }

    /** Keeps the existing fixed-policy lifecycle story separate from the explicitly requested policy rehearsal. */
    enum Purpose {
        /** Uses the unchanged BOTH_REQUIRED Compose deployment. */
        LIFECYCLE,
        /** Adds an owned local overlay for the four reviewed receiver startup configurations. */
        POLICY_REHEARSAL
    }

    /** Provides one explicit subprocess boundary with no shell expansion. */
    @FunctionalInterface
    interface Commands {
        /** Runs a bounded owned command and keeps raw diagnostics private. */
        String run(List<String> command, Map<String, String> environment, Duration deadline)
                throws IOException, InterruptedException;
    }

    /** Captures local Docker diagnostics separately from reviewed public HTTP evidence. */
    static final class NativeCommands implements Commands {
        private final Path directory;
        private int sequence;

        /** Creates only the dedicated local diagnostic directory. */
        NativeCommands(Path directory) throws IOException {
            this.directory = directory;
            Files.createDirectories(directory);
        }

        /** {@inheritDoc} */
        @Override
        public String run(List<String> command, Map<String, String> environment, Duration deadline)
                throws IOException, InterruptedException {
            Path log = directory.resolve("%03d.log".formatted(++sequence));
            var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
            builder.environment().putAll(environment);
            if (command.contains("--context")) builder.environment().remove("DOCKER_HOST");
            for (String variable : List.of("COMPOSE_FILE", "COMPOSE_ENV_FILES", "COMPOSE_PROJECT_NAME")) {
                builder.environment().remove(variable);
            }
            Process process = builder.start();
            try {
                if (!process.waitFor(deadline.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IOException("The owned Docker command exceeded its deadline.");
                }
                if (process.exitValue() != 0)
                    throw new IOException("The owned Docker command failed; inspect local diagnostics.");
                try (var input = Files.newInputStream(log)) {
                    return new String(input.readNBytes(262_144), java.nio.charset.StandardCharsets.UTF_8);
                }
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    if (!process.waitFor(10, TimeUnit.SECONDS))
                        throw new IOException("An owned Docker client could not be stopped.");
                }
            }
        }
    }
}
