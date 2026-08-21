<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';
require_once __DIR__ . '/_layout.php';
define('HULK_OPERATIONS_ADMIN', true);
require_once __DIR__ . '/actions.php';

try {
    $admin = ops_require_admin();
    $db = ops_db();
} catch (Throwable $exception) {
    error_log('HULK Operations admin unavailable: ' . $exception->getMessage());
    http_response_code(503);
    exit('تعذر تشغيل لوحة الإدارة. تحقق من قاعدة البيانات وملف config.php.');
}

$allowedSections = ['dashboard', 'releases', 'announcements', 'service', 'features', 'audit'];
$section = (string) ($_GET['section'] ?? 'dashboard');
if (!in_array($section, $allowedSections, true)) {
    $section = 'dashboard';
}

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    ops_admin_handle_post($db, $admin, $section);
}

$flash = ops_take_flash();
$csrf = ops_csrf_token();

if ($section === 'dashboard') {
    $activeRelease = ops_active_release($db);
    $latestRelease = $db->query('SELECT * FROM app_releases ORDER BY created_at DESC, id DESC LIMIT 1')->fetch();
    $service = ops_service_snapshot($db);
    $now = new DateTimeImmutable('now');
    $activeAnnouncements = ops_active_announcements($db, $now);
    $features = ops_feature_snapshot($db);
    $lastModified = $db->query(
        'SELECT MAX(changed_at) FROM ('
        . 'SELECT MAX(updated_at) AS changed_at FROM app_releases '
        . 'UNION ALL SELECT MAX(updated_at) FROM app_announcements '
        . 'UNION ALL SELECT MAX(updated_at) FROM app_feature_flags '
        . 'UNION ALL SELECT MAX(updated_at) FROM app_service_status'
        . ') AS operations_changes'
    )->fetchColumn();
} elseif ($section === 'releases') {
    $releases = $db->query('SELECT * FROM app_releases ORDER BY version_code DESC LIMIT 100')->fetchAll();
} elseif ($section === 'announcements') {
    $announcements = $db->query(
        'SELECT * FROM app_announcements ORDER BY created_at DESC, id DESC LIMIT 100'
    )->fetchAll();
} elseif ($section === 'service') {
    $serviceRow = $db->query('SELECT * FROM app_service_status WHERE id = 1 LIMIT 1')->fetch();
    if (!is_array($serviceRow)) {
        $serviceRow = [
            'status' => 'OPERATIONAL',
            'message' => '',
            'starts_at' => null,
            'estimated_end_at' => null,
        ];
    }
} elseif ($section === 'features') {
    $featureRows = $db->query('SELECT * FROM app_feature_flags ORDER BY flag_key')->fetchAll();
    $features = ops_normalize_feature_flags($featureRows);
} else {
    $auditRows = $db->query(
        'SELECT a.*, u.username FROM app_admin_audit a '
        . 'LEFT JOIN app_admin_users u ON u.id = a.admin_user_id '
        . 'ORDER BY a.id DESC LIMIT 150'
    )->fetchAll();
}

$titles = [
    'dashboard' => 'لوحة التحكم',
    'releases' => 'إدارة تحديثات التطبيق',
    'announcements' => 'الرسائل العامة',
    'service' => 'حالة الخدمة',
    'features' => 'المميزات التشغيلية',
    'audit' => 'سجل العمليات',
];

ops_admin_page_start($titles[$section], $section, $admin);
ops_admin_flash($flash);
?>

