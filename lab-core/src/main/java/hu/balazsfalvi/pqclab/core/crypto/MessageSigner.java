package hu.balazsfalvi.pqclab.core.crypto;

/** Signs exact message bytes using a key supplied by the trusted application. */
public interface MessageSigner {
    /**
     * Returns the optional, unauthenticated DSSE key lookup hint.
     *
     * @return the hint, or an empty string when no hint is supplied
     */
    String keyId();

    /**
     * Signs the message without changing the caller's bytes.
     *
     * @param message the exact bytes to authenticate
     * @return a newly allocated signature
     */
    byte[] sign(byte[] message);
}
