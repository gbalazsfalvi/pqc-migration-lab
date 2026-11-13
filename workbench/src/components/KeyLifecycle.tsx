import { useEffect, useRef, useState } from 'react';
import {
    ArrowRight,
    Ban,
    Check,
    Download,
    KeyRound,
    LockKeyhole,
    RefreshCw,
    ShieldCheck,
    X,
} from 'lucide-react';
import { useKeyRegistry } from '../hooks/useKeyRegistry';
import type { KeyGeneration } from '../domain/contracts';
import { algorithmLabels, downloadJson, formatBytes } from '../domain/presentation';

const states = {
    ACTIVE: {
        label: 'Active',
        className: 'tag-green',
        detail: 'Used for new signatures and trusted by the receiver.',
    },
    RETIRING: {
        label: 'Retiring',
        className: 'tag-amber',
        detail: 'Existing signatures remain trusted during the overlap.',
    },
    RETIRED: {
        label: 'Retired',
        className: 'tag-neutral',
        detail: 'The overlap has ended. This generation is no longer trusted.',
    },
    REVOKED: {
        label: 'Revoked',
        className: 'tag-red',
        detail: 'The receiver rejects this generation, including previously accepted retries.',
    },
} as const;

/** Present actual public key state and explicit controls, keeping private material entirely in Java. */
export function KeyLifecycle({ onTrustChanged }: { onTrustChanged: () => Promise<void> }) {
    const keys = useKeyRegistry(onTrustChanged);
    const [selected, setSelected] = useState<KeyGeneration | null>(null);
    const registry = keys.registry;
    const current = registry?.generations.find(
        (generation) => generation.id === registry.activeGenerationId,
    );
    const blocked = current?.state === 'REVOKED';
    const limit = registry ? registry.generations.length >= registry.maximumGenerations : false;
    return (
        <section className="key-lifecycle" aria-labelledby="key-lifecycle-title">
            <div className="page-heading">
                <div>
                    <div className="eyebrow">TRUST, WITH A MEMORY</div>
                    <h1 id="key-lifecycle-title">
                        Key lifecycle<span>.</span>
                    </h1>
                    <p className="page-intro">One publisher. Versioned keys. Explicit trust.</p>
                </div>
                <div className="page-actions">
                    <button
                        className="icon-button"
                        type="button"
                        onClick={() => void keys.refresh()}
                        disabled={keys.pending !== null}
                        aria-label="Refresh key registry"
                    >
                        <RefreshCw
                            size={16}
                            className={keys.pending === 'refresh' ? 'spin' : ''}
                            aria-hidden="true"
                        />
                    </button>
                    <button
                        className="button secondary"
                        type="button"
                        disabled={!registry || keys.pending !== null}
                        onClick={() =>
                            registry &&
                            downloadJson(
                                'pqc-public-key-registry-r' + registry.revision + '.json',
                                registry,
                            )
                        }
                    >
                        <Download size={14} aria-hidden="true" /> Export public registry
                    </button>
                </div>
            </div>

            {keys.error ? (
                <div className="notice notice-error" role="alert">
                    <Ban size={17} aria-hidden="true" />
                    <span>{keys.error}</span>
                </div>
            ) : null}
            {keys.notice ? (
                <div className="key-success" role="status">
                    <Check size={17} aria-hidden="true" />
                    <span>{keys.notice}</span>
                </div>
            ) : null}
            {!registry ? (
                <div className="panel key-loading" role="status">
                    <KeyRound size={24} strokeWidth={1.3} aria-hidden="true" />
                    <h2>
                        {keys.pending ? 'Reading receiver trust…' : 'The registry is unavailable.'}
                    </h2>
                    <p>Public identities appear when the Java receiver responds.</p>
                </div>
            ) : !registry.managed ? (
                <div className="panel key-loading">
                    <LockKeyhole size={25} aria-hidden="true" />
                    <h2>Ephemeral probe runtime.</h2>
                    <p>
                        This deterministic test runtime has no durable key controls. Start the
                        normal local application to use managed keys.
                    </p>
                </div>
            ) : (
                <>
                    <div className="key-method">
                        <div>
                            <span>01</span>
                            <strong>Durable identity</strong>
                            <p>The same public fingerprints survive a full restart.</p>
                        </div>
                        <div>
                            <span>02</span>
                            <strong>{registry.gracePeriodSeconds / 60}-minute overlap</strong>
                            <p>Retiring generations keep a bounded verification window.</p>
                        </div>
                        <div>
                            <span>03</span>
                            <strong>Explicit revocation</strong>
                            <p>Every delivery is checked against current trust.</p>
                        </div>
                    </div>
                    <div
                        className={'key-control-panel panel ' + (blocked ? 'signing-blocked' : '')}
                    >
                        <div className="key-control-icon">
                            <ShieldCheck size={24} strokeWidth={1.35} aria-hidden="true" />
                        </div>
                        <div className="key-control-copy">
                            <div className="section-kicker">PUBLISHER CONTROL</div>
                            <h2>{blocked ? 'Signing is paused.' : 'Move both keys together.'}</h2>
                            <p>
                                {blocked
                                    ? 'The selected generation is revoked. Create a new generation to resume signing.'
                                    : 'Rotate Ed25519 and ML-DSA-65 as one generation. The receiver policy stays unchanged.'}
                            </p>
                            {limit ? (
                                <p className="key-capacity">
                                    The bounded registry has reached its{' '}
                                    {registry.maximumGenerations}-generation limit.
                                </p>
                            ) : null}
                        </div>
                        <button
                            className="button primary"
                            type="button"
                            disabled={keys.pending !== null || limit}
                            onClick={() => void keys.rotate()}
                        >
                            <RefreshCw size={14} aria-hidden="true" />
                            {keys.pending === 'rotate' ? 'Rotating…' : 'Rotate signing keys'}
                        </button>
                    </div>
                    <div className="key-registry-heading">
                        <div>
                            <div className="section-kicker">RECEIVER-OWNED TRUST</div>
                            <h2>Public key registry</h2>
                        </div>
                        <div className="key-observation">
                            <span>
                                Revision <b>{registry.revision}</b>
                            </span>
                            <span>{dateTime(registry.observedAt)}</span>
                        </div>
                    </div>
                    <div className="key-generation-list">
                        {registry.generations.map((generation) => {
                            const state = states[generation.state];
                            const isSelected = generation.id === registry.activeGenerationId;
                            return (
                                <article
                                    className="panel key-generation"
                                    key={generation.id}
                                    aria-label={'Generation ' + generation.sequence}
                                >
                                    <div className="key-generation-heading">
                                        <div className="key-generation-number">
                                            {String(generation.sequence).padStart(2, '0')}
                                        </div>
                                        <div className="key-generation-title">
                                            <h3>
                                                Generation{' '}
                                                {String(generation.sequence).padStart(2, '0')}
                                            </h3>
                                            <code>{generation.id}</code>
                                        </div>
                                        <div className="key-generation-status">
                                            {isSelected ? (
                                                <span className="key-selected-label">
                                                    {blocked
                                                        ? 'Selected generation'
                                                        : 'New signatures'}
                                                </span>
                                            ) : null}
                                            <span className={'tag ' + state.className}>
                                                {state.label}
                                            </span>
                                        </div>
                                    </div>
                                    <p className="key-state-detail">{state.detail}</p>
                                    <div className="key-metadata">
                                        <span>
                                            Created <b>{dateTime(generation.createdAt)}</b>
                                        </span>
                                        {generation.acceptUntil ? (
                                            <span>
                                                Overlap deadline{' '}
                                                <b>{dateTime(generation.acceptUntil)}</b>
                                            </span>
                                        ) : null}
                                        {generation.revokedAt ? (
                                            <span>
                                                Revoked <b>{dateTime(generation.revokedAt)}</b>
                                            </span>
                                        ) : null}
                                    </div>
                                    <div className="public-key-rows">
                                        {generation.keys.map((key) => (
                                            <div className="public-key-row" key={key.id}>
                                                <div className="public-key-algorithm">
                                                    <KeyRound
                                                        size={16}
                                                        strokeWidth={1.4}
                                                        aria-hidden="true"
                                                    />
                                                    <div>
                                                        <strong>
                                                            {algorithmLabels[key.algorithm]}
                                                        </strong>
                                                        <small>
                                                            {formatBytes(key.spkiBytes)} SPKI
                                                        </small>
                                                    </div>
                                                </div>
                                                <div className="public-key-identity">
                                                    <span>CONFIGURED KEY ID</span>
                                                    <code>{key.id}</code>
                                                </div>
                                                <div className="public-key-fingerprint">
                                                    <span>SPKI SHA-256</span>
                                                    <code>{key.spkiSha256}</code>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                    <div className="key-generation-footer">
                                        <span>
                                            <LockKeyhole size={12} aria-hidden="true" />
                                            {isSelected && !blocked
                                                ? 'Private signing material stays with the publisher.'
                                                : 'Only public verification material is retained.'}
                                        </span>
                                        {generation.state === 'REVOKED' ? (
                                            <span className="key-revocation-final">
                                                Revocation is final
                                            </span>
                                        ) : (
                                            <button
                                                className="key-revoke-button"
                                                type="button"
                                                disabled={keys.pending !== null}
                                                aria-label={
                                                    'Revoke generation ' + generation.sequence
                                                }
                                                onClick={() => setSelected(generation)}
                                            >
                                                Revoke generation{' '}
                                                <ArrowRight size={13} aria-hidden="true" />
                                            </button>
                                        )}
                                    </div>
                                </article>
                            );
                        })}
                    </div>
                    <aside className="key-boundary">
                        <LockKeyhole size={17} aria-hidden="true" />
                        <p>
                            <strong>Public evidence only.</strong> This view exposes identifiers and
                            public fingerprints. The publisher stores active private keys in an
                            encrypted local vault. An account-owned unlock file stays outside the
                            integration data directory. Wire key IDs remain unauthenticated hints.
                        </p>
                    </aside>
                </>
            )}
            <RevocationDialog
                generation={selected}
                active={selected?.id === registry?.activeGenerationId}
                pending={keys.pending === 'revoke'}
                onClose={() => setSelected(null)}
                onRevoke={async () => {
                    if (!selected) return;
                    await keys.revoke(selected.id);
                    setSelected(null);
                }}
            />
        </section>
    );
}

/** Make irreversible revocation and active-signing consequences concrete before a control request. */
function RevocationDialog({
    generation,
    active,
    pending,
    onClose,
    onRevoke,
}: {
    generation: KeyGeneration | null;
    active: boolean;
    pending: boolean;
    onClose: () => void;
    onRevoke: () => Promise<void>;
}) {
    const dialog = useRef<HTMLDialogElement>(null);
    useEffect(() => {
        if (generation && dialog.current && !dialog.current.open) dialog.current.showModal();
        if (!generation && dialog.current?.open) dialog.current.close();
    }, [generation]);
    return (
        <dialog
            ref={dialog}
            className="receipt-dialog key-revoke-dialog"
            aria-labelledby="revoke-title"
            onClose={onClose}
            onCancel={(event) => {
                if (pending) event.preventDefault();
            }}
        >
            <div className="dialog-heading">
                <div>
                    <div className="section-kicker">RECEIVER TRUST CHANGE</div>
                    <h2 id="revoke-title">Revoke generation {generation?.sequence}?</h2>
                </div>
                <button
                    className="icon-button"
                    type="button"
                    disabled={pending}
                    aria-label="Close revocation"
                    onClick={onClose}
                >
                    <X size={18} aria-hidden="true" />
                </button>
            </div>
            <p>
                The receiver will reject signatures from this generation on every future delivery,
                including exact retries with an existing receipt.
            </p>
            {active ? (
                <p className="key-revoke-consequence">
                    This is the selected signing generation. New signing will stop until you rotate
                    to a new generation.
                </p>
            ) : null}
            <p className="key-revoke-id">{generation?.id}</p>
            <p>Revocation cannot be undone. Stored release receipts remain available.</p>
            <div className="key-dialog-actions">
                <button
                    className="button secondary"
                    type="button"
                    disabled={pending}
                    onClick={onClose}
                >
                    Keep trusted
                </button>
                <button
                    className="button key-danger"
                    type="button"
                    disabled={pending}
                    onClick={() => void onRevoke()}
                >
                    <Ban size={14} aria-hidden="true" />
                    {pending ? 'Revoking…' : 'Revoke generation'}
                </button>
            </div>
        </dialog>
    );
}

/** Display UTC observations consistently; lifecycle eligibility is always calculated by Java. */
function dateTime(value: string): string {
    const instant = new Date(value);
    return Number.isNaN(instant.getTime())
        ? 'Unavailable'
        : new Intl.DateTimeFormat('en-GB', {
              dateStyle: 'medium',
              timeStyle: 'medium',
              timeZone: 'UTC',
          }).format(instant) + ' UTC';
}
