<?php

declare(strict_types=1);

/**
 * Phase 9A legacy reseller-owner cutover.
 *
 * The legacy reseller-owner panel stays authenticated and readable, but it no
 * longer mutates reseller state. Login and logout remain session actions; every
 * business mutation is routed to HULK SA Control Center, which reuses the
 * unchanged shared authority in `.hulk-reseller-app/admin-domain.php`. This
 * file defines no reseller state and no cutover feature flag.
 */

function hulk_legacy_owner_control_center_url(): string
{
    return '/control-center/resellers/';
}

function hulk_legacy_owner_session_actions(): array
{
    return ['login', 'logout'];
}

function hulk_legacy_owner_allows_session_action(string $action): bool
{
    return in_array($action, hulk_legacy_owner_session_actions(), true);
}

function hulk_legacy_owner_read_only_notice(): string
{
    return 'لوحة إدارة الموزعين القديمة أصبحت للعرض فقط. تتم إدارة الموزعين الآن من HULK SA Control Center.';
}
