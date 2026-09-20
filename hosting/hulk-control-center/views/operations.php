<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل بيانات Operations', 'لم يتم إجراء أي تعديل. تحقق من اتصال قاعدة Operations ثم أعد المحاولة.', cc_url($moduleKey));
    return;
}

$csrf = cc_csrf_token();
?>
<section class="module-stack">
<?php if ($moduleKey === 'releases'): ?>
    <?php $releases = $pageData['releases'] ?? []; ?>
    <section class="panel">
        <div class="panel__header"><div><span class="eyebrow">APK موثوق</span><h2>رفع إصدار جديد</h2></div><?php cc_status_badge('Operations DB', 'success'); ?></div>
        <form class="admin-form admin-form--grid" method="post" enctype="multipart/form-data">
            <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="upload_release">
            <label class="form-field"><span>اسم الإصدار</span><input name="version_name" maxlength="32" required></label>
            <label class="form-field"><span>رمز الإصدار</span><input name="version_code" type="number" min="1" required></label>
            <label class="form-field"><span>الحد الأدنى المدعوم</span><input name="minimum_supported_version_code" type="number" min="1" required></label>
            <label class="form-field"><span>ملف APK</span><input name="apk" type="file" accept=".apk,application/vnd.android.package-archive" required></label>
            <label class="form-field form-field--wide"><span>ملاحظات الإصدار</span><textarea name="release_notes" maxlength="10000" required></textarea></label>
            <label class="check-control"><input name="required" type="checkbox" value="1"> تحديث إجباري</label>
            <div><button class="button button--primary" type="submit">رفع الإصدار</button></div>
        </form>
    </section>
    <section class="panel">
        <div class="panel__header"><h2>الإصدارات</h2><span class="muted-copy"><?= count($releases) ?> إصدار</span></div>
        <?php if ($releases === []): cc_empty_state('لا توجد إصدارات', 'ارفع أول APK من النموذج أعلاه.'); else: ?>
        <div class="table-shell table-shell--responsive"><div class="table-scroll" tabindex="0" aria-label="الإصدارات المحفوظة"><table data-mobile-cards><thead><tr><th>الإصدار</th><th>السياسة</th><th>الحالة</th><th>الإجراءات</th></tr></thead><tbody>
        <?php foreach ($releases as $release): ?>
            <tr>
                <td><strong><?= cc_e($release['version_name']) ?></strong><br><span class="mono muted-copy">code <?= (int) $release['version_code'] ?></span></td>
                <td><?= !empty($release['required']) ? 'إجباري' : 'اختياري' ?><br><span class="muted-copy">الحد الأدنى: <?= (int) $release['minimum_supported_version_code'] ?></span></td>
                <td><?php cc_status_badge(!empty($release['is_active']) ? 'نشط' : (!empty($release['enabled']) ? 'متاح' : 'معطّل'), !empty($release['is_active']) ? 'success' : 'neutral'); ?></td>
                <td><div class="row-actions">
                    <?php if (empty($release['is_active'])): ?><form method="post" data-confirm="تفعيل هذا الإصدار؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="activate_release"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>"><button class="button button--secondary" type="submit">تفعيل</button></form><?php endif; ?>
                    <?php if (!empty($release['enabled']) || !empty($release['is_active'])): ?><form method="post" data-confirm="تعطيل هذا الإصدار؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="disable_release"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>"><button class="button button--danger" type="submit">تعطيل</button></form><?php endif; ?>
                    <details class="inline-editor"><summary>السياسة</summary><form method="post"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="update_release_policy"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>"><input name="minimum_supported_version_code" type="number" min="1" value="<?= (int) $release['minimum_supported_version_code'] ?>" required><label><input name="required" type="checkbox" value="1" <?= !empty($release['required']) ? 'checked' : '' ?>> إجباري</label><button class="button button--secondary" type="submit">حفظ</button></form></details>
                </div></td>
            </tr>
        <?php endforeach; ?>
        </tbody></table></div></div>
        <?php endif; ?>
    </section>

