package hu.balazsfalvi.pqclab.integration.app.trust;

import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import java.time.Instant;
import java.util.Map;

/**
 * Holds only public capabilities and lifecycle timestamps imported by trusted bootstrap code.
 *
 * @param id the configured generation identifier
 * @param sequence the increasing generation number
 * @param createdAt the creation time
 * @param retiringAt the rotation time, otherwise null
 * @param acceptUntil the exclusive overlap deadline, otherwise null
 * @param revokedAt the revocation time, otherwise null
 * @param verifiers the independent public verification ports
 * @param keys public identifiers and fingerprints
 */
public record PublicGeneration(
        String id,
        int sequence,
        Instant createdAt,
        Instant retiringAt,
        Instant acceptUntil,
        Instant revokedAt,
        Map<SignatureAlgorithm, MessageVerifier> verifiers,
        java.util.List<KeyRegistryView.Key> keys) {
    /** Snapshots public-only capabilities before crossing into the receiver graph. */
    public PublicGeneration {
        verifiers = Map.copyOf(verifiers);
        keys = java.util.List.copyOf(keys);
    }

    /**
     * Evaluates the effective lifecycle state from trusted stored timestamps.
     *
     * @param now the operation's single trusted clock snapshot
     * @return the public state label
     */
    public String stateAt(Instant now) {
        if (revokedAt != null) return "REVOKED";
        if (acceptUntil == null) return "ACTIVE";
        return now.isBefore(acceptUntil) ? "RETIRING" : "RETIRED";
    }

    /**
     * Allows verification only while active or within the explicit overlap.
     *
     * @param now the operation observation time
     * @return whether this generation remains trusted
     */
    public boolean trustedAt(Instant now) {
        return revokedAt == null && (acceptUntil == null || now.isBefore(acceptUntil));
    }
}
