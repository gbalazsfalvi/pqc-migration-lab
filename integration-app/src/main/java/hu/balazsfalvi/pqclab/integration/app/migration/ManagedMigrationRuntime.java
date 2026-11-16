package hu.balazsfalvi.pqclab.integration.app.migration;

import hu.balazsfalvi.pqclab.integration.app.runtime.MigrationRuntime;
import java.io.IOException;

/** Gives the local demonstration control over only its newly allocated service processes or containers. */
interface ManagedMigrationRuntime extends MigrationRuntime {
    /** @return the live publisher process/container identity; never included verbatim in public evidence */
    String publisherIdentity();

    /** @return the live receiver process/container identity; never included verbatim in public evidence */
    String receiverIdentity();

    /** Stops only the owned publisher, retaining durable private state. */
    void stopPublisher() throws IOException, InterruptedException;

    /** Starts only the owned publisher on its existing endpoint. */
    void startPublisher() throws IOException, InterruptedException;

    /** Stops only the owned receiver, retaining durable receipts. */
    void stopReceiver() throws IOException, InterruptedException;

    /** Starts only the owned receiver on its existing endpoint. */
    void startReceiver() throws IOException, InterruptedException;
}
