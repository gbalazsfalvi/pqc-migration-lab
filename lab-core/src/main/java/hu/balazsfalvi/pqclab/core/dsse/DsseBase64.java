package hu.balazsfalvi.pqclab.core.dsse;

import java.util.Base64;
import java.util.Objects;

/** Implements DSSE's accepted base64 alphabets with allocation bounds. */
public final class DsseBase64 {
    /** Prevents construction of a stateless encoding utility. */
    private DsseBase64() {}

    /**
     * Encodes bytes with the standard alphabet and padding.
     *
     * @param bytes the bytes to encode
     * @return a standard base64 representation
     */
    public static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(Objects.requireNonNull(bytes, "bytes"));
    }

    /**
     * Decodes either DSSE-supported alphabet, rejecting whitespace and mixed alphabets.
     *
     * @param value the encoded bytes
     * @param maximumBytes the largest acceptable decoded length
     * @return newly allocated decoded bytes
     * @throws MalformedEnvelopeException if the representation or size is invalid
     */
    public static byte[] decode(String value, int maximumBytes) {
        Objects.requireNonNull(value, "value");
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("The decoded-byte limit must be non-negative.");
        }
        // Bound allocation before asking either decoder to allocate its output.
        long maximumCharacters = 4L * ((maximumBytes + 2L) / 3L);
        if (value.length() > maximumCharacters) {
            throw new MalformedEnvelopeException("The base64 value exceeds the configured size limit.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException standardFailure) {
            try {
                decoded = Base64.getUrlDecoder().decode(value);
            } catch (IllegalArgumentException urlFailure) {
                throw new MalformedEnvelopeException("The envelope contains invalid base64.", urlFailure);
            }
        }
        if (decoded.length > maximumBytes) {
            throw new MalformedEnvelopeException("The decoded value exceeds the configured size limit.");
        }
        return decoded;
    }
}
