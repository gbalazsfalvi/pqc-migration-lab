import { useEffect, useRef, useState } from 'react';
import { Check, Copy, FileJson, Fingerprint, Layers } from 'lucide-react';
import type { Comparison, Envelope, Scenario } from '../domain/contracts';
import { abbreviate, algorithmLabels, displayPayload, formatBytes } from '../domain/presentation';

interface Props {
    envelope: Envelope | null;
    comparison: Comparison | null;
    scenario: Scenario | null;
}
type InspectorView = 'payload' | 'envelope' | 'signatures';

/** Copy explicit displayed content; payload previews are never passed off as the signed byte encoding. */
function CopyButton({ text, label }: { text: string; label: string }) {
    const [copied, setCopied] = useState(false);
    const [failed, setFailed] = useState(false);
    const timer = useRef<number | undefined>(undefined);
    useEffect(() => () => window.clearTimeout(timer.current), []);
    async function copy() {
        try {
            await navigator.clipboard.writeText(text);
            setCopied(true);
            setFailed(false);
            window.clearTimeout(timer.current);
            timer.current = window.setTimeout(() => setCopied(false), 1_500);
        } catch {
            setFailed(true);
        }
    }
    return (
        <button
            type="button"
            className="copy-button"
            onClick={() => void copy()}
            aria-label={label}
            title={failed ? 'Clipboard access was unavailable. Select the JSON to copy it.' : label}
        >
            {copied ? (
                <Check size={14} aria-hidden="true" />
            ) : (
                <Copy size={14} aria-hidden="true" />
            )}
            <span>{copied ? 'Copied' : failed ? 'Select to copy' : 'Copy'}</span>
        </button>
    );
}

/** Render JSON as escaped React text, keeping even untrusted payload content out of HTML parsing. */
function JsonCode({ text }: { text: string }) {
    return (
        <pre className="json-code" tabIndex={0}>
            <code>
                {text.split('\n').map((line, index) => (
                    <span className="code-line" key={index}>
                        <span className="line-number" aria-hidden="true">
                            {String(index + 1).padStart(2, '0')}
                        </span>
                        <span>{line}</span>
                    </span>
                ))}
            </code>
        </pre>
    );
}

/** Inspect public wire data and actual Java signature observations without verifying in the browser. */
export function EnvelopeViewer({ envelope, comparison, scenario }: Props) {
    const [view, setView] = useState<InspectorView>('payload');
    const payload = envelope ? displayPayload(envelope) : null;
    const text = envelope
        ? view === 'payload'
            ? (payload?.text ?? '')
            : JSON.stringify(envelope, null, 2)
        : '';
    return (
        <section className="panel envelope-panel" aria-labelledby="envelope-title">
            <div className="panel-heading">
                <div>
                    <div className="section-kicker">
                        <span>02</span> INSPECT THE BYTES
                    </div>
                    <h2 id="envelope-title">Signed envelope</h2>
                </div>
                <span className={'tag ' + (scenario ? 'tag-amber' : 'tag-neutral')}>
                    <Layers size={12} aria-hidden="true" />
                    {envelope
                        ? envelope.signatures.length +
                          ' signature' +
                          (envelope.signatures.length === 1 ? '' : 's')
                        : 'DSSE v1'}
                </span>
            </div>
            {envelope && payload ? (
                <>
                    <div className="inspector-toolbar">
                        <div className="view-toggle" aria-label="Envelope view">
                            {(['payload', 'envelope', 'signatures'] as const).map((item) => (
                                <button
                                    type="button"
                                    key={item}
                                    aria-pressed={view === item}
                                    onClick={() => setView(item)}
                                >
                                    {item === 'payload'
                                        ? 'Payload'
                                        : item === 'envelope'
                                          ? 'DSSE envelope'
                                          : 'Signatures'}
                                </button>
                            ))}
                        </div>
                        {view !== 'signatures' ? (
                            <CopyButton
                                text={text}
                                label={
                                    view === 'payload' ? 'Copy JSON preview' : 'Copy DSSE envelope'
                                }
                            />
                        ) : null}
                    </div>
                    <div className="inspector-body">
                        {view === 'signatures' ? (
                            <div className="signature-entries">
                                {comparison?.signatures.length ? (
                                    comparison.signatures.map((entry) => (
                                        <div className="signature-entry" key={entry.index}>
                                            <div className="signature-title">
                                                <strong>
                                                    {entry.verifiedAlgorithms.length
                                                        ? entry.verifiedAlgorithms
                                                              .map((id) => algorithmLabels[id])
                                                              .join(' + ')
                                                        : 'Unverified entry'}
                                                </strong>
                                                <span
                                                    className={
                                                        entry.verifiedAlgorithms.length
                                                            ? 'proof-valid'
                                                            : 'proof-invalid'
                                                    }
                                                >
                                                    {entry.verifiedAlgorithms.length
                                                        ? 'Trusted key verified'
                                                        : 'No trusted key verified'}
                                                </span>
                                            </div>
                                            <dl>
                                                <div>
                                                    <dt>Signature size</dt>
                                                    <dd>
                                                        {entry.bytes.toLocaleString('en-US')} bytes
                                                    </dd>
                                                </div>
                                                <div>
                                                    <dt>Key hint · unauthenticated</dt>
                                                    <dd>{entry.keyHint || 'Not supplied'}</dd>
                                                </div>
                                                <div>
                                                    <dt>Signature preview</dt>
                                                    <dd>
                                                        {abbreviate(
                                                            envelope.signatures[entry.index]?.sig ??
                                                                '',
                                                            24,
                                                            14,
                                                        )}
                                                    </dd>
                                                </div>
                                            </dl>
                                        </div>
                                    ))
                                ) : (
                                    <p>No signature entries are present in this envelope.</p>
                                )}
                            </div>
                        ) : (
                            <>
                                <div className="code-caption">
                                    <span>
                                        <FileJson size={13} aria-hidden="true" />
                                        {view === 'payload'
                                            ? 'release-event.json'
                                            : 'dsse-envelope.json'}
                                    </span>
                                    <span>READ ONLY</span>
                                </div>
                                <JsonCode text={text} />
                            </>
                        )}
                    </div>
                    <div className="byte-measurements">
                        <span>
                            PAYLOAD{' '}
                            <strong>
                                {comparison ? formatBytes(comparison.payloadBytes) : '—'}
                            </strong>
                        </span>
                        <span>
                            PAE FRAME{' '}
                            <strong>{comparison ? formatBytes(comparison.paeBytes) : '—'}</strong>
                        </span>
                        <span>
                            WIRE JSON{' '}
                            <strong>
                                {comparison ? formatBytes(comparison.envelopeBytes) : '—'}
                            </strong>
                        </span>
                    </div>
                    <div className="payload-fingerprint" title={comparison?.payloadSha256}>
                        <Fingerprint size={14} aria-hidden="true" />
                        <span>SHA-256</span>
                        <code>{abbreviate(comparison?.payloadSha256 ?? '', 16, 12)}</code>
                    </div>
                    <p className="inspector-footnote">
                        Decoded preview only. Signatures cover the original payload bytes.
                    </p>
                </>
            ) : (
                <div className="empty-envelope">
                    <div className="paper-symbol" aria-hidden="true">
                        <FileJson size={29} strokeWidth={1.2} />
                        <span />
                    </div>
                    <h3>Every byte has a role.</h3>
                    <p>
                        Create a signed event to inspect its payload,
                        <br />
                        signatures and authenticated framing.
                    </p>
                    <span className="empty-code">DSSEv1 · type · length · original bytes</span>
                </div>
            )}
        </section>
    );
}
