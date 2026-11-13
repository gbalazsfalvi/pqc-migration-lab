import { ArrowRight, History } from 'lucide-react';
import type { LabRun } from '../domain/contracts';
import {
    abbreviate,
    deliveryLabel,
    displayPayload,
    formatTime,
    modeLabels,
} from '../domain/presentation';

/** Show bounded public session observations without presenting browser memory as the durable ledger. */
export function SessionHistory({
    runs,
    onOpen,
    onCreate,
}: {
    runs: LabRun[];
    onOpen: (run: LabRun) => void;
    onCreate: () => void;
}) {
    return (
        <section className="history-section" aria-labelledby="history-title">
            <div className="section-kicker">
                <span>SESSION</span> PUBLIC OBSERVATIONS
            </div>
            <h1 id="history-title">Run history.</h1>
            <p className="page-intro">
                Up to 20 recent runs in this tab. Durable receipts are stored separately by the
                receiver.
            </p>
            {runs.length ? (
                <div className="history-list">
                    {runs.map((run) => {
                        const event = displayPayload(run.baseline);
                        return (
                            <button
                                type="button"
                                className="history-row"
                                key={run.id}
                                onClick={() => onOpen(run)}
                            >
                                <div className="history-icon">
                                    <History size={18} aria-hidden="true" />
                                </div>
                                <div className="history-artifact">
                                    <strong>
                                        {event.artifact}
                                        <span>{event.version}</span>
                                    </strong>
                                    <code>{abbreviate(run.id, 14, 8)}</code>
                                </div>
                                <div className="history-profile">
                                    {modeLabels[run.mode]}
                                    <small>{formatTime(run.createdAt)}</small>
                                </div>
                                <span
                                    className={
                                        'history-result ' +
                                        (run.delivery?.kind === 'rejection' ? 'text-reject' : '')
                                    }
                                >
                                    {run.delivery ? deliveryLabel(run.delivery) : 'Evaluated'}
                                </span>
                                <ArrowRight size={16} aria-hidden="true" />
                            </button>
                        );
                    })}
                </div>
            ) : (
                <div className="panel history-empty">
                    <History size={28} strokeWidth={1.2} aria-hidden="true" />
                    <h2>A record of your experiments.</h2>
                    <p>Create an event to start collecting real observations.</p>
                    <button type="button" className="button primary" onClick={onCreate}>
                        Open the workbench
                        <ArrowRight size={15} aria-hidden="true" />
                    </button>
                </div>
            )}
        </section>
    );
}
