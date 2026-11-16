package hu.balazsfalvi.pqclab.integration.app.migration;

import static hu.balazsfalvi.pqclab.integration.app.migration.MigrationTransport.Route.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.balazsfalvi.pqclab.core.dsse.DsseEnvelope;
import hu.balazsfalvi.pqclab.integration.app.migration.MigrationTransport.Reply;
import hu.balazsfalvi.pqclab.integration.app.runtime.LocalRuntime;
import hu.balazsfalvi.pqclab.integration.app.runtime.MigrationRuntime;
import hu.balazsfalvi.pqclab.integration.app.wire.ReleaseJsonCodec;
import hu.balazsfalvi.pqclab.json.DsseJsonCodec;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Tells a fixed, fail-fast migration story entirely through the real public HTTP interfaces. */
final class MigrationScenario {
    private static final byte[] EMPTY = new byte[0];
    private final MigrationJournal journal;
    private final ObjectMapper json = new ObjectMapper();
    private final DsseJsonCodec envelopes = new DsseJsonCodec();
    private final RuntimeFactory runtimes;
    private final boolean recovery;

    /** Binds all assertions to one public evidence run. */
    MigrationScenario(MigrationJournal journal) {
        this(
                journal,
                workspace -> LocalRuntime.start(workspace.options(), Clock.systemUTC(), workspace.secretFile()),
                false);
    }

    /** Selects actual native JVM or Compose ownership while preserving the established HTTP assertions. */
    MigrationScenario(MigrationJournal journal, RuntimeFactory runtimes, boolean recovery) {
        this.journal = journal;
        this.runtimes = runtimes;
        this.recovery = recovery;
    }

    /** Runs three independently reconstructed runtimes, retaining public bytes and receipts only between phases. */
    void run(MigrationWorkspace workspace) throws IOException, InterruptedException {
        var original = acceptAndRotate(workspace);
        var current = revokeAndReplace(workspace, original);
        verifyDurableRevocation(workspace, original, current);
    }

