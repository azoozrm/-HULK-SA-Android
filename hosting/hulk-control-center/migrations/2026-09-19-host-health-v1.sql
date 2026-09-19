SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS cc_host_health_checks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reseller_id BIGINT UNSIGNED NOT NULL,
    host_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    checked_at DATETIME(6) NOT NULL,
    probe_result ENUM(
        'HEALTHY',
        'HTTP_ERROR',
        'DNS_FAILURE',
        'CONNECT_FAILURE',
        'TIMEOUT',
        'TLS_FAILURE',
        'POLICY_BLOCKED',
        'NETWORK_FAILURE'
    ) NOT NULL,
    dns_ok TINYINT(1) NOT NULL,
    tcp_ok TINYINT(1) NOT NULL,
    http_status SMALLINT UNSIGNED NULL,
    latency_ms INT UNSIGNED NOT NULL,
    failure_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO cc_schema_migrations (migration_key)
VALUES ('2026-09-19-host-health-v1');
