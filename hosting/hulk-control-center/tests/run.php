<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';
require_once dirname(__DIR__) . '/lib/dashboard.php';
require_once dirname(__DIR__) . '/lib/presence.php';

date_default_timezone_set('UTC');

$tests = 0;

function cc_test(bool $condition, string $message): void
{
    global $tests;
    $tests++;
    if (!$condition) {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    }
}

cc_test(cc_csrf_tokens_match('known-token', 'known-token'), 'matching CSRF token is accepted');
cc_test(!cc_csrf_tokens_match('known-token', 'other-token'), 'mismatched CSRF token is rejected');
cc_test(!cc_csrf_tokens_match('', ''), 'empty CSRF tokens are rejected');
cc_test(password_get_info(cc_dummy_password_hash())['algoName'] === 'bcrypt', 'unknown-user verification uses a fixed valid bcrypt hash');

$activeAdmin = ['id' => 7, 'username' => 'owner', 'enabled' => 1];
cc_test(cc_admin_record_matches_session(7, 'owner', $activeAdmin), 'active authority matches the session');
cc_test(!cc_admin_record_matches_session(7, 'owner', ['id' => 7, 'username' => 'owner', 'enabled' => 0]), 'disabled authority invalidates the session');
cc_test(!cc_admin_record_matches_session(7, 'owner', ['id' => 8, 'username' => 'owner', 'enabled' => 1]), 'different authority ID invalidates the session');
cc_test(!cc_admin_record_matches_session(7, 'owner', null), 'deleted authority invalidates the session');

$now = strtotime('2026-09-16 12:00:00 UTC');
$passwordHash = password_hash('correct horse battery staple', PASSWORD_DEFAULT);
$loginAdmin = $activeAdmin + ['password_hash' => $passwordHash, 'locked_until' => null];
cc_test(cc_login_record_accepts_password($loginAdmin, 'correct horse battery staple', $now), 'valid enabled owner can sign in');
cc_test(!cc_login_record_accepts_password($loginAdmin, 'incorrect password', $now), 'incorrect owner password is rejected');
cc_test(!cc_login_record_accepts_password(null, 'incorrect password', $now), 'unknown owner follows the dummy verification path and is rejected');
cc_test(!cc_login_record_accepts_password(null, 'password', $now), 'a dummy-hash match never authenticates an unknown owner');
cc_test(!cc_login_record_accepts_password(array_replace($loginAdmin, ['enabled' => 0]), 'correct horse battery staple', $now), 'disabled owner cannot sign in');
cc_test(!cc_login_record_accepts_password(array_replace($loginAdmin, ['locked_until' => '2026-09-16 12:15:00']), 'correct horse battery staple', $now), 'locked owner cannot sign in');
cc_test(cc_login_record_is_valid($activeAdmin + ['locked_until' => null], true, $now), 'valid enabled owner record is accepted');
cc_test(!cc_login_record_is_valid($activeAdmin + ['locked_until' => null], false, $now), 'incorrect password is rejected');
cc_test(!cc_login_record_is_valid($activeAdmin + ['locked_until' => '2026-09-16 12:15:00'], true, $now), 'database lockout rejects a correct password');

$attempt = cc_next_login_attempt_state(4, 4, 5, 900, $now);
cc_test($attempt['session_attempts'] === 5, 'session failed attempts increment');
cc_test($attempt['session_locked_until'] === $now + 900, 'session lockout follows Operations duration');
cc_test($attempt['database_attempts'] === 5, 'authority failed attempts increment');
cc_test($attempt['database_locked_until'] === date('Y-m-d H:i:s', $now + 900), 'authority lockout follows Operations duration');

$cookie = cc_session_cookie_options('https://hulksa.com/control-center');
cc_test($cookie['path'] === '/control-center/', 'session cookie is scoped to Control Center');
cc_test($cookie['secure'] === true && $cookie['httponly'] === true, 'session cookie is Secure and HttpOnly');
cc_test($cookie['samesite'] === 'Strict', 'session cookie is SameSite Strict');

