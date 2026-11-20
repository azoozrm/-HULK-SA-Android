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
        'league' => ['id' => 17, 'name' => 'Saudi Pro League', 'country' => 'Saudi Arabia'],
        'home_team' => ['id' => 10, 'name' => 'Al Hilal'],
        'away_team' => ['id' => 20, 'name' => 'Al Nassr'],
    ];
}

function football_aggregate_count(string $directory, int $clock): int
{
    $path = $directory . '/quota-aggregate-' . gmdate('Y-m-d', $clock) . '.json';
    if (!is_file($path)) {
        return 0;
    }
    $decoded = json_decode((string) file_get_contents($path), true);
    return max(0, (int) ($decoded['count'] ?? 0));
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

$scheduleLiveAlias = ops_football_build_payload(
    [
        football_event(41, '2026-11-19T18:00:00Z', 'postponed', 51),
        array_merge(football_event(51, '2026-11-19T20:00:00Z', 'notstarted'), [
            'league' => ['id' => 17, 'name' => '', 'country' => 'Saudi Arabia'],
        ]),
        array_merge(football_event(51, '2026-11-19T20:00:00Z', 'inprogress', null, 1, 0), [
            'current_minute' => 31,
            'home_team' => 'Al Hilal',
            'away_team' => 'Al Nassr',
        ]),
    ],
    '2026-11-19',
    strtotime('2026-11-19T20:40:00Z'),
    false,
    0,
    null,
    60,
    ['17' => 'Saudi Pro League']
);
football_test(count($scheduleLiveAlias['matches']) === 1, 'replacement schedule/live rows collapse to one event');
football_test($scheduleLiveAlias['matches'][0]['status'] === 'live', 'live status wins');
football_test($scheduleLiveAlias['matches'][0]['minute'] === 31, 'live minute wins');
football_test($scheduleLiveAlias['matches'][0]['homeScore'] === 1, 'live score wins');
football_test($scheduleLiveAlias['matches'][0]['kickoffAt'] === strtotime('2026-11-19T20:00:00Z'), 'schedule kickoff preserved');
football_test($scheduleLiveAlias['matches'][0]['competition']['name'] === 'Saudi Pro League', 'schedule/league metadata preserved');
football_test(in_array('41', $scheduleLiveAlias['matches'][0]['aliases'], true), 'replacement alias preserved');

$liveScoreProgression = ops_football_build_payload(
    [
        array_merge(football_event(52, '2026-11-19T18:00:00Z', 'inprogress', null, 0, 0), ['current_minute' => 10]),
        array_merge(football_event(52, '2026-11-19T18:00:00Z', 'inprogress', null, 1, 0), ['current_minute' => 10]),
    ],
    '2026-11-19',
    strtotime('2026-11-19T18:20:00Z'),
    false,
    0,
    null,
    60
);
football_test($liveScoreProgression['matches'][0]['homeScore'] === 1, 'newer live score replaces 0-0');

$finalFirst = ops_football_build_payload(
    [
        football_event(53, '2026-11-19T18:00:00Z', 'finished', null, 2, 1),
        array_merge(football_event(53, '2026-11-19T18:00:00Z', 'inprogress', null, 0, 0), ['current_minute' => 60]),
    ],
    '2026-11-19',
    strtotime('2026-11-19T18:20:00Z'),
    false,
    0,
    null,
    60
);
football_test($finalFirst['matches'][0]['status'] === 'finished', 'final result survives older live row (final first)');
football_test($finalFirst['matches'][0]['homeScore'] === 2, 'final score survives older live row (final first)');

$finalSecond = ops_football_build_payload(
    [
        array_merge(football_event(54, '2026-11-19T18:00:00Z', 'inprogress', null, 0, 0), ['current_minute' => 60]),
        football_event(54, '2026-11-19T18:00:00Z', 'finished', null, 2, 1),
    ],
    '2026-11-19',
    strtotime('2026-11-19T18:20:00Z'),
    false,
    0,
    null,
    60
);
football_test($finalSecond['matches'][0]['status'] === 'finished', 'final result survives older live row (live first)');
football_test($finalSecond['matches'][0]['homeScore'] === 2, 'final score survives older live row (live first)');

$allowlistPayload = ops_football_build_payload(
    [
        football_event(61, '2026-11-19T17:00:00Z', 'finished', null, 2, 1),
        array_merge(
            football_event(62, '2026-11-19T18:00:00Z', 'finished', null, 1, 0),
            ['league' => ['id' => 18, 'name' => 'MLS', 'country' => 'USA']]
        ),
    ],
    '2026-11-19',
    strtotime('2026-11-19T20:00:00Z'),
    false,
    0,
    null,
    60
);
football_test(count($allowlistPayload['matches']) === 1, 'non-allowlisted competition is removed from matches');
football_test(count($allowlistPayload['kickoffHistory']) === 1, 'non-allowlisted competition is removed from history');
football_test($allowlistPayload['matches'][0]['competition']['id'] === '17', 'allowed competition remains');

$filteredCache = ops_football_filter_payload(
    [
        'empty' => false,
        'matches' => [
            ['competition' => ['id' => '17'], 'homeTeam' => ['id' => '262'], 'awayTeam' => ['id' => '267']],
            ['competition' => ['id' => '18'], 'homeTeam' => ['id' => '1'], 'awayTeam' => ['id' => '2']],
        ],
        'kickoffHistory' => [],
        'reminderCandidates' => [
            ['competition' => ['id' => '53'], 'homeTeam' => ['id' => '3'], 'awayTeam' => ['id' => '4']],
        ],
        'teams' => [
            ['id' => '262', 'competitionIds' => ['17']],
            ['id' => '1', 'competitionIds' => ['18']],
            ['id' => '3', 'competitionIds' => ['53']],
        ],
    ],
    ops_football_allowed_league_ids()
);
football_test(count($filteredCache['matches']) === 1 && count($filteredCache['reminderCandidates']) === 0, 'cached payload is filtered before presentation');
football_test(count($filteredCache['teams']) === 1 && $filteredCache['teams'][0]['id'] === '262', 'cached teams are filtered by competition membership');
football_test(in_array('53', ops_football_allowed_league_ids(['include_optional_arab_domestic' => true]), true), 'optional Arab domestic flag is opt-in');
football_test(!in_array('53', ops_football_allowed_league_ids(), true), 'optional Arab domestic is disabled by default');

$providerUpdatedShape = ops_football_normalize_event(array_merge(
    football_event(56, '2026-11-19T18:00:00Z', 'inprogress', null, 1, 0),
    ['last_updated' => '2026-11-19T18:25:00Z']
));
football_test($providerUpdatedShape['providerUpdatedAt'] === strtotime('2026-11-19T18:25:00Z'), 'provider last_updated is preserved');

$newerCancellation = ops_football_build_payload(
    [
        array_merge(
            football_event(57, '2026-11-19T18:00:00Z', 'finished', null, 2, 1),
            ['last_updated' => '2026-11-19T20:00:00Z']
        ),
        array_merge(
            football_event(57, '2026-11-19T18:00:00Z', 'cancelled'),
            ['last_updated' => '2026-11-19T21:00:00Z']
        ),
    ],
    '2026-11-19',
    strtotime('2026-11-19T22:00:00Z'),
    false,
    0,
    null,
    60
);
football_test($newerCancellation['matches'][0]['status'] === 'cancelled', 'newer cancellation replaces an older final status');
football_test($newerCancellation['matches'][0]['homeScore'] === 2, 'newer cancellation preserves the verified home score');
football_test($newerCancellation['matches'][0]['awayScore'] === 1, 'newer cancellation preserves the verified away score');

$newerCancellationFirst = ops_football_build_payload(
    [
        array_merge(
            football_event(59, '2026-11-19T18:00:00Z', 'cancelled'),
            ['last_updated' => '2026-11-19T21:00:00Z']
        ),
        array_merge(
            football_event(59, '2026-11-19T18:00:00Z', 'finished', null, 2, 1),
            ['last_updated' => '2026-11-19T20:00:00Z']
        ),
    ],
    '2026-11-19',
    strtotime('2026-11-19T22:00:00Z'),
    false,
    0,
    null,
    60
);
football_test($newerCancellationFirst['matches'][0]['status'] === 'cancelled', 'newer cancellation wins in either input order');

$olderCancellation = ops_football_build_payload(
    [
        array_merge(
            football_event(58, '2026-11-19T18:00:00Z', 'cancelled'),
            ['last_updated' => '2026-11-19T19:00:00Z']
        ),
        array_merge(
            football_event(58, '2026-11-19T18:00:00Z', 'finished', null, 3, 2),
            ['last_updated' => '2026-11-19T21:00:00Z']
        ),
    ],
    '2026-11-19',
    strtotime('2026-11-19T22:00:00Z'),
    false,
    0,
    null,
    60
);
football_test($olderCancellation['matches'][0]['status'] === 'finished', 'older cancellation cannot erase a newer final result');
football_test($olderCancellation['matches'][0]['homeScore'] === 3, 'newer final score is retained');

$finalWithoutScore = ops_football_build_payload(
    [
        football_event(55, '2026-11-19T18:00:00Z', 'finished', null, 2, 1),
        football_event(55, '2026-11-19T18:00:00Z', 'finished', null, null, null),
    ],
    '2026-11-19',
    strtotime('2026-11-19T20:30:00Z'),
    false,
    0,
    null,
    60
);
football_test($finalWithoutScore['matches'][0]['status'] === 'finished', 'later final row keeps final status');
football_test($finalWithoutScore['matches'][0]['homeScore'] === 2, 'later final row without score keeps verified home score');
football_test($finalWithoutScore['matches'][0]['awayScore'] === 1, 'later final row without score keeps verified away score');

$liveMerge = ops_football_build_payload(
    [
        football_event(31, '2026-11-19T00:30:00Z', 'unresolved', null, 2, 1),
        array_merge(football_event(31, '2026-11-19T00:30:00Z', 'inprogress', null, 2, 1), ['current_minute' => 73]),
    ],
    '2026-11-19',
    strtotime('2026-11-19T01:00:00Z'),
    false,
    0,
    null,
    60
);
football_test($liveMerge['matches'][0]['status'] === 'live', 'live row wins over an unresolved day duplicate');
football_test($liveMerge['matches'][0]['minute'] === 73, 'live minute is retained');

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
$finishedCarry = ops_football_build_payload(
    [football_event(13, '2026-11-18T20:30:00Z', 'finished', null, 2, 1)],
    '2026-11-19',
    strtotime('2026-11-18T21:30:00Z'),
    false,
    0,
    null,
    60
);
football_test($finishedCarry['matches'] === [], 'finished previous-day results do not cross midnight');

$historyPayload = ops_football_build_payload(
    [
        football_event(61, '2026-11-18T22:50:00Z', 'live', null, 0, 0),
        football_event(62, '2026-11-18T22:00:00Z', 'finished', null, 2, 1),
        football_event(63, '2026-11-19T22:00:00Z', 'upcoming'),
    ],
    '2026-11-19',
    strtotime('2026-11-19T21:00:00Z'),
    false,
    0,
    null,
    60
);
football_test(count($historyPayload['kickoffHistory']) === 2, '24-hour history is separate from the displayed day');
football_test($historyPayload['kickoffHistory'][0]['providerEventId'] === '61', 'history is newest first');
$liveHistory = null;
foreach ($historyPayload['kickoffHistory'] as $historyMatch) {
    if ($historyMatch['providerEventId'] === '61') $liveHistory = $historyMatch;
}
football_test($liveHistory !== null, 'live started event is in history');
football_test($liveHistory['startedAt'] !== null, 'live startedAt is provider-derived');

$empty = ops_football_build_payload([], '2026-11-18', $now, false, 0, null, 60);
football_test($empty['empty'] === true && $empty['stale'] === false, 'empty day is distinct from stale');

$realShape = ops_football_normalize_event([
    'id' => 223066,
    'event_date' => '2026-11-19T18:00:00+00:00',
    'status' => 'notstarted',
    'league_id' => 17,
    'home_team_id' => 266,
    'home_team' => 'Al-Taawoun',
    'away_team_id' => 271,
    'away_team' => 'Al-Ittihad',
    'home_score' => null,
    'away_score' => null,
    'replaced_by' => null,
], ['17' => 'Saudi Pro League']);
football_test($realShape !== null, 'real Bzzoiro string-team shape normalizes');
football_test($realShape['competition']['name'] === 'Saudi Pro League', 'league name map is applied');
football_test($realShape['homeTeam']['id'] === '266', 'string team keeps provider id');
football_test($realShape['status'] === 'upcoming', 'notstarted maps to upcoming');
football_test($realShape['homeScore'] === null, 'real null score stays null');

$metadataShape = ops_football_normalize_event([
    'id' => 900001,
    'event_date' => '2026-11-19T18:00:00+00:00',
    'status' => 'notstarted',
    'league_id' => 17,
    'home_team_id' => 262,
    'home_team' => 'Al-Hilal',
    'away_team_id' => 267,
    'away_team' => 'Al-Nassr',
]);
football_test($metadataShape['homeTeam']['arabicName'] === 'الهلال', 'verified Saudi Arabic team name is attached');
football_test($metadataShape['homeTeam']['logoUrl'] === 'https://sports.bzzoiro.com/img/team/262/', 'verified team crest URL is attached');
football_test($metadataShape['awayTeam']['arabicName'] === 'النصر', 'second verified Arabic team name is attached');
football_test($metadataShape['competition']['arabicName'] === 'دوري روشن السعودي', 'Arabic competition name is attached');
football_test($metadataShape['competition']['priority'] === 10, 'Saudi competition priority is first');
football_test($metadataShape['competition']['logoUrl'] === 'https://sports.bzzoiro.com/img/league/17/', 'verified league crest URL is attached');
football_test($metadataShape['homeTeam']['competitionIds'] === ['17'], 'team competition membership is attached');
football_test($metadataShape['homeTeam']['nationalTeam'] === false, 'club team is not classified as a national team');
$nationalShape = ops_football_normalize_event([
    'id' => 900002,
    'event_date' => '2026-11-19T18:00:00+00:00',
    'status' => 'notstarted',
    'league_id' => 27,
    'home_team_id' => 7001,
    'home_team' => 'Algeria',
    'away_team_id' => 7002,
    'away_team' => 'Argentina',
]);
football_test($nationalShape['homeTeam']['nationalTeam'] === true, 'World Cup team is classified as a national team');
$names = ops_football_arabic_team_names();
football_test(($names['44'] ?? '') === 'برشلونة', 'verified Barcelona ID maps to its Arabic name');
football_test(($names['479'] ?? '') === 'السعودية', 'verified Saudi national-team ID maps to its Arabic name');

$liveShape = ops_football_provider_rows([
    'count' => 1,
    'events' => [['id' => 1, 'status' => 'live']],
]);
football_test(is_array($liveShape) && count($liveShape) === 1, 'live events envelope is accepted');
football_test(
    ops_football_provider_rows(['count' => 0, 'events' => []]) === [],
    'empty live events envelope is accepted'
);

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
football_test(football_aggregate_count($directory, $now) === 2, 'each successful request is counted exactly once');
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
football_test(football_aggregate_count($directory, $now) === 3, 'failed provider attempt is counted exactly once');

football_test(OPS_FOOTBALL_DEFAULT_MAX_REQUESTS_PER_DAY === 500, 'feed default budget is 500/day');
football_test(OPS_FOOTBALL_MAX_REQUESTS_PER_DAY === 2500, 'feed hard ceiling is 2500/day');
football_test(OPS_FOOTBALL_AGGREGATE_MAX_REQUESTS_PER_DAY === 2500, 'aggregate hard ceiling is 2500/day');
football_test(OPS_FOOTBALL_DEFAULT_CATALOG_MAX_PER_DAY === 200, 'catalog cap is 200/day');
football_test(count(ops_football_allowed_league_ids()) === 27, 'default allowlist has 27 available IDs');

$broadcastDirectory = sys_get_temp_dir() . '/hulk-football-broadcasts-' . bin2hex(random_bytes(4));
mkdir($broadcastDirectory);
$broadcastCalls = 0;
$broadcastTransport = static function (string $url) use (&$broadcastCalls): array {
    $broadcastCalls++;
    football_test(str_contains($url, 'country_code=SA'), 'broadcast request pins Saudi Arabia');
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 2, 'results' => [
        ['event_id' => 7001, 'league_id' => 1, 'channel_name' => 'beIN SPORTS 1', 'event_date' => '2026-11-18T18:00:00Z'],
        ['event_id' => 7001, 'league_id' => 1, 'channel_name' => 'MBC Shahid', 'event_date' => '2026-11-18T18:00:00Z'],
        ['event_id' => 7002, 'league_id' => 18, 'channel_name' => 'Apple TV', 'event_date' => '2026-11-18T18:00:00Z'],
    ]]), 'error' => null];
};
$broadcastUsed = 0;
$broadcastRemaining = 2;
$broadcastRetry = null;
$broadcastMap = ops_football_broadcast_map(
    $settings,
    $broadcastDirectory,
    $broadcastTransport,
    ['Authorization' => 'Token fixture-token'],
    8,
    $now,
    $broadcastUsed,
    $broadcastRemaining,
    $broadcastRetry,
);
$broadcastKey = ops_football_broadcast_key('7001', strtotime('2026-11-18T18:00:00Z'));
football_test($broadcastCalls === 1 && $broadcastUsed === 1, 'broadcast refresh is one bounded request');
football_test(
    $broadcastMap[$broadcastKey]['kickoffAt'] === strtotime('2026-11-18T18:00:00Z')
        && $broadcastMap[$broadcastKey]['channels'] === ['beIN SPORTS 1', 'MBC Shahid'],
    'confirmed multi-channel broadcaster is retained with its provider kickoff'
);
football_test(
    !isset($broadcastMap[ops_football_broadcast_key('7002', strtotime('2026-11-18T18:00:00Z'))]),
    'non-allowlisted broadcaster row is discarded'
);
$broadcastCached = ops_football_broadcast_map(
    $settings,
    $broadcastDirectory,
    $broadcastTransport,
    ['Authorization' => 'Token fixture-token'],
    8,
    $now + 60,
    $broadcastUsed,
    $broadcastRemaining,
    $broadcastRetry,
);
football_test($broadcastCalls === 1 && $broadcastCached === $broadcastMap, 'broadcast cache avoids per-request calls');
$cleanupBroadcast = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupBroadcast($broadcastDirectory);

