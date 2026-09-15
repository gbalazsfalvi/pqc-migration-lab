import { useEffect, useRef, useState } from 'react';
import { labApi, WorkbenchError } from '../api/client';
import type {
    Comparison,
    Delivery,
    Envelope,
    LabConfig,
    LabRun,
    Policy,
    PublishForm,
    Receipt,
    Scenario,
    SigningMode,
    View,
} from '../domain/contracts';
import { displayPayload } from '../domain/presentation';

interface WorkbenchState {
    config: LabConfig | null;
    connection: 'connecting' | 'ready' | 'offline';
    error: string | null;
    pending: string | null;
    baseline: Envelope | null;
    baselineComparison: Comparison | null;
    envelope: Envelope | null;
    comparison: Comparison | null;
    selectedPolicy: Policy;
    scenario: Scenario | null;
    note: string | null;
    delivery: Delivery | null;
    receipt: Receipt | null;
    runs: LabRun[];
    view: View;
    mode: SigningMode;
    createdAt: string;
    inspectedReceipt: Receipt | null;
}

const initial: WorkbenchState = {
    config: null,
    connection: 'connecting',
    error: null,
    pending: null,
    baseline: null,
    baselineComparison: null,
    envelope: null,
    comparison: null,
    selectedPolicy: 'BOTH_REQUIRED',
    scenario: null,
    note: null,
    delivery: null,
    receipt: null,
    runs: [],
    view: 'workbench',
    mode: 'DUAL',
    createdAt: '',
    inspectedReceipt: null,
};

/** Keep a bounded in-memory session record; this never claims to list every durable ledger row. */
function remember(state: WorkbenchState, patch: Partial<WorkbenchState>): WorkbenchState {
    const next = { ...state, ...patch };
    if (!next.baseline || !next.baselineComparison || !next.envelope || !next.comparison)
        return next;
    const run: LabRun = {
        id: displayPayload(next.baseline).eventId,
        createdAt: next.createdAt,
        mode: next.mode,
        baseline: next.baseline,
        baselineComparison: next.baselineComparison,
        envelope: next.envelope,
        comparison: next.comparison,
        scenario: next.scenario,
        note: next.note,
        delivery: next.delivery,
        receipt: next.receipt,
    };
    return {
        ...next,
        runs: [run, ...next.runs.filter((existing) => existing.id !== run.id)].slice(0, 20),
    };
}

