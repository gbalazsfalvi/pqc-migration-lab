package hu.balazsfalvi.pqclab.integration.app.runtime;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Launches a single service role, initialization, or a local container readiness probe. */
public final class ServiceApplication {
    /** Prevents construction of the executable entry point. */
    private ServiceApplication() {}

    /**
     * Runs only documented roles; secret values are never command-line arguments.
     *
     * @param arguments strict service options or a local healthcheck port
     * @throws Exception if validated startup cannot complete
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && arguments[0].startsWith("--healthcheck=")) {
            if (!healthy(Integer.parseInt(arguments[0].substring("--healthcheck=".length())))) {
                throw new IllegalStateException("The local service is not ready.");
            }
            return;
        }
        if (arguments.length == 1 && arguments[0].equals("--help")) {
            System.out.println("Usage: --role=init|publisher|receiver [--network=local|compose] [--port=8781]"
                    + " [--receiver-port=8782] [--data-dir=PATH] [--trust-dir=PATH]"
                    + " [--secret-file=PATH] [--ready-file=PATH] [--policy=BOTH_REQUIRED]");
            return;
        }
        ServiceOptions options = ServiceOptions.parse(arguments);
        if (options.role() == ServiceOptions.Role.INITIALIZE) {
            ServiceRuntime.initialize(options);
            System.out.println("PQC MIGRATION LAB / Public trust initialized");
            return;
        }
        ServiceRuntime runtime = ServiceRuntime.start(options);
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            try {
                                runtime.close();
                            } catch (IOException failure) {
                                System.err.println("The local service could not release all owned resources.");
                            }
                        },
                        "pqc-service-shutdown"));
        System.out.println("PQC MIGRATION LAB / " + options.role() + " ready");
    }

    /**
     * Probes only a fixed local readiness route without redirects or an external target.
     *
     * @param port the validated local service port
     * @return whether the actual service reports HTTP 200
     */
    public static boolean healthy(int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Use a local service port.");
        try (var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/service/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException unavailable) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
