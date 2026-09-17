<?php

declare(strict_types=1);
?>
<section class="dashboard-intro">
    <div class="dashboard-intro__copy">
        <span class="eyebrow">مركز واحد. رؤية أوضح.</span>
        <h2>الأساس الجديد لإدارة HULK SA</h2>
        <p>تم توحيد إدارة Operations والموزعين مع إبقاء كل قاعدة بيانات مالكة لبياناتها. مؤشرات لوحة المتابعة نفسها مؤجلة للمرحلة الثالثة.</p>
    </div>
    <div class="dashboard-intro__seal" aria-hidden="true">
        <img src="<?= cc_e(cc_asset_url('hulk-sa-mark.svg')) ?>" alt="">
    </div>
</section>

<div class="section-heading">
    <div><span class="eyebrow">المؤشرات الرئيسية</span><h2>ملخص فوري</h2></div>
    <?php cc_status_badge('لا توجد بيانات تجريبية', 'success'); ?>
</div>

<section class="kpi-grid" aria-label="المؤشرات الرئيسية غير المتصلة">
    <?php cc_kpi_card('المستخدمون الآن', 'غير متاح بعد', 'يتطلب عقد Presence في المراحل اللاحقة.', 'users'); ?>
    <?php cc_kpi_card('الجلسات اليوم', 'غير متاح بعد', 'سيظهر بعد تفعيل سجل الجلسات.', 'clock'); ?>
    <?php cc_kpi_card('الأجهزة النشطة', 'غير متاح بعد', 'لا يوجد مصدر خادم موثوق حاليًا.', 'device'); ?>
    <?php cc_kpi_card('الموزعون النشطون', 'غير متاح بعد', 'سيظهر كمؤشر موثوق ضمن Dashboard V1 في Phase 3.', 'briefcase'); ?>
    <?php cc_kpi_card('صحة الهوستات', 'غير متاح بعد', 'الفحص التشغيلي ضمن Phase 7.', 'heart'); ?>
    <?php cc_kpi_card('اعتماد الإصدارات', 'غير متاح بعد', 'لن يُستنتج من تنزيلات APK.', 'chart'); ?>
</section>

<section class="dashboard-grid">
    <article class="panel panel--wide">
        <header class="panel__header">
            <div><span class="eyebrow">المستخدمون الآن</span><h2>معاينة الاتصال المباشر</h2></div>
            <?php cc_status_badge('غير متصل', 'neutral'); ?>
        </header>
        <?php cc_unavailable_state('غير متاح بعد', 'سيظهر المستخدمون الفعليون هنا بعد اكتمال Presence وربط Android. لا تُعرض أرقام تقديرية أو بيانات وهمية.', 'users'); ?>
    </article>

    <article class="panel">
        <header class="panel__header"><div><span class="eyebrow">الجاهزية</span><h2>مصادر البيانات</h2></div></header>
        <div class="source-list">
            <div class="source-row"><span class="source-row__icon"><?= cc_icon('pulse') ?></span><span><strong>Operations</strong><small>سلطة التحكم والتشغيل</small></span><?php cc_status_badge('متصل', 'success'); ?></div>
            <div class="source-row"><span class="source-row__icon"><?= cc_icon('briefcase') ?></span><span><strong>Reseller</strong><small>سلطة الموزعين والوصول</small></span><?php cc_status_badge('متصل', 'success'); ?></div>
            <div class="source-row"><span class="source-row__icon"><?= cc_icon('users') ?></span><span><strong>Presence</strong><small>الجلسات والحضور المباشر</small></span><?php cc_status_badge('Phase 4', 'neutral'); ?></div>
        </div>
    </article>
</section>

<section class="panel">
    <header class="panel__header">
        <div><span class="eyebrow">النشاط الإداري</span><h2>آخر العمليات</h2></div>
        <?php cc_status_badge('غير متصل', 'neutral'); ?>
    </header>
    <?php cc_table_shell(['العملية', 'المسؤول', 'الحالة', 'التاريخ']); ?>
</section>
