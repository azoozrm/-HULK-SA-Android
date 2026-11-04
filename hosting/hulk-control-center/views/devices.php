<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل الاجهزة', 'تعذر الاتصال بخدمة البيانات اعد المحاولة', cc_url('devices'));
    return;
}

$rows = is_array($pageData['rows'] ?? null) ? $pageData['rows'] : [];
$filters = is_array($pageData['filters'] ?? null) ? $pageData['filters'] : [];
$platformLabels = ['PHONE' => 'هاتف', 'TABLET' => 'جهاز لوحي', 'TV' => 'تلفاز', 'OTHER' => 'جهاز اخر'];
$paginationUrl = static function (int $page) use ($filters): string {
    $query = cc_presence_query_parameters($filters, ['page' => $page]);
    return cc_url('devices') . ($query === [] ? '' : '?' . http_build_query($query));
};
$hasActiveFilters = trim((string) ($filters['q'] ?? '')) !== ''
    || trim((string) ($filters['app_version'] ?? '')) !== ''
    || trim((string) ($filters['platform'] ?? '')) !== '';
?>
<section class="module-stack device-inventory-page">
    <?php cc_page_summary(
        'مخزون التطبيق',
        'الاجهزة المعروفة لـ HULK SA',
        'هوية الجهاز واصدار التطبيق واخر نشاط تظهر اولًا، بينما يبقى معرّف التثبيت داخل التفاصيل التقنية',
        'device',
        ['label' => 'قراءة تشغيلية', 'tone' => 'info'],
        [
            ['label' => 'اجمالي الاجهزة', 'value' => (string) ((int) ($pageData['total'] ?? 0))],
            ['label' => 'المعروض في الصفحة', 'value' => (string) count($rows)],
            ['label' => 'دلالة السجل', 'value' => 'جهاز معروف', 'hint' => 'لا تعني انه متصل الان'],
        ]
    ); ?>

    <?php cc_filter_disclosure(
        'البحث في الاجهزة',
        $hasActiveFilters ? 'هناك فلاتر مطبقة على المخزون الحالي' : 'ابحث حسب الشركة او الموديل او النظام او اصدار التطبيق',
        $hasActiveFilters
    ); ?>
        <form class="presence-filters presence-filters--devices" method="get" aria-label="بحث وتصفية الاجهزة" data-saved-filters="devices" data-saved-filter-fields="platform,sort,direction">
            <label class="form-field presence-filter--wide"><span>بحث</span><input name="q" type="search" maxlength="100" value="<?= cc_e($filters['q'] ?? '') ?>" placeholder="الشركة، الموديل، Android او معرّف التثبيت"></label>
            <label class="form-field"><span>اصدار التطبيق</span><input name="app_version" maxlength="32" dir="ltr" value="<?= cc_e($filters['app_version'] ?? '') ?>" placeholder="0.9.3.23"></label>
            <label class="form-field"><span>المنصة</span><select name="platform"><option value="">كل المنصات</option><?php foreach ($platformLabels as $value => $label): ?><option value="<?= cc_e($value) ?>" <?= ($filters['platform'] ?? '') === $value ? 'selected' : '' ?>><?= cc_e($label) ?></option><?php endforeach; ?></select></label>
            <label class="form-field"><span>الترتيب</span><select name="sort"><option value="last_seen" <?= ($filters['sort'] ?? '') === 'last_seen' ? 'selected' : '' ?>>اخر ظهور</option><option value="first_seen" <?= ($filters['sort'] ?? '') === 'first_seen' ? 'selected' : '' ?>>اول ظهور</option><option value="app_version" <?= ($filters['sort'] ?? '') === 'app_version' ? 'selected' : '' ?>>اصدار التطبيق</option><option value="device" <?= ($filters['sort'] ?? '') === 'device' ? 'selected' : '' ?>>هوية الجهاز</option></select></label>
            <label class="form-field"><span>الاتجاه</span><select name="direction"><option value="desc" <?= ($filters['direction'] ?? '') === 'desc' ? 'selected' : '' ?>>الاحدث اولًا</option><option value="asc" <?= ($filters['direction'] ?? '') === 'asc' ? 'selected' : '' ?>>الاقدم اولًا</option></select></label>
            <div class="filter-actions"><button class="button button--primary" type="submit">عرض النتائج</button><a class="button button--quiet" href="<?= cc_e(cc_url('devices')) ?>">مسح الفلاتر</a></div>
            <?php cc_saved_filter_controls('devices', ['platform', 'sort', 'direction']); ?>
        </form>
    <?php cc_disclosure_end(); ?>

    <section class="records-section" aria-labelledby="device-records-title">
        <header class="section-heading"><div><span class="eyebrow">الاجهزة المسجلة</span><h2 id="device-records-title">قائمة الاجهزة</h2></div><span class="record-count"><?= (int) ($pageData['total'] ?? 0) ?> جهاز</span></header>
        <?php if ($rows === []): ?>
            <?php cc_empty_state('لا توجد اجهزة مطابقة', 'غيّر الفلاتر او انتظر اول نشاط مسجل من جهاز جديد'); ?>
        <?php else: ?>
            <div class="device-records presence-mobile-list">
                <?php foreach ($rows as $row):
                    $platform = $platformLabels[$row['platform_class']] ?? (string) $row['platform_class'];
                    $deviceName = trim((string) ($row['manufacturer'] . ' ' . $row['model']));
                ?>
                    <details class="record-card device-record compact-details">
                        <summary class="record-card__header">
                            <div class="record-identity">
                                <span class="record-identity__icon"><?= cc_icon('device') ?></span>
                                <span><strong><?= cc_e($deviceName !== '' ? $deviceName : 'جهاز غير مسمى') ?></strong><small><?= cc_e($platform) ?></small></span>
                            </div>
                            <span class="row-meta"><bdi dir="ltr"><?= cc_e($row['latest_app_version_name']) ?></bdi><time dir="ltr"><?= cc_e(cc_presence_display_time($row['last_seen_at'])) ?></time></span>
                        <span class="details-caption">التفاصيل<?= cc_icon('chevron-down') ?></span></summary><div class="compact-details__body">
                        <dl class="record-card__highlights record-card__highlights--three">
                            <div><dt>اصدار التطبيق</dt><dd><bdi dir="ltr"><?= cc_e($row['latest_app_version_name']) ?> (<?= (int) $row['latest_app_version_code'] ?>)</bdi></dd></div>
                            <div><dt>نظام Android</dt><dd><bdi dir="ltr"><?= cc_e($row['android_release']) ?> / SDK <?= (int) $row['android_sdk_int'] ?></bdi></dd></div>
                            <div><dt>اخر نشاط</dt><dd><?= cc_e(cc_presence_display_time($row['last_seen_at'])) ?></dd></div>
                        </dl>
                        <details class="technical-disclosure">
                            <summary>السجل التقني للجهاز</summary>
                            <div class="technical-disclosure__body">
                                <?php cc_fact_list([
                                    ['label' => 'معرّف التثبيت', 'value' => (string) $row['installation_id'], 'ltr' => true],
                                    ['label' => 'اول ظهور', 'value' => cc_presence_display_time($row['first_seen_at'])],
                                    ['label' => 'اخر ظهور', 'value' => cc_presence_display_time($row['last_seen_at'])],
                                ], 'fact-list--technical'); ?>
                            </div>
                        </details>
                    </div></details>
                <?php endforeach; ?>
            </div>
            <?php $page = (int) $pageData['page']; $pages = (int) $pageData['pages']; ?><nav class="pagination presence-pagination" aria-label="ترقيم صفحات الاجهزة"><p>الصفحة <?= $page ?> من <?= $pages ?></p><div><?php if ($page > 1): ?><a class="page-button" href="<?= cc_e($paginationUrl($page - 1)) ?>" aria-label="الصفحة السابقة"><?= cc_icon('chevron') ?></a><?php endif; ?><span class="page-button" aria-current="page"><?= $page ?></span><?php if ($page < $pages): ?><a class="page-button icon-button--flip" href="<?= cc_e($paginationUrl($page + 1)) ?>" aria-label="الصفحة التالية"><?= cc_icon('chevron') ?></a><?php endif; ?></div></nav>
        <?php endif; ?>
    </section>
</section>
