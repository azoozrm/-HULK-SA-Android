<?php

declare(strict_types=1);

/**
 * First-party football feed.
 *
 * Provider credentials stay in the server configuration. The Android client only knows this
 * endpoint. One server-owned refresh serves every device, with a per-day quota ledger, a refresh
 * lock, a last-good cache and provider Retry-After handling.
 */

const OPS_FOOTBALL_SCHEMA_VERSION = 1;
const OPS_FOOTBALL_TIMEZONE = 'Asia/Riyadh';
const OPS_FOOTBALL_DEFAULT_REFRESH_SECONDS = 60;
const OPS_FOOTBALL_MIN_REFRESH_SECONDS = 30;
const OPS_FOOTBALL_MAX_REFRESH_SECONDS = 300;
const OPS_FOOTBALL_DEFAULT_MAX_REQUESTS_PER_DAY = 500;
const OPS_FOOTBALL_MAX_REQUESTS_PER_DAY = 7500;
const OPS_FOOTBALL_CARRY_OVER_SECONDS = 6 * 60 * 60;
const OPS_FOOTBALL_MAX_EVENTS = 400;

function ops_football_statuses(): array
{
    return ['upcoming', 'live', 'finished', 'cancelled', 'postponed', 'unresolved'];
}

function ops_football_riyadh_day_key(int $timestamp): string
{
    $date = new DateTimeImmutable('@' . $timestamp);
    return $date->setTimezone(new DateTimeZone(OPS_FOOTBALL_TIMEZONE))->format('Y-m-d');
}

function ops_football_riyadh_day_bounds(string $day): ?array
{
    $parsed = DateTimeImmutable::createFromFormat(
        '!Y-m-d',
        $day,
        new DateTimeZone(OPS_FOOTBALL_TIMEZONE)
    );
    if (!$parsed instanceof DateTimeImmutable || $parsed->format('Y-m-d') !== $day) {
        return null;
    }
    $start = $parsed->getTimestamp();
    $end = $parsed->modify('+1 day')->getTimestamp();
    return ['start' => $start, 'end' => $end];
}

function ops_football_parse_time(mixed $value): ?int
{
    if (!is_string($value)) {
        return null;
    }
    $trimmed = trim($value);
    if ($trimmed === '') {
        return null;
    }
    $parsed = DateTimeImmutable::createFromFormat(DateTimeInterface::ATOM, $trimmed);
    if (!$parsed instanceof DateTimeImmutable) {
        $parsed = date_create_immutable($trimmed);
    }
    if (!$parsed instanceof DateTimeImmutable) {
        return null;
    }
    $timestamp = $parsed->getTimestamp();
    return $timestamp > 0 ? $timestamp : null;
}

function ops_football_nullable_int(mixed $value): ?int
{
    if ($value === null || $value === '' || $value === false) {
        return null;
    }
    if (!is_int($value) && !is_numeric($value)) {
        return null;
    }
    return (int) $value;
}

function ops_football_string_id(mixed $value): ?string
{
    if (is_int($value) || is_string($value)) {
        $normalized = trim((string) $value);
        if ($normalized !== '' && strlen($normalized) <= 80) {
            return $normalized;
        }
    }
    return null;
}

function ops_football_team_payload(mixed $team, mixed $fallbackId, mixed $fallbackName): ?array
{
    if (is_array($team)) {
        $id = ops_football_string_id($team['id'] ?? $fallbackId);
        $name = trim((string) ($team['name'] ?? $fallbackName ?? ''));
    } else {
        $id = ops_football_string_id($fallbackId);
        $name = trim((string) ($team ?? $fallbackName ?? ''));
    }
    if ($name === '' || strlen($name) > 120) {
        return null;
    }
    if ($id === null) {
        $id = 'name:' . strtolower(preg_replace('/\s+/u', ' ', $name) ?? $name);
    }
    return ['id' => $id, 'name' => $name];
}

function ops_football_normalize_status(mixed $status): string
{
    $normalized = strtolower(trim((string) $status));
    return match ($normalized) {
        'notstarted', 'not_started', 'scheduled', 'upcoming' => 'upcoming',
        'inprogress', 'in_progress', 'live' => 'live',
        'finished', 'ft', 'ended' => 'finished',
        'cancelled', 'canceled', 'abandoned', 'awarded', 'walkover' => 'cancelled',
        'postponed', 'suspended' => 'postponed',
        default => 'unresolved',
    };
}

