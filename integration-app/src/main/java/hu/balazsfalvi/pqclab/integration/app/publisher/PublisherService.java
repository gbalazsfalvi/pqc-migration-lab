package hu.balazsfalvi.pqclab.integration.app.publisher;

import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.core.dsse.DsseSigner;
import hu.balazsfalvi.pqclab.integration.core.ReleaseEvent;
import hu.balazsfalvi.pqclab.integration.core.ReleaseEventCodec;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Publishes a synthetic event with receiver-owned identity claims supplied by trusted configuration. */
public final class PublisherService {
    private final SigningIdentity identity;
    private final ReleaseEventCodec codec;
    private final Clock clock;
    private final String audience;

    /**
     * Keeps identity, audience and publication time outside the caller's request body.
     *
     * @param identity the publisher's private signing ports
     * @param codec the business serializer
     * @param clock the publisher's clock
     * @param audience the configured receiver identity
     */
    public PublisherService(SigningIdentity identity, ReleaseEventCodec codec, Clock clock, String audience) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.audience = Objects.requireNonNull(audience, "audience");
    }

    /**
     * Creates and signs a new event; retries must resend this envelope rather than mint another ID.
     *
     * @param request the bounded release details and signing mode
     * @return the envelope containing the original serialized event bytes
     */
    public DsseEnvelope envelope(PublishRequest request) {
        ReleaseEvent event = new ReleaseEvent(
                1,
                UUID.randomUUID(),
                identity.issuer(),
                audience,
                clock.instant(),
                request.artifact(),
                request.version(),
                request.sha256());
        return new DsseSigner()
                .sign(
                        ReleaseEvent.PAYLOAD_TYPE,
                        codec.encode(event),
                        identity.signers(request.signingMode().algorithms()));
    }
}
