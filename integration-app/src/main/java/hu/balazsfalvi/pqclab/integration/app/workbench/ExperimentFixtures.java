package hu.balazsfalvi.pqclab.integration.app.workbench;

import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.core.dsse.DsseBase64;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import hu.balazsfalvi.pqclab.core.dsse.DsseSigner;
import hu.balazsfalvi.pqclab.core.dsse.DsseVerifier;
import hu.balazsfalvi.pqclab.integration.app.http.ApiFailure;
import hu.balazsfalvi.pqclab.integration.app.publisher.SigningIdentity;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalTrust;
import hu.balazsfalvi.pqclab.integration.app.runtime.ReceiverProperties;
import hu.balazsfalvi.pqclab.integration.app.wire.ReleaseJsonCodec;
import hu.balazsfalvi.pqclab.integration.app.wire.StrictJson;
import hu.balazsfalvi.pqclab.integration.core.PublisherTrust;
import hu.balazsfalvi.pqclab.integration.core.ReleaseEvent;
import hu.balazsfalvi.pqclab.json.DsseJsonCodec;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Creates bounded local fault fixtures while keeping all signing capabilities on the publisher. */
public final class ExperimentFixtures {
    private final SigningIdentity identity;
    private final PublisherTrust trusted;
    private final ReceiverProperties settings;
    private final Clock clock;
    private final StrictJson json = new StrictJson(DsseLimits.MAX_JSON_BYTES);
    private final DsseJsonCodec envelopes = new DsseJsonCodec();
    private final ReleaseJsonCodec events = new ReleaseJsonCodec();

