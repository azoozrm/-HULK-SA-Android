<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once dirname(__DIR__) . '/bootstrap.php';
require_once dirname(__DIR__) . '/lib/host-health.php';
require_once dirname(__DIR__) . '/lib/diagnostics.php';

$controlDb = null;
$lockHeld = false;
$invocationStartedAt = microtime(true);
try {
    $hostHealthConfig = cc_host_health_config();
    ini_set('max_execution_time', (string) $hostHealthConfig['max_runtime_seconds']);
    ini_set('default_socket_timeout', '3');
    putenv('RES_OPTIONS=attempts:1 timeout:2');
    set_time_limit((int) $hostHealthConfig['max_runtime_seconds']);
    $controlDb = cc_host_health_database('control');
    $resellerDb = cc_host_health_database('reseller');
    cc_host_health_apply_database_deadlines($controlDb);
    cc_host_health_apply_database_deadlines($resellerDb);
    $lockHeld = cc_host_health_acquire_scheduler_lock($controlDb);
    if (!$lockHeld) {
        fwrite(STDOUT, "Phase 7 maintenance skipped: another invocation is active.\n");
        exit(0);
    }
    $result = cc_host_health_run(
        $controlDb,
        $resellerDb,
        $hostHealthConfig,
        null,
        null,
        $invocationStartedAt
    );
    $diagnosticsDeleted = cc_diagnostics_cleanup(
        $controlDb,
        cc_diagnostics_config()
    );
    fwrite(
        STDOUT,
        sprintf(
            "Phase 7 maintenance complete: checked=%d skipped=%d health_deleted=%d diagnostics_deleted=%d\n",
            $result['checked_count'],
            $result['skipped_for_runtime'],
            $result['retention_deleted'],
            $diagnosticsDeleted
        )
    );
} catch (Throwable $exception) {
    fwrite(STDERR, "Phase 7 maintenance failed.\n");
    exit(1);
} finally {
    if ($lockHeld && $controlDb instanceof PDO) {
        try {
            cc_host_health_release_scheduler_lock($controlDb);
        } catch (Throwable $exception) {
            // The MySQL connection releases its advisory lock when the process exits.
        }
    }
}
