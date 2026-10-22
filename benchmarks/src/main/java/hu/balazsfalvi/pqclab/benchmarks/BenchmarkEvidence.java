package hu.balazsfalvi.pqclab.benchmarks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openjdk.jmh.util.Version;

/** Collects public setup evidence and refuses incomplete, duplicated or incorrectly configured results. */
public final class BenchmarkEvidence {
    private static final List<String> OPERATIONS = List.of(
            "signMessage", "verifyMessage", "signEnvelope", "verifyEnvelope", "encodeEnvelope", "decodeEnvelope");

    /** Prevents construction of a stateless evidence utility. */
    private BenchmarkEvidence() {}

    /**
     * Collects sizes from the same implementations and input matrix used by JMH.
     *
     * @param profile the selected measurement matrix
     * @return public byte-size observations, outside the timing loop
     */
    public static List<BenchmarkReport.TransportSize> sizes(MeasurementProfile profile) {
        var sizes = new ArrayList<BenchmarkReport.TransportSize>();
        for (SignatureProfile signature : SignatureProfile.values()) {
            for (int payloadBytes : profile.payloadSizes()) {
                sizes.add(new BenchmarkWorkload(signature, payloadBytes).transportSize());
            }
        }
        return List.copyOf(sizes);
    }

    /**
     * Captures runtime metadata without serializing a key, account, host name or full environment.
     *
     * @return public runtime and selected provider information
     */
    public static BenchmarkReport.Environment environment() {
        var providers = new BenchmarkWorkload(SignatureProfile.DUAL, 256).providers();
        String cpu = System.getenv("PROCESSOR_IDENTIFIER");
        return new BenchmarkReport.Environment(
                System.getProperty("java.runtime.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.vm.name"),
                System.getProperty("java.vm.version"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                cpu == null || cpu.isBlank() ? "Not exposed by this operating system" : cpu,
                Version.getPlainVersion(),
                providers);
    }

    /**
     * Requires every expected result and matching method settings before publishing a complete report.
     *
     * @param profile the selected method
     * @param measurements the actual harness observations
     * @throws IllegalStateException for any missing, duplicate or incorrectly configured result
     */
    public static void requireComplete(MeasurementProfile profile, List<BenchmarkReport.Measurement> measurements) {
        Set<String> expected = new HashSet<>();
        for (SignatureProfile signature : SignatureProfile.values()) {
            expected.add("generateKeyPairs/" + signature.name() + "/-");
            for (int size : profile.payloadSizes()) {
                for (String operation : OPERATIONS) {
                    expected.add(operation + "/" + signature.name() + "/" + size);
                }
            }
        }
        Set<String> actual = new HashSet<>();
        for (BenchmarkReport.Measurement measurement : measurements) {
            if (!actual.add(measurement.key())
                    || measurement.forks() != profile.method().forks()
                    || measurement.samples()
                            != (long) profile.method().forks()
                                    * profile.method().measurementIterations()
                    || !"us/op".equals(measurement.unit())) {
                throw new IllegalStateException(
                        "The harness returned duplicate or incorrectly configured observations.");
            }
        }
        if (!actual.equals(expected)) {
            throw new IllegalStateException("The harness did not return the complete expected matrix.");
        }
    }

    /**
     * States the interpretation limits beside the numbers, including the smoke-profile distinction.
     *
     * @param profile the completed method
     * @return immutable explanatory limitations
     */
    public static List<String> limitations(MeasurementProfile profile) {
        return List.of(
                profile == MeasurementProfile.SMOKE
                        ? "SMOKE ONLY: harness validation; timings must not be used for comparative performance claims."
                        : "Local exploratory measurements on a general-purpose workstation, not a controlled production capacity study.",
                "Valid synthetic opaque byte payloads only; no HTTP, TLS, JDBC, business-claim validation or replay processing is timed.",
                "One operation in DUAL executes both algorithms sequentially; it is not a per-signature average or a standardized hybrid signature scheme.",
                "The unchanged production adapters create fresh JCA primitives for each operation; key generation includes generator construction and secure randomness.",
                "Signing and verification reuse trial keys; input signing, key generation and provider selection occur outside verification timing.",
                "DSSE operations include framing/base64/copying; JSON encode/decode are measured separately and exclude cryptographic work.",
                "JMH score errors are 99.9% confidence half-widths over measured iteration samples; null means unavailable, not zero uncertainty.",
                "Background load, CPU scheduling, power policy, JIT and thermal state can affect results; rerun on the intended deployment hardware.",
                "Transport sizes are direct byte counts collected outside timing and are not inferred from latency.");
    }
}
