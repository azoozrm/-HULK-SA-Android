<?php

declare(strict_types=1);

require dirname(__DIR__) . '/.hulk-reseller-app/bootstrap.php';

if (!hulk_is_https()) {
    hulk_redirect('https://hulksa.com/hulk-reseller-admin/');
}
hulk_security_headers();
hulk_start_session('admin');

$createdResellerId = 0;
try {
    $admin = hulk_current_admin();
    $resellers = [];
    if (is_array($admin)) {
        $resellers = hulk_db()->query(
            'SELECT reseller_id, reseller_name, host, access_code, status, created_at, updated_at '
            . 'FROM resellers ORDER BY reseller_id DESC'
        )->fetchAll();
        $createdResellerId = (int) ($_SESSION['created_reseller_id'] ?? 0);
        unset($_SESSION['created_reseller_id']);
    }
} catch (Throwable) {
    http_response_code(503);
    $admin = null;
    $resellers = [];
    $serviceUnavailable = true;
}

$resultMessages = [
    'created' => 'تم إنشاء حساب الموزع وتجهيز كود الدخول.',
    'status' => 'تم تحديث حالة الموزع.',
    'host' => 'تم تحديث مضيف الموزع.',
    'code' => 'تم تحديث كود الدخول وإيقاف الكود السابق.',
    'password' => 'تم تعيين كلمة مرور جديدة للموزع.',
];
$errorMessages = [
    'invalid' => 'اسم المستخدم أو كلمة المرور غير صحيحة.',
    'inactive' => 'حساب الإدارة متوقف.',
    'reseller' => 'أدخل اسم موزع وكلمة مرور صحيحة.',
    'exists' => 'اسم الموزع مستخدم مسبقًا.',
    'host' => 'رابط المضيف غير صالح.',
    'code_format' => 'الكود يجب أن يكون من 8 إلى 12 حرفًا أو رقمًا، وبداخله حرف إنجليزي كبير.',
    'code_exists' => 'كود الدخول مستخدم. اختر كودًا مختلفًا.',
    'password_match' => 'كلمتا المرور غير متطابقتين.',
    'password_value' => 'أدخل كلمة مرور صحيحة.',
    'csrf' => 'تعذر التحقق من الطلب. أعد تحميل الصفحة وحاول مرة أخرى.',
    'session' => 'انتهت الجلسة. سجل الدخول من جديد.',
    'request' => 'تعذر إكمال الطلب. حاول مرة أخرى.',
    'service' => 'الخدمة غير متاحة مؤقتًا. تحقق من قاعدة البيانات.',
];
$resultKey = is_string($_GET['result'] ?? null) ? $_GET['result'] : '';
$errorKey = isset($serviceUnavailable)
    ? 'service'
    : (is_string($_GET['error'] ?? null) ? $_GET['error'] : '');
$resultMessage = $resultMessages[$resultKey] ?? null;
$errorMessage = $errorMessages[$errorKey] ?? null;

