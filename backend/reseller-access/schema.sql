CREATE TABLE IF NOT EXISTS resellers (
    reseller_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reseller_name VARCHAR(100) NOT NULL,
    reseller_name_key VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    host VARCHAR(2048) NOT NULL DEFAULT '',
    access_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    access_code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status ENUM('active', 'inactive') NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (reseller_id),
    UNIQUE KEY resellers_name_key_unique (reseller_name_key),
    UNIQUE KEY resellers_access_code_unique (access_code),
    UNIQUE KEY resellers_access_code_hash_unique (access_code_hash),
    KEY resellers_status_idx (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

