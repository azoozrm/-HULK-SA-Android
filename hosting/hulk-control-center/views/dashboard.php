<?php

declare(strict_types=1);

$operationsAvailable = (bool) ($pageData['operations']['available'] ?? false);
$resellerAvailable = (bool) ($pageData['reseller']['available'] ?? false);
$operations = $operationsAvailable && is_array($pageData['operations']['data'] ?? null) ? $pageData['operations']['data'] : [];
$reseller = $resellerAvailable && is_array($pageData['reseller']['data'] ?? null) ? $pageData['reseller']['data'] : [];
$serviceLabels = [
    'OPERATIONAL' => ['تعمل بصورة طبيعية', 'success'],
    'DEGRADED' => ['أداء متأثر', 'warning'],
    'MAINTENANCE' => ['صيانة جارية', 'danger'],
];
$serviceStatus = (string) ($operations['service']['status'] ?? '');
$servicePresentation = $serviceLabels[$serviceStatus] ?? ['حالة غير معروفة', 'neutral'];
$updateType = (string) ($operations['update']['updateType'] ?? '');
$updateLabel = $updateType === 'REQUIRED' ? 'إلزامي' : ($updateType === 'OPTIONAL' ? 'اختياري' : 'غير متاح');
$updateTone = $updateType === 'REQUIRED' ? 'warning' : ($updateType === 'OPTIONAL' ? 'info' : 'neutral');
$activeRelease = is_array($operations['active_release'] ?? null) ? $operations['active_release'] : null;
$auditRows = is_array($operations['audit'] ?? null) ? $operations['audit'] : [];
?>
<section class="dashboard-intro dashboard-intro--connected">
    <div class="dashboard-intro__copy">
        <span class="eyebrow">بيانات تشغيلية موثوقة</span>
        <h2>صورة واضحة للحالة الحالية</h2>
        <p>ملخص مباشر من سلطتَي Operations والموزعين، مع عزل كل مصدر عن الآخر وعدم تحويل تعذر الاتصال إلى أرقام صفرية.</p>
        <div class="dashboard-intro__status">
            <?php cc_status_badge($operationsAvailable ? 'Operations متصل' : 'Operations غير متاح', $operationsAvailable ? 'success' : 'danger'); ?>
            <?php cc_status_badge($resellerAvailable ? 'Reseller متصل' : 'Reseller غير متاح', $resellerAvailable ? 'success' : 'danger'); ?>
        </div>
    </div>
    <div class="dashboard-intro__seal" aria-hidden="true"><img src="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" alt=""></div>
</section>

<div class="section-heading">
    <div><span class="eyebrow">حالة التشغيل</span><h2>Operations الآن</h2></div>
    <?php cc_status_badge('مصدر حي حالي', $operationsAvailable ? 'success' : 'danger'); ?>
</div>
<?php if ($operationsAvailable): ?>
<section class="kpi-grid" aria-label="مؤشرات Operations الحالية">
    <?php cc_kpi_card('حالة الخدمة', $servicePresentation[0], 'من سجل حالة الخدمة المنشور للتطبيق.', 'pulse', $serviceStatus ?: 'غير معروف', $servicePresentation[1]); ?>
    <?php cc_kpi_card('الإصدار الحالي', (string) ($operations['update']['latestVersionName'] ?? 'غير متاح'), 'Version code: ' . (string) ($operations['update']['latestVersionCode'] ?? '—'), 'package', $activeRelease === null ? 'من الإعدادات' : 'إصدار نشط', $activeRelease === null ? 'info' : 'success'); ?>
    <?php cc_kpi_card('الحد الأدنى المدعوم', (string) ($operations['update']['minimumSupportedVersionCode'] ?? 'غير متاح'), 'نفس الحد الأدنى المنشور في عقد التطبيق.', 'shield', 'Version code', 'info'); ?>
    <?php cc_kpi_card('سياسة التحديث', $updateLabel, 'مأخوذة من لقطة التحديث العامة الحالية.', 'toggle', $updateType ?: 'غير معروف', $updateTone); ?>
    <?php cc_kpi_card('الإعلانات الحالية', (string) ($operations['current_announcements'] ?? 0), 'مفعلة وضمن نافذة العرض الحالية.', 'bell', 'حاليًا', 'success'); ?>
    <?php cc_kpi_card('المميزات المفعلة', (string) ($operations['enabled_features'] ?? 0) . ' / ' . (string) ($operations['known_features'] ?? 0), 'من قائمة المميزات المعروفة في عقد Operations.', 'toggle', 'فعّالة', 'success'); ?>
</section>
<?php else: ?>
    <?php cc_error_state('تعذر قراءة بيانات Operations', 'مؤشرات Operations غير متاحة حاليًا، بينما تظل بيانات الموزعين المستقلة قابلة للعرض.', cc_url('dashboard')); ?>
<?php endif; ?>

<div class="section-heading dashboard-section-heading">
    <div><span class="eyebrow">سلطة الموزعين</span><h2>الوضع الحالي للموزعين</h2></div>
    <?php cc_status_badge('مصدر حي حالي', $resellerAvailable ? 'success' : 'danger'); ?>
