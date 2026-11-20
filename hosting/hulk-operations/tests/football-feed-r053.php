<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/lib/football-feed.php';

/**
 * R05.3 independent-review regressions.
 *
 * These checks are deterministic and offline: every provider response is injected, and the clock is
 * a mutable variable so the test can make a response arrive after the refresh started (including
 * across UTC midnight). The production code reads its own real clock; tests inject both clocks.
 */

$r053Checks = 0;
$r053Failures = [];

function r053_test(bool $condition, string $message): void
{
    global $r053Checks, $r053Failures;
    $r053Checks++;
    if (!$condition) {
        $r053Failures[] = $message;
    }
}

function r053_cleanup(string $path): void
{
    if (!is_dir($path)) {
        return;
    }
    foreach (glob($path . '/*') ?: [] as $file) {
        if (is_file($file)) {
            @unlink($file);
        }
    }
    @rmdir($path);
}

function r053_directory(string $label): string
{
    $path = sys_get_temp_dir() . '/hulk-football-r053-' . $label . '-' . bin2hex(random_bytes(4));
    mkdir($path);
    return $path;
}

function r053_settings(string $directory, array $overrides = []): array
{
    return array_merge([
        'refresh_seconds' => 60,
        'max_requests_per_day' => 500,
        'max_requests_per_refresh' => 4,
        'cache_directory' => $directory,
        'bzzoiro' => [
            'base_url' => 'https://sports.bzzoiro.com/api/v2',
            'api_key' => 'fixture-token',
        ],
    ], $overrides);
}

function r053_catalog_settings(string $directory, array $overrides = []): array
{
    return array_merge([
        'cache_directory' => $directory,
        'bzzoiro' => [
            'base_url' => 'https://sports.bzzoiro.com/api/v2',
            'api_key' => 'fixture-token',
        ],
        'catalog_league_ids' => [1],
        'catalog_scope' => 'explicit',
        'catalog_max_requests_per_day' => 200,
    ], $overrides);
}

function r053_event(int $id, string $kickoff, string $status): array
{
    return [
        'id' => $id,
        'event_date' => $kickoff,
        'status' => $status,
        'replaced_by' => null,
        'home_score' => null,
        'away_score' => null,
        'current_minute' => $status === 'live' ? 63 : null,
        'league' => ['id' => 17, 'name' => 'Saudi Pro League', 'country' => 'Saudi Arabia'],
        'home_team' => ['id' => 10, 'name' => 'Al Hilal'],
        'away_team' => ['id' => 20, 'name' => 'Al Nassr'],
    ];
}

function r053_ledger_count(string $directory, string $prefix, string $day): int
{
    $path = $directory . '/' . $prefix . '-' . $day . '.json';
    if (!is_file($path)) {
        return 0;
    }
    $decoded = json_decode((string) file_get_contents($path), true);
    return max(0, (int) ($decoded['count'] ?? 0));
}

function r053_write_ledger(string $directory, string $prefix, string $day, int $count): void
{
    file_put_contents(
        $directory . '/' . $prefix . '-' . $day . '.json',
        json_encode(['count' => $count, 'day' => $day])
    );
}

$r053Start = strtotime('2026-11-18T23:59:59Z');
$r053ResponseAt = strtotime('2026-11-19T00:00:11Z');
$r053Until = $r053ResponseAt + 10;
$r053DayOne = gmdate('Y-m-d', $r053Start);
$r053DayTwo = gmdate('Y-m-d', $r053ResponseAt);

// ---------------------------------------------------------------------------
// 1. Shared Retry-After is anchored to provider response receipt.
// ---------------------------------------------------------------------------

$r053RetryClock = $r053Start;
$r053Clock = static function () use (&$r053RetryClock): int {
    return $r053RetryClock;
};

// 1a. Catalog receives a late 429, then the feed route must stay closed for the full interval.
$r053CatalogFirstDirectory = r053_directory('retry-catalog-first');
$r053CatalogFirstCalls = 0;
$r053CatalogFirst = ops_football_build_team_catalog(
    r053_catalog_settings($r053CatalogFirstDirectory),
    $r053Start,
    static function () use (&$r053CatalogFirstCalls, &$r053RetryClock, $r053ResponseAt): array {
        $r053CatalogFirstCalls++;
        // The provider answers after midnight, twelve seconds after the refresh began.
        $r053RetryClock = $r053ResponseAt;
        return ['status' => 429, 'headers' => ['retry-after' => '10'], 'body' => '', 'error' => null];
    },
    $r053Clock,
    $r053Clock,
);
r053_test($r053CatalogFirst['httpStatus'] === 503, 'catalog 429 fails closed without a cache');
r053_test($r053CatalogFirstCalls === 1, 'catalog 429 makes one reserved transport attempt');

