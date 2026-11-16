package hu.balazsfalvi.pqclab.integration.app.runtime;

import java.io.IOException;
import java.net.URI;

/** Exposes public endpoints and owned shutdown without leaking signing or persistence capabilities. */
public interface MigrationRuntime extends AutoCloseable {
    /**
     * Returns the actual publisher origin.
     * @return the owned publisher address
     */
    URI publisherUri();

    /**
     * Returns the actual receiver origin.
     * @return the owned receiver address
     */
    URI receiverUri();

    /** Stops every owned service before the scenario releases its private temporary workspace. */
    @Override
    void close() throws IOException;
}
