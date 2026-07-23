package hu.balazsfalvi.pqclab.integration.app.runtime;

import hu.balazsfalvi.pqclab.integration.app.http.ApiErrors;
import hu.balazsfalvi.pqclab.integration.app.http.LocalApiGuard;
import hu.balazsfalvi.pqclab.integration.app.publisher.PublisherController;
import hu.balazsfalvi.pqclab.integration.app.publisher.PublisherService;
import hu.balazsfalvi.pqclab.integration.app.publisher.ReceiverClient;
import hu.balazsfalvi.pqclab.integration.app.publisher.SigningIdentity;
import hu.balazsfalvi.pqclab.integration.app.publisher.keys.KeyLifecycleController;
import hu.balazsfalvi.pqclab.integration.app.wire.ReleaseJsonCodec;
import hu.balazsfalvi.pqclab.integration.app.workbench.ExperimentFixtures;
import hu.balazsfalvi.pqclab.integration.app.workbench.WorkbenchController;
import hu.balazsfalvi.pqclab.integration.core.PublisherTrust;
import java.net.URI;
import java.time.Clock;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Composes the publisher without creating a database or scanning receiver components. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
@EnableConfigurationProperties(ReceiverProperties.class)
@Import({PublisherController.class, WorkbenchController.class, KeyLifecycleController.class, ApiErrors.class})
public class PublisherConfiguration {
    /** Creates the stateless publisher composition root. */
    public PublisherConfiguration() {}

    /**
     * Binds release creation to the publisher's runtime-only private signing ports.
     *
     * @param identity the publisher-only signing material
     * @param settings the signed receiver audience
     * @param clock the publication clock
     * @return the synthetic publisher service
     */
    @Bean
    public PublisherService publisher(SigningIdentity identity, ReceiverProperties settings, Clock clock) {
        return new PublisherService(identity, new ReleaseJsonCodec(), clock, settings.audience());
    }

    /**
     * Uses only the receiver endpoint selected by the local bootstrap.
     *
     * @param receiverEndpoint the trusted loopback endpoint
     * @return the bounded delivery adapter
     */
    @Bean
    public ReceiverClient receiverClient(URI receiverEndpoint) {
        return new ReceiverClient(receiverEndpoint);
    }

    /**
     * Prevents browser pages from invoking the local synthetic signing API.
     *
     * @return the local API guard
     */
    @Bean
    public LocalApiGuard guard() {
        return new LocalApiGuard();
    }

    /**
     * Gives only the publisher controlled fixture-signing capabilities.
     *
     * @param identity the private signing ports
     * @param directory the corresponding public trust directory
     * @param settings the local receiver claim requirements
     * @param clock the fixture clock
     * @return the bounded synthetic experiment builder
     */
    @Bean
    public ExperimentFixtures fixtures(
            SigningIdentity identity, PublisherTrust directory, ReceiverProperties settings, Clock clock) {
        return new ExperimentFixtures(identity, directory, settings, clock);
    }
}
