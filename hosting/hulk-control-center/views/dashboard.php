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
    'DEGRADED' => ['الخدمة تعمل باداء متاثر', 'warning'],
    'MAINTENANCE' => ['الخدمة في وضع الصيانة', 'danger'],
];
$serviceStatus = (string) ($operations['service']['status'] ?? '');
$servicePresentation = $serviceLabels[$serviceStatus] ?? ['الحالة غير متاحة', 'neutral'];
$updateType = (string) ($operations['update']['updateType'] ?? '');
$updateLabel = $updateType === 'REQUIRED' ? 'تحديث الزامي' : ($updateType === 'OPTIONAL' ? 'تحديث اختياري' : 'السياسة غير متاحة');
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
$overall = ['title' => 'الانظمة تعمل بصورة طبيعية', 'description' => 'المصادر الاساسية متاحة ولا توجد اشارة تشغيلية عاجلة', 'tone' => 'success', 'icon' => 'shield'];
if (!$operationsAvailable) {
    $overall = ['title' => 'تعذر قراءة حالة التطبيق', 'description' => 'المصدر الاساسي للخدمة والاصدارات غير متاح حاليًا', 'tone' => 'danger', 'icon' => 'diagnostic'];
} elseif ($serviceStatus !== 'OPERATIONAL') {
    $overall = ['title' => $servicePresentation[0], 'description' => 'راجع حالة الخدمة والرسالة المنشورة للمستخدمين', 'tone' => $servicePresentation[1], 'icon' => 'pulse'];
} elseif (!$resellerAvailable || !$presenceAvailable || !$hostHealthAvailable || !$diagnosticsAvailable || !$analyticsAvailable) {
    $overall = ['title' => 'البيانات التشغيلية جزئية', 'description' => 'التطبيق متاح، لكن احد مصادر النشاط او الهوستات يحتاج الى تحقق', 'tone' => 'warning', 'icon' => 'diagnostic'];
} elseif (!$hostHealthCoverageComplete) {
    $overall = [
        'title' => 'بيانات صحة الهوستات جزئية',
        'description' => 'الخدمة الاساسية تعمل، لكن اخر فحص لم يشمل كل الهوستات المستهدفة',
        'tone' => 'warning',
        'icon' => 'heart',
    ];
} elseif ($hostTargetCount > 0 && $healthyHosts < $hostTargetCount) {
    $overall = ['title' => 'بعض الهوستات تحتاج الى انتباه', 'description' => 'الخدمة الاساسية تعمل، لكن اخر فحص لم يكن سليمًا لكل الهوستات', 'tone' => 'warning', 'icon' => 'heart'];
}

