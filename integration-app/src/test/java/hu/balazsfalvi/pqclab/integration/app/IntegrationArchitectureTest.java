package hu.balazsfalvi.pqclab.integration.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Extends executable dependency rules to the new use cases and receiver capability boundary. */
final class IntegrationArchitectureTest {
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("hu.balazsfalvi.pqclab");

    /** The demonstration observes public HTTP contracts and cannot inspect private key or ledger adapters. */
    @Test
    void migrationEvidenceCannotReadPrivateCapabilitiesOrPersistenceAdapters() {
        noClasses()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.integration.app.migration..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "hu.balazsfalvi.pqclab.integration.app.publisher..",
                        "hu.balazsfalvi.pqclab.integration.app.ledger..",
                        "hu.balazsfalvi.pqclab.crypto..",
                        "javax.crypto..")
                .check(PRODUCTION);
        noClasses()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.integration.app.migration..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.security.PrivateKey")
                .check(PRODUCTION);
    }

    /** Application rules depend inward on the protocol, never on Spring, JDBC, JCA or wire adapters. */
    @Test
    void applicationCoreIsFrameworkAndAdapterFree() {
        classes()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.integration.core..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..", "hu.balazsfalvi.pqclab.integration.core..", "hu.balazsfalvi.pqclab.core..")
                .check(PRODUCTION);
        noClasses()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.integration.core..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("java.security..", "java.sql..")
                .check(PRODUCTION);
    }

    /** Receiver request handling has no dependency on private publisher signing capabilities. */
    @Test
    void receiverCannotDependOnPublisherSigningCode() {
        noClasses()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.integration.app.trust..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("hu.balazsfalvi.pqclab.integration.app.publisher..", "javax.crypto..")
                .check(PRODUCTION);
        noClasses()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.integration.app.trust..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.security.PrivateKey")
                .check(PRODUCTION);
        noClasses()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.integration.app.receiver..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("hu.balazsfalvi.pqclab.integration.app.publisher..")
                .check(PRODUCTION);
        noClasses()
                .that()
                .resideInAPackage("hu.balazsfalvi.pqclab.core..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("hu.balazsfalvi.pqclab.integration..")
                .check(PRODUCTION);
    }
}
