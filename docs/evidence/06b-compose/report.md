# PQC Migration Lab · Migration demonstration

**PASS** · 165 passed · 0 failed · 49 HTTP attempts

- Run: 475f288d-cef6-4e07-8b06-265810be59d3
- Started: 2026-11-16T11:50:20.108967600Z
- Finished: 2026-11-16T11:52:07.411247Z
- Scenario and workspace cleanup completed: true
- Failure category: none

A fresh synthetic integration retains one publisher while the receiver is deployed under **CLASSICAL_ONLY → SHADOW_PQC → BOTH_REQUIRED → PQC_REQUIRED**. The same three original signed events, public trust and durable ledger cross all four phases. Each phase verifies the actual receiver configuration, signature evidence, delivery permission and unchanged first-acceptance history. Policy changes use owned receiver restarts, not an HTTP mutation API.

## Observed policy rollout

Completed phases: 4/4.

| Phase | Live requirement | Ed25519 only | Dual signature | ML-DSA-65 only |
| ---: | --- | --- | --- | --- |
| 1 | CLASSICAL_ONLY | HTTP 201 · ACCEPTED (#7) | HTTP 201 · ACCEPTED (#10) | HTTP 403 · POLICY_REJECTED (#13) |
| 2 | SHADOW_PQC | HTTP 200 · ALREADY_ACCEPTED (#18) | HTTP 200 · ALREADY_ACCEPTED (#21) | HTTP 403 · POLICY_REJECTED (#24) |
| 3 | BOTH_REQUIRED | HTTP 403 · POLICY_REJECTED (#29) | HTTP 200 · ALREADY_ACCEPTED (#32) | HTTP 403 · POLICY_REJECTED (#35) |
| 4 | PQC_REQUIRED | HTTP 403 · POLICY_REJECTED (#41) | HTTP 200 · ALREADY_ACCEPTED (#44) | HTTP 201 · ACCEPTED (#47) |

201 creates a result, 200 reuses the original receipt, and 403 rejects delivery. Historical receipt reads are separate observations. enforcedPolicy reports today's requirement; acceptedPolicy, acceptedAt, receipt identity and first signature evidence remain original. PQC_REQUIRED keeps PQC mandatory while retiring the classical requirement; it deliberately admits PQC-only senders rejected by BOTH_REQUIRED.

## Runtime and method

- Java: 25.0.5+8-LTS / Eclipse Adoptium
- Platform: Windows 11 / amd64
- Transport: Independent local Compose containers; one publisher, four receiver deployments, unchanged public trust and durable ledger
- Protocol: DSSEv1 / Ed25519 + ML-DSA-65

Every HTTP row is an actual bounded request. Expected statuses and semantic requirements are specified by the scenario before comparing the response. UUIDs, keys, timestamps and fingerprints are fresh on each run; outcomes and invariants are reproducible. No artificial clock or ten-minute sleep is used. Exact overlap expiry remains covered by the lifecycle tests.

## Observed HTTP timeline

| # | Phase | Scenario | Endpoint | Method and route | Expected | Observed |
| ---: | ---: | --- | --- | --- | ---: | ---: |
| 1 | 1 | phase-1-configuration | RECEIVER | GET /api/receiver/status | 200 | 200 |
| 2 | 1 | phase-1-registry | RECEIVER | GET /api/receiver/keys | 200 | 200 |
| 3 | 1 | create-classical | PUBLISHER | POST /api/publisher/envelopes | 200 | 200 |
| 4 | 1 | create-dual | PUBLISHER | POST /api/publisher/envelopes | 200 | 200 |
| 5 | 1 | create-pqc | PUBLISHER | POST /api/publisher/envelopes | 200 | 200 |
| 6 | 1 | phase-1-classical-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 7 | 1 | phase-1-classical-delivery | PUBLISHER | POST /api/workbench/deliveries | 201 | 201 |
| 8 | 1 | phase-1-classical-history | RECEIVER | GET /api/receiver/releases/c6e31721-9184-4cb7-8a58-4e26f51a3535 | 200 | 200 |
| 9 | 1 | phase-1-dual-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 10 | 1 | phase-1-dual-delivery | PUBLISHER | POST /api/workbench/deliveries | 201 | 201 |
| 11 | 1 | phase-1-dual-history | RECEIVER | GET /api/receiver/releases/15217ab3-8bc6-42af-b33f-13da174f1718 | 200 | 200 |
| 12 | 1 | phase-1-pqc-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 13 | 1 | phase-1-pqc-delivery | PUBLISHER | POST /api/workbench/deliveries | 403 | 403 |
| 14 | 1 | phase-1-pqc-history | RECEIVER | GET /api/receiver/releases/d900ba34-0426-4159-bd76-d3835211c91b | 404 | 404 |
| 15 | 2 | phase-2-configuration | RECEIVER | GET /api/receiver/status | 200 | 200 |
| 16 | 2 | phase-2-registry | RECEIVER | GET /api/receiver/keys | 200 | 200 |
| 17 | 2 | phase-2-classical-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 18 | 2 | phase-2-classical-delivery | PUBLISHER | POST /api/workbench/deliveries | 200 | 200 |
| 19 | 2 | phase-2-classical-history | RECEIVER | GET /api/receiver/releases/c6e31721-9184-4cb7-8a58-4e26f51a3535 | 200 | 200 |
| 20 | 2 | phase-2-dual-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 21 | 2 | phase-2-dual-delivery | PUBLISHER | POST /api/workbench/deliveries | 200 | 200 |
| 22 | 2 | phase-2-dual-history | RECEIVER | GET /api/receiver/releases/15217ab3-8bc6-42af-b33f-13da174f1718 | 200 | 200 |
| 23 | 2 | phase-2-pqc-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 24 | 2 | phase-2-pqc-delivery | PUBLISHER | POST /api/workbench/deliveries | 403 | 403 |
| 25 | 2 | phase-2-pqc-history | RECEIVER | GET /api/receiver/releases/d900ba34-0426-4159-bd76-d3835211c91b | 404 | 404 |
| 26 | 3 | phase-3-configuration | RECEIVER | GET /api/receiver/status | 200 | 200 |
| 27 | 3 | phase-3-registry | RECEIVER | GET /api/receiver/keys | 200 | 200 |
| 28 | 3 | phase-3-classical-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 29 | 3 | phase-3-classical-delivery | PUBLISHER | POST /api/workbench/deliveries | 403 | 403 |
| 30 | 3 | phase-3-classical-history | RECEIVER | GET /api/receiver/releases/c6e31721-9184-4cb7-8a58-4e26f51a3535 | 200 | 200 |
| 31 | 3 | phase-3-dual-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 32 | 3 | phase-3-dual-delivery | PUBLISHER | POST /api/workbench/deliveries | 200 | 200 |
| 33 | 3 | phase-3-dual-history | RECEIVER | GET /api/receiver/releases/15217ab3-8bc6-42af-b33f-13da174f1718 | 200 | 200 |
| 34 | 3 | phase-3-pqc-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 35 | 3 | phase-3-pqc-delivery | PUBLISHER | POST /api/workbench/deliveries | 403 | 403 |
| 36 | 3 | phase-3-pqc-history | RECEIVER | GET /api/receiver/releases/d900ba34-0426-4159-bd76-d3835211c91b | 404 | 404 |
| 37 | 3 | phase-3-unsigned-downgrade | PUBLISHER | POST /api/workbench/deliveries | 403 | 403 |
| 38 | 4 | phase-4-configuration | RECEIVER | GET /api/receiver/status | 200 | 200 |
| 39 | 4 | phase-4-registry | RECEIVER | GET /api/receiver/keys | 200 | 200 |
| 40 | 4 | phase-4-classical-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 41 | 4 | phase-4-classical-delivery | PUBLISHER | POST /api/workbench/deliveries | 403 | 403 |
| 42 | 4 | phase-4-classical-history | RECEIVER | GET /api/receiver/releases/c6e31721-9184-4cb7-8a58-4e26f51a3535 | 200 | 200 |
| 43 | 4 | phase-4-dual-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 44 | 4 | phase-4-dual-delivery | PUBLISHER | POST /api/workbench/deliveries | 200 | 200 |
| 45 | 4 | phase-4-dual-history | RECEIVER | GET /api/receiver/releases/15217ab3-8bc6-42af-b33f-13da174f1718 | 200 | 200 |
| 46 | 4 | phase-4-pqc-evaluation | RECEIVER | POST /api/receiver/evaluations | 200 | 200 |
| 47 | 4 | phase-4-pqc-delivery | PUBLISHER | POST /api/workbench/deliveries | 201 | 201 |
| 48 | 4 | phase-4-pqc-history | RECEIVER | GET /api/receiver/releases/d900ba34-0426-4159-bd76-d3835211c91b | 200 | 200 |
| 49 | 4 | phase-4-unsigned-downgrade | PUBLISHER | POST /api/workbench/deliveries | 403 | 403 |

## Assertions

Large values are abbreviated here; report.json preserves complete expected and observed values.

| Assertion | Expected | Observed | Result | HTTP references |
| --- | --- | --- | --- | --- |
| roles.independent · Publisher and receiver are distinct live services. | false | false | PASS | [] |
| phase-1-configuration.http · Observed HTTP status for phase-1-configuration | 200 | 200 | PASS | [1] |
| phase-1.live-policy · The real receiver enforces this phase's startup policy. | CLASSICAL_ONLY | CLASSICAL_ONLY | PASS | [1] |
| phase-1.claims-and-time · The same signed identity, audience and freshness remain enforced. | [release-publisher, urn:pqclab:release-receiver, PT5M, PT30S] | [release-publisher, urn:pqclab:release-receiver, PT5M, PT30S] | PASS | [1] |
| phase-1-registry.http · Observed HTTP status for phase-1-registry | 200 | 200 | PASS | [2] |
| trust.initial · The rehearsal begins with one managed public generation at revision 1. | [true, 1, 1] | [true, 1, 1] | PASS | [2] |
| create-classical.http · Observed HTTP status for create-classical | 200 | 200 | PASS | [3] |
| create-dual.http · Observed HTTP status for create-dual | 200 | 200 | PASS | [4] |
| create-pqc.http · Observed HTTP status for create-pqc | 200 | 200 | PASS | [5] |
| events.distinct · The publisher creates three independently identified events. | 3 | 3 | PASS | [] |
| phase-1-classical-evaluation.http · Observed HTTP status for phase-1-classical-evaluation | 200 | 200 | PASS | [6] |
| phase-1-classical.evaluation-policy · Read-only Java observations identify the actual live requirement. | CLASSICAL_ONLY | CLASSICAL_ONLY | PASS | [6] |
| phase-1-classical.verified-signatures · Java independently verifies this sender's actual signatures. | [true, false] | [true, false] | PASS | [6] |
| phase-1-classical-delivery.http · Observed HTTP status for phase-1-classical-delivery | 201 | 201 | PASS | [7] |
| phase-1-classical.delivery-status · Delivery creates or reuses the one durable business result. | ACCEPTED | ACCEPTED | PASS | [7] |
| phase-1-classical.first-acceptance · A new result records its actual first policy and signature evidence. | [c6e31721-9184-4cb7-8a58-4e26f51a3535, CLASSICAL_ONLY, true, false] | [c6e31721-9184-4cb7-8a58-4e26f51a3535, CLASSICAL_ONLY, true, false] | PASS | [7] |
| phase-1-classical-delivery.status · The response identifies the observed delivery or historical query. | ACCEPTED | ACCEPTED | PASS | [7] |
| phase-1-classical-delivery.enforced-policy · The current requirement is reported separately from original acceptance. | CLASSICAL_ONLY | CLASSICAL_ONLY | PASS | [7] |
| phase-1-classical-delivery.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | PASS | [7, 7] |
| phase-1-classical-history.http · Observed HTTP status for phase-1-classical-history | 200 | 200 | PASS | [8] |
| phase-1-classical-history.status · The response identifies the observed delivery or historical query. | STORED | STORED | PASS | [8] |
| phase-1-classical-history.enforced-policy · The current requirement is reported separately from original acceptance. | CLASSICAL_ONLY | CLASSICAL_ONLY | PASS | [8] |
| phase-1-classical-history.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | PASS | [7, 8] |
| phase-1-dual-evaluation.http · Observed HTTP status for phase-1-dual-evaluation | 200 | 200 | PASS | [9] |
| phase-1-dual.evaluation-policy · Read-only Java observations identify the actual live requirement. | CLASSICAL_ONLY | CLASSICAL_ONLY | PASS | [9] |
| phase-1-dual.verified-signatures · Java independently verifies this sender's actual signatures. | [true, true] | [true, true] | PASS | [9] |
| phase-1-dual-delivery.http · Observed HTTP status for phase-1-dual-delivery | 201 | 201 | PASS | [10] |
| phase-1-dual.delivery-status · Delivery creates or reuses the one durable business result. | ACCEPTED | ACCEPTED | PASS | [10] |
| phase-1-dual.first-acceptance · A new result records its actual first policy and signature evidence. | [15217ab3-8bc6-42af-b33f-13da174f1718, CLASSICAL_ONLY, true, true] | [15217ab3-8bc6-42af-b33f-13da174f1718, CLASSICAL_ONLY, true, true] | PASS | [10] |
| phase-1-dual-delivery.status · The response identifies the observed delivery or historical query. | ACCEPTED | ACCEPTED | PASS | [10] |
| phase-1-dual-delivery.enforced-policy · The current requirement is reported separately from original acceptance. | CLASSICAL_ONLY | CLASSICAL_ONLY | PASS | [10] |
| phase-1-dual-delivery.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | PASS | [10, 10] |
| phase-1-dual-history.http · Observed HTTP status for phase-1-dual-history | 200 | 200 | PASS | [11] |
| phase-1-dual-history.status · The response identifies the observed delivery or historical query. | STORED | STORED | PASS | [11] |
| phase-1-dual-history.enforced-policy · The current requirement is reported separately from original acceptance. | CLASSICAL_ONLY | CLASSICAL_ONLY | PASS | [11] |
| phase-1-dual-history.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | PASS | [10, 11] |
| phase-1-pqc-evaluation.http · Observed HTTP status for phase-1-pqc-evaluation | 200 | 200 | PASS | [12] |
| phase-1-pqc.evaluation-policy · Read-only Java observations identify the actual live requirement. | CLASSICAL_ONLY | CLASSICAL_ONLY | PASS | [12] |
| phase-1-pqc.verified-signatures · Java independently verifies this sender's actual signatures. | [false, true] | [false, true] | PASS | [12] |
| phase-1-pqc-delivery.http · Observed HTTP status for phase-1-pqc-delivery | 403 | 403 | PASS | [13] |
| phase-1-pqc.policy-rejected · Insufficient signatures fail the live requirement even after prior acceptance. | POLICY_REJECTED | POLICY_REJECTED | PASS | [13] |
| phase-1-pqc-history.http · Observed HTTP status for phase-1-pqc-history | 404 | 404 | PASS | [14] |
| phase-1-pqc.no-write · Rejected/evaluated events have no durable result. | RELEASE_NOT_FOUND | RELEASE_NOT_FOUND | PASS | [14] |
| phase-2.receiver-replaced · Only the receiver receives new startup settings. | false | false | PASS | [] |
| phase-2.publisher-survived · The original publisher remains running. | true | true | PASS | [] |
| phase-2.endpoints-preserved · Both owned HTTP endpoints remain unchanged. | true | true | PASS | [] |
| phase-2-configuration.http · Observed HTTP status for phase-2-configuration | 200 | 200 | PASS | [15] |
| phase-2.live-policy · The real receiver enforces this phase's startup policy. | SHADOW_PQC | SHADOW_PQC | PASS | [15] |
| phase-2.claims-and-time · The same signed identity, audience and freshness remain enforced. | [release-publisher, urn:pqclab:release-receiver, PT5M, PT30S] | [release-publisher, urn:pqclab:release-receiver, PT5M, PT30S] | PASS | [15] |
| phase-2-registry.http · Observed HTTP status for phase-2-registry | 200 | 200 | PASS | [16] |
| phase-2.trust-preserved · Policy deployment does not rotate or replace any public trust. | {"managed":true,"revision":1,"issuer":"release-publisher","activeGenerationId":"9130d72e-d099-4d07-9225-f38786727bec","gracePeriodSeconds":6… (see JSON) | {"managed":true,"revision":1,"issuer":"release-publisher","activeGenerationId":"9130d72e-d099-4d07-9225-f38786727bec","gracePeriodSeconds":6… (see JSON) | PASS | [16] |
| phase-2-classical-evaluation.http · Observed HTTP status for phase-2-classical-evaluation | 200 | 200 | PASS | [17] |
| phase-2-classical.evaluation-policy · Read-only Java observations identify the actual live requirement. | SHADOW_PQC | SHADOW_PQC | PASS | [17] |
| phase-2-classical.verified-signatures · Java independently verifies this sender's actual signatures. | [true, false] | [true, false] | PASS | [17] |
| phase-2-classical-delivery.http · Observed HTTP status for phase-2-classical-delivery | 200 | 200 | PASS | [18] |
| phase-2-classical.delivery-status · Delivery creates or reuses the one durable business result. | ALREADY_ACCEPTED | ALREADY_ACCEPTED | PASS | [18] |
| phase-2-classical-delivery.status · The response identifies the observed delivery or historical query. | ALREADY_ACCEPTED | ALREADY_ACCEPTED | PASS | [18] |
| phase-2-classical-delivery.enforced-policy · The current requirement is reported separately from original acceptance. | SHADOW_PQC | SHADOW_PQC | PASS | [18] |
| phase-2-classical-delivery.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | PASS | [7, 18] |
| phase-2-classical-history.http · Observed HTTP status for phase-2-classical-history | 200 | 200 | PASS | [19] |
| phase-2-classical-history.status · The response identifies the observed delivery or historical query. | STORED | STORED | PASS | [19] |
| phase-2-classical-history.enforced-policy · The current requirement is reported separately from original acceptance. | SHADOW_PQC | SHADOW_PQC | PASS | [19] |
| phase-2-classical-history.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | PASS | [7, 19] |
| phase-2-dual-evaluation.http · Observed HTTP status for phase-2-dual-evaluation | 200 | 200 | PASS | [20] |
| phase-2-dual.evaluation-policy · Read-only Java observations identify the actual live requirement. | SHADOW_PQC | SHADOW_PQC | PASS | [20] |
| phase-2-dual.verified-signatures · Java independently verifies this sender's actual signatures. | [true, true] | [true, true] | PASS | [20] |
| phase-2-dual-delivery.http · Observed HTTP status for phase-2-dual-delivery | 200 | 200 | PASS | [21] |
| phase-2-dual.delivery-status · Delivery creates or reuses the one durable business result. | ALREADY_ACCEPTED | ALREADY_ACCEPTED | PASS | [21] |
| phase-2-dual-delivery.status · The response identifies the observed delivery or historical query. | ALREADY_ACCEPTED | ALREADY_ACCEPTED | PASS | [21] |
| phase-2-dual-delivery.enforced-policy · The current requirement is reported separately from original acceptance. | SHADOW_PQC | SHADOW_PQC | PASS | [21] |
| phase-2-dual-delivery.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | PASS | [10, 21] |
| phase-2-dual-history.http · Observed HTTP status for phase-2-dual-history | 200 | 200 | PASS | [22] |
| phase-2-dual-history.status · The response identifies the observed delivery or historical query. | STORED | STORED | PASS | [22] |
| phase-2-dual-history.enforced-policy · The current requirement is reported separately from original acceptance. | SHADOW_PQC | SHADOW_PQC | PASS | [22] |
| phase-2-dual-history.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | PASS | [10, 22] |
| phase-2-pqc-evaluation.http · Observed HTTP status for phase-2-pqc-evaluation | 200 | 200 | PASS | [23] |
| phase-2-pqc.evaluation-policy · Read-only Java observations identify the actual live requirement. | SHADOW_PQC | SHADOW_PQC | PASS | [23] |
| phase-2-pqc.verified-signatures · Java independently verifies this sender's actual signatures. | [false, true] | [false, true] | PASS | [23] |
| phase-2-pqc-delivery.http · Observed HTTP status for phase-2-pqc-delivery | 403 | 403 | PASS | [24] |
| phase-2-pqc.policy-rejected · Insufficient signatures fail the live requirement even after prior acceptance. | POLICY_REJECTED | POLICY_REJECTED | PASS | [24] |
| phase-2-pqc-history.http · Observed HTTP status for phase-2-pqc-history | 404 | 404 | PASS | [25] |
| phase-2-pqc.no-write · Rejected/evaluated events have no durable result. | RELEASE_NOT_FOUND | RELEASE_NOT_FOUND | PASS | [25] |
| phase-3.receiver-replaced · Only the receiver receives new startup settings. | false | false | PASS | [] |
| phase-3.publisher-survived · The original publisher remains running. | true | true | PASS | [] |
| phase-3.endpoints-preserved · Both owned HTTP endpoints remain unchanged. | true | true | PASS | [] |
| phase-3-configuration.http · Observed HTTP status for phase-3-configuration | 200 | 200 | PASS | [26] |
| phase-3.live-policy · The real receiver enforces this phase's startup policy. | BOTH_REQUIRED | BOTH_REQUIRED | PASS | [26] |
| phase-3.claims-and-time · The same signed identity, audience and freshness remain enforced. | [release-publisher, urn:pqclab:release-receiver, PT5M, PT30S] | [release-publisher, urn:pqclab:release-receiver, PT5M, PT30S] | PASS | [26] |
| phase-3-registry.http · Observed HTTP status for phase-3-registry | 200 | 200 | PASS | [27] |
| phase-3.trust-preserved · Policy deployment does not rotate or replace any public trust. | {"managed":true,"revision":1,"issuer":"release-publisher","activeGenerationId":"9130d72e-d099-4d07-9225-f38786727bec","gracePeriodSeconds":6… (see JSON) | {"managed":true,"revision":1,"issuer":"release-publisher","activeGenerationId":"9130d72e-d099-4d07-9225-f38786727bec","gracePeriodSeconds":6… (see JSON) | PASS | [27] |
| phase-3-classical-evaluation.http · Observed HTTP status for phase-3-classical-evaluation | 200 | 200 | PASS | [28] |
| phase-3-classical.evaluation-policy · Read-only Java observations identify the actual live requirement. | BOTH_REQUIRED | BOTH_REQUIRED | PASS | [28] |
| phase-3-classical.verified-signatures · Java independently verifies this sender's actual signatures. | [true, false] | [true, false] | PASS | [28] |
| phase-3-classical-delivery.http · Observed HTTP status for phase-3-classical-delivery | 403 | 403 | PASS | [29] |
| phase-3-classical.policy-rejected · Insufficient signatures fail the live requirement even after prior acceptance. | POLICY_REJECTED | POLICY_REJECTED | PASS | [29] |
| phase-3-classical-history.http · Observed HTTP status for phase-3-classical-history | 200 | 200 | PASS | [30] |
| phase-3-classical-history.status · The response identifies the observed delivery or historical query. | STORED | STORED | PASS | [30] |
| phase-3-classical-history.enforced-policy · The current requirement is reported separately from original acceptance. | BOTH_REQUIRED | BOTH_REQUIRED | PASS | [30] |
| phase-3-classical-history.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | PASS | [7, 30] |
| phase-3-dual-evaluation.http · Observed HTTP status for phase-3-dual-evaluation | 200 | 200 | PASS | [31] |
| phase-3-dual.evaluation-policy · Read-only Java observations identify the actual live requirement. | BOTH_REQUIRED | BOTH_REQUIRED | PASS | [31] |
| phase-3-dual.verified-signatures · Java independently verifies this sender's actual signatures. | [true, true] | [true, true] | PASS | [31] |
| phase-3-dual-delivery.http · Observed HTTP status for phase-3-dual-delivery | 200 | 200 | PASS | [32] |
| phase-3-dual.delivery-status · Delivery creates or reuses the one durable business result. | ALREADY_ACCEPTED | ALREADY_ACCEPTED | PASS | [32] |
| phase-3-dual-delivery.status · The response identifies the observed delivery or historical query. | ALREADY_ACCEPTED | ALREADY_ACCEPTED | PASS | [32] |
| phase-3-dual-delivery.enforced-policy · The current requirement is reported separately from original acceptance. | BOTH_REQUIRED | BOTH_REQUIRED | PASS | [32] |
| phase-3-dual-delivery.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | PASS | [10, 32] |
| phase-3-dual-history.http · Observed HTTP status for phase-3-dual-history | 200 | 200 | PASS | [33] |
| phase-3-dual-history.status · The response identifies the observed delivery or historical query. | STORED | STORED | PASS | [33] |
| phase-3-dual-history.enforced-policy · The current requirement is reported separately from original acceptance. | BOTH_REQUIRED | BOTH_REQUIRED | PASS | [33] |
| phase-3-dual-history.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | PASS | [10, 33] |
| phase-3-pqc-evaluation.http · Observed HTTP status for phase-3-pqc-evaluation | 200 | 200 | PASS | [34] |
| phase-3-pqc.evaluation-policy · Read-only Java observations identify the actual live requirement. | BOTH_REQUIRED | BOTH_REQUIRED | PASS | [34] |
| phase-3-pqc.verified-signatures · Java independently verifies this sender's actual signatures. | [false, true] | [false, true] | PASS | [34] |
| phase-3-pqc-delivery.http · Observed HTTP status for phase-3-pqc-delivery | 403 | 403 | PASS | [35] |
| phase-3-pqc.policy-rejected · Insufficient signatures fail the live requirement even after prior acceptance. | POLICY_REJECTED | POLICY_REJECTED | PASS | [35] |
| phase-3-pqc-history.http · Observed HTTP status for phase-3-pqc-history | 404 | 404 | PASS | [36] |
| phase-3-pqc.no-write · Rejected/evaluated events have no durable result. | RELEASE_NOT_FOUND | RELEASE_NOT_FOUND | PASS | [36] |
| phase-3-unsigned-downgrade.http · Observed HTTP status for phase-3-unsigned-downgrade | 403 | 403 | PASS | [37] |
| phase-3.unsigned-policy-rejected · Unsigned sender metadata cannot select the receiver's requirement. | POLICY_REJECTED | POLICY_REJECTED | PASS | [37] |
| phase-4.receiver-replaced · Only the receiver receives new startup settings. | false | false | PASS | [] |
| phase-4.publisher-survived · The original publisher remains running. | true | true | PASS | [] |
| phase-4.endpoints-preserved · Both owned HTTP endpoints remain unchanged. | true | true | PASS | [] |
| phase-4-configuration.http · Observed HTTP status for phase-4-configuration | 200 | 200 | PASS | [38] |
| phase-4.live-policy · The real receiver enforces this phase's startup policy. | PQC_REQUIRED | PQC_REQUIRED | PASS | [38] |
| phase-4.claims-and-time · The same signed identity, audience and freshness remain enforced. | [release-publisher, urn:pqclab:release-receiver, PT5M, PT30S] | [release-publisher, urn:pqclab:release-receiver, PT5M, PT30S] | PASS | [38] |
| phase-4-registry.http · Observed HTTP status for phase-4-registry | 200 | 200 | PASS | [39] |
| phase-4.trust-preserved · Policy deployment does not rotate or replace any public trust. | {"managed":true,"revision":1,"issuer":"release-publisher","activeGenerationId":"9130d72e-d099-4d07-9225-f38786727bec","gracePeriodSeconds":6… (see JSON) | {"managed":true,"revision":1,"issuer":"release-publisher","activeGenerationId":"9130d72e-d099-4d07-9225-f38786727bec","gracePeriodSeconds":6… (see JSON) | PASS | [39] |
| phase-4-classical-evaluation.http · Observed HTTP status for phase-4-classical-evaluation | 200 | 200 | PASS | [40] |
| phase-4-classical.evaluation-policy · Read-only Java observations identify the actual live requirement. | PQC_REQUIRED | PQC_REQUIRED | PASS | [40] |
| phase-4-classical.verified-signatures · Java independently verifies this sender's actual signatures. | [true, false] | [true, false] | PASS | [40] |
| phase-4-classical-delivery.http · Observed HTTP status for phase-4-classical-delivery | 403 | 403 | PASS | [41] |
| phase-4-classical.policy-rejected · Insufficient signatures fail the live requirement even after prior acceptance. | POLICY_REJECTED | POLICY_REJECTED | PASS | [41] |
| phase-4-classical-history.http · Observed HTTP status for phase-4-classical-history | 200 | 200 | PASS | [42] |
| phase-4-classical-history.status · The response identifies the observed delivery or historical query. | STORED | STORED | PASS | [42] |
| phase-4-classical-history.enforced-policy · The current requirement is reported separately from original acceptance. | PQC_REQUIRED | PQC_REQUIRED | PASS | [42] |
| phase-4-classical-history.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"dd6d8ceb-b6e5-411a-a56e-632fce125d59","eventId":"c6e31721-9184-4cb7-8a58-4e26f51a3535","issuer":"release-publisher","artifact"… (see JSON) | PASS | [7, 42] |
| phase-4-dual-evaluation.http · Observed HTTP status for phase-4-dual-evaluation | 200 | 200 | PASS | [43] |
| phase-4-dual.evaluation-policy · Read-only Java observations identify the actual live requirement. | PQC_REQUIRED | PQC_REQUIRED | PASS | [43] |
| phase-4-dual.verified-signatures · Java independently verifies this sender's actual signatures. | [true, true] | [true, true] | PASS | [43] |
| phase-4-dual-delivery.http · Observed HTTP status for phase-4-dual-delivery | 200 | 200 | PASS | [44] |
| phase-4-dual.delivery-status · Delivery creates or reuses the one durable business result. | ALREADY_ACCEPTED | ALREADY_ACCEPTED | PASS | [44] |
| phase-4-dual-delivery.status · The response identifies the observed delivery or historical query. | ALREADY_ACCEPTED | ALREADY_ACCEPTED | PASS | [44] |
| phase-4-dual-delivery.enforced-policy · The current requirement is reported separately from original acceptance. | PQC_REQUIRED | PQC_REQUIRED | PASS | [44] |
| phase-4-dual-delivery.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | PASS | [10, 44] |
| phase-4-dual-history.http · Observed HTTP status for phase-4-dual-history | 200 | 200 | PASS | [45] |
| phase-4-dual-history.status · The response identifies the observed delivery or historical query. | STORED | STORED | PASS | [45] |
| phase-4-dual-history.enforced-policy · The current requirement is reported separately from original acceptance. | PQC_REQUIRED | PQC_REQUIRED | PASS | [45] |
| phase-4-dual-history.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"def52704-fe4f-456c-aaa6-7598e4e86f02","eventId":"15217ab3-8bc6-42af-b33f-13da174f1718","issuer":"release-publisher","artifact"… (see JSON) | PASS | [10, 45] |
| phase-4-pqc-evaluation.http · Observed HTTP status for phase-4-pqc-evaluation | 200 | 200 | PASS | [46] |
| phase-4-pqc.evaluation-policy · Read-only Java observations identify the actual live requirement. | PQC_REQUIRED | PQC_REQUIRED | PASS | [46] |
| phase-4-pqc.verified-signatures · Java independently verifies this sender's actual signatures. | [false, true] | [false, true] | PASS | [46] |
| phase-4-pqc-delivery.http · Observed HTTP status for phase-4-pqc-delivery | 201 | 201 | PASS | [47] |
| phase-4-pqc.delivery-status · Delivery creates or reuses the one durable business result. | ACCEPTED | ACCEPTED | PASS | [47] |
| phase-4-pqc.first-acceptance · A new result records its actual first policy and signature evidence. | [d900ba34-0426-4159-bd76-d3835211c91b, PQC_REQUIRED, false, true] | [d900ba34-0426-4159-bd76-d3835211c91b, PQC_REQUIRED, false, true] | PASS | [47] |
| phase-4-pqc-delivery.status · The response identifies the observed delivery or historical query. | ACCEPTED | ACCEPTED | PASS | [47] |
| phase-4-pqc-delivery.enforced-policy · The current requirement is reported separately from original acceptance. | PQC_REQUIRED | PQC_REQUIRED | PASS | [47] |
| phase-4-pqc-delivery.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"4812a119-9443-4500-94b4-4b05c003c68e","eventId":"d900ba34-0426-4159-bd76-d3835211c91b","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"4812a119-9443-4500-94b4-4b05c003c68e","eventId":"d900ba34-0426-4159-bd76-d3835211c91b","issuer":"release-publisher","artifact"… (see JSON) | PASS | [47, 47] |
| phase-4-pqc-history.http · Observed HTTP status for phase-4-pqc-history | 200 | 200 | PASS | [48] |
| phase-4-pqc-history.status · The response identifies the observed delivery or historical query. | STORED | STORED | PASS | [48] |
| phase-4-pqc-history.enforced-policy · The current requirement is reported separately from original acceptance. | PQC_REQUIRED | PQC_REQUIRED | PASS | [48] |
| phase-4-pqc-history.original-preserved · Every original receipt identity, claim, time, policy and signature result remains unchanged. | {"receiptId":"4812a119-9443-4500-94b4-4b05c003c68e","eventId":"d900ba34-0426-4159-bd76-d3835211c91b","issuer":"release-publisher","artifact"… (see JSON) | {"receiptId":"4812a119-9443-4500-94b4-4b05c003c68e","eventId":"d900ba34-0426-4159-bd76-d3835211c91b","issuer":"release-publisher","artifact"… (see JSON) | PASS | [47, 48] |
| phase-4-unsigned-downgrade.http · Observed HTTP status for phase-4-unsigned-downgrade | 403 | 403 | PASS | [49] |
| phase-4.unsigned-policy-rejected · Unsigned sender metadata cannot select the receiver's requirement. | POLICY_REJECTED | POLICY_REJECTED | PASS | [49] |
| rollout.complete · All three original events have exactly one distinct durable receipt. | 3 | 3 | PASS | [] |

## Inspect the evidence

[Structured report](report.json) · [Digest manifest](SHA256SUMS)

The structured timeline links exact request and validated public response bodies in http/. Repeated original deliveries use identical request bytes and therefore identical SHA-256 digests. Receipt comparisons include every original acceptance field, not just an HTTP status or receipt ID. The manifest covers both reports and every referenced HTTP artifact. Verify it before reviewing a copied bundle.

## Boundaries

- Public synthetic data only. Runtime databases, encrypted vaults, wrapping secrets, headers, usernames and absolute runtime paths are excluded. Temporary runtime material is removed after all servers stop.
- This is a local, single-JVM laboratory. It does not demonstrate post-quantum TLS, production operator authentication, KMS isolation, multi-process trust distribution or filesystem anti-rollback.
- These are functional observations, not latency benchmarks, security certification or a proof of cryptographic implementation correctness. Separate JMH evidence has its own measurement method.
- SHA-256 checksums identify bytes and detect changes relative to this manifest. The manifest is unsigned and does not establish an independent source of trust or protect against deliberate replacement of the whole bundle.
- A failed or incomplete run stops dependent actions and retains only actual observations. A missing SHA256SUMS file means bundle generation did not finish.
