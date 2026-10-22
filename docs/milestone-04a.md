# Milestone 04A — Reproducible benchmark evidence

This module measures the existing JCA, DSSE and strict JSON adapters. It adds no cryptographic implementation and no live receiver dependency. Managed key storage, rotation and revocation are documented separately in [milestone 04B](milestone-04b.md); benchmark keys remain ephemeral.

## Reproduce a run

Requirements: JDK 25 and the checked-in Gradle wrapper. Node, Spring Boot, Docker and the running workbench are unnecessary.

```powershell
.\lab.ps1 benchmark -BenchmarkProfile smoke
.\lab.ps1 benchmark -BenchmarkProfile quick
.\lab.ps1 benchmark -BenchmarkProfile standard
```

On any supported OS, with `JAVA_HOME` pointing to JDK 25:

```sh
./gradlew benchmarkSmoke
./gradlew benchmarkQuick
./gradlew benchmark
```

For a custom evidence parent directory, use `:benchmarks:run --args="--profile standard --output <directory>"` or the installed command-line distribution. Options are strict, case-sensitive and reject unknown, duplicate or missing values.

## Fixed measurement profiles

All profiles use JMH AverageTime in microseconds per operation, one measured thread, explicit JVM forks and `-Xms256m -Xmx256m`. Fork arguments replace parent instrumentation flags. Explicit garbage collection between iterations is disabled; normal JVM allocation and collection remain part of execution.

| Profile | JVM forks per case | Warm-up per fork | Measurement per fork | Payload sizes | Cases |
| --- | ---: | --- | --- | --- | ---: |
| smoke | 1 | 1 × 100 ms | 1 × 100 ms | 256 bytes | 21 |
| quick | 2 | 2 × 250 ms | 3 × 250 ms | 256, 4096 bytes | 39 |
| standard | 2 | 3 × 1 s | 5 × 1 s | 256, 4096, 65536 bytes | 57 |

Smoke validates annotation-generated harness discovery, fork startup, case coverage and report publication. Its timings are deliberately labelled unsuitable for comparative claims. Quick is a short exploratory comparison. Standard is the default full matrix, with ten measured iteration samples per case across two independent JVMs.

The standard matrix has at least 912 seconds of timed iterations, before process startup and setup. Stop competing builds and heavy workloads while collecting results. A general-purpose workstation still has uncontrolled scheduling, power and thermal effects; repeat on deployment hardware before making capacity decisions.

## Algorithms and operation units

`ED25519` uses the classical adapter, `ML_DSA_65` uses pure ML-DSA-65 with its existing empty context, and `DUAL` executes both sequentially on the same input. One dual operation is the complete pair, not a per-signature average. This is independent multiple-signature composition, not a standardized hybrid signature construction.

JCA selects the available default provider once per trial, then the adapter fixes that provider instance. Actual provider names and versions are recorded. No provider is globally added, removed or reordered. This milestone measures the native local defaults; the earlier provider-interoperability proof remains separate.

| Operation | Inside the timed loop | Prepared outside timing |
| --- | --- | --- |
| `generateKeyPairs` | Fresh JCA key-pair generator construction, secure randomness and one pair per required algorithm | Provider selection |
| `signMessage` | Raw signing through fresh production JCA signature primitives | Keys, raw payload |
| `verifyMessage` | All required raw signature verifications through fresh primitives | Keys, payload and valid signatures |
| `signEnvelope` | Payload snapshots, PAE, signing, base64 and immutable DSSE construction | Keys and payload |
| `verifyEnvelope` | Base64 decoding, PAE reconstruction, candidate-entry scanning and all required verifications | Existing valid envelope and trusted keys |
| `encodeEnvelope` | Strict JSON serialization to UTF-8 | Existing signed envelope |
| `decodeEnvelope` | Bounded strict JSON parsing and immutable envelope construction | Existing wire bytes |

Each method returns its result to JMH so the harness consumes it. State is thread-scoped and initialized at trial level. Verification never signs or generates keys in its timing loop. Every setup verifies its prepared inputs before measurements begin.

Key generation is size-independent and therefore has only three cases. The six input-dependent operations have one case per algorithm/size pair. The largest decoded payload is the laboratory's existing 65,536-byte bound.

Inputs are deterministic opaque synthetic bytes with payload type `application/vnd.pqclab.benchmark.v1`. Private-key generation retains the provider's secure randomness. No deterministic crypto seed is introduced. These bytes do not claim to be business release JSON; business schema, HTTP, policy, replay, H2 and TLS costs are outside this experiment.