<?php if ($section === 'dashboard'): ?>
    <?php
    $activeVersionCode = $activeRelease ? (int) $activeRelease['version_code'] : 64;
    $activeVersionName = $activeRelease ? (string) $activeRelease['version_name'] : '0.9.3.20';
    $minimumCode = $activeRelease ? (int) $activeRelease['minimum_supported_version_code'] : 64;
    $updateLabel = $activeRelease
        ? ((bool) $activeRelease['required'] ? 'إجباري' : 'اختياري')
        : 'لا يوجد تحديث منشور';
    $serviceStatus = (string) $service['status'];
    $releaseUrl = $activeRelease ? ops_public_url((string) $activeRelease['apk_path']) : null;
    ?>
    <header class="page-head">
        <div><h1>HULK Operations Center</h1><p>ملخص تشغيلي آمن وخفيف لتطبيق HULK SA.</p></div>
        <a class="button secondary" href="<?= ops_e(ops_public_url('api/app/v1/config/')) ?>" target="_blank" rel="noreferrer">فتح Operations API</a>
    </header>
    <section class="grid stats">
        <article class="card"><span class="stat-label">Current Published Version</span><strong class="stat-value"><?= ops_e($activeVersionName) ?> / <?= $activeVersionCode ?></strong></article>
        <article class="card"><span class="stat-label">Minimum Supported Version</span><strong class="stat-value"><?= $minimumCode ?></strong></article>
        <article class="card"><span class="stat-label">Update Status</span><strong class="stat-value"><?= ops_e($updateLabel) ?></strong></article>
        <article class="card"><span class="stat-label">Service Status</span><strong class="stat-value"><?= ops_e($serviceStatus) ?></strong></article>
        <article class="card"><span class="stat-label">Maintenance Mode</span><strong class="stat-value"><?= $serviceStatus === 'MAINTENANCE' ? 'مفعّل' : 'متوقف' ?></strong></article>
        <article class="card"><span class="stat-label">Active Announcement</span><strong class="stat-value"><?= ops_e((string) ($activeAnnouncements[0]['message_key'] ?? 'لا توجد')) ?></strong></article>
        <article class="card"><span class="stat-label">Active Feature Flags</span><strong class="stat-value"><?= count(array_filter($features)) ?> / <?= count($features) ?></strong></article>
        <article class="card"><span class="stat-label">آخر تعديل</span><strong class="stat-value"><?= ops_e((string) ($lastModified ?: '—')) ?></strong></article>
    </section>
    <section class="grid two" style="margin-top:14px">
        <article class="card">
            <h2>آخر إصدار APK تم رفعه</h2>
            <?php if (is_array($latestRelease)): ?>
                <p><strong><?= ops_e((string) $latestRelease['version_name']) ?> / <?= (int) $latestRelease['version_code'] ?></strong></p>
                <div class="stat-label">SHA-256</div>
                <p class="mono"><?= ops_e((string) $latestRelease['apk_sha256']) ?></p>
                <div class="actions">
                    <button class="button secondary" type="button" data-copy="<?= ops_e(ops_public_url((string) $latestRelease['apk_path'])) ?>">نسخ رابط APK</button>
                    <a class="button secondary" href="index.php?section=releases">إدارة الإصدارات</a>
                </div>
            <?php else: ?><p class="empty">لم يتم رفع أي APK بعد.</p><?php endif; ?>
        </article>
        <article class="card">
            <h2>النسخة الحالية</h2>
            <p class="muted">الرابط المنشور لا يظهر إلا بعد تفعيل إصدار.</p>
            <?php if ($releaseUrl !== null): ?>
                <p class="mono"><?= ops_e($releaseUrl) ?></p>
                <button class="button" type="button" data-copy="<?= ops_e($releaseUrl) ?>">نسخ رابط التحميل</button>
            <?php else: ?><p class="empty">Safe default: 0.9.3.20 / 64 بدون تحديث مفروض.</p><?php endif; ?>
        </article>
    </section>

