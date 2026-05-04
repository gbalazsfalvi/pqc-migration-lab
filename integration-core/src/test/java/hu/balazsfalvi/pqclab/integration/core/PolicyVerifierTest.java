package hu.balazsfalvi.pqclab.integration.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseSignature;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Uses explicitly labelled verifier spies for trust grouping; real signatures are tested over HTTP. */
final class PolicyVerifierTest {
    /** Finds a valid post-quantum snapshot even when no classical signature is present. */
    @Test
    void returnsExactBytesAndTheTrustedOwnerForPqcOnly() {
        var verifier = new PolicyVerifier(List.of(publisher("publisher-a", 1, 2)));
        var result = verifier.verify(envelope("Ag=="), MigrationPolicy.PQC_REQUIRED);
        assertEquals("publisher-a", result.issuer());
        assertEquals(new SignatureEvidence(false, true), result.evidence());
        assertArrayEquals(new byte[] {42}, result.payload().body());
    }

    /** Independent valid signatures cannot be combined across different trusted key owners. */
    @Test
    void rejectsCrossPublisherSignatureSplicing() {
        var verifier = new PolicyVerifier(List.of(publisher("publisher-a", 1, 2), publisher("publisher-b", 3, 4)));
        var envelope = new DsseEnvelope(
                ReleaseEvent.PAYLOAD_TYPE,
                "Kg==",
                List.of(new DsseSignature("publisher-a", "AQ=="), new DsseSignature("publisher-a", "BA==")));
        assertEquals(
                ReceptionFailure.Code.POLICY_REJECTED,
                assertThrows(ReceptionFailure.class, () -> verifier.verify(envelope, MigrationPolicy.BOTH_REQUIRED))
                        .code());
        assertEquals(
                "publisher-a",
                verifier.verify(envelope, MigrationPolicy.CLASSICAL_ONLY).issuer());
        assertEquals(
                "publisher-b",
                verifier.verify(envelope, MigrationPolicy.PQC_REQUIRED).issuer());
    }

    /** Unsupported payload types are rejected before the business codec is allowed to run. */
    @Test
    void rejectsOtherProtocolsAndInvalidTrustConfiguration() {
        var publisher = publisher("publisher-a", 1, 2);
        var verifier = new PolicyVerifier(List.of(publisher));
        assertEquals(
                ReceptionFailure.Code.INVALID_PAYLOAD_TYPE,
                assertThrows(
                                ReceptionFailure.class,
                                () -> verifier.verify(
                                        new DsseEnvelope("other/type", "Kg==", List.of()),
                                        MigrationPolicy.BOTH_REQUIRED))
                        .code());
        assertThrows(IllegalArgumentException.class, () -> new PolicyVerifier(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PolicyVerifier(List.of(publisher, publisher)));
        assertThrows(
                IllegalArgumentException.class, () -> new PolicyVerifier(java.util.Collections.nCopies(9, publisher)));
        assertThrows(
                IllegalArgumentException.class, () -> new TrustedPublisher("Invalid issuer", publisher.verifiers()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrustedPublisher("publisher-a", Map.of(SignatureAlgorithm.ED25519, spy(1))));
    }

    /** Supplies complete, independently owned verification ports without pretending they are cryptography. */
    static TrustedPublisher publisher(String issuer, int classicalMarker, int pqcMarker) {
        return new TrustedPublisher(
                issuer,
                Map.of(SignatureAlgorithm.ED25519, spy(classicalMarker), SignatureAlgorithm.ML_DSA_65, spy(pqcMarker)));
    }

    /** Creates a verifier spy whose marker identifies a trusted key in pure application tests. */
    private static MessageVerifier spy(int marker) {
        return (message, signature) -> signature.length == 1 && signature[0] == marker;
    }

    /** Wraps the binary byte 42 without business parsing or canonicalisation. */
    static DsseEnvelope envelope(String signature) {
        return new DsseEnvelope(
                ReleaseEvent.PAYLOAD_TYPE, "Kg==", List.of(new DsseSignature("untrusted-hint", signature)));
    }
}
