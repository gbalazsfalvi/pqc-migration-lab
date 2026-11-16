package hu.balazsfalvi.pqclab.integration.app.trust;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;

/** Coordinates public trust through a stable OS lock file and crash-fenced atomic snapshots. */
public final class SharedTrustStore {
    private final Path directory;
    private final String issuer;
    private final boolean writable;
    private final Duration deadline;
    private final PublicTrustCodec codec = new PublicTrustCodec();

    /**
     * Selects a dedicated public-only directory; readers never create or repair files.
     *
     * @param directory the operator-owned public trust namespace
     * @param issuer the configured owner
     * @param writable whether this instance is publisher-owned
     */
    public SharedTrustStore(Path directory, String issuer, boolean writable) {
        this(directory, issuer, writable, Duration.ofSeconds(5));
    }

    /** Supplies a short bounded contention deadline for focused lock tests. */
    SharedTrustStore(Path directory, String issuer, boolean writable, Duration deadline) {
        this.directory = directory.toAbsolutePath().normalize();
        this.issuer = issuer;
        this.writable = writable;
        this.deadline = deadline;
        if (deadline.isNegative() || deadline.isZero())
            throw new IllegalArgumentException("A lock deadline is required.");
    }

    /**
     * Reconciles public state from an already authenticated publisher vault at explicit startup.
     *
     * @param snapshot a strict public-only projection of the authenticated vault
     * @throws IOException if lineage, integrity or locking cannot be established
     */
    public void recover(byte[] snapshot) throws IOException {
        requireWriter();
        prepare();
        try (var channel = channel(true);
                var lease = lock(channel, false)) {
            if (!lease.isValid()) throw new IOException("The public trust lease is invalid.");
            var next = validate(snapshot);
            boolean initialized = Files.exists(path("trust.initialized"), LinkOption.NOFOLLOW_LINKS);
            boolean pending = Files.exists(path("trust.pending"), LinkOption.NOFOLLOW_LINKS);
            if (Files.exists(path("trust-state.json"), LinkOption.NOFOLLOW_LINKS)) {
                byte[] previous = bytes(path("trust-state.json"));
                var current = validate(previous);
                if (current.revision() > next.revision()
                        || (current.revision() == next.revision() && !Arrays.equals(previous, snapshot))
                        || (current.revision() < next.revision() && !pending)) {
                    throw new IOException("Public trust cannot roll back or change lineage.");
                }
            } else if (initialized && !pending) {
                throw new IOException("Initialized public trust is missing.");
            }
            publish(snapshot, () -> {});
        }
    }

    /**
     * Fences all receivers before persisting private state, then publishes the corresponding public revision.
     *
     * @param snapshot the next strict public-only state
     * @param persist the publisher-only durable mutation, invoked only under the exclusive lease
     * @throws IOException if persistence or publication cannot complete; pending state remains fail-closed
     */
    public void commit(byte[] snapshot, Persistence persist) throws IOException {
        requireWriter();
        try (var channel = channel(true);
                var lease = lock(channel, false)) {
            if (!lease.isValid()) throw new IOException("The public trust lease is invalid.");
            requireReady();
            long current = validate(bytes(path("trust-state.json"))).revision();
            if (validate(snapshot).revision() != current + 1) throw new IOException("Public trust revision conflict.");
            publish(snapshot, persist);
        }
    }

    /**
     * Holds current trust through the entire receiver operation, including its durable database commit.
     *
     * @param observation the public-only operation
     * @param <T> the operation result
     * @return the result observed under one public trust lease
     * @throws IOException if trust is unavailable, ambiguous or locked beyond the deadline
     */
    public <T> T read(Function<PublicTrustCodec.State, T> observation) throws IOException {
        try (var channel = channel(false);
                var lease = lock(channel, true)) {
            if (!lease.isValid()) throw new IOException("The public trust lease is invalid.");
            requireReady();
            return observation.apply(validate(bytes(path("trust-state.json"))));
        }
    }

