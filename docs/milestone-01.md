# Milestone 01 · Technical proof

## Question

Can two independent Java cryptographic implementations exchange Ed25519 and ML-DSA-65 signatures and key encodings, then use those signatures in a byte-correct DSSE integration boundary?

## Implemented acceptance criteria

- [x] Generate ephemeral Ed25519 and ML-DSA-65 key pairs with the JDK and Bouncy Castle.
- [x] Sign and verify using each provider.
- [x] Verify across providers in both directions using SPKI public-key imports.
- [x] Import PKCS#8 private keys in both directions and verify the resulting signatures.
- [x] Use provider instances explicitly without altering global registration or ordering.
- [x] Implement DSSE PAE with byte-counted UTF-8 types and opaque binary bodies.
- [x] Round-trip DSSE JSON through the complete provider matrix.
- [x] Verify a two-signature envelope independently against each trusted key.
- [x] Reject message, signature and payload-type tampering, truncated signatures and unrelated keys.
- [x] Prove that unauthenticated hints cannot replace explicit caller trust.
- [x] Accept DSSE's two base64 alphabets and ignore unknown extensions.
- [x] Reject ambiguous, malformed and oversized JSON inputs.
- [x] Return the original verified payload snapshot.
- [x] Verify independent RFC and NIST public fixtures.
- [x] Produce public, machine-readable and human-readable functional evidence.
- [x] Enforce dependency boundaries, formatting, documentation and coverage locally.

## Reproduce

```powershell
.\lab.ps1 verify
.\lab.ps1 probe
```

The probe has 53 explicit expectations. Failed setup is recorded as failed evidence, not omitted as an unsupported case. The command exits unsuccessfully if any expectation fails or a report cannot be written.

Unit and integration-style tests include additional JSON shape, resource-limit, concurrent adapter reuse and published-answer checks. Their test counts are available in Gradle's generated reports.

The local quality gate requires at least 90% line coverage in each module. It also compiles with all Java warnings treated as errors, checks formatting, builds strict Javadoc, and runs ArchUnit against production classes.

The generated evidence is intentionally ignored by Git. Commit reproducible source and public fixtures; generate fresh observations for the runtime being evaluated.

## Decisions

| Decision | Reason |
| --- | --- |
| JDK 25 | An LTS baseline with native ML-DSA support, enabling an independent JDK/BC comparison |
| Standard JCA adapter | Provider selection remains outside the protocol core |
| Bouncy Castle as an explicit provider object | Experiments do not alter global provider order |
| Four small modules | Cryptography, JSON and executable composition have separate boundaries |
| Original payload bytes | Verification cannot accidentally authenticate one representation and consume another |
| Public fixtures pinned to a source commit | Tests do not depend on a changing network resource |
| Functional checks rather than elapsed timings | A short CLI run cannot provide defensible performance conclusions |

## Limits of this evidence

This working copy has been exercised on Windows with a project-local Temurin JDK 25. GitHub Actions configuration includes Linux and Windows, but has not run remotely.

Passing these checks does not certify a cryptographic module or prove a complete integration secure. No original cryptographic primitive is implemented here. Milestone two adds replay handling and multi-key migration policy in a separate application layer; durable key management, revocation, operational telemetry and performance measurement remain later work.

Private-key interoperability is demonstrated in memory using provider-generated PKCS#8 encodings. It does not promise compatibility with every historical key container, hardware module or third-party provider.
