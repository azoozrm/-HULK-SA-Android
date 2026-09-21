<?php

declare(strict_types=1);

require __DIR__ . '/redirect.php';

// Phase 9B: the retired Operations owner login no longer authenticates. Both
// the GET form and any POST are sent to the Control Center login route.
ops_legacy_redirect(ops_legacy_control_center_login_path());
