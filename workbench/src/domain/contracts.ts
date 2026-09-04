import { z } from 'zod';

/** Runtime-checked contracts keep a stale or unavailable backend from becoming fabricated UI data. */
export const policies = ['CLASSICAL_ONLY', 'SHADOW_PQC', 'BOTH_REQUIRED', 'PQC_REQUIRED'] as const;
export const algorithms = ['ED25519', 'ML_DSA_65'] as const;
export const signingModes = ['ED25519_ONLY', 'ML_DSA_65_ONLY', 'DUAL'] as const;
export const scenarios = [
    'STRIP_PQC',
    'TAMPER_PAYLOAD',
    'WRONG_KEY',
    'EXPIRED_EVENT',
    'FUTURE_EVENT',
    'WRONG_AUDIENCE',
    'UNSIGNED_DOWNGRADE',
    'CONFLICTING_ID',
] as const;

export type Policy = (typeof policies)[number];
export type Algorithm = (typeof algorithms)[number];
export type SigningMode = (typeof signingModes)[number];
export type Scenario = (typeof scenarios)[number];
export type View = 'workbench' | 'keys' | 'history' | 'guide';

const policySchema = z.enum(policies);
const evidenceSchema = z.object({ ed25519Valid: z.boolean(), mlDsa65Valid: z.boolean() });

/** Unknown DSSE extensions survive transport; they never select receiver policy. */
export const envelopeSchema = z
    .object({
        payloadType: z.string(),
        payload: z.string(),
        signatures: z
            .array(z.object({ keyid: z.string().optional(), sig: z.string() }).passthrough())
            .max(4),
    })
    .passthrough();
export type Envelope = z.infer<typeof envelopeSchema>;

export const configSchema = z.object({
    javaVersion: z.string(),
    protocol: z.string(),
    receiverPolicy: policySchema,
    issuer: z.string(),
    audience: z.string(),
    maximumAgeSeconds: z.number().int().positive(),
    futureToleranceSeconds: z.number().int().nonnegative(),
});
export type LabConfig = z.infer<typeof configSchema>;

export const comparisonSchema = z.object({
    trustRevision: z.number().int().nonnegative().default(0),
    evaluatedAt: z.string(),
    receiverPolicy: policySchema,
    payloadSha256: z.string().length(64),
    envelopeBytes: z.number().int().nonnegative(),
    payloadBytes: z.number().int().nonnegative(),
    paeBytes: z.number().int().nonnegative(),
    signatureEvidence: evidenceSchema,
    signatures: z.array(
        z.object({
            index: z.number().int().nonnegative(),
            keyHint: z.string(),
            bytes: z.number().int().nonnegative(),
            verifiedAlgorithms: z.array(z.enum(algorithms)),
        }),
    ),
    policies: z
        .array(
            z.object({
                policy: policySchema,
                accepted: z.boolean(),
                rejectionCode: z.string().nullable(),
                steps: z.array(
                    z.object({
                        id: z.string(),
                        label: z.string(),
                        status: z.enum(['PASSED', 'FAILED', 'NOT_CHECKED']),
                        detail: z.string(),
                    }),
                ),
            }),
        )
        .length(4),
});
export type Comparison = z.infer<typeof comparisonSchema>;
export type PolicyObservation = Comparison['policies'][number];

/** Public-only lifecycle responses reject unexpected fields, including accidental private material. */
const registryKeySchema = z
    .object({
        id: z.string(),
        algorithm: z.enum(algorithms),
        spkiSha256: z.string().regex(/^[a-f0-9]{64}$/),
        spkiBytes: z.number().int().positive(),
    })
    .strict();
const generationSchema = z
    .object({
        id: z.string().uuid(),
        sequence: z.number().int().positive(),
        state: z.enum(['ACTIVE', 'RETIRING', 'RETIRED', 'REVOKED']),
        createdAt: z.string(),
        retiringAt: z.string().nullable(),
        acceptUntil: z.string().nullable(),
        revokedAt: z.string().nullable(),
        keys: z.array(registryKeySchema).length(2),
    })
    .strict();
export const keyRegistrySchema = z
    .object({
        managed: z.boolean(),
        revision: z.number().int().nonnegative(),
        issuer: z.string(),
        activeGenerationId: z.string().uuid().nullable(),
        observedAt: z.string(),
        gracePeriodSeconds: z.number().int().nonnegative(),
        maximumGenerations: z.number().int().nonnegative().max(16),
        generations: z.array(generationSchema).max(16),
    })
    .strict();
export type KeyRegistry = z.infer<typeof keyRegistrySchema>;
export type KeyGeneration = KeyRegistry['generations'][number];

export const receiptSchema = z.object({
    status: z.enum(['ACCEPTED', 'ALREADY_ACCEPTED', 'STORED']),
    receiptId: z.string().uuid(),
    eventId: z.string().uuid(),
    issuer: z.string(),
    artifact: z.string(),
    version: z.string(),
    acceptedAt: z.string(),
    enforcedPolicy: policySchema,
    acceptedPolicy: policySchema,
    acceptedEvidence: evidenceSchema,
});
export type Receipt = z.infer<typeof receiptSchema>;

export const problemSchema = z.object({
    code: z.string(),
    status: z.number().int().optional(),
    title: z.string().optional(),
});
export type Problem = z.infer<typeof problemSchema>;
export type Delivery =
    | { kind: 'receipt'; httpStatus: number; receipt: Receipt }
    | { kind: 'rejection' | 'unavailable'; httpStatus: number; problem: Problem };

export const fixtureSchema = z.object({
    scenario: z.enum(scenarios),
    note: z.string(),
    envelope: envelopeSchema,
});
export type Fixture = z.infer<typeof fixtureSchema>;

/** Form fields describe a new synthetic release; the Java publisher supplies all identity claims. */
export interface PublishForm {
    artifact: string;
    version: string;
    sha256: string;
    signingMode: SigningMode;
}

/** Session history stores public evidence in memory; it is neither a key store nor a ledger replica. */
export interface LabRun {
    id: string;
    createdAt: string;
    mode: SigningMode;
    baseline: Envelope;
    baselineComparison: Comparison;
    envelope: Envelope;
    comparison: Comparison;
    scenario: Scenario | null;
    note: string | null;
    delivery: Delivery | null;
    receipt: Receipt | null;
}