/**
 * Normalize one Bzzoiro football event. Missing identity/teams/kickoff is rejected rather than
 * invented. Scores remain null when the provider has no confirmed score.
 */
function ops_football_normalize_event(mixed $raw): ?array
{
    if (!is_array($raw)) {
        return null;
    }
    $eventId = ops_football_string_id($raw['id'] ?? $raw['event_id'] ?? null);
    if ($eventId === null) {
        return null;
    }
    $home = ops_football_team_payload(
        $raw['home_team'] ?? $raw['homeTeam'] ?? null,
        $raw['home_team_id'] ?? $raw['homeTeamId'] ?? null,
        $raw['home_team_name'] ?? $raw['homeTeamName'] ?? null
    );
    $away = ops_football_team_payload(
        $raw['away_team'] ?? $raw['awayTeam'] ?? null,
        $raw['away_team_id'] ?? $raw['awayTeamId'] ?? null,
        $raw['away_team_name'] ?? $raw['awayTeamName'] ?? null
    );
    if ($home === null || $away === null || $home['id'] === $away['id']) {
        return null;
    }
    $kickoff = ops_football_parse_time(
        $raw['event_date'] ?? $raw['kickoff'] ?? $raw['start_time'] ?? null
    );
    if ($kickoff === null) {
        return null;
    }
    $league = $raw['league'] ?? null;
    $competitionId = is_array($league)
        ? ops_football_string_id($league['id'] ?? null)
        : ops_football_string_id($raw['league_id'] ?? null);
    $competitionName = is_array($league)
        ? trim((string) ($league['name'] ?? ''))
        : trim((string) ($raw['league_name'] ?? $raw['competition_name'] ?? ''));
    $competitionCountry = is_array($league)
        ? trim((string) ($league['country'] ?? ''))
        : trim((string) ($raw['league_country'] ?? $raw['country'] ?? ''));
    if ($competitionId === null) {
        $competitionId = $competitionName !== ''
            ? 'name:' . strtolower(preg_replace('/\s+/u', ' ', $competitionName) ?? $competitionName)
            : 'unknown';
    }

    $score = is_array($raw['score'] ?? null) ? $raw['score'] : [];
    $homeScore = ops_football_nullable_int(
        $raw['home_score'] ?? $score['home'] ?? $raw['homeScore'] ?? null
    );
    $awayScore = ops_football_nullable_int(
        $raw['away_score'] ?? $score['away'] ?? $raw['awayScore'] ?? null
    );
    $minute = ops_football_nullable_int(
        $raw['current_minute'] ?? $raw['minute'] ?? $raw['elapsed'] ?? null
    );
    $replacedBy = ops_football_string_id($raw['replaced_by'] ?? $raw['replacedBy'] ?? null);

    return [
        'providerEventId' => $eventId,
        'replacedBy' => $replacedBy === $eventId ? null : $replacedBy,
        'competition' => [
            'id' => $competitionId,
            'name' => $competitionName !== '' ? $competitionName : 'مسابقة',
            'country' => $competitionCountry !== '' ? $competitionCountry : null,
        ],
        'homeTeam' => $home,
        'awayTeam' => $away,
        'kickoffAt' => $kickoff,
        'status' => ops_football_normalize_status($raw['status'] ?? null),
        'homeScore' => $homeScore,
        'awayScore' => $awayScore,
        'minute' => $minute !== null && $minute >= 0 ? $minute : null,
    ];
}

/**
 * Resolve replaced_by chains to the replacement row when it is present. A postponed/cancelled
 * row whose replacement is outside the requested window is not shown on the old date.
 */
