package hu.balazsfalvi.pqclab.integration.app.publisher;

import hu.balazsfalvi.pqclab.integration.app.http.ApiFailure;
import hu.balazsfalvi.pqclab.integration.app.wire.StrictJson;
import java.util.Set;

/** Parses the deliberately small publisher API without allowing identity or policy overrides. */
public final class PublishRequestCodec {
    private final StrictJson json = new StrictJson();

    /** Creates a reusable private request parser. */
    public PublishRequestCodec() {}

    /**
     * Reads release details, defaulting the publisher's signature mode to dual.
     *
     * @param body the bounded UTF-8 request
     * @return the explicit publisher request
     */
    public PublishRequest read(byte[] body) {
        try {
            var root = json.read(body, Set.of("artifact", "version", "sha256", "signingMode"));
            SigningMode mode =
                    root.has("signingMode") ? SigningMode.valueOf(json.text(root, "signingMode")) : SigningMode.DUAL;
            return new PublishRequest(
                    json.text(root, "artifact"), json.text(root, "version"), json.text(root, "sha256"), mode);
        } catch (IllegalArgumentException failure) {
            throw new ApiFailure(400, "INVALID_PUBLISH_REQUEST");
        }
    }
}
