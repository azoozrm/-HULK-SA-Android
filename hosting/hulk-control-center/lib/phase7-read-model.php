<?php

declare(strict_types=1);

require_once __DIR__ . '/host-health.php';
require_once __DIR__ . '/diagnostics.php';

const CC_PHASE7_HISTORY_LIMIT = 200;
const CC_PHASE7_ANALYTICS_DAYS = 30;

function cc_phase7_metric_definitions(): array
{
    return [
        'host_health_summary' => 'الحالة الحالية لكل resellers.host صالح تُشتق من أحدث فحوص fingerprint نفسه؛ فشل نقل واحد يظهر TRANSIENT_FAILURE وفشلان متتاليان يظهران UNREACHABLE.',
        'host_health_history' => 'آخر 200 ملاحظة محفوظة مرتبة بمعرّف الفحص؛ تغيير الهوست لا يعيد كتابة fingerprint التاريخي.',
        'application_failures' => 'عدد cc_diagnostic_events المستلمة خلال نافذة 30 يومًا؛ الجدول يعرض أعلى 50 مجموعة event_type وerror_code ويصرح إذا كانت القراءة جزئية.',
        'failures_by_version' => 'أعلى 25 مجموعة لأحداث التشخيص خلال 30 يومًا حسب app_version_name وapp_version_code المخزنين مع الحدث، مع حالة جزئية صريحة.',
        'failures_by_device' => 'أعلى 25 مجموعة لأحداث التشخيص خلال 30 يومًا حسب platform_class والشركة والطراز المخزنة مع الحدث، مع حالة جزئية صريحة.',
        'failures_by_host' => 'أعلى 25 مجموعة لأحداث التشخيص خلال 30 يومًا حسب host_fingerprint المشتق من host_snapshot للجلسة، مع حالة جزئية صريحة.',
        'session_trend' => 'جلسات cc_app_sessions مجمعة حسب DATE(started_at) خلال آخر 30 يومًا.',
        'version_adoption_trend' => 'آخر جلسة لكل device_id في كل يوم ثم تجميع الأجهزة حسب إصدار التطبيق لذلك اليوم خلال آخر 30 يومًا؛ العرض محدود بـ500 صف ويصرح إذا كان جزئيًا.',
        'current_adoption_distribution' => 'أعلى 25 إصدارًا من أحدث جلسة لكل device_id وصلت لها نبضة خلال آخر 24 ساعة، من Presence فقط وليس تنزيلات APK، مع حالة جزئية صريحة.',
    ];
}

function cc_phase7_health_state(array $checks): string
{
    if ($checks === []) {
        return 'NOT_CHECKED';
    }
    $latest = (string) ($checks[0]['probe_result'] ?? '');
    if ($latest === 'HEALTHY') {
        return 'HEALTHY';
    }
    if ($latest === 'HTTP_ERROR') {
        return 'DEGRADED';
    }
    if ($latest === 'POLICY_BLOCKED') {
        return 'DEGRADED';
    }
    $previous = (string) ($checks[1]['probe_result'] ?? '');
    $transportFailures = [
        'DNS_FAILURE', 'CONNECT_FAILURE', 'TIMEOUT', 'TLS_FAILURE', 'NETWORK_FAILURE',
    ];
    return in_array($latest, $transportFailures, true)
        && in_array($previous, $transportFailures, true)
        ? 'UNREACHABLE'
        : 'TRANSIENT_FAILURE';
}

function cc_phase7_health_rows_by_id(PDO $controlDb, array $ids): array
{
    if ($ids === []) {
        return [];
    }
    $ids = array_values(array_map('intval', $ids));
    $rows = $controlDb->query(
        'SELECT id, reseller_id, host_fingerprint, checked_at, probe_result, dns_ok, tcp_ok, '
        . 'http_status, latency_ms, failure_code FROM cc_host_health_checks WHERE id IN ('
        . implode(',', $ids) . ')'
    )->fetchAll();
    $byId = [];
    foreach ($rows as $row) {
        if (is_array($row)) {
            $byId[(int) $row['id']] = $row;
        }
    }
    return $byId;
}

