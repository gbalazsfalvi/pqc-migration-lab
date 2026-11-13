package hu.balazsfalvi.pqclab.integration.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.balazsfalvi.pqclab.core.dsse.DsseBase64;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseSignature;
import hu.balazsfalvi.pqclab.core.dsse.DsseSigner;
import hu.balazsfalvi.pqclab.integration.app.publisher.SigningMode;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalOptions;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalRuntime;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalTrust;
import hu.balazsfalvi.pqclab.integration.app.wire.ReleaseJsonCodec;
import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import hu.balazsfalvi.pqclab.integration.core.ReleaseEvent;
import hu.balazsfalvi.pqclab.json.DsseJsonCodec;
import java.io.ByteArrayInputStream;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Exercises the complete publisher, cryptographic receiver and durable ledger over real HTTP. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class IntegrationFlowTest {
    private static final Instant NOW = Instant.parse("2026-11-13T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ObjectMapper json = new ObjectMapper();
    private final DsseJsonCodec envelopes = new DsseJsonCodec();
    private final ReleaseJsonCodec events = new ReleaseJsonCodec();
    private final LocalTrust trust = LocalTrust.generate(LocalRuntime.ISSUER);
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private LocalRuntime runtime;
    private LocalOptions options;

    /** Starts two actual Spring Boot contexts with public-only receiver trust and a temporary file ledger. */
    @BeforeAll
    void start(@TempDir Path directory) throws Exception {
        options = new LocalOptions(MigrationPolicy.BOTH_REQUIRED, 0, 0, directory);
        runtime = LocalRuntime.start(options, CLOCK, trust);
    }

    /** Releases listeners and database locks before JUnit removes its temporary directory. */
    @AfterAll
    void stop() {
        if (runtime != null) {
            runtime.close();
        }
        client.close();
    }

    /** Follows a public publisher request through signing, HTTP delivery, verification and durable query. */
    @Test
    void publishesThroughBothServersAndReadsTheStoredBusinessResult() throws Exception {
        String body =
                "{\"artifact\":\"synthetic-service\",\"version\":\"1.2.3\",\"sha256\":\"" + "a".repeat(64) + "\"}";
        var response =
                post(runtime.publisherUri().resolve("/api/publisher/releases"), body.getBytes(StandardCharsets.UTF_8));
        assertEquals(201, response.statusCode(), response.body());
        JsonNode receipt = json.readTree(response.body());
        assertEquals("ACCEPTED", receipt.get("status").textValue());
        assertEquals("BOTH_REQUIRED", receipt.get("enforcedPolicy").textValue());
        assertTrue(receipt.get("acceptedEvidence").get("ed25519Valid").booleanValue());
        assertTrue(receipt.get("acceptedEvidence").get("mlDsa65Valid").booleanValue());
        var stored = get(runtime.receiverUri()
                .resolve("/api/receiver/releases/" + receipt.get("eventId").textValue()));
        assertEquals(200, stored.statusCode());
        assertEquals(receipt.get("receiptId"), json.readTree(stored.body()).get("receiptId"));
        assertEquals("no-store", stored.headers().firstValue("Cache-Control").orElseThrow());
    }

    /** Verifies that an exact replay cannot apply the release again or generate another receipt. */
    @Test
    void suppressesAnIdenticalReplayWithTheOriginalReceipt() throws Exception {
        DsseEnvelope envelope = sign(
                event(UUID.randomUUID(), NOW, LocalRuntime.ISSUER, LocalRuntime.AUDIENCE, "1.0"), SigningMode.DUAL);
        var first = receive(envelope);
        var retry = receive(envelope);
        assertEquals(201, first.statusCode());
        assertEquals(200, retry.statusCode());
        JsonNode original = json.readTree(first.body());
        JsonNode repeated = json.readTree(retry.body());
        assertEquals("ALREADY_ACCEPTED", repeated.get("status").textValue());
        assertEquals(original.get("receiptId"), repeated.get("receiptId"));
        assertEquals(original.get("acceptedAt"), repeated.get("acceptedAt"));
    }

    /** Requires one committed result when concurrent authenticated deliveries race for the same event. */
    @Test
    void arbitratesConcurrentRetriesAtomically() throws Exception {
        byte[] body = envelopes.write(sign(
                event(UUID.randomUUID(), NOW, LocalRuntime.ISSUER, LocalRuntime.AUDIENCE, "2.0"), SigningMode.DUAL));
        Callable<HttpResponse<String>> delivery =
                () -> post(runtime.receiverUri().resolve("/api/receiver/releases"), body);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = executor.invokeAll(java.util.Collections.nCopies(8, delivery));
            int created = 0;
            Set<String> receipts = new java.util.HashSet<>();
            for (var result : results) {
                var response = result.get();
                assertTrue(response.statusCode() == 201 || response.statusCode() == 200, response.body());
                if (response.statusCode() == 201) {
                    created++;
                }
                receipts.add(json.readTree(response.body()).get("receiptId").textValue());
            }
            assertEquals(1, created);
            assertEquals(1, receipts.size());
        }
    }

    /** A genuine signature on different bytes must not overwrite an already accepted event identifier. */
    @Test
    void rejectsConflictingIdentifierReuseWithoutChangingTheStoredRelease() throws Exception {
        UUID id = UUID.randomUUID();
        var accepted =
                receive(sign(event(id, NOW, LocalRuntime.ISSUER, LocalRuntime.AUDIENCE, "original"), SigningMode.DUAL));
        assertEquals(201, accepted.statusCode());
        var conflict =
                receive(sign(event(id, NOW, LocalRuntime.ISSUER, LocalRuntime.AUDIENCE, "changed"), SigningMode.DUAL));
        assertProblem(conflict, 409, "EVENT_ID_CONFLICT");
        assertEquals(
                "original",
                json.readTree(get(runtime.receiverUri().resolve("/api/receiver/releases/" + id))
                                .body())
                        .get("version")
                        .textValue());
    }

    /** Persistence survives a complete shutdown and restart rather than relying on an in-memory replay set. */
    @Test
    void retainsTheReceiptAcrossApplicationRestart() throws Exception {
        DsseEnvelope envelope = sign(
                event(UUID.randomUUID(), NOW, LocalRuntime.ISSUER, LocalRuntime.AUDIENCE, "restart"), SigningMode.DUAL);
        var first = receive(envelope);
        assertEquals(201, first.statusCode());
        runtime.close();
        runtime = LocalRuntime.start(options, CLOCK, trust);
        var retry = receive(envelope);
        assertEquals(200, retry.statusCode());
        assertEquals(
                json.readTree(first.body()).get("receiptId"),
                json.readTree(retry.body()).get("receiptId"));
    }

    /** Stripping, duplicating classical entries or advertising a weaker policy cannot meet both-required. */
    @Test
    void rejectsStrippingAndUnsignedPolicyOverrides() throws Exception {
        DsseEnvelope dual = sign(
                event(UUID.randomUUID(), NOW, LocalRuntime.ISSUER, LocalRuntime.AUDIENCE, "stripping"),
                SigningMode.DUAL);
        DsseSignature classical = dual.signatures().getFirst();
        var stripped = new DsseEnvelope(dual.payloadType(), dual.payload(), List.of(classical));
        assertProblem(receive(stripped), 403, "POLICY_REJECTED");
        assertProblem(
                receive(new DsseEnvelope(dual.payloadType(), dual.payload(), List.of(classical, classical))),
                403,
                "POLICY_REJECTED");
        var untrusted = json.readTree(envelopes.write(stripped));
        ((com.fasterxml.jackson.databind.node.ObjectNode) untrusted).put("policy", "CLASSICAL_ONLY");
        assertProblem(
                post(runtime.receiverUri().resolve("/api/receiver/releases"), json.writeValueAsBytes(untrusted)),
                403,
                "POLICY_REJECTED");
    }

    /** Key hints do not control the trusted registry, while a different key with the same issuer fails. */
    @Test
    void ignoresForgedHintsAndRejectsKeySubstitution() throws Exception {
        ReleaseEvent event = event(UUID.randomUUID(), NOW, LocalRuntime.ISSUER, LocalRuntime.AUDIENCE, "keys");
        var dual = sign(event, SigningMode.DUAL);
        var renamed = new DsseEnvelope(
                dual.payloadType(),
                dual.payload(),
                dual.signatures().stream()
                        .map(entry -> new DsseSignature("forged-lookup-hint", entry.signature()))
                        .toList());
        assertEquals(201, receive(renamed).statusCode());
        LocalTrust intruder = LocalTrust.generate(LocalRuntime.ISSUER);
        var forged = new DsseSigner()
                .sign(
                        ReleaseEvent.PAYLOAD_TYPE,
                        events.encode(event),
                        SigningMode.DUAL.algorithms().stream()
                                .map(intruder.signingIdentity()::signer)
                                .toList());
        assertProblem(receive(forged), 403, "POLICY_REJECTED");
    }

    /** The receiver binds verified bytes to its configured identity, audience and freshness window. */
    @ParameterizedTest
    @CsvSource({
        "other-publisher,urn:pqclab:release-receiver,0,403,ISSUER_MISMATCH",
        "release-publisher,urn:pqclab:other-receiver,0,422,AUDIENCE_MISMATCH",
        "release-publisher,urn:pqclab:release-receiver,-301,422,EVENT_EXPIRED",
        "release-publisher,urn:pqclab:release-receiver,31,422,EVENT_IN_FUTURE"
    })
    void rejectsInvalidSignedClaims(String issuer, String audience, long timeOffset, int status, String code)
            throws Exception {
        UUID id = UUID.randomUUID();
        assertProblem(
                receive(sign(event(id, NOW.plusSeconds(timeOffset), issuer, audience, "claims"), SigningMode.DUAL)),
                status,
                code);
        assertEquals(
                404,
                get(runtime.receiverUri().resolve("/api/receiver/releases/" + id))
                        .statusCode());
    }

    /** The explicitly documented age and clock-skew boundaries remain inclusive. */
    @ParameterizedTest
    @CsvSource({"-300", "30"})
    void acceptsFreshnessBoundaryValues(long seconds) throws Exception {
        assertEquals(
                201,
                receive(sign(
                                event(
                                        UUID.randomUUID(),
                                        NOW.plusSeconds(seconds),
                                        LocalRuntime.ISSUER,
                                        LocalRuntime.AUDIENCE,
                                        "boundary"),
                                SigningMode.DUAL))
                        .statusCode());
    }

    /** Modified authenticated bytes and an unsupported type cannot reach a durable business result. */
    @Test
    void rejectsPayloadAndTypeChanges() throws Exception {
        var dual = sign(
                event(UUID.randomUUID(), NOW, LocalRuntime.ISSUER, LocalRuntime.AUDIENCE, "before"), SigningMode.DUAL);
        byte[] changed = dual.payloadBytes();
        changed[0] ^= 1;
        assertProblem(
                receive(new DsseEnvelope(dual.payloadType(), DsseBase64.encode(changed), dual.signatures())),
                403,
                "POLICY_REJECTED");
        assertProblem(
                receive(new DsseEnvelope("application/other", dual.payload(), dual.signatures())),
                422,
                "INVALID_PAYLOAD_TYPE");
    }

    /** Successful cryptography does not make an ambiguous or unsupported business schema acceptable. */
    @ParameterizedTest
    @CsvSource({"{}", "'{\"schemaVersion\":\"1\"}'", "'{\"schemaVersion\":1,\"schemaVersion\":1}'"})
    void rejectsInvalidSignedBusinessDocuments(String document) throws Exception {
        var envelope = new DsseSigner()
                .sign(
                        ReleaseEvent.PAYLOAD_TYPE,
                        document.getBytes(StandardCharsets.UTF_8),
                        SigningMode.DUAL.algorithms().stream()
                                .map(trust.signingIdentity()::signer)
                                .toList());
        assertProblem(receive(envelope), 422, "MALFORMED_EVENT");
    }

    /** The HTTP boundary rejects ambiguous DSSE, browser-origin access and unbounded streaming input. */
    @Test
    void rejectsMalformedOriginAndChunkedOversizedRequests() throws Exception {
        URI target = runtime.receiverUri().resolve("/api/receiver/releases");
        assertProblem(
                post(target, "{\"payload\":\"\",\"payload\":\"\"}".getBytes(StandardCharsets.UTF_8)),
                400,
                "MALFORMED_ENVELOPE");
        var origin = HttpRequest.newBuilder(target)
                .header("Content-Type", "application/json")
                .header("Origin", "https://untrusted.example")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        assertProblem(client.send(origin, HttpResponse.BodyHandlers.ofString()), 403, "BROWSER_ORIGIN_REJECTED");
        byte[] oversized = new byte[1_048_577];
        var chunked = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(oversized)))
                .build();
        assertProblem(client.send(chunked, HttpResponse.BodyHandlers.ofString()), 413, "BODY_TOO_LARGE");
        var fixed = HttpRequest.newBuilder(target)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(oversized))
                .build();
        assertProblem(client.send(fixed, HttpResponse.BodyHandlers.ofString()), 413, "BODY_TOO_LARGE");
    }

    /** Publisher callers cannot inject identity, receiver policy or malformed scalar types. */
    @Test
    void rejectsPublisherOverridesAndReportsReadOnlyReceiverSettings() throws Exception {
        assertProblem(
                post(
                        runtime.publisherUri().resolve("/api/publisher/envelopes"),
                        "{\"issuer\":\"intruder\"}".getBytes(StandardCharsets.UTF_8)),
                400,
                "INVALID_PUBLISH_REQUEST");
        JsonNode status = json.readTree(
                get(runtime.receiverUri().resolve("/api/receiver/status")).body());
        assertEquals("BOTH_REQUIRED", status.get("policy").textValue());
        assertFalse(status.has("privateKey"));
        assertProblem(
                get(runtime.receiverUri().resolve("/api/receiver/releases/not-a-uuid")), 400, "INVALID_PARAMETER");
    }

    /** Sends exactly the supplied envelope bytes through the real receiver HTTP boundary. */
    private HttpResponse<String> receive(DsseEnvelope envelope) throws Exception {
        return post(runtime.receiverUri().resolve("/api/receiver/releases"), envelopes.write(envelope));
    }

    /** Creates controlled signed claims for negative tests without weakening the publisher API. */
    private ReleaseEvent event(UUID id, Instant issuedAt, String issuer, String audience, String version) {
        return new ReleaseEvent(1, id, issuer, audience, issuedAt, "synthetic-service", version, "b".repeat(64));
    }

    /** Uses real private signing ports for independent receiver rejection tests. */
    private DsseEnvelope sign(ReleaseEvent event, SigningMode mode) {
        return new DsseSigner()
                .sign(
                        ReleaseEvent.PAYLOAD_TYPE,
                        events.encode(event),
                        mode.algorithms().stream()
                                .map(trust.signingIdentity()::signer)
                                .toList());
    }

    /** Performs a bounded JSON POST without following redirects. */
    private HttpResponse<String> post(URI target, byte[] body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(target)
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Queries the real persisted-result API. */
    private HttpResponse<String> get(URI target) throws Exception {
        return client.send(
                HttpRequest.newBuilder(target)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Requires both the HTTP category and the stable safe reason code. */
    private void assertProblem(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, json.readTree(response.body()).get("code").textValue());
    }
}
