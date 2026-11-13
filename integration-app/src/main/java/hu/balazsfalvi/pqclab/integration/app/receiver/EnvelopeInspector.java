package hu.balazsfalvi.pqclab.integration.app.receiver;

import static hu.balazsfalvi.pqclab.integration.app.receiver.ComparisonResponse.StepStatus.*;

import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.PaeEncoder;
import hu.balazsfalvi.pqclab.integration.app.ledger.Sha256PayloadDigester;
import hu.balazsfalvi.pqclab.integration.app.runtime.ReceiverProperties;
import hu.balazsfalvi.pqclab.integration.core.MigrationEvaluator;
import hu.balazsfalvi.pqclab.integration.core.PolicyDecision;
import hu.balazsfalvi.pqclab.integration.core.PublisherTrust;
import hu.balazsfalvi.pqclab.integration.core.SignatureEvidence;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Adds public byte measurements and per-signature observations to real read-only policy decisions. */
public final class EnvelopeInspector {
    private static final List<String> STEP_IDS =
            List.of("envelope", "signatures", "schema", "issuer", "audience", "freshness");
    private static final List<String> LABELS = List.of(
            "Envelope contract",
            "Required signatures",
            "Release schema",
            "Publisher identity",
            "Receiver audience",
            "Freshness window");
    private static final List<String> SUCCESS = List.of(
            "The signed payload type matches this integration.",
            "Required signatures verify for one trusted publisher.",
            "The authenticated bytes match release-event v1.",
            "The signed issuer matches the trusted key owner.",
            "The event is addressed to this receiver.",
            "The signed time is within the receiver's accepted window.");
    private final MigrationEvaluator evaluator;
    private final PublisherTrust trust;
    private final ReceiverProperties settings;

    /**
     * Supplies only public verification and read-only validation capabilities.
     *
     * @param evaluator the framework-free policy comparator
     * @param trust the configured operation-scoped public directory
     * @param settings the unchanged receiver requirements
     */
    public EnvelopeInspector(MigrationEvaluator evaluator, PublisherTrust trust, ReceiverProperties settings) {
        this.evaluator = evaluator;
        this.trust = trust;
        this.settings = settings;
    }

    /**
     * Inspects actual cryptographic evidence without writing or reserving an event.
     *
     * @param envelope the bounded immutable wire envelope
     * @param wireBytes the actual received JSON byte count
     * @return independent policy decisions and public measurements
     */
    public ComparisonResponse inspect(DsseEnvelope envelope, int wireBytes) {
        return trust.read(snapshot -> inspect(envelope, wireBytes, snapshot));
    }

    /** Uses one public snapshot for per-entry observations and all four policy decisions. */
    private ComparisonResponse inspect(DsseEnvelope envelope, int wireBytes, PublisherTrust.Snapshot snapshot) {
        var publisher = snapshot.publishers().getFirst();
        byte[] body = envelope.payloadBytes();
        byte[] frame = new PaeEncoder().encode(envelope.payloadType(), body);
        var signatures = new ArrayList<ComparisonResponse.SignatureObservation>();
        for (int index = 0; index < envelope.signatures().size(); index++) {
            var entry = envelope.signatures().get(index);
            byte[] signature = entry.signatureBytes();
            List<SignatureAlgorithm> verified = Arrays.stream(SignatureAlgorithm.values())
                    .filter(algorithm -> publisher.verifiers().get(algorithm).verify(frame, signature))
                    .toList();
            signatures.add(
                    new ComparisonResponse.SignatureObservation(index, entry.keyId(), signature.length, verified));
        }
        var evidence = new SignatureEvidence(
                signatures.stream().anyMatch(entry -> entry.verifiedAlgorithms().contains(SignatureAlgorithm.ED25519)),
                signatures.stream()
                        .anyMatch(entry -> entry.verifiedAlgorithms().contains(SignatureAlgorithm.ML_DSA_65)));
        var evaluation = evaluator.evaluate(envelope, snapshot);
        return new ComparisonResponse(
                evaluation.observedAt(),
                settings.policy(),
                new Sha256PayloadDigester().digest(body),
                wireBytes,
                body.length,
                frame.length,
                evidence,
                List.copyOf(signatures),
                evaluation.decisions().stream().map(this::observation).toList(),
                snapshot.revision());
    }

    /** Converts an actual domain decision into ordered explanatory stages. */
    private ComparisonResponse.PolicyObservation observation(PolicyDecision decision) {
        int failure = decision.accepted()
                ? STEP_IDS.size()
                : switch (decision.rejectionCode()) {
                    case INVALID_PAYLOAD_TYPE -> 0;
                    case POLICY_REJECTED -> 1;
                    case MALFORMED_EVENT -> 2;
                    case ISSUER_MISMATCH -> 3;
                    case AUDIENCE_MISMATCH -> 4;
                    case EVENT_EXPIRED, EVENT_IN_FUTURE -> 5;
                    case EVENT_ID_CONFLICT ->
                        throw new IllegalStateException("Read-only evaluation cannot reserve event IDs.");
                };
        var steps = new ArrayList<ComparisonResponse.ValidationStep>();
        for (int index = 0; index < STEP_IDS.size(); index++) {
            var status = index < failure ? PASSED : index == failure ? FAILED : NOT_CHECKED;
            String detail = status == PASSED
                    ? SUCCESS.get(index)
                    : status == FAILED ? failureDetail(decision) : "Not checked after an earlier policy rejection.";
            steps.add(new ComparisonResponse.ValidationStep(STEP_IDS.get(index), LABELS.get(index), status, detail));
        }
        return new ComparisonResponse.PolicyObservation(
                decision.policy(), decision.accepted(), decision.rejectionCode(), List.copyOf(steps));
    }

    /** Explains only server-owned rejection categories, never parser or provider input fragments. */
    private String failureDetail(PolicyDecision decision) {
        return switch (decision.rejectionCode()) {
            case POLICY_REJECTED ->
                switch (decision.policy()) {
                    case CLASSICAL_ONLY, SHADOW_PQC -> "A valid signature from the trusted Ed25519 key is required.";
                    case BOTH_REQUIRED -> "Both algorithms must verify for the same trusted publisher.";
                    case PQC_REQUIRED -> "A valid signature from the trusted ML-DSA-65 key is required.";
                };
            case INVALID_PAYLOAD_TYPE -> "This payload type is outside the receiver's release-event contract.";
            case MALFORMED_EVENT -> "The authenticated payload does not match the strict release-event schema.";
            case ISSUER_MISMATCH -> "The signed issuer differs from the trusted owner of the signing keys.";
            case AUDIENCE_MISMATCH -> "The signed audience identifies a different receiver.";
            case EVENT_EXPIRED -> "The signed event is older than the receiver's maximum age.";
            case EVENT_IN_FUTURE -> "The signed event exceeds the receiver's future clock tolerance.";
            case EVENT_ID_CONFLICT ->
                throw new IllegalStateException("Read-only evaluation cannot observe ledger conflicts.");
        };
    }
}
