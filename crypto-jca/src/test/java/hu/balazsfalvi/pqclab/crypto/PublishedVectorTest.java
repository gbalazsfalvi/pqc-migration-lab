package hu.balazsfalvi.pqclab.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Provider;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jcajce.spec.MLDSAPublicKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks independent published answers, rather than relying only on self-generated round trips. */
final class PublishedVectorTest {
    private static final Provider BOUNCY_CASTLE = new BouncyCastleProvider();
    private static final HexFormat HEX = HexFormat.of();

    /** Verifies RFC 8032 section 7.1, TEST 1, with both implementations. */
    @Test
    void verifiesRfc8032EmptyMessageVector() throws Exception {
        byte[] publicKey = HEX.parseHex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] signature = HEX.parseHex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
                + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");
        // ASN.1 is delegated to a library; the fixture contains only the RFC's raw public key.
        byte[] spki = new SubjectPublicKeyInfo(
                        new AlgorithmIdentifier(new ASN1ObjectIdentifier("1.3.101.112")), publicKey)
                .getEncoded();
        for (JcaSignatureEngine engine : engines(SignatureAlgorithm.ED25519)) {
            assertTrue(engine.verifier(engine.importPublicKey(spki)).verify(new byte[0], signature));
        }
    }

    /** Checks two valid signatures and one invalid signature from the pinned NIST ACVP corpus. */
    @ParameterizedTest
    @ValueSource(strings = {"ml-dsa-65-valid-3", "ml-dsa-65-valid-15", "ml-dsa-65-invalid"})
    void verifiesNistAcvpPublicFixtures(String name) throws Exception {
        Properties vector = readVector(name);
        byte[] rawPublicKey = HEX.parseHex(vector.getProperty("publicKey"));
        byte[] message = HEX.parseHex(vector.getProperty("message"));
        byte[] signature = HEX.parseHex(vector.getProperty("signature"));
        // Only the wire-format conversion uses BC. Each provider independently verifies the answer.
        byte[] spki = KeyFactory.getInstance("ML-DSA-65", BOUNCY_CASTLE)
                .generatePublic(new MLDSAPublicKeySpec(MLDSAParameterSpec.ml_dsa_65, rawPublicKey))
                .getEncoded();
        boolean expected = Boolean.parseBoolean(vector.getProperty("expected"));
        for (JcaSignatureEngine engine : engines(SignatureAlgorithm.ML_DSA_65)) {
            assertEquals(
                    expected,
                    engine.verifier(engine.importPublicKey(spki)).verify(message, signature),
                    () -> name + " with " + engine.providerName());
        }
    }

    /** Loads only the checked-in, public verification fields of a published fixture. */
    private static Properties readVector(String name) throws Exception {
        try (InputStream input = PublishedVectorTest.class.getResourceAsStream("/vectors/" + name + ".properties")) {
            assertNotNull(input, "A pinned public fixture must be present.");
            Properties vector = new Properties();
            vector.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return vector;
        }
    }

    /** Supplies the independent implementations whose answers must agree with the publication. */
    private static List<JcaSignatureEngine> engines(SignatureAlgorithm algorithm) {
        return List.of(
                JcaSignatureEngine.usingDefaultProvider(algorithm),
                JcaSignatureEngine.usingProvider(algorithm, BOUNCY_CASTLE));
    }
}
