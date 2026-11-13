package hu.balazsfalvi.pqclab.integration.app;

import hu.balazsfalvi.pqclab.integration.app.runtime.LocalOptions;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalRuntime;

/** Launches the local publisher and receiver with explicit migration policy selection. */
public final class LocalIntegration {
    /** Prevents construction of the executable entry point. */
    private LocalIntegration() {}

    /**
     * Starts both HTTP servers and keeps them running until the local process is stopped.
     *
     * @param arguments the documented policy, port and storage settings
     * @throws Exception if startup or local storage preparation fails
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && "--help".equals(arguments[0])) {
            System.out.println("Usage: integration-app [--policy=BOTH_REQUIRED] [--publisher-port=8781]"
                    + " [--receiver-port=8782] [--data-dir=.local/integration]");
            return;
        }
        LocalOptions options = LocalOptions.parse(arguments);
        LocalRuntime runtime = LocalRuntime.start(options);
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close, "pqc-local-shutdown"));
        System.out.println("PQC MIGRATION LAB / Milestone 04B");
        System.out.println("Workbench: " + runtime.publisherUri() + "/");
        System.out.println("Publisher: " + runtime.publisherUri() + "/api/publisher/releases");
        System.out.println("Receiver:  " + runtime.receiverUri() + "/api/receiver/status");
        System.out.println("Policy:    " + options.policy());
        System.out.println("Ledger:    " + options.dataDirectory());
        System.out.println("Keys:      Durable encrypted vault / public receiver registry");
        System.out.println("Both servers are bound to loopback. Press Ctrl+C to stop.");
    }
}
