package hu.balazsfalvi.pqclab.integration.app.migration;

import static hu.balazsfalvi.pqclab.integration.app.migration.MigrationTransport.Route.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.balazsfalvi.pqclab.integration.app.migration.MigrationTransport.Reply;
import hu.balazsfalvi.pqclab.integration.app.wire.ReleaseJsonCodec;
import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import hu.balazsfalvi.pqclab.json.DsseJsonCodec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rehearses four real receiver deployments using the same public envelopes, publisher, trust and durable ledger. */
final class PolicyRehearsalScenario {
    private static final byte[] EMPTY = new byte[0];
    private static final List<Profile> PROFILES = List.of(
            new Profile("classical", "ED25519_ONLY", true, false),
            new Profile("dual", "DUAL", true, true),
            new Profile("pqc", "ML_DSA_65_ONLY", false, true));
    // Expectations are specified independently of MigrationPolicy.accepts or any production verifier.
    private static final List<List<Integer>> EXPECTED =
            List.of(List.of(201, 201, 403), List.of(200, 200, 403), List.of(403, 200, 403), List.of(403, 200, 201));
    private final MigrationJournal journal;
    private final RuntimeFactory runtimes;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Reply> firstReceipts = new HashMap<>();

    /** Binds an observer to one owned native or container deployment without importing signing or ledger capabilities. */
    PolicyRehearsalScenario(MigrationJournal journal, RuntimeFactory runtimes) {
        this.journal = journal;
        this.runtimes = runtimes;
    }

    /** Runs every reviewed phase, stopping dependent actions and retaining partial evidence on any contradiction. */
    void run(MigrationWorkspace workspace) throws IOException, InterruptedException {
        try (var runtime = runtimes.start(workspace)) {
            String publisher = runtime.publisherIdentity();
            String previousReceiver = runtime.receiverIdentity();
            var publisherUri = runtime.publisherUri();
            var receiverUri = runtime.receiverUri();
            journal.expect(
                    "roles.independent",
                    "Publisher and receiver are distinct live services.",
                    false,
                    publisher.equals(previousReceiver));
            List<Sample> samples = List.of();
            ObjectNode initialRegistry = null;
            for (int index = 0; index < PolicyProgression.PHASES.size(); index++) {
                int phase = index + 1;
                MigrationPolicy policy = PolicyProgression.PHASES.get(index);
                String label = "phase-" + phase;
                if (index > 0) {
                    runtime.advancePolicy(policy);
                    journal.expect(
                            label + ".receiver-replaced",
                            "Only the receiver receives new startup settings.",
                            false,
                            previousReceiver.equals(runtime.receiverIdentity()));
                    journal.expect(
                            label + ".publisher-survived",
                            "The original publisher remains running.",
                            true,
                            publisher.equals(runtime.publisherIdentity()));
                    journal.expect(
                            label + ".endpoints-preserved",
                            "Both owned HTTP endpoints remain unchanged.",
                            true,
                            publisherUri.equals(runtime.publisherUri()) && receiverUri.equals(runtime.receiverUri()));
                }
                previousReceiver = runtime.receiverIdentity();
                try (var http = new MigrationTransport(runtime.publisherUri(), runtime.receiverUri(), journal, phase)) {
                    Reply configured = http.exchange(label + "-configuration", STATUS, 200, EMPTY);
                    journal.expect(
                            label + ".live-policy",
                            "The real receiver enforces this phase's startup policy.",
                            policy.name(),
                            configured.json().required("policy").textValue(),
                            configured.sequence());
                    journal.expect(
                            label + ".claims-and-time",
                            "The same signed identity, audience and freshness remain enforced.",
                            List.of("release-publisher", "urn:pqclab:release-receiver", "PT5M", "PT30S"),
                            List.of(
                                    configured.json().required("issuer").textValue(),
                                    configured.json().required("audience").textValue(),
                                    configured.json().required("maximumAge").textValue(),
                                    configured
                                            .json()
                                            .required("futureTolerance")
                                            .textValue()),
                            configured.sequence());
                    Reply registry = http.exchange(label + "-registry", KEYS, 200, EMPTY);
                    ObjectNode current = (ObjectNode) registry.json();
                    current.remove("observedAt");
                    if (index == 0) {
                        initialRegistry = current;
                        journal.expect(
                                "trust.initial",
                                "The rehearsal begins with one managed public generation at revision 1.",
                                List.of(true, 1L, 1),
                                List.of(
                                        current.required("managed").booleanValue(),
                                        current.required("revision").longValue(),
                                        current.required("generations").size()),
                                registry.sequence());
                        samples = publish(http);
                    } else {
                        journal.expect(
                                label + ".trust-preserved",
                                "Policy deployment does not rotate or replace any public trust.",
                                initialRegistry,
                                current,
                                registry.sequence());
                    }
                    var deliveries = new ArrayList<MigrationReport.PolicyDelivery>();
                    for (int profile = 0; profile < samples.size(); profile++) {
                        deliveries.add(observe(
                                http,
                                phase,
                                policy,
                                samples.get(profile),
                                EXPECTED.get(index).get(profile)));
                    }
                    if (phase >= 3) unsignedOverride(http, phase, samples.getFirst());
                    journal.completePolicyPhase(new MigrationReport.PolicyPhase(
                            phase,
                            configured.json().required("policy").textValue(),
                            configured.sequence(),
                            deliveries));
                }
            }
            journal.expect(
                    "rollout.complete",
                    "All three original events have exactly one distinct durable receipt.",
                    3L,
                    firstReceipts.values().stream()
                            .map(reply -> reply.json().required("receiptId").textValue())
                            .distinct()
                            .count());
        }
    }

