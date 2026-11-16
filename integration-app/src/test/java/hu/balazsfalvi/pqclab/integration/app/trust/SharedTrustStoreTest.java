package hu.balazsfalvi.pqclab.integration.app.trust;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.balazsfalvi.pqclab.integration.app.http.ApiFailure;
import hu.balazsfalvi.pqclab.integration.app.publisher.keys.KeyLifecycleManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises public-only lineage, interrupted publication and cross-instance OS lease coordination. */
final class SharedTrustStoreTest {
    @TempDir
    Path root;

    private final ObjectMapper json = new ObjectMapper();

    /** Returns a writer for this test's independent public namespace. */
    private SharedTrustStore writer() {
        return new SharedTrustStore(root.resolve("public"), "release-publisher", true);
    }

    /** Returns a receiver projection that cannot create, update or recover trust. */
    private FilePublicDirectory reader() {
        return new FilePublicDirectory(
                new SharedTrustStore(root.resolve("public"), "release-publisher", false), Clock.systemUTC());
    }

    /** Opens private state outside the public directory and its external wrapping secret. */
    private KeyLifecycleManager manager() throws IOException {
        return KeyLifecycleManager.open(
                root.resolve("publisher"),
                root.resolve("secret/account.key"),
                "release-publisher",
                Clock.systemUTC(),
                writer());
    }

    /** Receiver imports only public SPKI, follows rotation/revocation and persists no private representation. */
    @Test
    void importsPublicKeysAndObservesDurableLifecycle() throws Exception {
        String initial;
        try (var manager = manager()) {
            var first = reader().view();
            initial = first.activeGenerationId();
            assertEquals(1, first.revision());
            assertTrue(first.managed());
            assertFalse(
                    Files.readString(root.resolve("public/trust-state.json")).contains("pkcs8"));
            manager.rotate(1);
            assertEquals(2, reader().view().generations().size());
            manager.revoke(2, initial);
            assertEquals("REVOKED", reader().view().generations().getLast().state());
            long revision = reader().read(snapshot -> snapshot.revision());
            assertEquals(3L, revision);
        }
        try (var manager = manager()) {
            assertEquals(3, reader().view().revision());
            assertEquals(initial, reader().view().generations().getLast().id());
            assertEquals(
                    manager.publicDirectory().view().activeGenerationId(),
                    reader().view().activeGenerationId());
        }
    }

    /** A receiver with missing or pending trust fails closed and cannot repair the publisher's files. */
    @Test
    void interruptedPrivatePersistenceLeavesTrustFencedUntilAuthenticatedRecovery() throws Exception {
        assertThrows(ApiFailure.class, () -> reader().view());
        try (var manager = manager()) {
            byte[] original = Files.readAllBytes(root.resolve("public/trust-state.json"));
            var next = json.readTree(original);
            ((com.fasterxml.jackson.databind.node.ObjectNode) next).put("revision", 2);
            assertThrows(
                    IOException.class,
                    () -> writer().commit(json.writeValueAsBytes(next), () -> {
                        throw new IOException("Synthetic persistence failure.");
                    }));
            assertTrue(Files.exists(root.resolve("public/trust.pending")));
            assertThrows(ApiFailure.class, () -> reader().view());
            var readOnly = new SharedTrustStore(root.resolve("public"), "release-publisher", false);
            assertThrows(IOException.class, () -> readOnly.recover(original));
            assertThrows(IOException.class, () -> readOnly.commit(original, () -> {}));
            assertEquals(1, manager.publicDirectory().view().revision());
        }
        try (var reopened = manager()) {
            assertEquals(1, reader().view().revision());
            assertFalse(Files.exists(root.resolve("public/trust.pending")));
            assertNotNull(reopened.signingIdentity());
        }
    }

