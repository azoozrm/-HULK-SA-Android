<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';

try {
    ops_start_admin_session();
    if (ops_admin() !== null) {
        ops_redirect('index.php');
    }
} catch (Throwable $exception) {
    http_response_code(503);
    exit('تعذر تشغيل لوحة الإدارة بأمان. تحقق من إعداد HTTPS وملف config.php.');
}

$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    ops_require_csrf();
    $now = time();
    $sessionLockedUntil = (int) ($_SESSION['login_locked_until'] ?? 0);
    if ($sessionLockedUntil > $now) {
        $error = 'تم إيقاف المحاولات مؤقتًا. حاول بعد قليل.';
    } else {
        $username = trim((string) ($_POST['username'] ?? ''));
        $password = (string) ($_POST['password'] ?? '');
        try {
            $db = ops_db();
            $statement = $db->prepare('SELECT * FROM app_admin_users WHERE username = :username LIMIT 1');
            $statement->execute(['username' => $username]);
            $user = $statement->fetch();
            $dummyHash = password_hash('HULK-operations-invalid-password', PASSWORD_DEFAULT);
            $hash = is_array($user) ? (string) $user['password_hash'] : $dummyHash;
            $passwordMatches = password_verify($password, $hash);
            $lockedUntil = is_array($user) && $user['locked_until']
                ? strtotime((string) $user['locked_until'])
                : 0;
            $valid = is_array($user)
                && (bool) $user['enabled']
                && $lockedUntil <= $now
                && $passwordMatches;

            if ($valid) {
                $update = $db->prepare(
                    'UPDATE app_admin_users SET failed_attempts = 0, locked_until = NULL, last_login_at = NOW() '
                    . 'WHERE id = :id'
                );
                $update->execute(['id' => $user['id']]);
                session_regenerate_id(true);
                $_SESSION['admin_user_id'] = (int) $user['id'];
                $_SESSION['admin_username'] = (string) $user['username'];
                $_SESSION['login_attempts'] = 0;
                unset($_SESSION['login_locked_until']);
                ops_redirect('index.php');
            }

            $app = ops_load_config()['app'];
            $maxAttempts = max(3, (int) ($app['login_max_attempts'] ?? 5));
            $lockSeconds = max(60, (int) ($app['login_lock_seconds'] ?? 900));
            $sessionAttempts = ((int) ($_SESSION['login_attempts'] ?? 0)) + 1;
            $_SESSION['login_attempts'] = $sessionAttempts;
            if ($sessionAttempts >= $maxAttempts) {
                $_SESSION['login_locked_until'] = $now + $lockSeconds;
            }

            if (is_array($user)) {
                $attempts = ((int) $user['failed_attempts']) + 1;
                $lockUntilSql = $attempts >= $maxAttempts
                    ? date('Y-m-d H:i:s', $now + $lockSeconds)
                    : null;
                $update = $db->prepare(
                    'UPDATE app_admin_users SET failed_attempts = :attempts, locked_until = :locked_until '
                    . 'WHERE id = :id'
                );
                $update->execute([
                    'attempts' => $attempts,
                    'locked_until' => $lockUntilSql,
                    'id' => $user['id'],
                ]);
            }
            $error = 'بيانات الدخول غير صحيحة أو الحساب موقوف مؤقتًا.';
        } catch (Throwable $exception) {
            error_log('HULK Operations login failure: ' . $exception->getMessage());
            $error = 'تعذر تسجيل الدخول حاليًا.';
        }
    }
}
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex,nofollow">
    <title>تسجيل الدخول — HULK Operations</title>
    <link rel="stylesheet" href="../assets/app.css">
</head>
<body>
<main class="login-shell">
    <section class="card login-card">
        <div class="brand">
            <div class="brand-mark">H</div>
            <div><strong>HULK SA</strong><small>Operations Center</small></div>
        </div>
        <h1>تسجيل دخول المسؤول</h1>
        <?php if ($error !== null): ?><div class="flash error"><?= ops_e($error) ?></div><?php endif; ?>
        <form method="post" autocomplete="on">
            <input type="hidden" name="csrf_token" value="<?= ops_e(ops_csrf_token()) ?>">
            <div class="field">
                <label for="username">اسم المستخدم</label>
                <input id="username" name="username" maxlength="64" required autocomplete="username">
            </div>
            <div class="field">
                <label for="password">كلمة المرور</label>
                <input id="password" name="password" type="password" required autocomplete="current-password">
            </div>
            <button class="button" type="submit">دخول</button>
        </form>
    </section>
</main>
</body>
</html>
