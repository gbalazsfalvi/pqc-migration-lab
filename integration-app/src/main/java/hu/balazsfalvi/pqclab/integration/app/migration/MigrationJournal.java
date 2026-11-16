package hu.balazsfalvi.pqclab.integration.app.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Accumulates ordered assertions and exact public artifacts, without inspecting any runtime directory. */
final class MigrationJournal {
    private final Path directory;
    private final List<MigrationReport.Exchange> exchanges = new ArrayList<>();
    private final List<MigrationReport.Check> checks = new ArrayList<>();
    private final List<MigrationReport.PolicyPhase> policyPhases = new ArrayList<>();

    /** Binds the journal to one newly created public run directory. */
    MigrationJournal(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectory(directory.resolve("http"));
    }

    /** Records one completed or interrupted HTTP attempt and only explicitly validated response bytes. */
    int exchange(
            int phase,
            String scenario,
            String endpoint,
            String method,
            String path,
            int expectedStatus,
            Integer actualStatus,
            byte[] request,
            byte[] response,
            boolean responseValidated,
            String failureCode)
            throws IOException {
        int sequence = exchanges.size() + 1;
        String stem = "http/%03d-%s".formatted(sequence, scenario);
        String requestFile = request.length == 0 ? null : stem + ".request.json";
        String responseFile = responseValidated ? stem + ".response.json" : null;
        if (requestFile != null) write(requestFile, request);
        if (responseFile != null) write(responseFile, response);
        exchanges.add(new MigrationReport.Exchange(
                sequence,
                phase,
                scenario,
                endpoint,
                method,
                path,
                Instant.now().toString(),
                expectedStatus,
                actualStatus,
                requestFile,
                request.length == 0 ? null : sha256(request),
                responseFile,
                response == null ? null : sha256(response),
                failureCode));
        return sequence;
    }

    /** Stops dependent actions as soon as a status or semantic expectation actually fails. */
    void expect(String id, String description, Object expected, Object observed, int... references) {
        boolean passed = Objects.equals(expected, observed);
        checks.add(new MigrationReport.Check(
                id,
                description,
                render(expected),
                render(observed),
                passed,
                Arrays.stream(references).boxed().toList()));
        if (!passed) throw new ScenarioFailure("EXPECTATION_FAILED");
    }

    /** Renders equal unordered values consistently so a passing assertion remains readable in exported evidence. */
    private String render(Object value) {
        if (value instanceof Set<?> values) {
            return values.stream().map(String::valueOf).sorted().toList().toString();
        }
        return String.valueOf(value);
    }

    /** @return an immutable snapshot of real transport attempts */
    List<MigrationReport.Exchange> exchanges() {
        return List.copyOf(exchanges);
    }

    /** @return an immutable snapshot of observed expectations */
    List<MigrationReport.Check> checks() {
        return List.copyOf(checks);
    }

    /** Retains a phase summary only after every delivery, historical read and invariant in that phase passed. */
    void completePolicyPhase(MigrationReport.PolicyPhase phase) {
        if (phase.phase() != policyPhases.size() + 1)
            throw new IllegalArgumentException("Record completed phases in order.");
        policyPhases.add(phase);
    }

    /** @return immutable completed policy phases; unfinished phases remain visible only in the actual exchange timeline */
    List<MigrationReport.PolicyPhase> policyPhases() {
        return List.copyOf(policyPhases);
    }

    /** Writes a single new public artifact; an earlier run is never overwritten. */
    private void write(String relative, byte[] bytes) throws IOException {
        Files.write(directory.resolve(relative), bytes, StandardOpenOption.CREATE_NEW);
    }

    /** Computes a standard digest for exact public byte identity, not as a trust decision. */
    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("The required SHA-256 implementation is unavailable.", failure);
        }
    }
}
