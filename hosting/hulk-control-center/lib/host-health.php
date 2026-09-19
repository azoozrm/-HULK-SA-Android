<?php

declare(strict_types=1);

require_once __DIR__ . '/reseller-adapter.php';

const CC_HOST_HEALTH_MAINTENANCE_RESERVE_SECONDS = 5;
const CC_HOST_HEALTH_DNS_KILL_GRACE_MS = 200;

function cc_host_health_database(string $authority, int $connectTimeoutSeconds = 3): PDO
{
    if (!in_array($authority, ['control', 'reseller'], true)) {
        throw new InvalidArgumentException('Unknown Host Health database authority.');
    }
    $database = cc_load_config()['databases'][$authority] ?? null;
    if (!is_array($database)) {
        throw new RuntimeException('Host Health database configuration is unavailable.');
    }
    $options = is_array($database['options'] ?? null) ? $database['options'] : [];
    $options[PDO::ATTR_ERRMODE] = PDO::ERRMODE_EXCEPTION;
    $options[PDO::ATTR_DEFAULT_FETCH_MODE] = PDO::FETCH_ASSOC;
    $options[PDO::ATTR_EMULATE_PREPARES] = false;
    $options[PDO::ATTR_TIMEOUT] = max(1, min(3, $connectTimeoutSeconds));
    return new PDO(
        (string) ($database['dsn'] ?? ''),
        (string) ($database['username'] ?? ''),
        (string) ($database['password'] ?? ''),
        $options
    );
}

function cc_host_health_config(?array $root = null): array
{
    $source = ($root ?? cc_load_config())['host_health'] ?? null;
    if (!is_array($source)) {
        throw new RuntimeException('Host Health configuration is not installed.');
    }
    $config = [
        'connect_timeout_ms' => (int) ($source['connect_timeout_ms'] ?? 0),
        'dns_timeout_ms' => (int) (
            $source['dns_timeout_ms'] ?? min(2000, (int) ($source['connect_timeout_ms'] ?? 0))
        ),
        'request_timeout_ms' => (int) ($source['request_timeout_ms'] ?? 0),
        'max_runtime_seconds' => (int) ($source['max_runtime_seconds'] ?? 0),
        'max_hosts_per_run' => (int) ($source['max_hosts_per_run'] ?? 0),
        'candidate_limit' => (int) ($source['candidate_limit'] ?? 0),
        'retention_days' => (int) ($source['retention_days'] ?? 0),
        'cleanup_batch_size' => (int) ($source['cleanup_batch_size'] ?? 0),
        'user_agent' => trim((string) ($source['user_agent'] ?? '')),
    ];
    if ($config['connect_timeout_ms'] < 250 || $config['connect_timeout_ms'] > 3000) {
        throw new RuntimeException('Host Health connect timeout is invalid.');
    }
    if ($config['dns_timeout_ms'] < 250 || $config['dns_timeout_ms'] > 3000) {
        throw new RuntimeException('Host Health DNS timeout is invalid.');
    }
    if (
        $config['request_timeout_ms'] < $config['connect_timeout_ms']
        || $config['request_timeout_ms'] > 5000
    ) {
        throw new RuntimeException('Host Health request timeout is invalid.');
    }
    if ($config['max_runtime_seconds'] < 10 || $config['max_runtime_seconds'] > 45) {
        throw new RuntimeException('Host Health runtime bound is invalid.');
    }
    if ($config['max_hosts_per_run'] < 1 || $config['max_hosts_per_run'] > 20) {
        throw new RuntimeException('Host Health work bound is invalid.');
    }
    if (
        $config['candidate_limit'] < $config['max_hosts_per_run']
        || $config['candidate_limit'] > 500
    ) {
        throw new RuntimeException('Host Health candidate bound is invalid.');
    }
    if ($config['retention_days'] < 7 || $config['retention_days'] > 90) {
        throw new RuntimeException('Host Health retention is invalid.');
    }
    if ($config['cleanup_batch_size'] < 1 || $config['cleanup_batch_size'] > 1000) {
        throw new RuntimeException('Host Health cleanup batch is invalid.');
    }
    if (
        $config['user_agent'] === ''
        || strlen($config['user_agent']) > 128
        || preg_match('/[\x00-\x1F\x7F]/', $config['user_agent'])
    ) {
        throw new RuntimeException('Host Health user agent is invalid.');
    }
    if (!extension_loaded('curl')) {
        throw new RuntimeException('Host Health requires the cURL extension.');
    }
    if (!function_exists('proc_open')) {
        throw new RuntimeException('Host Health requires bounded DNS subprocess support.');
    }
    return $config;
}

