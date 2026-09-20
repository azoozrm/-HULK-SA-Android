<?php

declare(strict_types=1);

const CC_PHASE8_AUDIT_PAGE_SIZE = 50;
const CC_PHASE8_AUDIT_MAX_PAGES = 200;
const CC_PHASE8_AUDIT_MAX_WINDOW_DAYS = 366;

function cc_phase8_query_text(mixed $value, int $maximum): string
{
    if (!is_string($value)) {
        return '';
    }
    $value = trim($value);
    return strlen($value) <= $maximum ? $value : substr($value, 0, $maximum);
}

function cc_phase8_query_token(mixed $value, int $maximum, string $pattern): string
{
    $token = cc_phase8_query_text($value, $maximum);
    return $token !== '' && preg_match($pattern, $token) === 1 ? $token : '';
}

function cc_phase8_query_integer(mixed $value, int $maximum = PHP_INT_MAX): ?int
{
    $parsed = filter_var($value, FILTER_VALIDATE_INT);
    return $parsed === false || $parsed < 1 || $parsed > $maximum ? null : (int) $parsed;
}

function cc_phase8_parse_date(mixed $value, DateTimeZone $timezone): ?DateTimeImmutable
{
    if (!is_string($value) || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $value)) {
        return null;
    }
    $date = DateTimeImmutable::createFromFormat('!Y-m-d', $value, $timezone);
    $errors = DateTimeImmutable::getLastErrors();
    if (
        !$date instanceof DateTimeImmutable
        || ($errors !== false && (($errors['warning_count'] ?? 0) > 0 || ($errors['error_count'] ?? 0) > 0))
        || $date->format('Y-m-d') !== $value
    ) {
        return null;
    }
    return $date;
}

function cc_phase8_audit_filters(array $query, ?DateTimeImmutable $now = null): array
{
    $now ??= new DateTimeImmutable('now');
    $timezone = $now->getTimezone();
    $defaultTo = $now->setTime(0, 0);
    $defaultFrom = $defaultTo->modify('-29 days');
    $from = cc_phase8_parse_date($query['date_from'] ?? null, $timezone) ?? $defaultFrom;
    $to = cc_phase8_parse_date($query['date_to'] ?? null, $timezone) ?? $defaultTo;
    $notice = '';
    if ($from > $to) {
        $from = $defaultFrom;
        $to = $defaultTo;
        $notice = 'أعيد نطاق التاريخ إلى آخر 30 يومًا لأن تاريخ البداية كان بعد النهاية.';
    }
    $windowDays = (int) $from->diff($to)->format('%a') + 1;
    if ($windowDays > CC_PHASE8_AUDIT_MAX_WINDOW_DAYS) {
        $from = $to->modify('-' . (CC_PHASE8_AUDIT_MAX_WINDOW_DAYS - 1) . ' days');
        $notice = 'قُيّد نطاق البحث إلى 366 يومًا لحماية ميزانية الاستعلام.';
    }

    $requestedPage = cc_phase8_query_integer($query['page'] ?? 1);
    $page = min($requestedPage ?? 1, CC_PHASE8_AUDIT_MAX_PAGES);

    return [
        'date_from' => $from->format('Y-m-d'),
        'date_to' => $to->format('Y-m-d'),
        'date_from_sql' => $from->format('Y-m-d 00:00:00'),
        'date_to_sql' => $to->modify('+1 day')->format('Y-m-d 00:00:00'),
        'admin' => cc_phase8_query_token($query['admin'] ?? null, 64, '/^[A-Za-z0-9._-]{1,64}$/'),
        'action' => cc_phase8_query_token($query['action'] ?? null, 80, '/^[A-Z0-9_:-]{1,80}$/'),
        'reseller_id' => cc_phase8_query_integer($query['reseller_id'] ?? null),
        'page' => $page,
        'snapshot' => cc_phase8_query_integer($query['snapshot'] ?? null),
        'notice' => $notice,
    ];
}

function cc_phase8_like_pattern(string $value, bool $prefixOnly = false): string
{
    $escaped = strtr($value, ['=' => '==', '%' => '=%', '_' => '=_']);
    return ($prefixOnly ? '' : '%') . $escaped . '%';
}

