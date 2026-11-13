package hu.balazsfalvi.pqclab.integration.core;

/** Defines receiver-owned migration requirements; an envelope cannot select its own policy. */
public enum MigrationPolicy {
    /** Requires the classical signature while still recording both verification results. */
    CLASSICAL_ONLY,
    /** Keeps classical enforcement while explicitly evaluating the post-quantum trial. */
    SHADOW_PQC,
    /** Requires both algorithms for the same trusted publisher and original payload. */
    BOTH_REQUIRED,
    /** Requires the post-quantum signature without making the classical signature mandatory. */
    PQC_REQUIRED;

    /**
     * Evaluates requirements against independently obtained cryptographic evidence.
     *
     * @param evidence the results for a single trusted publisher
     * @return whether this evidence satisfies the configured policy
     */
    public boolean accepts(SignatureEvidence evidence) {
        return switch (this) {
            case CLASSICAL_ONLY, SHADOW_PQC -> evidence.ed25519Valid();
            case BOTH_REQUIRED -> evidence.ed25519Valid() && evidence.mlDsa65Valid();
            case PQC_REQUIRED -> evidence.mlDsa65Valid();
        };
    }
}
