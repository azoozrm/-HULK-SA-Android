<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل نشاط الجلسات', 'لم تُعرض أي قيم بديلة. تحقق من اتصال قاعدة مركز التحكم ثم أعد المحاولة.', cc_url($moduleKey));
    return;
}

$rows = is_array($pageData['rows'] ?? null) ? $pageData['rows'] : [];
$filters = is_array($pageData['filters'] ?? null) ? $pageData['filters'] : [];
$resellers = is_array($pageData['resellers'] ?? null) ? $pageData['resellers'] : [];
$resellersAvailable = (bool) ($pageData['resellers_available'] ?? false);
$isLive = $moduleKey === 'live-users';
$platformLabels = ['PHONE' => 'هاتف', 'TABLET' => 'جهاز لوحي', 'TV' => 'تلفاز', 'OTHER' => 'أخرى'];
$endReasonLabels = [
    'LOGOUT' => 'تسجيل خروج',
    'ACCOUNT_REPLACED' => 'تبديل الحساب',
    'APP_SHUTDOWN' => 'إغلاق التطبيق',
];
$resellerLabel = static function (array $row) use ($resellers): string {
    $id = (int) ($row['reseller_id'] ?? 0);
    $current = $resellers[$id] ?? null;
    return is_array($current) ? (string) ($current['reseller_name'] ?? ('#' . $id)) : '#' . $id;
};
$paginationUrl = static function (int $page) use ($moduleKey, $filters): string {
    $query = cc_presence_query_parameters($filters, ['page' => $page]);
    return cc_url($moduleKey) . ($query === [] ? '' : '?' . http_build_query($query));
};
$hasActiveFilters = trim((string) ($filters['q'] ?? '')) !== ''
    || ($filters['status'] ?? ($isLive ? 'online' : 'all')) !== ($isLive ? 'online' : 'all')
    || trim((string) ($filters['app_version'] ?? '')) !== ''
    || trim((string) ($filters['reseller'] ?? '')) !== ''
    || trim((string) ($filters['host'] ?? '')) !== ''
    || trim((string) ($filters['platform'] ?? '')) !== '';
