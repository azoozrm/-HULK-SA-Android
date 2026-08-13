<?php

declare(strict_types=1);

require dirname(__DIR__) . '/.hulk-reseller-app/bootstrap.php';

if (!hulk_is_https()) {
    hulk_redirect('https://hulksa.com/reseller/?error=request');
}
hulk_start_session();
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    hulk_redirect('/reseller/?error=request');
}

$action = is_string($_POST['action'] ?? null) ? $_POST['action'] : '';
$csrfToken = is_string($_POST['csrf_token'] ?? null) ? $_POST['csrf_token'] : null;
if (!hulk_verify_csrf($csrfToken)) {
    hulk_redirect('/reseller/?error=csrf');
}

try {
    switch ($action) {
        case 'login':
            hulk_login_action();
            break;
        case 'update_host':
            hulk_update_host_action();
            break;
        case 'rotate_code':
            hulk_rotate_code_action();
            break;
        case 'set_code':
            hulk_set_code_action();
            break;
        case 'change_password':
            hulk_change_password_action();
            break;
        case 'logout':
            hulk_destroy_session();
            hulk_redirect('/reseller/');
        default:
            hulk_redirect('/reseller/?error=request');
    }
} catch (Throwable) {
    hulk_redirect('/reseller/?error=service');
}

function hulk_login_action(): never
{
    $rawName = is_string($_POST['reseller_name'] ?? null) ? $_POST['reseller_name'] : '';
    $password = is_string($_POST['password'] ?? null) ? $_POST['password'] : '';
    $nameKey = hulk_normalize_reseller_name($rawName);
    if ($nameKey === '' || strlen($nameKey) > 100 || $password === '' || strlen($password) > 256) {
        password_hash(bin2hex(random_bytes(16)), PASSWORD_DEFAULT);
        hulk_redirect('/reseller/?error=invalid');
    }

    $statement = hulk_db()->prepare(
        'SELECT reseller_id, password_hash, status FROM resellers WHERE reseller_name_key = :name_key LIMIT 1'
    );
    $statement->execute(['name_key' => $nameKey]);
    $reseller = $statement->fetch();
    $passwordHash = is_array($reseller)
        ? (string) ($reseller['password_hash'] ?? '')
        : password_hash(bin2hex(random_bytes(16)), PASSWORD_DEFAULT);
    $passwordMatches = $passwordHash !== '' && password_verify($password, $passwordHash);
    if (!is_array($reseller) || !$passwordMatches) {
        hulk_redirect('/reseller/?error=invalid');
    }
    if (($reseller['status'] ?? '') !== HULK_ACTIVE_STATUS) {
        hulk_redirect('/reseller/?error=inactive');
    }

    session_regenerate_id(true);
    $_SESSION['reseller_id'] = (int) $reseller['reseller_id'];
    $_SESSION['csrf_token'] = bin2hex(random_bytes(24));
    $_SESSION['last_activity'] = time();
    hulk_redirect('/reseller/');
}

function hulk_require_reseller(): array
{
    $reseller = hulk_current_reseller();
    if (!is_array($reseller)) {
        hulk_redirect('/reseller/?error=session');
    }
    return $reseller;
}

function hulk_update_host_action(): never
{
    $reseller = hulk_require_reseller();
    $rawHost = is_string($_POST['host'] ?? null) ? $_POST['host'] : '';
    $host = hulk_normalize_host($rawHost);
    if ($host === null) {
        hulk_redirect('/reseller/?error=host');
    }

    $statement = hulk_db()->prepare(
        'UPDATE resellers SET host = :host WHERE reseller_id = :id AND status = :status'
    );
    $statement->execute([
        'host' => $host,
        'id' => (int) $reseller['reseller_id'],
        'status' => HULK_ACTIVE_STATUS,
    ]);
    hulk_redirect('/reseller/?result=host');
}

function hulk_rotate_code_action(): never
{
    $reseller = hulk_require_reseller();
    for ($attempt = 0; $attempt < 5; $attempt++) {
        $accessCode = hulk_generate_access_code();
        try {
            $statement = hulk_db()->prepare(
                'UPDATE resellers SET access_code = :access_code, access_code_hash = :access_code_hash '
                . 'WHERE reseller_id = :id AND status = :status'
            );
            $statement->execute([
                'access_code' => $accessCode,
                'access_code_hash' => hulk_access_code_hash($accessCode),
                'id' => (int) $reseller['reseller_id'],
                'status' => HULK_ACTIVE_STATUS,
            ]);
            hulk_redirect('/reseller/?result=code');
        } catch (PDOException $error) {
            if ((string) $error->getCode() !== '23000' || $attempt === 4) {
                throw $error;
            }
        }
    }
    hulk_redirect('/reseller/?error=request');
}

function hulk_set_code_action(): never
{
    $reseller = hulk_require_reseller();
    $rawCode = is_string($_POST['access_code'] ?? null) ? $_POST['access_code'] : '';
    $accessCode = hulk_normalize_custom_access_code($rawCode);
    if ($accessCode === null) {
        hulk_redirect('/reseller/?error=code_format');
    }

    try {
        $statement = hulk_db()->prepare(
            'UPDATE resellers SET access_code = :access_code, access_code_hash = :access_code_hash '
            . 'WHERE reseller_id = :id AND status = :status'
        );
        $statement->execute([
            'access_code' => $accessCode,
            'access_code_hash' => hulk_access_code_hash($accessCode),
            'id' => (int) $reseller['reseller_id'],
            'status' => HULK_ACTIVE_STATUS,
        ]);
    } catch (PDOException $error) {
        if ((string) $error->getCode() === '23000') {
            hulk_redirect('/reseller/?error=code_exists');
        }
        throw $error;
    }
    hulk_redirect('/reseller/?result=code');
}

function hulk_change_password_action(): never
{
    $reseller = hulk_require_reseller();
    $currentPassword = is_string($_POST['current_password'] ?? null) ? $_POST['current_password'] : '';
    $newPassword = is_string($_POST['new_password'] ?? null) ? $_POST['new_password'] : '';
    $confirmPassword = is_string($_POST['confirm_password'] ?? null) ? $_POST['confirm_password'] : '';

    if ($newPassword === '' || strlen($newPassword) > 256) {
        hulk_redirect('/reseller/?error=password_value');
    }
    if (!hash_equals($newPassword, $confirmPassword)) {
        hulk_redirect('/reseller/?error=password_match');
    }

    $statement = hulk_db()->prepare(
        'SELECT password_hash FROM resellers WHERE reseller_id = :id AND status = :status LIMIT 1'
    );
    $statement->execute([
        'id' => (int) $reseller['reseller_id'],
        'status' => HULK_ACTIVE_STATUS,
    ]);
    $passwordHash = (string) ($statement->fetchColumn() ?: '');
    if ($currentPassword === '' || $passwordHash === '' || !password_verify($currentPassword, $passwordHash)) {
        hulk_redirect('/reseller/?error=password_current');
    }

    $update = hulk_db()->prepare(
        'UPDATE resellers SET password_hash = :password_hash WHERE reseller_id = :id AND status = :status'
    );
    $update->execute([
        'password_hash' => password_hash($newPassword, PASSWORD_DEFAULT),
        'id' => (int) $reseller['reseller_id'],
        'status' => HULK_ACTIVE_STATUS,
    ]);

    session_regenerate_id(true);
    $_SESSION['csrf_token'] = bin2hex(random_bytes(24));
    $_SESSION['last_activity'] = time();
    hulk_redirect('/reseller/?result=password');
}