$r053RetryState = json_decode(
    (string) @file_get_contents($r053CatalogFirstDirectory . '/retry.json'),
    true
);
r053_test(
    (int) ($r053RetryState['until'] ?? 0) === $r053Until,
    'late catalog 429 cooldown starts at response receipt (expected '
        . $r053Until . ', stored ' . (int) ($r053RetryState['until'] ?? 0) . ')'
);

$r053FeedBlockedCalls = 0;
$r053FeedBlockedTransport = static function () use (&$r053FeedBlockedCalls): array {
    $r053FeedBlockedCalls++;
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
};
$r053FeedBlocked = ops_football_build_feed(
    r053_settings($r053CatalogFirstDirectory),
    '2026-11-19',
    $r053ResponseAt,
    $r053FeedBlockedTransport,
    $r053Clock,
);
r053_test($r053FeedBlocked['httpStatus'] === 503, 'catalog cooldown closes the feed route');
r053_test(
    ($r053FeedBlocked['payload']['error'] ?? '') === 'provider_rate_limited',
    'catalog cooldown reports provider_rate_limited on the feed route'
);
r053_test($r053FeedBlockedCalls === 0, 'catalog cooldown blocks feed transport');

$r053RetryClock = $r053Until - 1;
$r053FeedStillBlocked = ops_football_build_feed(
    r053_settings($r053CatalogFirstDirectory),
    '2026-11-19',
    $r053RetryClock,
    $r053FeedBlockedTransport,
    $r053Clock,
);
r053_test($r053FeedStillBlocked['httpStatus'] === 503, 'feed stays closed one second before the cooldown ends');
r053_test($r053FeedBlockedCalls === 0, 'feed still makes no provider call before the cooldown ends');

$r053RetryClock = $r053Until;
$r053FeedAllowed = ops_football_build_feed(
    r053_settings($r053CatalogFirstDirectory),
    '2026-11-19',
    $r053RetryClock,
    $r053FeedBlockedTransport,
    $r053Clock,
);
r053_test($r053FeedAllowed['httpStatus'] === 200, 'feed resumes when the receipt-anchored cooldown ends');
r053_test($r053FeedBlockedCalls > 0, 'feed reaches transport after the cooldown ends');
r053_cleanup($r053CatalogFirstDirectory);

// 1b. Feed receives a late 429, then the catalog route must stay closed for the full interval.
$r053FeedFirstDirectory = r053_directory('retry-feed-first');
$r053RetryClock = $r053Start;
$r053FeedFirstCalls = 0;
$r053FeedFirst = ops_football_build_feed(
    r053_settings($r053FeedFirstDirectory),
    '2026-11-19',
    $r053Start,
    static function () use (&$r053FeedFirstCalls, &$r053RetryClock, $r053ResponseAt): array {
        $r053FeedFirstCalls++;
        $r053RetryClock = $r053ResponseAt;
        return ['status' => 429, 'headers' => ['retry-after' => '10'], 'body' => '', 'error' => null];
    },
    $r053Clock,
    $r053Clock,
);
r053_test($r053FeedFirst['httpStatus'] === 503, 'feed 429 fails closed without a cache');
r053_test($r053FeedFirstCalls === 1, 'feed 429 makes one reserved transport attempt');
$r053FeedFirstRetryState = json_decode(
    (string) @file_get_contents($r053FeedFirstDirectory . '/retry.json'),
    true
);
r053_test(
    (int) ($r053FeedFirstRetryState['until'] ?? 0) === $r053Until,
    'late feed 429 cooldown starts at response receipt (expected '
        . $r053Until . ', stored ' . (int) ($r053FeedFirstRetryState['until'] ?? 0) . ')'
);

$r053CatalogBlockedCalls = 0;
$r053CatalogBlockedTransport = static function () use (&$r053CatalogBlockedCalls): array {
    $r053CatalogBlockedCalls++;
    return [
        'status' => 200,
        'headers' => [],
        'body' => json_encode([
            'count' => 1,
            'results' => [['id' => 1, 'name' => 'One', 'country' => 'Saudi Arabia']],
        ]),
        'error' => null,
    ];
};
$r053CatalogBlocked = ops_football_build_team_catalog(
    r053_catalog_settings($r053FeedFirstDirectory),
    $r053ResponseAt,
    $r053CatalogBlockedTransport,
    $r053Clock,
);
r053_test($r053CatalogBlocked['httpStatus'] === 503, 'feed cooldown closes the catalog route');
r053_test(
    ($r053CatalogBlocked['payload']['error'] ?? '') === 'provider_rate_limited',
    'feed cooldown reports provider_rate_limited on the catalog route'
);
r053_test($r053CatalogBlockedCalls === 0, 'feed cooldown blocks catalog transport');

