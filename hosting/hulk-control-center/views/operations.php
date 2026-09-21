<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل إدارة التطبيق', 'لم يتم إجراء أي تعديل. تحقق من اتصال قاعدة Operations ثم أعد المحاولة.', cc_url($moduleKey));
    return;
}

$csrf = cc_csrf_token();
?>

<?php if ($moduleKey === 'releases'): ?>
    <?php
    $releases = is_array($pageData['releases'] ?? null) ? $pageData['releases'] : [];
    $activeRelease = null;
    foreach ($releases as $candidate) {
        if (!empty($candidate['is_active'])) {
            $activeRelease = $candidate;
            break;
        }
    }
    ?>
    <section class="module-stack release-management-page">
        <?php cc_page_summary(
            'إدارة التحديث',
            $activeRelease === null ? 'لا يوجد إصدار APK نشط' : 'الإصدار النشط ' . (string) $activeRelease['version_name'],
            $activeRelease === null
                ? 'فعّل إصدارًا جاهزًا أو ارفع ملفًا جديدًا قبل تغيير سياسة التحديث.'
                : 'حالة الإصدار الحالي وسياسة وصوله للمستخدمين تظهر هنا قبل أي إجراء.',
            'package',
            ['label' => $activeRelease === null ? 'يحتاج إلى انتباه' : 'نشط الآن', 'tone' => $activeRelease === null ? 'warning' : 'success'],
            [
                ['label' => 'الإصدار الحالي', 'value' => $activeRelease === null ? 'غير متاح' : (string) $activeRelease['version_name'], 'ltr' => true],
                ['label' => 'نوع التحديث', 'value' => $activeRelease === null ? 'غير متاح' : (!empty($activeRelease['required']) ? 'إلزامي' : 'اختياري')],
                ['label' => 'الحد الأدنى', 'value' => $activeRelease === null ? 'غير متاح' : (string) ((int) $activeRelease['minimum_supported_version_code']), 'ltr' => true],
                ['label' => 'سجل الإصدارات', 'value' => count($releases) . ' إصدار'],
            ]
        ); ?>

        <?php if ($activeRelease !== null): ?>
            <section class="release-current panel">
                <header class="panel__header"><div><span class="eyebrow">النسخة المنشورة</span><h2><bdi dir="ltr"><?= cc_e($activeRelease['version_name']) ?></bdi></h2></div><?php cc_status_badge(!empty($activeRelease['required']) ? 'تحديث إلزامي' : 'تحديث اختياري', !empty($activeRelease['required']) ? 'warning' : 'info'); ?></header>
                <?php cc_fact_list([
                    ['label' => 'رمز الإصدار', 'value' => (string) ((int) $activeRelease['version_code']), 'ltr' => true],
                    ['label' => 'الحد الأدنى المدعوم', 'value' => (string) ((int) $activeRelease['minimum_supported_version_code']), 'ltr' => true],
                    ['label' => 'الحالة', 'value' => !empty($activeRelease['enabled']) ? 'متاح للمستخدمين' : 'غير متاح'],
                ], 'fact-list--summary'); ?>
            </section>
        <?php endif; ?>

        <details class="form-section">
            <summary><span><?= cc_icon('package') ?></span><span><strong>رفع إصدار جديد</strong><small>أدخل بيانات النسخة وارفع ملف APK بعد مراجعة سياسة التحديث.</small></span></summary>
            <form class="admin-form admin-form--grid" method="post" enctype="multipart/form-data">
                <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="upload_release">
                <label class="form-field"><span>اسم الإصدار</span><input name="version_name" maxlength="32" dir="ltr" required></label>
                <label class="form-field"><span>رمز الإصدار</span><input name="version_code" type="number" min="1" required></label>
                <label class="form-field"><span>الحد الأدنى المدعوم</span><input name="minimum_supported_version_code" type="number" min="1" required></label>
                <label class="form-field"><span>ملف APK</span><input name="apk" type="file" accept=".apk,application/vnd.android.package-archive" required></label>
                <label class="form-field form-field--wide"><span>ملاحظات الإصدار</span><textarea name="release_notes" maxlength="10000" required></textarea></label>
                <label class="check-control"><input name="required" type="checkbox" value="1"> اجعل التحديث إلزاميًا</label>
                <div class="form-submit"><button class="button button--primary" type="submit">رفع الإصدار</button></div>
            </form>
        </details>

        <section class="records-section">
            <header class="section-heading"><div><span class="eyebrow">السجل</span><h2>كل الإصدارات</h2></div><span class="record-count"><?= count($releases) ?> إصدار</span></header>
            <?php if ($releases === []): ?>
                <?php cc_empty_state('لا توجد إصدارات', 'ارفع أول APK لتكوين سجل الإصدارات.'); ?>
            <?php else: ?>
                <div class="release-records">
                    <?php foreach ($releases as $release): ?>
                        <article class="record-card release-record<?= !empty($release['is_active']) ? ' release-record--active' : '' ?>">
                            <header class="record-card__header">
                                <div class="record-identity"><span class="record-identity__icon"><?= cc_icon('package') ?></span><span><strong><bdi dir="ltr"><?= cc_e($release['version_name']) ?></bdi></strong><small>رمز <bdi dir="ltr"><?= (int) $release['version_code'] ?></bdi></small></span></div>
                                <?php cc_status_badge(!empty($release['is_active']) ? 'النشط حاليًا' : (!empty($release['enabled']) ? 'جاهز' : 'معطّل'), !empty($release['is_active']) ? 'success' : (!empty($release['enabled']) ? 'info' : 'neutral')); ?>
                            </header>
                            <dl class="record-card__highlights record-card__highlights--three">
                                <div><dt>السياسة</dt><dd><?= !empty($release['required']) ? 'إلزامي' : 'اختياري' ?></dd></div>
                                <div><dt>الحد الأدنى</dt><dd><bdi dir="ltr"><?= (int) $release['minimum_supported_version_code'] ?></bdi></dd></div>
                                <div><dt>الجاهزية</dt><dd><?= !empty($release['enabled']) ? 'متاح' : 'متوقف' ?></dd></div>
                            </dl>
                            <div class="record-actions">
                                <?php if (empty($release['is_active'])): ?><form method="post" data-confirm="تفعيل هذا الإصدار؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="activate_release"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>"><button class="button button--primary" type="submit">تفعيل الإصدار</button></form><?php endif; ?>
                                <details class="inline-editor"><summary>تعديل السياسة</summary><form method="post"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="update_release_policy"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>"><label class="form-field"><span>الحد الأدنى</span><input name="minimum_supported_version_code" type="number" min="1" value="<?= (int) $release['minimum_supported_version_code'] ?>" required></label><label class="check-control"><input name="required" type="checkbox" value="1" <?= !empty($release['required']) ? 'checked' : '' ?>> تحديث إلزامي</label><button class="button button--secondary" type="submit">حفظ السياسة</button></form></details>
                                <?php if (!empty($release['enabled']) || !empty($release['is_active'])): ?><form method="post" data-confirm="تعطيل هذا الإصدار؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="disable_release"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>"><button class="button button--danger" type="submit">تعطيل</button></form><?php endif; ?>
                            </div>
                            <section class="destructive-zone">
                                <div><strong>حذف الإصدار نهائيًا</strong><small>يحذف سجل الإصدار وملف APK المرتبط به من الاستضافة. لا يمكن التراجع عن هذا الإجراء.</small></div>
                                <div class="record-actions">
                                    <form method="post" data-confirm="حذف هذا الإصدار نهائيًا؟ سيُحذف سجل الإصدار وملف APK، وإذا كان الإصدار منشورًا فستعود سياسة التحديث إلى الوضع الآمن."><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="delete_release"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>"><button class="button button--danger" type="submit">حذف الإصدار</button></form>
                                </div>
                            </section>
                        </article>
                    <?php endforeach; ?>
                </div>
            <?php endif; ?>
        </section>
    </section>

