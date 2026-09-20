<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل سجل الإدارة', 'تحقق من اتصال قاعدة Operations ثم أعد المحاولة.', cc_url('audit'));
    return;
}
$rows = is_array($pageData['audit'] ?? null) ? $pageData['audit'] : [];
$filters = is_array($pageData['filters'] ?? null) ? $pageData['filters'] : [];
$page = (int) ($pageData['page'] ?? 1);
$pageUrl = static function (int $target) use ($filters): string {
    return cc_url('audit') . '?' . http_build_query(array_replace($filters, ['page' => $target]));
};
?>
<section class="module-stack">
    <section class="panel">
        <div class="panel__header"><div><span class="eyebrow">Operations audit authority</span><h2>بحث سجل الإدارة</h2><p class="muted-copy">نافذة افتراضية 30 يومًا، وبحد أقصى 366 يومًا و<?= (int) ($pageData['maximum_rows'] ?? 10000) ?> نتيجة لكل بحث ثابت.</p></div><?php cc_status_badge('بدون أسرار', 'success'); ?></div>
        <?php if (($pageData['filter_notice'] ?? '') !== ''): ?><?php cc_alert('تم ضبط نطاق البحث', (string) $pageData['filter_notice'], 'warning'); ?><?php endif; ?>
        <form class="audit-filters" method="get" aria-label="بحث وتصفية سجل الإدارة" data-saved-filters="audit" data-saved-filter-fields="date_from,date_to,reseller_id">
            <label class="form-field"><span>من تاريخ</span><input name="date_from" type="date" value="<?= cc_e($filters['date_from'] ?? '') ?>" required></label>
            <label class="form-field"><span>إلى تاريخ</span><input name="date_to" type="date" value="<?= cc_e($filters['date_to'] ?? '') ?>" required></label>
            <label class="form-field"><span>المسؤول</span><input name="admin" maxlength="64" value="<?= cc_e($filters['admin'] ?? '') ?>" placeholder="اسم المسؤول"></label>
            <label class="form-field"><span>بادئة الإجراء</span><input name="action" maxlength="80" dir="ltr" value="<?= cc_e($filters['action'] ?? '') ?>" placeholder="CONTROL_CENTER_"></label>
            <label class="form-field"><span>رقم الموزع</span><input name="reseller_id" type="number" min="1" value="<?= cc_e($filters['reseller_id'] ?? '') ?>" placeholder="مثال: 7"></label>
            <div class="presence-filter__actions"><button class="button button--primary" type="submit">بحث</button><a class="button button--quiet" href="<?= cc_e(cc_url('audit')) ?>">مسح الفلاتر</a></div>
            <?php cc_saved_filter_controls('audit', ['date_from', 'date_to', 'reseller_id']); ?>
        </form>
    </section>

    <section class="panel">
        <div class="panel__header"><div><h2>نتائج التدقيق</h2><p class="muted-copy">الترتيب ثابت بالمعرّف، والصفحات التالية لا تتأثر بسجلات أضيفت بعد بدء البحث.</p></div><div class="row-actions"><span class="record-count"><?= count($rows) ?> سجل في الصفحة</span><?php cc_status_badge('تفاصيل آمنة فقط', 'success'); ?></div></div>
        <?php if ($rows === []): cc_empty_state('لا توجد عمليات مطابقة', 'غيّر نطاق التاريخ أو الفلاتر الآمنة.'); else: ?>
        <div class="table-shell table-shell--responsive"><div class="table-scroll" tabindex="0" aria-label="نتائج سجل الإدارة"><table data-mobile-cards><thead><tr><th>الوقت</th><th>المسؤول</th><th>الإجراء</th><th>التفاصيل غير السرية</th></tr></thead><tbody><?php foreach ($rows as $row): ?><tr><td><?= cc_e($row['created_at']) ?></td><td><?= cc_e($row['username'] ?? 'نظام') ?></td><td><code class="mono"><?= cc_e($row['action']) ?></code></td><td class="audit-details"><?= cc_e($row['details_safe'] ?? '—') ?></td></tr><?php endforeach; ?></tbody></table></div><nav class="pagination" aria-label="ترقيم سجل الإدارة"><p>الصفحة <?= $page ?> · حتى <?= (int) ($pageData['page_size'] ?? 50) ?> سجلًا</p><div><?php if (!empty($pageData['has_previous'])): ?><a class="page-button" href="<?= cc_e($pageUrl($page - 1)) ?>" aria-label="الصفحة السابقة"><?= cc_icon('chevron') ?></a><?php endif; ?><span class="page-button" aria-current="page"><?= $page ?></span><?php if (!empty($pageData['has_next'])): ?><a class="page-button icon-button--flip" href="<?= cc_e($pageUrl($page + 1)) ?>" aria-label="الصفحة التالية"><?= cc_icon('chevron') ?></a><?php endif; ?></div></nav></div>
        <?php endif; ?>
    </section>
</section>
