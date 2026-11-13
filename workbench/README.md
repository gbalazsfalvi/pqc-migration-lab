# Migration workbench

English React + TypeScript UI for the local Java integration. Java owns signing, trust, claim checks and all four policy decisions. Browser code validates API response shapes and displays public observations.

From the repository root:

```powershell
.\lab.ps1 serve
```

Open `http://127.0.0.1:8781/`. Gradle builds this package and bundles it into Spring Boot.

For optional frontend development with Java already running:

```powershell
npm ci --prefix workbench
npm run dev --prefix workbench
```

The Vite preview listens only on `127.0.0.1:5173` and proxies `/api` to the publisher on 8781. Use Node.js 24.14+ in the 24 LTS line and npm 11. There is no remote API configuration.

```powershell
npm run check --prefix workbench
npm run build --prefix workbench
npm run format --prefix workbench
```

- `src/domain` contains runtime wire contracts and display/export helpers.
- `src/api` handles fixed relative endpoints, deadlines and explicit rejection versus operational failure.
- `src/hooks/useWorkbench.ts` serializes commands and keeps bounded public session snapshots.
- `src/components` presents the composer, envelope, policy matrix, trace, experiments, history and guide.
- `public/licenses` retains the licences of the locally bundled fonts.

State is memory-only. A page reload clears run snapshots; durable receipts remain in the Java ledger. No private key enters browser state. See [the milestone contract](../docs/milestone-03.md) for API and security boundaries.

The Key lifecycle view reads receiver-owned public metadata, fences rotate/revoke commands with the reviewed revision and refreshes existing event observations after trust changes. Public export contains identifiers, lifecycle timestamps and SPKI fingerprints only. [Milestone 04B](../docs/milestone-04b.md) documents the managed encrypted vault and its separate account secret.