    /** Proves acceptance, idempotency, downgrade rejection and bounded rotation overlap. */
    private Original acceptAndRotate(MigrationWorkspace workspace) throws IOException, InterruptedException {
        try (var runtime = runtimes.start(workspace);
                var http = new MigrationTransport(runtime.publisherUri(), runtime.receiverUri(), journal, 1)) {
            configuration(http, "initial");
            Reply initial = http.exchange("initial-registry", KEYS, 200, EMPTY);
            journal.expect(
                    "initial.managed",
                    "The scenario uses managed durable keys.",
                    true,
                    initial.json().required("managed").booleanValue(),
                    initial.sequence());
            journal.expect(
                    "initial.revision",
                    "A new isolated workspace begins at trust revision 1.",
                    1L,
                    initial.json().required("revision").longValue(),
                    initial.sequence());
            journal.expect(
                    "initial.generations",
                    "A new workspace has exactly one generation.",
                    1,
                    initial.json().required("generations").size(),
                    initial.sequence());
            String oldGeneration = initial.json().required("activeGenerationId").textValue();
            journal.expect(
                    "initial.algorithms",
                    "The active generation contains both configured algorithms.",
                    Set.of("ED25519", "ML_DSA_65"),
                    algorithms(generation(initial, oldGeneration)),
                    initial.sequence());

            Reply created = http.exchange("original-envelope", ENVELOPE, 200, publishRequest("1.0.0"));
            byte[] original = created.bytes();
            UUID eventId = eventId(original);
            Reply evaluation = http.exchange("original-evaluation", EVALUATE, 200, original);
            policies("original", evaluation, 1, true, true, List.of(true, true, true, true));
            http.receipt("evaluation-does-not-store", eventId, 404);

            Reply accepted = http.exchange("original-delivery", DELIVER, 201, original);
            journal.expect(
                    "receipt.original-status",
                    "The first delivery creates a durable result.",
                    "ACCEPTED",
                    accepted.json().required("status").textValue(),
                    accepted.sequence());
            journal.expect(
                    "receipt.original-event",
                    "The receipt belongs to the signed event.",
                    eventId.toString(),
                    accepted.json().required("eventId").textValue(),
                    accepted.sequence());
            journal.expect(
                    "receipt.original-evidence",
                    "First acceptance records both verified signatures.",
                    List.of(true, true),
                    evidence(accepted.json().required("acceptedEvidence")),
                    accepted.sequence());
            Reply replay = http.exchange("original-replay", DELIVER, 200, original);
            receipt("replay", accepted, replay, "ALREADY_ACCEPTED");
            if (recovery) ProcessRecoveryCheck.verify(runtime, http, journal, original, accepted);

            // DUAL has a documented publisher order. Actual Java observations below must confirm what remains.
            DsseEnvelope envelope = envelopes.read(original);
            byte[] stripped = envelopes.write(new DsseEnvelope(
                    envelope.payloadType(),
                    envelope.payload(),
                    List.of(envelope.signatures().getFirst())));
            Reply strippedEvaluation = http.exchange("stripped-evaluation", EVALUATE, 200, stripped);
            policies("stripped", strippedEvaluation, 1, true, false, List.of(true, true, false, false));
            Reply strippedDelivery = http.exchange("stripped-delivery", DELIVER, 403, stripped);
            rejection("stripped", strippedDelivery);

            Reply rotation = http.exchange("rotate-generation", ROTATE, 200, revision(1));
            Reply rotated = http.exchange("rotated-registry", KEYS, 200, EMPTY);
            journal.expect(
                    "rotation.receiver-observation",
                    "Receiver state agrees with the completed control response.",
                    publicRegistry(rotation),
                    publicRegistry(rotated),
                    rotation.sequence(),
                    rotated.sequence());
            journal.expect(
                    "rotation.revision",
                    "Rotation advances the durable revision once.",
                    2L,
                    rotated.json().required("revision").longValue(),
                    rotated.sequence());
            journal.expect(
                    "rotation.generations",
                    "Public history retains the old and new generations.",
                    2,
                    rotated.json().required("generations").size(),
                    rotated.sequence());
            String active = rotated.json().required("activeGenerationId").textValue();
            journal.expect(
                    "rotation.new-identity",
                    "New signing uses a distinct generation.",
                    false,
                    oldGeneration.equals(active),
                    initial.sequence(),
                    rotated.sequence());
            JsonNode retiring = generation(rotated, oldGeneration);
            JsonNode fresh = generation(rotated, active);
            journal.expect(
                    "rotation.old-state",
                    "The old generation enters bounded overlap.",
                    "RETIRING",
                    retiring.required("state").textValue(),
                    rotated.sequence());
            journal.expect(
                    "rotation.active-state",
                    "The new generation is active.",
                    "ACTIVE",
                    fresh.required("state").textValue(),
                    rotated.sequence());
            journal.expect(
                    "rotation.overlap",
                    "The verification overlap is exactly ten minutes.",
                    600L,
                    Duration.between(
                                    Instant.parse(
                                            retiring.required("retiringAt").textValue()),
                                    Instant.parse(
                                            retiring.required("acceptUntil").textValue()))
                            .getSeconds(),
                    rotated.sequence());
            journal.expect(
                    "rotation.new-public-keys",
                    "Both algorithms receive new public key fingerprints.",
                    true,
                    disjointFingerprints(generation(initial, oldGeneration), fresh),
                    initial.sequence(),
                    rotated.sequence());
            journal.expect(
                    "rotation.old-public-keys",
                    "Rotation preserves old public verification identities.",
                    generation(initial, oldGeneration).required("keys"),
                    retiring.required("keys"),
                    initial.sequence(),
                    rotated.sequence());
            Reply overlap = http.exchange("overlap-replay", DELIVER, 200, original);
            receipt("overlap", accepted, overlap, "ALREADY_ACCEPTED");
            return new Original(original, eventId, oldGeneration, accepted, rotated);
        }
    }

    /** Reconstructs from disk, revokes old trust and creates a fresh event with the current generation. */
    private Current revokeAndReplace(MigrationWorkspace workspace, Original original)
            throws IOException, InterruptedException {
        try (var runtime = runtimes.start(workspace);
                var http = new MigrationTransport(runtime.publisherUri(), runtime.receiverUri(), journal, 2)) {
            configuration(http, "rotation-restart");
            Reply restarted = http.exchange("rotation-restart-registry", KEYS, 200, EMPTY);
            journal.expect(
                    "restart.rotation-state",
                    "A full restart preserves revision, identities and overlap metadata.",
                    publicRegistry(original.rotated()),
                    publicRegistry(restarted),
                    original.rotated().sequence(),
                    restarted.sequence());
            Reply overlap = http.exchange("rotation-restart-replay", DELIVER, 200, original.envelope());
            receipt("restart-overlap", original.receipt(), overlap, "ALREADY_ACCEPTED");

            ObjectNode input =
                    json.createObjectNode().put("expectedRevision", 2).put("generationId", original.generation());
            http.exchange("revoke-old-generation", REVOKE, 200, json.writeValueAsBytes(input));
            Reply revoked = http.exchange("revoked-registry", KEYS, 200, EMPTY);
            journal.expect(
                    "revocation.revision",
                    "Revocation advances durable trust to revision 3.",
                    3L,
                    revoked.json().required("revision").longValue(),
                    revoked.sequence());
            journal.expect(
                    "revocation.old-state",
                    "The old generation is explicitly revoked.",
                    "REVOKED",
                    generation(revoked, original.generation()).required("state").textValue(),
                    revoked.sequence());
            journal.expect(
                    "revocation.active-unchanged",
                    "Revoking history does not replace the active generation.",
                    restarted.json().required("activeGenerationId"),
                    revoked.json().required("activeGenerationId"),
                    restarted.sequence(),
                    revoked.sequence());

            Reply oldEvaluation = http.exchange("revoked-evaluation", EVALUATE, 200, original.envelope());
            policies("revoked", oldEvaluation, 3, false, false, List.of(false, false, false, false));
            Reply rejected = http.exchange("revoked-replay", DELIVER, 403, original.envelope());
            rejection("revoked", rejected);
            Reply historical = http.receipt("historical-receipt", original.eventId(), 200);
            receipt("historical", original.receipt(), historical, "STORED");

            Reply created = http.exchange("current-envelope", ENVELOPE, 200, publishRequest("2.0.0"));
            UUID eventId = eventId(created.bytes());
            journal.expect(
                    "current.distinct-event",
                    "New signing creates a different event identity.",
                    false,
                    original.eventId().equals(eventId),
                    created.sequence());
            Reply evaluation = http.exchange("current-evaluation", EVALUATE, 200, created.bytes());
            policies("current", evaluation, 3, true, true, List.of(true, true, true, true));
            Reply accepted = http.exchange("current-delivery", DELIVER, 201, created.bytes());
            journal.expect(
                    "current.distinct-receipt",
                    "The new business result has a distinct receipt.",
                    false,
                    original.receipt()
                            .json()
                            .required("receiptId")
                            .equals(accepted.json().required("receiptId")),
                    original.receipt().sequence(),
                    accepted.sequence());
            return new Current(created.bytes(), accepted, revoked);
        }
    }

