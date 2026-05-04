package hu.balazsfalvi.pqclab.integration.core;

import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseVerifier;
import hu.balazsfalvi.pqclab.core.dsse.VerifiedPayload;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/** Enforces algorithm requirements using keys grouped by their trusted publisher identity. */
public final class PolicyVerifier {
    private final List<TrustedPublisher> publishers;
    private final PublisherTrust liveTrust;
    private final DsseVerifier verifier = new DsseVerifier();

    /**
     * Freezes a small, unambiguous out-of-band trust registry.
     *
     * @param publishers the configured publishers, each with its own trusted algorithm keys
     */
    public PolicyVerifier(List<TrustedPublisher> publishers) {
        this.liveTrust = null;
        this.publishers = List.copyOf(publishers);
        var issuers = new HashSet<String>();
        if (publishers.isEmpty()
                || publishers.size() > 8
                || publishers.stream().anyMatch(publisher -> !issuers.add(publisher.issuer()))) {
            throw new IllegalArgumentException("Configure one to eight uniquely named trusted publishers.");
        }
    }

    /**
     * Binds verification to current receiver-owned trust without receiving private signing material.
     *
     * @param liveTrust the public registry and operation-scoped read boundary
     */
    public PolicyVerifier(PublisherTrust liveTrust) {
        this.liveTrust = Objects.requireNonNull(liveTrust, "liveTrust");
        this.publishers = List.of();
    }

    /**
     * Holds one trust revision across validation and any durable application.
     *
     * @param <T> the operation result
     * @param operation the receiver operation, using only this snapshot's verifier
     * @return the observed or committed result
     */
    public <T> T readCurrent(BiFunction<PolicyVerifier, Long, T> operation) {
        return liveTrust == null
                ? operation.apply(this, 0L)
                : liveTrust.read(
                        snapshot -> operation.apply(new PolicyVerifier(snapshot.publishers()), snapshot.revision()));
    }

    /**
     * Evaluates both algorithms without trusting key hints or unsigned policy fields.
     *
     * @param envelope the bounded immutable wire envelope
     * @param policy the receiver-owned requirement
     * @return an authenticated publisher and the original verified payload snapshot
     * @throws ReceptionFailure if the type or signatures fail the trusted requirement
     */
    public AuthenticatedPayload verify(DsseEnvelope envelope, MigrationPolicy policy) {
        if (liveTrust != null) return readCurrent((current, revision) -> current.verify(envelope, policy));
        Objects.requireNonNull(policy, "policy");
        if (!ReleaseEvent.PAYLOAD_TYPE.equals(envelope.payloadType())) {
            throw new ReceptionFailure(ReceptionFailure.Code.INVALID_PAYLOAD_TYPE);
        }
        for (TrustedPublisher publisher : publishers) {
            var ed = verifier.verify(
                    envelope, ReleaseEvent.PAYLOAD_TYPE, publisher.verifiers().get(SignatureAlgorithm.ED25519));
            var ml = verifier.verify(
                    envelope, ReleaseEvent.PAYLOAD_TYPE, publisher.verifiers().get(SignatureAlgorithm.ML_DSA_65));
            var evidence = new SignatureEvidence(ed.isPresent(), ml.isPresent());
            if (policy.accepts(evidence)) {
                return new AuthenticatedPayload(
                        publisher.issuer(), evidence, ed.or(() -> ml).orElseThrow());
            }
        }
        throw new ReceptionFailure(ReceptionFailure.Code.POLICY_REJECTED);
    }

    /**
     * Holds bytes and identity established by configured keys, before business claims are interpreted.
     *
     * @param issuer the trusted owner of the qualifying signatures
     * @param evidence the independent algorithm outcomes
     * @param payload the exact verified byte snapshot
     */
    public record AuthenticatedPayload(String issuer, SignatureEvidence evidence, VerifiedPayload payload) {}
}
