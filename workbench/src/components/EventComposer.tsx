import { useState, type FormEvent } from 'react';
import { ArrowRight, ChevronDown, KeyRound, Layers, LoaderCircle, ShieldCheck } from 'lucide-react';
import type { PublishForm, SigningMode } from '../domain/contracts';

interface Props {
    connected: boolean;
    pending: string | null;
    onCreate: (form: PublishForm) => Promise<void>;
}

const profiles: { id: SigningMode; name: string; detail: string; icon: typeof KeyRound }[] = [
    { id: 'ED25519_ONLY', name: 'Classical', detail: 'Ed25519', icon: KeyRound },
    { id: 'ML_DSA_65_ONLY', name: 'Post-quantum', detail: 'ML-DSA-65', icon: ShieldCheck },
    { id: 'DUAL', name: 'Dual signature', detail: 'Ed25519 + ML-DSA-65', icon: Layers },
];

/** Keep creation intent separate from an existing envelope and the receiver's fixed policy. */
export function EventComposer({ connected, pending, onCreate }: Props) {
    const [form, setForm] = useState<PublishForm>({
        artifact: 'synthetic-service',
        version: '1.0.0',
        sha256: 'a'.repeat(64),
        signingMode: 'DUAL',
    });
    const [error, setError] = useState<string | null>(null);

    /** Client validation improves field feedback; the Java schema remains authoritative. */
    function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (
            !/^[a-zA-Z][a-zA-Z0-9._-]{0,63}$/.test(form.artifact) ||
            !/^[a-zA-Z0-9][a-zA-Z0-9.+_-]{0,63}$/.test(form.version)
        ) {
            setError('Use a short artifact and version with letters, numbers, dots or dashes.');
            return;
        }
        if (!/^[0-9a-f]{64}$/.test(form.sha256)) {
            setError(
                'The artifact digest must contain exactly 64 lowercase hexadecimal characters.',
            );
            return;
        }
        setError(null);
        void onCreate(form);
    }

    return (
        <section className="panel composer" aria-labelledby="composer-title">
            <div className="section-kicker">
                <span>01</span> CREATE AN EVENT
            </div>
            <h2 id="composer-title">The starting point.</h2>
            <p className="panel-intro">A synthetic release, signed by the Java publisher.</p>
            <form onSubmit={submit} noValidate>
                <label className="field">
                    <span>Artifact</span>
                    <input
                        name="artifact"
                        value={form.artifact}
                        maxLength={64}
                        autoComplete="off"
                        spellCheck={false}
                        onChange={(event) =>
                            setForm((previous) => ({ ...previous, artifact: event.target.value }))
                        }
                    />
                </label>
                <label className="field">
                    <span>Release version</span>
                    <input
                        name="version"
                        value={form.version}
                        maxLength={64}
                        autoComplete="off"
                        spellCheck={false}
                        onChange={(event) =>
                            setForm((previous) => ({ ...previous, version: event.target.value }))
                        }
                    />
                </label>
                <fieldset className="profile-fieldset">
                    <legend>Signing profile</legend>
                    {profiles.map(({ id, name, detail, icon: Icon }) => (
                        <label
                            key={id}
                            className={
                                'profile-option ' + (form.signingMode === id ? 'is-selected' : '')
                            }
                        >
                            <input
                                type="radio"
                                name="signingMode"
                                value={id}
                                checked={form.signingMode === id}
                                onChange={() =>
                                    setForm((previous) => ({ ...previous, signingMode: id }))
                                }
                            />
                            <Icon size={18} aria-hidden="true" />
                            <span className="profile-copy">
                                <strong>{name}</strong>
                                <small>{detail}</small>
                            </span>
                            <span className="radio-mark" aria-hidden="true" />
                        </label>
                    ))}
                </fieldset>
                <details className="digest-details">
                    <summary>
                        Artifact digest <ChevronDown size={14} aria-hidden="true" />
                    </summary>
                    <label className="field">
                        <span className="sr-only">Artifact SHA-256</span>
                        <textarea
                            rows={3}
                            name="sha256"
                            className="mono"
                            value={form.sha256}
                            maxLength={64}
                            spellCheck={false}
                            onChange={(event) =>
                                setForm((previous) => ({ ...previous, sha256: event.target.value }))
                            }
                        />
                    </label>
                    <p>A synthetic SHA-256 claim for this local experiment.</p>
                </details>
                {error ? (
                    <p className="field-error" role="alert">
                        {error}
                    </p>
                ) : null}
                <button
                    type="submit"
                    className="button primary create-button"
                    disabled={!connected || pending !== null}
                >
                    {pending === 'create' ? (
                        <LoaderCircle size={17} className="spin" aria-hidden="true" />
                    ) : null}
                    {pending === 'create' ? 'Signing with Java…' : 'Create signed event'}
                    {pending !== 'create' ? <ArrowRight size={17} aria-hidden="true" /> : null}
                </button>
                <p className="form-footnote">
                    Identity, audience and time are assigned by the publisher.
                </p>
            </form>
        </section>
    );
}
