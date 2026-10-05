package hu.balazsfalvi.pqclab.integration.app.publisher.keys;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests strict encrypted-plaintext schemas without ever writing plaintext key encodings to disk. */
final class VaultCodecTest {
    /** The authenticated schema retains lifecycle metadata and drops private encodings for old generations. */
    @Test
    void roundTripsPublicHistoryAndActiveKeys() throws Exception {
        var first = KeyGeneration.generate(1, Instant.parse("2026-11-13T10:00:00Z"));
        var second = KeyGeneration.generate(2, first.createdAt().plusSeconds(1));
        var state = new VaultState(
                "release-publisher", 2, second.id(), List.of(first.retire(second.createdAt(), 600), second));
        var codec = new VaultCodec();
        byte[] plaintext = codec.encode(state);
        try {
            var restored = codec.decode(plaintext, state.issuer());
            assertEquals(state.activeId(), restored.activeId());
            assertEquals(
                    state.generations().getFirst().publicOnly().keys(),
                    restored.generations().getFirst().publicOnly().keys());
            var tree = new ObjectMapper().readTree(plaintext);
            for (var key : tree.get("generations").get(0).get("keys"))
                assertTrue(key.get("pkcs8").isNull());
            for (var key : tree.get("generations").get(1).get("keys"))
                assertTrue(key.get("pkcs8").isTextual());
        } finally {
            java.util.Arrays.fill(plaintext, (byte) 0);
        }
    }

    /** Wrong versions, owners, duplicate algorithms, mismatched key pairs and unexpected fields are rejected. */
    @Test
    void rejectsAmbiguousOrMismatchedMaterial() throws Exception {
        var first = KeyGeneration.generate(1, Instant.parse("2026-11-13T10:00:00Z"));
        var other = KeyGeneration.generate(1, first.createdAt());
        var state = new VaultState("release-publisher", 1, first.id(), List.of(first));
        var codec = new VaultCodec();
        var json = new ObjectMapper();
        byte[] valid = codec.encode(state);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid, "another-publisher"));
        for (String mutation :
                List.of("version", "unknown", "missing", "empty", "duplicate", "mismatch", "no-private", "timestamp")) {
            var tree = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(valid);
            var generation = (com.fasterxml.jackson.databind.node.ObjectNode)
                    tree.get("generations").get(0);
            var keys = (com.fasterxml.jackson.databind.node.ArrayNode) generation.get("keys");
            switch (mutation) {
                case "version" -> tree.put("formatVersion", 1.0);
                case "unknown" -> tree.put("override", true);
                case "missing" -> tree.remove("revision");
                case "empty" -> tree.putArray("generations");
                case "duplicate" -> keys.set(1, keys.get(0).deepCopy());
                case "mismatch" ->
                    ((com.fasterxml.jackson.databind.node.ObjectNode) keys.get(0))
                            .put(
                                    "spki",
                                    java.util.Base64.getEncoder()
                                            .encodeToString(other.pairs()
                                                    .get(hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm.ED25519)
                                                    .getPublic()
                                                    .getEncoded()));
                case "no-private" -> ((com.fasterxml.jackson.databind.node.ObjectNode) keys.get(0)).putNull("pkcs8");
                case "timestamp" -> generation.remove("revokedAt");
                default -> throw new AssertionError("Unsupported test mutation.");
            }
            byte[] changed = json.writeValueAsBytes(tree);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(changed, "release-publisher"), mutation);
        }
        java.util.Arrays.fill(valid, (byte) 0);
    }
}
