# Milestone 06B: end-to-end migration rehearsal

This is the final local reference milestone. It changes the **live receiver's**
startup requirement across four deployments while retaining one publisher, the
same public trust, the same ledger and the same three original signed events.
The existing 06A lifecycle/crash story remains available independently.

## Run the complete rehearsal

On Windows, from the laboratory repository:

    .\lab.ps1 rehearsal-demo
    .\lab.ps1 compose-rehearsal

The first command allocates native service JVMs. The second builds the checked-out
source and allocates actual local Compose containers. Both own fresh runtime
state, fail on any unexpected outcome, and finish by removing only their own
runtime resources. Neither connects to the interactive workbench.

On Linux/macOS, with the documented JDK 25 and Node/npm toolchains:

    npm ci --prefix workbench --no-audit --no-fund
    ./gradlew rehearsalDemo --console=plain
    ./gradlew composeRehearsalDemo --console=plain

Docker is required only for the container command. It accepts a local Unix-socket
or named-pipe context, with the same ownership controls as 06A. The image is built
before events are signed, so build duration cannot consume event freshness.

## The actual deployment sequence

| Phase | Receiver startup policy | Ed25519 only | Dual signature | ML-DSA-65 only |
| ---: | --- | --- | --- | --- |
| 1 | CLASSICAL_ONLY | 201: create | 201: create | 403: reject |
| 2 | SHADOW_PQC | 200: original receipt | 200: original receipt | 403: reject |
| 3 | BOTH_REQUIRED | 403: reject | 200: original receipt | 403: reject |
| 4 | PQC_REQUIRED | 403: reject | 200: original receipt | 201: create |

These statuses are independent expectations, written before the HTTP calls.
They are not calculated by calling the production policy evaluator.

The driver creates each envelope exactly once through the real publisher API.
Every delivery goes through that surviving publisher's fixed HTTP gateway to the
receiver. Three controlled receiver replacements apply the next startup policy,
retain both endpoint mappings and preserve its database volume. Actual process
or container identities prove replacement and publisher continuity; only boolean
comparisons enter public reports.

There is no new HTTP policy editor. The control boundary permits exactly the next
reviewed step and rejects repeated, skipped or reversed steps before stopping a
receiver. The interactive Compose deployment keeps its BOTH_REQUIRED default;
the isolated rehearsal uses an owned, enum-derived Compose overlay.

PQC_REQUIRED is the final migration target, not a claim that every step accepts a
strict subset of the previous one. It keeps PQC mandatory while retiring the
classical requirement, deliberately admitting PQC-only senders that BOTH_REQUIRED
rejected. SHADOW_PQC retains classical enforcement while the Java observations
expose the PQC evidence.

## History and current permission are different facts

A classical event first accepted under CLASSICAL_ONLY has a durable receipt.
Under BOTH_REQUIRED and PQC_REQUIRED, its exact retry returns POLICY_REJECTED.
The original receipt can still be queried, with the same receipt ID, event,
artifact, version, first-acceptance time, acceptedPolicy and acceptedEvidence.

The response's enforcedPolicy describes the **current** receiver configuration.
It may change across deployments. It is intentionally excluded from the
immutable first-acceptance comparison, then separately checked against the live
phase. The original acceptedPolicy is never rewritten to imply that a classical
acceptance satisfied a later PQC requirement.

All three sender profiles receive real Java signature observations in every
phase. Rejected PQC-only events have no result until phase four. Unsigned
CLASSICAL_ONLY override fields are rejected under both PQC-mandatory policies.
Public generation metadata is compared in full, excluding only observedAt;
policy changes must not rotate keys or replace trust.

Real UTC freshness and the existing five-minute age/thirty-second tolerance
remain active. The rehearsal does not freeze the clock, renew old signatures
between phases or bypass verification because a receipt already exists.

## Inspect without starting the services

Each invocation creates a new directory under:

- Native: build/reports/policy-rehearsal/run-UUID/
- Compose: build/reports/compose-policy-rehearsal/run-UUID/

The public bundle contains:

| File | Purpose |
| --- | --- |
| report.html | Standalone, responsive, script-free English summary and compatibility matrix |
| report.md | Human-readable rollout, exact HTTP timeline and semantic assertions |
| report.json | Schema-2 structured evidence and completed policyPhases |
| http/ | Exact synthetic requests and strictly validated public responses |
| SHA256SUMS | Digests of every referenced artifact, including the HTML view |

