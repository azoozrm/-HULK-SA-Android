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
$totalEvents = (int) ($pageData['total_events'] ?? 0);
?>
<section class="module-stack failure-insights-page">
    <?php cc_page_summary(
        'أعطال التطبيق',
        $totalEvents === 0 ? 'لا توجد أعطال مسجلة في الفترة' : 'توجد ' . $totalEvents . ' حالة عطل مسجلة',
        'تعرّف على أكثر الأعطال أثرًا، والإصدارات والأجهزة المتأثرة، ثم راجع الحدث التقني عند الحاجة.',
        'diagnostic',
        ['label' => $totalEvents === 0 ? 'لا توجد إشارات عطل' : 'تحتاج مراجعة', 'tone' => $totalEvents === 0 ? 'success' : 'warning'],
        [
            ['label' => 'مدة العرض', 'value' => (string) ((int) ($pageData['retention_days'] ?? 30)) . ' يومًا'],
            ['label' => 'أنواع الأعطال', 'value' => (string) count($byCode) . (!empty($partial['failures_by_code']) ? '+' : '')],
            ['label' => 'إصدارات متأثرة', 'value' => (string) count($byVersion) . (!empty($partial['failures_by_version']) ? '+' : '')],
        ]
    ); ?>

    <?php if ($hasPartialAggregate): ?>
        <?php cc_alert('بعض التفاصيل جزئية', 'وصل أحد التجميعات إلى حد العرض. الإجمالي ما زال محسوبًا من كل الحقائق داخل مدة الاحتفاظ.', 'warning'); ?>
    <?php endif; ?>

    <section class="insight-grid">
        <article class="panel insight-panel insight-panel--priority">
            <header class="panel__header"><div><span class="eyebrow">الأكثر تكرارًا</span><h2>أنواع الأعطال الحالية</h2><p class="muted-copy">العنوان يصف المعنى الموثوق فقط، والكود الأصلي متاح للتتبع.</p></div></header>
            <?php if ($byCode === []): ?>
                <?php cc_empty_state('لا توجد أنواع أعطال', 'لم يصل حدث مصنف داخل مدة الاحتفاظ.'); ?>
            <?php else: ?>
                <div class="ranked-list">
                    <?php foreach ($byCode as $index => $row): ?>
                        <article class="ranked-item">
                            <span class="ranked-item__rank"><?= (int) $index + 1 ?></span>
                            <div class="ranked-item__content"><strong>عطل تشغيل مسجل</strong><small>آخر ظهور: <?= cc_e((string) $row['last_received_at']) ?></small></div>
                            <?php cc_status_badge((string) ((int) $row['event_count']) . ' حدث', 'danger'); ?>
                            <details class="inline-technical"><summary>الكود التقني</summary><p><bdi dir="ltr"><?= cc_e((string) $row['error_code']) ?></bdi> · <bdi dir="ltr"><?= cc_e((string) $row['event_type']) ?></bdi></p></details>
                        </article>
                    <?php endforeach; ?>
                </div>
            <?php endif; ?>
        </article>

        <article class="panel">
            <header class="panel__header"><div><span class="eyebrow">نطاق الأثر</span><h2>أين تظهر الأعطال؟</h2></div></header>
            <div class="impact-groups">
                <section><h3>الإصدارات</h3><?php if ($byVersion === []): ?><p class="muted-copy">لا توجد بيانات.</p><?php else: ?><div class="metric-list"><?php foreach ($byVersion as $row): ?><div class="metric-row"><span><strong><?= cc_e((string) $row['app_version_name']) ?></strong><small>رمز <bdi dir="ltr"><?= (int) $row['app_version_code'] ?></bdi></small></span><?php cc_status_badge((string) ((int) $row['event_count']), 'warning'); ?></div><?php endforeach; ?></div><?php endif; ?></section>
                <section><h3>الأجهزة والمنصات</h3><?php if ($byDevice === []): ?><p class="muted-copy">لا توجد بيانات.</p><?php else: ?><div class="metric-list"><?php foreach ($byDevice as $row): ?><div class="metric-row"><span><strong><?= cc_e(trim((string) $row['device_manufacturer'] . ' ' . (string) $row['device_model'])) ?></strong><small><bdi dir="ltr"><?= cc_e((string) $row['platform_class']) ?></bdi></small></span><?php cc_status_badge((string) ((int) $row['event_count']), 'warning'); ?></div><?php endforeach; ?></div><?php endif; ?></section>
                <section><h3>الهوستات</h3><?php if ($byHost === []): ?><p class="muted-copy">لا توجد بيانات.</p><?php else: ?><div class="metric-list"><?php foreach ($byHost as $row): ?><div class="metric-row"><span><strong>هوست متأثر</strong><small><bdi dir="ltr"><?= cc_e($fingerprint((string) $row['host_fingerprint'])) ?></bdi></small></span><?php cc_status_badge((string) ((int) $row['event_count']), 'warning'); ?></div><?php endforeach; ?></div><?php endif; ?></section>
            </div>
        </article>
    </section>

    <section class="panel">
        <header class="panel__header"><div><span class="eyebrow">الأحداث الأحدث</span><h2>آخر ما وصل من التطبيق</h2><p class="muted-copy">تعرض القائمة الحقول الآمنة فقط؛ التفاصيل التقنية مطوية ولا تتضمن رسائل حرة.</p></div><?php cc_status_badge((string) count($recent) . ' حدث', 'info'); ?></header>
        <?php if ($recent === []): ?>
            <?php cc_empty_state('لا توجد أحداث حديثة', 'ستظهر الأعطال المصنفة هنا عند وصولها من التطبيق.'); ?>
        <?php else: ?>
            <div class="activity-feed diagnostic-feed" role="list">
                <?php foreach ($recent as $row): ?>
                    <article class="activity-row" role="listitem">
                        <span class="activity-row__icon"><?= cc_icon('diagnostic') ?></span>
                        <div class="activity-row__body">
                            <div class="activity-row__title"><strong>عطل تشغيل على الإصدار <?= cc_e((string) $row['app_version_name']) ?></strong><?php cc_status_badge('مسجل', 'danger'); ?></div>
                            <p><?= cc_e((string) $row['occurred_at']) ?> · <?= cc_e((string) $row['platform_class']) ?></p>
                            <div class="activity-row__context"><span>رمز الإصدار <bdi dir="ltr"><?= (int) $row['app_version_code'] ?></bdi></span><span>بصمة الهوست <bdi dir="ltr"><?= cc_e($fingerprint((string) $row['host_fingerprint'])) ?></bdi></span></div>
                            <details class="inline-technical"><summary>تفاصيل تقنية</summary><p>النوع: <bdi dir="ltr"><?= cc_e((string) $row['event_type']) ?></bdi> · الكود: <bdi dir="ltr"><?= cc_e((string) $row['error_code']) ?></bdi> · الوصول: <?= cc_e((string) $row['received_at']) ?></p></details>
                        </div>
                    </article>
                <?php endforeach; ?>
            </div>
        <?php endif; ?>
    </section>
</section>
