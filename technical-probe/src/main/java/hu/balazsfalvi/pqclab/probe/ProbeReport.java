package hu.balazsfalvi.pqclab.probe;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Captures a complete functional proof without claiming certification or benchmark results.
 *
 * @param generatedAt the UTC instant at which the run finished
 * @param environment the runtime and implementation versions
 * @param checks the ordered expectations and their observed outcomes
 */
public record ProbeReport(Instant generatedAt, Map<String, String> environment, List<ProbeCheck> checks) {
    /**
     * Copies all collections so report evidence cannot change after execution.
     *
     * @param generatedAt the completion time
     * @param environment the public runtime metadata
     * @param checks the completed checks
     */
    public ProbeReport {
        Objects.requireNonNull(generatedAt, "generatedAt");
        environment = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(environment, "environment")));
        checks = List.copyOf(checks);
    }

    /**
     * Counts expectations that were observed successfully.
     *
     * @return the number of passing checks
     */
    public long passedCount() {
        return checks.stream().filter(ProbeCheck::passed).count();
    }

    /**
     * Counts failures, including unavailable implementations and operational errors.
     *
     * @return the number of failed checks
     */
    public long failedCount() {
        return checks.size() - passedCount();
    }

    /**
     * Requires actual evidence and no failed expectation.
     *
     * @return whether this complete run succeeded
     */
    public boolean successful() {
        return !checks.isEmpty() && failedCount() == 0;
    }
}
