# Publication review · 05B, part one

**Reviewed locally on 13 November 2026.** Scope: the standalone PQC Migration Lab source, build configuration, prepared CI, retained notices, screenshot assets and README.

The review and README work are complete for this part. Two build-time dependency findings were fixed and a missing upstream notice was retained. Project-licence selection, Linux execution and the first public release remain open decisions; this report does not mark the entire publication milestone complete.

## Findings and disposition

| ID     | Finding                                                                                                               | Disposition                                                                                                                              |
| ------ | --------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| PUB-01 | Spring Boot's Gradle-plugin classpath selected Commons Lang 3.16.0, covered by CVE-2025-48924                         | Fixed with a buildscript-only minimum constraint at 3.18.0. The classpath report confirms 3.16.0 → 3.18.0.                               |
| PUB-02 | Spotless's Google Java Format unused-import step selected Guava 33.6.0-jre, covered by CVE-2026-102554                | Fixed with a component-metadata rule scoped to that formatter's Guava dependency, including detached configurations; minimum 33.7.2-jre. |
| PUB-03 | NIST verification fixtures had attribution and source URLs, but the complete upstream notice was not retained locally | Fixed by retaining the pinned upstream notice and documenting the public-data adaptation.                                                |
| PUB-04 | Runtime files were excluded by directory, but key/database files placed elsewhere lacked explicit ignore rules        | Hardened with key-container, vault-marker and H2 file patterns. Candidate tracked files were checked separately.                         |
| PUB-05 | Prepared CI had no project-specific deadline or redundant-run cancellation                                            | Added a 30-minute job deadline and same-ref concurrency cancellation; local workflow lint passed.                                        |
| PUB-06 | README led with milestone inventories and depended on readers starting the application                                | Rewritten around purpose, three actual screenshots, observed behaviour, quick start, architecture and evidence.                          |
| PUB-07 | No project LICENSE file                                                                                               | Open owner decision before describing a distribution as open source. No project licence was chosen by this review.                       |
| PUB-08 | Windows source-only execution is verified; Linux and remote GitHub Actions execution are not                          | Open validation gap. The workflow is prepared and pinned, with no remote green-status claim.                                             |
| PUB-09 | No initial commit, remote or public release                                                                           | Future release preparation/publication. This task made no commit, tag, remote, upload or deployment.                                     |

The dependency findings were build-tool issues, not newly discovered vulnerabilities in an application runtime dependency. The fixes change build resolution only. They do not change receiver policy, cryptographic algorithms, API contracts or private-key storage behaviour.

The old vulnerable Guava and Commons Lang artifact allowances were removed from the verification metadata. New Commons Lang and parent/BOM metadata were fetched from Maven Central over HTTPS, cross-checked against its published delivery digests and pinned with SHA-256. Dependency verification remained strict throughout.

## Candidate-source review

The repository has no committed history yet. The review therefore scanned copies of the **working files selected by Git's tracked-file inventory**, including intent-to-add files. Scanning the empty Git history or empty index blobs would not represent the code being prepared for publication.

The initial inventory contained 218 files. A final scan included the new documentation and image assets. The local snapshot and its hashes are retained under the ignored `.local/publication-review/` directory.

Checks included:

- Gitleaks 8.30.1 with the release's explicit official rule configuration, full redaction and no project-specific suppression rules.
- A separate textual review for personal Windows paths, the private workspace/partner identifiers, server addresses and PEM private-key headers.
- Candidate names and Git ignore behaviour for runtime secrets, database files, generated outputs, local toolchains, dependency directories and logs.
- Wrapper authenticity, pinned Actions references, public fixture attribution and bundled-font notices.

The final candidate scan reported **zero secret findings**. No private-workspace identifier, personal home path or supplied server address was found in the candidate text. This is scoped evidence from known rules and reviewed patterns, not a general proof that every possible secret or privacy issue has been detected.

Runtime vaults, the external account secret and the interactive database were not opened for this source scan.

## Dependency advisory checks

| Inventory                                                         | Method                                                        | Observed result after fixes                                                         |
| ----------------------------------------------------------------- | ------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| npm production and development lock graph                         | npm 11 audit against the public npm registry                  | Zero advisories returned; 103 dependency entries reported                           |
| 87 locked Maven coordinates plus recorded verification components | OSV batch queries for exact public package names and versions | 281 distinct coordinates queried; zero matches returned; no remaining pagination    |
| Build-tool resolution                                             | Forced rebuild and actual buildscript classpath report        | Patched Commons Lang selected; obsolete vulnerable artifact allowances not required |