$broadcastKickoff = strtotime('2026-11-18T18:00:00Z');
$attached = ops_football_attach_broadcasts(
    ['matches' => [
        ['providerEventId' => '7001', 'kickoffAt' => $broadcastKickoff],
        ['providerEventId' => '7001', 'kickoffAt' => $broadcastKickoff + 86_400],
    ]],
    $broadcastMap
);
football_test(
    ($attached['matches'][0]['broadcasters'] ?? []) === ['beIN SPORTS 1', 'MBC Shahid'],
    'unchanged provider id and kickoff attach the confirmed channels'
);
football_test(
    !isset($attached['matches'][1]['broadcasters']),
    'same provider id rescheduled to a new kickoff does not inherit channels'
);
$aliasAttached = ops_football_attach_broadcasts(
    ['matches' => [[
        'providerEventId' => 'new',
        'aliases' => ['old'],
        'kickoffAt' => $broadcastKickoff,
    ]]],
    [
        ops_football_broadcast_key('old', $broadcastKickoff) => [
            'providerEventId' => 'old',
            'kickoffAt' => $broadcastKickoff,
            'channels' => ['Old Channel'],
        ],
        ops_football_broadcast_key('new', $broadcastKickoff) => [
            'providerEventId' => 'new',
            'kickoffAt' => $broadcastKickoff,
            'channels' => ['New Channel'],
        ],
    ]
);
football_test(
    ($aliasAttached['matches'][0]['broadcasters'] ?? []) === ['New Channel'],
    'replacement alias keeps only its own matching provider assignment'
);

