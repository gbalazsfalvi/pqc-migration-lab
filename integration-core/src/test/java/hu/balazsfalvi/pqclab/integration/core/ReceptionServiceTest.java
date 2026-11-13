package hu.balazsfalvi.pqclab.integration.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies security ordering and fail-closed side effects independently of Spring and JDBC. */
final class ReceptionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-13T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String AUDIENCE = "urn:example:receiver";
    private final AtomicInteger decodes = new AtomicInteger();
    private final AtomicInteger writes = new AtomicInteger();
    private final AtomicReference<ReleaseEvent> event = new AtomicReference<>(validEvent());
    private final AtomicReference<StoredRelease> stored = new AtomicReference<>();

    /** An unauthenticated body is never handed to a business parser or a persistence operation. */
    @Test
    void rejectsBeforeParsingOrWriting() {
        ReceptionService service = service(Duration.ofMinutes(5), Duration.ofSeconds(30));
        assertEquals(
                ReceptionFailure.Code.POLICY_REJECTED,
                assertThrows(ReceptionFailure.class, () -> service.receive(PolicyVerifierTest.envelope("AA==")))
                        .code());
        assertEquals(0, decodes.get());
        assertEquals(0, writes.get());
    }

    /** A valid delivery uses the verified snapshot, records its policy, and applies the business effect once. */
    @Test
    void persistsOnlyValidatedOriginalBytes() {
        var result =
                service(Duration.ofMinutes(5), Duration.ofSeconds(30)).receive(PolicyVerifierTest.envelope("AQ=="));
        assertTrue(result.created());
        assertEquals(event.get(), result.release().event());
        assertEquals(NOW, result.release().acceptedAt());
        assertEquals(MigrationPolicy.CLASSICAL_ONLY, result.release().policy());
        assertEquals(new SignatureEvidence(true, false), result.release().evidence());
        assertEquals("c".repeat(64), result.release().payloadSha256());
        assertEquals(1, decodes.get());
        assertEquals(1, writes.get());
        assertTrue(ledger().find("publisher-a", event.get().eventId()).isPresent());
        assertFalse(ledger().find("other", event.get().eventId()).isPresent());
    }

    /** Signed but unacceptable claims are rejected without reserving the event identifier. */
    @ParameterizedTest
    @CsvSource({
        "publisher-b,urn:example:receiver,0,ISSUER_MISMATCH",
        "publisher-a,urn:example:other,0,AUDIENCE_MISMATCH",
        "publisher-a,urn:example:receiver,-301,EVENT_EXPIRED",
        "publisher-a,urn:example:receiver,31,EVENT_IN_FUTURE"
    })
    void rejectsClaimsBeforeApplyingEffects(
            String issuer, String audience, long offset, ReceptionFailure.Code expected) {
        event.set(new ReleaseEvent(
                1, UUID.randomUUID(), issuer, audience, NOW.plusSeconds(offset), "service", "1", "a".repeat(64)));
        assertEquals(
                expected,
                assertThrows(
                                ReceptionFailure.class,
                                () -> service(Duration.ofMinutes(5), Duration.ofSeconds(30))
                                        .receive(PolicyVerifierTest.envelope("AQ==")))
                        .code());
        assertEquals(1, decodes.get());
        assertEquals(0, writes.get());
    }

    /** Freshness configuration must not disable, reverse or unreasonably widen the window. */
    @ParameterizedTest
    @CsvSource({"0,0", "-1,0", "86401,0", "300,-1", "300,301"})
    void rejectsUnsafeFreshnessConfiguration(long age, long future) {
        assertThrows(
                IllegalArgumentException.class, () -> service(Duration.ofSeconds(age), Duration.ofSeconds(future)));
    }

    /** Every domain field remains bounded even when the caller bypasses the JSON adapter. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void rejectsInvalidDomainFields(int field) {
        assertThrows(
                ReceptionFailure.class,
                () -> new ReleaseEvent(
                        field == 0 ? 2 : 1,
                        UUID.randomUUID(),
                        field == 1 ? "BAD" : "publisher-a",
                        field == 2 ? "bad audience" : AUDIENCE,
                        NOW,
                        field == 3 ? "!artifact" : "service",
                        field == 4 ? "!version" : "1",
                        field == 5 ? null : "a".repeat(64)));
    }

    /** An incomplete stored result must fail before reaching a database. */
    @Test
    void validatesStoredFingerprintsAndRequiredClaims() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoredRelease(
                        UUID.randomUUID(),
                        validEvent(),
                        NOW,
                        MigrationPolicy.BOTH_REQUIRED,
                        new SignatureEvidence(true, true),
                        "invalid"));
        assertThrows(
                NullPointerException.class,
                () -> new ReleaseEvent(1, null, "publisher-a", AUDIENCE, NOW, "service", "1", "a".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReceptionService(
                        verifier(),
                        MigrationPolicy.CLASSICAL_ONLY,
                        codec(),
                        ledger(),
                        body -> "a".repeat(64),
                        CLOCK,
                        "",
                        Duration.ofMinutes(5),
                        Duration.ZERO));
    }

    /** Builds a pure receive service with explicitly labelled test ports. */
    private ReceptionService service(Duration age, Duration future) {
        return new ReceptionService(
                verifier(),
                MigrationPolicy.CLASSICAL_ONLY,
                codec(),
                ledger(),
                body -> {
                    assertArrayEquals(new byte[] {42}, body);
                    return "c".repeat(64);
                },
                CLOCK,
                AUDIENCE,
                age,
                future);
    }

    /** Supplies trusted verifier spies; cryptographic implementations are exercised in HTTP tests. */
    private PolicyVerifier verifier() {
        return new PolicyVerifier(List.of(PolicyVerifierTest.publisher("publisher-a", 1, 2)));
    }

    /** Observes that parsing receives exactly the verified payload snapshot. */
    private ReleaseEventCodec codec() {
        return new ReleaseEventCodec() {
            /** {@inheritDoc} */
            @Override
            public byte[] encode(ReleaseEvent release) {
                return new byte[] {42};
            }
            /** {@inheritDoc} */
            @Override
            public ReleaseEvent decode(byte[] verifiedBody) {
                assertArrayEquals(new byte[] {42}, verifiedBody);
                decodes.incrementAndGet();
                return event.get();
            }
        };
    }

    /** Counts durable application attempts without imitating SQL idempotency semantics. */
    private ReleaseLedger ledger() {
        return new ReleaseLedger() {
            /** {@inheritDoc} */
            @Override
            public Outcome record(StoredRelease candidate) {
                writes.incrementAndGet();
                stored.set(candidate);
                return new Outcome(candidate, true);
            }
            /** {@inheritDoc} */
            @Override
            public Optional<StoredRelease> find(String issuer, UUID id) {
                return Optional.ofNullable(stored.get())
                        .filter(value -> value.event().issuer().equals(issuer)
                                && value.event().eventId().equals(id));
            }
        };
    }

    /** Creates a syntactically valid, fresh synthetic event for application-ordering tests. */
    private static ReleaseEvent validEvent() {
        return new ReleaseEvent(1, UUID.randomUUID(), "publisher-a", AUDIENCE, NOW, "service", "1", "a".repeat(64));
    }
}
