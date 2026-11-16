package hu.balazsfalvi.pqclab.integration.app.migration;

import com.fasterxml.jackson.databind.JsonNode;
import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Drives only known routes on the two newly created loopback servers and journals actual responses. */
final class MigrationTransport implements AutoCloseable {
    private static final Duration DEADLINE = Duration.ofSeconds(10);
    private final URI publisher;
    private final URI receiver;
    private final MigrationJournal journal;
    private final int phase;
    private final HttpClient client;

    /** Rejects remote addresses before constructing a direct-only client with no redirect following. */
    MigrationTransport(URI publisher, URI receiver, MigrationJournal journal, int phase) {
        this.publisher = loopback(publisher);
        this.receiver = loopback(receiver);
        this.journal = journal;
        this.phase = phase;
        client = HttpClient.newBuilder()
                .proxy(new DirectOnlyProxy())
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    /** Routes are fixed in code; no remote target or policy mutation route is accepted from the CLI. */
    enum Route {
        /** Reads the actual configured receiver requirements. */
        STATUS(false, "GET", "/api/receiver/status", PublicResponse.Kind.STATUS),
        /** Reads actual public receiver state. */
        KEYS(false, "GET", "/api/receiver/keys", PublicResponse.Kind.REGISTRY),
        /** Requests a synthetic signed event from the publisher. */
        ENVELOPE(true, "POST", "/api/publisher/envelopes", PublicResponse.Kind.ENVELOPE),
        /** Compares policies without any business write. */
        EVALUATE(false, "POST", "/api/receiver/evaluations", PublicResponse.Kind.EVALUATION),
        /** Delivers the exact supplied envelope under the unchanged receiver policy. */
        DELIVER(false, "POST", "/api/receiver/releases", PublicResponse.Kind.RECEIPT),
        /** Observes real upstream failure and recovery through the publisher gateway. */
        GATEWAY_DELIVER(true, "POST", "/api/workbench/deliveries", PublicResponse.Kind.RECEIPT),
        /** Rotates both keys through publisher-only administration. */
        ROTATE(true, "POST", "/api/workbench/keys/rotate", PublicResponse.Kind.REGISTRY),
        /** Revokes a generation through publisher-only administration. */
        REVOKE(true, "POST", "/api/workbench/keys/revoke", PublicResponse.Kind.REGISTRY),
        /** Reads a typed event's durable receiver receipt. */
        RECEIPT(false, "GET", "/api/receiver/releases/", PublicResponse.Kind.RECEIPT);

        private final boolean publisher;
        private final String method;
        private final String path;
        private final PublicResponse.Kind kind;

        /** Retains fixed method, role, relative path and reviewed export schema. */
        Route(boolean publisher, String method, String path, PublicResponse.Kind kind) {
            this.publisher = publisher;
            this.method = method;
            this.path = path;
            this.kind = kind;
        }
    }

    /** Sends a fixed route and records its independently specified HTTP expectation. */
    Reply exchange(String scenario, Route route, int expected, byte[] request)
            throws IOException, InterruptedException {
        if (route == Route.RECEIPT) throw new IllegalArgumentException("A receipt requires a typed event identifier.");
        return send(scenario, route, route.path, expected, request);
    }

    /** Reads one canonical event identifier without accepting caller-controlled URL syntax. */
    Reply receipt(String scenario, UUID eventId, int expected) throws IOException, InterruptedException {
        return send(scenario, Route.RECEIPT, Route.RECEIPT.path + eventId, expected, new byte[0]);
    }

    /** Bounds allocation and the entire response deadline, including body consumption. */
    private Reply send(String scenario, Route route, String path, int expected, byte[] request)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder((route.publisher ? publisher : receiver).resolve(path))
                .timeout(DEADLINE);
        if ("POST".equals(route.method)) {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(request));
        } else {
            builder.GET();
        }
        var pending = client.sendAsync(
                builder.build(),
                HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), DsseLimits.MAX_JSON_BYTES));
        HttpResponse<byte[]> response;
        try {
            response = pending.get(DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            pending.cancel(true);
            record(scenario, route, path, expected, null, request, null, false, "INTERRUPTED");
            throw failure;
        } catch (ExecutionException | TimeoutException failure) {
            pending.cancel(true);
            record(scenario, route, path, expected, null, request, null, false, "HTTP_TRANSPORT_FAILED");
            throw new ScenarioFailure("HTTP_TRANSPORT_FAILED");
        }
        JsonNode parsed;
        try {
            parsed = PublicResponse.read(route.kind, response.statusCode(), response.body());
        } catch (ScenarioFailure failure) {
            record(
                    scenario,
                    route,
                    path,
                    expected,
                    response.statusCode(),
                    request,
                    response.body(),
                    false,
                    failure.code());
            throw failure;
        }
        int sequence =
                record(scenario, route, path, expected, response.statusCode(), request, response.body(), true, null);
        journal.expect(
                scenario + ".http", "Observed HTTP status for " + scenario, expected, response.statusCode(), sequence);
        return new Reply(sequence, parsed, response.body());
    }

    /** Journals exact synthetic bytes after response export validation, without copying arbitrary headers. */
    private int record(
            String scenario,
            Route route,
            String path,
            int expected,
            Integer status,
            byte[] request,
            byte[] response,
            boolean validated,
            String failure)
            throws IOException {
        return journal.exchange(
                phase,
                scenario,
                route.publisher ? "PUBLISHER" : "RECEIVER",
                route.method,
                path,
                expected,
                status,
                request,
                response,
                validated,
                failure);
    }

    /** Requires the actual bootstrap-selected loopback origin, with no alternate host or URI components. */
    static URI loopback(URI address) {
        if (!"http".equals(address.getScheme())
                || !"127.0.0.1".equals(address.getHost())
                || address.getPort() < 1
                || address.getPort() > 65535
                || address.getUserInfo() != null
                || address.getRawQuery() != null
                || address.getRawFragment() != null
                || !(address.getRawPath().isEmpty() || "/".equals(address.getRawPath()))) {
            throw new IllegalArgumentException("Use a bootstrap-selected IPv4 loopback origin.");
        }
        return address;
    }

    /** Releases direct-only HTTP resources after the corresponding runtime phase. */
    @Override
    public void close() {
        client.close();
    }

    /**
     * Retains only a validated public response and its exact bytes.
     *
     * @param sequence the supporting exchange number
     * @param json the parsed public body
     * @param bytes the original public HTTP response bytes
     */
    record Reply(int sequence, JsonNode json, byte[] bytes) {
        /** Snapshots mutable wire and tree representations within the observer boundary. */
        Reply {
            json = json.deepCopy();
            bytes = bytes.clone();
        }

        /** @return a defensive public tree snapshot */
        @Override
        public JsonNode json() {
            return json.deepCopy();
        }

        /** @return the exact response bytes, independently owned */
        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    /** Ignores machine-wide proxy settings without modifying any global JVM configuration. */
    private static final class DirectOnlyProxy extends ProxySelector {
        /** Always connects directly to a validated numeric loopback origin. */
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        /** No proxy connection is attempted, so there is no proxy failure state to update. */
        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException failure) {}
    }
}
