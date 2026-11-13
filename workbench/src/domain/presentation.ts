import type { Algorithm, Delivery, Envelope, Policy, Scenario, SigningMode } from './contracts';

/** Presentation labels do not calculate acceptance; every verdict comes from the Java response. */
export const policyLabels: Record<
    Policy,
    { name: string; short: string; requirement: string; phase: string }
> = {
    CLASSICAL_ONLY: {
        name: 'Classical baseline',
        short: 'Classical',
        requirement: 'Ed25519',
        phase: '01',
    },
    SHADOW_PQC: {
        name: 'Shadow PQC',
        short: 'Shadow PQC',
        requirement: 'Ed25519 · observe PQC',
        phase: '02',
    },
    BOTH_REQUIRED: {
        name: 'Both required',
        short: 'Both required',
        requirement: 'Ed25519 + ML-DSA-65',
        phase: '03',
    },
    PQC_REQUIRED: {
        name: 'PQC required',
        short: 'PQC required',
        requirement: 'ML-DSA-65',
        phase: '04',
    },
};

export const algorithmLabels: Record<Algorithm, string> = {
    ED25519: 'Ed25519',
    ML_DSA_65: 'ML-DSA-65',
};
export const modeLabels: Record<SigningMode, string> = {
    ED25519_ONLY: 'Classical',
    ML_DSA_65_ONLY: 'Post-quantum',
    DUAL: 'Dual signature',
};
export const scenarioLabels: Record<Scenario, { name: string; description: string }> = {
    STRIP_PQC: {
        name: 'Remove PQC signature',
        description: 'Does the migration requirement hold?',
    },
    TAMPER_PAYLOAD: {
        name: 'Change the payload',
        description: 'Edit the release; retain its signatures.',
    },
    WRONG_KEY: {
        name: 'Substitute signing keys',
        description: 'Use keys outside the trusted registry.',
    },
    EXPIRED_EVENT: {
        name: 'Expire the event',
        description: 'Move the signed time outside its window.',
    },
    FUTURE_EVENT: {
        name: 'Move time forward',
        description: 'Exceed the permitted clock tolerance.',
    },
    WRONG_AUDIENCE: { name: 'Change the audience', description: 'Address a different receiver.' },
    UNSIGNED_DOWNGRADE: {
        name: 'Attempt a policy downgrade',
        description: 'Strip PQC and add an unsigned override.',
    },
    CONFLICTING_ID: {
        name: 'Reuse the event ID',
        description: 'Deliver different bytes under a stored ID.',
    },
};

/** Decode for display only. This function never verifies signatures or returns a trust decision. */
export function displayPayload(envelope: Envelope): {
    text: string;
    eventId: string;
    artifact: string;
    version: string;
} {
    try {
        const normalized = envelope.payload.replaceAll('-', '+').replaceAll('_', '/');
        const bytes = Uint8Array.from(atob(normalized), (character) => character.charCodeAt(0));
        const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
        const value: unknown = JSON.parse(text);
        if (typeof value !== 'object' || value === null) throw new Error('Not an object');
        const event = value as Record<string, unknown>;
        return {
            text: JSON.stringify(value, null, 2),
            eventId: typeof event.eventId === 'string' ? event.eventId : 'Unknown event',
            artifact: typeof event.artifact === 'string' ? event.artifact : 'Unparsed artifact',
            version: typeof event.version === 'string' ? event.version : '—',
        };
    } catch {
        return {
            text: 'Payload preview is unavailable. Java still owns verification.',
            eventId: 'Unknown event',
            artifact: 'Unparsed artifact',
            version: '—',
        };
    }
}

/** Format observed byte counts independently of cryptographic semantics. */
export function formatBytes(value: number): string {
    return value < 1_024
        ? value.toLocaleString('en-US') + ' B'
        : (value / 1_024).toFixed(2) + ' KB';
}

/** Show UTC observation times so the freshness explanation has one consistent time basis. */
export function formatTime(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.valueOf())
        ? '—'
        : new Intl.DateTimeFormat('en-GB', {
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit',
              timeZone: 'UTC',
          }).format(date) + ' UTC';
}

/** Keep full values available in titles, copied JSON and exports. */
export function abbreviate(value: string, start = 8, end = 6): string {
    return value.length <= start + end + 1
        ? value
        : value.slice(0, start) + '…' + value.slice(-end);
}

/** Distinguish normal rejection experiments from operational unavailability and duplicate suppression. */
export function deliveryLabel(delivery: Delivery): string {
    if (delivery.kind === 'receipt') {
        return delivery.receipt.status === 'ALREADY_ACCEPTED'
            ? 'Duplicate suppressed'
            : 'Release accepted';
    }
    return delivery.kind === 'unavailable' ? 'Receiver unavailable' : 'Delivery rejected';
}

/** Download public observations only; no Java private-key material exists in browser state. */
export function downloadJson(filename: string, value: unknown): void {
    const url = URL.createObjectURL(
        new Blob([JSON.stringify(value, null, 2) + '\n'], { type: 'application/json' }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
}
