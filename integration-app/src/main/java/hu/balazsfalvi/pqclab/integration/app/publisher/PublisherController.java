package hu.balazsfalvi.pqclab.integration.app.publisher;

import hu.balazsfalvi.pqclab.integration.app.http.BoundedBody;
import hu.balazsfalvi.pqclab.json.DsseJsonCodec;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes local synthetic signing and a complete publisher-to-receiver HTTP delivery. */
@RestController
public final class PublisherController {
    private final PublisherService publisher;
    private final ReceiverClient receiver;
    private final DsseJsonCodec envelopes = new DsseJsonCodec();
    private final PublishRequestCodec requests = new PublishRequestCodec();

    /**
     * Supplies the signing use case and trusted delivery target.
     *
     * @param publisher the publisher signing service
     * @param receiver the loopback receiver client
     */
    public PublisherController(PublisherService publisher, ReceiverClient receiver) {
        this.publisher = publisher;
        this.receiver = receiver;
    }

    /**
     * Builds an envelope for inspection, controlled tampering and manual delivery experiments.
     *
     * @param request the bounded synthetic release details
     * @return the DSSE JSON bytes
     */
    @PostMapping(
            path = "/api/publisher/envelopes",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public byte[] envelope(HttpServletRequest request) {
        return envelopes.write(publisher.envelope(requests.read(BoundedBody.read(request))));
    }

    /**
     * Signs and sends a release through actual HTTP, returning the receiver's acceptance decision.
     *
     * @param request the synthetic release request
     * @return the receiver's safe JSON response and status
     */
    @PostMapping(path = "/api/publisher/releases", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> deliver(HttpServletRequest request) {
        var delivery = receiver.deliver(envelope(request));
        MediaType type = delivery.status() >= 400 ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON;
        return ResponseEntity.status(delivery.status()).contentType(type).body(delivery.body());
    }
}
