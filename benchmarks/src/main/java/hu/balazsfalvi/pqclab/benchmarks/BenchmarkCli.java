package hu.balazsfalvi.pqclab.benchmarks;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.UUID;

/** Runs a fixed, reproducible measurement matrix and publishes evidence only after full completion. */
public final class BenchmarkCli {
    private static final String USAGE =
            "Usage: pqc-benchmarks [--profile smoke|quick|standard] [--output <directory>] | --help";

    /** Prevents construction of an entry-point utility. */
    private BenchmarkCli() {}

    /**
     * Starts the local benchmark command.
     *
     * @param args the named method and optional local report parent directory
     */
    public static void main(String[] args) {
        int status = execute(args, System.out, System.err, Clock.systemUTC(), new JmhExecutor());
        if (status != 0) System.exit(status);
    }

    /**
     * Separates process termination and measured execution for meaningful command/error tests.
     *
     * @param args the strict command-line options
     * @param output ordinary progress output
     * @param errors safe failure diagnostics
     * @param clock the trusted evidence timestamp source
     * @param executor the forked measurement capability
     * @return zero on a complete run, one on failure, or two on invalid arguments
     */
    static int execute(String[] args, PrintStream output, PrintStream errors, Clock clock, BenchmarkExecutor executor) {
        if (args.length == 1 && "--help".equals(args[0])) {
            output.println(USAGE);
            return 0;
        }
        MeasurementProfile profile = MeasurementProfile.STANDARD;
        Path parent = Path.of("build", "reports", "benchmarks");
        try {
            var seen = new HashSet<String>();
            for (int index = 0; index < args.length; index += 2) {
                if (index + 1 >= args.length || !seen.add(args[index]) || args[index + 1].isBlank()) {
                    throw new IllegalArgumentException("An option is missing or duplicated.");
                }
                switch (args[index]) {
                    case "--profile" -> profile = MeasurementProfile.parse(args[index + 1]);
                    case "--output" -> parent = Path.of(args[index + 1]);
                    default -> throw new IllegalArgumentException("Unknown benchmark option.");
                }
            }
        } catch (IllegalArgumentException invalid) {
            errors.println(USAGE);
            return 2;
        }
        String startedAt = clock.instant().toString();
        String runId = profile.id() + "-"
                + DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssSSS'Z'")
                        .withZone(ZoneOffset.UTC)
                        .format(clock.instant())
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path directory = parent.toAbsolutePath().normalize().resolve(runId);
        var writer = new BenchmarkReportWriter();
        boolean initialized = false;
        try {
            Files.createDirectories(parent);
            Files.createDirectory(directory);
            writer.manifest(directory, runId, profile, "RUNNING", startedAt, null);
            initialized = true;
            output.println("PQC MIGRATION LAB / Milestone 04A");
            output.println("JMH profile: " + profile.id() + " / expected cases: " + profile.expectedResults());
            output.println("Run: " + directory);
            output.println("Harness progress: " + directory.resolve("jmh-output.log"));
            if (profile == MeasurementProfile.SMOKE) {
                output.println("SMOKE ONLY: this profile validates the harness, not comparative performance.");
            }
            // Collect size and provider evidence before the forked timing phase.
            var environment = BenchmarkEvidence.environment();
            var sizes = BenchmarkEvidence.sizes(profile);
            var measured = executor.execute(
                    profile, directory.resolve("jmh-results.json"), directory.resolve("jmh-output.log"));
            BenchmarkEvidence.requireComplete(profile, measured);
            var report = new BenchmarkReport(
                    1,
                    runId,
                    startedAt,
                    clock.instant().toString(),
                    profile.method(),
                    environment,
                    sizes,
                    measured,
                    BenchmarkEvidence.limitations(profile));
            writer.write(report, directory);
            // Completion is the last write; an interrupted run remains visibly RUNNING or FAILED.
            writer.manifest(directory, runId, profile, "COMPLETED", startedAt, report.completedAt());
            output.println("Completed: " + measured.size() + " cases.");
            output.println("Report: " + directory.resolve("report.md"));
            return 0;
        } catch (Exception failure) {
            if (initialized) {
                try {
                    writer.manifest(
                            directory,
                            runId,
                            profile,
                            "FAILED",
                            startedAt,
                            clock.instant().toString());
                } catch (IOException manifestFailure) {
                    // An unwritable manifest remains incomplete; never emit a false completion.
                    errors.println("The incomplete run status could not be updated.");
                }
            }
            errors.println(
                    "The benchmark run did not complete (" + failure.getClass().getSimpleName() + ").");
            return 1;
        }
    }
}
