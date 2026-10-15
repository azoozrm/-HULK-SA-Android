<?php

declare(strict_types=1);

/**
 * Coordinated create worker for tests/pairing-native.php: waits for the barrier
 * file and performs exactly one create against the isolated disposable database.
 * Exit codes: 0 created, 7 pairing capacity, 4 other failure.
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

$settings = cc_pairing_settings();
$maxRows = (int) (getenv('HULK_PAIRING_CREATE_MAX_ROWS') !== false ? getenv('HULK_PAIRING_CREATE_MAX_ROWS') : $settings['max_retained_rows']);
$settings['max_retained_rows'] = $maxRows;
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

// Worker-ready protocol: connection + settings prepared and reported before the
// parent opens the gate for the last-slot race.
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

$input = [
    'contractVersion' => 1,
    'clientRequestId' => cc_pairing_uuid4(),
    'clientSecret' => rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '='),
    'installationId' => (string) getenv('HULK_PAIRING_CREATE_INSTALLATION'),
    'device' => ['platform' => 'TV'],
    'requestedAtEpochMs' => 0,
];

$resultExit = 0;
$resultReason = 'ok';
$requestId = null;
try {
    $result = cc_pairing_create($db, $input, $settings);
    $requestId = $result['body']['requestId'];
} catch (CcPairingException $exception) {
    $resultExit = $exception->apiCode === 'PAIRING_CAPACITY' ? 7 : 4;
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
    'requestId' => $requestId,
    'fired_at' => $firedAt,
    'finished_at' => microtime(true),
]));
exit($resultExit);
