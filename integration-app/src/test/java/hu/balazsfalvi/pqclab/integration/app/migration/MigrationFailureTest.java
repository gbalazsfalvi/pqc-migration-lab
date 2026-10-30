package hu.balazsfalvi.pqclab.integration.app.migration;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ensures failed, unsafe and interrupted observations produce honest partial evidence without leaking exception data. */
final class MigrationFailureTest {
    private static final String CONFIG =
            "{\"policy\":\"BOTH_REQUIRED\",\"issuer\":\"release-publisher\",\"audience\":\"urn:pqclab:release-receiver\","
                    + "\"maximumAge\":\"PT5M\",\"futureTolerance\":\"PT30S\"}";

    @TempDir
    Path root;

    /** An unexpected real HTTP status stops dependent mutations and records a failed, incomplete story. */
    @Test
    void stopsOnAnUnexpectedStatusAndNeverOverwritesEarlierRuns() throws Exception {
        var attempts = new AtomicInteger();
        try (var server = new Stub(503, "{\"status\":503,\"code\":\"INTEGRATION_UNAVAILABLE\"}", attempts)) {
            var demo = observed(
                    server,
                    (http, journal) ->
                            http.exchange("unavailable-receiver", MigrationTransport.Route.STATUS, 200, new byte[0]));
            var first = demo.execute(root);
            String original = Files.readString(first.directory().resolve("report.json"));
            var second = demo.execute(root);
            assertNotEquals(first.directory(), second.directory());
            assertEquals(original, Files.readString(first.directory().resolve("report.json")));
            assertFalse(first.report().successful());
            assertFalse(first.report().scenarioCompleted());
            assertEquals(MigrationReport.Outcome.FAIL, first.report().outcome());
            assertEquals("EXPECTATION_FAILED", first.report().failureCode());
            assertEquals(503, first.report().exchanges().getFirst().actualStatus());
            assertFalse(first.report().checks().getFirst().passed());
            assertEquals(2, attempts.get());
            assertTrue(Files.readString(first.directory().resolve("report.md")).contains("**FAIL**"));
        }
    }

    /** A successful transport does not conceal a contradictory semantic observation. */
    @Test
    void treatsAContradictoryPolicyAsAFailureEvenAfterHttp200() throws Exception {
        try (var server = new Stub(200, CONFIG, new AtomicInteger())) {
            var result = observed(server, (http, journal) -> {
                        var reply = http.exchange("observed-status", MigrationTransport.Route.STATUS, 200, new byte[0]);
                        journal.expect(
                                "wrong-policy",
                                "A deliberate semantic mismatch must fail.",
                                "PQC_REQUIRED",
                                reply.json().required("policy").textValue(),
                                reply.sequence());
                    })
                    .execute(root);
            assertEquals(MigrationReport.Outcome.FAIL, result.report().outcome());
            assertTrue(result.report().checks().getFirst().passed());
            assertFalse(result.report().checks().getLast().passed());
        }
    }

    /** Unexpected private fields are rejected before any raw response bytes enter the shareable bundle. */
    @Test
    void refusesToExportUnexpectedPrivateFields() throws Exception {
        String marker = "DO_NOT_EXPORT_THIS_RESPONSE";
        try (var server = new Stub(200, "{\"privateKey\":\"" + marker + "\"}", new AtomicInteger())) {
            var result = observed(
                            server,
                            (http, journal) ->
                                    http.exchange("unsafe-response", MigrationTransport.Route.STATUS, 200, new byte[0]))
                    .execute(root);
            assertEquals(MigrationReport.Outcome.INCOMPLETE, result.report().outcome());
            assertEquals("PUBLIC_RESPONSE_REJECTED", result.report().failureCode());
            var exchange = result.report().exchanges().getFirst();
            assertEquals(200, exchange.actualStatus());
            assertNull(exchange.responseFile());
            assertNotNull(exchange.responseSha256());
            assertFalse(
                    Files.readString(result.directory().resolve("report.json")).contains(marker));
            try (var files = Files.list(result.directory().resolve("http"))) {
                assertEquals(0, files.count());
            }
            assertTrue(Files.exists(result.directory().resolve("SHA256SUMS")));
        }
    }

    /** A closed connection has no fabricated HTTP status, response body or passing assertion. */
    @Test
    void recordsNoResponseWhenThePeerClosesBeforeHeaders() throws Exception {
        try (var server = new Stub(0, "", new AtomicInteger())) {
            var result = observed(
                            server,
                            (http, journal) -> http.exchange(
                                    "closed-connection", MigrationTransport.Route.STATUS, 200, new byte[0]))
                    .execute(root);
            assertEquals(MigrationReport.Outcome.INCOMPLETE, result.report().outcome());
            assertEquals("HTTP_TRANSPORT_FAILED", result.report().failureCode());
            assertNull(result.report().exchanges().getFirst().actualStatus());
            assertNull(result.report().exchanges().getFirst().responseSha256());
            assertTrue(result.report().checks().isEmpty());
            assertTrue(Files.readString(result.directory().resolve("report.md")).contains("NO RESPONSE"));
        }
    }