function ops_football_reconcile_replaced(array $events): array
{
    $byId = [];
    foreach ($events as $event) {
        $byId[$event['providerEventId']] = $event;
    }
    $aliasesByCanonical = [];
    foreach ($events as $event) {
        $current = $event;
        $seen = [];
        while (true) {
            $replacement = $current['replacedBy'] ?? null;
            if ($replacement === null || isset($seen[$replacement])) {
                break;
            }
            if (!isset($byId[$replacement])) {
                break;
            }
            $seen[$replacement] = true;
            $current = $byId[$replacement];
        }
        if ($current !== $event) {
            $aliasesByCanonical[$current['providerEventId']][] = $event['providerEventId'];
        }
    }

    $result = [];
    $seen = [];
    foreach ($events as $event) {
        $canonical = $event;
        $visited = [];
        while (true) {
            $replacement = $canonical['replacedBy'] ?? null;
            if ($replacement === null || isset($visited[$replacement])) {
                break;
            }
            if (!isset($byId[$replacement])) {
                // The replacement moved outside this window; do not keep the old fixture row.
                continue 2;
            }
            $visited[$replacement] = true;
            $canonical = $byId[$replacement];
        }
        $canonicalId = $canonical['providerEventId'];
        if (isset($seen[$canonicalId])) {
            continue;
        }
        $seen[$canonicalId] = true;
        $canonical['aliases'] = array_values(array_unique(
            $aliasesByCanonical[$canonicalId] ?? []
        ));
        $result[] = $canonical;
    }
    return $result;
}

/**
 * Keep the requested Riyadh day plus matches that were already live at midnight. A finished
 * match that kicked off before midnight is retained for a short bounded result window.
 */
function ops_football_select_day(array $events, string $day): array
{
    $bounds = ops_football_riyadh_day_bounds($day);
    if ($bounds === null) {
        return [];
    }
    $selected = [];
    foreach ($events as $event) {
        $kickoff = (int) ($event['kickoffAt'] ?? 0);
        if ($kickoff <= 0) {
            continue;
        }
        $sameDay = $kickoff >= $bounds['start'] && $kickoff < $bounds['end'];
        $carried = false;
        if (!$sameDay) {
            $earliestCarry = $bounds['start'] - OPS_FOOTBALL_CARRY_OVER_SECONDS;
            $isStarted = in_array($event['status'], ['live', 'finished'], true);
            $isResultWindow = $event['status'] === 'finished'
                && $kickoff >= $bounds['start'] - 3 * 60 * 60;
            $carried = $isStarted
                && $kickoff >= $earliestCarry
                && ($event['status'] === 'live' || $isResultWindow);
        }
        if (!$sameDay && !$carried) {
            continue;
        }
        $event['carriedOver'] = $carried;
        $selected[] = $event;
    }
    return $selected;
}

function ops_football_build_payload(
    array $events,
    string $day,
    int $now,
    bool $stale,
    ?int $cachedAt,
    ?int $retryAfterSeconds,
    int $refreshSeconds,
): array {
    $normalized = [];
    foreach ($events as $raw) {
        $event = ops_football_normalize_event($raw);
        if ($event !== null) {
            $normalized[] = $event;
        }
    }
    $selected = ops_football_select_day(ops_football_reconcile_replaced($normalized), $day);
    usort($selected, static function (array $left, array $right): int {
        $statusOrder = ['live' => 0, 'upcoming' => 1, 'finished' => 2, 'postponed' => 3, 'cancelled' => 4, 'unresolved' => 5];
        $leftStatus = $statusOrder[$left['status']] ?? 5;
        $rightStatus = $statusOrder[$right['status']] ?? 5;
        return [$leftStatus, $left['kickoffAt'], $left['providerEventId']]
            <=> [$rightStatus, $right['kickoffAt'], $right['providerEventId']];
    });
    $selected = array_slice($selected, 0, OPS_FOOTBALL_MAX_EVENTS);

    $teams = [];
    foreach ($selected as $event) {
        foreach ([$event['homeTeam'], $event['awayTeam']] as $team) {
            $teams[$team['id']] = ['id' => $team['id'], 'name' => $team['name']];
        }
    }
    $teamList = array_values($teams);
    usort($teamList, static fn(array $left, array $right): int => strcmp($left['name'], $right['name']));

    $matches = array_map(static function (array $event): array {
        return [
            'id' => 'evt:' . $event['providerEventId'],
            'providerEventId' => $event['providerEventId'],
            'aliases' => $event['aliases'] ?? [],
            'competition' => $event['competition'],
            'homeTeam' => $event['homeTeam'],
            'awayTeam' => $event['awayTeam'],
            'kickoffAt' => $event['kickoffAt'],
            'status' => $event['status'],
            'homeScore' => $event['homeScore'],
            'awayScore' => $event['awayScore'],
            'minute' => $event['minute'],
            'carriedOver' => $event['carriedOver'] ?? false,
        ];
    }, $selected);

    return [
        'schemaVersion' => OPS_FOOTBALL_SCHEMA_VERSION,
        'day' => $day,
        'timezone' => OPS_FOOTBALL_TIMEZONE,
        'generatedAt' => $now,
        'cachedAt' => $cachedAt,
        'stale' => $stale,
        'empty' => $matches === [],
        'retryAfterSeconds' => $retryAfterSeconds,
        'refreshSeconds' => $refreshSeconds,
        'provider' => 'bzzoiro',
        'matches' => $matches,
        'teams' => $teamList,
    ];
}

