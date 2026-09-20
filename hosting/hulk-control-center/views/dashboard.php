<?php

declare(strict_types=1);

// Presence remains the authoritative source contract; the owner-facing copy below is Arabic-first.

$operationsAvailable = (bool) ($pageData['operations']['available'] ?? false);
$resellerAvailable = (bool) ($pageData['reseller']['available'] ?? false);
$presenceAvailable = (bool) ($pageData['presence']['available'] ?? false);
$hostHealthAvailable = (bool) ($pageData['host_health']['available'] ?? false);
$diagnosticsAvailable = (bool) ($pageData['diagnostics']['available'] ?? false);
$analyticsAvailable = (bool) ($pageData['analytics']['available'] ?? false);
$operations = $operationsAvailable && is_array($pageData['operations']['data'] ?? null) ? $pageData['operations']['data'] : [];
$reseller = $resellerAvailable && is_array($pageData['reseller']['data'] ?? null) ? $pageData['reseller']['data'] : [];
$presence = $presenceAvailable && is_array($pageData['presence']['data'] ?? null) ? $pageData['presence']['data'] : [];
$hostHealth = $hostHealthAvailable && is_array($pageData['host_health']['data'] ?? null) ? $pageData['host_health']['data'] : [];
$diagnostics = $diagnosticsAvailable && is_array($pageData['diagnostics']['data'] ?? null) ? $pageData['diagnostics']['data'] : [];
$analytics = $analyticsAvailable && is_array($pageData['analytics']['data'] ?? null) ? $pageData['analytics']['data'] : [];

$serviceLabels = [
    'OPERATIONAL' => ['تعمل بصورة طبيعية', 'success'],
    'DEGRADED' => ['الخدمة تعمل بأداء متأثر', 'warning'],
    'MAINTENANCE' => ['الخدمة في وضع الصيانة', 'danger'],
];
$serviceStatus = (string) ($operations['service']['status'] ?? '');
$servicePresentation = $serviceLabels[$serviceStatus] ?? ['الحالة غير متاحة', 'neutral'];
$updateType = (string) ($operations['update']['updateType'] ?? '');
$updateLabel = $updateType === 'REQUIRED' ? 'تحديث إلزامي' : ($updateType === 'OPTIONAL' ? 'تحديث اختياري' : 'السياسة غير متاحة');
$activeRelease = is_array($operations['active_release'] ?? null) ? $operations['active_release'] : null;
$auditRows = is_array($operations['audit'] ?? null) ? $operations['audit'] : [];
$livePreview = is_array($presence['live_preview'] ?? null) ? $presence['live_preview'] : [];
$presenceResellers = is_array($presence['resellers'] ?? null) ? $presence['resellers'] : [];
$healthSummary = is_array($hostHealth['summary'] ?? null) ? $hostHealth['summary'] : [];
$healthTargets = is_array($hostHealth['targets'] ?? null) ? $hostHealth['targets'] : [];
$hostHealthCoverageComplete = ($hostHealth['coverage_complete'] ?? false) === true;
$sessionTrend = is_array($analytics['session_trend'] ?? null) ? $analytics['session_trend'] : [];
$currentAdoption = is_array($analytics['current_adoption_distribution'] ?? null) ? $analytics['current_adoption_distribution'] : [];
$currentAdoptionPartial = ($analytics['current_adoption_partial'] ?? false) === true;
$latestSessionTrend = array_slice($sessionTrend, -7);
$healthyHosts = (int) ($healthSummary['HEALTHY'] ?? 0);
$hostTargetCount = count($healthTargets);
$diagnosticEvents = (int) ($diagnostics['total_events'] ?? 0);

