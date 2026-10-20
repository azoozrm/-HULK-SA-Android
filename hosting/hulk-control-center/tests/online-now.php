<?php

declare(strict_types=1);

$onlineNowRoot = dirname(__DIR__);
require_once $onlineNowRoot . '/lib/presence-read-model.php';

cc_test(function_exists('cc_presence_online_now_page'), 'Online Now grouped read model is available');
cc_test(function_exists('cc_presence_subscription_key'), 'Online Now subscription identity helper is available');
cc_test(CC_PRESENCE_ONLINE_DEVICE_LIMIT === 8, 'Online Now device detail bound is explicit');

if (!in_array('sqlite', PDO::getAvailableDrivers(), true)) {
    fwrite(STDOUT, "SKIP: PDO SQLite is unavailable; Online Now database integration checks were not run.\n");
    return;
}

$onlineNowControl = new PDO('sqlite::memory:');
$onlineNowReseller = new PDO('sqlite::memory:');
foreach ([$onlineNowControl, $onlineNowReseller] as $database) {
    $database->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $database->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
}
$onlineNowControl->exec(
    'CREATE TABLE cc_devices ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, installation_id TEXT NOT NULL UNIQUE, platform_class TEXT NOT NULL, '
    . 'manufacturer TEXT NOT NULL, model TEXT NOT NULL, android_release TEXT NOT NULL, android_sdk_int INTEGER NOT NULL, '
    . 'first_seen_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, latest_app_version_name TEXT NOT NULL, '
    . 'latest_app_version_code INTEGER NOT NULL)'
);
$onlineNowControl->exec(
    'CREATE TABLE cc_app_sessions ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL UNIQUE, device_id INTEGER NOT NULL, '
    . 'reseller_id INTEGER NOT NULL, access_code_snapshot TEXT NOT NULL, iptv_username TEXT NOT NULL, '
    . 'iptv_password TEXT NOT NULL, host_snapshot TEXT NOT NULL, authenticated_at_client_ms INTEGER NOT NULL, '
    . 'app_version_name TEXT NOT NULL, app_version_code INTEGER NOT NULL, platform_class TEXT NOT NULL, '
    . 'device_manufacturer TEXT NOT NULL, device_model TEXT NOT NULL, android_release TEXT NOT NULL, '
    . 'android_sdk_int INTEGER NOT NULL, started_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, ended_at TEXT NULL, '
    . 'end_reason TEXT NULL)'
);
$onlineNowControl->exec(
    'CREATE TABLE cc_subscriber_notes ('
    . 'reseller_id INTEGER NOT NULL, host_key TEXT NOT NULL, username_key TEXT NOT NULL, '
    . 'note_text TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, '
    . 'PRIMARY KEY (reseller_id, host_key, username_key))'
);

$onlineNowReseller->exec(
    'CREATE TABLE resellers ('
    . 'reseller_id INTEGER PRIMARY KEY, reseller_name TEXT NOT NULL, access_code TEXT NOT NULL, '
    . 'access_code_hash TEXT NOT NULL, host TEXT NOT NULL, status TEXT NOT NULL)'
);
$onlineNowReseller->exec(
    "INSERT INTO resellers VALUES "
    . "(7, 'Synthetic One', 'HULK-AAAA-BBBB-CCCC-DDDD', 'hash-seven', 'https://a.invalid', 'active'), "
    . "(8, 'Synthetic Two', 'HULK-EEEE-FFFF-GGGG-HHHH', 'hash-eight', 'https://b.invalid', 'active')"
);

