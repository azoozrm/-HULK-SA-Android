<?php

declare(strict_types=1);

function cc_page_start(array $resolved, array $admin): void
{
    $activeKey = (string) $resolved['key'];
    $module = $resolved['module'];
    $modules = cc_modules();
    $groups = cc_navigation_groups();
    ?>
    <!doctype html>
    <html lang="ar" dir="rtl">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="robots" content="noindex,nofollow">
        <meta name="color-scheme" content="dark">
        <meta name="theme-color" content="#07090b">
        <title><?= cc_e((string) $module['title']) ?> — HULK SA Control Center</title>
        <link rel="icon" href="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" type="image/svg+xml">
        <link rel="stylesheet" href="<?= cc_e(cc_asset_url('app.css?v=5.0.2')) ?>">
    </head>
    <body>
    <a class="skip-link" href="#main-content">انتقل إلى المحتوى</a>
    <div class="app-shell">
        <div class="nav-backdrop" data-nav-backdrop hidden></div>
        <aside class="sidebar" id="primary-sidebar" aria-label="التنقل الرئيسي" data-sidebar>
            <div class="sidebar__top">
                <a class="brand" href="<?= cc_e(cc_url()) ?>" aria-label="HULK SA Control Center — الرئيسية">
                    <span class="brand__mark"><img src="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" alt=""></span>
                    <span class="brand__copy"><strong>HULK SA</strong><small>CONTROL CENTER</small></span>
                </a>
                <button class="icon-button sidebar__close" type="button" data-nav-close aria-label="إغلاق قائمة التنقل">
                    <?= cc_icon('close') ?>
                </button>
            </div>

            <nav class="navigation">
                <?php foreach ($groups as $groupKey => $groupLabel): ?>
                    <section class="navigation__section" aria-labelledby="nav-group-<?= cc_e($groupKey) ?>">
                    <h2 class="navigation__group-title" id="nav-group-<?= cc_e($groupKey) ?>"><?= cc_e($groupLabel) ?></h2>
                    <div class="navigation__group">
                        <?php foreach ($modules as $key => $item): ?>
                            <?php if ($item['group'] !== $groupKey) { continue; } ?>
                            <a class="navigation__link<?= $activeKey === $key ? ' is-active' : '' ?>"
                               href="<?= cc_e(cc_url($key)) ?>"
                               title="<?= cc_e((string) $item['label']) ?>"
                               <?= $activeKey === $key ? 'aria-current="page"' : '' ?>>
                                <span class="navigation__icon"><?= cc_icon((string) $item['icon']) ?></span>
                                <span class="navigation__text"><?= cc_e((string) $item['label']) ?></span>
                            </a>
                        <?php endforeach; ?>
                    </div>
                    </section>
                <?php endforeach; ?>
            </nav>

            <div class="sidebar__footer">
                <div class="phase-card">
                    <span class="phase-card__icon"><?= cc_icon('shield') ?></span>
                    <span><strong>الإدارة الموحّدة</strong></span>
                </div>
            </div>
        </aside>

        <div class="workspace" data-workspace>
            <header class="topbar">
                <div class="topbar__start">
                    <button class="icon-button nav-trigger" type="button" data-nav-open aria-controls="primary-sidebar" aria-expanded="false" aria-label="فتح قائمة التنقل">
                        <?= cc_icon('menu') ?>
                    </button>
                    <div class="breadcrumb" aria-label="مسار الصفحة">
                        <span>HULK SA</span><?= cc_icon('chevron') ?><strong><?= cc_e((string) $module['label']) ?></strong>
                    </div>
                </div>
                <div class="topbar__actions">
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
            </header>

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
    <script src="<?= cc_e(cc_asset_url('app.js?v=5.0.0')) ?>" defer></script>
    </body>
    </html>
    <?php
}
