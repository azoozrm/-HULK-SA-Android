<?php

declare(strict_types=1);

/**
 * Explicit mock runner: loads the narrow in-memory PDO test double and then the
 * normal Control Center suite. Use ONLY on lab machines without a PDO extension;
 * native/native-DB environments must run tests/run.php directly.
 */

putenv('HULK_PAIRING_TEST_FIXTURE=1');

require_once __DIR__ . '/pairing-db-fixture.php';
require_once __DIR__ . '/run.php';
