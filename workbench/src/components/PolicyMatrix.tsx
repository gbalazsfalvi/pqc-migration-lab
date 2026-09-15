import { Check, ChevronRight, LockKeyhole, Minus, X } from 'lucide-react';
import { policies, type Comparison, type Policy } from '../domain/contracts';
import { policyLabels } from '../domain/presentation';

interface Props {
    comparison: Comparison | null;
    receiverPolicy: Policy;
    selected: Policy;
    onSelect: (policy: Policy) => void;
}

/** Compare observed decisions without providing any control that changes the live receiver policy. */
export function PolicyMatrix({ comparison, receiverPolicy, selected, onSelect }: Props) {
    return (
        <section className="panel matrix-panel" aria-labelledby="matrix-title">
            <div className="panel-heading">
                <div>
                    <div className="section-kicker">
                        <span>03</span> COMPARE THE REQUIREMENTS
                    </div>
                    <h2 id="matrix-title">One event. Four policies.</h2>
                </div>
                <span className="tag tag-neutral">Read-only</span>
            </div>
            <div className="table-scroll">
                <table className="policy-table">
                    <caption className="sr-only">
                        Java policy decisions for this exact envelope. No releases are stored by
                        evaluation.
                    </caption>
                    <thead>
                        <tr>
                            <th scope="col">Migration policy</th>
                            <th scope="col">Required evidence</th>
                            <th scope="col">Decision</th>
                        </tr>
                    </thead>
                    <tbody>
                        {policies.map((policy) => {
                            const observed = comparison?.policies.find(
                                (result) => result.policy === policy,
                            );
                            const label = policyLabels[policy];
                            return (
                                <tr
                                    key={policy}
                                    className={selected === policy ? 'selected-policy' : ''}
                                >
                                    <th scope="row">
                                        <button
                                            type="button"
                                            className="policy-name"
                                            aria-pressed={selected === policy}
                                            onClick={() => onSelect(policy)}
                                            aria-label={'Inspect ' + label.name}
                                        >
                                            <span className="phase-number">{label.phase}</span>
                                            <span>
                                                <strong>{label.name}</strong>
                                                {receiverPolicy === policy ? (
                                                    <small>
                                                        <LockKeyhole size={9} aria-hidden="true" />
                                                        Live receiver
                                                    </small>
                                                ) : null}
                                            </span>
                                            <ChevronRight
                                                size={13}
                                                className="row-arrow"
                                                aria-hidden="true"
                                            />
                                        </button>
                                    </th>
                                    <td className="requirement">{label.requirement}</td>
                                    <td>
                                        {observed ? (
                                            <span
                                                className={
                                                    'decision ' +
                                                    (observed.accepted ? 'accept' : 'reject')
                                                }
                                            >
                                                {observed.accepted ? (
                                                    <Check size={12} aria-hidden="true" />
                                                ) : (
                                                    <X size={12} aria-hidden="true" />
                                                )}
                                                {observed.accepted ? 'Accepts' : 'Rejects'}
                                            </span>
                                        ) : (
                                            <span className="not-evaluated">
                                                <Minus size={13} aria-hidden="true" />
                                                <span className="sr-only">Not evaluated</span>
                                            </span>
                                        )}
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
            <p className="matrix-footnote">
                <LockKeyhole size={12} aria-hidden="true" />
                Delivery remains governed by <strong>{policyLabels[receiverPolicy].short}</strong>.
            </p>
        </section>
    );
}