<?php elseif ($section === 'releases'): ?>
    <header class="page-head"><div><h1>التحديثات</h1><p>رفع APK ثم مراجعته وتفعيله بشكل منفصل.</p></div></header>
    <section class="card">
        <h2>رفع إصدار جديد</h2>
        <form method="post" enctype="multipart/form-data" class="form-grid">
            <input type="hidden" name="csrf_token" value="<?= ops_e($csrf) ?>">
            <input type="hidden" name="action" value="upload_release">
            <div class="field"><label>ملف APK</label><input name="apk" type="file" accept=".apk,application/vnd.android.package-archive" required></div>
            <div class="field"><label>Version Name</label><input name="version_name" maxlength="32" placeholder="0.9.3.21" required></div>
            <div class="field"><label>Version Code</label><input name="version_code" type="number" min="1" required></div>
            <div class="field"><label>Minimum Supported Version Code</label><input name="minimum_supported_version_code" type="number" min="1" value="64" required></div>
            <div class="field full"><label>Release Notes</label><textarea name="release_notes" maxlength="10000"></textarea></div>
            <label class="check full"><input type="checkbox" name="required" value="1"> Required Update — سيصبح هذا Version Code هو الحد الأدنى</label>
            <div class="actions full"><button class="button" type="submit">رفع APK وحساب SHA-256</button></div>
        </form>
    </section>
    <section class="card" style="margin-top:14px">
        <h2>App Releases</h2>
        <?php if ($releases === []): ?><p class="empty">لا توجد إصدارات.</p><?php else: ?>
        <div class="table-wrap"><table>
            <thead><tr><th>الإصدار</th><th>الحالة</th><th>السياسة</th><th>SHA-256</th><th>الحجم</th><th>الإجراءات</th></tr></thead>
            <tbody>
            <?php foreach ($releases as $release): ?>
                <?php $apkUrl = ops_public_url((string) $release['apk_path']); ?>
                <tr>
                    <td><strong><?= ops_e((string) $release['version_name']) ?></strong><br><span class="muted">Code <?= (int) $release['version_code'] ?></span></td>
                    <td>
                        <?php if ((bool) $release['is_active']): ?><span class="status ok">منشور</span>
                        <?php elseif ((bool) $release['enabled']): ?><span class="status warn">مفعّل</span>
                        <?php else: ?><span class="status">غير نشط</span><?php endif; ?>
                    </td>
                    <td>
                        <form method="post" class="actions" data-confirm="هل تريد تغيير سياسة هذا الإصدار؟">
                            <input type="hidden" name="csrf_token" value="<?= ops_e($csrf) ?>">
                            <input type="hidden" name="action" value="update_release_policy">
                            <input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>">
                            <input style="width:90px" name="minimum_supported_version_code" type="number" min="1" max="<?= (int) $release['version_code'] ?>" value="<?= (int) $release['minimum_supported_version_code'] ?>" required>
                            <label class="check"><input type="checkbox" name="required" value="1" <?= (bool) $release['required'] ? 'checked' : '' ?>> إجباري</label>
                            <button class="button secondary" type="submit">حفظ</button>
                        </form>
                    </td>
                    <td class="mono"><?= ops_e((string) $release['apk_sha256']) ?></td>
                    <td><?= number_format(((int) $release['apk_size_bytes']) / 1048576, 1) ?> MB</td>
                    <td><div class="actions">
                        <button class="button secondary" type="button" data-copy="<?= ops_e($apkUrl) ?>">نسخ الرابط</button>
                        <?php if (!(bool) $release['is_active']): ?>
                            <form method="post" data-confirm="هل تريد تفعيل هذا الإصدار<?= (bool) $release['required'] ? ' كتحديث إجباري' : '' ?>؟">
                                <input type="hidden" name="csrf_token" value="<?= ops_e($csrf) ?>"><input type="hidden" name="action" value="activate_release"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>">
                                <button class="button" type="submit">تفعيل</button>
                            </form>
                        <?php endif; ?>
                        <?php if ((bool) $release['enabled'] || (bool) $release['is_active']): ?>
                            <form method="post" data-confirm="هل تريد تعطيل هذا الإصدار؟">
                                <input type="hidden" name="csrf_token" value="<?= ops_e($csrf) ?>"><input type="hidden" name="action" value="disable_release"><input type="hidden" name="release_id" value="<?= (int) $release['id'] ?>">
                                <button class="button danger" type="submit">تعطيل</button>
                            </form>
                        <?php endif; ?>
                    </div></td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table></div>
        <?php endif; ?>
    </section>

