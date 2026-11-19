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
const OPS_FOOTBALL_DEFAULT_MAX_REQUESTS_PER_DAY = 6000;
const OPS_FOOTBALL_MAX_REQUESTS_PER_DAY = 7500;
const OPS_FOOTBALL_DEFAULT_CATALOG_LEAGUES = [17, 53, 47, 1, 3, 4, 5, 6, 7, 8, 83, 31, 27, 61, 64, 68];
const OPS_FOOTBALL_NATIONAL_LEAGUE_IDS = [31, 27, 61, 58, 64, 68];
const OPS_FOOTBALL_DEFAULT_CATALOG_MAX_PER_DAY = 200;
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

function ops_football_arabic_team_names(): array
{
    return [
        '2415' => 'ابها', '3807' => 'العدالة', '258' => 'الاهلي', '6283' => 'العين',
        '7467' => 'الانصار', '3393' => 'الباطن', '260' => 'الاتفاق', '4909' => 'الفيصلي',
        '268' => 'الفتح', '269' => 'الفيحاء', '257' => 'الحزم', '262' => 'الهلال',
        '271' => 'الاتحاد', '6718' => 'الكوكب', '265' => 'الخليج', '261' => 'الخلود',
        '7240' => 'النهضة', '273' => 'النجمة', '267' => 'النصر', '270' => 'الاخدود',
        '3182' => 'العروبة', '272' => 'القادسية', '3255' => 'الرائد', '263' => 'الرياض',
        '264' => 'الشباب', '7076' => 'الشعلة', '266' => 'التعاون', '3357' => 'الطائي',
        '5424' => 'الوحدة', '256' => 'ضمك', '3256' => 'الدرعية', '6932' => 'هجر',
        '6815' => 'نجران', '259' => 'نيوم', '6628' => 'احد',
        // Verified provider IDs for major teams used by the approved Home/Match Center boards.
        '44' => 'برشلونة', '57' => 'ريال مدريد', '54' => 'اتلتيكو مدريد',
        '1' => 'ليفربول', '12' => 'مانشستر سيتي', '18' => 'ارسنال', '13' => 'تشيلسي',
        '17' => 'مانشستر يونايتد', '9' => 'توتنهام',
        '79' => 'بايرن ميونخ', '92' => 'بوروسيا دورتموند',
        '114' => 'باريس سان جيرمان', '73' => 'يوفنتوس', '77' => 'انتر ميلان', '63' => 'ميلان',
        // Verified national-team IDs from the World Cup catalog.
        '479' => 'السعودية', '478' => 'مصر', '490' => 'الجزائر', '464' => 'المغرب',
        '474' => 'تونس', '484' => 'الاردن', '933' => 'العراق', '461' => 'قطر',
        '489' => 'الارجنتين', '463' => 'البرازيل', '493' => 'انجلترا', '485' => 'فرنسا',
        '467' => 'المانيا', '475' => 'اسبانيا', '491' => 'البرتغال', '469' => 'هولندا',
        '453' => 'كوريا الجنوبية', '470' => 'اليابان', '481' => 'ايران', '451' => 'المكسيك',
        '457' => 'الولايات المتحدة', '459' => 'استراليا',
    ];
}

function ops_football_arabic_league_names(): array
{
    return [
        '17' => 'دوري روشن السعودي', '53' => 'البطولة المغربية', '47' => 'الرابطة التونسية',
        '1' => 'الدوري الإنجليزي', '3' => 'الدوري الإسباني', '4' => 'الدوري الإيطالي',
        '5' => 'الدوري الألماني', '6' => 'الدوري الفرنسي', '7' => 'دوري أبطال أوروبا',
        '8' => 'الدوري الأوروبي', '83' => 'دوري المؤتمر الأوروبي',
        '31' => 'مباريات دولية ودية', '27' => 'كأس العالم',
        '61' => 'تصفيات كأس العالم - آسيا', '58' => 'تصفيات كأس العالم - أوروبا',
        '64' => 'دوري الأمم الأوروبية', '68' => 'كأس آسيا',
    ];
}

/**
 * Deterministic ID-bound Arabic transliteration used only when no verified Arabic name exists.
 * Original provider names remain untouched and searchable; this produces a readable Arabic
 * display value for the exposed catalog instead of leaking a Latin name into the Arabic UI.
 */
