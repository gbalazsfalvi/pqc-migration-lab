import { ArrowRight, RotateCcw, Repeat2 } from 'lucide-react';
import { scenarios, type Comparison, type Scenario } from '../domain/contracts';
import { scenarioLabels } from '../domain/presentation';

interface Props {
    ready: boolean;
    pending: string | null;
    active: Scenario | null;
    baselineComparison: Comparison | null;
    liveAccepted: boolean;
    onExperiment: (scenario: Scenario) => Promise<void>;
    onRestore: () => Promise<void>;
    onReplay: () => Promise<void>;
}

/** Expose controlled fault injection and distinguish replay suppression from failed authentication. */
export function ExperimentPanel(props: Props) {
    const {
        ready,
        pending,
        active,
        baselineComparison,
        liveAccepted,
        onExperiment,
        onRestore,
        onReplay,
    } = props;
    const hasOriginalPqc = baselineComparison?.signatureEvidence.mlDsa65Valid ?? false;
    const baselineTrusted =
        (baselineComparison?.signatureEvidence.ed25519Valid ?? false) || hasOriginalPqc;
    const baselineAccepted =
        baselineComparison?.policies.find(
            (result) => result.policy === baselineComparison.receiverPolicy,
        )?.accepted ?? false;
    return (
        <section className="experiments-section" aria-labelledby="experiments-title">
            <div className="experiments-heading">
                <div>
                    <div className="section-kicker">
                        <span>04</span> CHALLENGE THE BOUNDARY
                    </div>
                    <h2 id="experiments-title">What happens when something changes?</h2>
                    <p>
                        Each fixture starts from the original signed event. Java evaluates the
                        result.
                    </p>
                </div>
                <button
                    type="button"
                    className="button secondary"
                    onClick={() => void onRestore()}
                    disabled={!ready || pending !== null}
                >
                    <RotateCcw size={14} aria-hidden="true" />
                    Restore original
                </button>
            </div>
            <div className="experiment-grid">
                {scenarios.map((scenario, index) => {
                    const labels = scenarioLabels[scenario];
                    const needsPqc = scenario === 'STRIP_PQC' || scenario === 'UNSIGNED_DOWNGRADE';
                    const disabled =
                        !ready ||
                        !baselineTrusted ||
                        pending !== null ||
                        (needsPqc && !hasOriginalPqc) ||
                        (scenario === 'CONFLICTING_ID' && !baselineAccepted);
                    return (
                        <button
                            type="button"
                            key={scenario}
                            className={
                                'experiment-button ' + (active === scenario ? 'is-active' : '')
                            }
                            onClick={() => void onExperiment(scenario)}
                            disabled={disabled}
                            title={
                                ready && !baselineTrusted
                                    ? 'Create an event signed by a currently trusted key first.'
                                    : needsPqc && ready && !hasOriginalPqc
                                      ? 'Create an original event with a PQC signature first.'
                                      : scenario === 'CONFLICTING_ID' && !baselineAccepted
                                        ? 'Create a baseline accepted by the live receiver first.'
                                        : labels.description
                            }
                        >
                            <span className="experiment-index">
                                {String(index + 1).padStart(2, '0')}
                            </span>
                            <span>
                                <strong>{labels.name}</strong>
                                <small>{labels.description}</small>
                            </span>
                            <ArrowRight size={14} aria-hidden="true" />
                        </button>
                    );
                })}
                <button
                    type="button"
                    className="experiment-button replay-experiment"
                    disabled={!ready || pending !== null || !liveAccepted}
                    onClick={() => void onReplay()}
                    title={
                        !liveAccepted
                            ? 'Restore an envelope accepted by the live receiver first.'
                            : 'Deliver twice and compare the durable receipt.'
                    }
                >
                    <span className="experiment-index">
                        <Repeat2 size={16} aria-hidden="true" />
                    </span>
                    <span>
                        <strong>Replay the same event</strong>
                        <small>Two deliveries. One durable business result.</small>
                    </span>
                    <ArrowRight size={14} aria-hidden="true" />
                </button>
            </div>
        </section>
    );
}
