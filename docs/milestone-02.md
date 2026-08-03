# Milestone 02 · A working signed integration

## Run

From the repository root with JDK 25 selected:

```powershell
.\lab.ps1 verify
.\lab.ps1 demo
.\lab.ps1 serve -Policy BOTH_REQUIRED
```

`demo` starts temporary loopback listeners, executes 23 real HTTP expectations, writes `build/reports/integration-demo/report.md` and `report.json`, and closes the servers. Its demonstration ledgers also live under that ignored report directory.

`serve` keeps the publisher on port 8781 and the receiver on port 8782 running until Ctrl+C. The durable ledger lives in `.local/integration/release-ledger.mv.db`. A database installation and Docker are unnecessary.

Alternative launch arguments:

```powershell
.\gradlew.bat :integration-app:bootRun --args="--policy=SHADOW_PQC --publisher-port=9001 --receiver-port=9002 --data-dir=.local/shadow"
```

Unknown or duplicate arguments, unsupported policies, invalid ports and H2 URL option injection are rejected. There is no configurable remote target or public bind address.

## Architecture

```mermaid
sequenceDiagram
    participant Caller
    participant Publisher as Publisher / Boot context
    participant Receiver as Receiver / Boot context
    participant Rules as Framework-free application core
    participant Ledger as Transactional H2 ledger
    Caller->>Publisher: POST synthetic release details
    Publisher->>Publisher: Assign ID, issuer, audience and time; sign DSSE PAE
    Publisher->>Receiver: POST original DSSE JSON over loopback HTTP
    Receiver->>Rules: Bounded envelope + trusted receiver policy
    Rules->>Rules: Verify algorithms for one trusted owner
    Rules->>Rules: Parse verified bytes; validate issuer, audience and freshness
    Rules->>Ledger: Atomically insert one accepted release
    Ledger-->>Rules: New result or unchanged original receipt
    Rules-->>Caller: Receipt via receiver and publisher
```

The two applications use isolated Spring Boot contexts, separate dependency graphs and separate actual HTTP listeners within one JVM. They are not two independently deployed processes. This makes the first local integration reproducible while keeping private-key files and infrastructure setup out of the experiment.

Milestone two's deterministic probes use ephemeral keys. Since milestone 04B, normal application startup recovers managed keys from an authenticated encrypted local vault. Publisher signing ports receive private capabilities; receiver ports receive independently imported SPKI public keys and a configured owner. Keys are never learned from an incoming envelope or registered globally.

## Receiver policies

An optional invalid signature is an observed false result. A provider/configuration failure is an operational error and fails closed.

| Policy | Valid Ed25519 only | Valid ML-DSA-65 only | Both valid |
| --- | :---: | :---: | :---: |
| `CLASSICAL_ONLY` | Accept | Reject | Accept |
| `SHADOW_PQC` | Accept | Reject | Accept |
| `BOTH_REQUIRED` | Reject | Reject | Accept |
| `PQC_REQUIRED` | Reject | Accept | Accept |

Both algorithms are evaluated against configured keys for the **same trusted publisher**. Two classical entries do not satisfy both-required. A classical signature from publisher A plus a post-quantum signature from publisher B cannot be combined to satisfy it.

Classical and shadow policies deliberately share an enforcement requirement. Shadow mode labels an explicit migration trial, recording the post-quantum observation without accepting a post-quantum-only event. Every first acceptance stores both observed results.

Policy is chosen at trusted startup. An unsigned `policy` envelope extension is ignored, and the publisher request schema does not permit a receiver policy override. There is no HTTP endpoint that weakens policy.

## Signed event contract

Payload type: `application/vnd.pqclab.release-event.v1+json`.

```json
{
  "schemaVersion": 1,
  "eventId": "c06c6e10-1f63-4b13-8ef0-93de5b1edb35",
  "issuer": "release-publisher",
  "audience": "urn:pqclab:release-receiver",
  "issuedAt": "2026-11-13T08:00:00Z",
  "artifact": "synthetic-service",
  "version": "1.0.0",
  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}
```

The example timestamp is illustrative; live publication uses the current clock.

| Field | Validation |
| --- | --- |
| `schemaVersion` | Integer 1, without string or floating-point coercion |
| `eventId` | Canonical lowercase UUID; namespace is the authenticated issuer |
| `issuer` | Lowercase publisher identifier, at most 64 characters; must match the trusted key owner |
| `audience` | Bounded identifier, at most 128 characters; must equal the receiver's audience |
| `issuedAt` | ISO-8601 instant; at most five minutes old or thirty seconds ahead |
| `artifact` | Bounded artifact label, at most 64 characters |
| `version` | Bounded release label, at most 64 characters |
| `sha256` | Exactly 64 lowercase hexadecimal characters |

Time boundaries are inclusive. Freshness is checked before durable idempotency, including for already accepted events. The business schema rejects duplicate, unknown and incorrectly typed fields. DSSE extension fields retain their separate forward-compatible treatment.

## HTTP contract

