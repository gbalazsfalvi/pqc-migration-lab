package hu.balazsfalvi.pqclab.integration.core;

/**
 * Records the actual result of applying one trusted policy without reserving an event identifier.
 *
 * @param policy the evaluated receiver requirement
 * @param accepted whether both cryptographic and business-claim validation succeeded
 * @param rejectionCode the safe reason when rejected, otherwise null
 */
public record PolicyDecision(MigrationPolicy policy, boolean accepted, ReceptionFailure.Code rejectionCode) {}