$headers = cc_security_headers(true);
cc_test(($headers['X-Content-Type-Options'] ?? '') === 'nosniff', 'nosniff header is present');
cc_test(($headers['X-Frame-Options'] ?? '') === 'DENY', 'frame denial header is present');
cc_test(str_contains((string) ($headers['Content-Security-Policy'] ?? ''), "frame-ancestors 'none'"), 'CSP denies framing');
cc_test(isset($headers['Strict-Transport-Security']), 'HTTPS responses include HSTS');

$sessionDirectory = sys_get_temp_dir() . '/hulk-control-center-tests-' . bin2hex(random_bytes(8));
cc_test(mkdir($sessionDirectory, 0700), 'isolated session test directory is created');
$configPath = $sessionDirectory . '/config.php';
$testConfig = [
    'databases' => [
        'control' => ['dsn' => 'test:', 'username' => '', 'password' => '', 'options' => []],
        'reseller' => ['dsn' => 'test:', 'username' => '', 'password' => '', 'options' => []],
    ],
    'app' => [
        'base_url' => 'https://hulksa.com/control-center',
        'timezone' => 'Asia/Riyadh',
        'session_name' => 'hulk_control_center_test',
    ],
];
cc_test(
    file_put_contents($configPath, '<?php return ' . var_export($testConfig, true) . ';') !== false,
    'isolated runtime configuration is created'
);
putenv('HULK_CONTROL_CENTER_CONFIG=' . $configPath);
$_SERVER['HTTPS'] = 'on';
session_save_path($sessionDirectory);
session_id(bin2hex(random_bytes(16)));
cc_start_admin_session();
$firstSessionId = session_id();
$firstCsrf = cc_csrf_token();
cc_test(strlen($firstCsrf) === 64 && ctype_xdigit($firstCsrf), 'live session creates a strong CSRF token');
cc_test(cc_csrf_token() === $firstCsrf, 'live session reuses its CSRF token');
$_SESSION['admin_user_id'] = 7;
$_SESSION['admin_username'] = 'owner';
cc_clear_admin_identity();
cc_test(session_id() !== $firstSessionId, 'clearing an owner identity rotates the live session ID');
cc_test(!isset($_SESSION['admin_user_id'], $_SESSION['admin_username'], $_SESSION['csrf_token']), 'clearing an owner identity removes authentication and CSRF state');
$_SESSION['admin_user_id'] = 7;
$_SESSION['admin_username'] = 'owner';
$_SESSION['csrf_token'] = cc_csrf_token();
cc_destroy_admin_session();
cc_test(session_status() !== PHP_SESSION_ACTIVE && $_SESSION === [], 'logout destroys the live session and clears its data');
putenv('HULK_CONTROL_CENTER_CONFIG');
unlink($configPath);
foreach (glob($sessionDirectory . '/sess_*') ?: [] as $sessionFile) {
    unlink($sessionFile);
}
rmdir($sessionDirectory);

$modules = cc_modules();
$expectedRoutes = [
    'dashboard', 'live-users', 'sessions', 'devices', 'resellers', 'access-codes', 'hosts',
    'releases', 'service', 'announcements', 'features', 'growth', 'host-health', 'diagnostics',
    'analytics', 'audit', 'settings',
];
cc_test(array_keys($modules) === $expectedRoutes, 'final module route registry is exact and ordered');
cc_test(cc_resolve_module('live-users')['key'] === 'live-users', 'allow-listed route resolves');
$fallback = cc_resolve_module('../../config.php');
cc_test($fallback['valid'] === false && $fallback['key'] === 'dashboard', 'invalid route safely falls back');

