package hu.balazsfalvi.pqclab.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import hu.balazsfalvi.pqclab.core.dsse.DsseSignature;
import hu.balazsfalvi.pqclab.core.dsse.MalformedEnvelopeException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

/** Parses bounded DSSE JSON without interpreting, normalising or trusting its payload. */
public final class DsseJsonCodec {
    private final ObjectMapper mapper;

    /** Creates a reusable codec whose parser configuration is frozen before use. */
    public DsseJsonCodec() {
        var constraints = StreamReadConstraints.builder()
                .maxNestingDepth(16)
                .maxStringLength(DsseLimits.MAX_JSON_BYTES)
                .maxNumberLength(256)
                .build();
        var factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .streamReadConstraints(constraints)
                .build();
        this.mapper = new ObjectMapper(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    /**
     * Reads a UTF-8 DSSE envelope and ignores unknown extension fields as DSSE requires.
     *
     * @param json the complete encoded envelope
     * @return the immutable, validated envelope
     * @throws MalformedEnvelopeException if parsing, shape validation or resource bounds fail
     */
    public DsseEnvelope read(byte[] json) {
        Objects.requireNonNull(json, "json");
        if (json.length > DsseLimits.MAX_JSON_BYTES) {
            throw new MalformedEnvelopeException("The JSON envelope exceeds the configured size limit.");
        }
        JsonNode root;
        try {
            String text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(json))
                    .toString();
            root = mapper.readTree(text);
        } catch (IOException parserFailure) {
            // Parser diagnostics can echo payload fragments; the public error deliberately does not.
            throw new MalformedEnvelopeException("The envelope is not a valid UTF-8 JSON document.");
        }
        if (root == null || !root.isObject()) {
            throw new MalformedEnvelopeException("The envelope must be a JSON object.");
        }
        String payloadType = requiredText(root, "payloadType");
        String payload = requiredText(root, "payload");
        JsonNode entries = root.get("signatures");
        if (entries == null || !entries.isArray() || entries.size() > DsseLimits.MAX_SIGNATURES) {
            throw new MalformedEnvelopeException("The signatures field must be a bounded JSON array.");
        }
        var signatures = new ArrayList<DsseSignature>(entries.size());
        for (JsonNode entry : entries) {
            if (!entry.isObject()) {
                throw new MalformedEnvelopeException("Every signature entry must be a JSON object.");
            }
            String keyId = entry.has("keyid") ? requiredText(entry, "keyid") : "";
            signatures.add(new DsseSignature(keyId, requiredText(entry, "sig")));
        }
        return new DsseEnvelope(payloadType, payload, signatures);
    }

    /**
     * Writes the standard envelope without serialising or inspecting the payload itself.
     *
     * @param envelope the immutable envelope to encode
     * @return its UTF-8 JSON representation
     */
    public byte[] write(DsseEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        var root = mapper.createObjectNode();
        root.put("payloadType", envelope.payloadType());
        root.put("payload", envelope.payload());
        var entries = root.putArray("signatures");
        for (DsseSignature signature : envelope.signatures()) {
            entries.addObject().put("keyid", signature.keyId()).put("sig", signature.signature());
        }
        try {
            return mapper.writeValueAsBytes(root);
        } catch (IOException encodingFailure) {
            throw new IllegalStateException("The validated envelope could not be encoded.", encodingFailure);
        }
    }

    /** Extracts a required string without coercing numbers, arrays or null values. */
    private String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) {
            throw new MalformedEnvelopeException("A required envelope field is missing or is not a string: " + field);
        }
        return value.textValue();
    }
}