$onlineNowSessions = [];
$addSession = static function (
    int $id,
    int $resellerId,
    string $username,
    string $host,
    string $platform,
    string $lastSeen,
    string $password,
    string $accessCode,
    ?string $endedAt = null
) use (&$onlineNowSessions): void {
    $onlineNowSessions[] = [
        'id' => $id,
        'device_id' => $id,
        'reseller_id' => $resellerId,
        'access_code' => $accessCode,
        'username' => $username,
        'password' => $password,
        'host' => $host,
        'platform' => $platform,
        'manufacturer' => 'Synthetic',
        'model' => $platform . ' Device ' . $id,
        'android_release' => '14',
        'android_sdk_int' => 34,
        'started_at' => '2026-09-19 10:00:00.000000',
        'last_seen_at' => $lastSeen,
        'ended_at' => $endedAt,
        'end_reason' => $endedAt === null ? null : 'LOGOUT',
    ];
};

$sharedCode = 'HULK-AAAA-BBBB-CCCC-DDDD';
$sharedHost = 'https://a.invalid';
$addSession(1, 7, 'shared-user', $sharedHost, 'TV', '2026-09-19 11:59:30.000000', 'synthetic-password-a', $sharedCode);
$addSession(2, 7, 'shared-user', $sharedHost, 'PHONE', '2026-09-19 11:59:40.000000', 'synthetic-password-a', $sharedCode);
$addSession(3, 7, 'shared-user', $sharedHost, 'TABLET', '2026-09-19 11:58:00.000000', 'synthetic-password-a', $sharedCode);
$addSession(4, 8, 'shared-user', 'https://b.invalid', 'PHONE', '2026-09-19 11:59:00.000000', 'synthetic-password-b', 'HULK-EEEE-FFFF-GGGG-HHHH');
$addSession(5, 7, 'other-user', $sharedHost, 'TV', '2026-09-19 11:59:20.000000', 'synthetic-password-c', $sharedCode);
$addSession(6, 7, 'Alice', $sharedHost, 'PHONE', '2026-09-19 11:58:50.000000', 'synthetic-password-alice-upper', $sharedCode);
$addSession(7, 7, 'alice', $sharedHost, 'PHONE', '2026-09-19 11:58:40.000000', 'synthetic-password-alice-lower', $sharedCode);
$addSession(8, 7, 'user-é', $sharedHost, 'PHONE', '2026-09-19 11:58:30.000000', 'synthetic-password-accent', $sharedCode);
$addSession(9, 7, 'user-e', $sharedHost, 'PHONE', '2026-09-19 11:58:20.000000', 'synthetic-password-plain', $sharedCode);
for ($id = 10; $id <= 21; $id++) {
    $seconds = 5 + (($id - 10) * 5);
    $lastSeen = sprintf('2026-09-19 11:59:%02d.000000', $seconds);
    $addSession(
        $id,
        7,
        'many-device-user',
        $sharedHost,
        'PHONE',
        $lastSeen,
        'synthetic-password-h-' . $id,
        'HULK-HHHH-HHHH-HHHH-HHHH'
    );
}
$addSession(22, 7, 'boundary-user', $sharedHost, 'PHONE', '2026-09-19 11:57:00.000000', 'synthetic-password-boundary', $sharedCode);
$addSession(23, 7, 'stale-user', $sharedHost, 'PHONE', '2026-09-19 11:56:59.000000', 'synthetic-password-stale', $sharedCode);
$addSession(24, 7, 'ended-user', $sharedHost, 'PHONE', '2026-09-19 11:59:50.000000', 'synthetic-password-ended', $sharedCode, '2026-09-19 11:59:51.000000');
for ($id = 25; $id <= 28; $id++) {
    $seconds = 35 - (($id - 25) * 5);
    $addSession(
        $id,
        7,
        'page-user-' . ($id - 24),
        $sharedHost,
        'PHONE',
        sprintf('2026-09-19 11:57:%02d.000000', $seconds),
        'synthetic-password-page-' . $id,
        $sharedCode
    );
}

