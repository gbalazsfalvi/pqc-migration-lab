package hu.balazsfalvi.pqclab.core.dsse;

import java.util.Objects;

/**
 * Represents one DSSE signature entry; its key ID is never proof of identity.
 *
 * @param keyId the optional unauthenticated lookup hint
 * @param signature the base64-encoded signature
 */
public record DsseSignature(String keyId, String signature) {
    /**
     * Creates a bounded, immutable entry and normalises an omitted hint to the empty string.
     *
     * @param keyId the optional unauthenticated lookup hint
     * @param signature the base64-encoded signature
     * @throws MalformedEnvelopeException if the hint, encoding or signature size is invalid
     */
    public DsseSignature {
        keyId = keyId == null ? "" : keyId;
        Objects.requireNonNull(signature, "signature");
        if (keyId.length() > DsseLimits.MAX_KEY_ID_CHARACTERS) {
            throw new MalformedEnvelopeException("The key hint exceeds the configured size limit.");
        }
        DsseBase64.decode(signature, DsseLimits.MAX_SIGNATURE_BYTES);
    }

    /**
     * Returns a fresh copy of the decoded signature.
     *
     * @return the candidate signature bytes
     */
    public byte[] signatureBytes() {
        return DsseBase64.decode(signature, DsseLimits.MAX_SIGNATURE_BYTES);
    }
}