function cc_phase8_audit_action_presentation(string $action): array
{
    $catalog = [
        'ADMIN_CREATED' => ['title' => 'تم إنشاء حساب إداري', 'tone' => 'info', 'icon' => 'users'],
        'APK_UPLOADED' => ['title' => 'تم رفع إصدار جديد', 'tone' => 'info', 'icon' => 'package'],
        'RELEASE_ACTIVATED' => ['title' => 'تم تفعيل إصدار', 'tone' => 'success', 'icon' => 'package'],
        'RELEASE_DISABLED' => ['title' => 'تم تعطيل إصدار', 'tone' => 'warning', 'icon' => 'package'],
        'RELEASE_DELETED' => ['title' => 'تم حذف إصدار', 'tone' => 'danger', 'icon' => 'package'],
        'MINIMUM_VERSION_CHANGED' => ['title' => 'تم تحديث سياسة الإصدار', 'tone' => 'info', 'icon' => 'shield'],
        'ANNOUNCEMENT_CREATED' => ['title' => 'تم إنشاء إعلان', 'tone' => 'success', 'icon' => 'bell'],
        'ANNOUNCEMENT_DISABLED' => ['title' => 'تم تعطيل إعلان', 'tone' => 'warning', 'icon' => 'bell'],
        'MAINTENANCE_ENABLED' => ['title' => 'تم تغيير حالة الخدمة إلى الصيانة', 'tone' => 'warning', 'icon' => 'pulse'],
        'MAINTENANCE_DISABLED' => ['title' => 'تم تحديث حالة الخدمة', 'tone' => 'success', 'icon' => 'pulse'],
        'FEATURE_FLAG_CHANGED' => ['title' => 'تم تغيير حالة ميزة', 'tone' => 'info', 'icon' => 'toggle'],
        'GROWTH_CONFIG_PUBLISHED' => ['title' => 'تم تحديث إعدادات التجديد والدعم', 'tone' => 'info', 'icon' => 'trend'],
        'GROWTH_QR_REPLACED' => ['title' => 'تم تحديث رمز QR', 'tone' => 'info', 'icon' => 'trend'],
        'GROWTH_QR_DELETED' => ['title' => 'تم حذف رمز QR مخصص', 'tone' => 'warning', 'icon' => 'trend'],
        'CONTROL_CENTER_RESELLER_CREATED' => ['title' => 'تم إنشاء موزع', 'tone' => 'success', 'icon' => 'briefcase'],
        'CONTROL_CENTER_RESELLER_STATUS_CHANGED' => ['title' => 'تم تغيير حالة موزع', 'tone' => 'info', 'icon' => 'briefcase'],
        'CONTROL_CENTER_RESELLER_HOST_CHANGED' => ['title' => 'تم تحديث هوست موزع', 'tone' => 'info', 'icon' => 'server'],
        'CONTROL_CENTER_RESELLER_CODE_CHANGED' => ['title' => 'تم تحديث كود دخول موزع', 'tone' => 'warning', 'icon' => 'key'],
        'CONTROL_CENTER_RESELLER_PASSWORD_RESET' => ['title' => 'تمت إعادة تعيين كلمة مرور موزع', 'tone' => 'warning', 'icon' => 'shield'],
    ];
    $presentation = $catalog[$action] ?? ['title' => 'عملية إدارية', 'tone' => 'neutral', 'icon' => 'audit'];
    $presentation['technical_id'] = $action;
    $presentation['known'] = array_key_exists($action, $catalog);
    return $presentation;
}

