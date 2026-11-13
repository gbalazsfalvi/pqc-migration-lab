package hu.balazsfalvi.pqclab.integration.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Captures the durable business result and its original acceptance evidence.
 *
 * @param receiptId the stable receiver-generated receipt identifier
 * @param event the accepted signed event
 * @param acceptedAt the receiver's acceptance time
 * @param policy the trusted policy used at first acceptance
 * @param evidence the observed signature results at first acceptance
 * @param payloadSha256 the fingerprint of the original verified bytes
 */
public record StoredRelease(
        UUID receiptId,
        ReleaseEvent event,
        Instant acceptedAt,
        MigrationPolicy policy,
        SignatureEvidence evidence,
        String payloadSha256) {
    /**
     * Requires a complete result before the persistence boundary can receive it.
     *
     * @param receiptId the generated receipt identifier
     * @param event the validated release event
     * @param acceptedAt the receiver's timestamp
     * @param policy the enforcing policy
     * @param evidence the signature outcomes
     * @param payloadSha256 the original payload fingerprint
     */
    public StoredRelease {
        Objects.requireNonNull(receiptId, "receiptId");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(evidence, "evidence");
        if (payloadSha256 == null || !payloadSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("A stored release requires a SHA-256 payload fingerprint.");
        }
    }
}