<?php elseif ($moduleKey === 'service'): ?>
    <?php
    $service = is_array($pageData['service'] ?? null) ? $pageData['service'] : [];
    $serviceStatus = (string) ($service['status'] ?? 'OPERATIONAL');
    $serviceLabels = ['OPERATIONAL' => 'تعمل بصورة طبيعية', 'DEGRADED' => 'الخدمة متأثرة', 'MAINTENANCE' => 'وضع الصيانة'];
    $serviceTone = $serviceStatus === 'OPERATIONAL' ? 'success' : ($serviceStatus === 'DEGRADED' ? 'warning' : 'danger');
    ?>
    <section class="module-stack service-control-page">
        <?php cc_page_summary(
            'الحالة المنشورة للمستخدمين',
            $serviceLabels[$serviceStatus] ?? 'حالة غير معروفة',
            'هذه الحالة والرسالة هما ما يعتمد عليه التطبيق لعرض التوفر أو الصيانة.',
            'pulse',
            ['label' => 'منشورة الآن', 'tone' => $serviceTone],
            [
                ['label' => 'بداية الحالة', 'value' => ops_datetime_local_value($service['starts_at'] ?? null) ?: 'فورية'],
                ['label' => 'الانتهاء المتوقع', 'value' => ops_datetime_local_value($service['estimated_end_at'] ?? null) ?: 'غير محدد'],
            ]
        ); ?>
        <section class="service-workspace">
            <article class="service-message-preview panel">
                <span class="eyebrow">الرسالة الحالية</span>
                <h2><?= cc_e((string) (($service['message'] ?? '') !== '' ? $service['message'] : 'لا توجد رسالة مخصصة للمستخدمين.')) ?></h2>
                <p>راجع الصياغة قبل حفظ أي حالة متأثرة أو صيانة.</p>
            </article>
            <section class="form-section">
                <header><span><?= cc_icon('pulse') ?></span><span><strong>تحديث حالة الخدمة</strong><small>غيّر الحالة والرسالة والنافذة الزمنية من نموذج واحد واضح.</small></span></header>
                <form class="admin-form" method="post" data-confirm="تغيير حالة الخدمة الآن؟">
                    <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="update_service_status">
                    <label class="form-field"><span>الحالة</span><select name="status"><?php foreach ($serviceLabels as $value => $label): ?><option value="<?= cc_e($value) ?>" <?= $serviceStatus === $value ? 'selected' : '' ?>><?= cc_e($label) ?></option><?php endforeach; ?></select></label>
                    <label class="form-field"><span>الرسالة الموجهة للمستخدمين</span><textarea name="message" maxlength="2000"><?= cc_e($service['message'] ?? '') ?></textarea></label>
                    <div class="admin-form admin-form--grid"><label class="form-field"><span>وقت البداية</span><input name="starts_at" type="datetime-local" value="<?= cc_e(ops_datetime_local_value($service['starts_at'] ?? null)) ?>"></label><label class="form-field"><span>الانتهاء المتوقع</span><input name="estimated_end_at" type="datetime-local" value="<?= cc_e(ops_datetime_local_value($service['estimated_end_at'] ?? null)) ?>"></label></div>
                    <div class="form-submit"><button class="button button--primary" type="submit">حفظ ونشر الحالة</button></div>
                </form>
            </section>
        </section>
    </section>

