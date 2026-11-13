package hu.balazsfalvi.pqclab.integration.app.publisher.keys;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises actual protected files, authenticated encryption, process locking and durable key recovery. */
final class EncryptedKeyVaultTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path root;

    /** A new protected vault survives reopening with exactly the same public identities and private capability. */
    @Test
    void persistsEncryptedKeysAndReopensWithoutRegeneration() throws Exception {
        Path data = root.resolve("data");
        Path secret = root.resolve("account/secret.key");
        String generation;
        java.util.List<hu.balazsfalvi.pqclab.integration.app.trust.KeyRegistryView.Key> publicKeys;
        try (var vault = EncryptedKeyVault.open(data, secret, "release-publisher", CLOCK)) {
            var state = vault.load();
            generation = state.activeId();
            publicKeys = state.active().publicOnly().keys();
            assertEquals(1, state.revision());
            assertTrue(state.active().pairs().values().stream().allMatch(pair -> pair.getPrivate() != null));
            assertEquals(32, Files.size(secret));
            PrivateFiles.requirePrivate(secret);
            byte[] sealed = Files.readAllBytes(data.resolve("keys/vault.pqckey"));
            assertTrue(sealed.length > 128);
            String ciphertext = new String(sealed, StandardCharsets.ISO_8859_1);
            assertFalse(ciphertext.contains("pkcs8"));
            assertFalse(ciphertext.contains("release-publisher"));
            assertTrue(state.toString().contains("REDACTED"));
            assertTrue(state.active().toString().contains("REDACTED"));
            assertThrows(IOException.class, () -> EncryptedKeyVault.open(data, secret, "release-publisher", CLOCK));
        }
        // A complete authenticated pair may recover the final marker step after interrupted initialization.
        Files.delete(data.resolve("keys/vault.initialized"));
        try (var vault = EncryptedKeyVault.open(data, secret, "release-publisher", CLOCK)) {
            assertEquals(generation, vault.load().activeId());
            assertEquals(publicKeys, vault.load().active().publicOnly().keys());
            assertTrue(Files.exists(data.resolve("keys/vault.initialized")));
        }
    }

    /** GCM detects a modified ciphertext and no recovery path silently replaces the old trusted keys. */
    @Test
    void refusesCiphertextTampering() throws Exception {
        Path data = root.resolve("data");
        Path secret = root.resolve("account.key");
        try (var ignored = EncryptedKeyVault.open(data, secret, "release-publisher", CLOCK)) {
            assertNotNull(ignored);
        }
        Path file = data.resolve("keys/vault.pqckey");
        byte[] sealed = Files.readAllBytes(file);
        sealed[sealed.length - 1] ^= 1;
        Files.write(file, sealed);
        assertThrows(IOException.class, () -> EncryptedKeyVault.open(data, secret, "release-publisher", CLOCK));
        assertEquals(sealed.length, Files.size(file));
    }

    /** Losing either half of the vault/secret pair is an explicit failure, never an invitation to regenerate. */
    @Test
    void refusesMissingVaultOrAccountSecret() throws Exception {
        for (String missing : java.util.List.of("secret", "vault", "both")) {
            Path data = root.resolve("missing-" + missing);
            Path secret = root.resolve("secret-" + missing + ".key");
            try (var ignored = EncryptedKeyVault.open(data, secret, "release-publisher", CLOCK)) {
                assertNotNull(ignored);
            }
            Path file = data.resolve("keys/vault.pqckey");
            if (!missing.equals("vault")) Files.delete(secret);
            if (!missing.equals("secret")) Files.delete(file);
            assertThrows(IOException.class, () -> EncryptedKeyVault.open(data, secret, "release-publisher", CLOCK));
            if (!missing.equals("vault")) assertFalse(Files.exists(secret));
            if (!missing.equals("secret")) assertFalse(Files.exists(file));
        }
    }

    /** Wrong owner binding, wrong wrapping bytes and malformed headers all fail closed. */
    @Test
    void refusesWrongSecretOwnerAndFormat() throws Exception {
        Path data = root.resolve("data");
        Path secret = root.resolve("account.key");
        try (var ignored = EncryptedKeyVault.open(data, secret, "release-publisher", CLOCK)) {
            assertNotNull(ignored);
        }
        assertThrows(IOException.class, () -> EncryptedKeyVault.open(data, secret, "other-owner", CLOCK));
        byte[] original = Files.readAllBytes(secret);
        try {
            Files.write(secret, new byte[32]);
            assertThrows(IOException.class, () -> EncryptedKeyVault.open(data, secret, "release-publisher", CLOCK));
            Files.write(secret, new byte[3]);
            assertThrows(IOException.class, () -> EncryptedKeyVault.open(data, secret, "release-publisher", CLOCK));
        } finally {
            Files.write(secret, original);
            java.util.Arrays.fill(original, (byte) 0);
        }
        Files.write(data.resolve("keys/vault.pqckey"), new byte[12]);
        assertThrows(IOException.class, () -> EncryptedKeyVault.open(data, secret, "release-publisher", CLOCK));
        assertThrows(
                IOException.class,
                () -> EncryptedKeyVault.open(
                        root.resolve("invalid"), root.resolve("invalid/secret"), "release-publisher", CLOCK));
    }
}
