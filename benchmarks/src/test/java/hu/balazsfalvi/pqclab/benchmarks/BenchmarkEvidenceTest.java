package hu.balazsfalvi.pqclab.benchmarks;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Makes method identity, completeness and public-only evidence executable invariants. */
final class BenchmarkEvidenceTest {
    /** Named profiles retain actual forks, warm-up, units and explicit JVM arguments. */
    @ParameterizedTest
    @EnumSource(MeasurementProfile.class)
    void configuresExplicitUninstrumentedForks(MeasurementProfile profile, @TempDir Path directory) {
        assertEquals(profile, MeasurementProfile.parse(profile.id()));
        var options = profile.options(directory.resolve("jmh.json"), directory.resolve("harness.log"));
        assertEquals(profile.method().forks(), options.getForkCount().get());
        assertEquals(
                profile.method().warmupIterations(),
                options.getWarmupIterations().get());
        assertEquals(
                profile.method().measurementIterations(),
                options.getMeasurementIterations().get());
        assertEquals(1, options.getThreads().get());
        assertEquals(
                List.of("-Xms256m", "-Xmx256m"),
                List.copyOf(options.getJvmArgs().get()));
        assertTrue(options.shouldFailOnError().get());
        assertFalse(options.shouldDoGC().get());
        assertEquals(
                profile.expectedResults(), EvidenceFixtures.complete(profile).size());
        assertDoesNotThrow(() -> BenchmarkEvidence.requireComplete(profile, EvidenceFixtures.complete(profile)));
        assertEquals(
                profile.payloadSizes().size() * 3,
                BenchmarkEvidence.sizes(profile).size());
        assertEquals(
                profile == MeasurementProfile.SMOKE,
                BenchmarkEvidence.limitations(profile).getFirst().startsWith("SMOKE"));
    }

    /** A missing, duplicated, unexpected or misconfigured observation cannot be marked complete. */
    @Test
    void refusesPartialOrInconsistentResults() {
        var profile = MeasurementProfile.SMOKE;
        var complete = EvidenceFixtures.complete(profile);
        assertThrows(
                IllegalStateException.class,
                () -> BenchmarkEvidence.requireComplete(profile, complete.subList(1, complete.size())));
        var duplicate = new ArrayList<>(complete);
        duplicate.add(complete.getFirst());
        assertThrows(IllegalStateException.class, () -> BenchmarkEvidence.requireComplete(profile, duplicate));
        for (int invalid = 0; invalid < 4; invalid++) {
            var changed = new ArrayList<>(complete);
            var first = complete.getFirst();
            changed.set(
                    0,
                    new BenchmarkReport.Measurement(
                            invalid == 3 ? "unexpected" : first.operation(),
                            first.profile(),
                            null,
                            1,
                            null,
                            invalid == 0 ? "ns/op" : "us/op",
                            invalid == 1 ? 2 : 1,
                            invalid == 2 ? 2 : 1));
            assertThrows(IllegalStateException.class, () -> BenchmarkEvidence.requireComplete(profile, changed));
        }
        assertThrows(IllegalArgumentException.class, () -> MeasurementProfile.parse("STANDARD"));
    }

    /** Invalid scores are rejected; missing statistical uncertainty remains explicitly unavailable. */
    @Test
    void preservesUncertaintyWithoutInventingScores() {
        for (double score : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new BenchmarkReport.Measurement("op", "p", null, score, null, "us/op", 1, 1));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new BenchmarkReport.Measurement("op", "p", null, 1, null, "us/op", 0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BenchmarkReport.Measurement("op", "p", null, 1, null, "us/op", 1, 0));
        assertNull(new BenchmarkReport.Measurement("op", "p", null, 1, Double.NaN, "us/op", 1, 1).scoreError());
        assertEquals(0.5, new BenchmarkReport.Measurement("op", "p", null, 1, 0.5, "us/op", 1, 1).scoreError());
    }

    /** Only public typed evidence is serialized, while raw harness output is left untouched. */
    @Test
    void writesMethodAndSizesWithExplicitSmokeCaveat(@TempDir Path directory) throws Exception {
        var environment = BenchmarkEvidence.environment();
        assertEquals("1.37", environment.jmhVersion());
        assertEquals(
                Set.of("ED25519", "ML_DSA_65"),
                environment.providers().stream()
                        .map(BenchmarkReport.ProviderInfo::algorithm)
                        .collect(java.util.stream.Collectors.toSet()));
        var report = new BenchmarkReport(
                1,
                "test-only",
                "start",
                "finish",
                MeasurementProfile.SMOKE.method(),
                environment,
                BenchmarkEvidence.sizes(MeasurementProfile.SMOKE),
                EvidenceFixtures.complete(MeasurementProfile.SMOKE),
                BenchmarkEvidence.limitations(MeasurementProfile.SMOKE));
        Path raw = directory.resolve("jmh-results.json");
        Files.writeString(raw, "RAW_UNIT_TEST_MARKER");
        var writer = new BenchmarkReportWriter();
        writer.write(report, directory);
        writer.manifest(directory, "test-only", MeasurementProfile.SMOKE, "COMPLETED", "start", "finish");
        String wire = Files.readString(directory.resolve("report.json"));
        String markdown = Files.readString(directory.resolve("report.md"));
        assertEquals("RAW_UNIT_TEST_MARKER", Files.readString(raw));
        assertFalse(wire.contains("privateKey"));
        assertFalse(wire.contains("PKCS8"));
        assertTrue(markdown.contains("SMOKE ONLY"));
        assertTrue(markdown.contains("Unavailable"));
        assertTrue(markdown.contains("64 + 3309"));
        assertTrue(markdown.contains("99.9%"));
        var parsed = new ObjectMapper().readTree(wire);
        assertEquals(21, parsed.get("measurements").size());
        assertTrue(parsed.get("measurements").get(0).get("scoreError").isNull());
        assertEquals(
                "COMPLETED",
                new ObjectMapper()
                        .readTree(directory.resolve("manifest.json").toFile())
                        .get("status")
                        .textValue());
        assertThrows(
                UnsupportedOperationException.class, () -> report.measurements().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> report.transportSizes().clear());
    }
}
