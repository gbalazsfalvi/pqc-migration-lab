package hu.balazsfalvi.pqclab.integration.app.trust;

import java.util.List;

/**
 * Exposes public lifecycle metadata without key encodings or publisher signing capabilities.
 *
 * @param managed whether this runtime has durable managed keys
 * @param revision the durable registry revision
 * @param issuer the configured public owner
 * @param activeGenerationId the generation selected for new signing
 * @param observedAt the receiver's UTC observation time
 * @param gracePeriodSeconds the fixed overlap duration for a rotation
 * @param maximumGenerations the bounded local registry capacity
 * @param generations immutable public generations, newest first
 */
public record KeyRegistryView(
        boolean managed,
        long revision,
        String issuer,
        String activeGenerationId,
        String observedAt,
        long gracePeriodSeconds,
        int maximumGenerations,
        List<Generation> generations) {
    /** Snapshots all public rows. */
    public KeyRegistryView {
        generations = List.copyOf(generations);
    }

    /**
     * Describes one complete Ed25519 and ML-DSA-65 generation.
     *
     * @param id the server-assigned generation UUID
     * @param sequence the increasing generation number
     * @param state ACTIVE, RETIRING, RETIRED or REVOKED
     * @param createdAt the generation creation time
     * @param retiringAt the time new signing moved to a successor, otherwise null
     * @param acceptUntil the exclusive trust deadline, otherwise null
     * @param revokedAt the irreversible revocation time, otherwise null
     * @param keys the public identities of both keys
     */
    public record Generation(
            String id,
            int sequence,
            String state,
            String createdAt,
            String retiringAt,
            String acceptUntil,
            String revokedAt,
            List<Key> keys) {
        /** Snapshots public key descriptions. */
        public Generation {
            keys = List.copyOf(keys);
        }
    }

    /**
     * Identifies a public key by its actual SubjectPublicKeyInfo fingerprint.
     *
     * @param id the configured key identifier, never a wire hint used as trust
     * @param algorithm the algorithm name
     * @param spkiSha256 the public SPKI SHA-256 fingerprint
     * @param spkiBytes the public encoding's actual byte length
     */
    public record Key(String id, String algorithm, String spkiSha256, int spkiBytes) {}
}
