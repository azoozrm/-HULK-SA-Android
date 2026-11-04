CREATE TABLE IF NOT EXISTS hosts (
    host_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    host VARCHAR(2048) NOT NULL,
    host_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status ENUM('active', 'inactive') NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (host_id),
    UNIQUE KEY hosts_new_url_unique (host_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS host_access_codes (
    code_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    host_id BIGINT UNSIGNED NOT NULL,
    access_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    access_code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    alias_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status ENUM('active', 'inactive') NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (code_id),
    UNIQUE KEY host_codes_value_unique (access_code),
    UNIQUE KEY host_codes_hash_unique (access_code_hash),
    UNIQUE KEY host_codes_alias_unique (alias_key),
    KEY host_codes_host_idx (host_id),
    CONSTRAINT host_codes_host_fk FOREIGN KEY (host_id) REFERENCES hosts (host_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS host_schema_migrations (
    migration_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (migration_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
