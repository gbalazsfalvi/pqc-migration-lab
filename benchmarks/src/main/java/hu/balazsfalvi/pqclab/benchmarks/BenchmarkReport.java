package hu.balazsfalvi.pqclab.benchmarks;

import java.util.List;

/**
 * Contains public measurement evidence and method metadata, never runtime key material.
 *
 * @param formatVersion the evidence schema version
 * @param runId the unique local evidence identifier
 * @param startedAt the UTC run start
 * @param completedAt the UTC run completion
 * @param method the explicit harness configuration
 * @param environment the public runtime and hardware context
 * @param transportSizes the independently observed wire sizes
 * @param measurements the completed JMH observations
 * @param limitations the interpretation boundaries
 */
public record BenchmarkReport(
        int formatVersion,
        String runId,
        String startedAt,
        String completedAt,
        Method method,
        Environment environment,
        List<TransportSize> transportSizes,
        List<Measurement> measurements,
        List<String> limitations) {
    /** Snapshots public collections so a written result cannot be changed by its caller. */
    public BenchmarkReport {
        transportSizes = List.copyOf(transportSizes);
        measurements = List.copyOf(measurements);
        limitations = List.copyOf(limitations);
    }

    /**
     * Describes the full measurement method, including reduced-profile limitations.
     *
     * @param profile the named method
     * @param forks independent JVMs per case
     * @param threads measured worker threads
     * @param warmupIterations untimed warm-up iterations per fork
     * @param measurementIterations measured iterations per fork
     * @param iterationMillis duration of each iteration
     * @param mode the JMH measurement mode
     * @param unit the reported time unit
     * @param forkJvmArguments the fixed fork JVM options
     * @param payloadSizes the decoded message byte counts
     * @param expectedResults the complete expected matrix size
     * @param comparative whether the profile is intended for exploratory comparison
     */
    public record Method(
            String profile,
            int forks,
            int threads,
            int warmupIterations,
            int measurementIterations,
            int iterationMillis,
            String mode,
            String unit,
            List<String> forkJvmArguments,
            List<Integer> payloadSizes,
            int expectedResults,
            boolean comparative) {
        /** Preserves exactly the configured JVM arguments and payload sizes. */
        public Method {
            forkJvmArguments = List.copyOf(forkJvmArguments);
            payloadSizes = List.copyOf(payloadSizes);
        }
    }

    /**
     * Records implementation and machine context without account or host identifiers.
     *
     * @param javaRuntimeVersion the full JDK runtime version
     * @param javaVendor the runtime vendor
     * @param vmName the VM implementation
     * @param vmVersion the VM version
     * @param osName the operating system
     * @param osVersion the operating system version
     * @param architecture the CPU architecture
     * @param logicalProcessors the processors available to the parent JVM
     * @param processorIdentifier the public hardware description when the OS provides it
     * @param jmhVersion the actual harness version
     * @param providers the selected cryptographic implementations
     */
    public record Environment(
            String javaRuntimeVersion,
            String javaVendor,
            String vmName,
            String vmVersion,
            String osName,
            String osVersion,
            String architecture,
            int logicalProcessors,
            String processorIdentifier,
            String jmhVersion,
            List<ProviderInfo> providers) {
        /** Snapshots the provider identities independently of runtime key objects. */
        public Environment {
            providers = List.copyOf(providers);
        }
    }

    /**
     * Identifies a selected implementation rather than merely an algorithm label.
     *
     * @param algorithm the protocol algorithm
     * @param name the JCA provider name
     * @param version the provider version
     */
    public record ProviderInfo(String algorithm, String name, String version) {}

    /**
     * Reports observed public byte counts outside the timed loop.
     *
     * @param profile the algorithm combination
     * @param payloadBytes the decoded input bytes
     * @param paeBytes the authenticated DSSE frame bytes
     * @param publicKeySpkiBytes one SPKI encoding size per algorithm
     * @param signatureBytes one raw signature size per algorithm
     * @param dsseJsonBytes the complete encoded UTF-8 envelope size
     */
    public record TransportSize(
            String profile,
            int payloadBytes,
            int paeBytes,
            List<Integer> publicKeySpkiBytes,
            List<Integer> signatureBytes,
            int dsseJsonBytes) {
        /** Snapshots size observations without retaining any key object or byte encoding. */
        public TransportSize {
            publicKeySpkiBytes = List.copyOf(publicKeySpkiBytes);
            signatureBytes = List.copyOf(signatureBytes);
        }
    }

    /**
     * Records one JMH result without inventing an error estimate when insufficient samples exist.
     *
     * @param operation the measured method
     * @param profile the signature combination
     * @param payloadBytes the decoded input size, or null for key generation
     * @param score the finite average time per complete operation
     * @param scoreError the JMH 99.9% confidence half-width, or null when unavailable
     * @param unit the actual JMH unit
     * @param samples the number of measured iteration samples
     * @param forks the completed independent JVM results
     */
    public record Measurement(
            String operation,
            String profile,
            Integer payloadBytes,
            double score,
            Double scoreError,
            String unit,
            long samples,
            int forks) {
        /** Refuses invalid averages and represents unavailable uncertainty explicitly. */
        public Measurement {
            if (!Double.isFinite(score) || score < 0 || samples < 1 || forks < 1) {
                throw new IllegalArgumentException("A measurement must contain finite, completed observations.");
            }
            if (scoreError != null && !Double.isFinite(scoreError)) scoreError = null;
        }

        /**
         * Identifies a result independently of its measured score.
         *
         * @return the stable operation/profile/size matrix key
         */
        public String key() {
            return operation + "/" + profile + "/" + (payloadBytes == null ? "-" : payloadBytes);
        }
    }
}
