<?php

declare(strict_types=1);

require_once __DIR__ . '/phase8.php';

$operationsRoot = dirname(__DIR__, 2) . '/hulk-operations';
require_once $operationsRoot . '/bootstrap.php';
if (!defined('HULK_OPERATIONS_ADMIN')) {
    define('HULK_OPERATIONS_ADMIN', true);
}
require_once $operationsRoot . '/admin/actions.php';

function cc_operations_modules(): array
{
    return ['releases', 'service', 'announcements', 'features', 'growth'];
}

function cc_operations_remember_review_release_id(int $releaseId): void
{
    if ($releaseId < 1) {
        return;
    }
    cc_start_admin_session();
    $_SESSION['cc_review_release_id'] = $releaseId;
}

function cc_operations_take_review_release_id(): ?int
{
    cc_start_admin_session();
    $releaseId = filter_var($_SESSION['cc_review_release_id'] ?? null, FILTER_VALIDATE_INT);
    unset($_SESSION['cc_review_release_id']);

    return $releaseId !== false && $releaseId > 0 ? (int) $releaseId : null;
}

/**
 * Turn authoritative release rows into safe display facts.
 *
 * The view never receives raw filesystem paths or SHA metadata keys: it only
 * receives a validated public URL, a basename and an independently checked
 * SHA-256 string.
 */
function cc_operations_release_rows(array $releases): array
{
    $activeCode = 0;
    $activeMinimum = 0;
    foreach ($releases as $candidate) {
        if (
            is_array($candidate)
            && !empty($candidate['enabled'])
            && !empty($candidate['is_active'])
        ) {
            $activeCode = (int) ($candidate['version_code'] ?? 0);
            $activeMinimum = (int) ($candidate['minimum_supported_version_code'] ?? 0);
            break;
        }
    }

    $rows = [];
    foreach ($releases as $release) {
        if (!is_array($release)) {
            continue;
        }

        $versionCode = (int) ($release['version_code'] ?? 0);
        $enabled = !empty($release['enabled']);
        $isActive = $enabled && !empty($release['is_active']);
        $minimum = (int) ($release['minimum_supported_version_code'] ?? 0);
        $path = ops_release_relative_apk_path($release);
        $sha = strtolower(trim((string) ($release['apk_sha256'] ?? '')));
        if (!ops_is_sha256($sha)) {
            $sha = '';
        }

        $rows[] = [
            'id' => (int) ($release['id'] ?? 0),
            'version_name' => (string) ($release['version_name'] ?? ''),
            'version_code' => $versionCode,
            'minimum_supported_version_code' => $minimum,
            'required' => !empty($release['required']),
            'enabled' => $enabled,
            'is_active' => $isActive,
            'is_newer' => $activeCode === 0 || $versionCode > $activeCode,
            'is_older' => $activeCode > 0 && $versionCode < $activeCode,
            'below_active_minimum' => $activeMinimum > 0 && $minimum < $activeMinimum,
            'file_name' => $path === null ? '' : basename($path),
            'public_apk_url' => ops_release_public_apk_url($release),
            'sha256' => $sha,
            'size_bytes' => (int) ($release['apk_size_bytes'] ?? 0),
            'release_notes' => (string) ($release['release_notes'] ?? ''),
            'created_at' => (string) ($release['created_at'] ?? ''),
            'artifact_ready' => ops_release_artifact_is_readable($release),
        ];
    }

    return $rows;
}

