package hu.balazsfalvi.pqclab.integration.app.publisher.keys;

import hu.balazsfalvi.pqclab.core.crypto.MessageSigner;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.crypto.JcaSignatureEngine;
import hu.balazsfalvi.pqclab.integration.app.http.ApiFailure;
import hu.balazsfalvi.pqclab.integration.app.publisher.SigningIdentity;
import hu.balazsfalvi.pqclab.integration.app.trust.KeyRegistryView;
import hu.balazsfalvi.pqclab.integration.app.trust.PublicGeneration;
import hu.balazsfalvi.pqclab.integration.app.trust.PublicKeyDirectory;
import hu.balazsfalvi.pqclab.integration.app.trust.RotatingPublicDirectory;
import hu.balazsfalvi.pqclab.integration.app.trust.SharedTrustStore;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Owns private signing state, durable mutations and one public-only directory for the receiver. */
public final class KeyLifecycleManager implements AutoCloseable {
    private final EncryptedKeyVault vault;
    private final Clock clock;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final RotatingPublicDirectory directory;
    private final SigningIdentity identity;
    private VaultState state;
    private Map<SignatureAlgorithm, MessageSigner> signingPorts;
    private final SharedTrustStore publicStore;
    private boolean publicationFailed;

    /** Builds immutable public and private capabilities from an authenticated vault. */
    private KeyLifecycleManager(EncryptedKeyVault vault, Clock clock, VaultState state, SharedTrustStore publicStore)
            throws IOException {
        this.vault = vault;
        this.clock = clock;
        this.state = state;
        this.publicStore = publicStore;
        signingPorts = signingPorts(state);
        directory = new RotatingPublicDirectory(
                state.issuer(), clock, lock, state.revision(), state.activeId(), publicOnly(state));
        identity = new SigningIdentity(state.issuer(), this::currentSigners);
        if (publicStore != null) publicStore.recover(new VaultCodec().encodePublic(state));
    }

    /**
     * Opens durable managed state and releases the process lock if validation fails.
     *
     * @param dataDirectory the durable ledger/key namespace
     * @param secretFile the separate account secret
     * @param issuer the trusted configured publisher
     * @param clock the trusted lifecycle clock
     * @return a publisher-only lifecycle manager
     * @throws IOException if the vault cannot be authenticated or protected
     */
    public static KeyLifecycleManager open(Path dataDirectory, Path secretFile, String issuer, Clock clock)
            throws IOException {
        return open(dataDirectory, secretFile, issuer, clock, null);
    }

    /**
     * Opens publisher-only private state with an independently mounted public trust projection.
     *
     * @param dataDirectory the private publisher namespace
     * @param secretFile the external owner-only account secret
     * @param issuer the configured publisher owner
     * @param clock the trusted lifecycle clock
     * @param publicStore the publisher-owned public snapshot writer
     * @return the locked lifecycle manager
     * @throws IOException if private or public state cannot be reconciled safely
     */
    public static KeyLifecycleManager open(
            Path dataDirectory, Path secretFile, String issuer, Clock clock, SharedTrustStore publicStore)
            throws IOException {
        var vault = EncryptedKeyVault.open(dataDirectory, secretFile, issuer, clock);
        try {
            return new KeyLifecycleManager(vault, clock, vault.load(), publicStore);
        } catch (IOException | RuntimeException failure) {
            vault.close();
            throw failure;
        }
    }

    /**
     * Supplies only public verifier objects to the receiver.
     *
     * @return a public-only directory with no private manager closure
     */
    public PublicKeyDirectory publicDirectory() {
        return directory;
    }

    /**
     * Supplies the publisher with a per-operation active-generation snapshot.
     *
     * @return private signing capabilities, never installed in the receiver
     */
    public SigningIdentity signingIdentity() {
        return identity;
    }