$attentionItems = [];
if (!$operationsAvailable) {
    $attentionItems[] = ['title' => 'حالة التطبيق غير متاحة', 'message' => 'تعذر قراءة الخدمة والاصدار الحالي', 'tone' => 'danger', 'icon' => 'pulse', 'url' => cc_url('service')];
} elseif ($serviceStatus !== 'OPERATIONAL') {
    $attentionItems[] = ['title' => $servicePresentation[0], 'message' => 'راجع رسالة الحالة ووقت الانتهاء المتوقع', 'tone' => $servicePresentation[1], 'icon' => 'pulse', 'url' => cc_url('service')];
}
if ($operationsAvailable && $activeRelease === null) {
    $attentionItems[] = ['title' => 'لا يوجد اصدار APK نشط', 'message' => 'القيم المنشورة تاتي من الاعدادات ولا يوجد ملف اصدار نشط', 'tone' => 'warning', 'icon' => 'package', 'url' => cc_url('releases')];
}
if ($updateType === 'REQUIRED') {
    $attentionItems[] = ['title' => 'هناك تحديث الزامي', 'message' => 'تحقق من الاصدار والحد الادنى المدعوم قبل نشر اي تغيير', 'tone' => 'warning', 'icon' => 'shield', 'url' => cc_url('releases')];
}
if (!$presenceAvailable) {
    $attentionItems[] = ['title' => 'النشاط المباشر غير متاح', 'message' => 'لم تُستبدل بيانات الجلسات باصفار', 'tone' => 'danger', 'icon' => 'users', 'url' => cc_url('sessions')];
}
if (!$resellerAvailable) {
    $attentionItems[] = ['title' => 'بيانات الهوستات غير متاحة', 'message' => 'تعذر قراءة حالة الهوستات والاكواد والهوستات', 'tone' => 'danger', 'icon' => 'briefcase', 'url' => cc_url('hosts')];
}
if ($hostHealthAvailable && (!$hostHealthCoverageComplete || ($hostTargetCount > 0 && $healthyHosts < $hostTargetCount))) {
    $healthMessage = !$hostHealthCoverageComplete
        ? 'اخر قراءة لم تشمل كل الهوستات المستهدفة'
        : $healthyHosts . ' من ' . $hostTargetCount . ' هوستات سليمة في اخر قراءة';
    $attentionItems[] = ['title' => 'فحص الهوستات يحتاج الى مراجعة', 'message' => $healthMessage, 'tone' => 'warning', 'icon' => 'heart', 'url' => cc_url('host-health')];
} elseif (!$hostHealthAvailable) {
    $attentionItems[] = ['title' => 'صحة الهوستات غير متاحة', 'message' => 'تعذر قراءة اخر نتائج الفحص الدوري', 'tone' => 'danger', 'icon' => 'heart', 'url' => cc_url('host-health')];
}
if ($diagnosticsAvailable && $diagnosticEvents > 0) {
    $attentionItems[] = ['title' => 'احداث اعطال مسجلة', 'message' => $diagnosticEvents . ' حدثًا ضمن مدة الاحتفاظ الحالية', 'tone' => 'warning', 'icon' => 'diagnostic', 'url' => cc_url('diagnostics')];
}
?>
<?php
$onlineNow = $presenceAvailable ? (int) ($presence['online_now'] ?? 0) : null;
$onlineSubscriptions = $presenceAvailable ? (int) ($presence['online_subscriptions'] ?? 0) : null;
$onlineDevices = $presenceAvailable ? (int) ($presence['online_devices'] ?? 0) : null;
$sessionsToday = $presenceAvailable ? (int) ($presence['sessions_today'] ?? 0) : null;
$currentVersion = (string) ($operations['update']['latestVersionName'] ?? '');
$updateCopy = $operationsAvailable ? $updateLabel : 'السياسة غير متاحة';
$chartRange = (isset($_GET['chart']) && (string) $_GET['chart'] === '30') ? 30 : 7;
$chartNow = new DateTimeImmutable('now', new DateTimeZone('UTC'));
$serverNow = (string) ($presence['server_now'] ?? '');
if ($serverNow !== '') {
    try {
        $chartNow = new DateTimeImmutable($serverNow, new DateTimeZone('UTC'));
    } catch (Throwable $exception) {
        $chartNow = new DateTimeImmutable('now', new DateTimeZone('UTC'));
    }
}
$trendByDay = [];
foreach ($sessionTrend as $trendRow) {
    $trendByDay[(string) ($trendRow['day'] ?? '')] = (int) ($trendRow['session_count'] ?? 0);
}
$chartPoints = [];
if ($analyticsAvailable) {
    $chartToday = $chartNow->setTime(0, 0);
    for ($offset = $chartRange - 1; $offset >= 0; $offset--) {
        $day = $chartToday->modify('-' . $offset . ' days')->format('Y-m-d');
        $chartPoints[] = ['label' => $day, 'value' => $trendByDay[$day] ?? 0];
    }
}
$chartAxisIndexes = $chartRange === 30 ? [0, 5, 10, 15, 20, 25, 29] : range(0, 6);
$chartAxis = [];
foreach ($chartAxisIndexes as $index) {
    if (isset($chartPoints[$index])) {
        $chartAxis[] = substr($chartPoints[$index]['label'], 5);
    }
}
$chartMax = 1;
foreach ($chartPoints as $point) { $chartMax = max($chartMax, $point['value']); }
$chartLeft = 42.0; $chartRight = 426.0; $chartTop = 13.0; $chartBottom = 102.0;
$chartStep = count($chartPoints) > 1 ? ($chartRight - $chartLeft) / (count($chartPoints) - 1) : 0.0;
$chartX = static fn (int $index): float => $chartLeft + ($index * $chartStep);
$chartY = static fn (int $value): float => $chartBottom - (($value / $chartMax) * ($chartBottom - $chartTop));
$chartPath = '';
foreach ($chartPoints as $index => $point) {
    $chartPath .= ($index === 0 ? 'M' : 'L') . round($chartX($index), 1) . ' ' . round($chartY($point['value']), 1) . ' ';
}
$chartArea = $chartPoints === [] ? '' : $chartPath . 'L' . $chartRight . ' ' . $chartBottom . ' L' . $chartLeft . ' ' . $chartBottom . ' Z';
$firstAttention = $attentionItems[0] ?? null;
?>
<section class="banner">
    <div class="banner-status">
        <span class="banner-icon"><?= cc_icon((string) $overall['icon']) ?></span>
        <div>
            <h2>حالة الخدمة · <?= cc_e((string) $overall['title']) ?></h2>
            <p>الإصدار الحالي <bdi dir="ltr"><?= cc_e($currentVersion !== '' ? $currentVersion : '—') ?></bdi> · <?= cc_e($updateCopy) ?></p>
        </div>
    </div>
    <a class="btn" href="<?= cc_e(cc_url('service')) ?>">إدارة الخدمة</a>
