# Milestone 05A · Reproducible migration demonstration

## Run one command

From the repository root on Windows:

```powershell
.\lab.ps1 migration-demo
```

Prerequisites are JDK 25, Node.js 24.14+ in the 24 LTS line and npm 11. The launcher selects the existing project-local JDK or `JAVA_HOME`, installs locked workbench dependencies when needed and invokes the checked-in Gradle wrapper. It does not install a JDK, change persistent environment variables, or use NVM.

On Linux or macOS, set `JAVA_HOME` to JDK 25 and install the pinned frontend dependencies once:

```sh
npm ci --prefix workbench --no-audit --no-fund
./gradlew migrationDemo --console=plain
```

The equivalent Windows Gradle task is `.\gradlew.bat migrationDemo --console=plain`. Dependency downloads need access to the declared package registries on the first build. The demonstration itself communicates only with its own numeric loopback origins.

No running workbench, external database, Docker, provider account or API credential is required. An existing workbench may continue running on ports 8781 and 8782.

## What actually runs

The scenario opens three complete managed runtimes in sequence, with two full shutdown/reconstruction boundaries. Each runtime has isolated publisher and receiver Spring Boot contexts within one JVM. Servers bind to `127.0.0.1` with OS-assigned ports.

| Phase | Observation | Required result |
| --- | --- | --- |
| 1 | Read live receiver settings and initial public registry | BOTH_REQUIRED; managed revision 1; both algorithms |
| 1 | Create and evaluate a dual-signed event | All four read-only policy comparisons accept |
| 1 | Query its receipt before delivery | HTTP 404: evaluation created no business result |
| 1 | Deliver the original event | HTTP 201 and a durable receipt |
| 1 | Replay the same exact bytes | HTTP 200 and unchanged acceptance fields |
| 1 | Remove the PQC signature | Classical comparisons accept; both-required and PQC-required reject |
| 1 | Deliver that stripped envelope | HTTP 403 / POLICY_REJECTED, despite the original stored receipt |
| 1 | Rotate both keys and read actual receiver state | Revision 2; new active generation; old generation RETIRING |
| 1 | Retry the original during overlap | HTTP 200 and the original receipt |
| 2 | Rebuild both contexts from disk | Revision, fingerprints, states and overlap timestamps preserved |
| 2 | Revoke the old generation | Revision 3; old generation REVOKED |
| 2 | Evaluate and deliver the original | All four comparisons reject; delivery HTTP 403 / POLICY_REJECTED |
| 2 | Query the historical receipt | HTTP 200; every original acceptance field remains unchanged |
| 2 | Create and deliver a new event | Current keys verify; HTTP 201 with a distinct receipt |
| 3 | Rebuild both contexts again | Revocation and active public identity persist |
| 3 | Retry the revoked original | HTTP 403 / POLICY_REJECTED |
| 3 | Query old evidence and retry the current event | Historical evidence unchanged; current retry HTTP 200 with its receipt |

The complete current scenario makes **28 HTTP requests and 91 assertions**. Expectations are specified before comparing observations. A status match alone is insufficient: the scenario checks actual rejection reasons, signature evidence, four policy decisions, key identities and immutable receipt fields.

The live receiver remains `BOTH_REQUIRED`. This demonstrates operating through a key migration while keeping a strict requirement. It does not silently change the live policy; the four-policy matrix is a separate read-only comparison.

The trusted clock is real UTC. The five-minute freshness window remains active across restarts. Ten-minute key overlap never extends event freshness. The demo checks the actual overlap deadline without sleeping for ten minutes; exact expiry is covered by lifecycle tests. An unusually slow run or clock jump can cause a genuine freshness failure.

## Isolation and cleanup

Each run creates a new system-temporary `pqc-migration-demo-*` workspace:

- `integration/` contains a file-backed H2 ledger and authenticated encrypted vault.
- `account.key` is a separate owner-protected account secret outside that integration directory.
- Between phases, only original public envelopes, receiver metadata and receipts remain in the scenario. No key manager or private signing capability is passed into the next runtime.
- The workspace is removed after all clients and servers stop. Traversal does not follow symbolic links and its root comes only from `Files.createTempDirectory`.
- A cleanup failure prevents PASS. Abrupt process termination may leave a temporary workspace; it is not a secure-erasure guarantee.

The scenario explicitly supplies its temporary secret path. It does not use `PQC_LAB_KEY_SECRET_FILE` or the interactive workbench's account secret. It never reads or modifies `.local/integration`, never stops another process, and never reuses an existing listener.

