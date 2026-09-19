<?php

declare(strict_types=1);

$phaseSixRoot = dirname(__DIR__);
$phaseSixDomainPath = $phaseSixRoot . '/lib/presence-read-model.php';
cc_test(is_file($phaseSixDomainPath), 'Phase 6 Presence read model exists');
require_once $phaseSixDomainPath;

cc_test(function_exists('cc_presence_session_page'), 'Phase 6 session read model is available');
cc_test(function_exists('cc_presence_device_page'), 'Phase 6 device read model is available');
cc_test(function_exists('cc_presence_dashboard_snapshot'), 'Phase 6 Dashboard read model is available');
cc_test(function_exists('cc_presence_usage_for_resellers'), 'Phase 6 reseller usage read model is available');

if (!in_array('sqlite', PDO::getAvailableDrivers(), true)) {
    fwrite(STDOUT, "SKIP: PDO SQLite is unavailable; Phase 6 read-model integration checks were not run.\n");
    return;
}

$phaseSixControl = new PDO('sqlite::memory:');
$phaseSixReseller = new PDO('sqlite::memory:');
foreach ([$phaseSixControl, $phaseSixReseller] as $database) {
    $database->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $database->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
}
$phaseSixControl->exec(
    'CREATE TABLE cc_devices ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, installation_id TEXT NOT NULL UNIQUE, platform_class TEXT NOT NULL, '
    . 'manufacturer TEXT NOT NULL, model TEXT NOT NULL, android_release TEXT NOT NULL, android_sdk_int INTEGER NOT NULL, '
    . 'first_seen_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, latest_app_version_name TEXT NOT NULL, '
    . 'latest_app_version_code INTEGER NOT NULL)'
);
$phaseSixControl->exec(
    'CREATE TABLE cc_app_sessions ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL UNIQUE, device_id INTEGER NOT NULL, '
    . 'reseller_id INTEGER NOT NULL, access_code_snapshot TEXT NOT NULL, iptv_username TEXT NOT NULL, '
    . 'iptv_password TEXT NOT NULL, host_snapshot TEXT NOT NULL, authenticated_at_client_ms INTEGER NOT NULL, '
    . 'app_version_name TEXT NOT NULL, app_version_code INTEGER NOT NULL, platform_class TEXT NOT NULL, '
    . 'device_manufacturer TEXT NOT NULL, device_model TEXT NOT NULL, android_release TEXT NOT NULL, '
    . 'android_sdk_int INTEGER NOT NULL, started_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, ended_at TEXT NULL, '
    . 'end_reason TEXT NULL)'
);
$phaseSixReseller->exec(
    'CREATE TABLE resellers ('
    . 'reseller_id INTEGER PRIMARY KEY, reseller_name TEXT NOT NULL, access_code TEXT NOT NULL, '
    . 'access_code_hash TEXT NOT NULL, host TEXT NOT NULL, status TEXT NOT NULL)'
);
$phaseSixReseller->exec(
    "INSERT INTO resellers VALUES "
    . "(7, 'Synthetic One', 'HULK-ABCD-EFGH-JKMN-PQRS', 'hash-one', 'https://current.invalid', 'active'), "
    . "(8, 'Synthetic Two', 'HULK-2345-6789-ABCD-EFGH', 'hash-two', 'https://second.invalid', 'inactive')"
);
$phaseSixControl->exec(
    "INSERT INTO cc_devices "
    . "(id, installation_id, platform_class, manufacturer, model, android_release, android_sdk_int, first_seen_at, last_seen_at, latest_app_version_name, latest_app_version_code) VALUES "
    . "(1, '11111111-1111-4111-8111-111111111111', 'TV', 'Synthetic', 'Living Room', '15', 35, '2026-09-19 08:00:00.000000', '2026-09-19 11:59:30.000000', '0.9.3.21', 65), "
    . "(2, '22222222-2222-4222-8222-222222222222', 'PHONE', 'Synthetic', 'Pocket', '14', 34, '2026-09-18 08:00:00.000000', '2026-09-19 11:30:00.000000', '0.9.3.20', 64)"
);
$phaseSixControl->exec(
    "INSERT INTO cc_app_sessions "
    . "(id, session_id, device_id, reseller_id, access_code_snapshot, iptv_username, iptv_password, host_snapshot, authenticated_at_client_ms, app_version_name, app_version_code, platform_class, device_manufacturer, device_model, android_release, android_sdk_int, started_at, last_seen_at, ended_at, end_reason) VALUES "
    . "(1, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 1, 7, 'HULK-TUVW-XYZA-2345-6789', 'synthetic-user-a', 'synthetic-password-a', 'https://old.invalid', 1790000000000, '0.9.3.21', 65, 'TV', 'Synthetic', 'Living Room', '15', 35, '2026-09-19 10:00:00.000000', '2026-09-19 11:59:30.000000', NULL, NULL), "
    . "(2, 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 2, 8, 'HULK-2345-6789-ABCD-EFGH', 'synthetic-user-b', 'synthetic-password-b', 'https://second.invalid', 1790000000000, '0.9.3.20', 64, 'PHONE', 'Synthetic', 'Pocket', '14', 34, '2026-09-19 09:00:00.000000', '2026-09-19 11:56:59.000000', NULL, NULL), "
    . "(3, 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 2, 7, 'hulk abcd efgh jkmn pqrs', 'synthetic-user-c', 'synthetic-password-c', 'https://current.invalid', 1790000000000, '0.9.3.20', 64, 'PHONE', 'Synthetic', 'Pocket', '14', 34, '2026-09-19 08:00:00.000000', '2026-09-19 11:59:45.000000', '2026-09-19 11:59:50.000000', 'LOGOUT')"
);

