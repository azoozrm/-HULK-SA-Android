<?php

declare(strict_types=1);

require_once __DIR__ . '/reseller-adapter.php';

final class CcPresenceException extends RuntimeException
{
    public function __construct(
        public readonly string $apiCode,
        public readonly int $httpStatus,
        string $message
    ) {
        parent::__construct($message);
    }
}

function cc_presence_config(?array $root = null): array
{
    $presence = ($root ?? cc_load_config())['presence'] ?? null;
    if (!is_array($presence)) {
        throw new RuntimeException('Presence configuration is not installed.');
    }

    $limits = $presence['rate_limits'] ?? null;
    $config = [
        'heartbeat_seconds' => (int) ($presence['heartbeat_seconds'] ?? 0),
        'online_ttl_seconds' => (int) ($presence['online_ttl_seconds'] ?? 0),
        'session_retention_days' => (int) ($presence['session_retention_days'] ?? 0),
        'device_retention_days' => (int) ($presence['device_retention_days'] ?? 0),
        'cleanup_batch_size' => (int) ($presence['cleanup_batch_size'] ?? 0),
        'rate_limit_window_seconds' => (int) ($presence['rate_limit_window_seconds'] ?? 0),
        'rate_limits' => is_array($limits) ? $limits : [],
        'rate_limit_secret' => (string) ($presence['rate_limit_secret'] ?? ''),
        'token_secret' => (string) ($presence['token_secret'] ?? ''),
    ];

    if ($config['heartbeat_seconds'] < 15 || $config['heartbeat_seconds'] > 600) {
        throw new RuntimeException('Presence heartbeat configuration is invalid.');
    }
    if (
        $config['online_ttl_seconds'] < $config['heartbeat_seconds'] * 2
        || $config['online_ttl_seconds'] > 3600
    ) {
        throw new RuntimeException('Presence online TTL configuration is invalid.');
    }
    if ($config['session_retention_days'] < 1 || $config['session_retention_days'] > 3650) {
        throw new RuntimeException('Presence session retention configuration is invalid.');
    }
    if ($config['device_retention_days'] < 1 || $config['device_retention_days'] > 3650) {
        throw new RuntimeException('Presence device retention configuration is invalid.');
    }
    if ($config['cleanup_batch_size'] < 1 || $config['cleanup_batch_size'] > 1000) {
        throw new RuntimeException('Presence cleanup batch configuration is invalid.');
    }
    if ($config['rate_limit_window_seconds'] < 10 || $config['rate_limit_window_seconds'] > 3600) {
        throw new RuntimeException('Presence rate-limit window configuration is invalid.');
    }
    foreach (['start', 'heartbeat', 'end'] as $scope) {
        $attempts = (int) ($config['rate_limits'][$scope] ?? 0);
        if ($attempts < 1 || $attempts > 1000) {
            throw new RuntimeException('Presence rate-limit attempt configuration is invalid.');
        }
        $config['rate_limits'][$scope] = $attempts;
    }
    if (
        strlen($config['rate_limit_secret']) < 32
        || str_starts_with($config['rate_limit_secret'], 'CHANGE_ME')
    ) {
        throw new RuntimeException('Presence rate-limit secret is not installed.');
    }
    if (
        strlen($config['token_secret']) < 32
        || str_starts_with($config['token_secret'], 'CHANGE_ME')
    ) {
        throw new RuntimeException('Presence token secret is not installed.');
    }

    return $config;
}

function cc_presence_now(?DateTimeImmutable $now = null): DateTimeImmutable
{
    return ($now ?? new DateTimeImmutable('now'))->setTimezone(new DateTimeZone('UTC'));
}

function cc_presence_datetime(DateTimeImmutable $value): string
{
    return $value->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s.u');
}

function cc_presence_is_uuid(string $value): bool
{
    return preg_match(
        '/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/Di',
        $value
    ) === 1;
}

function cc_presence_string(mixed $value, string $field, int $maximum, bool $trim = false): string
{
    if (!is_string($value)) {
        throw new CcPresenceException('INVALID_REQUEST', 422, $field . ' must be a string.');
    }
    $result = $trim ? trim($value) : $value;
    if ($result === '' || trim($result) === '' || strlen($result) > $maximum) {
        throw new CcPresenceException('INVALID_REQUEST', 422, $field . ' is outside its allowed length.');
    }
    if (preg_match('//u', $result) !== 1 || preg_match('/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/', $result)) {
        throw new CcPresenceException('INVALID_REQUEST', 422, $field . ' contains invalid characters.');
    }
    return $result;
}

