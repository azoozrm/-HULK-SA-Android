<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل مخزون الأجهزة', 'لم تُعرض أي قيم بديلة. تحقق من اتصال قاعدة Control ثم أعد المحاولة.', cc_url('devices'));
    return;
}

$rows = is_array($pageData['rows'] ?? null) ? $pageData['rows'] : [];
$filters = is_array($pageData['filters'] ?? null) ? $pageData['filters'] : [];
$platformLabels = ['PHONE' => 'هاتف', 'TABLET' => 'جهاز لوحي', 'TV' => 'تلفاز', 'OTHER' => 'أخرى'];
$paginationUrl = static function (int $page) use ($filters): string {
    $query = cc_presence_query_parameters($filters, ['page' => $page]);
    return cc_url('devices') . ($query === [] ? '' : '?' . http_build_query($query));
};
?>
<section class="module-stack presence-module">
    <section class="panel presence-summary">
        <div class="panel__header"><div><span class="eyebrow">سجل الأجهزة</span><h2>الأجهزة المعروفة للتطبيق</h2><p class="muted-copy">يمثل معرّف التثبيت هوية ثابتة للجهاز. ظهور الجهاز هنا لا يعني أنه متصل الآن.</p></div><div class="presence-summary__badges"><?php cc_status_badge((string) ((int) ($pageData['total'] ?? 0)) . ' جهاز', 'info'); ?><?php cc_status_badge('آخر نشاط مسجل', 'neutral'); ?></div></div>
    <form class="presence-filters presence-filters--devices" method="get" aria-label="بحث وتصفية الأجهزة" data-saved-filters="devices" data-saved-filter-fields="platform,sort,direction">
            <label class="form-field presence-filter--wide"><span>بحث</span><input name="q" type="search" maxlength="100" value="<?= cc_e($filters['q'] ?? '') ?>" placeholder="installation ID، الشركة، الموديل أو Android"></label>
            <label class="form-field"><span>إصدار التطبيق</span><input name="app_version" maxlength="32" dir="ltr" value="<?= cc_e($filters['app_version'] ?? '') ?>" placeholder="0.9.3.21"></label>
            <label class="form-field"><span>المنصة</span><select name="platform"><option value="">كل المنصات</option><?php foreach ($platformLabels as $value => $label): ?><option value="<?= cc_e($value) ?>" <?= ($filters['platform'] ?? '') === $value ? 'selected' : '' ?>><?= cc_e($label) ?></option><?php endforeach; ?></select></label>
            <label class="form-field"><span>الترتيب</span><select name="sort"><option value="last_seen" <?= ($filters['sort'] ?? '') === 'last_seen' ? 'selected' : '' ?>>آخر ظهور</option><option value="first_seen" <?= ($filters['sort'] ?? '') === 'first_seen' ? 'selected' : '' ?>>أول ظهور</option><option value="app_version" <?= ($filters['sort'] ?? '') === 'app_version' ? 'selected' : '' ?>>إصدار التطبيق</option><option value="device" <?= ($filters['sort'] ?? '') === 'device' ? 'selected' : '' ?>>هوية الجهاز</option></select></label>
            <label class="form-field"><span>الاتجاه</span><select name="direction"><option value="desc" <?= ($filters['direction'] ?? '') === 'desc' ? 'selected' : '' ?>>الأحدث أولًا</option><option value="asc" <?= ($filters['direction'] ?? '') === 'asc' ? 'selected' : '' ?>>الأقدم أولًا</option></select></label>
            <div class="presence-filter__actions"><button class="button button--primary" type="submit">تطبيق</button><a class="button button--quiet" href="<?= cc_e(cc_url('devices')) ?>">مسح الفلاتر</a></div>
        <?php cc_saved_filter_controls('devices', ['platform', 'sort', 'direction']); ?>
        </form>
    </section>
    <section class="panel">
        <div class="panel__header"><div><h2>الأجهزة المسجلة</h2><p class="muted-copy">آخر بيانات الجهاز والتطبيق لكل معرّف تثبيت.</p></div><span class="record-count"><?= (int) ($pageData['total'] ?? 0) ?> سجل</span></div>
        <?php if ($rows === []): ?>
            <?php cc_empty_state('لا توجد أجهزة مطابقة', 'غيّر الفلاتر أو انتظر أول نشاط مسجل من الجهاز.'); ?>
        <?php else: ?>
            <div class="table-shell presence-desktop-table"><div class="table-scroll" tabindex="0" aria-label="مخزون الأجهزة"><table class="presence-table presence-device-table"><thead><tr><th>هوية التثبيت</th><th>المنصة والجهاز</th><th>Android</th><th>آخر إصدار</th><th>أول ظهور</th><th>آخر ظهور</th></tr></thead><tbody><?php foreach ($rows as $row): ?><tr><td><code class="credential-value" dir="ltr"><?= cc_e($row['installation_id']) ?></code></td><td><strong><?= cc_e($platformLabels[$row['platform_class']] ?? $row['platform_class']) ?></strong><small class="cell-note"><?= cc_e($row['manufacturer'] . ' ' . $row['model']) ?></small></td><td><?= cc_e($row['android_release']) ?><small class="cell-note">SDK <?= (int) $row['android_sdk_int'] ?></small></td><td><?= cc_e($row['latest_app_version_name']) ?><small class="cell-note">رمز الإصدار <?= (int) $row['latest_app_version_code'] ?></small></td><td><?= cc_e(cc_presence_display_time($row['first_seen_at'])) ?></td><td><?= cc_e(cc_presence_display_time($row['last_seen_at'])) ?></td></tr><?php endforeach; ?></tbody></table></div></div>
            <div class="presence-mobile-list" aria-label="مخزون الأجهزة للجوال"><?php foreach ($rows as $row): ?><article class="presence-card"><header><span><strong><?= cc_e($platformLabels[$row['platform_class']] ?? $row['platform_class']) ?></strong><small><?= cc_e($row['manufacturer'] . ' ' . $row['model']) ?></small></span><?php cc_status_badge('مسجل', 'info'); ?></header><dl class="presence-card__facts"><div><dt>installation_id</dt><dd class="credential-value" dir="ltr"><?= cc_e($row['installation_id']) ?></dd></div><div><dt>Android</dt><dd><?= cc_e($row['android_release']) ?> / SDK <?= (int) $row['android_sdk_int'] ?></dd></div><div><dt>آخر إصدار</dt><dd><?= cc_e($row['latest_app_version_name']) ?> (<?= (int) $row['latest_app_version_code'] ?>)</dd></div><div><dt>أول ظهور</dt><dd><?= cc_e(cc_presence_display_time($row['first_seen_at'])) ?></dd></div><div><dt>آخر ظهور</dt><dd><?= cc_e(cc_presence_display_time($row['last_seen_at'])) ?></dd></div></dl></article><?php endforeach; ?></div>
            <?php $page = (int) $pageData['page']; $pages = (int) $pageData['pages']; ?><nav class="pagination presence-pagination" aria-label="ترقيم صفحات الأجهزة"><p>الصفحة <?= $page ?> من <?= $pages ?></p><div><?php if ($page > 1): ?><a class="page-button" href="<?= cc_e($paginationUrl($page - 1)) ?>" aria-label="الصفحة السابقة"><?= cc_icon('chevron') ?></a><?php endif; ?><span class="page-button" aria-current="page"><?= $page ?></span><?php if ($page < $pages): ?><a class="page-button icon-button--flip" href="<?= cc_e($paginationUrl($page + 1)) ?>" aria-label="الصفحة التالية"><?= cc_icon('chevron') ?></a><?php endif; ?></div></nav>
        <?php endif; ?>
    </section>
</section>
