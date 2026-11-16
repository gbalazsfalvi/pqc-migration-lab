package hu.balazsfalvi.pqclab.integration.app.runtime;

import static org.junit.jupiter.api.Assertions.*;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises real role composition and public-only startup before subprocess demonstrations are run. */
final class ServiceRuntimeTest {
    @TempDir
    Path root;

    /** Builds deterministic role-owned storage while leaving the actual HTTP ports to the OS. */
    private ServiceOptions options(ServiceOptions.Role role, int receiverPort) {
        return new ServiceOptions(
                role,
                false,
                0,
                receiverPort,
                root.resolve(role == ServiceOptions.Role.RECEIVER ? "receiver" : "publisher"),
                root.resolve("public"),
                role == ServiceOptions.Role.RECEIVER ? null : root.resolve("secret/account.key"),
                null,
                MigrationPolicy.BOTH_REQUIRED);
    }

    /** Initialization, receiver and publisher compose without sharing private objects or ledger directories. */
    @Test
    void startsIndependentRolesAndObservesRealReceiverThroughGateway() throws Exception {
        var init = options(ServiceOptions.Role.INITIALIZE, 8782);
        ServiceRuntime.initialize(init);
        try (var receiver = ServiceRuntime.start(options(ServiceOptions.Role.RECEIVER, 8782));
                var publisher = ServiceRuntime.start(options(
                        ServiceOptions.Role.PUBLISHER, receiver.address().getPort()));
                var http = HttpClient.newHttpClient()) {
            assertNotEquals(receiver.address(), publisher.address());
            assertTrue(ServiceApplication.healthy(receiver.address().getPort()));
            assertTrue(ServiceApplication.healthy(publisher.address().getPort()));
            var observed = http.send(
                    HttpRequest.newBuilder(publisher.address().resolve("/api/workbench/config"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, observed.statusCode());
            assertTrue(observed.body().contains("BOTH_REQUIRED"));
            assertFalse(Files.exists(root.resolve("receiver/keys")));
            assertFalse(Files.exists(root.resolve("receiver/account.key")));
            assertTrue(Files.isRegularFile(root.resolve("receiver/release-ledger.mv.db")));
            assertFalse(Files.exists(root.resolve("publisher/release-ledger.mv.db")));
        }
        assertThrows(IllegalArgumentException.class, () -> ServiceRuntime.start(init));
        assertThrows(
                IllegalArgumentException.class,
                () -> ServiceRuntime.initialize(options(ServiceOptions.Role.RECEIVER, 8782)));
    }

    /** Unknown options, duplicate values, remote targets and overlapping private/public storage fail closed. */
    @Test
    void validatesStrictRoleOptionsAndServiceHosts() throws Exception {
        assertEquals(
                8781, ServiceOptions.parse(new String[] {"--role=publisher"}).port());
        assertEquals(
                8782, ServiceOptions.parse(new String[] {"--role=receiver"}).port());
        assertEquals(
                "receiver",
                ServiceOptions.parse(new String[] {"--role=publisher", "--network=compose"})
                        .receiverEndpoint()
                        .getHost());
        for (String value : new String[] {
            "--role=wrong",
            "--network=remote",
            "--port=-1",
            "--port=65536",
            "--receiver-port=0",
            "--policy=WEAK",
            "--host=evil.example",
            "--role",
            "role=receiver"
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ServiceOptions.parse(new String[] {"--role=publisher", value}));
        }
        assertThrows(IllegalArgumentException.class, () -> ServiceOptions.parse(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceOptions(
                        ServiceOptions.Role.PUBLISHER,
                        false,
                        1,
                        2,
                        root,
                        root.resolve("public"),
                        null,
                        null,
                        MigrationPolicy.BOTH_REQUIRED));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceOptions(
                        ServiceOptions.Role.RECEIVER,
                        false,
                        0,
                        2,
                        root.resolve("receiver"),
                        root.resolve("public"),
                        root.resolve("secret"),
                        null,
                        MigrationPolicy.BOTH_REQUIRED));
        assertThrows(
                IllegalArgumentException.class,
                () -> new hu.balazsfalvi.pqclab.integration.app.http.LocalApiGuard(Set.of("external.example")));
        assertThrows(IllegalArgumentException.class, () -> ServiceApplication.healthy(0));
        assertFalse(ServiceApplication.healthy(1));
        assertDoesNotThrow(() -> ServiceApplication.main(new String[] {"--help"}));
    }
}
