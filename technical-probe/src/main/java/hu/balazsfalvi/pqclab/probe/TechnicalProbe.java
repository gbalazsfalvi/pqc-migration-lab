package hu.balazsfalvi.pqclab.probe;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;

/** Provides a local command-line entry point with explicit success and failure exit codes. */
public final class TechnicalProbe {
    /** Prevents construction of an entry-point utility. */
    private TechnicalProbe() {}

    /**
     * Runs the technical proof and exits unsuccessfully when any expectation fails.
     *
     * @param args an optional {@code --output directory} or {@code --help}
     */
    public static void main(String[] args) {
        int exitCode = execute(args, System.out, System.err, Clock.systemUTC());
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Separates the command's behaviour from process termination for integration testing.
     *
     * @param args the command-line arguments
     * @param output the ordinary output stream
     * @param errors the diagnostic output stream
     * @param clock the clock used to timestamp evidence
     * @return zero on success, one on a failed proof or I/O error, or two on invalid arguments
     */
    static int execute(String[] args, PrintStream output, PrintStream errors, Clock clock) {
        if (args.length == 1 && "--help".equals(args[0])) {
            output.println("Usage: pqc-migration-lab [--output <directory>] | --help");
            return 0;
        }
        Path directory;
        try {
            if (args.length == 0) {
                directory = Path.of("build", "reports", "technical-probe");
            } else if (args.length == 2 && "--output".equals(args[0]) && !args[1].isBlank()) {
                directory = Path.of(args[1]);
            } else {
                errors.println("Usage: pqc-migration-lab [--output <directory>] | --help");
                return 2;
            }
        } catch (InvalidPathException invalidPath) {
            errors.println("The report destination is not a valid local path.");
            return 2;
        }
        output.println();
        output.println("PQC MIGRATION LAB");
        output.println("Milestone 01 / Signature and envelope interoperability");
        output.println("-----------------------------------------------------");
        try {
            ProbeReport report = new ProbeRunner(clock).run();
            new ReportWriter().write(report, directory);
            for (ProbeCheck check : report.checks()) {
                output.printf("[%s] %-42s %s%n", check.passed() ? "PASS" : "FAIL", check.id(), check.description());
            }
            output.println("-----------------------------------------------------");
            output.printf("%d passed / %d failed%n", report.passedCount(), report.failedCount());
            output.println("Reports: " + directory.toAbsolutePath().normalize());
            output.println("Functional evidence only; no private keys are written.");
            return report.successful() ? 0 : 1;
        } catch (IOException failure) {
            // Keep paths, payload fragments and provider internals out of public diagnostics.
            errors.println("The report could not be written. Check the local output directory.");
            return 1;
        } catch (RuntimeException failure) {
            errors.println("The probe could not complete (" + failure.getClass().getSimpleName() + ").");
            return 1;
        }
    }
}