<?php elseif ($section === 'announcements'): ?>
    <header class="page-head"><div><h1>الرسائل</h1><p>تظهر عند فتح التطبيق أو أول تحديث طبيعي، بدون Push.</p></div></header>
    <section class="card">
        <h2>إنشاء رسالة عامة</h2>
        <form method="post" class="form-grid">
            <input type="hidden" name="csrf_token" value="<?= ops_e($csrf) ?>"><input type="hidden" name="action" value="create_announcement">
            <div class="field"><label>Message ID (اختياري)</label><input name="message_key" maxlength="80" placeholder="MSG-001"></div>
            <div class="field"><label>Title</label><input name="title" maxlength="160" required></div>
            <div class="field full"><label>Message</label><textarea name="message" maxlength="10000" required></textarea></div>
            <div class="field"><label>Severity</label><select name="severity"><option>INFO</option><option>WARNING</option><option>IMPORTANT</option></select></div>
            <div class="field"><label>Target</label><select name="target"><option>ALL</option><option>MOBILE</option><option>TV</option></select></div>
            <div class="field"><label>Start Date</label><input name="starts_at" type="datetime-local" value="<?= ops_e(date('Y-m-d\TH:i')) ?>" required></div>
            <div class="field"><label>End Date (اختياري)</label><input name="ends_at" type="datetime-local"></div>
            <div class="field"><label>Minimum Version (اختياري)</label><input name="minimum_version_code" type="number" min="1"></div>
            <div class="field"><label>Maximum Version (اختياري)</label><input name="maximum_version_code" type="number" min="1"></div>
            <label class="check"><input type="checkbox" name="enabled" value="1" checked> Enabled</label>
            <label class="check"><input type="checkbox" name="show_once" value="1" checked> Show Once</label>
            <label class="check"><input type="checkbox" name="persistent" value="1"> Persistent Banner</label>
            <div class="actions full"><button class="button" type="submit">حفظ الرسالة</button></div>
        </form>
    </section>
    <section class="card" style="margin-top:14px">
        <h2>الرسائل المحفوظة</h2>
        <?php if ($announcements === []): ?><p class="empty">لا توجد رسائل.</p><?php else: ?>
        <div class="table-wrap"><table><thead><tr><th>ID</th><th>العنوان</th><th>النوع والهدف</th><th>المدة</th><th>الحالة</th><th></th></tr></thead><tbody>
        <?php foreach ($announcements as $announcement): ?>
            <tr>
                <td class="mono"><?= ops_e((string) $announcement['message_key']) ?></td>
                <td><strong><?= ops_e((string) $announcement['title']) ?></strong><br><span class="muted"><?= ops_e(ops_text_excerpt((string) $announcement['message'], 120)) ?></span></td>
                <td><?= ops_e((string) $announcement['severity']) ?> / <?= ops_e((string) $announcement['target']) ?><br><span class="muted"><?= (bool) $announcement['show_once'] ? 'مرة واحدة' : 'متكرر' ?><?= (bool) $announcement['persistent'] ? ' · ثابت' : '' ?></span></td>
                <td><?= ops_e((string) $announcement['starts_at']) ?><br><span class="muted"><?= ops_e((string) ($announcement['ends_at'] ?: 'بدون انتهاء')) ?></span></td>
                <td><?= (bool) $announcement['enabled'] ? '<span class="status ok">مفعلة</span>' : '<span class="status">معطلة</span>' ?></td>
                <td><?php if ((bool) $announcement['enabled']): ?><form method="post" data-confirm="هل تريد تعطيل هذه الرسالة؟"><input type="hidden" name="csrf_token" value="<?= ops_e($csrf) ?>"><input type="hidden" name="action" value="disable_announcement"><input type="hidden" name="announcement_id" value="<?= (int) $announcement['id'] ?>"><button class="button danger" type="submit">تعطيل</button></form><?php endif; ?></td>
            </tr>
        <?php endforeach; ?>
        </tbody></table></div><?php endif; ?>
    </section>