## Public evidence bundle

Every invocation creates a new directory and prints its exact location:

```text
build/reports/migration-demo/run-<uuid>/
  report.md
  report.json
  SHA256SUMS
  http/
    001-initial-configuration.response.json
    ...
```

| Artifact | Purpose |
| --- | --- |
| `report.md` | English walkthrough, observed HTTP timeline, assertions, method and limitations |
| `report.json` | Versioned structured run, runtime metadata, completion state, checks and artifact references |
| `http/*.request.json` | Exact synthetic request bytes, including the original DSSE envelope on each retry |
| `http/*.response.json` | Exact response bytes after the reviewed public schema accepts every object field |
| `SHA256SUMS` | SHA-256 checksums for both reports and every referenced HTTP artifact |

A complete passing run currently has 47 manifest entries. Absolute runtime paths, usernames, hostnames, transient origin ports, headers, exception messages, vault files, databases and account secrets are excluded from the bundle. Runtime metadata includes Java version/vendor and OS family/architecture.

The public export schema is deliberately narrower than DSSE's incoming protocol schema. An unexpected response property, nested private field, duplicate JSON key or malformed document stops export rather than being copied into the bundle. This does not change the existing DSSE parser's forward-compatible handling of unknown incoming envelope properties.

An unsafe response contributes its received status and body digest, but no raw response artifact. A failed connection has no fabricated status or response digest. Raw artifacts are copied from bounded transport bytes; they are not reconstructed from the report's interpretation.

## Outcomes and exit status

| Outcome | Meaning | Process result |
| --- | --- | --- |
| PASS | All planned phases, assertions and workspace cleanup completed | Zero exit status |
| FAIL | A real HTTP or semantic observation contradicted an expectation; dependent actions stopped | Nonzero |
| INCOMPLETE | Startup, transport, response validation, interruption or cleanup prevented completion | Nonzero |

A completed HTTP 403 can be a passing observation when rejection is required. Conversely, a run with zero failed assertions can still be INCOMPLETE after an operational failure.

Both reports are assembled from the same immutable snapshot. Evidence-write failures propagate as nonzero errors. A missing manifest means bundle generation did not finish, even if an earlier report file exists. Caught interruptions can be reported; forcibly killing the process cannot guarantee a final report.

The manifest is unsigned. It detects changed bytes relative to itself, but is not independent provenance, a trusted timestamp or protection against replacing an entire bundle.

## Verify a copied bundle

From inside the generated run directory on Linux:

```sh
sha256sum -c SHA256SUMS
```

In PowerShell, set the exact generated directory printed by the command:

```powershell
$pqcRunDirectory = 'build/reports/migration-demo/run-<uuid>'
foreach ($pqcLine in Get-Content -LiteralPath (Join-Path $pqcRunDirectory 'SHA256SUMS')) {
    $pqcParts = $pqcLine -split '  ', 2
    $pqcArtifact = Join-Path $pqcRunDirectory $pqcParts[1]
    $pqcDigest = (Get-FileHash -LiteralPath $pqcArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($pqcDigest -ne $pqcParts[0]) { throw "Evidence checksum mismatch: $($pqcParts[1])" }
}
Write-Output 'All evidence checksums match.'
```

These commands verify byte consistency, not the authenticity of a publisher or a third party's claimed run.

## Verification and implementation

`MigrationDemo` owns execution and reporting; `MigrationScenario` specifies the story; `MigrationTransport` performs direct-only, non-redirecting, size-bounded HTTP with a complete-response deadline. `MigrationJournal` preserves public bytes and assertions; `MigrationReportWriter` renders JSON, Markdown and the manifest.

Tests cover the complete real scenario, disk-only reconstruction, exact-byte artifact hashes, receipt preservation, workspace removal, repeated runs, failed HTTP and semantic expectations, unsafe nested fields, no-response transport failures, interruption and cleanup failure. Architecture rules prevent the demonstration package from depending on private publisher adapters, JCA signing adapters or ledger adapters.

The prepared CI workflow runs the demo after the existing quality gates and retains only the public evidence directory. Remote CI execution and publication remain future actions.

The [local verification record](migration-demo-qa.md) documents actual command execution, source-only reconstruction, negative cases and current quality-gate results.

Read [the walkthrough](walkthrough.md), [threat model](threat-model.md), and [architecture decision](decisions/0001-signed-migration-laboratory.md). This milestone adds a functional demonstration and documentation; independent JMH results retain their separate [measurement method](milestone-04a.md).
