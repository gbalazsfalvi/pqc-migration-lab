package hu.balazsfalvi.pqclab.integration.app.migration;

/** Renders a portable, script-free view of actual rehearsal evidence; no service or external asset is required. */
final class PolicyReportHtml {
    /** Prevents construction of the pure presentation adapter. */
    private PolicyReportHtml() {}

    /** Renders only completed phase observations; partial runs never gain invented success rows. */
    static String render(MigrationReport report) {
        long passed =
                report.checks().stream().filter(MigrationReport.Check::passed).count();
        var phases = new StringBuilder();
        var rows = new StringBuilder();
        var receipts = new StringBuilder();
        for (var phase : report.policyPhases()) {
            phases.append("""
                    <li><span class="index">0%d</span><h2>%s</h2><code>%s</code></li>
                    """.formatted(phase.phase(), title(phase.policy()), escape(phase.policy())));
            rows.append("<tr><th scope=\"row\"><span>0")
                    .append(phase.phase())
                    .append("</span> ")
                    .append(escape(phase.policy()))
                    .append("</th>");
            for (var delivery : phase.deliveries()) {
                String tone = delivery.actualStatus() >= 400 ? "rejected" : "accepted";
                rows.append("<td><span class=\"verdict ")
                        .append(tone)
                        .append("\">HTTP ")
                        .append(delivery.actualStatus())
                        .append("</span><small>")
                        .append(escape(delivery.result()))
                        .append("</small></td>");
                if (delivery.actualStatus() == 201) {
                    receipts.append("<tr><th scope=\"row\">")
                            .append(escape(delivery.signingMode()))
                            .append("</th><td>")
                            .append(escape(delivery.acceptedPolicy()))
                            .append("</td><td><code>")
                            .append(escape(delivery.receiptId()))
                            .append("</code></td></tr>");
                }
            }
            rows.append("</tr>\n");
        }
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="color-scheme" content="light">
                  <meta name="description" content="Actual local evidence of four receiver policy deployments, three signature profiles and preserved durable receipts.">
                  <title>Four-phase migration rehearsal · PQC Migration Lab</title>
                  <style>
                    :root { color-scheme: light; --ink: #182f43; --muted: #526879; --line: #d6e1e8; --blue: #315f91; }
                    * { box-sizing: border-box; }
                    body { margin: 0; background: #f1f5f8; color: var(--ink); font: 15px/1.6 "Segoe UI", sans-serif; }
                    main { max-width: 1160px; margin: auto; padding: 44px 36px; }
                    a { color: var(--blue); text-underline-offset: 4px; }
                    a:focus-visible { outline: 3px solid var(--blue); outline-offset: 5px; }
                    header { border-bottom: 1px solid var(--line); padding-bottom: 28px; }
                    .eyebrow, .index { font: 11px/1.6 Consolas, monospace; letter-spacing: .14em; text-transform: uppercase; }
                    .eyebrow { color: var(--muted); display: flex; justify-content: space-between; gap: 20px; }
                    h1 { font: 48px/1.1 Georgia, serif; letter-spacing: -.035em; margin: 26px 0 16px; }
                    header p { max-width: 720px; color: var(--muted); margin-bottom: 24px; }
                    .outcome { display: inline-block; font-weight: 700; padding: 5px 14px; border: 1px solid var(--blue); border-radius: 4px; }
                    .facts { display: flex; gap: 42px; flex-wrap: wrap; margin-top: 28px; }
                    .facts div { display: flex; flex-direction: column; }
                    .facts dt { color: var(--muted); font-size: 12px; order: 2; }
                    .facts dd { font: 28px/1.2 Georgia, serif; margin: 0 0 6px; }
                    .phases { list-style: none; padding: 0; margin: 32px 0; display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
                    .phases li { padding: 18px; border: 1px solid var(--line); border-top: 3px solid var(--blue); background: white; border-radius: 5px; }
                    .phases h2 { font-size: 16px; margin: 10px 0; }
                    .phases code { font-size: 11px; color: var(--muted); }
                    section { background: white; padding: 24px; border: 1px solid var(--line); border-radius: 6px; margin: 20px 0; }
                    section h2 { font: 27px/1.2 Georgia, serif; margin: 0 0 10px; }
                    section p, .note { color: var(--muted); }
                    .table-wrap { overflow-x: auto; }
                    table { border-collapse: collapse; width: 100%%; margin-top: 18px; text-align: left; }
                    caption { text-align: left; color: var(--muted); font-size: 13px; padding-bottom: 12px; }
                    th, td { padding: 14px 10px; border-top: 1px solid var(--line); vertical-align: top; }
                    thead th { font-size: 12px; color: var(--muted); }
                    tbody th { font: 12px/1.6 Consolas, monospace; }
                    tbody th span { color: var(--muted); margin-right: 8px; }
                    td small { display: block; font: 10px/1.5 Consolas, monospace; margin-top: 7px; }
                    .verdict { display: inline-block; padding: 3px 10px; border-radius: 3px; font-size: 13px; font-weight: 600; }
                    .accepted { color: #236554; background: #e8f4ee; }
                    .rejected { color: #94423c; background: #f9efed; }
                    .note { font-size: 12px; }
                    code { font-family: Consolas, monospace; font-size: 11px; overflow-wrap: anywhere; }
                    footer { margin-top: 28px; color: var(--muted); font-size: 12px; }
                    footer nav { display: flex; gap: 24px; flex-wrap: wrap; }
                    @media (max-width: 740px) {
                      main { padding: 24px 16px; } h1 { font-size: 36px; }
                      .phases { grid-template-columns: repeat(2, 1fr); } section { padding: 18px; }
                      .eyebrow { display: block; } .eyebrow span { display: block; margin-bottom: 6px; }
                      th, td { padding: 12px 8px; } .facts { gap: 24px; }
                    }
                    @media print { body { background: white; } main { max-width: none; } section, .phases { break-inside: avoid; } }
                  </style>
                </head>
                <body>
                <main>
                  <header>
                    <div class="eyebrow"><span>PQC Migration Lab / Milestone 06B</span><span>Actual local HTTP evidence</span></div>
                    <h1>Four phases. One integration.</h1>
                    <p>One publisher. Four receiver deployments. The same original signed events, public trust and durable ledger throughout the migration.</p>
                    <span class="outcome">%s</span>
                    <dl class="facts">
                      <div><dt>Completed policy phases</dt><dd>%d / 4</dd></div>
                      <div><dt>Passed assertions</dt><dd>%d</dd></div>
                      <div><dt>Actual HTTP attempts</dt><dd>%d</dd></div>
                    </dl>
                  </header>
                  <ol class="phases" aria-label="Completed receiver deployments">%s</ol>
                  <section>
                    <h2>Compatibility, observed.</h2>
                    <p>Each profile reuses its exact original envelope through the surviving publisher gateway.</p>
                    <div class="table-wrap">
                      <table>
                        <caption>Only completed phases are shown. HTTP 403 is the expected outcome for a sender that no longer meets the live requirement.</caption>
                        <thead><tr><th scope="col">Live receiver policy</th><th scope="col">Ed25519 only</th><th scope="col">Dual signature</th><th scope="col">ML-DSA-65 only</th></tr></thead>
                        <tbody>%s</tbody>
                      </table>
                    </div>
                    <p class="note">201 creates one receipt. 200 reuses it. 403 rejects delivery. PQC_REQUIRED retires the classical requirement while keeping PQC mandatory.</p>
                  </section>
                  <section>
                    <h2>History survives a policy change.</h2>
                    <p>First-acceptance evidence stays immutable. Historical receipt reads are checked separately from permission to deliver again under the current policy.</p>
                    <div class="table-wrap">
                      <table>
                        <caption>Receipts observed when each original event was first accepted.</caption>
                        <thead><tr><th scope="col">Sender profile</th><th scope="col">First acceptance policy</th><th scope="col">Original receipt identity</th></tr></thead>
                        <tbody>%s</tbody>
                      </table>
                    </div>
                  </section>
                  <footer>
                    <nav aria-label="Evidence files"><a href="report.json">Structured report</a><a href="report.md">HTTP timeline and assertions</a><a href="SHA256SUMS">Checksum manifest</a></nav>
                    <p>Observed %s · %s<br>Run %s · Scenario and cleanup completed: %s · Failure category: %s</p>
                    <p>Synthetic local reference experiment. No comparative performance claim. The unsigned checksum manifest identifies bytes; it does not independently authenticate this bundle.</p>
                  </footer>
                </main>
                </body>
                </html>
                """.formatted(
                        report.outcome(),
                        report.policyPhases().size(),
                        passed,
                        report.exchanges().size(),
                        phases,
                        rows,
                        receipts,
                        escape(report.finishedAt()),
                        escape(report.environment().transport()),
                        escape(report.runId()),
                        report.scenarioCompleted(),
                        escape(report.failureCode() == null ? "none" : report.failureCode()));
    }

    /** Gives fixed, human-readable titles to the observed receiver requirements. */
    private static String title(String policy) {
        return switch (policy) {
            case "CLASSICAL_ONLY" -> "Classical baseline";
            case "SHADOW_PQC" -> "Observe PQC";
            case "BOTH_REQUIRED" -> "Require both";
            case "PQC_REQUIRED" -> "Retire classical";
            default -> throw new IllegalArgumentException("Unknown observed policy.");
        };
    }

    /** Escapes every dynamic text fragment, including safely rendered partial-run metadata. */
    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