function cc_presence_integer(mixed $value, string $field, int $minimum, int $maximum): int
{
    if (!is_int($value) || $value < $minimum || $value > $maximum) {
        throw new CcPresenceException('INVALID_REQUEST', 422, $field . ' is outside its allowed range.');
    }
    return $value;
}

function cc_presence_assert_keys(array $value, array $expected, string $field): void
{
    $actual = array_keys($value);
    sort($actual);
    sort($expected);
    if ($actual !== $expected) {
        throw new CcPresenceException('INVALID_REQUEST', 422, $field . ' contains an unsupported shape.');
    }
}

function cc_presence_validate_start(array $payload): array
{
    cc_presence_assert_keys($payload, [
        'contractVersion', 'sessionId', 'installationId', 'accessCode', 'iptvUsername',
        'iptvPassword', 'host', 'authenticatedAtEpochMs', 'device', 'app',
    ], 'request');
    if (($payload['contractVersion'] ?? null) !== 1) {
        throw new CcPresenceException('UNSUPPORTED_CONTRACT', 422, 'Unsupported Presence contract version.');
    }

    $sessionId = strtolower(cc_presence_string($payload['sessionId'] ?? null, 'sessionId', 64, true));
    $installationId = strtolower(
        cc_presence_string($payload['installationId'] ?? null, 'installationId', 64, true)
    );
    if (!cc_presence_is_uuid($sessionId) || !cc_presence_is_uuid($installationId)) {
        throw new CcPresenceException('INVALID_REQUEST', 422, 'Session and installation identifiers must be UUIDs.');
    }

    cc_reseller_require_domain();
    $rawCode = cc_presence_string($payload['accessCode'] ?? null, 'accessCode', 64, true);
    $accessCode = hulk_normalize_access_code($rawCode);
    if ($accessCode === null) {
        throw new CcPresenceException('INVALID_ACCESS_CODE', 404, 'The access code is invalid.');
    }
    $host = hulk_normalize_host(cc_presence_string($payload['host'] ?? null, 'host', 2048, true));
    if ($host === null) {
        throw new CcPresenceException('INVALID_REQUEST', 422, 'The authenticated host is invalid.');
    }

    $device = $payload['device'] ?? null;
    $app = $payload['app'] ?? null;
    if (!is_array($device) || array_is_list($device) || !is_array($app) || array_is_list($app)) {
        throw new CcPresenceException('INVALID_REQUEST', 422, 'Device and app metadata must be objects.');
    }
    cc_presence_assert_keys(
        $device,
        ['platformClass', 'manufacturer', 'model', 'androidRelease', 'sdkInt'],
        'device'
    );
    cc_presence_assert_keys($app, ['versionName', 'versionCode'], 'app');
    $platform = strtoupper(cc_presence_string($device['platformClass'] ?? null, 'platformClass', 16, true));
    if (!in_array($platform, ['PHONE', 'TABLET', 'TV', 'OTHER'], true)) {
        throw new CcPresenceException('INVALID_REQUEST', 422, 'The platform class is invalid.');
    }

    return [
        'session_id' => $sessionId,
        'installation_id' => $installationId,
        'access_code' => $accessCode,
        'access_code_snapshot' => $rawCode,
        'iptv_username' => cc_presence_string($payload['iptvUsername'] ?? null, 'iptvUsername', 255),
        'iptv_password' => cc_presence_string($payload['iptvPassword'] ?? null, 'iptvPassword', 512),
        'host' => $host,
        'authenticated_at_client_ms' => cc_presence_integer(
            $payload['authenticatedAtEpochMs'] ?? null,
            'authenticatedAtEpochMs',
            0,
            PHP_INT_MAX
        ),
        'platform_class' => $platform,
        'manufacturer' => cc_presence_string($device['manufacturer'] ?? null, 'manufacturer', 100, true),
        'model' => cc_presence_string($device['model'] ?? null, 'model', 100, true),
        'android_release' => cc_presence_string(
            $device['androidRelease'] ?? null,
            'androidRelease',
            32,
            true
        ),
        'android_sdk_int' => cc_presence_integer($device['sdkInt'] ?? null, 'sdkInt', 1, 1000),
        'app_version_name' => cc_presence_string($app['versionName'] ?? null, 'versionName', 32, true),
        'app_version_code' => cc_presence_integer(
            $app['versionCode'] ?? null,
            'versionCode',
            1,
            2147483647
        ),
    ];
}