function cc_phase8_audit_detail_definition(string $key): ?array
{
    $definitions = [
        'reseller_id' => ['label' => 'رقم الموزع', 'direction' => 'ltr'],
        'status' => ['label' => 'الحالة', 'direction' => 'auto'],
        'mode' => ['label' => 'طريقة التغيير', 'direction' => 'auto'],
        'enabled' => ['label' => 'الحالة الجديدة', 'direction' => 'auto'],
        'host_configured' => ['label' => 'إعداد الهوست', 'direction' => 'auto'],
        'release_id' => ['label' => 'رقم الإصدار الداخلي', 'direction' => 'ltr'],
        'version_name' => ['label' => 'اسم الإصدار', 'direction' => 'ltr'],
        'version_code' => ['label' => 'رمز الإصدار', 'direction' => 'ltr'],
        'minimum_version_code' => ['label' => 'الحد الأدنى المدعوم', 'direction' => 'ltr'],
        'required' => ['label' => 'سياسة التحديث', 'direction' => 'auto'],
        'message_key' => ['label' => 'معرّف الإعلان', 'direction' => 'ltr'],
        'severity' => ['label' => 'مستوى الإعلان', 'direction' => 'auto'],
        'target' => ['label' => 'الجمهور المستهدف', 'direction' => 'auto'],
        'estimated_end_at' => ['label' => 'الانتهاء المتوقع', 'direction' => 'ltr'],
        'flag_key' => ['label' => 'الميزة', 'direction' => 'ltr'],
        'slot' => ['label' => 'القسم', 'direction' => 'auto'],
        'qr_mode' => ['label' => 'طريقة رمز QR', 'direction' => 'auto'],
        'changed_fields' => ['label' => 'الإعدادات المتغيرة', 'direction' => 'auto'],
        'growth_enabled' => ['label' => 'واجهة التجديد والدعم', 'direction' => 'auto'],
        'renewal_enabled' => ['label' => 'التجديد', 'direction' => 'auto'],
        'support_enabled' => ['label' => 'الدعم', 'direction' => 'auto'],
        'renewal_qr_mode' => ['label' => 'رمز التجديد', 'direction' => 'auto'],
        'support_qr_mode' => ['label' => 'رمز الدعم', 'direction' => 'auto'],
        'days_before_expiry' => ['label' => 'التنبيه قبل الانتهاء', 'direction' => 'auto'],
        'extension' => ['label' => 'نوع الملف', 'direction' => 'ltr'],
        'size_bytes' => ['label' => 'حجم الملف', 'direction' => 'ltr'],
        'sha256' => ['label' => 'بصمة الملف', 'direction' => 'ltr'],
        'was_active' => ['label' => 'كان الإصدار نشطًا', 'direction' => 'auto'],
        'username' => ['label' => 'اسم المسؤول', 'direction' => 'ltr'],
    ];
    return $definitions[$key] ?? null;
}

function cc_phase8_audit_changed_fields(string $value): string
{
    $labels = [
        'growth_enabled' => 'واجهة التجديد والدعم',
        'growth_renewal_enabled' => 'التجديد',
        'growth_renewal_title' => 'عنوان التجديد',
        'growth_renewal_url' => 'رابط التجديد',
        'growth_renewal_display_text' => 'نص التجديد',
        'growth_renewal_qr_mode' => 'رمز التجديد',
        'growth_renewal_custom_qr_path' => 'صورة رمز التجديد',
        'growth_support_enabled' => 'الدعم',
        'growth_support_title' => 'عنوان الدعم',
        'growth_support_url' => 'رابط الدعم',
        'growth_support_display_text' => 'نص الدعم',
        'growth_support_qr_mode' => 'رمز الدعم',
        'growth_support_custom_qr_path' => 'صورة رمز الدعم',
        'growth_renewal_banner_enabled' => 'تنبيه التجديد',
        'growth_renewal_banner_days' => 'مدة تنبيه التجديد',
    ];
    $fields = array_values(array_filter(array_map('trim', explode(',', $value)), static fn (string $field): bool => $field !== ''));
    return $fields === [] ? 'لا توجد تغييرات' : implode('، ', array_map(static fn (string $field): string => $labels[$field] ?? $field, $fields));
}

function cc_phase8_audit_detail_value(string $key, mixed $value): string
{
    if ($value === null || $value === '') {
        return 'غير محدد';
    }
    if (is_bool($value)) {
        if ($key === 'required') {
            return $value ? 'إلزامي' : 'اختياري';
        }
        return $value ? 'مفعّل' : 'معطّل';
    }
    $string = (string) $value;
    if ($key === 'changed_fields') {
        return cc_phase8_audit_changed_fields($string);
    }
    $labels = [
        'active' => 'نشط', 'inactive' => 'متوقف',
        'custom' => 'مخصص', 'generated' => 'تلقائي',
        'AUTO' => 'تلقائي', 'CUSTOM' => 'مخصص',
        'renewal' => 'التجديد', 'support' => 'الدعم',
        'INFO' => 'معلومات', 'WARNING' => 'تنبيه', 'IMPORTANT' => 'مهم',
        'ALL' => 'جميع المستخدمين', 'MOBILE' => 'الجوال والأجهزة اللوحية', 'TV' => 'التلفاز',
        'OPERATIONAL' => 'تعمل بصورة طبيعية', 'DEGRADED' => 'أداء متأثر', 'MAINTENANCE' => 'صيانة جارية',
    ];
    return $labels[$string] ?? $string;
}

