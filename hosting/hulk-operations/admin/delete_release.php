<?php

declare(strict_types=1);

require __DIR__ . '/redirect.php';

// Phase 9B: the legacy release-deletion route redirects to the Control Center
// releases module before authentication, CSRF, database access or the release
// deletion mutation can run.
ops_legacy_redirect(ops_legacy_control_center_path('releases'));
