<?php

declare(strict_types=1);

function ops_active_release(PDO $db): ?array
{
    $statement = $db->query(
        'SELECT * FROM app_releases WHERE enabled = 1 AND is_active = 1 '
        . 'ORDER BY version_code DESC LIMIT 1'
    );
    $row = $statement->fetch();
    return is_array($row) ? $row : null;
}

/**
 * Return the only relative APK path shape the public resolver may serve.
 *
 * The stored path is never used as a URL or filesystem path until it passes
 * this exact allow-list. Encoded separators, query/fragment markers, traversal
 * and non-release paths fail closed.
 */
function ops_release_relative_apk_path(array $release): ?string
{
    $path = trim((string) ($release['apk_path'] ?? ''));
    if ($path === '' || strlen($path) > 240) {
        return null;
    }
    if (
        str_contains($path, '\\')
        || str_contains($path, '..')
        || str_contains($path, '%')
        || str_contains($path, '?')
        || str_contains($path, '#')
    ) {
        return null;
    }
    if (preg_match('#^releases/[A-Za-z0-9][A-Za-z0-9._-]*\.apk$#D', $path) !== 1) {
        return null;
    }

    return $path;
}

/**
 * Build the public APK URL from the trusted Operations base URL and a valid
 * release row. This is shared by the update API, the Control Center and /app/.
 */
function ops_release_public_apk_url(array $release): ?string
{
    $path = ops_release_relative_apk_path($release);
    $sha = strtolower(trim((string) ($release['apk_sha256'] ?? '')));
    if ($path === null || !ops_is_sha256($sha)) {
        return null;
    }

    $baseUrl = rtrim((string) (ops_load_config()['app']['base_url'] ?? ''), '/');
    $baseParts = parse_url($baseUrl);
    if (
        !is_array($baseParts)
        || ($baseParts['scheme'] ?? '') !== 'https'
        || empty($baseParts['host'])
    ) {
        return null;
    }

    $url = $baseUrl . '/' . $path;
    $parts = parse_url($url);
    if (
        !is_array($parts)
        || !filter_var($url, FILTER_VALIDATE_URL)
        || ($parts['scheme'] ?? '') !== 'https'
        || ($parts['host'] ?? '') !== $baseParts['host']
        || ($parts['port'] ?? null) !== ($baseParts['port'] ?? null)
        || isset($parts['user'])
        || isset($parts['pass'])
        || isset($parts['query'])
        || isset($parts['fragment'])
    ) {
        return null;
    }

    $basePath = rtrim((string) ($baseParts['path'] ?? ''), '/');
    $expectedPrefix = ($basePath === '' ? '' : $basePath) . '/releases/';
    if (!str_starts_with((string) ($parts['path'] ?? ''), $expectedPrefix)) {
        return null;
    }

    return $url;
}

/**
 * Verify that the active release points at an existing, readable APK inside
 * the Operations releases directory and that its byte count matches metadata.
 * The resolver performs this check without hashing the file on every request.
 */
function ops_release_artifact_is_readable(array $release): bool
{
    $path = ops_release_relative_apk_path($release);
    if ($path === null) {
        return false;
    }

    $releasesRoot = realpath(dirname(__DIR__) . '/releases');
    $absolutePath = realpath(dirname(__DIR__) . '/' . $path);
    if (
        $releasesRoot === false
        || $absolutePath === false
        || !str_starts_with($absolutePath, $releasesRoot . DIRECTORY_SEPARATOR)
        || !is_file($absolutePath)
        || !is_readable($absolutePath)
    ) {
        return false;
    }

    $size = filesize($absolutePath);
    $expectedSize = (int) ($release['apk_size_bytes'] ?? 0);
    if ($size === false || $size < 1 || $expectedSize < 1 || $size !== $expectedSize) {
        return false;
    }

    return true;
}

