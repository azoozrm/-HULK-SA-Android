<?php

declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/views/components.php';
require_once __DIR__ . '/views/layout.php';
require_once __DIR__ . '/lib/operations-adapter.php';
require_once __DIR__ . '/lib/reseller-adapter.php';
require_once __DIR__ . '/lib/presence-read-model.php';
require_once __DIR__ . '/lib/phase7-read-model.php';
require_once __DIR__ . '/lib/dashboard.php';

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

$moduleKey = (string) $resolved['key'];
if ($resolved['valid'] && ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    cc_require_csrf();
    try {
        if (in_array($moduleKey, cc_operations_modules(), true)) {
            $message = cc_operations_handle_post($moduleKey, $admin);
        } elseif (in_array($moduleKey, cc_reseller_modules(), true)) {
            $message = cc_reseller_handle_post($moduleKey, $admin);
        } else {
            throw new InvalidArgumentException('هذه الوحدة لا تقبل تعديلات في المرحلة الثانية.');
        }
        cc_flash('success', $message);
    } catch (InvalidArgumentException $exception) {
        cc_flash('danger', $exception->getMessage());
    } catch (PDOException $exception) {
        error_log('HULK Control Center database mutation failed.');
        cc_flash(
            'danger',
            (string) $exception->getCode() === '23000'
                ? 'القيمة مستخدمة مسبقًا ولا يمكن تكرارها.'
                : 'تعذر حفظ التغيير في قاعدة البيانات.'
        );
    } catch (Throwable $exception) {
        error_log('HULK Control Center mutation failed.');
        cc_flash('danger', 'تعذر تنفيذ العملية بأمان.');
    }
    cc_redirect(cc_url($moduleKey));
}

$flash = cc_take_flash();
$pageData = [];
if ($resolved['valid']) {
    try {
        if ($moduleKey === 'dashboard') {
            $pageData = cc_dashboard_data();
        } elseif (in_array($moduleKey, ['live-users', 'sessions', 'devices'], true)) {
            $pageData = cc_presence_page_data($moduleKey, $_GET);
        } elseif (in_array($moduleKey, ['host-health', 'diagnostics', 'analytics'], true)) {
            $pageData = cc_phase7_page_data($moduleKey);
        } elseif (in_array($moduleKey, cc_operations_modules(), true) || $moduleKey === 'audit') {
            $pageData = cc_operations_data($moduleKey);
        } elseif (in_array($moduleKey, cc_reseller_modules(), true)) {
            $pageData = cc_reseller_page($moduleKey);
        }
    } catch (Throwable $exception) {
        error_log('HULK Control Center module read failed.');
        $pageData = ['load_error' => true];
    }
}

cc_page_start($resolved, $admin);
if ($flash !== null) {
    cc_alert(
        $flash['type'] === 'success' ? 'تم الحفظ' : 'تعذر الحفظ',
        (string) ($flash['message'] ?? ''),
        (string) ($flash['type'] ?? 'warning')
    );
}
if (!$resolved['valid']) {
    require __DIR__ . '/views/not-found.php';
} elseif ($resolved['key'] === 'dashboard') {
    require __DIR__ . '/views/dashboard.php';
} elseif (in_array($moduleKey, ['live-users', 'sessions'], true)) {
    require __DIR__ . '/views/sessions.php';
} elseif ($moduleKey === 'devices') {
    require __DIR__ . '/views/devices.php';
} elseif ($moduleKey === 'host-health') {
    require __DIR__ . '/views/host-health.php';
} elseif ($moduleKey === 'diagnostics') {
    require __DIR__ . '/views/diagnostics.php';
} elseif ($moduleKey === 'analytics') {
    require __DIR__ . '/views/analytics.php';
} elseif (in_array($moduleKey, cc_operations_modules(), true)) {
    require __DIR__ . '/views/operations.php';
} elseif (in_array($moduleKey, cc_reseller_modules(), true)) {
    require __DIR__ . '/views/resellers.php';
} elseif ($moduleKey === 'audit') {
    require __DIR__ . '/views/audit.php';
} else {
    require __DIR__ . '/views/module.php';
}
cc_page_end();
