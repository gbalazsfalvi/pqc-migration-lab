# Reviewed example evidence

The [06B Compose bundle](06b-compose/report.md) is a verbatim, checksum-verified
copy of one successful local run on 16 November 2026:

- Run: 475f288d-cef6-4e07-8b06-265810be59d3.
- Four actual receiver policies, 165 passing assertions, 49 scenario HTTP requests.
- One publisher, three receiver replacements, unchanged public trust and ledger.
- 81 manifest-listed artifacts plus the original SHA256SUMS file.

The bundle contains synthetic requests and reviewed public responses only.
It was copied by its explicit manifest, never by collecting runtime directories
or raw Docker diagnostics. Each destination digest was checked against the
original manifest. report.html works offline, with no script or external assets.

The separate portfolio download packages exactly those 82 files. Its ZIP SHA-256
is 086c1c699db40c45ff71369687de31fe4d4af63310d0b0d6009c32976b1accd6.
The new original report screenshot is documented in [image provenance](../images/README.md).

These are dated observations, not live health indicators. Fresh runs generate
different event IDs, receipts, keys, fingerprints, timestamps and checksums.
The unsigned manifest detects changes relative to its own recorded bytes; it
does not independently authenticate the source of a bundle.