$insertDevice = $onlineNowControl->prepare(
    'INSERT INTO cc_devices '
    . '(id, installation_id, platform_class, manufacturer, model, android_release, android_sdk_int, '
    . 'first_seen_at, last_seen_at, latest_app_version_name, latest_app_version_code) '
    . 'VALUES (:id, :installation_id, :platform_class, :manufacturer, :model, :android_release, '
    . ':android_sdk_int, :first_seen_at, :last_seen_at, :app_version_name, :app_version_code)'
);
$insertSession = $onlineNowControl->prepare(
    'INSERT INTO cc_app_sessions '
    . '(id, session_id, device_id, reseller_id, access_code_snapshot, iptv_username, iptv_password, '
    . 'host_snapshot, authenticated_at_client_ms, app_version_name, app_version_code, platform_class, '
    . 'device_manufacturer, device_model, android_release, android_sdk_int, started_at, last_seen_at, '
    . 'ended_at, end_reason) '
    . 'VALUES (:id, :session_id, :device_id, :reseller_id, :access_code, :username, :password, '
    . ':host, 1790000000000, :app_version_name, :app_version_code, :platform_class, '
    . ':manufacturer, :model, :android_release, :android_sdk_int, :started_at, :last_seen_at, '
    . ':ended_at, :end_reason)'
);
foreach ($onlineNowSessions as $session) {
    $insertDevice->execute([
        'id' => $session['device_id'],
        'installation_id' => sprintf('aaaaaaaa-aaaa-4aaa-8aaa-%012d', $session['device_id']),
        'platform_class' => $session['platform'],
        'manufacturer' => $session['manufacturer'],
        'model' => $session['model'],
        'android_release' => $session['android_release'],
        'android_sdk_int' => $session['android_sdk_int'],
        'first_seen_at' => $session['started_at'],
        'last_seen_at' => $session['last_seen_at'],
        'app_version_name' => '0.9.3.23',
        'app_version_code' => 67,
    ]);
    $insertSession->execute([
        'id' => $session['id'],
        'session_id' => sprintf('bbbbbbbb-bbbb-4bbb-8bbb-%012d', $session['id']),
        'device_id' => $session['device_id'],
        'reseller_id' => $session['reseller_id'],
        'access_code' => $session['access_code'],
        'username' => $session['username'],
        'password' => $session['password'],
        'host' => $session['host'],
        'app_version_name' => '0.9.3.23',
        'app_version_code' => 67,
        'platform_class' => $session['platform'],
        'manufacturer' => $session['manufacturer'],
        'model' => $session['model'],
        'android_release' => $session['android_release'],
        'android_sdk_int' => $session['android_sdk_int'],
        'started_at' => $session['started_at'],
        'last_seen_at' => $session['last_seen_at'],
        'ended_at' => $session['ended_at'],
        'end_reason' => $session['end_reason'],
    ]);
}

