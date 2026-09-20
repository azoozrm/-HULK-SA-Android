<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state('تعذر تحميل التحليلات', 'لم تُستبدل البيانات غير المتاحة بأصفار.', cc_url('analytics'));
    return;
}

$sessions = is_array($pageData['session_trend'] ?? null) ? $pageData['session_trend'] : [];
$versions = is_array($pageData['version_adoption_trend'] ?? null) ? $pageData['version_adoption_trend'] : [];
$current = is_array($pageData['current_adoption_distribution'] ?? null) ? $pageData['current_adoption_distribution'] : [];
$versionPartial = ($pageData['version_adoption_partial'] ?? false) === true;
$currentPartial = ($pageData['current_adoption_partial'] ?? false) === true;
$maximumSessions = max(1, ...array_map(static fn (array $row): int => (int) $row['session_count'], $sessions));
$maximumCurrent = max(1, ...array_map(static fn (array $row): int => (int) $row['device_count'], $current));
$sessionTotal = array_sum(array_map(static fn (array $row): int => (int) $row['session_count'], $sessions));
$latestSessionDay = $sessions === [] ? 'غير متاح' : (string) ($sessions[array_key_last($sessions)]['day'] ?? 'غير متاح');
?>
<section class="module-stack analytics-workspace">
    <?php cc_page_summary(
        'قراءة الاستخدام',
        'التحليلات',
        'تابع حركة الجلسات وانتشار الإصدارات ضمن الفترة المتاحة من دون استنتاج مؤشرات غير مدعومة.',
        'chart',
        ['label' => ($versionPartial || $currentPartial) ? 'بعض القراءات جزئية' : 'البيانات مكتملة ضمن الحدود', 'tone' => ($versionPartial || $currentPartial) ? 'warning' : 'success'],
        [
            ['label' => 'الفترة', 'value' => (string) ((int) ($pageData['window_days'] ?? 30)) . ' يومًا'],
            ['label' => 'الجلسات في الاتجاه', 'value' => (string) $sessionTotal],
            ['label' => 'آخر يوم ظاهر', 'value' => $latestSessionDay, 'ltr' => true],
        ]
    ); ?>

    <?php if ($versionPartial || $currentPartial): ?>
        <?php cc_alert('قراءة اعتماد الإصدارات جزئية', 'وصلت البيانات إلى حد العرض؛ لا تُحوّل الصفوف غير الظاهرة إلى أصفار.', 'warning'); ?>
    <?php endif; ?>

    <section class="analytics-primary-grid">
        <article class="panel analytics-chart analytics-chart--sessions">
            <header class="panel__header"><div><span class="eyebrow">حركة الاستخدام</span><h2>الجلسات خلال الفترة</h2><p class="muted-copy">عدد الجلسات التي بدأت في كل يوم.</p></div></header>
            <?php if ($sessions === []): ?>
                <?php cc_empty_state('لا توجد جلسات في الفترة', 'لم تُسجل جلسات يمكن عرضها في الاتجاه الحالي.'); ?>
            <?php else: ?>
                <div class="bar-chart bar-chart--timeline" role="img" aria-label="اتجاه الجلسات حسب اليوم">
                    <?php foreach ($sessions as $row): ?>
                        <div class="bar-chart__row"><time dir="ltr"><?= cc_e((string) $row['day']) ?></time><progress class="bar-chart__progress" max="<?= $maximumSessions ?>" value="<?= (int) $row['session_count'] ?>"><?= (int) $row['session_count'] ?></progress><strong><?= (int) $row['session_count'] ?></strong></div>
                    <?php endforeach; ?>
                </div>
            <?php endif; ?>
        </article>

        <article class="panel analytics-chart analytics-chart--versions">
            <header class="panel__header"><div><span class="eyebrow">الأجهزة النشطة خلال 24 ساعة<?= $currentPartial ? ' · قراءة جزئية' : '' ?></span><h2>الإصدارات المستخدمة الآن</h2><p class="muted-copy">أحدث إصدار ظهر على كل جهاز نشط.</p></div></header>
            <?php if ($current === []): ?>
                <?php cc_empty_state('لا توجد أجهزة حديثة', 'لا توجد أجهزة ذات نشاط حديث في نافذة العرض الحالية.'); ?>
            <?php else: ?>
                <div class="version-distribution">
                    <?php foreach ($current as $row): ?>
                        <div class="version-distribution__row">
                            <div><strong><?= cc_e((string) $row['app_version_name']) ?></strong><small>رمز الإصدار <bdi dir="ltr"><?= (int) $row['app_version_code'] ?></bdi></small></div>
                            <progress max="<?= $maximumCurrent ?>" value="<?= (int) $row['device_count'] ?>"><?= (int) $row['device_count'] ?></progress>
                            <span><?= (int) $row['device_count'] ?> جهاز</span>
                        </div>
                    <?php endforeach; ?>
                </div>
            <?php endif; ?>
        </article>
    </section>

    <section class="panel adoption-trend">
        <header class="panel__header"><div><span class="eyebrow">الانتقال بين الإصدارات<?= $versionPartial ? ' · قراءة جزئية' : '' ?></span><h2>اعتماد الإصدارات عبر الأيام</h2><p class="muted-copy">عرض مضغوط يسهل مقارنة اليوم والإصدار وعدد الأجهزة.</p></div></header>
        <?php if ($versions === []): ?>
            <?php cc_empty_state('لا توجد بيانات اعتماد', 'لا توجد جلسات ضمن فترة التحليل.'); ?>
        <?php else: ?>
            <div class="compact-records" role="list" aria-label="اتجاه اعتماد الإصدارات">
                <?php foreach ($versions as $row): ?>
                    <article class="compact-record" role="listitem">
                        <div class="compact-record__identity"><strong><?= cc_e((string) $row['app_version_name']) ?></strong><small><bdi dir="ltr"><?= cc_e((string) $row['day']) ?></bdi> · رمز <bdi dir="ltr"><?= (int) $row['app_version_code'] ?></bdi></small></div>
                        <div class="compact-record__metric"><span>الأجهزة</span><strong><?= (int) $row['device_count'] ?></strong></div>
                    </article>
                <?php endforeach; ?>
            </div>
        <?php endif; ?>
    </section>

    <details class="technical-disclosure methodology-disclosure">
        <summary>منهجية احتساب المؤشرات</summary>
        <div class="technical-disclosure__body">
            <p>يعتمد اتجاه الجلسات على وقت بدء الجلسة. ويأخذ اتجاه اعتماد الإصدارات آخر جلسة لكل جهاز في اليوم. أما التوزيع الحالي فيستخدم أحدث نشاط للجهاز خلال 24 ساعة، ولا يعتمد على عدد تنزيلات ملف التطبيق.</p>
        </div>
    </details>
</section>