    /** Verifies revocation, historical receipts and the current generation after another disk-only reconstruction. */
    private void verifyDurableRevocation(MigrationWorkspace workspace, Original original, Current current)
            throws IOException, InterruptedException {
        try (var runtime = runtimes.start(workspace);
                var http = new MigrationTransport(runtime.publisherUri(), runtime.receiverUri(), journal, 3)) {
            configuration(http, "revocation-restart");
            Reply restarted = http.exchange("revocation-restart-registry", KEYS, 200, EMPTY);
            journal.expect(
                    "restart.revocation-state",
                    "A second full restart preserves revocation and current identity.",
                    publicRegistry(current.registry()),
                    publicRegistry(restarted),
                    current.registry().sequence(),
                    restarted.sequence());
            Reply rejected = http.exchange("revocation-restart-replay", DELIVER, 403, original.envelope());
            rejection("revocation-restart", rejected);
            Reply historical = http.receipt("restart-historical-receipt", original.eventId(), 200);
            receipt("restart-historical", original.receipt(), historical, "STORED");
            Reply replay = http.exchange("restart-current-replay", DELIVER, 200, current.envelope());
            receipt("restart-current", current.receipt(), replay, "ALREADY_ACCEPTED");
        }
    }

    /** Requires actual receiver-owned settings at the start of every independently booted phase. */
    private void configuration(MigrationTransport http, String label) throws IOException, InterruptedException {
        Reply status = http.exchange(label + "-configuration", STATUS, 200, EMPTY);
        journal.expect(
                label + ".policy",
                "The live receiver continues to enforce BOTH_REQUIRED.",
                "BOTH_REQUIRED",
                status.json().required("policy").textValue(),
                status.sequence());
        journal.expect(
                label + ".identity",
                "Signed claims target the configured synthetic owner and audience.",
                List.of(LocalRuntime.ISSUER, LocalRuntime.AUDIENCE),
                List.of(
                        status.json().required("issuer").textValue(),
                        status.json().required("audience").textValue()),
                status.sequence());
        journal.expect(
                label + ".freshness",
                "The receiver keeps its real five-minute age and thirty-second tolerance.",
                List.of("PT5M", "PT30S"),
                List.of(
                        status.json().required("maximumAge").textValue(),
                        status.json().required("futureTolerance").textValue()),
                status.sequence());
    }

    /** Compares backend verdicts; no browser-side or report-side signature verification is substituted. */
    private void policies(
            String label, Reply reply, long revision, boolean classical, boolean pqc, List<Boolean> accepted) {
        JsonNode body = reply.json();
        journal.expect(
                label + ".trust",
                "One backend comparison uses the expected public trust revision.",
                revision,
                body.required("trustRevision").longValue(),
                reply.sequence());
        journal.expect(
                label + ".evidence",
                "Java independently verifies both configured algorithms.",
                List.of(classical, pqc),
                evidence(body.required("signatureEvidence")),
                reply.sequence());
        journal.expect(
                label + ".policy-order",
                "The comparison includes exactly the four migration policies.",
                List.of("CLASSICAL_ONLY", "SHADOW_PQC", "BOTH_REQUIRED", "PQC_REQUIRED"),
                StreamSupport.stream(body.required("policies").spliterator(), false)
                        .map(policy -> policy.required("policy").textValue())
                        .toList(),
                reply.sequence());
        journal.expect(
                label + ".decisions",
                "Observed Java policy decisions match the scenario requirement.",
                accepted,
                StreamSupport.stream(body.required("policies").spliterator(), false)
                        .map(policy -> policy.required("accepted").booleanValue())
                        .toList(),
                reply.sequence());
    }

