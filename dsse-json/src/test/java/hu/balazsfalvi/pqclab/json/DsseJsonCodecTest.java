package hu.balazsfalvi.pqclab.json;

import static org.junit.jupiter.api.Assertions.*;

import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import hu.balazsfalvi.pqclab.core.dsse.DsseSignature;
import hu.balazsfalvi.pqclab.core.dsse.MalformedEnvelopeException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests the hostile-input boundary independently of any signature provider. */
class DsseJsonCodecTest {
    private final DsseJsonCodec codec = new DsseJsonCodec();

    /** Envelope transport must preserve original binary bytes and signature entries. */
    @Test
    void roundTripsWithoutReinterpretingPayload() {
        var expected = new DsseEnvelope("application/example", "-_8", List.of(new DsseSignature("key", "AA==")));
        assertEquals(expected, codec.read(codec.write(expected)));
    }

    /** DSSE extensions are ignored, while an omitted optional key hint becomes empty. */
    @Test
    void acceptsForwardCompatibleFieldsAndOptionalHints() {
        var envelope = codec.read(utf8("""
                {"payloadType":"t","payload":"","future":{"version":2},
                 "signatures":[{"sig":"","anotherExtension":true}]}
                """));
        assertEquals("", envelope.signatures().getFirst().keyId());
    }

    /** Duplicate fields and trailing documents would make interpretation ambiguous. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"payloadType\":\"t\",\"payload\":\"\",\"payload\":\"YQ==\",\"signatures\":[]}",
                "{\"payloadType\":\"t\",\"payload\":\"\",\"signatures\":[{\"sig\":\"\",\"sig\":\"AA==\"}]}",
                "{\"payloadType\":\"t\",\"payload\":\"\",\"signatures\":[]} {}",
                "{",
                "[]",
                "null",
                "",
                "{\"payloadType\":1,\"payload\":\"\",\"signatures\":[]}",
                "{\"payloadType\":\"t\",\"signatures\":[]}",
                "{\"payloadType\":\"t\",\"payload\":\"\",\"signatures\":null}",
                "{\"payloadType\":\"t\",\"payload\":\"\",\"signatures\":[1]}",
                "{\"payloadType\":\"t\",\"payload\":\"\",\"signatures\":[{\"sig\":\"\" ,\"keyid\":null}]}",
                "{\"payloadType\":\"t\",\"payload\":\"%%%\",\"signatures\":[]}"
            })
    void rejectsMalformedDocuments(String input) {
        assertThrows(MalformedEnvelopeException.class, () -> codec.read(utf8(input)));
    }

    /** The byte-size guard runs before parsing and UTF-8 decoding is strict. */
    @Test
    void rejectsResourceAbuseAndInvalidUtf8() {
        assertThrows(MalformedEnvelopeException.class, () -> codec.read(new byte[DsseLimits.MAX_JSON_BYTES + 1]));
        assertThrows(MalformedEnvelopeException.class, () -> codec.read(new byte[] {(byte) 0xC3, 0x28}));
        String nested = "{\"extension\":" + "[".repeat(20) + "0" + "]".repeat(20) + "}";
        assertThrows(MalformedEnvelopeException.class, () -> codec.read(utf8(nested)));
        String tooMany = "{\"payloadType\":\"t\",\"payload\":\"\",\"signatures\":["
                + String.join(",", java.util.Collections.nCopies(5, "{\"sig\":\"\"}")) + "]}";
        assertThrows(MalformedEnvelopeException.class, () -> codec.read(utf8(tooMany)));
    }

    /** Parsing errors exposed to callers must not echo sensitive input fragments. */
    @Test
    void doesNotEchoParserInput() {
        var failure =
                assertThrows(MalformedEnvelopeException.class, () -> codec.read(utf8("{ secret-marker-never-echoed")));
        assertFalse(failure.toString().contains("secret-marker"));
        assertNull(failure.getCause());
    }

    /** Encodes test JSON with an explicit transport charset. */
    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
