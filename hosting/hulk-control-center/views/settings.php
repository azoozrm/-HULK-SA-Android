<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر قراءة حدود التشغيل', 'لم تُعرض قيم افتراضية. تحقق من ملف الإعداد الخاص ثم أعد المحاولة.', cc_url('settings'));
    return;
}
$settings = is_array($pageData['settings'] ?? null) ? $pageData['settings'] : [];
$application = $settings['application'] ?? [];
$authentication = $settings['authentication'] ?? [];
$presence = $settings['presence'] ?? [];
$diagnostics = $settings['diagnostics'] ?? [];
$health = $settings['host_health'] ?? [];
?>
<section class="module-stack settings-module">
    <?php cc_alert('حدود إعداد آمنة للقراءة فقط', 'القيم القابلة للتعديل تبقى لدى Operations أو سلطة الموزعين أو ملف النشر الخاص. لا تعرض هذه الصفحة أسرارًا ولا تنشئ مالكًا ثانيًا.', 'info'); ?>
    <section class="settings-grid">
        <article class="panel settings-card"><header class="panel__header"><div><span class="eyebrow">Control Center</span><h2>التطبيق والمصادقة</h2></div><?php cc_status_badge('قراءة فقط', 'info'); ?></header><dl class="settings-list"><div><dt>المسار</dt><dd dir="ltr"><?= cc_e($application['route_path'] ?? '—') ?></dd></div><div><dt>المنطقة الزمنية</dt><dd dir="ltr"><?= cc_e($application['timezone'] ?? '—') ?></dd></div><div><dt>سلطة الحساب</dt><dd><code><?= cc_e($authentication['authority'] ?? '—') ?></code></dd></div><div><dt>قفل المحاولات</dt><dd>بعد <?= (int) ($authentication['login_max_attempts'] ?? 0) ?> محاولات · <?= (int) ($authentication['login_lock_seconds'] ?? 0) ?> ثانية</dd></div><div><dt>الجلسة</dt><dd>Secure · HttpOnly · SameSite=<?= cc_e($authentication['same_site'] ?? '—') ?></dd></div></dl><p class="settings-card__note">استعادة حساب المالك عملية تشغيلية موثقة وليست إدارة RBAC داخل الواجهة.</p></article>
        <article class="panel settings-card"><header class="panel__header"><div><span class="eyebrow">Presence</span><h2>الحضور والاحتفاظ</h2></div><a class="button button--quiet" href="<?= cc_e(cc_url('sessions')) ?>">عرض الجلسات</a></header><dl class="settings-list"><div><dt>نبضة التطبيق</dt><dd><?= (int) ($presence['heartbeat_seconds'] ?? 0) ?> ثانية</dd></div><div><dt>Online TTL</dt><dd><?= (int) ($presence['online_ttl_seconds'] ?? 0) ?> ثانية</dd></div><div><dt>احتفاظ الجلسات</dt><dd><?= (int) ($presence['session_retention_days'] ?? 0) ?> يومًا</dd></div><div><dt>احتفاظ الأجهزة</dt><dd><?= (int) ($presence['device_retention_days'] ?? 0) ?> يومًا</dd></div><div><dt>دفعة التنظيف</dt><dd><?= (int) ($presence['cleanup_batch_size'] ?? 0) ?> صف</dd></div></dl></article>
        <article class="panel settings-card"><header class="panel__header"><div><span class="eyebrow">Diagnostics</span><h2>التشخيصات</h2></div><a class="button button--quiet" href="<?= cc_e(cc_url('diagnostics')) ?>">عرض التشخيصات</a></header><dl class="settings-list"><div><dt>مدة الاحتفاظ</dt><dd><?= (int) ($diagnostics['retention_days'] ?? 0) ?> يومًا</dd></div><div><dt>دفعة التنظيف</dt><dd><?= (int) ($diagnostics['cleanup_batch_size'] ?? 0) ?> صف</dd></div></dl><p class="settings-card__note">العقد typed فقط ولا يقبل رسائل حرة أو raw logs أو بيانات اعتماد.</p></article>
        <article class="panel settings-card"><header class="panel__header"><div><span class="eyebrow">Host Health</span><h2>ميزانية الفحص</h2></div><a class="button button--quiet" href="<?= cc_e(cc_url('host-health')) ?>">عرض الصحة</a></header><dl class="settings-list"><div><dt>DNS / الاتصال / الطلب</dt><dd><?= (int) ($health['dns_timeout_ms'] ?? 0) ?> / <?= (int) ($health['connect_timeout_ms'] ?? 0) ?> / <?= (int) ($health['request_timeout_ms'] ?? 0) ?> ms</dd></div><div><dt>حد التشغيل</dt><dd><?= (int) ($health['max_runtime_seconds'] ?? 0) ?> ثانية</dd></div><div><dt>العمل لكل تشغيل</dt><dd><?= (int) ($health['max_hosts_per_run'] ?? 0) ?> من نافذة <?= (int) ($health['candidate_limit'] ?? 0) ?></dd></div><div><dt>مدة الاحتفاظ</dt><dd><?= (int) ($health['retention_days'] ?? 0) ?> يومًا</dd></div><div><dt>دفعة التنظيف</dt><dd><?= (int) ($health['cleanup_batch_size'] ?? 0) ?> صف</dd></div></dl></article>
    </section>
    <section class="panel ownership-panel"><header class="panel__header"><div><span class="eyebrow">Single-owner boundaries</span><h2>أماكن التعديل الموثوقة</h2></div></header><div class="ownership-links"><a href="<?= cc_e(cc_url('releases')) ?>"><strong>الإصدارات والتحديثات</strong><span>Operations</span></a><a href="<?= cc_e(cc_url('service')) ?>"><strong>حالة الخدمة</strong><span>Operations</span></a><a href="<?= cc_e(cc_url('growth')) ?>"><strong>التجديد والدعم</strong><span>Operations</span></a><a href="<?= cc_e(cc_url('resellers')) ?>"><strong>الموزعون</strong><span>Reseller authority</span></a><a href="<?= cc_e(cc_url('audit')) ?>"><strong>سجل الإدارة</strong><span>Audit authority</span></a></div></section>
</section>
