package hu.balazsfalvi.pqclab.integration.app.migration;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.io.IOException;

/** Gives the owned rehearsal an operator-side restart capability, never an HTTP policy mutation route. */
interface PolicyMigrationRuntime extends ManagedMigrationRuntime {
    /** Advances one reviewed policy step by replacing only the receiver and retaining its durable state. */
    void advancePolicy(MigrationPolicy next) throws IOException, InterruptedException;
}
