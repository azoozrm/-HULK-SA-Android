<?php

declare(strict_types=1);

function cc_dashboard_metric_definitions(): array
{
    return [
        'service_status' => 'ops_service_snapshot() من app_service_status.id=1؛ وهي نفس حالة عقد Operations العام.',
        'current_release' => 'ops_active_release(): إصدار enabled=1 وis_active=1 الأعلى version_code، أو لا يوجد إصدار APK نشط.',
        'current_version' => 'ops_update_snapshot().latestVersionName/latestVersionCode؛ نفس القيم المنشورة لتطبيق Android.',
        'minimum_version' => 'ops_update_snapshot().minimumSupportedVersionCode؛ نفس الحد الأدنى في عقد Operations.',
        'update_policy' => 'ops_update_snapshot().updateType؛ REQUIRED أو OPTIONAL حسب الإصدار/الإعداد الموثوق.',
        'current_announcements' => 'إعلانات enabled داخل نافذة starts_at/ends_at وتنجح في ops_announcement_is_active().',
        'enabled_features' => 'القيم true بعد ops_feature_snapshot() على قائمة ops_known_feature_flags() الحالية.',
        'total_resellers' => 'COUNT(*) من resellers.',
        'active_resellers' => "عدد resellers حيث status='active'.",
        'configured_hosts' => "موزع نشط يملك host حاليًا يقبله hulk_normalize_host().",
        'resolver_ready_codes' => 'موزع نشط بكود حالي canonical وهاش مطابق وهوست حالي صالح؛ وهي شروط قابلية resolver الحالية.',
        'recent_admin_activity' => 'آخر 8 صفوف من app_admin_audit مع action/admin/time فقط، بدون details.',
        'online_now' => 'جلسات غير منتهية وآخر نبضة لها داخل online_ttl_seconds حسب وقت الخادم.',
        'sessions_today' => 'عدد جلسات cc_app_sessions التي بدأها الخادم منذ بداية اليوم بتوقيت لوحة التحكم.',
        'active_devices' => 'معرّفات installation_id المميزة التي وصلت جلساتها نبضة Presence خلال آخر 24 ساعة، مع إظهار النافذة صراحة.',
        'presence_version_distribution' => 'توزيع أحدث إصدار جلسة مرصودة لكل installation_id وصلت له نبضة Presence خلال آخر 24 ساعة.',
        'users_today' => 'غير متاح لعدم وجود account_id أو هوية حساب مستقرة في cc_app_sessions.',
        'host_health_summary' => cc_phase7_metric_definitions()['host_health_summary'],
        'application_failures' => cc_phase7_metric_definitions()['application_failures'],
        'session_trend' => cc_phase7_metric_definitions()['session_trend'],
        'version_adoption_trend' => cc_phase7_metric_definitions()['version_adoption_trend'],
    ];
}

function cc_dashboard_isolated(callable $loader, string $authority): array
{
    try {
        $data = $loader();
        if (!is_array($data)) {
            throw new RuntimeException('Dashboard authority returned invalid data.');
        }
        return ['available' => true, 'data' => $data];
    } catch (Throwable $exception) {
        error_log('HULK Control Center dashboard authority unavailable: ' . $authority);
        return ['available' => false, 'data' => null];
    }
}

function cc_dashboard_compose(
    callable $operationsLoader,
    callable $resellerLoader,
    ?callable $presenceLoader = null,
    ?callable $hostHealthLoader = null,
    ?callable $diagnosticsLoader = null,
    ?callable $analyticsLoader = null
): array
{
    return [
        'definitions' => cc_dashboard_metric_definitions(),
        'operations' => cc_dashboard_isolated($operationsLoader, 'operations'),
        'reseller' => cc_dashboard_isolated($resellerLoader, 'reseller'),
        'presence' => $presenceLoader === null
            ? ['available' => false, 'data' => null]
            : cc_dashboard_isolated($presenceLoader, 'presence'),
        'host_health' => $hostHealthLoader === null
            ? ['available' => false, 'data' => null]
            : cc_dashboard_isolated($hostHealthLoader, 'host-health'),
        'diagnostics' => $diagnosticsLoader === null
            ? ['available' => false, 'data' => null]
            : cc_dashboard_isolated($diagnosticsLoader, 'diagnostics'),
        'analytics' => $analyticsLoader === null
            ? ['available' => false, 'data' => null]
            : cc_dashboard_isolated($analyticsLoader, 'analytics'),
    ];
}

