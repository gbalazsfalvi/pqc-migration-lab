import { useEffect, useRef } from 'react';
import { Database, X } from 'lucide-react';
import type { Receipt } from '../domain/contracts';

/** Native dialog semantics provide focus trapping, Escape handling and an actual durable read result. */
export function ReceiptDialog({
    receipt,
    onClose,
}: {
    receipt: Receipt | null;
    onClose: () => void;
}) {
    const dialog = useRef<HTMLDialogElement>(null);
    useEffect(() => {
        if (receipt && dialog.current && !dialog.current.open) dialog.current.showModal();
        if (!receipt && dialog.current?.open) dialog.current.close();
    }, [receipt]);
    return (
        <dialog
            ref={dialog}
            className="receipt-dialog"
            aria-labelledby="receipt-dialog-title"
            onClose={onClose}
        >
            <div className="dialog-heading">
                <div>
                    <div className="section-kicker">
                        <Database size={13} aria-hidden="true" />
                        READ FROM H2 LEDGER
                    </div>
                    <h2 id="receipt-dialog-title">Stored release receipt</h2>
                </div>
                <button
                    type="button"
                    className="icon-button"
                    onClick={onClose}
                    aria-label="Close receipt"
                >
                    <X size={19} aria-hidden="true" />
                </button>
            </div>
            <p>
                The receiver returned this persisted result. Acceptance fields describe the original
                transaction.
            </p>
            <pre tabIndex={0}>
                <code>{receipt ? JSON.stringify(receipt, null, 2) : ''}</code>
            </pre>
        </dialog>
    );
}
