<?php

declare(strict_types=1);

$codeOnlineDb = new PDO('sqlite::memory:');
$codeOnlineDb->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$codeOnlineDb->exec(
    'CREATE TABLE cc_app_sessions ('
    . 'id INTEGER PRIMARY KEY, reseller_id INTEGER NOT NULL, access_code_snapshot TEXT NOT NULL, '
    . 'host_snapshot TEXT NOT NULL, iptv_username TEXT NOT NULL, ended_at TEXT NULL, last_seen_at TEXT NOT NULL)'
);
$codeInsert = $codeOnlineDb->prepare(
    'INSERT INTO cc_app_sessions (id, reseller_id, access_code_snapshot, host_snapshot, iptv_username, ended_at, last_seen_at) '
    . 'VALUES (:id, :reseller, :code, :host, :username, :ended, :seen)'
);
$codeSessions = [
    [1, 7, '000123', 'https://a.invalid', 'u1', null, '2026-09-19 11:59:50.000000'],
    [2, 7, '000123', 'https://a.invalid', 'u1', null, '2026-09-19 11:59:55.000000'],
    [3, 7, '000123', 'https://a.invalid', 'u2', null, '2026-09-19 11:59:40.000000'],
    [4, 7, '000123', 'https://a.invalid', 'u3', null, '2026-09-19 11:56:00.000000'],
    [5, 7, '000123', 'https://a.invalid', 'u4', '2026-09-19 11:59:59.000000', '2026-09-19 11:59:59.000000'],
    [6, 7, '000999', 'https://a.invalid', 'u5', null, '2026-09-19 11:59:30.000000'],
    [7, 8, '000123', 'https://b.invalid', 'u1', null, '2026-09-19 11:59:45.000000'],
];
foreach ($codeSessions as $session) {
    $codeInsert->execute([
        'id' => $session[0],
        'reseller' => $session[1],
        'code' => $session[2],
        'host' => $session[3],
        'username' => $session[4],
        'ended' => $session[5],
        'seen' => $session[6],
    ]);
}
$codeConfig = ['online_ttl_seconds' => 180];
$codeNow = new DateTimeImmutable('2026-09-19 12:00:00 UTC');
$hostACounts = cc_presence_code_online_subscription_counts(
    $codeOnlineDb,
    $codeConfig,
    7,
    'https://a.invalid',
    ['000123', '000999', '000777'],
    $codeNow
);
cc_test($hostACounts['000123'] === 2, 'code online count groups two devices into one subscription');
cc_test($hostACounts['000999'] === 1, 'rotated code counts the code recorded at connection');
cc_test($hostACounts['000777'] === 0, 'a successful read with no matches is zero');
cc_test(array_key_exists('000123', $hostACounts), 'leading zeros are preserved in the code key');
$hostBCounts = cc_presence_code_online_subscription_counts(
    $codeOnlineDb,
    $codeConfig,
    8,
    'https://b.invalid',
    ['000123'],
    $codeNow
);
cc_test($hostBCounts['000123'] === 1, 'the same code in another host/reseller context is counted separately');
$codeOnlineDb->exec('DROP TABLE cc_app_sessions');
$codeFailed = false;
try {
    cc_presence_code_online_subscription_counts($codeOnlineDb, $codeConfig, 7, 'https://a.invalid', ['000123'], $codeNow);
} catch (Throwable $exception) {
    $codeFailed = true;
}
cc_test($codeFailed, 'a failed code-online read is surfaced instead of becoming zero');
