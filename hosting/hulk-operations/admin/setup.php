<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';
require_once __DIR__ . '/read-only.php';

try {
    ops_start_admin_session();
    $db = ops_db();
    if ((int) $db->query('SELECT COUNT(*) FROM app_admin_users')->fetchColumn() > 0) {
        ops_redirect('login.php');
    }
} catch (Throwable $exception) {
    http_response_code(503);
    exit('تعذر تشغيل الإعداد الأولي. راجع قاعدة البيانات وملف config.php.');
}

$controlCenterUrl = ops_legacy_control_center_url('dashboard');
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex,nofollow"><title>إعداد المسؤول الأول</title>
    <link rel="stylesheet" href="../assets/app.css?v=2.2.1">
</head>
<body><main class="login-shell"><section class="card login-card">
    <div class="brand"><div class="brand-mark">H</div><div><strong>HULK SA</strong><small>إعداد آمن لمرة واحدة</small></div></div>
    <h1>إنشاء المسؤول الأول</h1>
    <div class="flash error">
        تم تعطيل إنشاء المسؤول الأول من هذه الصفحة. الإدارة الآن من HULK SA Control Center،
        ولا يبقى أي مسار ويب قديم قادرًا على إنشاء أو تغيير حساب الإدارة.
    </div>
    <p class="muted">لإنشاء أول مسؤول استخدم أداة الاستعادة عبر سطر الأوامر الحالية:</p>
    <p class="mono">php public_html/hulk-operations/tools/create_admin.php admin</p>
    <p class="muted">ثم سجّل الدخول من مركز التحكم.</p>
    <a class="button" href="<?= ops_e($controlCenterUrl) ?>">فتح HULK SA Control Center</a>
</section></main></body></html>
