package hu.balazsfalvi.pqclab.integration.app.trust;

import hu.balazsfalvi.pqclab.core.crypto.MessageVerifier;
import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import hu.balazsfalvi.pqclab.integration.core.TrustedPublisher;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/** Holds public keys only, sharing an operation lock with the trusted publisher-side lifecycle manager. */
public final class RotatingPublicDirectory implements PublicKeyDirectory {
    /** Bounds the maximum verification work and durable local history. */
    public static final int MAXIMUM_GENERATIONS = 16;
    /** Gives already-issued events ten minutes of overlap after a rotation. */
    public static final long GRACE_SECONDS = 600;

    private final String issuer;
    private final Clock clock;
    private final ReentrantReadWriteLock lock;
    private long revision;
    private String activeId;
    private List<PublicGeneration> generations;

    /**
     * Receives independent public verifier objects, never a key pair, private-key loader or signing closure.
     *
     * @param issuer the stable configured owner
     * @param clock the trusted clock
     * @param lock the shared read/write coordination boundary
     * @param revision the loaded durable revision
     * @param activeId the generation selected by the publisher
     * @param generations public-only imported generations
     */
    public RotatingPublicDirectory(
            String issuer,
            Clock clock,
            ReentrantReadWriteLock lock,
            long revision,
            String activeId,
            List<PublicGeneration> generations) {
        this.issuer = issuer;
        this.clock = clock;
        this.lock = lock;
        this.revision = revision;
        this.activeId = activeId;
        this.generations = List.copyOf(generations);
    }

    /** {@inheritDoc} */
    @Override
    public <T> T read(Function<Snapshot, T> observation) {
        lock.readLock().lock();
        try {
            var now = clock.instant();
            var eligible = generations.stream()
                    .filter(generation -> generation.trustedAt(now))
                    .toList();
            var verifiers = new EnumMap<SignatureAlgorithm, MessageVerifier>(SignatureAlgorithm.class);
            for (SignatureAlgorithm algorithm : SignatureAlgorithm.values()) {
                List<MessageVerifier> candidates = eligible.stream()
                        .map(generation -> generation.verifiers().get(algorithm))
                        .toList();
                verifiers.put(algorithm, (message, signature) -> {
                    boolean verified = false;
                    for (MessageVerifier candidate : candidates) verified |= candidate.verify(message, signature);
                    return verified;
                });
            }
            return observation.apply(new Snapshot(revision, List.of(new TrustedPublisher(issuer, verifiers))));
        } finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public KeyRegistryView view() {
        lock.readLock().lock();
        try {
            var now = clock.instant();
            var rows = new ArrayList<KeyRegistryView.Generation>();
            generations.stream()
                    .sorted(Comparator.comparingInt(PublicGeneration::sequence).reversed())
                    .forEach(generation -> rows.add(new KeyRegistryView.Generation(
                            generation.id(),
                            generation.sequence(),
                            generation.stateAt(now),
                            generation.createdAt().toString(),
                            text(generation.retiringAt()),
                            text(generation.acceptUntil()),
                            text(generation.revokedAt()),
                            generation.keys())));
            return new KeyRegistryView(
                    true, revision, issuer, activeId, now.toString(), GRACE_SECONDS, MAXIMUM_GENERATIONS, rows);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Publishes a successfully persisted revision while the lifecycle writer excludes all receivers.
     *
     * @param revision the new durable revision
     * @param activeId the publisher's selected generation
     * @param generations prevalidated public-only generations
     */
    public void replace(long revision, String activeId, List<PublicGeneration> generations) {
        if (!lock.isWriteLockedByCurrentThread())
            throw new IllegalStateException("Trust replacement needs the write lease.");
        this.revision = revision;
        this.activeId = activeId;
        this.generations = List.copyOf(generations);
    }

    /** Keeps absent lifecycle timestamps explicit in public JSON. */
    private String text(java.time.Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
