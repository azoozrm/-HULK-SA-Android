<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state(
        'تعذر تحميل المتصلين الان',
        'لم تُعرض اي ارقام بديلة. تحقق من اتصال قاعدة بيانات الاتصال ثم اعد المحاولة',
        cc_url('live-users')
    );
    return;
}

$groups = is_array($pageData['groups'] ?? null) ? $pageData['groups'] : [];
$resellers = is_array($pageData['resellers'] ?? null) ? $pageData['resellers'] : [];
$resellersAvailable = (bool) ($pageData['resellers_available'] ?? false);
$totalSubscriptions = (int) ($pageData['total_subscriptions'] ?? 0);
$totalConnections = (int) ($pageData['total_connections'] ?? 0);
$totalDevices = (int) ($pageData['total_devices'] ?? 0);
$tvConnections = (int) ($pageData['tv_connections'] ?? 0);
$phoneConnections = (int) ($pageData['phone_connections'] ?? 0);
$otherConnections = (int) ($pageData['other_connections'] ?? 0);
$multiDeviceSubscriptions = (int) ($pageData['multi_device_subscriptions'] ?? 0);
$ttlSeconds = (int) ($pageData['online_ttl_seconds'] ?? 0);
$serverNow = (string) ($pageData['server_now'] ?? '');
$page = (int) ($pageData['page'] ?? 1);
$pages = (int) ($pageData['pages'] ?? 1);
$notesAvailable = (bool) ($pageData['notes_available'] ?? true);
$targetToken = (string) ($pageData['target_token'] ?? '');
$targetMissing = (bool) ($pageData['target_missing'] ?? false);

