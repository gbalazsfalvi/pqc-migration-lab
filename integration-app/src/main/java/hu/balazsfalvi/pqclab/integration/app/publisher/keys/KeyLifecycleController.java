package hu.balazsfalvi.pqclab.integration.app.publisher.keys;

import hu.balazsfalvi.pqclab.integration.app.http.ApiFailure;
import hu.balazsfalvi.pqclab.integration.app.http.BoundedBody;
import hu.balazsfalvi.pqclab.integration.app.trust.KeyRegistryView;
import hu.balazsfalvi.pqclab.integration.app.wire.StrictJson;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Restricts lifecycle writes to publisher-side local control endpoints with optimistic revision fencing. */
@RestController
public final class KeyLifecycleController {
    private final ObjectProvider<KeyLifecycleManager> manager;
    private final StrictJson json = new StrictJson(1024);

    /**
     * Keeps private administration outside the receiver context.
     *
     * @param manager the optional managed publisher capability; deterministic probes remain unmanaged
     */
    public KeyLifecycleController(ObjectProvider<KeyLifecycleManager> manager) {
        this.manager = manager;
    }

    /**
     * Creates the next complete signing generation while preserving the fixed migration policy.
     *
     * @param request the strict expected-revision object
     * @return public metadata after durable rotation
     */
    @PostMapping(
            path = "/api/workbench/keys/rotate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public KeyRegistryView rotate(HttpServletRequest request) {
        var input = parse(request, Set.of("expectedRevision"));
        return managed().rotate(revision(input));
    }

    /**
     * Irreversibly revokes one configured generation without removing historical receipts.
     *
     * @param request the strict revision and generation object
     * @return public metadata after durable revocation
     */
    @PostMapping(
            path = "/api/workbench/keys/revoke",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public KeyRegistryView revoke(HttpServletRequest request) {
        var input = parse(request, Set.of("expectedRevision", "generationId"));
        String id;
        try {
            id = json.text(input, "generationId");
            if (!UUID.fromString(id).toString().equals(id))
                throw new IllegalArgumentException("A canonical ID is required.");
        } catch (IllegalArgumentException failure) {
            throw new ApiFailure(400, "INVALID_KEY_OPERATION");
        }
        return managed().revoke(revision(input), id);
    }

    /** Rejects unknown, duplicated or oversized control fields before any signing or persistence. */
    private com.fasterxml.jackson.databind.node.ObjectNode parse(HttpServletRequest request, Set<String> fields) {
        try {
            return json.read(BoundedBody.read(request), fields);
        } catch (IllegalArgumentException failure) {
            throw new ApiFailure(400, "INVALID_KEY_OPERATION");
        }
    }

    /** Reads an exact integral revision without accepting string or floating-point coercion. */
    private long revision(com.fasterxml.jackson.databind.node.ObjectNode root) {
        var value = root.get("expectedRevision");
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToLong()
                || value.longValue() < 1
                || value.longValue() > 1_000_000) {
            throw new ApiFailure(400, "INVALID_KEY_OPERATION");
        }
        return value.longValue();
    }

    /** Prevents legacy ephemeral probes from pretending they support durable lifecycle management. */
    private KeyLifecycleManager managed() {
        var selected = manager.getIfAvailable();
        if (selected == null) throw new ApiFailure(409, "KEY_LIFECYCLE_UNMANAGED");
        return selected;
    }
}
