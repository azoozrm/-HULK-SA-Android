<?php

declare(strict_types=1);

function cc_reseller_require_domain(): void
{
    static $loaded = false;
    if ($loaded) {
        return;
    }

    $candidates = array_filter([
        trim((string) getenv('HULK_RESELLER_BOOTSTRAP')),
        dirname(__DIR__, 2) . '/.hulk-reseller-app/bootstrap.php',
        dirname(__DIR__, 3) . '/backend/reseller-access/public/.hulk-reseller-app/bootstrap.php',
    ]);
    foreach ($candidates as $candidate) {
        if (is_file($candidate)) {
            require_once $candidate;
            $loaded = true;
            return;
        }
    }
    throw new RuntimeException('The authoritative reseller bootstrap is not installed.');
}

function cc_reseller_modules(): array
{
    return ['resellers', 'access-codes', 'hosts'];
}

function cc_reseller_handle_post(string $module, array $admin): string
{
    cc_reseller_require_domain();
    $resellerDb = cc_db('reseller');
    $controlDb = cc_db('control');
    $action = cc_post_action();
    $allowedActions = [
        'resellers' => ['create_reseller', 'set_status', 'reset_password'],
        'access-codes' => ['set_code', 'rotate_code'],
        'hosts' => ['update_host'],
    ];
    if (!in_array($action, $allowedActions[$module] ?? [], true)) {
        throw new InvalidArgumentException('الإجراء غير مسموح لهذه الوحدة.');
    }
    $resellerId = null;
    $auditAction = '';
    $details = [];

    $resellerDb->beginTransaction();
    try {
        switch ($action) {
            case 'create_reseller':
                $created = hulk_admin_create_reseller($resellerDb, $_POST);
                $resellerId = (int) $created['reseller_id'];
                $auditAction = 'CONTROL_CENTER_RESELLER_CREATED';
                $details = ['reseller_id' => $resellerId];
                $message = 'تم إنشاء الموزع وكود الدخول.';
                break;
            case 'set_status':
                $resellerId = hulk_admin_reseller_id($_POST['reseller_id'] ?? null);
                hulk_admin_set_status($resellerDb, $resellerId, $_POST['status'] ?? null);
                $auditAction = 'CONTROL_CENTER_RESELLER_STATUS_CHANGED';
                $details = ['reseller_id' => $resellerId, 'status' => (string) ($_POST['status'] ?? '')];
                $message = 'تم تحديث حالة الموزع.';
                break;
            case 'update_host':
                $resellerId = hulk_admin_reseller_id($_POST['reseller_id'] ?? null);
                $host = hulk_admin_update_host($resellerDb, $resellerId, $_POST['host'] ?? null);
                $auditAction = 'CONTROL_CENTER_RESELLER_HOST_CHANGED';
                $details = ['reseller_id' => $resellerId, 'host_configured' => $host !== ''];
                $message = $host === '' ? 'تم مسح الهوست.' : 'تم تحديث الهوست.';
                break;
            case 'set_code':
                $resellerId = hulk_admin_reseller_id($_POST['reseller_id'] ?? null);
                hulk_admin_set_code($resellerDb, $resellerId, $_POST['access_code'] ?? null);
                $auditAction = 'CONTROL_CENTER_RESELLER_CODE_CHANGED';
                $details = ['reseller_id' => $resellerId, 'mode' => 'custom'];
                $message = 'تم تحديث كود الدخول وإيقاف الكود السابق.';
                break;
            case 'rotate_code':
                $resellerId = hulk_admin_reseller_id($_POST['reseller_id'] ?? null);
                hulk_admin_rotate_code($resellerDb, $resellerId);
                $auditAction = 'CONTROL_CENTER_RESELLER_CODE_CHANGED';
                $details = ['reseller_id' => $resellerId, 'mode' => 'generated'];
                $message = 'تم تدوير كود الدخول وإيقاف الكود السابق.';
                break;
            case 'reset_password':
                $resellerId = hulk_admin_reseller_id($_POST['reseller_id'] ?? null);
                hulk_admin_reset_password(
                    $resellerDb,
                    $resellerId,
                    $_POST['password'] ?? null,
                    $_POST['confirm_password'] ?? null
                );
                $auditAction = 'CONTROL_CENTER_RESELLER_PASSWORD_RESET';
                $details = ['reseller_id' => $resellerId];
                $message = 'تم تعيين كلمة مرور جديدة للموزع.';
                break;
            default:
                throw new InvalidArgumentException('الإجراء غير معروف.');
        }

        ops_audit($controlDb, (int) $admin['id'], $auditAction, $details);
        $resellerDb->commit();
        return $message;
    } catch (Throwable $exception) {
        if ($resellerDb->inTransaction()) {
            $resellerDb->rollBack();
        }
        throw $exception;
    }
}

