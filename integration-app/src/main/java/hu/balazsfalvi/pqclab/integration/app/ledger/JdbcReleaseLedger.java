package hu.balazsfalvi.pqclab.integration.app.ledger;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;
import hu.balazsfalvi.pqclab.integration.core.ReceptionFailure;
import hu.balazsfalvi.pqclab.integration.core.ReleaseEvent;
import hu.balazsfalvi.pqclab.integration.core.ReleaseLedger;
import hu.balazsfalvi.pqclab.integration.core.SignatureEvidence;
import hu.balazsfalvi.pqclab.integration.core.StoredRelease;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses one atomic insert as both replay reservation and durable release application. */
public final class JdbcReleaseLedger implements ReleaseLedger {
    private static final String INSERT = """
            INSERT INTO accepted_release
              (issuer, event_id, receipt_id, schema_version, audience, issued_at, artifact, release_version,
               artifact_sha256, accepted_at, accepted_policy, ed25519_valid, ml_dsa_65_valid, payload_sha256)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    /**
     * Supplies the database adapter and transaction boundary through constructor injection.
     *
     * @param jdbc the configured JDBC operations
     * @param transactions the transaction manager for the same data source
     */
    public JdbcReleaseLedger(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    /** {@inheritDoc} */
    @Override
    public Outcome record(StoredRelease candidate) {
        ReleaseEvent event = candidate.event();
        try {
            // The primary key arbitrates concurrent retries. No external effect precedes this commit.
            transactions.executeWithoutResult(status -> jdbc.update(
                    INSERT,
                    event.issuer(),
                    event.eventId().toString(),
                    candidate.receiptId().toString(),
                    event.schemaVersion(),
                    event.audience(),
                    event.issuedAt().toString(),
                    event.artifact(),
                    event.version(),
                    event.sha256(),
                    candidate.acceptedAt().toString(),
                    candidate.policy().name(),
                    candidate.evidence().ed25519Valid(),
                    candidate.evidence().mlDsa65Valid(),
                    candidate.payloadSha256()));
            return new Outcome(candidate, true);
        } catch (DuplicateKeyException duplicate) {
            // Read only after rollback, outside the failed transaction.
            StoredRelease original = find(event.issuer(), event.eventId())
                    .orElseThrow(() -> new IllegalStateException("A conflicting ledger row could not be read."));
            if (!original.payloadSha256().equals(candidate.payloadSha256())) {
                throw new ReceptionFailure(ReceptionFailure.Code.EVENT_ID_CONFLICT);
            }
            return new Outcome(original, false);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<StoredRelease> find(String issuer, UUID eventId) {
        return jdbc
                .query(
                        "SELECT * FROM accepted_release WHERE issuer = ? AND event_id = ?",
                        this::mapRow,
                        issuer,
                        eventId.toString())
                .stream()
                .findFirst();
    }

    /** Restores the immutable business result and its original acceptance evidence. */
    private StoredRelease mapRow(ResultSet row, int rowNumber) throws SQLException {
        ReleaseEvent event = new ReleaseEvent(
                row.getInt("schema_version"),
                UUID.fromString(row.getString("event_id")),
                row.getString("issuer"),
                row.getString("audience"),
                Instant.parse(row.getString("issued_at")),
                row.getString("artifact"),
                row.getString("release_version"),
                row.getString("artifact_sha256"));
        return new StoredRelease(
                UUID.fromString(row.getString("receipt_id")),
                event,
                Instant.parse(row.getString("accepted_at")),
                MigrationPolicy.valueOf(row.getString("accepted_policy")),
                new SignatureEvidence(row.getBoolean("ed25519_valid"), row.getBoolean("ml_dsa_65_valid")),
                row.getString("payload_sha256"));
    }
}
