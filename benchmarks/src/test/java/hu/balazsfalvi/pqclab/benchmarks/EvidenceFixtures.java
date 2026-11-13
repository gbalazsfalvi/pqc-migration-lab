package hu.balazsfalvi.pqclab.benchmarks;

import java.util.ArrayList;
import java.util.List;

/** Supplies explicitly synthetic unit-test observations, never published as actual timing evidence. */
final class EvidenceFixtures {
    static final List<String> OPERATIONS = List.of(
            "signMessage", "verifyMessage", "signEnvelope", "verifyEnvelope", "encodeEnvelope", "decodeEnvelope");

    /** Prevents instantiation of a test-only fixture factory. */
    private EvidenceFixtures() {}

    /** Builds a complete fake score matrix solely for report and completeness-rule tests. */
    static List<BenchmarkReport.Measurement> complete(MeasurementProfile profile) {
        var results = new ArrayList<BenchmarkReport.Measurement>();
        for (SignatureProfile signature : SignatureProfile.values()) {
            results.add(measurement("generateKeyPairs", signature, null, profile));
            for (int size : profile.payloadSizes()) {
                for (String operation : OPERATIONS) results.add(measurement(operation, signature, size, profile));
            }
        }
        return List.copyOf(results);
    }

    /** Preserves method metadata while using an unmistakable test-only constant score. */
    private static BenchmarkReport.Measurement measurement(
            String operation, SignatureProfile signature, Integer size, MeasurementProfile profile) {
        return new BenchmarkReport.Measurement(
                operation,
                signature.name(),
                size,
                123.456,
                Double.NaN,
                "us/op",
                (long) profile.method().forks() * profile.method().measurementIterations(),
                profile.method().forks());
    }
}