</div>
<?php if ($resellerAvailable): ?>
<section class="kpi-grid kpi-grid--reseller" aria-label="مؤشرات الموزعين الحالية">
    <?php cc_kpi_card('إجمالي الموزعين', (string) ($reseller['total_resellers'] ?? 0), 'كل السجلات في سلطة الموزعين.', 'briefcase', 'إجمالي حقيقي', 'info'); ?>
    <?php cc_kpi_card('الموزعون النشطون', (string) ($reseller['active_resellers'] ?? 0), 'السجلات ذات الحالة active فقط.', 'users', 'نشط', 'success'); ?>
    <?php cc_kpi_card('هوستات نشطة مضبوطة', (string) ($reseller['configured_hosts'] ?? 0), 'موزعون نشطون وهوستاتهم تقبلها قواعد resolver.', 'server', 'هوست صالح', 'success'); ?>
    <?php cc_kpi_card('أكواد جاهزة للحل', (string) ($reseller['resolver_ready_codes'] ?? 0), 'موزع نشط + هوست صالح + كود canonical وهاش مطابق.', 'key', 'Resolver-ready', 'success'); ?>
</section>
<?php else: ?>
    <?php cc_error_state('تعذر قراءة بيانات الموزعين', 'مؤشرات الموزعين غير متاحة حاليًا، بينما تظل بيانات Operations المستقلة قابلة للعرض.', cc_url('dashboard')); ?>
<?php endif; ?>

<section class="dashboard-grid dashboard-grid--activity">
    <article class="panel panel--wide">
        <header class="panel__header"><div><span class="eyebrow">سجل الإدارة</span><h2>آخر النشاطات الإدارية</h2></div><?php cc_status_badge($operationsAvailable ? 'آخر 8 عمليات' : 'غير متاح', $operationsAvailable ? 'info' : 'danger'); ?></header>
        <?php if (!$operationsAvailable): ?>
            <?php cc_unavailable_state('السجل غير متاح حاليًا', 'تعذر الوصول إلى سلطة التدقيق في Operations.', 'audit'); ?>
        <?php elseif ($auditRows === []): ?>
            <?php cc_empty_state('لا توجد عمليات إدارية مسجلة', 'ستظهر هنا العمليات المسجلة في سلطة التدقيق الحالية.'); ?>
        <?php else: ?>
            <div class="table-shell dashboard-audit-table"><div class="table-scroll" tabindex="0" aria-label="آخر النشاطات الإدارية">
                <table><thead><tr><th scope="col">العملية</th><th scope="col">المسؤول</th><th scope="col">التاريخ</th></tr></thead><tbody>
                <?php foreach ($auditRows as $audit): ?><tr>
                    <td><strong><?= cc_e((string) ($audit['action'] ?? '—')) ?></strong></td>
                    <td><?= cc_e((string) (($audit['username'] ?? null) ?: 'مسؤول محذوف')) ?></td>
                    <td><time datetime="<?= cc_e((string) ($audit['created_at'] ?? '')) ?>"><?= cc_e((string) ($audit['created_at'] ?? '—')) ?></time></td>
                </tr><?php endforeach; ?>
                </tbody></table>
            </div></div>
        <?php endif; ?>
    </article>
    <article class="panel">
        <header class="panel__header"><div><span class="eyebrow">نزاهة القراءة</span><h2>حالة المصادر</h2></div></header>
        <div class="source-list">
            <div class="source-row"><span class="source-row__icon"><?= cc_icon('pulse') ?></span><span><strong>Operations</strong><small>الخدمة والإصدار والسياسات والتدقيق</small></span><?php cc_status_badge($operationsAvailable ? 'متصل' : 'خطأ قابل للاستعادة', $operationsAvailable ? 'success' : 'danger'); ?></div>
            <div class="source-row"><span class="source-row__icon"><?= cc_icon('briefcase') ?></span><span><strong>Reseller</strong><small>الموزعون والهوست والكود الحالي</small></span><?php cc_status_badge($resellerAvailable ? 'متصل' : 'خطأ قابل للاستعادة', $resellerAvailable ? 'success' : 'danger'); ?></div>
            <div class="source-row"><span class="source-row__icon"><?= cc_icon('users') ?></span><span><strong>Presence</strong><small>لا توجد له سلطة خادم حاليًا</small></span><?php cc_status_badge('غير متاح بعد', 'neutral'); ?></div>
        </div>
    </article>
</section>

<section class="panel future-metrics">
    <header class="panel__header"><div><span class="eyebrow">حدود البيانات الحالية</span><h2>مؤشرات لا يمكن قياسها بعد</h2></div><?php cc_status_badge('بدون تقديرات', 'neutral'); ?></header>
    <div class="future-metrics__grid">
        <?php cc_kpi_card('المستخدمون الآن', 'غير متاح بعد', 'يتطلب Presence من التطبيق.', 'users'); ?>
        <?php cc_kpi_card('الجلسات اليوم', 'غير متاح بعد', 'لا يوجد سجل جلسات خادمي حاليًا.', 'clock'); ?>
        <?php cc_kpi_card('الأجهزة النشطة', 'غير متاح بعد', 'لا توجد سلطة أجهزة حالية.', 'device'); ?>
        <?php cc_kpi_card('اعتماد الإصدارات', 'غير متاح بعد', 'لا يُستنتج من تنزيلات APK.', 'chart'); ?>
        <?php cc_kpi_card('صحة الهوستات', 'غير متاح بعد', 'لا يوجد فحص صحي خادمي حاليًا.', 'heart'); ?>
        <?php cc_kpi_card('التشخيصات والتحليلات', 'غير متاح بعد', 'لا يوجد ingest أو analytics حاليًا.', 'diagnostic'); ?>
    </div>
</section>