function cc_host_health_datetime(DateTimeImmutable $value): string
{
    return $value->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s.u');
}

function cc_host_health_acquire_scheduler_lock(PDO $controlDb): bool
{
    if ($controlDb->getAttribute(PDO::ATTR_DRIVER_NAME) !== 'mysql') {
        throw new RuntimeException('Host Health scheduler locking requires MySQL.');
    }
    $statement = $controlDb->prepare('SELECT GET_LOCK(:lock_name, 0)');
    $statement->execute(['lock_name' => 'hulk_cc_phase7_maintenance']);
    return (int) $statement->fetchColumn() === 1;
}

function cc_host_health_apply_database_deadlines(PDO $database): void
{
    if ($database->getAttribute(PDO::ATTR_DRIVER_NAME) !== 'mysql') {
        throw new RuntimeException('Host Health database deadlines require MySQL.');
    }
    $version = (string) $database->query('SELECT VERSION()')->fetchColumn();
    $database->exec('SET SESSION innodb_lock_wait_timeout = 1');
    $database->exec('SET SESSION lock_wait_timeout = 1');
    if (stripos($version, 'mariadb') !== false) {
        $database->exec('SET SESSION max_statement_time = 1');
    } else {
        $database->exec('SET SESSION MAX_EXECUTION_TIME = 1000');
    }
}

function cc_host_health_release_scheduler_lock(PDO $controlDb): void
{
    $statement = $controlDb->prepare('SELECT RELEASE_LOCK(:lock_name)');
    $statement->execute(['lock_name' => 'hulk_cc_phase7_maintenance']);
}

function cc_host_health_candidate_count(PDO $resellerDb): int
{
    cc_reseller_require_domain();
    return (int) $resellerDb->query(
        "SELECT COUNT(*) FROM resellers WHERE status = 'active' "
        . "AND host IS NOT NULL AND TRIM(host) <> ''"
    )->fetchColumn();
}

function cc_host_health_candidates(PDO $resellerDb, int $limit, int $offset = 0): array
{
    cc_reseller_require_domain();
    $limit = max(1, min(500, $limit));
    $offset = max(0, $offset);
    $rows = $resellerDb->query(
        "SELECT reseller_id, reseller_name, host FROM resellers WHERE status = 'active' "
        . "AND host IS NOT NULL AND TRIM(host) <> '' ORDER BY reseller_id ASC LIMIT " . $limit
        . ' OFFSET ' . $offset
    )->fetchAll();
    $targets = [];
    foreach ($rows as $row) {
        if (!is_array($row)) {
            continue;
        }
        $resellerId = (int) ($row['reseller_id'] ?? 0);
        $host = hulk_normalize_host((string) ($row['host'] ?? ''));
        if ($resellerId < 1 || $host === null) {
            continue;
        }
        $targets[] = [
            'reseller_id' => $resellerId,
            'reseller_name' => (string) ($row['reseller_name'] ?? ('#' . $resellerId)),
            'host' => $host,
            'host_fingerprint' => hash('sha256', $host),
        ];
    }
    return $targets;
}

function cc_host_health_candidate_window(
    PDO $resellerDb,
    int $limit,
    DateTimeImmutable $now
): array {
    $limit = max(1, min(500, $limit));
    $total = cc_host_health_candidate_count($resellerDb);
    if ($total === 0) {
        return ['row_count' => 0, 'offset' => 0, 'targets' => []];
    }
    $slot = intdiv($now->getTimestamp(), 300);
    $offset = $total <= $limit ? 0 : ($slot * $limit) % $total;
    $targets = cc_host_health_candidates($resellerDb, $limit, $offset);
    $remaining = $limit - count($targets);
    if ($offset > 0 && $remaining > 0) {
        $targets = array_merge($targets, cc_host_health_candidates($resellerDb, $remaining, 0));
    }
    return ['row_count' => $total, 'offset' => $offset, 'targets' => $targets];
}

