<?php

declare(strict_types=1);

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

function cc_operations_handle_post(string $module, array $admin): string
{
    if (!in_array($module, cc_operations_modules(), true)) {
        throw new InvalidArgumentException('وحدة Operations غير صالحة.');
    }
    $action = cc_post_action();
    $allowedActions = [
        'releases' => ['upload_release', 'activate_release', 'disable_release', 'update_release_policy'],
        'service' => ['update_service_status'],
        'announcements' => ['create_announcement', 'disable_announcement'],
        'features' => ['toggle_feature_flag'],
        'growth' => ['save_growth'],
    ];
    if (!in_array($action, $allowedActions[$module] ?? [], true)) {
        throw new InvalidArgumentException('الإجراء غير مسموح لهذه الوحدة.');
    }
    $db = cc_db('control');
    return match ($action) {
        'upload_release' => (function () use ($db, $admin): string {
            ops_upload_release($db, $admin);
            return 'تم رفع APK وحساب SHA-256. الإصدار غير نشط حتى يتم تفعيله.';
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
            return 'تم تحديث سياسة الإصدار.';
        })(),
        'update_service_status' => (function () use ($db, $admin): string {
            ops_update_service_status($db, $admin);
            return 'تم تحديث حالة الخدمة.';
        })(),
        'create_announcement' => (function () use ($db, $admin): string {
            ops_create_announcement($db, $admin);
            return 'تم إنشاء الإعلان.';
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

function cc_operations_data(string $module): array
{
    $db = cc_db('control');
    return match ($module) {
        'releases' => [
            'releases' => $db->query('SELECT * FROM app_releases ORDER BY version_code DESC, id DESC LIMIT 100')->fetchAll(),
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
        'audit' => cc_operations_audit_page($db),
        default => [],
    };
}

function cc_operations_audit_page(PDO $db): array
{
    $page = max(1, (int) ($_GET['page'] ?? 1));
    $perPage = 50;
    $total = (int) $db->query('SELECT COUNT(*) FROM app_admin_audit')->fetchColumn();
    $pages = max(1, (int) ceil($total / $perPage));
    $page = min($page, $pages);
    $offset = ($page - 1) * $perPage;
    $rows = $db->query(
        'SELECT a.id, a.action, a.details, a.created_at, u.username '
        . 'FROM app_admin_audit a LEFT JOIN app_admin_users u ON u.id = a.admin_user_id '
        . 'ORDER BY a.id DESC LIMIT ' . $perPage . ' OFFSET ' . $offset
    )->fetchAll();
    return ['audit' => $rows, 'total' => $total, 'page' => $page, 'pages' => $pages];
}
