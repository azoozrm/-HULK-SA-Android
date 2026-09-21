<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';
if (!defined('HULK_OPERATIONS_ADMIN')) {
    define('HULK_OPERATIONS_ADMIN', true);
}
require_once dirname(__DIR__) . '/admin/actions.php';

$releaseActionsSource = (string) file_get_contents(dirname(__DIR__) . '/admin/actions.php');
$legacyDeleteSource = (string) file_get_contents(dirname(__DIR__) . '/admin/delete_release.php');

ops_test(
    substr_count($releaseActionsSource, 'function ops_delete_release(') === 1,
    'exactly one authoritative release deletion implementation exists'
);
ops_test(
    str_contains($legacyDeleteSource, 'ops_delete_release(')
        && !str_contains($legacyDeleteSource, 'DELETE FROM app_releases')
        && !str_contains($legacyDeleteSource, 'FOR UPDATE'),
    'the legacy Operations route delegates to the authoritative deletion implementation'
);
ops_test(
    str_contains($releaseActionsSource, 'function ops_upload_release(')
        && str_contains($releaseActionsSource, 'function ops_activate_release(')
        && str_contains($releaseActionsSource, 'function ops_disable_release(')
        && str_contains($releaseActionsSource, 'function ops_update_release_policy('),
    'existing release upload, activation, disable and policy actions remain in place'
);

if (!in_array('sqlite', PDO::getAvailableDrivers(), true)) {
    fwrite(STDOUT, "SKIP: PDO SQLite is unavailable; release deletion database checks were not run.\n");
    return;
}

final class OpsReleaseDeleteTestPdo extends PDO
{
    public function prepare(string $query, array $options = []): PDOStatement|false
    {
        // SQLite has no row-level locking; production keeps FOR UPDATE for MySQL.
        $query = str_ireplace(' FOR UPDATE', '', $query);
        if (stripos($query, 'ON DUPLICATE KEY UPDATE') !== false) {
            // Test-harness-only translation of the production MySQL upsert into SQLite.
            $query = (string) preg_replace(
                '/ON DUPLICATE KEY UPDATE\s+([A-Za-z0-9_]+)\s*=\s*VALUES\(([A-Za-z0-9_]+)\)/i',
                'ON CONFLICT(setting_key) DO UPDATE SET $1 = excluded.$2',
                $query
            );
        }

        return parent::prepare($query, $options);
    }
}

function ops_release_delete_db(): OpsReleaseDeleteTestPdo
{
    $db = new OpsReleaseDeleteTestPdo('sqlite::memory:');
    $db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
    $db->exec(
        'CREATE TABLE app_releases ('
        . 'id INTEGER PRIMARY KEY AUTOINCREMENT, version_name TEXT NOT NULL, version_code INTEGER NOT NULL, '
        . 'minimum_supported_version_code INTEGER NOT NULL, apk_path TEXT NOT NULL, apk_sha256 TEXT NOT NULL, '
        . 'apk_size_bytes INTEGER NOT NULL, release_notes TEXT, required INTEGER NOT NULL, '
        . 'enabled INTEGER NOT NULL, is_active INTEGER NOT NULL)'
    );
    $db->exec('CREATE TABLE app_settings (setting_key TEXT PRIMARY KEY, setting_value TEXT)');
    $db->exec(
        'CREATE TABLE app_admin_audit ('
        . 'id INTEGER PRIMARY KEY AUTOINCREMENT, admin_user_id INTEGER, action TEXT NOT NULL, '
        . 'details TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)'
    );

    return $db;
}

function ops_release_delete_seed(PDO $db, array $release): int
{
    $statement = $db->prepare(
        'INSERT INTO app_releases (version_name, version_code, minimum_supported_version_code, apk_path, '
        . 'apk_sha256, apk_size_bytes, release_notes, required, enabled, is_active) '
        . 'VALUES (:version_name, :version_code, :minimum, :apk_path, :sha, 1024, "notes", '
        . ':required, :enabled, :is_active)'
    );
    $statement->execute([
        'version_name' => (string) $release['version_name'],
        'version_code' => (int) $release['version_code'],
        'minimum' => (int) ($release['minimum_supported_version_code'] ?? $release['version_code']),
        'apk_path' => (string) $release['apk_path'],
        'sha' => (string) ($release['apk_sha256'] ?? str_repeat('a', 64)),
        'required' => (int) ($release['required'] ?? 0),
        'enabled' => (int) ($release['enabled'] ?? 0),
        'is_active' => (int) ($release['is_active'] ?? 0),
    ]);

    return (int) $db->lastInsertId();
}

function ops_release_delete_count(PDO $db): int
{
    return (int) $db->query('SELECT COUNT(*) FROM app_releases')->fetchColumn();
}

