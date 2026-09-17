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
];
