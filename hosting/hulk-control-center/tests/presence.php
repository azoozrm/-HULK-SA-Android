<?php

declare(strict_types=1);

$presenceRoot = dirname(__DIR__);
$presenceMigration = file_get_contents($presenceRoot . '/migrations/2026-09-17-presence-v1.sql');
$presenceHttp = file_get_contents($presenceRoot . '/lib/presence-http.php');
$presenceDomain = file_get_contents($presenceRoot . '/lib/presence.php');

cc_test(is_string($presenceMigration), 'Presence migration is readable');
foreach (['cc_schema_migrations', 'cc_devices', 'cc_app_sessions', 'cc_api_rate_limits'] as $table) {
    cc_test(str_contains((string) $presenceMigration, 'CREATE TABLE IF NOT EXISTS ' . $table), $table . ' is additive');
}
foreach ([
    'uq_cc_devices_installation', 'uq_cc_app_sessions_session', 'uq_cc_app_sessions_token_hash',
    'idx_cc_app_sessions_online', 'idx_cc_app_sessions_reseller', 'idx_cc_app_sessions_device',
    'idx_cc_app_sessions_started', 'idx_cc_app_sessions_version', 'idx_cc_api_rate_limits_window',
] as $index) {
    cc_test(str_contains((string) $presenceMigration, $index), $index . ' is declared');
}
cc_test(!str_contains((string) $presenceMigration, 'DROP TABLE'), 'Presence migration has no destructive drop');
cc_test(!str_contains((string) $presenceMigration, 'account_id'), 'Presence schema does not invent an unsupplied account identifier');
cc_test(
    is_string($presenceHttp) && str_contains($presenceHttp, 'CC_PRESENCE_MAX_BODY_BYTES')
        && str_contains($presenceHttp, "REQUEST_METHOD")
        && str_contains($presenceHttp, 'application/json'),
    'Presence HTTP boundary is POST-only JSON with a bounded body'
);
cc_test(
    is_string($presenceHttp) && str_contains($presenceHttp, "error_log('HULK Control Center Presence request failed.')")
        && !str_contains($presenceHttp, 'file_get_contents(\'php://input\') .'),
    'Presence failure logging never includes request bodies'
);
require_once $presenceRoot . '/lib/presence-http.php';
try {
    cc_presence_decode_json_body(str_repeat('x', CC_PRESENCE_MAX_BODY_BYTES + 1));
    cc_test(false, 'oversized request bodies are rejected');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'BODY_TOO_LARGE', 'oversized request bodies are rejected');
}
try {
    cc_presence_decode_json_body('{not-json');
    cc_test(false, 'malformed JSON request bodies are rejected');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'INVALID_JSON', 'malformed JSON request bodies are rejected');
}
cc_test(
    is_string($presenceDomain) && str_contains($presenceDomain, "hash('sha256', \$token)")
        && !str_contains($presenceDomain, 'ops_audit('),
    'Presence stores token hashes and does not copy telemetry credentials into admin audit'
);

$presenceConfig = cc_presence_config([
    'presence' => [
        'heartbeat_seconds' => 60,
        'online_ttl_seconds' => 180,
        'session_retention_days' => 180,
        'device_retention_days' => 180,
        'cleanup_batch_size' => 200,
        'rate_limit_window_seconds' => 60,
        'rate_limits' => ['start' => 100, 'heartbeat' => 100, 'end' => 100],
        'rate_limit_secret' => str_repeat('s', 32),
        'token_secret' => str_repeat('t', 32),
    ],
]);
cc_test($presenceConfig['heartbeat_seconds'] === 60, 'heartbeat interval is configured');
cc_test($presenceConfig['online_ttl_seconds'] === 180, 'online TTL is configured');
cc_test($presenceConfig['session_retention_days'] === 180, 'session retention is configured');
cc_test($presenceConfig['device_retention_days'] === 180, 'device retention is configured');