    /** Creates only a genuinely new writer namespace; a deleted established lock is never silently replaced. */
    private void prepare() throws IOException {
        if (Files.isSymbolicLink(directory)) throw new IOException("Public trust must be an ordinary directory.");
        Files.createDirectories(directory);
        Path file = path("trust.lock");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(path("trust.initialized"), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(path("trust-state.json"), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(path("trust.pending"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("The established public trust lock is missing.");
            }
            Files.createFile(file, publicAttributes());
        }
    }

    /** Opens the stable lock inode without following links; a read-only receiver mount is sufficient. */
    private FileChannel channel(boolean write) throws IOException {
        if (Files.isSymbolicLink(directory) || !Files.isRegularFile(path("trust.lock"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A regular public trust lock is required.");
        }
        return FileChannel.open(
                path("trust.lock"),
                write
                        ? Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
                        : Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
    }

    /** Uses bounded NIO locks across JVMs; overlapping claims inside one JVM are also treated as contention. */
    private FileLock lock(FileChannel channel, boolean shared) throws IOException {
        long expires = System.nanoTime() + deadline.toNanos();
        do {
            try {
                FileLock lease = channel.tryLock(0, Long.MAX_VALUE, shared);
                if (lease != null) return lease;
            } catch (OverlappingFileLockException busy) {
                // Another operation in this JVM owns the same inode; never bypass its lease.
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Public trust lock acquisition was interrupted.");
            }
        } while (System.nanoTime() < expires);
        throw new IOException("Public trust is busy.");
    }

    /** Leaves the pending fence in place on any partial private/public commit or cleanup failure. */
    private void publish(byte[] snapshot, Persistence persist) throws IOException {
        replace(path("trust.pending"), new byte[] {1});
        persist.save();
        replace(path("trust-state.json"), snapshot);
        replace(path("trust.initialized"), new byte[] {1});
        Files.delete(path("trust.pending"));
    }

    /** Requires a complete initialized snapshot and no unfinished publisher mutation. */
    private void requireReady() throws IOException {
        if (!Arrays.equals(bytes(path("trust.initialized")), new byte[] {1})
                || Files.exists(path("trust.pending"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Public trust is not ready.");
        }
    }

    /** Converts parser errors to fixed safe filesystem diagnostics without retaining input fragments. */
    private PublicTrustCodec.State validate(byte[] document) throws IOException {
        try {
            return codec.decode(document, issuer);
        } catch (RuntimeException invalid) {
            throw new IOException("Public trust failed validation.");
        }
    }

    /** Reads a bounded ordinary file without following a replacement symbolic link. */
    private byte[] bytes(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("A public trust file is missing.");
        try (var channel = FileChannel.open(file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            var buffer = ByteBuffer.allocate(PublicTrustCodec.MAXIMUM_BYTES + 1);
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                /* Bounded snapshot. */
            }
            if (buffer.position() > PublicTrustCodec.MAXIMUM_BYTES) throw new IOException("Public trust is oversized.");
            return Arrays.copyOf(buffer.array(), buffer.position());
        }
    }

    /** Flushes a same-directory replacement and requires atomic rename support. */
    private void replace(Path target, byte[] document) throws IOException {
        if (Files.isSymbolicLink(target)) throw new IOException("Public trust files cannot be symbolic links.");
        Path temporary = Files.createTempFile(directory, "trust-", ".tmp", publicAttributes());
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(document);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Resolves fixed internal names only, never caller-supplied relative paths. */
    private Path path(String name) {
        return directory.resolve(name);
    }

    /** Allows a different receiver UID to read public metadata without granting it write access. */
    private java.nio.file.attribute.FileAttribute<?>[] publicAttributes() throws IOException {
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            return new java.nio.file.attribute.FileAttribute<?>[] {
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"))
            };
        }
        return new java.nio.file.attribute.FileAttribute<?>[0];
    }

    /** Prevents accidental repair or mutation through a receiver-owned store instance. */
    private void requireWriter() throws IOException {
        if (!writable) throw new IOException("The receiver has read-only public trust.");
    }

    /** Defines a publisher-only persistence operation; no callback is retained in receiver state. */
    @FunctionalInterface
    public interface Persistence {
        /**
         * Persists authenticated private state while all receiver operations are fenced.
         *
         * @throws IOException if durable private state cannot be committed
         */
        void save() throws IOException;
    }
}
