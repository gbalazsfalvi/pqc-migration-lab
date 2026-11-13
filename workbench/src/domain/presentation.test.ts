import { describe, expect, it } from 'vitest';
import { abbreviate, deliveryLabel, displayPayload, formatBytes, formatTime } from './presentation';
import { comparisonSchema, envelopeSchema } from './contracts';

/** These tests cover display and wire boundaries; cryptographic correctness is tested by Java. */
describe('public evidence presentation', () => {
    it('decodes a payload for display without treating its claims as authenticated', () => {
        const raw = JSON.stringify({ eventId: 'untrusted-id', artifact: 'demo', version: '1' });
        const envelope = { payloadType: 'arbitrary/type', payload: btoa(raw), signatures: [] };
        expect(displayPayload(envelope)).toMatchObject({
            eventId: 'untrusted-id',
            artifact: 'demo',
            version: '1',
        });
        expect(envelopeSchema.parse({ ...envelope, policy: 'CLASSICAL_ONLY' })).toHaveProperty(
            'policy',
            'CLASSICAL_ONLY',
        );
    });
    it('handles binary, invalid base64 and non-object previews without crashing the workbench', () => {
        for (const payload of ['not base64!', '/w==', btoa('[]'), btoa('null')]) {
            expect(displayPayload({ payloadType: 't', payload, signatures: [] }).eventId).toBe(
                'Unknown event',
            );
        }
    });
    it('formats actual byte counts at the unit boundary', () => {
        expect(formatBytes(64)).toBe('64 B');
        expect(formatBytes(1024)).toBe('1.00 KB');
        expect(formatBytes(3309)).toBe('3.23 KB');
        expect(abbreviate('123')).toBe('123');
        expect(abbreviate('abcdefghijklmnop', 4, 3)).toBe('abcd…nop');
    });
    it('uses UTC and safely handles an unavailable observation time', () => {
        expect(formatTime('2026-11-13T08:00:00Z')).toBe('08:00:00 UTC');
        expect(formatTime('invalid')).toBe('—');
    });
    it('distinguishes expected rejection from operational unavailability', () => {
        expect(
            deliveryLabel({
                kind: 'rejection',
                httpStatus: 403,
                problem: { code: 'POLICY_REJECTED' },
            }),
        ).toBe('Delivery rejected');
        expect(
            deliveryLabel({
                kind: 'unavailable',
                httpStatus: 503,
                problem: { code: 'RECEIVER_UNAVAILABLE' },
            }),
        ).toBe('Receiver unavailable');
    });
    it('does not accept a missing or stringly typed comparison as observed evidence', () => {
        expect(
            comparisonSchema.safeParse({
                receiverPolicy: 'BOTH_REQUIRED',
                signatureEvidence: { ed25519Valid: 'true' },
            }).success,
        ).toBe(false);
    });
});
