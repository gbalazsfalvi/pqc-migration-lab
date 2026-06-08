package hu.balazsfalvi.pqclab.integration.app.http;

/** Describes a safe transport error without retaining request bodies or upstream diagnostics. */
public final class ApiFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** Preserves the adapter-owned HTTP status when the exception is serialized. */
    private final int status;
    /** Preserves the safe reason identifier when the exception is serialized. */
    private final String code;

    /**
     * Creates an adapter-owned HTTP error.
     *
     * @param status the response status
     * @param code the stable safe error identifier
     */
    public ApiFailure(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    /**
     * Returns the response status.
     *
     * @return the HTTP status
     */
    public int status() {
        return status;
    }

    /**
     * Returns the public reason identifier.
     *
     * @return the safe error code
     */
    public String code() {
        return code;
    }
}