$availableSources = count(array_filter([
    $operationsAvailable, $resellerAvailable, $presenceAvailable,
    $hostHealthAvailable, $diagnosticsAvailable, $analyticsAvailable,
]));
$overall = ['title' => 'الأنظمة تعمل بصورة طبيعية', 'description' => 'المصادر الأساسية متاحة ولا توجد إشارة تشغيلية عاجلة.', 'tone' => 'success', 'icon' => 'shield'];
if (!$operationsAvailable) {
    $overall = ['title' => 'تعذر قراءة حالة التطبيق', 'description' => 'المصدر الأساسي للخدمة والإصدارات غير متاح حاليًا.', 'tone' => 'danger', 'icon' => 'diagnostic'];
} elseif ($serviceStatus !== 'OPERATIONAL') {
    $overall = ['title' => $servicePresentation[0], 'description' => 'راجع حالة الخدمة والرسالة المنشورة للمستخدمين.', 'tone' => $servicePresentation[1], 'icon' => 'pulse'];
} elseif (!$resellerAvailable || !$presenceAvailable || !$hostHealthAvailable || !$diagnosticsAvailable || !$analyticsAvailable) {
    $overall = ['title' => 'البيانات التشغيلية جزئية', 'description' => 'التطبيق متاح، لكن أحد مصادر النشاط أو الموزعين يحتاج إلى تحقق.', 'tone' => 'warning', 'icon' => 'diagnostic'];
} elseif (!$hostHealthCoverageComplete) {
    $overall = [
        'title' => 'بيانات صحة الهوستات جزئية',
        'description' => 'الخدمة الأساسية تعمل، لكن آخر فحص لم يشمل كل الهوستات المستهدفة.',
        'tone' => 'warning',
        'icon' => 'heart',
    ];
} elseif ($hostTargetCount > 0 && $healthyHosts < $hostTargetCount) {
    $overall = ['title' => 'بعض الهوستات تحتاج إلى انتباه', 'description' => 'الخدمة الأساسية تعمل، لكن آخر فحص لم يكن سليمًا لكل الهوستات.', 'tone' => 'warning', 'icon' => 'heart'];
}

