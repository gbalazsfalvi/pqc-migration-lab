package hu.balazsfalvi.pqclab.integration.app;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalOptions;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalRuntime;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalTrust;
import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves managed lifecycle behaviour through real HTTP and actual disk-only restarts. */
final class KeyLifecycleIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper json = new ObjectMapper();

    @TempDir
    Path root;

    /** Rotation, overlap, immutable receipts and revocation survive independently rebuilt runtimes. */
    @Test
    void survivesFullRestartAndRejectsRevokedRetriesWithStoredReceipts() throws Exception {
        var options = options("durable");
        Path secret = root.resolve("account.key");
        String firstId;
        String secondId;
        byte[] original;
        byte[] current;
        String receipt;
        try (var client = HttpClient.newHttpClient()) {
            try (var runtime = LocalRuntime.start(options, CLOCK, secret)) {
                var keys = get(client, runtime.publisherUri(), "/api/workbench/keys");
                assertEquals(200, keys.statusCode());
                var view = json.readTree(keys.body());
                assertTrue(view.get("managed").booleanValue());
                assertEquals(1, view.get("revision").longValue());
                firstId = view.get("activeGenerationId").textValue();
                assertEquals(2, view.get("generations").get(0).get("keys").size());
                assertFalse(keys.body().contains("pkcs8"));
                assertFalse(keys.body().contains("privateKey"));
                original = envelope(client, runtime.publisherUri());
                var first = post(client, runtime.publisherUri(), "/api/workbench/deliveries", original);
                assertEquals(201, first.statusCode());
                receipt = json.readTree(first.body()).get("receiptId").textValue();
                var rotated = post(
                        client,
                        runtime.publisherUri(),
                        "/api/workbench/keys/rotate",
                        bytes("{\"expectedRevision\":1}"));
                assertEquals(200, rotated.statusCode(), rotated.body());
                var updated = json.readTree(rotated.body());
                secondId = updated.get("activeGenerationId").textValue();
                assertNotEquals(firstId, secondId);
                assertEquals(
                        "RETIRING",
                        updated.get("generations").get(1).get("state").textValue());
                assertEquals(
                        200,
                        post(client, runtime.publisherUri(), "/api/workbench/deliveries", original)
                                .statusCode());
                current = envelope(client, runtime.publisherUri());
                assertEquals(
                        201,
                        post(client, runtime.publisherUri(), "/api/workbench/deliveries", current)
                                .statusCode());
                assertEquals(
                        "BOTH_REQUIRED",
                        json.readTree(get(client, runtime.receiverUri(), "/api/receiver/status")
                                        .body())
                                .get("policy")
                                .textValue());
                assertEquals(
                        2,
                        json.readTree(post(client, runtime.publisherUri(), "/api/workbench/evaluations", current)
                                        .body())
                                .get("trustRevision")
                                .longValue());
            }
            // No in-memory bundle is passed: both contexts reconstruct trust from the authenticated vault.
            try (var runtime = LocalRuntime.start(options, CLOCK, secret)) {
                var keys = json.readTree(
                        get(client, runtime.receiverUri(), "/api/receiver/keys").body());
                assertEquals(secondId, keys.get("activeGenerationId").textValue());
                assertEquals(2, keys.get("revision").longValue());
                assertEquals(
                        200,
                        post(client, runtime.publisherUri(), "/api/workbench/deliveries", current)
                                .statusCode());
                var retry = post(client, runtime.publisherUri(), "/api/workbench/deliveries", original);
                assertEquals(200, retry.statusCode());
                assertEquals(
                        receipt, json.readTree(retry.body()).get("receiptId").textValue());
                assertEquals(
                        200,
                        post(
                                        client,
                                        runtime.publisherUri(),
                                        "/api/workbench/keys/revoke",
                                        bytes("{\"expectedRevision\":2,\"generationId\":\"" + firstId + "\"}"))
                                .statusCode());
                assertEquals(
                        403,
                        post(client, runtime.publisherUri(), "/api/workbench/deliveries", original)
                                .statusCode());
                var oldEvent = json.readTree(java.util.Base64.getDecoder()
                        .decode(json.readTree(original).get("payload").textValue()));
                var stored = get(
                        client,
                        runtime.publisherUri(),
                        "/api/workbench/receipts/" + oldEvent.get("eventId").textValue());
                assertEquals(200, stored.statusCode());
                assertEquals(
                        receipt, json.readTree(stored.body()).get("receiptId").textValue());
                var disguised = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(original);
                for (JsonNode entry : disguised.get("signatures"))
                    ((com.fasterxml.jackson.databind.node.ObjectNode) entry).put("keyid", secondId);
                disguised.put("policy", "CLASSICAL_ONLY");
                assertEquals(
                        403,
                        post(
                                        client,
                                        runtime.publisherUri(),
                                        "/api/workbench/deliveries",
                                        json.writeValueAsBytes(disguised))
                                .statusCode());
                var observed =
                        json.readTree(post(client, runtime.publisherUri(), "/api/workbench/evaluations", original)
                                .body());
                assertEquals(3, observed.get("trustRevision").longValue());
                for (JsonNode policy : observed.get("policies"))
                    assertFalse(policy.get("accepted").booleanValue());
            }
            try (var runtime = LocalRuntime.start(options, CLOCK, secret)) {
                var keys = json.readTree(get(client, runtime.publisherUri(), "/api/workbench/keys")
                        .body());
                assertEquals(
                        "REVOKED", keys.get("generations").get(1).get("state").textValue());
                assertEquals(
                        403,
                        post(client, runtime.publisherUri(), "/api/workbench/deliveries", original)
                                .statusCode());
                assertEquals(
                        200,
                        post(client, runtime.publisherUri(), "/api/workbench/deliveries", current)
                                .statusCode());
            }
        }
    }

    /** Strict schemas and exact origins reject unsafe writes; active-key revocation requires recovery rotation. */
    @Test
    void protectsControlRequestsAndRecoversFromActiveRevocation() throws Exception {
        try (var runtime = LocalRuntime.start(options("controls"), CLOCK, root.resolve("control-account.key"));
                var client = HttpClient.newHttpClient()) {
            for (String body : List.of(
                    "{}",
                    "{\"expectedRevision\":\"1\"}",
                    "{\"expectedRevision\":1.0}",
                    "{\"expectedRevision\":0}",
                    "{\"expectedRevision\":1,\"policy\":\"CLASSICAL_ONLY\"}",
                    "{\"expectedRevision\":1,\"expectedRevision\":1}")) {
                assertEquals(
                        400,
                        post(client, runtime.publisherUri(), "/api/workbench/keys/rotate", bytes(body))
                                .statusCode(),
                        body);
            }
            var origin = client.send(
                    HttpRequest.newBuilder(runtime.publisherUri().resolve("/api/workbench/keys/rotate"))
                            .header("Content-Type", "application/json")
                            .header("Origin", "https://foreign.example")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"expectedRevision\":1}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, origin.statusCode());
            var view = json.readTree(
                    get(client, runtime.publisherUri(), "/api/workbench/keys").body());
            assertEquals(1, view.get("revision").longValue());
            String id = view.get("activeGenerationId").textValue();
            assertEquals(
                    400,
                    post(
                                    client,
                                    runtime.publisherUri(),
                                    "/api/workbench/keys/revoke",
                                    bytes("{\"expectedRevision\":1,\"generationId\":\"invalid\"}"))
                            .statusCode());
            assertEquals(
                    200,
                    post(
                                    client,
                                    runtime.publisherUri(),
                                    "/api/workbench/keys/revoke",
                                    bytes("{\"expectedRevision\":1,\"generationId\":\"" + id + "\"}"))
                            .statusCode());
            var blocked = post(client, runtime.publisherUri(), "/api/publisher/envelopes", publishBody());
            assertEquals(503, blocked.statusCode());
            assertEquals(
                    "SIGNING_KEYS_REVOKED",
                    json.readTree(blocked.body()).get("code").textValue());
            assertEquals(
                    409,
                    post(
                                    client,
                                    runtime.publisherUri(),
                                    "/api/workbench/keys/rotate",
                                    bytes("{\"expectedRevision\":1}"))
                            .statusCode());
            assertEquals(
                    200,
                    post(
                                    client,
                                    runtime.publisherUri(),
                                    "/api/workbench/keys/rotate",
                                    bytes("{\"expectedRevision\":2}"))
                            .statusCode());
            assertEquals(
                    201,
                    post(
                                    client,
                                    runtime.publisherUri(),
                                    "/api/workbench/deliveries",
                                    envelope(client, runtime.publisherUri()))
                            .statusCode());
            assertEquals(
                    404,
                    post(client, runtime.receiverUri(), "/api/workbench/keys/rotate", bytes("{\"expectedRevision\":3}"))
                            .statusCode());
        }
    }

    /** The same observed revision allows only one concurrent committed rotation. */
    @Test
    void fencesConcurrentRotationRequests() throws Exception {
        try (var runtime = LocalRuntime.start(options("concurrent"), CLOCK, root.resolve("concurrent-account.key"));
                var client = HttpClient.newHttpClient();
                var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> post(
                            client,
                            runtime.publisherUri(),
                            "/api/workbench/keys/rotate",
                            bytes("{\"expectedRevision\":1}"))
                    .statusCode());
            var second = executor.submit(() -> post(
                            client,
                            runtime.publisherUri(),
                            "/api/workbench/keys/rotate",
                            bytes("{\"expectedRevision\":1}"))
                    .statusCode());
            assertEquals(
                    List.of(200, 409),
                    java.util.stream.Stream.of(first.get(), second.get())
                            .sorted()
                            .toList());
            assertEquals(
                    2,
                    json.readTree(get(client, runtime.publisherUri(), "/api/workbench/keys")
                                    .body())
                            .get("revision")
                            .longValue());
        }
    }

    /** Deterministic ephemeral probes report their limited capabilities honestly. */
    @Test
    void keepsLegacyProbesExplicitlyUnmanaged() throws Exception {
        try (var runtime = LocalRuntime.start(options("ephemeral"), CLOCK, LocalTrust.generate(LocalRuntime.ISSUER));
                var client = HttpClient.newHttpClient()) {
            assertFalse(json.readTree(get(client, runtime.publisherUri(), "/api/workbench/keys")
                            .body())
                    .get("managed")
                    .booleanValue());
            assertEquals(
                    409,
                    post(
                                    client,
                                    runtime.publisherUri(),
                                    "/api/workbench/keys/rotate",
                                    bytes("{\"expectedRevision\":1}"))
                            .statusCode());
        }
    }

    /** Creates isolated durable state and ephemeral HTTP ports for each test. */
    private LocalOptions options(String name) {
        return new LocalOptions(MigrationPolicy.BOTH_REQUIRED, 0, 0, root.resolve(name));
    }

    /** Requests a real event under the current managed generation. */
    private byte[] envelope(HttpClient client, URI base) throws Exception {
        var response = post(client, base, "/api/publisher/envelopes", publishBody());
        assertEquals(200, response.statusCode(), response.body());
        return bytes(response.body());
    }

    /** Supplies synthetic business fields without caller-owned identity or time. */
    private byte[] publishBody() {
        return bytes("{\"artifact\":\"synthetic-service\",\"version\":\"1.0.0\",\"sha256\":\"" + "a".repeat(64)
                + "\",\"signingMode\":\"DUAL\"}");
    }

    /** Encodes test-owned data explicitly as UTF-8. */
    private byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Writes a fixed relative route on an actual test listener. */
    private HttpResponse<String> post(HttpClient client, URI base, String path, byte[] body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(base.resolve(path))
                        .timeout(java.time.Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Reads actual receiver or gateway state. */
    private HttpResponse<String> get(HttpClient client, URI base, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(base.resolve(path))
                        .timeout(java.time.Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
