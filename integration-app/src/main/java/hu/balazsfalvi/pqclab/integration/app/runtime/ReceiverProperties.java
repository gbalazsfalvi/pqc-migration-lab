package hu.balazsfalvi.pqclab.integration.app.runtime;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds receiver-owned policy, identity and time requirements through typed Spring configuration.
 *
 * @param policy the configured migration requirement
 * @param issuer the trusted publisher namespace used by this local example
 * @param audience the accepted signed audience
 * @param maximumAge the allowed age of a release event
 * @param futureTolerance the permitted publisher clock lead
 */
@ConfigurationProperties("lab")
public record ReceiverProperties(
        MigrationPolicy policy, String issuer, String audience, Duration maximumAge, Duration futureTolerance) {
    /**
     * Fails startup instead of silently choosing missing security configuration.
     *
     * @param policy the explicit migration policy
     * @param issuer the trusted publisher name
     * @param audience the receiving integration identity
     * @param maximumAge the maximum event age
     * @param futureTolerance the future clock tolerance
     */
    public ReceiverProperties {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(maximumAge, "maximumAge");
        Objects.requireNonNull(futureTolerance, "futureTolerance");
    }
}
