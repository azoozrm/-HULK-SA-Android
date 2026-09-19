<?php

declare(strict_types=1);

$phaseSevenRoot = dirname(__DIR__);
require_once $phaseSevenRoot . '/lib/phase7-read-model.php';
require_once $phaseSevenRoot . '/lib/diagnostics-http.php';

$phaseSevenConfigRoot = [
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
    'diagnostics' => [
        'retention_days' => 30,
        'cleanup_batch_size' => 2,
        'rate_limit_window_seconds' => 60,
        'rate_limit_attempts' => 30,
    ],
    'host_health' => [
        'dns_timeout_ms' => 2000,
        'connect_timeout_ms' => 3000,
        'request_timeout_ms' => 5000,
        'max_runtime_seconds' => 45,
        'max_hosts_per_run' => 20,
        'candidate_limit' => 500,
        'retention_days' => 90,
        'cleanup_batch_size' => 2,
        'user_agent' => 'HULK-SA-Control-Center-Host-Health/1.0',
    ],
];

$diagnosticsConfig = cc_diagnostics_config($phaseSevenConfigRoot);
$hostHealthConfig = cc_host_health_config($phaseSevenConfigRoot);
cc_test($diagnosticsConfig['retention_days'] === 30, 'Diagnostics retention is explicitly bounded to 30 days');
cc_test($hostHealthConfig['request_timeout_ms'] === 5000, 'Host Health request timeout is explicit');
cc_test($hostHealthConfig['dns_timeout_ms'] === 2000, 'Host Health DNS wall-clock timeout is explicit');
cc_test($hostHealthConfig['max_runtime_seconds'] === 45, 'Host Health invocation runtime is explicit');
cc_test($hostHealthConfig['max_hosts_per_run'] === 20, 'Host Health work per invocation is explicit');
cc_test(cc_host_health_public_ip('93.184.216.34'), 'Host Health accepts a publicly routable probe address');
cc_test(!cc_host_health_public_ip('127.0.0.1'), 'Host Health blocks loopback probe destinations');
cc_test(!cc_host_health_public_ip('10.0.0.1'), 'Host Health blocks private probe destinations');
cc_test(!cc_host_health_public_ip('169.254.169.254'), 'Host Health blocks link-local metadata destinations');
try {
    cc_host_health_config(array_replace_recursive($phaseSevenConfigRoot, [
        'host_health' => ['request_timeout_ms' => 5001],
    ]));
    cc_test(false, 'Host Health cannot be configured above the approved request timeout');
} catch (RuntimeException $exception) {
    cc_test(true, 'Host Health rejects a request timeout above the approved bound');
}
try {
    cc_diagnostics_config(array_replace_recursive($phaseSevenConfigRoot, [
        'diagnostics' => ['retention_days' => 31],
    ]));
    cc_test(false, 'Diagnostics cannot be configured above the 30-day retention policy');
} catch (RuntimeException $exception) {
    cc_test(true, 'Diagnostics rejects retention above 30 days');
}
try {
    cc_diagnostics_decode_body(str_repeat('x', CC_DIAGNOSTICS_MAX_BODY_BYTES + 1));
    cc_test(false, 'oversized Diagnostics bodies are rejected');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'BODY_TOO_LARGE' && $exception->httpStatus === 413, 'oversized Diagnostics bodies fail safely');
}

cc_test(cc_phase7_health_state([]) === 'NOT_CHECKED', 'missing Host Health data remains not checked');
cc_test(
    cc_phase7_health_state([['probe_result' => 'TIMEOUT']]) === 'TRANSIENT_FAILURE',
    'one transport failure remains transient'
);
cc_test(
    cc_phase7_health_state([['probe_result' => 'TIMEOUT'], ['probe_result' => 'CONNECT_FAILURE']]) === 'UNREACHABLE',
    'two consecutive transport failures establish unreachable state'
);
cc_test(
    cc_phase7_health_state([['probe_result' => 'HEALTHY'], ['probe_result' => 'TIMEOUT']]) === 'HEALTHY',
    'a later successful check recovers from a transient failure'
);
cc_test(
    cc_phase7_health_state([['probe_result' => 'HTTP_ERROR']]) === 'DEGRADED',
    'an HTTP response outside the success range is distinct from unreachable'
);

$definitions = cc_phase7_metric_definitions();
foreach ([
    'host_health_summary', 'host_health_history', 'application_failures', 'failures_by_version',
    'failures_by_device', 'failures_by_host', 'session_trend', 'version_adoption_trend',
    'current_adoption_distribution',
] as $definition) {
    cc_test(isset($definitions[$definition]) && $definitions[$definition] !== '', $definition . ' has a stored-fact definition');
}

