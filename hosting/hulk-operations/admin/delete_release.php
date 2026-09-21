<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';
if (!defined('HULK_OPERATIONS_ADMIN')) {
    define('HULK_OPERATIONS_ADMIN', true);
}
require_once __DIR__ . '/actions.php';
require_once __DIR__ . '/read-only.php';

try {
    $admin = ops_require_admin();
    ops_require_csrf();

    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
        throw new InvalidArgumentException('طريقة الطلب غير مسموحة.');
    }

    // Phase 9A: legacy release deletion is disabled. The request is routed to
    // the Control Center releases module before ops_delete_release executes.
    ops_legacy_block_owner_mutation('releases');

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
