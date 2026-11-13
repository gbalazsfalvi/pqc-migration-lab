import { useState } from 'react';
import {
    Check,
    CheckCheck,
    ChevronRight,
    Clock3,
    ExternalLink,
    Minus,
    Send,
    X,
} from 'lucide-react';
import type { Delivery, PolicyObservation, Receipt } from '../domain/contracts';
import { abbreviate, deliveryLabel, formatTime, policyLabels } from '../domain/presentation';

interface Props {
    observation: PolicyObservation | undefined;
    evaluatedAt: string | undefined;
    delivery: Delivery | null;
    receipt: Receipt | null;
    pending: string | null;
    canDeliver: boolean;
    receiverName: string;
    onDeliver: () => Promise<void>;
    onReadReceipt: () => void;
}

/** Explain the actual rejection boundary and keep read-only checks distinct from durable delivery. */
export function ValidationTrace(props: Props) {
    const {
        observation,
        evaluatedAt,
        delivery,
        receipt,
        pending,
        canDeliver,
        receiverName,
        onDeliver,
        onReadReceipt,
    } = props;
    const [selectedStep, setSelectedStep] = useState<string | null>(null);
    const detail =
        observation?.steps.find((step) => step.id === selectedStep) ??
        observation?.steps.find((step) => step.status === 'FAILED') ??
        observation?.steps.at(-1);
    return (
        <section className="panel trace-panel" aria-labelledby="trace-title">
            <div className="trace-heading">
                <div>
                    <h3 id="trace-title">Verification path</h3>
                    <p>
                        {observation
                            ? policyLabels[observation.policy].name
                            : 'Select an evaluated policy to inspect its path.'}
                    </p>
                </div>
                {evaluatedAt ? (
                    <span className="observation-time">
                        <Clock3 size={11} aria-hidden="true" />
                        {formatTime(evaluatedAt)}
                    </span>
                ) : null}
            </div>
            {observation ? (
                <>
                    <ol className="trace-list">
                        {observation.steps.map((step, index) => (
                            <li
                                key={step.id}
                                className={
                                    'trace-step status-' +
                                    step.status.toLowerCase() +
                                    (detail?.id === step.id ? ' detail-selected' : '')
                                }
                            >
                                <button
                                    type="button"
                                    onClick={() => setSelectedStep(step.id)}
                                    aria-pressed={detail?.id === step.id}
                                    aria-label={
                                        step.label +
                                        ': ' +
                                        step.status.toLowerCase().replaceAll('_', ' ')
                                    }
                                >
                                    <span className="step-node">
                                        {step.status === 'PASSED' ? (
                                            <Check size={12} aria-hidden="true" />
                                        ) : step.status === 'FAILED' ? (
                                            <X size={12} aria-hidden="true" />
                                        ) : (
                                            String(index + 1).padStart(2, '0')
                                        )}
                                    </span>
                                    <span>{step.label}</span>
                                </button>
                            </li>
                        ))}
                    </ol>
                    <p
                        className={
                            'trace-explanation ' +
                            (detail?.status === 'FAILED' ? 'has-rejection' : '')
                        }
                    >
                        <ChevronRight size={13} aria-hidden="true" />
                        {detail?.detail}
                    </p>
                </>
            ) : (
                <div className="trace-empty">
                    <Minus size={15} aria-hidden="true" />
                    The verification path appears after Java evaluates an event.
                </div>
            )}
            <div className="delivery-area">
                <div>
                    <strong>Apply to the receiver</strong>
                    <p>Live policy: {receiverName}. Evaluation alone stores nothing.</p>
                </div>
                <button
                    type="button"
                    className="button primary deliver-button"
                    disabled={!canDeliver || pending !== null}
                    onClick={() => void onDeliver()}
                >
                    <Send size={14} aria-hidden="true" />
                    {pending === 'deliver' ? 'Delivering…' : 'Deliver event'}
                </button>
            </div>
            {delivery ? (
                <div className={'delivery-outcome delivery-' + delivery.kind} aria-live="polite">
                    <span className="delivery-result-icon">
                        {delivery.kind === 'receipt' ? (
                            <CheckCheck size={17} aria-hidden="true" />
                        ) : (
                            <X size={17} aria-hidden="true" />
                        )}
                    </span>
                    <div>
                        <strong>{deliveryLabel(delivery)}</strong>
                        <p>
                            {delivery.kind === 'receipt'
                                ? delivery.receipt.status === 'ALREADY_ACCEPTED'
                                    ? 'Original receipt reused. No second business result.'
                                    : 'One release and its receipt were committed atomically.'
                                : delivery.problem.code.replaceAll('_', ' ').toLowerCase() +
                                  (delivery.problem.code === 'EVENT_ID_CONFLICT'
                                      ? ' · The original release remains unchanged.'
                                      : '')}
                        </p>
                    </div>
                    <span className="http-code">HTTP {delivery.httpStatus}</span>
                </div>
            ) : null}
            {receipt ? (
                <button type="button" className="stored-receipt" onClick={onReadReceipt}>
                    <span>
                        <span className="receipt-caption">DURABLE RECEIPT</span>
                        <code>{abbreviate(receipt.receiptId, 12, 8)}</code>
                    </span>
                    <span>
                        Read from ledger
                        <ExternalLink size={12} aria-hidden="true" />
                    </span>
                </button>
            ) : null}
        </section>
    );
}