function cc_phase7_current_health_checks(PDO $controlDb, array $targets): array
{
    if ($targets === []) {
        return [];
    }
    $conditions = [];
    $parameters = [];
    foreach (array_values($targets) as $index => $target) {
        $conditions[] = '(reseller_id = :reseller_' . $index . ' AND host_fingerprint = :fingerprint_' . $index . ')';
        $parameters['reseller_' . $index] = (int) $target['reseller_id'];
        $parameters['fingerprint_' . $index] = (string) $target['host_fingerprint'];
    }
    $where = implode(' OR ', $conditions);
    $latest = $controlDb->prepare(
        'SELECT reseller_id, host_fingerprint, MAX(id) AS check_id FROM cc_host_health_checks '
        . 'WHERE ' . $where . ' GROUP BY reseller_id, host_fingerprint'
    );
    $latest->execute($parameters);
    $latestByTarget = [];
    $latestIds = [];
    foreach ($latest->fetchAll() as $row) {
        if (!is_array($row)) {
            continue;
        }
        $key = (int) $row['reseller_id'] . ':' . (string) $row['host_fingerprint'];
        $latestByTarget[$key] = (int) $row['check_id'];
        $latestIds[] = (int) $row['check_id'];
    }
    $previousByTarget = [];
    if ($latestIds !== []) {
        $previous = $controlDb->prepare(
            'SELECT reseller_id, host_fingerprint, MAX(id) AS check_id FROM cc_host_health_checks '
            . 'WHERE (' . $where . ') AND id NOT IN (' . implode(',', $latestIds) . ') '
            . 'GROUP BY reseller_id, host_fingerprint'
        );
        $previous->execute($parameters);
        foreach ($previous->fetchAll() as $row) {
            if (!is_array($row)) {
                continue;
            }
            $key = (int) $row['reseller_id'] . ':' . (string) $row['host_fingerprint'];
            $previousByTarget[$key] = (int) $row['check_id'];
        }
    }
    $rowsById = cc_phase7_health_rows_by_id(
        $controlDb,
        array_merge($latestIds, array_values($previousByTarget))
    );
    $checks = [];
    foreach ($latestByTarget as $key => $latestId) {
        if (isset($rowsById[$latestId])) {
            $checks[$key][] = $rowsById[$latestId];
        }
        $previousId = $previousByTarget[$key] ?? null;
        if ($previousId !== null && isset($rowsById[$previousId])) {
            $checks[$key][] = $rowsById[$previousId];
        }
    }
    return $checks;
}

function cc_phase7_host_health_page(PDO $controlDb, PDO $resellerDb): array
{
    $eligibleRowCount = cc_host_health_candidate_count($resellerDb);
    $targets = cc_host_health_candidates($resellerDb, 500);
    $history = $controlDb->query(
        'SELECT id, reseller_id, host_fingerprint, checked_at, probe_result, dns_ok, tcp_ok, '
        . 'http_status, latency_ms, failure_code FROM cc_host_health_checks '
        . 'ORDER BY id DESC LIMIT ' . CC_PHASE7_HISTORY_LIMIT
    )->fetchAll();
    $checksByTarget = cc_phase7_current_health_checks($controlDb, $targets);
    $summary = [
        'HEALTHY' => 0,
        'DEGRADED' => 0,
        'TRANSIENT_FAILURE' => 0,
        'UNREACHABLE' => 0,
        'NOT_CHECKED' => 0,
    ];
    foreach ($targets as &$target) {
        $key = (int) $target['reseller_id'] . ':' . (string) $target['host_fingerprint'];
        $checks = $checksByTarget[$key] ?? [];
        $target['health_state'] = cc_phase7_health_state($checks);
        $target['latest_check'] = $checks[0] ?? null;
        $summary[$target['health_state']]++;
        unset($target['host']);
    }
    unset($target);
    return [
        'definitions' => cc_phase7_metric_definitions(),
        'eligible_row_count' => $eligibleRowCount,
        'coverage_complete' => $eligibleRowCount <= 500,
        'summary' => $summary,
        'targets' => $targets,
        'history' => array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'reseller_id' => (int) $row['reseller_id'],
            'host_fingerprint' => (string) $row['host_fingerprint'],
            'checked_at' => (string) $row['checked_at'],
            'probe_result' => (string) $row['probe_result'],
            'dns_ok' => (bool) $row['dns_ok'],
            'tcp_ok' => (bool) $row['tcp_ok'],
            'http_status' => $row['http_status'] === null ? null : (int) $row['http_status'],
            'latency_ms' => (int) $row['latency_ms'],
            'failure_code' => $row['failure_code'] === null ? null : (string) $row['failure_code'],
        ], $history),
    ];
}

