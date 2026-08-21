<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';

try {
    ops_start_admin_session();
    $db = ops_db();
    if ((int) $db->query('SELECT COUNT(*) FROM app_admin_users')->fetchColumn() > 0) {
        ops_redirect('login.php');
    }
    $bootstrapToken = (string) (ops_load_config()['app']['bootstrap_token'] ?? '');
} catch (Throwable $exception) {
    http_response_code(503);
    exit('تعذر تشغيل الإعداد الأولي. راجع قاعدة البيانات وملف config.php.');
}

$error = null;
$created = false;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    ops_require_csrf();
    $providedToken = (string) ($_POST['bootstrap_token'] ?? '');
    $username = trim((string) ($_POST['username'] ?? ''));
    $password = (string) ($_POST['password'] ?? '');
    if ($bootstrapToken === '' || !hash_equals($bootstrapToken, $providedToken)) {
        $error = 'رمز الإعداد الأولي غير صحيح أو تم تعطيله.';
    } elseif (!preg_match('/^[A-Za-z0-9._-]{3,64}$/', $username)) {
        $error = 'اسم المستخدم يجب أن يكون من 3 إلى 64 حرفًا آمنًا.';
    } elseif (strlen($password) < 12) {
        $error = 'كلمة مرور المسؤول يجب ألا تقل عن 12 حرفًا.';
    } else {
        try {
            $statement = $db->prepare(
                'INSERT INTO app_admin_users (username, password_hash) VALUES (:username, :password_hash)'
            );
            $statement->execute([
                'username' => $username,
                'password_hash' => password_hash($password, PASSWORD_DEFAULT),
            ]);
            ops_audit($db, (int) $db->lastInsertId(), 'ADMIN_CREATED', ['username' => $username]);
            $created = true;
        } catch (Throwable $exception) {
            error_log('HULK Operations first admin setup failed: ' . $exception->getMessage());
            $error = 'تعذر إنشاء المسؤول.';
        }
    }
}
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex,nofollow"><title>إعداد المسؤول الأول</title>
    <link rel="stylesheet" href="../assets/app.css">
</head>
<body><main class="login-shell"><section class="card login-card">
    <div class="brand"><div class="brand-mark">H</div><div><strong>HULK SA</strong><small>إعداد آمن لمرة واحدة</small></div></div>
    <h1>إنشاء المسؤول الأول</h1>
    <?php if ($created): ?>
        <div class="flash success">تم إنشاء المسؤول. امسح bootstrap_token من config.php الآن ثم انتقل لتسجيل الدخول.</div>
        <a class="button" href="login.php">فتح تسجيل الدخول</a>
    <?php elseif ($bootstrapToken === ''): ?>
        <div class="flash error">الإعداد الأولي معطل. ضع bootstrap_token مؤقتًا في config.php.</div>
    <?php else: ?>
        <?php if ($error !== null): ?><div class="flash error"><?= ops_e($error) ?></div><?php endif; ?>
        <form method="post" autocomplete="off">
            <input type="hidden" name="csrf_token" value="<?= ops_e(ops_csrf_token()) ?>">
            <div class="field"><label>رمز الإعداد الأولي</label><input name="bootstrap_token" type="password" required></div>
            <div class="field"><label>اسم المستخدم</label><input name="username" maxlength="64" required></div>
            <div class="field"><label>كلمة المرور</label><input name="password" type="password" minlength="12" required></div>
            <button class="button" type="submit">إنشاء المسؤول</button>
        </form>
    <?php endif; ?>
</section></main></body></html>
