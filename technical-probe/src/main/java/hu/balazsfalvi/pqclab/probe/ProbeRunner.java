package hu.balazsfalvi.pqclab.probe;

import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.core.dsse.DsseBase64;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseSignature;
import hu.balazsfalvi.pqclab.core.dsse.DsseSigner;
import hu.balazsfalvi.pqclab.core.dsse.DsseVerifier;
import hu.balazsfalvi.pqclab.crypto.JcaSignatureEngine;
import hu.balazsfalvi.pqclab.json.DsseJsonCodec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** Runs real cryptographic experiments using synthetic data and runtime-only keys. */
public final class ProbeRunner {
    private static final String PAYLOAD_TYPE = "application/vnd.pqclab.release-manifest.v1+json";
    private static final byte[] PAYLOAD = """
            {
              "artifact": "synthetic-service",
              "version": "1.0.0",
              "environment": "local-laboratory"
            }
            """.getBytes(StandardCharsets.UTF_8);
    private final Clock clock;

    /**
     * Supplies time explicitly so generated evidence can be tested without waiting.
     *
     * @param clock the clock used for the report timestamp
     */
    public ProbeRunner(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Executes the provider matrix, rejection checks and DSSE wire-format experiments.
     *
     * @return immutable evidence, including failed expectations rather than silently omitting them
     */
    public ProbeReport run() {
        Provider[] originalProviders = Security.getProviders();
        Provider bc = new BouncyCastleProvider();
        List<ProbeCheck> checks = new ArrayList<>();
        for (SignatureAlgorithm algorithm : SignatureAlgorithm.values()) {
            try {
                runAlgorithm(algorithm, bc, checks);
            } catch (RuntimeException failure) {
                checks.add(failure(
                        algorithm.name().toLowerCase(Locale.ROOT) + ".setup", "Algorithm setup completes", failure));
            }
        }
        try {
            runDualEnvelope(bc, checks);
        } catch (RuntimeException failure) {
            checks.add(failure("dsse.dual.setup", "Dual-signature setup completes", failure));
        }
        check(
                checks,
                "providers.unchanged",
                "Global provider registration and ordering stay unchanged",
                Map.of(),
                () -> Arrays.equals(originalProviders, Security.getProviders()));
        return new ProbeReport(
                clock.instant(),
                Map.of(
                        "javaVersion", System.getProperty("java.runtime.version"),
                        "javaVendor", System.getProperty("java.vendor"),
                        "os", System.getProperty("os.name") + " / " + System.getProperty("os.arch"),
                        "bouncyCastleVersion", bc.getVersionStr(),
                        "profile", "DSSE v1 / pure ML-DSA-65 / empty ML-DSA context"),
                checks);
    }

    /** Exercises four signing/verifying combinations and both directions of private-key import. */
    private void runAlgorithm(SignatureAlgorithm algorithm, Provider bc, List<ProbeCheck> checks) {
        JcaSignatureEngine jdk = JcaSignatureEngine.usingDefaultProvider(algorithm);
        JcaSignatureEngine alternative = JcaSignatureEngine.usingProvider(algorithm, bc);
        List<JcaSignatureEngine> engines = List.of(jdk, alternative);
        String prefix = algorithm.name().toLowerCase(Locale.ROOT);
        for (int sourceIndex = 0; sourceIndex < engines.size(); sourceIndex++) {
            JcaSignatureEngine source = engines.get(sourceIndex);
            JcaSignatureEngine other = engines.get(1 - sourceIndex);
            String sourceLabel = sourceIndex == 0 ? "jdk" : "bc";
            KeyPair pair = source.generateKeyPair();
            byte[] signature =
                    source.signer("ephemeral-" + sourceLabel, pair.getPrivate()).sign(PAYLOAD);
            for (int targetIndex = 0; targetIndex < engines.size(); targetIndex++) {
                JcaSignatureEngine target = engines.get(targetIndex);
                String edge = sourceLabel + "-to-" + (targetIndex == 0 ? "jdk" : "bc");
                Map<String, String> evidence = metadata(source, target, pair, signature);
                check(
                        checks,
                        prefix + ".signature." + edge,
                        "Imported SPKI key verifies the signature",
                        evidence,
                        () -> target.verifier(
                                        target.importPublicKey(pair.getPublic().getEncoded()))
                                .verify(PAYLOAD, signature));
                runDsse(prefix + ".dsse." + edge, source, target, pair, checks);
            }
            check(
                    checks,
                    prefix + ".pkcs8." + sourceLabel,
                    "Imported PKCS#8 key signs interoperably",
                    metadata(source, other, pair, signature),
                    () -> {
                        byte[] importedSignature = other.signer(
                                        "ephemeral-import",
                                        other.importPrivateKey(pair.getPrivate().getEncoded()))
                                .sign(PAYLOAD);
                        return source.verifier(pair.getPublic()).verify(PAYLOAD, importedSignature);
                    });
            runRejections(prefix + ".reject." + sourceLabel, source, pair, signature, checks);
        }
    }

    /** Signs the DSSE PAE frame, transports JSON and compares the exact verified payload bytes. */
    private void runDsse(
            String id, JcaSignatureEngine source, JcaSignatureEngine target, KeyPair pair, List<ProbeCheck> checks) {
        DsseEnvelope envelope =
                new DsseSigner().sign(PAYLOAD_TYPE, PAYLOAD, List.of(source.signer("ephemeral", pair.getPrivate())));
        MessageVerifier trusted =
                target.verifier(target.importPublicKey(pair.getPublic().getEncoded()));
        check(
                checks,
                id,
                "DSSE JSON preserves and authenticates the original bytes",
                Map.of(
                        "algorithm",
                        source.algorithm().name(),
                        "signer",
                        source.providerName(),
                        "verifier",
                        target.providerName()),
                () -> {
                    DsseJsonCodec codec = new DsseJsonCodec();
                    return verifies(codec.read(codec.write(envelope)), PAYLOAD_TYPE, trusted, PAYLOAD);
                });
    }

    /** Checks unframed signatures and DSSE bindings against realistic attacker-controlled changes. */
    private void runRejections(
            String prefix, JcaSignatureEngine engine, KeyPair pair, byte[] signature, List<ProbeCheck> checks) {
        MessageVerifier trusted = engine.verifier(pair.getPublic());
        Map<String, String> evidence =
                Map.of("algorithm", engine.algorithm().name(), "provider", engine.providerName());
        check(
                checks,
                prefix + ".message",
                "Changed message is rejected",
                evidence,
                () -> !trusted.verify(changed(PAYLOAD), signature));
        check(
                checks,
                prefix + ".signature",
                "Changed signature is rejected",
                evidence,
                () -> !trusted.verify(PAYLOAD, changed(signature)));
        check(
                checks,
                prefix + ".truncated",
                "Truncated signature is rejected",
                evidence,
                () -> !trusted.verify(PAYLOAD, Arrays.copyOf(signature, signature.length - 1)));
        check(
                checks,
                prefix + ".wrong-key",
                "Unrelated trusted key is rejected",
                evidence,
                () -> !engine.verifier(engine.generateKeyPair().getPublic()).verify(PAYLOAD, signature));
        DsseEnvelope envelope =
                new DsseSigner().sign(PAYLOAD_TYPE, PAYLOAD, List.of(engine.signer("ephemeral", pair.getPrivate())));
        check(
                checks,
                prefix + ".dsse-payload",
                "Changed DSSE payload is rejected",
                evidence,
                () -> !verifies(
                        new DsseEnvelope(PAYLOAD_TYPE, DsseBase64.encode(changed(PAYLOAD)), envelope.signatures()),
                        PAYLOAD_TYPE,
                        trusted,
                        changed(PAYLOAD)));
        // Pass the altered type as the expected type too: this tests its signature binding.
        String alteredType = "application/vnd.pqclab.other.v1+json";
        check(
                checks,
                prefix + ".dsse-type",
                "Changed DSSE payload type is rejected",
                evidence,
                () -> !verifies(
                        new DsseEnvelope(alteredType, envelope.payload(), envelope.signatures()),
                        alteredType,
                        trusted,
                        PAYLOAD));
    }

    /** Demonstrates two independent signatures without conflating their presence with a trust policy. */
    private void runDualEnvelope(Provider bc, List<ProbeCheck> checks) {
        JcaSignatureEngine ed = JcaSignatureEngine.usingDefaultProvider(SignatureAlgorithm.ED25519);
        JcaSignatureEngine ml = JcaSignatureEngine.usingProvider(SignatureAlgorithm.ML_DSA_65, bc);
        KeyPair edPair = ed.generateKeyPair();
        KeyPair mlPair = ml.generateKeyPair();
        MessageVerifier edTrusted =
                JcaSignatureEngine.usingProvider(SignatureAlgorithm.ED25519, bc).verifier(edPair.getPublic());
        JcaSignatureEngine nativeMl = JcaSignatureEngine.usingDefaultProvider(SignatureAlgorithm.ML_DSA_65);
        MessageVerifier mlTrusted =
                nativeMl.verifier(nativeMl.importPublicKey(mlPair.getPublic().getEncoded()));
        DsseEnvelope dual = new DsseSigner()
                .sign(
                        PAYLOAD_TYPE,
                        PAYLOAD,
                        List.of(
                                ed.signer("ed-local", edPair.getPrivate()),
                                ml.signer("ml-local", mlPair.getPrivate())));
        check(
                checks,
                "dsse.dual.ed25519",
                "Dual envelope verifies with the trusted Ed25519 key",
                Map.of("signatures", "2"),
                () -> verifies(dual, PAYLOAD_TYPE, edTrusted, PAYLOAD));
        check(
                checks,
                "dsse.dual.ml-dsa-65",
                "Dual envelope verifies with the trusted ML-DSA-65 key",
                Map.of("signatures", "2"),
                () -> verifies(dual, PAYLOAD_TYPE, mlTrusted, PAYLOAD));
        DsseEnvelope stripped = new DsseEnvelope(
                PAYLOAD_TYPE, dual.payload(), List.of(dual.signatures().getFirst()));
        check(
                checks,
                "dsse.stripped.ed25519",
                "Remaining Ed25519 signature is independently valid",
                Map.of("policy", "single-key verification only"),
                () -> verifies(stripped, PAYLOAD_TYPE, edTrusted, PAYLOAD));
        check(
                checks,
                "dsse.stripped.ml-dsa-65",
                "Missing ML-DSA signature cannot satisfy its trusted key",
                Map.of("policy", "no BOTH_REQUIRED policy is implemented"),
                () -> !verifies(stripped, PAYLOAD_TYPE, mlTrusted, PAYLOAD));
        DsseEnvelope hints = new DsseEnvelope(
                PAYLOAD_TYPE,
                dual.payload(),
                dual.signatures().stream()
                        .map(entry -> new DsseSignature("attacker-controlled-hint", entry.signature()))
                        .toList());
        check(
                checks,
                "dsse.key-hints",
                "Changing lookup hints does not change either signature",
                Map.of("trust", "public keys selected out of band"),
                () -> verifies(hints, PAYLOAD_TYPE, edTrusted, PAYLOAD)
                        && verifies(hints, PAYLOAD_TYPE, mlTrusted, PAYLOAD));
        DsseEnvelope urlSafe = new DsseEnvelope(
                PAYLOAD_TYPE,
                urlBase64(PAYLOAD),
                dual.signatures().stream()
                        .map(entry -> new DsseSignature(entry.keyId(), urlBase64(entry.signatureBytes())))
                        .toList());
        check(
                checks,
                "dsse.url-base64",
                "URL-safe unpadded base64 preserves both signatures",
                Map.of(),
                () -> verifies(urlSafe, PAYLOAD_TYPE, edTrusted, PAYLOAD)
                        && verifies(urlSafe, PAYLOAD_TYPE, mlTrusted, PAYLOAD));
        check(
                checks,
                "dsse.json-layout",
                "JSON whitespace, ordering and extensions do not alter signed bytes",
                Map.of(),
                () -> {
                    String json = "{ \"extension\": true, \"signatures\": ["
                            + "{\"sig\":\"" + dual.signatures().getFirst().signature()
                            + "\"}],\n \"payload\":\"" + dual.payload() + "\",\n \"payloadType\":\"" + PAYLOAD_TYPE
                            + "\" }";
                    return verifies(
                            new DsseJsonCodec().read(json.getBytes(StandardCharsets.UTF_8)),
                            PAYLOAD_TYPE,
                            edTrusted,
                            PAYLOAD);
                });
        byte[] binary = {0, 32, (byte) 255, 10, 0};
        DsseEnvelope binaryEnvelope =
                new DsseSigner().sign("application/octet-stream", binary, List.of(ed.signer("", edPair.getPrivate())));
        check(
                checks,
                "dsse.binary-payload",
                "Opaque binary payload is returned byte for byte",
                Map.of(),
                () -> verifies(binaryEnvelope, "application/octet-stream", edTrusted, binary));
    }

    /** Compares the verified snapshot with the original bytes, never a reserialised business object. */
    private boolean verifies(DsseEnvelope envelope, String type, MessageVerifier trusted, byte[] expected) {
        return new DsseVerifier()
                .verify(envelope, type, trusted)
                .map(payload -> Arrays.equals(expected, payload.body()))
                .orElse(false);
    }

    /** Captures an expectation and turns operational exceptions into explicit failed evidence. */
    private void check(
            List<ProbeCheck> checks,
            String id,
            String description,
            Map<String, String> evidence,
            BooleanSupplier expectation) {
        try {
            checks.add(new ProbeCheck(id, description, expectation.getAsBoolean(), evidence));
        } catch (RuntimeException failure) {
            checks.add(failure(id, description, failure));
        }
    }

    /** Records the exception category without exposing keys or provider diagnostic fragments. */
    private ProbeCheck failure(String id, String description, RuntimeException failure) {
        return new ProbeCheck(
                id, description, false, Map.of("error", failure.getClass().getSimpleName()));
    }

    /** Collects only public implementation details; private-key encodings are never retained. */
    private Map<String, String> metadata(
            JcaSignatureEngine source, JcaSignatureEngine target, KeyPair pair, byte[] signature) {
        return Map.of(
                "algorithm",
                source.algorithm().name(),
                "signer",
                source.providerName() + " " + source.providerVersion(),
                "verifier",
                target.providerName() + " " + target.providerVersion(),
                "publicKeyFormat",
                pair.getPublic().getFormat(),
                "publicKeySpkiBytes",
                Integer.toString(pair.getPublic().getEncoded().length),
                "publicKeySha256",
                fingerprint(pair.getPublic().getEncoded()),
                "signatureBytes",
                Integer.toString(signature.length));
    }

    /** Fingerprints a public SPKI encoding using the platform's SHA-256 implementation. */
    private String fingerprint(byte[] publicKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKey));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("This runtime lacks required SHA-256 support.", unavailable);
        }
    }

    /** Flips one bit while preserving the original input for other experiments. */
    private byte[] changed(byte[] original) {
        byte[] copy = original.clone();
        copy[0] ^= 1;
        return copy;
    }

    /** Uses the second base64 alphabet required by DSSE, including its unpadded form. */
    private String urlBase64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
