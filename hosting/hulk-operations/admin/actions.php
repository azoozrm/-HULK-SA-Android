<?php

declare(strict_types=1);

if (!defined('HULK_OPERATIONS_ADMIN')) {
    http_response_code(404);
    exit;
}

function ops_post_bool(string $key): bool
{
    return isset($_POST[$key]) && (string) $_POST[$key] === '1';
}

function ops_post_positive_int(string $key): int
{
    $value = filter_var($_POST[$key] ?? null, FILTER_VALIDATE_INT);
    if ($value === false || $value < 1 || $value > 2147483647) {
        throw new InvalidArgumentException('قيمة رقمية غير صالحة.');
    }
    return (int) $value;
}

function ops_post_nullable_positive_int(string $key): ?int
{
    $raw = trim((string) ($_POST[$key] ?? ''));
    if ($raw === '') {
        return null;
    }
    $value = filter_var($raw, FILTER_VALIDATE_INT);
    if ($value === false || $value < 1 || $value > 2147483647) {
        throw new InvalidArgumentException('نطاق الإصدارات غير صالح.');
    }
    return (int) $value;
}

function ops_store_active_release_settings(PDO $db, array $release): void
{
    ops_set_setting($db, 'active_release_id', (string) $release['id']);
    ops_set_setting($db, 'latest_version_code', (string) $release['version_code']);
    ops_set_setting($db, 'latest_version_name', (string) $release['version_name']);
    ops_set_setting(
        $db,
        'minimum_supported_version_code',
        (string) $release['minimum_supported_version_code']
    );
    ops_set_setting($db, 'release_required', (bool) $release['required'] ? '1' : '0');
}

function ops_reset_release_settings(PDO $db): void
{
    ops_set_setting($db, 'active_release_id', '');
    ops_set_setting($db, 'latest_version_code', '64');
    ops_set_setting($db, 'latest_version_name', '0.9.3.20');
    ops_set_setting($db, 'minimum_supported_version_code', '64');
    ops_set_setting($db, 'release_required', '0');
}

function ops_uploaded_apk_metadata(array $file): array
{
    if ((int) ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
        throw new InvalidArgumentException('لم يكتمل رفع ملف APK.');
    }
    $temporaryPath = (string) ($file['tmp_name'] ?? '');
    if ($temporaryPath === '' || !is_uploaded_file($temporaryPath)) {
        throw new InvalidArgumentException('ملف الرفع غير موثوق.');
    }

    $sizeBytes = (int) ($file['size'] ?? 0);
    $maximumBytes = max(1, (int) (ops_load_config()['app']['max_apk_bytes'] ?? 629145600));
    $mime = '';
    if (function_exists('finfo_open')) {
        $finfo = finfo_open(FILEINFO_MIME_TYPE);
        if ($finfo !== false) {
            $detected = finfo_file($finfo, $temporaryPath);
            $mime = is_string($detected) ? $detected : '';
            finfo_close($finfo);
        }
    }
    $header = (string) file_get_contents($temporaryPath, false, null, 0, 4);
    $errors = ops_apk_descriptor_errors(
        (string) ($file['name'] ?? ''),
        $mime,
        $sizeBytes,
        $maximumBytes,
        $header
    );
    if ($errors !== []) {
        throw new InvalidArgumentException(implode(' ', $errors));
    }

    if (class_exists('ZipArchive')) {
        $archive = new ZipArchive();
        $opened = $archive->open($temporaryPath);
        $hasManifest = $opened === true && $archive->locateName('AndroidManifest.xml') !== false;
        $hasDex = $opened === true && $archive->locateName('classes.dex') !== false;
        if ($opened === true) {
            $archive->close();
        }
        if (!$hasManifest || !$hasDex) {
            throw new InvalidArgumentException('ملف APK لا يحتوي على بنية تطبيق Android صالحة.');
        }
    }

    $sha256 = strtolower((string) hash_file('sha256', $temporaryPath));
    if (!ops_is_sha256($sha256)) {
        throw new RuntimeException('تعذر حساب SHA-256.');
    }

    return [
        'temporary_path' => $temporaryPath,
        'size_bytes' => $sizeBytes,
        'sha256' => $sha256,
    ];
}

