package hu.balazsfalvi.pqclab.integration.app.workbench;

/** Defines controlled synthetic faults; none of these operations changes receiver configuration. */
public enum ExperimentScenario {
    /** Removes every entry verified by the trusted post-quantum key. */
    STRIP_PQC,
    /** Changes a business field while retaining the original signatures. */
    TAMPER_PAYLOAD,
    /** Signs the same bytes with newly generated untrusted keys. */
    WRONG_KEY,
    /** Signs an event outside the receiver's maximum age. */
    EXPIRED_EVENT,
    /** Signs a time beyond the receiver's future tolerance. */
    FUTURE_EVENT,
    /** Signs an event targeting a different audience. */
    WRONG_AUDIENCE,
    /** Combines signature stripping with an ignored unsigned policy extension. */
    UNSIGNED_DOWNGRADE,
    /** Reuses the signed event identifier with different, genuinely signed business bytes. */
    CONFLICTING_ID
}
