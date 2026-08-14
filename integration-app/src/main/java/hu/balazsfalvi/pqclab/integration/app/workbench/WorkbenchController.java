package hu.balazsfalvi.pqclab.integration.app.workbench;

import hu.balazsfalvi.pqclab.core.dsse.DsseLimits;
import hu.balazsfalvi.pqclab.integration.app.http.BoundedBody;
import hu.balazsfalvi.pqclab.integration.app.publisher.ReceiverClient;
import hu.balazsfalvi.pqclab.integration.app.runtime.ReceiverProperties;
import hu.balazsfalvi.pqclab.integration.app.wire.StrictJson;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gives the browser a same-origin gateway with fixed receiver routes and controlled local fixtures. */
@RestController
public final class WorkbenchController {
    private final ReceiverClient receiver;
    private final ExperimentFixtures fixtures;
    private final ReceiverProperties settings;
    private final StrictJson json = new StrictJson(DsseLimits.MAX_JSON_BYTES);

    /**
     * Supplies trusted routing and publisher-only fixture capabilities.
     *
     * @param receiver the fixed loopback receiver adapter
     * @param fixtures the controlled synthetic fault builder
     * @param settings the shared local receiver requirements
     */
    public WorkbenchController(ReceiverClient receiver, ExperimentFixtures fixtures, ReceiverProperties settings) {
        this.receiver = receiver;
        this.fixtures = fixtures;
        this.settings = settings;
    }

    /**
     * Confirms actual receiver connectivity before reporting laboratory configuration.
     *
     * @return observed receiver identity and public runtime metadata
     */
    @GetMapping(path = "/api/workbench/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> config() {
        var response = receiver.status();
        if (response.status() != 200) {
            return forward(response);
        }
        var status =
                json.read(response.body(), Set.of("policy", "issuer", "audience", "maximumAge", "futureTolerance"));
        var root = json.object();
        root.put("javaVersion", System.getProperty("java.runtime.version"));
        root.put("protocol", "DSSEv1");
        root.put("receiverPolicy", json.text(status, "policy"));
        root.put("issuer", json.text(status, "issuer"));
        root.put("audience", json.text(status, "audience"));
        root.put("maximumAgeSeconds", settings.maximumAge().getSeconds());
        root.put("futureToleranceSeconds", settings.futureTolerance().getSeconds());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.write(root));
    }

    /**
     * Forwards only to the receiver's ledger-free evaluation endpoint.
     *
     * @param request the unchanged bounded DSSE envelope
     * @return observed Java policy decisions
     */
    @PostMapping(path = "/api/workbench/evaluations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> evaluate(HttpServletRequest request) {
        return forward(receiver.evaluate(BoundedBody.read(request)));
    }

    /**
     * Sends an existing envelope under the unchanged live receiver policy.
     *
     * @param request the exact envelope to deliver or retry
     * @return the receiver's actual receipt or rejection
     */
    @PostMapping(path = "/api/workbench/deliveries", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> deliver(HttpServletRequest request) {
        return forward(receiver.deliver(BoundedBody.read(request)));
    }

    /**
     * Reads an existing durable receipt without exposing a caller-controlled upstream URL.
     *
     * @param eventId the typed signed event identifier
     * @return the receiver's stored result or not-found problem
     */
    @GetMapping("/api/workbench/receipts/{eventId}")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID eventId) {
        return forward(receiver.receipt(eventId));
    }

    /**
     * Builds a local fixture without performing a delivery or changing receiver trust.
     *
     * @param request the strict scenario and original baseline envelope
     * @return the controlled modified envelope
     */
    @PostMapping(
            path = "/api/workbench/experiments",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public byte[] experiment(HttpServletRequest request) {
        return fixtures.create(BoundedBody.read(request));
    }

    /**
     * Reads public key metadata from the actual receiver rather than a browser-side simulation.
     *
     * @return the current receiver trust revision and public generations
     */
    @GetMapping(path = "/api/workbench/keys", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> keys() {
        return forward(receiver.keys());
    }

    /** Preserves actual HTTP outcomes and safe problem documents across the same-origin gateway. */
    private ResponseEntity<byte[]> forward(ReceiverClient.Delivery response) {
        return ResponseEntity.status(response.status())
                .contentType(response.status() >= 400 ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON)
                .body(response.body());
    }
}
