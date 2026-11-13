package hu.balazsfalvi.pqclab.integration.app.publisher.keys;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Creates secret files with owner-only access and refuses symlinks or unsupported protection models. */
final class PrivateFiles {
    /** Prevents construction of a filesystem utility. */
    private PrivateFiles() {}

    /** Creates a private leaf directory without changing permissions on existing user directories. */
    static void directory(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Secret directories cannot be symbolic links.");
        if (!Files.exists(path)) {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) directory(parent);
            Files.createDirectory(path, attributes(parent, true));
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("A secret directory is required.");
    }

    /** Chooses atomic creation attributes for supported POSIX or Windows ACL filesystems. */
    static FileAttribute<?>[] attributes(Path parent, boolean directory) throws IOException {
        if (Files.getFileStore(parent).supportsFileAttributeView("posix")) {
            return new FileAttribute<?>[] {
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"))
            };
        }
        if (Files.getFileStore(parent).supportsFileAttributeView("acl")) {
            var owner = parent.getFileSystem()
                    .getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            var entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
            return new FileAttribute<?>[] {
                new FileAttribute<List<AclEntry>>() {
                    @Override
                    public String name() {
                        return "acl:acl";
                    }

                    @Override
                    public List<AclEntry> value() {
                        return List.of(entry);
                    }
                }
            };
        }
        throw new IOException("This filesystem cannot protect local key material.");
    }

    /** Validates access before reading an existing secret, without silently broadening or rewriting ACLs. */
    static void requirePrivate(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("A regular secret file is required.");
        var store = Files.getFileStore(path);
        if (store.supportsFileAttributeView("posix")) {
            var permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (permissions.stream().anyMatch(permission -> !permission.name().startsWith("OWNER_"))) {
                throw new IOException("Secret access must be restricted to the owner.");
            }
        } else if (store.supportsFileAttributeView("acl")) {
            var owner = path.getFileSystem()
                    .getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            var view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view.getAcl().stream()
                    .anyMatch(entry -> entry.type() == AclEntryType.ALLOW
                            && !entry.principal().equals(owner)
                            && !entry.permissions().isEmpty())) {
                throw new IOException("Secret access must be restricted to the owner.");
            }
        } else {
            throw new IOException("This filesystem cannot verify secret protection.");
        }
    }

    /** Reads bounded regular-file bytes without following a replacement symbolic link. */
    static byte[] read(Path path, int maximum) throws IOException {
        requirePrivate(path);
        try (var channel = FileChannel.open(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            ByteBuffer buffer = ByteBuffer.allocate(maximum + 1);
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                /* Bounded file snapshot. */
            }
            if (buffer.position() > maximum) throw new IOException("The key file exceeds its bounded format.");
            return java.util.Arrays.copyOf(buffer.array(), buffer.position());
        }
    }

    /** Creates an owner-only secret once and flushes its bytes before closing. */
    static void create(Path path, byte[] bytes) throws IOException {
        try (var channel = FileChannel.open(
                path,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                attributes(path.getParent(), false))) {
            write(channel, bytes);
        }
        requirePrivate(path);
    }

    /** Atomically replaces the encrypted vault; unsupported atomic moves fail without a non-atomic fallback. */
    static void replace(Path path, byte[] bytes) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("A key vault cannot be a symbolic link.");
        Path temporary = Files.createTempFile(path.getParent(), "vault-", ".tmp", attributes(path.getParent(), false));
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                write(channel, bytes);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Flushes complete bytes; directory metadata durability still follows the host filesystem's guarantees. */
    private static void write(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
        channel.force(true);
    }
}