function cc_phase7_diagnostics_page(
    PDO $controlDb,
    ?DateTimeImmutable $now = null,
    int $retentionDays = 30
): array {
    $now = cc_presence_now($now);
    $since = cc_presence_datetime($now->modify('-' . $retentionDays . ' days'));
    $aggregateQueries = [
        'failures_by_code' =>
            'SELECT event_type, error_code, COUNT(*) AS event_count, MAX(received_at) AS last_received_at '
            . 'FROM cc_diagnostic_events WHERE received_at >= :since '
            . 'GROUP BY event_type, error_code ORDER BY event_count DESC, error_code ASC LIMIT 51',
        'failures_by_version' =>
            'SELECT app_version_name, app_version_code, COUNT(*) AS event_count '
            . 'FROM cc_diagnostic_events WHERE received_at >= :since '
            . 'GROUP BY app_version_name, app_version_code '
            . 'ORDER BY event_count DESC, app_version_code DESC LIMIT 26',
        'failures_by_device' =>
            'SELECT platform_class, device_manufacturer, device_model, COUNT(*) AS event_count '
            . 'FROM cc_diagnostic_events WHERE received_at >= :since '
            . 'GROUP BY platform_class, device_manufacturer, device_model '
            . 'ORDER BY event_count DESC, platform_class ASC LIMIT 26',
        'failures_by_host' =>
            'SELECT host_fingerprint, COUNT(*) AS event_count '
            . 'FROM cc_diagnostic_events WHERE received_at >= :since '
            . 'GROUP BY host_fingerprint ORDER BY event_count DESC, host_fingerprint ASC LIMIT 26',
    ];
    $aggregateLimits = [
        'failures_by_code' => 50,
        'failures_by_version' => 25,
        'failures_by_device' => 25,
        'failures_by_host' => 25,
    ];
    $data = [];
    $partial = [];
    foreach ($aggregateQueries as $key => $sql) {
        $statement = $controlDb->prepare($sql);
        $statement->execute(['since' => $since]);
        $rows = $statement->fetchAll();
        $limit = $aggregateLimits[$key];
        $partial[$key] = count($rows) > $limit;
        $data[$key] = array_slice($rows, 0, $limit);
    }
    $total = $controlDb->prepare(
        'SELECT COUNT(*) FROM cc_diagnostic_events WHERE received_at >= :since'
    );
    $total->execute(['since' => $since]);
    $recent = $controlDb->prepare(
        'SELECT event_id, event_type, error_code, app_version_name, app_version_code, platform_class, '
        . 'device_manufacturer, device_model, host_fingerprint, occurred_at, received_at '
        . 'FROM cc_diagnostic_events WHERE received_at >= :since ORDER BY id DESC LIMIT 50'
    );
    $recent->execute(['since' => $since]);
    return $data + [
        'definitions' => cc_phase7_metric_definitions(),
        'retention_days' => $retentionDays,
        'total_events' => (int) $total->fetchColumn(),
        'recent_events' => $recent->fetchAll(),
        'partial' => $partial,
    ];
}

