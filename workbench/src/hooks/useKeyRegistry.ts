import { useEffect, useRef, useState } from 'react';
import { labApi, WorkbenchError } from '../api/client';
import type { KeyRegistry } from '../domain/contracts';

/** Keep observed public state current while fencing mutations and discarding late read responses. */
export function useKeyRegistry(onTrustChanged: () => Promise<void>) {
    const [registry, setRegistry] = useState<KeyRegistry | null>(null);
    const [pending, setPending] = useState<string | null>('loading');
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const alive = useRef(false);
    const busy = useRef(false);
    const sequence = useRef(0);
    const lastRevision = useRef<number | null>(null);
    const changed = useRef(onTrustChanged);

    // Background reads use the latest event-refresh handler without restarting their subscription.
    useEffect(() => {
        changed.current = onTrustChanged;
    }, [onTrustChanged]);

    // Poll read-only metadata so grace expiry is always computed by Java, never by a browser trust rule.
    useEffect(() => {
        alive.current = true;
        const controller = new AbortController();
        async function poll() {
            if (busy.current) return;
            const request = ++sequence.current;
            try {
                const observed = await labApi.keys(controller.signal);
                if (alive.current && request === sequence.current) {
                    const previous = lastRevision.current;
                    lastRevision.current = observed.revision;
                    setRegistry(observed);
                    setError(null);
                    if (previous !== observed.revision) await changed.current();
                }
            } catch (failure) {
                if (alive.current && !controller.signal.aborted && request === sequence.current) {
                    setError(message(failure));
                }
            } finally {
                if (alive.current && request === sequence.current)
                    setPending((value) => (value === 'loading' ? null : value));
            }
        }
        void poll();
        const interval = window.setInterval(() => void poll(), 15_000);
        return () => {
            alive.current = false;
            controller.abort();
            window.clearInterval(interval);
        };
    }, []);

    /** Read actual receiver state, excluding older in-flight responses. */
    async function readCurrent() {
        const request = ++sequence.current;
        const observed = await labApi.keys();
        if (alive.current && request === sequence.current) {
            lastRevision.current = observed.revision;
            setRegistry(observed);
        }
        return observed;
    }

    /** Refresh public metadata without issuing a key mutation or manufacturing a new revision. */
    async function refresh() {
        if (busy.current) return;
        busy.current = true;
        setPending('refresh');
        setError(null);
        try {
            await readCurrent();
            await onTrustChanged();
        } catch (failure) {
            if (alive.current) setError(message(failure));
        } finally {
            busy.current = false;
            if (alive.current) setPending(null);
        }
    }

    /** Mutate once, then query the receiver and refresh any existing event's real policy observations. */
    async function change(kind: 'rotate' | 'revoke', generationId?: string) {
        if (busy.current || !registry) return;
        busy.current = true;
        sequence.current++;
        setPending(kind);
        setError(null);
        setNotice(null);
        try {
            if (kind === 'rotate') await labApi.rotateKeys(registry.revision);
            else if (generationId) await labApi.revokeKeys(registry.revision, generationId);
            else return;
            await readCurrent();
            await onTrustChanged();
            if (alive.current)
                setNotice(
                    kind === 'rotate'
                        ? 'Rotation persisted. New signatures use the newest generation.'
                        : 'Revocation persisted. This generation is no longer trusted for any delivery.',
                );
        } catch (failure) {
            if (alive.current) setError(message(failure));
            // A timeout may occur after commit. Read the truth before allowing an operator to retry.
            try {
                const actual = await readCurrent();
                if (actual.revision !== registry.revision) await onTrustChanged();
            } catch {
                /* Retain the last observed snapshot and the actionable error. */
            }
        } finally {
            busy.current = false;
            if (alive.current) setPending(null);
        }
    }

    return {
        registry,
        pending,
        error,
        notice,
        refresh,
        rotate: () => change('rotate'),
        revoke: (generationId: string) => change('revoke', generationId),
    };
}

/** Keep internal parser/provider diagnostics outside the user-facing control flow. */
function message(error: unknown): string {
    return error instanceof WorkbenchError
        ? error.message
        : 'The key operation could not complete. Refresh the registry and try again.';
}
