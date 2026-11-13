package hu.balazsfalvi.pqclab.core.dsse;

import java.util.List;
import java.util.Objects;

/**
 * Holds an immutable DSSE envelope while preserving its encoded payload.
 *
 * @param payloadType the exact authenticated payload type
 * @param payload the base64 representation of the original body
 * @param signatures the bounded list of signature entries
 */
public record DsseEnvelope(String payloadType, String payload, List<DsseSignature> signatures) {
    /**
     * Validates the laboratory's resource limits and defensively copies the signature list.
     *
     * @param payloadType the exact authenticated payload type
     * @param payload the base64 representation of the original body
     * @param signatures the candidate signatures, which may be empty for an unsigned envelope
     * @throws MalformedEnvelopeException if the envelope violates the lab profile
     */
    public DsseEnvelope {
        Objects.requireNonNull(payloadType, "payloadType");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(signatures, "signatures");
        if (signatures.size() > DsseLimits.MAX_SIGNATURES) {
            throw new MalformedEnvelopeException("The envelope contains too many signatures.");
        }
        signatures = List.copyOf(signatures);
        byte[] body = DsseBase64.decode(payload, DsseLimits.MAX_PAYLOAD_BYTES);
        // Reuse the framing boundary to validate Unicode and byte limits consistently.
        new PaeEncoder().encode(payloadType, body);
    }

    /**
     * Returns the original decoded bytes without parsing or normalising them.
     *
     * @return a fresh copy of the payload bytes
     */
    public byte[] payloadBytes() {
        return DsseBase64.decode(payload, DsseLimits.MAX_PAYLOAD_BYTES);
    }
}