$isLoggedIn = is_array($admin);
$totalCount = count($resellers);
$activeCount = count(array_filter(
    $resellers,
    static fn (array $reseller): bool => ($reseller['status'] ?? '') === HULK_ACTIVE_STATUS
));
$inactiveCount = $totalCount - $activeCount;
$withoutHostCount = count(array_filter(
    $resellers,
    static fn (array $reseller): bool => trim((string) ($reseller['host'] ?? '')) === ''
));
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex,nofollow">
    <meta name="theme-color" content="#050604">
    <title>إدارة الموزعين | HULK SA</title>
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
                    <h1 id="portal-title">إدارة الموزعين</h1>
                </div>
            </div>
            <?php if ($isLoggedIn): ?>
                <div class="inline-actions portal-actions">
                    <a class="button ghost-button small-button" href="/reseller/">بوابة الموزع</a>
                    <form action="/hulk-reseller-admin/action.php" method="post">
                        <input type="hidden" name="action" value="logout">
                        <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                        <button class="link-button small-button" type="submit">تسجيل الخروج</button>
                    </form>
                </div>
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
                <p class="section-kicker">لوحة الإدارة</p>
                <h2>إدارة حسابات الموزعين بسهولة</h2>
                <p class="intro">أنشئ الموزعين، اربط مضيفاتهم، وتابع حالة كل حساب من لوحة واحدة.</p>
            </div>

            <form class="form-stack auth-form" action="/hulk-reseller-admin/action.php" method="post">
                <input type="hidden" name="action" value="login">
                <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">

                <div class="field-group">
                    <label class="field-label" for="username">اسم المستخدم</label>
                    <input class="text-input ltr" id="username" name="username" type="text" autocomplete="username" maxlength="100" autofocus required>
                </div>

                <div class="field-group">
                    <label class="field-label" for="password">كلمة المرور</label>
                    <div class="input-with-action">
                        <input class="text-input ltr" id="password" name="password" type="password" autocomplete="current-password" maxlength="256" required>
                        <button class="input-action" type="button" data-password-toggle aria-controls="password" aria-pressed="false">إظهار</button>
                    </div>
                </div>

                <button class="primary-button" type="submit">تسجيل الدخول</button>
            </form>
            <p class="privacy-note">لوحة خاصة بالإدارة · اتصال آمن عبر HTTPS</p>
        <?php else: ?>
            <div class="dashboard-header">
                <div>
                    <p class="intro">مرحبًا، <?= hulk_escape((string) $admin['username']) ?></p>
                    <p class="helper-text">أنشئ الحساب واربط المضيف أو اترك الإعداد للموزع.</p>
                </div>
                <span class="status-badge">الإدارة نشطة</span>
            </div>

            <section class="stats-grid" aria-label="ملخص الموزعين">
                <div class="stat-card">
                    <span class="stat-label">إجمالي الموزعين</span>
                    <strong class="stat-value"><?= $totalCount ?></strong>
                </div>
                <div class="stat-card">
                    <span class="stat-label">حسابات نشطة</span>
                    <strong class="stat-value gold"><?= $activeCount ?></strong>
                </div>
                <div class="stat-card">
                    <span class="stat-label">حسابات متوقفة</span>
                    <strong class="stat-value"><?= $inactiveCount ?></strong>
                </div>
                <div class="stat-card">
                    <span class="stat-label">بانتظار ربط مضيف</span>
                    <strong class="stat-value"><?= $withoutHostCount ?></strong>
                </div>
            </section>

            <section class="settings-block" aria-labelledby="create-title">
                <div class="section-heading">
                    <span class="step-number" aria-hidden="true">+</span>
                    <div>
                        <h2 id="create-title">إضافة موزع جديد</h2>
                        <p class="helper-text">الاسم وكلمة المرور فقط مطلوبان. المضيف والكود اختياريان ويمكن للموزع تعديلهما لاحقًا.</p>
                    </div>
                </div>
                <form class="form-stack" action="/hulk-reseller-admin/action.php" method="post">
                    <input type="hidden" name="action" value="create_reseller">
                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">

                    <div class="form-grid">
                        <div class="field-group">
                            <label class="field-label" for="reseller_name">اسم دخول الموزع</label>
                            <input class="text-input" id="reseller_name" name="reseller_name" type="text" autocomplete="off" maxlength="100" required>
                        </div>
                        <div class="field-group">
                            <label class="field-label" for="reseller_password">كلمة مرور الموزع</label>
                            <input class="text-input ltr" id="reseller_password" name="password" type="password" autocomplete="new-password" maxlength="256" required>
                        </div>
                        <div class="field-group">
                            <label class="field-label" for="reseller_host">مضيف IPTV <span class="helper-text">(اختياري)</span></label>
                            <input class="text-input ltr" id="reseller_host" name="host" type="url" inputmode="url" placeholder="http://server.com:8080" maxlength="2048">
                        </div>
                        <div class="field-group">
                            <label class="field-label" for="reseller_code">كود مخصص <span class="helper-text">(اختياري)</span></label>
                            <input class="text-input ltr" id="reseller_code" name="access_code" type="text" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="AB12CD34" maxlength="19" data-access-code>
                            <p class="helper-text compact-help">اتركه فارغًا ليُنشأ كود عشوائي آمن.</p>
                        </div>
                    </div>

                    <button class="primary-button" type="submit">إنشاء حساب الموزع</button>
                </form>
            </section>

            <section class="reseller-section" aria-labelledby="resellers-title">
                <div class="section-heading">
                    <div>
                        <h2 id="resellers-title">حسابات الموزعين</h2>
                        <p class="helper-text">ابحث بالاسم أو المضيف أو الكود، ثم افتح إدارة الحساب عند الحاجة.</p>
                    </div>
                </div>

                <?php if ($resellers === []): ?>
                    <p class="empty-state">لا توجد حسابات موزعين حتى الآن.</p>
                <?php else: ?>
                    <div class="toolbar" role="search">
                        <div class="field-group search-field">
                            <label class="field-label" for="reseller-search">بحث سريع</label>
                            <input class="text-input" id="reseller-search" type="search" placeholder="اسم الموزع أو المضيف أو الكود" data-reseller-search>
                        </div>
                        <div class="field-group filter-field">
                            <label class="field-label" for="reseller-status">الحالة</label>
                            <select class="select-input" id="reseller-status" data-reseller-status>
                                <option value="all">الكل</option>
                                <option value="active">نشط</option>
                                <option value="inactive">متوقف</option>
                            </select>
                        </div>
                    </div>

                    <div class="reseller-list">
                        <?php foreach ($resellers as $reseller): ?>
                            <?php
                            $resellerId = (int) $reseller['reseller_id'];
                            $isActive = ($reseller['status'] ?? '') === HULK_ACTIVE_STATUS;
                            $host = trim((string) ($reseller['host'] ?? ''));
                            $updatedAt = '';
                            if (is_string($reseller['updated_at'] ?? null) && $reseller['updated_at'] !== '') {
                                try {
                                    $updatedAt = (new DateTimeImmutable($reseller['updated_at']))->format('Y/m/d · H:i');
                                } catch (Throwable) {
                                    $updatedAt = '';
                                }
                            }
                            ?>
                            <article
                                class="reseller-item <?= $createdResellerId === $resellerId ? 'is-new' : '' ?>"
                                data-reseller-card
                                data-status="<?= $isActive ? 'active' : 'inactive' ?>"
                            >
                                <div class="reseller-heading">
                                    <strong><?= hulk_escape((string) $reseller['reseller_name']) ?></strong>
                                    <span class="status-badge <?= $isActive ? '' : 'status-inactive' ?>">
                                        <?= $isActive ? 'نشط' : 'متوقف' ?>
                                    </span>
                                </div>

                                <dl class="reseller-details">
                                    <div>
                                        <dt>المضيف</dt>
                                        <dd class="ltr"><?= hulk_escape($host !== '' ? $host : 'لم يربط بعد') ?></dd>
                                    </div>
                                    <div>
                                        <dt>كود الدخول</dt>
                                        <dd>
                                            <span class="ltr code-value" id="admin-code-<?= $resellerId ?>"><?= hulk_escape((string) $reseller['access_code']) ?></span>
                                            <button class="ghost-button small-button" type="button" data-copy-target="admin-code-<?= $resellerId ?>">نسخ</button>
                                        </dd>
                                    </div>
                                    <div>
                                        <dt>آخر تحديث</dt>
                                        <dd><?= hulk_escape($updatedAt !== '' ? $updatedAt : '—') ?></dd>
                                    </div>
                                </dl>

                                <div class="reseller-card-footer">
                                    <details class="collapsible-panel">
                                        <summary>إدارة الحساب</summary>
                                        <div class="collapsible-content">
                                            <div class="admin-action-block">
                                                <h3>تعديل المضيف</h3>
                                                <form class="form-stack" action="/hulk-reseller-admin/action.php" method="post">
                                                    <input type="hidden" name="action" value="update_host">
                                                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                                                    <input type="hidden" name="reseller_id" value="<?= $resellerId ?>">
                                                    <div class="field-group">
                                                        <label class="field-label" for="host-<?= $resellerId ?>">رابط المضيف</label>
                                                        <input class="text-input ltr" id="host-<?= $resellerId ?>" name="host" type="url" inputmode="url" value="<?= hulk_escape($host) ?>" placeholder="http://server.com:8080" maxlength="2048">
                                                        <p class="helper-text compact-help">اترك الحقل فارغًا لإزالة الربط.</p>
                                                    </div>
                                                    <button class="secondary-button" type="submit">حفظ المضيف</button>
                                                </form>
                                            </div>

                                            <div class="admin-action-block">
                                                <h3>تغيير كود الدخول</h3>
                                                <form class="form-stack" action="/hulk-reseller-admin/action.php" method="post" data-confirm="سيتم إيقاف كود الموزع السابق. هل تريد المتابعة؟">
                                                    <input type="hidden" name="action" value="set_code">
                                                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                                                    <input type="hidden" name="reseller_id" value="<?= $resellerId ?>">
                                                    <div class="field-group">
                                                        <label class="field-label" for="code-<?= $resellerId ?>">كود مخصص</label>
                                                        <input class="text-input ltr" id="code-<?= $resellerId ?>" name="access_code" type="text" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="AB12CD34" minlength="8" maxlength="19" data-access-code required>
                                                    </div>
                                                    <button class="secondary-button" type="submit">حفظ الكود</button>
                                                </form>
                                                <form class="form-stack" action="/hulk-reseller-admin/action.php" method="post" data-confirm="سيتم إيقاف كود الموزع السابق وإنشاء كود عشوائي. هل تريد المتابعة؟">
                                                    <input type="hidden" name="action" value="rotate_code">
                                                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                                                    <input type="hidden" name="reseller_id" value="<?= $resellerId ?>">
                                                    <button class="ghost-button" type="submit">إنشاء كود عشوائي</button>
                                                </form>
                                            </div>

                                            <div class="admin-action-block">
                                                <h3>تعيين كلمة مرور جديدة</h3>
                                                <form class="form-stack" action="/hulk-reseller-admin/action.php" method="post">
                                                    <input type="hidden" name="action" value="reset_password">
                                                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                                                    <input type="hidden" name="reseller_id" value="<?= $resellerId ?>">
                                                    <div class="form-grid">
                                                        <div class="field-group">
                                                            <label class="field-label" for="password-<?= $resellerId ?>">كلمة المرور الجديدة</label>
                                                            <input class="text-input ltr" id="password-<?= $resellerId ?>" name="password" type="password" autocomplete="new-password" maxlength="256" required>
                                                        </div>
                                                        <div class="field-group">
                                                            <label class="field-label" for="confirm-<?= $resellerId ?>">تأكيد كلمة المرور</label>
                                                            <input class="text-input ltr" id="confirm-<?= $resellerId ?>" name="confirm_password" type="password" autocomplete="new-password" maxlength="256" required>
                                                        </div>
                                                    </div>
                                                    <button class="secondary-button" type="submit">حفظ كلمة المرور</button>
                                                </form>
                                            </div>
                                        </div>
                                    </details>

                                    <form action="/hulk-reseller-admin/action.php" method="post" <?= $isActive ? 'data-confirm="سيتم منع هذا الموزع من استخدام الكود والدخول للبوابة. هل تريد إيقافه؟"' : '' ?>>
                                        <input type="hidden" name="action" value="set_status">
                                        <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                                        <input type="hidden" name="reseller_id" value="<?= $resellerId ?>">
                                        <input type="hidden" name="status" value="<?= $isActive ? 'inactive' : 'active' ?>">
                                        <button class="<?= $isActive ? 'danger-button' : 'primary-button' ?>" type="submit">
                                            <?= $isActive ? 'إيقاف الموزع' : 'تفعيل الموزع' ?>
                                        </button>
                                    </form>
                                </div>
                            </article>
                        <?php endforeach; ?>
                    </div>
                    <p class="empty-state" data-filter-empty hidden>لا توجد نتائج مطابقة للبحث.</p>
                <?php endif; ?>
            </section>
        <?php endif; ?>
    </section>
</main>
<div class="toast" role="status" aria-live="polite" data-ui-feedback></div>
</body>
</html>
