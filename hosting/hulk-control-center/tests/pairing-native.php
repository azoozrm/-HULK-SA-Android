<?php

declare(strict_types=1);

/**
 * Native behavioral regressions against an isolated disposable MySQL/MariaDB test
 * database. Selected only when HULK_PAIRING_TEST_DSN + HULK_PAIRING_TEST_USER +
 * HULK_PAIRING_TEST_PASS + HULK_PAIRING_TEST_CONFIG (a private config file whose
 * databases point at that disposable database) are set. Synthetic values only.
 *
 * Concurrency uses a coordinated barrier file so workers start together; worker
 * exits, isolation values and persisted counts are recorded. No fixed sleeps are
 * used as synchronization.
 */

$nativeDsn = (string) getenv('HULK_PAIRING_TEST_DSN');
$nativeUser = (string) getenv('HULK_PAIRING_TEST_USER');
$nativePass = (string) getenv('HULK_PAIRING_TEST_PASS');
$nativeConfig = (string) getenv('HULK_PAIRING_TEST_CONFIG');

$nativePdo = static function () use ($nativeDsn, $nativeUser, $nativePass): PDO {
    return new PDO($nativeDsn, $nativeUser, $nativePass, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
};

$nativeDb = $nativePdo();
$nativeDb->exec('CREATE TABLE IF NOT EXISTS resellers ('
    . 'reseller_id INT UNSIGNED NOT NULL PRIMARY KEY, '
    . 'access_code VARCHAR(64) NOT NULL, '
    . 'access_code_hash CHAR(64) NOT NULL, '
    . 'host VARCHAR(2048) NOT NULL, '
    . 'status VARCHAR(32) NOT NULL'
    . ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci');
$nativeDb->exec('CREATE TABLE IF NOT EXISTS cc_api_rate_limits ('
    . 'scope VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, '
    . 'client_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, '
    . 'window_started_at DATETIME(6) NOT NULL, '
    . 'attempts SMALLINT UNSIGNED NOT NULL DEFAULT 0, '
    . 'PRIMARY KEY (scope, client_hash), '
    . 'KEY idx_cc_api_rate_limits_window (window_started_at)'
    . ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci');
$nativeDb->exec('SET FOREIGN_KEY_CHECKS=0');
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$nativeDb->exec('TRUNCATE TABLE resellers');
$nativeDb->exec('DELETE FROM cc_api_rate_limits');
$nativeDb->exec('SET FOREIGN_KEY_CHECKS=1');
cc_test(true, 'native: disposable test schema prepared (MariaDB/MySQL)');

// Deadline plan: MariaDB supported (generic statement bound), MySQL fails closed.
$mariadbPlan = cc_pairing_deadline_plan('mysql', '10.11.14-MariaDB-ubuntu');
$mysqlPlan = cc_pairing_deadline_plan('mysql', '8.0.36');
cc_test(
    $mariadbPlan['supported'] === true && $mariadbPlan['dml_bounded'] === true
    && in_array('SET SESSION max_statement_time = 5', $mariadbPlan['statements'], true),
    'native: MariaDB deadline plan advertises bounded reads and DML'
);
cc_test(
    $mysqlPlan['supported'] === false && $mysqlPlan['dml_bounded'] === false,
    'native: MySQL deadline plan fails closed because max_execution_time bounds SELECT only'
);

$nativeKey = base64_encode(random_bytes(32));
$nativeClaim = base64_encode(random_bytes(32));
$nativeRate = base64_encode(random_bytes(32));
$nativeBlock = [
    'enabled' => true,
    'ttl_seconds' => 600,
    'retention_seconds' => 86400,
    'max_retained_rows' => 5000,
    'max_deliveries' => 20,
    'poll_interval_seconds' => 3,
    'claim_secret' => $nativeClaim,
    'rate_limit_secret' => $nativeRate,
    'envelope_keys' => ['v1' => $nativeKey],
    'active_envelope_key_id' => 'v1',
];
$nativeSettings = cc_pairing_settings($nativeBlock);
$nativeSecret = static fn (): string => rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
$nativeInput = static function (string $secret): array {
    return [
        'contractVersion' => 1,
        'clientRequestId' => cc_pairing_uuid4(),
        'clientSecret' => $secret,
        'installationId' => '11111111-2222-4333-8444-555555555555',
        'device' => ['platform' => 'TV', 'manufacturer' => 'Example', 'model' => 'ExampleTV', 'appVersion' => '0.0.0'],
        'requestedAtEpochMs' => 0,
    ];
};
$nativeCode = 'HULK-ABCD-EFGH-JKMN-PQRS';
$nativeProbeOk = static fn (string $h, string $u, string $p, array $c): array => ['result' => 'OK', 'http_status' => 200, 'reason' => 'ACTIVE'];
$nativeBaseClock = static fn (): DateTimeImmutable => new DateTimeImmutable('2026-10-14T00:00:00Z', new DateTimeZone('UTC'));
$nativeSeedReseller = static function (PDO $db, array $overrides = []) use ($nativeCode): void {
    $row = array_replace([
        'reseller_id' => 7,
        'access_code' => $nativeCode,
        'access_code_hash' => hulk_access_code_hash($nativeCode),
        'host' => 'http://reseller.test:8080',
        'status' => 'active',
    ], $overrides);
    $db->exec('DELETE FROM resellers');
    $statement = $db->prepare(
        'INSERT INTO resellers (reseller_id, access_code, access_code_hash, host, status) '
        . 'VALUES (:reseller_id, :access_code, :access_code_hash, :host, :status)'
    );
    $statement->execute($row);
};

/**
 * Runs worker commands behind a strict readiness gate: every worker must report its
 * prepared connection/bounds/isolation record via the ready directory before the
 * parent opens the gate; readiness is bounded (no sleep-based synchronization) and a
 * missing/invalid readiness record fails the case. Per-worker ready/result/exit
 * records are retained.
 *
 * @return array{workers: list<array{id: int, ready: ?array, ready_error: ?string, stdout: string, stderr: string, exit: int}>, all_ready: bool}
 */
$nativeRunCoordinated = static function (array $commands, array $baseEnv): array {
    $gate = sys_get_temp_dir() . '/hulk-pairing-gate-' . bin2hex(random_bytes(6));
    $readyDir = sys_get_temp_dir() . '/hulk-pairing-ready-' . bin2hex(random_bytes(6));
    @mkdir($readyDir, 0700, true);
    $processes = [];
    foreach ($commands as $workerId => $command) {
        $pipes = [];
        $environment = $baseEnv + [
            'HULK_PAIRING_BARRIER' => $gate,
            'HULK_PAIRING_READY_DIR' => $readyDir,
            'HULK_PAIRING_WORKER_ID' => (string) $workerId,
        ];
        $process = proc_open(
            $command,
            [1 => ['pipe', 'w'], 2 => ['pipe', 'w']],
            $pipes,
            null,
            $environment
        );
        stream_set_blocking($pipes[1], false);
        stream_set_blocking($pipes[2], false);
        $processes[$workerId] = ['process' => $process, 'pipes' => $pipes, 'stdout' => '', 'stderr' => ''];
    }

    // Bounded readiness wait: the parent opens the gate only after every expected
    // worker has written a valid ready record.
    $expected = count($commands);
    $readyRecords = [];
    $readyErrors = [];
    $readyDeadline = microtime(true) + 15.0;
    while (count($readyRecords) + count($readyErrors) < $expected && microtime(true) < $readyDeadline) {
        for ($workerId = 0; $workerId < $expected; $workerId++) {
            if (isset($readyRecords[$workerId]) || isset($readyErrors[$workerId])) {
                continue;
            }
            $readyFile = $readyDir . '/worker-' . $workerId . '.ready';
            if (!is_file($readyFile)) {
                continue;
            }
            $decoded = json_decode((string) file_get_contents($readyFile), true);
            if (
                !is_array($decoded)
                || (string) ($decoded['worker'] ?? '') !== (string) $workerId
                || !in_array((string) ($decoded['isolation'] ?? ''), ['READ-COMMITTED', 'REPEATABLE-READ', 'SERIALIZABLE'], true)
            ) {
                $readyErrors[$workerId] = 'invalid_ready_record';
                continue;
            }
            $readyRecords[$workerId] = $decoded;
        }
        usleep(2000);
    }
    for ($workerId = 0; $workerId < $expected; $workerId++) {
        if (!isset($readyRecords[$workerId]) && !isset($readyErrors[$workerId])) {
            $readyErrors[$workerId] = 'ready_timeout';
        }
    }
    $allReady = count($readyRecords) === $expected;
    if ($allReady) {
        touch($gate);
    } else {
        foreach ($processes as $entry) {
            if (is_resource($entry['process'])) {
                proc_terminate($entry['process'], 9);
            }
        }
    }

    // Collect outputs/exits with a bounded wait.
    $collectDeadline = microtime(true) + 60.0;
    $running = true;
    while ($running && microtime(true) < $collectDeadline) {
        $running = false;
        foreach ($processes as $workerId => $entry) {
            $processes[$workerId]['stdout'] .= (string) stream_get_contents($entry['pipes'][1]);
            $processes[$workerId]['stderr'] .= (string) stream_get_contents($entry['pipes'][2]);
            $status = proc_get_status($entry['process']);
            if ($status['running']) {
                $running = true;
            } else {
                $processes[$workerId]['exit'] = (int) ($status['exitcode'] ?? -1);
            }
        }
        if ($running) {
            usleep(10000);
        }
    }

    $workers = [];
    foreach ($processes as $workerId => $entry) {
        $stdout = $entry['stdout'] . (string) stream_get_contents($entry['pipes'][1]);
        $stderr = $entry['stderr'] . (string) stream_get_contents($entry['pipes'][2]);
        fclose($entry['pipes'][1]);
        fclose($entry['pipes'][2]);
        if (!is_resource($entry['process'])) {
            continue;
        }
        $closeExit = proc_close($entry['process']);
        $exit = $entry['exit'] ?? $closeExit;
        $workers[] = [
            'id' => $workerId,
            'ready' => $readyRecords[$workerId] ?? null,
            'ready_error' => $readyErrors[$workerId] ?? null,
            'stdout' => trim($stdout),
            'stderr' => trim($stderr),
            'exit' => $exit,
        ];
    }
    @unlink($gate);
    foreach (glob($readyDir . '/*') ?: [] as $file) {
        @unlink($file);
    }
    @rmdir($readyDir);
    return ['workers' => $workers, 'all_ready' => $allReady];
};

$nativeTraceFile = (string) getenv('HULK_PAIRING_TRACE_FILE');
$nativeTrace = static function (string $case, string $result, ?float $startedAt = null, array $extra = []) use ($nativeTraceFile): void {
    if ($nativeTraceFile === '') {
        return;
    }
    $entry = [
        'case' => $case,
        'result' => $result,
        'utc' => gmdate('c'),
        'elapsed_ms' => $startedAt === null ? null : (int) round((microtime(true) - $startedAt) * 1000),
    ] + $extra;
    file_put_contents($nativeTraceFile, json_encode($entry, JSON_UNESCAPED_SLASHES) . "\n", FILE_APPEND);
};
$nativeToolPath = dirname(__DIR__) . '/tools/pairing_cleanup.php';
$nativeRunTool = static function (array $arguments, array $environment) use ($nativeToolPath): array {
    $pipes = [];
    $started = microtime(true);
    $process = proc_open(
        array_merge([PHP_BINARY, $nativeToolPath], $arguments),
        [1 => ['pipe', 'w'], 2 => ['pipe', 'w']],
        $pipes,
        null,
        $environment + ['PHPRC' => (string) php_ini_loaded_file()]
    );
    $stdout = stream_get_contents($pipes[1]);
    $stderr = stream_get_contents($pipes[2]);
    fclose($pipes[1]);
    fclose($pipes[2]);
    $exit = is_resource($process) ? proc_close($process) : -1;
    return [
        'exit' => $exit,
        'stdout' => is_string($stdout) ? trim($stdout) : '',
        'stderr' => is_string($stderr) ? trim($stderr) : '',
        'elapsed' => microtime(true) - $started,
    ];
};

// create -> persisted hash -> replay identity -> wrong proof
$nativeSecretA = $nativeSecret();
$nativeCreateInput = $nativeInput($nativeSecretA);
$nativeCreated = cc_pairing_create($nativeDb, $nativeCreateInput, $nativeSettings);
cc_test($nativeCreated['status'] === 201, 'native: create persists a request');
$nativeRow = cc_pairing_load_by_request_id($nativeDb, $nativeCreated['body']['requestId']);
cc_test(
    is_array($nativeRow)
    && hash_equals((string) $nativeRow['claim_token_hash'], cc_pairing_token_hash($nativeCreated['body']['claimToken'])),
    'native: stored token hash derives from the secret-proven response token'
);
$nativeReplay = cc_pairing_create($nativeDb, $nativeCreateInput, $nativeSettings);
cc_test(
    $nativeReplay['status'] === 200 && $nativeReplay['body']['claimToken'] === $nativeCreated['body']['claimToken'],
    'native: idempotent replay returns the identical token'
);
$nativeWrongSecret = $nativeInput($nativeSecret());
$nativeWrongSecret['clientRequestId'] = $nativeCreateInput['clientRequestId'];
$nativeMismatch = false;
try {
    cc_pairing_create($nativeDb, $nativeWrongSecret, $nativeSettings);
} catch (CcPairingException $exception) {
    $nativeMismatch = $exception->apiCode === 'IDEMPOTENCY_MISMATCH';
}
cc_test($nativeMismatch, 'native: wrong client secret is rejected');

// approve -> claim -> lost response -> ack
$nativeSeedReseller($nativeDb);
$nativeRequestId = $nativeCreated['body']['requestId'];
$nativeToken = $nativeCreated['body']['claimToken'];
$nativeApproval = cc_pairing_approve($nativeDb, $nativeRequestId, $nativeCode, 'user', 'pass', $nativeSettings, $nativeProbeOk, $nativeBaseClock, $nativeDb);
cc_test($nativeApproval['hostLabel'] === 'reseller.test:8080', 'native: approval returns the PHP host label');
$nativeClaimOne = cc_pairing_claim($nativeDb, $nativeRequestId, $nativeToken, $nativeSettings, $nativeBaseClock);
cc_test(
    $nativeClaimOne['state'] === 'APPROVED'
    && $nativeClaimOne['credentials'] === ['accessCode' => $nativeCode, 'username' => 'user', 'password' => 'pass'],
    'native: claim delivers credentials from the encrypted envelope'
);
$nativeClaimTwo = cc_pairing_claim($nativeDb, $nativeRequestId, $nativeToken, $nativeSettings, $nativeBaseClock);
cc_test($nativeClaimTwo['deliveryCount'] === 2 && $nativeClaimTwo['credentials'] === $nativeClaimOne['credentials'], 'native: lost-response claim retries identically');
cc_test(cc_pairing_ack($nativeDb, $nativeRequestId, $nativeToken, $nativeBaseClock)['state'] === 'ACKED', 'native: ack terminates delivery');

// claim THEN cancel on the SAME request
$nativeCancelSecret = $nativeSecret();
$nativeCancelCreated = cc_pairing_create($nativeDb, $nativeInput($nativeCancelSecret), $nativeSettings);
$nativeCancelId = $nativeCancelCreated['body']['requestId'];
$nativeCancelToken = $nativeCancelCreated['body']['claimToken'];
cc_pairing_approve($nativeDb, $nativeCancelId, $nativeCode, 'user', 'pass', $nativeSettings, $nativeProbeOk, $nativeBaseClock, $nativeDb);
$nativeClaimBeforeCancel = cc_pairing_claim($nativeDb, $nativeCancelId, $nativeCancelToken, $nativeSettings, $nativeBaseClock);
cc_test($nativeClaimBeforeCancel['state'] === 'APPROVED', 'native: claim succeeds before a same-request cancel');
cc_test(cc_pairing_cancel($nativeDb, $nativeCancelId, $nativeCancelToken)['state'] === 'CANCELLED', 'native: cancel then closes the claimed request');
cc_test(cc_pairing_claim($nativeDb, $nativeCancelId, $nativeCancelToken, $nativeSettings, $nativeBaseClock)['state'] === 'CANCELLED', 'native: claim after cancel returns terminal state');

// cancel then claim/approve ordering
$nativeLateSecret = $nativeSecret();
$nativeLateCreated = cc_pairing_create($nativeDb, $nativeInput($nativeLateSecret), $nativeSettings);
$nativeLateId = $nativeLateCreated['body']['requestId'];
$nativeLateToken = $nativeLateCreated['body']['claimToken'];
cc_pairing_cancel($nativeDb, $nativeLateId, $nativeLateToken);
cc_test(cc_pairing_claim($nativeDb, $nativeLateId, $nativeLateToken, $nativeSettings, $nativeBaseClock)['state'] === 'CANCELLED', 'native: claim after cancellation returns CANCELLED');
$nativeLateApprove = false;
try {
    cc_pairing_approve($nativeDb, $nativeLateId, $nativeCode, 'user', 'pass', $nativeSettings, $nativeProbeOk, $nativeBaseClock, $nativeDb);
} catch (CcPairingException $exception) {
    $nativeLateApprove = $exception->apiCode === 'PAIRING_STATE_CONFLICT';
}
cc_test($nativeLateApprove, 'native: approve after cancellation is a truthful conflict');

// deny after logical expiry (no prior cleanup)
$nativeDenyCreated = cc_pairing_create($nativeDb, $nativeInput($nativeSecret()), $nativeSettings, $nativeBaseClock());
$nativeDenyResult = cc_pairing_deny(
    $nativeDb,
    $nativeDenyCreated['body']['requestId'],
    static fn (): DateTimeImmutable => new DateTimeImmutable('2026-10-14T00:11:00Z', new DateTimeZone('UTC')),
);
$nativeDenyRowAfter = cc_pairing_load_by_request_id($nativeDb, $nativeDenyCreated['body']['requestId']);
cc_test(
    $nativeDenyResult === ['denied' => false, 'state' => 'EXPIRED']
    && (string) $nativeDenyRowAfter['state'] === 'EXPIRED'
    && $nativeDenyRowAfter['envelope_blob'] === null,
    'native: deny after logical expiry reports EXPIRED and stores no envelope'
);

// advancing-clock expiry at commit after provider IO
$nativeAdvance = 0;
$nativeAdvancingClock = static function () use (&$nativeAdvance): DateTimeImmutable {
    return (new DateTimeImmutable('2026-10-14T00:00:00Z', new DateTimeZone('UTC')))->modify('+' . $nativeAdvance . ' seconds');
};
$nativeSlowProbe = static function (string $h, string $u, string $p, array $c) use (&$nativeAdvance): array {
    $nativeAdvance = 700;
    return ['result' => 'OK', 'http_status' => 200, 'reason' => 'ACTIVE'];
};
$nativeExpireCreated = cc_pairing_create($nativeDb, $nativeInput($nativeSecret()), $nativeSettings, $nativeBaseClock());
$nativeExpired = false;
try {
    cc_pairing_approve($nativeDb, $nativeExpireCreated['body']['requestId'], $nativeCode, 'user', 'pass', $nativeSettings, $nativeSlowProbe, $nativeAdvancingClock, $nativeDb);
} catch (CcPairingException $exception) {
    $nativeExpired = $exception->apiCode === 'PAIRING_EXPIRED';
}
$nativeExpireAfter = cc_pairing_load_by_request_id($nativeDb, $nativeExpireCreated['body']['requestId']);
cc_test($nativeExpired && $nativeExpireAfter['envelope_blob'] === null, 'native: commit re-checks expiry after provider IO');

// disable DURING provider IO: the commit rejects and stores no envelope
$nativeDisabledConfig = sys_get_temp_dir() . '/hulk-pairing-disabled-' . bin2hex(random_bytes(6)) . '.php';
$nativeConfigSource = (string) file_get_contents($nativeConfig);
file_put_contents($nativeDisabledConfig, str_replace("'enabled' => true", "'enabled' => false", $nativeConfigSource));
chmod($nativeDisabledConfig, 0600);
$nativePriorConfigEnv = getenv('HULK_CONTROL_CENTER_CONFIG');
$nativeDisableCreated = cc_pairing_create($nativeDb, $nativeInput($nativeSecret()), $nativeSettings, $nativeBaseClock());
$nativeSeedReseller($nativeDb);
$nativeDisablingProbe = static function (string $h, string $u, string $p, array $c) use ($nativeDisabledConfig): array {
    putenv('HULK_CONTROL_CENTER_CONFIG=' . $nativeDisabledConfig);
    return ['result' => 'OK', 'http_status' => 200, 'reason' => 'ACTIVE'];
};
$nativeDisabledAtCommit = false;
try {
    cc_pairing_approve($nativeDb, $nativeDisableCreated['body']['requestId'], $nativeCode, 'user', 'pass', $nativeSettings, $nativeDisablingProbe, $nativeBaseClock, $nativeDb);
} catch (CcPairingException $exception) {
    $nativeDisabledAtCommit = $exception->apiCode === 'PAIRING_DISABLED';
}
if ($nativePriorConfigEnv !== false && $nativePriorConfigEnv !== '') {
    putenv('HULK_CONTROL_CENTER_CONFIG=' . $nativePriorConfigEnv);
} else {
    putenv('HULK_CONTROL_CENTER_CONFIG');
}
$nativeDisableRow = cc_pairing_load_by_request_id($nativeDb, $nativeDisableCreated['body']['requestId']);
cc_test(
    $nativeDisabledAtCommit
    && (string) $nativeDisableRow['state'] === 'PENDING'
    && $nativeDisableRow['envelope_blob'] === null,
    'native: disable during provider IO rejects the commit and stores no envelope'
);
unlink($nativeDisabledConfig);

// rotation rechecks at commit
$nativeRotateCreated = cc_pairing_create($nativeDb, $nativeInput($nativeSecret()), $nativeSettings, $nativeBaseClock());
$nativeSeedReseller($nativeDb);
$nativeRotateProbe = static function (string $h, string $u, string $p, array $c) use ($nativeDb): array {
    $nativeDb->exec("UPDATE resellers SET access_code = 'HULK-AAAA-BBBB-CCCC-DDDD', "
        . "access_code_hash = '" . hulk_access_code_hash('HULK-AAAA-BBBB-CCCC-DDDD') . "'");
    return ['result' => 'OK', 'http_status' => 200, 'reason' => 'ACTIVE'];
};
$nativeRotated = false;
try {
    cc_pairing_approve($nativeDb, $nativeRotateCreated['body']['requestId'], $nativeCode, 'user', 'pass', $nativeSettings, $nativeRotateProbe, $nativeBaseClock, $nativeDb);
} catch (CcPairingException $exception) {
    $nativeRotated = $exception->apiCode === 'INVALID_ACCESS_CODE';
}
cc_test($nativeRotated, 'native: access-code rotation at commit is rejected');

// blocked admission lock: bounded by innodb_lock_wait_timeout, then rollback
$nativeSeedReseller($nativeDb);
$nativeLocker = $nativePdo();
$nativeLocker->beginTransaction();
$nativeLocker->query('SELECT lock_id FROM cc_pairing_admission WHERE lock_id = 1 FOR UPDATE')->fetch();
$nativeBoundedConn = $nativePdo();
$nativePairingInfo = cc_pairing_apply_database_deadlines($nativeBoundedConn);
cc_test(
    $nativePairingInfo['flavor'] === 'mariadb' && $nativePairingInfo['dml_bounded'] === true,
    'native: pairing connection applies supported MariaDB DML bounds'
);
$nativeStart = microtime(true);
$nativeBlocked = false;
try {
    cc_pairing_create($nativeBoundedConn, $nativeInput($nativeSecret()), $nativeSettings);
} catch (PDOException $exception) {
    $nativeBlocked = true;
}
$nativeElapsed = microtime(true) - $nativeStart;
$nativeLocker->rollBack();
cc_test($nativeBlocked && $nativeElapsed >= 2.0 && $nativeElapsed <= 8.0, 'native: blocked admission lock aborts within the 3s lock-wait bound');
$nativeUnblocked = cc_pairing_create($nativePdo(), $nativeInput($nativeSecret()), $nativeSettings);
cc_test($nativeUnblocked['status'] === 201, 'native: create succeeds after the lock is released');

// blocked DML on a locked request row also aborts within the lock-wait bound
$nativeBlockedDmlSecret = $nativeSecret();
$nativeBlockedDmlCreated = cc_pairing_create($nativeDb, $nativeInput($nativeBlockedDmlSecret), $nativeSettings, $nativeBaseClock());
$nativeBlockedDmlId = $nativeBlockedDmlCreated['body']['requestId'];
$nativeBlockedDmlToken = $nativeBlockedDmlCreated['body']['claimToken'];
$nativeSeedReseller($nativeDb);
cc_pairing_approve($nativeDb, $nativeBlockedDmlId, $nativeCode, 'user', 'pass', $nativeSettings, $nativeProbeOk, $nativeBaseClock, $nativeDb);
$nativeRowLocker = $nativePdo();
$nativeRowLocker->beginTransaction();
$nativeRowLocker->query("SELECT pairing_id FROM cc_pairing_requests WHERE request_id = '" . $nativeBlockedDmlId . "' FOR UPDATE")->fetch();
$nativeDmlStart = microtime(true);
$nativeDmlBlocked = false;
try {
    cc_pairing_claim($nativeBoundedConn, $nativeBlockedDmlId, $nativeBlockedDmlToken, $nativeSettings, $nativeBaseClock);
} catch (PDOException $exception) {
    $nativeDmlBlocked = true;
}
$nativeDmlElapsed = microtime(true) - $nativeDmlStart;
$nativeRowLocker->rollBack();
cc_test($nativeDmlBlocked && $nativeDmlElapsed <= 8.0, 'native: blocked DML claim aborts within the lock-wait bound');

// statement deadline: reads and DML are aborted by max_statement_time
$nativeStatementStart = microtime(true);
$nativeStatementAborted = false;
try {
    $nativeBoundedConn->query('SELECT SLEEP(30)');
} catch (PDOException $exception) {
    $nativeStatementAborted = true;
}
$nativeStatementElapsed = microtime(true) - $nativeStatementStart;
cc_test($nativeStatementAborted && $nativeStatementElapsed <= 10.0, 'native: statement deadline aborts a long SELECT');

$nativeDmlStatementStart = microtime(true);
$nativeDmlStatementAborted = false;
try {
    $nativeBoundedConn->exec('UPDATE cc_pairing_requests SET created_at = created_at WHERE pairing_id = 1 AND SLEEP(30)');
} catch (PDOException $exception) {
    $nativeDmlStatementAborted = true;
}
$nativeDmlStatementElapsed = microtime(true) - $nativeDmlStatementStart;
cc_test(
    $nativeDmlStatementAborted && $nativeDmlStatementElapsed <= 10.0,
    'native: statement deadline aborts a long DML statement (max_statement_time)'
);

// coordinated concurrency: absent key, existing key, over-budget contender
$nativeWorker = __DIR__ . '/pairing-native-rate-worker.php';
$nativeWorkerEnv = [
    'HULK_PAIRING_TEST_CONFIG' => $nativeConfig,
    'HULK_PAIRING_RATE_SCOPE' => 'pairing_ack_req',
    'HULK_PAIRING_RATE_SECRET' => $nativeRate,
    'PHPRC' => (string) php_ini_loaded_file(),
];
$nativeRateHash = static fn (string $identifier): string => hash_hmac(
    'sha256',
    'pairing_ack_req' . "\x00" . $identifier,
    base64_decode($nativeRate)
);
$nativeAttemptsFor = static function (string $hash) use ($nativeDb): int {
    $statement = $nativeDb->prepare(
        "SELECT attempts FROM cc_api_rate_limits WHERE scope = 'pairing_ack_req' AND client_hash = :hash"
    );
    $statement->execute(['hash' => $hash]);
    $value = $statement->fetchColumn();
    return $value === false ? 0 : (int) $value;
};

$nativeDb->exec("DELETE FROM cc_api_rate_limits WHERE scope = 'pairing_ack_req'");
$absentKey = 'native-absent-key';
$absentRun = $nativeRunCoordinated(
    array_fill(0, 10, [PHP_BINARY, $nativeWorker]),
    $nativeWorkerEnv + ['HULK_PAIRING_RATE_KEY' => $absentKey, 'HULK_PAIRING_RATE_LIMIT' => '1000']
);
$absentResults = $absentRun['workers'];
$absentExits = array_map(static fn (array $r): int => $r['exit'], $absentResults);
$absentReadyValid = count(array_filter(
    $absentResults,
    static fn (array $r): bool => is_array($r['ready'])
        && $r['ready_error'] === null
        && in_array((string) ($r['ready']['isolation'] ?? ''), ['READ-COMMITTED', 'REPEATABLE-READ', 'SERIALIZABLE'], true)
)) === 10;
cc_test(
    $absentRun['all_ready'] === true && $absentReadyValid,
    'native-r06: all 10 absent-key workers reported a valid ready record before the gate opened'
);
$absentIsolation = [];
foreach ($absentResults as $result) {
    $decoded = json_decode($result['stdout'], true);
    if (is_array($decoded)) {
        $absentIsolation[] = (string) ($decoded['isolation'] ?? 'unknown');
    }
}
cc_test(
    $absentExits === array_fill(0, 10, 0),
    'native: 10 coordinated workers on an ABSENT key all succeed (exits recorded: ' . implode(',', $absentExits) . ')'
);
cc_test(
    $nativeAttemptsFor($nativeRateHash($absentKey)) === 10,
    'native: absent-key first row allocated atomically; attempts=10'
);
$isolationValues = array_values(array_unique($absentIsolation));
cc_test(
    count($absentIsolation) === 10
    && count($isolationValues) === 1
    && in_array($isolationValues[0], ['READ-COMMITTED', 'REPEATABLE-READ', 'SERIALIZABLE'], true),
    'native: every worker reported a valid isolation value (' . implode(',', $isolationValues) . ')'
);
$nativeTrace('rate-absent-key-10-workers', 'pass', null, [
    'all_ready' => $absentRun['all_ready'],
    'exits' => $absentExits,
    'attempts' => $nativeAttemptsFor($nativeRateHash($absentKey)),
    'isolation' => $isolationValues,
    'workers' => array_map(
        static fn (array $r): array => [
            'id' => $r['id'],
            'ready_isolation' => $r['ready']['isolation'] ?? null,
            'exit' => $r['exit'],
            'stdout' => $r['stdout'],
        ],
        $absentResults
    ),
]);

$existingKey = 'native-existing-key';
$existingHash = $nativeRateHash($existingKey);
$nativeDb->exec(
    "INSERT INTO cc_api_rate_limits (scope, client_hash, window_started_at, attempts) VALUES ('pairing_ack_req', '$existingHash', UTC_TIMESTAMP(), 1) "
    . 'ON DUPLICATE KEY UPDATE attempts = 1, window_started_at = UTC_TIMESTAMP()'
);
$existingRun = $nativeRunCoordinated(
    array_fill(0, 9, [PHP_BINARY, $nativeWorker]),
    $nativeWorkerEnv + ['HULK_PAIRING_RATE_KEY' => $existingKey, 'HULK_PAIRING_RATE_LIMIT' => '1000']
);
$existingResults = $existingRun['workers'];
$existingExits = array_map(static fn (array $r): int => $r['exit'], $existingResults);
cc_test($existingRun['all_ready'] === true, 'native-r06: all 9 existing-key workers reported ready before the gate opened');
cc_test(
    $existingExits === array_fill(0, 9, 0) && $nativeAttemptsFor($existingHash) === 10,
    'native: 9 coordinated workers on an EXISTING key match the seeded attempt (attempts=10)'
);

$nativeTrace('rate-existing-key-9-workers', 'pass', null, [
    'all_ready' => $existingRun['all_ready'],
    'exits' => $existingExits,
    'attempts' => $nativeAttemptsFor($existingHash),
    'workers' => array_map(
        static fn (array $r): array => [
            'id' => $r['id'],
            'ready_isolation' => $r['ready']['isolation'] ?? null,
            'exit' => $r['exit'],
        ],
        $existingResults
    ),
]);

$budgetKey = 'native-budget-key';
$budgetHash = $nativeRateHash($budgetKey);
$nativeDb->exec(
    "INSERT INTO cc_api_rate_limits (scope, client_hash, window_started_at, attempts) VALUES ('pairing_ack_req', '$budgetHash', UTC_TIMESTAMP(), 10) "
    . 'ON DUPLICATE KEY UPDATE attempts = 10, window_started_at = UTC_TIMESTAMP()'
);
$budgetRun = $nativeRunCoordinated(
    [[PHP_BINARY, $nativeWorker]],
    $nativeWorkerEnv + ['HULK_PAIRING_RATE_KEY' => $budgetKey, 'HULK_PAIRING_RATE_LIMIT' => '10']
);
$budgetResults = $budgetRun['workers'];
cc_test($budgetRun['all_ready'] === true, 'native-r06: the over-budget worker reported ready before the gate opened');
cc_test(
    $budgetResults[0]['exit'] === 10 && $nativeAttemptsFor($budgetHash) === 11,
    'native: over-budget contender is rate-limited once and still counted exactly once (attempts=11)'
);

$nativeTrace('rate-over-budget-contender', 'pass', null, [
    'all_ready' => $budgetRun['all_ready'],
    'exit' => $budgetResults[0]['exit'],
    'attempts' => $nativeAttemptsFor($budgetHash),
]);

// last capacity slot: coordinated creates never overshoot
$nativeCreateWorker = __DIR__ . '/pairing-native-create-worker.php';
$nativeCapacitySettings = $nativeSettings;
$nativeCapacitySettings['max_retained_rows'] = 100;
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$nativeCapacityValues = [];
for ($nativeSeed = 0; $nativeSeed < 99; $nativeSeed++) {
    $nativeCapacityValues[] = "(UUID(), UUID(), 'seed-$nativeSeed', REPEAT('a', 64), 'HULKTV-BULK" . str_pad((string) $nativeSeed, 2, '0', STR_PAD_LEFT) . "', REPEAT('b', 64), 'ACKED', 0, '2026-10-13 00:00:00', '2026-10-13 00:10:00', '2026-10-13 00:05:00')";
}
$nativeDb->exec(
    'INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at, terminal_at) VALUES '
    . implode(',', $nativeCapacityValues)
);
$capacityCommands = [];
for ($nativeIndex = 0; $nativeIndex < 4; $nativeIndex++) {
    $capacityCommands[] = [PHP_BINARY, $nativeCreateWorker];
}
$capacityRun = $nativeRunCoordinated(
    $capacityCommands,
    [
        'HULK_PAIRING_TEST_CONFIG' => $nativeConfig,
        'HULK_PAIRING_CREATE_MAX_ROWS' => '100',
        'HULK_PAIRING_CREATE_INSTALLATION' => '11111111-2222-4333-8444-555555555555',
        'PHPRC' => (string) php_ini_loaded_file(),
    ]
);
$capacityResults = $capacityRun['workers'];
$capacityExits = array_map(static fn (array $r): int => $r['exit'], $capacityResults);
cc_test($capacityRun['all_ready'] === true, 'native-r06: all 4 last-slot candidates reported ready before the gate opened');
$capacitySuccesses = count(array_filter($capacityExits, static fn (int $exit): bool => $exit === 0));
$capacityRejects = count(array_filter($capacityExits, static fn (int $exit): bool => $exit === 7));
$capacityTotal = (int) $nativeDb->query('SELECT COUNT(*) FROM cc_pairing_requests')->fetchColumn();
cc_test(
    $capacitySuccesses === 1 && $capacityRejects === 3 && $capacityTotal === 100,
    'native: 4 coordinated creates for the LAST capacity slot yield exactly one row (exits ' . implode(',', $capacityExits) . ', rows=' . $capacityTotal . ')'
);

$nativeTrace('capacity-last-slot-4-workers', 'pass', null, [
    'all_ready' => $capacityRun['all_ready'],
    'exits' => $capacityExits,
    'rows_after' => $capacityTotal,
    'workers' => array_map(
        static fn (array $r): array => [
            'id' => $r['id'],
            'ready_isolation' => $r['ready']['isolation'] ?? null,
            'exit' => $r['exit'],
            'stdout' => $r['stdout'],
        ],
        $capacityResults
    ),
]);

// cleanup budget: exhausted at entry, mid-run (injected clock), and retry-safe
$nativeBudgetNow = new DateTimeImmutable('2026-10-14T00:20:00Z', new DateTimeZone('UTC'));
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$nativeDb->exec(
    "INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at) VALUES "
    . "(UUID(), UUID(), 'budget-live', REPEAT('a',64), 'HULKTV-BUDG01', REPEAT('b',64), 'PENDING', 0, '2026-10-14 00:00:00', '2026-10-14 00:40:00')"
);
$expiredEntry = cc_pairing_cleanup($nativeDb, $nativeSettings, $nativeBudgetNow, false, 25, microtime(true) - 1.0);
cc_test(
    $expiredEntry['budget_exceeded'] === true
    && $expiredEntry['counts_confirmed'] === false
    && $expiredEntry['handoff_remaining'] === null
    && $expiredEntry['expired'] === 0,
    'native: budget exhausted at entry performs no work and reports unconfirmed counts'
);

$nativeDb->exec(
    "INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at) VALUES "
    . "(UUID(), UUID(), 'budget-exp', REPEAT('a',64), 'HULKTV-BUDG02', REPEAT('b',64), 'PENDING', 0, '2026-10-14 00:00:00', '2026-10-14 00:10:00')"
);
$budgetClockCalls = 0;
$budgetClock = static function () use (&$budgetClockCalls): float {
    $budgetClockCalls++;
    return $budgetClockCalls <= 2 ? 100.0 : 200.0; // expire SELECT + UPDATE fit, the rest does not
};
$midRun = cc_pairing_cleanup($nativeDb, $nativeSettings, $nativeBudgetNow, false, 25, 150.0, $budgetClock);
cc_test(
    $midRun['budget_exceeded'] === true && $midRun['counts_confirmed'] === false && $midRun['expired'] === 1,
    'native: budget exhausted mid-run stops further work and reports incomplete state'
);
$nativeDb->exec(
    "INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at) VALUES "
    . "(UUID(), UUID(), 'budget-retry', REPEAT('a',64), 'HULKTV-BUDG03', REPEAT('b',64), 'PENDING', 0, '2026-10-14 00:00:00', '2026-10-14 00:10:00')"
);
$retry = cc_pairing_cleanup($nativeDb, $nativeSettings, $nativeBudgetNow, false, 25, microtime(true) + 30.0);
cc_test(
    $retry['budget_exceeded'] === false && $retry['counts_confirmed'] === true && $retry['expired'] === 1,
    'native: retry with a fresh budget completes the pending work (retry-safe)'
);

// paired rate cleanup preserves live windows and Presence rows; invalid auth creates none
$nativeDb->exec("DELETE FROM cc_api_rate_limits");
$nativeNow = new DateTimeImmutable('2026-10-14T00:10:00Z', new DateTimeZone('UTC'));
$nativeInsertRate = $nativeDb->prepare(
    'INSERT INTO cc_api_rate_limits (scope, client_hash, window_started_at, attempts) VALUES (:s, :h, :w, 1) '
    . 'ON DUPLICATE KEY UPDATE window_started_at = VALUES(window_started_at), attempts = 1'
);
$nativeInsertRate->execute(['s' => 'pairing_claim_req', 'h' => str_repeat('c', 64), 'w' => cc_pairing_datetime($nativeNow)]);
$nativeInsertRate->execute(['s' => 'pairing_claim_req', 'h' => str_repeat('d', 64), 'w' => cc_pairing_datetime($nativeNow->modify('-7200 seconds'))]);
$nativeInsertRate->execute(['s' => 'start', 'h' => str_repeat('e', 64), 'w' => cc_pairing_datetime($nativeNow->modify('-7200 seconds'))]);
$nativeDeleted = cc_pairing_rate_limit_cleanup($nativeDb, $nativeNow, 200, $nativeSettings);
$nativeLiveKept = (int) $nativeDb->query("SELECT COUNT(*) FROM cc_api_rate_limits WHERE scope = 'pairing_claim_req' AND client_hash = '" . str_repeat('c', 64) . "'")->fetchColumn();
$nativePresenceKept = (int) $nativeDb->query("SELECT COUNT(*) FROM cc_api_rate_limits WHERE scope = 'start'")->fetchColumn();
cc_test(
    $nativeDeleted === 1 && $nativeLiveKept === 1 && $nativePresenceKept === 1,
    'native: pairing rate cleanup deletes only expired pairing windows'
);
$nativeRateRowsBefore = (int) $nativeDb->query('SELECT COUNT(*) FROM cc_api_rate_limits')->fetchColumn();
$nativeAuthFailed = false;
try {
    cc_pairing_authenticate($nativeDb, cc_pairing_uuid4(), str_repeat('A', 43));
} catch (CcPairingException $exception) {
    $nativeAuthFailed = $exception->apiCode === 'NOT_FOUND';
}
$nativeRateRowsAfter = (int) $nativeDb->query('SELECT COUNT(*) FROM cc_api_rate_limits')->fetchColumn();
cc_test($nativeAuthFailed && $nativeRateRowsAfter === $nativeRateRowsBefore, 'native: failed authentication allocates no rate rows');

// cleanup deletes retained history only and preserves live handoff material
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$nativeDb->exec(
    "INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at, terminal_at, terminal_reason) VALUES "
    . "(UUID(), UUID(), 'gone', REPEAT('a',64), 'HULKTV-GONE01', REPEAT('b',64), 'ACKED', 0, '2026-10-01 00:00:00', '2026-10-01 00:10:00', '2026-10-01 00:05:00', 'ACKED'), "
    . "(UUID(), UUID(), 'live', REPEAT('a',64), 'HULKTV-LIVE01', REPEAT('b',64), 'PENDING', 0, '2026-10-14 00:00:00', '2026-10-14 00:40:00', NULL, NULL)"
);
$nativeCleanup = cc_pairing_cleanup($nativeDb, $nativeSettings, $nativeNow, false);
$nativeLiveRows = (int) $nativeDb->query("SELECT COUNT(*) FROM cc_pairing_requests WHERE user_code = 'HULKTV-LIVE01'")->fetchColumn();
cc_test(
    $nativeCleanup['deleted'] === 1 && $nativeLiveRows === 1 && $nativeCleanup['handoff_remaining'] === 1,
    'native: cleanup deletes retained history only and preserves live handoff material'
);

// drain budget: bounded batches report incomplete until handoff is gone
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$nativeDrainValues = [];
for ($nativeSeed = 0; $nativeSeed < 3; $nativeSeed++) {
    $nativeDrainValues[] = "(UUID(), UUID(), 'drain-$nativeSeed', REPEAT('a',64), 'HULKTV-DRAIN0$nativeSeed', REPEAT('b',64), 'APPROVED', 0, '2026-10-14 00:00:00', '2026-10-14 00:40:00', 1, 'v1', 'sealed')";
}
$nativeDb->exec(
    'INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at, envelope_version, envelope_key_id, envelope_blob) VALUES '
    . implode(',', $nativeDrainValues)
);
$drainFirst = cc_pairing_cleanup($nativeDb, $nativeSettings, $nativeBudgetNow, true, 1, microtime(true) + 30.0);
cc_test(
    $drainFirst['purged'] === 3 && $drainFirst['handoff_remaining'] === 0 && $drainFirst['incomplete'] === false,
    'native: drain purges nonterminal envelopes within the bound'
);

/* ---------------------------------------------------------------------------
 * R05: whole-invocation budget enforcement (per-statement dispatches, summary,
 * setup/lock refusal, tool-level bound and lock finalization).
 * ------------------------------------------------------------------------- */

// deadline exhausted before lock acquisition: the tool refuses cleanly, no work runs,
// the whole invocation stays inside the tiny budget and the advisory lock stays free.
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$nativeDb->exec(
    "INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at) VALUES "
    . "(UUID(), UUID(), 'setup-refuse-live', REPEAT('a',64), 'HULKTV-SETUP1', REPEAT('b',64), 'PENDING', 0, '2026-10-14 00:00:00', '2026-10-14 00:40:00')"
);
$refuseStarted = microtime(true);
$refusedTool = $nativeRunTool(
    ['--budget-seconds=6'],
    ['HULK_CONTROL_CENTER_CONFIG' => $nativeConfig]
);
$refuseElapsed = $refusedTool['elapsed'];
$lockAfterRefusal = (int) $nativeDb->query('SELECT IS_FREE_LOCK("hulk_pairing_cleanup")')->fetchColumn();
cc_test(
    str_contains($refusedTool['stdout'], 'Pairing maintenance refused')
    && str_contains($refusedTool['stdout'], 'counts_confirmed=false')
    && $refuseElapsed <= 6.5
    && $lockAfterRefusal === 1,
    'native-r05: setup refusal stays inside the budget, starts no work and leaves the lock free'
);
$nativeTrace('setup-refusal-budget6', 'pass', $refuseStarted, [
    'elapsed_ms' => (int) round($refuseElapsed * 1000),
    'lock_free_after' => $lockAfterRefusal,
    'stdout' => $refusedTool['stdout'],
]);

// late drain SELECT completion must not start its UPDATE after expiry
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$drainLateRequestId = cc_pairing_uuid4();
$nativeDb->exec(
    "INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at, envelope_version, envelope_key_id, envelope_blob) VALUES "
    . "('$drainLateRequestId', UUID(), 'drain-late', REPEAT('a',64), 'HULKTV-DRAINL', REPEAT('b',64), 'APPROVED', 0, '2026-10-14 00:00:00', '2026-10-14 00:40:00', 1, 'v1', 'sealed')"
);
$drainLateCalls = 0;
$drainLateClock = static function () use (&$drainLateCalls): float {
    $drainLateCalls++;
    return $drainLateCalls <= 1 ? 100.0 : 200.0; // purge SELECT fits, its UPDATE does not
};
$drainLateStarted = microtime(true);
$drainLate = cc_pairing_cleanup($nativeDb, $nativeSettings, $nativeBudgetNow, true, 1, 150.0, $drainLateClock);
$drainLateRow = cc_pairing_load_by_request_id($nativeDb, $drainLateRequestId);
cc_test(
    $drainLate['purged'] === 0
    && (string) $drainLateRow['state'] === 'APPROVED'
    && $drainLateRow['envelope_blob'] === 'sealed'
    && $drainLate['budget_exceeded'] === true
    && $drainLate['counts_confirmed'] === false
    && $drainLate['incomplete'] === true,
    'native-r05: a late drain UPDATE is refused after expiry and leaves retry-safe state'
);
$nativeTrace('drain-late-update-refused', 'pass', $drainLateStarted, [
    'purged' => $drainLate['purged'],
    'budget_exceeded' => $drainLate['budget_exceeded'],
    'counts_confirmed' => $drainLate['counts_confirmed'],
    'state_after' => (string) $drainLateRow['state'],
]);

// late ordinary expire UPDATE refused: no state transition, envelope intact
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$lateExpireId = cc_pairing_uuid4();
$nativeDb->exec(
    "INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at, envelope_version, envelope_key_id, envelope_blob) VALUES "
    . "('$lateExpireId', UUID(), 'expire-late', REPEAT('a',64), 'HULKTV-EXPIRL', REPEAT('b',64), 'APPROVED', 0, '2026-10-14 00:00:00', '2026-10-14 00:10:00', 1, 'v1', 'sealed')"
);
$lateExpireCalls = 0;
$lateExpireClock = static function () use (&$lateExpireCalls): float {
    $lateExpireCalls++;
    return $lateExpireCalls <= 1 ? 100.0 : 200.0;
};
$lateExpireStarted = microtime(true);
$lateExpire = cc_pairing_cleanup($nativeDb, $nativeSettings, $nativeBudgetNow, false, 25, 150.0, $lateExpireClock);
$lateExpireRow = cc_pairing_load_by_request_id($nativeDb, $lateExpireId);
cc_test(
    $lateExpire['expired'] === 0
    && (string) $lateExpireRow['state'] === 'APPROVED'
    && $lateExpireRow['envelope_blob'] === 'sealed'
    && $lateExpire['budget_exceeded'] === true
    && $lateExpire['counts_confirmed'] === false,
    'native-r05: a late ordinary expire UPDATE is refused and leaves the row untouched'
);
$nativeTrace('ordinary-late-expire-refused', 'pass', $lateExpireStarted, [
    'expired' => $lateExpire['expired'],
    'state_after' => (string) $lateExpireRow['state'],
]);

// partial work then summary refusal: work completed before exhaustion is confirmed
// only when the single combined summary still fits; here it does not.
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$partialId = cc_pairing_uuid4();
$nativeDb->exec(
    "INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at) VALUES "
    . "('$partialId', UUID(), 'partial-expire', REPEAT('a',64), 'HULKTV-PARTL1', REPEAT('b',64), 'PENDING', 0, '2026-10-14 00:00:00', '2026-10-14 00:10:00')"
);
$partialCalls = 0;
$partialClock = static function () use (&$partialCalls): float {
    $partialCalls++;
    return $partialCalls <= 2 ? 100.0 : 200.0; // expire SELECT + UPDATE fit, delete/summary do not
};
$partialStarted = microtime(true);
$partial = cc_pairing_cleanup($nativeDb, $nativeSettings, $nativeBudgetNow, false, 25, 150.0, $partialClock);
cc_test(
    $partial['expired'] === 1
    && $partial['deleted'] === 0
    && $partial['budget_exceeded'] === true
    && $partial['counts_confirmed'] === false
    && $partial['handoff_remaining'] === null
    && $partial['metadata_retained'] === null,
    'native-r05: work applied before exhaustion is reported with unconfirmed summary counts'
);
$nativeTrace('partial-work-summary-refused', 'pass', $partialStarted, [
    'expired' => $partial['expired'],
    'counts_confirmed' => $partial['counts_confirmed'],
]);

// whole-invocation tool bound with a short real budget on the live database
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$nativeDb->exec(
    "INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at) VALUES "
    . "(UUID(), UUID(), 'tool-bound-live', REPEAT('a',64), 'HULKTV-TOOLB1', REPEAT('b',64), 'PENDING', 0, '2026-10-14 00:00:00', '2026-10-14 00:40:00'), "
    . "(UUID(), UUID(), 'tool-bound-exp', REPEAT('a',64), 'HULKTV-TOOLB2', REPEAT('b',64), 'PENDING', 0, '2026-10-14 00:00:00', '2026-10-14 00:10:00')"
);
$toolBoundStarted = microtime(true);
$toolBound = $nativeRunTool(['--budget-seconds=12'], ['HULK_CONTROL_CENTER_CONFIG' => $nativeConfig]);
cc_test(
    $toolBound['exit'] === 0
    && $toolBound['elapsed'] <= 12.8
    && str_contains($toolBound['stdout'], 'total_elapsed=')
    && str_contains($toolBound['stdout'], 'lock_released=true')
    && str_contains($toolBound['stdout'], 'counts_confirmed=true')
    && str_contains($toolBound['stdout'], 'budget_exceeded=false'),
    'native-r05: a 12s tool invocation completes inside its stated budget with truthful flags'
);
$nativeTrace('tool-invocation-budget12', 'pass', $toolBoundStarted, [
    'wall_ms' => (int) round($toolBound['elapsed'] * 1000),
    'exit' => $toolBound['exit'],
    'stdout' => $toolBound['stdout'],
]);

// non-overlap and lock finalization: held lock -> skipped; released -> completes; free after
$nativeLockHolder = $nativePdo();
$nativeLockAcquired = (int) $nativeLockHolder->query('SELECT GET_LOCK("hulk_pairing_cleanup", 0)')->fetchColumn();
cc_test($nativeLockAcquired === 1, 'native-r05: lock holder acquired the maintenance lock');
$overlap = $nativeRunTool(['--budget-seconds=12'], ['HULK_CONTROL_CENTER_CONFIG' => $nativeConfig]);
$nativeLockHolder->query('SELECT RELEASE_LOCK("hulk_pairing_cleanup")')->fetch();
$afterRelease = $nativeRunTool(['--budget-seconds=12'], ['HULK_CONTROL_CENTER_CONFIG' => $nativeConfig]);
$freeAfter = (int) $nativeDb->query('SELECT IS_FREE_LOCK("hulk_pairing_cleanup")')->fetchColumn();
cc_test(
    str_contains($overlap['stdout'], 'another invocation is active')
    && str_contains($afterRelease['stdout'], 'lock_released=true')
    && $freeAfter === 1,
    'native-r05: overlapping invocation is skipped and the lock is released after the winner'
);
$nativeTrace('lock-overlap-and-release', 'pass', null, [
    'overlap_skip' => str_contains($overlap['stdout'], 'another invocation is active'),
    'lock_free_after' => $freeAfter,
]);

// aborted DML leaves a known retry-safe state and the same request can be claimed later
$retryCreated = cc_pairing_create($nativeDb, $nativeInput($nativeSecret()), $nativeSettings, $nativeBaseClock());
$retryId = $retryCreated['body']['requestId'];
$retryToken = $retryCreated['body']['claimToken'];
$nativeSeedReseller($nativeDb);
cc_pairing_approve($nativeDb, $retryId, $nativeCode, 'user', 'pass', $nativeSettings, $nativeProbeOk, $nativeBaseClock, $nativeDb);
$retryLocker = $nativePdo();
$retryLocker->beginTransaction();
$retryLocker->query("SELECT pairing_id FROM cc_pairing_requests WHERE request_id = '" . $retryId . "' FOR UPDATE")->fetch();
$abortBefore = cc_pairing_load_by_request_id($nativeDb, $retryId);
$abortStarted = microtime(true);
$abortRaised = false;
try {
    cc_pairing_claim($nativeBoundedConn, $retryId, $retryToken, $nativeSettings, $nativeBaseClock);
} catch (PDOException $exception) {
    $abortRaised = true;
}
$abortElapsed = microtime(true) - $abortStarted;
$abortAfter = cc_pairing_load_by_request_id($nativeDb, $retryId);
$retryLocker->rollBack();
$retryClaim = cc_pairing_claim($nativeDb, $retryId, $retryToken, $nativeSettings, $nativeBaseClock);
cc_test(
    $abortRaised
    && $abortElapsed <= 8.0
    && (string) $abortAfter['state'] === (string) $abortBefore['state']
    && $abortAfter['envelope_blob'] === $abortBefore['envelope_blob']
    && $abortAfter['delivery_count'] === $abortBefore['delivery_count']
    && $retryClaim['state'] === 'APPROVED',
    'native-r05: an aborted blocked DML leaves state unchanged and a retry succeeds'
);
$nativeTrace('aborted-dml-retry-safe', 'pass', $abortStarted, [
    'elapsed_ms' => (int) round($abortElapsed * 1000),
    'state_after_abort' => (string) $abortAfter['state'],
]);

/* ---------------------------------------------------------------------------
 * R06: finalization outcomes, partial accounting, hard supervisor bound and
 * readiness-gated concurrency records.
 * ------------------------------------------------------------------------- */

// release outcome classification: released / not_held / refused / failed
$releaseConn = $nativePdo();
cc_pairing_apply_database_deadlines($releaseConn);
$releaseConn->query('SELECT GET_LOCK("hulk_pairing_cleanup", 0)')->fetch();
$releaseStarted = microtime(true);
$releasedOutcome = cc_pairing_release_maintenance_lock($releaseConn);
$releasedSecond = cc_pairing_release_maintenance_lock($releaseConn);
cc_test(
    $releasedOutcome === 'released' && $releasedSecond === 'not_held',
    'native-r06: RELEASE_LOCK outcomes are reported from the server result (released/not_held)'
);
$nativeTrace('release-outcomes-released-not-held', 'pass', $releaseStarted, [
    'first' => $releasedOutcome,
    'second' => $releasedSecond,
]);

$refusedConn = $nativePdo();
cc_pairing_apply_database_deadlines($refusedConn);
$refusedConn->query('SELECT GET_LOCK("hulk_pairing_cleanup", 0)')->fetch();
$refusedBudget = new CcPairingBudget(150.0, 5.0, static function (): float {
    return 200.0; // remaining -50s: the finalizer cannot fit
});
$refusedOutcome = cc_pairing_release_maintenance_lock($refusedConn, $refusedBudget);
$refusedConn->query('SELECT RELEASE_LOCK("hulk_pairing_cleanup")')->fetch();
cc_test(
    $refusedOutcome === 'refused' && $refusedBudget->finalizerRefused() === true,
    'native-r06: a budget that cannot fit the finalizer reports refused and sets the finalizer flag'
);
$nativeTrace('release-outcome-refused', 'pass', null, ['outcome' => $refusedOutcome]);

// finalizer exception (connection killed) then proven connection-close fallback
$killConn = $nativePdo();
cc_pairing_apply_database_deadlines($killConn);
$killConn->query('SELECT GET_LOCK("hulk_pairing_cleanup", 0)')->fetch();
$killConnId = (int) $killConn->query('SELECT CONNECTION_ID()')->fetchColumn();
$nativeDb->query('KILL ' . $killConnId);
usleep(200000);
$failedOutcome = cc_pairing_release_maintenance_lock($killConn);
$killConn = null;
$freeDeadline = microtime(true) + 10.0;
$freeAfterKill = 0;
while (microtime(true) < $freeDeadline) {
    $freeAfterKill = (int) $nativeDb->query('SELECT IS_FREE_LOCK("hulk_pairing_cleanup")')->fetchColumn();
    if ($freeAfterKill === 1) {
        break;
    }
    usleep(20000);
}
cc_test(
    $failedOutcome === 'failed' && $freeAfterKill === 1,
    'native-r06: a killed connection makes release fail and the lock frees on session end'
);
$nativeTrace('release-outcome-failed-connection-closed', 'pass', null, [
    'outcome' => $failedOutcome,
    'lock_free_after' => $freeAfterKill,
]);

// abrupt process death: bounded connection-close fallback
$lockHolder = proc_open(
    [PHP_BINARY, __DIR__ . '/pairing-native-lock-holder.php'],
    [1 => ['pipe', 'w'], 2 => ['pipe', 'w']],
    $holderPipes,
    null,
    ['HULK_PAIRING_TEST_CONFIG' => $nativeConfig, 'PHPRC' => (string) php_ini_loaded_file()]
);
stream_set_blocking($holderPipes[1], false);
$holderOut = '';
$holderReadyDeadline = microtime(true) + 5.0;
while (microtime(true) < $holderReadyDeadline && !str_contains($holderOut, '}')) {
    $holderOut .= (string) stream_get_contents($holderPipes[1]);
    usleep(20000);
}
$holderReady = json_decode($holderOut, true);
cc_test(is_array($holderReady) && ($holderReady['acquired'] ?? 0) === 1, 'native-r06: lock holder acquired the maintenance lock');
$holderKillStarted = microtime(true);
proc_terminate($lockHolder, 9);
proc_close($lockHolder);
$holderFree = 0;
$holderDeadline = microtime(true) + 10.0;
while (microtime(true) < $holderDeadline) {
    $holderFree = (int) $nativeDb->query('SELECT IS_FREE_LOCK("hulk_pairing_cleanup")')->fetchColumn();
    if ($holderFree === 1) {
        break;
    }
    usleep(20000);
}
$holderFreeElapsed = microtime(true) - $holderKillStarted;
cc_test(
    $holderFree === 1 && $holderFreeElapsed <= 5.0,
    'native-r06: abrupt process death frees the lock within a proven bounded fallback (' . round($holderFreeElapsed * 1000) . ' ms)'
);
$nativeTrace('connection-close-fallback-bound', 'pass', $holderKillStarted, [
    'free_ms' => (int) round($holderFreeElapsed * 1000),
]);

// partial rate deletion then refusal keeps the confirmed count (never zero)
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$nativeDb->exec("DELETE FROM cc_api_rate_limits");
$oldWindow = cc_pairing_datetime($nativeBudgetNow->modify('-7200 seconds'));
$nativeDb->exec(
    "INSERT INTO cc_api_rate_limits (scope, client_hash, window_started_at, attempts) VALUES "
    . "('pairing_claim_req', '" . str_repeat('1', 64) . "', '$oldWindow', 1), "
    . "('pairing_ack_req', '" . str_repeat('2', 64) . "', '$oldWindow', 1)"
);
$partialRateCalls = 0;
$partialRateClock = static function () use (&$partialRateCalls): float {
    $partialRateCalls++;
    return $partialRateCalls <= 5 ? 100.0 : 200.0; // SELECTs + create_min/create_hour + claim_req DELETE, then refuse
};
$partialRateStarted = microtime(true);
$partialRate = cc_pairing_cleanup($nativeDb, $nativeSettings, $nativeBudgetNow, false, 25, 150.0, $partialRateClock);
$remainingOldWindows = (int) $nativeDb->query(
    "SELECT COUNT(*) FROM cc_api_rate_limits WHERE window_started_at <= '" . cc_pairing_datetime($nativeBudgetNow->modify('-3600 seconds')) . "'"
)->fetchColumn();
cc_test(
    $partialRate['rate_limits_deleted'] === 1
    && $partialRate['rate_cleanup_stopped'] === true
    && $partialRate['budget_exceeded'] === true
    && $partialRate['counts_confirmed'] === false
    && $partialRate['incomplete'] === true
    && $remainingOldWindows === 1,
    'native-r06: a refused follow-up statement keeps the confirmed partial rate deletion (1, not 0)'
);
$nativeTrace('partial-rate-deletion-then-refusal', 'pass', $partialRateStarted, [
    'rate_limits_deleted' => $partialRate['rate_limits_deleted'],
    'rate_cleanup_stopped' => $partialRate['rate_cleanup_stopped'],
    'counts_confirmed' => $partialRate['counts_confirmed'],
]);

// cleanup DML abort on a row-locked expired request: timed, retry-safe, truthful
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$cleanupAbortId = cc_pairing_uuid4();
$nativeDb->exec(
    "INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at, envelope_version, envelope_key_id, envelope_blob) VALUES "
    . "('$cleanupAbortId', UUID(), 'cleanup-abort', REPEAT('a',64), 'HULKTV-CLNAB1', REPEAT('b',64), 'APPROVED', 0, '2026-10-14 00:00:00', '2026-10-14 00:10:00', 1, 'v1', 'sealed')"
);
$cleanupLocker = $nativePdo();
$cleanupLocker->beginTransaction();
$cleanupLocker->query("SELECT pairing_id FROM cc_pairing_requests WHERE request_id = '$cleanupAbortId' FOR UPDATE")->fetch();
$cleanupAbortStarted = microtime(true);
$cleanupAbort = cc_pairing_cleanup($nativeBoundedConn, $nativeSettings, $nativeBudgetNow, false, 25, microtime(true) + 60.0);
$cleanupAbortElapsed = microtime(true) - $cleanupAbortStarted;
$cleanupAbortRow = cc_pairing_load_by_request_id($nativeDb, $cleanupAbortId);
$cleanupLocker->rollBack();
cc_test(
    $cleanupAbort['aborted'] === true
    && $cleanupAbort['aborted_reason'] === 'PDOException'
    && $cleanupAbort['expired'] === 0
    && $cleanupAbortElapsed >= 2.0 && $cleanupAbortElapsed <= 8.0
    && (string) $cleanupAbortRow['state'] === 'APPROVED'
    && $cleanupAbortRow['envelope_blob'] === 'sealed',
    'native-r06: a blocked cleanup DML aborts within the lock-wait bound and leaves retry-safe state'
);
$cleanupRetry = cc_pairing_cleanup($nativeBoundedConn, $nativeSettings, $nativeBudgetNow, false, 25, microtime(true) + 60.0);
cc_test($cleanupRetry['aborted'] === false && $cleanupRetry['expired'] === 1, 'native-r06: cleanup retry applies the aborted expiry');
$nativeTrace('cleanup-dml-abort-retry', 'pass', $cleanupAbortStarted, [
    'aborted' => $cleanupAbort['aborted'],
    'aborted_reason' => $cleanupAbort['aborted_reason'],
    'elapsed_ms' => (int) round($cleanupAbortElapsed * 1000),
    'state_after' => (string) $cleanupAbortRow['state'],
]);

// tool-level aborted cleanup: exit 1, truthful flags, retry completes
$nativeDb->exec('TRUNCATE TABLE cc_pairing_requests');
$toolAbortId = cc_pairing_uuid4();
$nativeDb->exec(
    "INSERT INTO cc_pairing_requests (request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, state, delivery_count, created_at, expires_at) VALUES "
    . "('$toolAbortId', UUID(), 'tool-abort', REPEAT('a',64), 'HULKTV-TLAB01', REPEAT('b',64), 'PENDING', 0, '2026-10-14 00:00:00', '2026-10-14 00:10:00')"
);
$toolLocker = $nativePdo();
$toolLocker->beginTransaction();
$toolLocker->query("SELECT pairing_id FROM cc_pairing_requests WHERE request_id = '$toolAbortId' FOR UPDATE")->fetch();
$toolAbortStarted = microtime(true);
$toolAbortRun = $nativeRunTool(['--budget-seconds=20'], ['HULK_CONTROL_CENTER_CONFIG' => $nativeConfig]);
$toolLocker->rollBack();
$toolAbortWall = microtime(true) - $toolAbortStarted;
cc_test(
    $toolAbortRun['exit'] === 1
    && str_contains($toolAbortRun['stdout'], 'aborted=true')
    && str_contains($toolAbortRun['stdout'], 'incomplete=true')
    && str_contains($toolAbortRun['stdout'], 'lock_release=released')
    && str_contains($toolAbortRun['stdout'], 'child_overrun=false')
    && $toolAbortWall <= 20.5,
    'native-r06: a tool cleanup DML abort exits 1 with truthful flags and a confirmed release'
);
$toolRetry = $nativeRunTool(['--budget-seconds=20'], ['HULK_CONTROL_CENTER_CONFIG' => $nativeConfig]);
cc_test(
    $toolRetry['exit'] === 0
    && str_contains($toolRetry['stdout'], 'aborted=false')
    && str_contains($toolRetry['stdout'], 'counts_confirmed=true'),
    'native-r06: the tool retry after an abort completes with confirmed counts'
);
$nativeTrace('tool-abort-and-retry', 'pass', $toolAbortStarted, [
    'abort_exit' => $toolAbortRun['exit'],
    'abort_wall_ms' => (int) round($toolAbortWall * 1000),
    'abort_stdout' => $toolAbortRun['stdout'],
    'retry_exit' => $toolRetry['exit'],
]);

// supervisor hard bound: a silent listener stalls the child connect past the soft
// ATTR_TIMEOUT; the supervisor must kill at its hard deadline and report overrun.
$listener = proc_open(
    [PHP_BINARY, __DIR__ . '/pairing-native-silent-listener.php'],
    [1 => ['pipe', 'w'], 2 => ['pipe', 'w']],
    $listenerPipes,
    null,
    ['HULK_PAIRING_LISTENER_PORT' => '33999', 'PHPRC' => (string) php_ini_loaded_file()]
);
stream_set_blocking($listenerPipes[1], false);
$portReady = false;
$portDeadline = microtime(true) + 5.0;
while (microtime(true) < $portDeadline) {
    $probe = @stream_socket_client('tcp://127.0.0.1:33999', $probeErrno, $probeErrstr, 0.5);
    if (is_resource($probe)) {
        fclose($probe);
        $portReady = true;
        break;
    }
    usleep(50000);
}
cc_test($portReady, 'native-r06: silent listener port is ready');
$silentConfig = sys_get_temp_dir() . '/hulk-pairing-silent-' . bin2hex(random_bytes(6)) . '.php';
file_put_contents(
    $silentConfig,
    str_replace('port=33077', 'port=33999', (string) file_get_contents($nativeConfig))
);
chmod($silentConfig, 0600);
$supervisorStarted = microtime(true);
$supervisorRun = $nativeRunTool(['--budget-seconds=12'], ['HULK_CONTROL_CENTER_CONFIG' => $silentConfig]);
$supervisorWall = microtime(true) - $supervisorStarted;
unlink($silentConfig);
proc_terminate($listener, 9);
proc_close($listener);
cc_test(
    $supervisorRun['exit'] === 0
    && str_contains($supervisorRun['stdout'], 'child_overrun=true')
    && str_contains($supervisorRun['stdout'], 'lock_release=unknown_child_killed')
    && str_contains($supervisorRun['stdout'], 'incomplete=true')
    && $supervisorWall <= 12.8,
    'native-r06: the supervisor kills a stalled child at its hard deadline and never reports complete'
);
$nativeTrace('supervisor-hard-bound-kill', 'pass', $supervisorStarted, [
    'wall_ms' => (int) round($supervisorWall * 1000),
    'stdout' => $supervisorRun['stdout'],
]);
