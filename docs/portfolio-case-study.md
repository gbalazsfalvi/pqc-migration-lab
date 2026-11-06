# Portfolio case study · 05B, part two

**Implemented locally on 13 November 2026.** The English and Hungarian case studies live in the separately maintained Astro portfolio. This work does not create a public repository, publish the portfolio or change the laboratory's runtime.

## Presentation

The default English route is `/work/pqc-migration-lab/`; its Hungarian counterpart is `/hu/munkak/pqc-migration-lab/`.

The case study explains the software-release scenario, the author's end-to-end contribution, Java-owned policy decisions, DSSE, transactional retry handling, persistent key lifecycle, the four-step migration story and the benchmark method. Its experimental boundary distinguishes a local application-level integration from production key custody and post-quantum TLS.

The homepage includes a dedicated reference-project feature. The Lab case study and the earlier professional PQC research link to each other in both languages. Existing professional project descriptions remain separate from this independent implementation.

The portfolio's `src/data/pqc-migration-lab.ts` holds the typed narrative and shared evidence values. Shared Astro components render the translated story, screenshots and related work using the established paper, graphite and burgundy design. No running Java backend is needed to view the static case study.

## Evidence snapshot

The case study uses the laboratory's verified **13 November 2026** results: 210 Java tests, 17 frontend tests, 28 actual HTTP requests, 91 migration-story assertions and two full runtime reconstructions. They are historical verification figures, not a live health report or tests performed by the portfolio.

The three images are unchanged copies of [the original captures](images/README.md). Their SHA-256 values match in the Lab and portfolio:

| Capture                   | SHA-256                                                            |
| ------------------------- | ------------------------------------------------------------------ |
| `workbench.jpg`           | `04797034f9f345e098d6ef3893b3ad3199be3301f88e59a8474b4ea1faed8233` |
| `signature-stripping.jpg` | `1c9f44241f8cf913bb272d7a2bf861114b1cb39a455dc9a60cf043dda0c192ea` |
| `key-lifecycle.jpg`       | `d200c2059a7c3d50b59c63d0ee316d3da384f4c35329beb17be0de6f820d47a9` |

The UI inside each capture is English. Captions, alternative text and full-size actions are translated. Responsive WebP derivatives reduce transfer size; each image opens the original JPEG in a separate tab.

To update the evidence, verify the Lab first, then change the shared date and figures. Refresh screenshots only from actual synthetic observations and update their provenance together. Keep comparative JMH measurements distinct from HTTP functional checks and smoke timings.

## Local portfolio verification

Run these commands in the Astro portfolio:

```sh
npm run check
npm run format:check
npm run build
npm run verify
npm run preview
```

With the preview running, `npm run verify -- --base=http://127.0.0.1:4367` checks HTTP responses too. On Windows use `npm.cmd` if PowerShell restricts `npm.ps1`.

The static verification covers both case-study routes, reciprocal translations, homepage entry points, related research, dated evidence, three captioned images, translated alternative text, original JPEGs, every responsive image resource, metadata and sitemap entries. The build contains 25 HTML pages.

Browser checks cover English/Hungarian switching, full-size image opening, related-work navigation, the homepage feature and a 390 × 844 mobile viewport. The case study remains a local portfolio artifact. A public source link should be added only after an actual source release exists.
