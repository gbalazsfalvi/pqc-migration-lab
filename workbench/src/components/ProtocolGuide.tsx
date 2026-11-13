import { ArrowUpRight, FileCheck2, KeyRound, Repeat2 } from 'lucide-react';
import type { LabConfig } from '../domain/contracts';

/** Keep the protocol's security boundaries beside the experiment, using plain English and exact framing. */
export function ProtocolGuide({ config }: { config: LabConfig | null }) {
    return (
        <article className="protocol-guide" aria-labelledby="guide-title">
            <div className="section-kicker">
                <span>FIELD NOTES</span> THE PROTOCOL
            </div>
            <h1 id="guide-title">The contract behind the interface.</h1>
            <p className="page-intro">
                What is signed, what is trusted, and when an event becomes a business result.
            </p>
            <section className="guide-feature">
                <span className="guide-number">01</span>
                <div>
                    <h2>Authenticate the original bytes.</h2>
                    <p>
                        DSSE binds a payload type and an opaque byte sequence into one
                        pre-authentication frame. Lengths count UTF-8 bytes. The outer JSON layout
                        is outside the signature.
                    </p>
                    <div className="pae-example">
                        <span>PRE-AUTHENTICATION ENCODING</span>
                        <code>DSSEv1 1 t 3 abc</code>
                        <small>type = “t” · body = “abc” · no extra terminator</small>
                    </div>
                    <p>
                        The payload pane is a decoded preview. The Java verifier consumes the
                        original bytes carried in the envelope, and passes that verified snapshot to
                        business validation.
                    </p>
                </div>
            </section>
            <div className="guide-columns">
                <section className="panel guide-card">
                    <KeyRound size={23} strokeWidth={1.4} aria-hidden="true" />
                    <h2>Trust is configured.</h2>
                    <p>
                        The receiver knows public keys and their publisher owner before a request
                        arrives.
                    </p>
                    <ul>
                        <li>Private keys remain in the Java publisher.</li>
                        <li>Envelope key hints are unauthenticated.</li>
                        <li>Both-required needs both algorithms from the same trusted owner.</li>
                        <li>Policy comparison cannot change the live receiver policy.</li>
                    </ul>
                </section>
                <section className="panel guide-card">
                    <FileCheck2 size={23} strokeWidth={1.4} aria-hidden="true" />
                    <h2>Claims are signed.</h2>
                    <p>
                        The authenticated event includes its identifier, issuer, audience, time and
                        release details.
                    </p>
                    <ul>
                        <li>The issuer must match the key owner.</li>
                        <li>The audience must match this receiver.</li>
                        <li>
                            Maximum age:{' '}
                            {config ? config.maximumAgeSeconds + ' seconds' : 'receiver-configured'}
                            .
                        </li>
                        <li>
                            Future tolerance:{' '}
                            {config
                                ? config.futureToleranceSeconds + ' seconds'
                                : 'receiver-configured'}
                            .
                        </li>
                    </ul>
                </section>
            </div>
            <section className="guide-feature">
                <span className="guide-number">
                    <Repeat2 size={20} aria-hidden="true" />
                </span>
                <div>
                    <h2>A valid signature does not prevent a replay.</h2>
                    <p>
                        The durable ledger uses the authenticated issuer and event ID as a unique
                        key. One transaction stores the release and reserves its identifier.
                    </p>
                    <div className="http-legend">
                        <span>
                            <b>201</b>New result committed
                        </span>
                        <span>
                            <b>200</b>Exact retry suppressed
                        </span>
                        <span>
                            <b>409</b>Conflicting bytes rejected
                        </span>
                    </div>
                    <p>
                        Every retry is reverified under the current policy and freshness window. A
                        receipt accepted under a weaker policy cannot bypass a later migration
                        requirement.
                    </p>
                </div>
            </section>
            <aside className="guide-boundary">
                <strong>Laboratory scope</strong>
                <p>
                    This local HTTP integration demonstrates integrity and configured publisher
                    authentication. It does not encrypt the payload or provide post-quantum TLS.
                    Managed signing keys survive restarts in an encrypted local vault. Rotation
                    gives older public keys a bounded overlap; revocation applies to every retry. A
                    separate JMH module measures adapter and envelope costs.
                </p>
            </aside>
            <div className="guide-sources">
                <a
                    href="https://github.com/dsse-lf/dsse/blob/master/protocol.md"
                    target="_blank"
                    rel="noreferrer"
                >
                    DSSE protocol
                    <ArrowUpRight size={13} aria-hidden="true" />
                </a>
                <a
                    href="https://csrc.nist.gov/pubs/fips/204/final"
                    target="_blank"
                    rel="noreferrer"
                >
                    FIPS 204 · ML-DSA
                    <ArrowUpRight size={13} aria-hidden="true" />
                </a>
            </div>
        </article>
    );
}
