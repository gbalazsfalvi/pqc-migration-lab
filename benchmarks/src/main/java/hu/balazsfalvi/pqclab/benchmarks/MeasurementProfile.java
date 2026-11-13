package hu.balazsfalvi.pqclab.benchmarks;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/** Centralizes explicit harness settings so short diagnostics cannot masquerade as longer measurements. */
public enum MeasurementProfile {
    /** Harness and packaging check; these timings are not comparative evidence. */
    SMOKE(1, 1, 1, 100, List.of(256)),
    /** Short exploratory comparison with separate JVM forks and limited warm-up. */
    QUICK(2, 2, 3, 250, List.of(256, 4096)),
    /** Default full matrix with longer warm-up, measurements and independent JVM forks. */
    STANDARD(2, 3, 5, 1000, List.of(256, 4096, 65536));

    private final int forks;
    private final int warmupIterations;
    private final int measurementIterations;
    private final int iterationMillis;
    private final List<Integer> payloadSizes;

    /** Records the fixed settings for a named measurement method. */
    MeasurementProfile(
            int forks,
            int warmupIterations,
            int measurementIterations,
            int iterationMillis,
            List<Integer> payloadSizes) {
        this.forks = forks;
        this.warmupIterations = warmupIterations;
        this.measurementIterations = measurementIterations;
        this.iterationMillis = iterationMillis;
        this.payloadSizes = List.copyOf(payloadSizes);
    }

    /**
     * Parses the documented, case-sensitive command-line name.
     *
     * @param value the exact lower-case profile
     * @return the fixed profile
     * @throws IllegalArgumentException for unsupported names
     */
    public static MeasurementProfile parse(String value) {
        return Arrays.stream(values())
                .filter(profile -> profile.id().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown measurement profile."));
    }

    /**
     * Returns the command-line and report identifier.
     *
     * @return the stable lower-case name
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Lists all decoded byte sizes measured by this profile.
     *
     * @return immutable payload sizes
     */
    public List<Integer> payloadSizes() {
        return payloadSizes;
    }

    /**
     * Describes all expected operation/profile/size combinations.
     *
     * @return the complete matrix size, including three size-independent key-generation cases
     */
    public int expectedResults() {
        return 3 + 6 * SignatureProfile.values().length * payloadSizes.size();
    }

    /**
     * Returns the exact method configuration included in the evidence.
     *
     * @return immutable measurement settings
     */
    public BenchmarkReport.Method method() {
        return new BenchmarkReport.Method(
                id(),
                forks,
                1,
                warmupIterations,
                measurementIterations,
                iterationMillis,
                "AverageTime",
                "us/op",
                List.of("-Xms256m", "-Xmx256m"),
                payloadSizes,
                expectedResults(),
                this != SMOKE);
    }

    /**
     * Builds forked JMH execution without inheriting instrumentation flags from the parent JVM.
     *
     * @param rawResult the destination of JMH's unmodified JSON result
     * @param consoleLog the complete harness output file
     * @return the explicit JMH options
     */
    public Options options(Path rawResult, Path consoleLog) {
        return new OptionsBuilder()
                .include("^" + KeyGenerationBenchmark.class.getName().replace(".", "\\.") + "\\.generateKeyPairs$")
                .include("^" + IntegrationBenchmark.class.getName().replace(".", "\\.") + "\\..*$")
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.MICROSECONDS)
                .threads(1)
                .forks(forks)
                .warmupForks(0)
                .warmupIterations(warmupIterations)
                .measurementIterations(measurementIterations)
                .warmupTime(TimeValue.milliseconds(iterationMillis))
                .measurementTime(TimeValue.milliseconds(iterationMillis))
                .timeout(TimeValue.seconds(30))
                .jvmArgs("-Xms256m", "-Xmx256m")
                .param(
                        "profile",
                        Arrays.stream(SignatureProfile.values()).map(Enum::name).toArray(String[]::new))
                .param(
                        "payloadBytes",
                        payloadSizes.stream().map(String::valueOf).toArray(String[]::new))
                .shouldFailOnError(true)
                .shouldDoGC(false)
                .resultFormat(ResultFormatType.JSON)
                .result(rawResult.toAbsolutePath().toString())
                .output(consoleLog.toAbsolutePath().toString())
                .build();
    }
}
