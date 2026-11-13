package hu.balazsfalvi.pqclab.integration.app.receiver;

import hu.balazsfalvi.pqclab.integration.app.http.ApiFailure;
import hu.balazsfalvi.pqclab.integration.app.http.BoundedBody;
import hu.balazsfalvi.pqclab.integration.app.runtime.ReceiverProperties;
import hu.balazsfalvi.pqclab.integration.core.ReceptionService;
import hu.balazsfalvi.pqclab.integration.core.ReleaseLedger;
import hu.balazsfalvi.pqclab.json.DsseJsonCodec;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Receives authenticated releases and exposes only persisted synthetic business receipts. */
@RestController
public final class ReceiverController {
    private final ReceptionService reception;
    private final ReleaseLedger ledger;
    private final ReceiverProperties settings;
    private final DsseJsonCodec envelopes = new DsseJsonCodec();

    /**
     * Supplies the receive use case, durable query port and trusted receiver configuration.
     *
     * @param reception the verification and business application service
     * @param ledger the persisted release query port
     * @param settings the receiver-owned policy and identity
     */
    public ReceiverController(ReceptionService reception, ReleaseLedger ledger, ReceiverProperties settings) {
        this.reception = reception;
        this.ledger = ledger;
        this.settings = settings;
    }

    /**
     * Applies a release once and returns the original receipt for an identical valid retry.
     *
     * @param request the bounded DSSE envelope
     * @return a new or unchanged durable business receipt
     */
    @PostMapping(
            path = "/api/receiver/releases",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReceiptResponse> receive(HttpServletRequest request) {
        ReleaseLedger.Outcome outcome = reception.receive(envelopes.read(BoundedBody.read(request)));
        String status = outcome.created() ? "ACCEPTED" : "ALREADY_ACCEPTED";
        return ResponseEntity.status(outcome.created() ? 201 : 200)
                .body(ReceiptResponse.of(status, outcome.release(), settings.policy()));
    }

    /**
     * Reads a durable receipt in the configured publisher's namespace.
     *
     * @param eventId the canonical event identifier
     * @return the original acceptance evidence
     */
    @GetMapping(path = "/api/receiver/releases/{eventId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReceiptResponse receipt(@PathVariable UUID eventId) {
        return ledger.find(settings.issuer(), eventId)
                .map(release -> ReceiptResponse.of("STORED", release, settings.policy()))
                .orElseThrow(() -> new ApiFailure(404, "RELEASE_NOT_FOUND"));
    }

    /**
     * Reports receiver-owned requirements without exposing keys or a policy mutation endpoint.
     *
     * @return the trusted configuration summary
     */
    @GetMapping(path = "/api/receiver/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReceiverProperties status() {
        return settings;
    }
}
