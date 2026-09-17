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
];
