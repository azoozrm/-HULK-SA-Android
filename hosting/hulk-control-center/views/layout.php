<?php

declare(strict_types=1);

function cc_page_start(array $resolved, array $admin): void
{
    $activeKey = (string) $resolved['key'];
    $module = $resolved['module'];
    $mainNavigation = cc_main_navigation();
    $activeMain = cc_active_main_navigation($activeKey) ?? 'dashboard';
    $contextNavigation = cc_context_navigation($activeKey);
    $label = (string) ($mainNavigation[$activeMain]['label'] ?? $module['title']);
    $groups = [
        ['label' => null, 'keys' => ['dashboard']],
        ['label' => 'المتابعة', 'keys' => ['live-users', 'sessions']],
        ['label' => 'إدارة الوصول', 'keys' => ['hosts']],
        ['label' => 'إدارة التطبيق', 'keys' => ['announcements', 'releases', 'service']],
    ];
    $renderSections = static function () use ($groups, $mainNavigation, $activeMain): void {
        foreach ($groups as $group): ?>
            <?php if ($group['label'] !== null): ?><span class="side-label"><?= cc_e($group['label']) ?></span><?php endif; ?>
            <?php foreach ($group['keys'] as $key): $item = $mainNavigation[$key]; ?>
                <a class="nav-btn" href="<?= cc_e(cc_url($key)) ?>" <?= $activeMain === $key ? 'aria-current="page"' : '' ?>>
                    <?= cc_icon((string) $item['icon']) ?><span><?= cc_e((string) $item['label']) ?></span>
                </a>
            <?php endforeach; ?>
        <?php endforeach;
    };
    $GLOBALS['cc_active_main_navigation'] = $activeMain;
    ?>
    <!doctype html>
    <html lang="ar" dir="rtl">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="robots" content="noindex,nofollow">
        <meta name="color-scheme" content="light">
        <meta name="theme-color" content="#f7f6f2">
        <title><?= cc_e((string) $module['title']) ?> — HULK SA</title>
        <link rel="icon" href="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" type="image/svg+xml">
        <link rel="stylesheet" href="<?= cc_e(cc_asset_url('app.css?v=371.2')) ?>">
        <link rel="stylesheet" href="<?= cc_e(cc_asset_url('reference.css?v=371.3')) ?>">
    </head>
    <body>
    <a class="skip-link" href="#main-content">انتقل الى المحتوى</a>
    <div class="shell">
        <aside class="sidebar">
            <a class="brand" href="<?= cc_e(cc_url('dashboard')) ?>" aria-label="HULK SA — الرئيسية">
                <img src="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" alt="">
                <div><b dir="ltr">HULK SA</b><small>لوحة التحكم</small></div>
            </a>
            <nav class="side-nav" aria-label="أقسام اللوحة">
                <?php $renderSections(); ?>
            </nav>
            <div class="side-foot">HULK SA · لوحة التحكم</div>
        </aside>
        <div class="content">
            <header class="top">
                <a class="top-brand" href="<?= cc_e(cc_url('dashboard')) ?>" aria-label="HULK SA — الرئيسية">
                    <img src="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" alt=""><b dir="ltr">HULK SA</b>
                </a>
                <div class="crumb"><span>إدارة التطبيق</span><span aria-hidden="true">‹</span><b><?= cc_e($label) ?></b></div>
                <div class="top-tools">
                    <details class="mobile-sections">
                        <summary><?= cc_icon('menu') ?>الأقسام</summary>
                        <nav aria-label="كل أقسام اللوحة"><?php $renderSections(); ?></nav>
                    </details>
                    <span class="top-owner"><?= cc_e((string) $admin['username']) ?></span>
                    <form action="<?= cc_e(cc_base_path() . '/logout.php') ?>" method="post">
                        <input type="hidden" name="csrf_token" value="<?= cc_e(cc_csrf_token()) ?>">
                        <button class="account" type="submit" aria-label="تسجيل الخروج"><span>تسجيل الخروج</span><?= cc_icon('user') ?></button>
                    </form>
                </div>
            </header>
            <main id="main-content" tabindex="-1">
                <header class="page-title">
                    <div><h1 tabindex="-1"><?= cc_e((string) $module['title']) ?></h1><p class="subtitle"><?= cc_e((string) $module['description']) ?></p></div>
                </header>
                <?php if ($resolved['valid'] && count($contextNavigation) > 1): ?>
                    <nav class="context-nav" aria-label="أدوات <?= cc_e($label) ?>">
                        <?php foreach ($contextNavigation as $key => $contextLabel): ?>
                            <a href="<?= cc_e(cc_url($key)) ?>"<?= $activeKey === $key ? ' class="is-active" aria-current="page"' : '' ?>><?= cc_e($contextLabel) ?></a>
                        <?php endforeach; ?>
                    </nav>
                <?php endif; ?>
    <?php
}

function cc_page_end(): void
{
    ?>
            </main>
        </div>
    </div>
    <nav class="bottom-nav" aria-label="أقسام الجوال">
        <?php $bottom = ['dashboard' => 'الرئيسية', 'live-users' => 'المتصلون', 'hosts' => 'الهوستات والأكواد', 'releases' => 'تحديث التطبيق', 'service' => 'الإدارة']; $nav = cc_main_navigation(); foreach ($bottom as $key => $text): ?>
            <a href="<?= cc_e(cc_url($key)) ?>" <?= ($GLOBALS['cc_active_main_navigation'] ?? 'dashboard') === $key ? 'aria-current="page"' : '' ?>>
                <?= cc_icon((string) ($nav[$key]['icon'] ?? 'dashboard')) ?><span><?= cc_e($text) ?></span>
            </a>
        <?php endforeach; ?>
    </nav>
    <script src="<?= cc_e(cc_asset_url('app.js?v=371.2')) ?>" defer></script>
    </body>
    </html>
    <?php
}