$attentionItems = [];
if (!$operationsAvailable) {
    $attentionItems[] = ['title' => 'حالة التطبيق غير متاحة', 'message' => 'تعذر قراءة الخدمة والإصدار الحالي.', 'tone' => 'danger', 'icon' => 'pulse', 'url' => cc_url('service')];
} elseif ($serviceStatus !== 'OPERATIONAL') {
    $attentionItems[] = ['title' => $servicePresentation[0], 'message' => 'راجع رسالة الحالة ووقت الانتهاء المتوقع.', 'tone' => $servicePresentation[1], 'icon' => 'pulse', 'url' => cc_url('service')];
}
if ($operationsAvailable && $activeRelease === null) {
    $attentionItems[] = ['title' => 'لا يوجد إصدار APK نشط', 'message' => 'القيم المنشورة تأتي من الإعدادات ولا يوجد ملف إصدار نشط.', 'tone' => 'warning', 'icon' => 'package', 'url' => cc_url('releases')];
}
if ($updateType === 'REQUIRED') {
    $attentionItems[] = ['title' => 'هناك تحديث إلزامي', 'message' => 'تحقق من الإصدار والحد الأدنى المدعوم قبل نشر أي تغيير.', 'tone' => 'warning', 'icon' => 'shield', 'url' => cc_url('releases')];
}
if (!$presenceAvailable) {
    $attentionItems[] = ['title' => 'النشاط المباشر غير متاح', 'message' => 'لم تُستبدل بيانات الجلسات بأصفار.', 'tone' => 'danger', 'icon' => 'users', 'url' => cc_url('sessions')];
}
if (!$resellerAvailable) {
    $attentionItems[] = ['title' => 'بيانات الموزعين غير متاحة', 'message' => 'تعذر قراءة حالة الموزعين والأكواد والهوستات.', 'tone' => 'danger', 'icon' => 'briefcase', 'url' => cc_url('resellers')];
}
if ($hostHealthAvailable && (!$hostHealthCoverageComplete || ($hostTargetCount > 0 && $healthyHosts < $hostTargetCount))) {
    $healthMessage = !$hostHealthCoverageComplete
        ? 'آخر قراءة لم تشمل كل الهوستات المستهدفة.'
        : $healthyHosts . ' من ' . $hostTargetCount . ' هوستات سليمة في آخر قراءة.';
    $attentionItems[] = ['title' => 'فحص الهوستات يحتاج إلى مراجعة', 'message' => $healthMessage, 'tone' => 'warning', 'icon' => 'heart', 'url' => cc_url('host-health')];
} elseif (!$hostHealthAvailable) {
    $attentionItems[] = ['title' => 'صحة الهوستات غير متاحة', 'message' => 'تعذر قراءة آخر نتائج الفحص الدوري.', 'tone' => 'danger', 'icon' => 'heart', 'url' => cc_url('host-health')];
}
if ($diagnosticsAvailable && $diagnosticEvents > 0) {
    $attentionItems[] = ['title' => 'أحداث أعطال مسجلة', 'message' => $diagnosticEvents . ' حدثًا ضمن مدة الاحتفاظ الحالية.', 'tone' => 'warning', 'icon' => 'diagnostic', 'url' => cc_url('diagnostics')];
}
?>
<section class="dashboard-command dashboard-command--<?= cc_e($overall['tone']) ?>">
    <div class="dashboard-command__status">
        <span class="dashboard-command__icon"><?= cc_icon($overall['icon']) ?></span>
        <div><span class="eyebrow">الحالة العامة</span><h2><?= cc_e($overall['title']) ?></h2><p><?= cc_e($overall['description']) ?></p></div>
    </div>
    <div class="dashboard-command__signals">
        <a class="command-signal" href="<?= cc_e(cc_url('service')) ?>"><span>حالة الخدمة</span><strong><?= cc_e($operationsAvailable ? $servicePresentation[0] : 'غير متاحة') ?></strong><?php cc_status_badge($operationsAvailable ? 'حالية' : 'تعذر الاتصال', $operationsAvailable ? $servicePresentation[1] : 'danger'); ?></a>
        <a class="command-signal" href="<?= cc_e(cc_url('releases')) ?>"><span>الإصدار الحالي</span><strong dir="ltr"><?= cc_e($operationsAvailable ? (string) ($operations['update']['latestVersionName'] ?? '—') : '—') ?></strong><small><?= cc_e($operationsAvailable ? $updateLabel : 'تعذر الاتصال') ?></small></a>
        <a class="command-signal" href="<?= cc_e(cc_url('live-users')) ?>"><span>النشطون الآن</span><strong><?= $presenceAvailable ? (int) ($presence['online_now'] ?? 0) : '—' ?></strong><small><?= $presenceAvailable ? 'نشاط حديث' : 'غير متاح' ?></small></a>
        <a class="command-signal" href="<?= cc_e(cc_url('host-health')) ?>"><span>صحة الهوستات</span><strong><?= $hostHealthAvailable ? ($hostTargetCount === 0 ? 'لا توجد أهداف' : $healthyHosts . ' / ' . $hostTargetCount) : '—' ?></strong><small><?= $hostHealthAvailable ? ($hostHealthCoverageComplete ? 'آخر فحص' : 'قراءة جزئية') : 'غير متاح' ?></small></a>
    </div>
</section>

<section class="dashboard-priority">
    <article class="panel attention-panel">
        <header class="panel__header"><div><span class="eyebrow">الأولوية الآن</span><h2>ما يحتاج إلى انتباهك</h2></div><span class="record-count"><?= count($attentionItems) ?></span></header>
        <?php if ($attentionItems === []): ?>
            <div class="attention-clear"><span><?= cc_icon('shield') ?></span><div><strong>لا توجد تنبيهات عاجلة</strong><small>راجع النشاط الأخير أو انتقل مباشرة إلى الوحدة التي تريد إدارتها.</small></div></div>
        <?php else: ?>
            <div class="attention-list"><?php foreach ($attentionItems as $item): ?><a class="attention-item attention-item--<?= cc_e($item['tone']) ?>" href="<?= cc_e($item['url']) ?>"><span class="attention-item__icon"><?= cc_icon($item['icon']) ?></span><span><strong><?= cc_e($item['title']) ?></strong><small><?= cc_e($item['message']) ?></small></span><?= cc_icon('chevron', 'attention-item__arrow') ?></a><?php endforeach; ?></div>
        <?php endif; ?>
    </article>
