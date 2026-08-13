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
    'host' => 'تم حفظ المضيف. سيستخدمه التطبيق في عملية الدخول التالية.',
    'code' => 'تم حفظ كود الدخول الجديد وإيقاف الكود السابق فورًا.',
    'password' => 'تم تغيير كلمة مرور البوابة بنجاح.',
];
$errorMessages = [
    'invalid' => 'اسم الموزع أو كلمة المرور غير صحيحة.',
    'inactive' => 'حساب الموزع متوقف. تواصل مع الإدارة.',
    'host' => 'المضيف غير صالح. استخدم رابطًا يبدأ بـ http:// أو https:// من دون بيانات دخول أو استعلامات.',
    'code_format' => 'اكتب من 8 إلى 12 حرفًا أو رقمًا، مع حرف إنجليزي كبير واحد على الأقل.',
    'code_exists' => 'هذا الكود مستخدم. اختر كودًا مختلفًا.',
    'password_current' => 'كلمة المرور الحالية غير صحيحة.',
    'password_match' => 'كلمتا المرور الجديدتان غير متطابقتين.',
    'password_value' => 'أدخل كلمة مرور جديدة صحيحة.',
    'csrf' => 'تعذر التحقق من الطلب. أعد تحميل الصفحة وحاول مرة أخرى.',
    'session' => 'انتهت الجلسة. سجل الدخول من جديد.',
    'request' => 'تعذر إكمال الطلب. حاول مرة أخرى.',
    'service' => 'الخدمة غير متاحة مؤقتًا. حاول مرة أخرى لاحقًا.',
];
$resultKey = is_string($_GET['result'] ?? null) ? $_GET['result'] : '';
$errorKey = isset($serviceUnavailable)
    ? 'service'
    : (is_string($_GET['error'] ?? null) ? $_GET['error'] : '');
$resultMessage = $resultMessages[$resultKey] ?? null;
$errorMessage = $errorMessages[$errorKey] ?? null;

$isLoggedIn = is_array($reseller);
$hasHost = $isLoggedIn && trim((string) ($reseller['host'] ?? '')) !== '';
$lastUpdated = '';
if ($isLoggedIn && is_string($reseller['updated_at'] ?? null) && $reseller['updated_at'] !== '') {
    try {
        $lastUpdated = (new DateTimeImmutable($reseller['updated_at']))->format('Y/m/d · H:i');
    } catch (Throwable) {
        $lastUpdated = '';
    }
}
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="theme-color" content="#050604">
    <title>بوابة الموزع | HULK SA</title>
    <link rel="icon" type="image/png" href="/reseller/assets/hulk-icon.png">
    <link rel="stylesheet" href="/reseller/assets/styles.css">
    <script src="/reseller/assets/portal.js" defer></script>