function cc_presence_validate_session_payload(array $payload, bool $withReason): array
{
    cc_presence_assert_keys($payload, $withReason ? ['sessionId', 'reason'] : ['sessionId'], 'request');
    $sessionId = strtolower(cc_presence_string($payload['sessionId'] ?? null, 'sessionId', 64, true));
    if (!cc_presence_is_uuid($sessionId)) {
        throw new CcPresenceException('INVALID_REQUEST', 422, 'The session identifier must be a UUID.');
    }
    $result = ['session_id' => $sessionId];
    if ($withReason) {
        $reason = strtoupper(cc_presence_string($payload['reason'] ?? null, 'reason', 32, true));
        if (!in_array($reason, ['LOGOUT', 'ACCOUNT_REPLACED', 'APP_SHUTDOWN'], true)) {
            throw new CcPresenceException('INVALID_REQUEST', 422, 'The end reason is invalid.');
        }
        $result['reason'] = $reason;
    }
    return $result;
}

function cc_presence_validate_reseller(PDO $db, array $input): array
{
    $statement = $db->prepare(
        'SELECT reseller_id, access_code, access_code_hash, host, status '
        . 'FROM resellers WHERE access_code_hash = :access_code_hash LIMIT 1'
    );
    $statement->execute(['access_code_hash' => hulk_access_code_hash($input['access_code'])]);
    $reseller = $statement->fetch();
    if (!is_array($reseller)) {
        throw new CcPresenceException('INVALID_ACCESS_CODE', 404, 'The access code is invalid.');
    }
    $storedCode = hulk_normalize_access_code((string) ($reseller['access_code'] ?? ''));
    $storedHash = strtolower((string) ($reseller['access_code_hash'] ?? ''));
    if (
        $storedCode === null
        || !hash_equals($storedCode, $input['access_code'])
        || !preg_match('/^[a-f0-9]{64}$/D', $storedHash)
        || !hash_equals(hulk_access_code_hash($storedCode), $storedHash)
    ) {
        throw new CcPresenceException('INVALID_ACCESS_CODE', 404, 'The access code is invalid.');
    }
    if (($reseller['status'] ?? '') !== HULK_ACTIVE_STATUS) {
        throw new CcPresenceException('RESELLER_INACTIVE', 403, 'The reseller is inactive.');
    }
    $currentHost = hulk_normalize_host((string) ($reseller['host'] ?? ''));
    if ($currentHost === null || !hash_equals($currentHost, $input['host'])) {
        throw new CcPresenceException('HOST_MISMATCH', 409, 'The authenticated host is no longer current.');
    }
    return ['reseller_id' => (int) $reseller['reseller_id'], 'host' => $currentHost];
}

function cc_presence_driver(PDO $db): string
{
    return strtolower((string) $db->getAttribute(PDO::ATTR_DRIVER_NAME));
}

function cc_presence_for_update(PDO $db): string
{
    return cc_presence_driver($db) === 'mysql' ? ' FOR UPDATE' : '';
}

function cc_presence_retryable_database_conflict(PDOException $exception): bool
{
    $sqlState = (string) $exception->getCode();
    $driverCode = (int) ($exception->errorInfo[1] ?? 0);
    return in_array($sqlState, ['23000', '40001'], true)
        || in_array($driverCode, [1062, 1205, 1213], true);
}

function cc_presence_client_hash(string $scope, string $clientIdentifier, string $secret): string
{
    return hash_hmac('sha256', $scope . "\0" . $clientIdentifier, $secret);
}

