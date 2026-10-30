package hu.balazsfalvi.pqclab.integration.app.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.TreeSet;

/** Produces English JSON/Markdown views and a digest manifest over explicitly referenced public artifacts only. */
final class MigrationReportWriter {
    /** Writes one new evidence bundle; report roots and earlier runs are never scanned or overwritten. */
    void write(Path directory, MigrationReport report) throws IOException {
        var mapper = new ObjectMapper();
        var root = mapper.valueToTree(report);
        var object = (com.fasterxml.jackson.databind.node.ObjectNode) root;
        object.put("successful", report.successful());
        object.put(
                "passed",
                report.checks().stream().filter(MigrationReport.Check::passed).count());
        object.put(
                "failed",
                report.checks().stream().filter(check -> !check.passed()).count());
        Files.writeString(
                directory.resolve("report.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        Files.writeString(
                directory.resolve("report.md"),
                markdown(report),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);

        var artifacts = new TreeSet<String>();
        artifacts.add("report.json");
        artifacts.add("report.md");
        for (var exchange : report.exchanges()) {
            if (exchange.requestFile() != null) artifacts.add(exchange.requestFile());
            if (exchange.responseFile() != null) artifacts.add(exchange.responseFile());
        }
        var manifest = new StringBuilder();
        for (String relative : artifacts) {
            manifest.append(MigrationJournal.sha256(Files.readAllBytes(directory.resolve(relative))))
                    .append("  ")
                    .append(relative)
                    .append('\n');
        }
        // A missing manifest means evidence generation did not finish; it must never be presented as a valid bundle.
        Files.writeString(
                directory.resolve("SHA256SUMS"), manifest, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    /** Renders the scenario, actual transport timeline and assertions from the same immutable report. */
    private String markdown(MigrationReport report) {
        long passed =
                report.checks().stream().filter(MigrationReport.Check::passed).count();
        var text = new StringBuilder("# PQC Migration Lab · Migration demonstration\n\n");
        text.append("**")
                .append(report.outcome())
                .append("** · ")
                .append(passed)
                .append(" passed · ")
                .append(report.checks().size() - passed)
                .append(" failed · ")
                .append(report.exchanges().size())
                .append(" HTTP attempts\n\n")
                .append("- Run: ")
                .append(report.runId())
                .append('\n')
                .append("- Started: ")
                .append(report.startedAt())
                .append('\n')
                .append("- Finished: ")
                .append(report.finishedAt())
                .append('\n')
                .append("- Scenario and workspace cleanup completed: ")
                .append(report.scenarioCompleted())
                .append('\n')
                .append("- Failure category: ")
                .append(report.failureCode() == null ? "none" : report.failureCode())
                .append("\n\n")
                .append(
                        "A fresh synthetic integration accepts a dual-signed event, reuses its receipt, rotates both keys, ")
                .append(
                        "revokes the old generation and accepts a new event. Two full runtime reconstructions test durable ")
                .append(
                        "trust and receipts. The live receiver stays **BOTH_REQUIRED** throughout; four-policy comparisons ")
                .append("are read-only observations, not policy changes.\n\n")
                .append("## Runtime and method\n\n")
                .append("- Java: ")
                .append(escape(report.environment().javaRuntimeVersion()))
                .append(" / ")
                .append(escape(report.environment().javaVendor()))
                .append('\n')
                .append("- Platform: ")
                .append(escape(report.environment().operatingSystem()))
                .append(" / ")
                .append(escape(report.environment().architecture()))
                .append('\n')
                .append("- Transport: ")
                .append(report.environment().transport())
                .append('\n')
                .append("- Protocol: ")
                .append(report.environment().protocol())
                .append("\n\n")
                .append("Every HTTP row is an actual bounded request. Expected statuses and semantic requirements are ")
                .append(
                        "specified by the scenario before comparing the response. UUIDs, keys, timestamps and fingerprints ")
                .append(
                        "are fresh on each run; outcomes and invariants are reproducible. No artificial clock or ten-minute ")
                .append("sleep is used. Exact overlap expiry remains covered by the lifecycle tests.\n\n")
                .append("## Observed HTTP timeline\n\n")
                .append("| # | Phase | Scenario | Endpoint | Method and route | Expected | Observed |\n")
                .append("| ---: | ---: | --- | --- | --- | ---: | ---: |\n");
        for (var exchange : report.exchanges()) {
            text.append("| ")
                    .append(exchange.sequence())
                    .append(" | ")
                    .append(exchange.phase())
                    .append(" | ")
                    .append(exchange.scenario())
                    .append(" | ")
                    .append(exchange.endpoint())
                    .append(" | ")
                    .append(exchange.method())
                    .append(' ')
                    .append(escape(exchange.path()))
                    .append(" | ")
                    .append(exchange.expectedStatus())
                    .append(" | ")
                    .append(exchange.actualStatus() == null ? "NO RESPONSE" : exchange.actualStatus())
                    .append(" |\n");
        }
        text.append("\n## Assertions\n\n")
                .append(
                        "Large values are abbreviated here; report.json preserves complete expected and observed values.\n\n")
                .append("| Assertion | Expected | Observed | Result | HTTP references |\n")
                .append("| --- | --- | --- | --- | --- |\n");
        for (var check : report.checks()) {
            text.append("| ")
                    .append(check.id())
                    .append(" · ")
                    .append(escape(check.description()))
                    .append(" | ")
                    .append(compact(check.expected()))
                    .append(" | ")
                    .append(compact(check.observed()))
                    .append(" | ")
                    .append(check.passed() ? "PASS" : "FAIL")
                    .append(" | ")
                    .append(check.exchanges())
                    .append(" |\n");
        }
        text.append("\n## Inspect the evidence\n\n")
                .append("[Structured report](report.json) · [Digest manifest](SHA256SUMS)\n\n")
                .append("The structured timeline links exact request and validated public response bodies in http/. ")
                .append(
                        "Repeated original deliveries use identical request bytes and therefore identical SHA-256 digests. ")
                .append(
                        "Receipt comparisons include every original acceptance field, not just an HTTP status or receipt ID. ")
                .append(
                        "The manifest covers both reports and every referenced HTTP artifact. Verify it before reviewing a copied bundle.\n\n")
                .append("## Boundaries\n\n")
                .append(
                        "- Public synthetic data only. Runtime databases, encrypted vaults, wrapping secrets, headers, usernames ")
                .append(
                        "and absolute runtime paths are excluded. Temporary runtime material is removed after all servers stop.\n")
                .append(
                        "- This is a local, single-JVM laboratory. It does not demonstrate post-quantum TLS, production operator ")
                .append(
                        "authentication, KMS isolation, multi-process trust distribution or filesystem anti-rollback.\n")
                .append(
                        "- These are functional observations, not latency benchmarks, security certification or a proof of ")
                .append(
                        "cryptographic implementation correctness. Separate JMH evidence has its own measurement method.\n")
                .append(
                        "- SHA-256 checksums identify bytes and detect changes relative to this manifest. The manifest is unsigned ")
                .append(
                        "and does not establish an independent source of trust or protect against deliberate replacement of the whole bundle.\n")
                .append("- A failed or incomplete run stops dependent actions and retains only actual observations. ")
                .append("A missing SHA256SUMS file means bundle generation did not finish.\n");
        return text.toString();
    }

    /** Keeps long registry and receipt comparisons readable without altering the structured evidence. */
    private String compact(String value) {
        return escape(value.length() <= 140 ? value : value.substring(0, 140) + "… (see JSON)");
    }

    /** Prevents public runtime text from breaking Markdown tables or being interpreted as HTML. */
    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("|", "&#124;")
                .replace("\r", " ")
                .replace("\n", " ")
                .replace(Character.toString(96), "&#96;");
    }
}
