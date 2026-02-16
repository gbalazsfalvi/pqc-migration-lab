package hu.balazsfalvi.pqclab.core.dsse;

import java.io.ByteArrayOutputStream;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Encodes the exact DSSE v1 pre-authentication framing, without interpreting the payload. */
public final class PaeEncoder {
    /** Creates a stateless encoder. */
    public PaeEncoder() {}

    /**
     * Frames a payload type and body using their byte lengths, not character counts.
     *
     * @param payloadType the case-sensitive type bound to the signature
     * @param body the opaque payload bytes
     * @return the complete pre-authentication encoding
     * @throws MalformedEnvelopeException if the type or payload violates the lab profile
     */
    public byte[] encode(String payloadType, byte[] body) {
        Objects.requireNonNull(body, "body");
        byte[] typeBytes = encodeType(payloadType);
        if (body.length > DsseLimits.MAX_PAYLOAD_BYTES) {
            throw new MalformedEnvelopeException("The payload exceeds the configured size limit.");
        }
        var output = new ByteArrayOutputStream(typeBytes.length + body.length + 32);
        output.writeBytes("DSSEv1 ".getBytes(StandardCharsets.US_ASCII));
        writeLength(output, typeBytes.length);
        output.writeBytes(typeBytes);
        output.write(' ');
        writeLength(output, body.length);
        output.writeBytes(body);
        return output.toByteArray();
    }

    /** Encodes a well-formed Unicode type while refusing silent replacement characters. */
    private byte[] encodeType(String payloadType) {
        Objects.requireNonNull(payloadType, "payloadType");
        if (payloadType.length() > DsseLimits.MAX_PAYLOAD_TYPE_BYTES) {
            throw new MalformedEnvelopeException("The payload type exceeds the configured size limit.");
        }
        try {
            var encoded = StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(payloadType));
            if (encoded.remaining() > DsseLimits.MAX_PAYLOAD_TYPE_BYTES) {
                throw new MalformedEnvelopeException("The UTF-8 payload type exceeds the configured size limit.");
            }
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException failure) {
            throw new MalformedEnvelopeException("The payload type is not valid Unicode.", failure);
        }
    }

    /** Writes the ASCII decimal byte length followed by the required separating space. */
    private void writeLength(ByteArrayOutputStream output, int length) {
        output.writeBytes(Integer.toString(length).getBytes(StandardCharsets.US_ASCII));
        output.write(' ');
    }
}