function ops_football_parse_retry_after(?string $value, int $now): ?int
{
    $trimmed = trim((string) $value);
    if ($trimmed === '') {
        return null;
    }
    if (ctype_digit($trimmed)) {
        return max(1, min(86_400, (int) $trimmed));
    }
    $date = strtotime($trimmed);
    if ($date === false) {
        return null;
    }
    return max(1, min(86_400, $date - $now));
}

/**
 * Bounded HTTP GET. Tests inject the same array shape through the transport callable.
 *
 * @return array{status:int,headers:array<string,string>,body:string,error:?string}
 */
function ops_football_http_get(string $url, array $headers, int $timeoutSeconds): array
{
    $headerLines = [];
    foreach ($headers as $name => $value) {
        $headerLines[] = $name . ': ' . $value;
    }
    if (function_exists('curl_init')) {
        $handle = curl_init($url);
        curl_setopt_array($handle, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HTTPHEADER => $headerLines,
            CURLOPT_CONNECTTIMEOUT => min(5, $timeoutSeconds),
            CURLOPT_TIMEOUT => $timeoutSeconds,
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_HEADER => true,
        ]);
        $raw = curl_exec($handle);
        $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
        $headerSize = (int) curl_getinfo($handle, CURLINFO_HEADER_SIZE);
        $error = $raw === false ? (string) curl_error($handle) : null;
        curl_close($handle);
        if ($raw === false) {
            return ['status' => $status, 'headers' => [], 'body' => '', 'error' => $error];
        }
        $rawHeaders = substr((string) $raw, 0, $headerSize);
        $body = substr((string) $raw, $headerSize);
        $parsedHeaders = [];
        foreach (preg_split('/\r\n|\n/', $rawHeaders) ?: [] as $line) {
            $position = strpos($line, ':');
            if ($position === false) {
                continue;
            }
            $parsedHeaders[strtolower(trim(substr($line, 0, $position)))] = trim(substr($line, $position + 1));
        }
        return ['status' => $status, 'headers' => $parsedHeaders, 'body' => $body, 'error' => null];
    }

    $context = stream_context_create([
        'http' => [
            'method' => 'GET',
            'header' => implode("\r\n", $headerLines),
            'timeout' => $timeoutSeconds,
            'ignore_errors' => true,
            'follow_location' => 0,
        ],
    ]);
    $body = @file_get_contents($url, false, $context);
    $responseHeaders = function_exists('http_get_last_response_headers')
        ? (http_get_last_response_headers() ?: [])
        : (isset($http_response_header) ? $http_response_header : []);
    $status = 0;
    $parsedHeaders = [];
    foreach ($responseHeaders as $line) {
        if (preg_match('#^HTTP/\S+\s+(\d{3})#', $line, $matches) === 1) {
            $status = (int) $matches[1];
            continue;
        }
        $position = strpos($line, ':');
        if ($position !== false) {
            $parsedHeaders[strtolower(trim(substr($line, 0, $position)))] = trim(substr($line, $position + 1));
        }
    }
    return [
        'status' => $status,
        'headers' => $parsedHeaders,
        'body' => $body === false ? '' : $body,
        'error' => $body === false ? 'transport_failed' : null,
    ];
}