$onlineNowConfig = cc_presence_config([
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
$onlineNowTime = new DateTimeImmutable('2026-09-19 12:00:00 UTC');
$onlineNowLookups = 0;
$onlineNowLoader = static function (array $ids) use ($onlineNowReseller, &$onlineNowLookups): array {
    $onlineNowLookups++;
    return cc_presence_reseller_map($onlineNowReseller, $ids);
};

$onlineNowPage = cc_presence_online_now_page(
    $onlineNowControl,
    $onlineNowLoader,
    [],
    $onlineNowConfig,
    $onlineNowTime
);
cc_test($onlineNowLookups === 1, 'Online Now resolves all visible resellers with one bounded lookup');
cc_test($onlineNowPage['total_subscriptions'] === 13, 'Online Now counts distinct exact subscription groups');
cc_test($onlineNowPage['total_connections'] === 26, 'Online Now counts every online session');
cc_test($onlineNowPage['total_devices'] === 26, 'Online Now counts distinct online installation identities');
cc_test($onlineNowPage['tv_connections'] === 2, 'Online Now reports TV connections from authoritative platform snapshots');
cc_test($onlineNowPage['phone_connections'] === 24, 'Online Now reports phone and tablet connections');
cc_test($onlineNowPage['multi_device_subscriptions'] === 2, 'Online Now warns for subscriptions with more than one device');
cc_test($onlineNowPage['pages'] === 2 && $onlineNowPage['page'] === 1, 'Online Now paginates subscription groups deterministically');
cc_test(count($onlineNowPage['groups']) === 10, 'Online Now first page is bounded to ten subscriptions');

$onlineNowByKey = [];
foreach ($onlineNowPage['groups'] as $group) {
    $onlineNowByKey[$group['reseller_id'] . '|' . $group['host_snapshot'] . '|' . $group['iptv_username']] = $group;
}
$sharedGroup = $onlineNowByKey['7|https://a.invalid|shared-user'] ?? null;
cc_test(is_array($sharedGroup) && (int) $sharedGroup['device_count'] === 3, 'devices with the same reseller, host and username share one subscription');
cc_test(is_array($sharedGroup) && (int) $sharedGroup['connection_count'] === 3, 'shared subscription keeps its connection total');
cc_test(is_array($sharedGroup) && (int) $sharedGroup['tv_connections'] === 1 && (int) $sharedGroup['phone_connections'] === 2, 'shared subscription keeps its platform breakdown');
cc_test(is_array($sharedGroup) && count($sharedGroup['devices']) === 3, 'shared subscription groups one row per installation identity');
cc_test(is_array($sharedGroup) && !array_key_exists('sessions', $sharedGroup), 'bounded details retain no unbounded session array');
cc_test(is_array($sharedGroup) && ($sharedGroup['devices_truncated'] ?? null) === false, 'normal subscription is not marked truncated');
cc_test(is_array($sharedGroup) && (int) $sharedGroup['latest_session']['id'] === 2, 'subscription credentials come from the latest online snapshot');

$otherResellerGroup = $onlineNowByKey['8|https://b.invalid|shared-user'] ?? null;
cc_test(is_array($otherResellerGroup) && (int) $otherResellerGroup['device_count'] === 1, 'the same username under another reseller and host is not merged');

$aliceGroup = $onlineNowByKey['7|https://a.invalid|Alice'] ?? null;
$aliceLowerGroup = $onlineNowByKey['7|https://a.invalid|alice'] ?? null;
cc_test(is_array($aliceGroup) && is_array($aliceLowerGroup), 'Alice and alice remain separate exact subscriptions');
cc_test(is_array($aliceGroup) && (int) $aliceGroup['device_count'] === 1, 'Alice keeps one device');
cc_test(is_array($aliceLowerGroup) && (int) $aliceLowerGroup['device_count'] === 1, 'alice keeps one device');
cc_test(
    is_array($aliceGroup) && is_array($aliceLowerGroup)
        && $aliceGroup['latest_session']['iptv_password'] !== $aliceLowerGroup['latest_session']['iptv_password'],
    'case-distinct usernames keep their own credential snapshots'
);

$accentGroup = $onlineNowByKey['7|https://a.invalid|user-é'] ?? null;
$plainGroup = $onlineNowByKey['7|https://a.invalid|user-e'] ?? null;
cc_test(is_array($accentGroup) && is_array($plainGroup), 'accent-distinct usernames remain separate exact subscriptions');
cc_test(is_array($accentGroup) && (int) $accentGroup['device_count'] === 1, 'accent username keeps one device');
cc_test(is_array($plainGroup) && (int) $plainGroup['device_count'] === 1, 'plain username keeps one device');
cc_test(
    is_array($accentGroup) && is_array($plainGroup)
        && $accentGroup['latest_session']['iptv_password'] !== $plainGroup['latest_session']['iptv_password'],
    'accent-distinct usernames keep their own credential snapshots'
);

$manyGroup = $onlineNowByKey['7|https://a.invalid|many-device-user'] ?? null;
cc_test(is_array($manyGroup) && (int) $manyGroup['connection_count'] === 12, 'over-limit subscription keeps its true connection total');
cc_test(is_array($manyGroup) && (int) $manyGroup['device_count'] === 12, 'over-limit subscription keeps its true device total');
cc_test(is_array($manyGroup) && count($manyGroup['devices']) === CC_PRESENCE_ONLINE_DEVICE_LIMIT, 'over-limit device details are capped');
cc_test(is_array($manyGroup) && ($manyGroup['devices_truncated'] ?? null) === true, 'over-limit device details are marked partial');
cc_test(is_array($manyGroup) && (int) $manyGroup['latest_session']['id'] === 21, 'over-limit subscription keeps the true latest credential snapshot');
cc_test(
    is_array($manyGroup) && $manyGroup['latest_session']['iptv_password'] === 'synthetic-password-h-21',
    'over-limit subscription keeps the latest password snapshot'
);
cc_test(
    is_array($manyGroup)
        && $manyGroup['devices'][0]['installation_id'] === sprintf('aaaaaaaa-aaaa-4aaa-8aaa-%012d', 21),
    'capped device details are still ordered from the latest activity'
);
$manyDeviceIds = array_column($manyGroup['devices'] ?? [], 'installation_id');
cc_test(!in_array(sprintf('aaaaaaaa-aaaa-4aaa-8aaa-%012d', 13), $manyDeviceIds, true), 'capped device details exclude older rows beyond the bound');

$onlineNowSecond = cc_presence_online_now_page(
    $onlineNowControl,
    $onlineNowLoader,
    ['page' => 2],
    $onlineNowConfig,
    $onlineNowTime
);
cc_test($onlineNowSecond['page'] === 2 && count($onlineNowSecond['groups']) === 3, 'Online Now second page contains the remaining subscriptions');
$firstPageKeys = array_keys($onlineNowByKey);
$secondPageKeys = array_map(
    static fn (array $group): string => $group['reseller_id'] . '|' . $group['host_snapshot'] . '|' . $group['iptv_username'],
    $onlineNowSecond['groups']
);
cc_test(array_intersect($firstPageKeys, $secondPageKeys) === [], 'Online Now pagination never duplicates a subscription across pages');
$secondPageUsernames = array_column($onlineNowSecond['groups'], 'iptv_username');
cc_test(in_array('boundary-user', $secondPageUsernames, true), 'Online Now includes a session exactly at the configured TTL boundary');
cc_test(!in_array('stale-user', $secondPageUsernames, true), 'Online Now excludes a session outside the configured TTL');
cc_test(!in_array('ended-user', $secondPageUsernames, true), 'Online Now excludes an explicitly ended session');

$onlineNowClamped = cc_presence_online_now_page(
    $onlineNowControl,
    $onlineNowLoader,
    ['page' => 999],
    $onlineNowConfig,
    $onlineNowTime
);
cc_test($onlineNowClamped['page'] === 2, 'Online Now clamps an out-of-range page to the last authoritative page');

$onlineNowFailure = cc_presence_online_now_page(
    $onlineNowControl,
    static function (array $ids): array {
        throw new RuntimeException('synthetic reseller lookup failure');
    },
    [],
    $onlineNowConfig,
    $onlineNowTime
);
cc_test($onlineNowFailure['total_subscriptions'] === 13, 'reseller mapping failure preserves authoritative subscription counts');
cc_test($onlineNowFailure['resellers_available'] === false, 'reseller mapping failure is reported explicitly');
cc_test($onlineNowFailure['resellers'] === [], 'reseller mapping failure never fabricates names');

$tieControl = new PDO('sqlite::memory:');
$tieControl->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$tieControl->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
$tieControl->exec(
    'CREATE TABLE cc_devices ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, installation_id TEXT NOT NULL UNIQUE, platform_class TEXT NOT NULL, '
    . 'manufacturer TEXT NOT NULL, model TEXT NOT NULL, android_release TEXT NOT NULL, android_sdk_int INTEGER NOT NULL, '
    . 'first_seen_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, latest_app_version_name TEXT NOT NULL, '
    . 'latest_app_version_code INTEGER NOT NULL)'
);
$tieControl->exec(
    'CREATE TABLE cc_app_sessions ('
    . 'id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL UNIQUE, device_id INTEGER NOT NULL, '
    . 'reseller_id INTEGER NOT NULL, access_code_snapshot TEXT NOT NULL, iptv_username TEXT NOT NULL, '
    . 'iptv_password TEXT NOT NULL, host_snapshot TEXT NOT NULL, authenticated_at_client_ms INTEGER NOT NULL, '
    . 'app_version_name TEXT NOT NULL, app_version_code INTEGER NOT NULL, platform_class TEXT NOT NULL, '
    . 'device_manufacturer TEXT NOT NULL, device_model TEXT NOT NULL, android_release TEXT NOT NULL, '
    . 'android_sdk_int INTEGER NOT NULL, started_at TEXT NOT NULL, last_seen_at TEXT NOT NULL, ended_at TEXT NULL, '
    . 'end_reason TEXT NULL)'
);
$tieControl->exec(
    'CREATE TABLE cc_subscriber_notes ('
    . 'reseller_id INTEGER NOT NULL, host_key TEXT NOT NULL, username_key TEXT NOT NULL, '
    . 'note_text TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, '
    . 'PRIMARY KEY (reseller_id, host_key, username_key))'
);

$tieUsernames = ['Alice', 'alice', 'user-é', 'user-e'];
for ($index = 1; $index <= 8; $index++) {
    $tieUsernames[] = sprintf('tie-user-%02d', $index);
}
$tieCredentials = [];
$tieTime = '2026-09-19 11:59:00.000000';
$tieInsertDevice = $tieControl->prepare(
    'INSERT INTO cc_devices '
    . '(id, installation_id, platform_class, manufacturer, model, android_release, android_sdk_int, '
    . 'first_seen_at, last_seen_at, latest_app_version_name, latest_app_version_code) '
    . 'VALUES (:id, :installation_id, :platform_class, :manufacturer, :model, :android_release, '
    . ':android_sdk_int, :first_seen_at, :last_seen_at, :app_version_name, :app_version_code)'
);
$tieInsertSession = $tieControl->prepare(
    'INSERT INTO cc_app_sessions '
    . '(id, session_id, device_id, reseller_id, access_code_snapshot, iptv_username, iptv_password, '
    . 'host_snapshot, authenticated_at_client_ms, app_version_name, app_version_code, platform_class, '
    . 'device_manufacturer, device_model, android_release, android_sdk_int, started_at, last_seen_at, '
    . 'ended_at, end_reason) '
    . 'VALUES (:id, :session_id, :device_id, :reseller_id, :access_code, :username, :password, '
    . ':host, 1790000000000, :app_version_name, :app_version_code, :platform_class, '
    . ':manufacturer, :model, :android_release, :android_sdk_int, :started_at, :last_seen_at, '
    . ':ended_at, :end_reason)'
);
foreach ($tieUsernames as $index => $username) {
    $id = $index + 1;
    $code = sprintf('HULK-TIE-%02d-AAAA-BBBB', $id);
    $password = 'synthetic-password-tie-' . $id;
    $tieCredentials[$username] = ['password' => $password, 'code' => $code];
    $tieInsertDevice->execute([
        'id' => $id,
        'installation_id' => sprintf('cccccccc-cccc-4ccc-8ccc-%012d', $id),
        'platform_class' => 'PHONE',
        'manufacturer' => 'Synthetic',
        'model' => 'Tie Phone ' . $id,
        'android_release' => '14',
        'android_sdk_int' => 34,
        'first_seen_at' => $tieTime,
        'last_seen_at' => $tieTime,
        'app_version_name' => '0.9.3.23',
        'app_version_code' => 67,
    ]);
    $tieInsertSession->execute([
        'id' => $id,
        'session_id' => sprintf('dddddddd-dddd-4ddd-8ddd-%012d', $id),
        'device_id' => $id,
        'reseller_id' => 7,
        'access_code' => $code,
        'username' => $username,
        'password' => $password,
        'host' => 'https://tie.invalid',
        'app_version_name' => '0.9.3.23',
        'app_version_code' => 67,
        'platform_class' => 'PHONE',
        'manufacturer' => 'Synthetic',
        'model' => 'Tie Phone ' . $id,
        'android_release' => '14',
        'android_sdk_int' => 34,
        'started_at' => $tieTime,
        'last_seen_at' => $tieTime,
        'ended_at' => null,
        'end_reason' => null,
    ]);
}

$tieLoader = static function (array $ids): array {
    return [7 => [
        'reseller_id' => 7,
        'reseller_name' => 'Tie Reseller',
        'status' => 'active',
        'host' => 'https://tie.invalid',
        'access_code' => 'HULK-TIE-01-AAAA-BBBB',
    ]];
};
$tiePageOne = cc_presence_online_now_page($tieControl, $tieLoader, [], $onlineNowConfig, $onlineNowTime);
$tiePageTwo = cc_presence_online_now_page($tieControl, $tieLoader, ['page' => 2], $onlineNowConfig, $onlineNowTime);
$expectedTieOrder = $tieUsernames;
usort($expectedTieOrder, static fn (string $left, string $right): int => strcmp(bin2hex($left), bin2hex($right)));
cc_test($tiePageOne['total_subscriptions'] === 12, 'tie-breaker fixture counts all exact subscriptions');
cc_test($tiePageOne['total_connections'] === 12 && $tiePageOne['total_devices'] === 12, 'tie-breaker totals remain exact');
cc_test($tiePageOne['pages'] === 2 && count($tiePageOne['groups']) === 10 && count($tiePageTwo['groups']) === 2, 'tie-breaker fixture crosses the existing page boundary');
$tiePageOneUsernames = array_column($tiePageOne['groups'], 'iptv_username');
$tiePageTwoUsernames = array_column($tiePageTwo['groups'], 'iptv_username');
$tieAllUsernames = array_merge($tiePageOneUsernames, $tiePageTwoUsernames);
cc_test($tieAllUsernames === $expectedTieOrder, 'identical activity/reseller/host groups use exact username HEX tie-breakers across pages');
cc_test(count($tieAllUsernames) === 12 && count(array_unique($tieAllUsernames)) === 12, 'every tie-breaker subscription appears exactly once');
cc_test(array_intersect($tiePageOneUsernames, $tiePageTwoUsernames) === [], 'tie-breaker page boundary has no duplicated subscription');
cc_test(in_array('Alice', $tieAllUsernames, true) && in_array('alice', $tieAllUsernames, true), 'case-distinct usernames survive deterministic pagination');
cc_test(in_array('user-é', $tieAllUsernames, true) && in_array('user-e', $tieAllUsernames, true), 'accent-distinct usernames survive deterministic pagination');
$tieGroups = array_merge($tiePageOne['groups'], $tiePageTwo['groups']);
$tieCredentialsMatch = true;
$tieDetailsBounded = true;
foreach ($tieGroups as $group) {
    $username = (string) $group['iptv_username'];
    $expected = $tieCredentials[$username] ?? null;
    if (
        !is_array($expected)
        || (string) ($group['latest_session']['iptv_username'] ?? '') !== $username
        || (string) ($group['latest_session']['iptv_password'] ?? '') !== $expected['password']
        || (string) ($group['latest_session']['access_code_snapshot'] ?? '') !== $expected['code']
    ) {
        $tieCredentialsMatch = false;
    }
    if (
        (int) $group['device_count'] !== 1
        || count($group['devices']) !== 1
        || ($group['devices_truncated'] ?? null) !== false
    ) {
        $tieDetailsBounded = false;
    }
}
cc_test($tieCredentialsMatch, 'tie-breaker credentials remain attached to their own exact subscription');
cc_test($tieDetailsBounded, 'tie-breaker fixture preserves normal bounded device details');
