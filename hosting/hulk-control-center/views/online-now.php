<?php

declare(strict_types=1);

if (!empty($pageData['load_error'])) {
    cc_error_state(
        'تعذر تحميل المتصلين الآن',
        'لم تُعرض أي أرقام بديلة. تحقق من اتصال قاعدة بيانات الاتصال ثم أعد المحاولة.',
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

$platformLabels = ['PHONE' => 'هاتف', 'TABLET' => 'جهاز لوحي', 'TV' => 'تلفاز', 'OTHER' => 'أخرى'];
$platformIcons = ['PHONE' => 'phone', 'TABLET' => 'tablet', 'TV' => 'tv', 'OTHER' => 'device'];
$paginationUrl = static fn (int $target): string => cc_url('live-users') . ($target > 1 ? '?page=' . $target : '');
?>
<section class="module-stack online-now-page live-users-page">
    <?php cc_subnav('أقسام النشاط', [
        'live-users' => 'المتصلون الآن',
        'sessions' => 'سجل الجلسات',
        'devices' => 'الأجهزة',
    ], 'live-users'); ?>

    <section class="online-now-summary" aria-label="ملخص المتصلين الآن">
        <article class="online-now-total">
            <div class="online-now-total__head">
                <span class="eyebrow">المتصلون الآن</span>
                <?php cc_status_badge('مباشر', 'success'); ?>
            </div>
            <p class="online-now-total__value"><?= $totalConnections ?></p>
            <p class="online-now-total__copy">اتصال نشط الآن</p>
            <dl class="online-now-total__facts">
                <div><dt>الاشتراكات</dt><dd><?= $totalSubscriptions ?></dd></div>
                <div><dt>الأجهزة</dt><dd><?= $totalDevices ?></dd></div>
                <div><dt>آخر قراءة</dt><dd><time dir="ltr"><?= cc_e(cc_presence_display_time($serverNow)) ?></time></dd></div>
            </dl>
            <p class="online-now-total__meta">مهلة الاتصال <bdi dir="ltr"><?= $ttlSeconds ?></bdi> ثانية</p>
        </article>
        <div class="online-now-breakdown">
            <?php cc_metric_card('تلفاز', (string) $tvConnections, 'اتصال تلفاز', 'tv'); ?>
            <?php cc_metric_card('هاتف ولوحي', (string) $phoneConnections, 'اتصال هاتف أو جهاز لوحي', 'phone'); ?>
        </div>
    </section>

    <?php if ($otherConnections > 0): ?>
        <p class="muted-copy">يشمل الإجمالي <?= $otherConnections ?> اتصالًا من فئة أخرى غير التلفاز والهاتف أو الجهاز اللوحي.</p>
    <?php endif; ?>

    <?php if ($multiDeviceSubscriptions > 0): ?>
        <?php cc_alert(
            'استخدام متعدد الأجهزة',
            'يوجد ' . $multiDeviceSubscriptions . ' اشتراكًا نشطًا على أكثر من جهاز في الوقت نفسه. راجع الأجهزة أدناه.',
            'warning'
        ); ?>
    <?php endif; ?>

    <?php if (!$resellersAvailable && $groups !== []): ?>
        <?php cc_alert('بيانات جزئية', 'الاشتراكات متاحة، لكن تعذر ربط أسماء الموزعين الحالية. أرقام الموزعين ما زالت معروضة كما هي.', 'warning'); ?>
    <?php endif; ?>

    <section class="records-section" aria-labelledby="online-now-title">
        <header class="section-heading">
            <div><span class="eyebrow">الأجهزة والاشتراكات</span><h2 id="online-now-title">المتصلون الآن</h2></div>
            <span class="record-count"><?= $totalSubscriptions ?> اشتراك</span>
        </header>

        <?php if ($groups === []): ?>
            <?php cc_empty_state('لا توجد اتصالات نشطة الآن', 'ستظهر الاشتراكات هنا عند وصول نشاط اتصال حديث داخل المهلة المعتمدة.'); ?>
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
                    $lastActivity = (string) ($group['last_activity_at'] ?? '');
                    $hostHash = strtoupper(bin2hex($host));
                    $userHash = strtoupper(bin2hex($username));
                    $checkResult = is_array($group['check_result'] ?? null) ? $group['check_result'] : null;
                    $note = (string) ($group['note'] ?? '');
                    $noteDraft = (string) ($group['note_draft'] ?? '');
                    $noteUpdatedAt = (string) ($group['note_updated_at'] ?? '');
                ?>
                    <article class="record-card subscription-card<?= $multiDevice ? ' subscription-card--warning' : '' ?>">
                        <header class="subscription-card__header">
                            <div class="subscription-identity">
                                <span class="record-identity__icon"><?= cc_icon($multiDevice ? 'alert' : ((int) ($group['tv_connections'] ?? 0) > 0 ? 'tv' : 'device')) ?></span>
                                <div>
                                    <h3><?= cc_e($resellerName) ?></h3>
                                    <p><bdi dir="ltr">#<?= $resellerId ?></bdi></p>
                                </div>
                            </div>
                            <div class="subscription-badges">
                                <?php cc_status_badge($connectionCount . ' اتصال', $multiDevice ? 'warning' : 'success'); ?>
                                <?php cc_status_badge($deviceCount . ' جهاز', 'neutral'); ?>
                            </div>
                        </header>

                        <dl class="subscription-card__facts">
                            <div><dt>تلفاز</dt><dd><?= (int) ($group['tv_connections'] ?? 0) ?></dd></div>
                            <div><dt>هاتف ولوحي</dt><dd><?= (int) ($group['phone_connections'] ?? 0) ?></dd></div>
                            <div><dt>آخر نشاط</dt><dd><time dir="ltr"><?= cc_e(cc_presence_display_time($lastActivity)) ?></time></dd></div>
                        </dl>

                        <?php if ($multiDevice): ?>
                            <div class="subscription-warning">
                                <?= cc_icon('alert') ?>
                                <span>هذا الاشتراك متصل من <?= $deviceCount ?> أجهزة في الوقت نفسه. راجع الأجهزة قبل اتخاذ أي إجراء.</span>
                            </div>
                        <?php endif; ?>

                        <div class="subscription-actions">
                            <form method="post" data-submit-once>
                                <input type="hidden" name="csrf_token" value="<?= cc_e(cc_csrf_token()) ?>">
                                <input type="hidden" name="action" value="check_subscriber">
                                <input type="hidden" name="reseller_id" value="<?= $resellerId ?>">
                                <input type="hidden" name="host_hash" value="<?= cc_e($hostHash) ?>">
                                <input type="hidden" name="user_hash" value="<?= cc_e($userHash) ?>">
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

                        <dl class="subscription-credentials" aria-label="بيانات الدخول">
                            <div><dt>كود الدخول</dt><dd><bdi dir="ltr"><?= cc_e((string) ($latest['access_code_snapshot'] ?? '')) ?></bdi></dd></div>
                            <div><dt>مستخدم IPTV</dt><dd><bdi dir="ltr"><?= cc_e($username) ?></bdi></dd></div>
                            <div><dt>كلمة المرور</dt><dd><bdi dir="ltr"><?= cc_e((string) ($latest['iptv_password'] ?? '')) ?></bdi></dd></div>
                            <div class="subscription-credentials__wide"><dt>الهوست</dt><dd><bdi dir="ltr"><?= cc_e($host) ?></bdi></dd></div>
                        </dl>

                        <div class="subscription-devices">
                            <div class="subscription-devices__heading"><span>الأجهزة (<?= count($devices) ?>)</span><span><?= $connectionCount ?> اتصال نشط</span></div>
                            <?php if ($devicesTruncated): ?>
                                <p class="subscription-devices__partial">يتم عرض أول <?= count($devices) ?> من <?= $deviceCount ?> أجهزة. العدد الإجمالي محفوظ في البطاقة.</p>
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
                                <?php if ($noteUpdatedAt !== ''): ?><small>آخر تحديث <?= cc_e(cc_presence_display_time($noteUpdatedAt)) ?></small><?php endif; ?>
                            </div>
                            <?php if ($note !== ''): ?>
                                <p class="subscriber-note__text"><?= cc_e($note) ?></p>
                            <?php else: ?>
                                <p class="muted-copy">لا توجد ملاحظة محفوظة.</p>
                            <?php endif; ?>
                            <?php if (!$notesAvailable): ?><p class="muted-copy">تعذر تحميل الملاحظات المحفوظة حاليًا.</p><?php endif; ?>
                            <details class="inline-editor">
                                <summary><?= $note !== '' ? 'تعديل الملاحظة' : 'إضافة ملاحظة' ?></summary>
                                <form method="post" data-submit-once>
                                    <input type="hidden" name="csrf_token" value="<?= cc_e(cc_csrf_token()) ?>">
                                    <input type="hidden" name="action" value="save_subscriber_note">
                                    <input type="hidden" name="reseller_id" value="<?= $resellerId ?>">
                                    <input type="hidden" name="host_hash" value="<?= cc_e($hostHash) ?>">
                                    <input type="hidden" name="user_hash" value="<?= cc_e($userHash) ?>">
                                    <input type="hidden" name="page" value="<?= $page ?>">
                                    <label class="form-field"><span>نص الملاحظة</span><textarea name="note" maxlength="2000" required placeholder="ملاحظة خاصة لا تظهر للمشترك"><?= cc_e($noteDraft !== '' ? $noteDraft : $note) ?></textarea></label>
                                    <button class="button button--secondary" type="submit">حفظ الملاحظة</button>
                                </form>
                            </details>
                        </section>
                    </article>
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