$escaped = htmlspecialchars('<script>alert("x")</script>', ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
cc_test($escaped === '&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;', 'HTML output escaping contract');

$root = dirname(__DIR__);
$layout = file_get_contents($root . '/views/layout.php');
$css = file_get_contents($root . '/assets/app.css');
$javascript = file_get_contents($root . '/assets/app.js');
$login = file_get_contents($root . '/login.php');
$logout = file_get_contents($root . '/logout.php');
$htaccess = file_get_contents($root . '/.htaccess');
$index = file_get_contents($root . '/index.php');
$operationsAdapter = file_get_contents($root . '/lib/operations-adapter.php');
$resellerAdapter = file_get_contents($root . '/lib/reseller-adapter.php');
$resellerView = file_get_contents($root . '/views/resellers.php');
$dashboardSource = file_get_contents($root . '/lib/dashboard.php');
$dashboardView = file_get_contents($root . '/views/dashboard.php');
cc_test(is_string($layout) && str_contains($layout, '<html lang="ar" dir="rtl">'), 'layout is Arabic and RTL');
cc_test(is_string($css) && str_contains($css, ':focus-visible'), 'visible keyboard focus styles exist');
cc_test(is_string($css) && str_contains($css, '@media (max-width: 860px)'), 'mobile navigation breakpoint exists');
cc_test(is_string($css) && str_contains($css, '@media (max-width: 1180px)'), 'tablet navigation breakpoint exists');
cc_test(is_string($javascript) && str_contains($javascript, "setAttribute('inert'") && str_contains($javascript, "event.key !== 'Tab'"), 'mobile drawer hides inactive navigation and traps keyboard focus');
cc_test(is_string($login) && str_contains($login, 'FOR UPDATE') && str_contains($login, 'beginTransaction()') && str_contains($login, 'commit()'), 'owner lockout updates are serialized in a database transaction');
cc_test(is_string($login) && str_contains($login, 'cc_login_record_accepts_password(') && !str_contains($login, 'password_hash('), 'unknown-user login does not generate a request-specific password hash');
cc_test(is_string($logout) && str_contains($logout, "REQUEST_METHOD") && str_contains($logout, 'cc_require_csrf()') && str_contains($logout, 'cc_destroy_admin_session()'), 'logout is POST-only, CSRF-protected, and destroys the session');
cc_test(is_string($htaccess) && str_contains($htaccess, 'config') && str_contains($htaccess, '[F,L,NC]'), 'configuration and internals are denied by web rules');
cc_test(is_string($index) && str_contains($index, 'cc_require_csrf()'), 'all Control Center mutations pass through CSRF enforcement');
cc_test(
    is_string($operationsAdapter) &&
        str_contains($operationsAdapter, "hulk-operations") &&
        str_contains($operationsAdapter, "/admin/actions.php"),
    'Operations mutations reuse the legacy authoritative action library'
);
cc_test(is_string($operationsAdapter) && str_contains($operationsAdapter, '$allowedActions'), 'Operations actions are allow-listed per module');
cc_test(is_string($resellerAdapter) && str_contains($resellerAdapter, 'admin-domain.php') === false, 'reseller adapter obtains the shared domain through the authoritative bootstrap');
cc_test(is_string($resellerAdapter) && str_contains($resellerAdapter, 'ops_audit('), 'successful Control Center reseller mutations use Operations audit');
cc_test(
    is_string($resellerAdapter) &&
        !preg_match('/\$details\s*=\s*\[[^\]]*[\'\"](?:password|access_code)[\'\"]\s*=>/i', $resellerAdapter),
    'reseller audit details omit passwords and access-code values'
);
cc_test(is_string($resellerView) && str_contains($resellerView, "credential-value") && !str_contains($resellerView, 'reveal'), 'owner access codes are displayed directly without reveal UX');

$definitions = cc_dashboard_metric_definitions();
$expectedDashboardDefinitions = [
    'service_status', 'current_release', 'current_version', 'minimum_version', 'update_policy',
    'current_announcements', 'enabled_features', 'total_resellers', 'active_resellers',
    'configured_hosts', 'resolver_ready_codes', 'recent_admin_activity',
];
cc_test(array_keys($definitions) === $expectedDashboardDefinitions, 'every Dashboard V1 metric has an explicit authoritative definition');

$operationsPayload = ['service' => ['status' => 'OPERATIONAL']];
$resellerPayload = ['total_resellers' => 0];
$composed = cc_dashboard_compose(
    static fn (): array => $operationsPayload,
    static fn (): array => $resellerPayload
);
cc_test($composed['operations']['available'] === true && $composed['operations']['data'] === $operationsPayload, 'Operations zero-capable data remains available');
cc_test($composed['reseller']['available'] === true && $composed['reseller']['data'] === $resellerPayload, 'authoritative reseller zero remains a real zero');

$operationsFailure = cc_dashboard_compose(
    static function (): array {
        throw new RuntimeException('operations unavailable');
    },
    static fn (): array => $resellerPayload
);
cc_test($operationsFailure['operations']['available'] === false && $operationsFailure['operations']['data'] === null, 'Operations failure is unavailable and never converted to zero');
cc_test($operationsFailure['reseller']['available'] === true && $operationsFailure['reseller']['data'] === $resellerPayload, 'Operations failure does not erase reseller data');

$resellerFailure = cc_dashboard_compose(
    static fn (): array => $operationsPayload,
    static function (): array {
        throw new RuntimeException('reseller unavailable');
    }
);
cc_test($resellerFailure['reseller']['available'] === false && $resellerFailure['reseller']['data'] === null, 'reseller failure is unavailable and never converted to zero');
cc_test($resellerFailure['operations']['available'] === true && $resellerFailure['operations']['data'] === $operationsPayload, 'reseller failure does not erase Operations data');

cc_test(
    is_string($dashboardSource) &&
        str_contains($dashboardSource, 'ops_service_snapshot(') &&
        str_contains($dashboardSource, 'ops_update_snapshot(') &&
        str_contains($dashboardSource, 'ops_active_release(') &&
        str_contains($dashboardSource, 'ops_feature_snapshot(') &&
        str_contains($dashboardSource, 'ops_announcement_is_active('),
    'Dashboard Operations reads reuse public-contract domain semantics'
);
cc_test(
    is_string($dashboardSource) &&
        str_contains($dashboardSource, "status = 'active'") &&
        str_contains($dashboardSource, 'hulk_normalize_host(') &&
        str_contains($dashboardSource, 'hulk_normalize_access_code(') &&
        str_contains($dashboardSource, 'hulk_access_code_hash('),
    'Dashboard reseller reads reuse resolver status, host, canonical-code and hash semantics'
);
cc_test(
    is_string($dashboardSource) &&
        str_contains($dashboardSource, 'SELECT a.id, a.action, a.created_at, u.username') &&
        !preg_match('/SELECT[^;]*a\.details/is', $dashboardSource),
    'Dashboard recent audit query excludes audit details and secret-bearing payloads'
);
cc_test(
    is_string($dashboardView) &&
        str_contains($dashboardView, 'المستخدمون الآن') &&
        str_contains($dashboardView, 'الجلسات اليوم') &&
        str_contains($dashboardView, 'الأجهزة النشطة') &&
        substr_count($dashboardView, 'غير متاح بعد') >= 6,
    'future Presence, session, device, adoption, health and analytics metrics remain explicitly unavailable'
);
cc_test(
    is_string($dashboardView) &&
        !preg_match('/(?:المستخدمون الآن|الجلسات اليوم|الأجهزة النشطة)[^\n]{0,160}>\s*[0-9]+\s*</u', $dashboardView),
    'Dashboard does not fabricate future metric values'
);
cc_test(is_string($index) && str_contains($index, "\$moduleKey === 'dashboard'") && str_contains($index, 'cc_dashboard_data()'), 'Dashboard is connected through the existing allow-listed route');

$forbidden = [
    '/hulknjcx_/i' => 'production database name',
    '/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/' => 'private key',
];
$iterator = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS));
foreach ($iterator as $file) {
    if (!$file->isFile() || $file->getFilename() === 'run.php') {
        continue;
    }
    $contents = file_get_contents($file->getPathname());
    if (!is_string($contents)) {
        continue;
    }
    foreach ($forbidden as $pattern => $label) {
        cc_test(!preg_match($pattern, $contents), $label . ' is absent from ' . $file->getFilename());
    }
}

cc_test(!is_file($root . '/config.php'), 'runtime config is not present in source');

require __DIR__ . '/presence.php';

fwrite(STDOUT, "PASS: {$tests} HULK Control Center checks.\n");
