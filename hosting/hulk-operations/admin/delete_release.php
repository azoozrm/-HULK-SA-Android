<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';

try {
    $admin = ops_require_admin();
    ops_require_csrf();

    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
        throw new InvalidArgumentException('طريقة الطلب غير مسموحة.');
    }

    $releaseId = filter_var($_POST['release_id'] ?? null, FILTER_VALIDATE_INT);
    if ($releaseId === false || $releaseId < 1) {
        throw new InvalidArgumentException('الإصدار غير صالح.');
    }

    $db = ops_db();
    $db->beginTransaction();

    try {
        $statement = $db->prepare('SELECT * FROM app_releases WHERE id = :id FOR UPDATE');
        $statement->execute(['id' => (int) $releaseId]);
        $release = $statement->fetch();
        if (!is_array($release)) {
            throw new InvalidArgumentException('الإصدار غير موجود أو تم حذفه مسبقًا.');
        }

        $relativePath = ltrim(str_replace('\\', '/', (string) $release['apk_path']), '/');
        if (!preg_match('#^releases/[A-Za-z0-9._-]+\.apk$#D', $relativePath)) {
            throw new RuntimeException('مسار ملف APK غير صالح للحذف الآمن.');
        }

        if ((bool) $release['is_active']) {
            ops_set_setting($db, 'active_release_id', '');
            ops_set_setting($db, 'latest_version_code', '64');
            ops_set_setting($db, 'latest_version_name', '0.9.3.20');
            ops_set_setting($db, 'minimum_supported_version_code', '64');
            ops_set_setting($db, 'release_required', '0');
        }

        $delete = $db->prepare('DELETE FROM app_releases WHERE id = :id');
        $delete->execute(['id' => (int) $releaseId]);

        ops_audit($db, (int) $admin['id'], 'RELEASE_DELETED', [
            'release_id' => (int) $releaseId,
            'version_name' => (string) $release['version_name'],
            'version_code' => (int) $release['version_code'],
            'sha256' => (string) $release['apk_sha256'],
            'was_active' => (bool) $release['is_active'],
        ]);

        $db->commit();

        $absolutePath = dirname(__DIR__) . '/' . $relativePath;
        if (is_file($absolutePath) && !@unlink($absolutePath)) {
            error_log('HULK Operations release row deleted but APK file could not be removed: ' . $absolutePath);
        }

        ops_flash('success', 'تم حذف الإصدار وملف APK. يمكنك الآن رفع نفس رمز الإصدار من جديد.');
    } catch (Throwable $exception) {
        if ($db->inTransaction()) {
            $db->rollBack();
        }
        throw $exception;
    }
} catch (InvalidArgumentException $exception) {
    ops_flash('error', $exception->getMessage());
} catch (Throwable $exception) {
    error_log('HULK Operations release deletion failed: ' . $exception->getMessage());
    ops_flash('error', 'تعذر حذف الإصدار بأمان.');
}

ops_redirect('index.php?section=releases');