/** Own asynchronous transitions in one place; presentation components never manufacture verdicts. */
export function useWorkbench() {
    const [state, setState] = useState<WorkbenchState>(initial);
    const busy = useRef(false);

    // Connectivity is read-only, abortable, and refreshed without duplicating POST operations.
    useEffect(() => {
        const controller = new AbortController();
        async function connect() {
            try {
                const config = await labApi.config(controller.signal);
                if (!controller.signal.aborted)
                    setState((previous) => ({
                        ...previous,
                        config,
                        connection: 'ready',
                        selectedPolicy: previous.comparison
                            ? previous.selectedPolicy
                            : config.receiverPolicy,
                    }));
            } catch {
                if (!controller.signal.aborted)
                    setState((previous) => ({ ...previous, connection: 'offline' }));
            }
        }
        void connect();
        const interval = window.setInterval(() => void connect(), 20_000);
        return () => {
            controller.abort();
            window.clearInterval(interval);
        };
    }, []);

    /** Serialize user commands so late responses cannot replace a newer experiment. */
    async function exclusive(label: string, operation: () => Promise<void>) {
        if (busy.current) return;
        busy.current = true;
        setState((previous) => ({ ...previous, pending: label, error: null }));
        try {
            await operation();
        } catch (error) {
            setState((previous) => ({
                ...previous,
                error:
                    error instanceof WorkbenchError
                        ? error.message
                        : 'The experiment could not complete. Try again.',
            }));
        } finally {
            busy.current = false;
            setState((previous) => ({ ...previous, pending: null }));
        }
    }

    /** Commit observed snapshots together, preserving original receipts across later rejected trials. */
    function record(patch: Partial<WorkbenchState>) {
        setState((previous) => remember(previous, patch));
    }

    /** Generate a new baseline, then obtain actual Java policy observations for those exact bytes. */
    async function create(form: PublishForm) {
        await exclusive('create', async () => {
            const baseline = await labApi.create(form);
            const comparison = await labApi.compare(baseline);
            record({
                baseline,
                baselineComparison: comparison,
                envelope: baseline,
                comparison,
                selectedPolicy: comparison.receiverPolicy,
                scenario: null,
                note: null,
                delivery: null,
                receipt: null,
                mode: form.signingMode,
                createdAt: comparison.evaluatedAt,
                view: 'workbench',
            });
        });
    }

    /** Always apply a fixture to the original baseline rather than accidentally stacking mutations. */
    async function experiment(scenario: Scenario) {
        if (!state.baseline) return;
        const baseline = state.baseline;
        await exclusive(scenario, async () => {
            if (scenario === 'CONFLICTING_ID') {
                const original = await labApi.deliver(baseline);
                if (original.kind !== 'receipt') {
                    record({
                        delivery: original,
                        selectedPolicy: state.config?.receiverPolicy ?? 'BOTH_REQUIRED',
                    });
                    return;
                }
                record({ receipt: original.receipt });
            }
            const fixture = await labApi.experiment(scenario, baseline);
            const comparison = await labApi.compare(fixture.envelope);
            record({
                envelope: fixture.envelope,
                comparison,
                scenario,
                note: fixture.note,
                delivery: null,
            });
            if (scenario === 'CONFLICTING_ID') {
                const delivery = await labApi.deliver(fixture.envelope);
                record({ delivery, selectedPolicy: comparison.receiverPolicy });
            }
        });
    }

    /** Re-evaluate the original bytes with the current clock; cached freshness is never reused. */
    async function restore() {
        if (!state.baseline) return;
        const baseline = state.baseline;
        await exclusive('restore', async () =>
            record({
                envelope: baseline,
                comparison: await labApi.compare(baseline),
                scenario: null,
                note: null,
                delivery: null,
            }),
        );
    }

    /** Delivery never receives the UI's selected comparison policy as a parameter. */
    async function deliver(replay = false) {
        if (!state.envelope) return;
        const envelope = state.envelope;
        await exclusive(replay ? 'replay' : 'deliver', async () => {
            const first = await labApi.deliver(envelope);
            let delivery = first;
            if (replay && first.kind === 'receipt') {
                delivery = await labApi.deliver(envelope);
                if (
                    delivery.kind === 'receipt' &&
                    delivery.receipt.receiptId !== first.receipt.receiptId
                ) {
                    throw new WorkbenchError(
                        'The replay returned a different receipt. Inspect the receiver ledger.',
                        'RECEIPT_MISMATCH',
                    );
                }
            }
            record({
                delivery,
                receipt: delivery.kind === 'receipt' ? delivery.receipt : state.receipt,
                selectedPolicy: state.config?.receiverPolicy ?? 'BOTH_REQUIRED',
            });
            record({ comparison: await labApi.compare(envelope) });
        });
    }

    /** Explicit refresh checks both connectivity and the current envelope's read-only evaluation. */
    async function refresh() {
        await exclusive('refresh', async () => {
            const config = await labApi.config();
            setState((previous) => ({ ...previous, config, connection: 'ready' }));
            if (state.envelope) {
                const current = labApi.compare(state.envelope);
                const baseline =
                    state.baseline && state.baseline !== state.envelope
                        ? labApi.compare(state.baseline)
                        : current;
                const [comparison, baselineComparison] = await Promise.all([current, baseline]);
                // A previous delivery remains in its durable receipt; it is not the new trust revision's outcome.
                record({ comparison, baselineComparison, delivery: null });
            }
        });
    }

    /** Restore one public session snapshot without pretending it has just been freshly verified. */
    function openRun(run: LabRun) {
        if (busy.current) return;
        setState((previous) => ({
            ...previous,
            ...run,
            view: 'workbench',
            error: null,
            selectedPolicy: run.comparison.receiverPolicy,
        }));
        window.scrollTo({ top: 0, behavior: 'auto' });
    }

    /** Query the actual durable store rather than opening a cached acceptance as though it were a read. */
    async function inspectReceipt() {
        if (!state.receipt) return;
        const id = state.receipt.eventId;
        await exclusive('receipt', async () => {
            const receipt = await labApi.receipt(id);
            setState((previous) => ({ ...previous, inspectedReceipt: receipt }));
        });
    }

    return {
        state,
        create,
        experiment,
        restore,
        deliver,
        refresh,
        openRun,
        inspectReceipt,
        closeReceipt: () => setState((previous) => ({ ...previous, inspectedReceipt: null })),
        selectPolicy: (selectedPolicy: Policy) =>
            setState((previous) => ({ ...previous, selectedPolicy })),
        selectView: (view: View) => {
            setState((previous) => ({ ...previous, view }));
            window.scrollTo({ top: 0, behavior: 'auto' });
        },
        clearError: () => setState((previous) => ({ ...previous, error: null })),
    };
}
