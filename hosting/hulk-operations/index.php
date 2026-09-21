<?php

declare(strict_types=1);

require __DIR__ . '/admin/redirect.php';

// Phase 9B: the legacy Operations owner entry now redirects to Control Center.
ops_legacy_redirect(ops_legacy_control_center_path('dashboard'));
