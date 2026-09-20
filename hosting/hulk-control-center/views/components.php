<?php

declare(strict_types=1);

function cc_initial(string $value): string
{
    if (function_exists('mb_substr') && function_exists('mb_strtoupper')) {
        return mb_strtoupper(mb_substr($value, 0, 1, 'UTF-8'), 'UTF-8');
    }

    return strtoupper(substr($value, 0, 1));
}

function cc_icon(string $name, string $class = ''): string
{
    $content = match ($name) {
        'dashboard' => '<rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/>',
        'users' => '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
        'clock' => '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
        'device' => '<rect x="6" y="2" width="12" height="20" rx="2"/><path d="M10 18h4"/>',
        'briefcase' => '<rect x="3" y="7" width="18" height="13" rx="2"/><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 12h18M10 12v2h4v-2"/>',
        'key' => '<circle cx="8" cy="15" r="4"/><path d="m11 12 9-9M17 6l3 3M14 9l3 3"/>',
        'server' => '<rect x="3" y="4" width="18" height="6" rx="2"/><rect x="3" y="14" width="18" height="6" rx="2"/><path d="M7 7h.01M7 17h.01"/>',
        'package' => '<path d="m12 2 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5M3 17l9 5 9-5"/>',
        'pulse' => '<path d="M3 12h4l2-6 4 12 2-6h6"/>',
        'bell' => '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
        'toggle' => '<rect x="2" y="5" width="20" height="14" rx="7"/><circle cx="15" cy="12" r="3"/>',
        'trend' => '<path d="m3 17 6-6 4 4 8-9M15 6h6v6"/>',
        'heart' => '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z"/><path d="M7 12h3l1-2 2 4 1-2h3"/>',
        'diagnostic' => '<circle cx="12" cy="12" r="9"/><path d="M12 7v5M12 16h.01"/>',
        'chart' => '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
        'audit' => '<rect x="5" y="4" width="14" height="18" rx="2"/><path d="M9 4V2h6v2M9 10h6M9 14h6M9 18h4"/>',
        'settings' => '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1 1.55V21h-4v-.08A1.7 1.7 0 0 0 9 19.37a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.63 15a1.7 1.7 0 0 0-1.55-1H3v-4h.08A1.7 1.7 0 0 0 4.63 9a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.63a1.7 1.7 0 0 0 1-1.55V3h4v.08A1.7 1.7 0 0 0 15 4.63a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.37 9a1.7 1.7 0 0 0 1.55 1H21v4h-.08A1.7 1.7 0 0 0 19.4 15Z"/>',
        'menu' => '<path d="M4 6h16M4 12h16M4 18h16"/>',
        'close' => '<path d="m6 6 12 12M18 6 6 18"/>',
        'chevron' => '<path d="m9 18 6-6-6-6"/>',
        'search' => '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
        'filter' => '<path d="M4 5h16l-6 7v5l-4 2v-7L4 5Z"/>',
        'shield' => '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="m9 12 2 2 4-4"/>',
        'arrow-left' => '<path d="M19 12H5M11 18l-6-6 6-6"/>',
        default => '<circle cx="12" cy="12" r="9"/>',
    };

    return '<svg class="icon ' . cc_e($class) . '" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' . $content . '</svg>';
}

function cc_status_badge(string $label, string $tone = 'neutral'): void
{
    $allowed = ['neutral', 'success', 'warning', 'danger', 'info'];
    $tone = in_array($tone, $allowed, true) ? $tone : 'neutral';
    ?>
    <span class="status-badge status-badge--<?= cc_e($tone) ?>"><span class="status-dot"></span><?= cc_e($label) ?></span>
    <?php
}

function cc_alert(string $title, string $message, string $tone = 'info'): void
{
    $allowed = ['info', 'success', 'warning', 'danger'];
    $tone = in_array($tone, $allowed, true) ? $tone : 'info';
    ?>
    <div class="notice notice--<?= cc_e($tone) ?>" role="status">
        <span class="notice__icon"><?= cc_icon($tone === 'danger' ? 'diagnostic' : 'shield') ?></span>
        <span><strong><?= cc_e($title) ?></strong><small><?= cc_e($message) ?></small></span>
    </div>
    <?php
}

function cc_kpi_card(
    string $label,
    string $value,
    string $hint,
    string $icon,
    string $badge = 'لاحقًا',
    string $tone = 'neutral'
): void
{
    ?>
    <article class="kpi-card">
        <div class="kpi-card__head">
            <span class="kpi-card__icon"><?= cc_icon($icon) ?></span>
            <?php cc_status_badge($badge, $tone); ?>
        </div>
        <p class="kpi-card__label"><?= cc_e($label) ?></p>
        <p class="kpi-card__value"><?= cc_e($value) ?></p>
        <p class="kpi-card__hint"><?= cc_e($hint) ?></p>
    </article>
    <?php
}

