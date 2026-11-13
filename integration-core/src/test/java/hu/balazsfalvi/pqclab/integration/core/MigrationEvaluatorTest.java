package hu.balazsfalvi.pqclab.integration.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseSignature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Tests read-only policy semantics with verifier spies; HTTP tests exercise actual JCA signatures. */
final class MigrationEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-11-13T08:00:00Z");

    /** The exact event-age boundary is evaluated with one clock snapshot across all four policies. */
    @Test
    void usesOneClockSnapshotForEveryPolicy() {
        AtomicInteger reads = new AtomicInteger();
        Clock advancing = new Clock() {
            /** {@inheritDoc} */
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }
            /** Supplies the fixed reference for zone conversion outside this clock-read spy. */
            @Override
            public Clock withZone(ZoneId zone) {
                return Clock.fixed(NOW, zone);
            }
            /** Advances each observation to reveal accidental repeated clock reads. */
            @Override
            public Instant instant() {
                return NOW.plusSeconds(reads.getAndIncrement());
            }
        };
        var evaluation = evaluator(advancing, event(NOW.minusSeconds(300), "urn:receiver"))
                .evaluate(dual());
        assertEquals(NOW, evaluation.observedAt());
        assertEquals(1, reads.get());
        assertEquals(4, evaluation.decisions().size());
        assertTrue(evaluation.decisions().stream().allMatch(PolicyDecision::accepted));
    }

    /** Claim failures remain distinct from missing signatures without creating any business result. */
    @Test
    void comparesPolicyAndBusinessRejectionsIndependently() {
        var evaluation = evaluator(Clock.fixed(NOW, ZoneOffset.UTC), event(NOW, "urn:other"))
                .evaluate(PolicyVerifierTest.envelope("AQ=="));
        assertEquals(
                ReceptionFailure.Code.AUDIENCE_MISMATCH,
                evaluation.decisions().get(0).rejectionCode());
        assertEquals(
                ReceptionFailure.Code.POLICY_REJECTED,
                evaluation.decisions().get(2).rejectionCode());
    }

    /** A provider failure cannot be misreported as four successful negative experiments. */
    @Test
    void propagatesOperationalFailures() {
        var broken = new TrustedPublisher(
                "publisher-a",
                Map.of(
                        SignatureAlgorithm.ED25519,
                                (message, signature) -> {
                                    throw new IllegalStateException("Provider unavailable");
                                },
                        SignatureAlgorithm.ML_DSA_65, (message, signature) -> false));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var validator = new EventValidator(
                codec(event(NOW, "urn:receiver")),
                clock,
                "urn:receiver",
                Duration.ofMinutes(5),
                Duration.ofSeconds(30));
        var evaluator = new MigrationEvaluator(new PolicyVerifier(List.of(broken)), validator, clock);
        assertThrows(IllegalStateException.class, () -> evaluator.evaluate(dual()));
    }

    /** Wires pure application ports with no persistence capability. */
    private MigrationEvaluator evaluator(Clock clock, ReleaseEvent event) {
        var validator =
                new EventValidator(codec(event), clock, "urn:receiver", Duration.ofMinutes(5), Duration.ofSeconds(30));
        return new MigrationEvaluator(
                new PolicyVerifier(List.of(PolicyVerifierTest.publisher("publisher-a", 1, 2))), validator, clock);
    }

    /** Supplies a business-codec spy after the policy boundary has authenticated its bytes. */
    private ReleaseEventCodec codec(ReleaseEvent event) {
        return new ReleaseEventCodec() {
            /** {@inheritDoc} */
            @Override
            public byte[] encode(ReleaseEvent ignored) {
                return new byte[] {42};
            }
            /** {@inheritDoc} */
            @Override
            public ReleaseEvent decode(byte[] bytes) {
                return event;
            }
        };
    }

    /** Uses explicit trusted markers without pretending they are cryptographic signatures. */
    private DsseEnvelope dual() {
        return new DsseEnvelope(
                ReleaseEvent.PAYLOAD_TYPE,
                "Kg==",
                List.of(new DsseSignature("", "AQ=="), new DsseSignature("", "Ag==")));
    }

    /** Creates a bounded event for the selected time and audience. */
    private ReleaseEvent event(Instant time, String audience) {
        return new ReleaseEvent(1, UUID.randomUUID(), "publisher-a", audience, time, "service", "1", "a".repeat(64));
    }
}
