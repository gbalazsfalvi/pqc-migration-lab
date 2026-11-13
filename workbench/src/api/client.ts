import type { z } from 'zod';
import {
    comparisonSchema,
    configSchema,
    envelopeSchema,
    fixtureSchema,
    problemSchema,
    receiptSchema,
    keyRegistrySchema,
    type KeyRegistry,
    type Comparison,
    type Delivery,
    type Envelope,
    type Fixture,
    type LabConfig,
    type PublishForm,
    type Receipt,
    type Scenario,
} from '../domain/contracts';

/** Exposes safe, actionable transport errors instead of raw parser text or backend diagnostics. */
export class WorkbenchError extends Error {
    constructor(
        message: string,
        readonly code = 'CONNECTION_FAILED',
    ) {
        super(message);
    }
}

const failureMessages: Record<string, string> = {
    SCENARIO_NOT_APPLICABLE: 'This experiment needs an original event with a PQC signature.',
    INVALID_EXPERIMENT_BASELINE: 'Create a new original event before applying this experiment.',
    INVALID_PUBLISH_REQUEST: 'Check the release fields and try again.',
    MALFORMED_EVENT: 'Use a valid artifact, version and lowercase SHA-256 digest.',
    RECEIVER_UNAVAILABLE: 'The receiver is unavailable. Check the local Java application.',
    DELIVERY_TIMEOUT:
        'The receiver did not finish before the delivery deadline. Its receipt may still exist.',
    KEY_REVISION_CONFLICT:
        'The registry changed in another operation. Its current state has been refreshed; review it before trying again.',
    KEY_GENERATION_LIMIT: 'This local registry has reached its generation limit.',
    KEY_STORE_UNAVAILABLE:
        'The key change could not be persisted. The previous live state remains in use.',
    KEY_LIFECYCLE_UNMANAGED:
        'This runtime uses ephemeral probe keys and does not support durable key operations.',
    SIGNING_KEYS_REVOKED:
        'The selected signing generation is revoked. Rotate signing keys before creating another event.',
};

/** Use fixed same-origin routes; the Java gateway selects all upstream URLs. */
async function request(
    path: string,
    body?: unknown,
    signal?: AbortSignal,
): Promise<{ response: Response; json: unknown }> {
    const deadline = AbortSignal.timeout(15_000);
    try {
        const response = await fetch(path, {
            method: body === undefined ? 'GET' : 'POST',
            credentials: 'omit',
            cache: 'no-store',
            headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body),
            signal: signal ? AbortSignal.any([signal, deadline]) : deadline,
        });
        const json: unknown = await response.json();
        return { response, json };
    } catch (error) {
        if (signal?.aborted) throw error;
        throw new WorkbenchError(
            'The local Java backend could not be reached. Start it with .\\lab.ps1 serve.',
        );
    }
}

/** Validate every API boundary before a component can present it as observed evidence. */
async function checked<T>(
    schema: z.ZodType<T>,
    path: string,
    body?: unknown,
    signal?: AbortSignal,
): Promise<T> {
    const result = await request(path, body, signal);
    if (!result.response.ok) {
        const problem = problemSchema.safeParse(result.json);
        const code = problem.success ? problem.data.code : 'UNEXPECTED_RESPONSE';
        throw new WorkbenchError(
            failureMessages[code] ?? 'The backend could not complete this request (' + code + ').',
            code,
        );
    }
    const parsed = schema.safeParse(result.json);
    if (!parsed.success)
        throw new WorkbenchError(
            'The backend response does not match the workbench contract.',
            'INVALID_RESPONSE',
        );
    return parsed.data;
}

export const labApi = {
    /** Read the receiver's public registry, without requesting or exporting private encodings. */
    keys: (signal?: AbortSignal): Promise<KeyRegistry> =>
        checked(keyRegistrySchema, '/api/workbench/keys', undefined, signal),
    /** Fence rotation with the actual revision the operator reviewed. */
    rotateKeys: (expectedRevision: number): Promise<KeyRegistry> =>
        checked(keyRegistrySchema, '/api/workbench/keys/rotate', { expectedRevision }),
    /** Revoke one configured generation without changing the migration policy. */
    revokeKeys: (expectedRevision: number, generationId: string): Promise<KeyRegistry> =>
        checked(keyRegistrySchema, '/api/workbench/keys/revoke', {
            expectedRevision,
            generationId,
        }),
    /** Confirm connectivity without creating any events. */
    config: (signal?: AbortSignal): Promise<LabConfig> =>
        checked(configSchema, '/api/workbench/config', undefined, signal),
    /** Ask the Java publisher to assign claims and sign original event bytes. */
    create: (form: PublishForm): Promise<Envelope> =>
        checked(envelopeSchema, '/api/publisher/envelopes', form),
    /** Compare fixed policies without a ledger write or live configuration change. */
    compare: (envelope: Envelope): Promise<Comparison> =>
        checked(comparisonSchema, '/api/workbench/evaluations', envelope),
    /** Build a controlled fixture from an authenticated original baseline. */
    experiment: (scenario: Scenario, envelope: Envelope): Promise<Fixture> =>
        checked(fixtureSchema, '/api/workbench/experiments', { scenario, envelope }),
    /** Read the receiver's durable receipt, independently of session history. */
    receipt: (id: string): Promise<Receipt> =>
        checked(receiptSchema, '/api/workbench/receipts/' + encodeURIComponent(id)),
    /** Preserve expected 4xx rejection outcomes as evidence, not generic network exceptions. */
    async deliver(envelope: Envelope): Promise<Delivery> {
        const { response, json } = await request('/api/workbench/deliveries', envelope);
        if (response.ok) {
            const parsed = receiptSchema.safeParse(json);
            if (!parsed.success)
                throw new WorkbenchError(
                    'The receiver returned an invalid receipt.',
                    'INVALID_RESPONSE',
                );
            return { kind: 'receipt', httpStatus: response.status, receipt: parsed.data };
        }
        const parsed = problemSchema.safeParse(json);
        if (!parsed.success)
            throw new WorkbenchError(
                'The receiver returned an invalid problem response.',
                'INVALID_RESPONSE',
            );
        return {
            kind: response.status >= 500 ? 'unavailable' : 'rejection',
            httpStatus: response.status,
            problem: parsed.data,
        };
    },
};