function ops_football_arabic_transliteration(string $name): string
{
    $value = trim($name);
    if ($value === '') {
        return '';
    }
    $value = strtr($value, [
        'á' => 'a', 'à' => 'a', 'â' => 'a', 'ä' => 'a', 'å' => 'a', 'ã' => 'a',
        'é' => 'e', 'è' => 'e', 'ê' => 'e', 'ë' => 'e',
        'í' => 'i', 'ì' => 'i', 'î' => 'i', 'ï' => 'i',
        'ó' => 'o', 'ò' => 'o', 'ô' => 'o', 'ö' => 'o', 'õ' => 'o',
        'ú' => 'u', 'ù' => 'u', 'û' => 'u', 'ü' => 'u',
        'ç' => 'c', 'ñ' => 'n', 'ø' => 'o', 'ß' => 'ss',
    ]);
    $value = preg_replace('/\b(FC|CF|AFC|SC|AC|CD|UD|CA|SS|AS|US|Club|Football Club|Calcio)\b/iu', ' ', $value) ?? $value;
    $value = preg_replace('/[^a-zA-Z0-9 ]+/', ' ', $value) ?? $value;
    $words = preg_split('/\s+/', trim($value)) ?: [];
    $words = array_values(array_filter($words, static fn(string $word): bool => $word !== ''));
    if ($words === []) {
        return '';
    }
    $wordMap = [
        'al' => 'ال', 'el' => 'ال', 'real' => 'ريال', 'atletico' => 'اتلتيكو',
        'athletic' => 'اتلتيك', 'deportivo' => 'ديبورتيفو', 'sporting' => 'سبورتينغ',
        'united' => 'يونايتد', 'city' => 'سيتي', 'town' => 'تاون', 'rovers' => 'روفيرز',
        'wanderers' => 'واندررز', 'county' => 'كونتي', 'club' => 'كلوب',
    ];
    $noise = ['fc', 'cf', 'afc', 'sc', 'ac', 'cd', 'ud', 'ca', 'ss', 'as', 'us', 'de', 'la', 'le'];
    $translitWord = static function (string $word) use ($wordMap, $noise): string {
        $lower = strtolower($word);
        if (isset($wordMap[$lower])) {
            return $wordMap[$lower];
        }
        if (in_array($lower, $noise, true)) {
            return '';
        }
        $digraphs = [
            'tch' => 'تش', 'sch' => 'ش', 'sh' => 'ش', 'ch' => 'تش', 'kh' => 'خ',
            'gh' => 'غ', 'ph' => 'ف', 'th' => 'ث', 'dh' => 'ذ', 'ck' => 'ك',
            'qu' => 'ك', 'oo' => 'و', 'ou' => 'و', 'ee' => 'ي', 'ea' => 'ي',
            'ai' => 'اي', 'ay' => 'اي', 'ei' => 'اي', 'ey' => 'ي', 'oa' => 'و',
            'ue' => 'و', 'ui' => 'وي',
        ];
        $letters = [
            'a' => 'ا', 'b' => 'ب', 'c' => 'ك', 'd' => 'د', 'e' => '', 'f' => 'ف',
            'g' => 'غ', 'h' => 'ه', 'i' => 'ي', 'j' => 'ج', 'k' => 'ك', 'l' => 'ل',
            'm' => 'م', 'n' => 'ن', 'o' => 'و', 'p' => 'ب', 'q' => 'ق', 'r' => 'ر',
            's' => 'س', 't' => 'ت', 'u' => 'و', 'v' => 'ف', 'w' => 'و', 'x' => 'كس',
            'y' => 'ي', 'z' => 'ز',
        ];
        $result = '';
        $length = strlen($lower);
        for ($index = 0; $index < $length; $index++) {
            $remaining = substr($lower, $index);
            $matched = false;
            foreach ($digraphs as $digraph => $arabic) {
                if (str_starts_with($remaining, $digraph)) {
                    $result .= $arabic;
                    $index += strlen($digraph) - 1;
                    $matched = true;
                    break;
                }
            }
            if ($matched) {
                continue;
            }
            $char = $lower[$index];
            if ($char === 'c') {
                $next = $lower[$index + 1] ?? '';
                $result .= in_array($next, ['e', 'i', 'y'], true) ? 'س' : 'ك';
            } else {
                $result .= $letters[$char] ?? '';
            }
        }
        $result = preg_replace('/(.)\1+/u', '$1', $result) ?? $result;
        return trim($result);
    };
    $translated = [];
    $definite = false;
    foreach ($words as $word) {
        $lower = strtolower($word);
        if (!$definite && $translated === [] && in_array($lower, ['al', 'el'], true)) {
            $definite = true;
            continue;
        }
        $part = $translitWord($word);
        if ($part !== '') {
            $translated[] = $part;
        }
    }
    if ($translated === []) {
        return '';
    }
    if ($definite) {
        $translated[0] = 'ال' . $translated[0];
    }
    return implode(' ', $translated);
}

function ops_football_arabic_team_name(string $id, string $name): ?string
{
    $mapped = ops_football_arabic_team_names()[$id] ?? null;
    if ($mapped !== null && $mapped !== '') {
        return $mapped;
    }
    $transliterated = ops_football_arabic_transliteration($name);
    return $transliterated !== '' ? $transliterated : null;
}

function ops_football_arabic_competition_name(string $id, string $name): ?string
{
    $mapped = ops_football_arabic_league_names()[$id] ?? null;
    if ($mapped !== null && $mapped !== '') {
        return $mapped;
    }
    $transliterated = ops_football_arabic_transliteration($name);
    return $transliterated !== '' ? $transliterated : null;
}

function ops_football_league_priority(string $leagueId): int
{
    return match ($leagueId) {
        '17' => 10,
        '53', '47' => 30,
        '1', '3', '4', '5', '6', '7', '8', '83' => 40,
        '31', '27', '61', '58', '64', '68' => 35,
        default => 50,
    };
}

