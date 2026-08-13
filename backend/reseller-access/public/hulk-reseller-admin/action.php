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

$action = is_string($_POST['action'] ?? null) ? $_POST['action'] : '';
$csrfToken = is_string($_POST['csrf_token'] ?? null) ? $_POST['csrf_token'] : null;
if (!hulk_verify_csrf($csrfToken)) {
    hulk_redirect('/hulk-reseller-admin/?error=csrf');
}

try {
    switch ($action) {
        case 'login':
            hulk_admin_login_action();
            break;
        case 'create_reseller':
            hulk_admin_create_reseller_action();
            break;
        case 'set_status':
            hulk_admin_set_status_action();
            break;
        case 'update_host':
            hulk_admin_update_host_action();
            break;
        case 'set_code':
            hulk_admin_set_code_action();
            break;
        case 'rotate_code':
            hulk_admin_rotate_code_action();
            break;
        case 'reset_password':
            hulk_admin_reset_password_action();
            break;
        case 'logout':
            hulk_destroy_session();
            hulk_redirect('/hulk-reseller-admin/');
        default:
            hulk_redirect('/hulk-reseller-admin/?error=request');
    }
} catch (Throwable) {
    hulk_redirect('/hulk-reseller-admin/?error=service');
}

function hulk_admin_login_action(): never
{
    $rawUsername = is_string($_POST['username'] ?? null) ? $_POST['username'] : '';
    $password = is_string($_POST['password'] ?? null) ? $_POST['password'] : '';
    $usernameKey = hulk_normalize_reseller_name($rawUsername);
    if ($usernameKey === '' || strlen($usernameKey) > 100 || $password === '' || strlen($password) > 256) {
        password_hash(bin2hex(random_bytes(16)), PASSWORD_DEFAULT);
        hulk_redirect('/hulk-reseller-admin/?error=invalid');
    }

    $statement = hulk_db()->prepare(
        'SELECT admin_id, password_hash, status FROM admins WHERE username_key = :username_key LIMIT 1'
    );
    $statement->execute(['username_key' => $usernameKey]);
    $admin = $statement->fetch();
    $passwordHash = is_array($admin)
        ? (string) ($admin['password_hash'] ?? '')
        : password_hash(bin2hex(random_bytes(16)), PASSWORD_DEFAULT);
    if (!is_array($admin) || $passwordHash === '' || !password_verify($password, $passwordHash)) {
        hulk_redirect('/hulk-reseller-admin/?error=invalid');
    }
    if (($admin['status'] ?? '') !== HULK_ACTIVE_STATUS) {
        hulk_redirect('/hulk-reseller-admin/?error=inactive');
    }

    session_regenerate_id(true);
    $_SESSION['admin_id'] = (int) $admin['admin_id'];
    $_SESSION['csrf_token'] = bin2hex(random_bytes(24));
    $_SESSION['last_activity'] = time();
    hulk_redirect('/hulk-reseller-admin/');
}

function hulk_require_admin(): array
{
    $admin = hulk_current_admin();
    if (!is_array($admin)) {
        hulk_redirect('/hulk-reseller-admin/?error=session');
    }
    return $admin;
}

function hulk_admin_reseller_id(): int
{
    $resellerId = filter_var($_POST['reseller_id'] ?? null, FILTER_VALIDATE_INT);
    if (!is_int($resellerId) || $resellerId < 1) {
        hulk_redirect('/hulk-reseller-admin/?error=request');
    }
    return $resellerId;
}

function hulk_admin_create_reseller_action(): never
{
    hulk_require_admin();
    $resellerName = trim(is_string($_POST['reseller_name'] ?? null) ? $_POST['reseller_name'] : '');
    $password = is_string($_POST['password'] ?? null) ? $_POST['password'] : '';
    $rawHost = trim(is_string($_POST['host'] ?? null) ? $_POST['host'] : '');
    $rawCode = trim(is_string($_POST['access_code'] ?? null) ? $_POST['access_code'] : '');
    $nameKey = hulk_normalize_reseller_name($resellerName);

    if ($nameKey === '' || strlen($resellerName) > 100 || $password === '' || strlen($password) > 256) {
        hulk_redirect('/hulk-reseller-admin/?error=reseller');
    }

    $host = '';
    if ($rawHost !== '') {
        $host = hulk_normalize_host($rawHost) ?? '';
        if ($host === '') {
            hulk_redirect('/hulk-reseller-admin/?error=host');
        }
    }

    $customCode = $rawCode !== '';
    $normalizedCustomCode = $customCode ? hulk_normalize_custom_access_code($rawCode) : null;
    if ($customCode && $normalizedCustomCode === null) {
        hulk_redirect('/hulk-reseller-admin/?error=code_format');
    }

    $existing = hulk_db()->prepare('SELECT reseller_id FROM resellers WHERE reseller_name_key = :name_key LIMIT 1');
    $existing->execute(['name_key' => $nameKey]);
    if ($existing->fetch()) {
        hulk_redirect('/hulk-reseller-admin/?error=exists');
    }

    $maximumAttempts = $customCode ? 1 : 5;
    for ($attempt = 0; $attempt < $maximumAttempts; $attempt++) {
        $accessCode = $customCode ? (string) $normalizedCustomCode : hulk_generate_access_code();
        try {
            $statement = hulk_db()->prepare(
                'INSERT INTO resellers '
                . '(reseller_name, reseller_name_key, password_hash, host, access_code, access_code_hash, status) '
                . 'VALUES (:name, :name_key, :password_hash, :host, :access_code, :access_code_hash, :status)'
            );
            $statement->execute([
                'name' => $resellerName,
                'name_key' => $nameKey,
                'password_hash' => password_hash($password, PASSWORD_DEFAULT),
                'host' => $host,
                'access_code' => $accessCode,
                'access_code_hash' => hulk_access_code_hash($accessCode),
                'status' => HULK_ACTIVE_STATUS,
            ]);
            $_SESSION['created_reseller_id'] = (int) hulk_db()->lastInsertId();
            hulk_redirect('/hulk-reseller-admin/?result=created');
        } catch (PDOException $error) {
            if ((string) $error->getCode() !== '23000') {
                throw $error;
            }
            if ($customCode) {
                hulk_redirect('/hulk-reseller-admin/?error=code_exists');
            }
            if ($attempt === $maximumAttempts - 1) {
                throw $error;
            }
        }
    }
    hulk_redirect('/hulk-reseller-admin/?error=request');
}