function ops_release_delete_audit(PDO $db): array
{
    return $db->query('SELECT action, details FROM app_admin_audit ORDER BY id')->fetchAll();
}

function ops_release_delete_setting(PDO $db, string $key): ?string
{
    $statement = $db->prepare('SELECT setting_value FROM app_settings WHERE setting_key = :key LIMIT 1');
    $statement->execute(['key' => $key]);
    $value = $statement->fetchColumn();

    return $value === false ? null : (string) $value;
}

function ops_release_delete_put_setting(PDO $db, string $key, string $value): void
{
    $statement = $db->prepare(
        'INSERT INTO app_settings (setting_key, setting_value) VALUES (:key, :value)'
    );
    $statement->execute(['key' => $key, 'value' => $value]);
}

function ops_release_delete_audit_details(PDO $db): ?array
{
    $audit = ops_release_delete_audit($db);
    $details = is_string($audit[0]['details'] ?? null) ? json_decode($audit[0]['details'], true) : null;

    return is_array($details) ? $details : null;
}

$releaseDeleteAdmin = ['id' => 7, 'username' => 'owner'];

foreach (
    [
        [],
        ['release_id' => ''],
        ['release_id' => 'abc'],
        ['release_id' => '0'],
        ['release_id' => '-3'],
        ['release_id' => '2.5'],
        ['release_id' => '99999999999999999999'],
    ] as $invalidPost
) {
    $db = ops_release_delete_db();
    ops_release_delete_seed($db, [
        'version_name' => '0.9.3.21',
        'version_code' => 65,
        'apk_path' => 'releases/hulk-invalid-test.apk',
    ]);
    $_POST = $invalidPost;
    $rejected = false;
    try {
        ops_delete_release($db, $releaseDeleteAdmin);
    } catch (InvalidArgumentException $exception) {
        $rejected = true;
    }
    ops_test($rejected, 'an invalid or missing release identifier is rejected before any mutation');
    ops_test(ops_release_delete_count($db) === 1, 'an invalid identifier leaves the release row untouched');
    ops_test(ops_release_delete_audit($db) === [], 'an invalid identifier records no destructive audit event');
}

$db = ops_release_delete_db();
$_POST = ['release_id' => '42'];
$missingRejected = false;
try {
    ops_delete_release($db, $releaseDeleteAdmin);
} catch (InvalidArgumentException $exception) {
    $missingRejected = true;
}
ops_test($missingRejected, 'a missing release fails safely');
ops_test(ops_release_delete_audit($db) === [], 'a missing release records no audit event');

foreach (
    [
        '../../config.php',
        'releases/../config.php',
        'releases/shell.php',
        '/etc/passwd',
        'releases/good.apk/evil.apk',
        'releases/hulk.apk.php',
        'https://evil.test/hulk.apk',
    ] as $unsafePath
) {
    $db = ops_release_delete_db();
    $releaseId = ops_release_delete_seed($db, [
        'version_name' => '0.9.3.21',
        'version_code' => 65,
        'apk_path' => $unsafePath,
    ]);
    $_POST = ['release_id' => (string) $releaseId];
    $rejected = false;
    try {
        ops_delete_release($db, $releaseDeleteAdmin);
    } catch (RuntimeException $exception) {
        $rejected = true;
    }
    ops_test($rejected, 'an unsafe APK path is rejected for: ' . $unsafePath);
    ops_test(ops_release_delete_count($db) === 1, 'an unsafe APK path leaves the release row untouched');
    ops_test(ops_release_delete_audit($db) === [], 'an unsafe APK path records no audit event');
}

