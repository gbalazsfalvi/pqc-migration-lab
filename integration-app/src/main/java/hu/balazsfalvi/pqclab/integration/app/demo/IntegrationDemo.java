package hu.balazsfalvi.pqclab.integration.app.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.balazsfalvi.pqclab.core.dsse.DsseBase64;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Demonstrates migration outcomes through real HTTP and shuts down every server after reporting. */
public final class IntegrationDemo {
    private static final Map<MigrationPolicy, Set<SigningMode>> ACCEPTED = Map.of(
            MigrationPolicy.CLASSICAL_ONLY, Set.of(SigningMode.ED25519_ONLY, SigningMode.DUAL),
            MigrationPolicy.SHADOW_PQC, Set.of(SigningMode.ED25519_ONLY, SigningMode.DUAL),
            MigrationPolicy.BOTH_REQUIRED, Set.of(SigningMode.DUAL),
            MigrationPolicy.PQC_REQUIRED, Set.of(SigningMode.ML_DSA_65_ONLY, SigningMode.DUAL));
    private final ObjectMapper json = new ObjectMapper();
    private final DsseJsonCodec envelopes = new DsseJsonCodec();
    private final ReleaseJsonCodec events = new ReleaseJsonCodec();

    /** Creates an experiment runner whose codecs are reused only within this run. */
    public IntegrationDemo() {}

    /**
     * Runs all policies with ephemeral ports and emits functional evidence.
     *
     * @param arguments the optional report directory
     * @throws Exception if startup, transport or evidence writing fails
     */
    public static void main(String[] arguments) throws Exception {
        Path directory = arguments.length == 0 ? Path.of("build/reports/integration-demo") : Path.of(arguments[0]);
        if (!new IntegrationDemo().execute(directory)) {
            throw new IllegalStateException("One or more integration expectations failed; inspect the local report.");
        }
    }

    /**
     * Exercises the explicit policy matrix and attack cases using runtime-only signing keys.
     *
     * @param directory the local report and demonstration-ledger directory
     * @return whether every observed expectation passed
     * @throws Exception if the experiment cannot complete
     */
    public boolean execute(Path directory) throws Exception {
        List<DemoCheck> checks = new ArrayList<>();
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (MigrationPolicy policy : MigrationPolicy.values()) {
                LocalTrust trust = LocalTrust.generate(LocalRuntime.ISSUER);
                try (LocalRuntime runtime = LocalRuntime.start(
                        new LocalOptions(
                                policy, 0, 0, directory.resolve("ledgers").resolve(policy.name())),
                        Clock.systemUTC(),
                        trust)) {
                    for (SigningMode mode : SigningMode.values()) {
                        String request = "{\"artifact\":\"synthetic-service\",\"version\":\"1.0.0\",\"sha256\":\""
                                + "a".repeat(64) + "\",\"signingMode\":\"" + mode + "\"}";
                        add(
                                checks,
                                policy,
                                "publish." + mode,
                                ACCEPTED.get(policy).contains(mode) ? 201 : 403,
                                post(
                                        client,
                                        runtime.publisherUri().resolve("/api/publisher/releases"),
                                        request.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                                true);
                    }
                    ReleaseEvent event = event(Instant.now(), "1.0.0");
                    DsseEnvelope dual = sign(event, trust);
                    var accepted = receive(client, runtime, dual);
                    var repeated = receive(client, runtime, dual);
                    boolean stable = accepted.statusCode() == 201
                            && json.readTree(accepted.body())
                                    .get("receiptId")
                                    .equals(json.readTree(repeated.body()).get("receiptId"));
                    add(checks, policy, "replay.suppressed", 200, repeated, stable);
                    if (policy == MigrationPolicy.BOTH_REQUIRED) {
                        attacks(client, runtime, trust, event, dual, checks);
                    }
                }
            }
        }
        new DemoReportWriter().write(directory, checks);
        checks.forEach(check -> System.out.printf(
                "[%s] %-16s %s / HTTP %d%n",
                check.passed() ? "PASS" : "FAIL", check.policy(), check.scenario(), check.actualStatus()));
        long passed = checks.stream().filter(DemoCheck::passed).count();
        System.out.printf(
                "%d passed / %d failed%nReports: %s%n", passed, checks.size() - passed, directory.toAbsolutePath());
        return !checks.isEmpty() && passed == checks.size();
    }