function cc_presence_rate_limit(
    PDO $db,
    string $scope,
    string $clientIdentifier,
    array $config,
    DateTimeImmutable $now
): void {
    if (!in_array($scope, ['start', 'heartbeat', 'end'], true)) {
        throw new InvalidArgumentException('Unknown Presence rate-limit scope.');
    }
    $hash = cc_presence_client_hash($scope, $clientIdentifier, $config['rate_limit_secret']);
    $windowSeconds = (int) $config['rate_limit_window_seconds'];
    $maximum = (int) $config['rate_limits'][$scope];
    $nowString = cc_presence_datetime($now);

    for ($attempt = 0; $attempt < 2; $attempt++) {
        $started = !$db->inTransaction();
        if ($started) {
            $db->beginTransaction();
        }
        try {
            $statement = $db->prepare(
                'SELECT window_started_at, attempts FROM cc_api_rate_limits '
                . 'WHERE scope = :scope AND client_hash = :client_hash'
                . cc_presence_for_update($db)
            );
            $statement->execute(['scope' => $scope, 'client_hash' => $hash]);
            $row = $statement->fetch();
            if (!is_array($row)) {
                $insert = $db->prepare(
                    'INSERT INTO cc_api_rate_limits (scope, client_hash, window_started_at, attempts) '
                    . 'VALUES (:scope, :client_hash, :window_started_at, 1)'
                );
                $insert->execute([
                    'scope' => $scope,
                    'client_hash' => $hash,
                    'window_started_at' => $nowString,
                ]);
                if ($started) {
                    $db->commit();
                }
                return;
            }

            $windowStart = new DateTimeImmutable((string) $row['window_started_at'], new DateTimeZone('UTC'));
            $expired = $windowStart->getTimestamp() <= $now->getTimestamp() - $windowSeconds;
            $nextAttempts = $expired ? 1 : (int) $row['attempts'] + 1;
            $update = $db->prepare(
                'UPDATE cc_api_rate_limits SET window_started_at = :window_started_at, attempts = :attempts '
                . 'WHERE scope = :scope AND client_hash = :client_hash'
            );
            $update->execute([
                'window_started_at' => $expired ? $nowString : (string) $row['window_started_at'],
                'attempts' => min($nextAttempts, 65535),
                'scope' => $scope,
                'client_hash' => $hash,
            ]);
            if ($started) {
                $db->commit();
            }
            if (!$expired && $nextAttempts > $maximum) {
                throw new CcPresenceException('RATE_LIMITED', 429, 'Too many Presence requests.');
            }
            return;
        } catch (PDOException $exception) {
            if ($started && $db->inTransaction()) {
                $db->rollBack();
            }
            if ($attempt === 0 && cc_presence_retryable_database_conflict($exception)) {
                continue;
            }
            throw $exception;
        } catch (Throwable $exception) {
            if ($started && $db->inTransaction()) {
                $db->rollBack();
            }
            throw $exception;
        }
    }
}

function cc_presence_token_nonce(?callable $generator = null): string
{
    $bytes = $generator === null ? random_bytes(32) : $generator();
    if (!is_string($bytes) || strlen($bytes) < 32) {
        throw new RuntimeException('Presence token generation failed.');
    }
    return bin2hex(substr($bytes, 0, 32));
}

function cc_presence_token(string $sessionId, string $nonce, string $secret): string
{
    if (!preg_match('/^[a-f0-9]{64}$/D', $nonce)) {
        throw new RuntimeException('Presence token nonce is invalid.');
    }
    $bytes = hash_hmac('sha256', $sessionId . "\0" . $nonce, $secret, true);
    return rtrim(strtr(base64_encode($bytes), '+/', '-_'), '=');
}

function cc_presence_token_hash(string $token): string
{
    return hash('sha256', $token);
}

