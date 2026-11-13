# PQC Migration Lab operating guide

This is a standalone local reference project. All source, documentation, comments and output are English.

## Current scope

Milestones one through 05A are implemented: cryptographic interoperability, a local Spring Boot integration, an English React workbench, reproducible JMH benchmarks, durable key lifecycle management and an isolated one-command migration demonstration with public evidence. Implement later milestones only when requested. The protocol and application rules remain independent of Spring.

Milestone 05B part one adds a local publication review, scoped build-tool security fixes, retained upstream notices and a screenshot-led README. Part two adds an English/Hungarian case study in the separate local Astro portfolio, using the original screenshots and dated evidence; see `docs/portfolio-case-study.md`. The user authorized publication to `gbalazsfalvi/pqc-migration-lab`; the source is public and the actual Windows/Linux workflow records remote verification. Project licensing, tagged release creation, video work and hosted deployment remain separate decisions; see `docs/publication-review.md`.

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
- `integration-app` composes two isolated Boot contexts, technology adapters and a real loopback HTTP demonstration.
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
- Rotate both signing algorithms from one snapshot. Persist new state before publishing it under the exclusive lifecycle lease.
- Drop old private encodings on rotation/revocation; preserve public history and original release receipts.
- Missing, malformed, unauthenticated, broadly accessible or locked vault material must fail closed. Never repair it by silently generating replacement trust.
- Lifecycle writes require the reviewed revision. No HTTP operation may weaken the receiver's migration policy, extend overlap arbitrarily or reactivate a revoked generation.
- Receiver policy and trusted keys come from local configuration, never unsigned request fields or key hints.
- Both-required means both algorithms for the same trusted publisher and payload.
- Reverify every retry under the current policy before consulting durable idempotency.
- Apply the release and reserve its issuer-scoped event ID in one database transaction.
- Never edit an applied Flyway migration; add a new version.
- The migration demonstration owns a new system-temporary workspace and explicit independent account secret. Never attach it to an existing workbench, reuse interactive runtime files, or accept caller-supplied remote targets.
- Between demo runtime reconstructions retain only public envelopes, metadata and receipts. Observe through actual HTTP; never inspect private managers or ledger adapters to manufacture evidence.
- Evidence contains only synthetic request bytes and reviewed public response schemas. Do not collect runtime trees, headers, exception messages, usernames or absolute runtime paths. Reject unknown response fields before raw export.
- Demo PASS requires every planned phase, assertion and workspace cleanup to complete. Failures stop dependent actions, retain honest partial observations and return a nonzero exit. Checksums identify bytes; they do not authenticate an unsigned report.

## Code quality

Document every type and method with useful English Javadoc. Explain security decisions and unusual implementation details with inline comments. Prefer immutable data, constructor injection and small focused classes. Keep comments in sync with behaviour.

## Verification

Use JDK 25, Node.js 24.14+ in the 24 LTS line, npm 11 and the checked-in Gradle wrapper. On Windows use `gradlew.bat`; on Unix use `./gradlew`. Install pinned browser dependencies with `npm ci --prefix workbench` before direct Gradle builds. The Windows launcher installs them when its dependency fingerprint changes. The independent `probe` and `benchmark` tasks do not require Node.

- `gradlew.bat spotlessApply`: apply the configured Java formatter.
- `gradlew.bat clean check`: compile with warnings as errors, test, check formatting and generate API documentation/coverage.
- `gradlew.bat probe`: run the technical proof and generate reports.
- `gradlew.bat :integration-app:demo`: run the policy matrix and attack cases over real HTTP.
- `lab.ps1 migration-demo` or `gradlew.bat migrationDemo`: run the isolated lifecycle story through two disk-only restarts and write public JSON/Markdown evidence with a checksum manifest.
- `lab.ps1 serve -Policy BOTH_REQUIRED`: keep the local publisher and receiver running.
- `lab.ps1 benchmark -BenchmarkProfile standard`: run the complete forked JMH matrix.
- `gradlew.bat benchmarkSmoke`: verify the actual forked harness without a performance threshold.
- `npm run check --prefix workbench`: check frontend formatting and focused unit tests.
- `npm run dev --prefix workbench`: optional loopback Vite preview, with a fixed publisher API proxy.

Changes to security boundaries require negative tests and an architecture check. Do not weaken verification just to pass a provider or malformed-input test.

Keep the workbench visually independent of the portfolio: navy navigation, cool light surfaces and a blue accent. Reserve red/green for semantic outcomes. Preserve keyboard access, native form labels, reduced motion and mobile layouts. Bundle fonts locally with their licence notices. Never add a remote API target, wildcard origin allowlist or browser-side private keys.