$platformLabels = ['PHONE' => 'هاتف', 'TABLET' => 'جهاز لوحي', 'TV' => 'تلفاز', 'OTHER' => 'اخرى'];
$platformIcons = ['PHONE' => 'phone', 'TABLET' => 'tablet', 'TV' => 'tv', 'OTHER' => 'device'];
$paginationUrl = static fn (int $target): string => cc_url('live-users') . '?' . http_build_query(($pageData['filters'] ?? []) + ['page' => $target]);
?>
<section class="module-stack online-now-page live-users-page">
    <div class="kpis">
        <article class="kpi"><div class="kpi-label"><span class="kpi-icon"><?= cc_icon('users') ?></span>اشتراكات ظاهرة</div><strong><?= $totalSubscriptions ?></strong><small>هويات حساب وهوست دقيقة</small></article>
        <article class="kpi"><div class="kpi-label"><span class="kpi-icon"><?= cc_icon('screen') ?></span>أجهزة متصلة</div><strong><?= $totalDevices ?></strong><small>أجهزة مميزة الآن</small></article>
        <article class="kpi"><div class="kpi-label"><span class="kpi-icon"><?= cc_icon('pulse') ?></span>جلسات الآن</div><strong><?= $totalConnections ?></strong><small>قد تتعدد للجهاز الواحد</small></article>
        <article class="kpi"><div class="kpi-label"><span class="kpi-icon"><?= cc_icon('tv') ?></span>تلفاز / هاتف</div><strong><?= $tvConnections ?> / <?= $phoneConnections ?></strong><small>ضمن آخر <?= $ttlSeconds ?> ثانية</small></article>
    </div>
    <p class="note">الأرقام للنتائج المطابقة ضمن آخر <?= $ttlSeconds ?> ثانية · آخر قراءة <time dir="ltr"><?= cc_e(cc_presence_display_time($serverNow)) ?></time> · النشاط الحديث لا يثبت تشغيل فيديو</p>

    <?php if ($targetMissing): ?>
        <?php cc_alert('الاشتراك لم يعد ضمن المتصلين الآن', 'انتهت الجلسة أو تغيّر النشاط قبل فتح التفاصيل. افتح القائمة الحية لاختيار اشتراك آخر.', 'warning'); ?>
        <p><a class="button button--secondary" href="<?= cc_e(cc_url('live-users')) ?>">العودة إلى قائمة المتصلين</a></p>
    <?php endif; ?>

    <?php if ($otherConnections > 0): ?>
        <p class="muted-copy">يشمل الاجمالي <?= $otherConnections ?> اتصالًا من فئة اخرى غير التلفاز والهاتف او الجهاز اللوحي</p>
    <?php endif; ?>

    <?php if ($multiDeviceSubscriptions > 0): ?>
        <?php cc_alert(
            'استخدام متعدد الاجهزة',
            'يوجد ' . $multiDeviceSubscriptions . ' اشتراكًا ظاهرا على اكثر من جهاز في الوقت نفسه. راجع الاجهزة ادناه',
            'warning'
        ); ?>
    <?php endif; ?>

    <?php if (!$resellersAvailable && $groups !== []): ?>
        <?php cc_alert('بيانات جزئية', 'الاشتراكات متاحة، لكن تعذر تحميل اسماء الهوستات الحالية ، سجل الاشتراكات ما زال متاحا', 'warning'); ?>
    <?php endif; ?>

    <section class="records-section card" aria-labelledby="online-now-title">
        <header class="card-head">
            <div><h2 id="online-now-title">قائمة المتصلين</h2><p>افتح «التفاصيل» لبيانات الدخول والأجهزة والنشاط</p></div>
            <span class="note"><?= $totalSubscriptions ?> اشتراك</span>
        </header>

        <?php if ($groups === []): ?>
            <?php cc_empty_state('لا توجد جلسات حديثة الان', 'ستظهر الاشتراكات هنا عند وصول نشاط اتصال حديث داخل المهلة المعتمدة'); ?>
        <?php else: ?>
            <div class="subscription-list">
                <?php foreach ($groups as $group):
                    $resellerId = (int) ($group['reseller_id'] ?? 0);
                    $currentReseller = $resellers[$resellerId] ?? null;
                    $resellerName = is_array($currentReseller)
                        ? (string) ($currentReseller['reseller_name'] ?? ('#' . $resellerId))
                        : '#' . $resellerId;
                    $latest = is_array($group['latest_session'] ?? null) ? $group['latest_session'] : [];
                    $devices = is_array($group['devices'] ?? null) ? $group['devices'] : [];
                    $connectionCount = (int) ($group['connection_count'] ?? 0);
                    $deviceCount = (int) ($group['device_count'] ?? count($devices));
                    $devicesTruncated = (bool) ($group['devices_truncated'] ?? false);
                    $multiDevice = $deviceCount > 1;
                    $username = (string) ($latest['iptv_username'] ?? $group['iptv_username'] ?? '');
                    $host = (string) ($group['host_snapshot'] ?? '');
                    $accessCode = (string) ($latest['access_code_snapshot'] ?? '');
                    $password = (string) ($latest['iptv_password'] ?? '');
                    $lastActivity = (string) ($group['last_activity_at'] ?? '');
                    $hostHash = strtoupper(bin2hex($host));
                    $userHash = strtoupper(bin2hex($username));
                    $checkResult = is_array($group['check_result'] ?? null) ? $group['check_result'] : null;
                    $note = (string) ($group['note'] ?? '');
                    $noteDraft = (string) ($group['note_draft'] ?? '');
                    $noteUpdatedAt = (string) ($group['note_updated_at'] ?? '');
                ?>
                    <?php
                    $subscriptionToken = '';
                    try {
                        $subscriptionToken = cc_presence_subscription_token($resellerId, $host, $username);
                    } catch (Throwable $exception) {
                        $subscriptionToken = '';
                    }
                    $isTarget = $targetToken !== '' && $subscriptionToken !== '' && hash_equals($targetToken, $subscriptionToken);
                    ?>
                    <details class="subscription-card subscription-row-details" <?= $checkResult !== null || $noteDraft !== '' || $isTarget ? 'open' : '' ?><?= $isTarget ? ' id="subscription-target"' : '' ?>>
                        <summary class="subscription-row"><strong><bdi dir="ltr"><?=cc_e($username)?></bdi></strong><span><?=$deviceCount?> جهاز</span><span><bdi dir="ltr"><?=cc_e($host)?></bdi></span><time dir="ltr"><?=cc_e(cc_presence_display_time($lastActivity))?></time><span>التفاصيل</span></summary>
                        <div class="subscription-expanded">
                        <section class="subscription-access" aria-label="بيانات الدخول">
                            <h3>بيانات الدخول</h3>
                            <dl class="subscription-credentials">
                                <div><dt>اليوزر</dt><dd><bdi dir="ltr"><?= cc_e($username !== '' ? $username : 'غير متاح') ?></bdi></dd></div>
                                <div><dt>الباسورد</dt><dd><bdi dir="ltr"><?= cc_e($password !== '' ? $password : 'غير متاح') ?></bdi></dd></div>
                                <div><dt>كود الدخول</dt><dd><bdi dir="ltr"><?= cc_e($accessCode !== '' ? $accessCode : 'غير متاح') ?></bdi></dd></div>
                                <div class="subscription-credentials__wide"><dt>الهوست</dt><dd><bdi dir="ltr"><?= cc_e($host !== '' ? $host : 'غير متاح') ?></bdi></dd></div>
                            </dl>
                        </section>
                        <dl class="subscription-card__facts">
                            <div><dt>تلفاز</dt><dd><?= (int) ($group['tv_connections'] ?? 0) ?></dd></div>
                            <div><dt>هاتف ولوحي</dt><dd><?= (int) ($group['phone_connections'] ?? 0) ?></dd></div>
                            <div><dt>اخر نشاط</dt><dd><time dir="ltr"><?= cc_e(cc_presence_display_time($lastActivity)) ?></time></dd></div>
                        </dl>

                        <?php if ($multiDevice): ?>
                            <div class="subscription-warning">
                                <?= cc_icon('alert') ?>
                                <span>هذا الاشتراك متصل من <?= $deviceCount ?> اجهزة في الوقت نفسه. راجع الاجهزة قبل اتخاذ اي اجراء</span>
                            </div>
                        <?php endif; ?>

                        <div class="subscription-actions">
                            <form method="post" data-submit-once>
                                <input type="hidden" name="csrf_token" value="<?= cc_e(cc_csrf_token()) ?>">
                                <input type="hidden" name="action" value="check_subscriber">
                                <input type="hidden" name="reseller_id" value="<?= $resellerId ?>">
                                <input type="hidden" name="host_hash" value="<?= cc_e($hostHash) ?>">
                                <input type="hidden" name="user_hash" value="<?= cc_e($userHash) ?>">
                                <?php if ($subscriptionToken !== ''): ?><input type="hidden" name="subscription" value="<?= cc_e($subscriptionToken) ?>"><?php endif; ?>
                                <input type="hidden" name="page" value="<?= $page ?>">
                                <button class="button button--secondary" type="submit"><?= cc_icon('pulse') ?>فحص المشترك</button>
                            </form>
                        </div>

                        <?php if ($checkResult !== null):
                            $checkPresentation = cc_subscriber_check_presentation((string) ($checkResult['state'] ?? 'UNAVAILABLE'));
                            $providerStatus = (string) ($checkResult['provider_status'] ?? '');
                            $expiresAt = $checkResult['expires_at'] ?? null;
                            $checkedAt = (string) ($checkResult['checked_at'] ?? '');
                        ?>
                            <section class="subscriber-check-result subscriber-check-result--<?= cc_e($checkPresentation['tone']) ?>">
                                <header><span class="eyebrow">نتيجة فحص المشترك</span><?php cc_status_badge($checkPresentation['label'], $checkPresentation['tone']); ?></header>
                                <dl>
                                    <div><dt>حالة المزود</dt><dd><?= $providerStatus !== '' ? cc_e($providerStatus) : 'غير متاح' ?></dd></div>
                                    <div><dt>تاريخ الانتهاء</dt><dd dir="ltr"><?= cc_e(cc_subscriber_expiration_label(is_int($expiresAt) ? $expiresAt : null)) ?></dd></div>
                                    <div><dt>وقت الفحص</dt><dd><time dir="ltr"><?= cc_e(cc_presence_display_time($checkedAt)) ?></time></dd></div>
                                </dl>
                            </section>
                        <?php endif; ?>

                        <div class="subscription-devices">
                            <div class="subscription-devices__heading"><span>الاجهزة (<?= count($devices) ?>)</span><span><?= $connectionCount ?> جلسة حديثة</span></div>
                            <?php if ($devicesTruncated): ?>
                                <p class="subscription-devices__partial">يتم عرض اول <?= count($devices) ?> من <?= $deviceCount ?> اجهزة. العدد الاجمالي محفوظ في البطاقة</p>
                            <?php endif; ?>
                            <?php foreach ($devices as $device):
                                $platform = (string) ($device['platform_class'] ?? 'OTHER');
                                $deviceLabel = $platformLabels[$platform] ?? $platform;
                                $deviceIcon = $platformIcons[$platform] ?? 'device';
                                $model = trim((string) ($device['manufacturer'] ?? '') . ' ' . (string) ($device['model'] ?? ''));
                            ?>
                                <article class="subscription-device">
                                    <span class="subscription-device__icon"><?= cc_icon($deviceIcon) ?></span>
                                    <div class="subscription-device__copy">
                                        <strong><?= cc_e($deviceLabel . ($model !== '' ? ' · ' . $model : '')) ?></strong>
                                        <small><bdi dir="ltr"><?= cc_e((string) ($device['app_version_name'] ?? '')) ?> (<?= (int) ($device['app_version_code'] ?? 0) ?>)</bdi></small>
                                    </div>
                                    <time class="subscription-device__time" dir="ltr"><?= cc_e(cc_presence_display_time((string) ($device['last_seen_at'] ?? ''))) ?></time>
                                </article>
                            <?php endforeach; ?>
                        </div>

                        <section class="subscriber-note" aria-label="ملاحظة المشترك">
                            <div class="subscriber-note__head">
                                <span class="eyebrow">ملاحظة المشترك</span>
                                <?php if ($noteUpdatedAt !== ''): ?><small>اخر تحديث <?= cc_e(cc_presence_display_time($noteUpdatedAt)) ?></small><?php endif; ?>
                            </div>
                            <?php if ($note !== ''): ?>
                                <p class="subscriber-note__text"><?= cc_e($note) ?></p>
                            <?php else: ?>
                                <p class="muted-copy">لا توجد ملاحظة محفوظة</p>
                            <?php endif; ?>
                            <?php if (!$notesAvailable): ?><p class="muted-copy">تعذر تحميل الملاحظات المحفوظة حاليًا</p><?php endif; ?>
                            <details class="inline-editor">
                                <summary><?= $note !== '' ? 'تعديل الملاحظة' : 'اضافة ملاحظة' ?></summary>
                                <form method="post" data-submit-once>
                                    <input type="hidden" name="csrf_token" value="<?= cc_e(cc_csrf_token()) ?>">
                                    <input type="hidden" name="action" value="save_subscriber_note">
                                    <input type="hidden" name="reseller_id" value="<?= $resellerId ?>">
                                    <input type="hidden" name="host_hash" value="<?= cc_e($hostHash) ?>">
                                    <input type="hidden" name="user_hash" value="<?= cc_e($userHash) ?>">
                                    <?php if ($subscriptionToken !== ''): ?><input type="hidden" name="subscription" value="<?= cc_e($subscriptionToken) ?>"><?php endif; ?>
                                    <input type="hidden" name="page" value="<?= $page ?>">
                                    <label class="form-field"><span>نص الملاحظة</span><textarea name="note" maxlength="2000" required placeholder="ملاحظة خاصة لا تظهر للمشترك"><?= cc_e($noteDraft !== '' ? $noteDraft : $note) ?></textarea></label>
                                    <button class="button button--secondary" type="submit">حفظ الملاحظة</button>
                                </form>
                            </details>
                        </section>
                        </div>
                    </details>
                <?php endforeach; ?>
            </div>

            <nav class="pagination presence-pagination" aria-label="ترقيم صفحات المتصلين">
                <p>الصفحة <?= $page ?> من <?= $pages ?> · <?= $totalSubscriptions ?> اشتراكًا</p>
                <div>
                    <?php if ($page > 1): ?><a class="page-button" href="<?= cc_e($paginationUrl($page - 1)) ?>" aria-label="الصفحة السابقة"><?= cc_icon('chevron') ?></a><?php endif; ?>
                    <span class="page-button" aria-current="page"><?= $page ?></span>
                    <?php if ($page < $pages): ?><a class="page-button icon-button--flip" href="<?= cc_e($paginationUrl($page + 1)) ?>" aria-label="الصفحة التالية"><?= cc_icon('chevron') ?></a><?php endif; ?>
                </div>
            </nav>
        <?php endif; ?>
    </section>
</section>
