package hu.balazsfalvi.pqclab.benchmarks;

import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import java.util.List;

/** Defines the single-algorithm and sequential dual-signature work performed by one operation. */
public enum SignatureProfile {
    /** The classical Ed25519 baseline. */
    ED25519(List.of(SignatureAlgorithm.ED25519)),
    /** Pure ML-DSA-65 with the existing adapter's empty context. */
    ML_DSA_65(List.of(SignatureAlgorithm.ML_DSA_65)),
    /** One complete operation for each algorithm, performed sequentially on the same bytes. */
    DUAL(List.of(SignatureAlgorithm.ED25519, SignatureAlgorithm.ML_DSA_65));

    private final List<SignatureAlgorithm> algorithms;

    /** Snapshots the algorithms in their actual execution order. */
    SignatureProfile(List<SignatureAlgorithm> algorithms) {
        this.algorithms = List.copyOf(algorithms);
    }

    /**
     * Returns the fixed algorithms required by this benchmark profile.
     *
     * @return immutable ordered algorithms
     */
    public List<SignatureAlgorithm> algorithms() {
        return algorithms;
    }
}
