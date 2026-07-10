package hu.balazsfalvi.pqclab.integration.app.receiver;

import hu.balazsfalvi.pqclab.integration.app.http.BoundedBody;
import hu.balazsfalvi.pqclab.json.DsseJsonCodec;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes policy comparison separately from durable delivery and its fixed live requirement. */
@RestController
public final class EvaluationController {
    private final EnvelopeInspector inspector;
    private final DsseJsonCodec envelopes = new DsseJsonCodec();

    /**
     * Supplies a public-only, read-only inspection service.
     *
     * @param inspector the cryptographic and validation observer
     */
    public EvaluationController(EnvelopeInspector inspector) {
        this.inspector = inspector;
    }

    /**
     * Compares policies without accepting a caller-selected live policy or creating a receipt.
     *
     * @param request the raw bounded DSSE envelope
     * @return real Java observations for every fixed migration policy
     */
    @PostMapping(
            path = "/api/receiver/evaluations",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ComparisonResponse evaluate(HttpServletRequest request) {
        byte[] wire = BoundedBody.read(request);
        return inspector.inspect(envelopes.read(wire), wire.length);
    }
}
