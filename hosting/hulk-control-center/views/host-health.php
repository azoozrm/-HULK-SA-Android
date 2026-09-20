<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل صحة الهوستات', 'لم تُعرض حالة بديلة. تحقق من جاهزية مصدر البيانات وتشغيل الفحص المجدول.', cc_url('host-health'));
    return;
}

$summary = is_array($pageData['summary'] ?? null) ? $pageData['summary'] : [];
$targets = is_array($pageData['targets'] ?? null) ? $pageData['targets'] : [];
$history = is_array($pageData['history'] ?? null) ? $pageData['history'] : [];
$coverageComplete = ($pageData['coverage_complete'] ?? false) === true;
$eligibleRowCount = (int) ($pageData['eligible_row_count'] ?? count($targets));
$labels = [
    'HEALTHY' => ['سليم', 'success'],
    'DEGRADED' => ['يحتاج متابعة', 'warning'],
    'TRANSIENT_FAILURE' => ['تعذر مؤقتًا', 'warning'],
    'UNREACHABLE' => ['غير قابل للوصول', 'danger'],
    'NOT_CHECKED' => ['لم يُفحص بعد', 'neutral'],
];
$fingerprint = static fn (string $value): string => substr($value, 0, 12) . '…';
$attentionTargets = array_values(array_filter($targets, static fn (array $target): bool =>
    (string) ($target['health_state'] ?? 'NOT_CHECKED') !== 'HEALTHY'
));
$healthyCount = (int) ($summary['HEALTHY'] ?? 0);
$attentionCount = count($attentionTargets);
$overallTone = $attentionCount === 0 && $targets !== [] ? 'success' : ($attentionCount > 0 ? 'warning' : 'neutral');
$overallLabel = $attentionCount === 0 && $targets !== [] ? 'الهوستات مستقرة' : ($attentionCount > 0 ? 'توجد حالات للمتابعة' : 'بانتظار أول فحص');
?>
<section class="module-stack host-decision-page">
    <?php cc_page_summary(
        'متابعة الاتصال',
        'صحة الهوستات',
        'ابدأ بالهوستات التي تحتاج قرارًا، ثم راجع تفاصيل الفحص عند الحاجة.',
        'heart',
        ['label' => $overallLabel, 'tone' => $overallTone],
        [
            ['label' => 'سليم', 'value' => (string) $healthyCount],
            ['label' => 'يحتاج متابعة', 'value' => (string) $attentionCount],
            ['label' => 'إجمالي الهوستات', 'value' => (string) count($targets)],
        ]
    ); ?>

    <?php if (!$coverageComplete): ?>
        <?php cc_alert('القراءة الحالية جزئية', 'تدوّر أداة الفحص نافذتها دوريًا لتغطية ' . $eligibleRowCount . ' صفًا مؤهلًا؛ لا تُعامل الصفوف غير الظاهرة كحالات سليمة.', 'warning'); ?>
    <?php endif; ?>

    <section class="panel health-attention-list">
        <header class="panel__header">
            <div><span class="eyebrow">الأولوية الآن</span><h2>هوستات تحتاج المتابعة</h2><p class="muted-copy">الحالات غير السليمة فقط، مرتبة لتصل إلى الموزع أو سجل الجلسات بسرعة.</p></div>
            <?php cc_status_badge($attentionCount === 0 ? 'لا توجد تنبيهات' : $attentionCount . ' للمتابعة', $attentionCount === 0 ? 'success' : 'warning'); ?>
        </header>
        <?php if ($attentionTargets === []): ?>
            <?php cc_empty_state('لا توجد حالات تستدعي التدخل', $targets === [] ? 'لم يصل أول فحص بعد.' : 'جميع الهوستات المفحوصة في حالة سليمة.'); ?>
        <?php else: ?>
            <div class="record-list record-list--attention">
                <?php foreach ($attentionTargets as $target):
                    $state = (string) ($target['health_state'] ?? 'NOT_CHECKED');
                    $latest = is_array($target['latest_check'] ?? null) ? $target['latest_check'] : null;
                ?>
                    <article class="record-card record-card--<?= cc_e($labels[$state][1] ?? 'neutral') ?>">
                        <header class="record-card__header">
                            <div><span class="record-card__eyebrow">موزع #<?= (int) $target['reseller_id'] ?></span><h3><?= cc_e((string) $target['reseller_name']) ?></h3></div>
                            <?php cc_status_badge($labels[$state][0] ?? 'حالة غير معروفة', $labels[$state][1] ?? 'neutral'); ?>
                        </header>
                        <?php cc_fact_list([
                            ['label' => 'آخر فحص', 'value' => (string) ($latest['checked_at'] ?? 'غير متاح')],
                            ['label' => 'استجابة HTTP', 'value' => $latest === null || $latest['http_status'] === null ? 'غير متاح' : (string) ((int) $latest['http_status']), 'ltr' => true],
                            ['label' => 'زمن الاستجابة', 'value' => $latest === null ? 'غير متاح' : (string) ((int) $latest['latency_ms']) . ' مللي ثانية'],
                        ]); ?>
                        <div class="record-actions">
                            <a class="button button--secondary" href="<?= cc_e(cc_context_url('hosts', ['reseller' => (int) $target['reseller_id']])) ?>">إدارة الهوست</a>
                            <a class="button button--quiet" href="<?= cc_e(cc_context_url('sessions', ['reseller' => (int) $target['reseller_id']])) ?>">جلسات الموزع</a>
                        </div>
                        <?php cc_technical_disclosure(); ?>
                            <?php cc_fact_list([
                                ['label' => 'بصمة الهوست', 'value' => $fingerprint((string) $target['host_fingerprint']), 'ltr' => true],
                                ['label' => 'الحالة الداخلية', 'value' => $state, 'ltr' => true],
                            ], 'fact-list--technical'); ?>
                        <?php cc_disclosure_end(); ?>
                    </article>
                <?php endforeach; ?>
            </div>
        <?php endif; ?>
    </section>

    <section class="panel">
        <header class="panel__header">
            <div><span class="eyebrow">كل الهوستات</span><h2>الحالة الحالية</h2><p class="muted-copy">عرض مختصر لكل موزع؛ الهوست نفسه يبقى مملوكًا لوحدة الهوستات.</p></div>
            <?php cc_status_badge((string) count($targets) . ' هوست', 'info'); ?>
        </header>
        <?php if ($targets === []): ?>
            <?php cc_empty_state('لا توجد هوستات صالحة للفحص', 'لا يوجد موزع نشط يملك هوستًا يقبله مسار التطبيع الحالي.'); ?>
        <?php else: ?>
            <div class="compact-records" role="list" aria-label="الحالة الحالية للهوستات">
                <?php foreach ($targets as $target):
                    $state = (string) ($target['health_state'] ?? 'NOT_CHECKED');
                    $latest = is_array($target['latest_check'] ?? null) ? $target['latest_check'] : null;
                ?>
                    <article class="compact-record" role="listitem">
                        <div class="compact-record__identity"><strong><?= cc_e((string) $target['reseller_name']) ?></strong><small>آخر فحص: <?= cc_e((string) ($latest['checked_at'] ?? 'غير متاح')) ?></small></div>
                        <div class="compact-record__metric"><span>الاستجابة</span><strong><?= $latest === null ? '—' : (int) $latest['latency_ms'] . ' ms' ?></strong></div>
                        <?php cc_status_badge($labels[$state][0] ?? 'غير معروف', $labels[$state][1] ?? 'neutral'); ?>
                    </article>
                <?php endforeach; ?>
            </div>
        <?php endif; ?>
    </section>

    <?php cc_technical_disclosure('سجل الفحوص والتتبع التقني'); ?>
        <section class="technical-section">
            <div class="technical-section__intro"><h2>آخر <?= CC_PHASE7_HISTORY_LIMIT ?> نتيجة فحص</h2><p>يُستخدم هذا السجل للتحقق من DNS وTCP وHTTP؛ تغيير الهوست لا يغيّر البصمات التاريخية.</p></div>
            <?php if ($history === []): ?>
                <?php cc_empty_state('لم يصل أول فحص بعد', 'ستظهر النتائج بعد أول تشغيل للفحص المجدول.'); ?>
            <?php else: ?>
                <div class="table-shell"><div class="table-scroll" tabindex="0" aria-label="سجل فحوص الهوستات"><table data-mobile-cards><thead><tr><th>الوقت</th><th>الموزع</th><th>البصمة</th><th>النتيجة</th><th>DNS</th><th>TCP</th><th>HTTP</th><th>الزمن</th></tr></thead><tbody>
                <?php foreach ($history as $check): ?>
                    <tr><td><?= cc_e((string) $check['checked_at']) ?></td><td>#<?= (int) $check['reseller_id'] ?></td><td><bdi dir="ltr"><?= cc_e($fingerprint((string) $check['host_fingerprint'])) ?></bdi></td><td><bdi dir="ltr"><?= cc_e((string) $check['probe_result']) ?></bdi></td><td><?= $check['dns_ok'] ? 'ناجح' : 'فشل' ?></td><td><?= $check['tcp_ok'] ? 'ناجح' : 'فشل' ?></td><td><?= $check['http_status'] === null ? '—' : (int) $check['http_status'] ?></td><td><?= (int) $check['latency_ms'] ?> ms</td></tr>
                <?php endforeach; ?>
                </tbody></table></div></div>
            <?php endif; ?>
        </section>
    <?php cc_disclosure_end(); ?>
</section>
