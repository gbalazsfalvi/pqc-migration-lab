# A migration story you can reproduce

## The engineering question

A valid signature is only one part of accepting an integration event. The receiver must know whose key it trusts, what the event means, which algorithms it requires and whether an old delivery is still acceptable now.

PQC Migration Lab makes those decisions visible using synthetic software releases. The publisher signs one payload with Ed25519 and ML-DSA-65. The receiver verifies the original bytes, validates signed claims and applies a release once in a durable transaction.

## Start with the automatic demonstration

Run `.\lab.ps1 migration-demo` from the repository root. See [the complete prerequisites and Unix command](milestone-05a.md#run-one-command). Open the new `report.md` path printed at the end.

Start by checking the result and completion state. A partial run remains useful evidence, but it is not a successful demonstration. Verify `SHA256SUMS` before reviewing a copied bundle.

## Follow the decisions

### One event, one business result

Find `original-delivery` and `original-replay`. The first returns HTTP 201, the second HTTP 200. Compare their referenced response files: receipt ID, event ID, acceptance time, accepted policy and original signature evidence agree.

The request digests also agree. This is an exact authenticated retry, not a second event that happens to use the same artifact name.

The earlier `evaluation-does-not-store` observation returns 404. Comparing policies did not reserve an event or create a business receipt.

### A stored receipt does not bypass today's policy

Find `stripped-evaluation` and `stripped-delivery`. The read-only Java matrix explains why a valid remaining classical signature satisfies the classical policies but fails BOTH_REQUIRED.

Delivery returns `POLICY_REJECTED` even though the original dual-signed event already has a receipt. The receiver rechecks current trust and policy before consulting idempotency. Otherwise an old receipt would become a route around stronger verification.

### Rotation preserves a controlled transition

Find `rotated-registry`. A new generation is ACTIVE, the previous one is RETIRING, and its verification deadline is ten minutes after retirement started.

The original event still receives its original receipt during overlap. Both active signing keys changed together. The application is not mixing an old Ed25519 signer with a new ML-DSA signer.

The next phase starts new Boot contexts and reconstructs trust from the encrypted vault. Compare `rotated-registry` with `rotation-restart-registry`: ignore only `observedAt`; the durable public state agrees.

### Revocation changes trust, not historical facts

Find `revoked-evaluation`, `revoked-replay` and `historical-receipt`. Current verification rejects the old generation. Querying the original acceptance record still returns the original evidence.

A receipt records what was accepted then. It is not a promise that those signing keys remain trusted now.

### The integration keeps working

Find `current-delivery`. The publisher creates a new event with the active generation and the receiver commits a distinct result.

After the second restart, the revoked original is still rejected, the historical receipt still agrees and the current event reuses its own receipt. This connects storage, trust and business behaviour across real runtime lifetimes.

## Inspect it manually

Run `.\lab.ps1 serve` and open `http://127.0.0.1:8781/`. The interactive workbench has its own persistent state; the automatic demonstration does not populate or modify it.

- **Workbench:** create a fresh dual-signed event, compare four policies and deliver it.
- **Key lifecycle:** inspect actual public generations, rotate both keys and review revocation consequences.
- **Run history:** revisit bounded in-memory observations in the current page session.
- **Protocol guide:** read how bytes, claims, policy and retries fit together.

Create fresh events for manual experiments: event freshness remains five minutes. A valid key overlap alone does not make an expired event acceptable.

## Describe the project accurately

The project demonstrates application-level signature migration using library/platform cryptography. It includes reproducible interoperability probes, policy and claim validation, transactional idempotency, key lifecycle management, a React workbench and independent JMH measurements.

The two HTTP servers run in one JVM. The local control API has no production operator authentication. This is neither a post-quantum TLS deployment nor a production KMS, and the evidence manifest is not independently signed.

Use [the threat model](threat-model.md) and [architecture decision](decisions/0001-signed-migration-laboratory.md) when discussing those boundaries. Publication, a video and the portfolio case study remain separate work.
