<?php

declare(strict_types=1);

/**
 * R06 focused contract for the shared release URL/artifact helpers used by the
 * public update API, the Control Center and the permanent /app/ resolver.
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

$resolverTestDirectory = sys_get_temp_dir() . '/hulk-r06-resolver-' . bin2hex(random_bytes(6));
ops_test(mkdir($resolverTestDirectory, 0700), 'isolated resolver test directory is created');
$resolverConfigPath = $resolverTestDirectory . '/config.php';
$resolverConfig = [
    'database' => [
        'dsn' => 'sqlite::memory:',
        'username' => '',
        'password' => '',
        'options' => [],
    ],
    'app' => [
        'base_url' => 'https://hulksa.com/hulk-operations',
        'timezone' => 'Asia/Riyadh',
        'session_name' => 'hulk_operations_r06_test',
        'max_apk_bytes' => 629145600,
        'api_cache_seconds' => 60,
    ],
];
ops_test(
    file_put_contents(
        $resolverConfigPath,
        '<?php return ' . var_export($resolverConfig, true) . ';'
    ) !== false,
    'isolated resolver configuration is written'
);
$priorOperationsConfig = getenv('HULK_OPERATIONS_CONFIG');
putenv('HULK_OPERATIONS_CONFIG=' . $resolverConfigPath);

require_once dirname(__DIR__) . '/bootstrap.php';

$validRelease = [
    'id' => 6,
    'version_name' => '1.0.0',
    'version_code' => 68,
    'minimum_supported_version_code' => 68,
    'apk_path' => 'releases/hulk-sa-v1.0.0-68-1d957b42.apk',
    'apk_sha256' => str_repeat('a', 64),
    'apk_size_bytes' => 1024,
    'release_notes' => 'ملاحظات',
    'required' => 1,
    'enabled' => 1,
    'is_active' => 1,
];

ops_test(
    ops_release_relative_apk_path($validRelease) === 'releases/hulk-sa-v1.0.0-68-1d957b42.apk',
    'a valid Operations release path is accepted'
);
ops_test(
    ops_release_public_apk_url($validRelease) === 'https://hulksa.com/hulk-operations/releases/hulk-sa-v1.0.0-68-1d957b42.apk',
    'the public URL uses the trusted Operations base and validated path'
);

foreach ([
    'releases/../config.php',
    'releases/%2e%2e/config.php',
    'releases/%2fconfig.php',
    'releases/config.php',
    'releases/file.apk?download=1',
    'releases/file.apk#fragment',
    'https://evil.example/file.apk',
    '/releases/file.apk',
    'releases\\file.apk',
    'releases/.hidden.apk',
    'releases/hulk-sa-v1..0-69-abcdef12.apk',
    '',
] as $unsafePath) {
    $candidate = $validRelease;
    $candidate['apk_path'] = $unsafePath;
    ops_test(
        ops_release_relative_apk_path($candidate) === null,
        'unsafe release path is rejected: ' . $unsafePath
    );
    ops_test(
        ops_release_public_apk_url($candidate) === null,
        'unsafe release path cannot produce a public URL: ' . $unsafePath
    );
}

$invalidSha = $validRelease;
$invalidSha['apk_sha256'] = str_repeat('z', 64);
ops_test(ops_release_public_apk_url($invalidSha) === null, 'invalid release SHA-256 produces no public URL');
ops_test(ops_release_artifact_is_readable($invalidSha) === false, 'invalid release metadata cannot be served');

$fixtureName = 'r06-test-' . bin2hex(random_bytes(4)) . '.apk';
$fixturePath = dirname(__DIR__) . '/releases/' . $fixtureName;
ops_test(file_put_contents($fixturePath, str_repeat('x', 1024)) !== false, 'isolated APK fixture is written');
$fixtureRelease = $validRelease;
$fixtureRelease['apk_path'] = 'releases/' . $fixtureName;
ops_test(ops_release_artifact_is_readable($fixtureRelease), 'an existing APK with the expected size is readable');
$fixtureRelease['apk_size_bytes'] = 0;
ops_test(!ops_release_artifact_is_readable($fixtureRelease), 'zero recorded size fails closed');
$fixtureRelease['apk_size_bytes'] = 1023;
ops_test(!ops_release_artifact_is_readable($fixtureRelease), 'a size mismatch fails closed');
@unlink($fixturePath);
ops_test(!ops_release_artifact_is_readable($fixtureRelease), 'a missing APK fails closed');

if (in_array('sqlite', PDO::getAvailableDrivers(), true)) {
    $db = new PDO('sqlite::memory:');
    $db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
    $db->exec(
        'CREATE TABLE app_releases ('
        . 'id INTEGER PRIMARY KEY, version_name TEXT NOT NULL, version_code INTEGER NOT NULL, '
        . 'minimum_supported_version_code INTEGER NOT NULL, apk_path TEXT NOT NULL, apk_sha256 TEXT NOT NULL, '
        . 'apk_size_bytes INTEGER NOT NULL, release_notes TEXT NOT NULL, required INTEGER NOT NULL, '
        . 'enabled INTEGER NOT NULL, is_active INTEGER NOT NULL)'
    );
    $db->exec('CREATE TABLE app_settings (setting_key TEXT PRIMARY KEY, setting_value TEXT)');
    $insert = $db->prepare(
        'INSERT INTO app_releases (id, version_name, version_code, minimum_supported_version_code, '
        . 'apk_path, apk_sha256, apk_size_bytes, release_notes, required, enabled, is_active) '
        . 'VALUES (6, :name, 68, 68, :path, :sha, 1024, :notes, 1, 1, 1)'
    );
    $insert->execute([
        'name' => '1.0.0',
        'path' => 'releases/hulk-sa-v1.0.0-68-1d957b42.apk',
        'sha' => str_repeat('a', 64),
        'notes' => 'ملاحظات',
    ]);

    $active = ops_active_release($db);
    ops_test(is_array($active) && (int) $active['version_code'] === 68, 'the active release is the enabled-active authority');
    $snapshot = ops_update_snapshot($db);
    ops_test(
        $snapshot['apkUrl'] === 'https://hulksa.com/hulk-operations/releases/hulk-sa-v1.0.0-68-1d957b42.apk',
        'the public update snapshot and resolver share the same validated URL'
    );
    ops_test($snapshot['latestVersionCode'] === 68 && $snapshot['required'] === true, 'the update snapshot keeps the active version and policy');
} else {
    fwrite(STDOUT, "SKIP: PDO SQLite is unavailable; resolver update-snapshot consistency was not executed.\n");
}

putenv($priorOperationsConfig === false || $priorOperationsConfig === ''
    ? 'HULK_OPERATIONS_CONFIG'
    : 'HULK_OPERATIONS_CONFIG=' . $priorOperationsConfig);
@unlink($resolverConfigPath);
@rmdir($resolverTestDirectory);