function cc_dashboard_data(): array
{
    $config = cc_load_config();
    $timezone = new DateTimeZone((string) ($config['app']['timezone'] ?? 'Asia/Riyadh'));
    $clocks = cc_dashboard_clock_context(null, $timezone);
    return cc_dashboard_compose(
        static fn (): array => cc_dashboard_operations_snapshot(
            cc_db('control'),
            $clocks['operations_now']
        ),
        static fn (): array => cc_dashboard_reseller_snapshot(cc_db('reseller')),
        static fn (): array => cc_presence_dashboard_snapshot(
            cc_db('control'),
            static fn (array $ids): array => cc_presence_reseller_map(cc_db('reseller'), $ids),
            cc_presence_config(),
            $clocks['presence_now'],
            $timezone
        ),
        static fn (): array => cc_phase7_host_health_page(cc_db('control'), cc_db('reseller')),
        static fn (): array => cc_phase7_diagnostics_page(
            cc_db('control'),
            $clocks['presence_now'],
            (int) cc_diagnostics_config()['retention_days']
        ),
        static fn (): array => cc_phase7_analytics_page(cc_db('control'), $clocks['presence_now'])
    );
}

function cc_dashboard_clock_context(
    ?DateTimeImmutable $now,
    DateTimeZone $operationsTimezone
): array {
    $instant = $now ?? new DateTimeImmutable('now');
    return [
        'operations_now' => $instant->setTimezone($operationsTimezone),
        'presence_now' => cc_presence_now($instant),
    ];
}

function cc_dashboard_operations_snapshot(PDO $db, DateTimeImmutable $now): array
{
    $service = ops_service_snapshot($db);
    $update = ops_update_snapshot($db);
    $activeRelease = ops_active_release($db);
    $features = ops_feature_snapshot($db);

    $announcementStatement = $db->prepare(
        'SELECT enabled, starts_at, ends_at FROM app_announcements '
        . 'WHERE enabled = 1 AND starts_at <= :starts_now '
        . 'AND (ends_at IS NULL OR ends_at > :ends_now)'
    );
    $formattedNow = $now->format('Y-m-d H:i:s');
    $announcementStatement->execute(['starts_now' => $formattedNow, 'ends_now' => $formattedNow]);
    $currentAnnouncements = 0;
    while (($announcement = $announcementStatement->fetch()) !== false) {
        if (is_array($announcement) && ops_announcement_is_active($announcement, $now)) {
            $currentAnnouncements++;
        }
    }

    $audit = $db->query(
        'SELECT a.id, a.action, a.created_at, u.username '
        . 'FROM app_admin_audit a LEFT JOIN app_admin_users u ON u.id = a.admin_user_id '
        . 'ORDER BY a.id DESC LIMIT 8'
    )->fetchAll();

    return [
        'service' => $service,
        'update' => $update,
        'active_release' => $activeRelease,
        'current_announcements' => $currentAnnouncements,
        'enabled_features' => count(array_filter($features, static fn (bool $enabled): bool => $enabled)),
        'known_features' => count(ops_known_feature_flags()),
        'audit' => $audit,
    ];
}

function cc_dashboard_reseller_snapshot(PDO $db): array
{
    cc_reseller_require_domain();

    $aggregate = $db->query(
        "SELECT COUNT(*) AS total_resellers, "
        . "SUM(CASE WHEN status = 'active' THEN 1 ELSE 0 END) AS active_resellers "
        . 'FROM resellers'
    )->fetch();
    if (!is_array($aggregate)) {
        throw new RuntimeException('Reseller aggregate is unavailable.');
    }

    $configuredHosts = 0;
    $resolverReadyCodes = 0;
    $rows = $db->query(
        "SELECT host, access_code, access_code_hash FROM resellers WHERE status = 'active'"
    );
    while (($row = $rows->fetch()) !== false) {
        if (!is_array($row)) {
            continue;
        }
        $host = hulk_normalize_host((string) ($row['host'] ?? ''));
        if ($host !== null) {
            $configuredHosts++;
        }

        $code = hulk_normalize_access_code((string) ($row['access_code'] ?? ''));
        $storedHash = strtolower((string) ($row['access_code_hash'] ?? ''));
        if (
            $host !== null
            && $code !== null
            && preg_match('/^[a-f0-9]{64}$/D', $storedHash)
            && hash_equals(hulk_access_code_hash($code), $storedHash)
        ) {
            $resolverReadyCodes++;
        }
    }

    return [
        'total_resellers' => (int) ($aggregate['total_resellers'] ?? 0),
        'active_resellers' => (int) ($aggregate['active_resellers'] ?? 0),
        'configured_hosts' => $configuredHosts,
        'resolver_ready_codes' => $resolverReadyCodes,
    ];
}
