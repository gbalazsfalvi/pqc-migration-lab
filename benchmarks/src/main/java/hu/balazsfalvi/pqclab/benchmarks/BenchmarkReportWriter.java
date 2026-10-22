package hu.balazsfalvi.pqclab.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

/** Writes a readable report beside versioned JSON and preserves JMH's original evidence files. */
public final class BenchmarkReportWriter {
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Creates a writer for public records only; runtime workload and key objects are never supplied. */
    public BenchmarkReportWriter() {}

    /**
     * Writes the summary without replacing or reformatting raw JMH output.
     *
     * @param report the complete validated public report
     * @param directory the uniquely created run directory
     * @throws IOException if an evidence file cannot be written
     */
    public void write(BenchmarkReport report, Path directory) throws IOException {
        json.writeValue(directory.resolve("report.json").toFile(), report);
        Files.writeString(directory.resolve("report.md"), markdown(report), StandardCharsets.UTF_8);
    }

    /**
     * Writes a run-status manifest so an interrupted run cannot look like a completed measurement.
     *
     * @param directory the unique run directory
     * @param runId the public evidence identifier
     * @param profile the method name
     * @param status RUNNING, COMPLETED or FAILED
     * @param startedAt the UTC start
     * @param completedAt the UTC finish, or null while running
     * @throws IOException if the manifest cannot be written
     */
    public void manifest(
            Path directory,
            String runId,
            MeasurementProfile profile,
            String status,
            String startedAt,
            String completedAt)
            throws IOException {
        var manifest = json.createObjectNode();
        manifest.put("formatVersion", 1);
        manifest.put("runId", runId);
        manifest.put("profile", profile.id());
        manifest.put("status", status);
        manifest.put("startedAt", startedAt);
        manifest.put("completedAt", completedAt);
        json.writeValue(directory.resolve("manifest.json").toFile(), manifest);
    }

    /** Renders exact operation boundaries and uncertainty alongside each measurement. */
    private String markdown(BenchmarkReport report) {
        var text = new StringBuilder("# PQC Migration Lab — benchmark evidence\n\n");
        var method = report.method();
        text.append("Run: `")
                .append(report.runId())
                .append("`  \n")
                .append("Started: ")
                .append(report.startedAt())
                .append("  \n")
                .append("Completed: ")
                .append(report.completedAt())
                .append("\n\n")
                .append("## Method\n\n")
                .append("- Profile: **")
                .append(method.profile())
                .append("**.\n")
                .append("- JMH ")
                .append(escape(report.environment().jmhVersion()))
                .append(", ")
                .append(method.mode())
                .append(", ")
                .append(method.unit())
                .append("; lower is faster.\n")
                .append("- ")
                .append(method.forks())
                .append(" independent JVM forks per case, ")
                .append(method.threads())
                .append(" worker thread; ")
                .append(method.warmupIterations())
                .append(" warm-up and ")
                .append(method.measurementIterations())
                .append(" measurement iterations per fork, ")
                .append(method.iterationMillis())
                .append(" ms each.\n")
                .append("- Fork JVM arguments: `")
                .append(String.join(" ", method.forkJvmArguments()))
                .append("`.\n")
                .append("- Complete matrix: ")
                .append(report.measurements().size())
                .append(" cases.\n\n")
                .append("## Environment\n\n")
                .append("| Field | Observed value |\n| --- | --- |\n");
        var env = report.environment();
        row(text, "Java", env.javaRuntimeVersion() + " / " + env.javaVendor());
        row(text, "VM", env.vmName() + " / " + env.vmVersion());
        row(text, "OS", env.osName() + " " + env.osVersion());
        row(text, "Architecture", env.architecture());
        row(text, "Available logical processors", String.valueOf(env.logicalProcessors()));
        row(text, "Processor identifier", env.processorIdentifier());
        for (var provider : env.providers()) {
            row(text, provider.algorithm(), provider.name() + " " + provider.version());
        }
        text.append("\n## Timings\n\n")
                .append("One row is one complete operation. The error column is JMH's 99.9% confidence half-width; ")
                .append(
                        "an unavailable estimate is shown explicitly. Key generation has no payload-size parameter.\n\n")
                .append(
                        "| Operation | Signature profile | Payload bytes | Average us/op | Error ± us/op | Samples | Forks |\n")
                .append("| --- | --- | ---: | ---: | ---: | ---: | ---: |\n");
        report.measurements().stream()
                .sorted(Comparator.comparing(BenchmarkReport.Measurement::operation)
                        .thenComparing(
                                measurement -> measurement.payloadBytes() == null ? 0 : measurement.payloadBytes())
                        .thenComparing(BenchmarkReport.Measurement::profile))
                .forEach(result -> text.append("| ")
                        .append(result.operation())
                        .append(" | ")
                        .append(result.profile())
                        .append(" | ")
                        .append(result.payloadBytes() == null ? "—" : result.payloadBytes())
                        .append(" | ")
                        .append(number(result.score()))
                        .append(" | ")
                        .append(result.scoreError() == null ? "Unavailable" : number(result.scoreError()))
                        .append(" | ")
                        .append(result.samples())
                        .append(" | ")
                        .append(result.forks())
                        .append(" |\n"));
        text.append("\n## Transport sizes\n\n")
                .append("Observed outside timing. Lists follow Ed25519, then ML-DSA-65 for DUAL.\n\n")
                .append(
                        "| Profile | Payload bytes | PAE bytes | Public SPKI bytes | Raw signature bytes | DSSE JSON bytes |\n")
                .append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
        for (var size : report.transportSizes()) {
            text.append("| ")
                    .append(size.profile())
                    .append(" | ")
                    .append(size.payloadBytes())
                    .append(" | ")
                    .append(size.paeBytes())
                    .append(" | ")
                    .append(join(size.publicKeySpkiBytes()))
                    .append(" | ")
                    .append(join(size.signatureBytes()))
                    .append(" | ")
                    .append(size.dsseJsonBytes())
                    .append(" |\n");
        }
        text.append("\n## Interpretation and limits\n\n");
        report.limitations().forEach(limit -> text.append("- ").append(limit).append('\n'));
        text.append("\nRaw evidence: [JMH JSON](jmh-results.json), [harness log](jmh-output.log), ")
                .append("[versioned summary](report.json), [run status](manifest.json).\n");
        return text.toString();
    }

    /** Writes a public metadata row after escaping Markdown structural characters. */
    private void row(StringBuilder text, String field, String value) {
        text.append("| ").append(field).append(" | ").append(escape(value)).append(" |\n");
    }

    /** Prevents runtime metadata from introducing extra Markdown columns or lines. */
    private String escape(String value) {
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ").replace("`", "'");
    }

    /** Formats finite observed values independently of the workstation's locale. */
    private String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /** Lists independently observed component sizes without implying an aggregate key format. */
    private String join(java.util.List<Integer> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(" + "));
    }
}