</section>

<nav class="dashboard-shortcuts" aria-label="إجراءات سريعة">
    <a href="<?= cc_e(cc_url('releases')) ?>"><?= cc_icon('package') ?><span>إدارة إصدار</span></a>
    <a href="<?= cc_e(cc_url('resellers')) ?>"><?= cc_icon('briefcase') ?><span>إدارة موزع</span></a>
    <a href="<?= cc_e(cc_url('announcements')) ?>"><?= cc_icon('bell') ?><span>نشر إعلان</span></a>
    <a href="<?= cc_e(cc_url('audit')) ?>"><?= cc_icon('audit') ?><span>مراجعة النشاط</span></a>
</nav>

<section class="operations-strip" aria-label="ملخص النشاط والوصول">
    <a href="<?= cc_e(cc_url('live-users')) ?>"><span>النشطون الآن</span><strong><?= $presenceAvailable ? (int) ($presence['online_now'] ?? 0) : '—' ?></strong><small><?= $presenceAvailable ? 'جلسة حديثة' : 'غير متاح' ?></small></a>
    <a href="<?= cc_e(cc_url('sessions')) ?>"><span>جلسات اليوم</span><strong><?= $presenceAvailable ? (int) ($presence['sessions_today'] ?? 0) : '—' ?></strong><small><?= $presenceAvailable ? 'حسب وقت الخادم' : 'غير متاح' ?></small></a>
    <a href="<?= cc_e(cc_url('devices')) ?>"><span>أجهزة نشطة</span><strong><?= $presenceAvailable ? (int) ($presence['active_devices'] ?? 0) : '—' ?></strong><small><?= $presenceAvailable ? 'آخر ' . (int) ($presence['active_device_window_hours'] ?? 24) . ' ساعة' : 'غير متاح' ?></small></a>
    <a href="<?= cc_e(cc_url('diagnostics')) ?>"><span>أعطال مسجلة</span><strong><?= $diagnosticsAvailable ? $diagnosticEvents : '—' ?></strong><small><?= $diagnosticsAvailable ? 'ضمن مدة الاحتفاظ' : 'غير متاح' ?></small></a>
</section>