function cc_host_health_prioritize(PDO $controlDb, array $targets, int $maximum): array
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
    $statement = $controlDb->prepare(
        'SELECT reseller_id, host_fingerprint, MAX(checked_at) AS last_checked_at '
        . 'FROM cc_host_health_checks WHERE ' . implode(' OR ', $conditions)
        . ' GROUP BY reseller_id, host_fingerprint'
    );
    $statement->execute($parameters);
    $lastChecks = [];
    foreach ($statement->fetchAll() as $row) {
        if (!is_array($row)) {
            continue;
        }
        $key = (int) $row['reseller_id'] . ':' . (string) $row['host_fingerprint'];
        $lastChecks[$key] = (string) $row['last_checked_at'];
    }
    usort($targets, static function (array $left, array $right) use ($lastChecks): int {
        $leftKey = (int) $left['reseller_id'] . ':' . (string) $left['host_fingerprint'];
        $rightKey = (int) $right['reseller_id'] . ':' . (string) $right['host_fingerprint'];
        $leftChecked = $lastChecks[$leftKey] ?? '';
        $rightChecked = $lastChecks[$rightKey] ?? '';
        return [$leftChecked !== '', $leftChecked, (int) $left['reseller_id']]
            <=> [$rightChecked !== '', $rightChecked, (int) $right['reseller_id']];
    });
    return array_slice($targets, 0, max(1, $maximum));
}

function cc_host_health_classify(
    int $curlError,
    int $httpStatus,
    bool $hasPrimaryIp,
    bool $hasTcpConnection = false
): array
{
    if ($curlError === 0) {
        return $httpStatus >= 200 && $httpStatus < 400
            ? ['probe_result' => 'HEALTHY', 'dns_ok' => true, 'tcp_ok' => true, 'failure_code' => null]
            : ['probe_result' => 'HTTP_ERROR', 'dns_ok' => true, 'tcp_ok' => true, 'failure_code' => 'HTTP_STATUS'];
    }
    $dnsFailure = defined('CURLE_COULDNT_RESOLVE_HOST') && $curlError === CURLE_COULDNT_RESOLVE_HOST;
    $connectFailure = defined('CURLE_COULDNT_CONNECT') && $curlError === CURLE_COULDNT_CONNECT;
    $timeout = defined('CURLE_OPERATION_TIMEDOUT') && $curlError === CURLE_OPERATION_TIMEDOUT;
    $tlsErrors = array_filter([
        defined('CURLE_SSL_CONNECT_ERROR') ? CURLE_SSL_CONNECT_ERROR : null,
        defined('CURLE_PEER_FAILED_VERIFICATION') ? CURLE_PEER_FAILED_VERIFICATION : null,
        defined('CURLE_SSL_CERTPROBLEM') ? CURLE_SSL_CERTPROBLEM : null,
    ], static fn (mixed $value): bool => is_int($value));
    if ($dnsFailure) {
        return ['probe_result' => 'DNS_FAILURE', 'dns_ok' => false, 'tcp_ok' => false, 'failure_code' => 'DNS_FAILURE'];
    }
    if ($connectFailure) {
        return ['probe_result' => 'CONNECT_FAILURE', 'dns_ok' => $hasPrimaryIp, 'tcp_ok' => false, 'failure_code' => 'CONNECT_FAILURE'];
    }
    if ($timeout) {
        return [
            'probe_result' => 'TIMEOUT',
            'dns_ok' => $hasPrimaryIp,
            'tcp_ok' => $hasTcpConnection,
            'failure_code' => 'TIMEOUT',
        ];
    }
    if (in_array($curlError, $tlsErrors, true)) {
        return ['probe_result' => 'TLS_FAILURE', 'dns_ok' => $hasPrimaryIp, 'tcp_ok' => true, 'failure_code' => 'TLS_FAILURE'];
    }
    return [
        'probe_result' => 'NETWORK_FAILURE',
        'dns_ok' => $hasPrimaryIp,
        'tcp_ok' => $hasTcpConnection || $httpStatus > 0,
        'failure_code' => 'CURL_' . min(9999, max(1, $curlError)),
    ];
}