$phaseSixConfig = $presenceConfig;
$phaseSixNow = new DateTimeImmutable('2026-09-19 12:00:00 UTC');
$resellerLookups = 0;
$resellerLoader = static function (array $ids) use ($phaseSixReseller, &$resellerLookups): array {
    $resellerLookups++;
    return cc_presence_reseller_map($phaseSixReseller, $ids);
};

$livePage = cc_presence_session_page(
    $phaseSixControl,
    $resellerLoader,
    'live-users',
    [],
    $phaseSixConfig,
    $phaseSixNow
);
cc_test($livePage['total'] === 1, 'fresh non-ended session is the only default Live Users row');
cc_test($livePage['rows'][0]['online'] === true, 'fresh non-ended session is Online in the owner read model');
cc_test($livePage['rows'][0]['access_code_snapshot'] === 'HULK-TUVW-XYZA-2345-6789', 'historical access-code snapshot survives current code rotation');
cc_test($livePage['rows'][0]['host_snapshot'] === 'https://old.invalid', 'historical host snapshot survives current host rotation');
cc_test($livePage['rows'][0]['iptv_username'] === 'synthetic-user-a', 'historical IPTV username is preserved exactly');
cc_test($livePage['rows'][0]['iptv_password'] === 'synthetic-password-a', 'historical IPTV password is preserved exactly');
cc_test($resellerLookups === 1, 'one bounded reseller mapping resolves the Live Users page without N+1 lookups');
cc_test(($livePage['resellers'][7]['reseller_name'] ?? '') === 'Synthetic One', 'session reseller is mapped from current reseller authority');

$offlinePage = cc_presence_session_page(
    $phaseSixControl,
    $resellerLoader,
    'sessions',
    ['status' => 'offline', 'sort' => 'last_seen', 'direction' => 'desc'],
    $phaseSixConfig,
    $phaseSixNow
);
cc_test($offlinePage['total'] === 2, 'stale non-ended and explicitly ended sessions are Offline');
cc_test($offlinePage['rows'][0]['session_id'] === 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'session sorting is deterministic by selected field');
cc_test($offlinePage['rows'][0]['online'] === false && $offlinePage['rows'][1]['online'] === false, 'Offline rows never become Online through existence alone');

$filteredPage = cc_presence_session_page(
    $phaseSixControl,
    $resellerLoader,
    'sessions',
    [
        'q' => 'synthetic-user-a',
        'status' => 'online',
        'app_version' => '0.9.3.21',
        'reseller' => '7',
        'host' => 'old.invalid',
        'platform' => 'TV',
        'sort' => 'started',
        'direction' => 'asc',
    ],
    $phaseSixConfig,
    $phaseSixNow
);
cc_test($filteredPage['total'] === 1, 'server-side search and status/version/reseller/host/platform filters compose');
cc_test($filteredPage['filters']['page'] === 1, 'filtered pagination starts from a stable first page');

