package hu.balazsfalvi.pqclab.integration.core;

/**
 * Holds independently verified results for keys owned by one trusted publisher.
 *
 * @param ed25519Valid whether a trusted Ed25519 key verified the envelope
 * @param mlDsa65Valid whether a trusted ML-DSA-65 key verified the same envelope
 */
public record SignatureEvidence(boolean ed25519Valid, boolean mlDsa65Valid) {}
