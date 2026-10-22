package hu.balazsfalvi.pqclab.benchmarks;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Keeps benchmark orchestration outside protocol rules and the live receiver's dependency graph. */
final class BenchmarkArchitectureTest {
    /** Benchmark code may use the public protocol/adapters, never Spring or integration services. */
    @Test
    void benchmarksStayIndependentOfTheWorkingApplication() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("hu.balazsfalvi.pqclab");
        noClasses()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.benchmarks..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("hu.balazsfalvi.pqclab.integration..", "org.springframework..", "java.sql..")
                .check(classes);
        noClasses()
                .that()
                .resideInAnyPackage(
                        "hu.balazsfalvi.pqclab.core..",
                        "hu.balazsfalvi.pqclab.crypto..",
                        "hu.balazsfalvi.pqclab.json..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.openjdk.jmh..", "hu.balazsfalvi.pqclab.benchmarks..")
                .check(classes);
    }
}