$partialPage = cc_presence_session_page(
    $phaseSixControl,
    static function (array $ids): array {
        throw new RuntimeException('synthetic reseller read failure');
    },
    'sessions',
    ['status' => 'all'],
    $phaseSixConfig,
    $phaseSixNow
);
cc_test($partialPage['total'] === 3 && $partialPage['resellers_available'] === false, 'reseller failure preserves control data as partial data');
cc_test($partialPage['resellers'] === [], 'reseller failure never fabricates reseller values');

$devicePage = cc_presence_device_page(
    $phaseSixControl,
    ['platform' => 'TV', 'app_version' => '0.9.3.21', 'sort' => 'last_seen', 'direction' => 'desc'],
    $phaseSixNow
);
cc_test($devicePage['total'] === 1, 'device inventory filters authoritative cc_devices metadata');
cc_test($devicePage['rows'][0]['installation_id'] === '11111111-1111-4111-8111-111111111111', 'installation ID is the stable device identity');
cc_test($devicePage['rows'][0]['model'] === 'Living Room' && (int) $devicePage['rows'][0]['android_sdk_int'] === 35, 'device inventory exposes authoritative metadata');
cc_test(!array_key_exists('online', $devicePage['rows'][0]), 'device existence is not presented as Online state');

$dashboardPresence = cc_presence_dashboard_snapshot(
    $phaseSixControl,
    $resellerLoader,
    $phaseSixConfig,
    $phaseSixNow,
    new DateTimeZone('Asia/Riyadh')
);
cc_test($dashboardPresence['online_now'] === 1, 'Dashboard Online Now uses server time and configured TTL');
cc_test($dashboardPresence['sessions_today'] === 3, 'Dashboard sessions today counts authoritative session starts');
cc_test($dashboardPresence['active_devices'] === 2 && $dashboardPresence['active_device_window_hours'] === 24, 'Dashboard active devices uses a labeled 24-hour window');
cc_test($dashboardPresence['users_today_available'] === false, 'users today remains unavailable without stable account identity');
cc_test($dashboardPresence['live_preview'][0]['session_id'] === 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Dashboard Live Users preview reuses the authoritative live query');
cc_test($dashboardPresence['version_distribution'][0]['app_version_code'] === 65, 'Dashboard version coverage is derived from recent Presence devices');

$resellerRows = [
    ['reseller_id' => 7, 'access_code' => 'HULK-ABCD-EFGH-JKMN-PQRS', 'host' => 'https://current.invalid'],
    ['reseller_id' => 8, 'access_code' => 'HULK-2345-6789-ABCD-EFGH', 'host' => 'https://second.invalid'],
];
$resellerUsage = cc_presence_usage_for_resellers(
    $phaseSixControl,
    $resellerRows,
    'resellers',
    $phaseSixNow,
    180
);
cc_test($resellerUsage[7]['sessions'] === 2 && $resellerUsage[7]['online'] === 1, 'reseller usage is derived from Presence by stable reseller relation');
$codeUsage = cc_presence_usage_for_resellers(
    $phaseSixControl,
    $resellerRows,
    'access-codes',
    $phaseSixNow,
    180
);
cc_test($codeUsage[7]['sessions'] === 1 && $codeUsage[7]['online'] === 0, 'current-code usage excludes rotated historical code snapshots');
$hostUsage = cc_presence_usage_for_resellers(
    $phaseSixControl,
    $resellerRows,
    'hosts',
    $phaseSixNow,
    180
);
cc_test($hostUsage[7]['sessions'] === 1 && $hostUsage[7]['online'] === 0, 'current-host usage excludes rotated historical host snapshots');

$stableInsert = $phaseSixControl->prepare(
    'INSERT INTO cc_app_sessions '
    . '(id, session_id, device_id, reseller_id, access_code_snapshot, iptv_username, iptv_password, '
    . 'host_snapshot, authenticated_at_client_ms, app_version_name, app_version_code, platform_class, '
    . 'device_manufacturer, device_model, android_release, android_sdk_int, started_at, last_seen_at, ended_at, end_reason) '
    . 'VALUES (:id, :session_id, 1, 7, :code, :username, :password, :host, 1790000000000, '
    . ':version_name, 65, :platform, :manufacturer, :model, :android_release, 35, :started_at, :last_seen_at, NULL, NULL)'
);
for ($id = 100; $id < 130; $id++) {
    $stableInsert->execute([
        'id' => $id,
        'session_id' => sprintf('dddddddd-dddd-4ddd-8ddd-%012d', $id),
        'code' => 'HULK-ABCD-EFGH-JKMN-PQRS',
        'username' => 'synthetic-page-user-' . $id,
        'password' => 'synthetic-page-password-' . $id,
        'host' => 'https://current.invalid',
        'version_name' => '0.9.3.21',
        'platform' => 'TV',
        'manufacturer' => 'Synthetic',
        'model' => 'Stable Page',
        'android_release' => '15',
        'started_at' => '2026-09-19 10:00:00.000000',
        'last_seen_at' => '2026-09-19 10:00:00.000000',
    ]);
}
$stableFirst = cc_presence_session_page(
    $phaseSixControl,
    $resellerLoader,
    'sessions',
    ['status' => 'all', 'sort' => 'started', 'direction' => 'desc', 'page' => 1],
    $phaseSixConfig,
    $phaseSixNow
);
$stableSecond = cc_presence_session_page(
    $phaseSixControl,
    $resellerLoader,
    'sessions',
    ['status' => 'all', 'sort' => 'started', 'direction' => 'desc', 'page' => 2],
    $phaseSixConfig,
    $phaseSixNow
);
$firstIds = array_column($stableFirst['rows'], 'id');
$secondIds = array_column($stableSecond['rows'], 'id');
cc_test((int) $firstIds[0] === 129, 'equal primary sort values use the stable session ID tie-breaker');
cc_test(count(array_intersect($firstIds, $secondIds)) === 0, 'stable pagination never duplicates rows across adjacent pages');

$query = cc_presence_query_parameters($filteredPage['filters'], ['page' => 2]);
cc_test(($query['q'] ?? '') === 'synthetic-user-a' && ($query['page'] ?? 0) === 2, 'pagination and deep links preserve active filters');
$firstResellerQuery = cc_presence_query_parameters(['reseller' => 1, 'page' => 1]);
cc_test(($firstResellerQuery['reseller'] ?? null) === 1 && !isset($firstResellerQuery['page']), 'reseller ID one is preserved while the default page is omitted');

$phaseSixSource = file_get_contents($phaseSixDomainPath);
$phaseSixIndex = file_get_contents($phaseSixRoot . '/index.php');
$phaseSixDashboard = file_get_contents($phaseSixRoot . '/views/dashboard.php');
$phaseSixSessionsView = file_get_contents($phaseSixRoot . '/views/sessions.php');
cc_test(
    is_string($phaseSixSource)
        && !preg_match('/\b(?:INSERT\s+INTO|UPDATE\s+\w|DELETE\s+FROM|REPLACE\s+INTO)\b/i', $phaseSixSource),
    'Phase 6 read model contains no database mutation'
);
cc_test(is_string($phaseSixIndex) && strpos($phaseSixIndex, 'cc_require_admin()') < strpos($phaseSixIndex, 'cc_presence_page_data('), 'owner authentication precedes every Presence owner read');
cc_test(is_string($phaseSixSessionsView) && str_contains($phaseSixSessionsView, 'iptv_password') && !str_contains($phaseSixSessionsView, 'reveal'), 'owner session view displays direct credential snapshots without reveal UX');
cc_test(is_string($phaseSixDashboard) && str_contains($phaseSixDashboard, 'غير متاح لعدم وجود هوية حساب ثابتة'), 'unsupported users-today metric remains explicitly unavailable');
cc_test(!str_contains((string) $phaseSixSource, 'account_id'), 'Phase 6 does not invent an account identity');