$validPayload = [
    'contractVersion' => 1,
    'sessionId' => '11111111-1111-4111-8111-111111111111',
    'installationId' => 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'accessCode' => 'HULK-ABCD-EFGH-JKMN-PQRS',
    'iptvUsername' => 'synthetic-user',
    'iptvPassword' => 'synthetic-password',
    'host' => 'https://iptv.example.test:8443',
    'authenticatedAtEpochMs' => 1789639200000,
    'device' => [
        'platformClass' => 'TV',
        'manufacturer' => 'Synthetic',
        'model' => 'Test Model',
        'androidRelease' => '15',
        'sdkInt' => 35,
    ],
    'app' => ['versionName' => '0.9.3.20', 'versionCode' => 64],
];
$validated = cc_presence_validate_start($validPayload);
cc_test($validated['session_id'] === $validPayload['sessionId'], 'start accepts the current UUID session identifier');
cc_test($validated['installation_id'] === $validPayload['installationId'], 'start accepts the stable UUID installation identifier');
cc_test($validated['iptv_password'] === 'synthetic-password', 'credential snapshot validation preserves the exact synthetic password');

$tooLong = $validPayload;
$tooLong['iptvUsername'] = str_repeat('u', 256);
try {
    cc_presence_validate_start($tooLong);
    cc_test(false, 'oversized fields are rejected');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'INVALID_REQUEST', 'oversized fields are rejected');
}
$unknownField = $validPayload;
$unknownField['unexpected'] = true;
try {
    cc_presence_validate_start($unknownField);
    cc_test(false, 'unsupported request fields are rejected');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'INVALID_REQUEST', 'unsupported request fields are rejected');
}

$fresh = ['last_seen_at' => '2026-09-17 12:00:00.000000', 'ended_at' => null];
$stale = ['last_seen_at' => '2026-09-17 11:56:59.000000', 'ended_at' => null];
$ended = ['last_seen_at' => '2026-09-17 12:00:00.000000', 'ended_at' => '2026-09-17 12:00:01.000000'];
$onlineNow = new DateTimeImmutable('2026-09-17 12:03:00 UTC');
cc_test(cc_presence_is_online($fresh, $onlineNow, 180), 'fresh non-ended session is Online at the TTL boundary');
cc_test(!cc_presence_is_online($stale, $onlineNow, 180), 'stale session is Offline after TTL');
cc_test(!cc_presence_is_online($ended, $onlineNow, 180), 'explicitly ended session is Offline');

if (!in_array('sqlite', PDO::getAvailableDrivers(), true)) {
    fwrite(STDOUT, "SKIP: PDO SQLite is unavailable; Presence database integration checks were not run.\n");
    return;
}

$control = new PDO('sqlite::memory:');
$reseller = new PDO('sqlite::memory:');
foreach ([$control, $reseller] as $database) {
    $database->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $database->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
}
$control->exec('PRAGMA foreign_keys = ON');
$control->exec(
    'CREATE TABLE cc_devices ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, installation_id TEXT NOT NULL UNIQUE, platform_class TEXT NOT NULL, '
    . 'manufacturer TEXT NOT NULL, model TEXT NOT NULL, android_release TEXT NOT NULL, android_sdk_int INTEGER NOT NULL, '
    . 'first_seen_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, latest_app_version_name TEXT NOT NULL, '
    . 'latest_app_version_code INTEGER NOT NULL)'
);
$control->exec(
    'CREATE TABLE cc_app_sessions ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL UNIQUE, device_id INTEGER NOT NULL, '
    . 'reseller_id INTEGER NOT NULL, access_code_snapshot TEXT NOT NULL, iptv_username TEXT NOT NULL, '
    . 'iptv_password TEXT NOT NULL, host_snapshot TEXT NOT NULL, authenticated_at_client_ms INTEGER NOT NULL, '
    . 'app_version_name TEXT NOT NULL, app_version_code INTEGER NOT NULL, platform_class TEXT NOT NULL, '
    . 'device_manufacturer TEXT NOT NULL, device_model TEXT NOT NULL, android_release TEXT NOT NULL, '
    . 'android_sdk_int INTEGER NOT NULL, started_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, ended_at TEXT NULL, '
    . 'end_reason TEXT NULL, presence_token_nonce TEXT NOT NULL, presence_token_hash TEXT NOT NULL UNIQUE, '
    . 'FOREIGN KEY(device_id) REFERENCES cc_devices(id))'
);
$control->exec(
    'CREATE TABLE cc_api_rate_limits ('
    . 'scope TEXT NOT NULL, client_hash TEXT NOT NULL, window_started_at TEXT NOT NULL, attempts INTEGER NOT NULL, '
    . 'PRIMARY KEY(scope, client_hash))'
);
$reseller->exec(
    'CREATE TABLE resellers ('
    . 'reseller_id INTEGER PRIMARY KEY, access_code TEXT NOT NULL, access_code_hash TEXT NOT NULL UNIQUE, '
    . 'host TEXT NOT NULL, status TEXT NOT NULL)'
);
$insertReseller = $reseller->prepare(
    'INSERT INTO resellers (reseller_id, access_code, access_code_hash, host, status) '
    . 'VALUES (7, :code, :hash, :host, :status)'
);
$insertReseller->execute([
    'code' => $validPayload['accessCode'],
    'hash' => hash('sha256', $validPayload['accessCode']),
    'host' => $validPayload['host'],
    'status' => 'active',
]);

