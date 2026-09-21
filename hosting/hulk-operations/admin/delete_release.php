<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';
if (!defined('HULK_OPERATIONS_ADMIN')) {
    define('HULK_OPERATIONS_ADMIN', true);
}
require_once __DIR__ . '/actions.php';

try {
    $admin = ops_require_admin();
    ops_require_csrf();

    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
        throw new InvalidArgumentException('طريقة الطلب غير مسموحة.');
    }

    $db = ops_db();
    $message = ops_delete_release($db, $admin);
    ops_flash('success', $message);
} catch (InvalidArgumentException $exception) {
    ops_flash('error', $exception->getMessage());
} catch (Throwable $exception) {
    error_log('HULK Operations release deletion failed: ' . $exception->getMessage());
    ops_flash('error', 'تعذر حذف الإصدار بأمان.');
}

ops_redirect('index.php?section=releases');