function ops_football_is_national_league(string $leagueId): bool
{
    return in_array($leagueId, array_map('strval', OPS_FOOTBALL_NATIONAL_LEAGUE_IDS), true);
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
    $numericId = ctype_digit($id) ? $id : null;
    return [
        'id' => $id,
        'name' => $name,
        'arabicName' => ops_football_arabic_team_name($id, $name),
        'logoUrl' => $numericId !== null
            ? 'https://sports.bzzoiro.com/img/team/' . $numericId . '/'
            : null,
        'country' => null,
        'competitionIds' => [],
        'nationalTeam' => false,
        'aliases' => [$name],
    ];
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
function ops_football_normalize_event(mixed $raw, array $leagueNames = []): ?array
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
    if ($competitionName === '' && isset($leagueNames[$competitionId])) {
        $competitionName = trim((string) $leagueNames[$competitionId]);
    }

    $national = ops_football_is_national_league($competitionId);
    $home['competitionIds'] = [$competitionId];
    $away['competitionIds'] = [$competitionId];
    $home['nationalTeam'] = $national;
    $away['nationalTeam'] = $national;

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
    $providerUpdatedAt = ops_football_parse_time(
        $raw['last_updated'] ?? $raw['updated_at'] ?? $raw['updatedAt'] ?? null
    );

    return [
        'providerEventId' => $eventId,
        'replacedBy' => $replacedBy === $eventId ? null : $replacedBy,
        'competition' => [
            'id' => $competitionId,
            'name' => $competitionName !== '' ? $competitionName : 'مسابقة',
            'country' => $competitionCountry !== '' ? $competitionCountry : null,
            'arabicName' => ops_football_arabic_competition_name($competitionId, $competitionName !== '' ? $competitionName : 'مسابقة'),
            'logoUrl' => ctype_digit($competitionId)
                ? 'https://sports.bzzoiro.com/img/league/' . $competitionId . '/'
                : null,
            'priority' => ops_football_league_priority($competitionId),
        ],
        'homeTeam' => $home,
        'awayTeam' => $away,
        'kickoffAt' => $kickoff,
        'status' => ops_football_normalize_status($raw['status'] ?? null),
        'homeScore' => $homeScore,
        'awayScore' => $awayScore,
        'minute' => $minute !== null && $minute >= 0 ? $minute : null,
        'providerUpdatedAt' => $providerUpdatedAt,
    ];
}

function ops_football_status_rank(string $status): int
{
    // A confirmed final result outranks an older live row. Live outranks scheduled.
    return match ($status) {
        'finished' => 4,
        'live' => 3,
        'upcoming' => 1,
        default => 0,
    };
}

/**
 * Merge duplicate schedule/live rows by verified provider event id.
 *
 * The schedule row owns fixture metadata (competition, teams, kickoff, replacement link). A live
 * or later row supplies only the fresher status/score/minute fields. This keeps a kickoff time
 * that a lightweight live payload might omit while never leaving an unresolved day status over a
 * confirmed live score.
 */
function ops_football_merge_event_rows(array $events): array
{
    $merged = [];
    $order = [];
    foreach ($events as $row) {
        $id = (string) ($row['providerEventId'] ?? '');
        if ($id === '') {
            continue;
        }
        if (!isset($merged[$id])) {
            $merged[$id] = $row;
            $order[] = $id;
            continue;
        }
        $base = $merged[$id];
        $baseRank = ops_football_status_rank((string) ($base['status'] ?? 'unresolved'));
        $rowRank = ops_football_status_rank((string) ($row['status'] ?? 'unresolved'));
        $rowHasDetail = ($row['minute'] ?? null) !== null
            || ($row['homeScore'] ?? null) !== null
            || ($row['awayScore'] ?? null) !== null;
        $baseHasDetail = ($base['minute'] ?? null) !== null
            || ($base['homeScore'] ?? null) !== null
            || ($base['awayScore'] ?? null) !== null;
        $baseUpdated = is_int($base['providerUpdatedAt'] ?? null) ? $base['providerUpdatedAt'] : 0;
        $rowUpdated = is_int($row['providerUpdatedAt'] ?? null) ? $row['providerUpdatedAt'] : 0;
        $newerCancellation = $rowUpdated > $baseUpdated && $rowUpdated > 0
            && in_array($row['status'], ['cancelled', 'postponed'], true);
        $newerFinalScore = $rowUpdated > $baseUpdated && $rowUpdated > 0
            && $row['status'] === 'finished' && $base['status'] === 'finished'
            && $rowHasDetail;
        $existingNewerCancellation = $baseUpdated > $rowUpdated && $baseUpdated > 0
            && in_array($base['status'], ['cancelled', 'postponed'], true);
        $takeRow = false;
        if ($newerCancellation || $newerFinalScore) {
            $takeRow = true;
        } elseif ($existingNewerCancellation) {
            $takeRow = false;
        } elseif ($rowRank > $baseRank) {
            $takeRow = true;
        } elseif ($rowRank === $baseRank && $row['status'] === 'live' && $base['status'] === 'live') {
            // The live endpoint is appended after the schedule list, so a later row is the fresher
            // observation. Higher elapsed minutes also win; equal-minute scores use the later row.
            $baseMinute = is_int($base['minute'] ?? null) ? $base['minute'] : -1;
            $rowMinute = is_int($row['minute'] ?? null) ? $row['minute'] : -1;
            $takeRow = $rowMinute > $baseMinute
                || ($rowMinute === $baseMinute && ($rowHasDetail || $baseHasDetail));
        } elseif ($rowRank === $baseRank && $row['status'] === 'finished' && $base['status'] === 'finished') {
            // Protect the first confirmed final result unless it lacks a score and the later row
            // carries the confirmed score.
            $takeRow = (!$baseHasDetail && $rowHasDetail);
        }
        if ($takeRow) {
            $base['status'] = $row['status'];
            // A fresher status row may omit a field that the previous row already verified.
            // Preserve the known score/minute instead of replacing it with null.
            $base['homeScore'] = $row['homeScore'] ?? $base['homeScore'] ?? null;
            $base['awayScore'] = $row['awayScore'] ?? $base['awayScore'] ?? null;
            $base['minute'] = $row['minute'] ?? $base['minute'] ?? null;
            $base['replacedBy'] = $row['replacedBy'] ?? $base['replacedBy'] ?? null;
        }
        if (!$takeRow && in_array($base['status'], ['finished', 'cancelled', 'postponed'], true)) {
            $base['homeScore'] = $base['homeScore'] ?? $row['homeScore'] ?? null;
            $base['awayScore'] = $base['awayScore'] ?? $row['awayScore'] ?? null;
        }
        $base['providerUpdatedAt'] = max(
            is_int($base['providerUpdatedAt'] ?? null) ? $base['providerUpdatedAt'] : 0,
            is_int($row['providerUpdatedAt'] ?? null) ? $row['providerUpdatedAt'] : 0,
        ) ?: null;
        if (
            (($base['competition']['name'] ?? '') === 'مسابقة' || ($base['competition']['name'] ?? '') === '')
            && ($row['competition']['name'] ?? '') !== ''
            && ($row['competition']['name'] ?? '') !== 'مسابقة'
        ) {
            $base['competition']['name'] = $row['competition']['name'];
            if (($base['competition']['country'] ?? null) === null) {
                $base['competition']['country'] = $row['competition']['country'] ?? null;
            }
        }
        $base['aliases'] = array_values(array_unique(array_merge(
            $base['aliases'] ?? [],
            $row['aliases'] ?? [],
        )));
        $merged[$id] = $base;
    }
    $result = [];
    foreach ($order as $id) {
        $result[] = $merged[$id];
    }
    return $result;
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
        // The aggregate intentionally appends the live endpoint after the day list. Use the
        // latest normalized row for the same provider id, otherwise an unresolved day row would
        // keep its status while the live row only supplied a score.
        $canonical = $byId[$event['providerEventId']] ?? $event;
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
 * Keep the requested Riyadh day plus matches that were already live at midnight. Finished
 * results from the previous day belong to the separate 24-hour kickoff history, not this rail.
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
            $carried = $event['status'] === 'live'
                && $kickoff >= $earliestCarry;
        }
        if (!$sameDay && !$carried) {
            continue;
        }
        $event['carriedOver'] = $carried;
        $selected[] = $event;
    }
    return $selected;
}

