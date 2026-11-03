<?php

declare(strict_types=1);

function cc_page_start(array $resolved, array $admin): void
{
    $activeKey = (string) $resolved['key'];
    $module = $resolved['module'];
    $mainNavigation = cc_main_navigation();
    $activeMain = cc_active_main_navigation($activeKey);
    $contextNavigation = cc_context_navigation($activeKey);
    ?>
    <!doctype html>
    <html lang="ar" dir="rtl">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="robots" content="noindex,nofollow">
        <meta name="color-scheme" content="dark">
        <meta name="theme-color" content="#030402">
        <title><?= cc_e((string) $module['title']) ?> — HULK SA</title>
        <link rel="icon" href="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" type="image/svg+xml">
        <link rel="stylesheet" href="<?= cc_e(cc_asset_url('app.css?v=371.1')) ?>">
    </head>
    <body>
    <a class="skip-link" href="#main-content">انتقل الى المحتوى</a>
    <div class="app-shell">
        <header class="app-header">
            <div class="app-header__bar">
                <a class="brand" href="<?= cc_e(cc_url('dashboard')) ?>" aria-label="HULK SA — الرئيسية">
                    <span class="brand__mark"><img src="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" alt=""></span>
                    <span class="brand__copy"><strong>HULK SA</strong><small>لوحة التحكم</small></span>
                </a>
                <div class="app-header__actions">
                    <span class="owner-name"><?= cc_e((string) $admin['username']) ?></span>
                    <form action="<?= cc_e(cc_base_path() . '/logout.php') ?>" method="post">
                        <input type="hidden" name="csrf_token" value="<?= cc_e(cc_csrf_token()) ?>">
                        <button class="button button--quiet logout-button" type="submit">تسجيل الخروج</button>
                    </form>
                </div>
            </div>
            <details class="primary-navigation" data-primary-navigation open>
                <summary><?= cc_icon('menu') ?><span>الاقسام</span><strong><?= cc_e((string) ($mainNavigation[$activeMain]['label'] ?? 'الرئيسية')) ?></strong><?= cc_icon('chevron-down') ?></summary>
                <nav class="primary-tabs" aria-label="التنقل الرئيسي">
                    <div class="primary-tabs__track" data-primary-tabs>
                        <?php foreach ($mainNavigation as $key => $item): ?>
                            <a class="primary-tab<?= $activeMain === $key ? ' is-active' : '' ?>"
                               href="<?= cc_e(cc_url($key)) ?>" <?= $activeMain === $key ? 'aria-current="page"' : '' ?>>
                                <?= cc_icon((string) $item['icon']) ?><span><?= cc_e((string) $item['label']) ?></span>
                            </a>
                        <?php endforeach; ?>
                    </div>
                </nav>
            </details>
        </header>
        <div class="workspace">
            <main class="main-content" id="main-content" tabindex="-1">
                <?php if ($resolved['valid'] && $contextNavigation !== []): ?>
                    <?php cc_subnav('اقسام ' . (string) ($mainNavigation[$activeMain]['label'] ?? ''), $contextNavigation, $activeKey === 'resellers' ? 'hosts' : $activeKey); ?>
                <?php endif; ?>
                <header class="page-header">
                    <h1><?= cc_e((string) $module['title']) ?></h1>
                    <p><?= cc_e((string) $module['description']) ?></p>
                </header>
    <?php
}

function cc_page_end(): void
{
    ?>
            </main>
            <footer class="app-footer"><span>HULK SA</span><span>لوحة التحكم</span></footer>
        </div>
    </div>
    <script src="<?= cc_e(cc_asset_url('app.js?v=371.1')) ?>" defer></script>
    </body>
    </html>
    <?php
}
