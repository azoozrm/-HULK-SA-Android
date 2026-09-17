SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS cc_schema_migrations (
    migration_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (migration_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cc_devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    installation_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    platform_class ENUM('PHONE', 'TABLET', 'TV', 'OTHER') NOT NULL,
    manufacturer VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    android_release VARCHAR(32) NOT NULL,
    android_sdk_int SMALLINT UNSIGNED NOT NULL,
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    latest_app_version_name VARCHAR(32) NOT NULL,
    latest_app_version_code INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_cc_devices_installation (installation_id),
    KEY idx_cc_devices_last_seen (last_seen_at),
    KEY idx_cc_devices_version (latest_app_version_code, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cc_app_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    reseller_id BIGINT UNSIGNED NOT NULL,
    access_code_snapshot VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    iptv_username VARCHAR(255) NOT NULL,
    iptv_password VARCHAR(512) NOT NULL,
    host_snapshot VARCHAR(2048) NOT NULL,
    authenticated_at_client_ms BIGINT UNSIGNED NOT NULL,
    app_version_name VARCHAR(32) NOT NULL,
    app_version_code INT UNSIGNED NOT NULL,
    platform_class ENUM('PHONE', 'TABLET', 'TV', 'OTHER') NOT NULL,
    device_manufacturer VARCHAR(100) NOT NULL,
    device_model VARCHAR(100) NOT NULL,
    android_release VARCHAR(32) NOT NULL,
    android_sdk_int SMALLINT UNSIGNED NOT NULL,
    started_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    end_reason ENUM('LOGOUT', 'ACCOUNT_REPLACED', 'APP_SHUTDOWN') NULL,
    presence_token_nonce CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    presence_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_cc_app_sessions_session (session_id),
    UNIQUE KEY uq_cc_app_sessions_token_hash (presence_token_hash),
    KEY idx_cc_app_sessions_online (ended_at, last_seen_at),
    KEY idx_cc_app_sessions_reseller (reseller_id, started_at),
    KEY idx_cc_app_sessions_device (device_id, started_at),
    KEY idx_cc_app_sessions_started (started_at),
    KEY idx_cc_app_sessions_version (app_version_code, started_at),
    CONSTRAINT fk_cc_app_sessions_device
        FOREIGN KEY (device_id) REFERENCES cc_devices (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cc_api_rate_limits (
    scope VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    attempts SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (scope, client_hash),
    KEY idx_cc_api_rate_limits_window (window_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO cc_schema_migrations (migration_key)
VALUES ('2026-09-17-presence-v1');
