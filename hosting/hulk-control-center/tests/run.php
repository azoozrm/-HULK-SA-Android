<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/lib/policies.php';
require_once dirname(__DIR__) . '/lib/routes.php';

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

$activeAdmin = ['id' => 7, 'username' => 'owner', 'enabled' => 1];
cc_test(cc_admin_record_matches_session(7, 'owner', $activeAdmin), 'active authority matches the session');
cc_test(!cc_admin_record_matches_session(7, 'owner', ['id' => 7, 'username' => 'owner', 'enabled' => 0]), 'disabled authority invalidates the session');
cc_test(!cc_admin_record_matches_session(7, 'owner', ['id' => 8, 'username' => 'owner', 'enabled' => 1]), 'different authority ID invalidates the session');
cc_test(!cc_admin_record_matches_session(7, 'owner', null), 'deleted authority invalidates the session');

$now = strtotime('2026-09-16 12:00:00 UTC');
cc_test(cc_login_record_is_valid($activeAdmin + ['locked_until' => null], true, $now), 'valid enabled owner can sign in');
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
$logout = file_get_contents($root . '/logout.php');
$htaccess = file_get_contents($root . '/.htaccess');
cc_test(is_string($layout) && str_contains($layout, '<html lang="ar" dir="rtl">'), 'layout is Arabic and RTL');
cc_test(is_string($css) && str_contains($css, ':focus-visible'), 'visible keyboard focus styles exist');
cc_test(is_string($css) && str_contains($css, '@media (max-width: 860px)'), 'mobile navigation breakpoint exists');
cc_test(is_string($css) && str_contains($css, '@media (max-width: 1180px)'), 'tablet navigation breakpoint exists');
cc_test(is_string($logout) && str_contains($logout, "REQUEST_METHOD") && str_contains($logout, 'cc_require_csrf()') && str_contains($logout, 'session_destroy()'), 'logout is POST-only, CSRF-protected, and destroys the session');
cc_test(is_string($htaccess) && str_contains($htaccess, 'config') && str_contains($htaccess, '[F,L,NC]'), 'configuration and internals are denied by web rules');

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

fwrite(STDOUT, "PASS: {$tests} HULK Control Center Phase 1 checks.\n");
