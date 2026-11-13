# Screenshot provenance

Captured from the actual packaged local workbench on 13 November 2026, using synthetic software-release data. Images are browser screenshots with no composited UI, invented verdicts or private runtime files.

| Image                                              | Observed state                                                                                                                                                          |
| -------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [workbench.jpg](workbench.jpg)                     | A freshly generated dual-signed event, original JSON payload, byte sizes and accepting Java policy comparisons                                                          |
| [signature-stripping.jpg](signature-stripping.jpg) | The original payload after removal of its verified PQC signature; classical comparisons accept, both-required and PQC-required reject; actual delivery returns HTTP 403 |
| [key-lifecycle.jpg](key-lifecycle.jpg)             | Receiver trust revision 3, an active generation and a revoked predecessor, with public fingerprints and explicit controls                                               |

The original dual-signed event was delivered successfully with HTTP 201 before the stripping experiment. Its durable receipt remains queryable in the rejection view.

The lifecycle state comes from the existing local synthetic session. No extra rotation or revocation was performed to manufacture a screenshot. New installations and subsequent runs will have different identifiers, fingerprints, timestamps and session-history counts.

## Reproduce the views

1. Run `.\lab.ps1 serve` and open `http://127.0.0.1:8781/`.
2. Create a fresh dual-signed synthetic event and deliver it.
3. Inspect the payload and policy comparisons.
4. Apply **Remove PQC signature**, then deliver the result under BOTH_REQUIRED.
5. For a separate lifecycle experiment, rotate a generation, review the overlap and revoke the predecessor. Read actual public state in **Key lifecycle**.

Event freshness remains active. Preserve screenshot captions that describe the observed scenario rather than treating a historical screenshot as current runtime evidence.

The original application screenshots are covered by the project's [Apache-2.0 licence](../../LICENSE). Fonts and icons retain their upstream terms, described in the [third-party notices](../../THIRD_PARTY_NOTICES.md).