/**
 * Provider-confirmed start estimate. Kickoff is the provider `event_date`; a live/finished row
 * confirms that timestamp only once it is no later than the server clock. The frozen minute is
 * never used to invent a start.
 */
function ops_football_started_at(array $event, int $now): ?int
{
    $kickoff = (int) ($event['kickoffAt'] ?? 0);
    if ($kickoff <= 0) {
        return null;
    }
    if ($event['status'] === 'live' || $event['status'] === 'finished') {
        // Provider event_date is the only stable kickoff evidence in the list/live schema. The
        // elapsed minute freezes at halftime and must never be used to invent a start time.
        return $kickoff <= $now ? $kickoff : null;
    }
    return null;
}

function ops_football_match_payload(array $event, int $now): array
{
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
        'startedAt' => ops_football_started_at($event, $now),
        'providerUpdatedAt' => $event['providerUpdatedAt'] ?? null,
    ];
}

function ops_football_build_payload(
    array $events,
    string $day,
    int $now,
    bool $stale,
    ?int $cachedAt,
    ?int $retryAfterSeconds,
    int $refreshSeconds,
    array $leagueNames = [],
    ?string $reminderDay = null,
): array {
    $normalized = [];
    foreach ($events as $raw) {
        $event = ops_football_normalize_event($raw, $leagueNames);
        if ($event !== null) {
            $normalized[] = $event;
        }
    }
    $reconciled = ops_football_reconcile_replaced(ops_football_merge_event_rows($normalized));
    $selected = ops_football_select_day($reconciled, $day);
    $reminderEvents = $reminderDay !== null
        ? ops_football_select_day($reconciled, $reminderDay)
        : [];
    $reminderEvents = array_values(array_filter(
        $reminderEvents,
        static fn(array $event): bool => $event['status'] === 'upcoming'
            && (int) ($event['kickoffAt'] ?? 0) > $now
    ));
    usort($reminderEvents, static fn(array $left, array $right): int =>
        (int) ($left['kickoffAt'] ?? 0) <=> (int) ($right['kickoffAt'] ?? 0)
    );
    $reminderEvents = array_slice($reminderEvents, 0, 50);
    $historyEvents = array_values(array_filter(
        $reconciled,
        static function (array $event) use ($now): bool {
            $startedAt = ops_football_started_at($event, $now);
            return $startedAt !== null
                && $startedAt >= $now - 24 * 60 * 60
                && $startedAt <= $now;
        }
    ));
    usort($historyEvents, static function (array $left, array $right) use ($now): int {
        return (ops_football_started_at($right, $now) ?? 0)
            <=> (ops_football_started_at($left, $now) ?? 0);
    });
    $historyEvents = array_slice($historyEvents, 0, 200);
    usort($selected, static function (array $left, array $right): int {
        $statusOrder = ['live' => 0, 'upcoming' => 1, 'finished' => 2, 'postponed' => 3, 'cancelled' => 4, 'unresolved' => 5];
        $leftStatus = $statusOrder[$left['status']] ?? 5;
        $rightStatus = $statusOrder[$right['status']] ?? 5;
        return [$leftStatus, $left['kickoffAt'], $left['providerEventId']]
            <=> [$rightStatus, $right['kickoffAt'], $right['providerEventId']];
    });
    $selected = array_slice($selected, 0, OPS_FOOTBALL_MAX_EVENTS);

    $teams = [];
    foreach (array_merge($selected, $historyEvents, $reminderEvents) as $event) {
        foreach ([$event['homeTeam'], $event['awayTeam']] as $team) {
            $existing = $teams[$team['id']] ?? [];
            $mergedTeam = array_merge($existing, $team);
            $mergedTeam['competitionIds'] = array_values(array_unique(array_merge(
                $existing['competitionIds'] ?? [],
                $team['competitionIds'] ?? [],
            )));
            $mergedTeam['aliases'] = array_values(array_unique(array_filter(array_merge(
                $existing['aliases'] ?? [],
                $team['aliases'] ?? [],
            ))));
            $mergedTeam['nationalTeam'] = ($existing['nationalTeam'] ?? false) === true
                || ($team['nationalTeam'] ?? false) === true;
            $teams[$team['id']] = $mergedTeam;
        }
    }
    $teamList = array_values($teams);
    usort($teamList, static fn(array $left, array $right): int => strcmp($left['name'], $right['name']));

    $matches = array_map(
        static fn(array $event): array => ops_football_match_payload($event, $now),
        $selected,
    );
    $kickoffHistory = array_map(
        static fn(array $event): array => ops_football_match_payload($event, $now),
        $historyEvents,
    );
    $reminderCandidates = array_map(
        static fn(array $event): array => ops_football_match_payload($event, $now),
        $reminderEvents,
    );

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
        'kickoffHistory' => $kickoffHistory,
        'reminderCandidates' => $reminderCandidates,
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
 * Bzzoiro list responses use `results`; the live endpoint uses `events`. Accept both plus a raw
 * list without weakening the shape check.
 */
function ops_football_provider_rows(mixed $decoded): ?array
{
    if (!is_array($decoded)) {
        return null;
    }
    if (isset($decoded['results']) && is_array($decoded['results'])) {
        return array_values(array_filter($decoded['results'], 'is_array'));
    }
    if (isset($decoded['events']) && is_array($decoded['events'])) {
        return array_values(array_filter($decoded['events'], 'is_array'));
    }
    if (array_is_list($decoded)) {
        return array_values(array_filter($decoded, 'is_array'));
    }
    return null;
}

function ops_football_provider_count(mixed $decoded, array $rows): int
{
    if (is_array($decoded) && isset($decoded['count']) && is_numeric($decoded['count'])) {
        return max(0, (int) $decoded['count']);
    }
    return count($rows);
}

/**
 * One provider GET with the shared error/Retry-After contract.
 *
 * @return array{ok:bool,status:int,retryAfter:?int,rows:array,count:int,error:?string}
 */
function ops_football_provider_get(
    callable $transport,
    string $url,
    array $headers,
    int $timeoutSeconds,
    int $clock,
): array {
    $response = $transport($url, $headers, $timeoutSeconds);
    $status = (int) ($response['status'] ?? 0);
    $retryAfter = ops_football_parse_retry_after(
        $response['headers']['retry-after'] ?? null,
        $clock
    );
    if ($status === 429 || $status === 503) {
        return [
            'ok' => false,
            'status' => $status,
            'retryAfter' => $retryAfter ?? ($status === 429 ? 60 : 30),
            'rows' => [],
            'count' => 0,
            'error' => 'provider_unavailable',
        ];
    }
    if ($status < 200 || $status >= 300 || ($response['error'] ?? null) !== null) {
        return [
            'ok' => false,
            'status' => $status,
            'retryAfter' => $retryAfter,
            'rows' => [],
            'count' => 0,
            'error' => 'provider_unavailable',
        ];
    }
    $decoded = json_decode((string) ($response['body'] ?? ''), true);
    $rows = ops_football_provider_rows($decoded);
    if ($rows === null) {
        return [
            'ok' => false,
            'status' => $status,
            'retryAfter' => $retryAfter,
            'rows' => [],
            'count' => 0,
            'error' => 'provider_invalid_response',
        ];
    }
    return [
        'ok' => true,
        'status' => $status,
        'retryAfter' => $retryAfter,
        'rows' => $rows,
        'count' => ops_football_provider_count($decoded, $rows),
        'error' => null,
    ];
}

/**
 * Daily verified team catalog for the favorite-team picker. Teams are resolved by stable provider
 * IDs from configured competitions so an empty match day still has a searchable catalog.
 *
 * @return array{httpStatus:int,payload:array,retryAfter:?int}
 */
function ops_football_build_team_catalog(
    array $settings,
    ?int $now = null,
    ?callable $transport = null,
): array {
    $clock = $now ?? time();
    $refreshSeconds = 86_400;
    $directory = ops_football_cache_directory($settings);
    $cachePath = $directory . DIRECTORY_SEPARATOR . 'teams.json';
    $lockPath = $directory . DIRECTORY_SEPARATOR . 'teams.lock';
    $quotaPath = $directory . DIRECTORY_SEPARATOR . 'quota-teams-' . gmdate('Y-m-d', $clock) . '.json';
    $cache = ops_football_read_json($cachePath);
    if ($cache !== null && (($cache['payload']['complete'] ?? false) !== true)) {
        $cache = null;
    }
    $cacheAge = $cache !== null ? $clock - (int) ($cache['cachedAt'] ?? 0) : PHP_INT_MAX;
    if ($cache !== null && $cacheAge >= 0 && $cacheAge < $refreshSeconds) {
        $payload = is_array($cache['payload'] ?? null) ? $cache['payload'] : [];
        $payload['stale'] = false;
        $payload['complete'] = true;
        $payload['cachedAt'] = (int) ($cache['cachedAt'] ?? 0);
        return ['httpStatus' => 200, 'payload' => $payload, 'retryAfter' => null];
    }

    $baseUrl = rtrim(trim((string) ($settings['bzzoiro']['base_url'] ?? '')), '/');
    $apiKey = trim((string) ($settings['bzzoiro']['api_key'] ?? ''));
    if ($apiKey === '') {
        if ($cache !== null) {
            $payload = is_array($cache['payload'] ?? null) ? $cache['payload'] : [];
            $payload['stale'] = true;
            return ['httpStatus' => 200, 'payload' => $payload, 'retryAfter' => null];
        }
        return ['httpStatus' => 503, 'payload' => ['error' => 'provider_not_configured'], 'retryAfter' => null];
    }
    $parts = parse_url($baseUrl);
    if (!is_array($parts) || ($parts['scheme'] ?? '') !== 'https' || ($parts['host'] ?? '') !== 'sports.bzzoiro.com') {
        return ['httpStatus' => 503, 'payload' => ['error' => 'provider_not_configured'], 'retryAfter' => null];
    }

    $lock = @fopen($lockPath, 'c');
    if ($lock === false) {
        return $cache !== null
            ? ['httpStatus' => 200, 'payload' => array_merge($cache['payload'] ?? [], ['stale' => true]), 'retryAfter' => null]
            : ['httpStatus' => 503, 'payload' => ['error' => 'cache_unavailable'], 'retryAfter' => null];
    }
    if (!flock($lock, LOCK_EX | LOCK_NB)) {
        fclose($lock);
        return $cache !== null
            ? ['httpStatus' => 200, 'payload' => array_merge($cache['payload'] ?? [], ['stale' => true]), 'retryAfter' => null]
            : ['httpStatus' => 503, 'payload' => ['error' => 'refresh_busy'], 'retryAfter' => null];
    }

    try {
        $cache = ops_football_read_json($cachePath);
        if ($cache !== null && (($cache['payload']['complete'] ?? false) !== true)) {
            $cache = null;
        }
        $cacheAge = $cache !== null ? $clock - (int) ($cache['cachedAt'] ?? 0) : PHP_INT_MAX;
        if ($cache !== null && $cacheAge >= 0 && $cacheAge < $refreshSeconds) {
            $payload = is_array($cache['payload'] ?? null) ? $cache['payload'] : [];
            $payload['stale'] = false;
            $payload['complete'] = true;
            return ['httpStatus' => 200, 'payload' => $payload, 'retryAfter' => null];
        }
        $retryPath = $directory . DIRECTORY_SEPARATOR . 'retry.json';
        $retryState = ops_football_read_json($retryPath) ?? [];
        $retryUntil = (int) ($retryState['until'] ?? 0);
        if ($retryUntil > $clock) {
            $retryAfter = $retryUntil - $clock;
            if ($cache !== null) {
                $payload = is_array($cache['payload'] ?? null) ? $cache['payload'] : [];
                $payload['stale'] = true;
                $payload['complete'] = true;
                return ['httpStatus' => 200, 'payload' => $payload, 'retryAfter' => $retryAfter];
            }
            return ['httpStatus' => 503, 'payload' => ['error' => 'provider_rate_limited', 'complete' => false], 'retryAfter' => $retryAfter];
        }
        $quota = ops_football_read_json($quotaPath) ?? [];
        $used = max(0, (int) ($quota['count'] ?? 0));
        $maxPerDay = max(1, min(200, (int) ($settings['catalog_max_requests_per_day'] ?? OPS_FOOTBALL_DEFAULT_CATALOG_MAX_PER_DAY)));
        if ($used >= $maxPerDay) {
            return $cache !== null
                ? ['httpStatus' => 200, 'payload' => array_merge($cache['payload'] ?? [], ['stale' => true]), 'retryAfter' => null]
                : ['httpStatus' => 503, 'payload' => ['error' => 'provider_quota_exhausted'], 'retryAfter' => null];
        }
        $leagueIds = $settings['catalog_league_ids'] ?? OPS_FOOTBALL_DEFAULT_CATALOG_LEAGUES;
        if (!is_array($leagueIds)) {
            $leagueIds = OPS_FOOTBALL_DEFAULT_CATALOG_LEAGUES;
        }
        $leagueIds = array_values(array_filter(array_map('intval', $leagueIds), static fn(int $id): bool => $id > 0));
        $leagueIds = array_slice(array_unique($leagueIds), 0, 16);
        $headers = [
            'Authorization' => 'Token ' . $apiKey,
            'Accept' => 'application/json',
            'User-Agent' => 'NEO4K-Football-Catalog/1.0',
        ];
        $transport = $transport ?? 'ops_football_http_get';
        $timeoutSeconds = max(3, min(15, (int) ($settings['timeout_seconds'] ?? 8)));
        $teams = [];
        $usedRequests = 0;
        $successful = 0;
        $retryAfter = null;
        $complete = true;
        foreach ($leagueIds as $leagueId) {
            $offset = 0;
            $leagueReceived = 0;
            $leagueCount = 0;
            for ($page = 0; $page < 8; $page++) {
                if ($used + $usedRequests >= $maxPerDay) {
                    $complete = false;
                    break 2;
                }
                $retryState = ops_football_read_json($retryPath) ?? [];
                if ((int) ($retryState['until'] ?? 0) > $clock) {
                    $retryAfter = (int) $retryState['until'] - $clock;
                    $complete = false;
                    break 2;
                }
                $usedRequests++;
                $response = ops_football_provider_get(
                    $transport,
                    $baseUrl . '/teams/?league_id=' . $leagueId . '&limit=200&offset=' . $offset,
                    $headers,
                    $timeoutSeconds,
                    $clock,
                );
                if (!$response['ok']) {
                    $retryAfter = $response['retryAfter'] ?? $retryAfter;
                    $complete = false;
                    break 2;
                }
                $successful++;
                $leagueReceived += count($response['rows']);
                $leagueCount = max($leagueCount, $response['count']);
                foreach ($response['rows'] as $team) {
                    $id = ops_football_string_id($team['id'] ?? null);
                    $name = trim((string) ($team['name'] ?? ''));
                    if ($id !== null && $name !== '') {
                        $country = trim((string) ($team['country'] ?? '')) ?: null;
                        $national = ops_football_is_national_league((string) $leagueId);
                        $existing = $teams[$id] ?? null;
                        $competitionIds = array_values(array_unique(array_merge(
                            $existing['competitionIds'] ?? [],
                            [$leagueId],
                        )));
                        $aliases = array_values(array_unique(array_filter([
                            $name,
                            trim((string) ($team['short_name'] ?? '')),
                            $existing['name'] ?? '',
                        ])));
                        $teams[$id] = [
                            'id' => $id,
                            'name' => $name,
                            'arabicName' => $existing['arabicName'] ?? ops_football_arabic_team_name($id, $name),
                            'logoUrl' => ctype_digit($id)
                                ? 'https://sports.bzzoiro.com/img/team/' . $id . '/'
                                : null,
                            'country' => $country ?? ($existing['country'] ?? null),
                            'competitionIds' => $competitionIds,
                            'nationalTeam' => $national || (($existing['nationalTeam'] ?? false) === true),
                            'aliases' => $aliases,
                        ];
                    }
                }
                if (count($response['rows']) < 200) {
                    // A short page is complete only when the provider count agrees with what was
                    // returned. A short page that leaves counted rows behind is an incomplete
                    // refresh and must not become a fresh complete cache.
                    if ($offset + count($response['rows']) < $response['count']) {
                        $complete = false;
                        break 2;
                    }
                    break;
                }
                $offset += 200;
            }
            // Two full pages can still leave provider-counted rows behind. Never publish such a
            // catalog as complete, even though every requested page succeeded.
            if ($leagueCount > 0 && $leagueReceived < $leagueCount) {
                $complete = false;
                break;
            }
        }
        $quota['count'] = $used + $usedRequests;
        $quota['day'] = gmdate('Y-m-d', $clock);
        ops_football_write_json($quotaPath, $quota);
        if ($retryAfter !== null) {
            ops_football_write_json($directory . DIRECTORY_SEPARATOR . 'retry.json', ['until' => $clock + $retryAfter]);
        }
        if (!$complete || $successful === 0) {
            if ($cache !== null) {
                $payload = is_array($cache['payload'] ?? null) ? $cache['payload'] : [];
                $payload['stale'] = true;
                $payload['complete'] = true;
                return ['httpStatus' => 200, 'payload' => $payload, 'retryAfter' => $retryAfter];
            }
            return [
                'httpStatus' => 503,
                'payload' => ['error' => 'provider_unavailable', 'complete' => false],
                'retryAfter' => $retryAfter,
            ];
        }
        $teamList = array_values($teams);
        usort($teamList, static fn(array $left, array $right): int => strcmp($left['name'], $right['name']));
        $payload = [
            'schemaVersion' => OPS_FOOTBALL_SCHEMA_VERSION,
            'generatedAt' => $clock,
            'cachedAt' => $clock,
            'stale' => false,
            'complete' => true,
            'provider' => 'bzzoiro',
            'teams' => $teamList,
        ];
        ops_football_write_json($cachePath, ['cachedAt' => $clock, 'payload' => $payload]);
        return ['httpStatus' => 200, 'payload' => $payload, 'retryAfter' => $retryAfter];
    } finally {
        flock($lock, LOCK_UN);
        fclose($lock);
    }
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
    $maxPerRefresh = (int) ($settings['max_requests_per_refresh'] ?? 4);
    $maxPerRefresh = max(1, min(4, $maxPerRefresh));
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
        $transport = $transport ?? 'ops_football_http_get';
        $events = [];
        $reminderEvents = [];
        $leagueNames = [];
        $usedRequests = 0;
        $remaining = $budgetForRefresh;
        $failure = null;
        $rateLimitedFor = null;

        // League names are not embedded in the list event rows. Cache the provider league list for
        // one day; a failure here degrades names only and never blocks match rows.
        $leagueCachePath = $directory . DIRECTORY_SEPARATOR . 'leagues.json';
        $leagueCache = ops_football_read_json($leagueCachePath);
        $leagueCacheAge = $leagueCache !== null ? $clock - (int) ($leagueCache['cachedAt'] ?? 0) : PHP_INT_MAX;
        if ($leagueCache !== null && $leagueCacheAge >= 0 && $leagueCacheAge < 86_400) {
            foreach (($leagueCache['leagues'] ?? []) as $league) {
                if (is_array($league) && isset($league['id'], $league['name'])) {
                    $leagueNames[(string) $league['id']] = (string) $league['name'];
                }
            }
        } elseif ($remaining > 0) {
            $remaining--;
            $usedRequests++;
            $leagueResponse = ops_football_provider_get(
                $transport,
                $baseUrl . '/leagues/?limit=200',
                $headers,
                $timeoutSeconds,
                $clock,
            );
            if ($leagueResponse['ok']) {
                $cachedLeagues = [];
                foreach ($leagueResponse['rows'] as $league) {
                    if (isset($league['id'], $league['name'])) {
                        $leagueNames[(string) $league['id']] = (string) $league['name'];
                        $cachedLeagues[] = [
                            'id' => (string) $league['id'],
                            'name' => (string) $league['name'],
                        ];
                    }
                }
                if ($cachedLeagues !== []) {
                    ops_football_write_json($leagueCachePath, [
                        'cachedAt' => $clock,
                        'leagues' => $cachedLeagues,
                    ]);
                }
            } elseif ($leagueResponse['retryAfter'] !== null) {
                // A rate-limited league request means the shared provider window is closed for
                // every remaining fixture/catalog call in this refresh.
                $rateLimitedFor = $leagueResponse['retryAfter'];
                $failure = $leagueResponse['error'] ?? 'provider_unavailable';
            }
        }

        $dayBase = $baseUrl . '/events/?date_from=' . rawurlencode($previousDay)
            . '&date_to=' . rawurlencode($requestedDay) . '&limit=200';
        $dayOffset = 0;
        $dayTotal = 0;
        while ($failure === null && $remaining > 0) {
            $remaining--;
            $usedRequests++;
            $dayResponse = ops_football_provider_get(
                $transport,
                $dayBase . '&offset=' . $dayOffset,
                $headers,
                $timeoutSeconds,
                $clock,
            );
            if (!$dayResponse['ok']) {
                $failure = $dayResponse['error'];
                if ($dayResponse['retryAfter'] !== null) {
                    $rateLimitedFor = $dayResponse['retryAfter'];
                }
                break;
            }
            foreach ($dayResponse['rows'] as $row) {
                $events[] = $row;
            }
            $dayTotal = $dayResponse['count'];
            $returned = count($dayResponse['rows']);
            if ($returned === 0 || $returned < 200 || $dayOffset + $returned >= $dayTotal || $dayOffset >= 200) {
                break;
            }
            $dayOffset += 200;
        }
        $dayCollected = count($events);
        if ($failure === null && $dayTotal > $dayCollected) {
            // Never publish a truncated fixture page set as a complete fresh feed.
            $failure = 'provider_truncated';
        }

        if ($failure === null && $requestedDay === ops_football_riyadh_day_key($clock) && $remaining > 0) {
            $remaining--;
            $usedRequests++;
            $liveResponse = ops_football_provider_get(
                $transport,
                $baseUrl . '/events/live/',
                $headers,
                $timeoutSeconds,
                $clock,
            );
            if (!$liveResponse['ok']) {
                $failure = $liveResponse['error'];
                if ($liveResponse['retryAfter'] !== null) {
                    $rateLimitedFor = $liveResponse['retryAfter'];
                }
            } else {
                foreach ($liveResponse['rows'] as $row) {
                    $events[] = $row;
                }
            }
        }

        $todayKey = ops_football_riyadh_day_key($clock);
        $reminderDay = $requestedDay === $todayKey
            ? ops_football_riyadh_day_key($bounds['end'])
            : null;
        if ($failure === null && $reminderDay !== null && $remaining > 0) {
            $remaining--;
            $usedRequests++;
            $reminderResponse = ops_football_provider_get(
                $transport,
                $baseUrl . '/events/?date_from=' . rawurlencode($reminderDay)
                    . '&date_to=' . rawurlencode($reminderDay) . '&limit=200&offset=0',
                $headers,
                $timeoutSeconds,
                $clock,
            );
            if ($reminderResponse['ok']) {
                foreach ($reminderResponse['rows'] as $row) {
                    $reminderEvents[] = $row;
                }
            } elseif ($reminderResponse['retryAfter'] !== null) {
                // Reminder prefetch is optional: keep today's already-fetched data but record the
                // shared provider cooldown for the next request.
                $rateLimitedFor = $reminderResponse['retryAfter'];
                $reminderDay = null;
            }
        }

        $quota['count'] = $used + $usedRequests;
        $quota['day'] = gmdate('Y-m-d', $clock);
        ops_football_write_json($quotaPath, $quota);

        if ($rateLimitedFor !== null) {
            ops_football_write_json($retryPath, ['until' => $clock + $rateLimitedFor]);
        }
        if ($failure !== null) {
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
            events: array_merge($events, $reminderEvents),
            day: $requestedDay,
            now: $clock,
            stale: false,
            cachedAt: $clock,
            retryAfterSeconds: null,
            refreshSeconds: $refreshSeconds,
            leagueNames: $leagueNames,
            reminderDay: $reminderDay,
        );
        ops_football_write_json($cachePath, ['cachedAt' => $clock, 'payload' => $payload]);
        return ['httpStatus' => 200, 'payload' => $payload, 'retryAfter' => null, 'refreshSeconds' => $refreshSeconds];
    } finally {
        flock($lock, LOCK_UN);
        fclose($lock);
    }
}
