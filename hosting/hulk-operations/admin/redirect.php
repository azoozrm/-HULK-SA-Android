<?php

declare(strict_types=1);

/**
 * Phase 9B legacy Operations owner redirect gate.
 *
 * Stage B retires the remaining legacy Operations owner entry points. They no
 * longer authenticate a legacy owner, read the database, process CSRF or run a
 * mutation; each request is redirected to the equivalent HULK SA Control Center
 * module, which is now the sole owner-facing administration entry.
 *
 * This file only derives a fixed, allow-listed Control Center path and emits
 * the correct navigation status. It defines no owner state and no cutover flag.
 */

function ops_legacy_control_center_modules(): array
{
    return ['dashboard', 'releases', 'announcements', 'service', 'features', 'growth', 'audit'];
}

function ops_legacy_control_center_path(string $section = 'dashboard'): string
{
    $paths = [
        'dashboard' => '/control-center/',
        'releases' => '/control-center/releases/',
        'announcements' => '/control-center/announcements/',
        'service' => '/control-center/service/',
        'features' => '/control-center/features/',
        'growth' => '/control-center/growth/',
        'audit' => '/control-center/audit/',
    ];

    // Only an allow-listed legacy section can select a destination; anything
    // else falls back to the Control Center root. The path is never built from
    // the requested value, so no arbitrary path construction is possible.
    $key = in_array($section, ops_legacy_control_center_modules(), true) ? $section : 'dashboard';

    return $paths[$key];
}

function ops_legacy_control_center_login_path(): string
{
    return '/control-center/login.php';
}

/**
 * 303 after a retired legacy POST so the browser does not replay the old POST
 * against the destination; 302 for legacy GET/navigation entries.
 */
function ops_legacy_redirect_status(): int
{
    return ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST' ? 303 : 302;
}

function ops_legacy_redirect(string $path): never
{
    header('Cache-Control: no-store');
    header('X-Content-Type-Options: nosniff');
    header('Location: ' . $path, true, ops_legacy_redirect_status());
    exit;
}
