package hu.balazsfalvi.pqclab.integration.core;

import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import java.util.Map;
import java.util.Objects;

/**
 * Binds trusted verification ports to one publisher through out-of-band configuration.
 *
 * @param issuer the authenticated publisher identity
 * @param verifiers the trusted verifier for each supported algorithm
 */
public record TrustedPublisher(String issuer, Map<SignatureAlgorithm, MessageVerifier> verifiers) {
    /**
     * Requires a complete registry entry rather than silently treating missing configuration as failure.
     *
     * @param issuer the publisher's stable identifier
     * @param verifiers the complete trusted algorithm-to-key mapping
     */
    public TrustedPublisher {
        Objects.requireNonNull(issuer, "issuer");
        if (!issuer.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("A trusted publisher needs a bounded lowercase identifier.");
        }
        verifiers = Map.copyOf(verifiers);
        if (verifiers.size() != SignatureAlgorithm.values().length) {
            throw new IllegalArgumentException("Configure a trusted key for every supported algorithm.");
        }
    }
}
