package hu.balazsfalvi.pqclab.integration.app.runtime;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Defines local launch settings without allowing a remote receiver or public bind address.
 *
 * @param policy the receiver's migration policy
 * @param publisherPort the publisher port, or zero for an ephemeral test port
 * @param receiverPort the receiver port, or zero for an ephemeral test port
 * @param dataDirectory the local directory containing the durable H2 ledger
 */
public record LocalOptions(MigrationPolicy policy, int publisherPort, int receiverPort, Path dataDirectory) {
    /**
     * Validates local ports and keeps the filesystem path out of H2's URL option syntax.
     *
     * @param policy the trusted receiver policy
     * @param publisherPort the publisher's loopback port
     * @param receiverPort the receiver's loopback port
     * @param dataDirectory the durable local database directory
     */
    public LocalOptions {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        dataDirectory = dataDirectory.toAbsolutePath().normalize();
        String path = dataDirectory.toString();
        if (publisherPort < 0
                || publisherPort > 65535
                || receiverPort < 0
                || receiverPort > 65535
                || (publisherPort != 0 && publisherPort == receiverPort)
                || path.contains(";")
                || path.contains("\n")
                || path.contains("\r")) {
            throw new IllegalArgumentException("Use distinct local ports and an ordinary local data directory.");
        }
    }

    /**
     * Reads a small strict command line; receiver policy never comes from an HTTP request.
     *
     * @param arguments the optional key-equals-value settings
     * @return validated launch settings
     */
    public static LocalOptions parse(String[] arguments) {
        Map<String, String> values = new HashMap<>();
        Set<String> allowed = Set.of("policy", "publisher-port", "receiver-port", "data-dir");
        for (String argument : arguments) {
            String[] pair = argument.split("=", 2);
            if (pair.length != 2 || !pair[0].startsWith("--") || pair[1].isBlank()) {
                throw new IllegalArgumentException("Use documented --name=value launch arguments.");
            }
            String key = pair[0].substring(2);
            if (!allowed.contains(key) || values.putIfAbsent(key, pair[1]) != null) {
                throw new IllegalArgumentException("Unknown or duplicate local launch argument.");
            }
        }
        return new LocalOptions(
                MigrationPolicy.valueOf(values.getOrDefault("policy", "BOTH_REQUIRED")),
                Integer.parseInt(values.getOrDefault("publisher-port", "8781")),
                Integer.parseInt(values.getOrDefault("receiver-port", "8782")),
                Path.of(values.getOrDefault("data-dir", ".local/integration")));
    }
}
