# Milestone 05A · Local verification

Verified on 13 November 2026. All execution remained local; no repository publication, deployment or remote CI run was performed.

## Toolchain and gates

- Windows host; Temurin JDK 25.0.5+8.
- Node.js 24.14.0 and npm 11.9.0.
- Checked-in Gradle wrapper; dependency locks and verification remained enabled.
- 210 Java tests passed, with zero failures, errors or skipped tests.
- 17 frontend tests passed.
- Strict Java compilation, Javadoc, Java/frontend formatting, TypeScript, architecture and per-module 90% line-coverage gates passed.
- Integration-app line coverage: 95.62%. Integration-core: 99.35%.
- Existing functional probe: 53 successful checks.
- Existing four-policy HTTP demonstration: 23 successful expectations.

## Actual migration command

The documented `.\lab.ps1 migration-demo` command completed with PASS:

| Observation | Result |
| --- | --- |
| Actual HTTP requests | 28 |
| HTTP and semantic assertions | 91 passed |
| Fully reconstructed runtimes | 3 |
| Shutdown/reconstruction boundaries | 2 |
| Manifest entries | 47 |
| Source and archived artifact checksums | All matched |
| Original interactive public registry | Unchanged at revision 3 |
| Launcher's process-local Java setting | Restored after execution |

The scenario verified acceptance, exact replay, read-only evaluation without a stored receipt, signature-stripping rejection, rotation overlap, disk-reconstructed public identity, durable revocation, old-retry rejection, unchanged original receipt fields, new-generation delivery and current-event replay after the final restart.

The public report and exact HTTP artifacts were also archived under the ignored `.local/migration-evidence/` directory so a later Gradle clean does not remove that local review copy.

## Source-only verification

A separate system-temporary copy contained the project source/configuration only, copied from the local Git inventory and checked file-by-file with SHA-256. It began without `.local`, project build outputs or `workbench/node_modules`.

Using the documented external JDK and installed Node/npm, verification ran:

```powershell
npm ci --prefix workbench --no-audit --no-fund
.\gradlew.bat clean check probe :integration-app:demo migrationDemo --no-build-cache --console=plain
```

This rebuilt the Java modules and frontend, passed the full gates, and reproduced the 28-request/91-assertion story. Shared dependency caches were available. This was a fresh source copy on the same Windows host, not a fresh OS installation, offline build or an executed Linux CI job.

## Negative and interrupted execution

Automated checks additionally verified:

- An actual unexpected HTTP 503 creates FAIL and stops dependent actions.
- A real HTTP 200 followed by a contradictory semantic policy creates FAIL.
- A peer closing before response headers creates INCOMPLETE without an invented HTTP status.
- An unreviewed private response field creates INCOMPLETE and no raw response artifact.
- Nested private fields, invalid containers, duplicate JSON and non-boolean evidence are rejected.
- Startup/storage exceptions use safe categories without copying their messages into reports.
- Empty execution, interruption and workspace-cleanup failure cannot become PASS.
- Interruption preserves the caller's cancellation flag.
- Repeated invocations create different directories and preserve earlier evidence.
- Non-bootstrap origins and untyped receipt routes are rejected before requests.
- Public response trees and byte arrays remain defensive snapshots.

The existing H2/Flyway compatibility warning remained visible; it was not suppressed and did not cause a migration or verification failure. No dependency upgrade was made as part of 05A.

## Scope

The packaged workbench UI was unchanged by this milestone. Earlier [browser verification](workbench-qa.md) remains relevant to that interface; this milestone was verified through the actual CLI, HTTP and filesystem flow.

The source-only verification copies are separate development artifacts. An automatic approval review rejected cleanup of an incomplete source-copy directory with the reason "blocked by policy"; that directory was left in place. This did not affect the demo's owned runtime-workspace cleanup tests or its passing result.

Read [the contract](milestone-05a.md), [walkthrough](walkthrough.md), [threat model](threat-model.md) and [architecture decision](decisions/0001-signed-migration-laboratory.md) for interpretation and limits.