    /**
     * Supplies publisher-only signing material and its configured public owner.
     *
     * @param identity the runtime-only publisher signing ports
     * @param trusted the corresponding public verification ports
     * @param settings the local receiver's fixed claim requirements
     * @param clock the trusted fixture clock
     */
    public ExperimentFixtures(
            SigningIdentity identity, PublisherTrust trusted, ReceiverProperties settings, Clock clock) {
        this.identity = identity;
        this.trusted = trusted;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Builds one controlled fixture from a publisher-authenticated synthetic baseline.
     *
     * @param request the strict scenario and envelope wrapper
     * @return the modified wire envelope, scenario and safe explanatory note
     */
    public byte[] create(byte[] request) {
        return trusted.read(snapshot -> create(request, snapshot));
    }

    /** Keeps baseline admission and fixture signing inside the same trust lease. */
    private byte[] create(byte[] request, PublisherTrust.Snapshot snapshot) {
        ExperimentScenario scenario;
        DsseEnvelope original;
        try {
            var root = json.read(request, Set.of("scenario", "envelope"));
            scenario = ExperimentScenario.valueOf(json.text(root, "scenario"));
            var input = root.get("envelope");
            if (input == null || !input.isObject()) {
                throw new IllegalArgumentException("An envelope object is required.");
            }
            original = envelopes.read(json.write((com.fasterxml.jackson.databind.node.ObjectNode) input));
        } catch (IllegalArgumentException failure) {
            throw new ApiFailure(400, "INVALID_EXPERIMENT_REQUEST");
        }
        List<SignatureAlgorithm> algorithms = authenticatedAlgorithms(original, snapshot);
        ReleaseEvent event = events.decode(original.payloadBytes());
        if (!identity.issuer().equals(event.issuer()) || !settings.audience().equals(event.audience())) {
            throw new ApiFailure(422, "INVALID_EXPERIMENT_BASELINE");
        }
        DsseEnvelope modified = switch (scenario) {
            case STRIP_PQC, UNSIGNED_DOWNGRADE -> stripPqc(original, snapshot);
            case TAMPER_PAYLOAD ->
                new DsseEnvelope(
                        original.payloadType(),
                        DsseBase64.encode(events.encode(
                                changed(event, event.issuedAt(), event.audience(), changedVersion(event)))),
                        original.signatures());
            case WRONG_KEY ->
                sign(event, algorithms, LocalTrust.generate(identity.issuer()).signingIdentity());
            case EXPIRED_EVENT ->
                sign(
                        changed(
                                event,
                                clock.instant().minus(settings.maximumAge()).minusSeconds(60),
                                event.audience(),
                                event.version()),
                        algorithms,
                        identity);
            case FUTURE_EVENT ->
                sign(
                        changed(
                                event,
                                clock.instant().plus(settings.futureTolerance()).plusSeconds(60),
                                event.audience(),
                                event.version()),
                        algorithms,
                        identity);
            case WRONG_AUDIENCE ->
                sign(
                        changed(event, event.issuedAt(), "urn:pqclab:other-receiver", event.version()),
                        algorithms,
                        identity);
            case CONFLICTING_ID ->
                sign(changed(event, event.issuedAt(), event.audience(), changedVersion(event)), algorithms, identity);
        };
        var wire = json.read(envelopes.write(modified), Set.of("payloadType", "payload", "signatures"));
        if (scenario == ExperimentScenario.UNSIGNED_DOWNGRADE) {
            wire.put("policy", "CLASSICAL_ONLY");
        }
        var response = json.object();
        response.put("scenario", scenario.name());
        response.put("note", note(scenario));
        response.set("envelope", wire);
        return json.write(response);
    }

    /** Admits a baseline only when at least one configured publisher key verifies its exact bytes. */
    private List<SignatureAlgorithm> authenticatedAlgorithms(DsseEnvelope envelope, PublisherTrust.Snapshot snapshot) {
        var verifier = new DsseVerifier();
        List<SignatureAlgorithm> algorithms = Arrays.stream(SignatureAlgorithm.values())
                .filter(algorithm -> verifier.verify(
                                envelope,
                                ReleaseEvent.PAYLOAD_TYPE,
                                snapshot.publishers().getFirst().verifiers().get(algorithm))
                        .isPresent())
                .toList();
        if (algorithms.isEmpty()) {
            throw new ApiFailure(422, "INVALID_EXPERIMENT_BASELINE");
        }
        return algorithms;
    }

    /** Selects removable entries by actual trusted verification, never by unauthenticated key hints. */
    private DsseEnvelope stripPqc(DsseEnvelope original, PublisherTrust.Snapshot snapshot) {
        var verifier = new DsseVerifier();
        var remaining = original.signatures().stream()
                .filter(entry -> verifier.verify(
                                new DsseEnvelope(original.payloadType(), original.payload(), List.of(entry)),
                                ReleaseEvent.PAYLOAD_TYPE,
                                snapshot.publishers().getFirst().verifiers().get(SignatureAlgorithm.ML_DSA_65))
                        .isEmpty())
                .toList();
        if (remaining.size() == original.signatures().size()) {
            throw new ApiFailure(422, "SCENARIO_NOT_APPLICABLE");
        }
        return new DsseEnvelope(original.payloadType(), original.payload(), remaining);
    }

    /** Retains the identifier while changing exactly the intended controlled business claim. */
    private ReleaseEvent changed(ReleaseEvent event, Instant time, String audience, String version) {
        return new ReleaseEvent(
                event.schemaVersion(),
                event.eventId(),
                event.issuer(),
                audience,
                time,
                event.artifact(),
                version,
                event.sha256());
    }

    /** Guarantees different valid version bytes without widening the bounded schema. */
    private String changedVersion(ReleaseEvent event) {
        return (event.version().startsWith("0") ? "1" : "0") + event.version().substring(1);
    }

    /** Uses platform-backed signing ports; no private key encoding leaves the publisher. */
    private DsseEnvelope sign(ReleaseEvent event, List<SignatureAlgorithm> algorithms, SigningIdentity signer) {
        return new DsseSigner().sign(ReleaseEvent.PAYLOAD_TYPE, events.encode(event), signer.signers(algorithms));
    }

    /** Explains the exact fixture operation without pretending it has already been delivered. */
    private String note(ExperimentScenario scenario) {
        return switch (scenario) {
            case STRIP_PQC -> "Removed entries verified by the trusted ML-DSA-65 key.";
            case TAMPER_PAYLOAD -> "Changed the release version while keeping the original signatures.";
            case WRONG_KEY -> "Signed the original payload with fresh keys outside the receiver's trust registry.";
            case EXPIRED_EVENT -> "Re-signed a timestamp older than the receiver's maximum age.";
            case FUTURE_EVENT -> "Re-signed a timestamp beyond the receiver's future tolerance.";
            case WRONG_AUDIENCE -> "Re-signed the event for a different receiver audience.";
            case UNSIGNED_DOWNGRADE -> "Removed the PQC signature and added an unauthenticated policy override.";
            case CONFLICTING_ID -> "Re-signed different release bytes under the same event identifier.";
        };
    }
}
