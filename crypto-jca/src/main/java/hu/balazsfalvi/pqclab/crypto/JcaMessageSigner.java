package hu.balazsfalvi.pqclab.crypto;

import hu.balazsfalvi.pqclab.core.crypto.MessageSigner;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Signature;
import java.util.Objects;

/** Keeps private key material behind the signing port and never renders it as text. */
final class JcaMessageSigner implements MessageSigner {
    private final String algorithm;
    private final Provider provider;
    private final String keyId;
    private final PrivateKey privateKey;

    /** Binds the signing operation without creating shared mutable Signature state. */
    JcaMessageSigner(String algorithm, Provider provider, String keyId, PrivateKey privateKey) {
        this.algorithm = algorithm;
        this.provider = provider;
        this.keyId = keyId == null ? "" : keyId;
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
    }

    /** {@inheritDoc} */
    @Override
    public String keyId() {
        return keyId;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] sign(byte[] message) {
        Objects.requireNonNull(message, "message");
        try {
            // Signature is stateful and not shared across callers or threads.
            Signature signature = Signature.getInstance(algorithm, provider);
            signature.initSign(privateKey);
            signature.update(message);
            return signature.sign();
        } catch (GeneralSecurityException failure) {
            throw new CryptoOperationException("Message signing failed.", failure);
        }
    }
}
