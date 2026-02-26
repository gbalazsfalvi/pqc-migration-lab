package hu.balazsfalvi.pqclab.core.dsse;

import hu.balazsfalvi.pqclab.core.crypto.MessageSigner;
import java.util.List;
import java.util.Objects;

/** Applies one or more independent signatures to the same DSSE pre-authentication bytes. */
public final class DsseSigner {
    private final PaeEncoder encoder;

    /** Creates a signer with the standard DSSE v1 encoder. */
    public DsseSigner() {
        this.encoder = new PaeEncoder();
    }

    /**
     * Signs a snapshot of the payload with every supplied signer.
     *
     * @param payloadType the authenticated application-specific type
     * @param body the original payload bytes
     * @param signers the explicitly selected signing identities
     * @return an immutable multi-signature envelope
     * @throws IllegalArgumentException if no signer is supplied or the lab limit is exceeded
     */
    public DsseEnvelope sign(String payloadType, byte[] body, List<? extends MessageSigner> signers) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(signers, "signers");
        List<? extends MessageSigner> snapshot = List.copyOf(signers);
        if (snapshot.isEmpty() || snapshot.size() > DsseLimits.MAX_SIGNATURES) {
            throw new IllegalArgumentException("Supply between one and four signing identities.");
        }
        byte[] original = body.clone();
        byte[] framed = encoder.encode(payloadType, original);
        var signatures = snapshot.stream()
                .map(signer -> new DsseSignature(signer.keyId(), DsseBase64.encode(signer.sign(framed.clone()))))
                .toList();
        return new DsseEnvelope(payloadType, DsseBase64.encode(original), signatures);
    }
}
