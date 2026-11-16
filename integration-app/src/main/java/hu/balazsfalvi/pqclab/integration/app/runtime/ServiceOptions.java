package hu.balazsfalvi.pqclab.integration.app.runtime;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Defines trusted role-specific launch settings without accepting arbitrary upstream hosts.
 *
 * @param role the single process responsibility
 * @param compose whether the process belongs to the isolated Compose network
 * @param port the native listener port, or zero for an ephemeral native test port
 * @param receiverPort the fixed upstream receiver port
 * @param dataDirectory this role's independent persistent state
 * @param trustDirectory the public-only shared namespace
 * @param secretFile an optional external publisher-only secret path
 * @param readyFile an optional owner-selected native startup marker
 * @param policy the receiver-owned migration policy
 */
public record ServiceOptions(
        Role role,
        boolean compose,
        int port,
        int receiverPort,
        Path dataDirectory,
        Path trustDirectory,
        Path secretFile,
        Path readyFile,
        MigrationPolicy policy) {
    /** Names the three explicit executable responsibilities. */
    public enum Role {
        /** Initializes authenticated publisher state and public trust without starting HTTP. */
        INITIALIZE,
        /** Owns signing, lifecycle administration and the bundled React workbench. */
        PUBLISHER,
        /** Owns verification, policy enforcement and the durable business ledger. */
        RECEIVER
    }

    /** Validates ordinary independent storage, local networking and role-specific access. */
    public ServiceOptions {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(policy, "policy");
        dataDirectory = new LocalOptions(policy, 0, 0, dataDirectory).dataDirectory();
        trustDirectory = new LocalOptions(policy, 0, 0, trustDirectory).dataDirectory();
        if (port < 0
                || port > 65535
                || receiverPort < 1
                || receiverPort > 65535
                || dataDirectory.startsWith(trustDirectory)
                || trustDirectory.startsWith(dataDirectory)
                || (compose && (receiverPort != 8782 || port != (role == Role.RECEIVER ? 8782 : 8781)))) {
            throw new IllegalArgumentException("Use independent storage and documented local service ports.");
        }
        if (secretFile != null) {
            secretFile = secretFile.toAbsolutePath().normalize();
            if (role == Role.RECEIVER
                    || secretFile.startsWith(dataDirectory)
                    || secretFile.startsWith(trustDirectory)) {
                throw new IllegalArgumentException("Only the publisher may use an external private account secret.");
            }
        }
        if (readyFile != null) readyFile = readyFile.toAbsolutePath().normalize();
    }

    /**
     * Parses only documented options; raw Spring arguments and caller-selected remote URLs are rejected.
     *
     * @param arguments the strict key-equals-value settings
     * @return validated role settings
     */
    public static ServiceOptions parse(String[] arguments) {
        Map<String, String> values = new HashMap<>();
        Set<String> allowed = Set.of(
                "role",
                "network",
                "port",
                "receiver-port",
                "data-dir",
                "trust-dir",
                "secret-file",
                "ready-file",
                "policy");
        for (String argument : arguments) {
            String[] pair = argument.split("=", 2);
            if (pair.length != 2
                    || !pair[0].startsWith("--")
                    || pair[1].isBlank()
                    || !allowed.contains(pair[0].substring(2))
                    || values.putIfAbsent(pair[0].substring(2), pair[1]) != null) {
                throw new IllegalArgumentException("Use documented --name=value service options.");
            }
        }
        Role role = switch (values.getOrDefault("role", "")) {
            case "init" -> Role.INITIALIZE;
            case "publisher" -> Role.PUBLISHER;
            case "receiver" -> Role.RECEIVER;
            default -> throw new IllegalArgumentException("Choose role init, publisher or receiver.");
        };
        String network = values.getOrDefault("network", "local");
        if (!Set.of("local", "compose").contains(network))
            throw new IllegalArgumentException("Unknown service network.");
        boolean compose = network.equals("compose");
        String storageRole = role == Role.RECEIVER ? "receiver" : "publisher";
        String base = compose ? "/data/" : ".local/services/";
        return new ServiceOptions(
                role,
                compose,
                Integer.parseInt(values.getOrDefault("port", role == Role.RECEIVER ? "8782" : "8781")),
                Integer.parseInt(values.getOrDefault("receiver-port", "8782")),
                Path.of(values.getOrDefault("data-dir", base + storageRole)),
                Path.of(values.getOrDefault("trust-dir", compose ? "/data/trust" : base + "public-trust")),
                values.containsKey("secret-file") ? Path.of(values.get("secret-file")) : null,
                values.containsKey("ready-file") ? Path.of(values.get("ready-file")) : null,
                MigrationPolicy.valueOf(values.getOrDefault("policy", "BOTH_REQUIRED")));
    }

    /**
     * Constructs only a bootstrap-owned local or exact Compose service endpoint.
     *
     * @return the complete receiver delivery route
     */
    public URI receiverEndpoint() {
        return URI.create(
                "http://" + (compose ? "receiver" : "127.0.0.1") + ":" + receiverPort + "/api/receiver/releases");
    }
}
