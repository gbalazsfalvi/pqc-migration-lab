package hu.balazsfalvi.pqclab.integration.core;

import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Authenticates, validates and atomically applies a signed release event in that order. */
public final class ReceptionService {
    private final PolicyVerifier verifier;
    private final MigrationPolicy policy;
    private final EventValidator validator;
    private final ReleaseLedger ledger;
    private final PayloadDigester digester;
    private final Clock clock;

    /**
     * Supplies all trusted dependencies and receiver-owned freshness requirements explicitly.
     *
     * @param verifier the out-of-band publisher trust registry and cryptographic verifier
     * @param policy the configured algorithm requirement
     * @param codec the strict business payload adapter
     * @param ledger the atomic durable business store
     * @param digester the exact-byte idempotency fingerprint adapter
     * @param clock the receiver's trusted clock
     * @param audience the receiver's accepted audience
     * @param maximumAge the maximum event age, greater than zero and at most one day
     * @param futureTolerance the permitted clock lead, from zero to five minutes
     */
    public ReceptionService(
            PolicyVerifier verifier,
            MigrationPolicy policy,
            ReleaseEventCodec codec,
            ReleaseLedger ledger,
            PayloadDigester digester,
            Clock clock,
            String audience,
            Duration maximumAge,
            Duration futureTolerance) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.validator = new EventValidator(codec, clock, audience, maximumAge, futureTolerance);
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.digester = Objects.requireNonNull(digester, "digester");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Processes a delivery only after its signatures, identity, audience and time have been validated.
     *
     * <p>Every retry is reverified under the current policy before consulting durable idempotency.
     * An old receipt cannot bypass a newly strengthened algorithm requirement.
     *
     * @param envelope the parsed DSSE envelope
     * @return the original durable receipt and whether a new business result was created
     */
    public ReleaseLedger.Outcome receive(DsseEnvelope envelope) {
        // The registry read lease includes the commit: revocation cannot finish between verification and application.
        return verifier.readCurrent((current, revision) -> receive(envelope, current));
    }

    /** Applies one event using the immutable trust snapshot held by the caller's read lease. */
    private ReleaseLedger.Outcome receive(DsseEnvelope envelope, PolicyVerifier current) {
        PolicyVerifier.AuthenticatedPayload authenticated = current.verify(envelope, policy);
        byte[] original = authenticated.payload().body();
        ReleaseEvent event = validator.validate(authenticated);
        Instant now = clock.instant();
        return ledger.record(new StoredRelease(
                UUID.randomUUID(), event, now, policy, authenticated.evidence(), digester.digest(original)));
    }
}
