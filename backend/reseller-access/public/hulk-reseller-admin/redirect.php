<?php

declare(strict_types=1);

/**
 * Phase 9B legacy reseller-owner redirect gate.
 *
 * Stage B retires the legacy /hulk-reseller-admin/ owner entry points. They no
 * longer start a session, verify CSRF, read the database or call any
 * hulk_admin_* mutation; each request is redirected to HULK SA Control Center,
 * which is now the sole owner-facing reseller administration entry.
 *
 * This file only derives a fixed Control Center path and emits the correct
 * navigation status. It defines no reseller state and no cutover flag.
 */

function hulk_legacy_owner_control_center_url(): string
{
    return '/control-center/resellers/';
}

function hulk_legacy_owner_login_url(): string
{
    return '/control-center/login.php';
}

/**
 * 303 after a retired legacy POST so the browser does not replay the old POST
 * against the destination; 302 for legacy GET/navigation entries.
 */
function hulk_legacy_owner_redirect_status(): int
{
    return ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST' ? 303 : 302;
}

function hulk_legacy_owner_redirect(string $location): never
{
    header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
    header('Referrer-Policy: no-referrer');
    header('X-Content-Type-Options: nosniff');
    header('Location: ' . $location, true, hulk_legacy_owner_redirect_status());
    exit;
}