function cc_host_health_public_ip(string $address): bool
{
    return filter_var(
        $address,
        FILTER_VALIDATE_IP,
        FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE
    ) !== false;
}

function cc_host_health_dns_lookup(string $hostname, int $timeoutMs): array
{
    if (!function_exists('proc_open') || !defined('PHP_BINARY') || PHP_BINARY === '') {
        return ['status' => 'unavailable', 'addresses' => []];
    }
    $descriptors = [
        0 => ['pipe', 'r'],
        1 => ['pipe', 'w'],
        2 => ['pipe', 'w'],
    ];
    $pipes = [];
    $process = @proc_open(
        [PHP_BINARY, dirname(__DIR__) . '/tools/host_health_dns.php'],
        $descriptors,
        $pipes,
        null,
        null,
        ['bypass_shell' => true]
    );
    if (!is_resource($process) || count($pipes) !== 3) {
        return ['status' => 'unavailable', 'addresses' => []];
    }
    fwrite($pipes[0], $hostname);
    fclose($pipes[0]);
    stream_set_blocking($pipes[1], false);
    stream_set_blocking($pipes[2], false);
    $output = '';
    $deadline = microtime(true) + max(250, min(3000, $timeoutMs)) / 1000;
    $timedOut = false;
    while (true) {
        $output .= (string) stream_get_contents($pipes[1], 8192 - strlen($output));
        $status = proc_get_status($process);
        if (!$status['running']) {
            break;
        }
        if (microtime(true) >= $deadline) {
            $timedOut = true;
            @proc_terminate($process, 15);
            $killDeadline = microtime(true) + CC_HOST_HEALTH_DNS_KILL_GRACE_MS / 1000;
            do {
                usleep(10000);
                $status = proc_get_status($process);
            } while ($status['running'] && microtime(true) < $killDeadline);
            if ($status['running']) {
                @proc_terminate($process, 9);
            }
            break;
        }
        usleep(10000);
    }
    $output .= (string) stream_get_contents($pipes[1], max(0, 8192 - strlen($output)));
    fclose($pipes[1]);
    fclose($pipes[2]);
    @proc_close($process);
    if ($timedOut) {
        return ['status' => 'timeout', 'addresses' => []];
    }
    $decoded = json_decode($output, true);
    if (!is_array($decoded) || ($decoded['status'] ?? null) !== 'ok' || !is_array($decoded['addresses'] ?? null)) {
        return ['status' => 'failure', 'addresses' => []];
    }
    $addresses = [];
    foreach (array_slice($decoded['addresses'], 0, 16) as $address) {
        if (is_string($address) && filter_var($address, FILTER_VALIDATE_IP) !== false) {
            $addresses[$address] = $address;
        }
    }
    return ['status' => $addresses === [] ? 'failure' : 'ok', 'addresses' => array_values($addresses)];
}

function cc_host_health_resolve_target(string $host, int $dnsTimeoutMs): array
{
    $parts = parse_url($host);
    if (!is_array($parts) || !isset($parts['host'], $parts['scheme'])) {
        return ['ok' => false, 'dns_ok' => false, 'failure_code' => 'INVALID_TARGET'];
    }
    $hostname = trim((string) $parts['host'], '[]');
    $scheme = strtolower((string) $parts['scheme']);
    if (!in_array($scheme, ['http', 'https'], true)) {
        return ['ok' => false, 'dns_ok' => false, 'failure_code' => 'INVALID_TARGET'];
    }
    $port = isset($parts['port']) ? (int) $parts['port'] : ($scheme === 'https' ? 443 : 80);
    $addresses = [];
    if (filter_var($hostname, FILTER_VALIDATE_IP) !== false) {
        $addresses[] = $hostname;
    } else {
        $lookup = cc_host_health_dns_lookup($hostname, $dnsTimeoutMs);
        if ($lookup['status'] !== 'ok') {
            $failureCode = match ($lookup['status']) {
                'timeout' => 'DNS_TIMEOUT',
                'unavailable' => 'DNS_RUNTIME_UNAVAILABLE',
                default => 'DNS_FAILURE',
            };
            return ['ok' => false, 'dns_ok' => false, 'failure_code' => $failureCode];
        }
        $addresses = $lookup['addresses'];
    }
    $addresses = array_values($addresses);
    if ($addresses === []) {
        return ['ok' => false, 'dns_ok' => false, 'failure_code' => 'DNS_FAILURE'];
    }
    foreach ($addresses as $address) {
        if (!cc_host_health_public_ip($address)) {
            return ['ok' => false, 'dns_ok' => true, 'failure_code' => 'NON_PUBLIC_ADDRESS'];
        }
    }
    sort($addresses, SORT_STRING);
    $pinnedAddress = str_contains($addresses[0], ':') ? '[' . $addresses[0] . ']' : $addresses[0];
    $resolveHost = str_contains($hostname, ':') ? '[' . $hostname . ']' : $hostname;
    return [
        'ok' => true,
        'dns_ok' => true,
        'failure_code' => null,
        'resolve' => $resolveHost . ':' . $port . ':' . $pinnedAddress,
    ];
}

