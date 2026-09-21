<?php

declare(strict_types=1);

/**
 * Phase 9B contract: the retired legacy Operations owner entry points are
 * deterministic redirects to the equivalent HULK SA Control Center module.
 *
 * This test calls the redirect gate directly to prove the allow-listed mapping
 * and the 302/303 status rule, then verifies that every retired route delegates
 * to the gate and contains no legacy authentication, database, CSRF or
 * mutation code.
 */

require_once dirname(__DIR__) . '/admin/redirect.php';

ops_test(
    ops_legacy_control_center_modules() === ['dashboard', 'releases', 'announcements', 'service', 'features', 'growth', 'audit'],
    'the legacy Operations allow-list is exact and ordered'
);

ops_test(
    ops_legacy_control_center_path('dashboard') === '/control-center/',
    'legacy dashboard maps to the Control Center root'
);
foreach ([
    'releases' => '/control-center/releases/',
    'announcements' => '/control-center/announcements/',
    'service' => '/control-center/service/',
    'features' => '/control-center/features/',
    'growth' => '/control-center/growth/',
    'audit' => '/control-center/audit/',
] as $legacySection => $expected) {
    ops_test(
        ops_legacy_control_center_path($legacySection) === $expected,
        'legacy section ' . $legacySection . ' maps to its exact Control Center module'
    );
}

foreach (['unknown', '../config', '../../admin', 'https://evil.example/', 'releases/../../etc', ''] as $untrusted) {
    ops_test(
        ops_legacy_control_center_path($untrusted) === '/control-center/',
        'an untrusted section value falls back to the safe Control Center root'
    );
}

ops_test(
    ops_legacy_control_center_login_path() === '/control-center/login.php',
    'legacy owner login maps to the Control Center login route'
);

$_SERVER['REQUEST_METHOD'] = 'GET';
ops_test(ops_legacy_redirect_status() === 302, 'legacy GET navigation uses HTTP 302');
$_SERVER['REQUEST_METHOD'] = 'POST';
ops_test(ops_legacy_redirect_status() === 303, 'legacy POST uses HTTP 303');
$_SERVER['REQUEST_METHOD'] = 'HEAD';
ops_test(ops_legacy_redirect_status() === 302, 'a non-POST method is treated as navigation');
unset($_SERVER['REQUEST_METHOD']);
ops_test(ops_legacy_redirect_status() === 302, 'a missing method is treated as navigation');

$legacyRoutes = [
    'index.php',
    'admin/index.php',
    'admin/login.php',
    'admin/logout.php',
    'admin/setup.php',
    'admin/delete_release.php',
];
$forbidden = [
    'ops_require_admin',
    'ops_require_csrf',
    'ops_db(',
    'ops_load_config',
    'ops_start_admin_session',
    'session_start',
    'ops_admin_handle_post',
    'ops_delete_release',
    'ops_take_flash',
    'INSERT INTO app_admin_users',
    'DELETE FROM app_releases',
    'password_verify',
];
foreach ($legacyRoutes as $route) {
    $source = (string) file_get_contents(dirname(__DIR__) . '/' . $route);
    ops_test(
        strpos($source, 'ops_legacy_redirect(') !== false,
        $route . ' delegates to the Phase 9B redirect gate'
    );
    foreach ($forbidden as $marker) {
        ops_test(
            strpos($source, $marker) === false,
            $route . ' no longer contains legacy work: ' . $marker
        );
    }
}

$deleteRelease = (string) file_get_contents(dirname(__DIR__) . '/admin/delete_release.php');
ops_test(
    strpos($deleteRelease, "ops_legacy_control_center_path('releases')") !== false,
    'legacy release deletion targets the Control Center releases module'
);

$setup = (string) file_get_contents(dirname(__DIR__) . '/admin/setup.php');
ops_test(
    strpos($setup, 'ops_legacy_control_center_login_path()') !== false,
    'the retired web setup targets the Control Center login route'
);
ops_test(
    strpos($setup, 'INSERT') === false
        && strpos($setup, 'ops_db(') === false
        && strpos($setup, 'app_admin_users') === false,
    'the retired web setup cannot create or touch an administrator record'
);

ops_test(
    !is_file(dirname(__DIR__) . '/admin/read-only.php')
        && !function_exists('ops_legacy_block_owner_mutation'),
    'the Phase 9A read-only gate has been replaced by the Phase 9B redirect gate'
);