    /** Startup and storage exceptions are categorized without serializing paths or arbitrary messages. */
    @Test
    void recordsOperationalFailureAndCleanupFailureWithoutFalsePasses() throws Exception {
        String marker = "DO_NOT_EXPORT_EXCEPTION_OR_PATH";
        var failed = new MigrationDemo((workspace, journal) -> {
                    throw new IOException(marker);
                })
                .execute(root);
        assertEquals("EXECUTION_FAILED", failed.report().failureCode());
        assertFalse(Files.readString(failed.directory().resolve("report.json")).contains(marker));
        var empty = new MigrationDemo((workspace, journal) -> {}).execute(root);
        assertEquals("NO_OBSERVATIONS", empty.report().failureCode());
        assertFalse(empty.report().successful());
        var cleanup = new MigrationDemo((workspace, journal) -> {
                    journal.expect("before-cleanup", "Cleanup must still complete.", true, true);
                    // This test owns the freshly created, empty temporary directory.
                    Files.delete(workspace.secretFile().getParent());
                })
                .execute(root);
        assertEquals(MigrationReport.Outcome.INCOMPLETE, cleanup.report().outcome());
        assertEquals("EXECUTION_FAILED", cleanup.report().failureCode());
        assertFalse(cleanup.report().scenarioCompleted());
    }

    /** Interruption still produces bounded local evidence and restores the calling thread's interrupt flag. */
    @Test
    void recordsInterruptionAndRestoresInterruptStatus() throws Exception {
        try {
            var result = new MigrationDemo((workspace, journal) -> {
                        throw new InterruptedException("not exported");
                    })
                    .execute(root);
            assertEquals("INTERRUPTED", result.report().failureCode());
            assertEquals(MigrationReport.Outcome.INCOMPLETE, result.report().outcome());
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(Files.exists(result.directory().resolve("SHA256SUMS")));
        } finally {
            Thread.interrupted();
        }
    }

    /** Fixed direct-only origins reject remote hosts, credentials, alternate paths and query syntax. */
    @Test
    void rejectsNonBootstrapOriginsBeforeAnyRequest() throws Exception {
        Path directory = Files.createDirectory(root.resolve("origin-test"));
        var journal = new MigrationJournal(directory);
        URI local = URI.create("http://127.0.0.1:1");
        for (String value : new String[] {
            "https://127.0.0.1:1",
            "http://example.com:1",
            "http://127.0.0.1",
            "http://user@127.0.0.1:1",
            "http://127.0.0.1:1/path",
            "http://127.0.0.1:1/?q=value",
            "http://127.0.0.1:1/#fragment",
            "http://127.0.0.1:65536"
        }) {
            assertThrows(
                    IllegalArgumentException.class, () -> new MigrationTransport(URI.create(value), local, journal, 1));
        }
        try (var transport = new MigrationTransport(local, local, journal, 1)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> transport.exchange("bad-receipt", MigrationTransport.Route.RECEIPT, 200, new byte[0]));
        }
    }

    /** Wraps a real controlled loopback server inside the execution boundary being tested. */
    private MigrationDemo observed(Stub stub, Observations observations) {
        return new MigrationDemo((workspace, journal) -> {
            try (var http = new MigrationTransport(stub.uri(), stub.uri(), journal, 1)) {
                observations.run(http, journal);
            }
        });
    }

    /** Supplies negative-test expectations without substituting the transport implementation. */
    @FunctionalInterface
    private interface Observations {
        /** Performs actual loopback requests and their deliberately controlled assertions. */
        void run(MigrationTransport http, MigrationJournal journal) throws IOException, InterruptedException;
    }

    /** Owns one small HTTP peer; zero status deliberately closes before any response headers. */
    private static final class Stub implements AutoCloseable {
        private final HttpServer server;

        /** Configures a real peer and a non-exported response header for transport evidence tests. */
        Stub(int status, String body, AtomicInteger attempts) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                attempts.incrementAndGet();
                try (exchange) {
                    exchange.getRequestBody().readAllBytes();
                    if (status == 0) return;
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("X-Not-Public", "DO_NOT_EXPORT_HEADER");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
        }

        /** @return the actual bound numeric loopback origin */
        URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        /** Stops the owned peer after every negative test. */
        @Override
        public void close() {
            server.stop(0);
        }
    }
}
