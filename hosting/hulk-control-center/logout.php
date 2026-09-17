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
cc_destroy_admin_session();
cc_redirect(cc_base_path() . '/login.php');
