package hu.balazsfalvi.pqclab.integration.app.migration;

import hu.balazsfalvi.pqclab.integration.app.runtime.ServiceApplication;
import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Owns two genuinely separate Java processes with independent state and a public-only trust directory. */
final class NativeProcessRuntime implements PolicyMigrationRuntime {
    private final MigrationWorkspace workspace;
    private Process publisher;
    private Process receiver;
    private URI publisherUri;
    private URI receiverUri;
    private MigrationPolicy policy;

    /** Retains only the owned temporary namespace, subprocess handles and public endpoint addresses. */
    private NativeProcessRuntime(MigrationWorkspace workspace, MigrationPolicy policy) {
        this.workspace = workspace;
        this.policy = java.util.Objects.requireNonNull(policy);
    }

    /** Initializes once from an authenticated vault, then starts receiver and publisher in different JVMs. */
    static NativeProcessRuntime start(MigrationWorkspace workspace) throws IOException, InterruptedException {
        return start(workspace, MigrationPolicy.BOTH_REQUIRED);
    }

    /** Selects a reviewed initial policy before allocating either child; later changes require receiver replacement. */
    static NativeProcessRuntime start(MigrationWorkspace workspace, MigrationPolicy policy)
            throws IOException, InterruptedException {
        var runtime = new NativeProcessRuntime(workspace, policy);
        try {
            Process initializer = runtime.launch("init", 8781, 8782, null);
            try {
                if (!initializer.waitFor(45, TimeUnit.SECONDS) || initializer.exitValue() != 0) {
                    throw new IOException("The isolated initializer did not complete.");
                }
            } finally {
                stop(initializer);
            }
            runtime.startReceiver();
            runtime.startPublisher();
            return runtime;
        } catch (IOException | InterruptedException | RuntimeException failure) {
            runtime.close();
            throw failure;
        }
    }

    /** {@inheritDoc} */
    @Override
    public URI publisherUri() {
        return publisherUri;
    }

    /** {@inheritDoc} */
    @Override
    public URI receiverUri() {
        return receiverUri;
    }

    /** {@inheritDoc} */
    @Override
    public String publisherIdentity() {
        return identity(publisher);
    }

    /** {@inheritDoc} */
    @Override
    public String receiverIdentity() {
        return identity(receiver);
    }

    /** {@inheritDoc} */
    @Override
    public void startReceiver() throws IOException, InterruptedException {
        if (receiver != null) throw new IllegalStateException("The owned receiver is already started.");
        Path ready = workspace.directory().resolve("receiver.ready");
        Files.deleteIfExists(ready);
        receiver = launch("receiver", receiverUri == null ? 0 : receiverUri.getPort(), 8782, ready);
        receiverUri = await(receiver, ready);
    }

    /** {@inheritDoc} */
    @Override
    public void startPublisher() throws IOException, InterruptedException {
        if (publisher != null || receiverUri == null)
            throw new IllegalStateException("The publisher needs its receiver endpoint.");
        Path ready = workspace.directory().resolve("publisher.ready");
        Files.deleteIfExists(ready);
        publisher =
                launch("publisher", publisherUri == null ? 0 : publisherUri.getPort(), receiverUri.getPort(), ready);
        publisherUri = await(publisher, ready);
    }

    /** {@inheritDoc} */
    @Override
    public void stopPublisher() throws IOException {
        stop(publisher);
        publisher = null;
    }

    /** {@inheritDoc} */
    @Override
    public void stopReceiver() throws IOException {
        // Fault injection deliberately exercises crash recovery rather than relying on graceful H2 close.
        if (receiver != null && receiver.isAlive()) receiver.destroyForcibly();
        stop(receiver);
        receiver = null;
    }

    /** Keeps the publisher and its fixed target alive while only the owned receiver receives new startup settings. */
    @Override
    public void advancePolicy(MigrationPolicy next) throws IOException, InterruptedException {
        PolicyProgression.requireNext(policy, next);
        if (receiver == null || publisher == null) throw new IllegalStateException("Both owned roles must be running.");
        // Planned policy deployment is graceful; 06A independently exercises forced crash recovery.
        stop(receiver);
        receiver = null;
        policy = next;
        startReceiver();
    }

    /** Starts Java directly, without a shell, inherited remote targets or secret values in arguments. */
    private Process launch(String role, int port, int receiverPort, Path ready) throws IOException {
        var command = new ArrayList<String>();
        command.add(Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                .toString());
        command.add("-Xmx256m");
        command.add("-XX:ActiveProcessorCount=2");
        String classpath = System.getProperty("pqc.lab.childClasspath", System.getProperty("java.class.path"));
        if (!classpath.contains(java.io.File.pathSeparator) && classpath.endsWith(".jar")) {
            command.addAll(List.of("-jar", classpath));
        } else {
            command.addAll(List.of("-cp", classpath, ServiceApplication.class.getName()));
        }
        command.addAll(List.of(
                "--role=" + role,
                "--port=" + port,
                "--receiver-port=" + receiverPort,
                "--data-dir=" + workspace.directory().resolve(role.equals("receiver") ? "receiver" : "publisher"),
                "--trust-dir=" + workspace.directory().resolve("public-trust"),
                "--policy=" + policy));
        if (!role.equals("receiver")) command.add("--secret-file=" + workspace.secretFile());
        if (ready != null) command.add("--ready-file=" + ready);
        var builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(workspace.directory().resolve(role + ".log").toFile());
        // JVM option injection and account-secret overrides must not escape the owned scenario.
        for (String variable :
                List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "PQC_LAB_KEY_SECRET_FILE")) {
            builder.environment().remove(variable);
        }
        return builder.start();
    }

    /** Waits for the child's actual bound address and ready HTTP response within a fixed deadline. */
    private URI await(Process process, Path marker) throws IOException, InterruptedException {
        long expires = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < expires) {
            if (!process.isAlive()) throw new IOException("An isolated service exited during startup.");
            if (Files.isRegularFile(marker) && Files.size(marker) < 256) {
                URI address = MigrationTransport.loopback(URI.create(Files.readString(marker)));
                if (ServiceApplication.healthy(address.getPort())) return address;
            }
            Thread.sleep(50);
        }
        throw new IOException("An isolated service did not become ready.");
    }

    /** Requires a live owned process before using its identity as control-plane evidence. */
    private String identity(Process process) {
        if (process == null || !process.isAlive()) throw new IllegalStateException("The owned service is not running.");
        return "jvm-" + process.pid();
    }

    /** Stops only a retained child handle; interruption never leaves a signer process running. */
    private static void stop(Process process) throws IOException {
        if (process == null || !process.isAlive()) return;
        boolean interrupted = Thread.interrupted();
        try {
            process.destroy();
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(10, TimeUnit.SECONDS))
                        throw new IOException("An owned service could not be stopped.");
                }
            } catch (InterruptedException cancelled) {
                interrupted = true;
                process.destroyForcibly();
                try {
                    if (!process.waitFor(10, TimeUnit.SECONDS))
                        throw new IOException("An owned service could not be stopped.");
                } catch (InterruptedException repeated) {
                    interrupted = true;
                    throw new IOException("Owned service cleanup was interrupted.");
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /** Stops both child JVMs before any private workspace file can be removed. */
    @Override
    public void close() throws IOException {
        try {
            stopPublisher();
        } finally {
            stopReceiver();
        }
    }
}
