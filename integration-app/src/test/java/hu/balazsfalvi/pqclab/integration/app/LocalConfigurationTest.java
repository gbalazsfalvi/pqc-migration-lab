package hu.balazsfalvi.pqclab.integration.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hu.balazsfalvi.pqclab.integration.app.publisher.SigningIdentity;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalOptions;
import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks local launch configuration without starting listeners or changing persistent settings. */
final class LocalConfigurationTest {
    /** Keeps the strongest default while allowing an explicit controlled migration phase. */
    @Test
    void parsesDefaultsAndExplicitLocalSettings() {
        LocalOptions defaults = LocalOptions.parse(new String[0]);
        assertEquals(MigrationPolicy.BOTH_REQUIRED, defaults.policy());
        assertEquals(8781, defaults.publisherPort());
        assertEquals(8782, defaults.receiverPort());
        LocalOptions custom = LocalOptions.parse(new String[] {
            "--policy=SHADOW_PQC", "--publisher-port=9001", "--receiver-port=9002", "--data-dir=build/local test data"
        });
        assertEquals(MigrationPolicy.SHADOW_PQC, custom.policy());
        assertEquals(9001, custom.publisherPort());
        assertEquals(9002, custom.receiverPort());
        assertEquals(Path.of("build/local test data").toAbsolutePath().normalize(), custom.dataDirectory());
    }

    /** Bad names and values cannot silently widen binding, inject H2 options or choose a weaker fallback. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "--unknown=value",
                "--policy=anything",
                "--policy=",
                "--policy",
                "policy=BOTH_REQUIRED",
                "--publisher-port=-1",
                "--publisher-port=65536",
                "--publisher-port=not-a-number",
                "--receiver-port=8781",
                "--data-dir=build/test;INIT=RUNSCRIPT"
            })
    void rejectsInvalidLocalArguments(String argument) {
        assertThrows(IllegalArgumentException.class, () -> LocalOptions.parse(new String[] {argument}));
    }

    /** Duplicate configuration is rejected rather than relying on argument ordering. */
    @Test
    void rejectsDuplicateFlagsAndIncompleteSigningIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalOptions.parse(new String[] {"--policy=CLASSICAL_ONLY", "--policy=BOTH_REQUIRED"}));
        assertThrows(IllegalArgumentException.class, () -> new SigningIdentity("publisher", Map.of()));
        assertDoesNotThrow(() -> LocalIntegration.main(new String[] {"--help"}));
    }
}
