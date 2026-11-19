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
$reminderTransport = static function (string $url): array {
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
