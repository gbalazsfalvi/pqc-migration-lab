package hu.balazsfalvi.pqclab.core.dsse;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Checks independently calculated framing examples, including Unicode and binary payloads. */
class PaeEncoderTest {
    private final PaeEncoder encoder = new PaeEncoder();

    /** Empty values retain both length delimiters and the final space. */
    @Test
    void encodesEmptyValuesExactly() {
        assertArrayEquals(ascii("DSSEv1 0  0 "), encoder.encode("", new byte[0]));
    }

    /** A literal expected frame makes accidental framing changes visible. */
    @Test
    void matchesAnIndependentlyCalculatedFrame() {
        assertArrayEquals(ascii("DSSEv1 1 t 11 hello world"), encoder.encode("t", ascii("hello world")));
    }

    /** The type length must count UTF-8 bytes rather than Java characters. */
    @Test
    void countsUtf8Bytes() {
        assertArrayEquals("DSSEv1 2 é 1 x".getBytes(StandardCharsets.UTF_8), encoder.encode("é", ascii("x")));
    }

    /** Opaque payload bytes must survive framing without a text conversion. */
    @Test
    void preservesBinaryPayload() {
        byte[] framed = encoder.encode("t", new byte[] {0, (byte) 255, 1});
        byte[] prefix = ascii("DSSEv1 1 t 3 ");
        assertArrayEquals(prefix, java.util.Arrays.copyOf(framed, prefix.length));
        assertArrayEquals(
                new byte[] {0, (byte) 255, 1}, java.util.Arrays.copyOfRange(framed, prefix.length, framed.length));
    }

    /** Silent surrogate replacement could bind distinct types to identical bytes. */
    @Test
    void rejectsMalformedUnicode() {
        assertThrows(MalformedEnvelopeException.class, () -> encoder.encode("\uD800", new byte[0]));
    }

    /** Character and byte bounds are checked independently. */
    @Test
    void enforcesResourceBounds() {
        assertThrows(MalformedEnvelopeException.class, () -> encoder.encode("x".repeat(1_025), new byte[0]));
        assertThrows(MalformedEnvelopeException.class, () -> encoder.encode("é".repeat(600), new byte[0]));
        assertThrows(MalformedEnvelopeException.class, () -> encoder.encode("t", new byte[65_537]));
    }

    /** Encodes reference literals without involving production framing logic. */
    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