$r053RetryClock = $r053Until - 1;
$r053CatalogStillBlocked = ops_football_build_team_catalog(
    r053_catalog_settings($r053FeedFirstDirectory),
    $r053RetryClock,
    $r053CatalogBlockedTransport,
    $r053Clock,
);
r053_test($r053CatalogStillBlocked['httpStatus'] === 503, 'catalog stays closed one second before the cooldown ends');
r053_test($r053CatalogBlockedCalls === 0, 'catalog still makes no provider call before the cooldown ends');

$r053RetryClock = $r053Until;
$r053CatalogAllowed = ops_football_build_team_catalog(
    r053_catalog_settings($r053FeedFirstDirectory),
    $r053RetryClock,
    $r053CatalogBlockedTransport,
    $r053Clock,
);
r053_test($r053CatalogAllowed['httpStatus'] === 200, 'catalog resumes when the receipt-anchored cooldown ends');
r053_test($r053CatalogBlockedCalls === 1, 'catalog reaches transport after the cooldown ends');
r053_cleanup($r053FeedFirstDirectory);

// Bounded HTTP-date parsing keeps the same receipt anchor.
r053_test(
    ops_football_parse_retry_after(
        gmdate('D, d M Y H:i:s \G\M\T', $r053Until),
        $r053ResponseAt
    ) === 10,
    'HTTP-date Retry-After is measured from response receipt'
);

// ---------------------------------------------------------------------------
// 2. Daily quota is decided per attempted UTC day, with independent route and
//    aggregate ceilings and per-attempt reservation before transport.
// ---------------------------------------------------------------------------

// 2a. Feed route rolls over at 499/500 across UTC midnight; the second attempt must use day two.
$r053FeedRolloverDirectory = r053_directory('quota-feed-rollover');
r053_write_ledger($r053FeedRolloverDirectory, 'quota', $r053DayOne, 499);
$r053FeedRollClock = $r053Start;
$r053FeedRollTransport = static function (string $url) use (&$r053FeedRollClock): array {
    $r053FeedRollClock = strtotime('2026-11-19T00:00:01Z');
    if (str_contains($url, '/leagues/')) {
        return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 0, 'results' => []]), 'error' => null];
    }
    if (str_contains($url, '/events/live/')) {
        return [
            'status' => 200,
            'headers' => [],
            'body' => json_encode(['count' => 1, 'results' => [
                r053_event(9102, '2026-11-19T00:01:00Z', 'live'),
            ]]),
            'error' => null,
        ];
    }
    return [
        'status' => 200,
        'headers' => [],
        'body' => json_encode(['count' => 1, 'results' => [
            r053_event(9101, '2026-11-19T18:00:00Z', 'upcoming'),
        ]]),
        'error' => null,
    ];
};
$r053FeedRollCalls = 0;
$r053FeedRollTransportCounted = static function (string $url) use ($r053FeedRollTransport, &$r053FeedRollCalls): array {
    $r053FeedRollCalls++;
    return $r053FeedRollTransport($url);
};
$r053FeedRoll = ops_football_build_feed(
    r053_settings($r053FeedRolloverDirectory, ['max_requests_per_refresh' => 3]),
    '2026-11-19',
    $r053Start,
    $r053FeedRollTransportCounted,
    static function () use (&$r053FeedRollClock): int {
        return $r053FeedRollClock;
    },
);
r053_test($r053FeedRoll['httpStatus'] === 200, 'feed crosses UTC midnight inside its per-refresh cap');
r053_test($r053FeedRollCalls === 3, 'feed uses league, day and live attempts after the rollover (got ' . $r053FeedRollCalls . ')');
r053_test(count($r053FeedRoll['payload']['matches'] ?? []) > 0, 'feed does not return an empty result from a stale daily budget');
r053_test(
    r053_ledger_count($r053FeedRolloverDirectory, 'quota', $r053DayOne) === 500,
    'feed stops exactly at the day-one route ceiling'
);
r053_test(
    r053_ledger_count($r053FeedRolloverDirectory, 'quota', $r053DayTwo) === 2,
    'feed charges the remaining attempts to day two'
);
r053_test(
    r053_ledger_count($r053FeedRolloverDirectory, 'quota-aggregate', $r053DayTwo) === 2,
    'feed charges the shared ceiling to day two'
);
r053_cleanup($r053FeedRolloverDirectory);

