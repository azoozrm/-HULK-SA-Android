<?php

declare(strict_types=1);

require_once __DIR__ . '/presence.php';

const CC_PRESENCE_PAGE_SIZE = 25;
const CC_PRESENCE_ACTIVE_DEVICE_WINDOW_HOURS = 24;

function cc_presence_query_text(mixed $value, int $maximum): string
{
    if (!is_string($value)) {
        return '';
    }
    $value = trim($value);
    return strlen($value) <= $maximum ? $value : substr($value, 0, $maximum);
}

function cc_presence_like_pattern(string $value): string
{
    return '%' . strtr($value, ['=' => '==', '%' => '=%', '_' => '=_']) . '%';
}

function cc_presence_page_number(mixed $value): int
{
    $page = filter_var($value, FILTER_VALIDATE_INT);
    return $page === false ? 1 : max(1, min((int) $page, 100000));
}

function cc_presence_session_filters(string $module, array $query): array
{
    if (!in_array($module, ['live-users', 'sessions'], true)) {
        throw new InvalidArgumentException('Unknown Presence session module.');
    }
    $status = cc_presence_query_text($query['status'] ?? null, 16);
    if (!in_array($status, ['all', 'online', 'offline'], true)) {
        $status = $module === 'live-users' ? 'online' : 'all';
    }
    $platform = strtoupper(cc_presence_query_text($query['platform'] ?? null, 16));
    if (!in_array($platform, ['', 'PHONE', 'TABLET', 'TV', 'OTHER'], true)) {
        $platform = '';
    }
    $reseller = filter_var($query['reseller'] ?? null, FILTER_VALIDATE_INT);
    $reseller = $reseller === false || $reseller < 1 ? null : (int) $reseller;
    $sort = cc_presence_query_text($query['sort'] ?? null, 24);
    if (!in_array($sort, ['last_seen', 'started', 'app_version', 'reseller', 'device'], true)) {
        $sort = $module === 'live-users' ? 'last_seen' : 'started';
    }
    $direction = strtolower(cc_presence_query_text($query['direction'] ?? null, 4));
    if (!in_array($direction, ['asc', 'desc'], true)) {
        $direction = 'desc';
    }
    return [
        'q' => cc_presence_query_text($query['q'] ?? null, 100),
        'status' => $status,
        'app_version' => cc_presence_query_text($query['app_version'] ?? null, 32),
        'reseller' => $reseller,
        'host' => cc_presence_query_text($query['host'] ?? null, 255),
        'platform' => $platform,
        'sort' => $sort,
        'direction' => $direction,
        'page' => cc_presence_page_number($query['page'] ?? 1),
    ];
}

function cc_presence_device_filters(array $query): array
{
    $platform = strtoupper(cc_presence_query_text($query['platform'] ?? null, 16));
    if (!in_array($platform, ['', 'PHONE', 'TABLET', 'TV', 'OTHER'], true)) {
        $platform = '';
    }
    $sort = cc_presence_query_text($query['sort'] ?? null, 24);
    if (!in_array($sort, ['last_seen', 'first_seen', 'app_version', 'device'], true)) {
        $sort = 'last_seen';
    }
    $direction = strtolower(cc_presence_query_text($query['direction'] ?? null, 4));
    if (!in_array($direction, ['asc', 'desc'], true)) {
        $direction = 'desc';
    }
    return [
        'q' => cc_presence_query_text($query['q'] ?? null, 100),
        'app_version' => cc_presence_query_text($query['app_version'] ?? null, 32),
        'platform' => $platform,
        'sort' => $sort,
        'direction' => $direction,
        'page' => cc_presence_page_number($query['page'] ?? 1),
    ];
}

