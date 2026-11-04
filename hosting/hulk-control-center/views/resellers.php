<?php
declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل الهوستات', 'اعد المحاولة', cc_url('hosts'));
    return;
}

$rows = $pageData['rows'] ?? [];
$csrf = cc_csrf_token();
$focusHost = (int) ($pageData['reseller'] ?? 0);
$codeOnlineAvailable = (bool) ($pageData['code_online_available'] ?? true);
$hostPage = (int) $pageData['page'];
$codePage = max(1, (int) ($_GET['code_page'] ?? 1));
$form = $_GET['add'] ?? '';
$hostsUrl = static function (array $query = [], string $anchor = ''): string {
    $url = cc_url('hosts');
    return $url . ($query === [] ? '' : '?' . http_build_query($query)) . $anchor;
};
$context = $focusHost > 0
    ? ['reseller' => $focusHost, 'code_page' => $codePage]
    : ($hostPage > 1 ? ['page' => $hostPage] : []);
$eligibleHosts = array_filter(
    $rows,
    static fn (array $row): bool => hulk_normalize_host((string) $row['host']) !== null
);

$hidden = static function (string $action, array $ids = []) use ($csrf): void { ?>
    <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>">
    <input type="hidden" name="action" value="<?= cc_e($action) ?>">
    <?php foreach ($ids as $key => $id): ?>
        <input type="hidden" name="<?= cc_e($key) ?>" value="<?= (int) $id ?>">
    <?php endforeach;
};
$hostFields = static function (array $row = []): void { ?>
    <label class="form-field"><span>اسم الهوست</span><input name="name" maxlength="100" value="<?= cc_e($row['name'] ?? '') ?>" required></label>
    <label class="form-field"><span>رابط الهوست</span><input name="host" type="url" dir="ltr" maxlength="2048" value="<?= cc_e($row['host'] ?? '') ?>" required placeholder="https://server.example:8080"></label>
    <label class="form-field"><span>الحالة</span><select name="status">
        <option value="active" <?= ($row['status'] ?? 'active') === 'active' ? 'selected' : '' ?>>نشط</option>
        <option value="inactive" <?= ($row['status'] ?? '') === 'inactive' ? 'selected' : '' ?>>متوقف</option>
    </select></label>
<?php }; ?>

