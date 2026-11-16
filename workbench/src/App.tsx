import {
    ArrowRight,
    BookOpen,
    Download,
    FlaskConical,
    History,
    Info,
    LoaderCircle,
    LockKeyhole,
    KeyRound,
    RefreshCw,
    ShieldCheck,
    X,
} from 'lucide-react';
import { EventComposer } from './components/EventComposer';
import { EnvelopeViewer } from './components/EnvelopeViewer';
import { ExperimentPanel } from './components/ExperimentPanel';
import { PolicyMatrix } from './components/PolicyMatrix';
import { ProtocolGuide } from './components/ProtocolGuide';
import { ReceiptDialog } from './components/ReceiptDialog';
import { SessionHistory } from './components/SessionHistory';
import { ValidationTrace } from './components/ValidationTrace';
import { KeyLifecycle } from './components/KeyLifecycle';
import { useWorkbench } from './hooks/useWorkbench';
import { displayPayload, downloadJson, policyLabels, scenarioLabels } from './domain/presentation';
import type { View } from './domain/contracts';

const navigation: { id: View; label: string; icon: typeof FlaskConical }[] = [
    { id: 'workbench', label: 'Workbench', icon: FlaskConical },
    { id: 'keys', label: 'Key lifecycle', icon: KeyRound },
    { id: 'history', label: 'Run history', icon: History },
    { id: 'guide', label: 'Protocol guide', icon: BookOpen },
];

