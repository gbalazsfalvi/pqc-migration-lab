# PQC Migration Lab operating guide

This is a standalone local reference project. All source, documentation, comments and output are English.

## Current scope

Milestones one through 06B implement the complete local reference scope: cryptographic interoperability, a Spring Boot integration, an English React workbench, JMH benchmarks, durable keys, separate JVMs/Compose containers and a four-phase live-policy rehearsal. See `docs/milestone-06b.md` and `docs/milestone-06a.md`; keep the retained 05A reference available. Do not expand scope unless requested. The protocol and application rules remain independent of Spring.

Milestone 05B adds a publication review, scoped build-tool security fixes, retained upstream notices, a screenshot-led README and an English/Hungarian case study in the separate Astro portfolio; see `docs/portfolio-case-study.md`. The source is public at `gbalazsfalvi/pqc-migration-lab`, with Windows/Linux verification in GitHub Actions. The user selected Apache-2.0, authorized the initial v0.1.0 release, and now authorized publishing 06A/06B to main as v0.2.0; see `docs/releases/0.2.0.md`. Services still run locally. Video work, hosted application deployment and portfolio deployment remain separate decisions.

## Boundaries

- `lab-core` has zero production dependencies and no JCA, Jackson or provider imports.
- `crypto-jca` and `dsse-json` depend only on the core and their own technology.
- `technical-probe` assembles the adapters and produces evidence.
- `benchmarks` measures the unchanged public protocol/JCA/JSON adapters. It never depends on live integration services or changes provider ordering.
- Benchmark keys remain ephemeral. Never serialize a workload, key pair or private key into a report.
- Keep crypto, DSSE and JSON operation boundaries explicit; verification setup must happen outside its timed loop.
- Do not publish incomplete matrices, hide the measurement profile, invent missing uncertainty or use smoke results as comparative evidence.
- Keep build-tool security constraints scoped to their actual classpath or component. Do not restore obsolete vulnerable artifact allowances or disable dependency verification to pass a build.
- README screenshots must come from actual local synthetic observations, retain truthful captions and have their capture provenance documented. Never invent CI badges, verdicts or benchmark results.
- JMH-generated timing loops alone are excluded from coverage; handwritten benchmarks and report code retain the 90% gate.
- `integration-core` depends only on `lab-core`; it owns policy, identity/freshness checks and persistence ports.
- `integration-app` provides strict single-role publisher/receiver processes, an initializer and actual HTTP demonstrations; the original dual-context launcher remains a legacy reference.
- `workbench` is a React/TypeScript client, built by Vite and served from the publisher's packaged resources.
- Cryptographic decisions, policy evaluation and claim validation belong to Java. Never infer trust from signature length, key hints or a browser-side calculation.
- Read-only evaluation has no ledger capability. UI policy selection never changes the live receiver requirement.
- Only delivery can create a durable release. Session history is a bounded in-memory snapshot, not a ledger browser.
- Receiver request handling must never depend on publisher signing capabilities.
- Never register or reorder cryptographic providers globally. Pass provider instances explicitly.
- Use platform/library cryptography. Never implement cryptographic mathematics here.
- Keep original verified payload bytes. DSSE key IDs are unauthenticated hints, not trust decisions.
- Preserve DSSE's standard and URL-safe base64 support and forward-compatible unknown envelope fields.
- Reject ambiguous JSON, malformed encodings and resource-limit violations.
- Probe and benchmark keys stay in memory. Managed integration keys persist only in the publisher's authenticated AES-256-GCM vault, with an owner-only external account secret. Never log keys, commit runtime material, or include private encodings in reports or API responses.
- The receiver receives only independently imported public verification ports and public metadata; its directory must never capture a private manager, signer, key pair or private-key loader.
- Hold current public trust through durable reception. Revocation must wait for any already-verified commit and apply to every subsequent retry.
- Separate-process trust uses a stable OS lock inode and public-only atomic snapshots. Keep the receiver's shared file lease through durable application and the publisher's exclusive lease through private/public commit.
- Flush the pending fence before private persistence. Incomplete publication must reject receiver operations and new publisher signing until authenticated startup recovery; never remove a pending marker merely to pass a check.
- Receiver containers run as a separate non-root UID and mount only their own ledger plus read-only public trust. Never mount publisher keys, wrapping secrets or the Docker socket into receiver/application containers.
- Host publications stay on IPv4 loopback. Compose-only DNS names are fixed bootstrap choices, never HTTP-supplied targets; preserve Host/Origin rejection for foreign browser pages.
- Keep `WRITE_DELAY=0` on the local H2 ledger: an acknowledged receipt must survive immediate receiver termination, not only graceful context shutdown.
- Compose demonstrations own generated `pqc06a-...` or `pqc06b-...` projects, local-only Docker contexts, separate ports and volumes. Cleanup must target only that owned project. Raw Docker diagnostics stay under ignored `.local/compose-demo/`, never in public reports.
- Rotate both signing algorithms from one snapshot. Persist new state before publishing it under the exclusive lifecycle lease.
- Drop old private encodings on rotation/revocation; preserve public history and original release receipts.
- Missing, malformed, unauthenticated, broadly accessible or locked vault material must fail closed. Never repair it by silently generating replacement trust.
- Lifecycle writes require the reviewed revision. No HTTP operation may weaken the receiver's migration policy, extend overlap arbitrarily or reactivate a revoked generation.
- Receiver policy and trusted keys come from local configuration, never unsigned request fields or key hints.
- The 06B rehearsal advances exactly CLASSICAL_ONLY → SHADOW_PQC → BOTH_REQUIRED → PQC_REQUIRED by replacing only its owned receiver. Preserve the publisher, endpoints, public trust and ledger; never add a wire-level policy override.
- Compare original receipt fields independently of current enforcedPolicy. acceptedPolicy and first-acceptance evidence remain immutable; current policy must still reject previously accepted events that no longer qualify.
- Schema-2 rehearsal reports require all four completed phase observations before PASS. Only actual completed rows enter the portable HTML summary; include it in the checksum manifest.
- Both-required means both algorithms for the same trusted publisher and payload.
- Reverify every retry under the current policy before consulting durable idempotency.
- Apply the release and reserve its issuer-scoped event ID in one database transaction.
- Never edit an applied Flyway migration; add a new version.
- The migration demonstration owns a new system-temporary workspace and explicit independent account secret. Never attach it to an existing workbench, reuse interactive runtime files, or accept caller-supplied remote targets.
- Between demo runtime reconstructions retain only public envelopes, metadata and receipts. Observe through actual HTTP; never inspect private managers or ledger adapters to manufacture evidence.
- Evidence contains only synthetic request bytes and reviewed public response schemas. Do not collect runtime trees, headers, exception messages, usernames or absolute runtime paths. Reject unknown response fields before raw export.
- Demo PASS requires every planned phase, assertion and workspace cleanup to complete. Failures stop dependent actions, retain honest partial observations and return a nonzero exit. Checksums identify bytes; they do not authenticate an unsigned report.

