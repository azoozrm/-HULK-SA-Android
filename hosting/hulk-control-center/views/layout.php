<?php

declare(strict_types=1);

function cc_page_start(array $resolved, array $admin): void
{
    $activeKey = (string) $resolved['key'];
    $module = $resolved['module'];
    $mainNavigation = cc_main_navigation();
    $activeMain = cc_active_main_navigation($activeKey);
    $secondaryGroups = cc_secondary_navigation_groups();
    ?>
    <!doctype html>
    <html lang="ar" dir="rtl">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="robots" content="noindex,nofollow">
        <meta name="color-scheme" content="dark">
        <meta name="theme-color" content="#030402">
        <title><?= cc_e((string) $module['title']) ?> — HULK SA Control Center</title>
        <link rel="icon" href="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" type="image/svg+xml">
        <link rel="stylesheet" href="<?= cc_e(cc_asset_url('app.css?v=6.2.0')) ?>">
    </head>
    <body>
    <a class="skip-link" href="#main-content">انتقل إلى المحتوى</a>
    <div class="app-shell">
        <header class="app-header">
            <div class="app-header__bar">
                <a class="brand" href="<?= cc_e(cc_url()) ?>" aria-label="HULK SA Control Center — الرئيسية">
                    <span class="brand__mark"><img src="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" alt=""></span>
                    <span class="brand__copy"><strong>HULK SA</strong><small>CONTROL CENTER</small></span>
                </a>
                <div class="app-header__actions">
                    <details class="settings-menu" data-settings-menu>
                        <summary class="icon-button settings-menu__trigger" aria-label="الإعدادات والخدمة" title="الإعدادات والخدمة">
                            <?= cc_icon('settings') ?>
                        </summary>
                        <div class="settings-menu__panel">
                            <header class="settings-menu__header">
                                <span class="settings-menu__header-icon"><?= cc_icon('settings') ?></span>
                                <span><strong>الإعدادات والخدمة</strong><small>الحالة والصيانة والدعم وإدارة المركز</small></span>
                            </header>
                            <div class="settings-menu__service-grid">
                                <a class="settings-menu__service" href="<?= cc_e(cc_url('service')) ?>">
                                    <span class="settings-menu__service-icon"><?= cc_icon('pulse') ?></span>
                                    <span><strong>حالة الخدمة والصيانة</strong><small>الحالة المنشورة ورسائل الصيانة</small></span>
                                </a>
                                <a class="settings-menu__service" href="<?= cc_e(cc_url('growth')) ?>">
                                    <span class="settings-menu__service-icon"><?= cc_icon('trend') ?></span>
                                    <span><strong>الدعم والتجديد</strong><small>قنوات الدعم وروابط التجديد</small></span>
                                </a>
                            </div>
                            <div class="settings-menu__groups">
                                <?php foreach ($secondaryGroups as $groupKey => $group): ?>
                                    <section class="settings-menu__group" aria-labelledby="settings-group-<?= cc_e($groupKey) ?>">
                                        <h2 class="settings-menu__group-title" id="settings-group-<?= cc_e($groupKey) ?>"><?= cc_e($group['label']) ?></h2>
                                        <div class="settings-menu__links">
                                            <?php foreach ($group['items'] as $key => $label): ?>
                                                <a href="<?= cc_e(cc_url($key)) ?>"><?= cc_e($label) ?></a>
                                            <?php endforeach; ?>
                                        </div>
                                    </section>
                                <?php endforeach; ?>
                            </div>
                            <footer class="settings-menu__footer">
                                <a href="<?= cc_e(cc_url('settings')) ?>">
                                    <span class="settings-menu__footer-icon"><?= cc_icon('settings') ?></span>
                                    <span><strong>إعدادات المركز</strong><small>حدود التشغيل والجهات المسؤولة</small></span>
                                </a>
                            </footer>
                        </div>
                    </details>
                    <div class="admin-menu">
                        <span class="admin-menu__avatar" aria-hidden="true"><?= cc_e(cc_initial((string) $admin['username'])) ?></span>
                        <span class="admin-menu__copy"><strong><?= cc_e((string) $admin['username']) ?></strong><small>مسؤول النظام</small></span>
                    </div>
                    <form action="<?= cc_e(cc_base_path() . '/logout.php') ?>" method="post">
                        <input type="hidden" name="csrf_token" value="<?= cc_e(cc_csrf_token()) ?>">
                        <button class="button button--quiet logout-button" type="submit" aria-label="تسجيل الخروج">
                            <span class="logout-button__icon"><?= cc_icon('arrow-left') ?></span>
                            <span class="logout-button__text">تسجيل الخروج</span>
                        </button>
                    </form>
                </div>
            </div>
            <nav class="primary-tabs" aria-label="التنقل الرئيسي">
                <div class="primary-tabs__track" data-primary-tabs>
                    <?php foreach ($mainNavigation as $key => $item): ?>
                        <a class="primary-tab<?= $activeMain === $key ? ' is-active' : '' ?>"
                           href="<?= cc_e(cc_url($key)) ?>"
                           title="<?= cc_e((string) $item['label']) ?>"
                           <?= $activeMain === $key ? 'aria-current="page"' : '' ?>>
                            <span class="primary-tab__icon"><?= cc_icon((string) $item['icon']) ?></span>
                            <span class="primary-tab__text"><?= cc_e((string) $item['label']) ?></span>
                        </a>
                    <?php endforeach; ?>
                </div>
            </nav>
        </header>

        <div class="workspace">
            <main class="main-content" id="main-content" tabindex="-1">
                <header class="page-header">
                    <div>
                        <h1><?= cc_e((string) $module['title']) ?></h1>
                        <p><?= cc_e((string) $module['description']) ?></p>
                    </div>
                </header>
    <?php
}

function cc_page_end(): void
{
    ?>
            </main>
            <footer class="app-footer"><span>HULK SA Control Center</span><span>واجهة إدارية عربية موحّدة</span></footer>
        </div>
    </div>
    <script src="<?= cc_e(cc_asset_url('app.js?v=6.1.0')) ?>" defer></script>
    </body>
    </html>
    <?php
}