function ops_upload_release(PDO $db, array $admin): void
{
    $versionName = trim((string) ($_POST['version_name'] ?? ''));
    $versionCode = ops_post_positive_int('version_code');
    $required = ops_post_bool('required');
    $minimumVersionCode = $required
        ? $versionCode
        : ops_post_positive_int('minimum_supported_version_code');
    $releaseNotes = trim((string) ($_POST['release_notes'] ?? ''));
    $errors = ops_release_metadata_errors(
        $versionName,
        $versionCode,
        $minimumVersionCode,
        $required,
        $releaseNotes
    );
    if ($errors !== []) {
        throw new InvalidArgumentException(implode(' ', $errors));
    }

    $duplicate = $db->prepare('SELECT id FROM app_releases WHERE version_code = :version_code LIMIT 1');
    $duplicate->execute(['version_code' => $versionCode]);
    if ($duplicate->fetchColumn() !== false) {
        throw new InvalidArgumentException('رمز الإصدار مستخدم مسبقًا.');
    }

    $metadata = ops_uploaded_apk_metadata($_FILES['apk'] ?? []);
    $safeVersion = preg_replace('/[^0-9A-Za-z._-]/', '-', $versionName) ?: 'release';
    $fileName = sprintf(
        'hulk-sa-v%s-%d-%s.apk',
        $safeVersion,
        $versionCode,
        bin2hex(random_bytes(4))
    );
    $relativePath = 'releases/' . $fileName;
    $destination = dirname(__DIR__) . '/releases/' . $fileName;
    if (!move_uploaded_file((string) $metadata['temporary_path'], $destination)) {
        throw new RuntimeException('تعذر حفظ ملف APK في مجلد الإصدارات.');
    }
    chmod($destination, 0644);

    try {
        $db->beginTransaction();
        $statement = $db->prepare(
            'INSERT INTO app_releases '
            . '(version_name, version_code, minimum_supported_version_code, apk_path, apk_sha256, '
            . 'apk_size_bytes, release_notes, required, enabled, is_active) '
            . 'VALUES (:version_name, :version_code, :minimum_supported_version_code, :apk_path, '
            . ':apk_sha256, :apk_size_bytes, :release_notes, :required, 0, 0)'
        );
        $statement->execute([
            'version_name' => $versionName,
            'version_code' => $versionCode,
            'minimum_supported_version_code' => $minimumVersionCode,
            'apk_path' => $relativePath,
            'apk_sha256' => $metadata['sha256'],
            'apk_size_bytes' => $metadata['size_bytes'],
            'release_notes' => $releaseNotes,
            'required' => $required ? 1 : 0,
        ]);
        ops_audit($db, (int) $admin['id'], 'APK_UPLOADED', [
            'version_name' => $versionName,
            'version_code' => $versionCode,
            'sha256' => $metadata['sha256'],
            'size_bytes' => $metadata['size_bytes'],
        ]);
        $db->commit();
    } catch (Throwable $exception) {
        if ($db->inTransaction()) {
            $db->rollBack();
        }
        @unlink($destination);
        throw $exception;
    }
}

function ops_activate_release(PDO $db, array $admin): void
{
    $releaseId = ops_post_positive_int('release_id');
    $db->beginTransaction();
    try {
        $statement = $db->prepare('SELECT * FROM app_releases WHERE id = :id FOR UPDATE');
        $statement->execute(['id' => $releaseId]);
        $release = $statement->fetch();
        if (!is_array($release)) {
            throw new InvalidArgumentException('الإصدار غير موجود.');
        }
        $db->exec('UPDATE app_releases SET is_active = 0');
        $activate = $db->prepare('UPDATE app_releases SET enabled = 1, is_active = 1 WHERE id = :id');
        $activate->execute(['id' => $releaseId]);
        $release['enabled'] = 1;
        $release['is_active'] = 1;
        ops_store_active_release_settings($db, $release);
        ops_audit($db, (int) $admin['id'], 'RELEASE_ACTIVATED', [
            'release_id' => $releaseId,
            'version_code' => $release['version_code'],
            'required' => (bool) $release['required'],
        ]);
        $db->commit();
    } catch (Throwable $exception) {
        if ($db->inTransaction()) {
            $db->rollBack();
        }
        throw $exception;
    }
}

