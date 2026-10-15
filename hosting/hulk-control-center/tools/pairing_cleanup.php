<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once dirname(__DIR__) . '/bootstrap.php';
require_once dirname(__DIR__) . '/lib/pairing.php';

/**
 * Bounded pairing maintenance with a hard whole-invocation owner.
 *
 * The script runs in two roles:
 *  - supervisor (default): spawns the child with the same arguments and enforces a
 *    hard wall-clock deadline (budget minus a 0.5 s kill grace) using proc_open +
 *    SIGTERM/SIGKILL. MariaDB statement timeouts are cooperative, so the
 *    supervisor is the actual hard bound for the whole invocation; its total
 *    elapsed time is reported truthfully (overrun is never reported as complete).
 *  - child (env HULK_PAIRING_MAINTENANCE_SUPERVISED=1): carries one absolute
 *    CcPairingBudget from before setup through connection setup, deadline
 *    statements, lock acquisition, every batch statement, rate cleanup, the single
 *    combined summary query and finalization. RELEASE_LOCK is reported from its
 *    actual outcome; when it cannot be attempted inside the budget the child closes
 *    the connection (server-side lock release on session end) and reports that
 *    fallback instead of "released".
 *
 * Usage: php pairing_cleanup.php [--drain] [--budget-seconds=N]   (N 5..300, default 45)
 */

$arguments = array_slice($argv, 1);
$drain = in_array('--drain', $arguments, true);
$budgetSeconds = (float) CC_PAIRING_MAINTENANCE_SECONDS;
foreach ($arguments as $argument) {
    if (preg_match('/^--budget-seconds=(\d+)$/', $argument, $matches) === 1) {
        $budgetSeconds = max(5.0, min(300.0, (float) $matches[1]));
    }
}

if (getenv('HULK_PAIRING_MAINTENANCE_SUPERVISED') === '1') {
    exit(hulk_pairing_maintenance_child($budgetSeconds, $drain));
}
exit(hulk_pairing_maintenance_supervisor($budgetSeconds, $arguments));

/**
 * @return array{line: string, incomplete: bool, release_confirmed: bool}
 */
function hulk_pairing_maintenance_finalize(?PDO $db, bool $lockHeld, CcPairingBudget $budget, string $summaryLine): array
{
    $rollbackOutcome = 'none';
    if ($db instanceof PDO && $db->inTransaction()) {
        try {
            $db->rollBack();
            $rollbackOutcome = 'rolled_back';
        } catch (Throwable) {
            $rollbackOutcome = 'failed';
        }
    }

    $releaseOutcome = 'not_held';
    if ($lockHeld && $db instanceof PDO) {
        $releaseOutcome = cc_pairing_release_maintenance_lock($db, $budget);
        if ($releaseOutcome === 'refused' || $releaseOutcome === 'failed') {
            // Proven bounded fallback: closing the connection ends the MySQL session
            // and releases named locks server-side (see native evidence).
            $releaseOutcome = 'connection_closed';
        }
    }
    return [
        'line' => $summaryLine
            . sprintf(
                ' lock_release=%s rolled_back=%s',
                $releaseOutcome,
                $rollbackOutcome
            ),
        'incomplete' => !in_array($releaseOutcome, ['released', 'not_held', 'connection_closed'], true),
        'release_confirmed' => in_array($releaseOutcome, ['released', 'not_held'], true),
    ];
}

