<?php

declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/views/components.php';

try {
    cc_start_admin_session();
    if (cc_admin() !== null) {
        cc_redirect(cc_url());
    }
} catch (Throwable $exception) {
    http_response_code(503);
    exit('تعذر تشغيل مركز التحكم بأمان. تحقق من إعداد HTTPS وملف الإعدادات.');
}

$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    cc_require_csrf();
    $now = time();
    $sessionLockedUntil = (int) ($_SESSION['login_locked_until'] ?? 0);
    if ($sessionLockedUntil > $now) {
        $error = 'تم إيقاف المحاولات مؤقتًا. حاول بعد قليل.';
    } else {
        $username = trim((string) ($_POST['username'] ?? ''));
        $password = (string) ($_POST['password'] ?? '');

        try {
            $db = cc_db('control');
            $db->beginTransaction();
            $statement = $db->prepare(
                'SELECT * FROM app_admin_users WHERE username = :username LIMIT 1 FOR UPDATE'
            );
            $statement->execute(['username' => $username]);
            $user = $statement->fetch();

            if (cc_login_record_accepts_password($user, $password, $now)) {
                $update = $db->prepare(
                    'UPDATE app_admin_users SET failed_attempts = 0, locked_until = NULL, last_login_at = NOW() '
                    . 'WHERE id = :id'
                );
                $update->execute(['id' => $user['id']]);
                $db->commit();
                session_regenerate_id(true);
                $_SESSION['admin_user_id'] = (int) $user['id'];
                $_SESSION['admin_username'] = (string) $user['username'];
                $_SESSION['login_attempts'] = 0;
                unset($_SESSION['login_locked_until']);
                cc_redirect(cc_url());
            }

            $app = cc_load_config()['app'];
            $attemptState = cc_next_login_attempt_state(
                (int) ($_SESSION['login_attempts'] ?? 0),
                is_array($user) ? (int) $user['failed_attempts'] : 0,
                (int) ($app['login_max_attempts'] ?? 5),
                (int) ($app['login_lock_seconds'] ?? 900),
                $now
            );
            $_SESSION['login_attempts'] = $attemptState['session_attempts'];
            if ($attemptState['session_locked_until'] > 0) {
                $_SESSION['login_locked_until'] = $attemptState['session_locked_until'];
            }

            if (is_array($user)) {
                $update = $db->prepare(
                    'UPDATE app_admin_users SET failed_attempts = :attempts, locked_until = :locked_until '
                    . 'WHERE id = :id'
                );
                $update->execute([
                    'attempts' => $attemptState['database_attempts'],
                    'locked_until' => $attemptState['database_locked_until'],
                    'id' => $user['id'],
                ]);
            }
            $db->commit();
            $error = 'بيانات الدخول غير صحيحة أو الحساب موقوف مؤقتًا.';
        } catch (Throwable $exception) {
            if (isset($db) && $db instanceof PDO && $db->inTransaction()) {
                $db->rollBack();
            }
            error_log('HULK Control Center login failure.');
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
    <meta name="color-scheme" content="dark">
    <meta name="theme-color" content="#07090b">
    <title>تسجيل الدخول — HULK SA Control Center</title>
    <link rel="icon" href="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" type="image/svg+xml">
    <link rel="stylesheet" href="<?= cc_e(cc_asset_url('app.css?v=5.0.2')) ?>">
</head>
<body class="login-page">
<main class="login-layout">
    <section class="login-story" aria-label="هوية مركز التحكم">
        <a class="brand brand--large" href="<?= cc_e(cc_url()) ?>">
            <span class="brand__mark"><img src="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" alt=""></span>
            <span class="brand__copy"><strong>HULK SA</strong><small>CONTROL CENTER</small></span>
        </a>
        <div class="login-story__copy">
            <span class="eyebrow">إدارة موحّدة. قرار أسرع.</span>
            <h1>كل ما يتعلق بـ HULK SA في مركز احترافي واحد.</h1>
            <p>أساس إنتاجي آمن ومصمم للعربية، يجمع الرؤية التشغيلية بدون المساس بسلطات الأنظمة الحالية.</p>
        </div>
        <div class="login-story__glow" aria-hidden="true"><img src="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" alt=""></div>
    </section>

    <section class="login-panel">
        <div class="login-card">
            <header>
                <span class="login-card__mark"><img src="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" alt=""></span>
                <span class="eyebrow">دخول المالك</span>
                <h2>مرحبًا بعودتك</h2>
                <p>استخدم حساب مسؤول HULK Operations نفسه.</p>
            </header>
            <?php if ($error !== null): ?><div class="alert alert--error" role="alert"><?= cc_e($error) ?></div><?php endif; ?>
            <form method="post" autocomplete="on" class="form-stack">
                <input type="hidden" name="csrf_token" value="<?= cc_e(cc_csrf_token()) ?>">
                <label class="form-field" for="username">
                    <span>اسم المستخدم</span>
                    <input id="username" name="username" maxlength="64" required autocomplete="username" autofocus>
                </label>
                <label class="form-field" for="password">
                    <span>كلمة المرور</span>
                    <input id="password" name="password" type="password" required autocomplete="current-password">
                </label>
                <button class="button button--primary button--full" type="submit">دخول إلى مركز التحكم<?= cc_icon('arrow-left') ?></button>
            </form>
            <div class="security-note"><?= cc_icon('shield') ?><span>جلسة إدارية محمية ومحدودة بمسار مركز التحكم.</span></div>
        </div>
    </section>
</main>
</body>
</html>
