package hu.balazsfalvi.pqclab.benchmarks;

import java.nio.file.Path;
import java.util.List;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;

/** Adapts completed JMH results to the public evidence model without changing scores or raw output. */
public final class JmhExecutor implements BenchmarkExecutor {
    /** Creates a stateless adapter; every call launches new independent JVM forks. */
    public JmhExecutor() {}

    /** {@inheritDoc} */
    @Override
    public List<BenchmarkReport.Measurement> execute(MeasurementProfile profile, Path rawResult, Path consoleLog)
            throws RunnerException {
        return new Runner(profile.options(rawResult, consoleLog))
                .run().stream().map(JmhExecutor::observation).toList();
    }

    /**
     * Projects actual harness observations into the versioned summary.
     *
     * @param result one completed benchmark and parameter combination
     * @return its unchanged score, uncertainty and sample count
     */
    static BenchmarkReport.Measurement observation(RunResult result) {
        var parameters = result.getParams();
        var primary = result.getPrimaryResult();
        String benchmark = parameters.getBenchmark();
        Integer size = parameters.getParamsKeys().contains("payloadBytes")
                ? Integer.valueOf(parameters.getParam("payloadBytes"))
                : null;
        return new BenchmarkReport.Measurement(
                benchmark.substring(benchmark.lastIndexOf('.') + 1),
                parameters.getParam("profile"),
                size,
                primary.getScore(),
                primary.getScoreError(),
                primary.getScoreUnit(),
                primary.getStatistics().getN(),
                result.getBenchmarkResults().size());
    }
}
