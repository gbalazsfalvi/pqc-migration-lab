package hu.balazsfalvi.pqclab.benchmarks;

import hu.balazsfalvi.pqclab.core.crypto.MessageSigner;
import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import hu.balazsfalvi.pqclab.core.dsse.DsseSigner;
import hu.balazsfalvi.pqclab.core.dsse.DsseVerifier;
import hu.balazsfalvi.pqclab.core.dsse.PaeEncoder;
import hu.balazsfalvi.pqclab.crypto.JcaSignatureEngine;
import hu.balazsfalvi.pqclab.json.DsseJsonCodec;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

/** Owns one thread's synthetic public inputs and ephemeral keys, outside the measured method setup. */
public final class BenchmarkWorkload {
    /** Distinguishes opaque benchmark bytes from the integration's signed business-event schema. */
    public static final String PAYLOAD_TYPE = "application/vnd.pqclab.benchmark.v1";

    private final SignatureProfile profile;
    private final List<JcaSignatureEngine> engines;
    private final List<KeyPair> keys;
    private final List<MessageSigner> signers;
    private final List<MessageVerifier> verifiers;
    private final byte[] payload;
    private final List<byte[]> signatures;
    private final DsseSigner dsseSigner = new DsseSigner();
    private final DsseVerifier dsseVerifier = new DsseVerifier();
    private final DsseJsonCodec json = new DsseJsonCodec();
    private final DsseEnvelope envelope;
    private final byte[] wire;

    /**
     * Prepares reusable valid inputs; no randomness is seeded with predictable benchmark data.
     *
     * @param profile the algorithms required for one operation
     * @param payloadBytes the exact decoded byte count within the laboratory limit
     */
    public BenchmarkWorkload(SignatureProfile profile, int payloadBytes) {
        if (payloadBytes < 1 || payloadBytes > DsseLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Payload size must be within the laboratory limit.");
        }
        this.profile = java.util.Objects.requireNonNull(profile, "profile");
        engines = profile.algorithms().stream()
                .map(JcaSignatureEngine::usingDefaultProvider)
                .toList();
        keys = engines.stream().map(JcaSignatureEngine::generateKeyPair).toList();
        var signing = new ArrayList<MessageSigner>();
        var verifying = new ArrayList<MessageVerifier>();
        for (int index = 0; index < engines.size(); index++) {
            signing.add(engines.get(index)
                    .signer(
                            "benchmark-" + profile.algorithms().get(index),
                            keys.get(index).getPrivate()));
            verifying.add(engines.get(index).verifier(keys.get(index).getPublic()));
        }
        signers = List.copyOf(signing);
        verifiers = List.copyOf(verifying);
        payload = new byte[payloadBytes];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index * 31 + 17);
        }
        signatures = signMessage();
        envelope = signEnvelope();
        wire = encodeEnvelope();
        if (!verifyMessage() || !verifyEnvelope()) {
            throw new IllegalStateException("Benchmark setup did not verify its own valid inputs.");
        }
    }

    /**
     * Signs existing raw message bytes using the unchanged production JCA adapter.
     *
     * @return all fresh signatures, consumed by the JMH harness
     */
    public List<byte[]> signMessage() {
        return signers.stream().map(signer -> signer.sign(payload)).toList();
    }

    /**
     * Verifies precomputed valid raw signatures, excluding signing and key generation.
     *
     * @return whether every algorithm verified
     */
    public boolean verifyMessage() {
        boolean valid = true;
        for (int index = 0; index < verifiers.size(); index++) {
            // Evaluate every required algorithm even if an earlier observation is false.
            valid &= verifiers.get(index).verify(payload, signatures.get(index));
        }
        return valid;
    }

    /**
     * Creates a DSSE envelope including PAE, signing, byte copies and base64, without outer JSON.
     *
     * @return the new immutable envelope
     */
    public DsseEnvelope signEnvelope() {
        return dsseSigner.sign(PAYLOAD_TYPE, payload, signers);
    }

    /**
     * Verifies a prebuilt DSSE envelope against all required trusted public keys.
     *
     * @return whether every required key verified the original bytes
     */
    public boolean verifyEnvelope() {
        boolean valid = true;
        for (MessageVerifier verifier : verifiers) {
            valid &= dsseVerifier.verify(envelope, PAYLOAD_TYPE, verifier).isPresent();
        }
        return valid;
    }

    /**
     * Encodes an existing envelope without signing it again.
     *
     * @return the actual strict JSON UTF-8 bytes
     */
    public byte[] encodeEnvelope() {
        return json.write(envelope);
    }

    /**
     * Decodes precomputed wire bytes without verifying their signatures.
     *
     * @return the bounded immutable DSSE envelope
     */
    public DsseEnvelope decodeEnvelope() {
        return json.read(wire);
    }

    /**
     * Observes actual byte sizes independently of timed operations.
     *
     * @return public transport and provider evidence with no private key representation
     */
    public BenchmarkReport.TransportSize transportSize() {
        var publicKeys =
                keys.stream().map(key -> key.getPublic().getEncoded().length).toList();
        var signatureSizes =
                signatures.stream().map(signature -> signature.length).toList();
        return new BenchmarkReport.TransportSize(
                profile.name(),
                payload.length,
                new PaeEncoder().encode(PAYLOAD_TYPE, payload).length,
                publicKeys,
                signatureSizes,
                wire.length);
    }

    /**
     * Reports the providers actually selected by JCA without changing the global registry.
     *
     * @return public algorithm and implementation identities
     */
    public List<BenchmarkReport.ProviderInfo> providers() {
        return engines.stream()
                .map(engine -> new BenchmarkReport.ProviderInfo(
                        engine.algorithm().name(), engine.providerName(), engine.providerVersion()))
                .toList();
    }
}
