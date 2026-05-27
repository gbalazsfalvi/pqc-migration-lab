package hu.balazsfalvi.pqclab.integration.core;

import java.util.Optional;
import java.util.UUID;

/** Atomically records a release once, including across duplicate deliveries and receiver restarts. */
public interface ReleaseLedger {
    /**
     * Inserts one business result or returns the unchanged result for identical payload bytes.
     *
     * @param candidate the fully authenticated and validated release
     * @return whether a new result was created, with the stable persisted result
     * @throws ReceptionFailure if an identifier already belongs to different payload bytes
     */
    Outcome record(StoredRelease candidate);

    /**
     * Reads an accepted release in its trusted publisher namespace.
     *
     * @param issuer the trusted publisher identity
     * @param eventId the signed event identifier
     * @return the persisted business result, if present
     */
    Optional<StoredRelease> find(String issuer, UUID eventId);

    /**
     * Distinguishes first acceptance from a suppressed duplicate delivery.
     *
     * @param release the original persisted business result
     * @param created whether this call created that result
     */
    record Outcome(StoredRelease release, boolean created) {}
}