</section>

<section class="online-now-card">
    <div class="online-now-card__head">
        <span class="online-now-card__label"><span class="dot"></span>المتصلون الآن</span>
        <a class="btn quiet" href="<?= cc_e(cc_url('live-users')) ?>">عرض المتصلين <?= cc_icon('arrow-left') ?></a>
    </div>
    <strong><?= $onlineSubscriptions === null ? '—' : $onlineSubscriptions ?></strong>
    <small><?= $onlineSubscriptions === null ? 'غير متاح' : 'اشتراك فريد متصل الآن' ?></small>
</section>

<div class="kpis">
    <article class="kpi"><div class="kpi-label"><span class="kpi-icon"><?= cc_icon('users') ?></span>اشتراكات نشطة</div><strong><?= $onlineSubscriptions === null ? '—' : $onlineSubscriptions ?></strong><small><?= $onlineSubscriptions === null ? 'غير متاح' : 'حسب هوية الاشتراك الحالية' ?></small></article>
    <article class="kpi"><div class="kpi-label"><span class="kpi-icon"><?= cc_icon('screen') ?></span>أجهزة متصلة</div><strong><?= $onlineDevices === null ? '—' : $onlineDevices ?></strong><small><?= $onlineDevices === null ? 'غير متاح' : 'أجهزة مميزة الآن' ?></small></article>
    <article class="kpi"><div class="kpi-label"><span class="kpi-icon"><?= cc_icon('pulse') ?></span>جلسات الآن</div><strong><?= $onlineNow === null ? '—' : $onlineNow ?></strong><small><?= $onlineNow === null ? 'غير متاح' : 'قد تتعدد للجهاز الواحد' ?></small></article>
    <article class="kpi"><div class="kpi-label"><span class="kpi-icon"><?= cc_icon('clock') ?></span>جلسات اليوم</div><strong><?= $sessionsToday === null ? '—' : $sessionsToday ?></strong><small><?= $sessionsToday === null ? 'غير متاح' : 'من بداية اليوم' ?></small></article>
