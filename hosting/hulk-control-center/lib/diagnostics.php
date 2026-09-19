<?php

declare(strict_types=1);

require_once __DIR__ . '/presence.php';

function cc_diagnostics_config(?array $root = null): array
{
    $configuration = $root ?? cc_load_config();
    $source = $configuration['diagnostics'] ?? null;
    if (!is_array($source)) {
        throw new RuntimeException('Diagnostics configuration is not installed.');
    }
    $presence = cc_presence_config($configuration);
    $config = [
        'retention_days' => (int) ($source['retention_days'] ?? 0),
        'cleanup_batch_size' => (int) ($source['cleanup_batch_size'] ?? 0),
        'rate_limit_window_seconds' => (int) ($source['rate_limit_window_seconds'] ?? 0),
        'rate_limit_attempts' => (int) ($source['rate_limit_attempts'] ?? 0),
        'rate_limit_secret' => (string) $presence['rate_limit_secret'],
    ];
    if ($config['retention_days'] < 1 || $config['retention_days'] > 30) {
        throw new RuntimeException('Diagnostics retention is invalid.');
    }
    if ($config['cleanup_batch_size'] < 1 || $config['cleanup_batch_size'] > 1000) {
        throw new RuntimeException('Diagnostics cleanup batch is invalid.');
    }
    if ($config['rate_limit_window_seconds'] < 10 || $config['rate_limit_window_seconds'] > 3600) {
        throw new RuntimeException('Diagnostics rate-limit window is invalid.');
    }
    if ($config['rate_limit_attempts'] < 1 || $config['rate_limit_attempts'] > 120) {
        throw new RuntimeException('Diagnostics rate-limit attempts are invalid.');
    }
    return $config;
}

function cc_diagnostic_event_types(): array
{
    return [
        'APP_START_FAILURE',
        'AUTHENTICATION_FAILURE',
        'PLAYBACK_START_FAILURE',
        'PLAYBACK_STALL',
        'STREAM_SOURCE_FAILURE',
        'DOWNLOAD_FAILURE',
        'UPDATE_FAILURE',
        'PRESENCE_FAILURE',
    ];
}

function cc_diagnostic_error_codes(): array
{
    return [
        'NETWORK_TIMEOUT',
        'DNS_FAILURE',
        'CONNECTION_FAILURE',
        'HTTP_ERROR',
        'TLS_FAILURE',
        'AUTH_REJECTED',
        'UNSUPPORTED_MEDIA',
        'DECODER_FAILURE',
        'SOURCE_EXHAUSTED',
        'STALLED',
        'STORAGE_FAILURE',
        'CHECKSUM_FAILURE',
        'UNKNOWN_TYPED_FAILURE',
    ];
}

function cc_diagnostic_allowed_codes_by_type(): array
{
    $network = ['NETWORK_TIMEOUT', 'DNS_FAILURE', 'CONNECTION_FAILURE', 'HTTP_ERROR', 'TLS_FAILURE'];
    return [
        'APP_START_FAILURE' => array_merge($network, ['STORAGE_FAILURE', 'UNKNOWN_TYPED_FAILURE']),
        'AUTHENTICATION_FAILURE' => array_merge($network, ['AUTH_REJECTED', 'UNKNOWN_TYPED_FAILURE']),
        'PLAYBACK_START_FAILURE' => array_merge(
            $network,
            ['UNSUPPORTED_MEDIA', 'DECODER_FAILURE', 'SOURCE_EXHAUSTED', 'UNKNOWN_TYPED_FAILURE']
        ),
        'PLAYBACK_STALL' => array_merge($network, ['STALLED', 'SOURCE_EXHAUSTED', 'UNKNOWN_TYPED_FAILURE']),
        'STREAM_SOURCE_FAILURE' => array_merge($network, ['SOURCE_EXHAUSTED', 'UNKNOWN_TYPED_FAILURE']),
        'DOWNLOAD_FAILURE' => array_merge(
            $network,
            ['STORAGE_FAILURE', 'CHECKSUM_FAILURE', 'UNKNOWN_TYPED_FAILURE']
        ),
        'UPDATE_FAILURE' => array_merge($network, ['STORAGE_FAILURE', 'CHECKSUM_FAILURE', 'UNKNOWN_TYPED_FAILURE']),
        'PRESENCE_FAILURE' => array_merge($network, ['AUTH_REJECTED', 'UNKNOWN_TYPED_FAILURE']),
    ];
}

