package hu.balazsfalvi.pqclab.crypto;

import hu.balazsfalvi.pqclab.core.crypto.MessageSigner;
import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

/**
 * Binds one algorithm to one provider without changing the JVM's global provider registry.
 *
 * <p>The laboratory deliberately fixes a provider after selection so that cross-provider evidence
 * identifies the implementation actually exercised. Every operation creates fresh JCA primitives.
 */
public final class JcaSignatureEngine {
    private final SignatureAlgorithm algorithm;
    private final Provider provider;
    private final String jcaName;

    /** Creates an immutable binding after both the algorithm and provider have been selected. */
    private JcaSignatureEngine(SignatureAlgorithm algorithm, Provider provider) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.jcaName = algorithmName(algorithm);
    }

    /**
     * Resolves the normal JCA implementation once, then fixes that provider for the experiment.
     *
     * @param algorithm the signature family to exercise
     * @return an engine bound to the selected default provider
     * @throws CryptoOperationException if the runtime does not support the algorithm
     */
    public static JcaSignatureEngine usingDefaultProvider(SignatureAlgorithm algorithm) {
        try {
            return new JcaSignatureEngine(
                    algorithm, Signature.getInstance(algorithmName(algorithm)).getProvider());
        } catch (GeneralSecurityException failure) {
            throw new CryptoOperationException(
                    "The runtime does not support the requested signature algorithm.", failure);
        }
    }

    /**
     * Selects a provider instance directly, without registering it globally.
     *
     * @param algorithm the signature family to exercise
     * @param provider the provider under test
     * @return an engine bound to this provider instance
     */
    public static JcaSignatureEngine usingProvider(SignatureAlgorithm algorithm, Provider provider) {
        return new JcaSignatureEngine(algorithm, provider);
    }

    /**
     * Generates a new runtime-only key pair using the provider's secure random source.
     *
     * @return a fresh key pair that the caller must not log or persist in reports
     * @throws CryptoOperationException if key generation fails
     */
    public KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance(jcaName, provider).generateKeyPair();
        } catch (GeneralSecurityException failure) {
            throw new CryptoOperationException("Key generation failed.", failure);
        }
    }

    /**
     * Imports a public key's SubjectPublicKeyInfo encoding through this provider.
     *
     * @param encoded the X.509/SPKI bytes from another implementation
     * @return the provider's parsed public key
     * @throws CryptoOperationException if the key cannot be imported for this algorithm
     */
    public PublicKey importPublicKey(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            return KeyFactory.getInstance(jcaName, provider).generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException failure) {
            throw new CryptoOperationException("Public-key import failed.", failure);
        }
    }

    /**
     * Imports an ephemeral private key for the in-memory interoperability proof.
     *
     * @param encoded the unencrypted PKCS#8 bytes, never intended for report output
     * @return the provider's parsed private key
     * @throws CryptoOperationException if the key cannot be imported for this algorithm
     */
    public PrivateKey importPrivateKey(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            return KeyFactory.getInstance(jcaName, provider).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException failure) {
            throw new CryptoOperationException("Private-key import failed.", failure);
        }
    }

    /**
     * Creates a signing port bound to a supplied in-memory private key.
     *
     * @param keyId the optional DSSE hint, not an identity assertion
     * @param privateKey the key used to sign
     * @return a signer that creates fresh signature state for each invocation
     */
    public MessageSigner signer(String keyId, PrivateKey privateKey) {
        return new JcaMessageSigner(jcaName, provider, keyId, privateKey);
    }

    /**
     * Creates a verification port bound to a caller-selected public key.
     *
     * @param publicKey the public key trusted by the caller
     * @return a verifier that creates fresh signature state for each invocation
     */
    public MessageVerifier verifier(PublicKey publicKey) {
        return new JcaMessageVerifier(jcaName, provider, publicKey);
    }

    /**
     * Identifies the experiment's algorithm independently of provider naming.
     *
     * @return the signature family
     */
    public SignatureAlgorithm algorithm() {
        return algorithm;
    }

    /**
     * Reports the implementation used by this engine.
     *
     * @return the provider's registered name
     */
    public String providerName() {
        return provider.getName();
    }

    /**
     * Reports the provider version without using JCA's deprecated numeric version API.
     *
     * @return the provider's version string
     */
    public String providerVersion() {
        return provider.getVersionStr();
    }

    /** Maps the laboratory's fixed algorithm choices to standard JCA names. */
    private static String algorithmName(SignatureAlgorithm algorithm) {
        return switch (Objects.requireNonNull(algorithm, "algorithm")) {
            case ED25519 -> "Ed25519";
            case ML_DSA_65 -> "ML-DSA-65";
        };
    }
}
