# Roadmap

The portfolio story is a controlled migration of a working signed integration, supported by reproducible evidence.

## 01 · Signature and envelope proof — implemented

The current repository establishes Ed25519, ML-DSA-65, key-format interoperability and DSSE's exact byte contract. Run the CLI to reproduce the evidence.

## 02 · A working signed integration — implemented

The local Spring Boot publisher sends signed synthetic software releases to a receiver over real loopback HTTP. The framework-free application module owns policy and claim validation; a transactional H2 ledger stores one durable business result per issuer-scoped event ID.

The implemented receiver policies use an explicitly trusted publisher key registry:

| Policy                | Accepted evidence                                                                                 |
| --------------------- | ------------------------------------------------------------------------------------------------- |
| Classical baseline    | A valid trusted Ed25519 signature                                                                 |
| Post-quantum trial    | Independently record Ed25519 and ML-DSA outcomes while the classical requirement remains explicit |
| Both required         | A valid trusted signature from each required algorithm                                            |
| Post-quantum required | A valid trusted ML-DSA-65 signature                                                               |

Event identity, issuer, audience and freshness are signed into the payload. Atomic idempotency suppresses duplicate application, while conflicting identifiers and stale events are rejected. Signature stripping fails under both-required, including when a valid classical receipt already exists.

Run `lab.ps1 demo` to reproduce 23 HTTP expectations. See [the milestone contract](milestone-02.md) for the API, security decisions and remaining limits.

## 03 · Migration workbench — implemented

The English React and TypeScript interface inspects the real synthetic integration:

- Compare policy outcomes for the same envelope.
- Inspect public signature and envelope sizes.
- Trigger payload changes, signature stripping, wrong-key attempts and replay cases.
- Read precise reasons for rejection without exposing keys or sensitive input.

The workbench has its own restrained visual identity: navy navigation, cool light surfaces, clear evidence tables and a blue accent. Green and red communicate outcomes. It is independent of the portfolio landing page.

Java owns all verdicts. Read-only comparison has no ledger capability; delivery always uses the live receiver policy. The Spring Boot JAR serves the built UI. Run history, public JSON export, a protocol guide and actual durable receipt reads are included. See [the workbench contract](milestone-03.md).

## 04A · Measurements — implemented

The independent JMH module compares Ed25519, ML-DSA-65 and sequential dual operations. It separates key generation, raw signatures, DSSE operations and JSON coding, with actual transport-size observations outside timing. Named profiles preserve warm-up, forks, samples, runtime/provider metadata and uncertainty in raw and readable evidence.

The default standard profile covers 57 cases. Reduced smoke runs verify the forked harness without becoming comparative performance claims. The module remains separate from the live workbench. See [the measurement method](milestone-04a.md).

## 04B · Key lifecycle — implemented

Managed keys survive complete restarts in an authenticated encrypted local vault, using an external owner-only account secret. Rotation moves both signing algorithms atomically to a new generation with a bounded verification overlap. Revocation persists, rejects old retries and preserves historical receipts.

The receiver holds public capabilities only. Its operation-scoped trust lease covers durable application, preventing revocation from completing between verification and commit. Revision-fenced controls and public fingerprint/state inspection are available in the workbench. See [the lifecycle contract](milestone-04b.md).

## 05A · Reproducible migration story — implemented

Run `lab.ps1 migration-demo` or the Gradle `migrationDemo` task. A fresh temporary managed integration demonstrates acceptance, exact replay, downgrade rejection, rotation overlap, persistent revocation and unchanged historical receipts through three full runtime phases. It never attaches to the interactive workbench.

The scenario writes actual public HTTP request/response artifacts, a structured JSON report, an English Markdown walkthrough and a SHA-256 manifest. Failed or interrupted runs retain honest partial evidence and return a nonzero exit. See [the demonstration contract](milestone-05a.md), [walkthrough](walkthrough.md), [threat model](threat-model.md) and [architecture decision](decisions/0001-signed-migration-laboratory.md).

## 05B · Public reference project

Part one is implemented locally: [publication review](publication-review.md), scoped fixes for two build-time dependency advisories, complete upstream fixture-notice retention, a security policy and a rewritten README with three actual screenshots. No project licence, release or remote publication was created.

Part two is implemented in the separate local Astro portfolio: an English/Hungarian case study, three original application screenshots, dated verification figures, a homepage feature and reciprocal links to the earlier PQC research. See [the case-study handover](portfolio-case-study.md).

The user subsequently authorized the public [source repository](https://github.com/gbalazsfalvi/pqc-migration-lab). Windows/Linux verification runs through the repository's actual Actions workflow; the initial cold-cache metadata correction is recorded in [the publication review](publication-review.md#public-source-follow-up).

The owner selected Apache-2.0 and authorized the first tagged reference release, [version 0.1.0](releases/0.1.0.md). The repository includes the full licence, project attribution, preserved third-party terms and consistent release metadata. Java archives carry the legal files under META-INF, and CI builds the distributable archives as well as checking the source.

A demonstration video and hosted deployment are optional future work. Keep the dependency and fixture attribution current.

The application still runs locally. Public source publication does not create a hosted workbench or publish the separate portfolio website.

## 06A · Separate service processes and local Compose

The local implementation adds independent publisher and receiver JVMs, a multi-stage
Docker build and an isolated Compose network with separate persistent volumes.
The receiver has no publisher vault/secret mount and reads public trust through a
read-only volume and an operation-scoped OS file lease. Pending publication fences
partial private/public commits until authenticated publisher startup recovery.

The expanded migration story exercises actual receiver termination, gateway
unavailability, independent role restarts, durable receipt reuse and the original
rotation/revocation phases. Native and Compose runs produce the same reviewed
public evidence format. The 05A one-JVM program remains available separately.

See [the milestone contract and commands](milestone-06a.md). This work is local;
publishing it, creating another release and hosted operation are separate actions.

## 06B · End-to-end migration rehearsal — final local scope

The receiver actually advances through CLASSICAL_ONLY, SHADOW_PQC, BOTH_REQUIRED
and PQC_REQUIRED using three receiver-only deployments. One publisher, one ledger,
unchanged public trust and three original envelopes cross all four phases.
Compatibility, rejection after earlier acceptance, immutable original receipts
and unsigned downgrade rejection are observed through real HTTP.

Native and actual Compose commands produce standalone HTML, Markdown, schema-2
JSON, exact public bodies and checksums. A reviewed example and the bilingual
portfolio explain the results without requiring readers to run Java.
See [the final milestone contract](milestone-06b.md).

The reference is complete at this boundary. The 06A/06B implementation is included
in the [v0.2.0 source release](releases/0.2.0.md). Hosted operation and portfolio
deployment remain separate actions. Additional infrastructure is not required
to demonstrate the project.
