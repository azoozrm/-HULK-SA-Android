<?php

declare(strict_types=1);

return [
    'database' => [
        'dsn' => 'mysql:host=localhost;dbname=hulk_operations;charset=utf8mb4',
        'username' => 'CHANGE_ME',
        'password' => 'CHANGE_ME',
        'options' => [],
    ],
    'app' => [
        'base_url' => 'https://hulksa.com/hulk-operations',
        'timezone' => 'Asia/Riyadh',
        'session_name' => 'hulk_operations_admin',
        'max_apk_bytes' => 629145600,
        'max_growth_qr_bytes' => 2097152,
        'login_max_attempts' => 5,
        'login_lock_seconds' => 900,
        'api_cache_seconds' => 60,

        // Optional one-time value for /admin/setup.php. Clear it immediately
        // after the first administrator is created.
        'bootstrap_token' => '',
    ],
    // Optional additive schema-v1 discovery. Keep disabled until the separate
    // Android integration is approved and deployed; disabled means omitted.
    'presence' => [
        'enabled' => false,
        'base_url' => 'https://hulksa.com/control-center/api/app/v1/presence/',
        'heartbeat_seconds' => 60,
        'online_ttl_seconds' => 180,
    ],
];
