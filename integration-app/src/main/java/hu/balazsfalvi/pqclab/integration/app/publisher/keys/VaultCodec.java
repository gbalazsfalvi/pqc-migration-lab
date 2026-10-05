package hu.balazsfalvi.pqclab.integration.app.publisher.keys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.crypto.JcaSignatureEngine;
import hu.balazsfalvi.pqclab.integration.app.wire.StrictJson;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Set;

/** Encodes publisher-only plaintext exclusively inside the authenticated encrypted vault boundary. */
final class VaultCodec {
    private final StrictJson json = new StrictJson(1_048_576);

    /** Creates a bounded codec without enabling polymorphic object deserialization. */
    VaultCodec() {}

    /** Serializes validated key material only for immediate encryption, never HTTP or a report. */
    byte[] encode(VaultState state) {
        var root = json.object();
        root.put("formatVersion", 1);
        root.put("issuer", state.issuer());
        root.put("revision", state.revision());
        root.put("activeGenerationId", state.activeId());
        var generations = root.putArray("generations");
        for (var generation : state.generations()) {
            var node = generations.addObject();
            node.put("id", generation.id());
            node.put("sequence", generation.sequence());
            node.put("createdAt", generation.createdAt().toString());
            node.put("retiringAt", text(generation.retiringAt()));
            node.put("acceptUntil", text(generation.acceptUntil()));
            node.put("revokedAt", text(generation.revokedAt()));
            var keys = node.putArray("keys");
            for (var algorithm : SignatureAlgorithm.values()) {
                var key = keys.addObject();
                key.put("algorithm", algorithm.name());
                key.put(
                        "spki",
                        Base64.getEncoder()
                                .encodeToString(generation
                                        .pairs()
                                        .get(algorithm)
                                        .getPublic()
                                        .getEncoded()));
                var privateKey = generation.pairs().get(algorithm).getPrivate();
                if (privateKey == null) {
                    key.putNull("pkcs8");
                } else {
                    byte[] encoded = privateKey.getEncoded();
                    try {
                        key.put("pkcs8", Base64.getEncoder().encodeToString(encoded));
                    } finally {
                        java.util.Arrays.fill(encoded, (byte) 0);
                    }
                }
            }
        }
        return json.write(root);
    }

    /** Loads strict authenticated state and proves every imported public/private pair corresponds. */
    VaultState decode(byte[] plaintext, String expectedIssuer) {
        var root = json.read(
                plaintext, Set.of("formatVersion", "issuer", "revision", "activeGenerationId", "generations"));
        if (integer(root, "formatVersion") != 1 || !expectedIssuer.equals(string(root, "issuer", 64))) {
            throw new IllegalArgumentException("The key vault has an unsupported format or owner.");
        }
        JsonNode rows = root.get("generations");
        if (rows == null || !rows.isArray() || rows.isEmpty() || rows.size() > 16) {
            throw new IllegalArgumentException("A bounded generation array is required.");
        }
        var generations = new ArrayList<KeyGeneration>();
        for (JsonNode row : rows) {
            var generation = object(
                    row, Set.of("id", "sequence", "createdAt", "retiringAt", "acceptUntil", "revokedAt", "keys"));
            var keys = generation.get("keys");
            if (keys == null || !keys.isArray() || keys.size() != 2)
                throw new IllegalArgumentException("Both key encodings are required.");
            var pairs = new EnumMap<SignatureAlgorithm, KeyPair>(SignatureAlgorithm.class);
            for (JsonNode keyNode : keys) {
                var key = object(keyNode, Set.of("algorithm", "spki", "pkcs8"));
                var algorithm = SignatureAlgorithm.valueOf(string(key, "algorithm", 32));
                var engine = JcaSignatureEngine.usingDefaultProvider(algorithm);
                var publicKey = engine.importPublicKey(Base64.getDecoder().decode(string(key, "spki", 16_384)));
                var encodedNode = key.get("pkcs8");
                if (encodedNode == null) throw new IllegalArgumentException("A private-key state is required.");
                java.security.PrivateKey privateKey = null;
                if (!encodedNode.isNull()) {
                    byte[] encoded = Base64.getDecoder().decode(string(key, "pkcs8", 32_768));
                    try {
                        privateKey = engine.importPrivateKey(encoded);
                    } finally {
                        java.util.Arrays.fill(encoded, (byte) 0);
                    }
                    byte[] challenge = "pqc-key-vault-pair-check/v1".getBytes(StandardCharsets.UTF_8);
                    if (!engine.verifier(publicKey)
                            .verify(challenge, engine.signer("", privateKey).sign(challenge))) {
                        throw new IllegalArgumentException("Private and public keys do not correspond.");
                    }
                }
                if (pairs.putIfAbsent(algorithm, new KeyPair(publicKey, privateKey)) != null) {
                    throw new IllegalArgumentException("Key encodings must form unique corresponding pairs.");
                }
            }
            generations.add(new KeyGeneration(
                    string(generation, "id", 64),
                    Math.toIntExact(integer(generation, "sequence")),
                    Instant.parse(string(generation, "createdAt", 64)),
                    instant(generation, "retiringAt"),
                    instant(generation, "acceptUntil"),
                    instant(generation, "revokedAt"),
                    pairs));
        }
        return new VaultState(
                expectedIssuer, integer(root, "revision"), string(root, "activeGenerationId", 64), generations);
    }

    /** Reuses duplicate-key and field allowlisting rules for every nested object. */
    private ObjectNode object(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("A key metadata object is required.");
        return json.read(json.write((ObjectNode) node), fields);
    }

    /** Reads a bounded string without coercion or input echoing. */
    private String string(ObjectNode node, String field, int maximum) {
        var value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().length() > maximum) {
            throw new IllegalArgumentException("A key metadata string is invalid.");
        }
        return value.textValue();
    }

    /** Reads a bounded integral revision or sequence without floating-point coercion. */
    private long integer(ObjectNode node, String field) {
        var value = node.get(field);
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToLong()
                || value.longValue() < 1
                || value.longValue() > 1_000_000) {
            throw new IllegalArgumentException("A key metadata counter is invalid.");
        }
        return value.longValue();
    }

    /** Requires an explicit null or valid timestamp for optional lifecycle fields. */
    private Instant instant(ObjectNode node, String field) {
        var value = node.get(field);
        if (value == null) throw new IllegalArgumentException("A lifecycle timestamp is missing.");
        return value.isNull() ? null : Instant.parse(string(node, field, 64));
    }

    /** Preserves absent lifecycle timestamps as explicit JSON null. */
    private String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
