<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل بيانات الموزعين', 'لم يتم إجراء أي تعديل. تحقق من اتصال قاعدة الموزعين ثم أعد المحاولة.', cc_url($moduleKey));
    return;
}

$rows = is_array($pageData['rows'] ?? null) ? $pageData['rows'] : [];
$presenceUsage = is_array($pageData['presence_usage'] ?? null) ? $pageData['presence_usage'] : [];
$presenceUsageAvailable = (bool) ($pageData['presence_usage_available'] ?? false);
$csrf = cc_csrf_token();
$modeClass = match ($moduleKey) {
    'access-codes' => 'access-code-management-page',
    'hosts' => 'host-management-page',
    default => 'reseller-accounts-page',
};
$summary = match ($moduleKey) {
    'access-codes' => [
        'eyebrow' => 'الوصول إلى التطبيق',
        'title' => 'أكواد الدخول الحالية',
        'description' => 'اعرف مالك كل كود وحالته واستخدامه الأخير قبل تغييره أو تدويره.',
        'icon' => 'key',
    ],
    'hosts' => [
        'eyebrow' => 'وجهات الاتصال',
        'title' => 'هوستات الموزعين',
        'description' => 'راجع الهوست الحالي وعلاقته بالموزع ونشاطه، ثم انتقل إلى صحته أو عدّله عند الحاجة.',
        'icon' => 'server',
    ],
    default => [
        'eyebrow' => 'إدارة الموزعين',
        'title' => 'حسابات الموزعين',
        'description' => 'الحالة والوصول والهوست والنشاط مجمعة حول كل حساب بدل توزيعها في جدول عام.',
        'icon' => 'briefcase',
    ],
};
$hasFilters = trim((string) ($pageData['search'] ?? '')) !== ''
    || trim((string) ($pageData['status'] ?? '')) !== ''
    || trim((string) ($pageData['reseller'] ?? '')) !== '';
