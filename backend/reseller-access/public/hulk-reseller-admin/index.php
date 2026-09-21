<?php

declare(strict_types=1);

require dirname(__DIR__) . '/.hulk-reseller-app/bootstrap.php';
require __DIR__ . '/read-only.php';

if (!hulk_is_https()) {
    hulk_redirect('https://hulksa.com/hulk-reseller-admin/');
}
hulk_security_headers();
hulk_start_session('admin');

try {
    $admin = hulk_current_admin();
    $resellers = is_array($admin)
        ? hulk_db()->query(
            'SELECT reseller_id, reseller_name, host, access_code, status, created_at, updated_at '
            . 'FROM resellers ORDER BY reseller_id DESC'
        )->fetchAll()
        : [];
} catch (Throwable) {
    http_response_code(503);
    $admin = null;
    $resellers = [];
    $serviceError = 'الخدمة غير متاحة مؤقتًا.';
}

$errors = [
    'invalid' => 'اسم المستخدم أو كلمة المرور غير صحيحة.',
    'inactive' => 'حساب الإدارة متوقف.',
    'csrf' => 'تعذر التحقق من الطلب. أعد المحاولة.',
    'session' => 'انتهت الجلسة. سجل الدخول من جديد.',
    'request' => 'تعذر إكمال الطلب. تحقق من القيم.',
    'exists' => 'الاسم أو كود الدخول مستخدم مسبقًا.',
    'service' => 'الخدمة غير متاحة مؤقتًا.',
];
$errorKey = is_string($_GET['error'] ?? null) ? $_GET['error'] : '';
$error = $serviceError ?? ($errors[$errorKey] ?? null);
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex,nofollow">
    <title>إدارة الموزعين | HULK SA</title>
    <link rel="stylesheet" href="/reseller/assets/styles.css">
</head>
<body>
<main class="portal-shell">
<?php if (!is_array($admin)): ?>
    <section class="portal-card" aria-labelledby="admin-title">
        <h1 id="admin-title">إدارة الموزعين</h1>
        <?php if ($error !== null): ?><p role="alert"><?= hulk_escape($error) ?></p><?php endif; ?>
        <form class="form-stack" action="/hulk-reseller-admin/action.php" method="post">
            <input type="hidden" name="action" value="login">
            <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
            <label>اسم المستخدم<input name="username" maxlength="100" autocomplete="username" required></label>
            <label>كلمة المرور<input name="password" type="password" maxlength="256" autocomplete="current-password" required></label>
            <button type="submit">تسجيل الدخول</button>
        </form>
    </section>
<?php else: ?>
    <section class="portal-card" aria-labelledby="admin-title">
        <header>
            <h1 id="admin-title">إدارة الموزعين</h1>
            <p><?= hulk_escape((string) $admin['username']) ?></p>
            <form action="/hulk-reseller-admin/action.php" method="post">
                <input type="hidden" name="action" value="logout">
                <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                <button type="submit">تسجيل الخروج</button>
            </form>
        </header>
        <?php if ($error !== null): ?><p role="alert"><?= hulk_escape($error) ?></p><?php endif; ?>
        <p role="status"><?= hulk_escape(hulk_legacy_owner_read_only_notice()) ?>
            <a href="<?= hulk_escape(hulk_legacy_owner_control_center_url()) ?>">فتح HULK SA Control Center</a></p>

        <section aria-labelledby="accounts-title">
            <h2 id="accounts-title">الحسابات</h2>
            <div class="table-wrap">
                <table>
                    <thead><tr><th>الموزع</th><th>الحالة</th><th>الهوست</th><th>كود الدخول</th></tr></thead>
                    <tbody>
                    <?php foreach ($resellers as $row): ?>
                        <tr>
                            <td><?= hulk_escape((string) $row['reseller_name']) ?></td>
                            <td><?= hulk_escape((string) $row['status']) ?></td>
                            <td dir="ltr"><?= hulk_escape((string) $row['host']) ?></td>
                            <td dir="ltr"><strong><?= hulk_escape((string) $row['access_code']) ?></strong></td>
                        </tr>
                    <?php endforeach; ?>
                    </tbody>
                </table>
            </div>
        </section>
    </section>
<?php endif; ?>
</main>
</body>
</html>
