<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر قراءة إعدادات المركز', 'لم تُعرض قيم افتراضية. تحقق من ملف الإعداد الخاص ثم أعد المحاولة.', cc_url('settings'));
    return;
}
$settings = is_array($pageData['settings'] ?? null) ? $pageData['settings'] : [];
$application = $settings['application'] ?? [];
$authentication = $settings['authentication'] ?? [];
$presence = $settings['presence'] ?? [];
$diagnostics = $settings['diagnostics'] ?? [];
$health = $settings['host_health'] ?? [];
$durationLabel = static function (int $seconds): string {
    $seconds = max(0, $seconds);
    if ($seconds < 60) {
        return $seconds . ' ثانية';
    }
    $minutes = intdiv($seconds, 60);
    $remainder = $seconds % 60;
    return $remainder === 0
        ? $minutes . ' دقيقة'
        : $minutes . ' دقيقة و' . $remainder . ' ثانية';
};
$lockDuration = $durationLabel((int) ($authentication['login_lock_seconds'] ?? 0));
$onlineDuration = $durationLabel((int) ($presence['online_ttl_seconds'] ?? 0));
?>
<section class="module-stack settings-module">
    <section class="settings-overview panel">
        <div class="settings-overview__copy">
            <span class="eyebrow">إعدادات مركز التحكم</span>
            <h2>حدود التشغيل في مكان واحد</h2>
            <p>هذه الصفحة للقراءة فقط. تعرض القيم التي يحتاجها المالك لفهم عمل المركز، بينما تبقى التعديلات داخل الوحدات المسؤولة عنها.</p>
        </div>
        <div class="settings-overview__badge"><?php cc_status_badge('قراءة فقط', 'info'); ?></div>
    </section>

    <section class="settings-grid" aria-label="ملخص إعدادات مركز التحكم">
        <article class="panel settings-card">
            <header class="settings-card__header"><span class="settings-card__icon"><?= cc_icon('shield') ?></span><div><h2>تسجيل الدخول والحماية</h2><p>حساب المالك محمي بقفل تلقائي للجلسات والمحاولات المتكررة.</p></div></header>
            <dl class="settings-list settings-list--primary">
                <div><dt>قفل المحاولات</dt><dd>بعد <?= (int) ($authentication['login_max_attempts'] ?? 0) ?> محاولات لمدة <?= cc_e($lockDuration) ?></dd></div>
                <div><dt>حماية الجلسة</dt><dd>اتصال آمن وملف ارتباط محمي</dd></div>
            </dl>
            <details class="technical-disclosure"><summary>تفاصيل تقنية</summary><dl class="settings-list settings-list--technical"><div><dt>المسار</dt><dd><bdi dir="ltr"><?= cc_e($application['route_path'] ?? '—') ?></bdi></dd></div><div><dt>المنطقة الزمنية</dt><dd><bdi dir="ltr"><?= cc_e($application['timezone'] ?? '—') ?></bdi></dd></div><div><dt>مصدر الحساب</dt><dd><code dir="ltr"><?= cc_e($authentication['authority'] ?? '—') ?></code></dd></div><div><dt>سياسة الارتباط</dt><dd><bdi dir="ltr">Secure · HttpOnly · SameSite=<?= cc_e($authentication['same_site'] ?? '—') ?></bdi></dd></div></dl></details>
        </article>

        <article class="panel settings-card">
            <header class="settings-card__header"><span class="settings-card__icon"><?= cc_icon('users') ?></span><div><h2>نشاط المستخدمين والجلسات</h2><p>تُحدد حالة الاتصال من آخر نشاط وصل إلى الخادم، لا من وجود سجل الجهاز فقط.</p></div></header>
            <dl class="settings-list settings-list--primary">
                <div><dt>المستخدم متصل</dt><dd>إذا وصل نشاط خلال آخر <?= cc_e($onlineDuration) ?></dd></div>
                <div><dt>الاحتفاظ بالسجل</dt><dd><?= (int) ($presence['session_retention_days'] ?? 0) ?> يومًا للجلسات و<?= (int) ($presence['device_retention_days'] ?? 0) ?> يومًا للأجهزة</dd></div>
            </dl>
            <div class="settings-card__action"><a class="button button--quiet" href="<?= cc_e(cc_url('sessions')) ?>">عرض سجل الجلسات</a></div>
            <details class="technical-disclosure"><summary>تفاصيل تقنية</summary><dl class="settings-list settings-list--technical"><div><dt>فاصل النشاط</dt><dd><?= (int) ($presence['heartbeat_seconds'] ?? 0) ?> ثانية</dd></div><div><dt>مهلة الاتصال</dt><dd><?= (int) ($presence['online_ttl_seconds'] ?? 0) ?> ثانية</dd></div><div><dt>دفعة التنظيف</dt><dd><?= (int) ($presence['cleanup_batch_size'] ?? 0) ?> سجل</dd></div></dl></details>
        </article>

        <article class="panel settings-card">
            <header class="settings-card__header"><span class="settings-card__icon"><?= cc_icon('heart') ?></span><div><h2>صحة الهوستات</h2><p>الفحص الدوري محدود بالوقت وعدد الهوستات حتى لا يؤثر في تشغيل المركز.</p></div></header>
            <dl class="settings-list settings-list--primary">
                <div><dt>الحد لكل تشغيل</dt><dd>حتى <?= (int) ($health['max_hosts_per_run'] ?? 0) ?> هوست</dd></div>
                <div><dt>مدة الاحتفاظ</dt><dd><?= (int) ($health['retention_days'] ?? 0) ?> يومًا</dd></div>
            </dl>
            <div class="settings-card__action"><a class="button button--quiet" href="<?= cc_e(cc_url('host-health')) ?>">عرض صحة الهوستات</a></div>
            <details class="technical-disclosure"><summary>تفاصيل تقنية</summary><dl class="settings-list settings-list--technical"><div><dt>مهلة اسم النطاق</dt><dd><?= (int) ($health['dns_timeout_ms'] ?? 0) ?> مللي ثانية</dd></div><div><dt>مهلة الاتصال</dt><dd><?= (int) ($health['connect_timeout_ms'] ?? 0) ?> مللي ثانية</dd></div><div><dt>مهلة الطلب</dt><dd><?= (int) ($health['request_timeout_ms'] ?? 0) ?> مللي ثانية</dd></div><div><dt>حد التشغيل</dt><dd><?= (int) ($health['max_runtime_seconds'] ?? 0) ?> ثانية من نافذة <?= (int) ($health['candidate_limit'] ?? 0) ?> هوست</dd></div><div><dt>دفعة التنظيف</dt><dd><?= (int) ($health['cleanup_batch_size'] ?? 0) ?> سجل</dd></div></dl></details>
        </article>

        <article class="panel settings-card">
            <header class="settings-card__header"><span class="settings-card__icon"><?= cc_icon('diagnostic') ?></span><div><h2>أعطال التطبيق</h2><p>تُحفظ أحداث مصنفة وآمنة فقط، من دون سجلات خام أو بيانات دخول.</p></div></header>
            <dl class="settings-list settings-list--primary"><div><dt>مدة الاحتفاظ</dt><dd><?= (int) ($diagnostics['retention_days'] ?? 0) ?> يومًا</dd></div><div><dt>نوع البيانات</dt><dd>رموز أعطال محددة مسبقًا</dd></div></dl>
            <div class="settings-card__action"><a class="button button--quiet" href="<?= cc_e(cc_url('diagnostics')) ?>">عرض أعطال التطبيق</a></div>
            <details class="technical-disclosure"><summary>تفاصيل تقنية</summary><dl class="settings-list settings-list--technical"><div><dt>دفعة التنظيف</dt><dd><?= (int) ($diagnostics['cleanup_batch_size'] ?? 0) ?> سجل</dd></div></dl></details>
        </article>
    </section>

    <section class="panel ownership-panel">
        <header class="panel__header"><div><span class="eyebrow">الجهات المسؤولة عن التعديل</span><h2>كل تغيير من مكانه الصحيح</h2><p class="muted-copy">لا توجد عناصر تحكم وهمية هنا. استخدم الوحدة المختصة لتعديل كل جانب.</p></div></header>
        <div class="ownership-links">
            <a href="<?= cc_e(cc_url('releases')) ?>"><span class="ownership-links__icon"><?= cc_icon('package') ?></span><strong>الإصدارات والتحديثات</strong><small>إدارة التطبيق</small></a>
            <a href="<?= cc_e(cc_url('service')) ?>"><span class="ownership-links__icon"><?= cc_icon('pulse') ?></span><strong>حالة الخدمة</strong><small>إدارة التطبيق</small></a>
            <a href="<?= cc_e(cc_url('growth')) ?>"><span class="ownership-links__icon"><?= cc_icon('trend') ?></span><strong>التجديد والدعم</strong><small>إدارة التطبيق</small></a>
            <a href="<?= cc_e(cc_url('resellers')) ?>"><span class="ownership-links__icon"><?= cc_icon('briefcase') ?></span><strong>الموزعون والوصول</strong><small>إدارة الموزعين</small></a>
            <a href="<?= cc_e(cc_url('audit')) ?>"><span class="ownership-links__icon"><?= cc_icon('audit') ?></span><strong>النشاط الإداري</strong><small>سجل التغييرات</small></a>
        </div>
    </section>
</section>
