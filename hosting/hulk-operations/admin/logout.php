<?php

declare(strict_types=1);

require __DIR__ . '/redirect.php';

// Phase 9B: the retired Operations owner session no longer exists, so there is
// nothing to destroy. This route redirects to the Control Center entry and
// never mutates Control Center session state.
ops_legacy_redirect(ops_legacy_control_center_path('dashboard'));