$bidirectionalOldKickoff = strtotime('2026-11-18T18:00:00Z');
$bidirectionalNewKickoff = $bidirectionalOldKickoff + 86_400;
$bidirectionalRows = [
    ['event_id' => 7100, 'league_id' => 1, 'channel_name' => 'Old Channel', 'event_date' => gmdate('c', $bidirectionalOldKickoff)],
    ['event_id' => 7100, 'league_id' => 1, 'channel_name' => 'New Channel', 'event_date' => gmdate('c', $bidirectionalNewKickoff)],
];
foreach ([$bidirectionalRows, array_reverse($bidirectionalRows)] as $orderIndex => $orderedRows) {
    $bidirectionalDirectory = sys_get_temp_dir() . '/hulk-football-broadcast-order-' . bin2hex(random_bytes(4));
    mkdir($bidirectionalDirectory);
    $bidirectionalUsed = 0;
    $bidirectionalRemaining = 2;
    $bidirectionalRetry = null;
    $bidirectionalMap = ops_football_broadcast_map(
        $settings,
        $bidirectionalDirectory,
        static fn(): array => [
            'status' => 200,
            'headers' => [],
            'body' => json_encode(['count' => 2, 'results' => $orderedRows]),
            'error' => null,
        ],
        [],
        8,
        $now,
        $bidirectionalUsed,
        $bidirectionalRemaining,
        $bidirectionalRetry,
        5
    );
    $oldKey = ops_football_broadcast_key('7100', $bidirectionalOldKickoff);
    $newKey = ops_football_broadcast_key('7100', $bidirectionalNewKickoff);
    football_test(
        ($bidirectionalMap[$oldKey]['channels'] ?? []) === ['Old Channel']
            && ($bidirectionalMap[$newKey]['channels'] ?? []) === ['New Channel'],
        'both kickoff assignments survive row order ' . $orderIndex
    );
    $newAttached = ops_football_attach_broadcasts(
        ['matches' => [['providerEventId' => '7100', 'kickoffAt' => $bidirectionalNewKickoff]]],
        $bidirectionalMap
    );
    $oldAttached = ops_football_attach_broadcasts(
        ['matches' => [['providerEventId' => '7100', 'kickoffAt' => $bidirectionalOldKickoff]]],
        $bidirectionalMap
    );
    football_test(
        ($newAttached['matches'][0]['broadcasters'] ?? []) === ['New Channel'],
        'new fixture receives only its confirmed channel in order ' . $orderIndex
    );
    football_test(
        ($oldAttached['matches'][0]['broadcasters'] ?? []) === ['Old Channel'],
        'old fixture keeps only its own channel in order ' . $orderIndex
    );
    $bidirectionalCache = json_decode(
        (string) file_get_contents($bidirectionalDirectory . '/broadcasts.json'),
        true
    );
    football_test(
        (int) ($bidirectionalCache['schemaVersion'] ?? 0) === 3
            && count($bidirectionalCache['assignments'] ?? []) === 2,
        'broadcaster cache retains both kickoff assignments in order ' . $orderIndex
    );
    $cleanupBroadcast($bidirectionalDirectory);
}

$legacyBroadcastDirectory = sys_get_temp_dir() . '/hulk-football-broadcast-legacy-' . bin2hex(random_bytes(4));
mkdir($legacyBroadcastDirectory);
file_put_contents(
    $legacyBroadcastDirectory . '/broadcasts.json',
    json_encode([
        'cachedAt' => $now,
        'countryCode' => 'SA',
        'assignments' => ['7001' => ['Legacy Channel']],
    ])
);
$legacyBroadcastCalls = 0;
$legacyUsed = 0;
$legacyRemaining = 1;
$legacyRetry = null;
$legacyMap = ops_football_broadcast_map(
    $settings,
    $legacyBroadcastDirectory,
    static function (string $url) use (&$legacyBroadcastCalls): array {
        $legacyBroadcastCalls++;
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 1, 'results' => [
            ['event_id' => 8001, 'league_id' => 1, 'channel_name' => 'Verified Channel', 'event_date' => '2026-11-18T20:00:00Z'],
        ]]), 'error' => null];
    },
    [],
    8,
    $now,
    $legacyUsed,
    $legacyRemaining,
    $legacyRetry,
    5
);
football_test($legacyBroadcastCalls === 1, 'legacy id-only broadcaster cache cannot confirm a kickoff and is refreshed');
football_test(
    array_filter(array_keys($legacyMap), static fn(string $key): bool => str_starts_with($key, '7001|')) === [],
    'legacy id-only assignment is not reused'
);
football_test(
    ($legacyMap[ops_football_broadcast_key('8001', strtotime('2026-11-18T20:00:00Z'))]['channels'] ?? []) === ['Verified Channel'],
    'kickoff-bound refresh replaces the legacy cache'
);
$legacyFailedDirectory = sys_get_temp_dir() . '/hulk-football-broadcast-legacy-fail-' . bin2hex(random_bytes(4));
mkdir($legacyFailedDirectory);
file_put_contents(
    $legacyFailedDirectory . '/broadcasts.json',
    json_encode([
        'cachedAt' => $now,
        'countryCode' => 'SA',
        'assignments' => ['7001' => ['Legacy Channel']],
    ])
);
$legacyFailedUsed = 0;
$legacyFailedRemaining = 1;
$legacyFailedRetry = null;
$legacyFailedMap = ops_football_broadcast_map(
    $settings,
    $legacyFailedDirectory,
    static fn(): array => ['status' => 503, 'headers' => [], 'body' => '', 'error' => 'unavailable'],
    [],
    8,
    $now,
    $legacyFailedUsed,
    $legacyFailedRemaining,
    $legacyFailedRetry,
    5
);
football_test($legacyFailedMap === [], 'legacy id-only cache cannot fall back to an unconfirmed kickoff');
$cleanupBroadcast($legacyBroadcastDirectory);
$cleanupBroadcast($legacyFailedDirectory);