function ops_disable_release(PDO $db, array $admin): void
{
    $releaseId = ops_post_positive_int('release_id');
    $db->beginTransaction();
    try {
        $statement = $db->prepare('SELECT * FROM app_releases WHERE id = :id FOR UPDATE');
        $statement->execute(['id' => $releaseId]);
        $release = $statement->fetch();
        if (!is_array($release)) {
            throw new InvalidArgumentException('الإصدار غير موجود.');
        }
        $disable = $db->prepare('UPDATE app_releases SET enabled = 0, is_active = 0 WHERE id = :id');
        $disable->execute(['id' => $releaseId]);
        if ((bool) $release['is_active']) {
            ops_reset_release_settings($db);
        }
        ops_audit($db, (int) $admin['id'], 'RELEASE_DISABLED', [
            'release_id' => $releaseId,
            'version_code' => $release['version_code'],
        ]);
        $db->commit();
    } catch (Throwable $exception) {
        if ($db->inTransaction()) {
            $db->rollBack();
        }
        throw $exception;
    }
}

function ops_update_release_policy(PDO $db, array $admin): void
{
    $releaseId = ops_post_positive_int('release_id');
    $required = ops_post_bool('required');
    $minimumVersionCode = ops_post_positive_int('minimum_supported_version_code');
    $db->beginTransaction();
    try {
        $statement = $db->prepare('SELECT * FROM app_releases WHERE id = :id FOR UPDATE');
        $statement->execute(['id' => $releaseId]);
        $release = $statement->fetch();
        if (!is_array($release)) {
            throw new InvalidArgumentException('الإصدار غير موجود.');
        }
        $versionCode = (int) $release['version_code'];
        if ($required) {
            $minimumVersionCode = $versionCode;
        }
        $errors = ops_release_metadata_errors(
            (string) $release['version_name'],
            $versionCode,
            $minimumVersionCode,
            $required,
            (string) $release['release_notes']
        );
        if ($errors !== []) {
            throw new InvalidArgumentException(implode(' ', $errors));
        }
        $update = $db->prepare(
            'UPDATE app_releases SET minimum_supported_version_code = :minimum, required = :required '
            . 'WHERE id = :id'
        );
        $update->execute([
            'minimum' => $minimumVersionCode,
            'required' => $required ? 1 : 0,
            'id' => $releaseId,
        ]);
        $release['minimum_supported_version_code'] = $minimumVersionCode;
        $release['required'] = $required ? 1 : 0;
        if ((bool) $release['is_active']) {
            ops_store_active_release_settings($db, $release);
        }
        ops_audit($db, (int) $admin['id'], 'MINIMUM_VERSION_CHANGED', [
            'release_id' => $releaseId,
            'minimum_version_code' => $minimumVersionCode,
            'required' => $required,
        ]);
        $db->commit();
    } catch (Throwable $exception) {
        if ($db->inTransaction()) {
            $db->rollBack();
        }
        throw $exception;
    }
}