## Direct byte observations

Before timing, the same adapters independently produce:

- Decoded payload length and authenticated PAE length.
- X.509/SPKI public-key byte lengths, one per algorithm.
- Raw signature byte lengths, one per algorithm.
- Complete strict DSSE JSON UTF-8 byte length.

No private-key encoding or key object is serialized. These are byte counts, not latency-derived estimates. Dual lists retain Ed25519 then ML-DSA order; individual public keys remain independent formats.

## Evidence and truthful completion

Every invocation creates a unique run directory under `build/reports/benchmarks/`:

Gradle `clean` removes build reports. For long-lived evidence, choose a separate local parent with `--output .local/benchmark-evidence` or archive a completed run before cleaning.

| File | Purpose |
| --- | --- |
| `manifest.json` | RUNNING, COMPLETED or FAILED status, run ID, method and timestamps |
| `jmh-results.json` | Unmodified JMH JSON, including per-fork raw samples and confidence data |
| `jmh-output.log` | Full harness configuration, warm-up, measured iterations and diagnostics |
| `report.json` | Versioned summary, environment, method, measured results and direct byte observations |
| `report.md` | Human-readable tables, method, uncertainty and interpretation limits |

The command rejects missing, duplicated, unexpected or misconfigured observations before writing a complete report. The matrix must match exactly, with the configured fork count, iteration sample count and units. The manifest is marked COMPLETED only after the summary files have been written. An interrupted run remains incomplete; failures return a nonzero process exit.

Average scores must be finite. JMH's 99.9% confidence half-width is preserved. An unavailable or non-finite error estimate becomes JSON null and “Unavailable” in Markdown, rather than fabricated zero uncertainty. No confidence interval is silently clamped into a claim of precision.

Environment metadata includes JDK/VM version and vendor, OS/version, CPU architecture, available logical processors, the OS-provided processor description when available, harness version and actual providers. The typed summary excludes account names, host names, full environment variables and private keys. Raw JMH output retains local JVM/classpath details; review local paths before future public publication.

## Build and test boundaries

JMH and its annotation processor are pinned to 1.37, the published Maven Central release verified for this implementation. Dependency locks and artifact verification metadata fix the graph. The Gradle application distribution includes the generated benchmark registry and timing loops.

Functional tests verify real signing/verification, all profiles/sizes, unchanged provider registration, generated harness discovery, exact wire counts, method configuration, matrix completeness, public-only serialization, strict CLI parsing and failure manifests. Architecture rules keep benchmarks out of the protocol/adapters and keep Spring/integration services out of the benchmark module.

The existing 90% line-coverage gate applies to all handwritten benchmark and report classes. Only JMH-generated timing loops are excluded. Compilation/Javadoc warnings remain errors for project code.

The prepared Windows/Linux CI workflow runs the actual forked smoke matrix with no timing thresholds. CI results are harness checks and cannot establish which algorithm is faster.

JMH 1.37 emits a JDK 25 warning about its use of `sun.misc.Unsafe.objectFieldOffset`. The pinned release successfully runs this project's generated harness; the upstream warning remains visible and is not suppressed. Revisit it when upgrading the harness or JDK.

## Local verification

Verified on Windows on 13 November 2026:

- All 177 Java tests and 11 frontend unit tests passed, including architecture, formatting, strict Javadoc/TypeScript and per-module coverage gates.
- Handwritten benchmark/report line coverage was 95.13%; only generated JMH harness classes were excluded.
- The earlier 53 cryptographic probe expectations and 23 real HTTP demonstration expectations passed.
- The forked smoke matrix completed all 21 cases.
- The installed command-line distribution completed the full standard matrix: 57 cases, 114 JVM forks and 570 measured iteration samples, in approximately 16 minutes.
- An independent comparison matched every summary score, error estimate, fork count and sample count to the unmodified JMH JSON. All nine transport-size observations were retained.

This is local evidence. The prepared GitHub Actions workflow has not been executed remotely.

## Primary references

- [OpenJDK JMH 1.37 source and documentation](https://github.com/openjdk/jmh/tree/1.37).
- [JMH's forking example](https://github.com/openjdk/jmh/blob/1.37/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_12_Forking.java).
- [JMH's constant-folding guidance](https://github.com/openjdk/jmh/blob/1.37/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_10_ConstantFold.java).
- [JMH annotation configuration example](https://github.com/openjdk/jmh/blob/1.37/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_20_Annotations.java).
