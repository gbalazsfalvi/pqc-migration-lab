package hu.balazsfalvi.pqclab.integration.core;

import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Compares migration policies using the same verifier and claim rules as durable reception. */
public final class MigrationEvaluator {
    private final PolicyVerifier verifier;
    private final EventValidator validator;
    private final Clock clock;

    /**
     * Supplies verification and validation capabilities only; no ledger capability is available.
     *
     * @param verifier the out-of-band publisher verifier
     * @param validator the receiver's shared claim validator
     * @param clock the trusted clock used once per comparison
     */
    public MigrationEvaluator(PolicyVerifier verifier, EventValidator validator, Clock clock) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Evaluates every fixed migration policy without changing the live receiver requirement.
     *
     * <p>Operational provider failures propagate instead of becoming fabricated rejection results.
     *
     * @param envelope the immutable bounded envelope to inspect
     * @return the ordered read-only policy observations
     */
    public Evaluation evaluate(DsseEnvelope envelope) {
        return verifier.readCurrent((current, revision) -> evaluate(envelope, current));
    }

    /**
     * Uses an already-held public snapshot for inspector evidence and policy decisions together.
     *
     * @param envelope the bounded envelope
     * @param snapshot the receiver-owned snapshot held by the caller
     * @return read-only observations under exactly this trust set
     */
    public Evaluation evaluate(DsseEnvelope envelope, PublisherTrust.Snapshot snapshot) {
        return evaluate(envelope, new PolicyVerifier(snapshot.publishers()));
    }

    /** Captures one clock instant and applies all four policies to one immutable key set. */
    private Evaluation evaluate(DsseEnvelope envelope, PolicyVerifier current) {
        Instant observedAt = clock.instant();
        return new Evaluation(
                observedAt,
                Arrays.stream(MigrationPolicy.values())
                        .map(policy -> evaluate(envelope, policy, observedAt, current))
                        .toList());
    }

    /** Applies one real verifier and claim-validation path without any persistence operation. */
    private PolicyDecision evaluate(
            DsseEnvelope envelope, MigrationPolicy policy, Instant observedAt, PolicyVerifier current) {
        try {
            validator.validateAt(current.verify(envelope, policy), observedAt);
            return new PolicyDecision(policy, true, null);
        } catch (ReceptionFailure rejection) {
            return new PolicyDecision(policy, false, rejection.code());
        }
    }

    /**
     * Records the shared clock snapshot and immutable policy outcomes.
     *
     * @param observedAt the receiver-owned time used for every policy
     * @param decisions the complete ordered outcomes
     */
    public record Evaluation(Instant observedAt, List<PolicyDecision> decisions) {
        /**
         * Snapshots the complete comparison result.
         *
         * @param observedAt the trusted observation instant
         * @param decisions the ordered policy decisions
         */
        public Evaluation {
            Objects.requireNonNull(observedAt, "observedAt");
            decisions = List.copyOf(decisions);
        }
    }
}
