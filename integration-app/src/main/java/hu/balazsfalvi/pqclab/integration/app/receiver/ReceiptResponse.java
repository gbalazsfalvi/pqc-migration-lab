package hu.balazsfalvi.pqclab.integration.app.receiver;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import hu.balazsfalvi.pqclab.integration.core.SignatureEvidence;
import hu.balazsfalvi.pqclab.integration.core.StoredRelease;
import java.time.Instant;
import java.util.UUID;

/**
 * Exposes a business receipt while clearly separating current enforcement from original audit data.
 *
 * @param status whether this delivery created or reused a business result
 * @param receiptId the stable persisted receipt identifier
 * @param eventId the signed event identifier
 * @param issuer the authenticated publisher
 * @param artifact the accepted artifact label
 * @param version the accepted release version
 * @param acceptedAt the first acceptance time
 * @param enforcedPolicy the receiver policy applied to this delivery
 * @param acceptedPolicy the policy used when the result was first stored
 * @param acceptedEvidence the immutable first-acceptance signature results
 */
public record ReceiptResponse(
        String status,
        UUID receiptId,
        UUID eventId,
        String issuer,
        String artifact,
        String version,
        Instant acceptedAt,
        MigrationPolicy enforcedPolicy,
        MigrationPolicy acceptedPolicy,
        SignatureEvidence acceptedEvidence) {
    /**
     * Maps a stored result without exposing database entities or unverified input.
     *
     * @param status the delivery outcome
     * @param release the original persisted result
     * @param currentPolicy the policy enforced for this request
     * @return the public receipt
     */
    public static ReceiptResponse of(String status, StoredRelease release, MigrationPolicy currentPolicy) {
        return new ReceiptResponse(
                status,
                release.receiptId(),
                release.event().eventId(),
                release.event().issuer(),
                release.event().artifact(),
                release.event().version(),
                release.acceptedAt(),
                currentPolicy,
                release.policy(),
                release.evidence());
    }
}