function cc_reseller_page(string $module): array
{
    if (!in_array($module, cc_reseller_modules(), true)) {
        throw new InvalidArgumentException('وحدة الموزعين غير معروفة.');
    }
    cc_reseller_require_domain();
    $search = trim(is_string($_GET['q'] ?? null) ? $_GET['q'] : '');
    $status = is_string($_GET['status'] ?? null) && in_array($_GET['status'], ['active', 'inactive'], true)
        ? $_GET['status']
        : '';
    $resellerFilter = filter_var($_GET['reseller'] ?? null, FILTER_VALIDATE_INT);
    $resellerFilter = $resellerFilter === false || $resellerFilter < 1 ? null : (int) $resellerFilter;
    $page = max(1, (int) ($_GET['page'] ?? 1));
    $perPage = 25;
    $conditions = [];
    $parameters = [];
    if ($search !== '') {
        $conditions[] = '(reseller_name LIKE :search_name OR host LIKE :search_host OR access_code LIKE :search_code)';
        $pattern = '%' . $search . '%';
        $parameters['search_name'] = $pattern;
        $parameters['search_host'] = $pattern;
        $parameters['search_code'] = $pattern;
    }
    if ($status !== '') {
        $conditions[] = 'status = :status';
        $parameters['status'] = $status;
    }
    if ($resellerFilter !== null) {
        $conditions[] = 'reseller_id = :reseller_id';
        $parameters['reseller_id'] = $resellerFilter;
    }
    $where = $conditions === [] ? '' : ' WHERE ' . implode(' AND ', $conditions);
    $db = cc_db('reseller');
    $count = $db->prepare('SELECT COUNT(*) FROM resellers' . $where);
    $count->execute($parameters);
    $total = (int) $count->fetchColumn();
    $pages = max(1, (int) ceil($total / $perPage));
    $page = min($page, $pages);
    $offset = ($page - 1) * $perPage;
    $query = $db->prepare(
        'SELECT reseller_id, reseller_name, host, access_code, status, created_at, updated_at '
        . 'FROM resellers' . $where . ' ORDER BY reseller_id DESC LIMIT ' . $perPage . ' OFFSET ' . $offset
    );
    $query->execute($parameters);
    $rows = $query->fetchAll();
    $presenceUsage = [];
    $presenceUsageAvailable = true;
    try {
        $presenceConfig = cc_presence_config();
        $presenceUsage = cc_presence_usage_for_resellers(
            cc_db('control'),
            $rows,
            $module,
            cc_presence_now(),
            (int) $presenceConfig['online_ttl_seconds']
        );
    } catch (Throwable $exception) {
        error_log('HULK Control Center reseller Presence usage unavailable.');
        $presenceUsageAvailable = false;
    }
    return [
        'rows' => $rows, 'total' => $total, 'page' => $page, 'pages' => $pages,
        'search' => $search, 'status' => $status, 'reseller' => $resellerFilter,
        'presence_usage' => $presenceUsage,
        'presence_usage_available' => $presenceUsageAvailable,
    ];
}