$broadcastPageDirectory = sys_get_temp_dir() . '/hulk-football-broadcast-pages-' . bin2hex(random_bytes(4));
mkdir($broadcastPageDirectory);
$broadcastPageCalls = 0;
$broadcastPageTransport = static function (string $url) use (&$broadcastPageCalls): array {
    $broadcastPageCalls++;
    parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
    $offset = (int) ($query['offset'] ?? 0);
    $rows = [];
    for ($index = 0; $index < 200; $index++) {
        $rows[] = ['event_id' => 9000 + $offset + $index, 'league_id' => 1, 'channel_name' => 'Channel'];
    }
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 300, 'results' => $rows]), 'error' => null];
};
$broadcastPageUsed = 0;
$broadcastPageRemaining = 2;
$broadcastPageRetry = null;
$broadcastPageMap = ops_football_broadcast_map(
    $settings,
    $broadcastPageDirectory,
    $broadcastPageTransport,
    [],
    8,
    $now,
    $broadcastPageUsed,
    $broadcastPageRemaining,
    $broadcastPageRetry,
    1,
);
football_test($broadcastPageCalls === 1, 'broadcast page limit is bounded');
football_test(!file_exists($broadcastPageDirectory . '/broadcasts.json'), 'incomplete broadcaster pagination is not cached');
$broadcastRateUsed = 0;
$broadcastRateRemaining = 2;
$broadcastRateRetry = null;
$broadcastRateCalls = 0;
$broadcastRateMap = ops_football_broadcast_map(
    $settings,
    $broadcastPageDirectory,
    static function () use (&$broadcastRateCalls): array {
        $broadcastRateCalls++;
        return ['status' => 429, 'headers' => ['retry-after' => '60'], 'body' => '', 'error' => null];
    },
    [],
    8,
    $now,
    $broadcastRateUsed,
    $broadcastRateRemaining,
    $broadcastRateRetry,
    5,
);
football_test($broadcastRateCalls === 1 && $broadcastRateRetry === 60, 'broadcaster Retry-After stops further pages');
football_test($broadcastRateMap === [], 'failed optional broadcaster metadata leaves match data usable');
$cleanupBroadcastPages = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupBroadcastPages($broadcastPageDirectory);

$broadcastEmptyDirectory = sys_get_temp_dir() . '/hulk-football-broadcast-empty-' . bin2hex(random_bytes(4));
mkdir($broadcastEmptyDirectory);
$broadcastEmptyCalls = 0;
$broadcastEmptyUsed = 0;
$broadcastEmptyRemaining = 2;
$broadcastEmptyRetry = null;
$broadcastEmptyMap = ops_football_broadcast_map(
    $settings,
    $broadcastEmptyDirectory,
    static function (string $url) use (&$broadcastEmptyCalls): array {
        $broadcastEmptyCalls++;
        parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
        $offset = (int) ($query['offset'] ?? 0);
        $rows = $offset === 0
            ? array_fill(0, 200, ['event_id' => 9100, 'league_id' => 1, 'channel_name' => 'Channel'])
            : [];
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 300, 'results' => $rows]), 'error' => null];
    },
    [],
    8,
    $now,
    $broadcastEmptyUsed,
    $broadcastEmptyRemaining,
    $broadcastEmptyRetry,
    2,
);
football_test($broadcastEmptyCalls === 2, 'broadcast empty page follows a short page');
football_test(!file_exists($broadcastEmptyDirectory . '/broadcasts.json'), 'empty short broadcaster page with reported rows is not cached');
$cleanupBroadcastPages($broadcastEmptyDirectory);

$aggregateDirectory = sys_get_temp_dir() . '/hulk-football-aggregate-' . bin2hex(random_bytes(4));
mkdir($aggregateDirectory);
file_put_contents(
    $aggregateDirectory . '/quota-aggregate-' . gmdate('Y-m-d', $now) . '.json',
    json_encode(['count' => 2500, 'day' => gmdate('Y-m-d', $now)])
);
$aggregateCalls = 0;
$aggregateFeed = ops_football_build_feed(
    array_merge($settings, ['cache_directory' => $aggregateDirectory, 'max_requests_per_day' => 500]),
    '2026-11-19',
    $now,
    static function () use (&$aggregateCalls): array {
        $aggregateCalls++;
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['results' => []]), 'error' => null];
    }
);
football_test($aggregateFeed['httpStatus'] === 503, 'aggregate daily ceiling fails closed');
football_test($aggregateCalls === 0, 'aggregate ceiling makes no provider call');
$cleanupAggregate = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupAggregate($aggregateDirectory);

$aggregateCatalogDirectory = sys_get_temp_dir() . '/hulk-football-catalog-aggregate-' . bin2hex(random_bytes(4));
mkdir($aggregateCatalogDirectory);
file_put_contents(
    $aggregateCatalogDirectory . '/quota-aggregate-' . gmdate('Y-m-d', $now) . '.json',
    json_encode(['count' => 2500, 'day' => gmdate('Y-m-d', $now)])
);
$aggregateCatalogCalls = 0;
$aggregateCatalog = ops_football_build_team_catalog(
    array_merge($settings, [
        'cache_directory' => $aggregateCatalogDirectory,
        'catalog_league_ids' => [1],
        'catalog_scope' => 'explicit',
        'catalog_max_requests_per_day' => 200,
    ]),
    $now,
    static function () use (&$aggregateCatalogCalls): array {
        $aggregateCatalogCalls++;
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
    }
);
football_test($aggregateCatalog['httpStatus'] === 503, 'catalog aggregate daily ceiling fails closed');
football_test($aggregateCatalogCalls === 0, 'catalog aggregate ceiling makes no provider call');
$cleanupAggregateCatalog = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupAggregateCatalog($aggregateCatalogDirectory);

$cooldownDirectory = sys_get_temp_dir() . '/hulk-football-cooldown-' . bin2hex(random_bytes(4));
mkdir($cooldownDirectory);
file_put_contents(
    $cooldownDirectory . '/retry.json',
    json_encode(['until' => $now + 120])
);
$cooldownCalls = 0;
$cooldownCatalog = ops_football_build_team_catalog(
    array_merge($settings, ['cache_directory' => $cooldownDirectory, 'catalog_league_ids' => [1], 'catalog_scope' => 'explicit']),
    $now,
    static function () use (&$cooldownCalls): array {
        $cooldownCalls++;
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
    }
);
football_test($cooldownCatalog['httpStatus'] === 503 && $cooldownCalls === 0, 'catalog respects shared feed cooldown');
$cooldownFeed = ops_football_build_feed(
    array_merge($settings, ['cache_directory' => $cooldownDirectory, 'max_requests_per_refresh' => 1]),
    '2026-11-19',
    $now,
    static function () use (&$cooldownCalls): array {
        $cooldownCalls++;
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
    }
);
football_test($cooldownFeed['httpStatus'] === 503 && $cooldownCalls === 0, 'feed and catalog share the same cooldown owner');
$cleanupCooldown = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupCooldown($cooldownDirectory);

$concurrentDirectory = sys_get_temp_dir() . '/hulk-football-concurrent-' . bin2hex(random_bytes(4));
mkdir($concurrentDirectory);
$concurrentResults = [];
if (function_exists('pcntl_fork')) {
    $concurrentSettings = array_merge($settings, [
        'cache_directory' => $concurrentDirectory,
        'max_requests_per_day' => 10,
        'max_requests_per_refresh' => 1,
        'catalog_league_ids' => [1],
        'catalog_scope' => 'explicit',
        'catalog_max_requests_per_day' => 10,
    ]);
    $workers = [
        ['feed', '2026-11-19'],
        ['feed', '2026-11-20'],
        ['catalog', '2026-11-19'],
    ];
    $pids = [];
    foreach ($workers as $index => [$kind, $day]) {
        $pid = pcntl_fork();
        if ($pid === 0) {
            $attempts = 0;
            $transport = static function (string $url) use (&$attempts): array {
                $attempts++;
                usleep(600_000);
                return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
            };
            if ($kind === 'catalog') {
                $result = ops_football_build_team_catalog($concurrentSettings, $now, $transport);
            } else {
                $result = ops_football_build_feed($concurrentSettings, $day, $now, $transport);
            }
            file_put_contents(
                $concurrentDirectory . '/worker-' . $index . '.json',
                json_encode(['attempts' => $attempts, 'status' => $result['httpStatus']])
            );
            exit(0);
        }
        $pids[] = $pid;
    }
    foreach ($pids as $pid) {
        pcntl_waitpid($pid, $status);
    }
    foreach (glob($concurrentDirectory . '/worker-*.json') ?: [] as $file) {
        $concurrentResults[] = json_decode((string) file_get_contents($file), true);
    }
    $aggregate = json_decode(
        (string) file_get_contents($concurrentDirectory . '/quota-aggregate-' . gmdate('Y-m-d', $now) . '.json'),
        true
    );
    $attempts = array_sum(array_map(static fn(array $row): int => (int) ($row['attempts'] ?? 0), $concurrentResults));
    football_test(count($concurrentResults) === 3, 'three offline provider workers completed');
    football_test($attempts === 3, 'each concurrent worker counted its attempted request');
    football_test((int) ($aggregate['count'] ?? 0) === 3, 'shared provider lock prevents lost aggregate increments');
}
$cleanupConcurrent = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupConcurrent($concurrentDirectory);