function ops_football_cache_directory(array $settings): string
{
    $configured = trim((string) ($settings['cache_directory'] ?? ''));
    $directory = $configured !== ''
        ? $configured
        : rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR . 'hulk-football-feed';
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        throw new RuntimeException('Football feed cache directory is unavailable.');
    }
    return rtrim($directory, DIRECTORY_SEPARATOR);
}

function ops_football_read_json(string $path): ?array
{
    if (!is_file($path)) {
        return null;
    }
    $raw = @file_get_contents($path);
    if (!is_string($raw) || $raw === '') {
        return null;
    }
    $decoded = json_decode($raw, true);
    return is_array($decoded) ? $decoded : null;
}

function ops_football_write_json(string $path, array $payload): bool
{
    $encoded = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if (!is_string($encoded)) {
        return false;
    }
    $temporary = $path . '.tmp.' . bin2hex(random_bytes(4));
    if (@file_put_contents($temporary, $encoded, LOCK_EX) === false) {
        return false;
    }
    @chmod($temporary, 0600);
    if (!@rename($temporary, $path)) {
        @unlink($temporary);
        return false;
    }
    return true;
}

function ops_football_cached_payload(array $cache, int $refreshSeconds): array
{
    $payload = is_array($cache['payload'] ?? null) ? $cache['payload'] : [];
    $payload['stale'] = false;
    $payload['cachedAt'] = (int) ($cache['cachedAt'] ?? 0);
    $payload['refreshSeconds'] = $refreshSeconds;
    $payload['retryAfterSeconds'] = null;
    return $payload;
}

/**
 * Build one server-owned feed snapshot.
 *
 * @return array{httpStatus:int,payload:array,retryAfter:?int,refreshSeconds:int}
 */
