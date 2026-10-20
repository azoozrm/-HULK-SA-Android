SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS cc_subscriber_notes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reseller_id BIGINT UNSIGNED NOT NULL,
    host_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    username_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    note_text TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cc_subscriber_notes_identity (reseller_id, host_key, username_key),
    KEY idx_cc_subscriber_notes_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO cc_schema_migrations (migration_key)
VALUES ('2026-10-19-subscriber-notes-v1');
