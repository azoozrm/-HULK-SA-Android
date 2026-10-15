<?php

declare(strict_types=1);

/**
 * Coordinated rate-counting worker for tests/pairing-native.php: waits for the
 * barrier file, reports its isolation level, performs exactly one atomic rate-limit
 * increment and prints a JSON result. Exit codes: 0 success, 10 rate limited,
 * 4 other failure.
 */

if (PHP_SAPI !== 'cli') {
    exit(2);
}

require_once dirname(__DIR__) . '/bootstrap.php';
require_once dirname(__DIR__) . '/lib/pairing.php';

$config = (string) getenv('HULK_PAIRING_TEST_CONFIG');
if ($config === '') {
    exit(3);
}
putenv('HULK_CONTROL_CENTER_CONFIG=' . $config);

$db = cc_pairing_database('control');
$isolation = 'unknown';
try {
    $isolation = (string) $db->query('SELECT @@transaction_isolation')->fetchColumn();
} catch (Throwable) {
    try {
        $isolation = (string) $db->query('SELECT @@tx_isolation')->fetchColumn();
    } catch (Throwable) {
        $isolation = 'unknown';
    }
}
if (!in_array($isolation, ['READ-COMMITTED', 'REPEATABLE-READ', 'SERIALIZABLE'], true)) {
    fwrite(STDOUT, json_encode(['exit' => 4, 'reason' => 'invalid_isolation', 'isolation' => $isolation]));
    exit(4);
}

// Worker-ready protocol: report DB connection + bounds + isolation to the parent
// BEFORE waiting for the gate; the parent opens the gate only after every expected
// worker reports ready (no sleep-based synchronization).
$readyDir = (string) getenv('HULK_PAIRING_READY_DIR');
$workerId = (string) getenv('HULK_PAIRING_WORKER_ID');
if ($readyDir !== '' && $workerId !== '') {
    $readyTmp = $readyDir . '/worker-' . $workerId . '.tmp';
    $readyFile = $readyDir . '/worker-' . $workerId . '.ready';
    file_put_contents($readyTmp, json_encode([
        'worker' => $workerId,
        'pid' => getmypid(),
        'isolation' => $isolation,
        'ready_at' => microtime(true),
    ]));
    rename($readyTmp, $readyFile);
}

$barrier = (string) getenv('HULK_PAIRING_BARRIER');
if ($barrier !== '') {
    $waitStarted = microtime(true);
    while (!is_file($barrier)) {
        if (microtime(true) - $waitStarted > 15.0) {
            fwrite(STDOUT, json_encode(['exit' => 5, 'reason' => 'gate_timeout', 'isolation' => $isolation]));
            exit(5);
        }
        usleep(2000);
    }
}
$firedAt = microtime(true);

$resultExit = 0;
$resultReason = 'ok';
try {
    cc_pairing_rate_limit(
        $db,
        (string) getenv('HULK_PAIRING_RATE_SCOPE'),
        (string) getenv('HULK_PAIRING_RATE_KEY'),
        (int) (getenv('HULK_PAIRING_RATE_LIMIT') !== false ? getenv('HULK_PAIRING_RATE_LIMIT') : 1000),
        60,
        (string) getenv('HULK_PAIRING_RATE_SECRET'),
        cc_pairing_now(),
    );
} catch (CcPairingException $exception) {
    $resultExit = $exception->apiCode === 'RATE_LIMITED' ? 10 : 4;
    $resultReason = $exception->apiCode;
} catch (Throwable $exception) {
    $resultExit = 4;
    $resultReason = 'exception';
}

fwrite(STDOUT, json_encode([
    'exit' => $resultExit,
    'reason' => $resultReason,
    'isolation' => $isolation,
    'worker' => $workerId,
    'fired_at' => $firedAt,
    'finished_at' => microtime(true),
]));
exit($resultExit);
