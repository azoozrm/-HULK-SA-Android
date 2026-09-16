<?php

declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';

try {
    cc_start_admin_session();
} catch (Throwable $exception) {
    http_response_code(503);
    exit('تعذر إنهاء الجلسة بأمان.');
}

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    http_response_code(405);
    header('Allow: POST');
    exit;
}

cc_require_csrf();
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
cc_redirect(cc_base_path() . '/login.php');