function cc_operations_handle_post(string $module, array $admin): string
{
    if (!in_array($module, cc_operations_modules(), true)) {
        throw new InvalidArgumentException('وحدة Operations غير صالحة.');
    }
    $action = cc_post_action();
    $allowedActions = [
        'releases' => ['upload_release', 'activate_release', 'disable_release', 'update_release_policy', 'delete_release'],
        'service' => ['update_service_status'],
        'announcements' => ['create_announcement', 'update_announcement', 'disable_announcement'],
        'features' => ['toggle_feature_flag'],
        'growth' => ['save_growth'],
    ];
    if (!in_array($action, $allowedActions[$module] ?? [], true)) {
        throw new InvalidArgumentException('الإجراء غير مسموح لهذه الوحدة.');
    }
    $db = cc_db('control');
    return match ($action) {
        'upload_release' => (function () use ($db, $admin): string {
            $versionCode = filter_var($_POST['version_code'] ?? null, FILTER_VALIDATE_INT);
            ops_upload_release($db, $admin);
            if ($versionCode !== false && $versionCode > 0) {
                $statement = $db->prepare(
                    'SELECT id FROM app_releases WHERE version_code = :version_code LIMIT 1'
                );
                $statement->execute(['version_code' => (int) $versionCode]);
                $releaseId = $statement->fetchColumn();
                if ($releaseId !== false) {
                    cc_operations_remember_review_release_id((int) $releaseId);
                }
            }
            return 'تم رفع APK وحساب SHA-256. راجع النسخة قبل النشر.';
        })(),
        'activate_release' => (function () use ($db, $admin): string {
            ops_activate_release($db, $admin);
            return 'تم تفعيل الإصدار.';
        })(),
        'disable_release' => (function () use ($db, $admin): string {
            ops_disable_release($db, $admin);
            return 'تم تعطيل الإصدار.';
        })(),
        'update_release_policy' => (function () use ($db, $admin): string {
            ops_update_release_policy($db, $admin);
            if (isset($_POST['return_to_review']) && (string) $_POST['return_to_review'] === '1') {
                $releaseId = filter_var($_POST['release_id'] ?? null, FILTER_VALIDATE_INT);
                if ($releaseId !== false && $releaseId > 0) {
                    cc_operations_remember_review_release_id((int) $releaseId);
                }
            }
            return 'تم تحديث سياسة الإصدار.';
        })(),
        'delete_release' => ops_delete_release($db, $admin),
        'update_service_status' => (function () use ($db, $admin): string {
            ops_update_service_status($db, $admin);
            return 'تم تحديث حالة الخدمة.';
        })(),
        'create_announcement' => (function () use ($db, $admin): string {
            ops_create_announcement($db, $admin);
            return 'تم إنشاء الإعلان.';
        })(),
        'update_announcement' => (function () use ($db, $admin): string {
            ops_update_announcement($db, $admin);
            return 'تم تحديث الإعلان.';
        })(),
        'disable_announcement' => (function () use ($db, $admin): string {
            ops_disable_announcement($db, $admin);
            return 'تم تعطيل الإعلان.';
        })(),
        'toggle_feature_flag' => (function () use ($db, $admin): string {
            ops_toggle_feature_flag($db, $admin);
            return 'تم تحديث الميزة.';
        })(),
        'save_growth' => ops_save_growth($db, $admin),
        default => throw new InvalidArgumentException('الإجراء غير معروف.'),
    };
}

function cc_operations_data(string $module, array $query = []): array
{
    $db = cc_db('control');
    return match ($module) {
        'releases' => [
            'releases' => cc_operations_release_rows(
                $db->query('SELECT * FROM app_releases ORDER BY version_code DESC, id DESC LIMIT 100')->fetchAll()
            ),
        ],
        'service' => [
            'service' => ($db->query('SELECT * FROM app_service_status WHERE id = 1 LIMIT 1')->fetch() ?: [
                'status' => 'OPERATIONAL', 'message' => '', 'starts_at' => null, 'estimated_end_at' => null,
            ]),
        ],
        'announcements' => [
            'announcements' => $db->query('SELECT * FROM app_announcements ORDER BY starts_at DESC, id DESC LIMIT 100')->fetchAll(),
        ],
        'features' => ['features' => ops_feature_snapshot($db)],
        'growth' => ['growth' => ops_growth_snapshot($db)],
        'audit' => cc_phase8_audit_page($db, $query),
        default => [],
    };
}
