package hu.balazsfalvi.pqclab.integration.app.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/** Runs the isolated milestone-05A story and writes public evidence even when execution cannot finish. */
public final class MigrationDemo {
    private final Scenario scenario;

    /** Creates the real three-phase HTTP scenario. */
    public MigrationDemo() {
        this((workspace, journal) -> new MigrationScenario(journal).run(workspace));
    }

    /** Supplies an execution-boundary seam for testing interrupted evidence without replacing production HTTP verdicts. */
    MigrationDemo(Scenario scenario) {
        this.scenario = scenario;
    }

    /**
     * Executes one new evidence run and fails the process on any failed or incomplete outcome.
     *
     * @param arguments an optional report root, or --help
     * @throws IOException if a public evidence directory or bundle cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length == 1 && "--help".equals(arguments[0])) {
            System.out.println("Usage: MigrationDemo [report-root]");
            System.out.println("Starts isolated loopback runtimes; never connects to an existing workbench.");
            return;
        }
        if (arguments.length > 1
                || (arguments.length == 1 && (arguments[0].isBlank() || arguments[0].startsWith("--")))) {
            throw new IllegalArgumentException("Use one optional report root, or --help.");
        }
        Path root = arguments.length == 0 ? Path.of("build/reports/migration-demo") : Path.of(arguments[0]);
        Result result = new MigrationDemo().execute(root);
        System.out.printf(
                "PQC MIGRATION LAB / Milestone 05A%nOutcome: %s%n%d assertions / %d HTTP attempts%nReports: %s%n",
                result.report().outcome(),
                result.report().checks().size(),
                result.report().exchanges().size(),
                result.directory().toAbsolutePath());
        if (!result.report().successful()) {
            throw new IllegalStateException(
                    "Migration demonstration did not pass; inspect its public evidence report.");
        }
    }

    /**
     * Allocates a unique report directory and a completely separate temporary runtime workspace.
     *
     * @param reportRoot the trusted local parent directory for public evidence runs
     * @return the immutable report and its new local directory
     * @throws IOException if evidence itself cannot be written
     */
    public Result execute(Path reportRoot) throws IOException {
        String runId = UUID.randomUUID().toString();
        Instant started = Instant.now();
        Files.createDirectories(reportRoot);
        Path directory = Files.createDirectory(reportRoot.resolve("run-" + runId));
        var journal = new MigrationJournal(directory);
        boolean completed = false;
        boolean interrupted = false;
        String failureCode = null;
        try (var workspace = MigrationWorkspace.create()) {
            scenario.run(workspace, journal);
        } catch (InterruptedException failure) {
            interrupted = true;
            failureCode = "INTERRUPTED";
        } catch (ScenarioFailure failure) {
            failureCode = failure.code();
        } catch (IOException | RuntimeException failure) {
            // Native/provider/filesystem messages can contain paths or data; the report includes only a fixed category.
            failureCode = "EXECUTION_FAILED";
        }
        // Finish only bounded local evidence with a clear flag; preserve cancellation for the caller afterwards.
        if (Thread.interrupted()) interrupted = true;
        // Cleanup belongs to completion. A failure while closing the workspace cannot produce a PASS.
        if (failureCode == null && !journal.checks().isEmpty()) completed = true;
        if (failureCode == null && !completed) failureCode = "NO_OBSERVATIONS";
        var outcome = completed
                ? MigrationReport.Outcome.PASS
                : "EXPECTATION_FAILED".equals(failureCode)
                        ? MigrationReport.Outcome.FAIL
                        : MigrationReport.Outcome.INCOMPLETE;
        var environment = new MigrationReport.Environment(
                System.getProperty("java.runtime.version"),
                System.getProperty("java.vendor"),
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                "Two isolated Spring Boot contexts and loopback HTTP servers within one JVM; three disk-only runtime phases",
                "DSSEv1 / Ed25519 + ML-DSA-65");
        var report = new MigrationReport(
                1,
                "05A",
                runId,
                started.toString(),
                Instant.now().toString(),
                outcome,
                completed,
                failureCode,
                environment,
                journal.exchanges(),
                journal.checks());
        try {
            new MigrationReportWriter().write(directory, report);
            return new Result(directory, report);
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /**
     * Identifies the actual generated bundle without including its absolute path in the shareable report.
     *
     * @param directory the new local public evidence directory
     * @param report the completed or interrupted observations
     */
    public record Result(Path directory, MigrationReport report) {}

    /** Allows controlled startup/transport/cleanup failures to be verified at the execution boundary. */
    @FunctionalInterface
    interface Scenario {
        /** Performs the scenario using only its owned workspace and public journal. */
        void run(MigrationWorkspace workspace, MigrationJournal journal) throws IOException, InterruptedException;
    }
}
