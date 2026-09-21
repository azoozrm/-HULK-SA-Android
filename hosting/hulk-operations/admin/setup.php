<?php

declare(strict_types=1);

require __DIR__ . '/redirect.php';

// Phase 9B: the one-time legacy web first-admin workflow is retired and no
// longer touches the database. Account creation uses the preserved CLI recovery
// tool (tools/create_admin.php); this web entry redirects to the Control Center
// login route.
ops_legacy_redirect(ops_legacy_control_center_login_path());
