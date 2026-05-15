package hu.balazsfalvi.pqclab.integration.core;

/** Defines the business wire boundary independently of a JSON implementation. */
public interface ReleaseEventCodec {
    /**
     * Encodes an event once before signing its exact bytes.
     *
     * @param event the validated synthetic release
     * @return the original bytes to authenticate
     */
    byte[] encode(ReleaseEvent event);

    /**
     * Interprets only a verified payload snapshot.
     *
     * @param verifiedBody the exact bytes returned after cryptographic policy verification
     * @return the validated business event
     * @throws ReceptionFailure if the business schema is invalid
     */
    ReleaseEvent decode(byte[] verifiedBody);
}