<?php elseif ($moduleKey === 'announcements'): ?>
    <?php
    $announcements = is_array($pageData['announcements'] ?? null) ? $pageData['announcements'] : [];
    $enabledAnnouncements = array_values(array_filter($announcements, static fn (array $row): bool => !empty($row['enabled'])));
    ?>
    <section class="module-stack announcement-management-page">
        <?php cc_page_summary(
            'رسائل التطبيق',
            count($enabledAnnouncements) === 0 ? 'لا يوجد إعلان مفعّل' : count($enabledAnnouncements) . ' إعلان مفعّل',
            'راجع الرسائل الفعالة والجمهور والنافذة الزمنية قبل نشر إعلان جديد.',
            'bell',
            ['label' => count($enabledAnnouncements) === 0 ? 'هادئ' : 'نشط', 'tone' => count($enabledAnnouncements) === 0 ? 'neutral' : 'success'],
            [
                ['label' => 'الإعلانات المفعلة', 'value' => (string) count($enabledAnnouncements)],
                ['label' => 'إجمالي السجل', 'value' => (string) count($announcements)],
            ]
        ); ?>

        <section class="records-section">
            <header class="section-heading"><div><span class="eyebrow">الحالي والتاريخ</span><h2>الإعلانات</h2></div><span class="record-count"><?= count($announcements) ?> إعلان</span></header>
            <?php if ($announcements === []): ?>
                <?php cc_empty_state('لا توجد إعلانات', 'أنشئ إعلانًا فقط عندما تحتاج إلى مخاطبة مستخدمي التطبيق.'); ?>
            <?php else: ?>
                <div class="announcement-records">
                    <?php foreach ($announcements as $row): ?>
                        <article class="record-card announcement-record">
                            <header class="record-card__header"><div class="record-identity"><span class="record-identity__icon"><?= cc_icon('bell') ?></span><span><strong><?= cc_e($row['title']) ?></strong><small><?= cc_e((string) $row['severity']) ?> · <?= cc_e((string) $row['target']) ?></small></span></div><?php cc_status_badge(!empty($row['enabled']) ? 'مفعّل' : 'معطّل', !empty($row['enabled']) ? 'success' : 'neutral'); ?></header>
                            <dl class="record-card__highlights record-card__highlights--three"><div><dt>الجمهور</dt><dd><?= cc_e((string) $row['target']) ?></dd></div><div><dt>البداية</dt><dd><?= cc_e((string) $row['starts_at']) ?></dd></div><div><dt>النهاية</dt><dd><?= cc_e((string) ($row['ends_at'] ?: 'بدون انتهاء')) ?></dd></div></dl>
                            <details class="technical-disclosure"><summary>الاستهداف التقني</summary><div class="technical-disclosure__body"><?php cc_fact_list([['label' => 'معرّف الرسالة', 'value' => (string) $row['message_key'], 'ltr' => true]], 'fact-list--technical'); ?></div></details>
                            <?php if (!empty($row['enabled'])): ?><div class="record-actions"><form method="post" data-confirm="تعطيل هذا الإعلان؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="disable_announcement"><input type="hidden" name="announcement_id" value="<?= (int) $row['id'] ?>"><button class="button button--danger" type="submit">تعطيل الإعلان</button></form></div><?php endif; ?>
                        </article>
                    <?php endforeach; ?>
                </div>
            <?php endif; ?>
        </section>

        <details class="form-section">
            <summary><span><?= cc_icon('bell') ?></span><span><strong>إنشاء إعلان جديد</strong><small>اكتب الرسالة أولًا، ثم حدد الجمهور والنافذة والاستمرار.</small></span></summary>
            <form class="admin-form admin-form--grid" method="post">
                <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="create_announcement">
                <label class="form-field"><span>العنوان</span><input name="title" maxlength="160" required></label>
                <label class="form-field"><span>معرّف الرسالة <small>اختياري</small></span><input name="message_key" maxlength="80" dir="ltr"></label>
                <label class="form-field form-field--wide"><span>نص الإعلان</span><textarea name="message" maxlength="10000" required></textarea></label>
                <label class="form-field"><span>الأهمية</span><select name="severity"><option value="INFO">معلومات</option><option value="WARNING">تحذير</option><option value="IMPORTANT">مهم</option></select></label>
                <label class="form-field"><span>الجمهور</span><select name="target"><option value="ALL">الجميع</option><option value="MOBILE">الجوال والتابلت</option><option value="TV">التلفزيون</option></select></label>
                <label class="form-field"><span>البداية</span><input name="starts_at" type="datetime-local" value="<?= cc_e(date('Y-m-d\TH:i')) ?>" required></label>
                <label class="form-field"><span>النهاية <small>اختياري</small></span><input name="ends_at" type="datetime-local"></label>
                <label class="form-field"><span>أدنى إصدار <small>اختياري</small></span><input name="minimum_version_code" type="number" min="1"></label>
                <label class="form-field"><span>أعلى إصدار <small>اختياري</small></span><input name="maximum_version_code" type="number" min="1"></label>
                <div class="check-list form-field--wide"><label class="check-control"><input name="enabled" type="checkbox" value="1" checked> نشره مفعّلًا</label><label class="check-control"><input name="show_once" type="checkbox" value="1" checked> يظهر مرة واحدة</label><label class="check-control"><input name="persistent" type="checkbox" value="1"> يبقى ظاهرًا</label></div>
                <div class="form-submit form-field--wide"><button class="button button--primary" type="submit">نشر الإعلان</button></div>
            </form>
        </details>
    </section>