</div>

<div class="grid">
    <section class="card">
        <header class="card-head"><div><h2>حركة الجلسات</h2><p>عدد الجلسات المسجلة</p></div><div class="period" role="group" aria-label="فترة الرسم">
                <a href="<?= cc_e(cc_url('dashboard')) ?>?chart=7" aria-pressed="<?= $chartRange === 7 ? 'true' : 'false' ?>"<?= $chartRange === 7 ? ' class="is-active"' : '' ?>>٧ أيام</a>
                <a href="<?= cc_e(cc_url('dashboard')) ?>?chart=30" aria-pressed="<?= $chartRange === 30 ? 'true' : 'false' ?>"<?= $chartRange === 30 ? ' class="is-active"' : '' ?>>٣٠ يومًا</a>
            </div></header>
        <?php if (!$analyticsAvailable): ?>
            <div class="card-head"><p>تعذر قراءة بيانات التحليل</p></div>
        <?php else: ?>
            <div class="chart">
                <svg viewBox="0 0 468 111" preserveAspectRatio="none" role="img" aria-label="اتجاه عدد الجلسات خلال آخر <?= $chartRange === 30 ? '30 يومًا' : '7 أيام' ?>">
                    <defs><linearGradient id="hulk-area" x1="0" x2="0" y1="0" y2="1"><stop stop-color="#f3df9d"/><stop offset="1" stop-color="#fffcf1"/></linearGradient></defs>
                    <?php foreach ([0, .25, .5, .75, 1] as $tick): $tickY = $chartY((int) round($chartMax * $tick)); ?>
                        <path d="M28 <?= round($tickY, 1) ?>H460" stroke="#e9e6dc"/>
                        <text x="22" y="<?= round($tickY + 3, 1) ?>" text-anchor="end" font-size="8" fill="#666458"><?= (int) round($chartMax * $tick) ?></text>
                    <?php endforeach; ?>
                    <path d="<?= cc_e($chartArea) ?>" fill="url(#hulk-area)" opacity=".8"/>
                    <path d="<?= cc_e($chartPath) ?>" fill="none" stroke="#98752d" stroke-width="2.2" vector-effect="non-scaling-stroke"/>
                    <?php foreach ($chartPoints as $index => $point): ?>
                        <circle cx="<?= round($chartX($index), 1) ?>" cy="<?= round($chartY($point['value']), 1) ?>" r="3.5" fill="#fff" stroke="#947027" stroke-width="2"><title><?= $point['value'] ?> جلسة · <?= cc_e($point['label']) ?></title></circle>
                    <?php endforeach; ?>
                </svg>
                <div dir="ltr" class="axis" style="grid-template-columns:repeat(<?= count($chartAxis) ?>,minmax(0,1fr))"><?php foreach ($chartAxis as $axisLabel): ?><span><?= cc_e($axisLabel) ?></span><?php endforeach; ?></div>
            </div>
        <?php endif; ?>
    </section>
    <section class="card quick-card quick-card--desktop">
        <header class="card-head"><h2>إجراءات سريعة</h2></header>
        <div class="quick">
            <a class="quick-btn quick-btn--gold" href="<?= cc_e(cc_url('announcements')) ?>"><span class="action-icon"><?= cc_icon('bell') ?></span>إضافة رسالة<?= cc_icon('arrow-left') ?></a>
            <a class="quick-btn" href="<?= cc_e(cc_url('releases')) ?>"><span class="action-icon"><?= cc_icon('package') ?></span>تحديث التطبيق<?= cc_icon('arrow-left') ?></a>
            <?php if (is_array($firstAttention)): ?>
                <div class="urgent"><div><b><?= cc_e((string) $firstAttention['title']) ?></b><br><?= cc_e((string) $firstAttention['message']) ?></div><a class="btn quiet" href="<?= cc_e((string) $firstAttention['url']) ?>">عرض</a></div>
            <?php endif; ?>
        </div>
    </section>
