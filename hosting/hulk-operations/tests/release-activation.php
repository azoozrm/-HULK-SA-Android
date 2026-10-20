<?php

declare(strict_types=1);

/**
 * R06 correction contract: activation must reject a staged release unless its
 * public URL, SHA-256 metadata, artifact readability and recorded size are all
 * valid. Invalid activation must not disturb the current active row/settings.
 */

if (!function_exists('ops_test')) {
    $tests = 0;
    function ops_test(bool $condition, string $message): void
    {
        global $tests;
        $tests++;
        if (!$condition) {
            fwrite(STDERR, "FAIL: {$message}\n");
            exit(1);
        }
    }
}

final class OpsReleaseActivationTestPdo extends PDO
{
    public function prepare(string $query, array $options = []): PDOStatement|false
    {
        $query = str_ireplace(' FOR UPDATE', '', $query);
        if (stripos($query, 'ON DUPLICATE KEY UPDATE') !== false) {
            $query = (string) preg_replace(
                '/ON DUPLICATE KEY UPDATE\s+([A-Za-z0-9_]+)\s*=\s*VALUES\(([A-Za-z0-9_]+)\)/i',
                'ON CONFLICT(setting_key) DO UPDATE SET $1 = excluded.$2',
                $query
            );
        }

        return parent::prepare($query, $options);
    }
}

$fixtureDirectory = sys_get_temp_dir() . '/hulk-r06-activation-' . bin2hex(random_bytes(6));
ops_test(mkdir($fixtureDirectory, 0700), 'isolated activation fixture directory is created');
$fixtureConfigPath = $fixtureDirectory . '/config.php';
$fixtureConfig = [
    'database' => [
        'dsn' => 'sqlite::memory:',
        'username' => '',
        'password' => '',
        'options' => [],
    ],
    'app' => [
        'base_url' => 'https://hulksa.com/hulk-operations',
        'timezone' => 'Asia/Riyadh',
        'session_name' => 'hulk_operations_activation_test',
        'max_apk_bytes' => 629145600,
        'api_cache_seconds' => 60,
    ],
];
ops_test(
    file_put_contents(
        $fixtureConfigPath,
        '<?php return ' . var_export($fixtureConfig, true) . ';'
    ) !== false,
    'isolated activation configuration is written'
);
$priorOperationsConfig = getenv('HULK_OPERATIONS_CONFIG');
putenv('HULK_OPERATIONS_CONFIG=' . $fixtureConfigPath);

require_once dirname(__DIR__) . '/bootstrap.php';
if (!defined('HULK_OPERATIONS_ADMIN')) {
    define('HULK_OPERATIONS_ADMIN', true);
}
require_once dirname(__DIR__) . '/admin/actions.php';

$db = new OpsReleaseActivationTestPdo('sqlite::memory:');
$db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
$db->exec(
    'CREATE TABLE app_releases ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, version_name TEXT NOT NULL, version_code INTEGER NOT NULL UNIQUE, '
    . 'minimum_supported_version_code INTEGER NOT NULL, apk_path TEXT NOT NULL, apk_sha256 TEXT NOT NULL, '
    . 'apk_size_bytes INTEGER NOT NULL, release_notes TEXT NOT NULL, required INTEGER NOT NULL, '
    . 'enabled INTEGER NOT NULL, is_active INTEGER NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)'
);
$db->exec('CREATE TABLE app_settings (setting_key TEXT PRIMARY KEY, setting_value TEXT)');
$db->exec(
    'CREATE TABLE app_admin_audit ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, admin_user_id INTEGER, action TEXT NOT NULL, '
    . 'details TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)'
);

$releasesRoot = dirname(__DIR__) . '/releases';
$fixtureSuffix = bin2hex(random_bytes(4));
$fixtureFiles = [
    'active' => 'releases/ops-activation-active-' . $fixtureSuffix . '.apk',
    'valid' => 'releases/ops-activation-valid-' . $fixtureSuffix . '.apk',
    'badsha' => 'releases/ops-activation-badsha-' . $fixtureSuffix . '.apk',
    'size' => 'releases/ops-activation-size-' . $fixtureSuffix . '.apk',
    'unreadable' => 'releases/ops-activation-unreadable-' . $fixtureSuffix . '.apk',
];
foreach ($fixtureFiles as $relativePath) {
    ops_test(
        file_put_contents($releasesRoot . '/' . basename($relativePath), str_repeat('x', 1024)) !== false,
        'fixture APK is written: ' . $relativePath
    );
}
ops_test(
    chmod($releasesRoot . '/' . basename($fixtureFiles['unreadable']), 0000),
    'unreadable fixture APK is made unreadable'
);

