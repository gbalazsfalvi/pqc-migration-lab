# Workbench browser verification

Manual browser checks on Windows, 13 November 2026. The workbench was tested against the real local Java integration through its Vite proxy, then against the frontend bundled in the Spring Boot application. These checks complement the automated tests; they are not an automated browser suite.

| Flow | Observation |
| --- | --- |
| Dual-signature creation | Java returned two verified entries and four accepting policy results |
| Classical-only creation | Classical and shadow accepted; both-required and PQC-required rejected |
| PQC-only creation | PQC-required accepted; the other policies rejected |
| Signature stripping | Classical/shadow accepted; both/PQC rejected; actual delivery returned 403 |
| Untrusted replacement keys | Both entries remained unverified; all policies rejected |
| Exact replay | The receiver returned 200 and the same durable receipt ID |
| Conflicting signed bytes | Read-only validation passed; actual delivery returned 409 and retained the original receipt |
| Durable receipt dialog | An actual ledger read returned the stored transaction and original acceptance evidence |
| JSON export | Download contained the envelope, four decisions and receipt; no private-key field |
| Session navigation | History reopened a prior snapshot; the protocol guide explained trust, claims and replay |
| Mobile layout | At 390 × 844, event creation worked and document width did not exceed the viewport |
| Packaged Java UI | With Vite stopped, the bundled application created and delivered a new event with HTTP 201 |
| Signed-time rejection | An expired re-signed fixture passed signature checks, failed freshness, and returned HTTP 422 |
| Invalid form input | An unsupported artifact label produced English validation feedback without creating a run |

The mobile check exposed a wrapping hidden skip link; it was repositioned and checked again. Native dialog focus moved to its close button. The interface uses visible text alongside semantic outcome colours.

The packaged application was checked at `http://127.0.0.1:8781/` with Vite stopped. Generated screenshots are local verification artifacts under `build/reports/workbench-qa/` and are intentionally ignored by Git.

Final local verification passed: 152 Java tests, 11 frontend unit tests, 53 cryptographic probe expectations and 23 HTTP demonstration expectations. Strict TypeScript, Java formatting/Javadoc, architecture rules and all per-module coverage gates passed. Integration-core line coverage was 100%; integration-app was 95.30%. GitHub Actions has been prepared but has not been run remotely.

To reproduce, run `.\lab.ps1 serve` and create a fresh event. The default freshness window is five minutes; old snapshots must not be treated as newly verified observations.

## Milestone 04B lifecycle verification

Additional checks on 13 November 2026 used the packaged managed application at `http://127.0.0.1:8781/`, without Vite:

- Created a dual-signature event and received HTTP 201 with a durable receipt.
- Rotated both keys from generation 1 to generation 2; revision advanced from 1 to 2 and generation 1 became RETIRING.
- Replayed the original event during overlap: HTTP 200 with the same receipt.
- Revoked generation 1 through the native confirmation dialog; revision became 3.
- Retried the old event: HTTP 403, with every comparison policy rejecting the old signatures.
- Queried the original receipt after revocation: unchanged receipt and acceptance evidence remained available.
- Created an event with the new active generation: HTTP 201.
- Restarted the complete application from disk; generation IDs, public fingerprints, revocation state and revision remained unchanged.
- Opened active-generation revocation guidance and cancelled with Escape; no lifecycle change occurred.
- Downloaded public registry JSON and rejected-event evidence; verified the expected revision, states, rejection and original receipt identifier.
- Restarted the final build with the initialization marker enabled; public trust remained at revision 3 and a fresh dual-signature delivery returned HTTP 201.
- Checked the lifecycle view at 390 × 844: navigation, generation cards and full fingerprints remained within the document width. Reset the viewport after verification.
- Observed no browser console warnings or errors during the final packaged-UI checks.

The final local quality gate passed 198 Java tests and 17 frontend tests, without skipped tests. Integration-core line coverage was 99.35%, and integration-app was 94.67%. Strict TypeScript, formatting, Javadoc, architecture and all per-module coverage gates remained enabled. The 53 probe and 23 HTTP demonstration expectations also passed.

Local lifecycle screenshots and public JSON verification artifacts are retained under `.local/lifecycle-evidence/`, outside generated build reports.
