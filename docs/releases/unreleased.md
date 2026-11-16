# Development notes incorporated into version 0.2.0

The 06A/06B development notes below are incorporated into the
[0.2.0 reference release](0.2.0.md). The earlier v0.1.0 tag remains available.
The services still run locally; source publication does not deploy an application.

## 06A: independent service processes

- Single-role publisher/receiver JVMs and a public-trust initializer.
- Actual local Compose deployment with separate non-root users and volumes.
- Public-only snapshots, stable OS leases and pending-publication recovery.
- Independent crash/restart verification and durable acknowledgement handling.

## 06B: complete policy rollout

- CLASSICAL_ONLY → SHADOW_PQC → BOTH_REQUIRED → PQC_REQUIRED.
- Three receiver-only replacements, one surviving publisher and unchanged trust/ledger.
- Classical, dual and PQC-only original envelopes reused across all four phases.
- Current-policy enforcement separated from immutable first-acceptance evidence.
- Actual native and Compose rehearsals: 165 assertions, 49 HTTP attempts each.
- Portable HTML, Markdown, schema-2 JSON, exact public bodies and checksums.
- Reviewed example bundle, original report screenshot and bilingual portfolio refresh.

## Local validation

On 16 November 2026: 228 Java tests, 17 frontend tests, 53 protocol checks and
23 original policy-demo expectations passed. The retained native 06A story passed
108 assertions over 32 requests. Both 06B topologies passed all four phases.
Strict compilation, Javadoc, formatting, architecture and dependency verification
passed; all seven modules remain above the unchanged 90% line-coverage gate.

Windows native JVMs and Linux containers on the local Docker Desktop engine were
exercised. The prepared Windows/Linux workflow has not been executed remotely
at this initial milestone checkpoint. Release CI is tracked separately by the
exact commit and tag. No new comparative JMH measurement is claimed.
