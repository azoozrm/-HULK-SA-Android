<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل أعطال التطبيق', 'لم تُعرض أصفار بديلة. تحقق من جاهزية مصدر بيانات الأعطال.', cc_url('diagnostics'));
    return;
}

$byCode = is_array($pageData['failures_by_code'] ?? null) ? $pageData['failures_by_code'] : [];
$byVersion = is_array($pageData['failures_by_version'] ?? null) ? $pageData['failures_by_version'] : [];
$byDevice = is_array($pageData['failures_by_device'] ?? null) ? $pageData['failures_by_device'] : [];
$byHost = is_array($pageData['failures_by_host'] ?? null) ? $pageData['failures_by_host'] : [];
$recent = is_array($pageData['recent_events'] ?? null) ? $pageData['recent_events'] : [];
$partial = is_array($pageData['partial'] ?? null) ? $pageData['partial'] : [];
$hasPartialAggregate = in_array(true, $partial, true);
$fingerprint = static fn (string $value): string => substr($value, 0, 12) . '…';
?>
<section class="module-stack phase7-module">
    <?php if ($hasPartialAggregate): ?>
        <section class="notice notice--warning" role="status">بعض التجميعات جزئية بسبب حدود العرض المكتوبة؛ إجمالي الأحداث يبقى مستمدًا من كل الحقائق داخل نافذة الاحتفاظ.</section>
    <?php endif; ?>
    <section class="kpi-grid phase7-kpi-grid">
        <?php cc_kpi_card('الأحداث المسجلة', (string) ((int) ($pageData['total_events'] ?? 0)), 'أعطال مصنفة مستلمة داخل مدة الاحتفاظ فقط.', 'diagnostic', (string) ((int) ($pageData['retention_days'] ?? 30)) . ' يومًا', 'info'); ?>
        <?php cc_kpi_card('أنواع الأعطال', (string) count($byCode) . (!empty($partial['failures_by_code']) ? '+' : ''), 'أكثر 50 نوعًا وكودًا تكرارًا؛ علامة + تعني قراءة جزئية.', 'chart', 'بدون سجلات جهاز خام', 'success'); ?>
        <?php cc_kpi_card('الإصدارات المتأثرة', (string) count($byVersion) . (!empty($partial['failures_by_version']) ? '+' : ''), 'أعلى 25 إصدارًا وقت وقوع الحدث؛ علامة + تعني قراءة جزئية.', 'package', 'لقطة وقت الحدث', 'info'); ?>
        <?php cc_kpi_card('الهوستات المتأثرة', (string) count($byHost) . (!empty($partial['failures_by_host']) ? '+' : ''), 'أعلى 25 بصمة مجهولة القيمة مشتقة من الهوست؛ علامة + تعني قراءة جزئية.', 'server', 'بدون بيانات دخول', 'success'); ?>
    </section>

    <section class="dashboard-grid phase7-grid">
        <article class="panel"><header class="panel__header"><div><span class="eyebrow">أكثر الأخطاء تكرارًا</span><h2>الأعطال حسب الكود</h2></div></header><?php if ($byCode === []): ?><?php cc_empty_state('لا توجد أحداث تشخيصية', 'لم يصل حدث مصنف داخل مدة الاحتفاظ.'); ?><?php else: ?><div class="metric-list"><?php foreach ($byCode as $row): ?><div class="metric-row"><span><strong><bdi dir="ltr"><?= cc_e($row['error_code']) ?></bdi></strong><small><bdi dir="ltr"><?= cc_e($row['event_type']) ?></bdi> · آخر وصول <?= cc_e($row['last_received_at']) ?></small></span><?php cc_status_badge((string) ((int) $row['event_count']), 'danger'); ?></div><?php endforeach; ?></div><?php endif; ?></article>
        <article class="panel"><header class="panel__header"><div><span class="eyebrow">لقطة الإصدار</span><h2>الأعطال حسب الإصدار</h2></div></header><?php if ($byVersion === []): ?><?php cc_empty_state('لا توجد بيانات إصدار', 'لا توجد أحداث مصنفة في الفترة الحالية.'); ?><?php else: ?><div class="metric-list"><?php foreach ($byVersion as $row): ?><div class="metric-row"><span><strong><?= cc_e($row['app_version_name']) ?></strong><small>رقم الإصدار <?= (int) $row['app_version_code'] ?></small></span><?php cc_status_badge((string) ((int) $row['event_count']), 'warning'); ?></div><?php endforeach; ?></div><?php endif; ?></article>
        <article class="panel"><header class="panel__header"><div><span class="eyebrow">بيانات الجهاز وقت الحدث</span><h2>الأعطال حسب الجهاز</h2></div></header><?php if ($byDevice === []): ?><?php cc_empty_state('لا توجد بيانات جهاز', 'لا توجد أحداث مصنفة في الفترة الحالية.'); ?><?php else: ?><div class="metric-list"><?php foreach ($byDevice as $row): ?><div class="metric-row"><span><strong><bdi dir="ltr"><?= cc_e($row['platform_class']) ?></bdi> · <?= cc_e($row['device_manufacturer']) ?></strong><small><?= cc_e($row['device_model']) ?></small></span><?php cc_status_badge((string) ((int) $row['event_count']), 'warning'); ?></div><?php endforeach; ?></div><?php endif; ?></article>
        <article class="panel"><header class="panel__header"><div><span class="eyebrow">بصمة الهوست الآمنة</span><h2>الأعطال حسب الهوست</h2></div></header><?php if ($byHost === []): ?><?php cc_empty_state('لا توجد بيانات هوست', 'لا توجد أحداث مصنفة في الفترة الحالية.'); ?><?php else: ?><div class="metric-list"><?php foreach ($byHost as $row): ?><div class="metric-row"><span><strong><code dir="ltr"><?= cc_e($fingerprint((string) $row['host_fingerprint'])) ?></code></strong><small>بصمة مشتقة من لقطة الجلسة</small></span><?php cc_status_badge((string) ((int) $row['event_count']), 'warning'); ?></div><?php endforeach; ?></div><?php endif; ?></article>
    </section>

    <section class="panel">
        <header class="panel__header"><div><span class="eyebrow">آخر 50 حدثًا</span><h2>الأحداث الأخيرة</h2><p class="muted-copy">تعرض هذه القائمة بيانات مصنفة وآمنة فقط، من دون رسائل حرة أو سجلات جهاز خام.</p></div></header>
        <?php if ($recent === []): ?><?php cc_empty_state('لا توجد أحداث حديثة', 'ستظهر هنا الأعطال المصنفة عند وصولها من التطبيق.'); ?><?php else: ?><div class="table-shell table-shell--responsive"><div class="table-scroll" tabindex="0" aria-label="الأحداث التشخيصية الأخيرة"><table class="phase7-table" data-mobile-cards><thead><tr><th>وقت الحدوث</th><th>وقت الوصول</th><th>النوع</th><th>الكود</th><th>الإصدار</th><th>المنصة</th><th>بصمة الهوست</th></tr></thead><tbody><?php foreach ($recent as $row): ?><tr><td><?= cc_e($row['occurred_at']) ?></td><td><?= cc_e($row['received_at']) ?></td><td><bdi dir="ltr"><?= cc_e($row['event_type']) ?></bdi></td><td><bdi dir="ltr"><?= cc_e($row['error_code']) ?></bdi></td><td><?= cc_e($row['app_version_name']) ?> (<?= (int) $row['app_version_code'] ?>)</td><td><bdi dir="ltr"><?= cc_e($row['platform_class']) ?></bdi></td><td><code dir="ltr"><?= cc_e($fingerprint((string) $row['host_fingerprint'])) ?></code></td></tr><?php endforeach; ?></tbody></table></div></div><?php endif; ?>
    </section>
</section>