function cc_unavailable_state(string $title, string $description, string $icon = 'shield'): void
{
    ?>
    <section class="state-panel state-panel--unavailable" role="status">
        <div class="state-panel__icon"><?= cc_icon($icon) ?></div>
        <div>
            <span class="eyebrow">حالة موثوقة</span>
            <h2><?= cc_e($title) ?></h2>
            <p><?= cc_e($description) ?></p>
        </div>
    </section>
    <?php
}

function cc_empty_state(string $title, string $description): void
{
    ?>
    <section class="state-panel state-panel--empty" role="status">
        <div class="state-panel__icon"><?= cc_icon('search') ?></div>
        <div><h2><?= cc_e($title) ?></h2><p><?= cc_e($description) ?></p></div>
    </section>
    <?php
}

function cc_error_state(string $title, string $description, string $retryUrl): void
{
    ?>
    <section class="state-panel state-panel--error" role="alert">
        <div class="state-panel__icon"><?= cc_icon('diagnostic') ?></div>
        <div>
            <h2><?= cc_e($title) ?></h2>
            <p><?= cc_e($description) ?></p>
            <a class="button button--secondary" href="<?= cc_e($retryUrl) ?>">إعادة المحاولة</a>
        </div>
    </section>
    <?php
}

function cc_skeleton(int $rows = 3): void
{
    $rows = max(1, min(8, $rows));
    ?>
    <div class="skeleton" aria-busy="true" aria-label="جارٍ التحميل">
        <?php for ($index = 0; $index < $rows; $index++): ?>
            <span class="skeleton__row"></span>
        <?php endfor; ?>
    </div>
    <?php
}

function cc_saved_filter_controls(string $module, array $allowedFields): void
{
    $safeModule = preg_match('/^[a-z0-9-]{1,40}$/', $module) ? $module : '';
    $safeFields = array_values(array_filter($allowedFields, static fn (mixed $field): bool =>
        is_string($field) && preg_match('/^[a-z_]{1,32}$/', $field) === 1
    ));
    if ($safeModule === '' || $safeFields === []) {
        return;
    }
    ?>
    <div class="saved-filters" data-saved-filter-controls>
        <label class="form-field saved-filters__name"><span>اسم العرض المحفوظ</span><input type="text" maxlength="40" data-saved-filter-name autocomplete="off" placeholder="مثال: أجهزة التلفاز"></label>
        <button class="button button--secondary" type="button" data-saved-filter-save>حفظ الفلاتر الآمنة</button>
        <label class="form-field saved-filters__select"><span>العروض المحفوظة</span><select data-saved-filter-select><option value="">اختر عرضًا محفوظًا</option></select></label>
        <button class="button button--quiet" type="button" data-saved-filter-apply>تطبيق العرض</button>
        <button class="button button--quiet" type="button" data-saved-filter-delete>حذف العرض</button>
        <p class="saved-filters__status" data-saved-filter-status role="status" aria-live="polite">لا تُحفظ حقول البحث الحر أو بيانات الدخول أو الهوست.</p>
    </div>
    <?php
}

function cc_toolbar(string $searchLabel, array $filters = []): void
{
    ?>
    <div class="data-toolbar" aria-label="أدوات البحث والتصفية">
        <label class="search-control">
            <span class="sr-only"><?= cc_e($searchLabel) ?></span>
            <?= cc_icon('search') ?>
            <input type="search" placeholder="<?= cc_e($searchLabel) ?>" disabled aria-describedby="phase-one-disabled">
        </label>
        <?php foreach ($filters as $filter): ?>
            <label class="filter-control">
                <span class="sr-only"><?= cc_e($filter) ?></span>
                <?= cc_icon('filter') ?>
                <select disabled aria-describedby="phase-one-disabled">
                    <option><?= cc_e($filter) ?></option>
                </select>
            </label>
        <?php endforeach; ?>
    </div>
    <?php
}

function cc_table_shell(array $columns): void
{
    ?>
    <div class="table-shell">
        <div class="table-scroll" tabindex="0" aria-label="جدول بيانات غير متصل">
            <table>
                <thead><tr>
                    <?php foreach ($columns as $column): ?><th scope="col"><?= cc_e($column) ?></th><?php endforeach; ?>
                </tr></thead>
                <tbody>
                <tr class="table-empty-row">
                    <td colspan="<?= count($columns) ?>">لا توجد بيانات متصلة بهذه الوحدة.</td>
                </tr>
                </tbody>
            </table>
        </div>
        <?php cc_pagination(); ?>
    </div>
    <?php
}

function cc_pagination(): void
{
    ?>
    <nav class="pagination" aria-label="ترقيم الصفحات">
        <p>الصفحة — من —</p>
        <div>
            <button class="icon-button" type="button" disabled aria-label="الصفحة السابقة"><?= cc_icon('chevron') ?></button>
            <button class="page-button" type="button" disabled aria-current="page">—</button>
            <button class="icon-button icon-button--flip" type="button" disabled aria-label="الصفحة التالية"><?= cc_icon('chevron') ?></button>
        </div>
    </nav>
    <?php
}
