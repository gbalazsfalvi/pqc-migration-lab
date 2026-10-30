package hu.balazsfalvi.pqclab.integration.app.migration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the actual user-facing story, exact artifacts, cleanup and independent receipt/trust evidence. */
final class MigrationDemoTest {
    @TempDir
    Path root;

    /** Three real Boot reconstructions preserve trust and receipts while report references identify exact bytes. */
    @Test
    void recordsTheWholeRealMigrationAndRemovesPrivateRuntimeMaterial() throws Exception {
        var workspacePath = new AtomicReference<Path>();
        var demo = new MigrationDemo((workspace, journal) -> {
            workspacePath.set(workspace.secretFile().getParent());
            new MigrationScenario(journal).run(workspace);
        });
        var result = demo.execute(root);
        assertTrue(result.report().successful(), result.report().failureCode());
        assertTrue(result.report().scenarioCompleted());
        assertEquals(MigrationReport.Outcome.PASS, result.report().outcome());
        assertEquals(28, result.report().exchanges().size());
        assertTrue(result.report().checks().stream().allMatch(MigrationReport.Check::passed));
        for (var check : result.report().checks()) {
            assertEquals(check.expected(), check.observed(), check.id());
        }
        assertFalse(Files.exists(workspacePath.get()));
        assertFalse(result.directory().startsWith(workspacePath.get()));
        assertEquals(
                List.of(1, 2, 3),
                result.report().exchanges().stream()
                        .map(MigrationReport.Exchange::phase)
                        .distinct()
                        .toList());

        var mapper = new ObjectMapper();
        var structured = mapper.readTree(Files.readAllBytes(result.directory().resolve("report.json")));
        assertTrue(structured.required("successful").booleanValue());
        assertEquals("05A", structured.required("milestone").textValue());
        assertEquals(
                result.report().checks().size(), structured.required("passed").intValue());
        assertEquals(0, structured.required("failed").intValue());

        // Independently inspect referenced receiver responses, rather than trusting the report's PASS flags.
        var first = response(result, "original-delivery");
        var overlap = response(result, "overlap-replay");
        var historical = response(result, "restart-historical-receipt");
        for (String field : List.of("receiptId", "eventId", "acceptedAt", "acceptedPolicy", "acceptedEvidence")) {
            assertEquals(first.required(field), overlap.required(field));
            assertEquals(first.required(field), historical.required(field));
        }
        assertEquals("STORED", historical.required("status").textValue());
        assertEquals(
                "POLICY_REJECTED",
                response(result, "revocation-restart-replay").required("code").textValue());
        var newReceipt = response(result, "current-delivery");
        assertNotEquals(first.required("receiptId"), newReceipt.required("receiptId"));
        assertEquals(
                newReceipt.required("receiptId"),
                response(result, "restart-current-replay").required("receiptId"));
        var afterRevoke = response(result, "revoked-registry");
        var afterRestart = response(result, "revocation-restart-registry");
        assertEquals(3, afterRestart.required("revision").longValue());
        assertEquals(afterRevoke.required("generations"), afterRestart.required("generations"));

        var originalDigests = result.report().exchanges().stream()
                .filter(exchange -> List.of(
                                "original-delivery",
                                "original-replay",
                                "overlap-replay",
                                "rotation-restart-replay",
                                "revoked-replay",
                                "revocation-restart-replay")
                        .contains(exchange.scenario()))
                .map(MigrationReport.Exchange::requestSha256)
                .toList();
        assertEquals(6, originalDigests.size());
        assertEquals(1, new HashSet<>(originalDigests).size());

        List<String> manifest = Files.readAllLines(result.directory().resolve("SHA256SUMS"));
        for (String line : manifest) {
            String[] entry = line.split("  ", 2);
            Path artifact = result.directory().resolve(entry[1]).normalize();
            assertTrue(artifact.startsWith(result.directory()));
            assertEquals(entry[0], MigrationJournal.sha256(Files.readAllBytes(artifact)));
        }
        assertEquals(
                2
                        + result.report().exchanges().size()
                        + result.report().exchanges().stream()
                                .filter(exchange -> exchange.requestFile() != null)
                                .count(),
                manifest.size());
        for (var exchange : result.report().exchanges()) {
            assertNotNull(exchange.responseFile());
            assertEquals(
                    exchange.responseSha256(),
                    MigrationJournal.sha256(
                            Files.readAllBytes(result.directory().resolve(exchange.responseFile()))));
            if (exchange.requestFile() != null) {
                assertEquals(
                        exchange.requestSha256(),
                        MigrationJournal.sha256(
                                Files.readAllBytes(result.directory().resolve(exchange.requestFile()))));
            }
        }
        String markdown = Files.readString(result.directory().resolve("report.md"));
        assertTrue(markdown.contains("**PASS**"));
        assertTrue(markdown.contains("91 passed"));
        assertTrue(markdown.contains("BOTH_REQUIRED"));
        assertTrue(markdown.contains("revocation-restart-replay"));
        for (String line : manifest) {
            String value = Files.readString(result.directory().resolve(line.split("  ", 2)[1]), StandardCharsets.UTF_8);
            for (String forbidden : List.of(
                    "pkcs8",
                    "privateKey",
                    "wrappingKey",
                    workspacePath.get().toString(),
                    System.getProperty("user.home"),
                    root.toString())) {
                assertFalse(value.contains(forbidden), forbidden);
            }
        }
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.report().checks().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.report().exchanges().clear());
    }

    /** Strict CLI parsing prevents unnoticed policy, receiver or runtime-directory overrides. */
    @Test
    void supportsHelpAndRejectsUnknownOrAmbiguousArguments() throws Exception {
        MigrationDemo.main(new String[] {"--help"});
        for (String[] arguments : List.of(
                new String[] {"--receiver=https://example.com"},
                new String[] {"--policy=CLASSICAL_ONLY"},
                new String[] {" "},
                new String[] {"one", "two"})) {
            assertThrows(IllegalArgumentException.class, () -> MigrationDemo.main(arguments));
        }
        Path file = Files.writeString(root.resolve("not-a-directory"), "existing");
        assertThrows(java.io.IOException.class, () -> new MigrationDemo().execute(file));
        assertEquals("existing", Files.readString(file));
    }

    /** Reads exact archived bytes from the actual scenario result. */
    private JsonNode response(MigrationDemo.Result result, String scenario) throws Exception {
        var exchange = result.report().exchanges().stream()
                .filter(item -> scenario.equals(item.scenario()))
                .findFirst()
                .orElseThrow();
        return new ObjectMapper().readTree(Files.readAllBytes(result.directory().resolve(exchange.responseFile())));
    }
}