<?php elseif ($section === 'service'): ?>
    <header class="page-head"><div><h1>حالة الخدمة</h1><p>تعطل Operations API لا يشغّل الصيانة تلقائيًا داخل التطبيق.</p></div></header>
    <section class="card">
        <form method="post" class="form-grid" data-confirm="هل تريد تغيير حالة الخدمة الآن؟">
            <input type="hidden" name="csrf_token" value="<?= ops_e($csrf) ?>"><input type="hidden" name="action" value="update_service_status">
            <div class="field"><label>Status</label><select name="status">
                <?php foreach (['OPERATIONAL', 'DEGRADED', 'MAINTENANCE'] as $status): ?><option <?= $serviceRow['status'] === $status ? 'selected' : '' ?>><?= $status ?></option><?php endforeach; ?>
            </select></div>
            <div class="field"><label>Start Time</label><input name="starts_at" type="datetime-local" value="<?= ops_e(ops_datetime_local_value($serviceRow['starts_at'] ?? null)) ?>"></div>
            <div class="field full"><label>Maintenance / Degraded Message</label><textarea name="message" maxlength="2000"><?= ops_e((string) ($serviceRow['message'] ?? '')) ?></textarea></div>
            <div class="field"><label>Estimated End Time (اختياري)</label><input name="estimated_end_at" type="datetime-local" value="<?= ops_e(ops_datetime_local_value($serviceRow['estimated_end_at'] ?? null)) ?>"></div>
            <div class="actions full"><button class="button" type="submit">تحديث الحالة</button></div>
        </form>
    </section>

<?php elseif ($section === 'features'): ?>
    <?php $featureLabels = ['downloads_enabled' => 'التنزيلات', 'episode_notifications_enabled' => 'تنبيهات الحلقات', 'smart_recommendations_enabled' => 'التوصيات الذكية', 'live_tv_pro_enabled' => 'Live TV Pro']; ?>
    <header class="page-head"><div><h1>المميزات</h1><p>أسماء ثابتة معرّفة مسبقًا داخل Android؛ أي Flag غير معروف يتم تجاهله.</p></div></header>
    <section class="grid two">
        <?php foreach (ops_known_feature_flags() as $flagKey): ?>
            <?php $enabled = (bool) ($features[$flagKey] ?? true); ?>
            <article class="card">
                <span class="status <?= $enabled ? 'ok' : 'bad' ?>"><?= $enabled ? 'مفعلة' : 'متوقفة' ?></span>
                <h2 style="margin-top:12px"><?= ops_e($featureLabels[$flagKey] ?? $flagKey) ?></h2>
                <p class="mono muted"><?= ops_e($flagKey) ?></p>
                <form method="post" data-confirm="هل تريد <?= $enabled ? 'تعطيل' : 'تفعيل' ?> هذه الميزة؟">
                    <input type="hidden" name="csrf_token" value="<?= ops_e($csrf) ?>"><input type="hidden" name="action" value="toggle_feature_flag"><input type="hidden" name="flag_key" value="<?= ops_e($flagKey) ?>"><input type="hidden" name="enabled" value="<?= $enabled ? '0' : '1' ?>">
                    <button class="button <?= $enabled ? 'danger' : '' ?>" type="submit"><?= $enabled ? 'تعطيل' : 'تفعيل' ?></button>
                </form>
            </article>
        <?php endforeach; ?>
    </section>

<?php else: ?>
    <header class="page-head"><div><h1>سجل العمليات</h1><p>يسجل التغييرات الإدارية المهمة فقط، بدون كلمات مرور أو أسرار.</p></div></header>
    <section class="card">
        <?php if ($auditRows === []): ?><p class="empty">لا توجد عمليات مسجلة.</p><?php else: ?>
        <div class="table-wrap"><table><thead><tr><th>الوقت</th><th>المسؤول</th><th>العملية</th><th>التفاصيل</th></tr></thead><tbody>
        <?php foreach ($auditRows as $audit): ?><tr><td><?= ops_e((string) $audit['created_at']) ?></td><td><?= ops_e((string) ($audit['username'] ?: 'system')) ?></td><td class="mono"><?= ops_e((string) $audit['action']) ?></td><td class="mono"><?= ops_e((string) ($audit['details'] ?: '—')) ?></td></tr><?php endforeach; ?>
        </tbody></table></div><?php endif; ?>
    </section>
<?php endif; ?>

<?php ops_admin_page_end(); ?>
