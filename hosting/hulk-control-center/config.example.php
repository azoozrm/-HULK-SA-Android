<?php

declare(strict_types=1);

return [
    'databases' => [
        'control' => [
            'dsn' => 'mysql:host=localhost;dbname=hulk_operations;charset=utf8mb4',
            'username' => 'CHANGE_ME',
            'password' => 'CHANGE_ME',
            'options' => [],
        ],
        'reseller' => [
            'dsn' => 'mysql:host=localhost;dbname=hulk_reseller;charset=utf8mb4',
            'username' => 'CHANGE_ME',
            'password' => 'CHANGE_ME',
            'options' => [],
        ],
    ],
    'app' => [
        'base_url' => 'https://hulksa.com/control-center',
        'timezone' => 'Asia/Riyadh',
        'session_name' => 'hulk_control_center',
        'login_max_attempts' => 5,
        'login_lock_seconds' => 900,
    ],
    'presence' => [
        'heartbeat_seconds' => 60,
        'online_ttl_seconds' => 180,
        'session_retention_days' => 180,
        'device_retention_days' => 180,
        'cleanup_batch_size' => 200,
        'rate_limit_window_seconds' => 60,
        'rate_limits' => [
            'start' => 20,
            'heartbeat' => 600,
            'end' => 30,
        ],

        // Store a random deployment-only value outside source control. It is
        // used only to HMAC client network identifiers before rate-limit rows
        // are persisted; raw addresses are never stored.
        'rate_limit_secret' => 'CHANGE_ME_TO_A_RANDOM_32_BYTE_SECRET',

        // Separate deployment-only secret used with each session's random
        // nonce to reproduce the same opaque token on an idempotent retry.
        'token_secret' => 'CHANGE_ME_TO_ANOTHER_RANDOM_32_BYTE_SECRET',
    ],
    'diagnostics' => [
        'retention_days' => 30,
        'cleanup_batch_size' => 200,
        'rate_limit_window_seconds' => 60,
        'rate_limit_attempts' => 30,
    ],
    'host_health' => [
        // DNS runs in a killable CLI child so this is a wall-clock bound.
        'dns_timeout_ms' => 2000,
        'connect_timeout_ms' => 3000,
        'request_timeout_ms' => 5000,
        'max_runtime_seconds' => 45,
        'max_hosts_per_run' => 20,
        'candidate_limit' => 500,
        'retention_days' => 90,
        'cleanup_batch_size' => 200,
        'user_agent' => 'HULK-SA-Host-Health/1.0',
    ],

    // TV/mobile pairing. Disabled by default; discovery in HULK Operations only
    // advertises availability and never authorizes requests. Keep this file outside
    // the web root and generate every secret locally; never commit real values.
    'pairing' => [
        'enabled' => false,
        'ttl_seconds' => 600,
        'retention_seconds' => 86400,
        'max_retained_rows' => 5000,
        'max_deliveries' => 20,
        'poll_interval_seconds' => 3,

        // base64 of >= 32 random bytes; independent from each other and from
        // every Presence secret. Used to derive claim tokens.
        'claim_secret' => 'CHANGE_ME_TO_BASE64_32_BYTES',

        // base64 of >= 32 random bytes; HMACs client/request identifiers for
        // pairing-only rate-limit rows.
        'rate_limit_secret' => 'CHANGE_ME_TO_ANOTHER_BASE64_32_BYTES',

        // AEAD envelope keys: base64 of EXACTLY 32 random bytes per key id.
        'envelope_keys' => [
            'v1' => 'CHANGE_ME_TO_BASE64_32_BYTES',
        ],
        'active_envelope_key_id' => 'v1',

        'rate_limits' => [
            'create_minute_per_ip' => 30,
            'create_hour_per_ip' => 300,
            'claim_minute_per_request' => 40,
            'claim_minute_per_ip' => 600,
            'ack_minute_per_request' => 10,
            'cancel_minute_per_request' => 10,
            'page_lookup_minute_per_ip' => 30,
            'page_approve_minute_per_ip' => 20,
            'page_approve_minute_per_request' => 10,
        ],
    ],
];
