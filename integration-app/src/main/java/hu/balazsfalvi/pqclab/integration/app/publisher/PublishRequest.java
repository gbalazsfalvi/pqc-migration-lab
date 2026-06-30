package hu.balazsfalvi.pqclab.integration.app.publisher;

/**
 * Allows only synthetic release details and a publisher-side signature selection.
 *
 * @param artifact the synthetic artifact label
 * @param version the version label
 * @param sha256 the artifact digest
 * @param signingMode the publisher's selected signatures
 */
public record PublishRequest(String artifact, String version, String sha256, SigningMode signingMode) {}
