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
    ('release_required', '0');