$interruptDirectory = sys_get_temp_dir() . '/hulk-football-interrupt-' . bin2hex(random_bytes(4));
mkdir($interruptDirectory);
if (function_exists('pcntl_fork')) {
    $interruptPid = pcntl_fork();
    if ($interruptPid === 0) {
        ops_football_build_feed(
            array_merge($settings, ['cache_directory' => $interruptDirectory]),
            '2026-11-18',
            $now,
            static function (string $url, array $headers, int $timeout) use ($interruptDirectory, $now): array {
                file_put_contents(
                    $interruptDirectory . '/transport-observed.json',
                    json_encode(['reserved' => football_aggregate_count($interruptDirectory, $now)])
                );
                exit(0);
            }
        );
        exit(2);
    }
    pcntl_waitpid($interruptPid, $interruptStatus);
    $interruptObserved = json_decode(
        (string) file_get_contents($interruptDirectory . '/transport-observed.json'),
        true
    );
    football_test(
        (int) ($interruptObserved['reserved'] ?? 0) === 1,
        'attempt is durably reserved before transport'
    );
    football_test(
        football_aggregate_count($interruptDirectory, $now) === 1,
        'interrupted process does not lose or double-count its reservation'
    );
    football_test(
        !file_exists($interruptDirectory . '/day-2026-11-18.json'),
        'interrupted process publishes no partial cache'
    );
}
$cleanupInterrupt = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupInterrupt($interruptDirectory);

$ledgerFailureDirectory = sys_get_temp_dir() . '/hulk-football-ledger-failure-' . bin2hex(random_bytes(4));
mkdir($ledgerFailureDirectory);
$ledgerFailureName = 'quota-aggregate-' . gmdate('Y-m-d', $now) . '.json';
mkdir($ledgerFailureDirectory . '/' . $ledgerFailureName);
$ledgerFailureCalls = 0;
$ledgerFailureFeed = ops_football_build_feed(
    array_merge($settings, ['cache_directory' => $ledgerFailureDirectory]),
    '2026-11-18',
    $now,
    static function () use (&$ledgerFailureCalls): array {
        $ledgerFailureCalls++;
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['results' => []]), 'error' => null];
    }
);
football_test($ledgerFailureFeed['httpStatus'] === 503, 'unreadable aggregate ledger fails closed');
football_test(
    ($ledgerFailureFeed['payload']['error'] ?? '') === 'provider_ledger_unavailable',
    'ledger failure is explicit'
);
football_test($ledgerFailureCalls === 0, 'ledger failure makes no provider transport');
@rmdir($ledgerFailureDirectory . '/' . $ledgerFailureName);
$cleanupInterrupt($ledgerFailureDirectory);

$writeFailureDirectory = sys_get_temp_dir() . '/hulk-football-ledger-write-' . bin2hex(random_bytes(4));
mkdir($writeFailureDirectory);
file_put_contents($writeFailureDirectory . '/provider.lock', '');
file_put_contents(
    $writeFailureDirectory . '/quota-aggregate-' . gmdate('Y-m-d', $now) . '.json',
    json_encode(['count' => 0, 'day' => gmdate('Y-m-d', $now)])
);
chmod($writeFailureDirectory, 0555);
$writeFailureCalls = 0;
$writeFailureFeed = ops_football_build_feed(
    array_merge($settings, ['cache_directory' => $writeFailureDirectory]),
    '2026-11-18',
    $now,
    static function () use (&$writeFailureCalls): array {
        $writeFailureCalls++;
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['results' => []]), 'error' => null];
    }
);
football_test($writeFailureFeed['httpStatus'] === 503, 'failed ledger write fails closed');
football_test(
    ($writeFailureFeed['payload']['error'] ?? '') === 'provider_ledger_unavailable',
    'ledger write failure is explicit'
);
football_test($writeFailureCalls === 0, 'ledger write failure makes no provider transport');
football_test(
    football_aggregate_count($writeFailureDirectory, $now) === 0,
    'failed ledger write does not corrupt the readable aggregate'
);

$invalidLedgerDay = gmdate('Y-m-d', $now);
$invalidLedgerCases = [
    'feed aggregate without count' => [
        'kind' => 'feed',
        'aggregate' => ['day' => $invalidLedgerDay],
        'quota' => ['count' => 0, 'day' => $invalidLedgerDay],
    ],
    'feed route quota textual count' => [
        'kind' => 'feed',
        'aggregate' => ['count' => 0, 'day' => $invalidLedgerDay],
        'quota' => ['count' => '0', 'day' => $invalidLedgerDay],
    ],
    'catalog aggregate textual count' => [
        'kind' => 'catalog',
        'aggregate' => ['count' => '0', 'day' => $invalidLedgerDay],
        'quota' => ['count' => 0, 'day' => $invalidLedgerDay],
    ],
    'catalog route quota without count' => [
        'kind' => 'catalog',
        'aggregate' => ['count' => 0, 'day' => $invalidLedgerDay],
        'quota' => ['day' => $invalidLedgerDay],
    ],
    'feed aggregate float count' => [
        'kind' => 'feed',
        'aggregate' => ['count' => 1.5, 'day' => $invalidLedgerDay],
        'quota' => ['count' => 0, 'day' => $invalidLedgerDay],
    ],
];
foreach ($invalidLedgerCases as $label => $case) {
    $invalidDirectory = sys_get_temp_dir() . '/hulk-football-invalid-ledger-' . bin2hex(random_bytes(4));
    mkdir($invalidDirectory);
    $quotaPrefix = $case['kind'] === 'catalog' ? 'quota-teams' : 'quota';
    file_put_contents(
        $invalidDirectory . '/quota-aggregate-' . $invalidLedgerDay . '.json',
        json_encode($case['aggregate'])
    );
    file_put_contents(
        $invalidDirectory . '/' . $quotaPrefix . '-' . $invalidLedgerDay . '.json',
        json_encode($case['quota'])
    );
    $invalidCalls = 0;
    $invalidTransport = static function () use (&$invalidCalls): array {
        $invalidCalls++;
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
    };
    if ($case['kind'] === 'catalog') {
        $invalidResult = ops_football_build_team_catalog(
            [
                'cache_directory' => $invalidDirectory,
                'bzzoiro' => ['base_url' => 'https://sports.bzzoiro.com/api/v2', 'api_key' => 'fixture-token'],
                'catalog_league_ids' => [1],
                'catalog_scope' => 'explicit',
                'catalog_max_requests_per_day' => 10,
            ],
            $now,
            $invalidTransport
        );
    } else {
        $invalidResult = ops_football_build_feed(
            array_merge($settings, ['cache_directory' => $invalidDirectory]),
            '2026-11-18',
            $now,
            $invalidTransport
        );
    }
    football_test($invalidResult['httpStatus'] === 503, 'invalid ledger count fails closed: ' . $label);
    football_test(
        ($invalidResult['payload']['error'] ?? '') === 'provider_ledger_unavailable',
        'invalid ledger error is explicit: ' . $label
    );
    football_test($invalidCalls === 0, 'invalid ledger makes no transport: ' . $label);
    $cleanupInterrupt($invalidDirectory);
}

