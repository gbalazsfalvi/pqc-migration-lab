package hu.balazsfalvi.pqclab.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.balazsfalvi.pqclab.core.crypto.MessageSigner;
import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Exercises real signatures, imported keys, rejection paths, and concurrent adapter reuse. */
final class JcaSignatureEngineTest {
    private static final byte[] MESSAGE = "A synthetic release manifest — version 1".getBytes(StandardCharsets.UTF_8);
    private static final Provider BOUNCY_CASTLE = new BouncyCastleProvider();

    /** Confirms that both public and private key encodings travel in both directions. */
    @ParameterizedTest
    @EnumSource(SignatureAlgorithm.class)
    void exchangesSignaturesAndKeysInBothDirections(SignatureAlgorithm algorithm) {
        JcaSignatureEngine nativeEngine = JcaSignatureEngine.usingDefaultProvider(algorithm);
        JcaSignatureEngine bcEngine = JcaSignatureEngine.usingProvider(algorithm, BOUNCY_CASTLE);
        assertInterop(nativeEngine, bcEngine);
        assertInterop(bcEngine, nativeEngine);
        assertEquals(algorithm, nativeEngine.algorithm());
        assertEquals("BC", bcEngine.providerName());
        assertFalse(nativeEngine.providerVersion().isBlank());
        assertFalse(bcEngine.providerVersion().isBlank());
    }

    /** Requires cryptographic rejection for changed data, wrong keys, and malformed signatures. */
    @ParameterizedTest
    @EnumSource(SignatureAlgorithm.class)
    void rejectsTamperingAndUntrustedKeys(SignatureAlgorithm algorithm) {
        for (JcaSignatureEngine engine : engines(algorithm)) {
            KeyPair pair = engine.generateKeyPair();
            byte[] signature = engine.signer("local-key", pair.getPrivate()).sign(MESSAGE);
            MessageVerifier verifier = engine.verifier(pair.getPublic());
            assertTrue(verifier.verify(MESSAGE, signature));
            byte[] changedMessage = MESSAGE.clone();
            changedMessage[0] ^= 1;
            byte[] changedSignature = signature.clone();
            changedSignature[0] ^= 1;
            assertFalse(verifier.verify(changedMessage, signature));
            assertFalse(verifier.verify(MESSAGE, changedSignature));
            assertFalse(verifier.verify(MESSAGE, new byte[0]));
            assertFalse(verifier.verify(MESSAGE, Arrays.copyOf(signature, signature.length - 1)));
            assertFalse(engine.verifier(engine.generateKeyPair().getPublic()).verify(MESSAGE, signature));
        }
    }

    /** Distinguishes operational key errors from an ordinary invalid signature result. */
    @ParameterizedTest
    @EnumSource(SignatureAlgorithm.class)
    void reportsInvalidKeyMaterialAsAnOperationalFailure(SignatureAlgorithm algorithm) {
        JcaSignatureEngine engine = JcaSignatureEngine.usingDefaultProvider(algorithm);
        assertThrows(CryptoOperationException.class, () -> engine.importPublicKey(new byte[] {1, 2, 3}));
        assertThrows(CryptoOperationException.class, () -> engine.importPrivateKey(new byte[] {1, 2, 3}));
        SignatureAlgorithm other =
                algorithm == SignatureAlgorithm.ED25519 ? SignatureAlgorithm.ML_DSA_65 : SignatureAlgorithm.ED25519;
        KeyPair wrongFamily = JcaSignatureEngine.usingDefaultProvider(other).generateKeyPair();
        assertThrows(
                CryptoOperationException.class,
                () -> engine.signer("wrong-family", wrongFamily.getPrivate()).sign(MESSAGE));
        assertThrows(
                CryptoOperationException.class,
                () -> engine.verifier(wrongFamily.getPublic()).verify(MESSAGE, new byte[] {1}));
    }

    /** Proves that shared port instances do not share mutable JCA signature state. */
    @ParameterizedTest
    @EnumSource(SignatureAlgorithm.class)
    void supportsConcurrentReuse(SignatureAlgorithm algorithm) throws Exception {
        for (JcaSignatureEngine engine : engines(algorithm)) {
            KeyPair pair = engine.generateKeyPair();
            MessageSigner signer = engine.signer("concurrent", pair.getPrivate());
            MessageVerifier verifier = engine.verifier(pair.getPublic());
            Callable<Boolean> operation = () -> verifier.verify(MESSAGE, signer.sign(MESSAGE));
            try (var executor = Executors.newFixedThreadPool(4)) {
                for (var result : executor.invokeAll(java.util.Collections.nCopies(12, operation))) {
                    assertTrue(result.get());
                }
            }
        }
    }

    /** Leaves global provider ordering unchanged, including when a provider lacks the algorithm. */
    @Test
    void doesNotRegisterProvidersOrHideMissingAlgorithms() {
        Provider[] before = Security.getProviders();
        JcaSignatureEngine engine = JcaSignatureEngine.usingProvider(SignatureAlgorithm.ED25519, BOUNCY_CASTLE);
        engine.generateKeyPair();
        assertArrayEquals(before, Security.getProviders());
        Provider emptyProvider = new Provider("EmptyLabProvider", "1", "No algorithms for this test") {
            private static final long serialVersionUID = 1L;
        };
        assertThrows(
                CryptoOperationException.class,
                () -> JcaSignatureEngine.usingProvider(SignatureAlgorithm.ED25519, emptyProvider)
                        .generateKeyPair());
    }

    /** Tests one directed edge of the provider interoperability matrix. */
    private static void assertInterop(JcaSignatureEngine source, JcaSignatureEngine target) {
        KeyPair pair = source.generateKeyPair();
        byte[] signature = source.signer("runtime-only", pair.getPrivate()).sign(MESSAGE);
        assertEquals("X.509", pair.getPublic().getFormat());
        assertEquals("PKCS#8", pair.getPrivate().getFormat());
        assertEquals(source.algorithm() == SignatureAlgorithm.ED25519 ? 64 : 3309, signature.length);
        assertTrue(source.verifier(pair.getPublic()).verify(MESSAGE, signature));
        assertTrue(target.verifier(target.importPublicKey(pair.getPublic().getEncoded()))
                .verify(MESSAGE, signature));
        byte[] importedPrivateSignature = target.signer(
                        "imported", target.importPrivateKey(pair.getPrivate().getEncoded()))
                .sign(MESSAGE);
        assertTrue(source.verifier(pair.getPublic()).verify(MESSAGE, importedPrivateSignature));
    }

    /** Supplies the two independent implementations under test. */
    private static List<JcaSignatureEngine> engines(SignatureAlgorithm algorithm) {
        return List.of(
                JcaSignatureEngine.usingDefaultProvider(algorithm),
                JcaSignatureEngine.usingProvider(algorithm, BOUNCY_CASTLE));
    }
}
