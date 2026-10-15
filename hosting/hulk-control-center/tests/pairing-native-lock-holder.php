<?php

declare(strict_types=1);

/**
 * Lab-only lock holder for the R06 connection-close fallback test: acquires the
 * pairing maintenance advisory lock, reports readiness, then sleeps until killed.
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
$acquired = (int) $db->query('SELECT GET_LOCK("hulk_pairing_cleanup", 0)')->fetchColumn();
fwrite(STDOUT, json_encode(['pid' => getmypid(), 'acquired' => $acquired]));
if ($acquired !== 1) {
    exit(4);
}
while (true) {
    sleep(1);
}