function cc_presence_session_conditions(array $filters, string $cutoff, array &$parameters): array
{
    $conditions = [];
    if ($filters['status'] === 'online') {
        $conditions[] = 's.ended_at IS NULL AND s.last_seen_at >= :status_cutoff';
        $parameters['status_cutoff'] = $cutoff;
    } elseif ($filters['status'] === 'offline') {
        $conditions[] = '(s.ended_at IS NOT NULL OR s.last_seen_at < :status_cutoff)';
        $parameters['status_cutoff'] = $cutoff;
    }
    if ($filters['q'] !== '') {
        $pattern = cc_presence_like_pattern($filters['q']);
        $searchColumns = [
            'session' => 's.session_id',
            'code' => 's.access_code_snapshot',
            'username' => 's.iptv_username',
            'host' => 's.host_snapshot',
            'installation' => 'd.installation_id',
            'manufacturer' => 'd.manufacturer',
            'model' => 'd.model',
        ];
        $search = [];
        foreach ($searchColumns as $key => $column) {
            $search[] = $column . " LIKE :q_{$key} ESCAPE '='";
            $parameters['q_' . $key] = $pattern;
        }
        $conditions[] = '(' . implode(' OR ', $search) . ')';
    }
    if ($filters['app_version'] !== '') {
        $conditions[] = 's.app_version_name = :app_version';
        $parameters['app_version'] = $filters['app_version'];
    }
    if ($filters['reseller'] !== null) {
        $conditions[] = 's.reseller_id = :reseller_id';
        $parameters['reseller_id'] = $filters['reseller'];
    }
    if ($filters['host'] !== '') {
        $conditions[] = "s.host_snapshot LIKE :host_filter ESCAPE '='";
        $parameters['host_filter'] = cc_presence_like_pattern($filters['host']);
    }
    if ($filters['platform'] !== '') {
        $conditions[] = 's.platform_class = :platform';
        $parameters['platform'] = $filters['platform'];
    }
    return $conditions;
}

function cc_presence_session_page(
    PDO $controlDb,
    callable $resellerLoader,
    string $module,
    array $query,
    array $config,
    ?DateTimeImmutable $now = null
): array {
    $now = cc_presence_now($now);
    $ttlSeconds = (int) ($config['online_ttl_seconds'] ?? 0);
    if ($ttlSeconds < 1) {
        throw new RuntimeException('Presence read TTL is invalid.');
    }
    $filters = cc_presence_session_filters($module, $query);
    $cutoff = cc_presence_datetime($now->modify('-' . $ttlSeconds . ' seconds'));
    $parameters = [];
    $conditions = cc_presence_session_conditions($filters, $cutoff, $parameters);
    $where = $conditions === [] ? '' : ' WHERE ' . implode(' AND ', $conditions);

    $count = $controlDb->prepare(
        'SELECT COUNT(*) FROM cc_app_sessions s '
        . 'JOIN cc_devices d ON d.id = s.device_id' . $where
    );
    $count->execute($parameters);
    $total = (int) $count->fetchColumn();
    $pages = max(1, (int) ceil($total / CC_PRESENCE_PAGE_SIZE));
    $filters['page'] = min($filters['page'], $pages);
    $offset = ($filters['page'] - 1) * CC_PRESENCE_PAGE_SIZE;

    $sortColumns = [
        'last_seen' => 's.last_seen_at',
        'started' => 's.started_at',
        'app_version' => 's.app_version_code',
        'reseller' => 's.reseller_id',
        'device' => 'd.installation_id',
    ];
    $direction = $filters['direction'] === 'asc' ? 'ASC' : 'DESC';
    $order = $sortColumns[$filters['sort']] . ' ' . $direction . ', s.id ' . $direction;
    $rowParameters = $parameters;
    $rowParameters['online_cutoff'] = $cutoff;
    $statement = $controlDb->prepare(
        'SELECT s.id, s.session_id, s.reseller_id, s.access_code_snapshot, s.iptv_username, '
        . 's.iptv_password, s.host_snapshot, s.authenticated_at_client_ms, s.app_version_name, '
        . 's.app_version_code, s.platform_class, s.device_manufacturer, s.device_model, '
        . 's.android_release, s.android_sdk_int, s.started_at, s.last_seen_at, s.ended_at, s.end_reason, '
        . 'd.installation_id, d.first_seen_at AS device_first_seen_at, d.last_seen_at AS device_last_seen_at, '
        . 'CASE WHEN s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff THEN 1 ELSE 0 END AS online '
        . 'FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id'
        . $where . ' ORDER BY ' . $order
        . ' LIMIT ' . CC_PRESENCE_PAGE_SIZE . ' OFFSET ' . $offset
    );
    $statement->execute($rowParameters);
    $rows = $statement->fetchAll();
    foreach ($rows as &$row) {
        $row['online'] = (int) ($row['online'] ?? 0) === 1;
    }
    unset($row);

    $resellerIds = array_values(array_unique(array_map(
        static fn (array $row): int => (int) $row['reseller_id'],
        $rows
    )));
    $resellers = [];
    $resellersAvailable = true;
    if ($resellerIds !== []) {
        try {
            $loaded = $resellerLoader($resellerIds);
            if (!is_array($loaded)) {
                throw new RuntimeException('Reseller mapping returned invalid data.');
            }
            $resellers = $loaded;
        } catch (Throwable $exception) {
            error_log('HULK Control Center Presence reseller mapping unavailable.');
            $resellersAvailable = false;
        }
    }

    return [
        'rows' => $rows,
        'total' => $total,
        'page' => $filters['page'],
        'pages' => $pages,
        'per_page' => CC_PRESENCE_PAGE_SIZE,
        'filters' => $filters,
        'server_now' => cc_presence_datetime($now),
        'online_ttl_seconds' => $ttlSeconds,
        'resellers' => $resellers,
        'resellers_available' => $resellersAvailable,
    ];
}

