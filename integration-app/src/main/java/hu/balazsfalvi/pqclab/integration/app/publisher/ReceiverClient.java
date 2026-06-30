package hu.balazsfalvi.pqclab.integration.app.publisher;

import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import hu.balazsfalvi.pqclab.integration.app.http.ApiFailure;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Sends the exact signed envelope to the bootstrap-selected loopback receiver without redirects. */
public final class ReceiverClient implements AutoCloseable {
    private final URI receiver;
    private final HttpClient client;
    private final Duration timeout;

    /**
     * Binds the delivery adapter to a trusted receiver selected by the local bootstrap.
     *
     * @param receiver the complete loopback release endpoint
     */
    public ReceiverClient(URI receiver) {
        this(receiver, Duration.ofSeconds(5));
    }

    /**
     * Supplies a complete-response deadline, including body consumption.
     *
     * @param receiver the trusted loopback endpoint
     * @param timeout the positive deadline, at most thirty seconds
     */
    public ReceiverClient(URI receiver, Duration timeout) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Use a positive delivery deadline of at most thirty seconds.");
        }
        client = HttpClient.newBuilder()
                .connectTimeout(timeout.compareTo(Duration.ofSeconds(2)) < 0 ? timeout : Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Delivers an envelope and preserves the receiver's safe response and status.
     *
     * @param envelope the encoded signed envelope
     * @return the bounded receiver response
     */
    public Delivery deliver(byte[] envelope) {
        return post(receiver, envelope);
    }

    /**
     * Compares policies at the receiver's read-only endpoint.
     *
     * @param envelope the unchanged DSSE wire bytes
     * @return the actual receiver observations
     */
    public Delivery evaluate(byte[] envelope) {
        return post(receiver.resolve("/api/receiver/evaluations"), envelope);
    }

    /**
     * Checks the receiver's live trusted configuration.
     *
     * @return the bounded receiver status document
     */
    public Delivery status() {
        return exchange(HttpRequest.newBuilder(receiver.resolve("/api/receiver/status"))
                .timeout(timeout)
                .GET()
                .build());
    }

    /**
     * Reads a durable receipt through the trusted receiver route.
     *
     * @param eventId the typed event identifier
     * @return the original receiver receipt or safe not-found response
     */
    public Delivery receipt(UUID eventId) {
        return exchange(HttpRequest.newBuilder(receiver.resolve("/api/receiver/releases/" + eventId))
                .timeout(timeout)
                .GET()
                .build());
    }

    /**
     * Reads actual receiver-owned public key metadata through a fixed loopback route.
     *
     * @return the bounded public registry response
     */
    public Delivery keys() {
        return exchange(HttpRequest.newBuilder(receiver.resolve("/api/receiver/keys"))
                .timeout(timeout)
                .GET()
                .build());
    }

    /** Sends JSON to a fixed trusted route without accepting a caller-controlled URL. */
    private Delivery post(URI endpoint, byte[] envelope) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(envelope))
                .build();
        return exchange(request);
    }

    /** Bounds the complete upstream response for every gateway operation. */
    private Delivery exchange(HttpRequest request) {
        // Bound both allocation and the completion of the entire response, including a slow body.
        var pending = client.sendAsync(
                request,
                HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), DsseLimits.MAX_JSON_BYTES));
        try {
            var response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return new Delivery(response.statusCode(), response.body());
        } catch (TimeoutException failure) {
            pending.cancel(true);
            throw new ApiFailure(503, "DELIVERY_TIMEOUT");
        } catch (InterruptedException failure) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new ApiFailure(503, "DELIVERY_INTERRUPTED");
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof java.net.http.HttpTimeoutException) {
                throw new ApiFailure(503, "DELIVERY_TIMEOUT");
            }
            throw new ApiFailure(503, "RECEIVER_UNAVAILABLE");
        }
    }

    /** Releases the HTTP client's resources when the publisher context shuts down. */
    @Override
    public void close() {
        client.close();
    }

    /**
     * Represents a bounded transport response without exposing its body through default diagnostics.
     *
     * @param status the receiver's HTTP status
     * @param body the receiver's bounded JSON response
     */
    public record Delivery(int status, byte[] body) {
        /**
         * Snapshots the response before returning it to the controller.
         *
         * @param status the response status
         * @param body the response bytes
         */
        public Delivery {
            body = body.clone();
        }

        /**
         * Returns an independent response snapshot.
         *
         * @return the response bytes
         */
        @Override
        public byte[] body() {
            return body.clone();
        }
    }
}
