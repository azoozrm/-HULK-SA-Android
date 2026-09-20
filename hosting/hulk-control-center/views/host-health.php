<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل صحة الهوستات', 'لم تُعرض حالة بديلة. تحقق من migration وتشغيل الفحص المجدول.', cc_url('host-health'));
    return;
}

$summary = is_array($pageData['summary'] ?? null) ? $pageData['summary'] : [];
$targets = is_array($pageData['targets'] ?? null) ? $pageData['targets'] : [];
$history = is_array($pageData['history'] ?? null) ? $pageData['history'] : [];
$coverageComplete = ($pageData['coverage_complete'] ?? false) === true;
$eligibleRowCount = (int) ($pageData['eligible_row_count'] ?? count($targets));
$labels = [
    'HEALTHY' => ['سليم', 'success'],
    'DEGRADED' => ['متأثر', 'warning'],
    'TRANSIENT_FAILURE' => ['فشل عابر — ينتظر تأكيدًا', 'warning'],
    'UNREACHABLE' => ['غير قابل للوصول', 'danger'],
    'NOT_CHECKED' => ['لم يُفحص بعد', 'neutral'],
];
$fingerprint = static fn (string $value): string => substr($value, 0, 12) . '…';
?>
<section class="module-stack phase7-module">
    <?php if (!$coverageComplete): ?>
        <section class="notice notice--warning" role="status">
            الملخص جزئي: يعرض <?= count($targets) ?> هدفًا صالحًا من نافذة قراءة محدودة ضمن <?= $eligibleRowCount ?> صفًا نشطًا يملك host. أداة الجدولة تدوّر نافذتها كل خمس دقائق حتى لا تُحرم المعرّفات اللاحقة من الفحص.
        </section>
    <?php endif; ?>
    <section class="kpi-grid phase7-kpi-grid" aria-label="ملخص صحة الهوستات الحالية">
        <?php cc_kpi_card('سليم', (string) ($summary['HEALTHY'] ?? 0), 'أحدث فحص HTTP ناجح للهوست الحالي.', 'heart', 'حالي', 'success'); ?>
        <?php cc_kpi_card('متأثر', (string) ($summary['DEGRADED'] ?? 0), 'استجابة HTTP غير ناجحة أو عنوان غير عام محظور بسياسة الفحص.', 'pulse', 'حالي', 'warning'); ?>
        <?php cc_kpi_card('فشل عابر', (string) ($summary['TRANSIENT_FAILURE'] ?? 0), 'فشل نقل واحد لا يُعامل كحقيقة دائمة.', 'clock', 'ينتظر تأكيدًا', 'warning'); ?>
        <?php cc_kpi_card('غير قابل للوصول', (string) ($summary['UNREACHABLE'] ?? 0), 'فشلان متتاليان لنفس fingerprint الحالي.', 'diagnostic', 'مؤكد بفحصين', 'danger'); ?>
        <?php cc_kpi_card('لم يُفحص', (string) ($summary['NOT_CHECKED'] ?? 0), 'هوست حالي صالح بلا ملاحظة مجدولة حتى الآن.', 'server', 'لا توجد بيانات', 'neutral'); ?>
    </section>

    <section class="panel">
        <header class="panel__header"><div><span class="eyebrow">Current resellers.host</span><h2>الحالة الحالية</h2><p class="muted-copy">الهوست الحالي يبقى مملوكًا لسلطة الموزعين؛ السجل أدناه ملاحظات فقط.</p></div><?php cc_status_badge((string) count($targets) . ' هدف صالح', 'info'); ?></header>
        <?php if ($targets === []): ?>
            <?php cc_empty_state('لا توجد هوستات حالية صالحة', 'لا يوجد موزع نشط يملك host يقبله مسار التطبيع الحالي.'); ?>
        <?php else: ?>
            <div class="table-shell table-shell--responsive"><div class="table-scroll" tabindex="0" aria-label="الحالة الحالية للهوستات"><table class="phase7-table" data-mobile-cards><thead><tr><th>الموزع</th><th>Fingerprint</th><th>الحالة</th><th>آخر فحص</th><th>HTTP</th><th>الزمن</th><th>السياق</th></tr></thead><tbody>
            <?php foreach ($targets as $target): $state = (string) $target['health_state']; $latest = is_array($target['latest_check'] ?? null) ? $target['latest_check'] : null; ?>
                <tr><td><strong><?= cc_e($target['reseller_name']) ?></strong><small class="cell-note">#<?= (int) $target['reseller_id'] ?></small></td><td><code dir="ltr"><?= cc_e($fingerprint((string) $target['host_fingerprint'])) ?></code></td><td><?php cc_status_badge($labels[$state][0] ?? $state, $labels[$state][1] ?? 'neutral'); ?></td><td><?= cc_e($latest['checked_at'] ?? 'غير متاح') ?></td><td><?= $latest === null || $latest['http_status'] === null ? 'غير متاح' : (int) $latest['http_status'] ?></td><td><?= $latest === null ? 'غير متاح' : ((int) $latest['latency_ms'] . ' ms') ?></td><td><div class="context-links"><a href="<?= cc_e(cc_context_url('hosts', ['reseller' => (int) $target['reseller_id']])) ?>">الهوست الحالي</a><a href="<?= cc_e(cc_context_url('sessions', ['reseller' => (int) $target['reseller_id']])) ?>">الجلسات</a></div></td></tr>
            <?php endforeach; ?>
            </tbody></table></div></div>
        <?php endif; ?>
    </section>

    <section class="panel">
        <header class="panel__header"><div><span class="eyebrow">Append-only snapshots</span><h2>سجل الفحوص</h2><p class="muted-copy">آخر <?= CC_PHASE7_HISTORY_LIMIT ?> ملاحظة. تغيير الهوست لا يغيّر fingerprint التاريخي.</p></div></header>
        <?php if ($history === []): ?>
            <?php cc_empty_state('لم يصل أول فحص بعد', 'بعد نشر migration وجدولة أداة CLI ستظهر الملاحظات هنا.'); ?>
        <?php else: ?>
            <div class="table-shell table-shell--responsive"><div class="table-scroll" tabindex="0" aria-label="سجل فحوص الهوستات"><table class="phase7-table" data-mobile-cards><thead><tr><th>الوقت</th><th>الموزع</th><th>Fingerprint</th><th>النتيجة</th><th>DNS</th><th>TCP</th><th>HTTP</th><th>الزمن</th></tr></thead><tbody>
            <?php foreach ($history as $check): ?>
                <tr><td><?= cc_e($check['checked_at']) ?></td><td>#<?= (int) $check['reseller_id'] ?></td><td><code dir="ltr"><?= cc_e($fingerprint((string) $check['host_fingerprint'])) ?></code></td><td><?= cc_e($check['probe_result']) ?></td><td><?= $check['dns_ok'] ? 'PASS' : 'FAIL' ?></td><td><?= $check['tcp_ok'] ? 'PASS' : 'FAIL' ?></td><td><?= $check['http_status'] === null ? '—' : (int) $check['http_status'] ?></td><td><?= (int) $check['latency_ms'] ?> ms</td></tr>
            <?php endforeach; ?>
            </tbody></table></div></div>
        <?php endif; ?>
    </section>
</section>