function ops_active_announcements(PDO $db, DateTimeImmutable $now): array
{
    $statement = $db->prepare(
        'SELECT * FROM app_announcements '
        . 'WHERE enabled = 1 AND starts_at <= :starts_now '
        . 'AND (ends_at IS NULL OR ends_at > :ends_now) '
        . "ORDER BY FIELD(severity, 'IMPORTANT', 'WARNING', 'INFO'), starts_at DESC, id DESC "
        . 'LIMIT 20'
    );
    $formattedNow = $now->format('Y-m-d H:i:s');
    $statement->execute([
        'starts_now' => $formattedNow,
        'ends_now' => $formattedNow,
    ]);
    return array_values(array_filter($statement->fetchAll(), static function (array $row) use ($now): bool {
        return ops_announcement_is_active($row, $now);
    }));
}

function ops_service_snapshot(PDO $db): array
{
    $statement = $db->query('SELECT * FROM app_service_status WHERE id = 1 LIMIT 1');
    $row = $statement->fetch();
    if (!is_array($row)) {
        return [
            'status' => 'OPERATIONAL',
            'message' => null,
            'startsAt' => null,
            'estimatedEndAt' => null,
        ];
    }

    $status = strtoupper((string) ($row['status'] ?? 'OPERATIONAL'));
    if (!in_array($status, ['OPERATIONAL', 'DEGRADED', 'MAINTENANCE'], true)) {
        $status = 'OPERATIONAL';
    }

    return [
        'status' => $status,
        'message' => ($row['message'] ?? null) ?: null,
        'startsAt' => ops_datetime_epoch($row['starts_at'] ?? null),
        'estimatedEndAt' => ops_datetime_epoch($row['estimated_end_at'] ?? null),
    ];
}

function ops_update_snapshot(PDO $db): array
{
    $release = ops_active_release($db);
    $latestCode = (int) ops_setting($db, 'latest_version_code', '64');
    $latestName = (string) ops_setting($db, 'latest_version_name', '0.9.3.20');
    $minimumCode = (int) ops_setting($db, 'minimum_supported_version_code', '64');
    $required = ops_setting($db, 'release_required', '0') === '1';

    if ($release !== null) {
        $latestCode = (int) $release['version_code'];
        $latestName = (string) $release['version_name'];
        $minimumCode = (int) $release['minimum_supported_version_code'];
        $required = (bool) $release['required'];
    }

    $apkUrl = $release === null ? null : ops_release_public_apk_url($release);
    $sha = $release === null ? null : strtolower((string) $release['apk_sha256']);
    if ($sha !== null && !ops_is_sha256($sha)) {
        $sha = null;
        $apkUrl = null;
    }

    return [
        'latestVersionCode' => max(64, $latestCode),
        'latestVersionName' => $latestName !== '' ? $latestName : '0.9.3.20',
        'minimumSupportedVersionCode' => max(64, min($minimumCode, max(64, $latestCode))),
        'updateType' => $required ? 'REQUIRED' : 'OPTIONAL',
        'required' => $required,
        'apkUrl' => $apkUrl,
        'apkSha256' => $sha,
        'releaseNotes' => $release === null ? '' : (string) $release['release_notes'],
    ];
}

function ops_announcement_payload(array $row): array
{
    return [
        'id' => (string) $row['message_key'],
        'enabled' => (bool) $row['enabled'],
        'title' => (string) $row['title'],
        'message' => (string) $row['message'],
        'severity' => (string) $row['severity'],
        'target' => (string) $row['target'],
        'showOnce' => (bool) $row['show_once'],
        'persistent' => (bool) $row['persistent'],
        'minimumVersionCode' => $row['minimum_version_code'] === null
            ? null
            : (int) $row['minimum_version_code'],
        'maximumVersionCode' => $row['maximum_version_code'] === null
            ? null
            : (int) $row['maximum_version_code'],
        'startsAt' => ops_datetime_epoch((string) $row['starts_at']),
        'endsAt' => ops_datetime_epoch($row['ends_at'] ?? null),
    ];
}

function ops_feature_snapshot(PDO $db): array
{
    $placeholders = implode(',', array_fill(0, count(ops_known_feature_flags()), '?'));
    $statement = $db->prepare(
        'SELECT flag_key, enabled FROM app_feature_flags WHERE flag_key IN (' . $placeholders . ')'
    );
    $statement->execute(ops_known_feature_flags());
    return ops_normalize_feature_flags($statement->fetchAll());
}

