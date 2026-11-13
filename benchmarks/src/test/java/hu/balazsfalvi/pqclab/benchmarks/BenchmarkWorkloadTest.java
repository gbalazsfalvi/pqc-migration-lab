package hu.balazsfalvi.pqclab.benchmarks;

import static org.junit.jupiter.api.Assertions.*;

import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests functional benchmark boundaries, never machine-dependent performance thresholds. */
final class BenchmarkWorkloadTest {
    /** Every size/profile pair must use real signatures and produce accurate public byte observations. */
    @ParameterizedTest
    @CsvSource({
        "ED25519,256",
        "ML_DSA_65,256",
        "DUAL,256",
        "ED25519,4096",
        "ML_DSA_65,4096",
        "DUAL,4096",
        "ED25519,65536",
        "ML_DSA_65,65536",
        "DUAL,65536"
    })
    void preparesRealValidInputsAndMeasuresExactWireSizes(SignatureProfile profile, int size) {
        var workload = new BenchmarkWorkload(profile, size);
        var signatures = workload.signMessage();
        assertEquals(profile.algorithms().size(), signatures.size());
        assertTrue(workload.verifyMessage());
        assertTrue(workload.verifyEnvelope());
        var envelope = workload.decodeEnvelope();
        assertEquals(workload.signEnvelope().payload(), envelope.payload());
        assertEquals(profile.algorithms().size(), envelope.signatures().size());
        var observed = workload.transportSize();
        assertEquals(profile.name(), observed.profile());
        assertEquals(size, observed.payloadBytes());
        assertEquals(size, envelope.payloadBytes().length);
        assertEquals(workload.encodeEnvelope().length, observed.dsseJsonBytes());
        assertEquals(signatures.stream().map(value -> value.length).toList(), observed.signatureBytes());
        assertEquals(profile.algorithms().size(), observed.publicKeySpkiBytes().size());
        assertTrue(observed.publicKeySpkiBytes().stream().allMatch(bytes -> bytes > 0));
        assertTrue(observed.paeBytes() > size);
        assertEquals(profile.algorithms().size(), workload.providers().size());
    }

    /** Handwritten JMH entry points return consumed results and retain thread-local setup semantics. */
    @ParameterizedTest
    @EnumSource(SignatureProfile.class)
    void benchmarkMethodsExerciseProductionAdapters(SignatureProfile profile) {
        String[] before = Arrays.stream(Security.getProviders())
                .map(provider -> provider.getName())
                .toArray(String[]::new);
        var generation = new KeyGenerationBenchmark();
        generation.profile = profile.name();
        generation.setup();
        assertEquals(profile.algorithms().size(), generation.generateKeyPairs().size());
        var integration = new IntegrationBenchmark();
        integration.profile = profile.name();
        integration.payloadBytes = 256;
        integration.setup();
        assertEquals(profile.algorithms().size(), integration.signMessage().size());
        assertTrue(integration.verifyMessage());
        assertNotNull(integration.signEnvelope());
        assertTrue(integration.verifyEnvelope());
        assertTrue(integration.encodeEnvelope().length > 256);
        assertEquals(256, integration.decodeEnvelope().payloadBytes().length);
        assertArrayEquals(
                before,
                Arrays.stream(Security.getProviders())
                        .map(provider -> provider.getName())
                        .toArray(String[]::new));
    }

    /** Invalid parameter sizes cannot become unbounded payload allocations. */
    @Test
    void refusesOutOfProfilePayloadBounds() {
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkWorkload(SignatureProfile.DUAL, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BenchmarkWorkload(SignatureProfile.DUAL, DsseLimits.MAX_PAYLOAD_BYTES + 1));
        assertThrows(NullPointerException.class, () -> new BenchmarkWorkload(null, 256));
    }

    /** Annotation processing must actually register all seven methods, not merely compile their annotations. */
    @Test
    void generatedHarnessRegistryContainsEveryOperation() throws Exception {
        try (var resource = getClass().getResourceAsStream("/META-INF/BenchmarkList")) {
            assertNotNull(resource, "JMH annotation processing must generate its registry.");
            String registry = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            for (String operation : EvidenceFixtures.OPERATIONS) assertTrue(registry.contains(operation));
            assertTrue(registry.contains("generateKeyPairs"));
        }
    }
}