function cc_presence_upsert_device(PDO $db, array $input, string $now): int
{
    $select = $db->prepare(
        'SELECT id FROM cc_devices WHERE installation_id = :installation_id'
        . cc_presence_for_update($db)
    );
    $select->execute(['installation_id' => $input['installation_id']]);
    $deviceId = $select->fetchColumn();
    if ($deviceId === false) {
        try {
            $insert = $db->prepare(
                'INSERT INTO cc_devices '
                . '(installation_id, platform_class, manufacturer, model, android_release, android_sdk_int, '
                . 'first_seen_at, last_seen_at, latest_app_version_name, latest_app_version_code) '
                . 'VALUES (:installation_id, :platform_class, :manufacturer, :model, :android_release, '
                . ':android_sdk_int, :first_seen_at, :last_seen_at, :app_version_name, :app_version_code)'
            );
            $insert->execute([
                'installation_id' => $input['installation_id'],
                'platform_class' => $input['platform_class'],
                'manufacturer' => $input['manufacturer'],
                'model' => $input['model'],
                'android_release' => $input['android_release'],
                'android_sdk_int' => $input['android_sdk_int'],
                'first_seen_at' => $now,
                'last_seen_at' => $now,
                'app_version_name' => $input['app_version_name'],
                'app_version_code' => $input['app_version_code'],
            ]);
            $deviceId = (int) $db->lastInsertId();
        } catch (PDOException $exception) {
            if (!cc_presence_retryable_database_conflict($exception)) {
                throw $exception;
            }
            $select->execute(['installation_id' => $input['installation_id']]);
            $deviceId = $select->fetchColumn();
            if ($deviceId === false) {
                throw $exception;
            }
        }
    }

    $update = $db->prepare(
        'UPDATE cc_devices SET platform_class = :platform_class, manufacturer = :manufacturer, '
        . 'model = :model, android_release = :android_release, android_sdk_int = :android_sdk_int, '
        . 'last_seen_at = :last_seen_at, latest_app_version_name = :app_version_name, '
        . 'latest_app_version_code = :app_version_code WHERE id = :id'
    );
    $update->execute([
        'platform_class' => $input['platform_class'],
        'manufacturer' => $input['manufacturer'],
        'model' => $input['model'],
        'android_release' => $input['android_release'],
        'android_sdk_int' => $input['android_sdk_int'],
        'last_seen_at' => $now,
        'app_version_name' => $input['app_version_name'],
        'app_version_code' => $input['app_version_code'],
        'id' => (int) $deviceId,
    ]);
    return (int) $deviceId;
}

function cc_presence_session_matches(array $row, array $input, int $deviceId, int $resellerId): bool
{
    $expected = [
        'device_id' => $deviceId,
        'reseller_id' => $resellerId,
        'access_code_snapshot' => $input['access_code_snapshot'],
        'iptv_username' => $input['iptv_username'],
        'iptv_password' => $input['iptv_password'],
        'host_snapshot' => $input['host'],
        'authenticated_at_client_ms' => $input['authenticated_at_client_ms'],
        'app_version_name' => $input['app_version_name'],
        'app_version_code' => $input['app_version_code'],
        'platform_class' => $input['platform_class'],
        'device_manufacturer' => $input['manufacturer'],
        'device_model' => $input['model'],
        'android_release' => $input['android_release'],
        'android_sdk_int' => $input['android_sdk_int'],
    ];
    foreach ($expected as $key => $value) {
        if ((string) ($row[$key] ?? '') !== (string) $value) {
            return false;
        }
    }
    return true;
}