function cc_presence_reseller_map(PDO $resellerDb, array $resellerIds): array
{
    $ids = array_values(array_unique(array_filter(
        array_map('intval', $resellerIds),
        static fn (int $id): bool => $id > 0
    )));
    if ($ids === []) {
        return [];
    }
    if (count($ids) > CC_PRESENCE_PAGE_SIZE) {
        throw new InvalidArgumentException('Reseller mapping exceeds the page bound.');
    }
    $placeholders = [];
    $parameters = [];
    foreach ($ids as $index => $id) {
        $key = 'reseller_' . $index;
        $placeholders[] = ':' . $key;
        $parameters[$key] = $id;
    }
    $statement = $resellerDb->prepare(
        'SELECT reseller_id, reseller_name, status, host, access_code FROM resellers '
        . 'WHERE reseller_id IN (' . implode(', ', $placeholders) . ') '
        . 'ORDER BY reseller_id ASC LIMIT ' . count($ids)
    );
    $statement->execute($parameters);
    $mapped = [];
    while (($row = $statement->fetch()) !== false) {
        if (is_array($row)) {
            $mapped[(int) $row['reseller_id']] = $row;
        }
    }
    return $mapped;
}

function cc_presence_device_page(
    PDO $controlDb,
    array $query,
    ?DateTimeImmutable $now = null
): array {
    $filters = cc_presence_device_filters($query);
    $parameters = [];
    $conditions = [];
    if ($filters['q'] !== '') {
        $pattern = cc_presence_like_pattern($filters['q']);
        foreach (['installation_id', 'manufacturer', 'model', 'android_release'] as $column) {
            $key = 'q_' . $column;
            $conditions[] = $column . " LIKE :{$key} ESCAPE '='";
            $parameters[$key] = $pattern;
        }
        $conditions = ['(' . implode(' OR ', $conditions) . ')'];
    }
    if ($filters['app_version'] !== '') {
        $conditions[] = 'latest_app_version_name = :app_version';
        $parameters['app_version'] = $filters['app_version'];
    }
    if ($filters['platform'] !== '') {
        $conditions[] = 'platform_class = :platform';
        $parameters['platform'] = $filters['platform'];
    }
    $where = $conditions === [] ? '' : ' WHERE ' . implode(' AND ', $conditions);
    $count = $controlDb->prepare('SELECT COUNT(*) FROM cc_devices' . $where);
    $count->execute($parameters);
    $total = (int) $count->fetchColumn();
    $pages = max(1, (int) ceil($total / CC_PRESENCE_PAGE_SIZE));
    $filters['page'] = min($filters['page'], $pages);
    $offset = ($filters['page'] - 1) * CC_PRESENCE_PAGE_SIZE;
    $sortColumns = [
        'last_seen' => 'last_seen_at',
        'first_seen' => 'first_seen_at',
        'app_version' => 'latest_app_version_code',
        'device' => 'installation_id',
    ];
    $direction = $filters['direction'] === 'asc' ? 'ASC' : 'DESC';
    $statement = $controlDb->prepare(
        'SELECT id, installation_id, platform_class, manufacturer, model, android_release, android_sdk_int, '
        . 'first_seen_at, last_seen_at, latest_app_version_name, latest_app_version_code '
        . 'FROM cc_devices' . $where . ' ORDER BY ' . $sortColumns[$filters['sort']] . ' ' . $direction
        . ', id ' . $direction . ' LIMIT ' . CC_PRESENCE_PAGE_SIZE . ' OFFSET ' . $offset
    );
    $statement->execute($parameters);
    return [
        'rows' => $statement->fetchAll(),
        'total' => $total,
        'page' => $filters['page'],
        'pages' => $pages,
        'per_page' => CC_PRESENCE_PAGE_SIZE,
        'filters' => $filters,
        'server_now' => cc_presence_datetime(cc_presence_now($now)),
    ];
}

