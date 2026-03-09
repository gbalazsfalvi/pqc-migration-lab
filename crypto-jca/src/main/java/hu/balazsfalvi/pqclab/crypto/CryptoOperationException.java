package hu.balazsfalvi.pqclab.crypto;

/** Separates provider or key-configuration failures from an invalid candidate signature. */
public final class CryptoOperationException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates an operational failure without including key material in its message.
     *
     * @param message the non-sensitive operation description
     * @param cause the underlying JCA failure
     */
    public CryptoOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
