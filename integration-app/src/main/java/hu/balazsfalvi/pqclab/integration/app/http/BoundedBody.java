package hu.balazsfalvi.pqclab.integration.app.http;

import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/** Bounds request bodies even when clients omit Content-Length or use chunked transfer encoding. */
public final class BoundedBody {
    /** Prevents construction of a transport utility. */
    private BoundedBody() {}

    /**
     * Reads at most the wire limit plus one byte, before any JSON parsing or signature operation.
     *
     * @param request the incoming servlet request
     * @return the complete bounded body
     */
    public static byte[] read(HttpServletRequest request) {
        if (request.getContentLengthLong() > DsseLimits.MAX_JSON_BYTES) {
            throw new ApiFailure(413, "BODY_TOO_LARGE");
        }
        try {
            byte[] body = request.getInputStream().readNBytes(DsseLimits.MAX_JSON_BYTES + 1);
            if (body.length > DsseLimits.MAX_JSON_BYTES) {
                throw new ApiFailure(413, "BODY_TOO_LARGE");
            }
            return body;
        } catch (IOException failure) {
            throw new ApiFailure(400, "REQUEST_READ_FAILED");
        }
    }
}