<section class="dashboard-grid dashboard-grid--activity">
    <article class="panel panel--wide">
        <header class="panel__header"><div><span class="eyebrow">نشاط مباشر</span><h2>المستخدمون الآن</h2></div><a class="button button--quiet" href="<?= cc_e(cc_url('live-users')) ?>">عرض الكل</a></header>
        <?php if (!$presenceAvailable): ?>
            <?php cc_unavailable_state('النشاط المباشر غير متاح', 'تعذر قراءة الجلسات، ولم تُعرض أرقام بديلة.', 'users'); ?>
        <?php elseif ($livePreview === []): ?>
            <?php cc_empty_state('لا يوجد مستخدمون نشطون الآن', 'ستظهر الجلسات هنا عند وصول نشاط حديث إلى الخادم.'); ?>
        <?php else: ?>
            <div class="live-preview-list"><?php foreach ($livePreview as $session): $resellerId = (int) $session['reseller_id']; $currentReseller = $presenceResellers[$resellerId] ?? null; ?><article class="live-preview-row"><span class="live-preview-row__status" aria-label="متصل الآن"></span><div class="live-preview-row__identity"><strong><?= cc_e(is_array($currentReseller) ? ($currentReseller['reseller_name'] ?? ('#' . $resellerId)) : '#' . $resellerId) ?></strong><small><?= cc_e($session['device_manufacturer'] . ' ' . $session['device_model']) ?></small></div><div><span>المستخدم</span><bdi dir="ltr"><?= cc_e($session['iptv_username']) ?></bdi></div><div><span>الإصدار</span><bdi dir="ltr"><?= cc_e($session['app_version_name']) ?> (<?= (int) $session['app_version_code'] ?>)</bdi></div><div><span>آخر نشاط</span><time><?= cc_e(cc_presence_display_time($session['last_seen_at'])) ?></time></div></article><?php endforeach; ?></div>
        <?php endif; ?>
    </article>
    <article class="panel">
        <header class="panel__header"><div><span class="eyebrow">آخر التغييرات</span><h2>النشاط الإداري</h2></div><a class="button button--quiet" href="<?= cc_e(cc_url('audit')) ?>">عرض السجل</a></header>
        <?php if (!$operationsAvailable): ?>
            <?php cc_unavailable_state('النشاط الإداري غير متاح', 'تعذر الوصول إلى سجل التغييرات.', 'audit'); ?>
        <?php elseif ($auditRows === []): ?>
            <?php cc_empty_state('لا توجد عمليات مسجلة', 'ستظهر هنا آخر التغييرات الإدارية.'); ?>
        <?php else: ?>
            <div class="activity-preview"><?php foreach ($auditRows as $audit): $auditAction = cc_phase8_audit_action_presentation((string) ($audit['action'] ?? '')); $auditKnown = ($auditAction['known'] ?? false) === true; ?><div class="activity-preview__row"><span class="activity-preview__icon"><?= cc_icon((string) $auditAction['icon']) ?></span><span><strong><?= cc_e((string) $auditAction['title']) ?></strong><small><?= cc_e((string) (($audit['username'] ?? null) ?: 'النظام')) ?><?php if (!$auditKnown): ?> · <bdi dir="ltr"><?= cc_e((string) ($auditAction['technical_id'] ?? '')) ?></bdi><?php endif; ?></small></span><time><?= cc_e((string) ($audit['created_at'] ?? '—')) ?></time></div><?php endforeach; ?></div>
        <?php endif; ?>
    </article>
</section>

<details class="panel dashboard-secondary">
    <summary><span><strong>اتجاهات إضافية</strong><small>استخدام الإصدارات وحركة الجلسات</small></span><?= cc_icon('chart') ?></summary>
    <div class="dashboard-secondary__grid">
        <section>
            <header class="panel__header"><div><span class="eyebrow">آخر 24 ساعة</span><h2>استخدام الإصدارات</h2></div><a class="button button--quiet" href="<?= cc_e(cc_url('analytics')) ?>">التحليلات</a></header>
            <?php if (!$analyticsAvailable): ?>
                <?php cc_unavailable_state('استخدام الإصدارات غير متاح', 'تعذر قراءة بيانات الأجهزة الحديثة.', 'package'); ?>
            <?php elseif ($currentAdoption === []): ?>
                <?php cc_empty_state('لا توجد أجهزة حديثة', 'لا توجد أجهزة مرصودة ضمن النافذة الحالية.'); ?>
            <?php else: ?>
                <div class="version-ranking"><?php foreach (array_slice($currentAdoption, 0, 6) as $index => $version): ?><div class="version-ranking__row"><span class="version-ranking__rank"><?= $index + 1 ?></span><span><strong dir="ltr"><?= cc_e($version['app_version_name']) ?></strong><small>رمز <?= (int) $version['app_version_code'] ?></small></span><b><?= (int) $version['device_count'] ?> جهاز</b></div><?php endforeach; ?></div>
                <?php if ($currentAdoptionPartial): ?><p class="muted-copy">تعرض القائمة أعلى الإصدارات ضمن نافذة القراءة الحالية.</p><?php endif; ?>
            <?php endif; ?>
        </section>
        <section>
            <header class="panel__header"><div><span class="eyebrow">آخر 7 أيام متاحة</span><h2>اتجاه الجلسات</h2></div><a class="button button--quiet" href="<?= cc_e(cc_url('analytics')) ?>">التفاصيل</a></header>
            <?php if (!$analyticsAvailable): ?>
                <?php cc_unavailable_state('اتجاه الجلسات غير متاح', 'تعذر قراءة بيانات التحليل.', 'chart'); ?>
            <?php elseif ($latestSessionTrend === []): ?>
                <?php cc_empty_state('لا توجد جلسات في النافذة', 'لا توجد جلسات مسجلة ضمن آخر 30 يومًا.'); ?>
            <?php else: ?>
                <div class="metric-list"><?php foreach ($latestSessionTrend as $trend): ?><div class="metric-row"><time><?= cc_e($trend['day']) ?></time><strong><?= (int) $trend['session_count'] ?> جلسة</strong></div><?php endforeach; ?></div>
            <?php endif; ?>
        </section>
    </div>