    /**
     * Rotates both algorithms together with a fixed ten-minute public verification overlap.
     *
     * @param expectedRevision the operator's observed registry revision
     * @return the newly persisted public state
     */
    public KeyRegistryView rotate(long expectedRevision) {
        lock.writeLock().lock();
        try {
            requireRevision(expectedRevision);
            if (state.generations().size() >= RotatingPublicDirectory.MAXIMUM_GENERATIONS) {
                throw new ApiFailure(409, "KEY_GENERATION_LIMIT");
            }
            var now = clock.instant();
            var generations = new ArrayList<>(state.generations());
            generations.set(generations.size() - 1, state.active().retire(now, RotatingPublicDirectory.GRACE_SECONDS));
            var next = KeyGeneration.generate(generations.size() + 1, now);
            generations.add(next);
            commit(new VaultState(state.issuer(), state.revision() + 1, next.id(), generations));
            return directory.view();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Irreversibly removes a generation from verification, including exact retries with old receipts.
     *
     * @param expectedRevision the operator's observed revision
     * @param generationId a configured server-issued identifier
     * @return the persisted public state
     */
    public KeyRegistryView revoke(long expectedRevision, String generationId) {
        lock.writeLock().lock();
        try {
            requireRevision(expectedRevision);
            var target = state.generations().stream()
                    .filter(generation -> generation.id().equals(generationId))
                    .findFirst()
                    .orElseThrow(() -> new ApiFailure(404, "KEY_GENERATION_NOT_FOUND"));
            if (target.revokedAt() != null) return directory.view();
            var generations = state.generations().stream()
                    .map(generation ->
                            generation.id().equals(generationId) ? generation.revoke(clock.instant()) : generation)
                    .toList();
            commit(new VaultState(state.issuer(), state.revision() + 1, state.activeId(), generations));
            return directory.view();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Fences stale or concurrent operator actions rather than silently overwriting a newer state. */
    private void requireRevision(long expected) {
        if (publicationFailed) throw new ApiFailure(503, "KEY_STORE_UNAVAILABLE");
        if (state.revision() != expected) throw new ApiFailure(409, "KEY_REVISION_CONFLICT");
    }

    /** Persists first, then swaps prevalidated capabilities under the same exclusive operation boundary. */
    private void commit(VaultState next) {
        var publicState = publicOnly(next);
        var nextSigners = signingPorts(next);
        try {
            if (publicStore == null) {
                vault.save(next);
            } else {
                // The stable OS lease spans private persistence and public publication. A pending
                // marker makes partial commits unavailable until authenticated startup recovery.
                publicStore.commit(new VaultCodec().encodePublic(next), () -> vault.save(next));
            }
        } catch (IOException failure) {
            publicationFailed = publicStore != null;
            throw new ApiFailure(503, "KEY_STORE_UNAVAILABLE");
        }
        state = next;
        signingPorts = nextSigners;
        directory.replace(next.revision(), next.activeId(), publicState);
    }

    /** Reads all signing ports atomically; already-captured in-flight ports are still rechecked at reception. */
    private Map<SignatureAlgorithm, MessageSigner> currentSigners() {
        lock.readLock().lock();
        try {
            if (publicationFailed) throw new ApiFailure(503, "KEY_STORE_UNAVAILABLE");
            if (signingPorts.isEmpty()) throw new ApiFailure(503, "SIGNING_KEYS_REVOKED");
            return signingPorts;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Creates private ports only for the active, unrevoked generation. */
    private Map<SignatureAlgorithm, MessageSigner> signingPorts(VaultState selected) {
        if (selected.active().revokedAt() != null) return Map.of();
        var ports = new EnumMap<SignatureAlgorithm, MessageSigner>(SignatureAlgorithm.class);
        for (var algorithm : SignatureAlgorithm.values()) {
            var engine = JcaSignatureEngine.usingDefaultProvider(algorithm);
            ports.put(
                    algorithm,
                    engine.signer(
                            selected.active().keyId(algorithm),
                            selected.active().pairs().get(algorithm).getPrivate()));
        }
        return Map.copyOf(ports);
    }

    /** Copies independently imported public capabilities without retaining any private generation object. */
    private List<PublicGeneration> publicOnly(VaultState selected) {
        return selected.generations().stream().map(KeyGeneration::publicOnly).toList();
    }

    /** Releases the vault process lock only after local HTTP contexts have stopped. */
    @Override
    public void close() throws IOException {
        vault.close();
    }
}
