package hu.balazsfalvi.pqclab.integration.core;

import java.util.List;
import java.util.function.Function;

/** Supplies a public-only trust snapshot held consistently throughout one receiver operation. */
public interface PublisherTrust {
    /**
     * Runs an observation while trust updates are excluded, including any durable application.
     *
     * @param <T> the operation result
     * @param observation the operation that must finish before a revocation can complete
     * @return the operation's result
     */
    <T> T read(Function<Snapshot, T> observation);

    /**
     * Creates immutable trust for interoperability probes and legacy deterministic tests.
     *
     * @param publishers the fixed public publisher identities
     * @return a registry without mutation or signing capabilities
     */
    static PublisherTrust fixed(List<TrustedPublisher> publishers) {
        Snapshot snapshot = new Snapshot(0, publishers);
        return new PublisherTrust() {
            @Override
            public <T> T read(Function<Snapshot, T> observation) {
                return observation.apply(snapshot);
            }
        };
    }

    /**
     * Holds one revision of trusted public verification capabilities.
     *
     * @param revision the durable registry revision, or zero for an unmanaged test fixture
     * @param publishers the immutable configured public owners and keys
     */
    record Snapshot(long revision, List<TrustedPublisher> publishers) {
        /** Snapshots public owners before an operation can use them. */
        public Snapshot {
            if (revision < 0) throw new IllegalArgumentException("Trust revisions cannot be negative.");
            publishers = List.copyOf(publishers);
        }
    }
}
