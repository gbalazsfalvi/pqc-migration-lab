package hu.balazsfalvi.pqclab.integration.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.balazsfalvi.pqclab.integration.app.demo.IntegrationDemo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Requires the user-facing demonstration to generate complete, agreeing HTTP evidence. */
final class DemoTest {
    /** Runs all four policies and the attack scenarios rather than substituting mocked HTTP results. */
    @Test
    void writesObservedPolicyAndAttackEvidence(@TempDir Path directory) throws Exception {
        assertTrue(new IntegrationDemo().execute(directory));
        var report = new ObjectMapper().readTree(Files.readAllBytes(directory.resolve("report.json")));
        assertTrue(report.get("successful").booleanValue());
        assertEquals(2, report.get("milestone").intValue());
        assertEquals(23, report.get("checks").size());
        assertEquals(23, report.get("passed").intValue());
        assertEquals(0, report.get("failed").intValue());
        String markdown = Files.readString(directory.resolve("report.md"));
        assertTrue(markdown.contains("attack.signature-stripping"));
        assertTrue(markdown.contains("EVENT_ID_CONFLICT"));
        assertTrue(markdown.contains("**PASS**"));
    }
}