function cc_host_health_probe(string $host, array $config): array
{
    $started = microtime(true);
    $resolution = cc_host_health_resolve_target($host, (int) $config['dns_timeout_ms']);
    if (!$resolution['ok']) {
        if (($resolution['failure_code'] ?? null) === 'DNS_RUNTIME_UNAVAILABLE') {
            throw new RuntimeException('Host Health bounded DNS runtime is unavailable.');
        }
        $dnsOk = (bool) $resolution['dns_ok'];
        return [
            'probe_result' => $dnsOk ? 'POLICY_BLOCKED' : 'DNS_FAILURE',
            'dns_ok' => $dnsOk,
            'tcp_ok' => false,
            'failure_code' => (string) $resolution['failure_code'],
            'http_status' => null,
            'latency_ms' => max(0, (int) round((microtime(true) - $started) * 1000)),
        ];
    }
    $handle = curl_init($host);
    if ($handle === false) {
        throw new RuntimeException('Host Health cURL initialization failed.');
    }
    $elapsedMs = max(0, (int) round((microtime(true) - $started) * 1000));
    $remainingMs = (int) $config['request_timeout_ms'] - $elapsedMs;
    if ($remainingMs < 100) {
        curl_close($handle);
        return [
            'probe_result' => 'TIMEOUT',
            'dns_ok' => true,
            'tcp_ok' => false,
            'failure_code' => 'REQUEST_DEADLINE',
            'http_status' => null,
            'latency_ms' => $elapsedMs,
        ];
    }
    curl_setopt_array($handle, [
        CURLOPT_NOBODY => true,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_FOLLOWLOCATION => false,
        CURLOPT_PROXY => '',
        CURLOPT_CONNECTTIMEOUT_MS => min((int) $config['connect_timeout_ms'], $remainingMs),
        CURLOPT_TIMEOUT_MS => $remainingMs,
        CURLOPT_NOSIGNAL => true,
        CURLOPT_PROTOCOLS => CURLPROTO_HTTP | CURLPROTO_HTTPS,
        CURLOPT_RESOLVE => [(string) $resolution['resolve']],
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_SSL_VERIFYHOST => 2,
        CURLOPT_USERAGENT => (string) $config['user_agent'],
    ]);
    curl_exec($handle);
    $curlError = curl_errno($handle);
    $httpStatus = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    $primaryIp = (string) curl_getinfo($handle, CURLINFO_PRIMARY_IP);
    $connectTime = (float) curl_getinfo($handle, CURLINFO_CONNECT_TIME);
    curl_close($handle);
    $classification = cc_host_health_classify(
        $curlError,
        $httpStatus,
        $primaryIp !== '',
        $connectTime > 0.0
    );
    return $classification + [
        'http_status' => $httpStatus > 0 ? $httpStatus : null,
        'latency_ms' => max(0, (int) round((microtime(true) - $started) * 1000)),
    ];
}

