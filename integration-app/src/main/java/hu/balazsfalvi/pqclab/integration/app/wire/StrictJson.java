package hu.balazsfalvi.pqclab.integration.app.wire;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Centralises strict business-document parsing without registering a web serialization mapper. */
public final class StrictJson {
    private final ObjectMapper mapper;
    private final int maximumBytes;

    /** Freezes parser limits and ambiguity checks before sharing the codec across requests. */
    public StrictJson() {
        this(DsseLimits.MAX_PAYLOAD_BYTES);
    }

    /**
     * Creates a parser for bounded workbench wrappers while retaining strict business defaults.
     *
     * @param maximumBytes the positive document limit, at most the DSSE wire limit
     */
    public StrictJson(int maximumBytes) {
        if (maximumBytes < 1 || maximumBytes > DsseLimits.MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Use a bounded JSON document limit.");
        }
        this.maximumBytes = maximumBytes;
        var factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(8)
                        .maxStringLength(maximumBytes)
                        .maxNumberLength(32)
                        .build())
                .build();
        mapper = new ObjectMapper(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    /**
     * Parses one bounded UTF-8 object and rejects unsupported business fields.
     *
     * @param bytes the complete business document
     * @param allowedFields the supported schema fields
     * @return the strict object tree
     * @throws IllegalArgumentException if parsing or schema shape validation fails
     */
    public ObjectNode read(byte[] bytes, Set<String> allowedFields) {
        if (bytes.length > maximumBytes) {
            throw new IllegalArgumentException("The business document exceeds its size limit.");
        }
        try {
            String text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            JsonNode root = mapper.readTree(text);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("The business document must be an object.");
            }
            Set<String> actual = new HashSet<>();
            root.fieldNames().forEachRemaining(actual::add);
            if (!allowedFields.containsAll(actual)) {
                throw new IllegalArgumentException("The business document contains unsupported fields.");
            }
            return (ObjectNode) root;
        } catch (IOException failure) {
            // Parser exceptions may retain fragments of the untrusted document.
            throw new IllegalArgumentException("The business document is not valid UTF-8 JSON.");
        }
    }

    /**
     * Extracts a required string without scalar coercion.
     *
     * @param root the parsed object
     * @param field the schema-owned field name
     * @return the original string
     */
    public String text(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().length() > 512) {
            throw new IllegalArgumentException("A required business field is missing or invalid.");
        }
        return value.textValue();
    }

    /**
     * Creates a new object for explicit, stable publisher serialization.
     *
     * @return an empty object node
     */
    public ObjectNode object() {
        return mapper.createObjectNode();
    }

    /**
     * Serialises one validated object before its bytes are signed.
     *
     * @param root the schema-owned object
     * @return its UTF-8 JSON bytes
     */
    public byte[] write(ObjectNode root) {
        try {
            return mapper.writeValueAsBytes(root);
        } catch (IOException failure) {
            throw new IllegalStateException("The business document could not be encoded.", failure);
        }
    }
}
