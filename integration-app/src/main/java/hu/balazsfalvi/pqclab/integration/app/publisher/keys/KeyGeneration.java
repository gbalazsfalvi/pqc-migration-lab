package hu.balazsfalvi.pqclab.integration.app.publisher.keys;

import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.crypto.JcaSignatureEngine;
import hu.balazsfalvi.pqclab.integration.app.trust.KeyRegistryView;
import hu.balazsfalvi.pqclab.integration.app.trust.PublicGeneration;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps one publisher-only generation; this type never enters the receiver context or a response.
 *
 * @param id the server-generated UUID
 * @param sequence the increasing generation number
 * @param createdAt the creation time
 * @param retiringAt the rotation time, otherwise null
 * @param acceptUntil the exclusive verification deadline, otherwise null
 * @param revokedAt the irreversible revocation time, otherwise null
 * @param pairs both private/public algorithm pairs
 */
record KeyGeneration(
        String id,
        int sequence,
        Instant createdAt,
        Instant retiringAt,
        Instant acceptUntil,
        Instant revokedAt,
        Map<SignatureAlgorithm, KeyPair> pairs) {
    /** Validates complete capabilities and consistent lifecycle timestamps. */
    KeyGeneration {
        if (!UUID.fromString(id).toString().equals(id)
                || sequence < 1
                || createdAt == null
                || (retiringAt == null) != (acceptUntil == null)
                || (retiringAt != null && (retiringAt.isBefore(createdAt) || !acceptUntil.isAfter(retiringAt)))
                || (revokedAt != null && revokedAt.isBefore(createdAt))) {
            throw new IllegalArgumentException("Invalid key generation metadata.");
        }
        pairs = Map.copyOf(pairs);
        if (pairs.size() != SignatureAlgorithm.values().length)
            throw new IllegalArgumentException("Both keys are required.");
    }

    /** Generates native keys with the provider's secure randomness. */
    static KeyGeneration generate(int sequence, Instant now) {
        var pairs = new EnumMap<SignatureAlgorithm, KeyPair>(SignatureAlgorithm.class);
        for (var algorithm : SignatureAlgorithm.values()) {
            pairs.put(
                    algorithm,
                    JcaSignatureEngine.usingDefaultProvider(algorithm).generateKeyPair());
        }
        return new KeyGeneration(UUID.randomUUID().toString(), sequence, now, null, null, null, pairs);
    }

    /** Starts a bounded overlap while preserving any earlier irreversible revocation. */
    KeyGeneration retire(Instant now, long graceSeconds) {
        return new KeyGeneration(id, sequence, createdAt, now, now.plusSeconds(graceSeconds), revokedAt, publicPairs());
    }

    /** Makes revocation irreversible in the next durable state. */
    KeyGeneration revoke(Instant now) {
        return new KeyGeneration(id, sequence, createdAt, retiringAt, acceptUntil, now, publicPairs());
    }

    /** Drops obsolete private capabilities from every newly persisted state while preserving verification. */
    private Map<SignatureAlgorithm, KeyPair> publicPairs() {
        var result = new EnumMap<SignatureAlgorithm, KeyPair>(SignatureAlgorithm.class);
        pairs.forEach((algorithm, pair) -> result.put(algorithm, new KeyPair(pair.getPublic(), null)));
        return result;
    }

    /** Uses configured IDs for display while verification continues to ignore unauthenticated wire hints. */
    String keyId(SignatureAlgorithm algorithm) {
        return id + ":" + algorithm.name();
    }

    /** Independently imports public keys before crossing the publisher/receiver capability boundary. */
    PublicGeneration publicOnly() {
        var verifiers = new EnumMap<SignatureAlgorithm, hu.balazsfalvi.pqclab.core.crypto.MessageVerifier>(
                SignatureAlgorithm.class);
        var keys = new ArrayList<KeyRegistryView.Key>();
        for (var algorithm : SignatureAlgorithm.values()) {
            var engine = JcaSignatureEngine.usingDefaultProvider(algorithm);
            byte[] spki = pairs.get(algorithm).getPublic().getEncoded();
            verifiers.put(algorithm, engine.verifier(engine.importPublicKey(spki)));
            keys.add(new KeyRegistryView.Key(keyId(algorithm), algorithm.name(), fingerprint(spki), spki.length));
        }
        return new PublicGeneration(id, sequence, createdAt, retiringAt, acceptUntil, revokedAt, verifiers, keys);
    }

    /** Hashes public SPKI bytes using platform cryptography. */
    static String fingerprint(byte[] spki) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(spki));
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("Public-key fingerprinting is unavailable.", failure);
        }
    }

    /** Prevents accidental diagnostics from rendering runtime key objects. */
    @Override
    public String toString() {
        return "KeyGeneration[id=" + id + ", sequence=" + sequence + ", keyMaterial=REDACTED]";
    }
}
