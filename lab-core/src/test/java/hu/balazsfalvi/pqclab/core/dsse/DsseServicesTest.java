package hu.balazsfalvi.pqclab.core.dsse;

import static org.junit.jupiter.api.Assertions.*;

import hu.balazsfalvi.pqclab.core.crypto.MessageSigner;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Uses explicit port spies to test DSSE framing; real cryptography is exercised in adapter tests. */
class DsseServicesTest {
    /** A signing port must receive the literal frame, not JSON or an unframed body. */
    @Test
    void signsAnImmutablePayloadSnapshot() {
        byte[] body = ascii("abc");
        MessageSigner spy = echoSigner();
        var envelope = new DsseSigner().sign("t", body, List.of(spy));
        body[0] = 'x';
        assertArrayEquals(
                ascii("DSSEv1 1 t 3 abc"), envelope.signatures().getFirst().signatureBytes());
        assertArrayEquals(ascii("abc"), envelope.payloadBytes());
    }

    /** A lookup hint does not control whether the caller-selected trusted key verifies. */
    @Test
    void verifiesOriginalBytesWithoutTrustingTheHint() {
        var valid = new DsseSignature("an-untrusted-hint", DsseBase64.encode(ascii("DSSEv1 1 t 3 abc")));
        var envelope =
                new DsseEnvelope("t", DsseBase64.encode(ascii("abc")), List.of(new DsseSignature("", "AA=="), valid));
        var verified = new DsseVerifier().verify(envelope, "t", Arrays::equals).orElseThrow();
        assertEquals("t", verified.payloadType());
        byte[] returned = verified.body();
        returned[0] = 'x';
        assertArrayEquals(ascii("abc"), verified.body());
    }

    /** Mismatched types, altered bytes and unsigned envelopes never produce verified payloads. */
    @Test
    void rejectsMissingOrMismatchedEvidence() {
        var envelope = new DsseSigner().sign("t", ascii("abc"), List.of(echoSigner()));
        var verifier = new DsseVerifier();
        assertTrue(verifier.verify(envelope, "another-type", Arrays::equals).isEmpty());
        assertTrue(verifier.verify(new DsseEnvelope("t", "YWJk", envelope.signatures()), "t", Arrays::equals)
                .isEmpty());
        assertTrue(verifier.verify(new DsseEnvelope("t", envelope.payload(), List.of()), "t", Arrays::equals)
                .isEmpty());
    }

    /** Signing requires a bounded non-empty collection of explicit identities. */
    @Test
    void rejectsInvalidSignerCounts() {
        var signer = new DsseSigner();
        assertThrows(IllegalArgumentException.class, () -> signer.sign("t", new byte[0], List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> signer.sign("t", new byte[0], java.util.Collections.nCopies(5, echoSigner())));
    }

    /** Returns a framing spy, deliberately not a cryptographic signature implementation. */
    private MessageSigner echoSigner() {
        return new MessageSigner() {
            /** {@inheritDoc} */
            @Override
            public String keyId() {
                return "spy";
            }
            /** {@inheritDoc} */
            @Override
            public byte[] sign(byte[] bytes) {
                return bytes.clone();
            }
        };
    }

    /** Encodes a literal reference independently from production DSSE code. */
    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