function cc_presence_usage_for_resellers(
    PDO $controlDb,
    array $resellerRows,
    string $module,
    DateTimeImmutable $now,
    int $ttlSeconds
): array {
    if (!in_array($module, ['resellers', 'access-codes', 'hosts'], true)) {
        throw new InvalidArgumentException('Unknown reseller usage module.');
    }
    $rowsById = [];
    foreach (array_slice($resellerRows, 0, CC_PRESENCE_PAGE_SIZE) as $row) {
        $id = (int) ($row['reseller_id'] ?? 0);
        if ($id > 0) {
            $rowsById[$id] = $row;
        }
    }
    if ($rowsById === []) {
        return [];
    }
    $conditions = [];
    $parameters = [
        'usage_cutoff' => cc_presence_datetime(cc_presence_now($now)->modify('-' . $ttlSeconds . ' seconds')),
    ];
    foreach ($rowsById as $id => $row) {
        $suffix = (string) $id;
        $condition = 'reseller_id = :usage_reseller_' . $suffix;
        $parameters['usage_reseller_' . $suffix] = $id;
        if ($module === 'access-codes') {
            $currentCode = hulk_normalize_access_code((string) ($row['access_code'] ?? ''));
            $condition .= " AND UPPER(REPLACE(REPLACE(access_code_snapshot, '-', ''), ' ', '')) "
                . '= :usage_value_' . $suffix;
            $parameters['usage_value_' . $suffix] = $currentCode === null
                ? '__INVALID_CURRENT_CODE__'
                : str_replace('-', '', $currentCode);
        } elseif ($module === 'hosts') {
            $condition .= ' AND host_snapshot = :usage_value_' . $suffix;
            $parameters['usage_value_' . $suffix] = hulk_normalize_host((string) ($row['host'] ?? ''))
                ?? '__INVALID_CURRENT_HOST__';
        }
        $conditions[] = '(' . $condition . ')';
    }
    $statement = $controlDb->prepare(
        'SELECT reseller_id, COUNT(*) AS session_count, '
        . 'SUM(CASE WHEN ended_at IS NULL AND last_seen_at >= :usage_cutoff THEN 1 ELSE 0 END) AS online_count, '
        . 'MAX(last_seen_at) AS last_used_at FROM cc_app_sessions WHERE '
        . implode(' OR ', $conditions) . ' GROUP BY reseller_id ORDER BY reseller_id ASC'
    );
    $statement->execute($parameters);
    $usage = [];
    foreach ($rowsById as $id => $_row) {
        $usage[$id] = ['sessions' => 0, 'online' => 0, 'last_used_at' => null];
    }
    while (($row = $statement->fetch()) !== false) {
        if (!is_array($row)) {
            continue;
        }
        $id = (int) $row['reseller_id'];
        if (isset($usage[$id])) {
            $usage[$id] = [
                'sessions' => (int) $row['session_count'],
                'online' => (int) $row['online_count'],
                'last_used_at' => $row['last_used_at'] === null ? null : (string) $row['last_used_at'],
            ];
        }
    }
    return $usage;
}

