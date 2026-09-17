<?php

declare(strict_types=1);

require dirname(__DIR__) . '/.hulk-reseller-app/bootstrap.php';

if (!hulk_is_https()) {
    hulk_redirect('https://hulksa.com/hulk-reseller-admin/?error=request');
}
hulk_start_session('admin');
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    hulk_redirect('/hulk-reseller-admin/?error=request');
}
if (!hulk_verify_csrf(is_string($_POST['csrf_token'] ?? null) ? $_POST['csrf_token'] : null)) {
    hulk_redirect('/hulk-reseller-admin/?error=csrf');
}

$action = is_string($_POST['action'] ?? null) ? $_POST['action'] : '';
try {
    if ($action === 'login') {
        $username = hulk_normalize_reseller_name(is_string($_POST['username'] ?? null) ? $_POST['username'] : '');
        $password = is_string($_POST['password'] ?? null) ? $_POST['password'] : '';
        if ($username === '' || strlen($username) > 100 || $password === '' || strlen($password) > 256) {
            password_verify($password, '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi');
            hulk_redirect('/hulk-reseller-admin/?error=invalid');
        }
        $statement = hulk_db()->prepare(
            'SELECT admin_id, password_hash, status FROM admins WHERE username_key = :username_key LIMIT 1'
        );
        $statement->execute(['username_key' => $username]);
        $admin = $statement->fetch();
        $hash = is_array($admin)
            ? (string) ($admin['password_hash'] ?? '')
            : '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi';
        if (!is_array($admin) || $hash === '' || !password_verify($password, $hash)) {
            hulk_redirect('/hulk-reseller-admin/?error=invalid');
        }
        if (($admin['status'] ?? '') !== HULK_ACTIVE_STATUS) {
            hulk_redirect('/hulk-reseller-admin/?error=inactive');
        }
        session_regenerate_id(true);
        $_SESSION['admin_id'] = (int) $admin['admin_id'];
        $_SESSION['csrf_token'] = bin2hex(random_bytes(24));
        hulk_redirect('/hulk-reseller-admin/');
    }

    if (!is_array(hulk_current_admin())) {
        hulk_redirect('/hulk-reseller-admin/?error=session');
    }

    $result = match ($action) {
        'create_reseller' => (function (): string {
            hulk_admin_create_reseller(hulk_db(), $_POST);
            return 'created';
        })(),
        'set_status' => (function (): string {
            hulk_admin_set_status(hulk_db(), $_POST['reseller_id'] ?? null, $_POST['status'] ?? null);
            return 'status';
        })(),
        'update_host' => (function (): string {
            hulk_admin_update_host(hulk_db(), $_POST['reseller_id'] ?? null, $_POST['host'] ?? null);
            return 'host';
        })(),
        'set_code' => (function (): string {
            hulk_admin_set_code(hulk_db(), $_POST['reseller_id'] ?? null, $_POST['access_code'] ?? null);
            return 'code';
        })(),
        'rotate_code' => (function (): string {
            hulk_admin_rotate_code(hulk_db(), $_POST['reseller_id'] ?? null);
            return 'code';
        })(),
        'reset_password' => (function (): string {
            hulk_admin_reset_password(
                hulk_db(),
                $_POST['reseller_id'] ?? null,
                $_POST['password'] ?? null,
                $_POST['confirm_password'] ?? null
            );
            return 'password';
        })(),
        'logout' => (function (): never {
            hulk_destroy_session();
            hulk_redirect('/hulk-reseller-admin/');
        })(),
        default => throw new InvalidArgumentException('Unknown action.'),
    };
    hulk_redirect('/hulk-reseller-admin/?result=' . rawurlencode($result));
} catch (InvalidArgumentException) {
    hulk_redirect('/hulk-reseller-admin/?error=request');
} catch (PDOException $exception) {
    hulk_redirect('/hulk-reseller-admin/?error=' . ((string) $exception->getCode() === '23000' ? 'exists' : 'service'));
} catch (Throwable) {
    hulk_redirect('/hulk-reseller-admin/?error=service');
}