?>
<section class="module-stack presence-module <?= $isLive ? 'live-users-page' : 'session-history-page' ?>">
    <?php cc_page_summary(
        $isLive ? 'نشاط مباشر' : 'سجل الاتصالات',
        $isLive ? 'من يستخدم التطبيق الآن؟' : 'تاريخ الجلسات في مكان واحد',
        $isLive
            ? 'قائمة سريعة للجلسات التي وصل منها نشاط حديث، مرتبة لتسهيل الفحص من الهاتف.'
            : 'ابحث في تاريخ الاتصال وافتح بيانات الدخول أو المعرّفات التقنية عند الحاجة فقط.',
        $isLive ? 'users' : 'clock',
        ['label' => $isLive ? 'مباشر' : 'سجل محفوظ', 'tone' => $isLive ? 'success' : 'info'],
        [
            ['label' => $isLive ? 'النشطون المطابقون' : 'الجلسات المطابقة', 'value' => (string) ((int) ($pageData['total'] ?? 0))],
            ['label' => 'مهلة النشاط', 'value' => (int) ($pageData['online_ttl_seconds'] ?? 0) . ' ثانية'],
            ['label' => 'وقت الخادم', 'value' => cc_presence_display_time((string) ($pageData['server_now'] ?? ''))],
        ]
    ); ?>

    <?php if (!$resellersAvailable && $rows !== []): ?>
        <?php cc_alert('بيانات جزئية', 'الجلسات متاحة، لكن تعذر ربط أسماء الموزعين الحالية. أرقام الموزعين ما زالت معروضة كما هي.', 'warning'); ?>
    <?php endif; ?>

    <?php cc_filter_disclosure(
        $isLive ? 'تصفية النشاط المباشر' : 'البحث في سجل الجلسات',
        $hasActiveFilters ? 'هناك فلاتر مطبقة على النتائج الحالية.' : 'ابحث حسب الموزع أو الجهاز أو الإصدار أو حالة الجلسة.',
        $hasActiveFilters
    ); ?>
        <form class="presence-filters" method="get" aria-label="بحث وتصفية الجلسات" data-saved-filters="<?= cc_e($moduleKey) ?>" data-saved-filter-fields="status,reseller,platform,sort,direction">
            <label class="form-field presence-filter--wide"><span>بحث</span><input name="q" type="search" maxlength="100" value="<?= cc_e($filters['q'] ?? '') ?>" placeholder="الجلسة، الكود، مستخدم IPTV، الهوست أو الجهاز"></label>
            <label class="form-field"><span>الحالة</span><select name="status"><option value="all" <?= ($filters['status'] ?? '') === 'all' ? 'selected' : '' ?>>الكل</option><option value="online" <?= ($filters['status'] ?? '') === 'online' ? 'selected' : '' ?>>متصلة</option><option value="offline" <?= ($filters['status'] ?? '') === 'offline' ? 'selected' : '' ?>>غير متصلة</option></select></label>
            <label class="form-field"><span>إصدار التطبيق</span><input name="app_version" maxlength="32" dir="ltr" value="<?= cc_e($filters['app_version'] ?? '') ?>" placeholder="0.9.3.23"></label>
            <label class="form-field"><span>رقم الموزع</span><input name="reseller" type="number" min="1" value="<?= cc_e($filters['reseller'] ?? '') ?>" placeholder="مثال: 7"></label>
            <label class="form-field"><span>الهوست</span><input name="host" maxlength="255" dir="ltr" value="<?= cc_e($filters['host'] ?? '') ?>" placeholder="host.example"></label>
            <label class="form-field"><span>المنصة</span><select name="platform"><option value="">كل المنصات</option><?php foreach ($platformLabels as $value => $label): ?><option value="<?= cc_e($value) ?>" <?= ($filters['platform'] ?? '') === $value ? 'selected' : '' ?>><?= cc_e($label) ?></option><?php endforeach; ?></select></label>
            <label class="form-field"><span>الترتيب</span><select name="sort"><option value="last_seen" <?= ($filters['sort'] ?? '') === 'last_seen' ? 'selected' : '' ?>>آخر اتصال</option><option value="started" <?= ($filters['sort'] ?? '') === 'started' ? 'selected' : '' ?>>بداية الجلسة</option><option value="app_version" <?= ($filters['sort'] ?? '') === 'app_version' ? 'selected' : '' ?>>إصدار التطبيق</option><option value="reseller" <?= ($filters['sort'] ?? '') === 'reseller' ? 'selected' : '' ?>>الموزع</option><option value="device" <?= ($filters['sort'] ?? '') === 'device' ? 'selected' : '' ?>>الجهاز</option></select></label>
            <label class="form-field"><span>الاتجاه</span><select name="direction"><option value="desc" <?= ($filters['direction'] ?? '') === 'desc' ? 'selected' : '' ?>>الأحدث أولًا</option><option value="asc" <?= ($filters['direction'] ?? '') === 'asc' ? 'selected' : '' ?>>الأقدم أولًا</option></select></label>
            <div class="filter-actions"><button class="button button--primary" type="submit">عرض النتائج</button><a class="button button--quiet" href="<?= cc_e(cc_url($moduleKey)) ?>">مسح الفلاتر</a></div>
            <?php cc_saved_filter_controls($moduleKey, ['status', 'reseller', 'platform', 'sort', 'direction']); ?>
        </form>
    <?php cc_disclosure_end(); ?>

    <section class="records-section" aria-labelledby="session-records-title">
        <header class="section-heading">
            <div><span class="eyebrow"><?= $isLive ? 'الأحدث نشاطًا أولًا' : 'نتائج البحث' ?></span><h2 id="session-records-title"><?= $isLive ? 'الجلسات النشطة' : 'الجلسات المسجلة' ?></h2></div>
            <span class="record-count"><?= (int) ($pageData['total'] ?? 0) ?> سجل</span>
        </header>
        <?php if ($rows === []): ?>
            <?php cc_empty_state($isLive ? 'لا توجد جلسات نشطة مطابقة' : 'لا توجد جلسات مطابقة', $isLive ? 'ستظهر الجلسات هنا عند وصول نشاط حديث إلى الخادم.' : 'غيّر البحث أو وسّع الفلاتر لعرض جلسات أخرى.'); ?>
        <?php else: ?>
            <div class="session-records presence-mobile-list">
                <?php foreach ($rows as $row):
                    $online = (bool) $row['online'];
                    $lifecycle = $row['ended_at'] === null
                        ? ($online ? 'نشاط حديث' : 'تجاوزت مهلة النشاط')
                        : ($endReasonLabels[$row['end_reason']] ?? 'جلسة منتهية');
                    $deviceName = trim((string) ($row['device_manufacturer'] . ' ' . $row['device_model']));
                ?>
                    <article class="record-card session-record">
                        <header class="record-card__header">
                            <div class="record-identity">
                                <span class="record-identity__icon"><?= cc_icon($row['platform_class'] === 'TV' ? 'dashboard' : 'device') ?></span>
                                <span><strong><?= cc_e($resellerLabel($row)) ?></strong><small><?= cc_e(($platformLabels[$row['platform_class']] ?? $row['platform_class']) . ' · ' . $deviceName) ?></small></span>
                            </div>
                            <div class="record-card__state"><?php cc_status_badge($online ? 'متصلة الآن' : 'غير متصلة', $online ? 'success' : 'neutral'); ?><small><?= cc_e($lifecycle) ?></small></div>
                        </header>

                        <dl class="record-card__highlights">
                            <div><dt>مستخدم IPTV</dt><dd><bdi dir="ltr"><?= cc_e($row['iptv_username']) ?></bdi></dd></div>
                            <div><dt>الإصدار</dt><dd><bdi dir="ltr"><?= cc_e($row['app_version_name']) ?> (<?= (int) $row['app_version_code'] ?>)</bdi></dd></div>
                            <div><dt>آخر اتصال</dt><dd><?= cc_e(cc_presence_display_time($row['last_seen_at'])) ?></dd></div>
                            <div><dt>الهوست</dt><dd class="break-value"><bdi dir="ltr"><?= cc_e($row['host_snapshot']) ?></bdi></dd></div>
                        </dl>

                        <div class="record-card__footer">
                            <details class="credential-disclosure">
                                <summary><?= cc_icon('key') ?><span>بيانات الدخول</span></summary>
                                <dl class="credential-grid">
                                    <div><dt>كود الدخول</dt><dd><bdi dir="ltr"><?= cc_e($row['access_code_snapshot']) ?></bdi></dd></div>
                                    <div><dt>اسم مستخدم IPTV</dt><dd><bdi dir="ltr"><?= cc_e($row['iptv_username']) ?></bdi></dd></div>
                                    <div><dt>كلمة مرور IPTV</dt><dd><bdi dir="ltr"><?= cc_e($row['iptv_password']) ?></bdi></dd></div>
                                </dl>
                            </details>
                            <details class="technical-disclosure">
                                <summary>تفاصيل الجلسة والجهاز</summary>
                                <div class="technical-disclosure__body">
                                    <?php cc_fact_list([
                                        ['label' => 'بدأت', 'value' => cc_presence_display_time($row['started_at'])],
                                        ['label' => 'انتهت', 'value' => cc_presence_display_time($row['ended_at'])],
                                        ['label' => 'Android', 'value' => (string) $row['android_release'] . ' / SDK ' . (int) $row['android_sdk_int'], 'ltr' => true],
                                        ['label' => 'معرّف الجلسة', 'value' => (string) $row['session_id'], 'ltr' => true],
                                        ['label' => 'معرّف التثبيت', 'value' => (string) $row['installation_id'], 'ltr' => true],
                                        ['label' => 'رقم الموزع', 'value' => '#' . (int) $row['reseller_id'], 'ltr' => true],
                                    ], 'fact-list--technical'); ?>
                                </div>
                            </details>
                        </div>
                    </article>
                <?php endforeach; ?>
            </div>

            <?php $page = (int) $pageData['page']; $pages = (int) $pageData['pages']; ?>
            <nav class="pagination presence-pagination" aria-label="ترقيم صفحات الجلسات"><p>الصفحة <?= $page ?> من <?= $pages ?></p><div><?php if ($page > 1): ?><a class="page-button" href="<?= cc_e($paginationUrl($page - 1)) ?>" aria-label="الصفحة السابقة"><?= cc_icon('chevron') ?></a><?php endif; ?><span class="page-button" aria-current="page"><?= $page ?></span><?php if ($page < $pages): ?><a class="page-button icon-button--flip" href="<?= cc_e($paginationUrl($page + 1)) ?>" aria-label="الصفحة التالية"><?= cc_icon('chevron') ?></a><?php endif; ?></div></nav>
        <?php endif; ?>
    </section>
</section>
