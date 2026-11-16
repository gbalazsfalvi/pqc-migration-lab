package hu.balazsfalvi.pqclab.integration.app.migration;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Executes the four-phase live policy rollout in owned native JVMs or actual local Compose containers. */
public final class PolicyMigrationDemo {
    /** Prevents construction of the executable entry point. */
    private PolicyMigrationDemo() {}

    /**
     * Runs the reviewed migration plan without accepting a remote target or caller-selected phase sequence.
     *
     * @param arguments an optional report root and --compose, or --help
     * @throws IOException if the public report bundle cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length == 1 && "--help".equals(arguments[0])) {
            System.out.println("Usage: PolicyMigrationDemo [report-root] [--compose]");
            System.out.println("Rehearses four live receiver policies in a fresh owned local deployment.");
            return;
        }
        var options = new ArrayList<>(List.of(arguments));
        boolean compose = options.remove("--compose");
        if (options.size() > 1
                || (!options.isEmpty()
                        && (options.getFirst().isBlank() || options.getFirst().startsWith("--")))) {
            throw new IllegalArgumentException("Use one optional report root and --compose, or --help.");
        }
        Path root = options.isEmpty() ? Path.of("build/reports/policy-rehearsal") : Path.of(options.getFirst());
        var result = execute(root, compose);
        System.out.printf(
                "PQC MIGRATION LAB / Milestone 06B%nOutcome: %s%n%d/4 policy phases / %d assertions / %d HTTP attempts%nReports: %s%n",
                result.report().outcome(),
                result.report().policyPhases().size(),
                result.report().checks().size(),
                result.report().exchanges().size(),
                result.directory().toAbsolutePath());
        if (!result.report().successful())
            throw new IllegalStateException("The policy rehearsal did not pass; inspect its public evidence.");
    }

    /**
     * Builds before signing, retains one publisher, and changes receiver requirements only through controlled restarts.
     *
     * @param reportRoot the parent directory for one new public bundle
     * @param compose whether to use actual local containers instead of native JVMs
     * @return the complete or honestly incomplete observed result
     * @throws IOException if evidence cannot be created
     */
    public static MigrationDemo.Result execute(Path reportRoot, boolean compose) throws IOException {
        MigrationDemo.Scenario scenario = compose
                ? (workspace, journal) -> {
                    try (var project = new ComposeProject(null, ComposeProject.Purpose.POLICY_REHEARSAL)) {
                        project.build();
                        new PolicyRehearsalScenario(journal, ignored -> project.start()).run(workspace);
                    }
                }
                : (workspace, journal) -> new PolicyRehearsalScenario(
                                journal, owned -> NativeProcessRuntime.start(owned, MigrationPolicy.CLASSICAL_ONLY))
                        .run(workspace);
        return new MigrationDemo(
                        scenario,
                        "06B",
                        compose
                                ? "Independent local Compose containers; one publisher, four receiver deployments, unchanged public trust and durable ledger"
                                : "Independent native JVMs; one publisher, four receiver deployments, unchanged public trust and durable ledger")
                .execute(reportRoot);
    }
}
