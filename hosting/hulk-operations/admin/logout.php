<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';
ops_start_admin_session();

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    http_response_code(405);
    header('Allow: POST');
    exit;
}

ops_require_csrf();
$_SESSION = [];
if (ini_get('session.use_cookies')) {
    $parameters = session_get_cookie_params();
    setcookie(session_name(), '', [
        'expires' => time() - 42000,
        'path' => $parameters['path'],
        'domain' => $parameters['domain'],
        'secure' => true,
        'httponly' => true,
        'samesite' => 'Strict',
    ]);
}
session_destroy();
ops_redirect('login.php');
