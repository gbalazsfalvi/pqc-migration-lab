package hu.balazsfalvi.pqclab.core.dsse;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises immutable envelope state and the accepted wire-encoding boundaries. */
class DsseModelTest {
    /** Standard and URL-safe alphabets represent the same signed payload bytes. */
    @Test
    void acceptsBothBase64Alphabets() {
        byte[] expected = {(byte) 251, (byte) 255};
        assertArrayEquals(expected, DsseBase64.decode("+/8=", 2));
        assertArrayEquals(expected, DsseBase64.decode("-_8=", 2));
        assertArrayEquals(expected, DsseBase64.decode("-_8", 2));
    }

    /** Whitespace, mixed alphabets and excessive decoded sizes are rejected. */
    @Test
    void rejectsAmbiguousOrOversizedEncodings() {
        assertThrows(MalformedEnvelopeException.class, () -> DsseBase64.decode("+-_/", 16));
        assertThrows(MalformedEnvelopeException.class, () -> DsseBase64.decode("Y Q==", 16));
        assertThrows(MalformedEnvelopeException.class, () -> DsseBase64.decode("AAAA", 1));
        assertThrows(MalformedEnvelopeException.class, () -> DsseBase64.decode("AAAAA", 1));
        assertThrows(IllegalArgumentException.class, () -> DsseBase64.decode("", -1));
        assertArrayEquals(new byte[0], DsseBase64.decode("", 0));
    }

    /** Changing caller-owned collections or returned arrays cannot change the envelope. */
    @Test
    void defensivelyCopiesMutableBoundaries() {
        var entries = new ArrayList<DsseSignature>();
        entries.add(new DsseSignature(null, "AA=="));
        var envelope = new DsseEnvelope("t", "YQ==", entries);
        entries.clear();
        byte[] returned = envelope.payloadBytes();
        returned[0] = 0;
        assertEquals(1, envelope.signatures().size());
        assertEquals("", envelope.signatures().getFirst().keyId());
        assertArrayEquals(new byte[] {'a'}, envelope.payloadBytes());
        assertThrows(
                UnsupportedOperationException.class, () -> envelope.signatures().clear());
        byte[] signature = envelope.signatures().getFirst().signatureBytes();
        signature[0] = 1;
        assertArrayEquals(new byte[] {0}, envelope.signatures().getFirst().signatureBytes());
    }

    /** The lab profile limits metadata, signatures and payload allocations. */
    @Test
    void enforcesModelLimits() {
        var entry = new DsseSignature("", "");
        assertThrows(
                MalformedEnvelopeException.class,
                () -> new DsseEnvelope("t", "", java.util.Collections.nCopies(5, entry)));
        assertThrows(MalformedEnvelopeException.class, () -> new DsseSignature("x".repeat(257), ""));
        assertThrows(
                MalformedEnvelopeException.class,
                () -> new DsseSignature("", Base64.getEncoder().encodeToString(new byte[16_385])));
        assertThrows(
                MalformedEnvelopeException.class,
                () -> new DsseEnvelope("t", Base64.getEncoder().encodeToString(new byte[65_537]), List.of()));
    }
}
