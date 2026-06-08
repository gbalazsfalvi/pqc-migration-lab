package hu.balazsfalvi.pqclab.integration.app.wire;

import hu.balazsfalvi.pqclab.integration.core.ReceptionFailure;
import hu.balazsfalvi.pqclab.integration.core.ReleaseEvent;
import hu.balazsfalvi.pqclab.integration.core.ReleaseEventCodec;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Implements the release-event schema without coercion, canonicalisation or secret diagnostics. */
public final class ReleaseJsonCodec implements ReleaseEventCodec {
    private static final Set<String> FIELDS =
            Set.of("schemaVersion", "eventId", "issuer", "audience", "issuedAt", "artifact", "version", "sha256");
    private final StrictJson json = new StrictJson();

    /** Creates a reusable adapter with a private, frozen parser configuration. */
    public ReleaseJsonCodec() {}

    /** {@inheritDoc} */
    @Override
    public byte[] encode(ReleaseEvent event) {
        var root = json.object();
        root.put("schemaVersion", event.schemaVersion());
        root.put("eventId", event.eventId().toString());
        root.put("issuer", event.issuer());
        root.put("audience", event.audience());
        root.put("issuedAt", event.issuedAt().toString());
        root.put("artifact", event.artifact());
        root.put("version", event.version());
        root.put("sha256", event.sha256());
        return json.write(root);
    }

    /** {@inheritDoc} */
    @Override
    public ReleaseEvent decode(byte[] verifiedBody) {
        try {
            var root = json.read(verifiedBody, FIELDS);
            var schema = root.get("schemaVersion");
            if (schema == null || !schema.isInt()) {
                throw new IllegalArgumentException("The schema version must be an integer.");
            }
            String id = json.text(root, "eventId");
            UUID eventId = UUID.fromString(id);
            if (!eventId.toString().equals(id)) {
                throw new IllegalArgumentException("The event identifier must use canonical UUID syntax.");
            }
            return new ReleaseEvent(
                    schema.intValue(),
                    eventId,
                    json.text(root, "issuer"),
                    json.text(root, "audience"),
                    Instant.parse(json.text(root, "issuedAt")),
                    json.text(root, "artifact"),
                    json.text(root, "version"),
                    json.text(root, "sha256"));
        } catch (RuntimeException failure) {
            throw new ReceptionFailure(ReceptionFailure.Code.MALFORMED_EVENT);
        }
    }
}