function cc_presence_start(
    PDO $controlDb,
    PDO $resellerDb,
    array $payload,
    array $config,
    string $clientIdentifier,
    ?DateTimeImmutable $now = null,
    ?callable $tokenGenerator = null
): array {
    $currentTime = cc_presence_now($now);
    $input = cc_presence_validate_start($payload);
    cc_presence_rate_limit($controlDb, 'start', $clientIdentifier, $config, $currentTime);
    $reseller = cc_presence_validate_reseller($resellerDb, $input);
    $candidateNonce = cc_presence_token_nonce($tokenGenerator);
    $nowString = cc_presence_datetime($currentTime);

    for ($attempt = 0; $attempt < 2; $attempt++) {
        $controlDb->beginTransaction();
        try {
            $deviceId = cc_presence_upsert_device($controlDb, $input, $nowString);
            $select = $controlDb->prepare(
                'SELECT * FROM cc_app_sessions WHERE session_id = :session_id'
                . cc_presence_for_update($controlDb)
            );
            $select->execute(['session_id' => $input['session_id']]);
            $existing = $select->fetch();
            if (is_array($existing)) {
                if (!cc_presence_session_matches($existing, $input, $deviceId, $reseller['reseller_id'])) {
                    throw new CcPresenceException(
                        'SESSION_CONFLICT',
                        409,
                        'The session identifier is already in use.'
                    );
                }
                if (($existing['ended_at'] ?? null) !== null) {
                    throw new CcPresenceException('SESSION_ENDED', 409, 'The Presence session has ended.');
                }
                $nonce = (string) ($existing['presence_token_nonce'] ?? '');
                $token = cc_presence_token($input['session_id'], $nonce, $config['token_secret']);
                $tokenHash = cc_presence_token_hash($token);
                $update = $controlDb->prepare(
                    'UPDATE cc_app_sessions SET presence_token_hash = :token_hash, last_seen_at = :last_seen_at '
                    . 'WHERE id = :id AND ended_at IS NULL'
                );
                $update->execute([
                    'token_hash' => $tokenHash,
                    'last_seen_at' => $nowString,
                    'id' => (int) $existing['id'],
                ]);
                $serverId = (int) $existing['id'];
            } else {
                $nonce = $candidateNonce;
                $token = cc_presence_token($input['session_id'], $nonce, $config['token_secret']);
                $tokenHash = cc_presence_token_hash($token);
                $insert = $controlDb->prepare(
                    'INSERT INTO cc_app_sessions '
                    . '(session_id, device_id, reseller_id, access_code_snapshot, iptv_username, iptv_password, '
                    . 'host_snapshot, authenticated_at_client_ms, app_version_name, app_version_code, '
                    . 'platform_class, device_manufacturer, device_model, android_release, android_sdk_int, '
                    . 'started_at, last_seen_at, presence_token_nonce, presence_token_hash) VALUES '
                    . '(:session_id, :device_id, :reseller_id, :access_code, :iptv_username, :iptv_password, '
                    . ':host, :authenticated_at, :app_version_name, :app_version_code, :platform_class, '
                    . ':manufacturer, :model, :android_release, :android_sdk_int, :started_at, :last_seen_at, '
                    . ':token_nonce, :token_hash)'
                );
                $insert->execute([
                    'session_id' => $input['session_id'],
                    'device_id' => $deviceId,
                    'reseller_id' => $reseller['reseller_id'],
                    'access_code' => $input['access_code_snapshot'],
                    'iptv_username' => $input['iptv_username'],
                    'iptv_password' => $input['iptv_password'],
                    'host' => $input['host'],
                    'authenticated_at' => $input['authenticated_at_client_ms'],
                    'app_version_name' => $input['app_version_name'],
                    'app_version_code' => $input['app_version_code'],
                    'platform_class' => $input['platform_class'],
                    'manufacturer' => $input['manufacturer'],
                    'model' => $input['model'],
                    'android_release' => $input['android_release'],
                    'android_sdk_int' => $input['android_sdk_int'],
                    'started_at' => $nowString,
                    'last_seen_at' => $nowString,
                    'token_nonce' => $nonce,
                    'token_hash' => $tokenHash,
                ]);
                $serverId = (int) $controlDb->lastInsertId();
            }
            $controlDb->commit();
            break;
        } catch (PDOException $exception) {
            if ($controlDb->inTransaction()) {
                $controlDb->rollBack();
            }
            if ($attempt === 0 && cc_presence_retryable_database_conflict($exception)) {
                continue;
            }
            throw $exception;
        } catch (Throwable $exception) {
            if ($controlDb->inTransaction()) {
                $controlDb->rollBack();
            }
            throw $exception;
        }
    }

    return [
        'server_session_id' => $serverId,
        'sessionId' => $input['session_id'],
        'presenceToken' => $token,
        'heartbeatSeconds' => (int) $config['heartbeat_seconds'],
        'onlineTtlSeconds' => (int) $config['online_ttl_seconds'],
        'serverTimeEpochSeconds' => $currentTime->getTimestamp(),
    ];
}

function cc_presence_authorized_session(PDO $db, string $sessionId, string $token): array
{
    if (!preg_match('/^[A-Za-z0-9_-]{43,128}$/D', $token)) {
        throw new CcPresenceException('UNAUTHORIZED', 401, 'The Presence token is invalid.');
    }
    $statement = $db->prepare(
        'SELECT id, session_id, presence_token_hash, ended_at, end_reason FROM cc_app_sessions '
        . 'WHERE session_id = :session_id LIMIT 1'
    );
    $statement->execute(['session_id' => $sessionId]);
    $session = $statement->fetch();
    if (
        !is_array($session)
        || !hash_equals((string) $session['presence_token_hash'], cc_presence_token_hash($token))
    ) {
        throw new CcPresenceException('UNAUTHORIZED', 401, 'The Presence token is invalid.');
    }
    return $session;
}

