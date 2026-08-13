<?php

declare(strict_types=1);

require dirname(__DIR__) . '/.hulk-reseller-app/bootstrap.php';

if (!hulk_is_https()) {
    hulk_redirect('https://hulksa.com/reseller/');
}
hulk_security_headers();
hulk_start_session();

try {
    $reseller = hulk_current_reseller();
} catch (Throwable) {
    http_response_code(503);
    $reseller = null;
    $serviceUnavailable = true;
}

$resultMessages = [
    'host' => 'تم تحديث المضيف. سيستخدمه التطبيق في عملية الدخول التالية.',
    'code' => 'تم إنشاء كود دخول جديد وإلغاء الكود السابق.',
];
$errorMessages = [
    'invalid' => 'اسم الموزع أو كلمة المرور غير صحيحة.',
    'inactive' => 'حساب الموزع متوقف. تواصل مع الإدارة.',
    'host' => 'المضيف غير صالح. استخدم رابطا يبدأ بـ http:// أو https:// بدون بيانات دخول أو استعلامات.',
    'csrf' => 'تعذر التحقق من الطلب. أعد تحميل الصفحة وحاول مرة أخرى.',
    'session' => 'انتهت الجلسة. سجل الدخول من جديد.',
    'request' => 'تعذر إكمال الطلب. حاول مرة أخرى.',
    'service' => 'الخدمة غير متاحة مؤقتا. حاول مرة أخرى لاحقا.',
];
$resultKey = is_string($_GET['result'] ?? null) ? $_GET['result'] : '';
$errorKey = isset($serviceUnavailable)
    ? 'service'
    : (is_string($_GET['error'] ?? null) ? $_GET['error'] : '');
$resultMessage = $resultMessages[$resultKey] ?? null;
$errorMessage = $errorMessages[$errorKey] ?? null;
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex,nofollow">
    <title>بوابة الموزع | HULK SA</title>
    <link rel="stylesheet" href="/reseller/assets/styles.css">
</head>
<body>
<main class="portal-shell">
    <section class="portal-card" aria-labelledby="portal-title">
        <header class="brand-lockup">
            <span class="brand-mark" aria-hidden="true">H</span>
            <div>
                <p class="eyebrow">HULK SA</p>
                <h1 id="portal-title">بوابة الموزع</h1>
            </div>
            <?php if (is_array($reseller)): ?>
                <form class="logout-form" action="/reseller/action.php" method="post">
                    <input type="hidden" name="action" value="logout">
                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                    <button class="link-button" type="submit">تسجيل الخروج</button>
                </form>
            <?php endif; ?>
        </header>

        <?php if ($resultMessage !== null): ?>
            <p class="notice notice-success" role="status"><?= hulk_escape($resultMessage) ?></p>
        <?php endif; ?>
        <?php if ($errorMessage !== null): ?>
            <p class="notice notice-error" role="alert"><?= hulk_escape($errorMessage) ?></p>
        <?php endif; ?>

        <?php if (!is_array($reseller)): ?>
            <p class="intro">سجل الدخول لإدارة المضيف وكود الدخول الخاص بك.</p>
            <form class="form-stack" action="/reseller/action.php" method="post">
                <input type="hidden" name="action" value="login">
                <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">

                <label class="field-label" for="reseller_name">اسم الموزع</label>
                <input
                    class="text-input"
                    id="reseller_name"
                    name="reseller_name"
                    type="text"
                    autocomplete="username"
                    maxlength="100"
                    required
                >

                <label class="field-label" for="password">كلمة المرور</label>
                <input
                    class="text-input"
                    id="password"
                    name="password"
                    type="password"
                    autocomplete="current-password"
                    minlength="10"
                    maxlength="256"
                    required
                >

                <button class="primary-button" type="submit">تسجيل الدخول</button>
            </form>
        <?php else: ?>
            <div class="dashboard-header">
                <div>
                    <p class="intro">أهلا، <?= hulk_escape((string) $reseller['reseller_name']) ?></p>
                    <p class="helper-text">يمكنك إدارة المضيف وكود الدخول فقط.</p>
                </div>
                <span class="status-badge">الحساب نشط</span>
            </div>

            <div class="dashboard-grid">
                <section class="settings-block" aria-labelledby="host-title">
                    <h2 id="host-title">مضيف IPTV</h2>
                    <p class="helper-text">أي تعديل هنا يعمل في تسجيل الدخول التالي دون تحديث التطبيق.</p>
                    <form class="form-stack" action="/reseller/action.php" method="post">
                        <input type="hidden" name="action" value="update_host">
                        <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                        <label class="field-label" for="host">رابط المضيف</label>
                        <input
                            class="text-input ltr"
                            id="host"
                            name="host"
                            type="url"
                            inputmode="url"
                            value="<?= hulk_escape((string) $reseller['host']) ?>"
                            placeholder="http://reseller-server.com:8080"
                            maxlength="2048"
                            required
                        >
                        <button class="primary-button" type="submit">حفظ المضيف</button>
                    </form>
                </section>

                <section class="settings-block" aria-labelledby="code-title">
                    <h2 id="code-title">كود الدخول</h2>
                    <p class="helper-text">أرسله لعملائك لإدخاله قبل اسم المستخدم وكلمة المرور.</p>
                    <label class="field-label" for="access-code">الكود الحالي</label>
                    <input
                        class="code-input ltr"
                        id="access-code"
                        type="text"
                        value="<?= hulk_escape((string) $reseller['access_code']) ?>"
                        readonly
                        aria-readonly="true"
                    >
                    <form class="form-stack" action="/reseller/action.php" method="post">
                        <input type="hidden" name="action" value="rotate_code">
                        <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                        <button class="secondary-button" type="submit">إنشاء كود جديد</button>
                    </form>
                    <p class="helper-text">عند التغيير يتوقف الكود السابق فورًا.</p>
                </section>
            </div>
        <?php endif; ?>
    </section>
</main>
</body>
</html>
