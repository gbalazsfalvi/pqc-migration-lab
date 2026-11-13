package hu.balazsfalvi.pqclab.crypto;

import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Objects;

/** Distinguishes invalid signature bytes from a broken provider or trusted-key configuration. */
final class JcaMessageVerifier implements MessageVerifier {
    private final String algorithm;
    private final Provider provider;
    private final PublicKey publicKey;

    /** Binds verification to a public key selected outside the untrusted envelope. */
    JcaMessageVerifier(String algorithm, Provider provider, PublicKey publicKey) {
        this.algorithm = algorithm;
        this.provider = provider;
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
    }

    /** {@inheritDoc} */
    @Override
    public boolean verify(byte[] message, byte[] candidate) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(candidate, "candidate");
        Signature signature;
        try {
            signature = Signature.getInstance(algorithm, provider);
            signature.initVerify(publicKey);
            signature.update(message);
        } catch (GeneralSecurityException failure) {
            throw new CryptoOperationException("Signature verification could not be initialised.", failure);
        }
        try {
            return signature.verify(candidate);
        } catch (SignatureException malformedSignature) {
            // Some providers signal malformed signature encodings with an exception, not false.
            return false;
        }
    }
}
