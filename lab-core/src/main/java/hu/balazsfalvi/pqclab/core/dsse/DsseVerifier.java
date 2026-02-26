package hu.balazsfalvi.pqclab.core.dsse;

import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import java.util.Objects;
import java.util.Optional;

/** Verifies a DSSE envelope against one caller-trusted public key, without inventing trust policy. */
public final class DsseVerifier {
    private final PaeEncoder encoder;

    /** Creates a verifier using the standard DSSE v1 encoder. */
    public DsseVerifier() {
        this.encoder = new PaeEncoder();
    }

    /**
     * Finds a valid signature for the explicitly trusted key and expected payload type.
     *
     * <p>Key hints are deliberately ignored: the trusted key is supplied out of band. Multi-key
     * thresholds, revocation, freshness and replay policy belong to the application layer.
     *
     * @param envelope the parsed immutable envelope
     * @param expectedPayloadType the type the receiving application accepts
     * @param trustedVerifier the verifier bound to a preselected trusted public key
     * @return the original verified payload, or an empty result if verification fails
     */
    public Optional<VerifiedPayload> verify(
            DsseEnvelope envelope, String expectedPayloadType, MessageVerifier trustedVerifier) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(expectedPayloadType, "expectedPayloadType");
        Objects.requireNonNull(trustedVerifier, "trustedVerifier");
        if (!expectedPayloadType.equals(envelope.payloadType())) {
            return Optional.empty();
        }
        byte[] original = envelope.payloadBytes();
        byte[] framed = encoder.encode(envelope.payloadType(), original);
        for (DsseSignature signature : envelope.signatures()) {
            if (trustedVerifier.verify(framed.clone(), signature.signatureBytes())) {
                // Return this snapshot, not a second decode or a reserialised business object.
                return Optional.of(new VerifiedPayload(envelope.payloadType(), original));
            }
        }
        return Optional.empty();
    }
}
