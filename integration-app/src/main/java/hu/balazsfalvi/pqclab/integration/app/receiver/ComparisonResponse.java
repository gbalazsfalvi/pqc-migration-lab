package hu.balazsfalvi.pqclab.integration.app.receiver;

import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import hu.balazsfalvi.pqclab.integration.core.ReceptionFailure;
import hu.balazsfalvi.pqclab.integration.core.SignatureEvidence;
import java.time.Instant;
import java.util.List;

/**
 * Exposes observed Java verification results without exposing signing material.
 *
 * @param evaluatedAt the observation time
 * @param receiverPolicy the unchanged live receiver requirement
 * @param payloadSha256 the exact decoded payload fingerprint
 * @param envelopeBytes the received JSON byte count
 * @param payloadBytes the decoded payload byte count
 * @param paeBytes the DSSE pre-authentication byte count
 * @param signatureEvidence the independent results for the configured trusted publisher
 * @param signatures the public per-entry observations
 * @param policies the read-only decisions for every migration policy
 * @param trustRevision the public trust revision used consistently for this entire observation
 */
public record ComparisonResponse(
        Instant evaluatedAt,
        MigrationPolicy receiverPolicy,
        String payloadSha256,
        int envelopeBytes,
        int payloadBytes,
        int paeBytes,
        SignatureEvidence signatureEvidence,
        List<SignatureObservation> signatures,
        List<PolicyObservation> policies,
        long trustRevision) {

    /**
     * Records what each signature actually verified against, rather than inferring an algorithm by size.
     *
     * @param index the zero-based envelope entry index
     * @param keyHint the unauthenticated wire hint
     * @param bytes the decoded signature size
     * @param verifiedAlgorithms the configured algorithms whose trusted keys verified this entry
     */
    public record SignatureObservation(
            int index, String keyHint, int bytes, List<SignatureAlgorithm> verifiedAlgorithms) {}

    /**
     * Describes one policy's actual cryptographic and business validation path.
     *
     * @param policy the independently evaluated policy
     * @param accepted whether validation succeeded without applying a business effect
     * @param rejectionCode the safe rejection category, otherwise null
     * @param steps the ordered validation observations
     */
    public record PolicyObservation(
            MigrationPolicy policy,
            boolean accepted,
            ReceptionFailure.Code rejectionCode,
            List<ValidationStep> steps) {}

    /**
     * Gives the interface a factual validation step without inventing browser-side verdicts.
     *
     * @param id the stable step identifier
     * @param label the human-readable stage
     * @param status passed, failed, or not checked for this policy
     * @param detail the safe explanation
     */
    public record ValidationStep(String id, String label, StepStatus status, String detail) {}

    /** Distinguishes successful checks, rejection points and stages skipped after rejection. */
    public enum StepStatus {
        /** This stage succeeded for the selected policy. */
        PASSED,
        /** This stage rejected the event. */
        FAILED,
        /** Policy processing stopped before this stage. */
        NOT_CHECKED
    }
}
