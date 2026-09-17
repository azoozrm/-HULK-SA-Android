<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل سجل الإدارة', 'تحقق من اتصال قاعدة Operations ثم أعد المحاولة.', cc_url('audit'));
    return;
}
$rows = $pageData['audit'] ?? [];
?>
<section class="panel">
    <div class="panel__header"><div><span class="eyebrow">Operations audit authority</span><h2>آخر العمليات الإدارية</h2></div><div class="row-actions"><span class="record-count"><?= (int) ($pageData['total'] ?? 0) ?> سجل</span><?php cc_status_badge('بدون أسرار', 'success'); ?></div></div>
    <?php if ($rows === []): cc_empty_state('لا توجد عمليات مسجلة', 'سيظهر هنا أثر العمليات الإدارية الناجحة.'); else: ?>
    <div class="table-shell"><div class="table-scroll" tabindex="0"><table><thead><tr><th>الوقت</th><th>المسؤول</th><th>الإجراء</th><th>التفاصيل غير السرية</th></tr></thead><tbody><?php foreach ($rows as $row): ?><tr><td><?= cc_e($row['created_at']) ?></td><td><?= cc_e($row['username'] ?? 'نظام') ?></td><td><code class="mono"><?= cc_e($row['action']) ?></code></td><td class="audit-details"><?= cc_e($row['details'] ?? '—') ?></td></tr><?php endforeach; ?></tbody></table></div><?php $page = (int) ($pageData['page'] ?? 1); $pages = (int) ($pageData['pages'] ?? 1); ?><nav class="pagination" aria-label="ترقيم سجل الإدارة"><p>الصفحة <?= $page ?> من <?= $pages ?></p><div><?php if ($page > 1): ?><a class="page-button" href="?page=<?= $page - 1 ?>" aria-label="الصفحة السابقة"><?= cc_icon('chevron') ?></a><?php endif; ?><span class="page-button" aria-current="page"><?= $page ?></span><?php if ($page < $pages): ?><a class="page-button icon-button--flip" href="?page=<?= $page + 1 ?>" aria-label="الصفحة التالية"><?= cc_icon('chevron') ?></a><?php endif; ?></div></nav></div>
    <?php endif; ?>
</section>