<?php elseif ($moduleKey === 'service'): ?>
    <?php $service = $pageData['service'] ?? []; ?>
    <section class="panel narrow-panel">
        <div class="panel__header"><div><span class="eyebrow">العقد العام الحالي</span><h2>حالة الخدمة</h2></div><?php cc_status_badge((string) ($service['status'] ?? 'OPERATIONAL'), ($service['status'] ?? '') === 'OPERATIONAL' ? 'success' : 'warning'); ?></div>
        <form class="admin-form" method="post" data-confirm="تغيير حالة الخدمة الآن؟">
            <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="update_service_status">
            <label class="form-field"><span>الحالة</span><select name="status"><?php foreach (['OPERATIONAL' => 'تعمل طبيعيًا', 'DEGRADED' => 'متأثرة', 'MAINTENANCE' => 'صيانة'] as $value => $label): ?><option value="<?= cc_e($value) ?>" <?= ($service['status'] ?? '') === $value ? 'selected' : '' ?>><?= cc_e($label) ?></option><?php endforeach; ?></select></label>
            <label class="form-field"><span>الرسالة</span><textarea name="message" maxlength="2000"><?= cc_e($service['message'] ?? '') ?></textarea></label>
            <div class="admin-form admin-form--grid"><label class="form-field"><span>وقت البداية</span><input name="starts_at" type="datetime-local" value="<?= cc_e(ops_datetime_local_value($service['starts_at'] ?? null)) ?>"></label><label class="form-field"><span>الانتهاء المتوقع</span><input name="estimated_end_at" type="datetime-local" value="<?= cc_e(ops_datetime_local_value($service['estimated_end_at'] ?? null)) ?>"></label></div>
            <button class="button button--primary" type="submit">حفظ الحالة</button>
        </form>
    </section>

<?php elseif ($moduleKey === 'announcements'): ?>
    <?php $announcements = $pageData['announcements'] ?? []; ?>
    <section class="panel">
        <div class="panel__header"><h2>إعلان جديد</h2><?php cc_status_badge('Android contract', 'info'); ?></div>
        <form class="admin-form admin-form--grid" method="post">
            <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="create_announcement">
            <label class="form-field"><span>معرّف الرسالة (اختياري)</span><input name="message_key" maxlength="80"></label>
            <label class="form-field"><span>العنوان</span><input name="title" maxlength="160" required></label>
            <label class="form-field form-field--wide"><span>النص</span><textarea name="message" maxlength="10000" required></textarea></label>
            <label class="form-field"><span>الأهمية</span><select name="severity"><option value="INFO">معلومات</option><option value="WARNING">تحذير</option><option value="IMPORTANT">مهم</option></select></label>
            <label class="form-field"><span>الهدف</span><select name="target"><option value="ALL">الجميع</option><option value="MOBILE">الجوال والتابلت</option><option value="TV">التلفزيون</option></select></label>
            <label class="form-field"><span>البداية</span><input name="starts_at" type="datetime-local" value="<?= cc_e(date('Y-m-d\TH:i')) ?>" required></label>
            <label class="form-field"><span>النهاية (اختياري)</span><input name="ends_at" type="datetime-local"></label>
            <label class="form-field"><span>أدنى إصدار (اختياري)</span><input name="minimum_version_code" type="number" min="1"></label>
            <label class="form-field"><span>أعلى إصدار (اختياري)</span><input name="maximum_version_code" type="number" min="1"></label>
            <div class="check-list"><label class="check-control"><input name="enabled" type="checkbox" value="1" checked> مفعّل</label><label class="check-control"><input name="show_once" type="checkbox" value="1" checked> مرة واحدة</label><label class="check-control"><input name="persistent" type="checkbox" value="1"> ثابت</label></div>
            <div><button class="button button--primary" type="submit">نشر الإعلان</button></div>
        </form>
    </section>
    <section class="panel"><div class="panel__header"><h2>الإعلانات المحفوظة</h2><span class="muted-copy"><?= count($announcements) ?> إعلان</span></div>
        <?php if ($announcements === []): cc_empty_state('لا توجد إعلانات', 'أنشئ إعلانًا عند الحاجة.'); else: ?><div class="table-shell table-shell--responsive"><div class="table-scroll" tabindex="0" aria-label="الإعلانات المحفوظة"><table data-mobile-cards><thead><tr><th>الإعلان</th><th>الهدف</th><th>النافذة</th><th>الحالة</th><th>الإدارة</th></tr></thead><tbody><?php foreach ($announcements as $row): ?><tr><td><strong><?= cc_e($row['title']) ?></strong><br><span class="mono muted-copy"><?= cc_e($row['message_key']) ?></span></td><td><?= cc_e($row['severity']) ?> / <?= cc_e($row['target']) ?></td><td><?= cc_e($row['starts_at']) ?><br><span class="muted-copy"><?= cc_e($row['ends_at'] ?: 'بدون انتهاء') ?></span></td><td><?php cc_status_badge(!empty($row['enabled']) ? 'مفعّل' : 'معطّل', !empty($row['enabled']) ? 'success' : 'neutral'); ?></td><td><?php if (!empty($row['enabled'])): ?><form method="post" data-confirm="تعطيل هذا الإعلان؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="disable_announcement"><input type="hidden" name="announcement_id" value="<?= (int) $row['id'] ?>"><button class="button button--danger" type="submit">تعطيل</button></form><?php endif; ?></td></tr><?php endforeach; ?></tbody></table></div></div><?php endif; ?>
    </section>