function cc_presence_dashboard_snapshot(
    PDO $controlDb,
    callable $resellerLoader,
    array $config,
    ?DateTimeImmutable $now = null,
    ?DateTimeZone $dashboardTimezone = null
): array {
    $now = cc_presence_now($now);
    $ttlSeconds = (int) ($config['online_ttl_seconds'] ?? 0);
    if ($ttlSeconds < 1) {
        throw new RuntimeException('Presence Dashboard TTL is invalid.');
    }
    $onlineCutoff = cc_presence_datetime($now->modify('-' . $ttlSeconds . ' seconds'));
    $timezone = $dashboardTimezone ?? new DateTimeZone('Asia/Riyadh');
    $todayStart = $now->setTimezone($timezone)->setTime(0, 0)->setTimezone(new DateTimeZone('UTC'));
    $activeCutoff = cc_presence_datetime(
        $now->modify('-' . CC_PRESENCE_ACTIVE_DEVICE_WINDOW_HOURS . ' hours')
    );
    $metrics = $controlDb->prepare(
        'SELECT '
        . 'SUM(CASE WHEN ended_at IS NULL AND last_seen_at >= :online_cutoff THEN 1 ELSE 0 END) AS online_now, '
        . 'SUM(CASE WHEN started_at >= :today_start THEN 1 ELSE 0 END) AS sessions_today '
        . 'FROM cc_app_sessions'
    );
    $metrics->execute([
        'online_cutoff' => $onlineCutoff,
        'today_start' => cc_presence_datetime($todayStart),
    ]);
    $aggregate = $metrics->fetch();
    if (!is_array($aggregate)) {
        throw new RuntimeException('Presence Dashboard aggregate is unavailable.');
    }
    $activeDevices = $controlDb->prepare('SELECT COUNT(*) FROM cc_devices WHERE last_seen_at >= :active_cutoff');
    $activeDevices->execute(['active_cutoff' => $activeCutoff]);
    $versions = $controlDb->prepare(
        'SELECT latest_app_version_name AS app_version_name, latest_app_version_code AS app_version_code, '
        . 'COUNT(*) AS device_count FROM cc_devices WHERE last_seen_at >= :version_cutoff '
        . 'GROUP BY latest_app_version_name, latest_app_version_code '
        . 'ORDER BY device_count DESC, latest_app_version_code DESC LIMIT 8'
    );
    $versions->execute(['version_cutoff' => $activeCutoff]);
    $preview = cc_presence_session_page(
        $controlDb,
        $resellerLoader,
        'live-users',
        ['status' => 'online', 'sort' => 'last_seen', 'direction' => 'desc'],
        $config,
        $now
    );
    return [
        'online_now' => (int) ($aggregate['online_now'] ?? 0),
        'sessions_today' => (int) ($aggregate['sessions_today'] ?? 0),
        'active_devices' => (int) $activeDevices->fetchColumn(),
        'active_device_window_hours' => CC_PRESENCE_ACTIVE_DEVICE_WINDOW_HOURS,
        'users_today_available' => false,
        'version_distribution' => array_map(
            static fn (array $row): array => [
                'app_version_name' => (string) $row['app_version_name'],
                'app_version_code' => (int) $row['app_version_code'],
                'device_count' => (int) $row['device_count'],
            ],
            $versions->fetchAll()
        ),
        'live_preview' => array_slice($preview['rows'], 0, 6),
        'resellers' => $preview['resellers'],
        'resellers_available' => $preview['resellers_available'],
        'server_now' => cc_presence_datetime($now),
        'online_ttl_seconds' => $ttlSeconds,
    ];
}

function cc_presence_query_parameters(array $filters, array $overrides = []): array
{
    $values = array_merge($filters, $overrides);
    $values = array_filter(
        $values,
        static fn (mixed $value): bool => $value !== '' && $value !== null && $value !== 'all'
    );
    if (($values['page'] ?? null) === 1) {
        unset($values['page']);
    }
    return $values;
}

function cc_presence_page_data(string $module, array $query): array
{
    $control = cc_db('control');
    if ($module === 'devices') {
        return cc_presence_device_page($control, $query);
    }
    return cc_presence_session_page(
        $control,
        static fn (array $ids): array => cc_presence_reseller_map(cc_db('reseller'), $ids),
        $module,
        $query,
        cc_presence_config()
    );
}

function cc_presence_display_time(?string $utcValue): string
{
    if ($utcValue === null || trim($utcValue) === '') {
        return '—';
    }
    try {
        $timezone = new DateTimeZone((string) (cc_load_config()['app']['timezone'] ?? 'Asia/Riyadh'));
        return (new DateTimeImmutable($utcValue, new DateTimeZone('UTC')))
            ->setTimezone($timezone)
            ->format('Y-m-d H:i:s');
    } catch (Throwable $exception) {
        return '—';
    }
}
