package hu.balazsfalvi.pqclab.core.crypto;

/** Verifies bytes against one public key already selected by the trusted caller. */
@FunctionalInterface
public interface MessageVerifier {
    /**
     * Tests the signature without modifying either input.
     *
     * @param message the exact authenticated message
     * @param signature the candidate signature
     * @return whether the signature verifies against this verifier's trusted key
     */
    boolean verify(byte[] message, byte[] signature);
}
