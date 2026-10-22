package hu.balazsfalvi.pqclab.benchmarks;

import hu.balazsfalvi.pqclab.crypto.JcaSignatureEngine;
import java.security.KeyPair;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Measures production adapter key generation, without multiplying it by irrelevant payload sizes. */
@State(Scope.Thread)
public class KeyGenerationBenchmark {
    /** JMH injects the algorithms to generate in one operation. */
    @Param({"ED25519", "ML_DSA_65", "DUAL"})
    public String profile;

    private List<JcaSignatureEngine> engines;

    /** Creates an empty JMH state; trusted engines are selected at trial setup. */
    public KeyGenerationBenchmark() {}

    /** Resolves provider bindings once, outside the measured key-generation operation. */
    @Setup(Level.Trial)
    public void setup() {
        engines = SignatureProfile.valueOf(profile).algorithms().stream()
                .map(JcaSignatureEngine::usingDefaultProvider)
                .toList();
    }

    /**
     * Includes fresh generator construction, secure randomness and the complete key-pair result.
     *
     * @return fresh ephemeral pairs consumed by JMH, never written to an evidence file
     */
    @Benchmark
    public List<KeyPair> generateKeyPairs() {
        return engines.stream().map(JcaSignatureEngine::generateKeyPair).toList();
    }
}