function cc_phase8_safe_audit_details(mixed $raw): array
{
    if (!is_string($raw) || trim($raw) === '') {
        return ['rows' => [], 'technical_json' => null, 'state' => 'empty'];
    }
    $decoded = json_decode($raw, true);
    if (!is_array($decoded)) {
        return ['rows' => [], 'technical_json' => null, 'state' => 'legacy'];
    }
    $allowedKeys = [
        'reseller_id', 'status', 'mode', 'enabled', 'host_configured',
        'release_id', 'version_name', 'version_code', 'minimum_version_code', 'required',
        'message_key', 'severity', 'target', 'estimated_end_at', 'flag_key',
        'slot', 'qr_mode', 'changed_fields', 'growth_enabled', 'renewal_enabled',
        'support_enabled', 'renewal_qr_mode', 'support_qr_mode', 'days_before_expiry',
        'extension', 'size_bytes', 'sha256', 'was_active', 'username',
    ];
    $safe = [];
    foreach ($allowedKeys as $key) {
        $value = $decoded[$key] ?? null;
        if (array_key_exists($key, $decoded) && (is_scalar($value) || $value === null)) {
            $safe[$key] = $value;
        }
    }
    if ($safe === []) {
        return ['rows' => [], 'technical_json' => null, 'state' => 'hidden'];
    }
    $rows = [];
    foreach ($safe as $key => $value) {
        $definition = cc_phase8_audit_detail_definition($key);
        if ($definition === null) {
            continue;
        }
        $rows[] = [
            'label' => $definition['label'],
            'value' => cc_phase8_audit_detail_value($key, $value),
            'direction' => $definition['direction'],
            'technical' => in_array($key, ['release_id', 'message_key', 'flag_key', 'extension', 'size_bytes', 'sha256'], true),
        ];
    }
    $encoded = json_encode($safe, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    return [
        'rows' => $rows,
        'technical_json' => is_string($encoded) ? $encoded : null,
        'state' => $rows === [] ? 'hidden' : 'available',
    ];
}

function cc_phase8_audit_page(
    PDO $db,
    array $query,
    ?DateTimeImmutable $now = null
): array {
    $filters = cc_phase8_audit_filters($query, $now);
    $snapshot = $filters['snapshot'];
    if ($snapshot === null) {
        $snapshot = (int) $db->query('SELECT COALESCE(MAX(id), 0) FROM app_admin_audit')->fetchColumn();
    }

    $conditions = [
        'a.id <= :snapshot_id',
        'a.created_at >= :date_from',
        'a.created_at < :date_to',
    ];
    $parameters = [
        'snapshot_id' => $snapshot,
        'date_from' => $filters['date_from_sql'],
        'date_to' => $filters['date_to_sql'],
    ];
    if ($filters['admin'] !== '') {
        $conditions[] = "u.username LIKE :admin ESCAPE '='";
        $parameters['admin'] = cc_phase8_like_pattern($filters['admin']);
    }
    if ($filters['action'] !== '') {
        $conditions[] = "a.action LIKE :action ESCAPE '='";
        $parameters['action'] = cc_phase8_like_pattern($filters['action'], true);
    }
    if ($filters['reseller_id'] !== null) {
        $conditions[] = "(a.details LIKE :reseller_detail_last ESCAPE '=' "
            . "OR a.details LIKE :reseller_detail_more ESCAPE '=')";
        $parameters['reseller_detail_last'] = '%"reseller_id":' . $filters['reseller_id'] . '}%';
        $parameters['reseller_detail_more'] = '%"reseller_id":' . $filters['reseller_id'] . ',%';
    }

    $offset = ($filters['page'] - 1) * CC_PHASE8_AUDIT_PAGE_SIZE;
    $statement = $db->prepare(
        'SELECT a.id, a.action, a.details, a.created_at, u.username '
        . 'FROM app_admin_audit a LEFT JOIN app_admin_users u ON u.id = a.admin_user_id '
        . 'WHERE ' . implode(' AND ', $conditions)
        . ' ORDER BY a.id DESC LIMIT 51 OFFSET ' . $offset
    );
    $statement->execute($parameters);
    $rows = $statement->fetchAll();
    $hasNext = count($rows) > CC_PHASE8_AUDIT_PAGE_SIZE
        && $filters['page'] < CC_PHASE8_AUDIT_MAX_PAGES;
    $rows = array_slice($rows, 0, CC_PHASE8_AUDIT_PAGE_SIZE);
    foreach ($rows as &$row) {
        $row['action_presentation'] = cc_phase8_audit_action_presentation((string) ($row['action'] ?? ''));
        $row['details_safe'] = cc_phase8_safe_audit_details($row['details'] ?? null);
        unset($row['details']);
    }
    unset($row);

    $publicFilters = $filters;
    unset($publicFilters['date_from_sql'], $publicFilters['date_to_sql'], $publicFilters['notice']);
    $publicFilters['snapshot'] = $snapshot;
    return [
        'audit' => $rows,
        'page' => $filters['page'],
        'has_previous' => $filters['page'] > 1,
        'has_next' => $hasNext,
        'snapshot' => $snapshot,
        'filters' => $publicFilters,
        'filter_notice' => $filters['notice'],
        'page_size' => CC_PHASE8_AUDIT_PAGE_SIZE,
        'maximum_rows' => CC_PHASE8_AUDIT_PAGE_SIZE * CC_PHASE8_AUDIT_MAX_PAGES,
    ];
}

function cc_phase8_required_integer(array $source, string $key, int $minimum, int $maximum): int
{
    $value = (int) ($source[$key] ?? 0);
    if ($value < $minimum || $value > $maximum) {
        throw new RuntimeException('Safe Control Center setting is outside its approved bound.');
    }
    return $value;
}

function cc_phase8_settings_snapshot(array $configuration): array
{
    $app = is_array($configuration['app'] ?? null) ? $configuration['app'] : [];
    $presence = is_array($configuration['presence'] ?? null) ? $configuration['presence'] : [];
    $diagnostics = is_array($configuration['diagnostics'] ?? null) ? $configuration['diagnostics'] : [];
    $health = is_array($configuration['host_health'] ?? null) ? $configuration['host_health'] : [];
    $timezone = (string) ($app['timezone'] ?? '');
    if (!in_array($timezone, timezone_identifiers_list(), true)) {
        throw new RuntimeException('Control Center timezone is invalid.');
    }
    $parsedPath = parse_url((string) ($app['base_url'] ?? ''), PHP_URL_PATH);
    $basePath = is_string($parsedPath) ? rtrim($parsedPath, '/') : '';

    return [
        'application' => ['timezone' => $timezone, 'route_path' => $basePath . '/'],
        'authentication' => [
            'authority' => 'app_admin_users',
            'cookie_path' => $basePath . '/',
            'secure' => true,
            'http_only' => true,
            'same_site' => 'Strict',
            'login_max_attempts' => max(3, (int) ($app['login_max_attempts'] ?? 5)),
            'login_lock_seconds' => max(60, (int) ($app['login_lock_seconds'] ?? 900)),
        ],
        'presence' => [
            'heartbeat_seconds' => cc_phase8_required_integer($presence, 'heartbeat_seconds', 15, 600),
            'online_ttl_seconds' => cc_phase8_required_integer($presence, 'online_ttl_seconds', 30, 3600),
            'session_retention_days' => cc_phase8_required_integer($presence, 'session_retention_days', 1, 3650),
            'device_retention_days' => cc_phase8_required_integer($presence, 'device_retention_days', 1, 3650),
            'cleanup_batch_size' => cc_phase8_required_integer($presence, 'cleanup_batch_size', 1, 1000),
        ],
        'diagnostics' => [
            'retention_days' => cc_phase8_required_integer($diagnostics, 'retention_days', 1, 30),
            'cleanup_batch_size' => cc_phase8_required_integer($diagnostics, 'cleanup_batch_size', 1, 1000),
        ],
        'host_health' => [
            'dns_timeout_ms' => cc_phase8_required_integer($health, 'dns_timeout_ms', 250, 3000),
            'connect_timeout_ms' => cc_phase8_required_integer($health, 'connect_timeout_ms', 250, 3000),
            'request_timeout_ms' => cc_phase8_required_integer($health, 'request_timeout_ms', 250, 5000),
            'max_runtime_seconds' => cc_phase8_required_integer($health, 'max_runtime_seconds', 10, 45),
            'max_hosts_per_run' => cc_phase8_required_integer($health, 'max_hosts_per_run', 1, 20),
            'candidate_limit' => cc_phase8_required_integer($health, 'candidate_limit', 1, 500),
            'retention_days' => cc_phase8_required_integer($health, 'retention_days', 7, 90),
            'cleanup_batch_size' => cc_phase8_required_integer($health, 'cleanup_batch_size', 1, 1000),
        ],
    ];
}

function cc_phase8_settings_data(array $admin): array
{
    unset($admin);
    return ['settings' => cc_phase8_settings_snapshot(cc_load_config())];
}
