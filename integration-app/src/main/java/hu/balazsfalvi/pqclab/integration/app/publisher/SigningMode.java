package hu.balazsfalvi.pqclab.integration.app.publisher;

import hu.balazsfalvi.pqclab.core.crypto.SignatureAlgorithm;
import java.util.List;

/** Selects publisher signatures for an experiment without changing receiver requirements. */
public enum SigningMode {
    /** Emits only the classical signature for the baseline and stripping experiments. */
    ED25519_ONLY,
    /** Emits only the post-quantum signature. */
    ML_DSA_65_ONLY,
    /** Emits both independent signatures over identical PAE bytes. */
    DUAL;

    /**
     * Lists the algorithms that the publisher must use for this envelope.
     *
     * @return the immutable ordered algorithm selection
     */
    public List<SignatureAlgorithm> algorithms() {
        return switch (this) {
            case ED25519_ONLY -> List.of(SignatureAlgorithm.ED25519);
            case ML_DSA_65_ONLY -> List.of(SignatureAlgorithm.ML_DSA_65);
            case DUAL -> List.of(SignatureAlgorithm.ED25519, SignatureAlgorithm.ML_DSA_65);
        };
    }
}
