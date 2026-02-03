package hu.balazsfalvi.pqclab.core.dsse;

/** Carries the same immutable payload bytes that successfully passed signature verification. */
public final class VerifiedPayload {
    private final String payloadType;
    private final byte[] body;

    /** Restricts construction to the core verification boundary and freezes the verified bytes. */
    VerifiedPayload(String payloadType, byte[] body) {
        this.payloadType = payloadType;
        this.body = body.clone();
    }

    /**
     * Returns the authenticated, caller-accepted payload type.
     *
     * @return the exact payload type
     */
    public String payloadType() {
        return payloadType;
    }

    /**
     * Returns a defensive copy of the bytes that were verified.
     *
     * @return the authenticated payload bytes
     */
    public byte[] body() {
        return body.clone();
    }
}
