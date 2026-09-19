<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل بيانات الموزعين', 'لم يتم إجراء أي تعديل. تحقق من اتصال قاعدة reseller ثم أعد المحاولة.', cc_url($moduleKey));
    return;
}

$rows = $pageData['rows'] ?? [];
$presenceUsage = is_array($pageData['presence_usage'] ?? null) ? $pageData['presence_usage'] : [];
$presenceUsageAvailable = (bool) ($pageData['presence_usage_available'] ?? false);
$csrf = cc_csrf_token();
?>
<section class="module-stack">
    <?php if ($moduleKey === 'resellers'): ?>
    <section class="panel">
        <div class="panel__header"><div><span class="eyebrow">Reseller DB</span><h2>إنشاء موزع</h2></div><?php cc_status_badge('مالك البيانات', 'success'); ?></div>
        <form class="admin-form admin-form--grid" method="post">
            <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="create_reseller">
            <label class="form-field"><span>اسم دخول الموزع</span><input name="reseller_name" maxlength="100" autocomplete="off" required></label>
            <label class="form-field"><span>كلمة المرور</span><input name="password" type="password" minlength="10" maxlength="256" autocomplete="new-password" required></label>
            <label class="form-field"><span>الهوست (اختياري)</span><input name="host" type="url" maxlength="2048" dir="ltr" placeholder="https://server.example:8080"></label>
            <label class="form-field"><span>كود مخصص (اختياري)</span><input name="access_code" maxlength="64" dir="ltr" placeholder="HULK-XXXX-XXXX-XXXX-XXXX"></label>
            <div class="form-field--wide"><p class="muted-copy">اترك الكود فارغًا لإنشاء كود متوافق تلقائيًا. لا تُسجل كلمات المرور أو الأكواد في سجل الإدارة.</p><button class="button button--primary" type="submit">إنشاء الموزع</button></div>
        </form>
    </section>
    <?php endif; ?>

    <section class="panel">
        <div class="panel__header"><div><h2><?= $moduleKey === 'access-codes' ? 'أكواد الدخول الحالية' : ($moduleKey === 'hosts' ? 'هوستات الموزعين' : 'حسابات الموزعين') ?></h2><p class="muted-copy">البيانات المعروضة مباشرة من قاعدة reseller الموثوقة.</p></div><span class="record-count"><?= (int) ($pageData['total'] ?? 0) ?> سجل</span></div>
        <?php if (!$presenceUsageAvailable): ?>
            <?php cc_alert('استخدام Presence غير متاح', 'بيانات الموزعين الحالية متاحة، لكن تعذر قراءة إحصاءات الجلسات. لم تُعرض أصفار بديلة.', 'warning'); ?>
        <?php endif; ?>
        <form class="data-toolbar" method="get">
            <label class="search-control"><span class="sr-only">بحث</span><?= cc_icon('search') ?><input name="q" type="search" value="<?= cc_e($pageData['search'] ?? '') ?>" placeholder="بحث بالاسم أو الهوست أو الكود"></label>
            <label class="filter-control"><span class="sr-only">الحالة</span><?= cc_icon('filter') ?><select name="status"><option value="">كل الحالات</option><option value="active" <?= ($pageData['status'] ?? '') === 'active' ? 'selected' : '' ?>>نشط</option><option value="inactive" <?= ($pageData['status'] ?? '') === 'inactive' ? 'selected' : '' ?>>متوقف</option></select></label>
            <button class="button button--secondary" type="submit">تطبيق</button>
        </form>

        <?php if ($rows === []): cc_empty_state('لا توجد نتائج', 'غيّر البحث أو أنشئ موزعًا جديدًا.'); else: ?>
        <div class="table-shell"><div class="table-scroll" tabindex="0"><table class="management-table"><thead><tr><th>الموزع</th><th>الحالة</th><th><?= $moduleKey === 'access-codes' ? 'كود الدخول' : ($moduleKey === 'hosts' ? 'الهوست' : 'آخر تحديث') ?></th><th>استخدام Presence</th><th>الإدارة</th></tr></thead><tbody>
        <?php foreach ($rows as $row): $id = (int) $row['reseller_id']; $usage = $presenceUsage[$id] ?? null; ?>
            <tr>
                <td><strong><?= cc_e($row['reseller_name']) ?></strong><br><span class="mono muted-copy">#<?= $id ?></span></td>
                <td><?php cc_status_badge($row['status'] === 'active' ? 'نشط' : 'متوقف', $row['status'] === 'active' ? 'success' : 'danger'); ?></td>
                <?php if ($moduleKey === 'access-codes'): ?>
                    <td><code class="credential-value" dir="ltr"><?= cc_e($row['access_code']) ?></code></td>
                <?php elseif ($moduleKey === 'hosts'): ?>
                    <td><span class="mono break-value" dir="ltr"><?= cc_e($row['host'] !== '' ? $row['host'] : 'غير محدد') ?></span></td>
                <?php else: ?>
                    <td><?= cc_e($row['updated_at']) ?></td>
                <?php endif; ?>
                <td><?php if (!$presenceUsageAvailable || !is_array($usage)): ?><span class="muted-copy">غير متاح</span><?php else: ?><strong>متصل الآن: <?= (int) $usage['online'] ?></strong><small class="cell-note">الجلسات: <?= (int) $usage['sessions'] ?></small><small class="cell-note">آخر استخدام: <?= cc_e(cc_presence_display_time($usage['last_used_at'])) ?></small><?php endif; ?></td>
                <td><?php if ($moduleKey === 'resellers'): ?><div class="stacked-actions"><form method="post" data-confirm="تغيير حالة هذا الموزع؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="set_status"><input type="hidden" name="reseller_id" value="<?= $id ?>"><input type="hidden" name="status" value="<?= $row['status'] === 'active' ? 'inactive' : 'active' ?>"><button class="button <?= $row['status'] === 'active' ? 'button--danger' : 'button--secondary' ?>" type="submit"><?= $row['status'] === 'active' ? 'إيقاف' : 'تفعيل' ?></button></form><details class="inline-editor"><summary>إعادة كلمة المرور</summary><form method="post"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="reset_password"><input type="hidden" name="reseller_id" value="<?= $id ?>"><input name="password" type="password" minlength="10" maxlength="256" autocomplete="new-password" placeholder="كلمة مرور جديدة" required><input name="confirm_password" type="password" minlength="10" maxlength="256" autocomplete="new-password" placeholder="تأكيد كلمة المرور" required><button class="button button--secondary" type="submit">حفظ</button></form></details></div><?php endif; ?><?php if ($moduleKey === 'access-codes'): ?><div class="stacked-actions"><form class="inline-form" method="post"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="set_code"><input type="hidden" name="reseller_id" value="<?= $id ?>"><input name="access_code" maxlength="64" dir="ltr" value="<?= cc_e($row['access_code']) ?>" required><button class="button button--secondary" type="submit">حفظ</button></form><form method="post" data-confirm="سيُوقف الكود الحالي فورًا. هل تريد إنشاء كود جديد؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="rotate_code"><input type="hidden" name="reseller_id" value="<?= $id ?>"><button class="button button--danger" type="submit">تدوير الكود</button></form></div><?php elseif ($moduleKey === 'hosts'): ?><form class="inline-form" method="post"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="update_host"><input type="hidden" name="reseller_id" value="<?= $id ?>"><input name="host" type="url" maxlength="2048" dir="ltr" value="<?= cc_e($row['host']) ?>" placeholder="اتركه فارغًا للمسح"><button class="button button--secondary" type="submit"><?= $row['host'] === '' ? 'إضافة' : 'حفظ/مسح' ?></button></form><?php endif; ?></td>
            </tr>
        <?php endforeach; ?>
        </tbody></table></div>
        <?php $page = (int) $pageData['page']; $pages = (int) $pageData['pages']; $query = ['q' => $pageData['search'], 'status' => $pageData['status']]; ?>
        <nav class="pagination" aria-label="ترقيم الصفحات"><p>الصفحة <?= $page ?> من <?= $pages ?></p><div><?php if ($page > 1): ?><a class="page-button" href="?<?= cc_e(http_build_query($query + ['page' => $page - 1])) ?>" aria-label="الصفحة السابقة"><?= cc_icon('chevron') ?></a><?php endif; ?><span class="page-button" aria-current="page"><?= $page ?></span><?php if ($page < $pages): ?><a class="page-button icon-button--flip" href="?<?= cc_e(http_build_query($query + ['page' => $page + 1])) ?>" aria-label="الصفحة التالية"><?= cc_icon('chevron') ?></a><?php endif; ?></div></nav>
        </div>
        <?php endif; ?>
    </section>
</section>
