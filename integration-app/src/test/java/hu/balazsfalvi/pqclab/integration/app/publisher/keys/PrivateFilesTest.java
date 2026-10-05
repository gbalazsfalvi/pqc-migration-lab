package hu.balazsfalvi.pqclab.integration.app.publisher.keys;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the actual host filesystem boundary protecting the external account secret. */
final class PrivateFilesTest {
    @TempDir
    Path root;

    /** File protection is applied at creation, and reads remain bounded before decryption or parsing. */
    @Test
    void createsProtectedFilesAndBoundsReads() throws Exception {
        Path directory = root.resolve("private/nested");
        PrivateFiles.directory(directory);
        Path file = directory.resolve("account.key");
        PrivateFiles.create(file, new byte[32]);
        assertEquals(32, PrivateFiles.read(file, 32).length);
        assertThrows(IOException.class, () -> PrivateFiles.read(file, 8));
        assertThrows(IOException.class, () -> PrivateFiles.requirePrivate(directory));
        assertThrows(IOException.class, () -> PrivateFiles.directory(file));
        assertThrows(IOException.class, () -> PrivateFiles.create(file, new byte[32]));
        PrivateFiles.replace(file, new byte[16]);
        assertEquals(16, PrivateFiles.read(file, 32).length);
    }

    /** A file readable by another principal is refused rather than silently accepted or repaired. */
    @Test
    void rejectsBroadenedSecretPermissions() throws Exception {
        Path file = root.resolve("permission-test.key");
        PrivateFiles.create(file, new byte[32]);
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        } else {
            var view = Files.getFileAttributeView(file, AclFileAttributeView.class);
            var principal = Files.getOwner(file.getRoot());
            var owner = file.getFileSystem()
                    .getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            org.junit.jupiter.api.Assumptions.assumeFalse(principal.equals(owner));
            var acl = new ArrayList<>(view.getAcl());
            acl.add(AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(principal)
                    .setPermissions(Set.of(AclEntryPermission.READ_DATA))
                    .build());
            view.setAcl(acl);
        }
        assertThrows(IOException.class, () -> PrivateFiles.read(file, 32));
    }

    /** Canonical data-directory aliases resolve to one external account-secret namespace. */
    @Test
    void resolvesAccountSecretOutsideTheDataDirectory() throws Exception {
        Path data = Files.createDirectory(root.resolve("data"));
        Path first = EncryptedKeyVault.defaultSecretFile(data);
        Path alias = EncryptedKeyVault.defaultSecretFile(data.resolve("."));
        assertEquals(first, alias);
        assertFalse(first.startsWith(data));
        assertFalse(Files.exists(first), "Resolving a secret path must not create a credential.");
    }
}
