package hu.balazsfalvi.pqclab.benchmarks;

import java.nio.file.Path;
import java.util.List;
import org.openjdk.jmh.runner.RunnerException;

/** Separates process orchestration from report writing so failure and completeness rules are testable. */
@FunctionalInterface
public interface BenchmarkExecutor {
    /**
     * Executes the complete fixed measurement matrix.
     *
     * @param profile the trusted named settings
     * @param rawResult the untouched JMH JSON destination
     * @param consoleLog the harness output destination
     * @return completed observations only
     * @throws RunnerException when a harness or measured operation fails
     */
    List<BenchmarkReport.Measurement> execute(MeasurementProfile profile, Path rawResult, Path consoleLog)
            throws RunnerException;
}
