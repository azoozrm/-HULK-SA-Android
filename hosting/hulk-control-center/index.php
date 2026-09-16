<?php

declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/views/components.php';
require_once __DIR__ . '/views/layout.php';

try {
    $admin = cc_require_admin();
} catch (Throwable $exception) {
    http_response_code(503);
    exit('تعذر تشغيل مركز التحكم بأمان. تحقق من إعداد HTTPS وملف الإعدادات.');
}

$requested = isset($_GET['module']) && is_string($_GET['module']) ? $_GET['module'] : null;
$resolved = cc_resolve_module($requested);
if (!$resolved['valid']) {
    http_response_code(404);
}

cc_page_start($resolved, $admin);
if (!$resolved['valid']) {
    require __DIR__ . '/views/not-found.php';
} elseif ($resolved['key'] === 'dashboard') {
    require __DIR__ . '/views/dashboard.php';
} else {
    require __DIR__ . '/views/module.php';
}
cc_page_end();
