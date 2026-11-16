package hu.balazsfalvi.pqclab.integration.app.migration;

import static hu.balazsfalvi.pqclab.integration.core.MigrationPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves actual policy transitions, immutable historical evidence and honest shareable rehearsal reports. */
final class PolicyRehearsalTest {
    @TempDir
    Path root;

    /** The same publisher, trust and ledger serve three signature profiles across four real receiver configurations. */
    @Test
    void rehearsesAllFourLivePoliciesAndPreservesOriginalReceipts() throws Exception {
        var result = PolicyMigrationDemo.execute(root, false);
        assertTrue(
                result.report().successful(),
                () -> result.report().failureCode() + result.report().checks());
        assertEquals("06B", result.report().milestone());
        assertEquals(2, result.report().schemaVersion());
        assertEquals(
                List.of("CLASSICAL_ONLY", "SHADOW_PQC", "BOTH_REQUIRED", "PQC_REQUIRED"),
                result.report().policyPhases().stream()
                        .map(MigrationReport.PolicyPhase::policy)
                        .toList());
        assertEquals(
                List.of(List.of(201, 201, 403), List.of(200, 200, 403), List.of(403, 200, 403), List.of(403, 200, 201)),
                result.report().policyPhases().stream()
                        .map(phase -> phase.deliveries().stream()
                                .map(MigrationReport.PolicyDelivery::actualStatus)
                                .toList())
                        .toList());
        for (String profile : List.of("classical", "dual", "pqc")) {
            var deliveries = result.report().exchanges().stream()
                    .filter(exchange -> exchange.scenario().endsWith("-" + profile + "-delivery"))
                    .toList();
            assertEquals(4, deliveries.size());
            assertEquals(
                    1,
                    new HashSet<>(deliveries.stream()
                                    .map(MigrationReport.Exchange::requestSha256)
                                    .toList())
                            .size());
        }
        var mapper = new ObjectMapper();
        var first = response(result, "phase-1-classical-delivery");
        var historical = response(result, "phase-4-classical-history");
        for (String field : List.of("receiptId", "eventId", "acceptedAt", "acceptedPolicy", "acceptedEvidence")) {
            assertEquals(first.required(field), historical.required(field), field);
        }
        assertEquals("CLASSICAL_ONLY", historical.required("acceptedPolicy").textValue());
        assertEquals("PQC_REQUIRED", historical.required("enforcedPolicy").textValue());
        assertEquals(
                "POLICY_REJECTED",
                response(result, "phase-4-classical-delivery").required("code").textValue());
        var structured = mapper.readTree(Files.readAllBytes(result.directory().resolve("report.json")));
        assertEquals(4, structured.required("policyPhases").size());
        assertTrue(
                Files.readString(result.directory().resolve("report.html")).contains("Four phases. One integration."));
        String markdown = Files.readString(result.directory().resolve("report.md"));
        assertTrue(markdown.contains("## Observed policy rollout"));
        assertFalse(markdown.contains("live receiver stays **BOTH_REQUIRED** throughout"));
        for (String line : Files.readAllLines(result.directory().resolve("SHA256SUMS"))) {
            String[] item = line.split("  ", 2);
            Path artifact = result.directory().resolve(item[1]).normalize();
            assertTrue(artifact.startsWith(result.directory()));
            byte[] bytes = Files.readAllBytes(artifact);
            assertEquals(item[0], MigrationJournal.sha256(bytes));
            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            for (String secret : List.of(
                    "account.key",
                    "pkcs8",
                    "privateKey",
                    "wrappingKey",
                    "jvm-",
                    root.toString(),
                    System.getProperty("user.home"))) {
                assertFalse(content.contains(secret), secret);
            }
        }
    }

    /** Operator control rejects a reversed, skipped or repeated phase instead of silently weakening the trial. */
    @Test
    void requiresExactlyOneReviewedForwardStep() {
        assertDoesNotThrow(() -> PolicyProgression.requireNext(CLASSICAL_ONLY, SHADOW_PQC));
        assertDoesNotThrow(() -> PolicyProgression.requireNext(SHADOW_PQC, BOTH_REQUIRED));
        assertDoesNotThrow(() -> PolicyProgression.requireNext(BOTH_REQUIRED, PQC_REQUIRED));
        for (var current : PolicyProgression.PHASES) {
            for (var next : PolicyProgression.PHASES) {
                if (next.ordinal() != current.ordinal() + 1) {
                    assertThrows(IllegalArgumentException.class, () -> PolicyProgression.requireNext(current, next));
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> PolicyProgression.requireNext(null, CLASSICAL_ONLY));
        assertThrows(IllegalArgumentException.class, () -> PolicyProgression.requireNext(CLASSICAL_ONLY, null));
    }

    /** An interrupted rehearsal has no invented phase rows, success badge or leaked native diagnostic text. */
    @Test
    void exportsIncompleteEvidenceAndRejectsAmbiguousCliOptions() throws Exception {
        var result = new MigrationDemo(
                        (workspace, journal) -> {
                            throw new IOException("DO_NOT_EXPORT_NATIVE_PATH");
                        },
                        "06B",
                        "Owned native test boundary")
                .execute(root);
        assertEquals(MigrationReport.Outcome.INCOMPLETE, result.report().outcome());
        assertTrue(result.report().policyPhases().isEmpty());
        String html = Files.readString(result.directory().resolve("report.html"));
        assertTrue(html.contains("INCOMPLETE"));
        assertFalse(html.contains("DO_NOT_EXPORT_NATIVE_PATH"));
        assertFalse(html.contains("<td>201</td>"));
        assertDoesNotThrow(() -> PolicyMigrationDemo.main(new String[] {"--help"}));
        for (String[] options : List.of(
                new String[] {"--remote"},
                new String[] {"--compose", "--compose"},
                new String[] {"one", "two"},
                new String[] {" "})) {
            assertThrows(IllegalArgumentException.class, () -> PolicyMigrationDemo.main(options));
        }
    }

    /** Reads the actual validated response artifact rather than inferring success from report counters. */
    private com.fasterxml.jackson.databind.JsonNode response(MigrationDemo.Result result, String scenario)
            throws IOException {
        var exchange = result.report().exchanges().stream()
                .filter(item -> scenario.equals(item.scenario()))
                .findFirst()
                .orElseThrow();
        return new ObjectMapper().readTree(Files.readAllBytes(result.directory().resolve(exchange.responseFile())));
    }

    /** Passing a preliminary check is not enough to claim that all four policy deployments completed. */
    @Test
    void requiresEveryCompletedPhaseAndEscapesHtmlMetadata() throws Exception {
        var result = new MigrationDemo(
                        (workspace, journal) -> journal.expect(
                                "preliminary", "Only a preliminary execution-boundary observation.", true, true),
                        "06B",
                        "Local <svg onload='unsafe'>")
                .execute(root);
        assertFalse(result.report().successful());
        assertEquals(MigrationReport.Outcome.INCOMPLETE, result.report().outcome());
        assertEquals("PHASES_INCOMPLETE", result.report().failureCode());
        String html = Files.readString(result.directory().resolve("report.html"));
        assertFalse(html.contains("<svg"));
        assertTrue(html.contains("&lt;svg"));
        Path journalRoot = Files.createDirectory(root.resolve("ordered-journal"));
        var journal = new MigrationJournal(journalRoot);
        assertThrows(
                IllegalArgumentException.class,
                () -> journal.completePolicyPhase(new MigrationReport.PolicyPhase(2, "SHADOW_PQC", 1, List.of())));
    }
}