function cc_diagnostics_occurred_at(int $epochMs, DateTimeImmutable $now, int $retentionDays): DateTimeImmutable
{
    $seconds = intdiv($epochMs, 1000);
    $microseconds = ($epochMs % 1000) * 1000;
    $occurred = DateTimeImmutable::createFromFormat(
        'U.u',
        sprintf('%d.%06d', $seconds, $microseconds),
        new DateTimeZone('UTC')
    );
    if (!$occurred instanceof DateTimeImmutable) {
        throw new CcPresenceException('INVALID_REQUEST', 422, 'The occurrence time is invalid.');
    }
    $utcNow = cc_presence_now($now);
    if (
        $occurred < $utcNow->modify('-' . $retentionDays . ' days')
        || $occurred > $utcNow->modify('+5 minutes')
    ) {
        throw new CcPresenceException('INVALID_REQUEST', 422, 'The occurrence time is outside the accepted window.');
    }
    return $occurred;
}

function cc_diagnostics_validate(array $payload, array $config, DateTimeImmutable $now): array
{
    cc_presence_assert_keys(
        $payload,
        ['contractVersion', 'eventId', 'sessionId', 'eventType', 'errorCode', 'occurredAtEpochMs'],
        'request'
    );
    if (($payload['contractVersion'] ?? null) !== 1) {
        throw new CcPresenceException('UNSUPPORTED_CONTRACT', 422, 'Unsupported Diagnostics contract version.');
    }
    $eventId = strtolower(cc_presence_string($payload['eventId'] ?? null, 'eventId', 64, true));
    $sessionId = strtolower(cc_presence_string($payload['sessionId'] ?? null, 'sessionId', 64, true));
    if (!cc_presence_is_uuid($eventId) || !cc_presence_is_uuid($sessionId)) {
        throw new CcPresenceException('INVALID_REQUEST', 422, 'Event and session identifiers must be UUIDs.');
    }
    $eventType = strtoupper(cc_presence_string($payload['eventType'] ?? null, 'eventType', 32, true));
    $errorCode = strtoupper(cc_presence_string($payload['errorCode'] ?? null, 'errorCode', 32, true));
    if (!in_array($eventType, cc_diagnostic_event_types(), true)) {
        throw new CcPresenceException('UNKNOWN_EVENT_TYPE', 422, 'The diagnostic event type is not supported.');
    }
    if (!in_array($errorCode, cc_diagnostic_error_codes(), true)) {
        throw new CcPresenceException('UNKNOWN_ERROR_CODE', 422, 'The diagnostic error code is not supported.');
    }
    if (!in_array($errorCode, cc_diagnostic_allowed_codes_by_type()[$eventType], true)) {
        throw new CcPresenceException('INVALID_EVENT_PAIR', 422, 'The diagnostic type and code are incompatible.');
    }
    $occurredAtEpochMs = cc_presence_integer(
        $payload['occurredAtEpochMs'] ?? null,
        'occurredAtEpochMs',
        1,
        PHP_INT_MAX
    );
    return [
        'event_id' => $eventId,
        'session_id' => $sessionId,
        'event_type' => $eventType,
        'error_code' => $errorCode,
        'occurred_at' => cc_diagnostics_occurred_at(
            $occurredAtEpochMs,
            $now,
            (int) $config['retention_days']
        ),
    ];
}

