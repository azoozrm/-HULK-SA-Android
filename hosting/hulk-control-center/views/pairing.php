<?php
declare(strict_types=1);

require_once __DIR__ . '/pairing-icons.php';

function cc_pairing_page_headers(): void
{
    header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
    header('Pragma: no-cache');
    header('Referrer-Policy: no-referrer');
    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: DENY');
    header("Content-Security-Policy: default-src 'none'; style-src 'self'; font-src 'self'; script-src 'self'; img-src 'self' data:; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
    header('Permissions-Policy: camera=(), geolocation=(), microphone=(), payment=(), usb=()');
    if (cc_is_https_request()) {
        header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
    }
}

// Display projection only; raw exception identities and controller state stay intact.
function cc_pairing_display_copy(string $text): string
{
    $text = strtr($text, ['أ' => 'ا', 'إ' => 'ا', 'آ' => 'ا',
        '٠' => '0', '١' => '1', '٢' => '2', '٣' => '3', '٤' => '4',
        '٥' => '5', '٦' => '6', '٧' => '7', '٨' => '8', '٩' => '9']);
    $text = preg_replace('/[.،,؟!?…۔\s]+$/u', '', $text) ?? $text;
    $text = preg_replace('/\s*([،,؟])\s*/u', ' $1 ', $text) ?? $text;
    // Older application messages used an interior sentence stop; retain domains/decimals.
    $text = preg_replace('/\.\s+(?=[\p{Arabic}])/u', ' ، ', $text) ?? $text;
    return trim($text);
}

function cc_pairing_render_page(array $state): void
{
    $step = (string) ($state['step'] ?? 'code');
    $notice = is_array($state['notice'] ?? null) ? $state['notice'] : null;
    $request = is_array($state['request'] ?? null) ? $state['request'] : null;
    $csrf = (string) ($state['csrf'] ?? '');
    $requestId = is_string($state['requestId'] ?? null) ? $state['requestId'] : '';
    $formToken = is_string($state['formToken'] ?? null) ? $state['formToken'] : '';
    $maskedAccessCode = is_string($state['maskedAccessCode'] ?? null) ? $state['maskedAccessCode'] : null;
    $confirm = $step === 'confirm' && $request !== null && $requestId !== '' && $formToken !== '';
    $terminal = in_array($step, ['approved', 'denied', 'disabled', 'insecure', 'unavailable'], true);
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex,nofollow">
    <meta name="color-scheme" content="dark">
    <meta name="theme-color" content="#080a0d">
    <title>تفعيل التلفزيون — HULK SA</title>
    <link rel="icon" href="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" type="image/svg+xml">
    <link rel="stylesheet" href="<?= cc_e(cc_asset_url('pairing.css?v=r08')) ?>">
    <script src="<?= cc_e(cc_asset_url('pairing.js?v=2')) ?>" defer></script>
</head>
<body class="pairing-page" data-pairing-release="R08">
<div class="pairing-shell">
    <header class="pairing-site-header">
        <a class="pairing-site-logo" href="/" aria-label="HULK SA ، الرئيسية"><img src="<?= cc_e(cc_asset_url('pairing-site-logo.webp')) ?>" alt="HULK SA" width="54" height="54"></a>
        <nav class="pairing-nav" aria-label="روابط الموقع">
            <a href="/">الرئيسية</a><a href="/content/">المكتبة</a><a href="/hulk-app/">التطبيقات</a><a href="/subscription/">بيانات الاشتراك</a>
        </nav>
        <a class="pairing-site-back" href="/">العودة للموقع<span aria-hidden="true">←</span></a>
    </header>
    <main class="pairing-layout">
        <section class="pairing-panel" aria-labelledby="pairing-title">
            <?php if (!$terminal): ?>
            <header class="pairing-header">
                <p class="pairing-step"><b dir="ltr"><?= $confirm ? '2' : '1' ?></b><span><?= $confirm ? 'بيانات الاشتراك' : 'رمز الربط' ?></span></p>
                <h1 id="pairing-title">تفعيل التلفزيون</h1>
                <p class="pairing-sub"><?= $confirm ? 'ادخل بيانات اشتراكك لتسجيل الدخول على هذا التلفزيون' : 'ادخل رمز الربط الظاهر على شاشة التلفزيون للمتابعة' ?></p>
            </header>
            <?php endif; ?>

            <?php if ($notice !== null): ?>
            <div class="pairing-notice" role="alert">
                <span><?= cc_e(cc_pairing_display_copy((string) ($notice['message'] ?? ''))) ?></span>
                <span aria-hidden="true"><?= cc_pairing_icon('error_outline') ?></span>
            </div>
            <?php endif; ?>

            <?php if ($terminal): ?>
            <?php
                [$title, $body, $icon] = match ($step) {
                    'approved' => ['تمت الموافقة على الطلب', 'ارجع الى التلفزيون لمتابعة تسجيل الدخول', 'check_circle'],
                    'denied' => ['تم رفض الطلب', 'لم يتم تفعيل التلفزيون ، يمكنك بدء طلب جديد عند الحاجة', 'close'],
                    'disabled' => ['الصفحة غير متاحة', 'لا يمكن استخدام تفعيل التلفزيون حاليا', 'error_outline'],
                    'insecure' => ['اتصال غير امن', 'افتح الصفحة عبر اتصال HTTPS لحماية بياناتك', 'lock'],
                    default => ['الصفحة غير متاحة مؤقتا', 'حاول مرة اخرى بعد قليل', 'error_outline'],
                };
            ?>
            <div class="pairing-done" role="status">
                <span class="pairing-done__icon" aria-hidden="true"><?= cc_pairing_icon($icon) ?></span>
                <h1 id="pairing-title"><?= cc_e($title) ?></h1>
                <p><?= cc_e($body) ?></p>
                <?php if (in_array($step, ['approved', 'denied'], true)): ?>
                <a class="pairing-button pairing-button--secondary" href="/pair/">بدء طلب جديد<span aria-hidden="true"><?= cc_pairing_icon('refresh') ?></span></a>
                <?php endif; ?>
            </div>
            <?php elseif ($confirm): ?>
            <div class="pairing-tv">
                <span class="pairing-tv__icon" aria-hidden="true"><?= cc_pairing_icon('tv') ?></span>
                <div><strong dir="auto"><?= cc_e(trim((string) $request['manufacturer'] . ' ' . (string) $request['model'])) ?></strong>
                    <p>رمز الربط <bdi class="pairing-code" dir="ltr"><?= cc_e((string) $request['userCode']) ?></bdi></p>
                </div>
            </div>
            <?php if ($maskedAccessCode !== null): ?>
            <p class="pairing-masked">كود الدخول السابق <bdi dir="ltr"><?= cc_e($maskedAccessCode) ?></bdi></p>
            <?php endif; ?>
            <form method="post" class="pairing-form" autocomplete="off">
                <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>">
                <input type="hidden" name="request_id" value="<?= cc_e($requestId) ?>">
                <input type="hidden" name="form_token" value="<?= cc_e($formToken) ?>">
                <div class="pairing-field">
                    <label for="pairing-access-code">كود الدخول</label>
                    <div class="pairing-input-wrap"><span aria-hidden="true"><?= cc_pairing_icon('key') ?></span><input id="pairing-access-code" name="access_code" dir="ltr" maxlength="64" required autocomplete="off" spellcheck="false" placeholder="كود الدخول"></div>
                </div>
                <div class="pairing-field">
                    <label for="pairing-username">اسم المستخدم</label>
                    <div class="pairing-input-wrap"><span aria-hidden="true"><?= cc_pairing_icon('person') ?></span><input id="pairing-username" name="username" dir="ltr" maxlength="255" required autocomplete="username" spellcheck="false" placeholder="اسم المستخدم"></div>
                </div>
                <div class="pairing-field">
                    <label for="pairing-password">كلمة المرور</label>
                    <div class="pairing-input-wrap pairing-input-wrap--password"><span aria-hidden="true"><?= cc_pairing_icon('lock') ?></span><input id="pairing-password" name="password" dir="ltr" type="password" maxlength="255" required autocomplete="current-password" placeholder="كلمة المرور"><label class="pairing-toggle" for="pairing-show-password"><input id="pairing-show-password" type="checkbox"><span>اظهار</span><span class="pairing-sr-only">كلمة المرور</span></label></div>
                </div>
                <button class="pairing-button pairing-button--primary" type="submit" name="action" value="approve">تفعيل التلفزيون<span aria-hidden="true"><?= cc_pairing_icon('tv') ?></span></button>
            </form>
            <p class="pairing-hint">بعد الموافقة ، تابع تسجيل الدخول على التلفزيون</p>
            <div class="pairing-reject-row"><p>ليس هذا تلفزيونك ؟</p><div class="pairing-secondary-actions">
                <form method="post" autocomplete="off">
                    <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>">
                    <input type="hidden" name="request_id" value="<?= cc_e($requestId) ?>">
                    <input type="hidden" name="form_token" value="<?= cc_e($formToken) ?>">
                    <button class="pairing-link-button" type="submit" name="action" value="deny">رفض الطلب</button>
                </form>
                <form method="post" autocomplete="off">
                    <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>">
                    <button class="pairing-link-button" type="submit" name="action" value="change">تغيير الرمز</button>
                </form>
            </div></div>
            <?php else: ?>
            <form method="post" class="pairing-form" autocomplete="off">
                <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>">
                <div class="pairing-field">
                    <label for="pairing-code">رمز الربط الظاهر على التلفزيون</label>
                    <div class="pairing-input-wrap"><span aria-hidden="true"><?= cc_pairing_icon('tv') ?></span><input id="pairing-code" name="code" dir="ltr" maxlength="16" required autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="HULKTV-XXXXXX"></div>
                </div>
                <button class="pairing-button pairing-button--primary" type="submit" name="action" value="lookup">متابعة<span aria-hidden="true"><?= cc_pairing_icon('arrow_back') ?></span></button>
            </form>
            <?php endif; ?>
        </section>
        <aside class="pairing-story" aria-label="طريقة التفعيل">
            <div class="pairing-story-tv" aria-hidden="true"><span><?= cc_pairing_icon('tv') ?></span><strong dir="ltr">HULK</strong></div>
            <p class="pairing-story-kicker">سجل من المتصفح ، وشاهد على التلفزيون</p>
            <h2>دخول اسهل<br>على <em>شاشتك</em></h2>
            <p>ادخل بيانات اشتراكك هنا ، ثم تابع تسجيل الدخول على التلفزيون</p>
            <ol class="pairing-story-steps"><li><b dir="ltr">1</b>امسح الرمز او ادخله</li><li><b dir="ltr">2</b>ادخل بيانات اشتراكك</li><li><b dir="ltr">3</b>تابع على التلفزيون</li></ol>
        </aside>
    </main>
    <footer class="pairing-footer"><span aria-hidden="true"><?= cc_pairing_icon('tv') ?></span><span>تسجيل الدخول الى <bdi dir="ltr">HULK SA</bdi> على التلفزيون</span></footer>
</div>
</body>
</html>
<?php
}
