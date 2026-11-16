package hu.balazsfalvi.pqclab.integration.app.migration;

import static hu.balazsfalvi.pqclab.integration.app.migration.MigrationTransport.Route.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.balazsfalvi.pqclab.integration.app.runtime.MigrationRuntime;
import java.io.IOException;

/** Proves real role separation, unavailable-upstream handling and independent durable restart recovery. */
final class ProcessRecoveryCheck {
    /** Prevents construction of a scenario-only verification helper. */
    private ProcessRecoveryCheck() {}

    /** Exercises only the owned runtime and records results from actual public HTTP responses. */
    static void verify(
            MigrationRuntime runtime,
            MigrationTransport http,
            MigrationJournal journal,
            byte[] envelope,
            MigrationTransport.Reply original)
            throws IOException, InterruptedException {
        if (!(runtime instanceof ManagedMigrationRuntime managed)) {
            throw new IllegalArgumentException("The process scenario needs independently managed services.");
        }
        String publisher = managed.publisherIdentity();
        String receiver = managed.receiverIdentity();
        journal.expect(
                "process.distinct",
                "Publisher and receiver have distinct live OS/container identities.",
                false,
                publisher.equals(receiver));
        managed.stopReceiver();
        var unavailable = http.exchange("receiver-offline-gateway", GATEWAY_DELIVER, 503, envelope);
        journal.expect(
                "outage.safe-response",
                "An offline receiver produces a bounded connection/DNS failure or delivery deadline response.",
                true,
                java.util.Set.of("RECEIVER_UNAVAILABLE", "DELIVERY_TIMEOUT")
                        .contains(unavailable.json().required("code").textValue()),
                unavailable.sequence());
        journal.expect(
                "outage.publisher-survives",
                "Receiver failure does not restart the publisher.",
                true,
                publisher.equals(managed.publisherIdentity()));
        managed.startReceiver();
        journal.expect(
                "restart.receiver-only",
                "The receiver restart creates a new live instance.",
                false,
                receiver.equals(managed.receiverIdentity()));
        journal.expect(
                "restart.publisher-unchanged",
                "The publisher retains its original live instance.",
                true,
                publisher.equals(managed.publisherIdentity()));
        var recovered = http.exchange("receiver-restarted-gateway", GATEWAY_DELIVER, 200, envelope);
        sameReceipt("receiver-restart", original, recovered, journal);
        String survivingReceiver = managed.receiverIdentity();
        managed.stopPublisher();
        var offline = http.exchange("publisher-offline-direct-replay", DELIVER, 200, envelope);
        sameReceipt("publisher-offline", original, offline, journal);
        managed.startPublisher();
        journal.expect(
                "restart.publisher-only",
                "Publisher restart creates a new live instance.",
                false,
                publisher.equals(managed.publisherIdentity()));
        journal.expect(
                "restart.receiver-unchanged",
                "Publisher restart does not restart the receiver.",
                true,
                survivingReceiver.equals(managed.receiverIdentity()));
        var retried = http.exchange("publisher-restarted-gateway", GATEWAY_DELIVER, 200, envelope);
        sameReceipt("publisher-restart", original, retried, journal);
    }

    /** Compares every original acceptance field while allowing the retry response's status to differ. */
    private static void sameReceipt(
            String label,
            MigrationTransport.Reply original,
            MigrationTransport.Reply actual,
            MigrationJournal journal) {
        ObjectNode expected = ((ObjectNode) original.json()).deepCopy();
        ObjectNode observed = ((ObjectNode) actual.json()).deepCopy();
        expected.remove("status");
        observed.remove("status");
        journal.expect(
                label + ".receipt",
                "Independent restart preserves every original acceptance field.",
                expected,
                observed,
                original.sequence(),
                actual.sequence());
        journal.expect(
                label + ".idempotency",
                "The same signed event is not applied twice after recovery.",
                "ALREADY_ACCEPTED",
                actual.json().required("status").textValue(),
                actual.sequence());
    }
}
