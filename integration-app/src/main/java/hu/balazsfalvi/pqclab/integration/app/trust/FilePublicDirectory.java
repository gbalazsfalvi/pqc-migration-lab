package hu.balazsfalvi.pqclab.integration.app.trust;

import hu.balazsfalvi.pqclab.integration.app.http.ApiFailure;
import java.io.IOException;
import java.time.Clock;
import java.util.function.Function;

/** Imports only public files and holds their cross-process lease through receiver application. */
public final class FilePublicDirectory implements PublicKeyDirectory {
    private final SharedTrustStore store;
    private final Clock clock;

    /**
     * Supplies read-only trust storage and an independent receiver clock.
     *
     * @param store the receiver's read-only public store
     * @param clock the trusted receiver clock
     */
    public FilePublicDirectory(SharedTrustStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** {@inheritDoc} */
    @Override
    public <T> T read(Function<Snapshot, T> observation) {
        try {
            return store.read(state -> state.directory(clock).read(observation));
        } catch (IOException unavailable) {
            throw new ApiFailure(503, "TRUST_STORE_UNAVAILABLE");
        }
    }

    /** {@inheritDoc} */
    @Override
    public KeyRegistryView view() {
        try {
            return store.read(state -> state.directory(clock).view());
        } catch (IOException unavailable) {
            throw new ApiFailure(503, "TRUST_STORE_UNAVAILABLE");
        }
    }
}