if (!in_array('sqlite', PDO::getAvailableDrivers(), true)) {
    fwrite(STDOUT, "SKIP: PDO SQLite is unavailable; Phase 7 database integration checks were not run.\n");
    return;
}

$control = new PDO('sqlite::memory:');
$reseller = new PDO('sqlite::memory:');
foreach ([$control, $reseller] as $database) {
    $database->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $database->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
}

$reseller->exec(
    'CREATE TABLE resellers ('
    . 'reseller_id INTEGER PRIMARY KEY, reseller_name TEXT NOT NULL, host TEXT NULL, status TEXT NOT NULL)'
);
$reseller->exec(
    "INSERT INTO resellers VALUES "
    . "(7, 'Synthetic Active', 'https://first.example.test', 'active'), "
    . "(8, 'Synthetic Invalid', 'file:///tmp/not-network', 'active'), "
    . "(9, 'Synthetic Inactive', 'https://inactive.example.test', 'inactive')"
);
$control->exec(
    'CREATE TABLE cc_devices ('
    . 'id INTEGER PRIMARY KEY, installation_id TEXT NOT NULL UNIQUE, platform_class TEXT NOT NULL, '
    . 'manufacturer TEXT NOT NULL, model TEXT NOT NULL, android_release TEXT NOT NULL, android_sdk_int INTEGER NOT NULL, '
    . 'first_seen_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, latest_app_version_name TEXT NOT NULL, '
    . 'latest_app_version_code INTEGER NOT NULL)'
);
$control->exec(
    'CREATE TABLE cc_app_sessions ('
    . 'id INTEGER PRIMARY KEY, session_id TEXT NOT NULL UNIQUE, device_id INTEGER NOT NULL, reseller_id INTEGER NOT NULL, '
    . 'host_snapshot TEXT NOT NULL, app_version_name TEXT NOT NULL, app_version_code INTEGER NOT NULL, '
    . 'platform_class TEXT NOT NULL, device_manufacturer TEXT NOT NULL, device_model TEXT NOT NULL, '
    . 'android_release TEXT NOT NULL, android_sdk_int INTEGER NOT NULL, started_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, '
    . 'ended_at TEXT NULL, end_reason TEXT NULL, presence_token_hash TEXT NOT NULL)'
);
$control->exec(
    'CREATE TABLE cc_api_rate_limits ('
    . 'scope TEXT NOT NULL, client_hash TEXT NOT NULL, window_started_at TEXT NOT NULL, attempts INTEGER NOT NULL, '
    . 'PRIMARY KEY (scope, client_hash))'
);
$control->exec(
    'CREATE TABLE cc_host_health_checks ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, reseller_id INTEGER NOT NULL, host_fingerprint TEXT NOT NULL, '
    . 'checked_at TEXT NOT NULL, probe_result TEXT NOT NULL, dns_ok INTEGER NOT NULL, tcp_ok INTEGER NOT NULL, '
    . 'http_status INTEGER NULL, latency_ms INTEGER NOT NULL, failure_code TEXT NULL)'
);
$control->exec(
    'CREATE TABLE cc_diagnostic_events ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, event_id TEXT NOT NULL UNIQUE, session_id INTEGER NOT NULL, '
    . 'device_id INTEGER NOT NULL, host_fingerprint TEXT NOT NULL, event_type TEXT NOT NULL, error_code TEXT NOT NULL, '
    . 'app_version_name TEXT NOT NULL, app_version_code INTEGER NOT NULL, platform_class TEXT NOT NULL, '
    . 'device_manufacturer TEXT NOT NULL, device_model TEXT NOT NULL, android_release TEXT NOT NULL, '
    . 'android_sdk_int INTEGER NOT NULL, occurred_at TEXT NOT NULL, received_at TEXT NOT NULL)'
);