$startTime = new DateTimeImmutable('2026-09-17 12:00:00 UTC');
$first = cc_presence_start(
    $control,
    $reseller,
    $validPayload,
    $presenceConfig,
    '192.0.2.10',
    $startTime,
    static fn (): string => str_repeat("\x01", 32)
);
cc_test($first['sessionId'] === $validPayload['sessionId'], 'successful start returns the client session ID');
cc_test($first['heartbeatSeconds'] === 60 && $first['onlineTtlSeconds'] === 180, 'start returns configured timing');
cc_test((int) $control->query('SELECT COUNT(*) FROM cc_app_sessions')->fetchColumn() === 1, 'successful start persists one session');
cc_test((int) $control->query('SELECT COUNT(*) FROM cc_devices')->fetchColumn() === 1, 'successful start upserts one device');
$stored = $control->query('SELECT * FROM cc_app_sessions')->fetch();
cc_test(is_array($stored) && $stored['iptv_username'] === 'synthetic-user', 'synthetic IPTV username snapshot is persisted');
cc_test(is_array($stored) && $stored['iptv_password'] === 'synthetic-password', 'synthetic IPTV password snapshot is persisted');
cc_test(is_array($stored) && $stored['presence_token_hash'] === hash('sha256', $first['presenceToken']), 'only the bearer token hash is persisted');
cc_test(is_array($stored) && $stored['presence_token_hash'] !== $first['presenceToken'], 'plain bearer token is not persisted');

$retry = cc_presence_start(
    $control,
    $reseller,
    $validPayload,
    $presenceConfig,
    '192.0.2.10',
    $startTime->modify('+1 second'),
    static fn (): string => str_repeat("\x02", 32)
);
cc_test($retry['server_session_id'] === $first['server_session_id'], 'retried start returns the same logical session');
cc_test((int) $control->query('SELECT COUNT(*) FROM cc_app_sessions')->fetchColumn() === 1, 'retried start does not duplicate sessions');
cc_test($retry['presenceToken'] === $first['presenceToken'], 'retried start returns the same opaque token');
$conflictPayload = $validPayload;
$conflictPayload['iptvPassword'] = 'different-synthetic-password';
try {
    cc_presence_start(
        $control,
        $reseller,
        $conflictPayload,
        $presenceConfig,
        '192.0.2.10',
        $startTime->modify('+2 seconds')
    );
    cc_test(false, 'a reused session ID with different immutable data is rejected');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'SESSION_CONFLICT', 'a reused session ID with different immutable data is rejected');
}
cc_presence_heartbeat(
    $control,
    ['sessionId' => $validPayload['sessionId']],
    $first['presenceToken'],
    $presenceConfig,
    '192.0.2.10',
    $startTime->modify('+30 seconds')
);
cc_test(true, 'the idempotent start token remains authorized');

