package hu.balazsfalvi.pqclab.integration.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Runs the migration acceptance matrix through real publisher-to-receiver HTTP delivery. */
final class PolicyIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-13T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final DsseJsonCodec envelopes = new DsseJsonCodec();
    private final ReleaseJsonCodec events = new ReleaseJsonCodec();
    private final ObjectMapper json = new ObjectMapper();

    /** Uses an independently specified expected matrix, including each single-signature migration phase. */
    @ParameterizedTest
    @CsvSource({
        "CLASSICAL_ONLY,201,403,201",
        "SHADOW_PQC,201,403,201",
        "BOTH_REQUIRED,403,403,201",
        "PQC_REQUIRED,403,201,201"
    })
    void enforcesThePolicyMatrix(
            MigrationPolicy policy, int edStatus, int mlStatus, int dualStatus, @TempDir Path directory)
            throws Exception {
        try (var runtime = LocalRuntime.start(
                        new LocalOptions(policy, 0, 0, directory), CLOCK, LocalTrust.generate(LocalRuntime.ISSUER));
                var client = HttpClient.newHttpClient()) {
            int[] expected = {edStatus, mlStatus, dualStatus};
            for (int index = 0; index < SigningMode.values().length; index++) {
                SigningMode mode = SigningMode.values()[index];
                String request = "{\"artifact\":\"service\",\"version\":\"1\",\"sha256\":\"" + "a".repeat(64)
                        + "\",\"signingMode\":\"" + mode + "\"}";
                var response = client.send(
                        HttpRequest.newBuilder(runtime.publisherUri().resolve("/api/publisher/releases"))
                                .timeout(Duration.ofSeconds(10))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(request))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(expected[index], response.statusCode(), policy + " / " + mode + " / " + response.body());
                if (response.statusCode() == 201) {
                    assertEquals(
                            policy.name(),
                            json.readTree(response.body()).get("enforcedPolicy").textValue());
                }
            }
        }
    }

    /** Shadow mode records a broken PQC signature while retaining its explicit classical requirement. */
    @Test
    void recordsFailedPqcEvidenceWithoutWeakeningTheClassicalRequirement(@TempDir Path directory) throws Exception {
        LocalTrust trust = LocalTrust.generate(LocalRuntime.ISSUER);
        try (var runtime = LocalRuntime.start(
                        new LocalOptions(MigrationPolicy.SHADOW_PQC, 0, 0, directory), CLOCK, trust);
                var client = HttpClient.newHttpClient()) {
            var dual = sign(event(), SigningMode.DUAL, trust);
            byte[] invalid = dual.signatures().getLast().signatureBytes();
            invalid[0] ^= 1;
            var damaged = new DsseEnvelope(
                    dual.payloadType(),
                    dual.payload(),
                    List.of(
                            dual.signatures().getFirst(),
                            new DsseSignature(
                                    "same-untrusted-hint",
                                    java.util.Base64.getEncoder().encodeToString(invalid))));
            var accepted = receive(client, runtime.receiverUri(), damaged);
            assertEquals(201, accepted.statusCode(), accepted.body());
            var evidence = json.readTree(accepted.body()).get("acceptedEvidence");
            assertTrue(evidence.get("ed25519Valid").booleanValue());
            assertFalse(evidence.get("mlDsa65Valid").booleanValue());
            assertEquals(
                    403,
                    receive(client, runtime.receiverUri(), sign(event(), SigningMode.ML_DSA_65_ONLY, trust))
                            .statusCode());
        }
    }

    /** An old accepted receipt cannot bypass a strengthened receiver policy on retry. */
    @Test
    void rechecksRetriesAfterPolicyUpgradeAndRejectsStaleReplays(@TempDir Path directory) throws Exception {
        LocalTrust trust = LocalTrust.generate(LocalRuntime.ISSUER);
        ReleaseEvent event = event();
        DsseEnvelope classical = sign(event, SigningMode.ED25519_ONLY, trust);
        String receipt;
        try (var runtime = LocalRuntime.start(
                        new LocalOptions(MigrationPolicy.CLASSICAL_ONLY, 0, 0, directory), CLOCK, trust);
                var client = HttpClient.newHttpClient()) {
            var accepted = receive(client, runtime.receiverUri(), classical);
            assertEquals(201, accepted.statusCode());
            receipt = json.readTree(accepted.body()).get("receiptId").textValue();
        }
        try (var runtime = LocalRuntime.start(
                        new LocalOptions(MigrationPolicy.BOTH_REQUIRED, 0, 0, directory), CLOCK, trust);
                var client = HttpClient.newHttpClient()) {
            assertEquals(403, receive(client, runtime.receiverUri(), classical).statusCode());
            var upgraded = receive(client, runtime.receiverUri(), sign(event, SigningMode.DUAL, trust));
            assertEquals(200, upgraded.statusCode(), upgraded.body());
            var body = json.readTree(upgraded.body());
            assertEquals(receipt, body.get("receiptId").textValue());
            assertEquals("BOTH_REQUIRED", body.get("enforcedPolicy").textValue());
            assertEquals("CLASSICAL_ONLY", body.get("acceptedPolicy").textValue());
        }
        Clock later = Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC);
        try (var runtime = LocalRuntime.start(
                        new LocalOptions(MigrationPolicy.BOTH_REQUIRED, 0, 0, directory), later, trust);
                var client = HttpClient.newHttpClient()) {
            var stale = receive(client, runtime.receiverUri(), sign(event, SigningMode.DUAL, trust));
            assertEquals(422, stale.statusCode());
            assertEquals(
                    "EVENT_EXPIRED", json.readTree(stale.body()).get("code").textValue());
        }
    }

    /** Constructs one complete synthetic event with a fixed valid time. */
    private ReleaseEvent event() {
        return new ReleaseEvent(
                1, UUID.randomUUID(), LocalRuntime.ISSUER, LocalRuntime.AUDIENCE, NOW, "service", "1", "a".repeat(64));
    }

    /** Signs actual payload bytes with a caller-controlled local test identity. */
    private DsseEnvelope sign(ReleaseEvent event, SigningMode mode, LocalTrust trust) {
        return new DsseSigner()
                .sign(
                        ReleaseEvent.PAYLOAD_TYPE,
                        events.encode(event),
                        mode.algorithms().stream()
                                .map(trust.signingIdentity()::signer)
                                .toList());
    }

    /** Delivers exact DSSE JSON through the real receiver boundary. */
    private HttpResponse<String> receive(HttpClient client, URI base, DsseEnvelope envelope) throws Exception {
        return client.send(
                HttpRequest.newBuilder(base.resolve("/api/receiver/releases"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(envelopes.write(envelope)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