<?php elseif ($moduleKey === 'features'): ?>
    <?php
    $features = is_array($pageData['features'] ?? null) ? $pageData['features'] : [];
    $labels = [
        'downloads_enabled' => ['التنزيلات', 'السماح بحفظ المحتوى المتاح للمشاهدة لاحقًا.'],
        'episode_notifications_enabled' => ['تنبيهات الحلقات', 'إظهار تنبيهات الحلقات الجديدة للمستخدمين.'],
        'smart_recommendations_enabled' => ['التوصيات الذكية', 'عرض اقتراحات مبنية على تجربة التطبيق.'],
        'live_tv_pro_enabled' => ['البث المباشر الاحترافي', 'إتاحة تجربة البث المباشر الاحترافية.'],
    ];
    $enabledFlags = array_values(array_filter(ops_known_feature_flags(), static fn (string $flag): bool => (bool) ($features[$flag] ?? true)));
    $disabledFlags = array_values(array_filter(ops_known_feature_flags(), static fn (string $flag): bool => !(bool) ($features[$flag] ?? true)));
    ?>
    <section class="module-stack feature-control-page">
        <?php cc_page_summary(
            'قدرات التطبيق',
            count($enabledFlags) . ' من ' . count(ops_known_feature_flags()) . ' مميزات مفعّلة',
            'تحكم مباشر بالمميزات المعتمدة، مع إبقاء المعرّف التقني داخل التفاصيل.',
            'toggle',
            ['label' => $disabledFlags === [] ? 'كلها مفعّلة' : 'توجد مميزات متوقفة', 'tone' => $disabledFlags === [] ? 'success' : 'warning'],
            [
                ['label' => 'مفعّلة', 'value' => (string) count($enabledFlags)],
                ['label' => 'متوقفة', 'value' => (string) count($disabledFlags)],
            ]
        ); ?>
        <?php foreach ([['title' => 'المميزات المفعّلة', 'items' => $enabledFlags, 'tone' => 'success'], ['title' => 'المميزات المتوقفة', 'items' => $disabledFlags, 'tone' => 'neutral']] as $group): ?>
            <section class="feature-state-group">
                <header class="section-heading"><div><h2><?= cc_e($group['title']) ?></h2></div><span class="record-count"><?= count($group['items']) ?></span></header>
                <?php if ($group['items'] === []): ?>
                    <p class="group-empty">لا توجد عناصر في هذه المجموعة.</p>
                <?php else: ?>
                    <div class="feature-rows">
                        <?php foreach ($group['items'] as $flag): $enabled = (bool) ($features[$flag] ?? true); ?>
                            <article class="feature-row">
                                <span class="feature-row__icon"><?= cc_icon('toggle') ?></span>
                                <span class="feature-row__copy"><strong><?= cc_e($labels[$flag][0] ?? $flag) ?></strong><small><?= cc_e($labels[$flag][1] ?? '') ?></small></span>
                                <?php cc_status_badge($enabled ? 'مفعّلة' : 'متوقفة', $enabled ? 'success' : 'neutral'); ?>
                                <form method="post" data-confirm="تغيير حالة هذه الميزة؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="toggle_feature_flag"><input type="hidden" name="flag_key" value="<?= cc_e($flag) ?>"><input type="hidden" name="enabled" value="<?= $enabled ? '0' : '1' ?>"><button class="button <?= $enabled ? 'button--danger' : 'button--primary' ?>" type="submit"><?= $enabled ? 'إيقاف' : 'تفعيل' ?></button></form>
                                <details class="technical-disclosure"><summary>المعرّف التقني</summary><div class="technical-disclosure__body"><code dir="ltr"><?= cc_e($flag) ?></code></div></details>
                            </article>
                        <?php endforeach; ?>
                    </div>
                <?php endif; ?>
            </section>
        <?php endforeach; ?>
    </section>