    /** Requires a safe cryptographic policy rejection rather than accepting any unrelated HTTP 403. */
    private void rejection(String label, Reply reply) {
        journal.expect(
                label + ".reason",
                "The receiver rejects insufficient current trusted signatures.",
                "POLICY_REJECTED",
                reply.json().required("code").textValue(),
                reply.sequence());
    }

    /** Checks all original acceptance fields, not merely the response code or receipt identifier. */
    private void receipt(String label, Reply original, Reply observation, String status) {
        journal.expect(
                label + ".receipt-status",
                "The response identifies a retry or a stored historical result.",
                status,
                observation.json().required("status").textValue(),
                observation.sequence());
        ObjectNode expected = (ObjectNode) original.json();
        ObjectNode actual = (ObjectNode) observation.json();
        expected.remove("status");
        actual.remove("status");
        journal.expect(
                label + ".receipt-preserved",
                "Receipt identity and every original acceptance field are unchanged.",
                expected,
                actual,
                original.sequence(),
                observation.sequence());
    }

    /** Builds fixed synthetic content; only the version differs between the two new events. */
    private byte[] publishRequest(String version) throws IOException {
        return json.writeValueAsBytes(json.createObjectNode()
                .put("artifact", "synthetic-service")
                .put("version", version)
                .put("sha256", "a".repeat(64))
                .put("signingMode", "DUAL"));
    }

    /** Encodes only the reviewed optimistic revision, never a new policy or overlap duration. */
    private byte[] revision(long value) throws IOException {
        return json.writeValueAsBytes(json.createObjectNode().put("expectedRevision", value));
    }

    /** Reads the publisher-generated event identifier for later public receipt queries. */
    private UUID eventId(byte[] envelope) {
        return new ReleaseJsonCodec()
                .decode(envelopes.read(envelope).payloadBytes())
                .eventId();
    }

    /** Locates a configured public generation by exact identity rather than relying on its array position. */
    private JsonNode generation(Reply registry, String id) {
        return StreamSupport.stream(registry.json().required("generations").spliterator(), false)
                .filter(generation -> id.equals(generation.required("id").textValue()))
                .findFirst()
                .orElseThrow(() -> new ScenarioFailure("PUBLIC_RESPONSE_REJECTED"));
    }

    /** Extracts the configured algorithm set from actual public metadata. */
    private Set<String> algorithms(JsonNode generation) {
        return StreamSupport.stream(generation.required("keys").spliterator(), false)
                .map(key -> key.required("algorithm").textValue())
                .collect(Collectors.toSet());
    }

    /** Confirms both fingerprints changed without treating any key ID as authenticated evidence. */
    private boolean disjointFingerprints(JsonNode old, JsonNode fresh) {
        Set<String> previous = StreamSupport.stream(old.required("keys").spliterator(), false)
                .map(key -> key.required("spkiSha256").textValue())
                .collect(Collectors.toSet());
        return algorithms(fresh).equals(algorithms(old))
                && fresh.required("keys").size() == 2
                && StreamSupport.stream(fresh.required("keys").spliterator(), false)
                        .noneMatch(key ->
                                previous.contains(key.required("spkiSha256").textValue()));
    }

    /** Ignores only the current observation timestamp when comparing fully persisted public state. */
    private ObjectNode publicRegistry(Reply reply) {
        ObjectNode registry = (ObjectNode) reply.json();
        registry.remove("observedAt");
        return registry;
    }

    /** Extracts the backend's two independent public signature results. */
    private List<Boolean> evidence(JsonNode evidence) {
        return List.of(
                evidence.required("ed25519Valid").booleanValue(),
                evidence.required("mlDsa65Valid").booleanValue());
    }

    /** Retains public phase-one data only; no manager, signer or key pair crosses a runtime restart. */
    private record Original(byte[] envelope, UUID eventId, String generation, Reply receipt, Reply rotated) {}

    /** Retains public replacement-event evidence for the final disk-only phase. */
    private record Current(byte[] envelope, Reply receipt, Reply registry) {}

    /** Creates only newly owned runtimes from the scenario's private workspace. */
    @FunctionalInterface
    interface RuntimeFactory {
        /** Starts a complete runtime without importing any private capabilities into the scenario. */
        MigrationRuntime start(MigrationWorkspace workspace) throws IOException, InterruptedException;
    }
}