function hulk_pairing_maintenance_child(float $budgetSeconds, bool $drain): int
{
    $startedAt = microtime(true);
    $budget = cc_pairing_budget($startedAt, $budgetSeconds);
    $db = null;
    $lockHeld = false;
    $summaryLine = '';
    $exitCode = 0;
    $drainRefused = false;

    try {
        $settings = cc_pairing_settings();
        if ($drain && cc_pairing_enabled()) {
            $drainRefused = true;
            fwrite(STDERR, "Pairing drain refused: disable pairing authority before draining.\n");
            throw new CcPairingBudgetExceeded('disable pairing authority before draining (--drain requires disabled pairing).');
        }

        $db = cc_pairing_maintenance_database($budget);
        $info = cc_pairing_connection_info($db);
        fwrite(
            STDOUT,
            sprintf(
                "Pairing maintenance setup: driver=%s flavor=%s version=%s dml_bounded=%s statement_bound=%.1fs bounds=%s elapsed=%.3fs budget=%.1fs\n",
                $info['driver'],
                $info['flavor'],
                $info['version'] === '' ? 'n/a' : $info['version'],
                $info['dml_bounded'] ? 'true' : 'false',
                $info['statement_bound_seconds'],
                $info['bounds'] === [] ? 'n/a' : implode(',', $info['bounds']),
                microtime(true) - $startedAt,
                $budgetSeconds
            )
        );

        if (!$budget->allowWork()) {
            throw new CcPairingBudgetExceeded('budget cannot fit lock acquisition plus finalization');
        }
        $lockHeld = cc_pairing_acquire_maintenance_lock($db);
        if (!$lockHeld) {
            $totalElapsed = microtime(true) - $startedAt;
            fwrite(
                STDOUT,
                sprintf(
                    "Pairing maintenance skipped: another invocation is active. lock_release=not_held lock_released=n/a rolled_back=none total_elapsed=%.3fs budget=%.1fs overrun=%s incomplete=false\n",
                    $totalElapsed,
                    $budgetSeconds,
                    $totalElapsed > $budgetSeconds ? 'true' : 'false'
                )
            );
            return 0;
        }

        try {
            $result = cc_pairing_cleanup(
                $db,
                $settings,
                cc_pairing_now(),
                $drain,
                CC_PAIRING_CLEANUP_DRAIN_BATCHES,
                null,
                null,
                $budget
            );
            $summaryLine = sprintf(
                "Pairing cleanup complete: expired=%d deleted=%d purged=%d rate_limits_deleted=%d "
                . "rate_cleanup_stopped=%s rate_cleanup_unconfirmed=%s batches=%d aborted=%s "
                . "handoff_remaining=%s metadata_retained=%s counts_confirmed=%s budget_exceeded=%s incomplete=%s statement_bound=%s",
                $result['expired'],
                $result['deleted'],
                $result['purged'],
                $result['rate_limits_deleted'],
                $result['rate_cleanup_stopped'] ? 'true' : 'false',
                $result['rate_cleanup_unconfirmed'] ? 'true' : 'false',
                $result['batches'],
                $result['aborted'] ? 'true' : 'false',
                $result['handoff_remaining'] === null ? 'unconfirmed' : (string) $result['handoff_remaining'],
                $result['metadata_retained'] === null ? 'unconfirmed' : (string) $result['metadata_retained'],
                $result['counts_confirmed'] ? 'true' : 'false',
                $result['budget_exceeded'] ? 'true' : 'false',
                $result['incomplete'] ? 'true' : 'false',
                $result['statement_bound_seconds'] === null ? 'n/a' : sprintf('%.1fs', $result['statement_bound_seconds'])
            );
            if ($result['aborted']) {
                fwrite(STDERR, 'Pairing cleanup aborted: ' . (string) $result['aborted_reason'] . "\n");
                $exitCode = 1;
            }
        } catch (CcPairingBudgetExceeded $exception) {
            $summaryLine = sprintf(
                'Pairing maintenance refused: %s; no new work started, counts_confirmed=false budget_exceeded=true incomplete=true',
                $exception->getMessage()
            );
            $exitCode = 1;
        } catch (Throwable $exception) {
            $summaryLine = 'Pairing maintenance failed: ' . (new ReflectionClass($exception))->getShortName();
            fwrite(STDERR, 'Pairing maintenance failure: ' . (new ReflectionClass($exception))->getShortName() . "\n");
            $exitCode = 1;
        }
    } catch (CcPairingBudgetExceeded $exception) {
        $summaryLine = sprintf(
            'Pairing maintenance refused: %s; no new work started, counts_confirmed=false budget_exceeded=true incomplete=true',
            $exception->getMessage()
        );
        $exitCode = 1;
    } catch (Throwable $exception) {
        $summaryLine = 'Pairing maintenance failed: ' . (new ReflectionClass($exception))->getShortName();
        fwrite(STDERR, 'Pairing maintenance failure: ' . (new ReflectionClass($exception))->getShortName() . "\n");
        $exitCode = 1;
    }

    $final = hulk_pairing_maintenance_finalize($db, $lockHeld, $budget, $summaryLine);
    $db = null; // close the connection (includes the connection-close release fallback)
    $totalElapsed = microtime(true) - $startedAt;
    $overrun = $totalElapsed > $budgetSeconds;
    $incomplete = $final['incomplete'] || $overrun || $exitCode !== 0
        || str_contains($final['line'], 'incomplete=true');
    if ($final['incomplete']) {
        $exitCode = 1;
    }
    fwrite(
        STDOUT,
        $final['line']
        . sprintf(
            " lock_released=%s total_elapsed=%.3fs budget=%.1fs overrun=%s\n",
            $final['release_confirmed'] ? 'true' : ($final['incomplete'] ? 'false' : 'fallback'),
            $totalElapsed,
            $budgetSeconds,
            $overrun ? 'true' : 'false'
        )
    );
    if ($drain && $incomplete && !$drainRefused) {
        fwrite(STDOUT, "Pairing drain incomplete: rerun until handoff_remaining=0.\n");
    }
    return $exitCode;
}