    /** Creates three genuinely signed events once; the exact bytes are reused throughout all four deployments. */
    private List<Sample> publish(MigrationTransport http) throws IOException, InterruptedException {
        var samples = new ArrayList<Sample>();
        for (int index = 0; index < PROFILES.size(); index++) {
            Profile profile = PROFILES.get(index);
            byte[] request = json.writeValueAsBytes(json.createObjectNode()
                    .put("artifact", "migration-rehearsal")
                    .put("version", "1.0." + index)
                    .put("sha256", "a".repeat(64))
                    .put("signingMode", profile.mode()));
            Reply signed = http.exchange("create-" + profile.label(), ENVELOPE, 200, request);
            UUID id = new ReleaseJsonCodec()
                    .decode(new DsseJsonCodec().read(signed.bytes()).payloadBytes())
                    .eventId();
            samples.add(new Sample(profile, signed.bytes(), id));
        }
        journal.expect(
                "events.distinct",
                "The publisher creates three independently identified events.",
                3L,
                samples.stream().map(Sample::id).distinct().count());
        return List.copyOf(samples);
    }

    /** Confirms real cryptographic evidence, gateway delivery and the durable historical view for one unchanged envelope. */
    private MigrationReport.PolicyDelivery observe(
            MigrationTransport http, int phase, MigrationPolicy policy, Sample sample, int expected)
            throws IOException, InterruptedException {
        String label = "phase-" + phase + "-" + sample.profile().label();
        Reply evaluation = http.exchange(label + "-evaluation", EVALUATE, 200, sample.envelope());
        journal.expect(
                label + ".evaluation-policy",
                "Read-only Java observations identify the actual live requirement.",
                policy.name(),
                evaluation.json().required("receiverPolicy").textValue(),
                evaluation.sequence());
        var signatures = evaluation.json().required("signatureEvidence");
        journal.expect(
                label + ".verified-signatures",
                "Java independently verifies this sender's actual signatures.",
                List.of(sample.profile().classical(), sample.profile().pqc()),
                List.of(
                        signatures.required("ed25519Valid").booleanValue(),
                        signatures.required("mlDsa65Valid").booleanValue()),
                evaluation.sequence());

        Reply delivery = http.exchange(label + "-delivery", GATEWAY_DELIVER, expected, sample.envelope());
        if (expected == 403) {
            journal.expect(
                    label + ".policy-rejected",
                    "Insufficient signatures fail the live requirement even after prior acceptance.",
                    "POLICY_REJECTED",
                    delivery.json().required("code").textValue(),
                    delivery.sequence());
        } else {
            String status = expected == 201 ? "ACCEPTED" : "ALREADY_ACCEPTED";
            journal.expect(
                    label + ".delivery-status",
                    "Delivery creates or reuses the one durable business result.",
                    status,
                    delivery.json().required("status").textValue(),
                    delivery.sequence());
            if (expected == 201) {
                journal.expect(
                        label + ".first-acceptance",
                        "A new result records its actual first policy and signature evidence.",
                        List.of(
                                sample.id().toString(),
                                policy.name(),
                                sample.profile().classical(),
                                sample.profile().pqc()),
                        List.of(
                                delivery.json().required("eventId").textValue(),
                                delivery.json().required("acceptedPolicy").textValue(),
                                delivery.json()
                                        .required("acceptedEvidence")
                                        .required("ed25519Valid")
                                        .booleanValue(),
                                delivery.json()
                                        .required("acceptedEvidence")
                                        .required("mlDsa65Valid")
                                        .booleanValue()),
                        delivery.sequence());
                firstReceipts.put(sample.profile().label(), delivery);
            }
            preserved(
                    label + "-delivery",
                    delivery,
                    firstReceipts.get(sample.profile().label()),
                    status,
                    policy);
        }
        Reply first = firstReceipts.get(sample.profile().label());
        Reply historical = http.receipt(label + "-history", sample.id(), first == null ? 404 : 200);
        if (first == null) {
            journal.expect(
                    label + ".no-write",
                    "Rejected/evaluated events have no durable result.",
                    "RELEASE_NOT_FOUND",
                    historical.json().required("code").textValue(),
                    historical.sequence());
        } else {
            preserved(label + "-history", historical, first, "STORED", policy);
        }
        int observed = journal.exchanges().get(delivery.sequence() - 1).actualStatus();
        return new MigrationReport.PolicyDelivery(
                sample.profile().mode(),
                delivery.sequence(),
                expected,
                observed,
                delivery.json().required(expected == 403 ? "code" : "status").textValue(),
                expected == 403 ? null : delivery.json().required("receiptId").textValue(),
                expected == 403
                        ? null
                        : delivery.json().required("acceptedPolicy").textValue());
    }