$heartbeat = cc_presence_heartbeat(
    $control,
    ['sessionId' => $validPayload['sessionId']],
    $retry['presenceToken'],
    $presenceConfig,
    '192.0.2.10',
    $startTime->modify('+60 seconds')
);
cc_test($heartbeat['serverTimeEpochSeconds'] === $startTime->getTimestamp() + 60, 'heartbeat uses server time');
cc_test(
    str_starts_with((string) $control->query('SELECT last_seen_at FROM cc_app_sessions')->fetchColumn(), '2026-09-17 12:01:00'),
    'heartbeat updates the authorized session timestamp'
);
$loadConfig = $presenceConfig;
$loadConfig['rate_limits']['heartbeat'] = 1000;
$loadStartedAt = microtime(true);
for ($index = 0; $index < 200; $index++) {
    cc_presence_heartbeat(
        $control,
        ['sessionId' => $validPayload['sessionId']],
        $retry['presenceToken'],
        $loadConfig,
        '192.0.2.10',
        $startTime->modify('+' . (61 + $index) . ' seconds')
    );
}
$loadElapsed = microtime(true) - $loadStartedAt;
cc_test($loadElapsed < 30.0, 'bounded local load check completes 200 indexed heartbeats within 30 seconds');
cc_test((int) $control->query('SELECT COUNT(*) FROM cc_app_sessions')->fetchColumn() === 1, 'bounded heartbeat load does not duplicate sessions');
try {
    cc_presence_heartbeat(
        $control,
        ['sessionId' => '22222222-2222-4222-8222-222222222222'],
        $retry['presenceToken'],
        $presenceConfig,
        '192.0.2.10',
        $startTime
    );
    cc_test(false, 'cross-session bearer tokens are rejected');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'UNAUTHORIZED', 'cross-session bearer tokens are rejected');
}

$end = cc_presence_end(
    $control,
    ['sessionId' => $validPayload['sessionId'], 'reason' => 'LOGOUT'],
    $retry['presenceToken'],
    $presenceConfig,
    '192.0.2.10',
    $startTime->modify('+400 seconds')
);
$endRetry = cc_presence_end(
    $control,
    ['sessionId' => $validPayload['sessionId'], 'reason' => 'APP_SHUTDOWN'],
    $retry['presenceToken'],
    $presenceConfig,
    '192.0.2.10',
    $startTime->modify('+401 seconds')
);
cc_test($end['ended'] === true && $endRetry['ended'] === true, 'end is idempotent');
cc_test($endRetry['reason'] === 'LOGOUT', 'idempotent end preserves the original reason');
try {
    cc_presence_heartbeat(
        $control,
        ['sessionId' => $validPayload['sessionId']],
        $retry['presenceToken'],
        $presenceConfig,
        '192.0.2.10',
        $startTime->modify('+480 seconds')
    );
    cc_test(false, 'heartbeat cannot reactivate an ended session');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'SESSION_ENDED', 'heartbeat cannot reactivate an ended session');
}

$exactCodePayload = $validPayload;
$exactCodePayload['sessionId'] = '66666666-6666-4666-8666-666666666666';
$exactCodePayload['accessCode'] = 'hulk abcd efgh jkmn pqrs';
cc_presence_start(
    $control,
    $reseller,
    $exactCodePayload,
    $presenceConfig,
    '192.0.2.13',
    $startTime->modify('+9 minutes'),
    static fn (): string => str_repeat("\x06", 32)
);
$exactSnapshot = $control->query(
    "SELECT access_code_snapshot FROM cc_app_sessions "
    . "WHERE session_id = '66666666-6666-4666-8666-666666666666'"
)->fetchColumn();
cc_test($exactSnapshot === $exactCodePayload['accessCode'], 'the exact supplied access-code representation is snapshotted immutably');

