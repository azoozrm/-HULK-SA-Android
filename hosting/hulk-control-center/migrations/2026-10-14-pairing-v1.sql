SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS cc_schema_migrations (
    migration_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (migration_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Pairing request storage. No plaintext access-code/username/password columns exist:
-- credentials are held only inside the AEAD envelope (envelope_blob) between approval
-- and ACK/cancel/expiry/envelope-invalid/purge.
CREATE TABLE IF NOT EXISTS cc_pairing_requests (
    pairing_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    installation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_secret_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    device_platform VARCHAR(16) NOT NULL DEFAULT '',
    device_manufacturer VARCHAR(64) NOT NULL DEFAULT '',
    device_model VARCHAR(64) NOT NULL DEFAULT '',
    app_version VARCHAR(32) NOT NULL DEFAULT '',
    reseller_id INT UNSIGNED NULL,
    host_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    envelope_version TINYINT UNSIGNED NULL,
    envelope_key_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL,
    envelope_blob VARBINARY(1024) NULL,
    envelope_set_at DATETIME NULL,
    delivery_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    last_delivered_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    approved_at DATETIME NULL,
    acked_at DATETIME NULL,
    terminal_at DATETIME NULL,
    terminal_reason VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    PRIMARY KEY (pairing_id),
    UNIQUE KEY uq_cc_pairing_requests_request (request_id),
    UNIQUE KEY uq_cc_pairing_requests_user_code (user_code),
    UNIQUE KEY uq_cc_pairing_requests_client (installation_id, client_request_id),
    KEY idx_cc_pairing_requests_state_expires (state, expires_at),
    KEY idx_cc_pairing_requests_terminal (terminal_at),
    KEY idx_cc_pairing_requests_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Single-row admission lock table. Pairing-only; used to serialize the capacity
-- count + insert for new requests without holding any lock during network IO.
CREATE TABLE IF NOT EXISTS cc_pairing_admission (
    lock_id TINYINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (lock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO cc_pairing_admission (lock_id, created_at)
VALUES (1, UTC_TIMESTAMP());

INSERT IGNORE INTO cc_schema_migrations (migration_key)
VALUES ('2026-10-14-pairing-v1');
