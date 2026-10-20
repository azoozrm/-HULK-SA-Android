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

    $reviewId = filter_var($_GET['review'] ?? ($pageData['review_release_id'] ?? null), FILTER_VALIDATE_INT);
    $reviewRelease = null;
    if ($reviewId !== false && $reviewId > 0) {
        foreach ($releases as $candidate) {
            if ((int) $candidate['id'] === (int) $reviewId && empty($candidate['is_active'])) {
                $reviewRelease = $candidate;
                break;
            }
        }
    }

    $uploadOpen = !empty($pageData['open_upload']) || (($_GET['upload'] ?? '') === '1');
    $shortUrl = 'https://hulksa.com/app/';
    $shortLabel = 'hulksa.com/app/';
    $downloaderCode = '5672332';
    $activeMinimum = $activeRelease === null
        ? 0
        : max(1, (int) $activeRelease['minimum_supported_version_code']);
    $requiredDefault = $activeRelease !== null && !empty($activeRelease['required']);
    $minimumDefault = $activeRelease === null ? 1 : $activeMinimum;

    $releaseState = static function (array $release): array {
        if (!empty($release['is_active'])) {
            return ['المنشور الآن', 'success'];
        }
        if (!empty($release['enabled'])) {
            return ['متاح', 'info'];
        }
        if (!empty($release['is_newer'])) {
            return ['جاهز للنشر', 'warning'];
        }
        return ['محفوظ', 'neutral'];
    };
    $releaseStateDetail = static function (array $release): string {
        if (!empty($release['is_active'])) {
            return 'متاح عبر الرابط المختصر';
        }
        if (!empty($release['enabled'])) {
            return 'مفعّل وغير منشور';
        }
        if (!empty($release['is_newer'])) {
            return 'لم يُنشر للمستخدمين بعد';
        }
        return 'أقدم من النسخة المنشورة';
    };
    $formatSize = static function (int $bytes): string {
        return $bytes > 0 ? number_format($bytes / 1048576, 1) . ' MB' : '';
    };
    $formatDate = static function (string $value): string {
        $timestamp = strtotime($value);
        return $timestamp === false ? '' : date('Y-m-d', $timestamp);
    };
    ?>
    <section class="release-management-page" aria-label="تحديث التطبيق">
        <div class="r6-top-grid">
            <section class="r6-panel r6-link-panel" aria-labelledby="r6-link-title">
                <div class="r6-section-heading">
                    <h2 id="r6-link-title" class="r6-label"><?= cc_icon('link', 'r6-gold-icon') ?>رابط التحميل الرسمي</h2>
                    <?php if ($activeRelease !== null): ?>
                        <span class="r6-badge r6-badge--success"><span class="r6-dot"></span>منشور</span>
                    <?php else: ?>
                        <span class="r6-badge r6-badge--neutral">غير متاح</span>
                    <?php endif; ?>
                </div>
                <div class="r6-link-line">
                    <a class="r6-short-url" href="<?= cc_e($shortUrl) ?>" dir="ltr"><?= cc_e($shortLabel) ?></a>
                    <button class="r6-btn r6-icon-btn" type="button" data-copy-value="<?= cc_e($shortUrl) ?>" data-copy-fallback="r6-short-fallback" data-copy-status="r6-short-copy-status" aria-label="نسخ رابط التحميل الرسمي"><?= cc_icon('copy') ?></button>
                </div>
                <input class="r6-copy-fallback" id="r6-short-fallback" type="text" value="<?= cc_e($shortUrl) ?>" readonly hidden aria-label="رابط التحميل الرسمي للنسخ اليدوي">
                <p class="r6-copy-status" id="r6-short-copy-status" role="status" aria-live="polite"></p>
                <div class="r6-current-line">
                    <span class="r6-current-label">النسخة المنشورة</span>
                    <?php if ($activeRelease !== null): ?>
                        <strong class="r6-version" dir="ltr"><?= cc_e($activeRelease['version_name']) ?></strong>
                        <span class="r6-code">رمز <bdi dir="ltr"><?= (int) $activeRelease['version_code'] ?></bdi></span>
                        <span class="r6-badge <?= !empty($activeRelease['required']) ? 'r6-badge--gold' : 'r6-badge--neutral' ?>"><?= !empty($activeRelease['required']) ? 'إجباري' : 'اختياري' ?></span>
                    <?php else: ?>
                        <span class="r6-unavailable">لا يوجد إصدار منشور</span>
                    <?php endif; ?>
                </div>
                <div class="r6-download-code">
                    <span><bdi>Downloader</bdi><strong><?= cc_e($downloaderCode) ?></strong></span>
                    <span class="r6-stable-note"><?= cc_icon('lock') ?>الرابط والكود ثابتان مع كل تحديث</span>
                </div>
                <?php if ($activeRelease !== null && empty($activeRelease['artifact_ready'])): ?>
                    <p class="r6-warning">ملف الإصدار المنشور غير متاح على الخادم. قد لا يعمل رابط التحميل حتى تُصلح المشكلة.</p>
                <?php endif; ?>
            </section>

            <section class="r6-panel r6-upload-intro" aria-labelledby="r6-upload-intro-title">
                <div class="r6-upload-symbol"><?= cc_icon('upload') ?></div>
                <h2 id="r6-upload-intro-title">جاهز للتحديث القادم؟</h2>
                <p><?= $activeRelease === null ? 'ارفع أول ملف APK ثم راجعه وانشره على الرابط الثابت.' : 'ارفع ملف APK جديدًا. النسخة الحالية تبقى منشورة حتى تؤكد النشر.' ?></p>
                <a class="r6-btn r6-btn--primary" href="<?= cc_e(cc_url('releases') . '?upload=1#release-upload-panel') ?>" data-r6-open-upload><?= cc_icon('plus') ?>رفع تحديث جديد</a>
            </section>
        </div>

        <section class="r6-panel r6-editor" id="release-upload-panel" aria-labelledby="r6-upload-title" <?= $uploadOpen ? '' : 'hidden' ?>>
            <div class="r6-editor-heading">
                <div><div class="r6-eyebrow">١ / ٢ · الملف والبيانات</div><h2 id="r6-upload-title">رفع تحديث جديد</h2></div>
                <a class="r6-btn r6-btn--quiet r6-icon-btn" href="<?= cc_e(cc_url('releases') . '#release-library') ?>" data-r6-close-upload aria-label="إغلاق نموذج الرفع"><?= cc_icon('close') ?></a>
            </div>
            <form class="r6-form" method="post" enctype="multipart/form-data" data-r6-upload-form data-submit-once>
                <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>">
                <input type="hidden" name="action" value="upload_release">
                <label class="r6-field r6-span"><span class="r6-field-label">ملف التطبيق <bdi>APK</bdi></span><input class="r6-file-input" type="file" name="apk" id="r6-apk-file" required><span class="r6-field-hint" id="r6-file-hint">اختر ملفًا بامتداد .apk — لا يُنشر بمجرد اختياره.</span></label>
                <label class="r6-field"><span class="r6-field-label">اسم الإصدار</span><input class="r6-input" type="text" name="version_name" id="r6-version-name" dir="ltr" placeholder="مثال: 1.0.1" maxlength="32" required></label>
                <label class="r6-field"><span class="r6-field-label">رمز الإصدار</span><input class="r6-input" type="number" name="version_code" id="r6-version-code" dir="ltr" min="1" step="1" placeholder="مثال: 69" required></label>
                <label class="r6-field r6-span"><span class="r6-field-label">ما الجديد في التحديث؟</span><textarea class="r6-input" name="release_notes" maxlength="10000" placeholder="اكتب المميزات والتحسينات التي ستظهر للمستخدم." required></textarea></label>
                <label class="r6-policy-choice r6-span"><input type="checkbox" name="required" value="1" id="r6-required" data-r6-required <?= $requiredDefault ? 'checked' : '' ?>><span class="r6-policy-copy"><strong>اجعل التحديث إجباريًا</strong><span id="r6-policy-help"><?= $requiredDefault ? 'يجب على المستخدم تحديث التطبيق للمتابعة.' : 'يمكن للمستخدم تأجيل هذا التحديث، ما لم يكن أقل من الحد الأدنى المدعوم.' ?></span></span></label>
                <details class="r6-advanced r6-span" data-r6-advanced><summary>إعداد متقدم · الحد الأدنى المدعوم</summary><div class="r6-advanced-content"><label class="r6-field"><span class="r6-field-label">أقل رمز إصدار مسموح</span><input class="r6-input" type="number" name="minimum_supported_version_code" id="r6-min-code" min="1" step="1" value="<?= (int) $minimumDefault ?>" dir="ltr" required></label><p class="r6-field-hint">الإصدارات الأقل من هذا الرقم تحتاج إلى تحديث. لا يتغير تلقائيًا دون مراجعتك. عند اختيار تحديث إجباري يُضبط على رمز الإصدار.</p></div></details>
                <div class="r6-form-footer">
                    <p><?= cc_icon('shield') ?>ستراجع النسخة قبل إتاحتها للمستخدمين.</p>
                    <div class="r6-actions"><button class="r6-btn r6-btn--primary" type="submit">مراجعة التحديث<?= cc_icon('arrow-left') ?></button><a class="r6-btn r6-btn--quiet" href="<?= cc_e(cc_url('releases') . '#release-library') ?>" data-r6-close-upload>إلغاء</a></div>
                </div>
            </form>
        </section>

        <?php if ($reviewRelease !== null): ?>
            <section class="r6-panel r6-editor r6-review" id="release-review-panel" aria-labelledby="r6-review-title">
                <div class="r6-editor-heading">
                    <div><div class="r6-eyebrow">٢ / ٢ · المراجعة والنشر</div><h2 id="r6-review-title">راجع التحديث قبل النشر</h2></div>
                    <span class="r6-badge r6-badge--neutral">غير منشور</span>
                </div>
                <p class="r6-review-lead">عند التأكيد، يصبح هذا هو إصدار التطبيق المنشور والملف المتاح على رابطك المختصر.</p>
                <div class="r6-review-facts">
                    <div class="r6-fact"><span>الإصدار</span><strong dir="ltr"><?= cc_e($reviewRelease['version_name']) ?> / <?= (int) $reviewRelease['version_code'] ?></strong></div>
                    <div class="r6-fact"><span>سياسة التحديث</span><strong><?= !empty($reviewRelease['required']) ? 'إجباري' : 'اختياري' ?></strong></div>
                    <div class="r6-fact"><span>الحد الأدنى</span><strong dir="ltr"><?= (int) $reviewRelease['minimum_supported_version_code'] ?></strong></div>
                </div>
                <div class="r6-label">الملف المرفوع</div>
                <div class="r6-review-file" dir="ltr"><?= $reviewRelease['file_name'] !== '' ? cc_e($reviewRelease['file_name']) : 'ملف غير صالح' ?></div>
                <?php if ($reviewRelease['size_bytes'] > 0): ?><p class="r6-field-hint"><?= cc_e($formatSize((int) $reviewRelease['size_bytes'])) ?></p><?php endif; ?>
                <?php if ($reviewRelease['sha256'] !== ''): ?><div class="r6-review-hash"><span>SHA-256</span><code dir="ltr"><?= cc_e($reviewRelease['sha256']) ?></code></div><?php endif; ?>
                <p class="r6-review-notes"><?= nl2br(cc_e($reviewRelease['release_notes'])) ?></p>
                <div class="r6-publish-destination"><span>رابط التحميل بعد النشر</span><b dir="ltr"><?= cc_e($shortUrl) ?></b></div>
                <?php if (!empty($reviewRelease['is_older'])): ?>
                    <p class="r6-warning">هذا الإصدار أقدم من النسخة المنشورة، ولن يُنشر من هذه الصفحة.</p>
                <?php elseif (empty($reviewRelease['is_newer'])): ?>
                    <p class="r6-warning">هذا الإصدار ليس أحدث من النسخة المنشورة، ولن يُنشر من هذه الصفحة.</p>
                <?php endif; ?>
                <?php if (!empty($reviewRelease['below_active_minimum'])): ?>
                    <p class="r6-warning">الحد الأدنى لهذا الإصدار أقل من الحد الأدنى المنشور. لن يُتاح النشر حتى تؤكد أن هذا التخفيض مقصود أو تعدّل السياسة.</p>
                <?php endif; ?>
                <div class="r6-form-footer">
                    <p><?= cc_icon('check') ?>كود داونلودر <?= cc_e($downloaderCode) ?> لن يتغير.</p>
                    <div class="r6-actions">
                        <?php if (!empty($reviewRelease['is_newer'])): ?>
                            <form method="post" data-confirm="نشر الإصدار <?= cc_e($reviewRelease['version_name']) ?> (رمز <?= (int) $reviewRelease['version_code'] ?>) على الرابط المختصر الآن؟">
                                <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="activate_release"><input type="hidden" name="release_id" value="<?= (int) $reviewRelease['id'] ?>">
                                <?php if (!empty($reviewRelease['below_active_minimum'])): ?><label class="r6-risk-confirm"><input type="checkbox" data-r6-min-confirm> أؤكد أن خفض الحد الأدنى مقصود.</label><?php endif; ?>
                                <button class="r6-btn r6-btn--primary" type="submit" <?= !empty($reviewRelease['below_active_minimum']) ? 'disabled data-r6-publish' : '' ?>><?= cc_icon('send') ?>نشر على الرابط المختصر</button>
                            </form>
                        <?php endif; ?>
                        <a class="r6-btn" href="<?= cc_e(cc_url('releases') . '#release-library') ?>">حفظ دون نشر</a>
                    </div>
                </div>
                <details class="r6-inline-edit"><summary>تعديل السياسة قبل النشر</summary><form method="post"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="update_release_policy"><input type="hidden" name="release_id" value="<?= (int) $reviewRelease['id'] ?>"><input type="hidden" name="return_to_review" value="1"><label class="r6-field"><span class="r6-field-label">الحد الأدنى المدعوم</span><input class="r6-input" type="number" name="minimum_supported_version_code" min="1" value="<?= (int) $reviewRelease['minimum_supported_version_code'] ?>" dir="ltr" required></label><label class="r6-policy-choice"><input type="checkbox" name="required" value="1" <?= !empty($reviewRelease['required']) ? 'checked' : '' ?>><span class="r6-policy-copy"><strong>اجعل التحديث إجباريًا</strong><span>عند التفعيل يُضبط الحد الأدنى على رمز الإصدار.</span></span></label><button class="r6-btn r6-btn--secondary" type="submit">حفظ السياسة</button></form></details>
            </section>
        <?php endif; ?>

        <section class="r6-library" id="release-library" aria-labelledby="r6-library-title">
            <div class="r6-library-heading"><h2 id="r6-library-title">الإصدارات</h2><span class="r6-counter"><?= count($releases) ?> إصدار</span></div>
            <?php if ($releases === []): ?>
                <?php cc_empty_state('لا توجد إصدارات', 'ارفع أول APK لتكوين سجل الإصدارات.'); ?>
            <?php else: ?>
                <div class="r6-release-list">
                    <?php foreach ($releases as $release): $state = $releaseState($release); ?>
                        <details class="r6-release<?= !empty($release['is_active']) ? ' is-live' : '' ?>" <?= $reviewRelease !== null && (int) $reviewRelease['id'] === (int) $release['id'] ? 'open' : '' ?>>
                            <summary class="r6-release-row">
                                <span class="r6-release-icon"><?= cc_icon('package') ?></span>
                                <span class="r6-release-info">
                                    <span class="r6-release-name"><strong dir="ltr"><?= cc_e($release['version_name']) ?></strong><span class="r6-badge r6-badge--<?= cc_e($state[1]) ?>"><?= cc_e($state[0]) ?></span></span>
                                    <span class="r6-release-meta">رمز <bdi dir="ltr"><?= (int) $release['version_code'] ?></bdi> · <?= !empty($release['required']) ? 'تحديث إجباري' : 'تحديث اختياري' ?></span>
                                </span>
                                <span class="r6-release-buttons"><?= cc_icon('chevron-down', 'r6-chevron') ?></span>
                            </summary>
                            <div class="r6-release-details">
                                <div class="r6-release-detail-facts">
                                    <span>الحد الأدنى المدعوم: <bdi dir="ltr"><?= (int) $release['minimum_supported_version_code'] ?></bdi></span>
                                    <span><?= cc_e($releaseStateDetail($release)) ?></span>
                                    <?php $created = $formatDate($release['created_at']); if ($created !== ''): ?><span>أضيف: <bdi dir="ltr"><?= cc_e($created) ?></bdi></span><?php endif; ?>
                                </div>
                                <?php if ($release['file_name'] !== ''): ?><div class="r6-review-file" dir="ltr"><?= cc_e($release['file_name']) ?></div><?php endif; ?>
                                <?php if ($release['sha256'] !== ''): ?><div class="r6-review-hash"><span>SHA-256</span><code dir="ltr"><?= cc_e($release['sha256']) ?></code></div><?php endif; ?>
                                <?php if ($release['public_apk_url'] !== null): ?>
                                    <code class="r6-public-url" dir="ltr"><?= cc_e($release['public_apk_url']) ?></code>
                                    <div class="r6-copy-row"><button class="r6-btn" type="button" data-copy-value="<?= cc_e($release['public_apk_url']) ?>" data-copy-fallback="r6-apk-fallback-<?= (int) $release['id'] ?>" data-copy-status="r6-apk-status-<?= (int) $release['id'] ?>"><?= cc_icon('copy') ?>نسخ رابط APK المباشر</button></div>
                                    <input class="r6-copy-fallback" id="r6-apk-fallback-<?= (int) $release['id'] ?>" type="text" value="<?= cc_e($release['public_apk_url']) ?>" readonly hidden aria-label="رابط APK المباشر للنسخ اليدوي">
                                    <p class="r6-copy-status" id="r6-apk-status-<?= (int) $release['id'] ?>" role="status" aria-live="polite"></p>
                                <?php else: ?>
                                    <p class="r6-warning">لا يوجد رابط APK مباشر صالح لهذا السجل.</p>
                                <?php endif; ?>
                                <?php if ($release['release_notes'] !== ''): ?><p class="r6-review-notes"><?= nl2br(cc_e($release['release_notes'])) ?></p><?php endif; ?>
                                <div class="r6-row-actions">
                                    <?php if (empty($release['is_active']) && !empty($release['is_newer'])): ?><a class="r6-btn r6-btn--primary" href="<?= cc_e(cc_url('releases') . '?review=' . (int) $release['id'] . '#release-review-panel') ?>">مراجعة ونشر</a><?php endif; ?>
                                    <details class="r6-inline-edit"><summary>تعديل السياسة</summary><form method="post"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="update_release_policy"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>"><label class="r6-field"><span class="r6-field-label">الحد الأدنى</span><input class="r6-input" type="number" name="minimum_supported_version_code" min="1" value="<?= (int) $release['minimum_supported_version_code'] ?>" dir="ltr" required></label><label class="r6-policy-choice"><input type="checkbox" name="required" value="1" <?= !empty($release['required']) ? 'checked' : '' ?>><span class="r6-policy-copy"><strong>تحديث إجباري</strong></span></label><button class="r6-btn r6-btn--secondary" type="submit">حفظ السياسة</button></form></details>
                                    <?php if (!empty($release['enabled']) || !empty($release['is_active'])): ?><form method="post" data-confirm="تعطيل هذا الإصدار؟"><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="disable_release"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>"><button class="button button--danger r6-btn r6-btn--danger" type="submit">تعطيل</button></form><?php endif; ?>
                                </div>
                                <details class="r6-destructive"><summary>خيارات متقدمة</summary><div class="destructive-zone r6-destructive-zone"><strong>حذف الإصدار نهائيًا</strong><small>يحذف سجل الإصدار وملف APK المرتبط به من الاستضافة. لا يمكن التراجع عن هذا الإجراء.</small><form method="post" data-confirm="حذف هذا الإصدار نهائيًا؟ سيُحذف سجل الإصدار وملف APK، وإذا كان الإصدار منشورًا فستعود سياسة التحديث إلى الوضع الآمن."><input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="delete_release"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>"><button class="button button--danger r6-btn r6-btn--danger" type="submit">حذف الإصدار</button></form></div></details>
                            </div>
                        </details>
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
                            <details class="technical-disclosure"><summary>الاستهداف التقني</summary><div class="technical-disclosure__body"><?php cc_fact_list([['label' => 'معرّف الرسالة', 'value' => (string) $row['message_key'], 'ltr' => true]], 'fact-list--technical'); ?></div></details>                            <details class="inline-editor">
                                <summary>تعديل الإعلان</summary>
                                <form method="post" class="admin-form admin-form--grid">
                                    <input type="hidden" name="csrf_token" value="<?= cc_e($csrf) ?>"><input type="hidden" name="action" value="update_announcement"><input type="hidden" name="announcement_id" value="<?= (int) $row['id'] ?>">
                                    <label class="form-field"><span>العنوان</span><input name="title" maxlength="160" value="<?= cc_e((string) $row['title']) ?>" required></label>
                                    <label class="form-field form-field--wide"><span>نص الإعلان</span><textarea name="message" maxlength="10000" required><?= cc_e((string) $row['message']) ?></textarea></label>
                                    <label class="form-field"><span>الأهمية</span><select name="severity"><option value="INFO" <?= $row['severity'] === 'INFO' ? 'selected' : '' ?>>معلومات</option><option value="WARNING" <?= $row['severity'] === 'WARNING' ? 'selected' : '' ?>>تحذير</option><option value="IMPORTANT" <?= $row['severity'] === 'IMPORTANT' ? 'selected' : '' ?>>مهم</option></select></label>
                                    <label class="form-field"><span>الجمهور</span><select name="target"><option value="ALL" <?= $row['target'] === 'ALL' ? 'selected' : '' ?>>الجميع</option><option value="MOBILE" <?= $row['target'] === 'MOBILE' ? 'selected' : '' ?>>الجوال والتابلت</option><option value="TV" <?= $row['target'] === 'TV' ? 'selected' : '' ?>>التلفزيون</option></select></label>
                                    <label class="form-field"><span>البداية</span><input name="starts_at" type="datetime-local" value="<?= cc_e(ops_datetime_local_value($row['starts_at'])) ?>" required></label>
                                    <label class="form-field"><span>النهاية <small>اختياري</small></span><input name="ends_at" type="datetime-local" value="<?= cc_e(ops_datetime_local_value($row['ends_at'])) ?>"></label>
                                    <label class="form-field"><span>أدنى إصدار <small>اختياري</small></span><input name="minimum_version_code" type="number" min="1" value="<?= $row['minimum_version_code'] === null ? '' : (int) $row['minimum_version_code'] ?>"></label>
                                    <label class="form-field"><span>أعلى إصدار <small>اختياري</small></span><input name="maximum_version_code" type="number" min="1" value="<?= $row['maximum_version_code'] === null ? '' : (int) $row['maximum_version_code'] ?>"></label>
                                    <div class="check-list form-field--wide"><label class="check-control"><input name="show_once" type="checkbox" value="1" <?= !empty($row['show_once']) ? 'checked' : '' ?>> يظهر مرة واحدة</label><label class="check-control"><input name="persistent" type="checkbox" value="1" <?= !empty($row['persistent']) ? 'checked' : '' ?>> يبقى ظاهرًا</label><label class="check-control"><input name="enabled" type="checkbox" value="1" <?= !empty($row['enabled']) ? 'checked' : '' ?>> مفعّل</label></div>
                                    <div class="form-submit form-field--wide"><button class="button button--secondary" type="submit">حفظ التعديل</button></div>
                                </form>
                            </details>

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