function cc_presence_heartbeat(
    PDO $db,
    array $payload,
    string $token,
    array $config,
    string $clientIdentifier,
    ?DateTimeImmutable $now = null
): array {
    $currentTime = cc_presence_now($now);
    $input = cc_presence_validate_session_payload($payload, false);
    cc_presence_rate_limit($db, 'heartbeat', $clientIdentifier, $config, $currentTime);
    $session = cc_presence_authorized_session($db, $input['session_id'], $token);
    if (($session['ended_at'] ?? null) !== null) {
        throw new CcPresenceException('SESSION_ENDED', 409, 'The Presence session has ended.');
    }
    $nowString = cc_presence_datetime($currentTime);
    $update = $db->prepare(
        'UPDATE cc_app_sessions SET last_seen_at = :last_seen_at '
        . 'WHERE id = :id AND ended_at IS NULL'
    );
    $update->execute(['last_seen_at' => $nowString, 'id' => (int) $session['id']]);
    if ($update->rowCount() > 1) {
        throw new RuntimeException('Presence heartbeat updated an unexpected row count.');
    }
    if ($update->rowCount() === 0) {
        $state = $db->prepare('SELECT ended_at FROM cc_app_sessions WHERE id = :id LIMIT 1');
        $state->execute(['id' => (int) $session['id']]);
        $endedAt = $state->fetchColumn();
        if ($endedAt !== false && $endedAt !== null) {
            throw new CcPresenceException('SESSION_ENDED', 409, 'The Presence session has ended.');
        }
    }
    return [
        'sessionId' => $input['session_id'],
        'serverTimeEpochSeconds' => $currentTime->getTimestamp(),
        'onlineTtlSeconds' => (int) $config['online_ttl_seconds'],
    ];
}

function cc_presence_end(
    PDO $db,
    array $payload,
    string $token,
    array $config,
    string $clientIdentifier,
    ?DateTimeImmutable $now = null
): array {
    $currentTime = cc_presence_now($now);
    $input = cc_presence_validate_session_payload($payload, true);
    cc_presence_rate_limit($db, 'end', $clientIdentifier, $config, $currentTime);
    $session = cc_presence_authorized_session($db, $input['session_id'], $token);
    $endedAt = $session['ended_at'] ?? null;
    $reason = $session['end_reason'] ?? null;
    if ($endedAt === null) {
        $endedAt = cc_presence_datetime($currentTime);
        $reason = $input['reason'];
        $update = $db->prepare(
            'UPDATE cc_app_sessions SET ended_at = :ended_at, end_reason = :end_reason '
            . 'WHERE id = :id AND ended_at IS NULL'
        );
        $update->execute([
            'ended_at' => $endedAt,
            'end_reason' => $reason,
            'id' => (int) $session['id'],
        ]);
        if ($update->rowCount() > 1) {
            throw new RuntimeException('Presence end updated an unexpected row count.');
        }
        if ($update->rowCount() === 0) {
            $state = $db->prepare(
                'SELECT ended_at, end_reason FROM cc_app_sessions WHERE id = :id LIMIT 1'
            );
            $state->execute(['id' => (int) $session['id']]);
            $current = $state->fetch();
            if (!is_array($current) || ($current['ended_at'] ?? null) === null) {
                throw new RuntimeException('Presence end could not resolve the session state.');
            }
            $endedAt = (string) $current['ended_at'];
            $reason = (string) $current['end_reason'];
        }
    }
    return [
        'sessionId' => $input['session_id'],
        'ended' => true,
        'reason' => (string) $reason,
        'serverTimeEpochSeconds' => $currentTime->getTimestamp(),
    ];
}