function ops_growth_snapshot(PDO $db): array
{
    $renewalUrl = ops_growth_renewal_url((string) ops_setting($db, 'growth_renewal_url', ''));
    $supportUrl = ops_growth_support_url((string) ops_setting($db, 'growth_support_url', ''));
    $renewalMode = ops_growth_qr_mode((string) ops_setting($db, 'growth_renewal_qr_mode', 'AUTO'))
        ?? 'AUTO';
    $supportMode = ops_growth_qr_mode((string) ops_setting($db, 'growth_support_qr_mode', 'AUTO'))
        ?? 'AUTO';
    $days = ops_growth_days_before_expiry(ops_setting($db, 'growth_renewal_banner_days', '7')) ?? 7;

    return [
        'enabled' => ops_setting($db, 'growth_enabled', '0') === '1',
        'renewal' => [
            'enabled' => ops_setting($db, 'growth_renewal_enabled', '0') === '1' && $renewalUrl !== null,
            'title' => ops_growth_safe_text(
                (string) ops_setting($db, 'growth_renewal_title', ''),
                'التجديد والموقع'
            ),
            'url' => $renewalUrl,
            'displayText' => ops_growth_safe_text(
                (string) ops_setting($db, 'growth_renewal_display_text', ''),
                'hulksa.com'
            ),
            'qrMode' => $renewalMode,
            'customQrUrl' => ops_growth_custom_qr_url($db, 'renewal'),
        ],
        'support' => [
            'enabled' => ops_setting($db, 'growth_support_enabled', '0') === '1' && $supportUrl !== null,
            'title' => ops_growth_safe_text(
                (string) ops_setting($db, 'growth_support_title', ''),
                'الدعم الفني'
            ),
            'url' => $supportUrl,
            'displayText' => ops_growth_safe_text(
                (string) ops_setting($db, 'growth_support_display_text', ''),
                'رقم واتساب HULK SA'
            ),
            'qrMode' => $supportMode,
            'customQrUrl' => ops_growth_custom_qr_url($db, 'support'),
        ],
        'renewalBanner' => [
            'enabled' => ops_setting($db, 'growth_renewal_banner_enabled', '0') === '1',
            'daysBeforeExpiry' => $days,
        ],
    ];
}

function ops_growth_custom_qr_url(PDO $db, string $slot): ?string
{
    $key = $slot === 'renewal' ? 'growth_renewal_custom_qr_path' : 'growth_support_custom_qr_path';
    $relativePath = trim((string) ops_setting($db, $key, ''));
    if (!ops_growth_custom_qr_path_is_safe($relativePath, $slot)) {
        return null;
    }
    $absolutePath = dirname(__DIR__) . '/' . $relativePath;
    if (!is_file($absolutePath)) {
        return null;
    }
    return ops_public_url($relativePath);
}

function ops_presence_discovery(?array $settings = null): ?array
{
    $presence = $settings ?? (ops_load_config()['presence'] ?? []);
    if (!is_array($presence) || ($presence['enabled'] ?? false) !== true) {
        return null;
    }

    $baseUrl = rtrim(trim((string) ($presence['base_url'] ?? '')), '/') . '/';
    $parts = parse_url($baseUrl);
    $heartbeat = (int) ($presence['heartbeat_seconds'] ?? 0);
    $onlineTtl = (int) ($presence['online_ttl_seconds'] ?? 0);
    if (
        !filter_var($baseUrl, FILTER_VALIDATE_URL)
        || !is_array($parts)
        || ($parts['scheme'] ?? '') !== 'https'
        || strtolower((string) ($parts['host'] ?? '')) !== 'hulksa.com'
        || ($parts['path'] ?? '') !== '/control-center/api/app/v1/presence/'
        || isset($parts['user'])
        || isset($parts['pass'])
        || isset($parts['query'])
        || isset($parts['fragment'])
        || $heartbeat < 15
        || $heartbeat > 600
        || $onlineTtl < $heartbeat * 2
        || $onlineTtl > 3600
    ) {
        throw new RuntimeException('Operations Presence discovery configuration is invalid.');
    }

    return [
        'enabled' => true,
        'baseUrl' => $baseUrl,
        'heartbeatSeconds' => $heartbeat,
        'onlineTtlSeconds' => $onlineTtl,
    ];
}

