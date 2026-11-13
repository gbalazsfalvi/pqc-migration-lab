package hu.balazsfalvi.pqclab.integration.app.publisher;

import hu.balazsfalvi.pqclab.core.crypto.MessageSigner;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Holds publisher-only signing ports; the receiver never receives this object. */
public final class SigningIdentity {
    private final String issuer;
    private final Supplier<Map<SignatureAlgorithm, MessageSigner>> signers;

    /**
     * Freezes a runtime-only signing identity without exposing a private-key accessor.
     *
     * @param issuer the publisher's trusted name
     * @param signers the complete signing ports
     */
    public SigningIdentity(String issuer, Map<SignatureAlgorithm, MessageSigner> signers) {
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        Map<SignatureAlgorithm, MessageSigner> fixed = Map.copyOf(signers);
        this.signers = () -> fixed;
        if (fixed.size() != SignatureAlgorithm.values().length) {
            throw new IllegalArgumentException("Configure both publisher signing algorithms.");
        }
    }

    /**
     * Resolves the active generation once for each complete signing operation.
     *
     * @param issuer the stable owner
     * @param signers a publisher-only supplier protected by the lifecycle read boundary
     */
    public SigningIdentity(String issuer, Supplier<Map<SignatureAlgorithm, MessageSigner>> signers) {
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.signers = Objects.requireNonNull(signers, "signers");
    }

    /**
     * Identifies the publisher whose claim is signed into every event.
     *
     * @return the stable issuer
     */
    public String issuer() {
        return issuer;
    }

    /**
     * Selects a signing port without exposing its underlying key material.
     *
     * @param algorithm the configured signature algorithm
     * @return the publisher-only signing port
     */
    public MessageSigner signer(SignatureAlgorithm algorithm) {
        return signers(List.of(algorithm)).getFirst();
    }

    /**
     * Snapshots all requested ports together, preventing mixed-generation dual signatures.
     *
     * @param algorithms the required algorithms in execution order
     * @return immutable signing ports from one active generation
     */
    public List<MessageSigner> signers(List<SignatureAlgorithm> algorithms) {
        Map<SignatureAlgorithm, MessageSigner> snapshot = signers.get();
        return algorithms.stream()
                .map(algorithm ->
                        Objects.requireNonNull(snapshot.get(algorithm), "No active signing key is available."))
                .toList();
    }
}
