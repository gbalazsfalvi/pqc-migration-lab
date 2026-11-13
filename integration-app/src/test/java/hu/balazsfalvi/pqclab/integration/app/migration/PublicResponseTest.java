package hu.balazsfalvi.pqclab.integration.app.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests export refusal at root, nested and scalar/container boundaries for every reviewed public schema. */
final class PublicResponseTest {
    /** Unreviewed fields, nested material, duplicate keys and malformed JSON never enter public evidence. */
    @Test
    void rejectsUnreviewedFieldsAndAmbiguousDocuments() {
        for (var kind : PublicResponse.Kind.values()) {
            for (String body : List.of("{\"privateKey\":\"blocked\"}", "[]", "{", "{\"policy\":1,\"policy\":2} {}")) {
                var failure = assertThrows(ScenarioFailure.class, () -> PublicResponse.read(kind, 200, bytes(body)));
                assertEquals("PUBLIC_RESPONSE_REJECTED", failure.code());
                assertNull(failure.getCause());
            }
        }
        reject(PublicResponse.Kind.STATUS, "{\"policy\":{\"privateKey\":\"blocked\"}}");
        reject(PublicResponse.Kind.STATUS, "{\"policy\":[{\"privateKey\":\"blocked\"}]}");
        reject(
                PublicResponse.Kind.ENVELOPE,
                "{\"signatures\":[{\"keyid\":\"id\",\"sig\":\"x\",\"pkcs8\":\"blocked\"}]}");
        reject(PublicResponse.Kind.ENVELOPE, "{\"signatures\":null}");
        reject(PublicResponse.Kind.REGISTRY, "{\"generations\":[{\"keys\":[{\"pkcs8\":\"blocked\"}]}]}");
        reject(PublicResponse.Kind.REGISTRY, "{\"generations\":[{\"privateKey\":\"blocked\",\"keys\":[]}]}");
        reject(PublicResponse.Kind.RECEIPT, "{\"acceptedEvidence\":{\"ed25519Valid\":true,\"pkcs8\":\"blocked\"}}");
        reject(
                PublicResponse.Kind.RECEIPT,
                "{\"acceptedEvidence\":{\"ed25519Valid\":true,\"mlDsa65Valid\":\"false\"}}");
        reject(
                PublicResponse.Kind.EVALUATION,
                "{\"signatureEvidence\":{\"ed25519Valid\":true,\"mlDsa65Valid\":true},\"signatures\":[],"
                        + "\"policies\":[{\"accepted\":\"false\",\"steps\":[]}]}");
        reject(
                PublicResponse.Kind.EVALUATION,
                "{\"signatureEvidence\":{\"ed25519Valid\":true,\"mlDsa65Valid\":true},"
                        + "\"signatures\":[{\"verifiedAlgorithms\":[{\"privateKey\":\"blocked\"}]}],\"policies\":[]}");
        reject(
                PublicResponse.Kind.EVALUATION,
                "{\"signatureEvidence\":{\"ed25519Valid\":true,\"mlDsa65Valid\":true},"
                        + "\"signatures\":[],\"policies\":[{\"accepted\":true,\"steps\":[{\"detail\":{\"privateKey\":\"blocked\"}}]}]}");
        assertThrows(
                ScenarioFailure.class,
                () -> PublicResponse.read(
                        PublicResponse.Kind.STATUS, 503, bytes("{\"detail\":{\"privateKey\":\"blocked\"}}")));
        assertThrows(
                ScenarioFailure.class,
                () -> PublicResponse.read(PublicResponse.Kind.STATUS, 503, bytes("{\"privateKey\":\"blocked\"}")));
    }

    /** Mutable response inputs cannot change a journal reply after it is observed. */
    @Test
    void snapshotsPublicReplyBytesAndTrees() throws Exception {
        byte[] original = bytes("{\"policy\":\"BOTH_REQUIRED\"}");
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(original);
        var reply = new MigrationTransport.Reply(1, tree, original);
        original[0] = 'x';
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).put("policy", "changed");
        byte[] returned = reply.bytes();
        returned[0] = 'x';
        ((com.fasterxml.jackson.databind.node.ObjectNode) reply.json()).put("policy", "changed");
        assertEquals('{', reply.bytes()[0]);
        assertEquals("BOTH_REQUIRED", reply.json().required("policy").textValue());
    }

    /** Requires a safe refusal rather than accepting malformed or private nested fields. */
    private void reject(PublicResponse.Kind kind, String body) {
        assertThrows(ScenarioFailure.class, () -> PublicResponse.read(kind, 200, bytes(body)), body);
    }

    /** Encodes fixed test-owned JSON as UTF-8. */
    private byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
