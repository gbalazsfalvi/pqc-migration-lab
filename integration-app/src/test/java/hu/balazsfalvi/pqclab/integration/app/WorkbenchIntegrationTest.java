package hu.balazsfalvi.pqclab.integration.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseSigner;
import hu.balazsfalvi.pqclab.integration.app.publisher.SigningMode;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalOptions;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalRuntime;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalTrust;
import hu.balazsfalvi.pqclab.integration.app.wire.ReleaseJsonCodec;
import hu.balazsfalvi.pqclab.integration.app.workbench.ExperimentScenario;
import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import hu.balazsfalvi.pqclab.integration.core.ReleaseEvent;
import hu.balazsfalvi.pqclab.json.DsseJsonCodec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the workbench gateway, read-only comparisons and controlled fixtures against real Java services. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class WorkbenchIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-13T08:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private final DsseJsonCodec envelopes = new DsseJsonCodec();
    private final ReleaseJsonCodec events = new ReleaseJsonCodec();
    private final LocalTrust trust = LocalTrust.generate(LocalRuntime.ISSUER);
    private final HttpClient client = HttpClient.newHttpClient();
    private LocalRuntime runtime;

    /** Starts two actual HTTP listeners and a file-backed receiver with fixed trusted time. */
    @BeforeAll
    void start(@TempDir Path directory) throws Exception {
        runtime = LocalRuntime.start(
                new LocalOptions(MigrationPolicy.BOTH_REQUIRED, 0, 0, directory),
                Clock.fixed(NOW, ZoneOffset.UTC),
                trust);
    }

    /** Releases HTTP and database resources before temporary directory cleanup. */
    @AfterAll
    void stop() {
        if (runtime != null) runtime.close();
        client.close();
    }

    /** Merely comparing policies cannot store an event or weaken the live receiver. */
    @Test
    void comparesActualPoliciesWithoutAStoreSideEffect() throws Exception {
        DsseEnvelope envelope = baseline();
        var response = post("/api/workbench/evaluations", envelopes.write(envelope));
        assertEquals(200, response.statusCode(), response.body());
        JsonNode report = json.readTree(response.body());
        assertEquals(NOW.toString(), report.get("evaluatedAt").textValue());
        assertEquals("BOTH_REQUIRED", report.get("receiverPolicy").textValue());
        assertEquals(4, report.get("policies").size());
        for (JsonNode policy : report.get("policies")) {
            assertTrue(policy.get("accepted").booleanValue());
            assertEquals(6, policy.get("steps").size());
        }
        assertTrue(report.get("signatureEvidence").get("ed25519Valid").booleanValue());
        assertTrue(report.get("signatureEvidence").get("mlDsa65Valid").booleanValue());
        assertEquals(64, report.get("signatures").get(0).get("bytes").intValue());
        assertEquals(3309, report.get("signatures").get(1).get("bytes").intValue());
        String id = events.decode(envelope.payloadBytes()).eventId().toString();
        assertEquals(404, get("/api/workbench/receipts/" + id).statusCode());
        assertEquals(
                "BOTH_REQUIRED",
                json.readTree(get("/api/workbench/config").body())
                        .get("receiverPolicy")
                        .textValue());
    }

    /** Every fixture invokes actual signing or byte mutation, then real policy validation at the receiver. */
    @ParameterizedTest
    @CsvSource({
        "STRIP_PQC,true,false,POLICY_REJECTED",
        "TAMPER_PAYLOAD,false,false,POLICY_REJECTED",
        "WRONG_KEY,false,false,POLICY_REJECTED",
        "EXPIRED_EVENT,true,true,EVENT_EXPIRED",
        "FUTURE_EVENT,true,true,EVENT_IN_FUTURE",
        "WRONG_AUDIENCE,true,true,AUDIENCE_MISMATCH",
        "UNSIGNED_DOWNGRADE,true,false,POLICY_REJECTED"
    })
    void evaluatesControlledFaults(ExperimentScenario scenario, boolean ed, boolean ml, String code) throws Exception {
        JsonNode fixture = fixture(scenario, baseline());
        var response = post("/api/workbench/evaluations", json.writeValueAsBytes(fixture.get("envelope")));
        assertEquals(200, response.statusCode(), response.body());
        JsonNode report = json.readTree(response.body());
        assertEquals(ed, report.get("signatureEvidence").get("ed25519Valid").booleanValue());
        assertEquals(ml, report.get("signatureEvidence").get("mlDsa65Valid").booleanValue());
        JsonNode both = policy(report, "BOTH_REQUIRED");
        assertFalse(both.get("accepted").booleanValue());
        assertEquals(code, both.get("rejectionCode").textValue());
        if (scenario == ExperimentScenario.STRIP_PQC || scenario == ExperimentScenario.UNSIGNED_DOWNGRADE) {
            assertTrue(policy(report, "CLASSICAL_ONLY").get("accepted").booleanValue());
            assertFalse(policy(report, "PQC_REQUIRED").get("accepted").booleanValue());
            assertEquals("FAILED", both.get("steps").get(1).get("status").textValue());
        }
        var delivery = post("/api/workbench/deliveries", json.writeValueAsBytes(fixture.get("envelope")));
        assertEquals(code.equals("POLICY_REJECTED") ? 403 : 422, delivery.statusCode());
        assertEquals(code, json.readTree(delivery.body()).get("code").textValue());
    }

    /** A real baseline, retry, conflicting signed event and durable query remain separate operations. */
    @Test
    void preservesAtomicDeliveryAndConflictSemanticsThroughTheGateway() throws Exception {
        DsseEnvelope original = baseline();
        var first = post("/api/workbench/deliveries", envelopes.write(original));
        assertEquals(201, first.statusCode());
        var repeat = post("/api/workbench/deliveries", envelopes.write(original));
        assertEquals(200, repeat.statusCode());
        assertEquals(
                json.readTree(first.body()).get("receiptId"),
                json.readTree(repeat.body()).get("receiptId"));
        var changed = fixture(ExperimentScenario.CONFLICTING_ID, original);
        var evaluation = post("/api/workbench/evaluations", json.writeValueAsBytes(changed.get("envelope")));
        assertTrue(policy(json.readTree(evaluation.body()), "BOTH_REQUIRED")
                .get("accepted")
                .booleanValue());
        var conflict = post("/api/workbench/deliveries", json.writeValueAsBytes(changed.get("envelope")));
        assertEquals(409, conflict.statusCode());
        assertEquals(
                "EVENT_ID_CONFLICT", json.readTree(conflict.body()).get("code").textValue());
        String eventId = events.decode(original.payloadBytes()).eventId().toString();
        var stored = get("/api/workbench/receipts/" + eventId);
        assertEquals(200, stored.statusCode());
        assertEquals("STORED", json.readTree(stored.body()).get("status").textValue());
        assertEquals(
                json.readTree(first.body()).get("receiptId"),
                json.readTree(stored.body()).get("receiptId"));
    }

    /** Unsupported types and signed malformed business data produce accurate stopped validation paths. */
    @Test
    void reportsContractAndSchemaBoundaries() throws Exception {
        DsseEnvelope original = baseline();
        var otherType = new DsseEnvelope("application/other", original.payload(), original.signatures());
        var rejected = json.readTree(
                post("/api/workbench/evaluations", envelopes.write(otherType)).body());
        assertEquals(
                "INVALID_PAYLOAD_TYPE",
                policy(rejected, "BOTH_REQUIRED").get("rejectionCode").textValue());
        assertEquals(
                "FAILED",
                policy(rejected, "BOTH_REQUIRED")
                        .get("steps")
                        .get(0)
                        .get("status")
                        .textValue());
        var malformed = sign("{}".getBytes(StandardCharsets.UTF_8));
        var report = json.readTree(
                post("/api/workbench/evaluations", envelopes.write(malformed)).body());
        assertEquals(
                "MALFORMED_EVENT",
                policy(report, "BOTH_REQUIRED").get("rejectionCode").textValue());
        assertEquals(
                "FAILED",
                policy(report, "BOTH_REQUIRED")
                        .get("steps")
                        .get(2)
                        .get("status")
                        .textValue());
    }

    /** The fixture API does not turn arbitrary unsigned or differently owned input into a signing oracle. */
    @Test
    void rejectsUnauthenticatedAndMismatchedFixtureBaselines() throws Exception {
        var root = json.createObjectNode().put("scenario", "EXPIRED_EVENT");
        root.set(
                "envelope",
                json.readTree(
                        envelopes.write(new DsseEnvelope(ReleaseEvent.PAYLOAD_TYPE, "e30=", java.util.List.of()))));
        assertEquals(
                422,
                post("/api/workbench/experiments", json.writeValueAsBytes(root)).statusCode());
        ReleaseEvent event = new ReleaseEvent(
                1, UUID.randomUUID(), "another-publisher", LocalRuntime.AUDIENCE, NOW, "service", "1", "a".repeat(64));
        root.set("envelope", json.readTree(envelopes.write(sign(events.encode(event)))));
        assertEquals(
                422,
                post("/api/workbench/experiments", json.writeValueAsBytes(root)).statusCode());
        root.put("scenario", "UNKNOWN");
        assertEquals(
                400,
                post("/api/workbench/experiments", json.writeValueAsBytes(root)).statusCode());
        assertEquals(
                400,
                post("/api/workbench/experiments", "{}".getBytes(StandardCharsets.UTF_8))
                        .statusCode());
    }

    /** Exact origin checks admit only this app or the documented local development UI. */
    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:5173", "http://localhost:5173", "CURRENT"})
    void permitsExplicitWorkbenchOrigins(String origin) throws Exception {
        String actual = origin.equals("CURRENT") ? runtime.publisherUri().toString() : origin;
        var response = client.send(
                HttpRequest.newBuilder(runtime.publisherUri().resolve("/api/workbench/config"))
                        .header("Origin", actual)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }

    /** Suffix tricks, opaque origins and cross-site browser requests cannot reach the local API. */
    @ParameterizedTest
    @ValueSource(
            strings = {"https://evil.example", "http://localhost:5173.evil.example", "null", "http://127.0.0.1:5174"})
    void rejectsOtherBrowserOrigins(String origin) throws Exception {
        var response = client.send(
                HttpRequest.newBuilder(runtime.publisherUri().resolve("/api/workbench/config"))
                        .header("Origin", origin)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }

    /** The packaged workbench is served by the publisher with a restrictive same-origin content policy. */
    @Test
    void servesTheBuiltEnglishInterfaceWithSafeHeaders() throws Exception {
        var response = get("/");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Migration workbench"));
        assertTrue(response.headers()
                .firstValue("Content-Security-Policy")
                .orElseThrow()
                .contains("script-src 'self'"));
        var crossSite = client.send(
                HttpRequest.newBuilder(runtime.publisherUri().resolve("/api/workbench/config"))
                        .header("Sec-Fetch-Site", "cross-site")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, crossSite.statusCode());
    }

    /** Creates actual publisher-authenticated baseline bytes with deterministic freshness. */
    private DsseEnvelope baseline() {
        ReleaseEvent event = new ReleaseEvent(
                1,
                UUID.randomUUID(),
                LocalRuntime.ISSUER,
                LocalRuntime.AUDIENCE,
                NOW,
                "service",
                "1.0.0",
                "a".repeat(64));
        return sign(events.encode(event));
    }

    /** Signs exact test bytes using native JCA-backed private ports. */
    private DsseEnvelope sign(byte[] body) {
        return new DsseSigner()
                .sign(
                        ReleaseEvent.PAYLOAD_TYPE,
                        body,
                        SigningMode.DUAL.algorithms().stream()
                                .map(trust.signingIdentity()::signer)
                                .toList());
    }

    /** Invokes the public fixture route rather than constructing hypothetical frontend results. */
    private JsonNode fixture(ExperimentScenario scenario, DsseEnvelope envelope) throws Exception {
        var root = json.createObjectNode().put("scenario", scenario.name());
        root.set("envelope", json.readTree(envelopes.write(envelope)));
        var response = post("/api/workbench/experiments", json.writeValueAsBytes(root));
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    /** Selects an observed policy by its stable wire identifier. */
    private JsonNode policy(JsonNode report, String name) {
        for (JsonNode result : report.get("policies")) {
            if (name.equals(result.get("policy").textValue())) return result;
        }
        throw new AssertionError("Every supported policy must be represented.");
    }

    /** Delivers bounded JSON through the real publisher-origin gateway. */
    private HttpResponse<String> post(String path, byte[] body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(runtime.publisherUri().resolve(path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Reads the actual local HTTP result. */
    private HttpResponse<String> get(String path) throws Exception {
        URI uri = runtime.publisherUri().resolve(path);
        return client.send(
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
