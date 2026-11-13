import { afterEach, describe, expect, it, vi } from 'vitest';
import { labApi, WorkbenchError } from './client';

const envelope = { payloadType: 'test/type', payload: '', signatures: [] };

/** Mock only transport to test the typed boundary; acceptance decisions are never calculated here. */
function respond(status: number, body: unknown) {
    vi.stubGlobal(
        'fetch',
        vi.fn().mockImplementation(() =>
            Promise.resolve(
                new Response(JSON.stringify(body), {
                    status,
                    headers: { 'Content-Type': 'application/json' },
                }),
            ),
        ),
    );
}
afterEach(() => vi.unstubAllGlobals());

describe('same-origin API boundary', () => {
    it('reads actual receiver configuration through its fixed gateway route', async () => {
        respond(200, {
            javaVersion: '25',
            protocol: 'DSSEv1',
            receiverPolicy: 'BOTH_REQUIRED',
            issuer: 'publisher',
            audience: 'receiver',
            maximumAgeSeconds: 300,
            futureToleranceSeconds: 30,
        });
        expect((await labApi.config()).receiverPolicy).toBe('BOTH_REQUIRED');
        expect(fetch).toHaveBeenCalledWith(
            '/api/workbench/config',
            expect.objectContaining({ credentials: 'omit', cache: 'no-store' }),
        );
    });
    it('preserves a policy rejection as a normal observed outcome', async () => {
        respond(403, { code: 'POLICY_REJECTED', status: 403 });
        expect(await labApi.deliver(envelope)).toEqual({
            kind: 'rejection',
            httpStatus: 403,
            problem: { code: 'POLICY_REJECTED', status: 403 },
        });
    });
    it('does not label an upstream failure as a successful security rejection', async () => {
        respond(503, { code: 'RECEIVER_UNAVAILABLE' });
        expect((await labApi.deliver(envelope)).kind).toBe('unavailable');
    });
    it('rejects stale or invalid API response shapes', async () => {
        respond(200, { fake: 'receipt' });
        await expect(labApi.deliver(envelope)).rejects.toThrow(WorkbenchError);
        await expect(labApi.config()).rejects.toThrow('does not match');
    });
    it('reports a safe connection failure without echoing server text', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('SENSITIVE_TEST_MARKER')));
        await expect(labApi.config()).rejects.toThrow('local Java backend');
    });
});
