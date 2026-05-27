package hu.balazsfalvi.pqclab.integration.core;

/** Abstracts the non-secret payload fingerprint used by durable idempotency. */
@FunctionalInterface
public interface PayloadDigester {
    /**
     * Fingerprints the exact authenticated payload bytes.
     *
     * @param body the verified byte snapshot
     * @return the lowercase SHA-256 fingerprint
     */
    String digest(byte[] body);
}