function ops_pairing_discovery(?array $settings = null): ?array
{
    $pairing = $settings ?? (ops_load_config()['pairing'] ?? []);
    if (!is_array($pairing) || ($pairing['enabled'] ?? false) !== true) {
        return null;
    }

    $baseUrl = rtrim(trim((string) ($pairing['base_url'] ?? '')), '/') . '/';
    $parts = parse_url($baseUrl);
    if (
        !filter_var($baseUrl, FILTER_VALIDATE_URL)
        || !is_array($parts)
        || ($parts['scheme'] ?? '') !== 'https'
        || strtolower((string) ($parts['host'] ?? '')) !== 'hulksa.com'
        || ($parts['path'] ?? '') !== '/control-center/api/app/v1/pairing/'
        || isset($parts['user'])
        || isset($parts['pass'])
        || isset($parts['query'])
        || isset($parts['fragment'])
    ) {
        throw new RuntimeException('Operations pairing discovery configuration is invalid.');
    }

    return [
        'enabled' => true,
        'baseUrl' => $baseUrl,
    ];
}

function ops_build_public_config(PDO $db, ?DateTimeImmutable $now = null): array
{
    $currentTime = $now ?? new DateTimeImmutable('now');
    $announcements = array_map(
        'ops_announcement_payload',
        ops_active_announcements($db, $currentTime)
    );

    $payload = [
        'schemaVersion' => 1,
        'generatedAt' => $currentTime->getTimestamp(),
        'service' => ops_service_snapshot($db),
        'update' => ops_update_snapshot($db),
        'announcement' => $announcements[0] ?? null,
        'announcements' => $announcements,
        'features' => ops_feature_snapshot($db),
        'growth' => ops_growth_snapshot($db),
    ];
    $presence = ops_presence_discovery();
    if ($presence !== null) {
        $payload['presence'] = $presence;
    }
    $pairing = ops_pairing_discovery();
    if ($pairing !== null) {
        $payload['pairing'] = $pairing;
    }
    return $payload;
}

function ops_public_config_json(array $payload): string
{
    return json_encode(
        $payload,
        JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR
    );
}

/**
 * Weak cache validator for the public config.
 *
 * The successful 200 body carries the advisory `generatedAt` timestamp and
 * therefore differs on every request even when the observable configuration is
 * unchanged. A strong validator over the exact body would rotate every second.
 * This derives a weak validator from the same payload with ONLY `generatedAt`
 * removed, so it stays stable while the configuration is unchanged and changes
 * whenever any returned semantic field changes (service, update, announcement/s,
 * features, growth, optional presence, schemaVersion).
 */
function ops_public_config_validator(array $payload): string
{
    $semantic = $payload;
    unset($semantic['generatedAt']);

    return 'W/"' . hash('sha256', ops_public_config_json($semantic)) . '"';
}

/**
 * Weak comparison of an If-None-Match condition against this endpoint's
 * validator. RFC 9110 requires weak comparison for If-None-Match, so the
 * emitted weak validator and its strong-equivalent opaque tag match alike.
 * Lists and the `*` wildcard are handled; entity-tags cannot contain commas.
 */
function ops_public_config_validator_matches(string $condition, string $validator): bool
{
    $condition = trim($condition);
    if ($condition === '') {
        return false;
    }
    if ($condition === '*') {
        return true;
    }

    $target = str_starts_with($validator, 'W/') ? substr($validator, 2) : $validator;
    foreach (explode(',', $condition) as $candidate) {
        $candidate = trim($candidate);
        if (str_starts_with($candidate, 'W/')) {
            $candidate = substr($candidate, 2);
        }
        if ($candidate !== '' && hash_equals($target, $candidate)) {
            return true;
        }
    }

    return false;
}
