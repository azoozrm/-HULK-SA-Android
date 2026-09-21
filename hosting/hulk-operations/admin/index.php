<?php

declare(strict_types=1);

require __DIR__ . '/redirect.php';

$section = is_string($_GET['section'] ?? null) ? $_GET['section'] : 'dashboard';

// Phase 9B: the retired owner dashboard/sections redirect to the equivalent
// Control Center module before any legacy authentication, database read, CSRF
// processing or mutation runs.
ops_legacy_redirect(ops_legacy_control_center_path($section));
