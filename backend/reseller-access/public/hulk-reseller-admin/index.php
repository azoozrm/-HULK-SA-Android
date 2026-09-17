<?php

declare(strict_types=1);

require dirname(__DIR__) . '/.hulk-reseller-app/bootstrap.php';

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

$messages = [
    'created' => 'تم إنشاء الموزع.',
    'status' => 'تم تحديث الحالة.',
    'host' => 'تم تحديث الهوست.',
    'code' => 'تم تحديث كود الدخول.',
    'password' => 'تم تحديث كلمة المرور.',
];
$errors = [
    'invalid' => 'اسم المستخدم أو كلمة المرور غير صحيحة.',
    'inactive' => 'حساب الإدارة متوقف.',
    'csrf' => 'تعذر التحقق من الطلب. أعد المحاولة.',
    'session' => 'انتهت الجلسة. سجل الدخول من جديد.',
    'request' => 'تعذر إكمال الطلب. تحقق من القيم.',
    'exists' => 'الاسم أو كود الدخول مستخدم مسبقًا.',
    'service' => 'الخدمة غير متاحة مؤقتًا.',
];
$resultKey = is_string($_GET['result'] ?? null) ? $_GET['result'] : '';
$result = $messages[$resultKey] ?? null;
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
        <?php if ($result !== null): ?><p role="status"><?= hulk_escape($result) ?></p><?php endif; ?>
        <?php if ($error !== null): ?><p role="alert"><?= hulk_escape($error) ?></p><?php endif; ?>

        <section aria-labelledby="create-title">
            <h2 id="create-title">إضافة موزع</h2>
            <form class="form-stack" action="/hulk-reseller-admin/action.php" method="post">
                <input type="hidden" name="action" value="create_reseller">
                <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                <input name="reseller_name" maxlength="100" placeholder="اسم الموزع" required>
                <input name="password" type="password" minlength="10" maxlength="256" placeholder="كلمة المرور" required>
                <input name="host" type="url" maxlength="2048" placeholder="الهوست">
                <input name="access_code" maxlength="64" placeholder="HULK-XXXX-XXXX-XXXX-XXXX">
                <button type="submit">إنشاء</button>
            </form>
        </section>

        <section aria-labelledby="accounts-title">
            <h2 id="accounts-title">الحسابات</h2>
            <div class="table-wrap">
                <table>
                    <thead><tr><th>الموزع</th><th>الحالة</th><th>الهوست</th><th>كود الدخول</th><th>الإدارة</th></tr></thead>
                    <tbody>
                    <?php foreach ($resellers as $row): $id = (int) $row['reseller_id']; ?>
                        <tr>
                            <td><?= hulk_escape($row['reseller_name']) ?></td>
                            <td><?= hulk_escape($row['status']) ?></td>
                            <td>
                                <form action="/hulk-reseller-admin/action.php" method="post">
                                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                                    <input type="hidden" name="action" value="update_host">
                                    <input type="hidden" name="reseller_id" value="<?= $id ?>">
                                    <input name="host" type="url" maxlength="2048" value="<?= hulk_escape($row['host']) ?>" placeholder="فارغ للمسح">
                                    <button type="submit">حفظ</button>
                                </form>
                            </td>
                            <td dir="ltr">
                                <strong><?= hulk_escape($row['access_code']) ?></strong>
                                <form action="/hulk-reseller-admin/action.php" method="post">
                                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                                    <input type="hidden" name="action" value="set_code">
                                    <input type="hidden" name="reseller_id" value="<?= $id ?>">
                                    <input name="access_code" maxlength="64" value="<?= hulk_escape($row['access_code']) ?>" required>
                                    <button type="submit">حفظ الكود</button>
                                </form>
                                <form action="/hulk-reseller-admin/action.php" method="post">
                                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                                    <input type="hidden" name="action" value="rotate_code">
                                    <input type="hidden" name="reseller_id" value="<?= $id ?>">
                                    <button type="submit">تدوير</button>
                                </form>
                            </td>
                            <td>
                                <form action="/hulk-reseller-admin/action.php" method="post">
                                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                                    <input type="hidden" name="action" value="set_status">
                                    <input type="hidden" name="reseller_id" value="<?= $id ?>">
                                    <input type="hidden" name="status" value="<?= $row['status'] === 'active' ? 'inactive' : 'active' ?>">
                                    <button type="submit"><?= $row['status'] === 'active' ? 'إيقاف' : 'تفعيل' ?></button>
                                </form>
                                <form action="/hulk-reseller-admin/action.php" method="post">
                                    <input type="hidden" name="csrf_token" value="<?= hulk_escape(hulk_csrf_token()) ?>">
                                    <input type="hidden" name="action" value="reset_password">
                                    <input type="hidden" name="reseller_id" value="<?= $id ?>">
                                    <input name="password" type="password" minlength="10" maxlength="256" placeholder="كلمة جديدة" required>
                                    <input name="confirm_password" type="password" minlength="10" maxlength="256" placeholder="التأكيد" required>
                                    <button type="submit">تعيين</button>
                                </form>
                            </td>
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
