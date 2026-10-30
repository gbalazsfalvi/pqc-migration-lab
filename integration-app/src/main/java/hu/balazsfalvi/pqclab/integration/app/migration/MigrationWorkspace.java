package hu.balazsfalvi.pqclab.integration.app.migration;

import hu.balazsfalvi.pqclab.integration.app.runtime.LocalOptions;
import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/** Owns one system-temporary synthetic ledger and a separate account secret, never the interactive data directory. */
final class MigrationWorkspace implements AutoCloseable {
    private final Path root;

    /** Keeps the newly allocated directory private to this resource owner. */
    private MigrationWorkspace(Path root) {
        this.root = root;
    }

    /** @return a unique workspace, with no inherited runtime files or external secret-file override */
    static MigrationWorkspace create() throws IOException {
        return new MigrationWorkspace(Files.createTempDirectory("pqc-migration-demo-")
                .toAbsolutePath()
                .normalize());
    }

    /** @return fixed both-required settings and OS-assigned loopback ports */
    LocalOptions options() {
        return new LocalOptions(MigrationPolicy.BOTH_REQUIRED, 0, 0, root.resolve("integration"));
    }

    /** @return an explicit owner-protected secret path outside the integration data directory */
    Path secretFile() {
        return root.resolve("account.key");
    }

    /** Removes only this instance's created tree, without following symbolic links. */
    @Override
    public void close() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The demonstration workspace no longer has its expected directory identity.");
        }
        // The root is allocated by createTempDirectory, never supplied by a CLI or HTTP caller.
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            /** Removes files and links as entries; walkFileTree does not follow links. */
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            /** Removes a directory only after all of its children were handled successfully. */
            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
