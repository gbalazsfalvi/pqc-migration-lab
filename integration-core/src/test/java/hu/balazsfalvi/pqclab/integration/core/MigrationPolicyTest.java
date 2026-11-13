package hu.balazsfalvi.pqclab.integration.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Defines the complete acceptance matrix before wiring it to HTTP or persistence. */
final class MigrationPolicyTest {
    /** Covers all four combinations, including signature stripping and absent signatures. */
    @ParameterizedTest
    @CsvSource({
        "false,false,false,false,false,false",
        "true,false,true,true,false,false",
        "false,true,false,false,false,true",
        "true,true,true,true,true,true"
    })
    void enforcesExplicitReceiverRequirements(
            boolean ed, boolean ml, boolean classical, boolean shadow, boolean both, boolean pqc) {
        SignatureEvidence evidence = new SignatureEvidence(ed, ml);
        assertEquals(classical, MigrationPolicy.CLASSICAL_ONLY.accepts(evidence));
        assertEquals(shadow, MigrationPolicy.SHADOW_PQC.accepts(evidence));
        assertEquals(both, MigrationPolicy.BOTH_REQUIRED.accepts(evidence));
        assertEquals(pqc, MigrationPolicy.PQC_REQUIRED.accepts(evidence));
    }
}
