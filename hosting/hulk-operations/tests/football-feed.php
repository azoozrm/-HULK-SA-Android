<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/lib/football-feed.php';
require_once dirname(__DIR__) . '/lib/operations.php';

function football_test(bool $condition, string $message): void
{
    global $tests;
    $tests++;
    if (!$condition) {
        fwrite(STDERR, "FAIL: football {$message}\n");
        exit(1);
    }
}

function football_event(
    int $id,
    string $kickoff,
    string $status,
    ?int $replacedBy = null,
    ?int $homeScore = null,
    ?int $awayScore = null
): array {
    return [
        'id' => $id,
        'event_date' => $kickoff,
        'status' => $status,
        'replaced_by' => $replacedBy,
        'home_score' => $homeScore,
        'away_score' => $awayScore,
        'current_minute' => $status === 'live' ? 63 : null,
        'league' => ['id' => 85, 'name' => 'Saudi Pro League', 'country' => 'Saudi Arabia'],
        'home_team' => ['id' => 10, 'name' => 'Al Hilal'],
        'away_team' => ['id' => 20, 'name' => 'Al Nassr'],
    ];
}

$now = strtotime('2026-11-18T18:00:00Z');
football_test(ops_football_riyadh_day_key($now) === '2026-11-18', 'Riyadh day key');
football_test(
    ops_football_riyadh_day_bounds('2026-11-18') === [
        'start' => strtotime('2026-11-17T21:00:00Z'),
        'end' => strtotime('2026-11-18T21:00:00Z'),
    ],
    'Riyadh day bounds'
);

$replaced = ops_football_reconcile_replaced([
    ops_football_normalize_event(football_event(101, '2026-11-18T16:00:00Z', 'postponed', 202)),
    ops_football_normalize_event(football_event(202, '2026-11-19T16:00:00Z', 'upcoming')),
]);
football_test(count($replaced) === 1, 'replacement row replaces postponed row');
football_test($replaced[0]['providerEventId'] === '202' && $replaced[0]['aliases'] === ['101'], 'stable alias retained');

$orphan = ops_football_reconcile_replaced([
    ops_football_normalize_event(football_event(303, '2026-11-18T16:00:00Z', 'postponed', 404)),
]);
football_test($orphan === [], 'postponed row outside window is not shown');

$payload = ops_football_build_payload(
    [
        football_event(1, '2026-11-18T16:00:00Z', 'upcoming'),
        football_event(2, '2026-11-18T16:30:00Z', 'live', null, null, null),
        football_event(3, '2026-11-18T17:00:00Z', 'finished', null, 2, 1),
    ],
    '2026-11-18',
    $now,
    false,
    0,
    null,
    60
);
football_test(count($payload['matches']) === 3, 'same-day matches retained');
football_test($payload['matches'][1]['homeScore'] === null, 'nullable home score preserved');
football_test($payload['matches'][1]['awayScore'] === null, 'nullable away score preserved');
football_test($payload['matches'][2]['homeScore'] === 2, 'confirmed score preserved');
football_test($payload['empty'] === false, 'non-empty day');
football_test($payload['stale'] === false, 'fresh payload is not stale');

$carry = ops_football_build_payload(
    [
        football_event(11, '2026-11-18T20:30:00Z', 'live'),
        football_event(12, '2026-11-18T17:00:00Z', 'upcoming'),
    ],
    '2026-11-19',
    strtotime('2026-11-18T21:30:00Z'),
    false,
    0,
    null,
    60
);
football_test(count($carry['matches']) === 1, 'only the live match crosses midnight');
football_test($carry['matches'][0]['carriedOver'] === true, 'carry-over is marked');
football_test($carry['matches'][0]['providerEventId'] === '11', 'carried live identity retained');

$empty = ops_football_build_payload([], '2026-11-18', $now, false, 0, null, 60);
football_test($empty['empty'] === true && $empty['stale'] === false, 'empty day is distinct from stale');

$directory = sys_get_temp_dir() . '/hulk-football-test-' . bin2hex(random_bytes(4));
$settings = [
    'refresh_seconds' => 60,
    'max_requests_per_day' => 10,
    'max_requests_per_refresh' => 2,
    'cache_directory' => $directory,
    'bzzoiro' => [
        'base_url' => 'https://sports.bzzoiro.com/api/v2',
        'api_key' => 'fixture-token',
    ],
];

$emptyCacheDirectory = sys_get_temp_dir() . '/hulk-football-empty-day-' . bin2hex(random_bytes(4));
$emptySettings = array_merge($settings, ['cache_directory' => $emptyCacheDirectory]);
$emptySuccess = ops_football_build_feed(
    $emptySettings,
    '2026-11-18',
    $now,
    static fn(): array => [
        'status' => 200,
        'headers' => [],
        'body' => json_encode(['results' => []]),
        'error' => null,
    ]
);
football_test($emptySuccess['payload']['empty'] === true, 'successful empty day is cached as empty');
$emptyStale = ops_football_build_feed(
    $emptySettings,
    '2026-11-18',
    $now + 61,
    static fn(): array => [
        'status' => 503,
        'headers' => ['retry-after' => '30'],
        'body' => '',
        'error' => 'unavailable',
    ]
);
football_test($emptyStale['payload']['empty'] === true, 'cached empty day is not converted to a match day');
football_test($emptyStale['payload']['stale'] === true, 'cached empty day is marked stale after failure');
$cleanupEmptyCache = static function (string $path): void {
    if (!is_dir($path)) {
        return;
    }
    foreach (glob($path . '/*') ?: [] as $file) {
        if (is_file($file)) {
            @unlink($file);
        }
    }
    @rmdir($path);
};
$cleanupEmptyCache($emptyCacheDirectory);

