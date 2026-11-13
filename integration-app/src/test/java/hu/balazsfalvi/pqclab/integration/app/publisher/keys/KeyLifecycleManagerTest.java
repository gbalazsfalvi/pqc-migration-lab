package hu.balazsfalvi.pqclab.integration.app.publisher.keys;

import static org.junit.jupiter.api.Assertions.*;

import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseSigner;
import hu.balazsfalvi.pqclab.integration.app.http.ApiFailure;
import hu.balazsfalvi.pqclab.integration.app.ledger.Sha256PayloadDigester;
import hu.balazsfalvi.pqclab.integration.app.trust.RotatingPublicDirectory;
import hu.balazsfalvi.pqclab.integration.app.wire.ReleaseJsonCodec;
import hu.balazsfalvi.pqclab.integration.core.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises actual lifecycle transitions, grace deadlines, write failures and commit/revocation ordering. */
final class KeyLifecycleManagerTest {
    @TempDir
    Path root;

    private final TestClock clock = new TestClock();

    /** Rotation keeps old public keys during overlap and drops obsolete private encodings from durable state. */
    @Test
    void rotatesThenRevokesWithoutWeakeningPolicies() throws Exception {
        Path data = root.resolve("data");
        Path secret = root.resolve("account.key");
        String first;
        String second;
        try (var manager = KeyLifecycleManager.open(data, secret, "release-publisher", clock)) {
            first = manager.publicDirectory().view().activeGenerationId();
            var old = envelope(manager);
            var before = new PolicyVerifier(manager.publicDirectory());
            assertAcceptedByEveryPolicy(before, old);
            var rotated = manager.rotate(1);
            second = rotated.activeGenerationId();
            assertNotEquals(first, second);
            assertEquals(2, rotated.revision());
            assertEquals("RETIRING", rotated.generations().get(1).state());
            assertAcceptedByEveryPolicy(before, old);
            var current = envelope(manager);
            assertAcceptedByEveryPolicy(before, current);
            var revoked = manager.revoke(2, first);
            assertEquals(3, revoked.revision());
            assertEquals("REVOKED", revoked.generations().get(1).state());
            for (var policy : MigrationPolicy.values()) {
                assertThrows(ReceptionFailure.class, () -> before.verify(old, policy));
            }
            assertAcceptedByEveryPolicy(before, current);
            assertEquals(3, manager.revoke(3, first).revision());
            assertEquals(
                    409, assertThrows(ApiFailure.class, () -> manager.rotate(2)).status());
            assertEquals(
                    404,
                    assertThrows(
                                    ApiFailure.class,
                                    () -> manager.revoke(3, UUID.randomUUID().toString()))
                            .status());
        }
        try (var vault = EncryptedKeyVault.open(data, secret, "release-publisher", clock)) {
            var persisted = vault.load();
            assertEquals(second, persisted.activeId());
            assertTrue(persisted.generations().getFirst().pairs().values().stream()
                    .allMatch(pair -> pair.getPrivate() == null));
            assertTrue(persisted.active().pairs().values().stream().allMatch(pair -> pair.getPrivate() != null));
        }
    }

    /** A retiring generation is rejected at the exclusive deadline, including after a real vault reload. */
    @Test
    void enforcesGraceDeadlineAndPersistsActiveRevocation() throws Exception {
        Path data = root.resolve("data");
        Path secret = root.resolve("account.key");
        DsseEnvelope old;
        try (var manager = KeyLifecycleManager.open(data, secret, "release-publisher", clock)) {
            old = envelope(manager);
            manager.rotate(1);
            clock.advance(RotatingPublicDirectory.GRACE_SECONDS - 1);
            assertAcceptedByEveryPolicy(new PolicyVerifier(manager.publicDirectory()), old);
            clock.advance(1);
            assertEquals(
                    "RETIRED",
                    manager.publicDirectory().view().generations().get(1).state());
            assertThrows(
                    ReceptionFailure.class,
                    () -> new PolicyVerifier(manager.publicDirectory()).verify(old, MigrationPolicy.BOTH_REQUIRED));
            String active = manager.publicDirectory().view().activeGenerationId();
            manager.revoke(2, active);
            assertEquals(
                    "SIGNING_KEYS_REVOKED",
                    assertThrows(
                                    ApiFailure.class,
                                    () -> manager.signingIdentity().signers(List.of(SignatureAlgorithm.ED25519)))
                            .code());
        }
        try (var manager = KeyLifecycleManager.open(data, secret, "release-publisher", clock)) {
            assertEquals(3, manager.publicDirectory().view().revision());
            assertEquals(
                    "REVOKED",
                    manager.publicDirectory().view().generations().getFirst().state());
            assertThrows(ApiFailure.class, () -> envelope(manager));
            manager.rotate(3);
            assertAcceptedByEveryPolicy(new PolicyVerifier(manager.publicDirectory()), envelope(manager));
            assertThrows(
                    ReceptionFailure.class,
                    () -> new PolicyVerifier(manager.publicDirectory()).verify(old, MigrationPolicy.CLASSICAL_ONLY));
        }
    }

