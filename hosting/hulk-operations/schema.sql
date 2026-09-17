SET NAMES utf8mb4;
SET time_zone = '+03:00';

CREATE TABLE IF NOT EXISTS app_admin_users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    failed_attempts SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_admin_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_releases (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    version_name VARCHAR(32) NOT NULL,
    version_code INT UNSIGNED NOT NULL,
    minimum_supported_version_code INT UNSIGNED NOT NULL,
    apk_path VARCHAR(255) NOT NULL,
    apk_sha256 CHAR(64) NOT NULL,
    apk_size_bytes BIGINT UNSIGNED NOT NULL,
    release_notes TEXT NOT NULL,
    required TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_releases_version_code (version_code),
    KEY idx_app_releases_active (is_active, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_announcements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_key VARCHAR(80) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    severity ENUM('INFO', 'WARNING', 'IMPORTANT') NOT NULL DEFAULT 'INFO',
    target ENUM('ALL', 'MOBILE', 'TV') NOT NULL DEFAULT 'ALL',
    show_once TINYINT(1) NOT NULL DEFAULT 1,
    persistent TINYINT(1) NOT NULL DEFAULT 0,
    minimum_version_code INT UNSIGNED NULL,
    maximum_version_code INT UNSIGNED NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_announcements_message_key (message_key),
    KEY idx_app_announcements_window (enabled, starts_at, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_feature_flags (
    flag_key VARCHAR(80) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (flag_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_service_status (
    id TINYINT UNSIGNED NOT NULL,
    status ENUM('OPERATIONAL', 'DEGRADED', 'MAINTENANCE') NOT NULL DEFAULT 'OPERATIONAL',
    message TEXT NULL,
    starts_at DATETIME NULL,
    estimated_end_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(80) NOT NULL,
    setting_value TEXT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_admin_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admin_user_id BIGINT UNSIGNED NULL,
    action VARCHAR(80) NOT NULL,
    details TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_app_admin_audit_created (created_at),
    KEY idx_app_admin_audit_admin (admin_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

INSERT IGNORE INTO app_service_status (id, status, message)
VALUES (1, 'OPERATIONAL', NULL);

INSERT IGNORE INTO app_feature_flags (flag_key, enabled) VALUES
    ('downloads_enabled', 1),
    ('episode_notifications_enabled', 1),
    ('smart_recommendations_enabled', 1),
    ('live_tv_pro_enabled', 1);

INSERT IGNORE INTO app_settings (setting_key, setting_value) VALUES
    ('latest_version_code', '64'),
    ('latest_version_name', '0.9.3.20'),
    ('minimum_supported_version_code', '64'),
    ('active_release_id', ''),
    ('release_required', '0'),
    ('growth_enabled', '1'),
    ('growth_renewal_enabled', '1'),
    ('growth_renewal_title', 'التجديد والموقع'),
    ('growth_renewal_url', 'https://hulksa.com/'),
    ('growth_renewal_display_text', 'hulksa.com'),
    ('growth_renewal_qr_mode', 'AUTO'),
    ('growth_renewal_custom_qr_path', ''),
    ('growth_support_enabled', '1'),
    ('growth_support_title', 'الدعم الفني'),
    ('growth_support_url', 'https://wa.me/966506349935'),
    ('growth_support_display_text', '0506349935'),
    ('growth_support_qr_mode', 'AUTO'),
    ('growth_support_custom_qr_path', ''),
    ('growth_renewal_banner_enabled', '1'),
    ('growth_renewal_banner_days', '7');