</head>
<body>
<main class="portal-shell <?= $isLoggedIn ? 'dashboard-shell' : 'auth-shell' ?>">
    <section class="portal-card <?= $isLoggedIn ? 'dashboard-card' : 'auth-card' ?>" aria-labelledby="portal-title">
        <header class="portal-header">
            <div class="brand-lockup">
                <img class="brand-logo" src="/reseller/assets/hulk-logo.png" alt="شعار HULK SA" width="112" height="112">
                <div class="brand-copy">
                    <p class="eyebrow">HULK SA</p>
                    <h1 id="portal-title">بوابة الموزع</h1>
                </div>
            </div>
            <?php if ($isLoggedIn): ?>
                <form class="logout-form" action="/reseller/action.php" method="post">
                    <input type="hidden" name="action" value="logout">
                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                    <button class="link-button small-button" type="submit">تسجيل الخروج</button>
                </form>
            <?php endif; ?>
        </header>

        <?php if ($resultMessage !== null): ?>
            <p class="notice notice-success" role="status"><?= hulk_escape($resultMessage) ?></p>
        <?php endif; ?>
        <?php if ($errorMessage !== null): ?>
            <p class="notice notice-error" role="alert"><?= hulk_escape($errorMessage) ?></p>
        <?php endif; ?>

        <?php if (!$isLoggedIn): ?>
            <div class="auth-copy">
                <p class="section-kicker">حساب الموزع</p>
                <h2>أدر مضيفك وكود دخول عملائك</h2>
                <p class="intro">سجل الدخول بحسابك لإعداد السيرفر وتحديث كود الدخول من مكان واحد.</p>
            </div>

            <form class="form-stack auth-form" action="/reseller/action.php" method="post">
                <input type="hidden" name="action" value="login">
                <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">

                <div class="field-group">
                    <label class="field-label" for="reseller_name">اسم الموزع</label>
                    <input
                        class="text-input"
                        id="reseller_name"
                        name="reseller_name"
                        type="text"
                        autocomplete="username"
                        maxlength="100"
                        autofocus
                        required
                    >
                </div>

                <div class="field-group">
                    <label class="field-label" for="password">كلمة المرور</label>
                    <div class="input-with-action">
                        <input
                            class="text-input ltr"
                            id="password"
                            name="password"
                            type="password"
                            autocomplete="current-password"
                            maxlength="256"
                            required
                        >
                        <button class="input-action" type="button" data-password-toggle aria-controls="password" aria-pressed="false">إظهار</button>
                    </div>
                </div>

                <button class="primary-button" type="submit">تسجيل الدخول</button>
            </form>
            <p class="privacy-note">اتصال آمن عبر HTTPS · بياناتك لا تُحفظ في المتصفح</p>
        <?php else: ?>
            <div class="dashboard-header">
                <div>
                    <p class="intro">مرحبًا، <?= hulk_escape((string) $reseller['reseller_name']) ?></p>
                    <p class="helper-text">خطوتان فقط: اربط المضيف ثم شارك كود الدخول.</p>
                </div>
                <span class="status-badge">الحساب نشط</span>
            </div>

            <section class="stats-grid" aria-label="ملخص حساب الموزع">
                <div class="stat-card">
                    <span class="stat-label">حالة المضيف</span>
                    <strong class="stat-value"><?= $hasHost ? 'مرتبط' : 'غير مرتبط' ?></strong>
                </div>
                <div class="stat-card">
                    <span class="stat-label">كود الدخول</span>
                    <strong class="stat-value gold">جاهز</strong>
                </div>
                <div class="stat-card">
                    <span class="stat-label">التطبيق</span>
                    <strong class="stat-value">يتحدث فوريًا</strong>
                </div>
                <div class="stat-card">
                    <span class="stat-label">آخر تحديث</span>
                    <strong class="stat-value"><?= hulk_escape($lastUpdated !== '' ? $lastUpdated : '—') ?></strong>
                </div>
            </section>

            <div class="dashboard-grid">
                <section class="settings-block" aria-labelledby="host-title">
                    <div class="section-heading">
                        <span class="step-number" aria-hidden="true">1</span>
                        <div>
                            <h2 id="host-title">اربط مضيف IPTV</h2>
                            <p class="helper-text">غيّر الرابط في أي وقت من دون تحديث التطبيق.</p>
                        </div>
                    </div>
                    <form class="form-stack" action="/reseller/action.php" method="post">
                        <input type="hidden" name="action" value="update_host">
                        <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                        <div class="field-group">
                            <label class="field-label" for="host">رابط المضيف الكامل</label>
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
                            <p class="helper-text compact-help">يدعم http وhttps مع رقم المنفذ عند الحاجة.</p>
                        </div>
                        <button class="primary-button" type="submit">حفظ وربط المضيف</button>
                    </form>
                </section>

                <section class="settings-block" aria-labelledby="code-title">
                    <div class="section-heading">
                        <span class="step-number" aria-hidden="true">2</span>
                        <div>
                            <h2 id="code-title">شارك كود الدخول</h2>
                            <p class="helper-text">يدخله العميل قبل اسم المستخدم وكلمة المرور.</p>
                        </div>
                    </div>

                    <div class="code-preview">
                        <label class="field-label" for="access-code">الكود الحالي</label>
                        <div class="code-control">
                            <input
                                class="code-input ltr"
                                id="access-code"
                                type="text"
                                value="<?= hulk_escape((string) $reseller['access_code']) ?>"
                                readonly
                                aria-readonly="true"
                            >
                            <div class="code-actions">
                                <button class="secondary-button small-button" type="button" data-copy-target="access-code">نسخ</button>
                                <button class="ghost-button small-button" type="button" data-share-target="access-code">مشاركة</button>
                            </div>
                        </div>
                    </div>

                    <form class="form-stack code-form" action="/reseller/action.php" method="post" data-confirm="سيتم إيقاف الكود السابق فورًا. هل تريد حفظ الكود الجديد؟">
                        <input type="hidden" name="action" value="set_code">
                        <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                        <div class="field-group">
                            <label class="field-label" for="custom-access-code">اختر كودًا خاصًا بك</label>
                            <input
                                class="text-input ltr"
                                id="custom-access-code"
                                name="access_code"
                                type="text"
                                autocomplete="off"
                                autocapitalize="characters"
                                spellcheck="false"
                                placeholder="AB12CD34"
                                minlength="8"
                                maxlength="19"
                                data-access-code
                                required
                            >
                            <p class="helper-text compact-help">من 8 إلى 12 حرفًا أو رقمًا وبداخله حرف كبير. تضاف HULK تلقائيًا.</p>
                        </div>
                        <button class="primary-button" type="submit">حفظ الكود المختار</button>
                    </form>

                    <form class="form-stack code-form" action="/reseller/action.php" method="post" data-confirm="سيتم إيقاف الكود السابق وإنشاء كود عشوائي جديد. هل تريد المتابعة؟">
                        <input type="hidden" name="action" value="rotate_code">
                        <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                        <button class="secondary-button" type="submit">إنشاء كود عشوائي آمن</button>
                    </form>
                </section>
            </div>

            <details class="collapsible-panel">
                <summary>أمان حساب البوابة</summary>
                <div class="collapsible-content">
                    <p class="helper-text">غيّر كلمة المرور من هنا. لن تتغير بيانات اشتراكات IPTV.</p>
                    <form class="form-stack" action="/reseller/action.php" method="post">
                        <input type="hidden" name="action" value="change_password">
                        <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                        <div class="form-grid">
                            <div class="field-group full-span">
                                <label class="field-label" for="current-password">كلمة المرور الحالية</label>
                                <input class="text-input ltr" id="current-password" name="current_password" type="password" autocomplete="current-password" maxlength="256" required>
                            </div>
                            <div class="field-group">
                                <label class="field-label" for="new-password">كلمة المرور الجديدة</label>
                                <input class="text-input ltr" id="new-password" name="new_password" type="password" autocomplete="new-password" maxlength="256" required>
                            </div>
                            <div class="field-group">
                                <label class="field-label" for="confirm-password">تأكيد كلمة المرور</label>
                                <input class="text-input ltr" id="confirm-password" name="confirm_password" type="password" autocomplete="new-password" maxlength="256" required>
                            </div>
                        </div>
                        <button class="secondary-button" type="submit">تغيير كلمة المرور</button>
                    </form>
                </div>
            </details>
        <?php endif; ?>
    </section>
</main>
<div class="toast" role="status" aria-live="polite" data-ui-feedback></div>
</body>
</html>