function cc_phase7_analytics_page(PDO $controlDb, ?DateTimeImmutable $now = null): array
{
    $now = cc_presence_now($now);
    $since = cc_presence_datetime($now->modify('-' . CC_PHASE7_ANALYTICS_DAYS . ' days'));
    $sessions = $controlDb->prepare(
        'SELECT DATE(started_at) AS day, COUNT(*) AS session_count '
        . 'FROM cc_app_sessions WHERE started_at >= :since '
        . 'GROUP BY DATE(started_at) ORDER BY day ASC'
    );
    $sessions->execute(['since' => $since]);
    $versions = $controlDb->prepare(
        'SELECT bounded.day, bounded.app_version_name, bounded.app_version_code, bounded.device_count FROM ('
        . 'SELECT latest.day, s.app_version_name, s.app_version_code, COUNT(*) AS device_count FROM cc_app_sessions s '
        . 'JOIN (SELECT DATE(started_at) AS day, device_id, MAX(id) AS latest_session_id '
        . 'FROM cc_app_sessions WHERE started_at >= :since '
        . 'GROUP BY DATE(started_at), device_id) latest ON latest.latest_session_id = s.id '
        . 'GROUP BY latest.day, s.app_version_name, s.app_version_code '
        . 'ORDER BY latest.day DESC, s.app_version_code DESC, s.app_version_name DESC LIMIT 501'
        . ') bounded ORDER BY bounded.day ASC, bounded.app_version_code DESC, bounded.app_version_name DESC'
    );
    $versions->execute(['since' => $since]);
    $activeCutoff = cc_presence_datetime($now->modify('-24 hours'));
    $current = $controlDb->prepare(
        'SELECT recent.app_version_name, recent.app_version_code, COUNT(*) AS device_count FROM ('
        . 'SELECT s.device_id, s.app_version_name, s.app_version_code FROM cc_app_sessions s '
        . 'WHERE s.last_seen_at >= :active_cutoff AND NOT EXISTS ('
        . 'SELECT 1 FROM cc_app_sessions newer WHERE newer.device_id = s.device_id AND ('
        . 'newer.last_seen_at > s.last_seen_at OR '
        . '(newer.last_seen_at = s.last_seen_at AND newer.id > s.id)))'
        . ') recent GROUP BY recent.app_version_name, recent.app_version_code '
        . 'ORDER BY device_count DESC, recent.app_version_code DESC, recent.app_version_name DESC LIMIT 26'
    );
    $current->execute(['active_cutoff' => $activeCutoff]);
    $versionRows = $versions->fetchAll();
    $currentRows = $current->fetchAll();
    $versionPartial = count($versionRows) > 500;
    return [
        'definitions' => cc_phase7_metric_definitions(),
        'window_days' => CC_PHASE7_ANALYTICS_DAYS,
        'session_trend' => $sessions->fetchAll(),
        'version_adoption_trend' => $versionPartial ? array_slice($versionRows, 1, 500) : $versionRows,
        'current_adoption_distribution' => array_slice($currentRows, 0, 25),
        'current_adoption_window_hours' => 24,
        'version_adoption_partial' => $versionPartial,
        'current_adoption_partial' => count($currentRows) > 25,
    ];
}

function cc_phase7_page_data(string $module): array
{
    return match ($module) {
        'host-health' => cc_phase7_host_health_page(cc_db('control'), cc_db('reseller')),
        'diagnostics' => cc_phase7_diagnostics_page(
            cc_db('control'),
            null,
            (int) cc_diagnostics_config()['retention_days']
        ),
        'analytics' => cc_phase7_analytics_page(cc_db('control')),
        default => throw new InvalidArgumentException('Unknown Phase 7 module.'),
    };
}