$presenceToken = str_repeat('A', 43);
$control->prepare(
    'INSERT INTO cc_devices VALUES '
    . '(1, :installation_id, :platform_class, :manufacturer, :model, :android_release, :android_sdk_int, '
    . ':first_seen_at, :last_seen_at, :version_name, :version_code)'
)->execute([
    'installation_id' => '11111111-1111-4111-8111-111111111111',
    'platform_class' => 'TV',
    'manufacturer' => 'Synthetic',
    'model' => 'Test Display',
    'android_release' => '15',
    'android_sdk_int' => 35,
    'first_seen_at' => '2026-09-19 10:00:00.000000',
    'last_seen_at' => '2026-09-19 11:59:00.000000',
    'version_name' => '0.9.3.21',
    'version_code' => 65,
]);
$control->prepare(
    'INSERT INTO cc_app_sessions VALUES '
    . '(1, :session_id, 1, 7, :host, :version_name, :version_code, :platform, :manufacturer, :model, '
    . ':android_release, :android_sdk_int, :started_at, :last_seen_at, NULL, NULL, :token_hash)'
)->execute([
    'session_id' => '22222222-2222-4222-8222-222222222222',
    'host' => 'https://first.example.test',
    'version_name' => '0.9.3.21',
    'version_code' => 65,
    'platform' => 'TV',
    'manufacturer' => 'Synthetic',
    'model' => 'Test Display',
    'android_release' => '15',
    'android_sdk_int' => 35,
    'started_at' => '2026-09-19 10:00:00.000000',
    'last_seen_at' => '2026-09-19 11:59:00.000000',
    'token_hash' => hash('sha256', $presenceToken),
]);

$probeCalls = 0;
$probe = static function (string $host, array $config) use (&$probeCalls): array {
    $probeCalls++;
    cc_test(str_starts_with($host, 'https://'), 'Host Health probes only a normalized HTTP(S) current host');
    cc_test($config['request_timeout_ms'] <= 5000, 'per-host request timeout stays bounded');
    return [
        'probe_result' => 'HEALTHY', 'dns_ok' => true, 'tcp_ok' => true,
        'http_status' => 200, 'latency_ms' => 12, 'failure_code' => null,
    ];
};
$healthNow = new DateTimeImmutable('2026-09-19 12:00:00 UTC');
$firstRun = cc_host_health_run($control, $reseller, $hostHealthConfig, $probe, $healthNow);
cc_test($firstRun['checked_count'] === 1 && $probeCalls === 1, 'only the current active contract-valid host is probed');
$firstFingerprint = (string) $control->query('SELECT host_fingerprint FROM cc_host_health_checks LIMIT 1')->fetchColumn();
cc_test($firstFingerprint === hash('sha256', 'https://first.example.test'), 'Host Health stores the exact normalized-host fingerprint');

$reseller->exec("UPDATE resellers SET host = 'https://rotated.example.test' WHERE reseller_id = 7");
cc_host_health_run($control, $reseller, $hostHealthConfig, $probe, $healthNow->modify('+5 minutes'));
$fingerprints = $control->query(
    'SELECT DISTINCT host_fingerprint FROM cc_host_health_checks WHERE reseller_id = 7 ORDER BY host_fingerprint'
)->fetchAll(PDO::FETCH_COLUMN);
cc_test(count($fingerprints) === 2 && in_array($firstFingerprint, $fingerprints, true), 'host rotation preserves historical health fingerprints');
cc_test(
    (int) $control->query('SELECT COUNT(*) FROM cc_host_health_checks WHERE host_fingerprint LIKE \'%example.test%\'')->fetchColumn() === 0,
    'Host Health storage contains fingerprints rather than raw hosts'
);
$expiredRuntime = cc_host_health_run(
    $control,
    $reseller,
    $hostHealthConfig,
    $probe,
    $healthNow,
    microtime(true) - 46
);
cc_test($expiredRuntime['checked_count'] === 0, 'expired invocation runtime prevents additional network work');

