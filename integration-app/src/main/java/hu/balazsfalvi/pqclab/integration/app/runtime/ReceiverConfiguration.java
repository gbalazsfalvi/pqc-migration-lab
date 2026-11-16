package hu.balazsfalvi.pqclab.integration.app.runtime;

import hu.balazsfalvi.pqclab.integration.app.http.ApiErrors;
import hu.balazsfalvi.pqclab.integration.app.http.LocalApiGuard;
import hu.balazsfalvi.pqclab.integration.app.ledger.JdbcReleaseLedger;
import hu.balazsfalvi.pqclab.integration.app.ledger.Sha256PayloadDigester;
import hu.balazsfalvi.pqclab.integration.app.receiver.EnvelopeInspector;
import hu.balazsfalvi.pqclab.integration.app.receiver.EvaluationController;
import hu.balazsfalvi.pqclab.integration.app.receiver.KeyRegistryController;
import hu.balazsfalvi.pqclab.integration.app.receiver.ReceiverController;
import hu.balazsfalvi.pqclab.integration.app.trust.PublicKeyDirectory;
import hu.balazsfalvi.pqclab.integration.app.wire.ReleaseJsonCodec;
import hu.balazsfalvi.pqclab.integration.core.EventValidator;
import hu.balazsfalvi.pqclab.integration.core.MigrationEvaluator;
import hu.balazsfalvi.pqclab.integration.core.PolicyVerifier;
import hu.balazsfalvi.pqclab.integration.core.ReceptionService;
import hu.balazsfalvi.pqclab.integration.core.ReleaseLedger;
import java.time.Clock;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Composes the receiver without scanning publisher components or receiving signing ports. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableConfigurationProperties(ReceiverProperties.class)
@Import({ReceiverController.class, EvaluationController.class, KeyRegistryController.class, ApiErrors.class})
public class ReceiverConfiguration {
    /** Creates the receiver configuration without mutable configuration state. */
    public ReceiverConfiguration() {}

    /**
     * Defines one atomic durable store for the release application and replay reservation.
     *
     * @param jdbc the Boot-configured JDBC operations
     * @param manager the transaction manager for the same database
     * @return the durable release ledger
     */
    @Bean
    public ReleaseLedger ledger(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new JdbcReleaseLedger(jdbc, new TransactionTemplate(manager));
    }

    /**
     * Builds the framework-free receive use case from explicit trusted dependencies.
     *
     * @param directory the public-only operation-scoped trust directory
     * @param settings the receiver-owned policy and time limits
     * @param ledger the durable business application port
     * @param clock the receiver clock
     * @return the immutable application service
     */
    @Bean
    public ReceptionService reception(
            PublicKeyDirectory directory, ReceiverProperties settings, ReleaseLedger ledger, Clock clock) {
        return new ReceptionService(
                new PolicyVerifier(directory),
                settings.policy(),
                new ReleaseJsonCodec(),
                ledger,
                new Sha256PayloadDigester(),
                clock,
                settings.audience(),
                settings.maximumAge(),
                settings.futureTolerance());
    }

    /**
     * Rejects browser-origin requests and applies safe headers before verification.
     *
     * @param environment bootstrap-owned service host configuration
     * @return the local API guard
     */
    @Bean
    public LocalApiGuard guard(org.springframework.core.env.Environment environment) {
        return new LocalApiGuard(java.util.Set.of(environment.getProperty("lab.service-host", "localhost")));
    }

    /**
     * Keeps policy exploration separate from the durable reception use case.
     *
     * @param directory the public trusted owner and verification ports
     * @param settings the unchanged live receiver requirements
     * @param clock the trusted observation clock
     * @return a ledger-free cryptographic inspector
     */
    @Bean
    public EnvelopeInspector inspector(PublicKeyDirectory directory, ReceiverProperties settings, Clock clock) {
        var validator = new EventValidator(
                new ReleaseJsonCodec(), clock, settings.audience(), settings.maximumAge(), settings.futureTolerance());
        return new EnvelopeInspector(
                new MigrationEvaluator(new PolicyVerifier(directory), validator, clock), directory, settings);
    }
}