function hulk_admin_set_status_action(): never
{
    hulk_require_admin();
    $resellerId = hulk_admin_reseller_id();
    $status = is_string($_POST['status'] ?? null) ? $_POST['status'] : '';
    if (!in_array($status, ['active', 'inactive'], true)) {
        hulk_redirect('/hulk-reseller-admin/?error=request');
    }

    $statement = hulk_db()->prepare('UPDATE resellers SET status = :status WHERE reseller_id = :id');
    $statement->execute(['status' => $status, 'id' => $resellerId]);
    hulk_redirect('/hulk-reseller-admin/?result=status');
}

function hulk_admin_update_host_action(): never
{
    hulk_require_admin();
    $resellerId = hulk_admin_reseller_id();
    $rawHost = trim(is_string($_POST['host'] ?? null) ? $_POST['host'] : '');
    $host = '';
    if ($rawHost !== '') {
        $host = hulk_normalize_host($rawHost) ?? '';
        if ($host === '') {
            hulk_redirect('/hulk-reseller-admin/?error=host');
        }
    }

    $statement = hulk_db()->prepare('UPDATE resellers SET host = :host WHERE reseller_id = :id');
    $statement->execute(['host' => $host, 'id' => $resellerId]);
    hulk_redirect('/hulk-reseller-admin/?result=host');
}

function hulk_admin_set_code_action(): never
{
    hulk_require_admin();
    $resellerId = hulk_admin_reseller_id();
    $rawCode = is_string($_POST['access_code'] ?? null) ? $_POST['access_code'] : '';
    $accessCode = hulk_normalize_custom_access_code($rawCode);
    if ($accessCode === null) {
        hulk_redirect('/hulk-reseller-admin/?error=code_format');
    }

    try {
        hulk_admin_write_code($resellerId, $accessCode);
    } catch (PDOException $error) {
        if ((string) $error->getCode() === '23000') {
            hulk_redirect('/hulk-reseller-admin/?error=code_exists');
        }
        throw $error;
    }
    hulk_redirect('/hulk-reseller-admin/?result=code');
}

function hulk_admin_rotate_code_action(): never
{
    hulk_require_admin();
    $resellerId = hulk_admin_reseller_id();
    for ($attempt = 0; $attempt < 5; $attempt++) {
        try {
            hulk_admin_write_code($resellerId, hulk_generate_access_code());
            hulk_redirect('/hulk-reseller-admin/?result=code');
        } catch (PDOException $error) {
            if ((string) $error->getCode() !== '23000' || $attempt === 4) {
                throw $error;
            }
        }
    }
    hulk_redirect('/hulk-reseller-admin/?error=request');
}

function hulk_admin_write_code(int $resellerId, string $accessCode): void
{
    $statement = hulk_db()->prepare(
        'UPDATE resellers SET access_code = :access_code, access_code_hash = :access_code_hash '
        . 'WHERE reseller_id = :id'
    );
    $statement->execute([
        'access_code' => $accessCode,
        'access_code_hash' => hulk_access_code_hash($accessCode),
        'id' => $resellerId,
    ]);
}

function hulk_admin_reset_password_action(): never
{
    hulk_require_admin();
    $resellerId = hulk_admin_reseller_id();
    $password = is_string($_POST['password'] ?? null) ? $_POST['password'] : '';
    $confirmPassword = is_string($_POST['confirm_password'] ?? null) ? $_POST['confirm_password'] : '';
    if ($password === '' || strlen($password) > 256) {
        hulk_redirect('/hulk-reseller-admin/?error=password_value');
    }
    if (!hash_equals($password, $confirmPassword)) {
        hulk_redirect('/hulk-reseller-admin/?error=password_match');
    }

    $statement = hulk_db()->prepare('UPDATE resellers SET password_hash = :password_hash WHERE reseller_id = :id');
    $statement->execute([
        'password_hash' => password_hash($password, PASSWORD_DEFAULT),
        'id' => $resellerId,
    ]);
    hulk_redirect('/hulk-reseller-admin/?result=password');
}
