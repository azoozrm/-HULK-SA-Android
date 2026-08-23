<?php

declare(strict_types=1);

function ops_admin_page_start(string $title, string $section, array $admin): void
{
    $sections = [
        'dashboard' => 'لوحة التحكم',
        'releases' => 'التحديثات',
        'announcements' => 'الرسائل',
        'service' => 'حالة الخدمة',
        'features' => 'المميزات',
        'growth' => 'TV Growth',
        'audit' => 'سجل العمليات',
    ];
    ?>
    <!doctype html>
    <html lang="ar" dir="rtl">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="robots" content="noindex,nofollow">
        <title><?= ops_e($title) ?> — مركز عمليات HULK</title>
        <link rel="stylesheet" href="../assets/app.css?v=2.4.0">
    </head>
    <body>
    <div class="shell">
        <aside class="sidebar">
            <div class="brand">
                <div class="brand-mark">H</div>
                <div><strong>HULK SA</strong><small>مركز العمليات</small></div>
            </div>
            <nav class="nav" aria-label="التنقل الرئيسي">
                <?php foreach ($sections as $key => $label): ?>
                    <a class="<?= $section === $key ? 'active' : '' ?>" href="index.php?section=<?= ops_e($key) ?>">
                        <?= ops_e($label) ?>
                    </a>
                <?php endforeach; ?>
            </nav>
            <form class="logout" action="logout.php" method="post">
                <input type="hidden" name="csrf_token" value="<?= ops_e(ops_csrf_token()) ?>">
                <button class="button secondary" type="submit">تسجيل الخروج<span class="logout-user"> · <?= ops_e($admin['username']) ?></span></button>
            </form>
        </aside>
        <main class="content">
    <?php
}

function ops_admin_page_end(): void
{
    ?>
        </main>
    </div>
    <script src="../assets/app.js?v=2.4.0" defer></script>
    </body>
    </html>
    <?php
}

function ops_admin_flash(?array $flash): void
{
    if ($flash === null) {
        return;
    }
    $type = ($flash['type'] ?? '') === 'success' ? 'success' : 'error';
    ?>
    <div class="flash <?= $type ?>" role="status"><?= ops_e((string) ($flash['message'] ?? '')) ?></div>
    <?php
}
