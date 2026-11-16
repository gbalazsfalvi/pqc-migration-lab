package hu.balazsfalvi.pqclab.integration.app.runtime;

import hu.balazsfalvi.pqclab.integration.app.publisher.keys.EncryptedKeyVault;
import hu.balazsfalvi.pqclab.integration.app.publisher.keys.KeyLifecycleManager;
import hu.balazsfalvi.pqclab.integration.app.trust.FilePublicDirectory;
import hu.balazsfalvi.pqclab.integration.app.trust.SharedTrustStore;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import org.springframework.context.ConfigurableApplicationContext;

/** Owns exactly one Boot context; receiver startup never opens a vault, signer or account secret. */
public final class ServiceRuntime implements AutoCloseable {
    private final ConfigurableApplicationContext context;
    private final KeyLifecycleManager keys;

    /** Retains only this role's owned resources for deterministic shutdown. */
    private ServiceRuntime(ConfigurableApplicationContext context, KeyLifecycleManager keys) {
        this.context = context;
        this.keys = keys;
    }

    /**
     * Initializes publisher state and its public projection before either service is started.
     *
     * @param options the initializer's trusted local settings
     * @throws IOException if authenticated state cannot be initialized or reconciled
     */
    public static void initialize(ServiceOptions options) throws IOException {
        if (options.role() != ServiceOptions.Role.INITIALIZE) throw new IllegalArgumentException("Use the init role.");
        try (var manager = publisherKeys(options)) {
            manager.publicDirectory().view();
        }
    }

    /**
     * Starts one independent HTTP service with role-owned storage and capabilities.
     *
     * @param options the strict launch settings
     * @return the running single-role service
     * @throws IOException if storage, public trust or a native ready marker cannot be prepared
     */
    public static ServiceRuntime start(ServiceOptions options) throws IOException {
        if (options.role() == ServiceOptions.Role.INITIALIZE)
            throw new IllegalArgumentException("Init does not serve HTTP.");
        Files.createDirectories(options.dataDirectory());
        Clock clock = Clock.systemUTC();
        var publicDirectory = new FilePublicDirectory(
                new SharedTrustStore(options.trustDirectory(), RuntimeBootstrap.ISSUER, false), clock);
        KeyLifecycleManager manager = null;
        ConfigurableApplicationContext context = null;
        try {
            if (options.role() == ServiceOptions.Role.PUBLISHER) manager = publisherKeys(options);
            publicDirectory.view();
            boolean publisher = options.role() == ServiceOptions.Role.PUBLISHER;
            var application = RuntimeBootstrap.application(
                    publisher ? PublisherConfiguration.class : ReceiverConfiguration.class);
            KeyLifecycleManager ownedKeys = manager;
            application.addInitializers(candidate -> {
                var beans = candidate.getBeanFactory();
                beans.registerSingleton("clock", clock);
                beans.registerSingleton(
                        "publicKeyDirectory", publisher ? ownedKeys.publicDirectory() : publicDirectory);
                beans.registerSingleton(
                        "serviceHealth", new ServiceHealth(options.role().name(), publicDirectory));
                if (publisher) {
                    beans.registerSingleton("signingIdentity", ownedKeys.signingIdentity());
                    beans.registerSingleton("keyLifecycleManager", ownedKeys);
                    beans.registerSingleton("receiverEndpoint", options.receiverEndpoint());
                }
            });
            var arguments = RuntimeBootstrap.arguments(
                    options.policy(),
                    options.port(),
                    options.compose(),
                    RuntimeBootstrap.ISSUER,
                    publisher ? "publisher" : "receiver");
            if (!publisher) RuntimeBootstrap.ledger(arguments, options.dataDirectory());
            context = application.run(arguments.toArray(String[]::new));
            var runtime = new ServiceRuntime(context, manager);
            if (options.readyFile() != null) {
                Files.writeString(options.readyFile(), runtime.address().toString(), StandardOpenOption.CREATE_NEW);
            }
            return runtime;
        } catch (IOException | RuntimeException failure) {
            if (context != null) context.close();
            if (manager != null) manager.close();
            throw failure;
        }
    }

    /** Opens private state only for publisher/init roles and rejects a secret inside the public namespace. */
    private static KeyLifecycleManager publisherKeys(ServiceOptions options) throws IOException {
        Files.createDirectories(options.dataDirectory());
        Path secret = options.secretFile() == null
                ? EncryptedKeyVault.defaultSecretFile(options.dataDirectory())
                : options.secretFile();
        if (secret.toAbsolutePath().normalize().startsWith(options.trustDirectory())) {
            throw new IOException("The account secret cannot enter the public trust namespace.");
        }
        return KeyLifecycleManager.open(
                options.dataDirectory(),
                secret,
                RuntimeBootstrap.ISSUER,
                Clock.systemUTC(),
                new SharedTrustStore(options.trustDirectory(), RuntimeBootstrap.ISSUER, true));
    }

    /**
     * Returns the actual assigned native HTTP address.
     *
     * @return the local observation URI
     */
    public URI address() {
        return RuntimeBootstrap.address(context);
    }

    /** Stops HTTP handling before releasing the publisher's private vault process lock. */
    @Override
    public void close() throws IOException {
        try {
            context.close();
        } finally {
            if (keys != null) keys.close();
        }
    }
}
