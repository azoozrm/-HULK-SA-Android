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

    $apkUrl = $release === null ? null : ops_public_url((string) $release['apk_path']);
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

function ops_build_public_config(PDO $db, ?DateTimeImmutable $now = null): array
{
    $currentTime = $now ?? new DateTimeImmutable('now');
    $announcements = array_map(
        'ops_announcement_payload',
        ops_active_announcements($db, $currentTime)
    );

    return [
        'schemaVersion' => 1,
        'generatedAt' => $currentTime->getTimestamp(),
        'service' => ops_service_snapshot($db),
        'update' => ops_update_snapshot($db),
        'announcement' => $announcements[0] ?? null,
        'announcements' => $announcements,
        'features' => ops_feature_snapshot($db),
        'growth' => ops_growth_snapshot($db),
    ];
}