    /** A public publication failure stops new signing until authenticated restart recovery. */
    @Test
    void publicationFailureStopsPublisherSigningAndFurtherMutations() throws Exception {
        Path snapshot = root.resolve("public/trust-state.json");
        try (var manager = manager()) {
            byte[] original = Files.readAllBytes(snapshot);
            String active = manager.publicDirectory().view().activeGenerationId();
            Files.delete(snapshot);
            Files.createDirectory(snapshot);
            assertEquals(
                    "KEY_STORE_UNAVAILABLE",
                    assertThrows(ApiFailure.class, () -> manager.rotate(1)).code());
            assertEquals(
                    "KEY_STORE_UNAVAILABLE",
                    assertThrows(
                                    ApiFailure.class,
                                    () -> manager.signingIdentity()
                                            .signers(java.util.List.of(
                                                    hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm.ED25519)))
                            .code());
            assertThrows(ApiFailure.class, () -> manager.revoke(1, active));
            assertThrows(ApiFailure.class, () -> manager.rotate(1));
            Files.delete(snapshot);
            Files.write(snapshot, original);
            assertThrows(
                    ApiFailure.class,
                    () -> manager.signingIdentity()
                            .signers(java.util.List.of(hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm.ED25519)));
        }
        try (var recovered = manager()) {
            assertEquals(1, reader().view().revision());
            assertFalse(recovered
                    .signingIdentity()
                    .signers(java.util.List.of(hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm.ED25519))
                    .isEmpty());
        }
    }

    /** An in-flight receiver operation holds the trust lease through the durable application boundary. */
    @Test
    void writerCannotPersistWhileReceiverOwnsItsLease() throws Exception {
        try (var manager = manager();
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var observation = executor.submit(() -> reader().read(snapshot -> {
                entered.countDown();
                try {
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return snapshot.revision();
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var rotation = executor.submit(() -> manager.rotate(1));
            try {
                assertThrows(TimeoutException.class, () -> rotation.get(80, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            assertEquals(1L, observation.get(2, TimeUnit.SECONDS));
            assertEquals(2, rotation.get(2, TimeUnit.SECONDS).revision());
            assertEquals(2, reader().view().revision());
        }
    }

    /** Corruption, deletion and revision rollback are rejected rather than silently changing trust. */
    @Test
    void rejectsCorruptionMissingInitializedFilesAndRollback() throws Exception {
        byte[] old;
        try (var manager = manager()) {
            old = Files.readAllBytes(root.resolve("public/trust-state.json"));
            manager.rotate(1);
            assertThrows(IOException.class, () -> writer().recover(old));
            assertThrows(IOException.class, () -> writer().commit(old, () -> {}));
            Path snapshot = root.resolve("public/trust-state.json");
            byte[] current = Files.readAllBytes(snapshot);
            Files.writeString(snapshot, "{}");
            assertThrows(ApiFailure.class, () -> reader().view());
            assertThrows(IOException.class, () -> writer().recover(current));
            Files.write(snapshot, current);
            Files.delete(snapshot);
            assertThrows(IOException.class, () -> writer().recover(current));
            Files.write(snapshot, current);
            Files.delete(root.resolve("public/trust.lock"));
            assertThrows(IOException.class, () -> writer().recover(current));
            assertThrows(ApiFailure.class, () -> reader().view());
        }
    }

    /** The public schema refuses private encodings, unknown algorithms, ambiguous JSON and malformed counters. */
    @Test
    void validatesEveryPublicMetadataBoundary() throws Exception {
        try (var manager = manager()) {
            byte[] original = Files.readAllBytes(root.resolve("public/trust-state.json"));
            var codec = new PublicTrustCodec();
            assertEquals(1, codec.decode(original, "release-publisher").revision());
            for (String field :
                    new String[] {"formatVersion", "issuer", "revision", "activeGenerationId", "generations"}) {
                var document = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(original);
                document.remove(field);
                assertThrows(
                        IllegalArgumentException.class,
                        () -> codec.decode(json.writeValueAsBytes(document), "release-publisher"));
            }
            var privateField = json.readTree(original);
            ((com.fasterxml.jackson.databind.node.ObjectNode)
                            privateField.path("generations").get(0).path("keys").get(0))
                    .put("pkcs8", "forbidden");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec.decode(json.writeValueAsBytes(privateField), "release-publisher"));
            assertThrows(IllegalArgumentException.class, () -> codec.decode(original, "another-owner"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec.decode(new byte[PublicTrustCodec.MAXIMUM_BYTES + 1], "release-publisher"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec.decode(
                            "{\"revision\":1,\"revision\":2}".getBytes(StandardCharsets.UTF_8), "release-publisher"));
            assertThrows(
                    IllegalArgumentException.class, () -> new SharedTrustStore(root, "owner", false, Duration.ZERO));
            assertNotNull(manager.publicDirectory());
        }
    }
}
