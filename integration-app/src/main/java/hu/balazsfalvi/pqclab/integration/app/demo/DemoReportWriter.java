package hu.balazsfalvi.pqclab.integration.app.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Writes a compact local integration report containing public synthetic observations only. */
public final class DemoReportWriter {
    /** Creates a stateless evidence writer. */
    public DemoReportWriter() {}

    /**
     * Produces the same observed checks in JSON and readable Markdown.
     *
     * @param directory the ignored local output directory
     * @param checks the complete observed expectations
     * @throws IOException if evidence cannot be written
     */
    public void write(Path directory, List<DemoCheck> checks) throws IOException {
        Files.createDirectories(directory);
        ObjectMapper mapper = new ObjectMapper();
        long passed = checks.stream().filter(DemoCheck::passed).count();
        var root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("milestone", 2);
        root.put("generatedAt", Instant.now().toString());
        root.put("successful", !checks.isEmpty() && passed == checks.size());
        root.put("passed", passed);
        root.put("failed", checks.size() - passed);
        root.put("transport", "Two loopback HTTP servers in isolated Spring Boot contexts, within one local JVM");
        root.put("javaVersion", System.getProperty("java.runtime.version"));
        root.set("checks", mapper.valueToTree(checks));
        Files.writeString(
                directory.resolve("report.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
                StandardCharsets.UTF_8);
        StringBuilder text = new StringBuilder("# PQC Migration Lab · Integration evidence\n\n");
        text.append("**")
                .append(root.get("successful").booleanValue() ? "PASS" : "FAIL")
                .append("** · ")
                .append(passed)
                .append(" passed · ")
                .append(checks.size() - passed)
                .append(" failed\n\n")
                .append(
                        "Each row records a real publisher or receiver HTTP request. Receiver policy is set at startup.\n\n")
                .append("| Scenario | Receiver policy | Expected HTTP | Observed HTTP | Result | Observation |\n")
                .append("| --- | --- | ---: | ---: | --- | --- |\n");
        for (DemoCheck check : checks) {
            text.append("| ")
                    .append(check.scenario())
                    .append(" | ")
                    .append(check.policy())
                    .append(" | ")
                    .append(check.expectedStatus())
                    .append(" | ")
                    .append(check.actualStatus())
                    .append(" | ")
                    .append(check.passed() ? "PASS" : "FAIL")
                    .append(" | ")
                    .append(check.observation())
                    .append(" |\n");
        }
        text.append("\nThe ledger is a file-backed H2 database migrated by Flyway. ")
                .append(
                        "Exact authenticated retries reuse their first receipt; conflicting IDs and stale events are rejected. ")
                .append(
                        "Private keys remain in memory. Restart, concurrency and policy-upgrade cases are also covered by automated tests.\n");
        Files.writeString(directory.resolve("report.md"), text, StandardCharsets.UTF_8);
    }
}
