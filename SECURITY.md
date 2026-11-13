# Security policy

PQC Migration Lab 0.1.0 is a local synthetic reference release. Its security assumptions and exclusions are documented in the [threat model](docs/threat-model.md). There is no production deployment or production support commitment.

## Report a suspected issue

Contact [balazsfalvi12@gmail.com](mailto:balazsfalvi12@gmail.com) with a minimal synthetic reproduction, the affected component and your local revision or report identifier.

Please keep unpublished vulnerabilities, real credentials, private keys and personal data out of public issues and screenshots. A safe reproduction should use fresh laboratory keys and synthetic release content.

No response-time commitment or production support agreement is implied.

## Intended boundary

- Both HTTP listeners bind to loopback. Local control endpoints assume the trusted OS account and do not provide production operator authentication.
- Verification, signed-claim validation, current public trust and durable idempotency are enforced in Java.
- The receiver is supplied public verification capabilities only. Active private signing keys belong to the publisher.
- The encrypted local vault and external account secret are private runtime material. Back up both deliberately; do not commit them or attach them to an issue.
- Exported evidence is synthetic and public-only. Checksum manifests identify bytes; they are not independently authenticated provenance.
- The OS account, JVM, providers, installed dependencies and UTC clock are trusted.

Remote hosting, multi-process trust distribution, KMS custody, filesystem anti-rollback and post-quantum TLS require a separate design and review.

## Before sharing a change

Run the documented quality gates and repeat the [publication review](docs/publication-review.md) against the exact working files intended for Git. Automated secret and advisory checks are evidence about that snapshot and query date, not a guarantee that all security defects have been found.
