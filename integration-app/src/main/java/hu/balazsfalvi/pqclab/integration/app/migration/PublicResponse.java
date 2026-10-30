package hu.balazsfalvi.pqclab.integration.app.migration;

import com.fasterxml.jackson.databind.JsonNode;
import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import hu.balazsfalvi.pqclab.integration.app.wire.StrictJson;
import java.util.HashSet;
import java.util.Set;

/** Refuses unexpected response fields before exact HTTP body bytes can enter the public evidence directory. */
final class PublicResponse {
    private static final Set<String> EVIDENCE = Set.of("ed25519Valid", "mlDsa65Valid");
    private static final StrictJson JSON = new StrictJson(DsseLimits.MAX_JSON_BYTES);

    /** Prevents construction of a schema-only validator. */
    private PublicResponse() {}

    /** Names the small set of public response contracts used by this scenario. */
    enum Kind {
        /** Receiver-owned policy and time configuration. */
        STATUS,
        /** Public lifecycle metadata, with no encoded private material. */
        REGISTRY,
        /** A synthetic DSSE envelope. */
        ENVELOPE,
        /** Read-only Java policy comparisons. */
        EVALUATION,
        /** Durable public release evidence. */
        RECEIPT
    }

    /** Validates a complete public body, including nested objects, before it is retained. */
    static JsonNode read(Kind kind, int status, byte[] bytes) {
        try {
            if (status >= 400) {
                var problem = JSON.read(bytes, Set.of("type", "title", "status", "detail", "instance", "code"));
                fields(problem, Set.of("type", "title", "status", "detail", "instance", "code"));
                return problem;
            }
            return switch (kind) {
                case STATUS -> status(bytes);
                case ENVELOPE -> envelope(bytes);
                case REGISTRY -> registry(bytes);
                case EVALUATION -> evaluation(bytes);
                case RECEIPT -> receipt(bytes);
            };
        } catch (IllegalArgumentException failure) {
            // The original exception may contain an input fragment; do not retain it as a cause.
            throw new ScenarioFailure("PUBLIC_RESPONSE_REJECTED");
        }
    }

    /** Requires scalar configuration fields rather than unreviewed nested response data. */
    private static JsonNode status(byte[] bytes) {
        var allowed = Set.of("policy", "issuer", "audience", "maximumAge", "futureTolerance");
        var root = JSON.read(bytes, allowed);
        fields(root, allowed);
        return root;
    }

    /** Accepts only the synthetic publisher envelope and its public signature entries. */
    private static JsonNode envelope(byte[] bytes) {
        var root = JSON.read(bytes, Set.of("payloadType", "payload", "signatures"));
        fields(root, Set.of("payloadType", "payload", "signatures"), "signatures");
        for (JsonNode signature : array(root, "signatures")) {
            fields(signature, Set.of("keyid", "sig"));
        }
        return root;
    }

    /** Checks every generation and key object so future private fields cannot be exported accidentally. */
    private static JsonNode registry(byte[] bytes) {
        var root = JSON.read(
                bytes,
                Set.of(
                        "managed",
                        "revision",
                        "issuer",
                        "activeGenerationId",
                        "observedAt",
                        "gracePeriodSeconds",
                        "maximumGenerations",
                        "generations"));
        fields(
                root,
                Set.of(
                        "managed",
                        "revision",
                        "issuer",
                        "activeGenerationId",
                        "observedAt",
                        "gracePeriodSeconds",
                        "maximumGenerations",
                        "generations"),
                "generations");
        for (JsonNode generation : array(root, "generations")) {
            fields(
                    generation,
                    Set.of("id", "sequence", "state", "createdAt", "retiringAt", "acceptUntil", "revokedAt", "keys"),
                    "keys");
            for (JsonNode key : array(generation, "keys")) {
                fields(key, Set.of("id", "algorithm", "spkiSha256", "spkiBytes"));
            }
        }
        return root;
    }

    /** Checks both the public cryptographic observations and the complete policy validation paths. */
    private static JsonNode evaluation(byte[] bytes) {
        var root = JSON.read(
                bytes,
                Set.of(
                        "evaluatedAt",
                        "receiverPolicy",
                        "payloadSha256",
                        "envelopeBytes",
                        "payloadBytes",
                        "paeBytes",
                        "signatureEvidence",
                        "signatures",
                        "policies",
                        "trustRevision"));
        fields(
                root,
                Set.of(
                        "evaluatedAt",
                        "receiverPolicy",
                        "payloadSha256",
                        "envelopeBytes",
                        "payloadBytes",
                        "paeBytes",
                        "signatureEvidence",
                        "signatures",
                        "policies",
                        "trustRevision"),
                "signatureEvidence",
                "signatures",
                "policies");
        evidence(root.get("signatureEvidence"));
        for (JsonNode signature : array(root, "signatures")) {
            fields(signature, Set.of("index", "keyHint", "bytes", "verifiedAlgorithms"), "verifiedAlgorithms");
            for (JsonNode algorithm : array(signature, "verifiedAlgorithms")) {
                if (!algorithm.isTextual()) throw new IllegalArgumentException("Expected an algorithm name.");
            }
        }
        for (JsonNode policy : array(root, "policies")) {
            fields(policy, Set.of("policy", "accepted", "rejectionCode", "steps"), "steps");
            if (!policy.required("accepted").isBoolean())
                throw new IllegalArgumentException("Expected a policy verdict.");
            for (JsonNode step : array(policy, "steps")) {
                fields(step, Set.of("id", "label", "status", "detail"));
            }
        }
        return root;
    }

    /** Checks immutable first-acceptance evidence alongside the public receipt fields. */
    private static JsonNode receipt(byte[] bytes) {
        var root = JSON.read(
                bytes,
                Set.of(
                        "status",
                        "receiptId",
                        "eventId",
                        "issuer",
                        "artifact",
                        "version",
                        "acceptedAt",
                        "enforcedPolicy",
                        "acceptedPolicy",
                        "acceptedEvidence"));
        fields(
                root,
                Set.of(
                        "status",
                        "receiptId",
                        "eventId",
                        "issuer",
                        "artifact",
                        "version",
                        "acceptedAt",
                        "enforcedPolicy",
                        "acceptedPolicy",
                        "acceptedEvidence"),
                "acceptedEvidence");
        evidence(root.get("acceptedEvidence"));
        return root;
    }

    /** Requires actual boolean signature verdicts without scalar coercion. */
    private static void evidence(JsonNode node) {
        fields(node, EVIDENCE);
        for (String name : EVIDENCE) {
            if (!node.required(name).isBoolean()) throw new IllegalArgumentException("Expected a signature verdict.");
        }
    }

    /** Requires an object with no unreviewed properties, including inside nested public DTOs. */
    private static void fields(JsonNode node, Set<String> allowed, String... containers) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("Expected a public object.");
        var actual = new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!allowed.containsAll(actual)) throw new IllegalArgumentException("Unexpected public field.");
        // Public schemas contain scalar values or the explicitly traversed arrays/objects only.
        var traversed = Set.of(containers);
        node.fieldNames().forEachRemaining(name -> {
            if (node.get(name).isContainerNode() && !traversed.contains(name)) {
                throw new IllegalArgumentException("Unexpected nested public data.");
            }
        });
    }

    /** Requires an array rather than silently treating a missing or scalar field as an empty sequence. */
    private static JsonNode array(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isArray()) throw new IllegalArgumentException("Expected a public array.");
        return value;
    }
}
