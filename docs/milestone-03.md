# Milestone 03 — Migration workbench

An English React/TypeScript interface makes the existing signed integration inspectable. It uses actual Java signing, verification, HTTP delivery and durable receipt reads. The browser does not generate cryptographic verdicts.

## Run and build

Requirements: JDK 25, Node.js 24.14+ in the 24 LTS line, and npm 11.

```powershell
.\lab.ps1 serve
```

Open [http://127.0.0.1:8781/](http://127.0.0.1:8781/). The Windows launcher bootstraps locked npm dependencies when their package fingerprint changes, then builds the frontend through Gradle. It changes no persistent Java or Node settings.

For direct Gradle use on any supported OS, run `npm ci --prefix workbench` first. `:integration-app:processResources` depends on `workbenchBuild` and copies the Vite output into the publisher's static resources. `:integration-app:bootJar` therefore produces one runnable application containing both Boot contexts and the workbench. The finished JAR needs Java only.

The optional `npm run dev --prefix workbench` preview binds to `127.0.0.1:5173` and proxies `/api` to the publisher on 8781. Java must also be running. No configurable remote target is exposed.

## User flow

1. Create a synthetic release using Ed25519, ML-DSA-65 or both.
2. Inspect the decoded payload, original DSSE envelope and trusted signature observations.
3. Compare all four policies and inspect their validation paths.
4. Apply a controlled fixture to the original baseline, then compare again.
5. Deliver the chosen envelope under the unchanged live receiver policy.
6. Read its stored receipt, replay the event, or export the public observations.

Creation and evaluation do not store a release. Delivery is the only operation that applies a business result. Choosing a row in the policy table changes the displayed trace, never the live requirement.

## Java remains authoritative

`MigrationEvaluator` reuses `PolicyVerifier` and `EventValidator` from durable reception. It has no `ReleaseLedger` port. It captures the trusted clock once so all four policy decisions use the same freshness boundary. Provider or configuration failures propagate as operational failures rather than ordinary policy rejections.

`EnvelopeInspector` reports payload, PAE and wire lengths, a payload SHA-256 fingerprint, and actual verification observations for each entry. A signature is labelled Ed25519 or ML-DSA-65 only when a configured public verifier verifies it. Otherwise it is an unverified entry, even if its length or `keyid` looks familiar.

The trace reports envelope parsing, required signatures, release schema, publisher identity, receiver audience and freshness. Stages after a rejection are explicitly unchecked. A successful read-only trace does not predict idempotency: different authenticated bytes under a stored ID can pass validation and still receive a delivery-time 409.

## HTTP boundaries

The UI uses relative, same-origin URLs. The publisher gateway contacts only its configured loopback receiver. It preserves the receiver's HTTP status and safe problem response.

| Endpoint | Result |
| --- | --- |
| `GET :8781/` | Built React workbench |
| `POST :8781/api/publisher/envelopes` | Existing bounded synthetic event creation API |
| `GET :8781/api/workbench/config` | Public settings after an actual receiver connectivity check |
| `POST :8781/api/workbench/evaluations` | Read-only comparison, forwarded to the receiver |
| `POST :8781/api/workbench/deliveries` | Actual delivery under the receiver's startup policy |
| `GET :8781/api/workbench/receipts/{eventId}` | Actual ledger read through the fixed gateway |
| `POST :8781/api/workbench/experiments` | Controlled fixture, without a ledger write |
| `POST :8782/api/receiver/evaluations` | Receiver-owned evaluation with no persistence capability |

An evaluation request is the DSSE envelope itself, using the existing wire limits. A syntactically malformed envelope is rejected as a request problem. A well-formed envelope with invalid evidence returns observations containing policy rejections. The response includes `evaluatedAt`, `receiverPolicy`, lengths, signature observations, and ordered policy results with rejection codes and stage statuses.

Fixture requests have exactly two members:

```json
{
  "scenario": "STRIP_PQC",
  "envelope": {
    "payloadType": "application/vnd.pqclab.release-event.v1+json",
    "payload": "<original base64 payload>",
    "signatures": [
      { "keyid": "<unauthenticated hint>", "sig": "<base64 signature>" }
    ]
  }
}
```

The example strings are placeholders, not a valid signed fixture. The response contains `scenario`, a server-owned explanatory `note` and the resulting `envelope`.

## Controlled experiments

Every fixture starts from the original event rather than stacking modifications. The fixture builder requires at least one valid configured baseline signature and matching publisher/audience claims. Publisher-side fixture capabilities never enter the receiver's dependency graph.

| Scenario | Change | Expected consequence under both-required |
| --- | --- | --- |
| `STRIP_PQC` | Remove entries actually verified by the trusted ML-DSA key | Signature rejection; classical and shadow comparison can still accept |
| `TAMPER_PAYLOAD` | Change payload bytes while retaining signatures | Signature rejection |
| `WRONG_KEY` | Sign with fresh keys outside the trusted registry | Signature rejection; entries remain unverified |
| `EXPIRED_EVENT` | Re-sign an event older than the accepted window | Valid signatures, signed-time rejection |
| `FUTURE_EVENT` | Re-sign beyond the allowed future tolerance | Valid signatures, signed-time rejection |
| `WRONG_AUDIENCE` | Re-sign a claim addressed to another receiver | Valid signatures, audience rejection |
| `UNSIGNED_DOWNGRADE` | Strip PQC and add an unsigned policy extension | Signature rejection; the extension cannot weaken enforcement |
| `CONFLICTING_ID` | Re-sign different release bytes under the original ID | Read-only validation can pass; delivery rejects with 409 after baseline acceptance |

The conflict button first delivers the baseline, then builds and delivers its changed counterpart. It is available only for an originally accepted live-policy baseline. If baseline delivery fails, the UI reports that result and stops.

Replay sends the same current envelope twice. An accepted first delivery must be followed by `ALREADY_ACCEPTED` with the same receipt identifier. Every retry still passes current policy, audience and freshness validation.

## State and export

Run history retains the latest public snapshot of at most 20 event IDs in this tab. It is memory-only and clears on reload. Opening a snapshot preserves its original evaluation timestamp; it does not claim a fresh verification. Refresh asks Java to evaluate the current envelope again.

Receipts remain in the H2 ledger across application restarts. “Read from ledger” makes a real HTTP request before opening a native dialog. Original acceptance fields retain their original meaning even after later experiments fail.

Export produces a versioned JSON document with the current envelope, observation time, evaluation, latest delivery and any stored receipt. It contains public signatures and synthetic release details, with no private keys.

## Local browser boundary

Both servers bind to loopback. The shared guard accepts Host `127.0.0.1` or `localhost`, and requests with no Origin or an exact allowed origin:

- The request server's own `http://127.0.0.1:<port>` or `http://localhost:<port>`.
- The documented development origins `http://127.0.0.1:5173` and `http://localhost:5173`.

Foreign or opaque origins and `Sec-Fetch-Site: cross-site` are rejected. There is no wildcard CORS permission. Host validation rejects foreign DNS names that resolve to loopback. This is a local synthetic signing laboratory, not an authenticated public signing API.

API responses keep a restrictive content security policy. Static UI resources use self-only scripts, styles, fonts and connections, forbid framing and objects, and include no inline script requirement. Fonts are bundled locally with their original licence texts. No remote font/CDN service is contacted.

## Verification

`gradlew clean check` runs frontend formatting/unit checks, strict TypeScript compilation, Vite bundling, Java tests, architecture rules, strict Javadoc and the existing 90% per-module line-coverage gate.

Java tests cover the real four-policy comparison, read-only ledger boundary, fixed delivery policy, fixture rejection codes, conflicting-ID receipts, strict request shapes, public-only receiver architecture, allowed/foreign browser origins and the packaged English index. Application-core tests cover one shared time snapshot and operational failure propagation. Frontend tests check wire contracts and presentation boundaries. See [browser verification](workbench-qa.md) for interactive evidence.

## Remaining scope

Milestone three established the interface and its public-only boundaries. [Milestone 04A](milestone-04a.md) adds separate benchmark evidence; [milestone 04B](milestone-04b.md) adds managed durable keys and a lifecycle view. Public authentication and post-quantum TLS remain outside the local lab.