function cc_host_health_store(PDO $controlDb, array $target, array $observation, DateTimeImmutable $checkedAt): void
{
    $statement = $controlDb->prepare(
        'INSERT INTO cc_host_health_checks '
        . '(reseller_id, host_fingerprint, checked_at, probe_result, dns_ok, tcp_ok, http_status, latency_ms, failure_code) '
        . 'VALUES (:reseller_id, :host_fingerprint, :checked_at, :probe_result, :dns_ok, :tcp_ok, '
        . ':http_status, :latency_ms, :failure_code)'
    );
    $statement->execute([
        'reseller_id' => (int) $target['reseller_id'],
        'host_fingerprint' => (string) $target['host_fingerprint'],
        'checked_at' => cc_host_health_datetime($checkedAt),
        'probe_result' => (string) $observation['probe_result'],
        'dns_ok' => !empty($observation['dns_ok']) ? 1 : 0,
        'tcp_ok' => !empty($observation['tcp_ok']) ? 1 : 0,
        'http_status' => $observation['http_status'],
        'latency_ms' => min(4294967295, max(0, (int) $observation['latency_ms'])),
        'failure_code' => $observation['failure_code'],
    ]);
}

function cc_host_health_cleanup(PDO $controlDb, array $config, ?DateTimeImmutable $now = null): int
{
    $cutoff = ($now ?? new DateTimeImmutable('now', new DateTimeZone('UTC')))
        ->setTimezone(new DateTimeZone('UTC'))
        ->modify('-' . (int) $config['retention_days'] . ' days');
    $batch = (int) $config['cleanup_batch_size'];
    $expired = $controlDb->prepare(
        'SELECT /*+ MAX_EXECUTION_TIME(1000) */ id FROM cc_host_health_checks WHERE checked_at < :cutoff '
        . 'ORDER BY id ASC LIMIT ' . $batch
    );
    $expired->execute(['cutoff' => cc_host_health_datetime($cutoff)]);
    $ids = array_values(array_map('intval', $expired->fetchAll(PDO::FETCH_COLUMN)));
    if ($ids === []) {
        return 0;
    }
    $delete = $controlDb->prepare(
        'DELETE FROM cc_host_health_checks WHERE id IN (' . implode(',', $ids) . ')'
    );
    $delete->execute();
    return $delete->rowCount();
}

function cc_host_health_run(
    PDO $controlDb,
    PDO $resellerDb,
    array $config,
    ?callable $probe = null,
    ?DateTimeImmutable $now = null,
    ?float $startedAt = null
): array {
    $startedAt ??= microtime(true);
    $runNow = $now ?? new DateTimeImmutable('now', new DateTimeZone('UTC'));
    $deadline = $startedAt + (int) $config['max_runtime_seconds'] - CC_HOST_HEALTH_MAINTENANCE_RESERVE_SECONDS;
    $window = cc_host_health_candidate_window($resellerDb, (int) $config['candidate_limit'], $runNow);
    $candidateCount = count($window['targets']);
    $targets = $window['targets'];
    $targets = cc_host_health_prioritize($controlDb, $targets, (int) $config['max_hosts_per_run']);
    $checked = 0;
    foreach ($targets as $target) {
        $remainingMs = (int) floor(($deadline - microtime(true)) * 1000);
        if ($remainingMs < 250) {
            break;
        }
        $boundedConfig = $config;
        $boundedConfig['request_timeout_ms'] = min((int) $config['request_timeout_ms'], $remainingMs);
        $boundedConfig['connect_timeout_ms'] = min(
            (int) $config['connect_timeout_ms'],
            (int) $boundedConfig['request_timeout_ms']
        );
        $boundedConfig['dns_timeout_ms'] = min(
            (int) $config['dns_timeout_ms'],
            (int) $boundedConfig['request_timeout_ms']
        );
        $observation = $probe === null
            ? cc_host_health_probe((string) $target['host'], $boundedConfig)
            : $probe((string) $target['host'], $boundedConfig);
        cc_host_health_store(
            $controlDb,
            $target,
            $observation,
            $now ?? new DateTimeImmutable('now', new DateTimeZone('UTC'))
        );
        $checked++;
    }
    return [
        'eligible_row_count' => (int) $window['row_count'],
        'candidate_offset' => (int) $window['offset'],
        'candidate_count' => $candidateCount,
        'checked_count' => $checked,
        'skipped_for_runtime' => count($targets) - $checked,
        'retention_deleted' => cc_host_health_cleanup($controlDb, $config, $now),
    ];
}