function ops_football_build_feed(
    array $settings,
    ?string $day = null,
    ?int $now = null,
    ?callable $transport = null,
): array {
    $clock = $now ?? time();
    if ($day !== null && preg_match('/^\d{4}-\d{2}-\d{2}$/D', $day) !== 1) {
        return [
            'httpStatus' => 400,
            'payload' => ['error' => 'invalid_day'],
            'retryAfter' => null,
            'refreshSeconds' => OPS_FOOTBALL_DEFAULT_REFRESH_SECONDS,
        ];
    }
    $requestedDay = $day ?? ops_football_riyadh_day_key($clock);
    if (ops_football_riyadh_day_bounds($requestedDay) === null) {
        return [
            'httpStatus' => 400,
            'payload' => ['error' => 'invalid_day'],
            'retryAfter' => null,
            'refreshSeconds' => OPS_FOOTBALL_DEFAULT_REFRESH_SECONDS,
        ];
    }

    $refreshSeconds = (int) ($settings['refresh_seconds'] ?? OPS_FOOTBALL_DEFAULT_REFRESH_SECONDS);
    $refreshSeconds = max(
        OPS_FOOTBALL_MIN_REFRESH_SECONDS,
        min(OPS_FOOTBALL_MAX_REFRESH_SECONDS, $refreshSeconds)
    );
    $maxPerDay = (int) ($settings['max_requests_per_day'] ?? OPS_FOOTBALL_DEFAULT_MAX_REQUESTS_PER_DAY);
    $maxPerDay = max(1, min(OPS_FOOTBALL_MAX_REQUESTS_PER_DAY, $maxPerDay));
    $maxPerRefresh = (int) ($settings['max_requests_per_refresh'] ?? 2);
    $maxPerRefresh = max(1, min(2, $maxPerRefresh));
    $timeoutSeconds = max(3, min(15, (int) ($settings['timeout_seconds'] ?? 8)));

    $directory = ops_football_cache_directory($settings);
    $cachePath = $directory . DIRECTORY_SEPARATOR . 'day-' . $requestedDay . '.json';
    $quotaPath = $directory . DIRECTORY_SEPARATOR . 'quota-' . gmdate('Y-m-d', $clock) . '.json';
    $retryPath = $directory . DIRECTORY_SEPARATOR . 'retry.json';
    $lockPath = $directory . DIRECTORY_SEPARATOR . 'feed.lock';

    $cache = ops_football_read_json($cachePath);
    $retry = ops_football_read_json($retryPath) ?? [];
    $retryUntil = (int) ($retry['until'] ?? 0);
    if ($retryUntil > $clock) {
        $retryAfter = $retryUntil - $clock;
        if ($cache !== null) {
            $stale = ops_football_cached_payload($cache, $refreshSeconds);
            $stale['stale'] = true;
            $stale['retryAfterSeconds'] = $retryAfter;
            return [
                'httpStatus' => 200,
                'payload' => $stale,
                'retryAfter' => $retryAfter,
                'refreshSeconds' => $refreshSeconds,
            ];
        }
        return [
            'httpStatus' => 503,
            'payload' => ['error' => 'provider_rate_limited', 'retryAfterSeconds' => $retryAfter],
            'retryAfter' => $retryAfter,
            'refreshSeconds' => $refreshSeconds,
        ];
    }

    $cacheAge = $cache !== null ? $clock - (int) ($cache['cachedAt'] ?? 0) : PHP_INT_MAX;
    if ($cache !== null && $cacheAge >= 0 && $cacheAge < $refreshSeconds) {
        return [
            'httpStatus' => 200,
            'payload' => ops_football_cached_payload($cache, $refreshSeconds),
            'retryAfter' => null,
            'refreshSeconds' => $refreshSeconds,
        ];
    }

    $lock = @fopen($lockPath, 'c');
    if ($lock === false) {
        if ($cache !== null) {
            $stale = ops_football_cached_payload($cache, $refreshSeconds);
            $stale['stale'] = true;
            return ['httpStatus' => 200, 'payload' => $stale, 'retryAfter' => null, 'refreshSeconds' => $refreshSeconds];
        }
        return ['httpStatus' => 503, 'payload' => ['error' => 'cache_unavailable'], 'retryAfter' => null, 'refreshSeconds' => $refreshSeconds];
    }
    if (!flock($lock, LOCK_EX | LOCK_NB)) {
        fclose($lock);
        if ($cache !== null) {
            $stale = ops_football_cached_payload($cache, $refreshSeconds);
            $stale['stale'] = true;
            return ['httpStatus' => 200, 'payload' => $stale, 'retryAfter' => null, 'refreshSeconds' => $refreshSeconds];
        }
        return ['httpStatus' => 503, 'payload' => ['error' => 'refresh_busy'], 'retryAfter' => null, 'refreshSeconds' => $refreshSeconds];
    }

    try {
        // Another worker may have refreshed while this request waited for the lock.
        $cache = ops_football_read_json($cachePath);
        $cacheAge = $cache !== null ? $clock - (int) ($cache['cachedAt'] ?? 0) : PHP_INT_MAX;
        if ($cache !== null && $cacheAge >= 0 && $cacheAge < $refreshSeconds) {
            return [
                'httpStatus' => 200,
                'payload' => ops_football_cached_payload($cache, $refreshSeconds),
                'retryAfter' => null,
                'refreshSeconds' => $refreshSeconds,
            ];
        }

        $quota = ops_football_read_json($quotaPath) ?? [];
        $used = max(0, (int) ($quota['count'] ?? 0));
        $budgetForRefresh = min($maxPerRefresh, max(0, $maxPerDay - $used));
        if ($budgetForRefresh <= 0) {
            if ($cache !== null) {
                $stale = ops_football_cached_payload($cache, $refreshSeconds);
                $stale['stale'] = true;
                return ['httpStatus' => 200, 'payload' => $stale, 'retryAfter' => null, 'refreshSeconds' => $refreshSeconds];
            }
            return ['httpStatus' => 503, 'payload' => ['error' => 'provider_quota_exhausted'], 'retryAfter' => null, 'refreshSeconds' => $refreshSeconds];
        }

        $baseUrl = rtrim(trim((string) ($settings['bzzoiro']['base_url'] ?? '')), '/');
        $apiKey = trim((string) ($settings['bzzoiro']['api_key'] ?? ''));
        if ($apiKey === '') {
            if ($cache !== null) {
                $stale = ops_football_cached_payload($cache, $refreshSeconds);
                $stale['stale'] = true;
                return ['httpStatus' => 200, 'payload' => $stale, 'retryAfter' => null, 'refreshSeconds' => $refreshSeconds];
            }
            return ['httpStatus' => 503, 'payload' => ['error' => 'provider_not_configured'], 'retryAfter' => null, 'refreshSeconds' => $refreshSeconds];
        }
        $parts = parse_url($baseUrl);
        if (
            !is_array($parts)
            || ($parts['scheme'] ?? '') !== 'https'
            || ($parts['host'] ?? '') !== 'sports.bzzoiro.com'
        ) {
            return ['httpStatus' => 503, 'payload' => ['error' => 'provider_not_configured'], 'retryAfter' => null, 'refreshSeconds' => $refreshSeconds];
        }

        $bounds = ops_football_riyadh_day_bounds($requestedDay);
        $previousDay = ops_football_riyadh_day_key($bounds['start'] - 1);
        $headers = [
            'Authorization' => 'Token ' . $apiKey,
            'Accept' => 'application/json',
            'User-Agent' => 'NEO4K-Football-Feed/1.0',
        ];
        $requests = [[
            'url' => $baseUrl . '/events/?date_from=' . rawurlencode($previousDay)
                . '&date_to=' . rawurlencode($requestedDay) . '&limit=200',
        ]];
        if ($requestedDay === ops_football_riyadh_day_key($clock) && $budgetForRefresh >= 2) {
            $requests[] = ['url' => $baseUrl . '/events/live/'];
        }
        $requests = array_slice($requests, 0, $budgetForRefresh);

        $events = [];
        $usedRequests = 0;
        $failure = null;
        $rateLimitedFor = null;
        $transport = $transport ?? 'ops_football_http_get';
        foreach ($requests as $request) {
            $usedRequests++;
            $response = $transport($request['url'], $headers, $timeoutSeconds);
            $status = (int) ($response['status'] ?? 0);
            $retryAfter = ops_football_parse_retry_after(
                $response['headers']['retry-after'] ?? null,
                $clock
            );
            if ($status === 429 || $status === 503) {
                $rateLimitedFor = $retryAfter ?? ($status === 429 ? 60 : 30);
                $failure = 'provider_unavailable';
                break;
            }
            if ($status < 200 || $status >= 300 || ($response['error'] ?? null) !== null) {
                $failure = 'provider_unavailable';
                break;
            }
            $decoded = json_decode((string) ($response['body'] ?? ''), true);
            $rows = is_array($decoded)
                ? (isset($decoded['results']) && is_array($decoded['results']) ? $decoded['results'] : $decoded)
                : null;
            if (!is_array($rows)) {
                $failure = 'provider_invalid_response';
                break;
            }
            foreach ($rows as $row) {
                if (is_array($row)) {
                    $events[] = $row;
                }
            }
        }

        $quota['count'] = $used + $usedRequests;
        $quota['day'] = gmdate('Y-m-d', $clock);
        ops_football_write_json($quotaPath, $quota);

        if ($failure !== null) {
            if ($rateLimitedFor !== null) {
                ops_football_write_json($retryPath, ['until' => $clock + $rateLimitedFor]);
            }
            if ($cache !== null) {
                $stale = ops_football_cached_payload($cache, $refreshSeconds);
                $stale['stale'] = true;
                $stale['retryAfterSeconds'] = $rateLimitedFor;
                return [
                    'httpStatus' => 200,
                    'payload' => $stale,
                    'retryAfter' => $rateLimitedFor,
                    'refreshSeconds' => $refreshSeconds,
                ];
            }
            return [
                'httpStatus' => 503,
                'payload' => ['error' => $failure],
                'retryAfter' => $rateLimitedFor,
                'refreshSeconds' => $refreshSeconds,
            ];
        }

        $payload = ops_football_build_payload(
            events: $events,
            day: $requestedDay,
            now: $clock,
            stale: false,
            cachedAt: $clock,
            retryAfterSeconds: null,
            refreshSeconds: $refreshSeconds,
        );
        ops_football_write_json($cachePath, ['cachedAt' => $clock, 'payload' => $payload]);
        return ['httpStatus' => 200, 'payload' => $payload, 'retryAfter' => null, 'refreshSeconds' => $refreshSeconds];
    } finally {
        flock($lock, LOCK_UN);
        fclose($lock);
    }
}