<?php elseif ($moduleKey === 'features'): ?>
    <?php $features = $pageData['features'] ?? []; $labels = ['downloads_enabled' => 'التنزيلات', 'episode_notifications_enabled' => 'تنبيهات الحلقات', 'smart_recommendations_enabled' => 'التوصيات الذكية', 'live_tv_pro_enabled' => 'البث المباشر الاحترافي']; ?>
    <div class="management-grid"><?php foreach (ops_known_feature_flags() as $flag): $enabled = (bool) ($features[$flag] ?? true); ?><article class="panel"><div class="panel__header"><h2><?= cc_e($labels[$flag] ?? $flag) ?></h2><?php cc_status_badge($enabled ? 'مفعلة' : 'متوقفة', $enabled ? 'success' : 'danger'); ?></div><p class="mono muted-copy"><?= cc_e($flag) ?></p><form method="post" data-confirm="تغيير حالة هذه الميزة؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="toggle_feature_flag"><input type="hidden" name="flag_key" value="<?= cc_e($flag) ?>"><input type="hidden" name="enabled" value="<?= $enabled ? '0' : '1' ?>"><button class="button <?= $enabled ? 'button--danger' : 'button--primary' ?>" type="submit"><?= $enabled ? 'تعطيل' : 'تفعيل' ?></button></form></article><?php endforeach; ?></div>

