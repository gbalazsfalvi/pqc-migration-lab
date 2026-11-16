package hu.balazsfalvi.pqclab.integration.app.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Detects Git checkout normalization, missing bodies and unlisted files in the published example bundle. */
final class PublicExampleEvidenceTest {
    /** The committed fixture must reproduce its original byte manifest on Windows and Linux checkouts. */
    @Test
    void preservesEveryReviewedPublicArtifactExactly() throws Exception {
        Path bundle = repository().resolve("docs/evidence/06b-compose");
        Set<Path> expected = new HashSet<>();
        expected.add(bundle.resolve("SHA256SUMS"));
        for (String line : Files.readAllLines(bundle.resolve("SHA256SUMS"), StandardCharsets.UTF_8)) {
            String[] entry = line.split("  ", 2);
            assertEquals(2, entry.length, "Every manifest entry needs a digest and relative path.");
            assertTrue(entry[0].matches("[0-9a-f]{64}"));
            Path artifact = bundle.resolve(entry[1]).normalize();
            assertTrue(artifact.startsWith(bundle), "Public paths cannot escape the evidence directory.");
            assertTrue(expected.add(artifact), "A manifest must not repeat an artifact.");
            assertTrue(Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS), entry[1]);
            assertEquals(entry[0], MigrationJournal.sha256(Files.readAllBytes(artifact)), entry[1]);
        }
        try (var tree = Files.walk(bundle)) {
            Set<Path> actual = tree.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(expected, actual, "Only reviewed manifest-listed public files may be shipped.");
        }
    }

    /** Finds the checked-out source from the repository root or the Gradle module test working directory. */
    private Path repository() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 3 && candidate != null; depth++, candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) return candidate;
        }
        throw new IllegalStateException("Run the example evidence check from the laboratory checkout.");
    }
}