// 2b. Catalog route rolls over at 199/200 across UTC midnight and still completes page two.
$r053CatalogRolloverDirectory = r053_directory('quota-catalog-rollover');
r053_write_ledger($r053CatalogRolloverDirectory, 'quota-teams', $r053DayOne, 199);
r053_write_ledger($r053CatalogRolloverDirectory, 'quota-aggregate', $r053DayOne, 199);
$r053CatalogRollClock = $r053Start;
$r053CatalogRollCalls = 0;
$r053CatalogRoll = ops_football_build_team_catalog(
    r053_catalog_settings($r053CatalogRolloverDirectory),
    $r053Start,
    static function (string $url) use (&$r053CatalogRollCalls, &$r053CatalogRollClock): array {
        $r053CatalogRollCalls++;
        $r053CatalogRollClock = strtotime('2026-11-19T00:00:01Z');
        parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
        $offset = (int) ($query['offset'] ?? 0);
        if ($offset === 0) {
            $rows = [];
            for ($id = 1; $id <= 200; $id++) {
                $rows[] = ['id' => $id, 'name' => 'Rollover ' . $id, 'country' => 'England'];
            }
            return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 201, 'results' => $rows]), 'error' => null];
        }
        return [
            'status' => 200,
            'headers' => [],
            'body' => json_encode(['count' => 201, 'results' => [
                ['id' => 201, 'name' => 'Rollover 201', 'country' => 'England'],
            ]]),
            'error' => null,
        ];
    },
    static function () use (&$r053CatalogRollClock): int {
        return $r053CatalogRollClock;
    },
);
r053_test($r053CatalogRoll['httpStatus'] === 200, 'catalog crosses UTC midnight and completes');
r053_test($r053CatalogRollCalls === 2, 'catalog fetches both pages after the rollover (got ' . $r053CatalogRollCalls . ')');
r053_test(
    count($r053CatalogRoll['payload']['teams'] ?? []) === 201,
    'catalog publishes all 201 teams instead of a truncated empty authority'
);
r053_test(
    r053_ledger_count($r053CatalogRolloverDirectory, 'quota-teams', $r053DayOne) === 200,
    'catalog stops exactly at the day-one route ceiling'
);
r053_test(
    r053_ledger_count($r053CatalogRolloverDirectory, 'quota-teams', $r053DayTwo) === 1,
    'catalog charges its second page to day two'
);
r053_test(
    r053_ledger_count($r053CatalogRolloverDirectory, 'quota-aggregate', $r053DayTwo) === 1,
    'catalog charges its second page to the day-two shared ceiling'
);
r053_cleanup($r053CatalogRolloverDirectory);

// 2c. Route usage and remaining aggregate capacity are not double-subtracted.
$r053SharedDirectory = r053_directory('quota-shared-final');
r053_write_ledger($r053SharedDirectory, 'quota-teams', $r053DayOne, 5);
r053_write_ledger($r053SharedDirectory, 'quota-aggregate', $r053DayOne, 2499);
$r053SharedCalls = 0;
$r053SharedTransport = static function () use (&$r053SharedCalls): array {
    $r053SharedCalls++;
    $rows = [];
    for ($id = 1; $id <= 5; $id++) {
        $rows[] = ['id' => $id, 'name' => 'Final ' . $id, 'country' => 'Saudi Arabia'];
    }
    return ['status' => 200, 'headers' => [], 'body' => json_encode(['count' => 5, 'results' => $rows]), 'error' => null];
};
$r053Shared = ops_football_build_team_catalog(
    r053_catalog_settings($r053SharedDirectory),
    $r053Start,
    $r053SharedTransport,
);
r053_test($r053Shared['httpStatus'] === 200, 'catalog uses the last shared aggregate slot despite its own route usage');
r053_test($r053SharedCalls === 1, 'catalog makes exactly the one permitted shared attempt');
r053_test(
    r053_ledger_count($r053SharedDirectory, 'quota-teams', $r053DayOne) === 6,
    'catalog route ledger increments independently'
);
r053_test(
    r053_ledger_count($r053SharedDirectory, 'quota-aggregate', $r053DayOne) === 2500,
    'shared aggregate ledger reaches its own ceiling'
);
@unlink($r053SharedDirectory . '/teams.json');
$r053SharedFull = ops_football_build_team_catalog(
    r053_catalog_settings($r053SharedDirectory),
    $r053Start,
    $r053SharedTransport,
);
r053_test($r053SharedFull['httpStatus'] === 503, 'catalog fails closed once the shared ceiling is full');
r053_test(
    ($r053SharedFull['payload']['error'] ?? '') === 'provider_quota_exhausted',
    'shared ceiling gap is explicit'
);
r053_test($r053SharedCalls === 1, 'shared ceiling exhaustion makes no transport');
r053_cleanup($r053SharedDirectory);

// ---------------------------------------------------------------------------

if ($r053Failures === []) {
    echo 'PASS: ' . $r053Checks . " R05.3 football checks\n";
    exit(0);
}
foreach ($r053Failures as $failure) {
    fwrite(STDERR, 'FAIL: ' . $failure . "\n");
}
echo 'FAIL: ' . count($r053Failures) . ' of ' . $r053Checks . " R05.3 football checks\n";
exit(1);
