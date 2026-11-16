package hu.balazsfalvi.pqclab.integration.app.runtime;

import hu.balazsfalvi.pqclab.integration.app.publisher.SigningIdentity;
import hu.balazsfalvi.pqclab.integration.app.publisher.keys.EncryptedKeyVault;
import hu.balazsfalvi.pqclab.integration.app.publisher.keys.KeyLifecycleManager;
import hu.balazsfalvi.pqclab.integration.app.trust.PublicKeyDirectory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** Starts two isolated Spring Boot contexts and real loopback HTTP listeners in one local JVM. */
public final class LocalRuntime implements MigrationRuntime {
    /** Names the synthetic publisher; it never represents a real employer or partner. */
    public static final String ISSUER = RuntimeBootstrap.ISSUER;
    /** Identifies this integration as the signed event audience. */
    public static final String AUDIENCE = RuntimeBootstrap.AUDIENCE;

    private final ConfigurableApplicationContext publisher;
    private final ConfigurableApplicationContext receiver;
    private final URI publisherUri;
    private final URI receiverUri;
    private final KeyLifecycleManager keyLifecycle;

    /** Retains only running contexts and public endpoint addresses for coordinated shutdown. */
    private LocalRuntime(
            ConfigurableApplicationContext publisher,
            ConfigurableApplicationContext receiver,
            KeyLifecycleManager keyLifecycle) {
        this.publisher = publisher;
        this.receiver = receiver;
        this.publisherUri = address(publisher);
        this.receiverUri = address(receiver);
        this.keyLifecycle = keyLifecycle;
    }

    /**
     * Boots the durable local experiment with an account-protected encrypted key vault.
     *
     * @param options the trusted local launch settings
     * @return the running publisher and receiver
     * @throws IOException if local storage cannot be prepared
     */
    public static LocalRuntime start(LocalOptions options) throws IOException {
        Files.createDirectories(options.dataDirectory());
        return start(options, Clock.systemUTC(), EncryptedKeyVault.defaultSecretFile(options.dataDirectory()));
    }

    /**
     * Opens managed keys using an explicit external account secret location.
     *
     * @param options the trusted local launch settings
     * @param clock the trusted lifecycle and receiver clock
     * @param secretFile the owner-only secret outside the integration data directory
     * @return the durable running integration
     * @throws IOException if the key vault cannot be opened
     */
    public static LocalRuntime start(LocalOptions options, Clock clock, Path secretFile) throws IOException {
        var manager = KeyLifecycleManager.open(options.dataDirectory(), secretFile, ISSUER, clock);
        try {
            return start(options, clock, manager.signingIdentity(), manager.publicDirectory(), manager);
        } catch (IOException | RuntimeException failure) {
            manager.close();
            throw failure;
        }
    }

    /**
     * Supplies a clock and trust bundle for deterministic tests and controlled receiver restarts.
     *
     * @param options the trusted local settings
     * @param clock the shared test or system clock
     * @param trust the separate private publisher and public receiver identities
     * @return the running integration
     * @throws IOException if local storage cannot be prepared
     */
    public static LocalRuntime start(LocalOptions options, Clock clock, LocalTrust trust) throws IOException {
        return start(
                options,
                clock,
                trust.signingIdentity(),
                PublicKeyDirectory.fixed(trust.trustedPublisher(), clock),
                null);
    }

    /** Wires private publisher and public receiver capabilities separately for either runtime mode. */
    private static LocalRuntime start(
            LocalOptions options,
            Clock clock,
            SigningIdentity identity,
            PublicKeyDirectory publicTrust,
            KeyLifecycleManager manager)
            throws IOException {
        Files.createDirectories(options.dataDirectory());
        SpringApplication receiverApplication = application(ReceiverConfiguration.class);
        receiverApplication.addInitializers(context -> {
            context.getBeanFactory().registerSingleton("publicKeyDirectory", publicTrust);
            context.getBeanFactory().registerSingleton("clock", clock);
        });
        List<String> receiverArguments = arguments(options, options.receiverPort(), identity.issuer());
        RuntimeBootstrap.ledger(receiverArguments, options.dataDirectory());
        ConfigurableApplicationContext receiver = receiverApplication.run(receiverArguments.toArray(String[]::new));
        try {
            URI endpoint = address(receiver).resolve("/api/receiver/releases");
            SpringApplication publisherApplication = application(PublisherConfiguration.class);
            publisherApplication.addInitializers(context -> {
                context.getBeanFactory().registerSingleton("signingIdentity", identity);
                context.getBeanFactory().registerSingleton("publicKeyDirectory", publicTrust);
                if (manager != null) context.getBeanFactory().registerSingleton("keyLifecycleManager", manager);
                context.getBeanFactory().registerSingleton("receiverEndpoint", endpoint);
                context.getBeanFactory().registerSingleton("clock", clock);
            });
            ConfigurableApplicationContext publisher =
                    publisherApplication.run(arguments(options, options.publisherPort(), identity.issuer())
                            .toArray(String[]::new));
            return new LocalRuntime(publisher, receiver, manager);
        } catch (RuntimeException failure) {
            receiver.close();
            throw failure;
        }
    }

    /**
     * Returns the real publisher address, including an assigned ephemeral port in tests.
     *
     * @return the loopback publisher URI
     */
    public URI publisherUri() {
        return publisherUri;
    }

    /**
     * Returns the real receiver address.
     *
     * @return the loopback receiver URI
     */
    public URI receiverUri() {
        return receiverUri;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        try {
            publisher.close();
        } finally {
            try {
                receiver.close();
            } finally {
                if (keyLifecycle != null) {
                    try {
                        keyLifecycle.close();
                    } catch (IOException failure) {
                        throw new java.io.UncheckedIOException("The key vault lock could not be released.", failure);
                    }
                }
            }
        }
    }

    /** Creates a Boot context whose lifetime belongs to this launcher rather than independent hooks. */
    private static SpringApplication application(Class<?> source) {
        return RuntimeBootstrap.application(source);
    }

    /** Forces loopback binding and supplies receiver-owned settings with command-line precedence. */
    private static List<String> arguments(LocalOptions options, int port, String issuer) {
        return RuntimeBootstrap.arguments(options.policy(), port, false, issuer, "localhost");
    }

    /** Obtains the bound HTTP port only after the embedded server has started successfully. */
    private static URI address(ConfigurableApplicationContext context) {
        return RuntimeBootstrap.address(context);
    }
}
