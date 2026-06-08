package hu.balazsfalvi.pqclab.integration.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.balazsfalvi.pqclab.integration.app.http.ApiFailure;
import hu.balazsfalvi.pqclab.integration.app.publisher.PublishRequestCodec;
import hu.balazsfalvi.pqclab.integration.app.wire.ReleaseJsonCodec;
import hu.balazsfalvi.pqclab.integration.app.wire.StrictJson;
import hu.balazsfalvi.pqclab.integration.core.ReceptionFailure;
import hu.balazsfalvi.pqclab.integration.core.ReleaseEvent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Guards the separate business JSON schema against coercion and ambiguous identifiers. */
final class BusinessWireTest {
    /** Unsupported shapes and parser errors must expose only safe, fixed diagnostics. */
    @ParameterizedTest
    @ValueSource(strings = {"", "[]", "null", "{\"unexpected\":1}", "{\"a\":1} {}", "{\"a\":", "{\"a\":true}"})
    void rejectsInvalidBusinessShape(String document) {
        StrictJson codec = new StrictJson();
        assertThrows(IllegalArgumentException.class, () -> {
            var root = codec.read(document.getBytes(StandardCharsets.UTF_8), Set.of("a"));
            codec.text(root, "a");
        });
    }

    /** UTF-8 and resource bounds are checked before tree construction. */
    @Test
    void rejectsInvalidEncodingOversizedInputAndLongFields() {
        StrictJson codec = new StrictJson();
        assertThrows(IllegalArgumentException.class, () -> codec.read(new byte[] {(byte) 0xc3, 0x28}, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> codec.read(new byte[65_537], Set.of()));
        var object = codec.object().put("a", "x".repeat(513));
        assertThrows(IllegalArgumentException.class, () -> codec.text(object, "a"));
        assertThrows(IllegalArgumentException.class, () -> codec.text(codec.object(), "a"));
        ApiFailure failure = assertThrows(
                ApiFailure.class,
                () -> new PublishRequestCodec().read("{\"artifact\":1}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(400, failure.status());
        assertEquals("INVALID_PUBLISH_REQUEST", failure.code());
    }

    /** Equivalent but noncanonical UUID spellings cannot create ambiguous application identities. */
    @Test
    void requiresCanonicalUuidAndPreservesValidEvents() {
        ReleaseJsonCodec codec = new ReleaseJsonCodec();
        ReleaseEvent event = new ReleaseEvent(
                1,
                UUID.fromString("abcdefab-abcd-4abc-8abc-abcdefabcdef"),
                "publisher",
                "urn:example:receiver",
                Instant.parse("2026-11-13T08:00:00Z"),
                "service",
                "1",
                "a".repeat(64));
        byte[] encoded = codec.encode(event);
        assertEquals(event, codec.decode(encoded));
        String modified = new String(encoded, StandardCharsets.UTF_8)
                .replace(event.eventId().toString(), event.eventId().toString().toUpperCase(java.util.Locale.ROOT));
        assertEquals(
                ReceptionFailure.Code.MALFORMED_EVENT,
                assertThrows(ReceptionFailure.class, () -> codec.decode(modified.getBytes(StandardCharsets.UTF_8)))
                        .code());
        assertTrue(new String(encoded, StandardCharsets.UTF_8).contains("\"schemaVersion\":1"));
    }
}