?>
<section class="module-stack reseller-management <?= cc_e($modeClass) ?>">
    <?php cc_page_summary(
        $summary['eyebrow'],
        $summary['title'],
        $summary['description'],
        $summary['icon'],
        ['label' => 'المصدر المعتمد', 'tone' => 'success'],
        [
            ['label' => 'إجمالي السجلات', 'value' => (string) ((int) ($pageData['total'] ?? 0))],
            ['label' => 'المعروض الآن', 'value' => (string) count($rows)],
            ['label' => 'سياق الاستخدام', 'value' => $presenceUsageAvailable ? 'متاح' : 'غير متاح'],
        ]
    ); ?>

    <?php if ($moduleKey === 'resellers'): ?>
        <details class="form-section">
            <summary><span><?= cc_icon('briefcase') ?></span><span><strong>إنشاء موزع جديد</strong><small>أدخل الهوية الأساسية، ويمكن إضافة الهوست والكود الآن أو لاحقًا.</small></span></summary>
            <form class="admin-form admin-form--grid" method="post">
                <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="create_reseller">
                <label class="form-field"><span>اسم دخول الموزع</span><input name="reseller_name" maxlength="100" autocomplete="off" required></label>
                <label class="form-field"><span>كلمة المرور</span><input name="password" type="password" minlength="10" maxlength="256" autocomplete="new-password" required></label>
                <label class="form-field"><span>الهوست <small>اختياري</small></span><input name="host" type="url" maxlength="2048" dir="ltr" placeholder="https://server.example:8080"></label>
                <label class="form-field"><span>كود مخصص <small>اختياري</small></span><input name="access_code" maxlength="64" dir="ltr" placeholder="HULK-XXXX-XXXX-XXXX-XXXX"></label>
                <div class="form-submit form-field--wide"><p class="muted-copy">عند ترك الكود فارغًا ينشئ النظام كودًا متوافقًا تلقائيًا. لا تُنسخ كلمات المرور أو الأكواد إلى سجل الإدارة.</p><button class="button button--primary" type="submit">إنشاء الموزع</button></div>
            </form>
        </details>
    <?php endif; ?>

    <?php if (!$presenceUsageAvailable): ?>
        <?php cc_alert('سياق الاستخدام غير متاح', 'بيانات الموزعين الحالية متاحة، لكن تعذر قراءة الجلسات. لم تُعرض أصفار بديلة.', 'warning'); ?>
    <?php endif; ?>

    <?php cc_filter_disclosure(
        'البحث والتصفية',
        $hasFilters ? 'هناك فلاتر مطبقة على القائمة الحالية.' : 'ابحث بالاسم أو الهوست أو الكود، أو حدد حالة الحساب.',
        $hasFilters
    ); ?>
        <form class="data-toolbar data-toolbar--saved" method="get" data-saved-filters="<?= cc_e($moduleKey) ?>" data-saved-filter-fields="status,reseller">
            <label class="search-control"><span class="sr-only">بحث</span><?= cc_icon('search') ?><input name="q" type="search" value="<?= cc_e($pageData['search'] ?? '') ?>" placeholder="بحث بالاسم أو الهوست أو الكود"></label>
            <label class="filter-control"><span class="sr-only">الحالة</span><?= cc_icon('filter') ?><select name="status"><option value="">كل الحالات</option><option value="active" <?= ($pageData['status'] ?? '') === 'active' ? 'selected' : '' ?>>نشط</option><option value="inactive" <?= ($pageData['status'] ?? '') === 'inactive' ? 'selected' : '' ?>>متوقف</option></select></label>
            <label class="form-field reseller-id-filter"><span>رقم الموزع</span><input name="reseller" type="number" min="1" value="<?= cc_e($pageData['reseller'] ?? '') ?>" placeholder="مثال: 7"></label>
            <div class="filter-actions"><button class="button button--primary" type="submit">عرض النتائج</button><a class="button button--quiet" href="<?= cc_e(cc_url($moduleKey)) ?>">مسح الفلاتر</a></div>
            <?php cc_saved_filter_controls($moduleKey, ['status', 'reseller']); ?>
        </form>
    <?php cc_disclosure_end(); ?>

    <section class="management-summary records-section" aria-labelledby="management-records-title">
        <header class="section-heading"><div><span class="eyebrow">النتائج</span><h2 id="management-records-title"><?= cc_e($summary['title']) ?></h2></div><span class="record-count"><?= (int) ($pageData['total'] ?? 0) ?> سجل</span></header>
        <?php if ($rows === []): ?>
            <?php cc_empty_state('لا توجد نتائج', $moduleKey === 'resellers' ? 'غيّر البحث أو أنشئ موزعًا جديدًا.' : 'غيّر البحث أو راجع حسابات الموزعين الحالية.'); ?>
        <?php else: ?>
            <div class="management-records">
                <?php foreach ($rows as $row):
                    $id = (int) $row['reseller_id'];
                    $usage = $presenceUsage[$id] ?? null;
                    $active = $row['status'] === 'active';
                    $usageValue = !$presenceUsageAvailable || !is_array($usage)
                        ? 'غير متاح'
                        : ((int) $usage['online'] . ' متصل الآن');
                ?>
                    <article class="record-card management-record">
                        <header class="record-card__header">
                            <div class="record-identity">
                                <span class="record-identity__icon"><?= cc_icon($summary['icon']) ?></span>
                                <span><strong><?= cc_e($row['reseller_name']) ?></strong><small><bdi dir="ltr">#<?= $id ?></bdi></small></span>
                            </div>
                            <?php cc_status_badge($active ? 'نشط' : 'متوقف', $active ? 'success' : 'danger'); ?>
                        </header>

                        <?php if ($moduleKey === 'access-codes'): ?>
                            <div class="credential-block">
                                <span>الكود الحالي</span>
                        <code class="credential-value" dir="ltr"><?= cc_e($row['access_code'] !== '' ? $row['access_code'] : 'غير محدد') ?></code>
                            </div>
                            <dl class="record-card__highlights record-card__highlights--three">
                                <div><dt>الجاهزية</dt><dd><?= $active && $row['access_code'] !== '' ? 'الكود مرتبط بحساب نشط' : 'يحتاج إلى مراجعة' ?></dd></div>
                                <div><dt>الاستخدام</dt><dd><?= cc_e($usageValue) ?></dd></div>
                                <div><dt>آخر استخدام</dt><dd><?= is_array($usage) ? cc_e(cc_presence_display_time($usage['last_used_at'])) : 'غير متاح' ?></dd></div>
                            </dl>
                            <div class="record-actions">
                                <details class="inline-editor"><summary>تعديل الكود</summary><form method="post"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="set_code"><input type="hidden" name="reseller_id" value="<?= $id ?>"><label class="form-field"><span>الكود الجديد</span><input name="access_code" maxlength="64" dir="ltr" value="<?= cc_e($row['access_code']) ?>" required></label><button class="button button--secondary" type="submit">حفظ الكود</button></form></details>
                                <form method="post" data-confirm="سيُوقف الكود الحالي فورًا. هل تريد إنشاء كود جديد؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="rotate_code"><input type="hidden" name="reseller_id" value="<?= $id ?>"><button class="button button--danger" type="submit">تدوير الكود</button></form>
                            </div>
                        <?php elseif ($moduleKey === 'hosts'): ?>
                            <div class="host-block">
                                <span>الهوست الحالي</span>
                                <bdi dir="ltr"><?= cc_e($row['host'] !== '' ? $row['host'] : 'غير محدد') ?></bdi>
                            </div>
                            <dl class="record-card__highlights record-card__highlights--three">
                                <div><dt>الجاهزية</dt><dd><?= $active && $row['host'] !== '' ? 'مربوط بحساب نشط' : 'يحتاج إلى مراجعة' ?></dd></div>
                                <div><dt>النشاط</dt><dd><?= cc_e($usageValue) ?></dd></div>
                                <div><dt>الجلسات</dt><dd><?= is_array($usage) ? (int) $usage['sessions'] : 'غير متاح' ?></dd></div>
                            </dl>
                            <div class="context-links"><a href="<?= cc_e(cc_url('host-health')) ?>">عرض صحة الهوستات</a><a href="<?= cc_e(cc_context_url('sessions', ['reseller' => $id])) ?>">عرض الجلسات</a></div>
                            <div class="record-actions">
                                <details class="inline-editor"><summary><?= $row['host'] === '' ? 'إضافة هوست' : 'تعديل الهوست' ?></summary><form method="post"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="update_host"><input type="hidden" name="reseller_id" value="<?= $id ?>"><label class="form-field"><span>الهوست</span><input name="host" type="url" maxlength="2048" dir="ltr" value="<?= cc_e($row['host']) ?>" placeholder="اتركه فارغًا للمسح"></label><button class="button button--secondary" type="submit">حفظ التغيير</button></form></details>
                            </div>
                        <?php else: ?>
                            <dl class="record-card__highlights">
                                <div><dt>الهوست</dt><dd class="break-value"><bdi dir="ltr"><?= cc_e($row['host'] !== '' ? $row['host'] : 'غير محدد') ?></bdi></dd></div>
                                <div><dt>كود الدخول</dt><dd><bdi dir="ltr"><?= cc_e($row['access_code'] !== '' ? $row['access_code'] : 'غير محدد') ?></bdi></dd></div>
                                <div><dt>النشاط</dt><dd><?= cc_e($usageValue) ?></dd></div>
                                <div><dt>آخر استخدام</dt><dd><?= is_array($usage) ? cc_e(cc_presence_display_time($usage['last_used_at'])) : 'غير متاح' ?></dd></div>
                            </dl>
                            <div class="record-actions">
                                <form method="post" data-confirm="تغيير حالة هذا الموزع؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="set_status"><input type="hidden" name="reseller_id" value="<?= $id ?>"><input type="hidden" name="status" value="<?= $active ? 'inactive' : 'active' ?>"><button class="button <?= $active ? 'button--danger' : 'button--primary' ?>" type="submit"><?= $active ? 'إيقاف الحساب' : 'تفعيل الحساب' ?></button></form>
                                <details class="inline-editor"><summary>تغيير كلمة المرور</summary><form method="post"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="reset_password"><input type="hidden" name="reseller_id" value="<?= $id ?>"><label class="form-field"><span>كلمة المرور الجديدة</span><input name="password" type="password" minlength="10" maxlength="256" autocomplete="new-password" required></label><label class="form-field"><span>تأكيد كلمة المرور</span><input name="confirm_password" type="password" minlength="10" maxlength="256" autocomplete="new-password" required></label><button class="button button--secondary" type="submit">حفظ كلمة المرور</button></form></details>
                            </div>
                        <?php endif; ?>
                    </article>
                <?php endforeach; ?>
            </div>

            <?php $page = (int) $pageData['page']; $pages = (int) $pageData['pages']; $query = ['q' => $pageData['search'], 'status' => $pageData['status'], 'reseller' => $pageData['reseller']]; ?>
            <nav class="pagination" aria-label="ترقيم الصفحات"><p>الصفحة <?= $page ?> من <?= $pages ?></p><div><?php if ($page > 1): ?><a class="page-button" href="?<?= cc_e(http_build_query($query + ['page' => $page - 1])) ?>" aria-label="الصفحة السابقة"><?= cc_icon('chevron') ?></a><?php endif; ?><span class="page-button" aria-current="page"><?= $page ?></span><?php if ($page < $pages): ?><a class="page-button icon-button--flip" href="?<?= cc_e(http_build_query($query + ['page' => $page + 1])) ?>" aria-label="الصفحة التالية"><?= cc_icon('chevron') ?></a><?php endif; ?></div></nav>
        <?php endif; ?>
    </section>
</section>
