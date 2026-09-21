<?php

declare(strict_types=1);

/**
 * Phase 9A legacy Operations owner cutover.
 *
 * The legacy Operations owner panel stays authenticated and readable, but it no
 * longer mutates production state. Owner mutations are performed in HULK SA
 * Control Center, which reuses the unchanged authoritative functions in
 * actions.php. This file only routes a blocked legacy mutation to the
 * equivalent current Control Center module. It defines no owner state and no
 * cutover feature flag.
 */

function ops_legacy_control_center_modules(): array
{
    return ['dashboard', 'releases', 'announcements', 'service', 'features', 'growth', 'audit'];
}

function ops_legacy_control_center_origin(string $baseUrl): ?string
{
    $parts = parse_url(trim($baseUrl));
    if (!is_array($parts)) {
        return null;
    }

    $scheme = strtolower((string) ($parts['scheme'] ?? ''));
    $host = (string) ($parts['host'] ?? '');
    if (($scheme !== 'https' && $scheme !== 'http') || $host === '') {
        return null;
    }

    $origin = $scheme . '://' . $host;
    if (isset($parts['port'])) {
        $origin .= ':' . (int) $parts['port'];
    }

    return $origin;
}

function ops_legacy_control_center_url(string $section = 'dashboard', ?string $baseUrl = null): string
{
    if ($baseUrl === null) {
        try {
            $baseUrl = (string) ops_load_config()['app']['base_url'];
        } catch (Throwable $exception) {
            $baseUrl = '';
        }
    }

    $module = in_array($section, ops_legacy_control_center_modules(), true) ? $section : 'dashboard';
    $origin = ops_legacy_control_center_origin($baseUrl);
    if ($origin === null) {
        return '/control-center/';
    }

    return $origin
        . ($module === 'dashboard' ? '/control-center/' : '/control-center/' . rawurlencode($module) . '/');
}

function ops_legacy_block_owner_mutation(string $section = 'dashboard'): never
{
    ops_redirect(ops_legacy_control_center_url($section) . '?legacy=readonly');
}