</details>

<details class="panel dashboard-sources">
    <summary><span><strong>حالة مصادر البيانات</strong><small><?= $availableSources ?> من 6 مصادر متاحة</small></span><?php cc_status_badge($availableSources === 6 ? 'كاملة' : 'جزئية', $availableSources === 6 ? 'success' : 'warning'); ?></summary>
    <div class="source-list">
        <div class="source-row"><span class="source-row__icon"><?= cc_icon('pulse') ?></span><span><strong>حالة التطبيق والإصدارات</strong><small>الخدمة والإصدار والسياسات والنشاط الإداري</small></span><?php cc_status_badge($operationsAvailable ? 'متاح' : 'غير متاح', $operationsAvailable ? 'success' : 'danger'); ?></div>
        <div class="source-row"><span class="source-row__icon"><?= cc_icon('briefcase') ?></span><span><strong>الموزعون والوصول</strong><small>الموزعون والهوست والكود الحالي</small></span><?php cc_status_badge($resellerAvailable ? 'متاح' : 'غير متاح', $resellerAvailable ? 'success' : 'danger'); ?></div>
        <div class="source-row"><span class="source-row__icon"><?= cc_icon('users') ?></span><span><strong>نشاط المستخدمين</strong><small>الجلسات والأجهزة وإصدارات التطبيق</small></span><?php cc_status_badge($presenceAvailable ? 'متاح' : 'غير متاح', $presenceAvailable ? 'success' : 'danger'); ?></div>
        <div class="source-row"><span class="source-row__icon"><?= cc_icon('heart') ?></span><span><strong>صحة الهوستات</strong><small>آخر نتائج الفحص الدوري</small></span><?php cc_status_badge(!$hostHealthAvailable ? 'غير متاح' : ($hostHealthCoverageComplete ? 'متاح' : 'جزئي'), !$hostHealthAvailable ? 'danger' : ($hostHealthCoverageComplete ? 'success' : 'warning')); ?></div>
        <div class="source-row"><span class="source-row__icon"><?= cc_icon('diagnostic') ?></span><span><strong>أعطال التطبيق</strong><small>أحداث مصنفة وآمنة</small></span><?php cc_status_badge($diagnosticsAvailable ? 'متاح' : 'غير متاح', $diagnosticsAvailable ? 'success' : 'danger'); ?></div>
        <div class="source-row"><span class="source-row__icon"><?= cc_icon('chart') ?></span><span><strong>التحليلات</strong><small>اتجاهات الجلسات والإصدارات</small></span><?php cc_status_badge($analyticsAvailable ? 'متاح' : 'غير متاح', $analyticsAvailable ? 'success' : 'danger'); ?></div>
    </div>
    <p class="dashboard-sources__note">مؤشر «المستخدمون اليوم» غير متاح لعدم وجود هوية حساب ثابتة. لا يستنتج المركز هذه القيمة من اسم المستخدم أو الجهاز.</p>
</details>
