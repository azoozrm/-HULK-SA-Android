<?php

declare(strict_types=1);

$module = $resolved['module'];
?>
<section class="panel module-panel">
    <header class="panel__header">
        <div><span class="eyebrow">جاهز للربط</span><h2><?= cc_e((string) $module['title']) ?></h2></div>
        <?php cc_status_badge('غير متصل', 'neutral'); ?>
    </header>
    <?php cc_unavailable_state(
        'غير متاح بعد',
        'هذه الوحدة مسجلة في الهيكل الموحّد، لكن مصدرها الموثوق ليس ضمن نطاق المرحلة الثانية. الأنظمة الحالية مستمرة بدون تغيير.',
        (string) $module['icon']
    ); ?>
    <p class="assistive-note" id="phase-one-disabled">البحث والتصفية والترقيم تصبح فعالة عند ربط مصدر البيانات الموثوق.</p>
    <?php cc_toolbar('ابحث في ' . (string) $module['label'], ['كل الحالات']); ?>
    <?php cc_table_shell(['العنصر', 'الارتباط', 'الحالة', 'آخر تحديث']); ?>
</section>