$midnightStart = strtotime('2026-11-18T23:59:59Z');
$midnightNext = $midnightStart + 2;
$midnightFeedDirectory = sys_get_temp_dir() . '/hulk-football-midnight-feed-' . bin2hex(random_bytes(4));
mkdir($midnightFeedDirectory);
$midnightFeedTimes = [$midnightStart, $midnightNext];
$midnightFeedIndex = 0;
$midnightFeedClock = static function () use (&$midnightFeedIndex, $midnightFeedTimes): int {
    $index = min($midnightFeedIndex, count($midnightFeedTimes) - 1);
    $midnightFeedIndex++;
    return $midnightFeedTimes[$index];
};
$midnightFeedCalls = 0;
$midnightFeed = ops_football_build_feed(
    array_merge($settings, [
        'cache_directory' => $midnightFeedDirectory,
        'max_requests_per_refresh' => 2,
    ]),
    '2026-11-19',
    $midnightStart,
    static function () use (&$midnightFeedCalls): array {
        $midnightFeedCalls++;
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
    },
    $midnightFeedClock
);
$midnightFeedDayOne = gmdate('Y-m-d', $midnightStart);
$midnightFeedDayTwo = gmdate('Y-m-d', $midnightNext);
$midnightFeedQuotaOne = json_decode(
    (string) file_get_contents($midnightFeedDirectory . '/quota-' . $midnightFeedDayOne . '.json'),
    true
);
$midnightFeedQuotaTwo = json_decode(
    (string) file_get_contents($midnightFeedDirectory . '/quota-' . $midnightFeedDayTwo . '.json'),
    true
);
football_test($midnightFeedCalls === 2, 'midnight feed performs two reserved attempts');
football_test(
    (int) ($midnightFeedQuotaOne['count'] ?? 0) === 1
        && football_aggregate_count($midnightFeedDirectory, $midnightStart) === 1,
    'feed attempt before UTC midnight is charged to the first day'
);
football_test(
    (int) ($midnightFeedQuotaTwo['count'] ?? 0) === 1
        && football_aggregate_count($midnightFeedDirectory, $midnightNext) === 1,
    'feed attempt after UTC midnight is charged to the second day'
);
$cleanupInterrupt($midnightFeedDirectory);

$midnightCatalogDirectory = sys_get_temp_dir() . '/hulk-football-midnight-catalog-' . bin2hex(random_bytes(4));
mkdir($midnightCatalogDirectory);
$midnightCatalogTimes = [$midnightStart, $midnightNext];
$midnightCatalogIndex = 0;
$midnightCatalogClock = static function () use (&$midnightCatalogIndex, $midnightCatalogTimes): int {
    $index = min($midnightCatalogIndex, count($midnightCatalogTimes) - 1);
    $midnightCatalogIndex++;
    return $midnightCatalogTimes[$index];
};
$midnightCatalogCalls = 0;
$midnightCatalog = ops_football_build_team_catalog(
    [
        'cache_directory' => $midnightCatalogDirectory,
        'bzzoiro' => ['base_url' => 'https://sports.bzzoiro.com/api/v2', 'api_key' => 'fixture-token'],
        'catalog_league_ids' => [1],
        'catalog_scope' => 'explicit',
        'catalog_max_requests_per_day' => 10,
    ],
    $midnightStart,
    static function (string $url) use (&$midnightCatalogCalls): array {
        $midnightCatalogCalls++;
        parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
        $offset = (int) ($query['offset'] ?? 0);
        if ($offset === 0) {
            $rows = [];
            for ($id = 1; $id <= 200; $id++) {
                $rows[] = ['id' => $id, 'name' => 'Midnight ' . $id, 'country' => 'England'];
            }
            return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 201, 'results' => $rows]), 'error' => null];
        }
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 201, 'results' => [
            ['id' => 201, 'name' => 'Midnight 201', 'country' => 'England'],
        ]]), 'error' => null];
    },
    $midnightCatalogClock
);
$midnightCatalogQuotaOne = json_decode(
    (string) file_get_contents($midnightCatalogDirectory . '/quota-teams-' . $midnightFeedDayOne . '.json'),
    true
);
$midnightCatalogQuotaTwo = json_decode(
    (string) file_get_contents($midnightCatalogDirectory . '/quota-teams-' . $midnightFeedDayTwo . '.json'),
    true
);
football_test($midnightCatalog['httpStatus'] === 200, 'midnight catalog succeeds');
football_test($midnightCatalogCalls === 2, 'midnight catalog performs two reserved attempts');
football_test(
    (int) ($midnightCatalogQuotaOne['count'] ?? 0) === 1
        && football_aggregate_count($midnightCatalogDirectory, $midnightStart) === 1,
    'catalog attempt before UTC midnight is charged to the first day'
);
football_test(
    (int) ($midnightCatalogQuotaTwo['count'] ?? 0) === 1
        && football_aggregate_count($midnightCatalogDirectory, $midnightNext) === 1,
    'catalog attempt after UTC midnight is charged to the second day'
);
$cleanupInterrupt($midnightCatalogDirectory);

$rateFailureDirectory = sys_get_temp_dir() . '/hulk-football-rate-failure-' . bin2hex(random_bytes(4));
mkdir($rateFailureDirectory);
ops_football_build_feed(
    array_merge($settings, ['cache_directory' => $rateFailureDirectory]),
    '2026-11-18',
    $now,
    static fn(): array => [
        'status' => 200, 'headers' => [], 'body' => json_encode(['results' => []]), 'error' => null,
    ]
);
$rateFailureBefore = football_aggregate_count($rateFailureDirectory, $now);
$rateFailureFeed = ops_football_build_feed(
    array_merge($settings, ['cache_directory' => $rateFailureDirectory]),
    '2026-11-18',
    $now + 61,
    static fn(): array => [
        'status' => 429, 'headers' => ['retry-after' => '45'], 'body' => '', 'error' => null,
    ]
);
football_test($rateFailureFeed['payload']['stale'] === true, '429 keeps the last-good feed usable');
football_test(
    football_aggregate_count($rateFailureDirectory, $now) === $rateFailureBefore + 1,
    'a 429 attempt is reserved and counted exactly once'
);
$cleanupInterrupt($rateFailureDirectory);

$capDirectory = sys_get_temp_dir() . '/hulk-football-cap-race-' . bin2hex(random_bytes(4));
mkdir($capDirectory);
file_put_contents(
    $capDirectory . '/quota-aggregate-' . gmdate('Y-m-d', $now) . '.json',
    json_encode(['count' => 2499, 'day' => gmdate('Y-m-d', $now)])
);
if (function_exists('pcntl_fork')) {
    $capSettings = array_merge($settings, [
        'cache_directory' => $capDirectory,
        'max_requests_per_day' => 500,
        'max_requests_per_refresh' => 4,
        'catalog_league_ids' => [1],
        'catalog_scope' => 'explicit',
        'catalog_max_requests_per_day' => 200,
    ]);
    $capPids = [];
    foreach (['feed', 'catalog'] as $index => $kind) {
        $pid = pcntl_fork();
        if ($pid === 0) {
            $attempts = 0;
            $transport = static function () use (&$attempts): array {
                $attempts++;
                usleep(250_000);
                return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
            };
            $result = $kind === 'catalog'
                ? ops_football_build_team_catalog($capSettings, $now, $transport)
                : ops_football_build_feed($capSettings, '2026-11-19', $now, $transport);
            file_put_contents(
                $capDirectory . '/cap-worker-' . $index . '.json',
                json_encode(['attempts' => $attempts, 'status' => $result['httpStatus']])
            );
            exit(0);
        }
        $capPids[] = $pid;
    }
    foreach ($capPids as $pid) {
        pcntl_waitpid($pid, $status);
    }
    $capAttempts = 0;
    foreach (glob($capDirectory . '/cap-worker-*.json') ?: [] as $file) {
        $worker = json_decode((string) file_get_contents($file), true);
        $capAttempts += (int) ($worker['attempts'] ?? 0);
    }
    football_test($capAttempts === 1, 'concurrent feed and catalog reserve only the last aggregate slot');
    football_test(
        football_aggregate_count($capDirectory, $now) === 2500,
        'concurrent attempts cannot exceed the shared aggregate ceiling'
    );
}
$cleanupInterrupt($capDirectory);


