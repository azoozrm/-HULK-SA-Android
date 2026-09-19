<?php

declare(strict_types=1);

$operationsAvailable = (bool) ($pageData['operations']['available'] ?? false);
$resellerAvailable = (bool) ($pageData['reseller']['available'] ?? false);
$presenceAvailable = (bool) ($pageData['presence']['available'] ?? false);
$operations = $operationsAvailable && is_array($pageData['operations']['data'] ?? null) ? $pageData['operations']['data'] : [];
$reseller = $resellerAvailable && is_array($pageData['reseller']['data'] ?? null) ? $pageData['reseller']['data'] : [];
$presence = $presenceAvailable && is_array($pageData['presence']['data'] ?? null) ? $pageData['presence']['data'] : [];
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
$livePreview = is_array($presence['live_preview'] ?? null) ? $presence['live_preview'] : [];
$presenceResellers = is_array($presence['resellers'] ?? null) ? $presence['resellers'] : [];
$versionDistribution = is_array($presence['version_distribution'] ?? null) ? $presence['version_distribution'] : [];
?>
<section class="dashboard-intro dashboard-intro--connected">
    <div class="dashboard-intro__copy">
        <span class="eyebrow">بيانات تشغيلية موثوقة</span>
        <h2>صورة واضحة للحالة الحالية</h2>
        <p>ملخص مباشر من Operations والموزعين وPresence، مع عزل كل مصدر عن الآخر وعدم تحويل تعذر الاتصال إلى أرقام صفرية.</p>
        <div class="dashboard-intro__status">
            <?php cc_status_badge($operationsAvailable ? 'Operations متصل' : 'Operations غير متاح', $operationsAvailable ? 'success' : 'danger'); ?>
            <?php cc_status_badge($resellerAvailable ? 'Reseller متصل' : 'Reseller غير متاح', $resellerAvailable ? 'success' : 'danger'); ?>
            <?php cc_status_badge($presenceAvailable ? 'Presence متصل' : 'Presence غير متاح', $presenceAvailable ? 'success' : 'danger'); ?>
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
    <div><span class="eyebrow">Presence</span><h2>الحضور والجلسات والأجهزة</h2></div>
    <?php cc_status_badge('TTL من إعدادات الخادم', $presenceAvailable ? 'success' : 'danger'); ?>
</div>
<?php if ($presenceAvailable): ?>
<section class="kpi-grid kpi-grid--reseller" aria-label="مؤشرات Presence الحالية">
    <?php cc_kpi_card('المستخدمون الآن', (string) ($presence['online_now'] ?? 0), 'جلسة غير منتهية بنبضة حديثة؛ لا تعني بالضرورة تشغيل وسائط.', 'users', 'Online Now', 'success'); ?>
    <?php cc_kpi_card('الجلسات اليوم', (string) ($presence['sessions_today'] ?? 0), 'بدأت منذ بداية اليوم بتوقيت لوحة التحكم.', 'clock', 'جلسات خادم', 'info'); ?>
    <?php cc_kpi_card('الأجهزة النشطة', (string) ($presence['active_devices'] ?? 0), 'installation_id ظهر خلال آخر ' . (string) ($presence['active_device_window_hours'] ?? 24) . ' ساعة.', 'device', 'نافذة معلنة', 'success'); ?>
</section>

<?php if (!($presence['resellers_available'] ?? false) && $livePreview !== []): ?>
    <?php cc_alert('بيانات جزئية', 'مؤشرات Presence متاحة، لكن تعذر ربط أسماء الموزعين الحالية في المعاينة.', 'warning'); ?>
<?php endif; ?>