function ops_create_announcement(PDO $db, array $admin): void
{
    $messageKeyInput = trim((string) ($_POST['message_key'] ?? ''));
    $messageKey = $messageKeyInput === ''
        ? 'MSG-' . date('Ymd-His') . '-' . strtoupper(bin2hex(random_bytes(3)))
        : ops_safe_message_key($messageKeyInput);
    if ($messageKey === null) {
        throw new InvalidArgumentException('معرف الرسالة غير صالح.');
    }
    $title = trim((string) ($_POST['title'] ?? ''));
    $message = trim((string) ($_POST['message'] ?? ''));
    if ($title === '' || ops_text_length($title) > 160 || $message === '' || ops_text_length($message) > 10000) {
        throw new InvalidArgumentException('عنوان الرسالة أو محتواها غير صالح.');
    }
    $severity = strtoupper(trim((string) ($_POST['severity'] ?? 'INFO')));
    $target = strtoupper(trim((string) ($_POST['target'] ?? 'ALL')));
    if (!in_array($severity, ['INFO', 'WARNING', 'IMPORTANT'], true)) {
        throw new InvalidArgumentException('درجة الرسالة غير صالحة.');
    }
    if (!in_array($target, ['ALL', 'MOBILE', 'TV'], true)) {
        throw new InvalidArgumentException('هدف الرسالة غير صالح.');
    }
    $startsInput = trim((string) ($_POST['starts_at'] ?? ''));
    $startsAt = ops_parse_local_datetime($startsInput);
    if ($startsAt === null) {
        throw new InvalidArgumentException('تاريخ بدء الرسالة غير صالح.');
    }
    $endsInput = trim((string) ($_POST['ends_at'] ?? ''));
    $endsAt = ops_parse_local_datetime($endsInput);
    if ($endsInput !== '' && $endsAt === null) {
        throw new InvalidArgumentException('تاريخ انتهاء الرسالة غير صالح.');
    }
    if ($endsAt !== null && strtotime($endsAt) <= strtotime($startsAt)) {
        throw new InvalidArgumentException('انتهاء الرسالة يجب أن يكون بعد بدايتها.');
    }
    $minimum = ops_post_nullable_positive_int('minimum_version_code');
    $maximum = ops_post_nullable_positive_int('maximum_version_code');
    if ($minimum !== null && $maximum !== null && $minimum > $maximum) {
        throw new InvalidArgumentException('نطاق الإصدارات غير صالح.');
    }

    $statement = $db->prepare(
        'INSERT INTO app_announcements '
        . '(message_key, title, message, severity, target, show_once, persistent, '
        . 'minimum_version_code, maximum_version_code, starts_at, ends_at, enabled) '
        . 'VALUES (:message_key, :title, :message, :severity, :target, :show_once, :persistent, '
        . ':minimum_version_code, :maximum_version_code, :starts_at, :ends_at, :enabled)'
    );
    $statement->execute([
        'message_key' => $messageKey,
        'title' => $title,
        'message' => $message,
        'severity' => $severity,
        'target' => $target,
        'show_once' => ops_post_bool('show_once') ? 1 : 0,
        'persistent' => ops_post_bool('persistent') ? 1 : 0,
        'minimum_version_code' => $minimum,
        'maximum_version_code' => $maximum,
        'starts_at' => $startsAt,
        'ends_at' => $endsAt,
        'enabled' => ops_post_bool('enabled') ? 1 : 0,
    ]);
    ops_audit($db, (int) $admin['id'], 'ANNOUNCEMENT_CREATED', [
        'message_key' => $messageKey,
        'severity' => $severity,
        'target' => $target,
        'enabled' => ops_post_bool('enabled'),
    ]);
}

function ops_disable_announcement(PDO $db, array $admin): void
{
    $announcementId = ops_post_positive_int('announcement_id');
    $statement = $db->prepare('SELECT message_key FROM app_announcements WHERE id = :id LIMIT 1');
    $statement->execute(['id' => $announcementId]);
    $messageKey = $statement->fetchColumn();
    if ($messageKey === false) {
        throw new InvalidArgumentException('الرسالة غير موجودة.');
    }
    $update = $db->prepare('UPDATE app_announcements SET enabled = 0 WHERE id = :id');
    $update->execute(['id' => $announcementId]);
    ops_audit($db, (int) $admin['id'], 'ANNOUNCEMENT_DISABLED', [
        'message_key' => (string) $messageKey,
    ]);
}

function ops_update_service_status(PDO $db, array $admin): void
{
    $status = strtoupper(trim((string) ($_POST['status'] ?? 'OPERATIONAL')));
    if (!in_array($status, ['OPERATIONAL', 'DEGRADED', 'MAINTENANCE'], true)) {
        throw new InvalidArgumentException('حالة الخدمة غير صالحة.');
    }
    $message = trim((string) ($_POST['message'] ?? ''));
    if (ops_text_length($message) > 2000) {
        throw new InvalidArgumentException('رسالة حالة الخدمة طويلة جدًا.');
    }
    $startsInput = trim((string) ($_POST['starts_at'] ?? ''));
    $endInput = trim((string) ($_POST['estimated_end_at'] ?? ''));
    $startsAt = $status === 'OPERATIONAL'
        ? null
        : ($startsInput === '' ? date('Y-m-d H:i:s') : ops_parse_local_datetime($startsInput));
    $estimatedEndAt = $status === 'OPERATIONAL' ? null : ops_parse_local_datetime($endInput);
    if ($status !== 'OPERATIONAL' && $startsAt === null) {
        throw new InvalidArgumentException('وقت بدء الحالة غير صالح.');
    }
    if ($status !== 'OPERATIONAL' && $endInput !== '' && $estimatedEndAt === null) {
        throw new InvalidArgumentException('وقت الانتهاء المتوقع غير صالح.');
    }
    if ($estimatedEndAt !== null && strtotime($estimatedEndAt) <= strtotime((string) $startsAt)) {
        throw new InvalidArgumentException('وقت الانتهاء المتوقع يجب أن يكون بعد البداية.');
    }

    $statement = $db->prepare(
        'INSERT INTO app_service_status (id, status, message, starts_at, estimated_end_at) '
        . 'VALUES (1, :status, :message, :starts_at, :estimated_end_at) '
        . 'ON DUPLICATE KEY UPDATE status = VALUES(status), message = VALUES(message), '
        . 'starts_at = VALUES(starts_at), estimated_end_at = VALUES(estimated_end_at)'
    );
    $statement->execute([
        'status' => $status,
        'message' => $message === '' ? null : $message,
        'starts_at' => $startsAt,
        'estimated_end_at' => $estimatedEndAt,
    ]);
    ops_audit(
        $db,
        (int) $admin['id'],
        $status === 'MAINTENANCE' ? 'MAINTENANCE_ENABLED' : 'MAINTENANCE_DISABLED',
        ['status' => $status, 'estimated_end_at' => $estimatedEndAt]
    );
}

