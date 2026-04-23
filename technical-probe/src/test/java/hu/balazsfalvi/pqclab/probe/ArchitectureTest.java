package hu.balazsfalvi.pqclab.probe;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Makes the clean architecture boundaries executable rather than relying on documentation. */
final class ArchitectureTest {
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("hu.balazsfalvi.pqclab");

    /** Core code may use the Java standard library and its own types only. */
    @Test
    void coreHasNoTechnologyOrAdapterDependencies() {
        classes()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.core..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "hu.balazsfalvi.pqclab.core..")
                .check(PRODUCTION);
        noClasses()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.core..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("java.security..")
                .check(PRODUCTION);
    }

    /** Adapters cannot depend on each other, the executable, or a cryptographic vendor API. */
    @Test
    void adaptersStayIndependentAndVendorNeutral() {
        noClasses()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.crypto..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "hu.balazsfalvi.pqclab.json..", "hu.balazsfalvi.pqclab.probe..",
                        "org.bouncycastle..", "com.fasterxml.jackson..")
                .check(PRODUCTION);
        noClasses()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.json..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "hu.balazsfalvi.pqclab.crypto..", "hu.balazsfalvi.pqclab.probe..",
                        "java.security..", "org.bouncycastle..")
                .check(PRODUCTION);
    }
}
