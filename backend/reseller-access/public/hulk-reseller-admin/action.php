<?php

declare(strict_types=1);

require __DIR__ . '/redirect.php';

$action = is_string($_POST['action'] ?? null) ? $_POST['action'] : '';

// Phase 9B: the retired reseller-owner action endpoint no longer performs
// login, logout, CSRF, session ownership, database access or any hulk_admin_*
// mutation. A direct historical login POST goes to the Control Center login
// route; every other business action goes to the resellers module.
hulk_legacy_owner_redirect(
    $action === 'login' ? hulk_legacy_owner_login_url() : hulk_legacy_owner_control_center_url()
);
