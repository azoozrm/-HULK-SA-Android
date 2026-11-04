<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر قراءة اعدادات المركز', 'لم تُعرض قيم افتراضية. تحقق من ملف الاعداد الخاص ثم اعد المحاولة', cc_url('settings'));
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
    return $remainder === 0 ? $minutes . ' دقيقة' : $minutes . ' دقيقة و' . $remainder . ' ثانية';
};
$lockDuration = $durationLabel((int) ($authentication['login_lock_seconds'] ?? 0));
$onlineDuration = $durationLabel((int) ($presence['online_ttl_seconds'] ?? 0));
?>
<section class="module-stack settings-module settings-overview-page">
    <?php cc_page_summary(
        'اعدادات مركز التحكم',
        'ملخص حدود التشغيل',
        'صفحة قراءة فقط لفهم الحماية والاحتفاظ والفحص. يتم كل تعديل من الوحدة المالكة له',
        'settings',
        ['label' => 'قراءة فقط', 'tone' => 'info'],
        [
            ['label' => 'الاتصال الحالي', 'value' => 'خلال ' . $onlineDuration],
            ['label' => 'حفظ الجلسات', 'value' => (string) ((int) ($presence['session_retention_days'] ?? 0)) . ' يومًا'],
            ['label' => 'حفظ الاعطال', 'value' => (string) ((int) ($diagnostics['retention_days'] ?? 0)) . ' يومًا'],
        ]
    ); ?>

    <section class="panel policy-overview">
        <header class="panel__header"><div><span class="eyebrow">ما يهم في الاستخدام اليومي</span><h2>سياسات المركز</h2><p class="muted-copy">معلومات التشغيل والحماية</p></div></header>
        <div class="policy-list">
            <article class="policy-row">
                <span class="policy-row__icon"><?= cc_icon('shield') ?></span>
                <div><h3>تسجيل الدخول والحماية</h3><p>يُقفل تسجيل الدخول بعد <?= (int) ($authentication['login_max_attempts'] ?? 0) ?> محاولات لمدة <?= cc_e($lockDuration) ?>.</p></div>
                <?php cc_status_badge('محمي', 'success'); ?>
            </article>
            <article class="policy-row">
                <span class="policy-row__icon"><?= cc_icon('users') ?></span>
                <div><h3>نشاط المستخدمين والجلسات</h3><p>يُعد المستخدم نشطًا عند وصول نشاط خلال اخر <?= cc_e($onlineDuration) ?>، وتُحفظ الجلسات <?= (int) ($presence['session_retention_days'] ?? 0) ?> يومًا</p></div>
                <a class="button button--quiet" href="<?= cc_e(cc_url('sessions')) ?>">عرض الجلسات</a>
            </article>
            <article class="policy-row">
                <span class="policy-row__icon"><?= cc_icon('clock') ?></span>
                <div><h3>الاحتفاظ والتنظيف</h3><p>تُحفظ الاجهزة <?= (int) ($presence['device_retention_days'] ?? 0) ?> يومًا، واحداث الاعطال <?= (int) ($diagnostics['retention_days'] ?? 0) ?> يومًا</p></div>
                <?php cc_status_badge('تنظيف محدود', 'info'); ?>
            </article>
            <article class="policy-row">
                <span class="policy-row__icon"><?= cc_icon('heart') ?></span>
                <div><h3>صحة الهوستات</h3><p>يفحص التشغيل الواحد حتى <?= (int) ($health['max_hosts_per_run'] ?? 0) ?> هوست، وتُحفظ النتائج <?= (int) ($health['retention_days'] ?? 0) ?> يومًا</p></div>
                <a class="button button--quiet" href="<?= cc_e(cc_url('host-health')) ?>">عرض الصحة</a>
            </article>
            <article class="policy-row">
                <span class="policy-row__icon"><?= cc_icon('diagnostic') ?></span>
                <div><h3>اعطال التطبيق</h3><p>تُحفظ رموز اعطال مصنفة وامنة فقط، من دون رسائل خام او بيانات دخول</p></div>
                <a class="button button--quiet" href="<?= cc_e(cc_url('diagnostics')) ?>">عرض الاعطال</a>
            </article>
        </div>
    </section>

    <?php cc_technical_disclosure('القيم التشغيلية التقنية'); ?>
        <div class="settings-technical-grid">
            <section><h2>المركز وتسجيل الدخول</h2><?php cc_fact_list([
                ['label' => 'المسار', 'value' => (string) ($application['route_path'] ?? '—'), 'ltr' => true],
                ['label' => 'المنطقة الزمنية', 'value' => (string) ($application['timezone'] ?? '—'), 'ltr' => true],
                ['label' => 'مصدر الحساب', 'value' => (string) ($authentication['authority'] ?? '—'), 'ltr' => true],
                ['label' => 'سياسة الارتباط', 'value' => 'Secure · HttpOnly · SameSite=' . (string) ($authentication['same_site'] ?? '—'), 'ltr' => true],
            ]); ?></section>
            <section><h2>الجلسات والتنظيف</h2><?php cc_fact_list([
                ['label' => 'فاصل النشاط', 'value' => (string) ((int) ($presence['heartbeat_seconds'] ?? 0)) . ' ثانية'],
                ['label' => 'مهلة الاتصال', 'value' => (string) ((int) ($presence['online_ttl_seconds'] ?? 0)) . ' ثانية'],
                ['label' => 'دفعة تنظيف الجلسات', 'value' => (string) ((int) ($presence['cleanup_batch_size'] ?? 0)) . ' سجل'],
                ['label' => 'دفعة تنظيف الاعطال', 'value' => (string) ((int) ($diagnostics['cleanup_batch_size'] ?? 0)) . ' سجل'],
            ]); ?></section>
            <section><h2>فحص الهوستات</h2><?php cc_fact_list([
                ['label' => 'مهلة اسم النطاق', 'value' => (string) ((int) ($health['dns_timeout_ms'] ?? 0)) . ' مللي ثانية'],
                ['label' => 'مهلة الاتصال', 'value' => (string) ((int) ($health['connect_timeout_ms'] ?? 0)) . ' مللي ثانية'],
                ['label' => 'مهلة الطلب', 'value' => (string) ((int) ($health['request_timeout_ms'] ?? 0)) . ' مللي ثانية'],
                ['label' => 'حد التشغيل', 'value' => (string) ((int) ($health['max_runtime_seconds'] ?? 0)) . ' ثانية / ' . (string) ((int) ($health['candidate_limit'] ?? 0)) . ' مرشح'],
                ['label' => 'دفعة التنظيف', 'value' => (string) ((int) ($health['cleanup_batch_size'] ?? 0)) . ' سجل'],
            ]); ?></section>
        </div>
    <?php cc_disclosure_end(); ?>
</section>
