package hu.balazsfalvi.pqclab.benchmarks;

import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Separates adapter, DSSE and JSON costs for the same pre-generated, valid synthetic inputs. */
@State(Scope.Thread)
public class IntegrationBenchmark {
    /** JMH injects one of the fixed single or dual-signature profiles. */
    @Param({"ED25519", "ML_DSA_65", "DUAL"})
    public String profile;

    /** JMH injects decoded sizes; the launcher may select a documented subset for shorter runs. */
    @Param({"256", "4096", "65536"})
    public int payloadBytes;

    private BenchmarkWorkload workload;

    /** Creates an empty JMH state, populated only during untimed setup. */
    public IntegrationBenchmark() {}

    /** Generates keys and valid signatures once per thread and fork, outside the timing loop. */
    @Setup(Level.Trial)
    public void setup() {
        workload = new BenchmarkWorkload(SignatureProfile.valueOf(profile), payloadBytes);
    }

    /**
     * Measures raw signing through the production adapter.
     *
     * @return fresh signatures consumed by JMH
     */
    @Benchmark
    public List<byte[]> signMessage() {
        return workload.signMessage();
    }

    /**
     * Measures verification of existing raw signatures.
     *
     * @return the complete verification result
     */
    @Benchmark
    public boolean verifyMessage() {
        return workload.verifyMessage();
    }

    /**
     * Measures DSSE framing and signing, excluding outer JSON.
     *
     * @return the new signed envelope
     */
    @Benchmark
    public DsseEnvelope signEnvelope() {
        return workload.signEnvelope();
    }

    /**
     * Measures DSSE verification, including payload decoding and PAE reconstruction.
     *
     * @return whether every configured algorithm verified
     */
    @Benchmark
    public boolean verifyEnvelope() {
        return workload.verifyEnvelope();
    }

    /**
     * Measures strict JSON serialization of an existing envelope.
     *
     * @return wire bytes consumed by JMH
     */
    @Benchmark
    public byte[] encodeEnvelope() {
        return workload.encodeEnvelope();
    }

    /**
     * Measures bounded JSON parsing without cryptographic verification.
     *
     * @return the decoded immutable envelope
     */
    @Benchmark
    public DsseEnvelope decodeEnvelope() {
        return workload.decodeEnvelope();
    }
}
