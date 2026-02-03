package hu.balazsfalvi.pqclab.core.dsse;

/** Reports an invalid or resource-exhausting envelope without echoing its contents. */
public final class MalformedEnvelopeException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a safe validation failure.
     *
     * @param message the non-sensitive description of the failed constraint
     */
    public MalformedEnvelopeException(String message) {
        super(message);
    }

    /**
     * Creates a validation failure with its underlying parser error.
     *
     * @param message the non-sensitive description of the failed constraint
     * @param cause the underlying decoding failure
     */
    public MalformedEnvelopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