function ops_toggle_feature_flag(PDO $db, array $admin): void
{
    $flagKey = trim((string) ($_POST['flag_key'] ?? ''));
    if (!in_array($flagKey, ops_known_feature_flags(), true)) {
        throw new InvalidArgumentException('اسم الميزة غير معروف.');
    }
    $enabled = ops_post_bool('enabled');
    $statement = $db->prepare(
        'INSERT INTO app_feature_flags (flag_key, enabled) VALUES (:flag_key, :enabled) '
        . 'ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)'
    );
    $statement->execute(['flag_key' => $flagKey, 'enabled' => $enabled ? 1 : 0]);
    ops_audit($db, (int) $admin['id'], 'FEATURE_FLAG_CHANGED', [
        'flag_key' => $flagKey,
        'enabled' => $enabled,
    ]);
}

function ops_admin_handle_post(PDO $db, array $admin, string $section): never
{
    ops_require_csrf();
    $action = trim((string) ($_POST['action'] ?? ''));
    try {
        switch ($action) {
            case 'upload_release':
                ops_upload_release($db, $admin);
                $section = 'releases';
                $message = 'تم رفع APK وحساب SHA-256. الإصدار غير نشط حتى يتم تفعيله.';
                break;
            case 'activate_release':
                ops_activate_release($db, $admin);
                $section = 'releases';
                $message = 'تم تفعيل الإصدار.';
                break;
            case 'disable_release':
                ops_disable_release($db, $admin);
                $section = 'releases';
                $message = 'تم تعطيل الإصدار.';
                break;
            case 'update_release_policy':
                ops_update_release_policy($db, $admin);
                $section = 'releases';
                $message = 'تم تحديث سياسة الإصدار.';
                break;
            case 'create_announcement':
                ops_create_announcement($db, $admin);
                $section = 'announcements';
                $message = 'تم إنشاء الرسالة.';
                break;
            case 'disable_announcement':
                ops_disable_announcement($db, $admin);
                $section = 'announcements';
                $message = 'تم تعطيل الرسالة.';
                break;
            case 'update_service_status':
                ops_update_service_status($db, $admin);
                $section = 'service';
                $message = 'تم تحديث حالة الخدمة.';
                break;
            case 'toggle_feature_flag':
                ops_toggle_feature_flag($db, $admin);
                $section = 'features';
                $message = 'تم تحديث الميزة.';
                break;
            default:
                throw new InvalidArgumentException('الإجراء غير معروف.');
        }
        ops_flash('success', $message);
    } catch (InvalidArgumentException $exception) {
        ops_flash('error', $exception->getMessage());
    } catch (PDOException $exception) {
        error_log('HULK Operations database action failed: ' . $exception->getMessage());
        ops_flash(
            'error',
            $exception->getCode() === '23000'
                ? 'القيمة موجودة مسبقًا ولا يمكن تكرارها.'
                : 'تعذر حفظ التغيير في قاعدة البيانات.'
        );
    } catch (Throwable $exception) {
        error_log('HULK Operations action failed: ' . $exception->getMessage());
        ops_flash('error', 'تعذر تنفيذ العملية بأمان.');
    }

    ops_redirect('index.php?section=' . rawurlencode($section));
}