function hulk_pairing_maintenance_supervisor(float $budgetSeconds, array $arguments): int
{
    if (!function_exists('proc_open')) {
        fwrite(STDERR, "Pairing maintenance supervisor unavailable (proc_open disabled); refusing unbounded run.\n");
        return 1;
    }
    $startedAt = microtime(true);
    $hardDeadline = $startedAt + max(1.0, $budgetSeconds) - 0.5;
    $environment = getenv();
    if (!is_array($environment)) {
        $environment = [];
    }
    $environment['HULK_PAIRING_MAINTENANCE_SUPERVISED'] = '1';
    $childCommand = [PHP_BINARY];
    $loadedIni = php_ini_loaded_file();
    if (is_string($loadedIni) && $loadedIni !== '') {
        $childCommand[] = '-c';
        $childCommand[] = $loadedIni;
    }
    $childCommand[] = __FILE__;
    $pipes = [];
    $process = proc_open(
        array_merge($childCommand, $arguments),
        [0 => ['file', '/dev/null', 'r'], 1 => ['pipe', 'w'], 2 => ['pipe', 'w']],
        $pipes,
        null,
        $environment
    );
    if (!is_resource($process)) {
        fwrite(STDERR, "Pairing maintenance supervisor failed to start the child; refusing run.\n");
        return 1;
    }
    stream_set_blocking($pipes[1], false);
    stream_set_blocking($pipes[2], false);
    $stdout = '';
    $stderr = '';
    $status = proc_get_status($process);
    while ($status['running'] && microtime(true) < $hardDeadline) {
        $stdout .= (string) stream_get_contents($pipes[1]);
        $stderr .= (string) stream_get_contents($pipes[2]);
        usleep(20000);
        $status = proc_get_status($process);
    }
    $childOverrun = (bool) $status['running'];
    if ($childOverrun) {
        proc_terminate($process, 15);
        $graceDeadline = microtime(true) + 0.5;
        do {
            usleep(20000);
            $status = proc_get_status($process);
        } while ($status['running'] && microtime(true) < $graceDeadline);
        if ($status['running']) {
            proc_terminate($process, 9);
            usleep(50000);
            $status = proc_get_status($process);
        }
    }
    $stdout .= (string) stream_get_contents($pipes[1]);
    $stderr .= (string) stream_get_contents($pipes[2]);
    fclose($pipes[1]);
    fclose($pipes[2]);
    $childExit = $childOverrun ? 137 : (int) ($status['exitcode'] ?? -1);
    proc_close($process);

    $childStdout = trim($stdout);
    if ($childStdout !== '') {
        fwrite(STDOUT, $childStdout . "\n");
    }
    if (trim($stderr) !== '') {
        fwrite(STDERR, trim($stderr) . "\n");
    }
    $releaseOutcome = 'unknown_child_killed';
    if (preg_match('/lock_release=([a-z_]+)/', $childStdout, $matches) === 1) {
        $releaseOutcome = $matches[1];
    }
    $childIncomplete = str_contains($childStdout, 'incomplete=true');
    $totalElapsed = microtime(true) - $startedAt;
    $incomplete = $childOverrun || $childIncomplete || $childExit !== 0
        || !in_array($releaseOutcome, ['released', 'not_held', 'connection_closed'], true);
    fwrite(
        STDOUT,
        sprintf(
            "Pairing maintenance supervisor: child_exit=%d child_overrun=%s lock_release=%s hard_deadline=%.1fs whole_budget=%.1fs total_elapsed=%.3fs incomplete=%s\n",
            $childExit,
            $childOverrun ? 'true' : 'false',
            $releaseOutcome,
            max(1.0, $budgetSeconds) - 0.5,
            $budgetSeconds,
            $totalElapsed,
            $incomplete ? 'true' : 'false'
        )
    );
    if ($childOverrun) {
        return 0; // truthful incomplete state; cron may retry
    }
    return max(0, $childExit);
}