$inactivePayload = $validPayload;
$inactivePayload['sessionId'] = '33333333-3333-4333-8333-333333333333';
$reseller->exec("UPDATE resellers SET status = 'inactive'");
try {
    cc_presence_start($control, $reseller, $inactivePayload, $presenceConfig, '192.0.2.11', $startTime);
    cc_test(false, 'inactive reseller is rejected');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'RESELLER_INACTIVE', 'inactive reseller is rejected');
}
$reseller->exec("UPDATE resellers SET status = 'active'");
$wrongHostPayload = $validPayload;
$wrongHostPayload['sessionId'] = '55555555-5555-4555-8555-555555555555';
$wrongHostPayload['host'] = 'https://other.example.test';
try {
    cc_presence_start($control, $reseller, $wrongHostPayload, $presenceConfig, '192.0.2.11', $startTime);
    cc_test(false, 'a host outside current reseller truth is rejected');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'HOST_MISMATCH', 'a host outside current reseller truth is rejected');
}
$rotatedCode = 'HULK-TUVW-XYZA-2345-6789';
$rotate = $reseller->prepare('UPDATE resellers SET access_code = :code, access_code_hash = :hash');
$rotate->execute(['code' => $rotatedCode, 'hash' => hash('sha256', $rotatedCode)]);
$rotatedPayload = $validPayload;
$rotatedPayload['sessionId'] = '44444444-4444-4444-8444-444444444444';
try {
    cc_presence_start($control, $reseller, $rotatedPayload, $presenceConfig, '192.0.2.12', $startTime);
    cc_test(false, 'rotated-out access code is rejected for a new session');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'INVALID_ACCESS_CODE', 'rotated-out access code is rejected for a new session');
}
$currentPayload = $rotatedPayload;
$currentPayload['accessCode'] = $rotatedCode;
$currentPayload['app']['versionName'] = '0.9.3.21';
$currentPayload['app']['versionCode'] = 65;
$current = cc_presence_start(
    $control,
    $reseller,
    $currentPayload,
    $presenceConfig,
    '192.0.2.12',
    $startTime->modify('+5 minutes'),
    static fn (): string => str_repeat("\x03", 32)
);
cc_test((int) $control->query('SELECT COUNT(*) FROM cc_devices')->fetchColumn() === 1, 'new sessions reuse the stable installation device');
cc_test((int) $control->query('SELECT latest_app_version_code FROM cc_devices')->fetchColumn() === 65, 'device upsert records the latest app version');
cc_test($current['sessionId'] === $currentPayload['sessionId'], 'rotated current code starts a new logical session');

$limitedConfig = $presenceConfig;
$limitedConfig['rate_limits']['start'] = 2;
cc_presence_rate_limit($control, 'start', '198.51.100.1', $limitedConfig, $startTime);
cc_presence_rate_limit($control, 'start', '198.51.100.1', $limitedConfig, $startTime);
try {
    cc_presence_rate_limit($control, 'start', '198.51.100.1', $limitedConfig, $startTime);
    cc_test(false, 'persistent rate limit rejects excess requests');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'RATE_LIMITED', 'persistent rate limit rejects excess requests');
}
$rateRow = $control->query("SELECT client_hash FROM cc_api_rate_limits WHERE scope = 'start' LIMIT 1")->fetchColumn();
cc_test(is_string($rateRow) && strlen($rateRow) === 64, 'rate limiter persists only a keyed client hash');
cc_test($rateRow !== '198.51.100.1', 'rate limiter never persists the raw client address');

$old = '2026-01-01 00:00:00.000000';
$control->exec("UPDATE cc_app_sessions SET ended_at = '$old', last_seen_at = '$old' WHERE session_id = '11111111-1111-4111-8111-111111111111'");
$control->exec("UPDATE cc_api_rate_limits SET window_started_at = '$old' WHERE scope = 'start'");
$control->exec(
    "INSERT INTO cc_devices (installation_id, platform_class, manufacturer, model, android_release, android_sdk_int, "
    . "first_seen_at, last_seen_at, latest_app_version_name, latest_app_version_code) VALUES "
    . "('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'OTHER', 'Synthetic', 'Old', '14', 34, '$old', '$old', '0.9.3.20', 64)"
);
$cleanup = cc_presence_cleanup($control, $presenceConfig, new DateTimeImmutable('2026-09-17 12:00:00 UTC'));
cc_test($cleanup['sessions'] === 1, 'cleanup removes ended sessions beyond retention');
cc_test($cleanup['devices'] >= 1, 'cleanup removes unreferenced inactive devices beyond retention');
cc_test($cleanup['rate_limits'] >= 1, 'cleanup removes expired rate-limit windows');
cc_test((int) $control->query('SELECT COUNT(*) FROM cc_devices')->fetchColumn() === 1, 'cleanup retains a device still referenced by session history');