$boundedControl = new PDO('sqlite::memory:');
$boundedReseller = new PDO('sqlite::memory:');
foreach ([$boundedControl, $boundedReseller] as $database) {
    $database->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $database->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
}
$boundedControl->exec(
    'CREATE TABLE cc_host_health_checks ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, reseller_id INTEGER NOT NULL, host_fingerprint TEXT NOT NULL, '
    . 'checked_at TEXT NOT NULL, probe_result TEXT NOT NULL, dns_ok INTEGER NOT NULL, tcp_ok INTEGER NOT NULL, '
    . 'http_status INTEGER NULL, latency_ms INTEGER NOT NULL, failure_code TEXT NULL)'
);
$boundedReseller->exec(
    'CREATE TABLE resellers ('
    . 'reseller_id INTEGER PRIMARY KEY, reseller_name TEXT NOT NULL, host TEXT NULL, status TEXT NOT NULL)'
);
for ($target = 1; $target <= 25; $target++) {
    $boundedReseller->prepare('INSERT INTO resellers VALUES (:id, :name, :host, \'active\')')->execute([
        'id' => $target,
        'name' => 'Synthetic ' . $target,
        'host' => 'https://host-' . $target . '.example.test',
    ]);
}
$boundedProbeCalls = 0;
$boundedProbe = static function () use (&$boundedProbeCalls): array {
    $boundedProbeCalls++;
    return [
        'probe_result' => 'CONNECT_FAILURE', 'dns_ok' => true, 'tcp_ok' => false,
        'http_status' => null, 'latency_ms' => 5000, 'failure_code' => 'CONNECT_FAILURE',
    ];
};
$boundedRun = cc_host_health_run(
    $boundedControl,
    $boundedReseller,
    $hostHealthConfig,
    $boundedProbe,
    $healthNow
);
cc_test(
    $boundedRun['checked_count'] === 20 && $boundedProbeCalls === 20 && $boundedRun['skipped_for_runtime'] === 0,
    'Host Health enforces the configured per-invocation host work bound'
);
for ($target = 26; $target <= 501; $target++) {
    $boundedReseller->prepare('INSERT INTO resellers VALUES (:id, :name, :host, \'active\')')->execute([
        'id' => $target,
        'name' => 'Synthetic ' . $target,
        'host' => 'https://host-' . $target . '.example.test',
    ]);
}
$firstCandidateWindow = cc_host_health_candidate_window(
    $boundedReseller,
    500,
    new DateTimeImmutable('@0')
);
$rotatedCandidateWindow = cc_host_health_candidate_window(
    $boundedReseller,
    500,
    new DateTimeImmutable('@150000')
);
$firstCandidateIds = array_column($firstCandidateWindow['targets'], 'reseller_id');
$rotatedCandidateIds = array_column($rotatedCandidateWindow['targets'], 'reseller_id');
cc_test(
    !in_array(501, $firstCandidateIds, true) && in_array(501, $rotatedCandidateIds, true),
    'bounded Host Health candidate windows rotate so later reseller IDs are not starved'
);
$partialHealthPage = cc_phase7_host_health_page($boundedControl, $boundedReseller);
cc_test(
    $partialHealthPage['coverage_complete'] === false
        && $partialHealthPage['eligible_row_count'] === 501
        && count($partialHealthPage['targets']) === 500,
    'Host Health read model labels bounded coverage as partial instead of silently omitting targets'
);

$diagnosticPayload = [
    'contractVersion' => 1,
    'eventId' => '33333333-3333-4333-8333-333333333333',
    'sessionId' => '22222222-2222-4222-8222-222222222222',
    'eventType' => 'PLAYBACK_START_FAILURE',
    'errorCode' => 'NETWORK_TIMEOUT',
    'occurredAtEpochMs' => 1790000000000,
];
$diagnosticNow = new DateTimeImmutable('@1790000100');
$accepted = cc_diagnostics_ingest(
    $control,
    $diagnosticPayload,
    $presenceToken,
    $diagnosticsConfig,
    '192.0.2.10',
    $diagnosticNow
);
cc_test($accepted['accepted'] === true, 'a typed event authenticated by its Presence session is accepted');
$stored = $control->query('SELECT * FROM cc_diagnostic_events WHERE id = 1')->fetch();
cc_test(is_array($stored) && $stored['app_version_name'] === '0.9.3.21', 'Diagnostics derives app facts from the immutable session snapshot');
cc_test($stored['device_model'] === 'Test Display' && $stored['platform_class'] === 'TV', 'Diagnostics derives device facts from the session authority');
cc_test($stored['host_fingerprint'] === hash('sha256', 'https://first.example.test'), 'Diagnostics relates the event to a host fingerprint without copying the host');
cc_diagnostics_ingest($control, $diagnosticPayload, $presenceToken, $diagnosticsConfig, '192.0.2.10', $diagnosticNow);
cc_test((int) $control->query('SELECT COUNT(*) FROM cc_diagnostic_events')->fetchColumn() === 1, 'an identical diagnostic retry is idempotent');

