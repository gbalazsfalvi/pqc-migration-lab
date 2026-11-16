package hu.balazsfalvi.pqclab.integration.app.migration;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.util.List;

/** Defines the reviewed rehearsal order without reusing the verifier to manufacture expected outcomes. */
final class PolicyProgression {
    static final List<MigrationPolicy> PHASES = List.of(
            MigrationPolicy.CLASSICAL_ONLY,
            MigrationPolicy.SHADOW_PQC,
            MigrationPolicy.BOTH_REQUIRED,
            MigrationPolicy.PQC_REQUIRED);

    /** Prevents construction of the fixed operator-side plan. */
    private PolicyProgression() {}

    /** Rejects skipped, repeated or reversed steps before an owned receiver can be stopped. */
    static void requireNext(MigrationPolicy current, MigrationPolicy next) {
        if (current == null || next == null) throw new IllegalArgumentException("Both reviewed policies are required.");
        int position = PHASES.indexOf(current);
        if (position < 0 || position + 1 >= PHASES.size() || PHASES.get(position + 1) != next) {
            throw new IllegalArgumentException("Advance exactly one step in the reviewed migration plan.");
        }
    }
}
