-- One committed row is the complete synthetic business effect and replay reservation.
-- Keep accepted identifiers durable; expiry of a delivery window never deletes its receipt.
CREATE TABLE accepted_release (
    issuer VARCHAR(64) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    receipt_id VARCHAR(36) NOT NULL UNIQUE,
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    audience VARCHAR(128) NOT NULL,
    issued_at VARCHAR(64) NOT NULL,
    artifact VARCHAR(64) NOT NULL,
    release_version VARCHAR(64) NOT NULL,
    artifact_sha256 VARCHAR(64) NOT NULL,
    accepted_at VARCHAR(64) NOT NULL,
    accepted_policy VARCHAR(32) NOT NULL,
    ed25519_valid BOOLEAN NOT NULL,
    ml_dsa_65_valid BOOLEAN NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    PRIMARY KEY (issuer, event_id)
);
