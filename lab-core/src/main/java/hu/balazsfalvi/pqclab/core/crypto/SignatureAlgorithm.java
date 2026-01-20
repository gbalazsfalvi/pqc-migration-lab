package hu.balazsfalvi.pqclab.core.crypto;

/** Identifies the two signature families exercised by milestone one. */
public enum SignatureAlgorithm {
    /** The classical Ed25519 baseline. */
    ED25519,
    /** The FIPS 204 ML-DSA parameter set used by this laboratory. */
    ML_DSA_65
}
