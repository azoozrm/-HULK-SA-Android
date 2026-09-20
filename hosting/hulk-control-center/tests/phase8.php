<?php

declare(strict_types=1);

$phaseEightRoot = dirname(__DIR__);
require_once $phaseEightRoot . '/lib/phase8.php';

$now = new DateTimeImmutable('2026-09-19 12:00:00', new DateTimeZone('Asia/Riyadh'));
$filters = cc_phase8_audit_filters([
    'date_from' => '2026-09-01',
    'date_to' => '2026-09-19',
    'admin' => 'owner',
    'action' => 'CONTROL_CENTER_',
    'reseller_id' => '7',
    'page' => '2',
    'snapshot' => '99',
], $now);
cc_test($filters['date_from'] === '2026-09-01' && $filters['date_to'] === '2026-09-19', 'Phase 8 audit accepts a bounded explicit date window');
cc_test($filters['admin'] === 'owner' && $filters['action'] === 'CONTROL_CENTER_', 'Phase 8 audit accepts bounded administrator and action filters');
cc_test($filters['reseller_id'] === 7 && $filters['page'] === 2 && $filters['snapshot'] === 99, 'Phase 8 audit preserves safe stable-pagination context');

$unsafeTokens = cc_phase8_audit_filters(['admin' => 'owner/password', 'action' => 'free text'], $now);
cc_test($unsafeTokens['admin'] === '' && $unsafeTokens['action'] === '', 'Phase 8 audit rejects unsafe free-form URL tokens');

$fallback = cc_phase8_audit_filters([
    'date_from' => '2020-01-01',
    'date_to' => '2026-09-19',
    'page' => '999999',
], $now);
cc_test($fallback['page'] === CC_PHASE8_AUDIT_MAX_PAGES, 'Phase 8 audit page count is bounded');
cc_test($fallback['notice'] !== '', 'Phase 8 audit reports a normalized over-wide date window');

$safeDetails = cc_phase8_safe_audit_details(json_encode([
    'reseller_id' => 7,
    'status' => 'active',
    'password' => 'synthetic-secret',
    'token' => 'synthetic-token',
], JSON_THROW_ON_ERROR));
cc_test(str_contains($safeDetails, 'reseller_id') && str_contains($safeDetails, 'status'), 'Phase 8 audit retains allow-listed operational details');
cc_test(!str_contains($safeDetails, 'password') && !str_contains($safeDetails, 'token') && !str_contains($safeDetails, 'synthetic'), 'Phase 8 audit suppresses secret-bearing detail keys and values');

$settings = cc_phase8_settings_snapshot([
    'app' => [
        'base_url' => 'https://example.test/control-center',
        'timezone' => 'Asia/Riyadh',
        'session_name' => 'private-cookie-name',
        'login_max_attempts' => 5,
        'login_lock_seconds' => 900,
    ],
    'databases' => [
        'control' => ['dsn' => 'mysql:host=private;dbname=private', 'username' => 'private', 'password' => 'private'],
        'reseller' => ['dsn' => 'mysql:host=private;dbname=private', 'username' => 'private', 'password' => 'private'],
    ],
    'presence' => [
        'heartbeat_seconds' => 60,
        'online_ttl_seconds' => 180,
        'session_retention_days' => 180,
        'device_retention_days' => 180,
        'cleanup_batch_size' => 200,
        'rate_limit_secret' => str_repeat('s', 32),
        'token_secret' => str_repeat('t', 32),
    ],
    'diagnostics' => ['retention_days' => 30, 'cleanup_batch_size' => 200],
    'host_health' => [
        'connect_timeout_ms' => 3000,
        'dns_timeout_ms' => 2000,
        'request_timeout_ms' => 5000,
        'max_runtime_seconds' => 45,
        'max_hosts_per_run' => 20,
        'candidate_limit' => 500,
        'retention_days' => 90,
        'cleanup_batch_size' => 200,
        'user_agent' => 'private-user-agent',
    ],
]);
$encodedSettings = json_encode($settings, JSON_THROW_ON_ERROR);
cc_test(str_contains($encodedSettings, 'Asia/Riyadh') && str_contains($encodedSettings, 'online_ttl_seconds'), 'Phase 8 settings exposes useful safe runtime boundaries');
cc_test(($settings['authentication']['login_max_attempts'] ?? 0) === 5 && ($settings['authentication']['login_lock_seconds'] ?? 0) === 900, 'Phase 8 settings exposes the effective non-secret lockout policy');
foreach (['private-cookie-name', 'mysql:', 'private-user-agent', str_repeat('s', 32), str_repeat('t', 32)] as $forbidden) {
    cc_test(!str_contains($encodedSettings, $forbidden), 'Phase 8 settings never exposes private runtime configuration');
}

if (!in_array('sqlite', PDO::getAvailableDrivers(), true)) {
    fwrite(STDOUT, "SKIP: PDO SQLite is unavailable; Phase 8 audit database checks were not run.\n");
    return;
}

$db = new PDO('sqlite::memory:');
$db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
$db->exec('CREATE TABLE app_admin_users (id INTEGER PRIMARY KEY, username TEXT NOT NULL)');
$db->exec('CREATE TABLE app_admin_audit (id INTEGER PRIMARY KEY, admin_user_id INTEGER, action TEXT NOT NULL, details TEXT, created_at TEXT NOT NULL)');
$db->exec("INSERT INTO app_admin_users VALUES (1, 'owner'), (2, 'operator')");
$insert = $db->prepare('INSERT INTO app_admin_audit VALUES (:id, :admin, :action, :details, :created)');
for ($id = 1; $id <= 55; $id++) {
    $insert->execute([
        'id' => $id,
        'admin' => $id % 2 === 0 ? 1 : 2,
        'action' => $id % 3 === 0 ? 'CONTROL_CENTER_RESELLER_STATUS_CHANGED' : 'RELEASE_UPDATED',
        'details' => json_encode(['reseller_id' => $id === 6 ? 70 : ($id % 3 === 0 ? 7 : 9), 'password' => 'never-display'], JSON_THROW_ON_ERROR),
        'created' => '2026-09-' . str_pad((string) (($id % 19) + 1), 2, '0', STR_PAD_LEFT) . ' 10:00:00',
    ]);
}

$page = cc_phase8_audit_page($db, [
    'date_from' => '2026-09-01',
    'date_to' => '2026-09-19',
    'admin' => 'owner',
    'action' => 'CONTROL_CENTER_',
    'reseller_id' => '7',
], $now);
cc_test(count($page['audit']) > 0 && count($page['audit']) <= CC_PHASE8_AUDIT_PAGE_SIZE, 'Phase 8 audit returns only the bounded page');
cc_test($page['snapshot'] === 55, 'Phase 8 audit captures an immutable pagination snapshot');
foreach ($page['audit'] as $row) {
    cc_test($row['username'] === 'owner', 'Phase 8 audit administrator filter is applied by SQL');
    cc_test(str_starts_with($row['action'], 'CONTROL_CENTER_'), 'Phase 8 audit action prefix filter is applied by SQL');
    cc_test(str_contains($row['details_safe'], '"reseller_id":7'), 'Phase 8 audit reseller detail filter uses an exact numeric JSON boundary');
    cc_test(str_contains($row['details_safe'], 'reseller_id') && !str_contains($row['details_safe'], 'password'), 'Phase 8 audit emits only safe detail output');
    cc_test(!array_key_exists('details', $row), 'Phase 8 audit removes raw details before the view boundary');
}
