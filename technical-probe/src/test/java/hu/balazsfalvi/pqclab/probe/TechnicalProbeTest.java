package hu.balazsfalvi.pqclab.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the real CLI-to-report flow, command failures, and report interpretation. */
final class TechnicalProbeTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC);

    /** Requires all real experiments and both report formats to agree on their outcomes. */
    @Test
    void writesCompleteEvidenceThroughTheCommand(@TempDir Path directory) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = TechnicalProbe.execute(
                new String[] {"--output", directory.toString()}, stream(output), stream(errors), CLOCK);
        assertEquals(0, status, errors.toString(StandardCharsets.UTF_8));
        var report = new ObjectMapper().readTree(Files.readAllBytes(directory.resolve("report.json")));
        assertTrue(report.get("successful").booleanValue());
        assertEquals(1, report.get("schemaVersion").intValue());
        assertEquals(CLOCK.instant().toString(), report.get("generatedAt").textValue());
        assertEquals(53, report.get("checks").size());
        assertEquals(53, report.get("passed").intValue());
        assertEquals(0, report.get("failed").intValue());
        assertEquals(
                53,
                output.toString(StandardCharsets.UTF_8)
                        .lines()
                        .filter(line -> line.startsWith("[PASS]"))
                        .count());
        String markdown = Files.readString(directory.resolve("report.md"));
        assertTrue(markdown.contains("**PASS**"));
        assertTrue(markdown.contains("dsse.dual.ml-dsa-65"));
        assertTrue(markdown.contains("signatureBytes: 3309"));
        String json = Files.readString(directory.resolve("report.json"));
        assertFalse(json.contains("PRIVATE KEY"));
        assertFalse(json.contains("privateKey"));
        assertTrue(errors.toString(StandardCharsets.UTF_8).isEmpty());
    }

    /** Invalid invocations fail before running cryptography or creating reports. */
    @ParameterizedTest
    @ValueSource(strings = {"--unknown", "--output", "--HELP"})
    void rejectsInvalidArguments(String argument) {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        assertEquals(
                2,
                TechnicalProbe.execute(
                        new String[] {argument}, stream(new ByteArrayOutputStream()), stream(errors), CLOCK));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Usage:"));
    }

    /** Help is independent of providers, output directories, and report generation. */
    @Test
    void printsHelpWithoutExecutingTheProbe() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertEquals(
                0,
                TechnicalProbe.execute(
                        new String[] {"--help"}, stream(output), stream(new ByteArrayOutputStream()), CLOCK));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("--output"));
    }

    /** A local output error must produce an unsuccessful command result. */
    @Test
    void reportsUnwritableDestinationWithoutLeakingInput(@TempDir Path directory) throws Exception {
        Path file = Files.writeString(directory.resolve("a-file"), "not a directory");
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        assertEquals(
                1,
                TechnicalProbe.execute(
                        new String[] {"--output", file.toString()},
                        stream(new ByteArrayOutputStream()),
                        stream(errors),
                        CLOCK));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("could not be written"));
        assertFalse(errors.toString(StandardCharsets.UTF_8).contains(file.toString()));
    }

    /** Empty or failed evidence can never be represented as a successful experiment. */
    @Test
    void distinguishesFailureAndEscapesRuntimeMetadata(@TempDir Path directory) throws Exception {
        assertFalse(new ProbeReport(CLOCK.instant(), Map.of(), List.of()).successful());
        ProbeReport failed = new ProbeReport(
                CLOCK.instant(),
                Map.of("runtime", "<unsafe>|\nnext"),
                List.of(new ProbeCheck("example", "An unmet expectation", false, Map.of("error", "Unavailable"))));
        assertEquals(1, failed.failedCount());
        assertFalse(failed.successful());
        new ReportWriter().write(failed, directory);
        String markdown = Files.readString(directory.resolve("report.md"));
        assertTrue(markdown.contains("**FAIL**"));
        assertTrue(markdown.contains("&lt;unsafe&gt;&#124; next"));
    }

    /** Gives captured command output the same explicit encoding as generated report files. */
    private static PrintStream stream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }
}
