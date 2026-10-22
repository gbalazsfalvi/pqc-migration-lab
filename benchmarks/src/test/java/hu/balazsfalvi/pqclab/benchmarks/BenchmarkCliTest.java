package hu.balazsfalvi.pqclab.benchmarks;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.runner.RunnerException;

/** Tests orchestration and truthful failure states without treating fabricated timings as benchmark results. */
final class BenchmarkCliTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);

    /** Strict parsing rejects missing, duplicate, unexpected and invalid values before invoking JMH. */
    @Test
    void validatesArgumentsAndProvidesHelp() {
        BenchmarkExecutor forbidden = (profile, raw, log) -> {
            throw new AssertionError("No harness expected.");
        };
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        assertEquals(
                0,
                BenchmarkCli.execute(
                        new String[] {"--help"}, new PrintStream(output), new PrintStream(errors), CLOCK, forbidden));
        assertTrue(output.toString().contains("standard"));
        for (String[] invalid : new String[][] {
            {"--profile"},
            {"--profile", ""},
            {"--profile", "other"},
            {"--unknown", "x"},
            {"--profile", "smoke", "--profile", "quick"},
            {"--output", "\0"}
        }) {
            assertEquals(
                    2,
                    BenchmarkCli.execute(invalid, new PrintStream(output), new PrintStream(errors), CLOCK, forbidden));
        }
        BenchmarkCli.main(new String[] {"--help"});
    }

    /** Successful unit-test runs get unique directories and publish completion only after summary files. */
    @Test
    void writesUniqueCompleteEvidenceDirectories(@TempDir Path parent) throws Exception {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        for (int repeat = 0; repeat < 2; repeat++) {
            assertEquals(
                    0,
                    BenchmarkCli.execute(
                            new String[] {"--profile", "smoke", "--output", parent.toString()},
                            new PrintStream(out),
                            new PrintStream(err),
                            CLOCK,
                            (profile, raw, log) -> {
                                try {
                                    Files.writeString(raw, "UNIT_TEST_RAW");
                                    Files.writeString(log, "UNIT_TEST_LOG");
                                } catch (java.io.IOException failure) {
                                    throw new RunnerException("Test fixture could not be written.", failure);
                                }
                                return EvidenceFixtures.complete(profile);
                            }));
        }
        try (var children = Files.list(parent)) {
            var runs = children.toList();
            assertEquals(2, runs.size());
            for (Path run : runs) {
                assertTrue(Files.exists(run.resolve("report.md")));
                assertEquals(
                        "COMPLETED",
                        new ObjectMapper()
                                .readTree(run.resolve("manifest.json").toFile())
                                .get("status")
                                .textValue());
            }
        }
        assertTrue(out.toString().contains("SMOKE ONLY"));
        assertTrue(out.toString().contains("Completed: 21"));
        assertEquals("", err.toString());
    }

    /** Harness exceptions and incomplete matrices remain failed evidence, without leaking diagnostics. */
    @Test
    void failsClosedOnHarnessOrCompletenessFailure(@TempDir Path parent) throws Exception {
        for (boolean incomplete : List.of(false, true)) {
            Path output = parent.resolve(incomplete ? "partial" : "harness");
            var errors = new ByteArrayOutputStream();
            int result = BenchmarkCli.execute(
                    new String[] {"--profile", "smoke", "--output", output.toString()},
                    new PrintStream(new ByteArrayOutputStream()),
                    new PrintStream(errors),
                    CLOCK,
                    (profile, raw, log) -> {
                        if (incomplete) return List.of();
                        throw new RunnerException("SENSITIVE_TEST_MARKER");
                    });
            assertEquals(1, result);
            assertFalse(errors.toString().contains("SENSITIVE_TEST_MARKER"));
            try (var children = Files.list(output)) {
                Path run = children.findFirst().orElseThrow();
                assertEquals(
                        "FAILED",
                        new ObjectMapper()
                                .readTree(run.resolve("manifest.json").toFile())
                                .get("status")
                                .textValue());
                assertFalse(Files.exists(run.resolve("report.md")));
            }
        }
    }

    /** A file cannot be treated as a writable report directory. */
    @Test
    void reportsUnwritableOutput(@TempDir Path parent) throws Exception {
        Path occupied = Files.writeString(parent.resolve("occupied"), "existing");
        var errors = new ByteArrayOutputStream();
        assertEquals(
                1,
                BenchmarkCli.execute(
                        new String[] {"--output", occupied.toString()},
                        new PrintStream(new ByteArrayOutputStream()),
                        new PrintStream(errors),
                        CLOCK,
                        (profile, raw, log) -> {
                            throw new AssertionError("Output fails before measurement.");
                        }));
        assertTrue(errors.toString().contains("did not complete"));
    }
}
