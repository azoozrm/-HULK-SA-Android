<?php

declare(strict_types=1);

require_once __DIR__ . '/presence.php';
require_once __DIR__ . '/subscriber-notes.php';

const CC_PRESENCE_PAGE_SIZE = 25;
const CC_PRESENCE_SUBSCRIPTION_PAGE_SIZE = 10;
const CC_PRESENCE_ONLINE_DEVICE_LIMIT = 8;
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
        'days' => in_array((string) ($query['days'] ?? ''), ['1','7','30'], true) ? (int) $query['days'] : 0,
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

function cc_presence_session_conditions(array $filters, string $cutoff, array &$parameters, ?DateTimeImmutable $now = null): array
{
    $conditions = [];
    if (($filters['days'] ?? 0) > 0) {
        $conditions[] = 's.started_at >= :duration_since';
        $parameters['duration_since'] = cc_presence_datetime(cc_presence_now($now)->modify('-' . (int) $filters['days'] . ' days'));
    }
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
            'manufacturer' => 's.device_manufacturer',
            'model' => 's.device_model',
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
    $conditions = cc_presence_session_conditions($filters, $cutoff, $parameters, $now);
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
    cc_reseller_require_domain();
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
        'SELECT reseller_id, reseller_name, status, host FROM ' . hulk_host_relation() . ' host_records '
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

function cc_presence_subscription_key(int $resellerId, string $host, string $username): string
{
    return $resellerId . "\0" . $host . "\0" . $username;
}

function cc_presence_online_group_condition(
    array $group,
    string $prefix,
    array &$parameters,
    string $cutoff
): string {
    $parameters[$prefix . '_cutoff'] = $cutoff;
    $parameters[$prefix . '_reseller'] = (int) ($group['reseller_id'] ?? 0);
    $parameters[$prefix . '_host_hash'] = strtoupper(bin2hex((string) ($group['host_snapshot'] ?? '')));
    $parameters[$prefix . '_user_hash'] = strtoupper(bin2hex((string) ($group['iptv_username'] ?? '')));
    return 's.ended_at IS NULL AND s.last_seen_at >= :' . $prefix . '_cutoff'
        . ' AND s.reseller_id = :' . $prefix . '_reseller'
        . ' AND UPPER(HEX(s.host_snapshot)) = :' . $prefix . '_host_hash'
        . ' AND UPPER(HEX(s.iptv_username)) = :' . $prefix . '_user_hash';
}

function cc_presence_online_now_page(
    PDO $controlDb,
    callable $resellerLoader,
    array $query,
    array $config,
    ?DateTimeImmutable $now = null
): array {
    $now = cc_presence_now($now);
    $ttlSeconds = (int) ($config['online_ttl_seconds'] ?? 0);
    if ($ttlSeconds < 1) {
        throw new RuntimeException('Presence online TTL is invalid.');
    }
    $cutoff = cc_presence_datetime($now->modify('-' . $ttlSeconds . ' seconds'));
    $page = cc_presence_page_number($query['page'] ?? 1);
    $perPage = CC_PRESENCE_SUBSCRIPTION_PAGE_SIZE;
    $search = mb_substr(trim(is_string($query['q'] ?? null) ? $query['q'] : ''), 0, 100);
    $platform = is_string($query['platform'] ?? null) && in_array($query['platform'], ['TV', 'PHONE', 'TABLET', 'OTHER'], true) ? $query['platform'] : '';
    $filterSql = '';
    $filterParameters = ['online_cutoff' => $cutoff];
    if ($search !== '') {
        $filterSql .= " AND (s.iptv_username LIKE :filter_user ESCAPE '=' OR s.host_snapshot LIKE :filter_host ESCAPE '=')";
        $filterParameters['filter_user'] = cc_presence_like_pattern($search);
        $filterParameters['filter_host'] = cc_presence_like_pattern($search);
    }
    // A device filter selects whole subscription groups; all their devices remain counted
    if ($platform !== '') {
        $filterSql .= ' AND EXISTS (SELECT 1 FROM cc_app_sessions f WHERE f.reseller_id = s.reseller_id '
            . 'AND HEX(f.host_snapshot) = HEX(s.host_snapshot) AND HEX(f.iptv_username) = HEX(s.iptv_username) '
            . 'AND f.ended_at IS NULL AND f.last_seen_at >= :filter_cutoff AND f.platform_class = :filter_platform)';
        $filterParameters['filter_cutoff'] = $cutoff;
        $filterParameters['filter_platform'] = $platform;
    }


    $summary = $controlDb->prepare(
        'SELECT COUNT(*) AS connections, '
        . 'COUNT(DISTINCT d.installation_id) AS devices, '
        . "SUM(CASE WHEN s.platform_class = 'TV' THEN 1 ELSE 0 END) AS tv_connections, "
        . "SUM(CASE WHEN s.platform_class IN ('PHONE', 'TABLET') THEN 1 ELSE 0 END) AS phone_connections, "
        . "SUM(CASE WHEN s.platform_class = 'OTHER' THEN 1 ELSE 0 END) AS other_connections "
        . 'FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id '
        . 'WHERE s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff' . $filterSql
    );
    $summary->execute($filterParameters);
    $summaryRow = $summary->fetch();
    if (!is_array($summaryRow)) {
        throw new RuntimeException('Presence Online Now summary is unavailable.');
    }

    $subscriptionCount = $controlDb->prepare(
        'SELECT COUNT(*) FROM ('
        . 'SELECT 1 FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id '
        . 'WHERE s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff ' . $filterSql . ' '
        . 'GROUP BY s.reseller_id, HEX(s.host_snapshot), HEX(s.iptv_username)'
        . ') AS grouped_subscriptions'
    );
    $subscriptionCount->execute($filterParameters);
    $totalSubscriptions = (int) $subscriptionCount->fetchColumn();

    $multiDeviceCount = $controlDb->prepare(
        'SELECT COUNT(*) FROM ('
        . 'SELECT 1 FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id '
        . 'WHERE s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff ' . $filterSql . ' '
        . 'GROUP BY s.reseller_id, HEX(s.host_snapshot), HEX(s.iptv_username) '
        . 'HAVING COUNT(DISTINCT d.installation_id) > 1'
        . ') AS multi_device_subscriptions'
    );
    $multiDeviceCount->execute($filterParameters);
    $multiDeviceSubscriptions = (int) $multiDeviceCount->fetchColumn();

    $pages = max(1, (int) ceil($totalSubscriptions / $perPage));
    $page = min($page, $pages);
    $offset = ($page - 1) * $perPage;

    $groupStatement = $controlDb->prepare(
        'SELECT s.reseller_id, MIN(s.host_snapshot) AS host_snapshot, '
        . 'MIN(s.iptv_username) AS iptv_username, '
        . 'COUNT(*) AS connection_count, '
        . 'COUNT(DISTINCT d.installation_id) AS device_count, '
        . "SUM(CASE WHEN s.platform_class = 'TV' THEN 1 ELSE 0 END) AS tv_connections, "
        . "SUM(CASE WHEN s.platform_class IN ('PHONE', 'TABLET') THEN 1 ELSE 0 END) AS phone_connections, "
        . "SUM(CASE WHEN s.platform_class = 'OTHER' THEN 1 ELSE 0 END) AS other_connections, "
        . 'MIN(s.started_at) AS first_started_at, '
        . 'MAX(s.last_seen_at) AS last_activity_at '
        . 'FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id '
        . 'WHERE s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff ' . $filterSql . ' '
        . 'GROUP BY s.reseller_id, HEX(s.host_snapshot), HEX(s.iptv_username) '
        . 'ORDER BY last_activity_at DESC, s.reseller_id DESC, HEX(s.host_snapshot) ASC, HEX(s.iptv_username) ASC '
        . 'LIMIT ' . $perPage . ' OFFSET ' . $offset
    );
    $groupStatement->execute($filterParameters);
    $groupRows = $groupStatement->fetchAll();

    $groupedRows = [];
    foreach ($groupRows as $groupRow) {
        if (!is_array($groupRow)) {
            continue;
        }
        $key = cc_presence_subscription_key(
            (int) $groupRow['reseller_id'],
            (string) $groupRow['host_snapshot'],
            (string) $groupRow['iptv_username']
        );
        $groupRow['devices'] = [];
        $groupRow['devices_truncated'] = false;
        $groupRow['latest_session'] = null;
        $groupedRows[$key] = $groupRow;
    }

    foreach ($groupRows as $index => $groupRow) {
        if (!is_array($groupRow)) {
            continue;
        }
        $key = cc_presence_subscription_key(
            (int) $groupRow['reseller_id'],
            (string) $groupRow['host_snapshot'],
            (string) $groupRow['iptv_username']
        );
        if (!isset($groupedRows[$key])) {
            continue;
        }

        $latestParameters = [];
        $latestCondition = cc_presence_online_group_condition(
            $groupRow,
            'latest_' . (int) $index,
            $latestParameters,
            $cutoff
        );
        $latestStatement = $controlDb->prepare(
            'SELECT s.id, s.reseller_id, s.host_snapshot, s.iptv_username, '
            . 's.access_code_snapshot, s.iptv_password, s.last_seen_at '
            . 'FROM cc_app_sessions s WHERE ' . $latestCondition
            . ' ORDER BY s.last_seen_at DESC, s.id DESC LIMIT 1'
        );
        $latestStatement->execute($latestParameters);
        $latestSession = $latestStatement->fetch();
        if (is_array($latestSession)) {
            $groupedRows[$key]['latest_session'] = $latestSession;
        }

        $deviceParameters = [];
        $deviceCondition = cc_presence_online_group_condition(
            $groupRow,
            'device_' . (int) $index,
            $deviceParameters,
            $cutoff
        );
        $deviceStatement = $controlDb->prepare(
            'SELECT d.installation_id, d.platform_class, d.manufacturer, d.model, '
            . 'd.latest_app_version_name, d.latest_app_version_code, '
            . 'MAX(s.last_seen_at) AS last_seen_at '
            . 'FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id '
            . 'WHERE ' . $deviceCondition . ' '
            . 'GROUP BY d.installation_id, d.platform_class, d.manufacturer, d.model, '
            . 'd.latest_app_version_name, d.latest_app_version_code '
            . 'ORDER BY last_seen_at DESC, d.installation_id ASC '
            . 'LIMIT ' . (CC_PRESENCE_ONLINE_DEVICE_LIMIT + 1)
        );
        $deviceStatement->execute($deviceParameters);
        $deviceRows = $deviceStatement->fetchAll();
        usort($deviceRows, static function (array $left, array $right): int {
            $comparison = strcmp((string) $right['last_seen_at'], (string) $left['last_seen_at']);
            return $comparison !== 0
                ? $comparison
                : strcmp((string) $left['installation_id'], (string) $right['installation_id']);
        });
        $truncated = count($deviceRows) > CC_PRESENCE_ONLINE_DEVICE_LIMIT;
        if ($truncated) {
            $deviceRows = array_slice($deviceRows, 0, CC_PRESENCE_ONLINE_DEVICE_LIMIT);
        }
        $devices = [];
        foreach ($deviceRows as $device) {
            $devices[] = [
                'installation_id' => (string) $device['installation_id'],
                'platform_class' => (string) $device['platform_class'],
                'manufacturer' => (string) $device['manufacturer'],
                'model' => (string) $device['model'],
                'app_version_name' => (string) $device['latest_app_version_name'],
                'app_version_code' => (int) $device['latest_app_version_code'],
                'last_seen_at' => (string) $device['last_seen_at'],
            ];
        }
        $groupedRows[$key]['devices'] = $devices;
        $groupedRows[$key]['devices_truncated'] = $truncated;
    }

    $groups = [];
    foreach ($groupRows as $groupRow) {
        if (!is_array($groupRow)) {
            continue;
        }
        $key = cc_presence_subscription_key(
            (int) $groupRow['reseller_id'],
            (string) $groupRow['host_snapshot'],
            (string) $groupRow['iptv_username']
        );
        if (!isset($groupedRows[$key])) {
            continue;
        }
        $group = $groupedRows[$key];
        $group['devices'] = array_values($group['devices']);
        $groups[] = $group;
    }

    $notes = cc_subscriber_notes_attach($controlDb, $groups);
    $groups = $notes['groups'];
    $notesAvailable = $notes['notes_available'];

    $resellerIds = array_values(array_unique(array_map(
        static fn (array $group): int => (int) $group['reseller_id'],
        $groups
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
            error_log('HULK Control Center Online Now reseller mapping unavailable.');
            $resellersAvailable = false;
        }
    }

    return [
        'groups' => $groups,
        'filters' => ['q' => $search, 'platform' => $platform],
        'total_subscriptions' => $totalSubscriptions,
        'total_connections' => (int) ($summaryRow['connections'] ?? 0),
        'total_devices' => (int) ($summaryRow['devices'] ?? 0),
        'tv_connections' => (int) ($summaryRow['tv_connections'] ?? 0),
        'phone_connections' => (int) ($summaryRow['phone_connections'] ?? 0),
        'other_connections' => (int) ($summaryRow['other_connections'] ?? 0),
        'multi_device_subscriptions' => $multiDeviceSubscriptions,
        'page' => $page,
        'pages' => $pages,
        'per_page' => $perPage,
        'resellers' => $resellers,
        'resellers_available' => $resellersAvailable,
        'notes_available' => $notesAvailable,
        'server_now' => cc_presence_datetime($now),
        'online_ttl_seconds' => $ttlSeconds,
    ];
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

function cc_presence_usage_for_resellers(PDO $controlDb, array $resellerRows, string $module, DateTimeImmutable $now, int $ttlSeconds): array
{
    if (!in_array($module, ['hosts','access-codes','resellers'], true)) throw new InvalidArgumentException('Unknown usage module');
    $usage=[];
    foreach (array_slice($resellerRows,0,CC_PRESENCE_PAGE_SIZE) as $row) {
        $id=(int)($row['reseller_id']??0); if($id<1)continue;
        $key=$module==='access-codes'?(int)($row['code_id']??$id):$id;
        $where='reseller_id = :id';
        $params=['id'=>$id,'cutoff'=>cc_presence_datetime($now->modify('-'.$ttlSeconds.' seconds'))];
        if($module==='access-codes') {
            $code=(string)($row['access_code']??'');
            $where.=' AND (HEX(access_code_snapshot) = :code_hex'; $params['code_hex']=strtoupper(bin2hex($code));
            if(hulk_legacy_canonical_code($code)===$code) {
                $where.=" OR UPPER(REPLACE(REPLACE(access_code_snapshot, '-', ''), ' ', '')) = :legacy_code";
                $params['legacy_code']=str_replace('-','',$code);
            }
            $where.=')';
        } elseif($module==='hosts') {
            $where.=' AND HEX(host_snapshot) = :host_hex';
            $params['host_hex']=strtoupper(bin2hex(hulk_normalize_host((string)($row['host']??''))??'__INVALID_HOST__'));
        }
        $q=$controlDb->prepare('SELECT COUNT(*) AS sessions, SUM(CASE WHEN ended_at IS NULL AND last_seen_at >= :cutoff THEN 1 ELSE 0 END) AS online, MAX(last_seen_at) AS last_used_at FROM cc_app_sessions WHERE '.$where);
        $q->execute($params); $result=$q->fetch();
        $usage[$key]=['sessions'=>(int)($result['sessions']??0),'online'=>(int)($result['online']??0),'last_used_at'=>$result['last_used_at']??null];
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
    $activeDevices = $controlDb->prepare(
        'SELECT COUNT(DISTINCT d.installation_id) FROM cc_app_sessions s '
        . 'JOIN cc_devices d ON d.id = s.device_id WHERE s.last_seen_at >= :active_cutoff'
    );
    $activeDevices->execute(['active_cutoff' => $activeCutoff]);
    $versions = $controlDb->prepare(
        'SELECT recent.app_version_name, recent.app_version_code, COUNT(*) AS device_count FROM ('
        . 'SELECT d.installation_id, s.app_version_name, s.app_version_code '
        . 'FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id '
        . 'WHERE s.last_seen_at >= :version_cutoff AND NOT EXISTS ('
        . 'SELECT 1 FROM cc_app_sessions newer WHERE newer.device_id = s.device_id AND ('
        . 'newer.last_seen_at > s.last_seen_at '
        . 'OR (newer.last_seen_at = s.last_seen_at AND newer.id > s.id)'
        . '))'
        . ') recent GROUP BY recent.app_version_name, recent.app_version_code '
        . 'ORDER BY device_count DESC, recent.app_version_code DESC, recent.app_version_name DESC LIMIT 8'
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
    $onlineTotals = cc_presence_online_now_totals($controlDb, $config, $now);
    return [
        'online_now' => $onlineTotals['connections'],
        'online_subscriptions' => $onlineTotals['subscriptions'],
        'online_devices' => $onlineTotals['devices'],
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

function cc_presence_online_now_data(array $query): array
{
    return cc_presence_online_now_page(
        cc_db('control'),
        static fn (array $ids): array => cc_presence_reseller_map(cc_db('reseller'), $ids),
        $query,
        cc_presence_config()
    );
}

function cc_presence_online_now_totals(PDO $controlDb, array $config, ?DateTimeImmutable $now = null): array
{
    $now = cc_presence_now($now);
    $ttlSeconds = (int) ($config['online_ttl_seconds'] ?? 0);
    if ($ttlSeconds < 1) {
        throw new RuntimeException('Presence online TTL is invalid.');
    }
    $cutoff = cc_presence_datetime($now->modify('-' . $ttlSeconds . ' seconds'));
    $summary = $controlDb->prepare(
        'SELECT COUNT(*) AS connections, COUNT(DISTINCT d.installation_id) AS devices '
        . 'FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id '
        . 'WHERE s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff'
    );
    $summary->execute(['online_cutoff' => $cutoff]);
    $summaryRow = $summary->fetch();
    if (!is_array($summaryRow)) {
        throw new RuntimeException('Presence online totals are unavailable.');
    }
    $subscriptions = $controlDb->prepare(
        'SELECT COUNT(*) FROM ('
        . 'SELECT 1 FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id '
        . 'WHERE s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff '
        . 'GROUP BY s.reseller_id, HEX(s.host_snapshot), HEX(s.iptv_username)'
        . ') AS grouped_subscriptions'
    );
    $subscriptions->execute(['online_cutoff' => $cutoff]);
    return [
        'subscriptions' => (int) $subscriptions->fetchColumn(),
        'devices' => (int) ($summaryRow['devices'] ?? 0),
        'connections' => (int) ($summaryRow['connections'] ?? 0),
    ];
}

function cc_presence_code_online_subscription_counts(
    PDO $controlDb,
    array $config,
    int $resellerId,
    string $host,
    array $codes,
    ?DateTimeImmutable $now = null
): array {
    if ($resellerId < 1 || $codes === []) {
        return [];
    }
    cc_reseller_require_domain();
    $normalized = hulk_normalize_host($host);
    $hostValue = is_string($normalized) && $normalized !== '' ? $normalized : $host;
    if ($hostValue === '') {
        return [];
    }
    $now = cc_presence_now($now);
    $ttlSeconds = (int) ($config['online_ttl_seconds'] ?? 0);
    if ($ttlSeconds < 1) {
        throw new RuntimeException('Presence online TTL is invalid.');
    }
    $cutoff = cc_presence_datetime($now->modify('-' . $ttlSeconds . ' seconds'));
    $counts = [];
    foreach ($codes as $code) {
        $counts[(string) $code] = 0;
    }
    $statement = $controlDb->prepare(
        'SELECT HEX(s.access_code_snapshot) AS code_hex, HEX(s.iptv_username) AS user_hex '
        . 'FROM cc_app_sessions s '
        . 'WHERE s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff '
        . 'AND s.reseller_id = :target_reseller AND HEX(s.host_snapshot) = :target_host '
        . 'GROUP BY HEX(s.access_code_snapshot), HEX(s.iptv_username)'
    );
    $statement->execute([
        'online_cutoff' => $cutoff,
        'target_reseller' => $resellerId,
        'target_host' => strtoupper(bin2hex($hostValue)),
    ]);
    $identities = [];
    while (($row = $statement->fetch()) !== false) {
        $snapshot = hex2bin((string) $row['code_hex']);
        if ($snapshot === false) {
            throw new RuntimeException('Presence code snapshot is invalid.');
        }
        foreach ($codes as $storedCode) {
            // Use the resolver's existing legacy-alias grammar; short codes remain exact.
            if (hulk_access_code_matches((string) $storedCode, $snapshot)) {
                $identities[(string) $storedCode][(string) $row['user_hex']] = true;
            }
        }
    }
    foreach ($identities as $code => $users) {
        $counts[$code] = count($users);
    }
    return $counts;
}

function cc_presence_subscription_token(int $resellerId, string $host, string $username, ?array $config = null): string
{
    $secret = (string) (($config ?? cc_presence_config())['token_secret'] ?? '');
    if ($resellerId < 1 || $secret === '') {
        throw new RuntimeException('Presence subscription token is unavailable.');
    }
    return hash_hmac('sha256', $resellerId . "\0" . $host . "\0" . $username, $secret);
}

function cc_presence_online_now_target_group(
    PDO $controlDb,
    array $config,
    int $resellerId,
    string $token,
    ?DateTimeImmutable $now = null
): ?array {
    $token = strtolower(trim($token));
    if ($resellerId < 1 || preg_match('/^[0-9a-f]{64}$/', $token) !== 1) {
        return null;
    }
    $now = cc_presence_now($now);
    $ttlSeconds = (int) ($config['online_ttl_seconds'] ?? 0);
    if ($ttlSeconds < 1) {
        return null;
    }
    $cutoff = cc_presence_datetime($now->modify('-' . $ttlSeconds . ' seconds'));
    $identities = $controlDb->prepare(
        'SELECT s.reseller_id, MIN(s.host_snapshot) AS host_snapshot, MIN(s.iptv_username) AS iptv_username '
        . 'FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id '
        . 'WHERE s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff AND s.reseller_id = :target_reseller '
        . 'GROUP BY s.reseller_id, HEX(s.host_snapshot), HEX(s.iptv_username)'
    );
    $identities->execute(['online_cutoff' => $cutoff, 'target_reseller' => $resellerId]);
    $identity = null;
    foreach ($identities->fetchAll() as $row) {
        if (!is_array($row)) {
            continue;
        }
        $candidate = cc_presence_subscription_token(
            (int) $row['reseller_id'],
            (string) $row['host_snapshot'],
            (string) $row['iptv_username'],
            $config
        );
        if (hash_equals($candidate, $token)) {
            $identity = $row;
            break;
        }
    }
    if (!is_array($identity)) {
        return null;
    }
    $host = (string) $identity['host_snapshot'];
    $username = (string) $identity['iptv_username'];
    $hostHex = strtoupper(bin2hex($host));
    $userHex = strtoupper(bin2hex($username));
    $parameters = [
        'online_cutoff' => $cutoff,
        'target_reseller' => $resellerId,
        'target_host' => $hostHex,
        'target_user' => $userHex,
    ];
    $groupStatement = $controlDb->prepare(
        'SELECT s.reseller_id, MIN(s.host_snapshot) AS host_snapshot, MIN(s.iptv_username) AS iptv_username, '
        . 'COUNT(*) AS connection_count, COUNT(DISTINCT d.installation_id) AS device_count, '
        . "SUM(CASE WHEN s.platform_class = 'TV' THEN 1 ELSE 0 END) AS tv_connections, "
        . "SUM(CASE WHEN s.platform_class IN ('PHONE', 'TABLET') THEN 1 ELSE 0 END) AS phone_connections, "
        . "SUM(CASE WHEN s.platform_class = 'OTHER' THEN 1 ELSE 0 END) AS other_connections, "
        . 'MIN(s.started_at) AS first_started_at, MAX(s.last_seen_at) AS last_activity_at '
        . 'FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id '
        . 'WHERE s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff '
        . 'AND s.reseller_id = :target_reseller AND HEX(s.host_snapshot) = :target_host AND HEX(s.iptv_username) = :target_user '
        . 'GROUP BY s.reseller_id, HEX(s.host_snapshot), HEX(s.iptv_username)'
    );
    $groupStatement->execute($parameters);
    $group = $groupStatement->fetch();
    if (!is_array($group)) {
        return null;
    }
    $latest = $controlDb->prepare(
        'SELECT s.id, s.reseller_id, s.host_snapshot, s.iptv_username, '
        . 's.access_code_snapshot, s.iptv_password, s.last_seen_at '
        . 'FROM cc_app_sessions s WHERE s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff '
        . 'AND s.reseller_id = :target_reseller AND HEX(s.host_snapshot) = :target_host AND HEX(s.iptv_username) = :target_user '
        . 'ORDER BY s.last_seen_at DESC, s.id DESC LIMIT 1'
    );
    $latest->execute($parameters);
    $latestSession = $latest->fetch();
    $group['latest_session'] = is_array($latestSession) ? $latestSession : null;
    $devices = $controlDb->prepare(
        'SELECT d.installation_id, d.platform_class, d.manufacturer, d.model, '
        . 'd.latest_app_version_name, d.latest_app_version_code, MAX(s.last_seen_at) AS last_seen_at '
        . 'FROM cc_app_sessions s JOIN cc_devices d ON d.id = s.device_id '
        . 'WHERE s.ended_at IS NULL AND s.last_seen_at >= :online_cutoff '
        . 'AND s.reseller_id = :target_reseller AND HEX(s.host_snapshot) = :target_host AND HEX(s.iptv_username) = :target_user '
        . 'GROUP BY d.installation_id, d.platform_class, d.manufacturer, d.model, '
        . 'd.latest_app_version_name, d.latest_app_version_code '
        . 'ORDER BY last_seen_at DESC, d.installation_id ASC LIMIT ' . (CC_PRESENCE_ONLINE_DEVICE_LIMIT + 1)
    );
    $devices->execute($parameters);
    $deviceRows = $devices->fetchAll();
    usort($deviceRows, static function (array $left, array $right): int {
        $comparison = strcmp((string) $right['last_seen_at'], (string) $left['last_seen_at']);
        return $comparison !== 0 ? $comparison : strcmp((string) $left['installation_id'], (string) $right['installation_id']);
    });
    $truncated = count($deviceRows) > CC_PRESENCE_ONLINE_DEVICE_LIMIT;
    if ($truncated) {
        $deviceRows = array_slice($deviceRows, 0, CC_PRESENCE_ONLINE_DEVICE_LIMIT);
    }
    $group['devices'] = [];
    foreach ($deviceRows as $device) {
        $group['devices'][] = [
            'installation_id' => (string) $device['installation_id'],
            'platform_class' => (string) $device['platform_class'],
            'manufacturer' => (string) $device['manufacturer'],
            'model' => (string) $device['model'],
            'app_version_name' => (string) $device['latest_app_version_name'],
            'app_version_code' => (int) $device['latest_app_version_code'],
            'last_seen_at' => (string) $device['last_seen_at'],
        ];
    }
    $group['devices_truncated'] = $truncated;
    $notes = cc_subscriber_notes_attach($controlDb, [$group]);
    return is_array($notes['groups'][0] ?? null) ? $notes['groups'][0] : $group;
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
