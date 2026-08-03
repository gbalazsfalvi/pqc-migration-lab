package hu.balazsfalvi.pqclab.integration.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import hu.balazsfalvi.pqclab.integration.app.http.ApiErrors;
import hu.balazsfalvi.pqclab.integration.app.http.ApiFailure;
import hu.balazsfalvi.pqclab.integration.app.publisher.ReceiverClient;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Checks bounded upstream response handling and safe operational failure reporting. */
final class GatewayFailureTest {
    /** A receiver response cannot force the publisher to buffer beyond its configured wire limit. */
    @Test
    void rejectsOversizedUpstreamResponsesAndReleasesClientResources() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = new byte[1_048_577];
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        URI address = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        try (ReceiverClient client = new ReceiverClient(address)) {
            ApiFailure failure = assertThrows(ApiFailure.class, () -> client.deliver(new byte[] {1}));
            assertEquals(503, failure.status());
            assertEquals("RECEIVER_UNAVAILABLE", failure.code());
        } finally {
            server.stop(0);
        }
        try (ReceiverClient unavailable = new ReceiverClient(address)) {
            assertEquals(
                    503,
                    assertThrows(ApiFailure.class, () -> unavailable.deliver(new byte[] {1}))
                            .status());
        }
    }

    /** Interrupted delivery restores the caller's interrupt signal instead of silently consuming it. */
    @Test
    void preservesInterruptionAndMasksOperationalDetails() {
        try (ReceiverClient client = new ReceiverClient(URI.create("http://127.0.0.1:1/"))) {
            Thread.currentThread().interrupt();
            try {
                ApiFailure failure = assertThrows(ApiFailure.class, () -> client.deliver(new byte[] {1}));
                assertEquals("DELIVERY_INTERRUPTED", failure.code());
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
        var problem = new ApiErrors().unavailable(new IllegalStateException("SENSITIVE_TEST_MARKER"));
        assertEquals(503, problem.getStatus());
        assertFalse(problem.toString().contains("SENSITIVE_TEST_MARKER"));
    }

    /** A response that sends headers but stalls its body cannot retain the delivery thread indefinitely. */
    @Test
    void appliesTheDeadlineToTheCompleteResponseBody() throws Exception {
        CountDownLatch headers = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 1);
            headers.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (ReceiverClient client = new ReceiverClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofMillis(300))) {
            try {
                assertEquals(
                        "DELIVERY_TIMEOUT",
                        assertThrows(ApiFailure.class, () -> client.deliver(new byte[] {1}))
                                .code());
                assertTrue(headers.await(1, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        } finally {
            server.stop(0);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReceiverClient(URI.create("http://127.0.0.1:1"), Duration.ZERO));
    }
}
