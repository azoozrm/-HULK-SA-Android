<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل التحليلات', 'لم تُستبدل البيانات غير المتاحة بأصفار.', cc_url('analytics'));
    return;
}

$sessions = is_array($pageData['session_trend'] ?? null) ? $pageData['session_trend'] : [];
$versions = is_array($pageData['version_adoption_trend'] ?? null) ? $pageData['version_adoption_trend'] : [];
$current = is_array($pageData['current_adoption_distribution'] ?? null) ? $pageData['current_adoption_distribution'] : [];
$versionPartial = ($pageData['version_adoption_partial'] ?? false) === true;
$currentPartial = ($pageData['current_adoption_partial'] ?? false) === true;
$maximumSessions = max(1, ...array_map(static fn (array $row): int => (int) $row['session_count'], $sessions));
$maximumCurrent = max(1, ...array_map(static fn (array $row): int => (int) $row['device_count'], $current));
?>
<section class="module-stack phase7-module">
    <?php if ($versionPartial || $currentPartial): ?>
        <section class="notice notice--warning" role="status">بعض تحليلات الاعتماد جزئية بسبب حدود العرض المكتوبة؛ لا تُعرض الصفوف المحذوفة من الحد كقيم صفرية.</section>
    <?php endif; ?>
    <section class="panel metric-definition-panel">
        <header class="panel__header"><div><span class="eyebrow">تعريفات قابلة للتتبع</span><h2>حدود القياس</h2></div><?php cc_status_badge('Presence فقط', 'success'); ?></header>
        <p class="muted-copy">اتجاه الجلسات مبني على started_at. اتجاه الاعتماد يأخذ آخر جلسة لكل جهاز في اليوم. التوزيع الحالي يستخدم أحدث جهاز Presence خلال 24 ساعة. لا تُستخدم تنزيلات APK.</p>
    </section>

    <section class="dashboard-grid phase7-grid">
        <article class="panel panel--wide"><header class="panel__header"><div><span class="eyebrow">آخر <?= (int) ($pageData['window_days'] ?? 30) ?> يومًا</span><h2>اتجاه الجلسات</h2></div></header><?php if ($sessions === []): ?><?php cc_empty_state('لا توجد جلسات في النافذة', 'لا توجد حقائق started_at لعرض اتجاه حالي.'); ?><?php else: ?><div class="bar-chart" role="img" aria-label="اتجاه الجلسات حسب اليوم"><?php foreach ($sessions as $row): ?><div class="bar-chart__row"><time><?= cc_e($row['day']) ?></time><progress class="bar-chart__progress" max="<?= $maximumSessions ?>" value="<?= (int) $row['session_count'] ?>"><?= (int) $row['session_count'] ?></progress><strong><?= (int) $row['session_count'] ?></strong></div><?php endforeach; ?></div><?php endif; ?></article>
        <article class="panel"><header class="panel__header"><div><span class="eyebrow">آخر 24 ساعة · أعلى 25 إصدارًا<?= $currentPartial ? ' · جزئي' : '' ?></span><h2>التوزيع الحالي للإصدارات</h2></div></header><?php if ($current === []): ?><?php cc_empty_state('لا توجد أجهزة حديثة', 'لا توجد أجهزة Presence داخل نافذة التوزيع الحالية.'); ?><?php else: ?><div class="bar-chart bar-chart--compact"><?php foreach ($current as $row): ?><div class="bar-chart__row"><span><?= cc_e($row['app_version_name']) ?><small>Code <?= (int) $row['app_version_code'] ?></small></span><progress class="bar-chart__progress" max="<?= $maximumCurrent ?>" value="<?= (int) $row['device_count'] ?>"><?= (int) $row['device_count'] ?></progress><strong><?= (int) $row['device_count'] ?></strong></div><?php endforeach; ?></div><?php endif; ?></article>
    </section>

    <section class="panel">
        <header class="panel__header"><div><span class="eyebrow">آخر جهاز في اليوم · حتى 500 صف<?= $versionPartial ? ' · جزئي' : '' ?></span><h2>اتجاه اعتماد الإصدارات</h2><p class="muted-copy">كل صف يمثل أجهزة كان هذا آخر إصدار جلسة مرصودًا لها في ذلك اليوم؛ لا توجد استنتاجات من ملفات APK.</p></div></header>
        <?php if ($versions === []): ?><?php cc_empty_state('لا توجد بيانات اعتماد', 'لا توجد جلسات Presence ضمن نافذة التحليل.'); ?><?php else: ?><div class="table-shell"><div class="table-scroll" tabindex="0"><table class="phase7-table"><thead><tr><th>اليوم</th><th>الإصدار</th><th>Version code</th><th>الأجهزة</th></tr></thead><tbody><?php foreach ($versions as $row): ?><tr><td><?= cc_e($row['day']) ?></td><td><?= cc_e($row['app_version_name']) ?></td><td><?= (int) $row['app_version_code'] ?></td><td><?= (int) $row['device_count'] ?></td></tr><?php endforeach; ?></tbody></table></div></div><?php endif; ?>
    </section>
</section>
