# Portfolio case study · final local 06B

Updated locally on **16 November 2026** in the separate Astro portfolio.
The default English route is /work/pqc-migration-lab/; its Hungarian counterpart
is /hu/munkak/pqc-migration-lab/. The homepage feature and related PQC research
link to the case study in both languages.

## Narrative and verified evidence

The case study describes the author's research, design, Java implementation,
React workbench and operational verification. Its four steps now follow actual
CLASSICAL_ONLY → SHADOW_PQC → BOTH_REQUIRED → PQC_REQUIRED deployments.
It explains the surviving publisher, separate receiver, unchanged public trust,
durable ledger, current policy enforcement and immutable first-acceptance facts.

The shared evidence snapshot is **228 Java tests and 17 frontend tests**, with
**four policy phases, 165 assertions and 49 HTTP attempts per rehearsal**.
Both native JVMs and actual local Compose containers completed the plan.
These are dated observations, not a live health indicator.

## Original captures

The lead image is the actual 06B Compose report from run
475f288d-cef6-4e07-8b06-265810be59d3. The two existing workbench images retain
their original 13 November 2026 provenance and explicit historical captions.
The new report and old interface captures are not presented as one live session.

| Capture | Original JPEG SHA-256 |
| --- | --- |
| migration-rehearsal.jpg | 3b65ca9fd132ab8ee0256883336693e1209bb2e31a3b3b98990d282bbab5dbc2 |
| signature-stripping.jpg | 1c9f44241f8cf913bb272d7a2bf861114b1cb39a455dc9a60cf043dda0c192ea |
| key-lifecycle.jpg | d200c2059a7c3d50b59c63d0ee316d3da384f4c35329beb17be0de6f820d47a9 |

The portfolio and Lab retain matching original bytes. Astro generates responsive
WebP derivatives, and each figure opens the full-size original in a separate tab.
Captions, alternative text and surrounding content are translated; application
and report UI stay English. See [capture provenance](images/README.md).

## Inspectable download

The portfolio's public/downloads/pqc-migration-lab-06b-evidence.zip contains
exactly the 82 files from the [reviewed Compose example](evidence/README.md).
Its SHA-256 is
086c1c699db40c45ff71369687de31fe4d4af63310d0b0d6009c32976b1accd6.
Both translations link to this local static-site asset. Readers can inspect
HTML/Markdown/JSON, exact public bodies and checksums without running Java.
The archive contains no private key vault, account secret or raw Docker log.

## Local verification

Run in the Astro portfolio:

    npm run check
    npm run format:check
    npm run build
    npm run verify
    npm run preview

With preview running, npm run verify -- --base=http://127.0.0.1:4367 also verifies
HTTP responses. Use npm.cmd on Windows where required by PowerShell policy.
The site validator covers 25 pages, internal links/assets, reciprocal translations,
homepage/research entry points, four policy names, the evidence download, original
image resources, captions, dates, metadata, sitemap and draft exclusion.

The portfolio preserves its paper, graphite and burgundy design. It remains
independent of the Java runtime. Both translations identify the linked public
source separately from the locally served site. The 06A/06B laboratory source is
included in v0.2.0; publishing it does not deploy the portfolio or application.

Final local checks passed on 16 November 2026: Astro reported zero errors,
warnings or hints; formatting passed; all 25 pages and 768 internal links/assets
passed static and HTTP verification. Actual browser navigation confirmed both
languages, and the Hungarian case study had no horizontal document overflow at
390 × 844. The evidence download's bytes matched the verified ZIP digest.