<section class="dashboard-grid dashboard-grid--activity">
    <article class="panel panel--wide">
        <header class="panel__header"><div><span class="eyebrow">معاينة مباشرة</span><h2>المستخدمون الآن</h2></div><a class="button button--quiet" href="<?= cc_e(cc_url('live-users')) ?>">عرض الكل</a></header>
        <?php if ($livePreview === []): ?>
            <?php cc_empty_state('لا توجد جلسات Online الآن', 'ستظهر الجلسة بعد وصول نبضة حديثة ضمن TTL المضبوط.'); ?>
        <?php else: ?>
            <div class="table-shell"><div class="table-scroll" tabindex="0" aria-label="معاينة المستخدمين الآن"><table><thead><tr><th>الموزع</th><th>الكود</th><th>IPTV USERNAME</th><th>الهوست</th><th>الجهاز</th><th>الإصدار</th><th>آخر اتصال</th></tr></thead><tbody><?php foreach ($livePreview as $session): $resellerId = (int) $session['reseller_id']; $currentReseller = $presenceResellers[$resellerId] ?? null; ?><tr><td><?= cc_e(is_array($currentReseller) ? ($currentReseller['reseller_name'] ?? ('#' . $resellerId)) : '#' . $resellerId) ?></td><td><code class="credential-value" dir="ltr"><?= cc_e($session['access_code_snapshot']) ?></code></td><td><code class="credential-value" dir="ltr"><?= cc_e($session['iptv_username']) ?></code></td><td><span class="mono break-value" dir="ltr"><?= cc_e($session['host_snapshot']) ?></span></td><td><?= cc_e($session['device_manufacturer'] . ' ' . $session['device_model']) ?></td><td><?= cc_e($session['app_version_name']) ?> (<?= (int) $session['app_version_code'] ?>)</td><td><?= cc_e(cc_presence_display_time($session['last_seen_at'])) ?></td></tr><?php endforeach; ?></tbody></table></div></div>
        <?php endif; ?>
    </article>
    <article class="panel">
        <header class="panel__header"><div><span class="eyebrow">تغطية Presence</span><h2>الإصدارات خلال 24 ساعة</h2></div></header>
        <?php if ($versionDistribution === []): ?>
            <?php cc_empty_state('لا توجد أجهزة حديثة', 'لا توجد أجهزة Presence ضمن نافذة الأربع والعشرين ساعة.'); ?>
        <?php else: ?>
            <div class="presence-version-list"><?php foreach ($versionDistribution as $version): ?><div class="presence-version-row"><span><strong><?= cc_e($version['app_version_name']) ?></strong><small>Version code <?= (int) $version['app_version_code'] ?></small></span><?php cc_status_badge((string) ((int) $version['device_count']) . ' جهاز', 'info'); ?></div><?php endforeach; ?></div>
        <?php endif; ?>
        <p class="muted-copy">التوزيع يصف فقط أجهزة Presence المرصودة في النافذة، وليس إجمالي جميع عمليات تثبيت التطبيق.</p>
    </article>
</section>
<?php else: ?>
    <?php cc_error_state('تعذر قراءة بيانات Presence', 'المستخدمون والجلسات والأجهزة غير متاحة حاليًا، ولم تُعرض قيم صفرية بديلة.', cc_url('dashboard')); ?>
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
            <div class="source-row"><span class="source-row__icon"><?= cc_icon('users') ?></span><span><strong>Presence</strong><small>الجلسات والأجهزة وإصدارات التطبيق</small></span><?php cc_status_badge($presenceAvailable ? 'متصل' : 'خطأ قابل للاستعادة', $presenceAvailable ? 'success' : 'danger'); ?></div>
        </div>
    </article>
</section>

<section class="panel future-metrics">
    <header class="panel__header"><div><span class="eyebrow">حدود البيانات الحالية</span><h2>مؤشرات تبقى غير متاحة</h2></div><?php cc_status_badge('بدون تقديرات', 'neutral'); ?></header>
    <div class="future-metrics__grid">
        <?php cc_kpi_card('المستخدمون اليوم', 'غير متاح', 'غير متاح لعدم وجود هوية حساب ثابتة؛ لا يُستنتج من اسم IPTV أو الجهاز.', 'users'); ?>
        <?php cc_kpi_card('صحة الهوستات', 'غير متاح بعد', 'لا يوجد فحص صحي خادمي حاليًا.', 'heart'); ?>
        <?php cc_kpi_card('التشخيصات والتحليلات', 'غير متاح بعد', 'لا يوجد ingest أو analytics حاليًا.', 'diagnostic'); ?>
    </div>
</section>