All endpoints are local API endpoints. Milestone three adds a bundled React workbench, fixed same-origin gateway routes and an exact loopback-origin policy. See [the workbench contract](milestone-03.md); the original publisher and receiver endpoints remain available.

| Endpoint | Behaviour |
| --- | --- |
| `POST :8781/api/publisher/envelopes` | Creates a signed envelope for inspection or exact-byte retry experiments |
| `POST :8781/api/publisher/releases` | Creates an event, signs it, and sends it to the receiver over real HTTP |
| `POST :8782/api/receiver/releases` | Verifies and applies a supplied DSSE envelope |
| `GET :8782/api/receiver/releases/{eventId}` | Reads the persisted receipt in the configured publisher namespace |
| `GET :8782/api/receiver/status` | Reads trusted policy, identity and time-window settings |

Publisher request:

```json
{
  "artifact": "synthetic-service",
  "version": "1.0.0",
  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "signingMode": "DUAL"
}
```

`signingMode` defaults to `DUAL`. Alternatives are `ED25519_ONLY` and `ML_DSA_65_ONLY`. Publisher callers cannot choose the signed issuer, audience, event ID or timestamp.

Each call to a publisher creation endpoint creates a new event. To retry a delivery, create and retain an envelope, then resend that same envelope to the receiver. A durable publisher outbox is not implemented in this milestone.

Receiver decisions:

| HTTP | Public outcome |
| ---: | --- |
| 201 | `ACCEPTED`: a new release and receipt were committed |
| 200 | `ALREADY_ACCEPTED`: identical authenticated payload, unchanged original receipt |
| 400 | Malformed envelope, invalid publisher request shape or invalid path parameter |
| 403 | `POLICY_REJECTED`, `ISSUER_MISMATCH` or rejected browser-origin access |
| 404 | `RELEASE_NOT_FOUND` |
| 409 | `EVENT_ID_CONFLICT`: the signed ID already belongs to different payload bytes |
| 413 | `BODY_TOO_LARGE`, including chunked input without Content-Length |
| 422 | Invalid business schema, payload type, audience or signed time |
| 503 | Provider, database, upstream availability, interruption or delivery deadline failure |

Receipts expose `enforcedPolicy` for the current request. `acceptedPolicy`, `acceptedEvidence`, `acceptedAt` and `receiptId` describe the immutable original acceptance. A valid retry after a policy upgrade preserves that audit history while reporting the newly enforced requirement.

## Replay and transaction semantics

The ledger's primary key is `(issuer, event_id)`. One transaction inserts the complete synthetic release and its acceptance evidence. This row is both the business effect and the replay reservation; there is no gap between reserving an ID and applying a separate effect.

Concurrent exact retries race against this unique key. One succeeds with 201; the others roll back their attempted insertion and return the same committed receipt with 200. Reuse with a different SHA-256 fingerprint of the original verified payload returns 409 and leaves the old result unchanged.

Idempotency compares **original signed payload bytes**, not semantically equivalent reserializations. Identifiers and receipts are retained durably. An expired delivery is rejected even if its original receipt remains queryable.

Every retry is reverified under the current migration policy before the ledger is consulted. A previously accepted classical-only envelope cannot bypass an upgrade to both-required. The same event with the newly required valid signatures may reuse the original receipt if its original payload bytes still meet freshness.

This demonstrates atomic application of one local database result. It does not claim exactly-once effects across unrelated external systems.

## Verification

Automated tests exercise real signing, both HTTP listeners, Flyway, JDBC transactions and file-backed H2. They include the full policy matrix, shadow observations, stripped signatures, duplicated entries, unsigned downgrade hints, key substitution, claim validation, boundary timestamps, malformed signed business documents, chunked input limits, concurrent delivery and complete runtime restart.

The application core remains free of Spring, JCA, JDBC and JSON dependencies. ArchUnit also prevents receiver request handling from depending on publisher signing code. Existing milestone-one provider and published-vector tests continue to run.

## Current scope

The local signing API intentionally signs synthetic release details for a local caller. It is not an authenticated public signing service. Both servers are forced to loopback, foreign browser origins are blocked, response caching is disabled and request/response sizes are bounded. Milestone three permits the bundled UI's exact origin and the documented local development origin.

The original milestone-two persistence tests explicitly reuse an ephemeral test trust bundle. Normal startup now uses the durable lifecycle in [milestone 04B](milestone-04b.md); its additional tests reconstruct both contexts from disk without reusing private objects, and prove rotation, revocation and immutable receipt behaviour across complete restarts.

The transport is local HTTP, not post-quantum TLS. Signatures provide event integrity and configured publisher authentication; they do not encrypt the payload. No remote deployment or publication is performed.

Database versions follow Spring Boot's dependency BOM. Its H2 2.4.240 currently produces a Flyway notice because Flyway declares verification through H2 2.3.232. The selected combination passes this repository's migration, transaction, concurrency and restart tests; the notice is retained in local output.

Implementation references: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Boot database initialization and Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html), and [the DSSE profile](dsse-profile.md).
