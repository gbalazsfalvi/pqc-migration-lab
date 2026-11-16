package hu.balazsfalvi.pqclab.integration.app.trust;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.crypto.JcaSignatureEngine;
import hu.balazsfalvi.pqclab.integration.app.wire.StrictJson;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Imports a bounded public-only trust document; private encodings are never valid schema fields. */
public final class PublicTrustCodec {
    /** Bounds a complete sixteen-generation public trust history. */
    public static final int MAXIMUM_BYTES = 262_144;

    private final StrictJson json = new StrictJson(MAXIMUM_BYTES);

    /** Creates a strict parser without polymorphic deserialization. */
    public PublicTrustCodec() {}

    /**
     * Imports complete public verifier capabilities and validates their lineage.
     *
     * @param bytes the owner-provisioned public document
     * @param issuer the independently configured publisher owner
     * @return the validated immutable public state
     */
    public State decode(byte[] bytes, String issuer) {
        var root = json.read(bytes, Set.of("formatVersion", "issuer", "revision", "activeGenerationId", "generations"));
        if (integer(root, "formatVersion") != 1 || !issuer.equals(text(root, "issuer", 64))) {
            throw new IllegalArgumentException("Unsupported public trust format or owner.");
        }
        var rows = root.get("generations");
        if (rows == null
                || !rows.isArray()
                || rows.isEmpty()
                || rows.size() > RotatingPublicDirectory.MAXIMUM_GENERATIONS) {
            throw new IllegalArgumentException("A bounded public generation history is required.");
        }
        var generations = new ArrayList<PublicGeneration>();
        var ids = new HashSet<String>();
        var fingerprints = new HashSet<String>();
        int sequence = 0;
        for (var row : rows) {
            var node = object(
                    row, Set.of("id", "sequence", "createdAt", "retiringAt", "acceptUntil", "revokedAt", "keys"));
            String id = text(node, "id", 64);
            if (!UUID.fromString(id).toString().equals(id) || !ids.add(id) || integer(node, "sequence") != ++sequence) {
                throw new IllegalArgumentException("Public generations must be unique and ordered.");
            }
            Instant created = Instant.parse(text(node, "createdAt", 64));
            Instant retiring = instant(node, "retiringAt");
            Instant until = instant(node, "acceptUntil");
            Instant revoked = instant(node, "revokedAt");
            if ((retiring == null) != (until == null)
                    || (retiring != null
                            && (retiring.isBefore(created)
                                    || !until.equals(retiring.plusSeconds(RotatingPublicDirectory.GRACE_SECONDS))))
                    || (revoked != null && revoked.isBefore(created))) {
                throw new IllegalArgumentException("Invalid public lifecycle timestamps.");
            }
            var keys = node.get("keys");
            if (keys == null || !keys.isArray() || keys.size() != SignatureAlgorithm.values().length) {
                throw new IllegalArgumentException("Both public verification keys are required.");
            }
            var verifiers = new EnumMap<SignatureAlgorithm, MessageVerifier>(SignatureAlgorithm.class);
            var metadata = new ArrayList<KeyRegistryView.Key>();
            for (var entry : keys) {
                var key = object(entry, Set.of("algorithm", "spki"));
                var algorithm = SignatureAlgorithm.valueOf(text(key, "algorithm", 32));
                byte[] spki = Base64.getDecoder().decode(text(key, "spki", 16_384));
                var engine = JcaSignatureEngine.usingDefaultProvider(algorithm);
                var imported = engine.importPublicKey(spki);
                String digest = fingerprint(imported.getEncoded());
                if (!java.util.Arrays.equals(imported.getEncoded(), spki)
                        || verifiers.putIfAbsent(algorithm, engine.verifier(imported)) != null
                        || !fingerprints.add(digest)) {
                    throw new IllegalArgumentException("Public key identities must be canonical and unique.");
                }
                metadata.add(
                        new KeyRegistryView.Key(id + ":" + algorithm.name(), algorithm.name(), digest, spki.length));
            }
            generations.add(new PublicGeneration(id, sequence, created, retiring, until, revoked, verifiers, metadata));
        }
        String active = text(root, "activeGenerationId", 64);
        if (!generations.getLast().id().equals(active)
                || generations.getLast().retiringAt() != null
                || generations.stream().limit(generations.size() - 1L).anyMatch(g -> g.retiringAt() == null)) {
            throw new IllegalArgumentException("Only the newest public generation may be active.");
        }
        return new State(issuer, integer(root, "revision"), active, generations);
    }

    /** Rejects unknown fields in every nested object, including any attempted private-key field. */
    private ObjectNode object(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject())
            throw new IllegalArgumentException("A public metadata object is required.");
        return json.read(json.write((ObjectNode) node), fields);
    }

    /** Extracts bounded text without coercion or echoing input into diagnostics. */
    private String text(ObjectNode node, String field, int maximum) {
        var value = node.get(field);
        if (value == null
                || !value.isTextual()
                || value.textValue().isBlank()
                || value.textValue().length() > maximum) {
            throw new IllegalArgumentException("Invalid public metadata string.");
        }
        return value.textValue();
    }

    /** Extracts an integral positive format, revision or sequence counter. */
    private long integer(ObjectNode node, String field) {
        var value = node.get(field);
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToLong()
                || value.longValue() < 1
                || value.longValue() > 1_000_000) {
            throw new IllegalArgumentException("Invalid public metadata counter.");
        }
        return value.longValue();
    }

    /** Requires an explicit null or valid instant for optional lifecycle timestamps. */
    private Instant instant(ObjectNode node, String field) {
        var value = node.get(field);
        if (value == null) throw new IllegalArgumentException("Missing public lifecycle timestamp.");
        return value.isNull() ? null : Instant.parse(text(node, field, 64));
    }

    /** Fingerprints public SPKI bytes using platform cryptography. */
    private String fingerprint(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Public-key fingerprinting is unavailable.", failure);
        }
    }

    /**
     * Holds imported public capabilities only.
     *
     * @param issuer the configured owner
     * @param revision the durable revision
     * @param activeId the newest generation identifier
     * @param generations immutable public history
     */
    public record State(String issuer, long revision, String activeId, List<PublicGeneration> generations) {
        /** Snapshots public history before the file lease is used. */
        public State {
            generations = List.copyOf(generations);
        }

        /**
         * Creates a public in-memory projection while its caller still holds the cross-process file lease.
         *
         * @param clock the trusted observation clock
         * @return public verification and metadata capabilities
         */
        PublicKeyDirectory directory(Clock clock) {
            return new RotatingPublicDirectory(
                    issuer, clock, new ReentrantReadWriteLock(true), revision, activeId, generations);
        }
    }
}
