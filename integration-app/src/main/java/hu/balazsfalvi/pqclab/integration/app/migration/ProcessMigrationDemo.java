package hu.balazsfalvi.pqclab.integration.app.migration;

import java.io.IOException;
import java.nio.file.Path;

/** Runs the complete migration story with actual separate service JVMs and independent recovery checks. */
public final class ProcessMigrationDemo {
    /** Prevents construction of the executable entry point. */
    private ProcessMigrationDemo() {}

    /**
     * Allocates owned processes and public evidence without attaching to the interactive workbench.
     *
     * @param arguments an optional report root or --help
     * @throws IOException if the public evidence bundle cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length == 1 && "--help".equals(arguments[0])) {
            System.out.println("Usage: ProcessMigrationDemo [report-root] [--compose]");
            return;
        }
        var options = new java.util.ArrayList<>(java.util.List.of(arguments));
        boolean compose = options.remove("--compose");
        if (options.size() > 1
                || (!options.isEmpty()
                        && (options.getFirst().isBlank() || options.getFirst().startsWith("--")))) {
            throw new IllegalArgumentException("Use one optional report root and --compose, or --help.");
        }
        Path root = options.isEmpty() ? Path.of("build/reports/process-migration-demo") : Path.of(options.getFirst());
        var result = execute(root, compose);
        System.out.printf(
                "PQC MIGRATION LAB / Milestone 06A%nOutcome: %s%n%d assertions / %d HTTP attempts%nReports: %s%n",
                result.report().outcome(),
                result.report().checks().size(),
                result.report().exchanges().size(),
                result.directory().toAbsolutePath());
        if (!result.report().successful())
            throw new IllegalStateException("The separate-process migration did not pass.");
    }

    /**
     * Runs native child JVMs with real OS identities, separate state and public-only cross-process trust.
     *
     * @param reportRoot the parent directory for one new public report bundle
     * @return the observed result, including honest partial evidence on failure
     * @throws IOException if evidence cannot be created
     */
    public static MigrationDemo.Result execute(Path reportRoot) throws IOException {
        return execute(reportRoot, false);
    }

    /**
     * Selects owned native JVMs or an isolated local Compose project without changing the scenario verdicts.
     *
     * @param reportRoot the public evidence root
     * @param compose whether to build and exercise separate local containers
     * @return the observed completed or partial report
     * @throws IOException if public evidence cannot be written
     */
    public static MigrationDemo.Result execute(Path reportRoot, boolean compose) throws IOException {
        if (compose) {
            return new MigrationDemo(
                            (workspace, journal) -> {
                                try (var project = new ComposeProject()) {
                                    project.build();
                                    new MigrationScenario(journal, ignored -> project.start(), true).run(workspace);
                                }
                            },
                            "06A",
                            "Two independent local Compose service containers; separate private volumes; read-only public trust; "
                                    + "three disk-only phases and independent role restart/outage recovery")
                    .execute(reportRoot);
        }
        return new MigrationDemo(
                        (workspace, journal) ->
                                new MigrationScenario(journal, NativeProcessRuntime::start, true).run(workspace),
                        "06A",
                        "Two independent Java service processes; public-only file trust with OS leases; "
                                + "three disk-only phases, independent role restarts and receiver-outage recovery")
                .execute(reportRoot);
    }
}
