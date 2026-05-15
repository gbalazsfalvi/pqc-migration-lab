package hu.balazsfalvi.pqclab.integration.core;

import static org.junit.jupiter.api.Assertions.*;

import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseSignature;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Tests current-trust acquisition without importing cryptographic providers into application rules. */
final class PublisherTrustTest {
    /** A receiver must acquire new trust on its next operation while keeping the current operation coherent. */
    @Test
    void refreshesTrustOnlyBetweenCompleteOperations() {
        var calls = new AtomicInteger();
        var selected = new AtomicReference<PublisherTrust.Snapshot>();
        MessageVerifier accepted = (message, signature) -> {
            selected.set(new PublisherTrust.Snapshot(2, List.of(publisher((body, signed) -> false))));
            return true;
        };
        selected.set(new PublisherTrust.Snapshot(1, List.of(publisher(accepted))));
        PublisherTrust directory = new PublisherTrust() {
            @Override
            public <T> T read(Function<Snapshot, T> observation) {
                calls.incrementAndGet();
                return observation.apply(selected.get());
            }
        };
        var verifier = new PolicyVerifier(directory);
        var envelope =
                new DsseEnvelope(ReleaseEvent.PAYLOAD_TYPE, "e30=", List.of(new DsseSignature("ignored-hint", "AA==")));
        verifier.readCurrent((current, revision) -> {
            assertEquals(1, revision);
            assertEquals(
                    "release-publisher",
                    current.verify(envelope, MigrationPolicy.BOTH_REQUIRED).issuer());
            assertEquals(
                    "release-publisher",
                    current.verify(envelope, MigrationPolicy.PQC_REQUIRED).issuer());
            return null;
        });
        assertEquals(1, calls.get());
        assertThrows(ReceptionFailure.class, () -> verifier.verify(envelope, MigrationPolicy.BOTH_REQUIRED));
        assertEquals(2, calls.get());
    }

    /** Fixed probe trust snapshots their public owners and reject invalid revision counters. */
    @Test
    void snapshotsFixedPublicConfiguration() {
        var publishers = new ArrayList<>(List.of(publisher((message, signature) -> true)));
        var fixed = PublisherTrust.fixed(publishers);
        publishers.clear();
        fixed.read(snapshot -> {
            assertEquals(0, snapshot.revision());
            assertEquals(1, snapshot.publishers().size());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> snapshot.publishers().clear());
            return null;
        });
        assertThrows(IllegalArgumentException.class, () -> new PublisherTrust.Snapshot(-1, List.of()));
    }

    /** Binds both algorithm ports to one explicit owner for domain-level orchestration tests. */
    private TrustedPublisher publisher(MessageVerifier verifier) {
        return new TrustedPublisher(
                "release-publisher",
                Map.of(SignatureAlgorithm.ED25519, verifier, SignatureAlgorithm.ML_DSA_65, verifier));
    }
}
