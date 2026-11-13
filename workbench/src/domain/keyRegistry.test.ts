import { afterEach, describe, expect, it, vi } from 'vitest';
import { keyRegistrySchema } from './contracts';
import { labApi } from '../api/client';

const id = '7cde10f9-d16c-4149-8da6-4e39d2a5c919';
/** Public-only fixture used exclusively for wire contracts; no browser-side trust verdict is computed. */
function registry(revision = 7) {
    return {
        managed: true,
        revision,
        issuer: 'release-publisher',
        activeGenerationId: id,
        observedAt: '2026-11-13T10:00:00Z',
        gracePeriodSeconds: 600,
        maximumGenerations: 16,
        generations: [
            {
                id,
                sequence: 1,
                state: 'ACTIVE',
                createdAt: '2026-11-13T10:00:00Z',
                retiringAt: null,
                acceptUntil: null,
                revokedAt: null,
                keys: [
                    {
                        id: id + ':ED25519',
                        algorithm: 'ED25519',
                        spkiSha256: 'a'.repeat(64),
                        spkiBytes: 44,
                    },
                    {
                        id: id + ':ML_DSA_65',
                        algorithm: 'ML_DSA_65',
                        spkiSha256: 'b'.repeat(64),
                        spkiBytes: 1974,
                    },
                ],
            },
        ],
    };
}

/** Returns a fresh response per call so body consumption cannot hide a stale mock. */
function respond(body: unknown, status = 200) {
    const handler = vi.fn(() =>
        Promise.resolve(
            new Response(JSON.stringify(body), {
                status,
                headers: { 'Content-Type': 'application/json' },
            }),
        ),
    );
    vi.stubGlobal('fetch', handler);
}
afterEach(() => vi.unstubAllGlobals());

describe('public key lifecycle boundary', () => {
    it('retains public identities and lifecycle metadata', () => {
        const parsed = keyRegistrySchema.parse(registry());
        expect(parsed.revision).toBe(7);
        expect(parsed.generations[0]?.keys).toHaveLength(2);
    });
    it('rejects private material or unexpected fields at either boundary', () => {
        expect(
            keyRegistrySchema.safeParse({ ...registry(), privateKey: 'FORBIDDEN_TEST_FIELD' })
                .success,
        ).toBe(false);
        const value = registry();
        const first = value.generations[0];
        if (!first) throw new Error('Fixture generation missing.');
        const key = first.keys[0];
        first.keys[0] = { ...key, pkcs8: 'FORBIDDEN_TEST_FIELD' } as (typeof first.keys)[number];
        expect(keyRegistrySchema.safeParse(value).success).toBe(false);
    });
    it('rejects malformed public fingerprints and unsupported lifecycle labels', () => {
        const value = registry();
        const first = value.generations[0];
        if (!first || !first.keys[0]) throw new Error('Fixture key missing.');
        first.keys[0].spkiSha256 = 'unverified-label';
        expect(keyRegistrySchema.safeParse(value).success).toBe(false);
        first.keys[0].spkiSha256 = 'a'.repeat(64);
        first.state = 'UNKNOWN';
        expect(keyRegistrySchema.safeParse(value).success).toBe(false);
    });
    it('fences rotation using the revision actually reviewed by the operator', async () => {
        respond(registry(8));
        expect((await labApi.rotateKeys(7)).revision).toBe(8);
        expect(fetch).toHaveBeenCalledWith(
            '/api/workbench/keys/rotate',
            expect.objectContaining({ method: 'POST', body: '{"expectedRevision":7}' }),
        );
    });
    it('sends only the configured generation and expected revision for revocation', async () => {
        respond(registry(8));
        await labApi.revokeKeys(7, id);
        expect(fetch).toHaveBeenCalledWith(
            '/api/workbench/keys/revoke',
            expect.objectContaining({
                method: 'POST',
                body: JSON.stringify({ expectedRevision: 7, generationId: id }),
            }),
        );
    });
    it('reports revision conflicts without automatically repeating a mutation', async () => {
        respond({ code: 'KEY_REVISION_CONFLICT' }, 409);
        await expect(labApi.rotateKeys(7)).rejects.toThrow('registry changed');
        expect(fetch).toHaveBeenCalledTimes(1);
    });
});
