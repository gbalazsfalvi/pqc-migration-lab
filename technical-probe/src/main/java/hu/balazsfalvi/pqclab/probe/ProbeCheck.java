package hu.balazsfalvi.pqclab.probe;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Records one observed expectation with public, reproducible implementation metadata.
 *
 * @param id the stable machine-readable check identifier
 * @param description the expectation that was tested
 * @param passed whether the observed result met that expectation
 * @param evidence the public metadata, never private key material
 */
public record ProbeCheck(String id, String description, boolean passed, Map<String, String> evidence) {
    /**
     * Freezes metadata in a stable order for readable report diffs.
     *
     * @param id the stable check identifier
     * @param description the tested expectation
     * @param passed the outcome of the expectation
     * @param evidence the public implementation metadata
     */
    public ProbeCheck {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        evidence = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(evidence, "evidence")));
    }
}
