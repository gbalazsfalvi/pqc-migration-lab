package hu.balazsfalvi.pqclab.integration.app.trust;

import hu.balazsfalvi.pqclab.integration.core.PublisherTrust;
import hu.balazsfalvi.pqclab.integration.core.TrustedPublisher;
import java.time.Clock;
import java.util.List;
import java.util.function.Function;

/** Gives receiver code public verification and public metadata only. */
public interface PublicKeyDirectory extends PublisherTrust {
    /**
     * Reads public lifecycle metadata at the receiver's current clock.
     *
     * @return public registry state
     */
    KeyRegistryView view();

    /**
     * Keeps deterministic legacy tests and cryptographic probes explicitly unmanaged.
     *
     * @param publisher the immutable public identity
     * @param clock the observation clock
     * @return a read-only directory without rotation or secret storage
     */
    static PublicKeyDirectory fixed(TrustedPublisher publisher, Clock clock) {
        PublisherTrust fixed = PublisherTrust.fixed(List.of(publisher));
        return new PublicKeyDirectory() {
            @Override
            public <T> T read(Function<Snapshot, T> observation) {
                return fixed.read(observation);
            }

            @Override
            public KeyRegistryView view() {
                return new KeyRegistryView(
                        false, 0, publisher.issuer(), null, clock.instant().toString(), 0, 0, List.of());
            }
        };
    }
}
