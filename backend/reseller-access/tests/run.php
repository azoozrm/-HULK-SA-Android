<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/public/.hulk-reseller-app/bootstrap.php';
require_once dirname(__DIR__) . '/public/hulk-reseller-admin/redirect.php';

$tests = 0;
function reseller_test(bool $condition, string $message): void
{
    global $tests;
    $tests++;
    if (!$condition) {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    }
}

$db = new PDO('sqlite::memory:');
$db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
$db->exec(
    'CREATE TABLE resellers ('
    . 'reseller_id INTEGER PRIMARY KEY AUTOINCREMENT, reseller_name TEXT NOT NULL, '
    . 'reseller_name_key TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, host TEXT NOT NULL, '
    . 'access_code TEXT NOT NULL UNIQUE, access_code_hash TEXT NOT NULL UNIQUE, '
    . "status TEXT NOT NULL DEFAULT 'active', created_at TEXT, updated_at TEXT)"
);

// Synthetic historical row proves every obsolete callable fails before writing
$seed = $db->prepare('INSERT INTO resellers (reseller_name,reseller_name_key,password_hash,host,access_code,access_code_hash,status) VALUES (:name,:key,:password,:host,:code,:hash,:status)');
$seed->execute(['name'=>'Historical Test','key'=>'historical-test','password'=>'synthetic-frozen-hash','host'=>'https://legacy.example.test','code'=>'h5AbCdE29Z','hash'=>hash('sha256','h5AbCdE29Z'),'status'=>'active']);
$frozen = $db->query('SELECT * FROM resellers ORDER BY reseller_id')->fetchAll();
$retiredMutations = [
    'create' => static fn () => hulk_admin_create_reseller($db, ['reseller_name'=>'Synthetic','password'=>'synthetic-password','host'=>'https://new.example.test']),
    'status' => static fn () => hulk_admin_set_status($db, 1, 'inactive'),
    'host' => static fn () => hulk_admin_update_host($db, 1, 'https://changed.example.test'),
    'custom code' => static fn () => hulk_admin_set_code($db, 1, 'ChangedCustomCode'),
    'rotation' => static fn () => hulk_admin_rotate_code($db, 1),
    'code writer' => static fn () => hulk_admin_write_code($db, 1, 'ChangedCustomCode'),
    'password' => static fn () => hulk_admin_reset_password($db, 1, 'synthetic-new-password', 'synthetic-new-password'),
];
foreach ($retiredMutations as $name => $mutation) {
    try {
        $mutation();
        reseller_test(false, 'retired ' . $name . ' mutation must fail closed');
    } catch (RuntimeException) {
        reseller_test(true, 'retired ' . $name . ' mutation is explicitly rejected');
    }
    reseller_test($db->query('SELECT * FROM resellers ORDER BY reseller_id')->fetchAll() === $frozen, 'retired ' . $name . ' mutation preserves every historical field and row');
}
reseller_test((int) $db->query('SELECT COUNT(*) FROM resellers')->fetchColumn() === 1, 'retirement creates no dummy reseller account');
$generated = [];
for ($index = 0; $index < 100; $index++) {
    $code = hulk_generate_access_code();
    reseller_test(preg_match('/^[A-Za-z0-9]{10}$/D', $code) === 1, 'generated code keeps the deployed ten-character alphanumeric format');
    reseller_test(preg_match('/[A-Z]/', $code) === 1 && preg_match('/[a-z]/', $code) === 1 && preg_match('/[0-9]/', $code) === 1, 'generated code contains all deployed character classes');
    reseller_test(hulk_normalize_access_code($code) === $code && !isset($generated[$code]), 'generated code is stable and distinct in the bounded sample');
    $generated[$code] = true;
}
reseller_test(hulk_normalize_access_code('h5AbCdE29Z') === 'h5AbCdE29Z', 'normalization preserves the exact deployed mixed-case value');
reseller_test(hulk_access_code_matches('h5AbCdE29Z', 'H5ABCDE29Z') === false, 'deployed short code matching remains case sensitive');
reseller_test(hulk_access_code_matches('HULK-ABCD-EFGH-JKMN-PQRS', 'hulk abcd efgh jkmn pqrs'), 'legacy canonical codes retain their compatible formatted-input matching');
foreach (['', str_repeat('x', 65), "control\x00value"] as $invalidCode) {
    reseller_test(hulk_normalize_access_code($invalidCode) === null, 'empty oversized and control-character codes are rejected');
}

// Phase 9B: the retired legacy reseller-owner entry points redirect.
reseller_test(
    hulk_legacy_owner_control_center_url() === '/control-center/resellers/',
    'legacy owner navigation routes to the exact Control Center resellers module'
);
reseller_test(
    hulk_legacy_owner_login_url() === '/control-center/login.php',
    'a direct historical owner login routes to the Control Center login route'
);

$_SERVER['REQUEST_METHOD'] = 'GET';
reseller_test(hulk_legacy_owner_redirect_status() === 302, 'legacy owner GET navigation uses HTTP 302');
$_SERVER['REQUEST_METHOD'] = 'POST';
reseller_test(hulk_legacy_owner_redirect_status() === 303, 'legacy owner POST uses HTTP 303');
unset($_SERVER['REQUEST_METHOD']);
reseller_test(hulk_legacy_owner_redirect_status() === 302, 'a missing method is treated as navigation');

$ownerIndexSource = (string) file_get_contents(dirname(__DIR__) . '/public/hulk-reseller-admin/index.php');
$ownerActionSource = (string) file_get_contents(dirname(__DIR__) . '/public/hulk-reseller-admin/action.php');
$forbiddenLegacyWork = [
    'hulk_start_session',
    'hulk_verify_csrf',
    'hulk_db(',
    'hulk_current_admin',
    'hulk_normalize_reseller_name',
    'password_verify',
    '$_SESSION',
    'hulk_admin_create_reseller',
    'hulk_admin_set_status',
    'hulk_admin_update_host',
    'hulk_admin_set_code',
    'hulk_admin_rotate_code',
    'hulk_admin_reset_password',
];
foreach (['index.php' => $ownerIndexSource, 'action.php' => $ownerActionSource] as $route => $source) {
    reseller_test(
        strpos($source, 'hulk_legacy_owner_redirect(') !== false,
        'legacy owner ' . $route . ' delegates to the Phase 9B redirect gate'
    );
    foreach ($forbiddenLegacyWork as $marker) {
        reseller_test(
            strpos($source, $marker) === false,
            'legacy owner ' . $route . ' no longer contains ' . $marker
        );
    }
}
reseller_test(
    strpos($ownerActionSource, "'login'") !== false,
    'the retired owner action endpoint still distinguishes the historical login action'
);

fwrite(STDOUT, "PASS: {$tests} legacy retirement and resolver-format checks.\n");
