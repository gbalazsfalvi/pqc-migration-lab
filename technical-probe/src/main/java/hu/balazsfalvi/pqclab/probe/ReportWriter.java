package hu.balazsfalvi.pqclab.probe;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Writes the same public evidence as readable Markdown and schema-versioned JSON. */
public final class ReportWriter {
    /** Creates a writer with no mutable state shared between runs. */
    public ReportWriter() {}

    /**
     * Writes reports into a caller-selected local directory.
     *
     * @param report the immutable completed experiment
     * @param directory the local destination
     * @throws IOException if a directory or report cannot be written
     */
    public void write(ProbeReport report, Path directory) throws IOException {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(directory, "directory");
        Files.createDirectories(directory);
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("generatedAt", report.generatedAt().toString());
        root.put("successful", report.successful());
        root.put("passed", report.passedCount());
        root.put("failed", report.failedCount());
        root.set("environment", mapper.valueToTree(report.environment()));
        root.set("checks", mapper.valueToTree(report.checks()));
        Files.writeString(
                directory.resolve("report.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("report.md"), markdown(report), StandardCharsets.UTF_8);
    }

    /** Builds a compact evidence document with one independently readable row per expectation. */
    private String markdown(ProbeReport report) {
        StringBuilder text = new StringBuilder("# PQC Migration Lab\n\n");
        text.append("Milestone 01 · Signature and envelope interoperability\n\n")
                .append("**")
                .append(report.successful() ? "PASS" : "FAIL")
                .append("** · ")
                .append(report.passedCount())
                .append(" passed · ")
                .append(report.failedCount())
                .append(" failed\n\nGenerated at ")
                .append(report.generatedAt())
                .append(".\n\n")
                .append("## Runtime\n\n| Property | Value |\n| --- | --- |\n");
        report.environment()
                .forEach((key, value) -> text.append("| ")
                        .append(escape(key))
                        .append(" | ")
                        .append(escape(value))
                        .append(" |\n"));
        text.append("\n## Evidence\n\n| Check | Expectation | Result | Evidence |\n| --- | --- | --- | --- |\n");
        for (ProbeCheck check : report.checks()) {
            text.append("| ")
                    .append(escape(check.id()))
                    .append(" | ")
                    .append(escape(check.description()))
                    .append(" | ")
                    .append(check.passed() ? "PASS" : "FAIL")
                    .append(" | ");
            String separator = "";
            for (Map.Entry<String, String> entry : check.evidence().entrySet()) {
                text.append(separator)
                        .append(escape(entry.getKey()))
                        .append(": ")
                        .append(escape(entry.getValue()));
                separator = "; ";
            }
            text.append(" |\n");
        }
        return text.append("\n## Interpretation\n\n")
                .append(
                        "This is a functional interoperability experiment, not a performance benchmark or FIPS validation. ")
                .append("All generated keys are ephemeral. Reports contain public-key fingerprints and sizes only. ")
                .append("A dual-signature envelope is checked independently with each trusted key; ")
                .append("migration policy, replay prevention, key rotation and revocation are later milestones.\n")
                .toString();
    }

    /** Keeps externally supplied runtime descriptions inside their intended Markdown table cells. */
    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("|", "&#124;")
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("`", "&#96;");
    }
}
