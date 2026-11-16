package hu.balazsfalvi.pqclab.integration.app.runtime;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/** Shares explicit, bounded Boot settings between the legacy and separate-process launchers. */
public final class RuntimeBootstrap {
    /** Names the synthetic publisher, never a real employer or partner. */
    public static final String ISSUER = "release-publisher";
    /** Identifies the receiver in the signed business claims. */
    public static final String AUDIENCE = "urn:pqclab:release-receiver";

    /** Prevents construction of a bootstrap utility. */
    private RuntimeBootstrap() {}

    /**
     * Gives the owning launcher sole responsibility for closing the application.
     *
     * @param source the explicit composition root
     * @return the unstarted application without package scanning
     */
    public static SpringApplication application(Class<?> source) {
        var application = new SpringApplication(source);
        application.setRegisterShutdownHook(false);
        return application;
    }

    /**
     * Supplies command-line-precedence settings after strict launcher validation.
     *
     * @param policy the receiver-owned policy
     * @param port the selected port, or zero for native tests
     * @param compose whether to bind inside the isolated Compose network
     * @param issuer the independently configured publisher owner
     * @param host the single additional service Host header
     * @return mutable application arguments
     */
    public static List<String> arguments(
            MigrationPolicy policy, int port, boolean compose, String issuer, String host) {
        return new ArrayList<>(List.of(
                "--server.address=" + (compose ? "0.0.0.0" : "127.0.0.1"),
                "--server.port=" + port,
                "--lab.policy=" + policy,
                "--lab.issuer=" + issuer,
                "--lab.audience=" + AUDIENCE,
                "--lab.service-host=" + host,
                "--lab.maximum-age=PT5M",
                "--lab.future-tolerance=PT30S",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN",
                "--server.shutdown=graceful",
                "--spring.lifecycle.timeout-per-shutdown-phase=5s",
                "--server.error.include-message=never",
                "--server.error.include-stacktrace=never",
                "--server.tomcat.threads.max=16",
                "--server.tomcat.accept-count=16",
                "--server.tomcat.connection-timeout=5s",
                "--spring.jmx.enabled=false"));
    }

    /**
     * Keeps ledger configuration exclusively on the receiver side.
     *
     * @param arguments the receiver's argument list
     * @param directory its independent durable state directory
     */
    public static void ledger(List<String> arguments, Path directory) {
        String file = directory.resolve("release-ledger").toString().replace('\\', '/');
        arguments.addAll(List.of(
                // An acknowledged receipt must survive immediate process termination, not only graceful close.
                "--spring.datasource.url=jdbc:h2:file:" + file + ";DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.datasource.hikari.maximum-pool-size=4",
                "--spring.flyway.clean-disabled=true"));
    }

    /**
     * Reads the assigned HTTP port after successful server startup.
     *
     * @param context the running servlet context
     * @return its native loopback observation address
     */
    public static URI address(ConfigurableApplicationContext context) {
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        return URI.create("http://127.0.0.1:" + port);
    }
}
