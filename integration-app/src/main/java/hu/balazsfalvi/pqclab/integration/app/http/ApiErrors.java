package hu.balazsfalvi.pqclab.integration.app.http;

import hu.balazsfalvi.pqclab.core.dsse.MalformedEnvelopeException;
import hu.balazsfalvi.pqclab.integration.core.ReceptionFailure;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps transport, policy and operational failures to bounded public problem documents. */
@RestControllerAdvice
public final class ApiErrors {
    /** Creates a stateless exception mapper. */
    public ApiErrors() {}

    /**
     * Maps authenticated business rejections without echoing their input.
     *
     * @param failure the safe domain rejection
     * @return a stable problem document
     */
    @ExceptionHandler(ReceptionFailure.class)
    public ProblemDetail rejection(ReceptionFailure failure) {
        int status = switch (failure.code()) {
            case POLICY_REJECTED, ISSUER_MISMATCH -> 403;
            case EVENT_ID_CONFLICT -> 409;
            default -> 422;
        };
        return problem(status, failure.code().name());
    }

    /**
     * Treats malformed DSSE as a transport error before business processing.
     *
     * @param failure the protocol parser rejection
     * @return a safe malformed-envelope response
     */
    @ExceptionHandler(MalformedEnvelopeException.class)
    public ProblemDetail envelope(MalformedEnvelopeException failure) {
        return problem(400, "MALFORMED_ENVELOPE");
    }

    /**
     * Exposes only adapter-owned status and reason values.
     *
     * @param failure the safe adapter failure
     * @return the corresponding problem document
     */
    @ExceptionHandler(ApiFailure.class)
    public ProblemDetail transport(ApiFailure failure) {
        return problem(failure.status(), failure.code());
    }

    /**
     * Rejects malformed typed path parameters as client errors.
     *
     * @param failure the framework conversion failure
     * @return a safe invalid-parameter response
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail parameter(MethodArgumentTypeMismatchException failure) {
        return problem(400, "INVALID_PARAMETER");
    }

    /**
     * Fails closed on provider, persistence or unexpected operational errors.
     *
     * @param failure the internal failure, deliberately excluded from the response
     * @return a generic unavailable response
     */
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail unavailable(RuntimeException failure) {
        return problem(503, "INTEGRATION_UNAVAILABLE");
    }

    /** Builds RFC-style problem output containing only server-owned identifiers. */
    private ProblemDetail problem(int status, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("urn:pqclab:problem:" + code.toLowerCase(java.util.Locale.ROOT)));
        problem.setTitle("The integration request could not be completed.");
        problem.setProperty("code", code);
        return problem;
    }
}
