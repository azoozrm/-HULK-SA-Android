<?php

declare(strict_types=1);

require __DIR__ . '/redirect.php';

// Phase 9B: the legacy reseller-owner panel redirects to Control Center before
// any HTTPS bridge, session ownership, CSRF, database or mutation work.
hulk_legacy_owner_redirect(hulk_legacy_owner_control_center_url());