<?php elseif ($moduleKey === 'growth'): ?>
    <?php
    $growth = is_array($pageData['growth'] ?? null) ? $pageData['growth'] : [];
    $renewal = is_array($growth['renewal'] ?? null) ? $growth['renewal'] : [];
    $support = is_array($growth['support'] ?? null) ? $growth['support'] : [];
    $banner = is_array($growth['renewalBanner'] ?? null) ? $growth['renewalBanner'] : [];
    ?>
    <section class="module-stack growth-management-page">
        <?php cc_page_summary(
            'مسارات ما بعد الاشتراك',
            !empty($growth['enabled']) ? 'التجديد والدعم مفعّلان في التطبيق' : 'التجديد والدعم متوقفان',
            'راجع كل قناة ومحتواها وسلوك QR وموعد تنبيه الانتهاء قبل النشر.',
            'trend',
            ['label' => !empty($growth['enabled']) ? 'مفعّل' : 'متوقف', 'tone' => !empty($growth['enabled']) ? 'success' : 'neutral'],
            [
                ['label' => 'التجديد', 'value' => !empty($renewal['enabled']) ? 'مفعّل' : 'متوقف'],
                ['label' => 'الدعم', 'value' => !empty($support['enabled']) ? 'مفعّل' : 'متوقف'],
                ['label' => 'تنبيه الانتهاء', 'value' => !empty($banner['enabled']) ? ((int) ($banner['daysBeforeExpiry'] ?? 7) . ' أيام') : 'متوقف'],
            ]
        ); ?>

        <div class="growth-channel-grid">
            <article class="growth-channel"><span class="growth-channel__icon"><?= cc_icon('trend') ?></span><div><span class="eyebrow">التجديد</span><h2><?= cc_e((string) ($renewal['title'] ?? 'غير محدد')) ?></h2><p><?= cc_e((string) ($renewal['displayText'] ?? 'لا يوجد نص عرض')) ?></p></div><?php cc_status_badge(!empty($renewal['enabled']) ? 'مفعّل' : 'متوقف', !empty($renewal['enabled']) ? 'success' : 'neutral'); ?><small>QR: <?= ($renewal['qrMode'] ?? '') === 'CUSTOM' ? 'صورة مخصصة' : 'تلقائي' ?></small></article>
            <article class="growth-channel"><span class="growth-channel__icon"><?= cc_icon('users') ?></span><div><span class="eyebrow">الدعم</span><h2><?= cc_e((string) ($support['title'] ?? 'غير محدد')) ?></h2><p><?= cc_e((string) ($support['displayText'] ?? 'لا يوجد نص عرض')) ?></p></div><?php cc_status_badge(!empty($support['enabled']) ? 'مفعّل' : 'متوقف', !empty($support['enabled']) ? 'success' : 'neutral'); ?><small>QR: <?= ($support['qrMode'] ?? '') === 'CUSTOM' ? 'صورة مخصصة' : 'تلقائي' ?></small></article>
        </div>

        <section class="form-section">
            <header><span><?= cc_icon('settings') ?></span><span><strong>تحرير محتوى التجديد والدعم</strong><small>الإعدادات مجمعة حسب القناة بدل نموذج واحد مسطح.</small></span></header>
            <form class="admin-form growth-form" method="post" enctype="multipart/form-data">
                <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="save_growth"><input type="hidden" name="growth_command" value="save">
                <label class="check-control growth-master-toggle"><input name="growth_enabled" type="checkbox" value="1" <?= !empty($growth['enabled']) ? 'checked' : '' ?>> تفعيل التجديد والدعم داخل التطبيق</label>

                <fieldset class="growth-editor"><legend>قناة التجديد</legend>
                    <label class="form-field"><span>العنوان</span><input name="growth_renewal_title" value="<?= cc_e($renewal['title'] ?? '') ?>" maxlength="80" required></label>
                    <label class="form-field"><span>الرابط</span><input name="growth_renewal_url" type="url" dir="ltr" value="<?= cc_e($renewal['url'] ?? '') ?>" required></label>
                    <label class="form-field"><span>نص الرابط</span><input name="growth_renewal_display_text" value="<?= cc_e($renewal['displayText'] ?? '') ?>" maxlength="80" required></label>
                    <label class="form-field"><span>نوع QR</span><select name="growth_renewal_qr_mode"><option value="AUTO" <?= ($renewal['qrMode'] ?? '') === 'AUTO' ? 'selected' : '' ?>>تلقائي</option><option value="CUSTOM" <?= ($renewal['qrMode'] ?? '') === 'CUSTOM' ? 'selected' : '' ?>>صورة مخصصة</option></select></label>
                    <label class="form-field"><span>صورة QR مخصصة</span><input name="growth_renewal_custom_qr" type="file" accept="image/png,image/jpeg,image/webp"></label>
                    <label class="check-control"><input name="growth_renewal_enabled" type="checkbox" value="1" <?= !empty($renewal['enabled']) ? 'checked' : '' ?>> تفعيل التجديد</label>
                </fieldset>

                <fieldset class="growth-editor"><legend>قناة الدعم</legend>
                    <label class="form-field"><span>العنوان</span><input name="growth_support_title" value="<?= cc_e($support['title'] ?? '') ?>" maxlength="80" required></label>
                    <label class="form-field"><span>رابط واتساب</span><input name="growth_support_url" type="url" dir="ltr" value="<?= cc_e($support['url'] ?? '') ?>" required></label>
                    <label class="form-field"><span>نص الرابط</span><input name="growth_support_display_text" value="<?= cc_e($support['displayText'] ?? '') ?>" maxlength="80" required></label>
                    <label class="form-field"><span>نوع QR</span><select name="growth_support_qr_mode"><option value="AUTO" <?= ($support['qrMode'] ?? '') === 'AUTO' ? 'selected' : '' ?>>تلقائي</option><option value="CUSTOM" <?= ($support['qrMode'] ?? '') === 'CUSTOM' ? 'selected' : '' ?>>صورة مخصصة</option></select></label>
                    <label class="form-field"><span>صورة QR مخصصة</span><input name="growth_support_custom_qr" type="file" accept="image/png,image/jpeg,image/webp"></label>
                    <label class="check-control"><input name="growth_support_enabled" type="checkbox" value="1" <?= !empty($support['enabled']) ? 'checked' : '' ?>> تفعيل الدعم</label>
                </fieldset>

                <fieldset class="growth-editor growth-editor--banner"><legend>تنبيه قرب الانتهاء</legend>
                    <label class="form-field"><span>يظهر قبل الانتهاء بـ</span><input name="growth_renewal_banner_days" type="number" min="1" max="30" value="<?= (int) ($banner['daysBeforeExpiry'] ?? 7) ?>" required></label>
                    <label class="check-control"><input name="growth_renewal_banner_enabled" type="checkbox" value="1" <?= !empty($banner['enabled']) ? 'checked' : '' ?>> تفعيل شريط التجديد</label>
                </fieldset>
                <div class="form-submit"><button class="button button--primary" type="submit">حفظ ونشر الإعدادات</button></div>
            </form>
        </section>

        <?php if (!empty($renewal['customQrUrl']) || !empty($support['customQrUrl'])): ?>
            <section class="destructive-zone"><div><strong>إدارة صور QR المخصصة</strong><small>الحذف يعيد القناة إلى إنشاء QR تلقائيًا.</small></div><div class="record-actions">
                <?php if (!empty($renewal['customQrUrl'])): ?><form method="post" data-confirm="حذف QR التجديد المخصص والعودة للتلقائي؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="save_growth"><input type="hidden" name="growth_command" value="delete_renewal_qr"><button class="button button--danger" type="submit">حذف QR التجديد</button></form><?php endif; ?>
                <?php if (!empty($support['customQrUrl'])): ?><form method="post" data-confirm="حذف QR الدعم المخصص والعودة للتلقائي؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="save_growth"><input type="hidden" name="growth_command" value="delete_support_qr"><button class="button button--danger" type="submit">حذف QR الدعم</button></form><?php endif; ?>
            </div></section>
        <?php endif; ?>
    </section>
<?php endif; ?>