function cc_diagnostics_rate_limit(
    PDO $db,
    string $clientIdentifier,
    array $config,
    DateTimeImmutable $now
): void {
    $scope = 'diagnostics';
    $hash = cc_presence_client_hash(
        $scope,
        $clientIdentifier,
        (string) $config['rate_limit_secret']
    );
    $windowSeconds = (int) $config['rate_limit_window_seconds'];
    $maximum = (int) $config['rate_limit_attempts'];
    $nowString = cc_presence_datetime($now);

    for ($attempt = 0; $attempt < 2; $attempt++) {
        $started = !$db->inTransaction();
        if ($started) {
            $db->beginTransaction();
        }
        try {
            $statement = $db->prepare(
                'SELECT window_started_at, attempts FROM cc_api_rate_limits '
                . 'WHERE scope = :scope AND client_hash = :client_hash' . cc_presence_for_update($db)
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
            $attempts = $expired ? 1 : (int) $row['attempts'] + 1;
            $update = $db->prepare(
                'UPDATE cc_api_rate_limits SET window_started_at = :window_started_at, attempts = :attempts '
                . 'WHERE scope = :scope AND client_hash = :client_hash'
            );
            $update->execute([
                'window_started_at' => $expired ? $nowString : (string) $row['window_started_at'],
                'attempts' => min(65535, $attempts),
                'scope' => $scope,
                'client_hash' => $hash,
            ]);
            if ($started) {
                $db->commit();
            }
            if (!$expired && $attempts > $maximum) {
                throw new CcPresenceException('RATE_LIMITED', 429, 'Too many Diagnostics requests.');
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

function cc_diagnostics_session_snapshot(PDO $db, int $sessionId): array
{
    $statement = $db->prepare(
        'SELECT id, device_id, host_snapshot, app_version_name, app_version_code, platform_class, '
        . 'device_manufacturer, device_model, android_release, android_sdk_int, started_at, last_seen_at, ended_at '
        . 'FROM cc_app_sessions WHERE id = :id LIMIT 1'
    );
    $statement->execute(['id' => $sessionId]);
    $snapshot = $statement->fetch();
    if (!is_array($snapshot)) {
        throw new CcPresenceException('UNAUTHORIZED', 401, 'The Presence session is unavailable.');
    }
    cc_reseller_require_domain();
    $host = hulk_normalize_host((string) ($snapshot['host_snapshot'] ?? ''));
    if ($host === null) {
        throw new RuntimeException('The session host snapshot is invalid.');
    }
    $snapshot['host_fingerprint'] = hash('sha256', $host);
    return $snapshot;
}

function cc_diagnostics_assert_session_lifecycle(
    array $snapshot,
    DateTimeImmutable $occurredAt,
    DateTimeImmutable $now,
    int $retentionDays
): void {
    $startedAt = new DateTimeImmutable((string) $snapshot['started_at'], new DateTimeZone('UTC'));
    $lastSeenAt = new DateTimeImmutable((string) $snapshot['last_seen_at'], new DateTimeZone('UTC'));
    $endedAt = ($snapshot['ended_at'] ?? null) === null
        ? null
        : new DateTimeImmutable((string) $snapshot['ended_at'], new DateTimeZone('UTC'));
    $latestSessionFact = $endedAt ?? $lastSeenAt;
    if ($latestSessionFact < $now->modify('-' . $retentionDays . ' days')) {
        throw new CcPresenceException('SESSION_STALE', 409, 'The Presence session is outside Diagnostics retention.');
    }
    if ($occurredAt < $startedAt->modify('-5 minutes')) {
        throw new CcPresenceException('INVALID_OCCURRENCE_TIME', 422, 'The diagnostic predates the Presence session.');
    }
    if ($endedAt !== null && $occurredAt > $endedAt->modify('+5 minutes')) {
        throw new CcPresenceException('INVALID_OCCURRENCE_TIME', 422, 'The diagnostic occurred after the Presence session ended.');
    }
}

function cc_diagnostics_existing(PDO $db, string $eventId): ?array
{
    $statement = $db->prepare(
        'SELECT event_id, session_id, event_type, error_code, occurred_at '
        . 'FROM cc_diagnostic_events WHERE event_id = :event_id LIMIT 1'
    );
    $statement->execute(['event_id' => $eventId]);
    $row = $statement->fetch();
    return is_array($row) ? $row : null;
}

function cc_diagnostics_assert_idempotent(array $existing, array $input, int $serverSessionId): void
{
    if (
        (int) $existing['session_id'] !== $serverSessionId
        || !hash_equals((string) $existing['event_type'], (string) $input['event_type'])
        || !hash_equals((string) $existing['error_code'], (string) $input['error_code'])
        || !hash_equals((string) $existing['occurred_at'], cc_presence_datetime($input['occurred_at']))
    ) {
        throw new CcPresenceException('EVENT_CONFLICT', 409, 'The diagnostic event identifier is already in use.');
    }
}

function cc_diagnostics_ingest(
    PDO $db,
    array $payload,
    string $token,
    array $config,
    string $clientIdentifier,
    ?DateTimeImmutable $now = null
): array {
    $currentTime = cc_presence_now($now);
    $input = cc_diagnostics_validate($payload, $config, $currentTime);
    cc_diagnostics_rate_limit($db, $clientIdentifier, $config, $currentTime);
    $authorized = cc_presence_authorized_session($db, $input['session_id'], $token);
    $serverSessionId = (int) $authorized['id'];
    $snapshot = cc_diagnostics_session_snapshot($db, $serverSessionId);
    cc_diagnostics_assert_session_lifecycle(
        $snapshot,
        $input['occurred_at'],
        $currentTime,
        (int) $config['retention_days']
    );
    $existing = cc_diagnostics_existing($db, $input['event_id']);
    if (is_array($existing)) {
        cc_diagnostics_assert_idempotent($existing, $input, $serverSessionId);
        return [
            'eventId' => $input['event_id'],
            'accepted' => true,
            'serverTimeEpochSeconds' => $currentTime->getTimestamp(),
        ];
    }
    try {
        $statement = $db->prepare(
            'INSERT INTO cc_diagnostic_events '
            . '(event_id, session_id, device_id, host_fingerprint, event_type, error_code, app_version_name, '
            . 'app_version_code, platform_class, device_manufacturer, device_model, android_release, android_sdk_int, '
            . 'occurred_at, received_at) VALUES '
            . '(:event_id, :session_id, :device_id, :host_fingerprint, :event_type, :error_code, :app_version_name, '
            . ':app_version_code, :platform_class, :device_manufacturer, :device_model, :android_release, '
            . ':android_sdk_int, :occurred_at, :received_at)'
        );
        $statement->execute([
            'event_id' => $input['event_id'],
            'session_id' => $serverSessionId,
            'device_id' => (int) $snapshot['device_id'],
            'host_fingerprint' => (string) $snapshot['host_fingerprint'],
            'event_type' => $input['event_type'],
            'error_code' => $input['error_code'],
            'app_version_name' => (string) $snapshot['app_version_name'],
            'app_version_code' => (int) $snapshot['app_version_code'],
            'platform_class' => (string) $snapshot['platform_class'],
            'device_manufacturer' => (string) $snapshot['device_manufacturer'],
            'device_model' => (string) $snapshot['device_model'],
            'android_release' => (string) $snapshot['android_release'],
            'android_sdk_int' => (int) $snapshot['android_sdk_int'],
            'occurred_at' => cc_presence_datetime($input['occurred_at']),
            'received_at' => cc_presence_datetime($currentTime),
        ]);
    } catch (PDOException $exception) {
        if (!cc_presence_retryable_database_conflict($exception)) {
            throw $exception;
        }
        $existing = cc_diagnostics_existing($db, $input['event_id']);
        if (!is_array($existing)) {
            throw $exception;
        }
        cc_diagnostics_assert_idempotent($existing, $input, $serverSessionId);
    }
    return [
        'eventId' => $input['event_id'],
        'accepted' => true,
        'serverTimeEpochSeconds' => $currentTime->getTimestamp(),
    ];
}

function cc_diagnostics_cleanup(PDO $db, array $config, ?DateTimeImmutable $now = null): int
{
    $cutoff = cc_presence_now($now)->modify('-' . (int) $config['retention_days'] . ' days');
    $batch = (int) $config['cleanup_batch_size'];
    $expired = $db->prepare(
        'SELECT /*+ MAX_EXECUTION_TIME(1000) */ id FROM cc_diagnostic_events WHERE received_at < :cutoff '
        . 'ORDER BY id ASC LIMIT ' . $batch
    );
    $expired->execute(['cutoff' => cc_presence_datetime($cutoff)]);
    $ids = array_values(array_map('intval', $expired->fetchAll(PDO::FETCH_COLUMN)));
    if ($ids === []) {
        return 0;
    }
    $delete = $db->prepare(
        'DELETE FROM cc_diagnostic_events WHERE id IN (' . implode(',', $ids) . ')'
    );
    $delete->execute();
    return $delete->rowCount();
}
