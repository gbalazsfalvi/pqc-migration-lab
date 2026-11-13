package hu.balazsfalvi.pqclab.integration.app.publisher.keys;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Persists authenticated AES-256-GCM vault bytes with a separate owner-only account secret and process lock. */
public final class EncryptedKeyVault implements AutoCloseable {
    private static final byte[] MAGIC = "PQCLKEY1".getBytes(StandardCharsets.US_ASCII);
    private static final int MAXIMUM_BYTES = 1_048_576;
    private final Path vaultFile;
    private final String issuer;
    private final SecretKey wrappingKey;
    private final FileChannel lockChannel;
    private final FileLock processLock;
    private final SecureRandom random = new SecureRandom();
    private final VaultCodec codec = new VaultCodec();

    /** Retains the process lock and in-memory wrapping capability only on the publisher side. */
    private EncryptedKeyVault(
            Path vaultFile, String issuer, SecretKey wrappingKey, FileChannel lockChannel, FileLock processLock) {
        this.vaultFile = vaultFile;
        this.issuer = issuer;
        this.wrappingKey = wrappingKey;
        this.lockChannel = lockChannel;
        this.processLock = processLock;
    }

    /**
     * Resolves an account-scoped secret outside the checkout, or an explicitly configured local path.
     *
     * @param dataDirectory the durable integration identity namespace
     * @return an external secret-file path; no secret value is exposed
     * @throws IOException if the prepared data directory cannot be canonicalized
     */
    public static Path defaultSecretFile(Path dataDirectory) throws IOException {
        String configured = System.getenv("PQC_LAB_KEY_SECRET_FILE");
        if (configured != null && !configured.isBlank())
            return Path.of(configured).toAbsolutePath().normalize();
        String localAppData = System.getenv("LOCALAPPDATA");
        Path account = localAppData != null && !localAppData.isBlank()
                ? Path.of(localAppData).resolve("PqcMigrationLab/key-secrets")
                : Path.of(System.getProperty("user.home"), ".local", "share", "pqc-migration-lab", "key-secrets");
        String namespace =
                KeyGeneration.fingerprint(dataDirectory.toRealPath().toString().getBytes(StandardCharsets.UTF_8));
        return account.resolve(namespace + ".key").toAbsolutePath().normalize();
    }

    /**
     * Acquires one writer process and initializes only a genuinely new vault/secret pair.
     *
     * @param dataDirectory the local durable integration directory
     * @param secretFile the separate owner-protected 256-bit account secret file
     * @param issuer the trusted local owner
     * @param clock the initialization clock
     * @return a locked vault whose keys are never registered with the receiver
     * @throws IOException if protection, integrity, locking or initialization fails
     */
    public static EncryptedKeyVault open(Path dataDirectory, Path secretFile, String issuer, Clock clock)
            throws IOException {
        Path directory = dataDirectory.toAbsolutePath().normalize().resolve("keys");
        secretFile = secretFile.toAbsolutePath().normalize();
        if (secretFile.startsWith(dataDirectory.toAbsolutePath().normalize()))
            throw new IOException("The account secret must be outside the integration data directory.");
        PrivateFiles.directory(directory);
        Path lockFile = directory.resolve("vault.lock");
        if (Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)) PrivateFiles.requirePrivate(lockFile);
        FileChannel channel = FileChannel.open(
                lockFile,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PrivateFiles.attributes(directory, false));
        FileLock lock = null;
        try {
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException conflict) {
                throw new IOException("The key vault is already in use.");
            }
            if (lock == null) throw new IOException("The key vault is already in use.");
            Path file = directory.resolve("vault.pqckey");
            Path marker = directory.resolve("vault.initialized");
            boolean initialized = Files.exists(marker, LinkOption.NOFOLLOW_LINKS);
            boolean vaultExists = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
            boolean secretExists = Files.exists(secretFile, LinkOption.NOFOLLOW_LINKS);
            if (vaultExists != secretExists || (initialized && (!vaultExists || !secretExists)))
                throw new IOException("The vault or its account secret is missing; refusing to replace trust.");
            if (!vaultExists) {
                PrivateFiles.directory(secretFile.getParent());
                byte[] secret = new byte[32];
                new SecureRandom().nextBytes(secret);
                try {
                    PrivateFiles.create(secretFile, secret);
                } finally {
                    Arrays.fill(secret, (byte) 0);
                }
            }
            byte[] secret = PrivateFiles.read(secretFile, 32);
            if (secret.length != 32) throw new IOException("The account secret has an invalid size.");
            SecretKey key;
            try {
                key = new SecretKeySpec(secret, "AES");
            } finally {
                Arrays.fill(secret, (byte) 0);
            }
            var vault = new EncryptedKeyVault(file, issuer, key, channel, lock);
            if (!vaultExists) {
                var first = KeyGeneration.generate(1, clock.instant());
                vault.save(new VaultState(issuer, 1, first.id(), List.of(first)));
            }
            // Authenticate and validate the complete state before any HTTP listener starts.
            vault.load();
            if (initialized) {
                if (!Arrays.equals(MAGIC, PrivateFiles.read(marker, MAGIC.length))) {
                    throw new IOException("The key-vault initialization marker is invalid.");
                }
            } else {
                // Also recovers a complete authenticated pair left before an interrupted initial marker write.
                PrivateFiles.create(marker, MAGIC);
            }
            return vault;
        } catch (IOException | RuntimeException failure) {
            try {
                if (lock != null) lock.release();
            } finally {
                channel.close();
            }
            if (failure instanceof IOException io) throw io;
            throw new IOException("The local key vault could not be opened.", failure);
        }
    }

    /** Reads authenticated publisher-only state without retaining plaintext file bytes. */
    VaultState load() throws IOException {
        byte[] bytes = PrivateFiles.read(vaultFile, MAXIMUM_BYTES + 64);
        if (bytes.length < MAGIC.length + 12 + 16 || !Arrays.equals(MAGIC, Arrays.copyOf(bytes, MAGIC.length))) {
            throw new IOException("The local key vault format is invalid.");
        }
        byte[] nonce = Arrays.copyOfRange(bytes, MAGIC.length, MAGIC.length + 12);
        byte[] plaintext = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad());
            plaintext = cipher.doFinal(bytes, MAGIC.length + 12, bytes.length - MAGIC.length - 12);
            return codec.decode(plaintext, issuer);
        } catch (java.security.GeneralSecurityException | RuntimeException failure) {
            throw new IOException("The local key vault failed authentication or validation.", failure);
        } finally {
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    /** Writes a new authenticated state before a lifecycle change can become visible in memory. */
    void save(VaultState state) throws IOException {
        byte[] plaintext = codec.encode(state);
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad());
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] sealed = ByteBuffer.allocate(MAGIC.length + nonce.length + encrypted.length)
                    .put(MAGIC)
                    .put(nonce)
                    .put(encrypted)
                    .array();
            PrivateFiles.replace(vaultFile, sealed);
        } catch (java.security.GeneralSecurityException failure) {
            throw new IOException("The local key vault could not be encrypted.", failure);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    /** Authenticates the immutable format identifier and trusted owner along with all encrypted metadata. */
    private byte[] aad() {
        return ("pqc-migration-lab/key-vault/v1/" + issuer).getBytes(StandardCharsets.UTF_8);
    }

    /** Releases the process lock after both HTTP contexts have stopped. */
    @Override
    public void close() throws IOException {
        try {
            processLock.release();
        } finally {
            lockChannel.close();
        }
    }
}
