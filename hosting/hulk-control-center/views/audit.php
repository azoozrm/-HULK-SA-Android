<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل النشاط الإداري', 'تحقق من اتصال قاعدة بيانات مركز التحكم ثم أعد المحاولة.', cc_url('audit'));
    return;
}

$rows = is_array($pageData['audit'] ?? null) ? $pageData['audit'] : [];
$filters = is_array($pageData['filters'] ?? null) ? $pageData['filters'] : [];
$page = (int) ($pageData['page'] ?? 1);
$activeFilterCount = count(array_filter($filters, static fn (mixed $value, string $key): bool =>
    $key !== 'page' && $value !== '' && $value !== null
, ARRAY_FILTER_USE_BOTH));
$pageUrl = static function (int $target) use ($filters): string {
    return cc_url('audit') . '?' . http_build_query(array_replace($filters, ['page' => $target]));
};
?>
<section class="module-stack audit-module activity-browser-page">
    <?php cc_page_summary(
        'سجل الإدارة',
        'النشاط الإداري',
        'اقرأ التغييرات الأخيرة بلغة واضحة، وابحث في السجل فقط عندما تحتاج إلى عملية محددة.',
        'audit',
        ['label' => $activeFilterCount === 0 ? 'آخر التغييرات' : 'نتائج مفلترة', 'tone' => $activeFilterCount === 0 ? 'info' : 'warning'],
        [
            ['label' => 'العمليات الظاهرة', 'value' => (string) count($rows)],
            ['label' => 'الصفحة', 'value' => (string) $page],
            ['label' => 'فلاتر نشطة', 'value' => (string) $activeFilterCount],
        ]
    ); ?>

    <?php if (($pageData['filter_notice'] ?? '') !== ''): ?>
        <?php cc_alert('تم ضبط نطاق البحث', (string) $pageData['filter_notice'], 'warning'); ?>
    <?php endif; ?>

    <section class="panel audit-results">
        <div class="panel__header"><div><span class="eyebrow">الأحدث أولًا</span><h2><?= $activeFilterCount === 0 ? 'آخر ما تغير' : 'العمليات المطابقة' ?></h2><p class="muted-copy">العنوان والمعنى في الواجهة أولًا؛ المعرّف التقني مطوي داخل كل عملية.</p></div><span class="record-count"><?= count($rows) ?> عملية</span></div>
        <?php if ($rows === []): ?>
            <?php cc_empty_state('لا توجد عمليات مطابقة', $activeFilterCount === 0 ? 'ستظهر هنا التغييرات الإدارية عند تنفيذها.' : 'جرّب نطاقًا زمنيًا أوسع أو أزل بعض الفلاتر.'); ?>
        <?php else: ?>
            <div class="audit-timeline" role="list">
                <?php foreach ($rows as $row):
                    $action = is_array($row['action_presentation'] ?? null)
                        ? $row['action_presentation']
                        : cc_phase8_audit_action_presentation((string) ($row['action'] ?? ''));
                    $details = is_array($row['details_safe'] ?? null)
                        ? $row['details_safe']
                        : ['rows' => [], 'technical_json' => null, 'state' => 'hidden'];
                    $detailRows = is_array($details['rows'] ?? null) ? $details['rows'] : [];
                    $visibleRows = array_values(array_filter($detailRows, static fn (array $detail): bool => !($detail['technical'] ?? false)));
                    $isKnownAction = ($action['known'] ?? false) === true;
                ?>
                    <article class="audit-event" role="listitem">
                        <div class="audit-event__marker audit-event__marker--<?= cc_e((string) ($action['tone'] ?? 'neutral')) ?>"><?= cc_icon((string) ($action['icon'] ?? 'audit')) ?></div>
                        <div class="audit-event__body">
                            <header class="audit-event__header">
                                <div>
                                    <h3 class="audit-event__title"><?= cc_e((string) ($action['title'] ?? 'عملية إدارية')) ?></h3>
                                    <p class="audit-event__meta"><span><?= cc_icon('users') ?><?= cc_e((string) (($row['username'] ?? null) ?: 'النظام')) ?></span><time datetime="<?= cc_e((string) ($row['created_at'] ?? '')) ?>"><?= cc_e((string) ($row['created_at'] ?? '—')) ?></time></p>
                                </div>
                                <?php cc_status_badge($isKnownAction ? 'عملية معروفة' : 'غير مصنف', $isKnownAction ? (string) ($action['tone'] ?? 'neutral') : 'neutral'); ?>
                            </header>
                            <?php if ($visibleRows !== []): ?>
                                <dl class="audit-event__facts">
                                    <?php foreach ($visibleRows as $detail): ?><div><dt><?= cc_e((string) ($detail['label'] ?? 'تفصيل')) ?></dt><dd><bdi dir="<?= cc_e((string) ($detail['direction'] ?? 'auto')) ?>"><?= cc_e((string) ($detail['value'] ?? '—')) ?></bdi></dd></div><?php endforeach; ?>
                                </dl>
                            <?php elseif (($details['state'] ?? '') === 'legacy'): ?>
                                <p class="audit-event__empty">تفاصيل هذه العملية القديمة غير قابلة للعرض الآمن.</p>
                            <?php else: ?>
                                <p class="audit-event__empty">لا توجد تفاصيل إضافية لهذه العملية.</p>
                            <?php endif; ?>
                            <details class="technical-disclosure audit-event__technical">
                                <summary>تفاصيل تقنية</summary>
                                <dl class="audit-event__technical-list">
                                    <div><dt>معرّف الإجراء</dt><dd><code dir="ltr"><?= cc_e((string) ($action['technical_id'] ?? '')) ?></code></dd></div>
                                    <?php foreach ($detailRows as $detail): if (!($detail['technical'] ?? false)) { continue; } ?><div><dt><?= cc_e((string) ($detail['label'] ?? 'تفصيل')) ?></dt><dd><bdi dir="<?= cc_e((string) ($detail['direction'] ?? 'auto')) ?>"><?= cc_e((string) ($detail['value'] ?? '—')) ?></bdi></dd></div><?php endforeach; ?>
                                </dl>
                                <?php if (is_string($details['technical_json'] ?? null)): ?><code class="audit-event__json" dir="ltr"><?= cc_e((string) $details['technical_json']) ?></code><?php endif; ?>
                            </details>
                        </div>
                    </article>
                <?php endforeach; ?>
            </div>
            <nav class="pagination" aria-label="ترقيم النشاط الإداري"><p>الصفحة <?= $page ?></p><div><?php if (!empty($pageData['has_previous'])): ?><a class="page-button" href="<?= cc_e($pageUrl($page - 1)) ?>" aria-label="الصفحة السابقة"><?= cc_icon('chevron') ?></a><?php endif; ?><span class="page-button" aria-current="page"><?= $page ?></span><?php if (!empty($pageData['has_next'])): ?><a class="page-button icon-button--flip" href="<?= cc_e($pageUrl($page + 1)) ?>" aria-label="الصفحة التالية"><?= cc_icon('chevron') ?></a><?php endif; ?></div></nav>
        <?php endif; ?>
    </section>

    <?php cc_filter_disclosure('البحث في النشاط', 'حدد التاريخ أو المسؤول أو الموزع للوصول إلى تغيير بعينه.', $activeFilterCount > 0); ?>
        <form class="audit-filters" method="get" aria-label="بحث وتصفية النشاط الإداري" data-saved-filters="audit" data-saved-filter-fields="date_from,date_to,reseller_id">
            <label class="form-field"><span>من تاريخ</span><input name="date_from" type="date" value="<?= cc_e($filters['date_from'] ?? '') ?>" required></label>
            <label class="form-field"><span>إلى تاريخ</span><input name="date_to" type="date" value="<?= cc_e($filters['date_to'] ?? '') ?>" required></label>
            <label class="form-field"><span>المسؤول</span><input name="admin" maxlength="64" value="<?= cc_e($filters['admin'] ?? '') ?>" placeholder="اسم المسؤول"></label>
            <label class="form-field"><span>رقم الموزع</span><input name="reseller_id" type="number" min="1" value="<?= cc_e($filters['reseller_id'] ?? '') ?>" placeholder="مثال: 7"></label>
            <label class="form-field audit-filter--technical"><span>معرّف الإجراء التقني <small>اختياري</small></span><input name="action" maxlength="80" dir="ltr" value="<?= cc_e($filters['action'] ?? '') ?>" placeholder="CONTROL_CENTER_"></label>
            <div class="filter-actions"><button class="button button--primary" type="submit">عرض النتائج</button><a class="button button--quiet" href="<?= cc_e(cc_url('audit')) ?>">إعادة الضبط</a></div>
            <?php cc_saved_filter_controls('audit', ['date_from', 'date_to', 'reseller_id']); ?>
        </form>
    <?php cc_disclosure_end(); ?>
</section>