<?php elseif ($moduleKey === 'growth'): ?>
    <?php $growth = $pageData['growth'] ?? []; $renewal = $growth['renewal'] ?? []; $support = $growth['support'] ?? []; $banner = $growth['renewalBanner'] ?? []; ?>
    <section class="panel">
        <div class="panel__header"><div><span class="eyebrow">HULK TV Growth</span><h2>التجديد والدعم</h2></div><?php cc_status_badge(!empty($growth['enabled']) ? 'مفعّل' : 'متوقف', !empty($growth['enabled']) ? 'success' : 'neutral'); ?></div>
        <form class="admin-form admin-form--grid" method="post" enctype="multipart/form-data">
            <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="save_growth"><input type="hidden" name="growth_command" value="save">
            <label class="check-control form-field--wide"><input name="growth_enabled" type="checkbox" value="1" <?= !empty($growth['enabled']) ? 'checked' : '' ?>> تفعيل Growth</label>
            <label class="form-field"><span>عنوان التجديد</span><input name="growth_renewal_title" value="<?= cc_e($renewal['title'] ?? '') ?>" maxlength="80" required></label>
            <label class="form-field"><span>رابط التجديد</span><input name="growth_renewal_url" type="url" value="<?= cc_e($renewal['url'] ?? '') ?>" required></label>
            <label class="form-field"><span>نص رابط التجديد</span><input name="growth_renewal_display_text" value="<?= cc_e($renewal['displayText'] ?? '') ?>" maxlength="80" required></label>
            <label class="form-field"><span>QR التجديد</span><select name="growth_renewal_qr_mode"><option value="AUTO" <?= ($renewal['qrMode'] ?? '') === 'AUTO' ? 'selected' : '' ?>>تلقائي</option><option value="CUSTOM" <?= ($renewal['qrMode'] ?? '') === 'CUSTOM' ? 'selected' : '' ?>>مخصص</option></select></label>
            <label class="form-field"><span>صورة QR التجديد</span><input name="growth_renewal_custom_qr" type="file" accept="image/png,image/jpeg,image/webp"></label>
            <label class="check-control"><input name="growth_renewal_enabled" type="checkbox" value="1" <?= !empty($renewal['enabled']) ? 'checked' : '' ?>> تفعيل التجديد</label>
            <label class="form-field"><span>عنوان الدعم</span><input name="growth_support_title" value="<?= cc_e($support['title'] ?? '') ?>" maxlength="80" required></label>
            <label class="form-field"><span>رابط واتساب</span><input name="growth_support_url" type="url" value="<?= cc_e($support['url'] ?? '') ?>" required></label>
            <label class="form-field"><span>نص الدعم</span><input name="growth_support_display_text" value="<?= cc_e($support['displayText'] ?? '') ?>" maxlength="80" required></label>
            <label class="form-field"><span>QR الدعم</span><select name="growth_support_qr_mode"><option value="AUTO" <?= ($support['qrMode'] ?? '') === 'AUTO' ? 'selected' : '' ?>>تلقائي</option><option value="CUSTOM" <?= ($support['qrMode'] ?? '') === 'CUSTOM' ? 'selected' : '' ?>>مخصص</option></select></label>
            <label class="form-field"><span>صورة QR الدعم</span><input name="growth_support_custom_qr" type="file" accept="image/png,image/jpeg,image/webp"></label>
            <label class="check-control"><input name="growth_support_enabled" type="checkbox" value="1" <?= !empty($support['enabled']) ? 'checked' : '' ?>> تفعيل الدعم</label>
            <label class="form-field"><span>أيام ظهور شريط التجديد</span><input name="growth_renewal_banner_days" type="number" min="1" max="30" value="<?= (int) ($banner['daysBeforeExpiry'] ?? 7) ?>" required></label>
            <label class="check-control"><input name="growth_renewal_banner_enabled" type="checkbox" value="1" <?= !empty($banner['enabled']) ? 'checked' : '' ?>> تفعيل شريط التجديد</label>
            <div><button class="button button--primary" type="submit">حفظ ونشر الإعدادات</button></div>
        </form>
        <?php if (!empty($renewal['customQrUrl']) || !empty($support['customQrUrl'])): ?>
        <div class="row-actions destructive-row">
            <?php if (!empty($renewal['customQrUrl'])): ?><form method="post" data-confirm="حذف QR التجديد المخصص والعودة للتلقائي؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="save_growth"><input type="hidden" name="growth_command" value="delete_renewal_qr"><button class="button button--danger" type="submit">حذف QR التجديد</button></form><?php endif; ?>
            <?php if (!empty($support['customQrUrl'])): ?><form method="post" data-confirm="حذف QR الدعم المخصص والعودة للتلقائي؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="save_growth"><input type="hidden" name="growth_command" value="delete_support_qr"><button class="button button--danger" type="submit">حذف QR الدعم</button></form><?php endif; ?>
        </div>
        <?php endif; ?>
    </section>
<?php endif; ?>
</section>