## Code quality

Project-authored code, documentation and original screenshots use Apache-2.0. Keep LICENSE, NOTICE and all applicable third-party notices intact. Java archives include those legal files under META-INF. Use the same release version in Gradle and the workbench package metadata, and never tag or publish a revision that has not passed its required checks.

Document every type and method with useful English Javadoc. Explain security decisions and unusual implementation details with inline comments. Prefer immutable data, constructor injection and small focused classes. Keep comments in sync with behaviour.

## Verification

Use JDK 25, Node.js 24.14+ in the 24 LTS line, npm 11 and the checked-in Gradle wrapper. On Windows use `gradlew.bat`; on Unix use `./gradlew`. Install pinned browser dependencies with `npm ci --prefix workbench` before direct Gradle builds. The Windows launcher installs them when its dependency fingerprint changes. The independent `probe` and `benchmark` tasks do not require Node.

- `gradlew.bat spotlessApply`: apply the configured Java formatter.
- `gradlew.bat clean check`: compile with warnings as errors, test, check formatting and generate API documentation/coverage.
- `gradlew.bat probe`: run the technical proof and generate reports.
- `gradlew.bat :integration-app:demo`: run the policy matrix and attack cases over real HTTP.
- `lab.ps1 migration-demo` or `gradlew.bat migrationDemo`: run the separate-JVM lifecycle story, crash/outage recovery and independent role restarts; write public JSON/Markdown evidence with a checksum manifest.
- `lab.ps1 compose-demo` or `gradlew.bat composeMigrationDemo`: build and exercise actual independent local service containers, then remove only the demo-owned volumes.
- `lab.ps1 rehearsal-demo` or `gradlew.bat rehearsalDemo`: execute the four live receiver policies in native child JVMs and write HTML/Markdown/JSON/checksum evidence.
- `lab.ps1 compose-rehearsal` or `gradlew.bat composeRehearsalDemo`: build and verify the same policy rehearsal in an owned local Compose project.
- `lab.ps1 compose-up` / `compose-down`: build/start or stop the interactive local Compose deployment; ordinary down preserves its named volumes.
- `lab.ps1 init`, `receiver`, `publisher`: initialize and run native persistent service roles in separate terminals.
- `lab.ps1 legacy-migration-demo`: retain the original one-JVM 05A scenario for comparison.
- `lab.ps1 serve -Policy BOTH_REQUIRED`: keep the local publisher and receiver running.
- `lab.ps1 benchmark -BenchmarkProfile standard`: run the complete forked JMH matrix.
- `gradlew.bat benchmarkSmoke`: verify the actual forked harness without a performance threshold.
- `npm run check --prefix workbench`: check frontend formatting and focused unit tests.
- `npm run dev --prefix workbench`: optional loopback Vite preview, with a fixed publisher API proxy.

Changes to security boundaries require negative tests and an architecture check. Do not weaken verification just to pass a provider or malformed-input test.

Keep the workbench visually independent of the portfolio: navy navigation, cool light surfaces and a blue accent. Reserve red/green for semantic outcomes. Preserve keyboard access, native form labels, reduced motion and mobile layouts. Bundle fonts locally with their licence notices. Never add a remote API target, wildcard origin allowlist or browser-side private keys.