Package names and versions were queried against public advisory services. No source code, runtime data, credentials or screenshots were uploaded for scanning.

The Maven inventory intentionally includes build/formatter and metadata-only entries as well as locked application/test coordinates. It is not a claim that all 281 entries are runtime JARs. The query does not audit the OS, JDK distribution, Gradle's bundled libraries or the implementation of downloaded audit tools. Advisory results are specific to the query date.

Primary finding references:

- [Google Guava advisory](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94).
- [Apache's Commons Lang disclosure](https://lists.apache.org/thread/bgv0lpswokgol11tloxnjfzdl7yrc1g1).
- [OSV API method](https://google.github.io/osv.dev/api/).
- [npm audit behaviour](https://docs.npmjs.com/cli/v11/commands/npm-audit/).

## CI, supply chain and attribution

- Actionlint 1.7.12 passed the actual workflow. ShellCheck/Pyflakes integrations were disabled because those optional external analyzers were not part of this local review.
- All four pinned Actions SHAs matched their documented upstream release tags, and each pinned action manifest was readable.
- Workflow permissions remain `contents: read`, checkout does not persist credentials, and no `pull_request_target` trigger or production credentials are configured.
- The wrapper JAR and distribution SHA-256 pin matched Gradle's official values.
- Gitleaks and Actionlint release archives matched their GitHub release SHA-256 digests before use. Tools remain in ignored local storage.
- All three shipped font notices matched the installed pinned packages byte-for-byte.
- Public NIST fixture provenance points to an immutable upstream commit; the complete upstream notice is now included.
- Third-party notices remain distinct from the unresolved licence for project-authored code.

Relevant upstream guidance: [secure workflow use](https://docs.github.com/en/actions/reference/security/secure-use) and [repository licensing](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository).

## Focused application-boundary review

The source and existing tests were checked for the documented relationships:

- Loopback binding, fixed receiver routes, exact origin/host checks and bounded bodies.
- Original-byte DSSE verification, independent algorithm checks and receiver-owned requirements.
- Signed issuer/audience/freshness checks before durable application.
- Current-trust verification before retry lookup and a trust lease held through commit.
- Publisher-only signing capabilities and public-only receiver metadata.
- Atomic encrypted vault replacement, protected account-secret files and fail-closed loading.
- Strict public response export, safe failure categories and honest interrupted-demo reporting.

No additional application-boundary change was required by this focused review. The [threat model](threat-model.md) remains the authority for assumptions and exclusions. This was not an independent cryptographic audit or a production-deployment review.

## Forced verification after dependency fixes

All tasks were rerun with build/configuration caches disabled and strict artifact verification:

```powershell
.\gradlew.bat check probe :integration-app:demo migrationDemo benchmarkSmoke :integration-app:bootJar :integration-app:buildEnvironment --rerun-tasks --no-build-cache --no-configuration-cache --dependency-verification=strict --console=plain
```

Observed results:

- 210 Java tests and 17 frontend tests passed; no Java failures, errors or skipped tests.
- Strict compilation, Javadoc, formatting, TypeScript, architecture and per-module coverage gates passed.
- The probe passed 53 checks and the existing policy/attack demo passed 23 expectations.
- The migration story passed 91 assertions over 28 real HTTP requests and two full reconstructions.
- The forked JMH smoke matrix completed all 21 cases.
- The packaged Boot JAR built successfully.

Smoke timings are harness checks, not replacement comparative measurements. Existing H2/Flyway and JMH/JDK compatibility warnings remained visible.

The README images were captured through actual UI actions on the packaged loopback workbench: a fresh dual event returned HTTP 201, stripping its PQC signature returned HTTP 403, and the lifecycle view showed actual active/revoked public metadata. See [capture provenance](images/README.md).

## Repeating the publication checks

Install the named audit tools from their official releases and verify their published archive digests. Create a new source-only snapshot from the current Git candidate inventory; do not point the secret scanner at a runtime vault or assume a history scan covers uncommitted work.

```text
gitleaks dir <source-only-snapshot> --config <official-v8.30.1-config> --ignore-gitleaks-allow --redact=100 --no-banner --report-format=json --report-path=<local-report.json>
actionlint -shellcheck= -pyflakes= .github/workflows/verify.yml
npm audit --prefix workbench --json --registry=https://registry.npmjs.org
```

For Maven, repeat exact-version OSV queries from the current lockfiles and verification metadata, check response counts/pagination and review every match against its affected ranges and actual resolution scope. Keep raw local audit output out of the public source snapshot.

A new code, dependency, asset or workflow change after this review needs proportionate rechecking. A public release still needs the open licence decision, final source selection and its own publication authorization.
