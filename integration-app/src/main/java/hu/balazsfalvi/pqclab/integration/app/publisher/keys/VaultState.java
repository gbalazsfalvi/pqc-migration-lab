package hu.balazsfalvi.pqclab.integration.app.publisher.keys;

import hu.balazsfalvi.pqclab.integration.app.trust.RotatingPublicDirectory;
import java.util.HashSet;
import java.util.List;

/**
 * Represents authenticated publisher-only durable state.
 *
 * @param issuer the configured stable owner
 * @param revision the monotonic local revision
 * @param activeId the selected generation, which may be revoked until recovery rotation
 * @param generations immutable bounded history
 */
record VaultState(String issuer, long revision, String activeId, List<KeyGeneration> generations) {
    /** Rejects ambiguous ownership, identifiers, counters or duplicate public keys. */
    VaultState {
        generations = List.copyOf(generations);
        if (!issuer.matches("[a-z][a-z0-9-]{0,63}")
                || revision < 1
                || revision > 1_000_000
                || generations.isEmpty()
                || generations.size() > RotatingPublicDirectory.MAXIMUM_GENERATIONS) {
            throw new IllegalArgumentException("Invalid bounded key registry.");
        }
        var identifiers = new HashSet<String>();
        var fingerprints = new HashSet<String>();
        int sequence = 0;
        for (var generation : generations) {
            if (generation.sequence() != ++sequence || !identifiers.add(generation.id())) {
                throw new IllegalArgumentException("Key generations must be unique and ordered.");
            }
            for (var key : generation.publicOnly().keys()) {
                if (!fingerprints.add(key.spkiSha256()))
                    throw new IllegalArgumentException("Public keys cannot be reintroduced under a new ID.");
            }
            boolean needsPrivate = generation.id().equals(activeId) && generation.revokedAt() == null;
            if (generation.pairs().values().stream().anyMatch(pair -> (pair.getPrivate() != null) != needsPrivate)) {
                throw new IllegalArgumentException("Only an unrevoked active generation may retain private keys.");
            }
        }
        if (!generations.getLast().id().equals(activeId)
                || generations.getLast().retiringAt() != null
                || generations.stream()
                        .limit(generations.size() - 1L)
                        .anyMatch(generation -> generation.retiringAt() == null)) {
            throw new IllegalArgumentException("Only the newest generation may be selected for signing.");
        }
    }

    /** Returns the publisher's selected generation without exposing it to receiver code. */
    KeyGeneration active() {
        return generations.getLast();
    }

    /** Prevents accidental secret-bearing diagnostic output. */
    @Override
    public String toString() {
        return "VaultState[issuer=" + issuer + ", revision=" + revision + ", keyMaterial=REDACTED]";
    }
}