$db = ops_release_delete_db();
$releasesDirectory = dirname(__DIR__) . '/releases';
$apkName = 'hulk-delete-test-' . bin2hex(random_bytes(6)) . '.apk';
$apkPath = $releasesDirectory . '/' . $apkName;
$keepName = 'hulk-keep-test-' . bin2hex(random_bytes(6)) . '.apk';
$keepPath = $releasesDirectory . '/' . $keepName;
file_put_contents($apkPath, 'synthetic-apk');
file_put_contents($keepPath, 'synthetic-keep');
register_shutdown_function(static function () use ($apkPath, $keepPath): void {
    if (is_file($apkPath)) {
        @unlink($apkPath);
    }
    if (is_file($keepPath)) {
        @unlink($keepPath);
    }
});
$releaseId = ops_release_delete_seed($db, [
    'version_name' => '0.9.3.21',
    'version_code' => 65,
    'minimum_supported_version_code' => 65,
    'apk_path' => 'releases/' . $apkName,
    'apk_sha256' => str_repeat('b', 64),
    'required' => 1,
    'enabled' => 1,
    'is_active' => 0,
]);
$_POST = ['release_id' => (string) $releaseId];
$message = ops_delete_release($db, $releaseDeleteAdmin);
ops_test(
    $message === 'تم حذف الإصدار وملف APK. يمكنك الآن رفع نفس رمز الإصدار من جديد.',
    'a normal deletion reports the safe success message'
);
ops_test(ops_release_delete_count($db) === 0, 'a normal deletion removes the database row');
$audit = ops_release_delete_audit($db);
ops_test(
    count($audit) === 1 && ($audit[0]['action'] ?? '') === 'RELEASE_DELETED',
    'a normal deletion records exactly one RELEASE_DELETED audit event'
);
$details = ops_release_delete_audit_details($db);
ops_test(
    is_array($details)
        && ($details['release_id'] ?? null) === $releaseId
        && ($details['version_name'] ?? null) === '0.9.3.21'
        && ($details['version_code'] ?? null) === 65
        && ($details['was_active'] ?? null) === false,
    'a normal deletion audits the deleted release identity and state'
);
ops_test(!is_file($apkPath), 'a normal deletion removes the intended APK through the safe path contract');
ops_test(is_file($keepPath), 'a normal deletion leaves unrelated release files untouched');

$db = ops_release_delete_db();
$releaseId = ops_release_delete_seed($db, [
    'version_name' => '0.9.3.30',
    'version_code' => 90,
    'minimum_supported_version_code' => 90,
    'apk_path' => 'releases/active-delete-test.apk',
    'required' => 1,
    'enabled' => 1,
    'is_active' => 1,
]);
$activeSettings = [
    'active_release_id' => (string) $releaseId,
    'latest_version_code' => '90',
    'latest_version_name' => '0.9.3.30',
    'minimum_supported_version_code' => '90',
    'release_required' => '1',
];
foreach ($activeSettings as $key => $value) {
    ops_release_delete_put_setting($db, $key, $value);
}
$_POST = ['release_id' => (string) $releaseId];
ops_delete_release($db, $releaseDeleteAdmin);
ops_test(ops_release_delete_setting($db, 'active_release_id') === '', 'deleting the active release clears the active release id');
ops_test(ops_release_delete_setting($db, 'latest_version_code') === '64', 'deleting the active release restores the safe latest version code');
ops_test(ops_release_delete_setting($db, 'latest_version_name') === '0.9.3.20', 'deleting the active release restores the safe latest version name');
ops_test(ops_release_delete_setting($db, 'minimum_supported_version_code') === '64', 'deleting the active release restores the safe minimum version code');
ops_test(ops_release_delete_setting($db, 'release_required') === '0', 'deleting the active release clears the required flag');
$activeDetails = ops_release_delete_audit_details($db);
ops_test(($activeDetails['was_active'] ?? null) === true, 'deleting the active release audits that it was active');

$db = ops_release_delete_db();
$releaseId = ops_release_delete_seed($db, [
    'version_name' => '0.9.3.29',
    'version_code' => 89,
    'apk_path' => 'releases/inactive-delete-test.apk',
]);
$inactiveSettings = [
    'active_release_id' => '77',
    'latest_version_code' => '88',
    'latest_version_name' => '0.9.3.28',
    'minimum_supported_version_code' => '80',
    'release_required' => '1',
];
foreach ($inactiveSettings as $key => $value) {
    ops_release_delete_put_setting($db, $key, $value);
}
$_POST = ['release_id' => (string) $releaseId];
ops_delete_release($db, $releaseDeleteAdmin);
foreach ($inactiveSettings as $key => $value) {
    ops_test(
        ops_release_delete_setting($db, $key) === $value,
        'deleting an inactive release preserves the existing update policy setting: ' . $key
    );
}

$db = ops_release_delete_db();
$releaseId = ops_release_delete_seed($db, [
    'version_name' => '0.9.3.27',
    'version_code' => 87,
    'apk_path' => 'releases/rollback-delete-test.apk',
]);
$db->exec('DROP TABLE app_admin_audit');
$_POST = ['release_id' => (string) $releaseId];
$failed = false;
try {
    ops_delete_release($db, $releaseDeleteAdmin);
} catch (Throwable $exception) {
    $failed = true;
}
ops_test($failed, 'a database failure during deletion is surfaced safely');
ops_test(ops_release_delete_count($db) === 1, 'a database failure rolls the release deletion back');
ops_test(
    (string) $db->query('SELECT version_name FROM app_releases WHERE id = ' . $releaseId)->fetchColumn() === '0.9.3.27',
    'the rolled-back release row remains intact'
);