    /** Exercises downgrade, tampering, conflicting delivery, freshness and untrusted-key attacks. */
    private void attacks(
            HttpClient client,
            LocalRuntime runtime,
            LocalTrust trust,
            ReleaseEvent original,
            DsseEnvelope dual,
            List<DemoCheck> checks)
            throws Exception {
        MigrationPolicy policy = MigrationPolicy.BOTH_REQUIRED;
        var stripped = new DsseEnvelope(
                dual.payloadType(), dual.payload(), List.of(dual.signatures().getFirst()));
        add(checks, policy, "attack.signature-stripping", 403, receive(client, runtime, stripped), true);
        ObjectNode override = (ObjectNode) json.readTree(envelopes.write(stripped));
        override.put("policy", "CLASSICAL_ONLY");
        add(
                checks,
                policy,
                "attack.unsigned-policy",
                403,
                post(client, runtime.receiverUri().resolve("/api/receiver/releases"), json.writeValueAsBytes(override)),
                true);
        byte[] changed = dual.payloadBytes();
        changed[0] ^= 1;
        add(
                checks,
                policy,
                "attack.payload-change",
                403,
                receive(
                        client,
                        runtime,
                        new DsseEnvelope(dual.payloadType(), DsseBase64.encode(changed), dual.signatures())),
                true);
        var conflict = new ReleaseEvent(
                1,
                original.eventId(),
                original.issuer(),
                original.audience(),
                original.issuedAt(),
                original.artifact(),
                "2.0.0",
                original.sha256());
        add(checks, policy, "attack.identifier-conflict", 409, receive(client, runtime, sign(conflict, trust)), true);
        add(
                checks,
                policy,
                "attack.expired-event",
                422,
                receive(client, runtime, sign(event(Instant.now().minusSeconds(600), "1"), trust)),
                true);
        add(
                checks,
                policy,
                "attack.future-event",
                422,
                receive(client, runtime, sign(event(Instant.now().plusSeconds(120), "1"), trust)),
                true);
        add(
                checks,
                policy,
                "attack.key-substitution",
                403,
                receive(client, runtime, sign(event(Instant.now(), "1"), LocalTrust.generate(LocalRuntime.ISSUER))),
                true);
    }

    /** Appends an observation without turning an expected rejection into a failed experiment. */
    private void add(
            List<DemoCheck> checks,
            MigrationPolicy policy,
            String scenario,
            int expected,
            HttpResponse<String> response,
            boolean additionalRequirement)
            throws Exception {
        JsonNode body = json.readTree(response.body());
        String observation = body.has("code")
                ? body.get("code").textValue()
                : body.path("status").asText("NO_OUTCOME");
        checks.add(new DemoCheck(
                scenario,
                policy,
                expected,
                response.statusCode(),
                response.statusCode() == expected && additionalRequirement,
                observation));
    }

    /** Creates controlled synthetic claims for functional attack experiments. */
    private ReleaseEvent event(Instant issuedAt, String version) {
        return new ReleaseEvent(
                1,
                UUID.randomUUID(),
                LocalRuntime.ISSUER,
                LocalRuntime.AUDIENCE,
                issuedAt,
                "synthetic-service",
                version,
                "a".repeat(64));
    }

    /** Signs identical original bytes with both trusted private signing ports. */
    private DsseEnvelope sign(ReleaseEvent event, LocalTrust trust) {
        return new DsseSigner()
                .sign(
                        ReleaseEvent.PAYLOAD_TYPE,
                        events.encode(event),
                        SigningMode.DUAL.algorithms().stream()
                                .map(trust.signingIdentity()::signer)
                                .toList());
    }

    /** Sends a DSSE envelope through the actual receiver HTTP listener. */
    private HttpResponse<String> receive(HttpClient client, LocalRuntime runtime, DsseEnvelope envelope)
            throws Exception {
        return post(client, runtime.receiverUri().resolve("/api/receiver/releases"), envelopes.write(envelope));
    }

    /** Uses a finite request timeout and sends only the supplied exact bytes. */
    private HttpResponse<String> post(HttpClient client, URI target, byte[] body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(target)
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