$calls = 0;
$transport = static function (string $url, array $headers, int $timeout) use (&$calls): array {
    $calls++;
    football_test(str_contains($headers['Authorization'] ?? '', 'fixture-token'), 'server-held token is sent');
    if (str_contains($url, '/live/')) {
        return [
            'status' => 200,
            'headers' => [],
            'body' => json_encode(['results' => [
                football_event(21, '2026-11-18T18:00:00Z', 'live', null, 0, 0),
            ]]),
            'error' => null,
        ];
    }
    return [
        'status' => 200,
        'headers' => [],
        'body' => json_encode(['results' => [
            football_event(20, '2026-11-18T16:00:00Z', 'upcoming'),
            football_event(21, '2026-11-18T18:00:00Z', 'live', null, 0, 0),
        ]]),
        'error' => null,
    ];
};
$first = ops_football_build_feed($settings, '2026-11-18', $now, $transport);
football_test($first['httpStatus'] === 200, 'feed fetch succeeds');
football_test($calls === 2, 'aggregate refresh is bounded to two requests');
football_test(count($first['payload']['matches']) === 2, 'aggregate results merged');
$second = ops_football_build_feed($settings, '2026-11-18', $now + 5, $transport);
football_test($second['httpStatus'] === 200 && $calls === 2, 'fresh cache avoids provider calls');
football_test($second['payload']['stale'] === false, 'fresh cache is not stale');

$failing = static fn(string $url, array $headers, int $timeout): array => [
    'status' => 503,
    'headers' => ['retry-after' => '120'],
    'body' => '',
    'error' => 'unavailable',
];
$stale = ops_football_build_feed($settings, '2026-11-18', $now + 61, $failing);
football_test($stale['httpStatus'] === 200, 'last-good cache survives provider failure');
football_test($stale['payload']['stale'] === true, 'last-good cache is marked stale');
football_test($stale['retryAfter'] === 120, 'Retry-After propagated');
$blocked = ops_football_build_feed($settings, '2026-11-18', $now + 62, $failing);
football_test($blocked['payload']['stale'] === true, 'Retry-After window serves cache without a provider call');
football_test($blocked['retryAfter'] === 119, 'Retry-After countdown retained');

$noCacheDirectory = sys_get_temp_dir() . '/hulk-football-empty-' . bin2hex(random_bytes(4));
$unconfigured = ops_football_build_feed(
    array_merge($settings, [
        'cache_directory' => $noCacheDirectory,
        'bzzoiro' => ['base_url' => 'https://sports.bzzoiro.com/api/v2', 'api_key' => ''],
    ]),
    '2026-11-18',
    $now,
    $transport
);
football_test($unconfigured['httpStatus'] === 503, 'missing server credential fails closed');
football_test(($unconfigured['payload']['error'] ?? '') === 'provider_not_configured', 'exact configuration gap');

$invalid = ops_football_build_feed($settings, '18-11-2026', $now, $transport);
football_test($invalid['httpStatus'] === 400, 'invalid day rejected');

$quotaDirectory = sys_get_temp_dir() . '/hulk-football-quota-' . bin2hex(random_bytes(4));
$quotaCalls = 0;
$quotaTransport = static function () use (&$quotaCalls): array {
    $quotaCalls++;
    return [
        'status' => 200,
        'headers' => [],
        'body' => json_encode(['results' => []]),
        'error' => null,
    ];
};
$quotaSettings = array_merge($settings, [
    'cache_directory' => $quotaDirectory,
    'max_requests_per_day' => 1,
    'max_requests_per_refresh' => 1,
]);
$quotaFirst = ops_football_build_feed($quotaSettings, '2026-11-18', $now, $quotaTransport);
football_test($quotaFirst['httpStatus'] === 200, 'bounded refresh succeeds inside quota');
football_test($quotaCalls === 1, 'refresh never exceeds the configured request cap');
$quotaSecond = ops_football_build_feed($quotaSettings, '2026-11-19', $now + 61, $quotaTransport);
football_test($quotaSecond['httpStatus'] === 503, 'quota exhaustion fails closed without a cache');
football_test(($quotaSecond['payload']['error'] ?? '') === 'provider_quota_exhausted', 'quota gap is explicit');
football_test($quotaCalls === 1, 'quota exhaustion performs no provider call');
$cleanupQuota = static function (string $path): void {
    if (!is_dir($path)) {
        return;
    }
    foreach (glob($path . '/*') ?: [] as $file) {
        if (is_file($file)) {
            @unlink($file);
        }
    }
    @rmdir($path);
};
$cleanupQuota($quotaDirectory);

$discovery = ops_football_discovery([
    'enabled' => true,
    'feed_url' => 'https://neo4ksa.com/hulk-operations/api/app/v1/sports/',
    'refresh_seconds' => 600,
]);
football_test($discovery !== null && $discovery['refreshSeconds'] === 300, 'discovery clamps refresh');
football_test(
    ops_football_discovery([
        'enabled' => true,
        'feed_url' => 'https://example.com/hulk-operations/api/app/v1/sports/',
    ]) === null,
    'discovery rejects an off-host feed'
);
football_test(ops_football_discovery(['enabled' => false]) === null, 'disabled discovery is omitted');

$cleanup = static function (string $path): void {
    if (!is_dir($path)) {
        return;
    }
    foreach (glob($path . '/*') ?: [] as $file) {
        if (is_file($file)) {
            @unlink($file);
        }
    }
    @rmdir($path);
};
$cleanup($directory);
$cleanup($noCacheDirectory);