$invalidCases = [];
$invalidCases['unknown event type'] = array_replace($diagnosticPayload, [
    'eventId' => '44444444-4444-4444-8444-444444444444', 'eventType' => 'RAW_LOG',
]);
$invalidCases['unknown error code'] = array_replace($diagnosticPayload, [
    'eventId' => '55555555-5555-4555-8555-555555555555', 'errorCode' => 'PASSWORD_COPY',
]);
$invalidCases['incompatible typed pair'] = array_replace($diagnosticPayload, [
    'eventId' => '66666666-6666-4666-8666-666666666666', 'errorCode' => 'AUTH_REJECTED',
]);
$invalidCases['unexpected credential-shaped field'] = $diagnosticPayload + ['accessCode' => 'synthetic-not-accepted'];
foreach ($invalidCases as $label => $invalidPayload) {
    try {
        cc_diagnostics_validate($invalidPayload, $diagnosticsConfig, $diagnosticNow);
        cc_test(false, $label . ' is rejected');
    } catch (CcPresenceException $exception) {
        cc_test($exception->httpStatus === 422, $label . ' fails safely');
    }
}
try {
    cc_diagnostics_ingest($control, array_replace($diagnosticPayload, [
        'eventId' => '77777777-7777-4777-8777-777777777777',
    ]), str_repeat('B', 43), $diagnosticsConfig, '192.0.2.10', $diagnosticNow);
    cc_test(false, 'a diagnostic with the wrong Presence token is rejected');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'UNAUTHORIZED', 'Diagnostics fails closed on the wrong Presence token');
}
$control->exec(
    "UPDATE cc_app_sessions SET last_seen_at = '2026-07-01 00:00:00.000000', "
    . "ended_at = '2026-07-01 00:00:01.000000' WHERE id = 1"
);
try {
    cc_diagnostics_ingest($control, array_replace($diagnosticPayload, [
        'eventId' => '99999999-9999-4999-8999-999999999999',
    ]), $presenceToken, $diagnosticsConfig, '192.0.2.10', $diagnosticNow);
    cc_test(false, 'an event cannot attach to a Presence session outside Diagnostics retention');
} catch (CcPresenceException $exception) {
    cc_test($exception->apiCode === 'SESSION_STALE', 'stale Presence sessions fail Diagnostics closed');
}
$control->exec(
    "UPDATE cc_app_sessions SET last_seen_at = '2026-09-19 11:59:00.000000', ended_at = NULL WHERE id = 1"
);

$control->exec(
    "INSERT INTO cc_diagnostic_events "
    . "(event_id, session_id, device_id, host_fingerprint, event_type, error_code, app_version_name, app_version_code, "
    . "platform_class, device_manufacturer, device_model, android_release, android_sdk_int, occurred_at, received_at) VALUES "
    . "('88888888-8888-4888-8888-888888888888', 1, 1, '" . str_repeat('f', 64) . "', "
    . "'UPDATE_FAILURE', 'NETWORK_TIMEOUT', '0.9.3.20', 64, 'TV', 'Synthetic', 'Old Display', '14', 34, "
    . "'2026-07-01 00:00:00.000000', '2026-07-01 00:00:00.000000')"
);
cc_test(cc_diagnostics_cleanup($control, $diagnosticsConfig, $diagnosticNow) === 1, 'Diagnostics cleanup deletes an expired bounded batch');
cc_test((int) $control->query('SELECT COUNT(*) FROM cc_diagnostic_events')->fetchColumn() === 1, 'Diagnostics cleanup preserves retained events');

$healthPage = cc_phase7_host_health_page($control, $reseller);
cc_test(count($healthPage['targets']) === 1, 'Host Health summary follows only current valid reseller targets');
cc_test($healthPage['summary']['HEALTHY'] === 1, 'Host Health summary is derived from the current fingerprint observations');
$diagnosticsPage = cc_phase7_diagnostics_page($control, $diagnosticNow, 30);
cc_test($diagnosticsPage['total_events'] === 1, 'Diagnostics read model reports stored retained events');
$analytics = cc_phase7_analytics_page($control, new DateTimeImmutable('2026-09-19 12:00:00 UTC'));
cc_test(count($analytics['session_trend']) === 1, 'session trend is derived from Presence session starts');
cc_test(count($analytics['current_adoption_distribution']) === 1, 'current adoption is derived from recent Presence device facts');

$queryPlans = [
    $control->query(
        "EXPLAIN QUERY PLAN SELECT event_type, error_code, COUNT(*) FROM cc_diagnostic_events "
        . "WHERE received_at >= '2026-08-20 00:00:00.000000' GROUP BY event_type, error_code"
    )->fetchAll(),
    $control->query(
        "EXPLAIN QUERY PLAN SELECT DATE(started_at), COUNT(*) FROM cc_app_sessions "
        . "WHERE started_at >= '2026-08-20 00:00:00.000000' GROUP BY DATE(started_at)"
    )->fetchAll(),
];
cc_test($queryPlans[0] !== [] && $queryPlans[1] !== [], 'Phase 7 aggregate query plans are executable without rollup tables');
$tables = $control->query("SELECT name FROM sqlite_master WHERE type = 'table'")->fetchAll(PDO::FETCH_COLUMN);
cc_test(
    count(array_filter($tables, static fn (string $table): bool => str_contains($table, 'rollup'))) === 0,
    'no unproven analytics rollup table is introduced'
);