/** Compose a restrained English research workbench around observed Java API results. */
export function App() {
    const lab = useWorkbench();
    const { state } = lab;
    const receiverPolicy = state.config?.receiverPolicy ?? 'BOTH_REQUIRED';
    const receiverName = policyLabels[receiverPolicy].short;
    const selected = state.comparison?.policies.find(
        (policy) => policy.policy === state.selectedPolicy,
    );
    const liveAccepted =
        state.comparison?.policies.find((policy) => policy.policy === receiverPolicy)?.accepted ??
        false;

    /** Export the exact public envelope and observed responses, keeping creation and evaluation timestamps. */
    function exportRun() {
        if (!state.envelope || !state.comparison) return;
        const event = displayPayload(state.envelope);
        downloadJson('pqc-run-' + event.eventId + '.json', {
            formatVersion: 1,
            collectedAt: new Date().toISOString(),
            scenario: state.scenario,
            envelope: state.envelope,
            evaluation: state.comparison,
            delivery: state.delivery,
            storedReceipt: state.receipt,
        });
    }

    return (
        <div className="app-shell">
            <a className="skip-link" href="#main-content">
                Skip to workbench
            </a>
            <aside className="sidebar" aria-label="Laboratory navigation">
                <button
                    type="button"
                    className="brand"
                    onClick={() => lab.selectView('workbench')}
                    aria-label="PQC Migration Lab home"
                >
                    <span className="brand-mark" aria-hidden="true">
                        p<span>q</span>
                    </span>
                    <span>
                        <strong>PQC</strong>
                        <small>MIGRATION LAB</small>
                    </span>
                </button>
                <div className="sidebar-divider" />
                <div className="nav-caption">LABORATORY</div>
                <nav>
                    {navigation.map(({ id, label, icon: Icon }) => (
                        <button
                            type="button"
                            key={id}
                            onClick={() => lab.selectView(id)}
                            className={'nav-item ' + (state.view === id ? 'is-active' : '')}
                            aria-current={state.view === id ? 'page' : undefined}
                        >
                            <Icon size={17} strokeWidth={1.6} aria-hidden="true" />
                            <span>{label}</span>
                            {id === 'history' && state.runs.length ? (
                                <small>{state.runs.length}</small>
                            ) : null}
                        </button>
                    ))}
                </nav>
                <div className="sidebar-note">
                    <div className="sidebar-note-rule" />
                    <span>
                        FROM CLASSICAL
                        <br />
                        TO POST-QUANTUM.
                    </span>
                    <p>
                        A signed integration,
                        <br />
                        one verifiable step at a time.
                    </p>
                </div>
                <div className="sidebar-footer">
                    <div className={'connection-dot ' + state.connection} />
                    <div>
                        <strong>Local environment</strong>
                        <span>
                            {state.config
                                ? 'Java ' + state.config.javaVersion.split('+')[0] + ' · DSSE v1'
                                : 'Waiting for Java backend'}
                        </span>
                    </div>
                    <LockKeyhole size={12} aria-hidden="true" />
                </div>
            </aside>
            <div className="main-shell">
                <header className="topbar">
                    <div>
                        <span className="topbar-label">PQC MIGRATION LAB</span>
                        <span className="topbar-divider">/</span>
                        <span>
                            {state.view === 'workbench'
                                ? 'Signature migration'
                                : state.view === 'keys'
                                  ? 'Public trust registry'
                                  : state.view === 'history'
                                    ? 'Session observations'
                                    : 'Protocol notes'}
                        </span>
                    </div>
                    <span className={'connection-badge ' + state.connection}>
                        <span className="connection-dot" />
                        {state.connection === 'ready'
                            ? 'Receiver connected'
                            : state.connection === 'connecting'
                              ? 'Connecting…'
                              : 'Backend offline'}
                    </span>
                </header>
                <main id="main-content">
                    {state.view === 'workbench' ? (
                        <>
                            <div className="page-heading">
                                <div>
                                    <div className="eyebrow">CLASSICAL → POST-QUANTUM</div>
                                    <h1>
                                        Migration workbench<span>.</span>
                                    </h1>
                                    <p className="page-intro">
                                        Sign a release. Challenge its assumptions. Follow the
                                        evidence.
                                    </p>
                                </div>
                                <div className="page-actions">
                                    <button
                                        type="button"
                                        className="icon-button"
                                        onClick={() => void lab.refresh()}
                                        disabled={state.pending !== null}
                                        aria-label="Refresh backend observations"
                                        title="Refresh backend observations"
                                    >
                                        <RefreshCw
                                            size={16}
                                            className={state.pending === 'refresh' ? 'spin' : ''}
                                            aria-hidden="true"
                                        />
                                    </button>
                                    <button
                                        type="button"
                                        className="button secondary"
                                        onClick={exportRun}
                                        disabled={!state.comparison || state.pending !== null}
                                    >
                                        <Download size={14} aria-hidden="true" />
                                        Export run
                                    </button>
                                </div>
                            </div>
                            <div className="flow-strip" aria-label="Integration stages">
                                <div>
                                    <span className="flow-index">01</span>
                                    <div>
                                        <strong>Publisher</strong>
                                        <small>Java signing engine</small>
                                    </div>
                                </div>
                                <ArrowRight size={16} aria-hidden="true" />
                                <div>
                                    <span className="flow-index">02</span>
                                    <div>
                                        <strong>Signed envelope</strong>
                                        <small>Original bytes · DSSE v1</small>
                                    </div>
                                </div>
                                <ArrowRight size={16} aria-hidden="true" />
                                <div>
                                    <span className="flow-index">03</span>
                                    <div>
                                        <strong>Receiver</strong>
                                        <small>
                                            {state.config
                                                ? receiverName
                                                : 'Trusted policy enforcement'}
                                        </small>
                                    </div>
                                    <LockKeyhole size={12} aria-hidden="true" />
                                </div>
                                <span className="flow-local">
                                    <span />
                                    HTTP DELIVERY
                                </span>
                            </div>
                            {state.connection === 'offline' ? (
                                <div className="notice notice-offline" role="status">
                                    <Info size={17} aria-hidden="true" />
                                    <div>
                                        <strong>
                                            Start the local Java application to run experiments.
                                        </strong>
                                        <span>
                                            Use <code>.\lab.ps1 serve</code> at the repository root,
                                            then refresh the connection.
                                        </span>
                                    </div>
                                    <button
                                        type="button"
                                        className="text-button"
                                        onClick={() => void lab.refresh()}
                                        disabled={state.pending !== null}
                                    >
                                        Reconnect
                                        <RefreshCw size={13} aria-hidden="true" />
                                    </button>
                                </div>
                            ) : null}
                            {state.error ? (
                                <div className="notice notice-error" role="alert">
                                    <Info size={17} aria-hidden="true" />
                                    <span>{state.error}</span>
                                    <button
                                        type="button"
                                        className="icon-button"
                                        onClick={lab.clearError}
                                        aria-label="Dismiss error"
                                    >
                                        <X size={15} aria-hidden="true" />
                                    </button>
                                </div>
                            ) : null}
                            {state.note ? (
                                <div className="experiment-notice" aria-live="polite">
                                    <FlaskConical size={16} aria-hidden="true" />
                                    <span>
                                        <strong>
                                            {state.scenario
                                                ? scenarioLabels[state.scenario].name
                                                : 'Experiment'}
                                        </strong>
                                        {state.note}
                                    </span>
                                    <span className="tag tag-amber">Fixture applied</span>
                                </div>
                            ) : null}
                            {state.comparison && state.comparison.trustRevision > 0 ? (
                                <div className="trust-revision-line">
                                    <span>
                                        Observed with receiver trust revision{' '}
                                        <strong>{state.comparison.trustRevision}</strong>
                                    </span>
                                    <button
                                        type="button"
                                        className="text-button"
                                        onClick={() => lab.selectView('keys')}
                                    >
                                        Key lifecycle <ArrowRight size={12} aria-hidden="true" />
                                    </button>
                                </div>
                            ) : null}
                            <div className="workspace-grid">
                                <div className="composer-column">
                                    <EventComposer
                                        connected={state.connection === 'ready'}
                                        pending={state.pending}
                                        onCreate={lab.create}
                                    />
                                    <div className="trust-note">
                                        <ShieldCheck
                                            size={17}
                                            strokeWidth={1.5}
                                            aria-hidden="true"
                                        />
                                        <p>
                                            Private keys stay in Java.
                                            <br />
                                            Every verdict comes from the backend.
                                        </p>
                                    </div>
                                </div>
                                <div className="evidence-column">
                                    <EnvelopeViewer
                                        envelope={state.envelope}
                                        comparison={state.comparison}
                                        scenario={state.scenario}
                                    />
                                    <PolicyMatrix
                                        comparison={state.comparison}
                                        receiverPolicy={receiverPolicy}
                                        selected={state.selectedPolicy}
                                        onSelect={lab.selectPolicy}
                                    />
                                    <ValidationTrace
                                        key={
                                            state.selectedPolicy +
                                            ':' +
                                            (state.comparison?.payloadSha256 ?? '') +
                                            ':' +
                                            state.scenario
                                        }
                                        observation={selected}
                                        evaluatedAt={state.comparison?.evaluatedAt}
                                        delivery={state.delivery}
                                        receipt={state.receipt}
                                        pending={state.pending}
                                        canDeliver={
                                            state.envelope !== null && state.connection === 'ready'
                                        }
                                        receiverName={receiverName}
                                        onDeliver={() => lab.deliver()}
                                        onReadReceipt={() => void lab.inspectReceipt()}
                                    />
                                </div>
                            </div>
                            <ExperimentPanel
                                ready={state.baseline !== null && state.connection === 'ready'}
                                pending={state.pending}
                                active={state.scenario}
                                baselineComparison={state.baselineComparison}
                                liveAccepted={liveAccepted}
                                onExperiment={lab.experiment}
                                onRestore={lab.restore}
                                onReplay={() => lab.deliver(true)}
                            />
                            {state.pending ? (
                                <div className="operation-indicator" role="status">
                                    <LoaderCircle size={14} className="spin" aria-hidden="true" />
                                    Waiting for the Java result…
                                </div>
                            ) : null}
                        </>
                    ) : state.view === 'keys' ? (
                        <KeyLifecycle onTrustChanged={lab.refresh} />
                    ) : state.view === 'history' ? (
                        <SessionHistory
                            runs={state.runs}
                            onOpen={lab.openRun}
                            onCreate={() => lab.selectView('workbench')}
                        />
                    ) : (
                        <ProtocolGuide config={state.config} />
                    )}
                    <footer className="page-footer">
                        <span>
                            PQC Migration Lab <span className="footer-dot">·</span> Engineering,
                            with evidence.
                        </span>
                        <span>
                            Local sandbox
                            <LockKeyhole size={10} aria-hidden="true" />
                        </span>
                    </footer>
                </main>
            </div>
            <ReceiptDialog receipt={state.inspectedReceipt} onClose={lab.closeReceipt} />
        </div>
    );
}
