package hu.balazsfalvi.pqclab.integration.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Shares the exact business-claim contract between read-only evaluation and durable reception. */
public final class EventValidator {
    private final ReleaseEventCodec codec;
    private final Clock clock;
    private final String audience;
    private final Duration maximumAge;
    private final Duration futureTolerance;

    /**
     * Freezes receiver-owned requirements without accepting them from an envelope.
     *
     * @param codec the strict business payload adapter
     * @param clock the trusted receiver clock
     * @param audience the accepted receiver identity
     * @param maximumAge the positive maximum event age, at most one day
     * @param futureTolerance the allowed clock lead, from zero to five minutes
     */
    public EventValidator(
            ReleaseEventCodec codec, Clock clock, String audience, Duration maximumAge, Duration futureTolerance) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.maximumAge = Objects.requireNonNull(maximumAge, "maximumAge");
        this.futureTolerance = Objects.requireNonNull(futureTolerance, "futureTolerance");
        if (audience.isBlank()
                || maximumAge.isNegative()
                || maximumAge.isZero()
                || maximumAge.compareTo(Duration.ofDays(1)) > 0
                || futureTolerance.isNegative()
                || futureTolerance.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Receiver audience and freshness limits must be explicit and bounded.");
        }
    }

    /**
     * Parses only policy-authenticated bytes and checks every signed delivery claim.
     *
     * @param authenticated the exact snapshot and owner established by qualifying trusted signatures
     * @return the valid synthetic release
     * @throws ReceptionFailure if schema, identity, audience or freshness validation fails
     */
    public ReleaseEvent validate(PolicyVerifier.AuthenticatedPayload authenticated) {
        return validateAt(authenticated, clock.instant());
    }

    /**
     * Uses one receiver-owned observation time across all policies in a comparison.
     *
     * @param authenticated the policy-authenticated byte snapshot
     * @param now the trusted comparison clock snapshot
     * @return the validated business event
     */
    public ReleaseEvent validateAt(PolicyVerifier.AuthenticatedPayload authenticated, Instant now) {
        ReleaseEvent event = codec.decode(authenticated.payload().body());
        if (!authenticated.issuer().equals(event.issuer())) {
            throw new ReceptionFailure(ReceptionFailure.Code.ISSUER_MISMATCH);
        }
        if (!audience.equals(event.audience())) {
            throw new ReceptionFailure(ReceptionFailure.Code.AUDIENCE_MISMATCH);
        }
        if (event.issuedAt().isBefore(now.minus(maximumAge))) {
            throw new ReceptionFailure(ReceptionFailure.Code.EVENT_EXPIRED);
        }
        if (event.issuedAt().isAfter(now.plus(futureTolerance))) {
            throw new ReceptionFailure(ReceptionFailure.Code.EVENT_IN_FUTURE);
        }
        return event;
    }
}
