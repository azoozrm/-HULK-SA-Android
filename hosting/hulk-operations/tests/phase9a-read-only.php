<?php

declare(strict_types=1);

/**
 * Phase 9A contract: the legacy Operations owner panel is read-only.
 *
 * The legacy panel keeps its authentication and read views, but every owner
 * business mutation is routed to the equivalent HULK SA Control Center module
 * before the authoritative mutation function can run. This test proves the
 * routing data and the gate ordering in the entry points.
 */

require_once dirname(__DIR__) . '/admin/read-only.php';

$operationsBase = 'https://hulksa.com/hulk-operations';

ops_test(
    ops_legacy_control_center_origin($operationsBase) === 'https://hulksa.com',
    'Control Center origin is derived from the Operations base URL'
);
ops_test(
    ops_legacy_control_center_origin('https://127.0.0.1:8443/hulk-operations') === 'https://127.0.0.1:8443',
    'Control Center origin preserves a non-default HTTPS port'
);
ops_test(
    ops_legacy_control_center_origin('not-a-url') === null,
    'an unparseable base URL yields no Control Center origin'
);

ops_test(
    ops_legacy_control_center_url('dashboard', $operationsBase) === 'https://hulksa.com/control-center/',
    'dashboard maps to the Control Center root'
);
foreach (['releases', 'announcements', 'service', 'features', 'growth', 'audit'] as $legacySection) {
    ops_test(
        ops_legacy_control_center_url($legacySection, $operationsBase)
            === 'https://hulksa.com/control-center/' . $legacySection . '/',
        'legacy section ' . $legacySection . ' maps to its exact Control Center module'
    );
}
ops_test(
    ops_legacy_control_center_url('../config', $operationsBase) === 'https://hulksa.com/control-center/',
    'an unknown section falls back to the safe dashboard route'
);

$adminIndex = (string) file_get_contents(dirname(__DIR__) . '/admin/index.php');
$deleteRelease = (string) file_get_contents(dirname(__DIR__) . '/admin/delete_release.php');
$setup = (string) file_get_contents(dirname(__DIR__) . '/admin/setup.php');
$login = (string) file_get_contents(dirname(__DIR__) . '/admin/login.php');
$logout = (string) file_get_contents(dirname(__DIR__) . '/admin/logout.php');

ops_test(
    strpos($adminIndex, 'ops_legacy_block_owner_mutation($section)') !== false
        && strpos($adminIndex, 'ops_legacy_block_owner_mutation($section)') < strpos($adminIndex, 'ops_admin_handle_post('),
    'authenticated Operations POST is routed away before ops_admin_handle_post()'
);
ops_test(
    strpos($deleteRelease, "ops_legacy_block_owner_mutation('releases')") !== false
        && strpos($deleteRelease, "ops_legacy_block_owner_mutation('releases')") < strpos($deleteRelease, 'ops_delete_release($db, $admin)'),
    'legacy release deletion is routed away before ops_delete_release()'
);
ops_test(
    strpos($adminIndex, 'legacy-read-only') !== false,
    'authenticated Operations content is rendered in the disabled read-only region'
);
ops_test(
    strpos($adminIndex, 'HULK SA Control Center') !== false && strpos($login, 'HULK SA Control Center') !== false,
    'the legacy panel and login state that administration moved to Control Center'
);
ops_test(
    strpos($login, 'ops_csrf_token()') !== false && strpos($logout, 'session_destroy()') !== false,
    'legacy login and logout session actions remain available'
);
ops_test(
    strpos($setup, 'INSERT INTO app_admin_users') === false && strpos($setup, 'create_admin.php') !== false,
    'web first-admin setup no longer inserts an administrator and directs to CLI recovery'
);