$boundaryDirectory = sys_get_temp_dir() . '/hulk-football-boundary-' . bin2hex(random_bytes(4));
mkdir($boundaryDirectory);
file_put_contents(
    $boundaryDirectory . '/quota-aggregate-' . gmdate('Y-m-d', $now) . '.json',
    json_encode(['count' => 2499, 'day' => gmdate('Y-m-d', $now)])
);
$boundaryCalls = 0;
$boundaryTransport = static function () use (&$boundaryCalls): array {
    $boundaryCalls++;
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
};
$boundarySettings = array_merge($settings, [
    'cache_directory' => $boundaryDirectory,
    'max_requests_per_refresh' => 4,
]);
$boundaryFeed = ops_football_build_feed($boundarySettings, '2026-11-19', $now, $boundaryTransport);
football_test($boundaryCalls === 1, 'aggregate remaining budget caps requests at one');
football_test(
    $boundaryFeed['httpStatus'] === 503,
    'feed fails closed instead of publishing a fresh empty result after the last shared slot'
);
football_test(
    ($boundaryFeed['payload']['error'] ?? '') === 'provider_quota_exhausted',
    'shared boundary exhaustion is explicit'
);
$boundaryAggregate = json_decode(
    (string) file_get_contents($boundaryDirectory . '/quota-aggregate-' . gmdate('Y-m-d', $now) . '.json'),
    true
);
football_test((int) ($boundaryAggregate['count'] ?? 0) === 2500, 'aggregate boundary reaches exactly 2500');
$boundarySecond = ops_football_build_feed($boundarySettings, '2026-11-19', $now + 61, $boundaryTransport);
football_test($boundaryCalls === 1, 'no provider transport after the aggregate boundary');
football_test(
    $boundarySecond['httpStatus'] === 503
        && ($boundarySecond['payload']['error'] ?? '') === 'provider_quota_exhausted',
    'boundary exhaustion stays fail-closed without a last-good payload'
);
$cleanupBoundary = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupBoundary($boundaryDirectory);

$truncatedDirectory = sys_get_temp_dir() . '/hulk-football-truncated-' . bin2hex(random_bytes(4));
$truncatedSettings = array_merge($settings, [
    'cache_directory' => $truncatedDirectory,
    'max_requests_per_refresh' => 3,
]);
$truncatedTransport = static function (string $url): array {
    if (str_contains($url, '/leagues/')) {
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
    }
    if (str_contains($url, '/events/live/')) {
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'events' => []]), 'error' => null];
    }
    parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
    $offset = (int) ($query['offset'] ?? 0);
    $rows = [];
    for ($index = 0; $index < 200; $index++) {
        $rows[] = football_event(30_000 + $offset + $index, '2026-11-19T16:00:00Z', 'upcoming');
    }
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 1700, 'results' => $rows]), 'error' => null];
};
$truncated = ops_football_build_feed($truncatedSettings, '2026-11-19', $now, $truncatedTransport);
football_test($truncated['httpStatus'] === 503, 'eight full pages with counted rows behind fail closed');
football_test(($truncated['payload']['error'] ?? '') === 'provider_truncated', 'truncated fixture pagination is explicit');
football_test(!file_exists($truncatedDirectory . '/day-2026-11-19.json'), 'truncated fixture pagination is not cached as fresh');
$cleanupTruncated = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupTruncated($truncatedDirectory);

$reminderDirectory = sys_get_temp_dir() . '/hulk-football-reminder-' . bin2hex(random_bytes(4));
$reminderSettings = array_merge($settings, [
    'cache_directory' => $reminderDirectory,
    'max_requests_per_refresh' => 4,
]);
$reminderCalls = [];
$reminderTransport = static function (string $url) use (&$reminderCalls): array {
    $reminderCalls[] = $url;
    if (str_contains($url, '/leagues/')) {
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
    }
    if (str_contains($url, '/events/live/')) {
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'events' => []]), 'error' => null];
    }
    parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
    $from = (string) ($query['date_from'] ?? '');
    if ($from === '2026-11-19') {
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 1, 'results' => [
            football_event(41001, '2026-11-18T21:10:00Z', 'upcoming'),
        ]]), 'error' => null];
    }
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 1, 'results' => [
        football_event(41002, '2026-11-18T17:00:00Z', 'upcoming'),
    ]]), 'error' => null];
};
$reminderFeed = ops_football_build_feed($reminderSettings, '2026-11-18', $now, $reminderTransport);
football_test(count($reminderFeed['payload']['matches']) === 1, 'current-day feed keeps only today matches');
football_test(count($reminderFeed['payload']['reminderCandidates']) === 1, 'next Riyadh day reminder candidate is carried today');
football_test(
    array_filter($reminderCalls, static fn(string $url): bool => str_contains($url, '/broadcasts/')) === [],
    'eligible reminder is prioritized before optional broadcaster requests'
);
football_test($reminderFeed['payload']['reminderCandidates'][0]['providerEventId'] === '41001', 'tomorrow fixture is the reminder candidate');
$cleanupReminder = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupReminder($reminderDirectory);

$leagueRetryDirectory = sys_get_temp_dir() . '/hulk-football-league-retry-' . bin2hex(random_bytes(4));
$leagueRetrySettings = array_merge($settings, [
    'cache_directory' => $leagueRetryDirectory,
    'max_requests_per_refresh' => 4,
]);
$seedOk = static fn(): array => [
    'status' => 200, 'headers' => [], 'body' => json_encode(['results' => []]), 'error' => null,
];
ops_football_build_feed($leagueRetrySettings, '2026-11-18', $now, $seedOk);
$leagueRetryCalls = 0;
$leagueRetryTransport = static function (string $url) use (&$leagueRetryCalls): array {
    $leagueRetryCalls++;
    football_test(str_contains($url, '/leagues/'), 'league request is attempted first');
    return ['status' => 429, 'headers' => ['retry-after' => '120'], 'body' => '', 'error' => null];
};
$leagueRetry = ops_football_build_feed($leagueRetrySettings, '2026-11-18', $now + 61, $leagueRetryTransport);
football_test($leagueRetry['payload']['stale'] === true, 'league Retry-After keeps last-good feed');
football_test($leagueRetry['retryAfter'] === 120, 'league Retry-After is exposed');
football_test($leagueRetryCalls === 1, 'league Retry-After stops fixture and live calls');
$leagueRetryBlocked = ops_football_build_feed($leagueRetrySettings, '2026-11-18', $now + 62, $leagueRetryTransport);
football_test($leagueRetryBlocked['payload']['stale'] === true && $leagueRetryCalls === 1, 'league cooldown is shared');
$cleanupLeagueRetry = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupLeagueRetry($leagueRetryDirectory);

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
$catalogDirectory = sys_get_temp_dir() . '/hulk-football-catalog-' . bin2hex(random_bytes(4));
$catalogCalls = 0;
$catalogTransport = static function (string $url) use (&$catalogCalls): array {
    $catalogCalls++;
    parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
    $league = (int) ($query['league_id'] ?? 0);
    $offset = (int) ($query['offset'] ?? 0);
    $rows = [];
    if ($league === 1 && $offset === 0) {
        for ($id = 1; $id <= 200; $id++) {
            $rows[] = ['id' => $id, 'name' => 'Team '.$id, 'country' => 'England'];
        }
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 201, 'results' => $rows]), 'error' => null];
    }
    if ($league === 1 && $offset === 200) {
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 201, 'results' => [
            ['id' => 201, 'name' => 'Team 201', 'country' => 'England'],
        ]]), 'error' => null];
    }
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
};
$catalogSettings = [
    'cache_directory' => $catalogDirectory,
    'bzzoiro' => ['base_url' => 'https://sports.bzzoiro.com/api/v2', 'api_key' => 'fixture-token'],
    'catalog_league_ids' => [1],
    'catalog_scope' => 'explicit',
    'catalog_max_requests_per_day' => 10,
];
$catalog = ops_football_build_team_catalog($catalogSettings, $now, $catalogTransport);
football_test($catalog['httpStatus'] === 200, 'team catalog succeeds');
football_test(count($catalog['payload']['teams']) === 201, 'team catalog paginates past 200 teams');
football_test($catalogCalls === 2, 'team catalog uses two pages');
$catalogAgain = ops_football_build_team_catalog($catalogSettings, $now + 60, $catalogTransport);
football_test($catalogAgain['httpStatus'] === 200 && $catalogCalls === 2, 'team catalog cache avoids provider calls');
$catalogStale = ops_football_build_team_catalog($catalogSettings, $now + 90_000, static fn(): array => [
    'status' => 503, 'headers' => ['retry-after' => '30'], 'body' => '', 'error' => 'unavailable',
]);
football_test($catalogStale['httpStatus'] === 200 && $catalogStale['payload']['stale'] === true, 'team catalog keeps last-good on failure');
$cleanupCatalog = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupCatalog($catalogDirectory);