    /** A failed durable replacement leaves both receiver trust and active publisher capabilities unchanged. */
    @Test
    void failedWriteCannotPublishANewGeneration() throws Exception {
        Path data = root.resolve("data");
        Path secret = root.resolve("account.key");
        try (var manager = KeyLifecycleManager.open(data, secret, "release-publisher", clock)) {
            String active = manager.publicDirectory().view().activeGenerationId();
            var old = envelope(manager);
            Path file = data.resolve("keys/vault.pqckey");
            Path backup = data.resolve("keys/test-backup.pqckey");
            Files.move(file, backup);
            Files.createDirectory(file);
            try {
                var failure = assertThrows(ApiFailure.class, () -> manager.rotate(1));
                assertEquals("KEY_STORE_UNAVAILABLE", failure.code());
                assertEquals(active, manager.publicDirectory().view().activeGenerationId());
                assertEquals(1, manager.publicDirectory().view().revision());
                assertAcceptedByEveryPolicy(new PolicyVerifier(manager.publicDirectory()), old);
                assertAcceptedByEveryPolicy(new PolicyVerifier(manager.publicDirectory()), envelope(manager));
            } finally {
                Files.delete(file);
                Files.move(backup, file);
            }
        }
    }

    /** Revocation waits for an already-verified database commit, then excludes every subsequent old delivery. */
    @Test
    void revocationCannotSlipBetweenVerificationAndApplication() throws Exception {
        try (var manager = KeyLifecycleManager.open(
                root.resolve("data"), root.resolve("account.key"), "release-publisher", clock)) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var attempted = new CountDownLatch(1);
            var effects = new AtomicInteger();
            ReleaseLedger ledger = new ReleaseLedger() {
                @Override
                public Outcome record(StoredRelease candidate) {
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS))
                            throw new IllegalStateException("Test commit was not released.");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Test commit interrupted.");
                    }
                    effects.incrementAndGet();
                    return new Outcome(candidate, true);
                }

                @Override
                public Optional<StoredRelease> find(String issuer, UUID id) {
                    return Optional.empty();
                }
            };
            var receiver = new ReceptionService(
                    new PolicyVerifier(manager.publicDirectory()),
                    MigrationPolicy.BOTH_REQUIRED,
                    new ReleaseJsonCodec(),
                    ledger,
                    new Sha256PayloadDigester(),
                    clock,
                    "urn:pqclab:release-receiver",
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(30));
            var original = envelope(manager);
            String active = manager.publicDirectory().view().activeGenerationId();
            try (var executor = Executors.newFixedThreadPool(2)) {
                try {
                    var delivery = executor.submit(() -> receiver.receive(original));
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    var revoke = executor.submit(() -> {
                        attempted.countDown();
                        return manager.revoke(1, active);
                    });
                    assertTrue(attempted.await(5, TimeUnit.SECONDS));
                    assertFalse(revoke.isDone());
                    release.countDown();
                    assertTrue(delivery.get(5, TimeUnit.SECONDS).created());
                    assertEquals(2, revoke.get(5, TimeUnit.SECONDS).revision());
                    assertThrows(ReceptionFailure.class, () -> receiver.receive(original));
                    assertEquals(1, effects.get());
                } finally {
                    release.countDown();
                }
            }
        }
    }

    /** Concurrent signing snapshots never combine algorithms from different publisher generations. */
    @Test
    void concurrentRotationKeepsDualSigningGenerationsTogether() throws Exception {
        try (var manager = KeyLifecycleManager.open(
                        root.resolve("data"), root.resolve("account.key"), "release-publisher", clock);
                var executor = Executors.newFixedThreadPool(2)) {
            var rotating = executor.submit(() -> {
                for (int revision = 1; revision <= 5; revision++) manager.rotate(revision);
            });
            var publishing = executor.submit(() -> {
                for (int count = 0; count < 30; count++) {
                    var signed = envelope(manager);
                    var hints = signed.signatures().stream()
                            .map(entry -> entry.keyId().split(":")[0])
                            .distinct()
                            .toList();
                    assertEquals(1, hints.size());
                    assertAcceptedByEveryPolicy(new PolicyVerifier(manager.publicDirectory()), signed);
                }
            });
            rotating.get(20, TimeUnit.SECONDS);
            publishing.get(20, TimeUnit.SECONDS);
        }
    }

    /** Registry growth is explicitly bounded instead of allowing unbounded verification work. */
    @Test
    void capsTheGenerationHistory() throws Exception {
        try (var manager = KeyLifecycleManager.open(
                root.resolve("data"), root.resolve("account.key"), "release-publisher", clock)) {
            for (int revision = 1; revision < RotatingPublicDirectory.MAXIMUM_GENERATIONS; revision++)
                manager.rotate(revision);
            assertEquals(16, manager.publicDirectory().view().generations().size());
            assertEquals(
                    "KEY_GENERATION_LIMIT",
                    assertThrows(ApiFailure.class, () -> manager.rotate(16)).code());
        }
    }

    /** Creates valid signed claims while selecting both current private ports in one snapshot. */
    private DsseEnvelope envelope(KeyLifecycleManager manager) {
        var event = new ReleaseEvent(
                1,
                UUID.randomUUID(),
                "release-publisher",
                "urn:pqclab:release-receiver",
                clock.instant(),
                "synthetic-service",
                "1.0.0",
                "a".repeat(64));
        return new DsseSigner()
                .sign(
                        ReleaseEvent.PAYLOAD_TYPE,
                        new ReleaseJsonCodec().encode(event),
                        manager.signingIdentity()
                                .signers(List.of(SignatureAlgorithm.ED25519, SignatureAlgorithm.ML_DSA_65)));
    }

    /** Asserts actual verification under every unchanged algorithm requirement. */
    private void assertAcceptedByEveryPolicy(PolicyVerifier verifier, DsseEnvelope envelope) {
        for (var policy : MigrationPolicy.values())
            assertEquals("release-publisher", verifier.verify(envelope, policy).issuer());
    }

    /** Supplies trusted deterministic time without adding an HTTP clock override. */
    private static final class TestClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-13T10:00:00Z"));
        /** Advances only this test's trusted clock. */
        void advance(long seconds) {
            now.updateAndGet(value -> value.plusSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now.get(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