    /** Separates today's enforcement from immutable first-acceptance fields instead of rewriting audit history. */
    private void preserved(String label, Reply observation, Reply first, String status, MigrationPolicy policy) {
        journal.expect(
                label + ".status",
                "The response identifies the observed delivery or historical query.",
                status,
                observation.json().required("status").textValue(),
                observation.sequence());
        journal.expect(
                label + ".enforced-policy",
                "The current requirement is reported separately from original acceptance.",
                policy.name(),
                observation.json().required("enforcedPolicy").textValue(),
                observation.sequence());
        ObjectNode expected = (ObjectNode) first.json();
        ObjectNode actual = (ObjectNode) observation.json();
        expected.remove(List.of("status", "enforcedPolicy"));
        actual.remove(List.of("status", "enforcedPolicy"));
        journal.expect(
                label + ".original-preserved",
                "Every original receipt identity, claim, time, policy and signature result remains unchanged.",
                expected,
                actual,
                first.sequence(),
                observation.sequence());
    }

    /** An unsigned wire field cannot switch the live receiver back to the baseline requirement. */
    private void unsignedOverride(MigrationTransport http, int phase, Sample classical)
            throws IOException, InterruptedException {
        ObjectNode envelope = (ObjectNode) json.readTree(classical.envelope());
        envelope.put("policy", "CLASSICAL_ONLY");
        Reply rejected = http.exchange(
                "phase-" + phase + "-unsigned-downgrade", GATEWAY_DELIVER, 403, json.writeValueAsBytes(envelope));
        journal.expect(
                "phase-" + phase + ".unsigned-policy-rejected",
                "Unsigned sender metadata cannot select the receiver's requirement.",
                "POLICY_REJECTED",
                rejected.json().required("code").textValue(),
                rejected.sequence());
    }

    /** Holds only the independently specified sender profile; it does not import publisher implementation types. */
    private record Profile(String label, String mode, boolean classical, boolean pqc) {}

    /** Keeps only original public bytes and signed identity across receiver replacements. */
    private record Sample(Profile profile, byte[] envelope, UUID id) {
        /** Takes independent ownership of mutable HTTP bytes. */
        private Sample {
            envelope = envelope.clone();
        }

        /** @return a defensive copy of the original signed envelope */
        @Override
        public byte[] envelope() {
            return envelope.clone();
        }
    }

    /** Allocates only a fresh owned deployment; no existing workbench address or private capability is accepted. */
    @FunctionalInterface
    interface RuntimeFactory {
        /** Starts the initial classical receiver and its independently running publisher. */
        PolicyMigrationRuntime start(MigrationWorkspace workspace) throws IOException, InterruptedException;
    }
}
