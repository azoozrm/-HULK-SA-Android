SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS cc_diagnostic_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    host_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type ENUM(
        'APP_START_FAILURE',
        'AUTHENTICATION_FAILURE',
        'PLAYBACK_START_FAILURE',
        'PLAYBACK_STALL',
        'STREAM_SOURCE_FAILURE',
        'DOWNLOAD_FAILURE',
        'UPDATE_FAILURE',
        'PRESENCE_FAILURE'
    ) NOT NULL,
    error_code ENUM(
        'NETWORK_TIMEOUT',
        'DNS_FAILURE',
        'CONNECTION_FAILURE',
        'HTTP_ERROR',
        'TLS_FAILURE',
        'AUTH_REJECTED',
        'UNSUPPORTED_MEDIA',
        'DECODER_FAILURE',
        'SOURCE_EXHAUSTED',
        'STALLED',
        'STORAGE_FAILURE',
        'CHECKSUM_FAILURE',
        'UNKNOWN_TYPED_FAILURE'
    ) NOT NULL,
    app_version_name VARCHAR(32) NOT NULL,
    app_version_code INT UNSIGNED NOT NULL,
    platform_class ENUM('PHONE', 'TABLET', 'TV', 'OTHER') NOT NULL,
    device_manufacturer VARCHAR(100) NOT NULL,
    device_model VARCHAR(100) NOT NULL,
    android_release VARCHAR(32) NOT NULL,
    android_sdk_int SMALLINT UNSIGNED NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cc_diagnostic_events_event (event_id),
    CONSTRAINT fk_cc_diagnostic_events_session
        FOREIGN KEY (session_id) REFERENCES cc_app_sessions (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_cc_diagnostic_events_device
        FOREIGN KEY (device_id) REFERENCES cc_devices (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO cc_schema_migrations (migration_key)
VALUES ('2026-09-19-diagnostics-v1');
