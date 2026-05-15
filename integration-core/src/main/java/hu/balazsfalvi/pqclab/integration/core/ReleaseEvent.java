package hu.balazsfalvi.pqclab.integration.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Describes a synthetic software release, including all security-relevant delivery claims.
 *
 * @param schemaVersion the supported business schema version
 * @param eventId the signed, publisher-scoped idempotency identifier
 * @param issuer the claimed publisher, checked against the trusted key owner
 * @param audience the receiving integration that may consume the event
 * @param issuedAt the signed publication time
 * @param artifact the synthetic software artifact
 * @param version the release label
 * @param sha256 the lowercase artifact digest
 */
public record ReleaseEvent(
        int schemaVersion,
        UUID eventId,
        String issuer,
        String audience,
        Instant issuedAt,
        String artifact,
        String version,
        String sha256) {
    /** Identifies the exact business contract bound into every DSSE signature. */
    public static final String PAYLOAD_TYPE = "application/vnd.pqclab.release-event.v1+json";

    /**
     * Validates deterministic schema rules without interpreting freshness or receiver configuration.
     *
     * @param schemaVersion the business schema version, currently one
     * @param eventId the signed event identifier
     * @param issuer the signed publisher identifier
     * @param audience the signed receiving integration identifier
     * @param issuedAt the signed publication time
     * @param artifact the artifact label
     * @param version the version label
     * @param sha256 the lowercase artifact SHA-256 digest
     */
    public ReleaseEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (schemaVersion != 1
                || !matches(issuer, "[a-z][a-z0-9-]{0,63}")
                || !matches(audience, "[a-zA-Z0-9:._/-]{1,128}")
                || !matches(artifact, "[a-zA-Z][a-zA-Z0-9._-]{0,63}")
                || !matches(version, "[a-zA-Z0-9][a-zA-Z0-9.+_-]{0,63}")
                || !matches(sha256, "[0-9a-f]{64}")) {
            throw new ReceptionFailure(ReceptionFailure.Code.MALFORMED_EVENT);
        }
    }

    /** Checks bounded schema text without retaining rejected values in an exception. */
    private static boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }
}