</div>

<section class="card">
    <header class="card-head"><div><h2>آخر الاتصالات</h2><p>مرتبة حسب آخر إشارة من التطبيق</p></div><a class="btn quiet" href="<?= cc_e(cc_url('live-users')) ?>">عرض الكل</a></header>
    <?php if (!$presenceAvailable): ?>
        <div class="card-head"><p>النشاط المباشر غير متاح، ولم تُعرض أرقام بديلة</p></div>
    <?php else: ?>
        <div class="table-wrap">
            <table>
                <thead><tr><th scope="col">الاشتراك</th><th scope="col">الهوست</th><th scope="col">الأجهزة</th><th scope="col">آخر نشاط</th><th scope="col"></th></tr></thead>
                <tbody>
                <?php if ($livePreview === []): ?>
                    <tr><td class="empty" colspan="5">لا توجد اتصالات حديثة الآن</td></tr>
                <?php else: foreach (array_slice($livePreview, 0, 4) as $session):
                    $resellerId = (int) ($session['reseller_id'] ?? 0);
                    $currentReseller = $presenceResellers[$resellerId] ?? null;
                    $resellerName = is_array($currentReseller) ? (string) ($currentReseller['reseller_name'] ?? ('#' . $resellerId)) : '#' . $resellerId;
                    $deviceName = trim((string) ($session['device_manufacturer'] ?? '') . ' ' . (string) ($session['device_model'] ?? ''));
                ?>
                    <tr>
                        <td data-label="الاشتراك"><span class="id-wrap"><span class="round"><?= cc_icon('user') ?></span><b><bdi dir="ltr"><?= cc_e((string) ($session['iptv_username'] ?? '')) ?></bdi></b></span></td>
                        <td data-label="الهوست"><?= cc_e($resellerName) ?></td>
                        <td data-label="الأجهزة"><?= cc_e($deviceName !== '' ? $deviceName : 'غير متاح') ?></td>
                        <td data-label="آخر نشاط"><span class="badge green"><span class="dot"></span><?= cc_e(cc_presence_display_time((string) ($session['last_seen_at'] ?? ''))) ?></span></td>
                        <?php
                        $detailsUrl = cc_url('live-users');
                        try {
                            $detailsToken = cc_presence_subscription_token($resellerId, (string) ($session['host_snapshot'] ?? ''), (string) ($session['iptv_username'] ?? ''));
                            $detailsUrl = cc_url('live-users') . '?' . http_build_query([
                                'reseller' => $resellerId,
                                'subscription' => $detailsToken,
                            ]) . '#subscription-target';
                        } catch (Throwable $exception) {
                            $detailsUrl = cc_url('live-users');
                        }
                        ?>
                        <td><a class="btn quiet" href="<?= cc_e($detailsUrl) ?>">التفاصيل</a></td>
                    </tr>
                <?php endforeach; endif; ?>
                </tbody>
            </table>
        </div>
    <?php endif; ?>
</section>
<section class="card quick-card quick-card--mobile">
    <header class="card-head"><h2>إجراءات سريعة</h2></header>
    <div class="quick">
        <a class="quick-btn quick-btn--gold" href="<?= cc_e(cc_url('announcements')) ?>"><span class="action-icon"><?= cc_icon('bell') ?></span>إضافة رسالة<?= cc_icon('arrow-left') ?></a>
        <a class="quick-btn" href="<?= cc_e(cc_url('releases')) ?>"><span class="action-icon"><?= cc_icon('package') ?></span>تحديث التطبيق<?= cc_icon('arrow-left') ?></a>
    </div>
</section>
<p class="note" data-source="Presence">مؤشر «المستخدمون اليوم» غير متاح لعدم وجود هوية حساب ثابتة. لا يستنتج المركز هذه القيمة من اسم المستخدم أو الجهاز.</p>
