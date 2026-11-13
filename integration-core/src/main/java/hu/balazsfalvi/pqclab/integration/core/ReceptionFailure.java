package hu.balazsfalvi.pqclab.integration.core;

import java.util.Objects;

/** Represents a safe, structured rejection of untrusted integration input. */
public final class ReceptionFailure extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    /** Preserves the safe domain rejection category when the exception is serialized. */
    private final Code code;

    /** Enumerates public rejection reasons without including input or key material. */
    public enum Code {
        /** The signed business document is malformed or outside the supported schema. */
        MALFORMED_EVENT,
        /** The envelope's authenticated payload type is outside this integration contract. */
        INVALID_PAYLOAD_TYPE,
        /** The trusted signatures do not meet the receiver's configured migration requirements. */
        POLICY_REJECTED,
        /** The signed issuer is different from the key owner's trusted identity. */
        ISSUER_MISMATCH,
        /** The event targets another receiving application. */
        AUDIENCE_MISMATCH,
        /** The signed event is outside the receiver's maximum age. */
        EVENT_EXPIRED,
        /** The signed time exceeds the receiver's future clock tolerance. */
        EVENT_IN_FUTURE,
        /** An accepted event identifier was reused with different signed payload bytes. */
        EVENT_ID_CONFLICT
    }

    /**
     * Creates a rejection using a stable reason code instead of attacker-controlled diagnostics.
     *
     * @param code the public rejection category
     */
    public ReceptionFailure(Code code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    /**
     * Exposes the reason for consistent API mapping and audit tests.
     *
     * @return the safe rejection category
     */
    public Code code() {
        return code;
    }
}