$sha = str_repeat('a', 64);
$insert = $db->prepare(
    'INSERT INTO app_releases (version_name, version_code, minimum_supported_version_code, '
    . 'apk_path, apk_sha256, apk_size_bytes, release_notes, required, enabled, is_active) '
    . 'VALUES (:name, :code, :minimum, :path, :sha, :size, :notes, :required, :enabled, :active)'
);
$rows = [
    ['1.0.0', 68, 68, $fixtureFiles['active'], $sha, 1024, 1, 1, 1],
    ['1..0', 69, 68, 'releases/hulk-sa-v1..0-69-abcdef12.apk', $sha, 1024, 0, 0, 0],
    ['1.0.2', 70, 68, 'releases/ops-activation-missing-' . $fixtureSuffix . '.apk', $sha, 1024, 0, 0, 0],
    ['1.0.3', 71, 68, $fixtureFiles['badsha'], str_repeat('z', 64), 1024, 0, 0, 0],
    ['1.0.4', 72, 68, $fixtureFiles['size'], $sha, 2048, 0, 0, 0],
    ['1.0.5', 73, 68, $fixtureFiles['valid'], $sha, 1024, 0, 0, 0],
    ['1.0.6', 74, 68, $fixtureFiles['unreadable'], $sha, 1024, 0, 0, 0],
];
foreach ($rows as $row) {
    $insert->execute([
        'name' => $row[0],
        'code' => $row[1],
        'minimum' => $row[2],
        'path' => $row[3],
        'sha' => $row[4],
        'size' => $row[5],
        'notes' => 'fixture',
        'required' => $row[6],
        'enabled' => $row[7],
        'active' => $row[8],
    ]);
}
$setting = $db->prepare(
    'INSERT INTO app_settings (setting_key, setting_value) VALUES (:key, :value)'
);
foreach ([
    'active_release_id' => '1',
    'latest_version_code' => '68',
    'latest_version_name' => '1.0.0',
    'minimum_supported_version_code' => '68',
    'release_required' => '1',
] as $key => $value) {
    $setting->execute(['key' => $key, 'value' => $value]);
}

$settingValue = static function (string $key) use ($db): string {
    $statement = $db->prepare('SELECT setting_value FROM app_settings WHERE setting_key = :key LIMIT 1');
    $statement->execute(['key' => $key]);
    $value = $statement->fetchColumn();
    return $value === false ? '' : (string) $value;
};
$assertActiveUnchanged = static function (string $label) use ($db, $settingValue, $fixtureFiles): void {
    $active = ops_active_release($db);
    ops_test(
        is_array($active) && (int) $active['id'] === 1 && (int) $active['version_code'] === 68
            && (int) $active['enabled'] === 1 && (int) $active['is_active'] === 1,
        $label . ' keeps the previous active release'
    );
    ops_test($settingValue('active_release_id') === '1', $label . ' keeps active_release_id');
    ops_test($settingValue('latest_version_code') === '68', $label . ' keeps latest_version_code');
    ops_test($settingValue('latest_version_name') === '1.0.0', $label . ' keeps latest_version_name');
    ops_test($settingValue('minimum_supported_version_code') === '68', $label . ' keeps the minimum');
    ops_test($settingValue('release_required') === '1', $label . ' keeps release_required');
    $snapshot = ops_update_snapshot($db);
    ops_test(
        (int) $snapshot['latestVersionCode'] === 68 && $snapshot['required'] === true,
        $label . ' keeps the public update policy'
    );
    ops_test(
        is_string($snapshot['apkUrl'])
            && str_ends_with($snapshot['apkUrl'], basename($fixtureFiles['active'])),
        $label . ' keeps the public APK URL'
    );
};

ops_test(
    ops_release_relative_apk_path(['apk_path' => 'releases/hulk-sa-v1..0-69-abcdef12.apk']) === null,
    'generated 1..0 path is rejected by the canonical helper'
);
ops_test(
    ops_release_relative_apk_path(['apk_path' => 'releases/hulk-sa-v1.0.1-69-abcdef12.apk'])
        === 'releases/hulk-sa-v1.0.1-69-abcdef12.apk',
    'normal generated path is accepted by the canonical helper'
);

$admin = ['id' => 1, 'username' => 'fixture'];
foreach ([
    2 => 'invalid generated path',
    3 => 'missing artifact',
    4 => 'invalid SHA metadata',
    5 => 'size mismatch',
    7 => 'unreadable artifact',
] as $releaseId => $label) {
    $_POST['release_id'] = $releaseId;
    $error = null;
    try {
        ops_activate_release($db, $admin);
    } catch (InvalidArgumentException $exception) {
        $error = $exception->getMessage();
    }
    ops_test(
        is_string($error) && str_contains($error, 'ملف APK'),
        $label . ' activation is rejected'
    );
    $assertActiveUnchanged($label);
}

$_POST['release_id'] = 6;
ops_activate_release($db, $admin);
$active = ops_active_release($db);
ops_test(
    is_array($active) && (int) $active['id'] === 6 && (int) $active['version_code'] === 73
        && (int) $active['enabled'] === 1 && (int) $active['is_active'] === 1,
    'a valid release remains activatable'
);
ops_test($settingValue('active_release_id') === '6', 'valid activation updates active_release_id');
ops_test($settingValue('latest_version_code') === '73', 'valid activation updates latest_version_code');
ops_test($settingValue('minimum_supported_version_code') === '68', 'valid activation keeps the release minimum');
ops_test($settingValue('release_required') === '0', 'valid activation updates release_required');
$validSnapshot = ops_update_snapshot($db);
ops_test(
    (int) $validSnapshot['latestVersionCode'] === 73
        && $validSnapshot['required'] === false
        && is_string($validSnapshot['apkUrl'])
        && str_ends_with($validSnapshot['apkUrl'], basename($fixtureFiles['valid'])),
    'valid activation publishes the new APK URL and policy'
);

foreach ($fixtureFiles as $relativePath) {
    $absolutePath = $releasesRoot . '/' . basename($relativePath);
    @chmod($absolutePath, 0644);
    @unlink($absolutePath);
}
putenv($priorOperationsConfig === false || $priorOperationsConfig === ''
    ? 'HULK_OPERATIONS_CONFIG'
    : 'HULK_OPERATIONS_CONFIG=' . $priorOperationsConfig);
@unlink($fixtureConfigPath);
@rmdir($fixtureDirectory);
