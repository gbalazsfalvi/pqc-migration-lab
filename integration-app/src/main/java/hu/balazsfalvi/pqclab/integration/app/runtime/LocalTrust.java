package hu.balazsfalvi.pqclab.integration.app.runtime;

import hu.balazsfalvi.pqclab.core.crypto.MessageSigner;
import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.crypto.JcaSignatureEngine;
import hu.balazsfalvi.pqclab.integration.app.publisher.SigningIdentity;
import hu.balazsfalvi.pqclab.integration.core.TrustedPublisher;
import java.util.EnumMap;

/**
 * Separates publisher-only signing material from the public trust given to a local receiver.
 *
 * @param signingIdentity the runtime-only publisher signing ports
 * @param trustedPublisher the receiver's public verification ports and trusted owner
 */
public record LocalTrust(SigningIdentity signingIdentity, TrustedPublisher trustedPublisher) {
    /**
     * Generates fresh keys without persisting or transmitting private encodings.
     *
     * @param issuer the synthetic publisher name
     * @return separate private-signing and public-verification objects
     */
    public static LocalTrust generate(String issuer) {
        var signers = new EnumMap<SignatureAlgorithm, MessageSigner>(SignatureAlgorithm.class);
        var verifiers = new EnumMap<SignatureAlgorithm, MessageVerifier>(SignatureAlgorithm.class);
        for (SignatureAlgorithm algorithm : SignatureAlgorithm.values()) {
            JcaSignatureEngine engine = JcaSignatureEngine.usingDefaultProvider(algorithm);
            var pair = engine.generateKeyPair();
            signers.put(algorithm, engine.signer("local-" + algorithm.name(), pair.getPrivate()));
            // The receiver receives an independently imported public key, never the key pair.
            verifiers.put(
                    algorithm,
                    engine.verifier(engine.importPublicKey(pair.getPublic().getEncoded())));
        }
        return new LocalTrust(new SigningIdentity(issuer, signers), new TrustedPublisher(issuer, verifiers));
    }
}
