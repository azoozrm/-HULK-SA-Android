<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل بيانات Presence', 'لم تُعرض أي قيم بديلة. تحقق من اتصال قاعدة Control ثم أعد المحاولة.', cc_url($moduleKey));
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
?>
<section class="module-stack presence-module">
    <section class="panel presence-summary">
        <div class="panel__header">
            <div>
                <span class="eyebrow">Presence / وقت الخادم</span>
                <h2><?= $isLive ? 'الجلسات المرصودة الآن' : 'السجل التاريخي للجلسات' ?></h2>
                <p class="muted-copy">Online يعني نبضة حديثة خلال <?= (int) ($pageData['online_ttl_seconds'] ?? 0) ?> ثانية، ولا يعني بالضرورة تشغيل وسائط.</p>
            </div>
            <div class="presence-summary__badges">
                <?php cc_status_badge((string) ((int) ($pageData['total'] ?? 0)) . ' جلسة', 'info'); ?>
                <?php cc_status_badge('وقت الخادم ' . cc_presence_display_time((string) ($pageData['server_now'] ?? '')), 'neutral'); ?>
            </div>
        </div>
        <?php if (!$resellersAvailable && $rows !== []): ?>
            <?php cc_alert('بيانات جزئية', 'بيانات الجلسات متاحة، لكن تعذر ربط أسماء الموزعين الحالية. لم تُفبرك أي قيمة بديلة.', 'warning'); ?>
        <?php endif; ?>
        <form class="presence-filters" method="get" aria-label="بحث وتصفية الجلسات">
            <label class="form-field presence-filter--wide"><span>بحث</span><input name="q" type="search" maxlength="100" value="<?= cc_e($filters['q'] ?? '') ?>" placeholder="الجلسة، الكود، اسم IPTV، الهوست أو الجهاز"></label>
            <label class="form-field"><span>الحالة</span><select name="status"><option value="all" <?= ($filters['status'] ?? '') === 'all' ? 'selected' : '' ?>>الكل</option><option value="online" <?= ($filters['status'] ?? '') === 'online' ? 'selected' : '' ?>>Online</option><option value="offline" <?= ($filters['status'] ?? '') === 'offline' ? 'selected' : '' ?>>Offline</option></select></label>
            <label class="form-field"><span>إصدار التطبيق</span><input name="app_version" maxlength="32" dir="ltr" value="<?= cc_e($filters['app_version'] ?? '') ?>" placeholder="0.9.3.21"></label>
            <label class="form-field"><span>رقم الموزع</span><input name="reseller" type="number" min="1" value="<?= cc_e($filters['reseller'] ?? '') ?>" placeholder="مثال: 7"></label>
            <label class="form-field"><span>الهوست</span><input name="host" maxlength="255" dir="ltr" value="<?= cc_e($filters['host'] ?? '') ?>" placeholder="host.example"></label>
            <label class="form-field"><span>المنصة</span><select name="platform"><option value="">كل المنصات</option><?php foreach ($platformLabels as $value => $label): ?><option value="<?= cc_e($value) ?>" <?= ($filters['platform'] ?? '') === $value ? 'selected' : '' ?>><?= cc_e($label) ?></option><?php endforeach; ?></select></label>
            <label class="form-field"><span>الترتيب</span><select name="sort"><option value="last_seen" <?= ($filters['sort'] ?? '') === 'last_seen' ? 'selected' : '' ?>>آخر اتصال</option><option value="started" <?= ($filters['sort'] ?? '') === 'started' ? 'selected' : '' ?>>بداية الجلسة</option><option value="app_version" <?= ($filters['sort'] ?? '') === 'app_version' ? 'selected' : '' ?>>إصدار التطبيق</option><option value="reseller" <?= ($filters['sort'] ?? '') === 'reseller' ? 'selected' : '' ?>>الموزع</option><option value="device" <?= ($filters['sort'] ?? '') === 'device' ? 'selected' : '' ?>>الجهاز</option></select></label>
            <label class="form-field"><span>الاتجاه</span><select name="direction"><option value="desc" <?= ($filters['direction'] ?? '') === 'desc' ? 'selected' : '' ?>>الأحدث أولًا</option><option value="asc" <?= ($filters['direction'] ?? '') === 'asc' ? 'selected' : '' ?>>الأقدم أولًا</option></select></label>
            <div class="presence-filter__actions"><button class="button button--primary" type="submit">تطبيق</button><a class="button button--quiet" href="<?= cc_e(cc_url($moduleKey)) ?>">مسح الفلاتر</a></div>
        </form>
    </section>

    <section class="panel">
        <div class="panel__header"><div><h2><?= $isLive ? 'المستخدمون الآن' : 'نتائج الجلسات' ?></h2><p class="muted-copy">القيم الحساسة أدناه لقطات تاريخية مباشرة من الجلسة ولا تُنسخ إلى السجل الإداري.</p></div><span class="record-count"><?= (int) ($pageData['total'] ?? 0) ?> سجل</span></div>
        <?php if ($rows === []): ?>
            <?php cc_empty_state($isLive ? 'لا توجد جلسات Online مطابقة' : 'لا توجد جلسات مطابقة', 'غيّر الفلاتر أو انتظر وصول بيانات Presence جديدة.'); ?>
        <?php else: ?>
            <div class="table-shell presence-desktop-table"><div class="table-scroll" tabindex="0" aria-label="بيانات الجلسات"><table class="presence-table"><thead><tr><th>الحالة</th><th>الموزع والجلسة</th><th>بيانات الدخول</th><th>الهوست</th><th>الجهاز والإصدار</th><th>الأوقات</th></tr></thead><tbody>
            <?php foreach ($rows as $row): ?>
                <tr>
                    <td><?php cc_status_badge($row['online'] ? 'Online' : 'Offline', $row['online'] ? 'success' : 'neutral'); ?><small class="cell-note"><?= cc_e($row['ended_at'] === null ? ($row['online'] ? 'نبضة حديثة' : 'تجاوز TTL') : ($endReasonLabels[$row['end_reason']] ?? 'منتهية')) ?></small></td>
                    <td><strong><?= cc_e($resellerLabel($row)) ?></strong><small class="cell-note mono" dir="ltr">#<?= (int) $row['reseller_id'] ?> · <?= cc_e($row['session_id']) ?></small></td>
                    <td><dl class="credential-stack"><div><dt>ACCESS CODE</dt><dd class="credential-value" dir="ltr"><?= cc_e($row['access_code_snapshot']) ?></dd></div><div><dt>IPTV USERNAME</dt><dd class="credential-value" dir="ltr"><?= cc_e($row['iptv_username']) ?></dd></div><div><dt>IPTV PASSWORD</dt><dd class="credential-value" dir="ltr"><?= cc_e($row['iptv_password']) ?></dd></div></dl></td>
                    <td><span class="mono break-value" dir="ltr"><?= cc_e($row['host_snapshot']) ?></span></td>
                    <td><strong><?= cc_e($platformLabels[$row['platform_class']] ?? $row['platform_class']) ?> · <?= cc_e($row['device_manufacturer'] . ' ' . $row['device_model']) ?></strong><small class="cell-note">Android <?= cc_e($row['android_release']) ?> / SDK <?= (int) $row['android_sdk_int'] ?></small><small class="cell-note mono" dir="ltr"><?= cc_e($row['installation_id']) ?></small><small class="cell-note">App <?= cc_e($row['app_version_name']) ?> (<?= (int) $row['app_version_code'] ?>)</small></td>
                    <td><dl class="time-stack"><div><dt>بدأت</dt><dd><?= cc_e(cc_presence_display_time($row['started_at'])) ?></dd></div><div><dt>آخر اتصال</dt><dd><?= cc_e(cc_presence_display_time($row['last_seen_at'])) ?></dd></div><div><dt>انتهت</dt><dd><?= cc_e(cc_presence_display_time($row['ended_at'])) ?></dd></div></dl></td>
                </tr>
            <?php endforeach; ?>
            </tbody></table></div></div>

            <div class="presence-mobile-list" aria-label="بيانات الجلسات للجوال">
            <?php foreach ($rows as $row): ?>
                <article class="presence-card">
                    <header><span><strong><?= cc_e($resellerLabel($row)) ?></strong><small class="mono" dir="ltr">#<?= (int) $row['reseller_id'] ?></small></span><?php cc_status_badge($row['online'] ? 'Online' : 'Offline', $row['online'] ? 'success' : 'neutral'); ?></header>
                    <dl class="presence-card__facts"><div><dt>SESSION ID</dt><dd class="credential-value" dir="ltr"><?= cc_e($row['session_id']) ?></dd></div><div><dt>ACCESS CODE</dt><dd class="credential-value" dir="ltr"><?= cc_e($row['access_code_snapshot']) ?></dd></div><div><dt>IPTV USERNAME</dt><dd class="credential-value" dir="ltr"><?= cc_e($row['iptv_username']) ?></dd></div><div><dt>IPTV PASSWORD</dt><dd class="credential-value" dir="ltr"><?= cc_e($row['iptv_password']) ?></dd></div><div><dt>الهوست</dt><dd class="mono" dir="ltr"><?= cc_e($row['host_snapshot']) ?></dd></div><div><dt>الجهاز</dt><dd><?= cc_e(($platformLabels[$row['platform_class']] ?? $row['platform_class']) . ' · ' . $row['device_manufacturer'] . ' ' . $row['device_model']) ?><small class="cell-note">Android <?= cc_e($row['android_release']) ?> / SDK <?= (int) $row['android_sdk_int'] ?></small><small class="cell-note mono" dir="ltr"><?= cc_e($row['installation_id']) ?></small></dd></div><div><dt>التطبيق</dt><dd><?= cc_e($row['app_version_name']) ?> (<?= (int) $row['app_version_code'] ?>)</dd></div><div><dt>بدأت</dt><dd><?= cc_e(cc_presence_display_time($row['started_at'])) ?></dd></div><div><dt>آخر اتصال</dt><dd><?= cc_e(cc_presence_display_time($row['last_seen_at'])) ?></dd></div><div><dt>انتهت / السبب</dt><dd><?= cc_e(cc_presence_display_time($row['ended_at'])) ?> · <?= cc_e($row['ended_at'] === null ? 'غير منتهية' : ($endReasonLabels[$row['end_reason']] ?? 'منتهية')) ?></dd></div></dl>
                </article>
            <?php endforeach; ?>
            </div>

            <?php $page = (int) $pageData['page']; $pages = (int) $pageData['pages']; ?>
            <nav class="pagination presence-pagination" aria-label="ترقيم صفحات الجلسات"><p>الصفحة <?= $page ?> من <?= $pages ?></p><div><?php if ($page > 1): ?><a class="page-button" href="<?= cc_e($paginationUrl($page - 1)) ?>" aria-label="الصفحة السابقة"><?= cc_icon('chevron') ?></a><?php endif; ?><span class="page-button" aria-current="page"><?= $page ?></span><?php if ($page < $pages): ?><a class="page-button icon-button--flip" href="<?= cc_e($paginationUrl($page + 1)) ?>" aria-label="الصفحة التالية"><?= cc_icon('chevron') ?></a><?php endif; ?></div></nav>
        <?php endif; ?>
    </section>
</section>