Open report.html locally, or serve the bundle with any trusted local static
server. It has no remote font, script, image or application dependency.
The checked-in example under [evidence/06b-compose](evidence/06b-compose/report.md)
is one dated actual run, not a template or a promise about future executions.

Completed phase summaries are recorded only after that phase's actual delivery,
historical-read and invariant checks succeed. Operational failure or interruption
retains honest partial exchanges and returns a nonzero exit. PASS requires all
four completed phases, every assertion and resource cleanup. A missing manifest
means bundle generation did not finish.

Schema-1 05A/06A bundles retain their existing shape. Schema 2 adds completed
policyPhases with live configuration and delivery exchange references. The
unsigned checksums identify bytes; they do not independently authenticate a bundle.
Private runtime trees, account secrets, process identities, absolute paths and
raw Docker diagnostics are excluded.

## Verification and completion boundary

The ordinary Java tests execute the complete rehearsal in actual child JVMs.
Focused tests additionally cover the allowed transition sequence, unchanged
fixed-policy lifecycle behavior, receiver-only Compose control, phase ordering,
incomplete evidence, safe HTML rendering and checksum consistency. Controlled
Docker-command tests cover orchestration only; the explicit Compose rehearsal
verifies real containers and real HTTP verdicts.

The same strict compilation, Javadoc, formatting, architecture, dependency
verification and per-module 90% line-coverage gates remain enabled. The prepared
workflow includes the native rehearsal and retains its public artifacts; remote
CI execution is a separate action.

This completes the reference project's planned local scope. Deployment
authentication, remote trust distribution, production key custody, post-quantum
TLS and hosted operation require a separate project decision. No such capability
is implied by the local experiments. Existing benchmarks retain their independent
JMH methodology; functional HTTP durations are not comparative performance data.

The 06A/06B source and documentation are included in the
[v0.2.0 reference release](releases/0.2.0.md). The separate bilingual portfolio
remains a local site; source publication does not deploy it or the services.

## Initial local verification record

Verified on 16 November 2026 using Windows 11, Temurin JDK 25.0.5 and actual Linux
containers on the local Docker Desktop engine:

| Evidence | Observed result |
| --- | --- |
| Java tests | 228 passed; zero failures, errors or skipped tests |
| Frontend tests | 17 passed |
| Cryptographic interoperability probe | 53 passed |
| Original policy HTTP demonstration | 23 passed |
| Retained native 06A lifecycle story | 108 assertions / 32 HTTP attempts |
| Native four-phase rehearsal | PASS: 4/4 phases, 165 assertions / 49 HTTP attempts |
| Actual Compose four-phase rehearsal | PASS: 4/4 phases, 165 assertions / 49 HTTP attempts |
| Line coverage | All seven modules above 90%; integration-app 93.25% |
| Windows launcher | The documented rehearsal-demo command also passed all 165 checks |

Combined command:

    .\gradlew.bat check assemble probe :integration-app:demo migrationDemo rehearsalDemo composeRehearsalDemo --max-workers=2 --dependency-verification=strict --console=plain

The native recorded run is 634cd0b7-79e3-4a71-aeef-a791ba25abdf; the Compose
example is 475f288d-cef6-4e07-8b06-265810be59d3. A subsequent Windows launcher
run independently completed the same plan. No remote CI execution, new release
or hosted deployment was performed at that initial checkpoint. Release
verification and publication are tracked separately from this historical run.

Final presentation checks passed: the static portfolio built 25 pages and
verified 768 internal links/assets plus all page HTTP responses. English/Hungarian
switching was observed in the browser. The Hungarian case study fit a 390 × 844
viewport without horizontal document overflow; the default viewport was restored.
The downloaded 121,719-byte evidence ZIP matched its recorded SHA-256.
All three displayed original JPEGs matched the Lab's source bytes.

The reviewed tracked/untracked source candidate contained 344 files; the pinned
Gitleaks scan found no leaks. All 22 built Java archives retained the required
legal files, and the prepared workflow passed actionlint. The existing local
interactive Compose project remained separate from every rehearsal-owned project.