function cc_presence_is_online(array $session, DateTimeImmutable $now, int $ttlSeconds): bool
{
    if (($session['ended_at'] ?? null) !== null || !isset($session['last_seen_at'])) {
        return false;
    }
    try {
        $lastSeen = new DateTimeImmutable((string) $session['last_seen_at'], new DateTimeZone('UTC'));
    } catch (Throwable $exception) {
        return false;
    }
    return $lastSeen->getTimestamp() >= cc_presence_now($now)->getTimestamp() - $ttlSeconds;
}

function cc_presence_cleanup(PDO $db, array $config, ?DateTimeImmutable $now = null): array
{
    $currentTime = cc_presence_now($now);
    $batch = (int) $config['cleanup_batch_size'];
    $sessionCutoff = cc_presence_datetime(
        $currentTime->modify('-' . (int) $config['session_retention_days'] . ' days')
    );
    $deviceCutoff = cc_presence_datetime(
        $currentTime->modify('-' . (int) $config['device_retention_days'] . ' days')
    );
    $rateCutoff = cc_presence_datetime($currentTime->modify('-1 day'));

    $sessionSelect = $db->prepare(
        'SELECT id FROM cc_app_sessions WHERE '
        . '(ended_at IS NOT NULL AND ended_at < :ended_cutoff) '
        . 'OR (ended_at IS NULL AND last_seen_at < :stale_cutoff) '
        . 'ORDER BY id ASC LIMIT ' . $batch
    );
    $sessionSelect->execute(['ended_cutoff' => $sessionCutoff, 'stale_cutoff' => $sessionCutoff]);
    $sessionIds = array_map('intval', $sessionSelect->fetchAll(PDO::FETCH_COLUMN));
    $sessions = 0;
    if ($sessionIds !== []) {
        $sessionPlaceholders = implode(',', array_fill(0, count($sessionIds), '?'));
        $sessionDelete = $db->prepare(
            'DELETE FROM cc_app_sessions WHERE id IN (' . $sessionPlaceholders . ') AND '
            . '((ended_at IS NOT NULL AND ended_at < ?) '
            . 'OR (ended_at IS NULL AND last_seen_at < ?))'
        );
        $sessionDelete->execute([...$sessionIds, $sessionCutoff, $sessionCutoff]);
        $sessions = $sessionDelete->rowCount();
    }

    $deviceSelect = $db->prepare(
        'SELECT d.id FROM cc_devices d WHERE d.last_seen_at < :cutoff '
        . 'AND NOT EXISTS (SELECT 1 FROM cc_app_sessions s WHERE s.device_id = d.id) '
        . 'ORDER BY d.id ASC LIMIT ' . $batch
    );
    $deviceSelect->execute(['cutoff' => $deviceCutoff]);
    $deviceIds = array_map('intval', $deviceSelect->fetchAll(PDO::FETCH_COLUMN));
    $devices = 0;
    if ($deviceIds !== []) {
        $devicePlaceholders = implode(',', array_fill(0, count($deviceIds), '?'));
        $deviceDelete = $db->prepare(
            'DELETE FROM cc_devices WHERE id IN (' . $devicePlaceholders . ') '
            . 'AND last_seen_at < ? AND NOT EXISTS '
            . '(SELECT 1 FROM cc_app_sessions s WHERE s.device_id = cc_devices.id)'
        );
        $deviceDelete->execute([...$deviceIds, $deviceCutoff]);
        $devices = $deviceDelete->rowCount();
    }

    $rateSelect = $db->prepare(
        'SELECT scope, client_hash FROM cc_api_rate_limits WHERE window_started_at < :cutoff '
        . 'ORDER BY window_started_at ASC LIMIT ' . $batch
    );
    $rateSelect->execute(['cutoff' => $rateCutoff]);
    $rateDelete = $db->prepare(
        'DELETE FROM cc_api_rate_limits WHERE scope = :scope AND client_hash = :client_hash '
        . 'AND window_started_at < :cutoff'
    );
    $rateLimits = 0;
    while (($rateRow = $rateSelect->fetch()) !== false) {
        if (!is_array($rateRow)) {
            continue;
        }
        $rateDelete->execute([
            'scope' => (string) $rateRow['scope'],
            'client_hash' => (string) $rateRow['client_hash'],
            'cutoff' => $rateCutoff,
        ]);
        $rateLimits += $rateDelete->rowCount();
    }

    return ['sessions' => $sessions, 'devices' => $devices, 'rate_limits' => $rateLimits];
}
