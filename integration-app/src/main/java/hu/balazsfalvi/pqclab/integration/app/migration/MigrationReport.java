package hu.balazsfalvi.pqclab.integration.app.migration;

import java.util.List;

/**
 * Describes one isolated demonstration using public observations and explicit completion state.
 *
 * @param schemaVersion the evidence schema version
 * @param milestone the implemented milestone
 * @param runId the unique run identifier, independent of event and key identities
 * @param startedAt the wall-clock start in UTC
 * @param finishedAt the wall-clock finish in UTC
 * @param outcome PASS, FAIL for an observed assertion failure, or INCOMPLETE for an operational interruption
 * @param scenarioCompleted whether every planned phase and resource cleanup finished
 * @param failureCode the safe failure category, otherwise null
 * @param environment the public runtime description
 * @param exchanges the ordered actual HTTP attempts
 * @param checks the independently specified expectations
 */
public record MigrationReport(
        int schemaVersion,
        String milestone,
        String runId,
        String startedAt,
        String finishedAt,
        Outcome outcome,
        boolean scenarioCompleted,
        String failureCode,
        Environment environment,
        List<Exchange> exchanges,
        List<Check> checks) {

    /** Freezes ordered public observations before serialization. */
    public MigrationReport {
        exchanges = List.copyOf(exchanges);
        checks = List.copyOf(checks);
    }

    /**
     * Requires complete execution and successful assertions before a report can claim success.
     *
     * @return whether the complete scenario satisfied every recorded expectation
     */
    public boolean successful() {
        return outcome == Outcome.PASS
                && scenarioCompleted
                && failureCode == null
                && !checks.isEmpty()
                && checks.stream().allMatch(Check::passed);
    }

    /** Distinguishes an actual expectation failure from an unfinished experiment. */
    public enum Outcome {
        /** All phases, assertions and cleanup completed. */
        PASS,
        /** A real observation contradicted an expectation. */
        FAIL,
        /** Startup, transport, response validation or cleanup prevented completion. */
        INCOMPLETE
    }

    /**
     * Includes runtime facts without usernames, hostnames, absolute paths or account-secret locations.
     *
     * @param javaRuntimeVersion the executing Java version
     * @param javaVendor the runtime vendor
     * @param operatingSystem the OS family
     * @param architecture the executing machine architecture
     * @param transport the actual process and HTTP topology
     * @param protocol the signed envelope protocol
     */
    public record Environment(
            String javaRuntimeVersion,
            String javaVendor,
            String operatingSystem,
            String architecture,
            String transport,
            String protocol) {}

    /**
     * References exact public request/response bytes without embedding arbitrary exceptions or headers.
     *
     * @param sequence the one-based request number
     * @param phase the complete-runtime phase
     * @param scenario the stable observation identifier
     * @param endpoint PUBLISHER or RECEIVER, without an environment-specific port
     * @param method the HTTP method
     * @param path the fixed relative API route
     * @param observedAt the UTC attempt time
     * @param expectedStatus the independently specified expected HTTP status
     * @param actualStatus the received HTTP status, or null if no complete response arrived
     * @param requestFile the relative public request artifact, otherwise null
     * @param requestSha256 the exact request-byte digest, otherwise null
     * @param responseFile the relative validated public response artifact, otherwise null
     * @param responseSha256 the exact received-byte digest, otherwise null
     * @param failureCode the safe transport or export-validation category, otherwise null
     */
    public record Exchange(
            int sequence,
            int phase,
            String scenario,
            String endpoint,
            String method,
            String path,
            String observedAt,
            int expectedStatus,
            Integer actualStatus,
            String requestFile,
            String requestSha256,
            String responseFile,
            String responseSha256,
            String failureCode) {}

    /**
     * Records a semantic expectation separately from the HTTP response that supports it.
     *
     * @param id the stable assertion identifier
     * @param description the English assertion description
     * @param expected the expected public value rendered as text
     * @param observed the actual public value rendered as text
     * @param passed whether those values matched
     * @param exchanges the supporting request numbers
     */
    public record Check(
            String id, String description, String expected, String observed, boolean passed, List<Integer> exchanges) {
        /** Freezes evidence references so a report cannot change after it is assembled. */
        public Check {
            exchanges = List.copyOf(exchanges);
        }
    }
}