<section class="module-stack host-code-page">
    <form class="cc-subscription-search cc-code-search" method="post" role="search" aria-label="البحث عن كود">
        <?php $hidden('find_code'); ?>
        <label class="cc-search-field"><?= cc_icon('search') ?><input type="text" name="access_code" dir="ltr" maxlength="64" autocomplete="off" required placeholder="كود الدخول كاملًا" aria-label="كود الدخول كاملًا"></label>
        <button class="button button--secondary" type="submit">بحث عن كود</button>
    </form>

    <div class="host-actions" aria-label="إجراءات الهوستات والأكواد">
        <a class="button button--primary" href="<?= cc_e($hostsUrl($context + ['add' => 'host'], '#host-editor')) ?>"><?= cc_icon('plus') ?>إضافة هوست</a>
        <a class="button button--secondary" href="<?= cc_e($hostsUrl($context + ['add' => 'code'], '#host-editor')) ?>"><?= cc_icon('key') ?>إضافة كود</a>
    </div>

    <?php if ($form === 'host' || $form === 'code'): ?>
        <section class="card host-editor" id="host-editor" aria-labelledby="host-editor-title">
            <header class="card-head">
                <h2 id="host-editor-title"><?= $form === 'host' ? 'إضافة هوست' : 'إضافة كود' ?></h2>
                <a class="button button--quiet" href="<?= cc_e($hostsUrl($context)) ?>">إلغاء</a>
            </header>
            <?php if ($form === 'host'): ?>
                <form class="admin-form admin-form--grid" method="post" data-submit-once>
                    <?php $hidden('create_host'); $hostFields(); ?>
                    <button class="button button--primary" type="submit">حفظ الهوست</button>
                </form>
            <?php elseif ($eligibleHosts !== []): ?>
                <form class="admin-form admin-form--grid" method="post" data-submit-once>
                    <?php $hidden('create_code'); ?>
                    <label class="form-field"><span>الهوست</span><select name="host_id" required>
                        <option value="">اختر الهوست</option>
                        <?php foreach ($eligibleHosts as $row): ?>
                            <option value="<?= (int) $row['host_id'] ?>" <?= $focusHost === (int) $row['host_id'] ? 'selected' : '' ?>>
                                <?= cc_e($row['name'] . ' · ' . $row['host']) ?>
                            </option>
                        <?php endforeach; ?>
                    </select></label>
                    <label class="form-field"><span>كود مخصص <small>اتركه فارغا لتوليد كود</small></span><input name="access_code" maxlength="64" dir="ltr" autocomplete="off"></label>
                    <button class="button button--primary" type="submit">إنشاء الكود</button>
                </form>
                <?php if ($pageData['pages'] > 1): ?>
                    <p class="muted-copy">تظهر هنا هوستات الصفحة الحالية. للهوستات الاخرى انتقل للصفحة المطلوبة واختر «إضافة كود» عند الهوست.</p>
                <?php endif; ?>
            <?php else: ?>
                <p class="muted-copy">تحتاج الى هوست برابط صالح قبل إنشاء كود.</p>
                <a class="button button--secondary" href="<?= cc_e($hostsUrl($context + ['add' => 'host'], '#host-editor')) ?>">إضافة هوست</a>
            <?php endif; ?>
        </section>
    <?php endif; ?>

    <header class="host-list-heading">
        <h2>الهوستات <small class="record-count"><?= (int) $pageData['total'] ?></small></h2>
        <?php if ($focusHost > 0 || $pageData['search'] !== '' || $pageData['status'] !== ''): ?>
            <a class="button button--quiet" href="<?= cc_e($hostsUrl()) ?>">عرض كل الهوستات</a>
        <?php endif; ?>
    </header>
    <?php if ($rows === []): ?>
        <?php cc_empty_state('لا توجد هوستات', 'اضغط «إضافة هوست» للبدء'); ?>
    <?php endif; ?>

    <div class="host-records">
    <?php foreach ($rows as $row):
        $id = (int) $row['host_id'];
        $valid = hulk_normalize_host((string) $row['host']) !== null;
        $usage = $pageData['presence_usage'][$id] ?? null;
    ?>
        <article class="card host-record cc-host-card">
            <header class="card-head host-record-heading">
                <div>
                    <h3><?= cc_icon('server') ?><?= cc_e($row['name']) ?></h3>
                    <p><bdi dir="ltr"><?= cc_e($row['host'] !== '' ? $row['host'] : 'رابط غير محدد') ?></bdi></p>
                </div>
                <div class="host-actions">
                    <?php cc_status_badge($row['status'] === 'active' ? 'نشط' : 'متوقف', $row['status'] === 'active' ? 'success' : 'neutral'); ?>
                    <?php if ($valid): ?>
                        <a class="button button--secondary" href="<?= cc_e($hostsUrl(['reseller' => $id, 'add' => 'code'], '#host-editor')) ?>"><?= cc_icon('plus') ?>إضافة كود</a>
                    <?php endif; ?>
                </div>
            </header>
            <div class="host-details">
                <h4>أكواد الدخول <small class="record-count"><?= (int) $row['codes_total'] ?></small></h4>
                <?php if (!$valid): ?>
                    <p class="muted-copy">صحح رابط الهوست من «إدارة الهوست» قبل إضافة كود.</p>
                <?php endif; ?>
                <?php if ($row['codes'] === []): ?>
                    <p class="muted-copy">لا توجد أكواد لهذا الهوست.</p>
                <?php else: ?>
                    <div class="table-wrap">
                        <table class="host-code-table" aria-label="أكواد الدخول">
                            <thead><tr><th scope="col">كود الدخول</th><th scope="col">الحالة</th><th scope="col">آخر استخدام</th><th scope="col">المتصلون الآن</th><th scope="col">الإجراءات</th></tr></thead>
                            <tbody>
                            <?php foreach ($row['codes'] as $code):
                                $cid = (int) $code['code_id'];
                                $active = $code['status'] === 'active';
                                $codeUsage = $row['code_usage'][$cid] ?? null;
                            ?>
                                <tr class="code-row">
                                    <td class="cc-code-value" data-label="كود الدخول"><code class="credential-value" dir="ltr"><?= cc_e($code['access_code'] !== '' ? $code['access_code'] : 'غير متاح') ?></code></td>
                                    <td data-label="الحالة"><?php cc_status_badge($active ? ($row['status'] === 'active' ? 'نشط' : 'نشط، الهوست متوقف') : 'متوقف', $active ? 'success' : 'neutral'); ?></td>
                                    <td data-label="آخر استخدام">
                                        <?= is_array($codeUsage) ? cc_e(cc_presence_display_time($codeUsage['last_used_at'])) : 'غير متاح' ?>
                                        <?php if (is_array($codeUsage)): ?><small class="muted-copy"><?= (int) $codeUsage['online'] ?> جلسة حديثة</small><?php endif; ?>
                                    </td>
                                    <td class="cc-code-live" data-label="المتصلون الآن">
                                        <?php
                                        $codeOnline = is_array($row['code_online'] ?? null) ? $row['code_online'] : [];
                                        $onlineSubscriptions = array_key_exists((string) $code['access_code'], $codeOnline) ? (int) $codeOnline[(string) $code['access_code']] : 0;
                                        ?>
                                        <?= $codeOnlineAvailable ? $onlineSubscriptions : 'غير متاح' ?>
                                    </td>
                                    <td class="cc-code-actions"><details class="inline-editor cc-code-edit"><summary><?= cc_icon('edit') ?>تعديل الكود</summary>
                                        <form method="post" data-confirm="سيتم استبدال هذا الكود فقط ، هل تريد المتابعة؟">
                                            <?php $hidden('set_code', ['code_id' => $cid]); ?>
                                            <label class="form-field"><span>القيمة الجديدة</span><input name="access_code" maxlength="64" dir="ltr" required autocomplete="off"></label>
                                            <button type="submit" class="button button--secondary">حفظ الكود</button>
                                        </form></details><details class="inline-editor host-code-actions">
                                        <summary>إدارة الكود</summary>
                                        <form method="post" data-submit-once>
                                            <?php $hidden('set_code_status', ['code_id' => $cid]); ?>
                                            <input type="hidden" name="status" value="<?= $active ? 'inactive' : 'active' ?>">
                                            <button type="submit" class="button button--secondary"><?= $active ? 'إيقاف الكود' : 'تفعيل الكود' ?></button>
                                        </form>

                                        <form method="post" data-confirm="سيتم استبدال هذا الكود فقط بكود مولد ، هل تريد المتابعة؟">
                                            <?php $hidden('rotate_code', ['code_id' => $cid]); ?>
                                            <button type="submit" class="button button--quiet">توليد بديل لهذا الكود</button>
                                        </form>
                                    </details></td>
                                </tr>
                            <?php endforeach; ?>
                            </tbody>
                        </table>
                    </div>
                <?php endif; ?>

                <?php if ($row['code_pages'] > 1): ?>
                    <nav class="pagination code-pagination" aria-label="صفحات اكواد الهوست">
                        <span>الصفحة <?= (int) $row['code_page'] ?> من <?= (int) $row['code_pages'] ?></span>
                        <?php foreach ([-1, 1] as $delta):
                            $target = $row['code_page'] + $delta;
                            if ($target < 1 || $target > $row['code_pages']) continue;
                        ?>
                            <a class="button button--quiet" href="<?= cc_e($hostsUrl(['reseller' => $id, 'code_page' => $target])) ?>"><?= $delta < 0 ? 'الأكواد السابقة' : 'الأكواد التالية' ?></a>
                        <?php endforeach; ?>
                    </nav>
                <?php endif; ?>

                <details class="inline-editor host-management">
                    <summary>إدارة الهوست</summary>
                    <p class="muted-copy"><?= is_array($usage) ? (int) $usage['online'] . ' جلسة حديثة، ' . (int) $usage['sessions'] . ' جلسة مسجلة، اخر استخدام ' . cc_e(cc_presence_display_time($usage['last_used_at'])) : 'احصاءات الاستخدام غير متاحة' ?></p>
                    <form class="admin-form admin-form--grid" method="post" data-submit-once>
                        <?php $hidden('update_host', ['host_id' => $id]); $hostFields($row); ?>
                        <button type="submit" class="button button--primary">حفظ التغييرات</button>
                    </form>
                    <a class="button button--quiet" href="<?= cc_e(cc_context_url('sessions', ['reseller' => $id])) ?>">سجل الجلسات</a>
                    <p class="muted-copy">إيقاف الكود يمنع استخدامه للدخول مجددا ولا ينهي بثا قائما.</p>
                </details>
            </div>
        </article>
    <?php endforeach; ?>
    </div>

    <?php if ($pageData['pages'] > 1): ?>
        <nav class="pagination" aria-label="صفحات الهوستات">
            <span>الصفحة <?= $hostPage ?> من <?= (int) $pageData['pages'] ?></span>
            <?php foreach ([-1, 1] as $delta):
                $target = $hostPage + $delta;
                if ($target < 1 || $target > $pageData['pages']) continue;
            ?>
                <a class="button button--quiet" href="<?= cc_e($hostsUrl(['page' => $target])) ?>"><?= $delta < 0 ? 'السابقة' : 'التالية' ?></a>
            <?php endforeach; ?>
        </nav>
    <?php endif; ?>
</section>