$pageDirectory = sys_get_temp_dir() . '/hulk-football-pages-' . bin2hex(random_bytes(4));
$pageCalls = 0;
$pageEvents = static function (int $start, int $count): array {
    $rows = [];
    for ($index = 0; $index < $count; $index++) {
        $rows[] = football_event(10_000 + $start + $index, '2026-11-20T16:00:00Z', 'upcoming');
    }
    return $rows;
};
$pageTransport = static function (string $url) use (&$pageCalls, $pageEvents): array {
    $pageCalls++;
    parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
    if (str_contains($url, '/leagues/')) {
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
    }
    if (str_contains($url, '/events/live/')) {
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'events' => []]), 'error' => null];
    }
    $offset = (int) ($query['offset'] ?? 0);
    if ($offset === 0) {
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 250, 'results' => $pageEvents(0, 200)]), 'error' => null];
    }
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 250, 'results' => $pageEvents(200, 50)]), 'error' => null];
};
$pageSettings = array_merge($settings, [
    'cache_directory' => $pageDirectory,
    'max_requests_per_refresh' => 3,
]);
$pageFeed = ops_football_build_feed($pageSettings, '2026-11-20', strtotime('2026-11-19T12:00:00Z'), $pageTransport);
football_test($pageFeed['httpStatus'] === 200, 'paginated day feed succeeds');
football_test(count($pageFeed['payload']['matches']) === 250, 'day feed accumulates both pages');
football_test($pageCalls === 3, 'paginated refresh stays inside the three-request budget');
$cleanupPages = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupPages($pageDirectory);

$catalogRetryDirectory = sys_get_temp_dir() . '/hulk-football-catalog-retry-' . bin2hex(random_bytes(4));
$catalogRetrySettings = [
    'cache_directory' => $catalogRetryDirectory,
    'bzzoiro' => ['base_url' => 'https://sports.bzzoiro.com/api/v2', 'api_key' => 'fixture-token'],
    'catalog_league_ids' => [1],
    'catalog_scope' => 'explicit',
    'catalog_max_requests_per_day' => 10,
];
$fullCatalogTransport = static function (): array {
    $rows = [];
    for ($id = 1; $id <= 200; $id++) $rows[] = ['id' => $id, 'name' => 'Old '.$id, 'country' => 'England'];
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 201, 'results' => $rows]), 'error' => null];
};
$fullCatalogTransport = static function (string $url) use ($fullCatalogTransport): array {
    parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
    $offset = (int) ($query['offset'] ?? 0);
    if ($offset === 0) return $fullCatalogTransport();
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 201, 'results' => [
        ['id' => 201, 'name' => 'Old 201', 'country' => 'England'],
    ]]), 'error' => null];
};
$firstCatalog = ops_football_build_team_catalog($catalogRetrySettings, $now, $fullCatalogTransport);
football_test($firstCatalog['payload']['complete'] === true, 'first complete catalog is marked complete');
$catalogCachePath = $catalogRetryDirectory . '/teams.json';
$completeCacheBytes = (string) file_get_contents($catalogCachePath);
$partialCalls = 0;
$partialTransport = static function (string $url) use (&$partialCalls): array {
    $partialCalls++;
    parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
    $offset = (int) ($query['offset'] ?? 0);
    if ($offset === 0) {
        $rows = [];
        for ($id = 9001; $id <= 9200; $id++) $rows[] = ['id' => $id, 'name' => 'Partial '.$id, 'country' => 'England'];
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 201, 'results' => $rows]), 'error' => null];
    }
    return ['status' => 429, 'headers' => ['retry-after' => '120'], 'body' => '', 'error' => null];
};
$partial = ops_football_build_team_catalog($catalogRetrySettings, $now + 90_001, $partialTransport);
football_test($partial['payload']['stale'] === true, 'partial refresh returns last complete stale');
football_test($partial['payload']['complete'] === true, 'stale response is a complete old catalog');
football_test(count($partial['payload']['teams']) === 201, 'partial refresh does not replace complete teams');
football_test(hash_equals(hash('sha256', $completeCacheBytes), hash('sha256', (string) file_get_contents($catalogCachePath))), 'partial refresh does not overwrite cache');
$blocked = ops_football_build_team_catalog($catalogRetrySettings, $now + 90_002, $fullCatalogTransport);
football_test($blocked['payload']['stale'] === true && $partialCalls === 2, 'Retry-After blocks subsequent provider calls');

$recoveredTransport = static function (string $url): array {
    parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
    $offset = (int) ($query['offset'] ?? 0);
    if ($offset === 0) {
        $rows = [];
        for ($id = 5001; $id <= 5200; $id++) $rows[] = ['id' => $id, 'name' => 'New '.$id, 'country' => 'England'];
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 201, 'results' => $rows]), 'error' => null];
    }
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 201, 'results' => [
        ['id' => 5201, 'name' => 'New 5201', 'country' => 'England'],
    ]]), 'error' => null];
};
$recovered = ops_football_build_team_catalog($catalogRetrySettings, $now + 90_001 + 121, $recoveredTransport);
football_test($recovered['payload']['complete'] === true && count($recovered['payload']['teams']) === 201, 'catalog recovers after Retry-After cooldown');
football_test($recovered['payload']['teams'][0]['name'] !== 'Old 1', 'recovered cache is new complete data');

$quotaDirectory = sys_get_temp_dir() . '/hulk-football-catalog-quota-' . bin2hex(random_bytes(4));
$quotaSettings = [
    'cache_directory' => $quotaDirectory,
    'bzzoiro' => ['base_url' => 'https://sports.bzzoiro.com/api/v2', 'api_key' => 'fixture-token'],
    'catalog_league_ids' => [1],
    'catalog_scope' => 'explicit',
    'catalog_max_requests_per_day' => 1,
];
$quotaCalls = 0;
$quotaTransport = static function () use (&$quotaCalls): array {
    $quotaCalls++;
    $rows = [];
    for ($id = 1; $id <= 200; $id++) $rows[] = ['id' => $id, 'name' => 'Quota '.$id, 'country' => 'England'];
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 201, 'results' => $rows]), 'error' => null];
};
$quotaCatalog = ops_football_build_team_catalog($quotaSettings, $now, $quotaTransport);
football_test($quotaCatalog['httpStatus'] === 503, 'one request left cannot produce a complete catalog');
football_test($quotaCalls === 1, 'quota is checked before the second page');
football_test(!file_exists($quotaDirectory . '/teams.json'), 'incomplete quota catalog is not cached');
$overflowDirectory = sys_get_temp_dir() . '/hulk-football-catalog-overflow-' . bin2hex(random_bytes(4));
$overflowSettings = [
    'cache_directory' => $overflowDirectory,
    'bzzoiro' => ['base_url' => 'https://sports.bzzoiro.com/api/v2', 'api_key' => 'fixture-token'],
    'catalog_league_ids' => [1],
    'catalog_scope' => 'explicit',
    'catalog_max_requests_per_day' => 10,
];
$overflowTransport = static function (string $url): array {
    parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
    $offset = (int) ($query['offset'] ?? 0);
    $rows = [];
    for ($id = $offset + 1; $id <= $offset + 200; $id++) {
        $rows[] = ['id' => $id, 'name' => 'Overflow '.$id, 'country' => 'England'];
    }
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 1700, 'results' => $rows]), 'error' => null];
};
$overflow = ops_football_build_team_catalog($overflowSettings, $now, $overflowTransport);
football_test($overflow['httpStatus'] === 503, 'eight full pages with counted rows behind fail closed');
football_test(!file_exists($overflowDirectory . '/teams.json'), 'incomplete two-page catalog is not cached');
$cleanupOverflow = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupOverflow($overflowDirectory);

$translitDirectory = sys_get_temp_dir() . '/hulk-football-translit-' . bin2hex(random_bytes(4));
$translitSettings = [
    'cache_directory' => $translitDirectory,
    'bzzoiro' => ['base_url' => 'https://sports.bzzoiro.com/api/v2', 'api_key' => 'fixture-token'],
    'catalog_league_ids' => [1],
    'catalog_scope' => 'explicit',
    'catalog_max_requests_per_day' => 10,
];
$translitCatalog = ops_football_build_team_catalog($translitSettings, $now, static function (): array {
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 1, 'results' => [
        ['id' => 99999, 'name' => 'Zyx United', 'country' => 'England'],
    ]]), 'error' => null];
});
$translitTeam = $translitCatalog['payload']['teams'][0];
football_test($translitTeam['name'] === 'Zyx United', 'original provider name is preserved');
football_test(is_string($translitTeam['arabicName']) && $translitTeam['arabicName'] !== '', 'catalog team gets an Arabic display fallback');
football_test($translitTeam['arabicName'] !== $translitTeam['name'], 'Arabic fallback is not the Latin name');
$cleanupTranslit = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupTranslit($translitDirectory);

$cleanupCatalogRetry = static function (string $path): void {
    if (!is_dir($path)) return;
    foreach (glob($path . '/*') ?: [] as $file) { if (is_file($file)) @unlink($file); }
    @rmdir($path);
};
$cleanupCatalogRetry($catalogRetryDirectory);
$cleanupCatalogRetry($quotaDirectory);

$cleanup($directory);
$cleanup($noCacheDirectory);
