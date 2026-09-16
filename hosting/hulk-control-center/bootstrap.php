<?php

declare(strict_types=1);

ini_set('display_errors', '0');
ini_set('log_errors', '1');

require_once __DIR__ . '/lib/policies.php';
require_once __DIR__ . '/lib/routes.php';

function cc_load_config(): array
{
    static $config = null;
    if (is_array($config)) {
        return $config;
    }

    $externalPath = trim((string) getenv('HULK_CONTROL_CENTER_CONFIG'));
    $configPath = $externalPath !== '' ? $externalPath : __DIR__ . '/config.php';
    if (!is_file($configPath)) {
        throw new RuntimeException('Control Center configuration is not installed.');
    }

    $loaded = require $configPath;
    if (
        !is_array($loaded) ||
        !is_array($loaded['databases'] ?? null) ||
        !is_array($loaded['databases']['control'] ?? null) ||
        !is_array($loaded['databases']['reseller'] ?? null) ||
        !is_array($loaded['app'] ?? null)
    ) {
        throw new RuntimeException('Control Center configuration is invalid.');
    }

    $baseUrl = rtrim(trim((string) ($loaded['app']['base_url'] ?? '')), '/');
    $baseParts = parse_url($baseUrl);
    if (
        !filter_var($baseUrl, FILTER_VALIDATE_URL) ||
        !is_array($baseParts) ||
        ($baseParts['scheme'] ?? '') !== 'https' ||
        empty($baseParts['host']) ||
        isset($baseParts['user']) ||
        isset($baseParts['pass']) ||
        isset($baseParts['query']) ||
        isset($baseParts['fragment'])
    ) {
        throw new RuntimeException('Control Center base URL must be HTTPS.');
    }
    $loaded['app']['base_url'] = $baseUrl;

    $timezone = (string) ($loaded['app']['timezone'] ?? 'Asia/Riyadh');
    if (!in_array($timezone, timezone_identifiers_list(), true)) {
        throw new RuntimeException('Control Center timezone is invalid.');
    }
    date_default_timezone_set($timezone);

    $sessionName = (string) ($loaded['app']['session_name'] ?? 'hulk_control_center');
    if (!preg_match('/^[A-Za-z0-9_-]{1,64}$/', $sessionName)) {
        throw new RuntimeException('Control Center session name is invalid.');
    }
    $loaded['app']['session_name'] = $sessionName;

    $config = $loaded;
    return $config;
}

function cc_db(string $authority): PDO
{
    static $connections = [];
    if (!in_array($authority, ['control', 'reseller'], true)) {
        throw new InvalidArgumentException('Unknown database authority.');
    }
    if (($connections[$authority] ?? null) instanceof PDO) {
        return $connections[$authority];
    }

    $database = cc_load_config()['databases'][$authority];
    $options = is_array($database['options'] ?? null) ? $database['options'] : [];
    $options[PDO::ATTR_ERRMODE] = PDO::ERRMODE_EXCEPTION;
    $options[PDO::ATTR_DEFAULT_FETCH_MODE] = PDO::FETCH_ASSOC;
    $options[PDO::ATTR_EMULATE_PREPARES] = false;

    $connections[$authority] = new PDO(
        (string) ($database['dsn'] ?? ''),
        (string) ($database['username'] ?? ''),
        (string) ($database['password'] ?? ''),
        $options
    );

    return $connections[$authority];
}

function cc_is_https_request(): bool
{
    $https = strtolower((string) ($_SERVER['HTTPS'] ?? ''));
    if ($https !== '' && $https !== 'off') {
        return true;
    }

    return strtolower((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')) === 'https';
}

function cc_apply_security_headers(): void
{
    foreach (cc_security_headers(cc_is_https_request()) as $name => $value) {
        header($name . ': ' . $value);
    }
}

function cc_start_admin_session(): void
{
    cc_apply_security_headers();
    if (session_status() === PHP_SESSION_ACTIVE) {
        return;
    }

    $app = cc_load_config()['app'];
    ini_set('session.use_strict_mode', '1');
    ini_set('session.use_only_cookies', '1');
    session_name((string) $app['session_name']);
    session_set_cookie_params(cc_session_cookie_options((string) $app['base_url']));

    if (!cc_is_https_request() && PHP_SAPI !== 'cli') {
        throw new RuntimeException('Control Center requires HTTPS.');
    }

    session_start();
}

function cc_e(mixed $value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function cc_base_path(): string
{
    $path = rtrim((string) (parse_url((string) cc_load_config()['app']['base_url'], PHP_URL_PATH) ?: ''), '/');
    return $path === '' ? '' : $path;
}

function cc_url(string $module = 'dashboard'): string
{
    $base = cc_base_path();
    return $module === 'dashboard' ? $base . '/' : $base . '/' . rawurlencode($module) . '/';
}

function cc_asset_url(string $path): string
{
    return cc_base_path() . '/assets/' . ltrim($path, '/');
}

function cc_redirect(string $path): never
{
    header('Location: ' . $path, true, 303);
    exit;
}

function cc_csrf_token(): string
{
    cc_start_admin_session();
    if (!isset($_SESSION['csrf_token']) || !is_string($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }

    return $_SESSION['csrf_token'];
}

function cc_require_csrf(): void
{
    cc_start_admin_session();
    $expected = (string) ($_SESSION['csrf_token'] ?? '');
    $provided = (string) ($_POST['csrf_token'] ?? '');
    if (!cc_csrf_tokens_match($expected, $provided)) {
        http_response_code(419);
        exit('انتهت صلاحية الطلب. أعد تحميل الصفحة وحاول مرة أخرى.');
    }
}

function cc_clear_admin_identity(): void
{
    unset($_SESSION['admin_user_id'], $_SESSION['admin_username'], $_SESSION['csrf_token']);
    if (session_status() === PHP_SESSION_ACTIVE) {
        session_regenerate_id(true);
    }
}

function cc_admin(): ?array
{
    cc_start_admin_session();
    $id = filter_var($_SESSION['admin_user_id'] ?? null, FILTER_VALIDATE_INT);
    $username = $_SESSION['admin_username'] ?? null;
    if (!$id || !is_string($username) || $username === '') {
        return null;
    }

    try {
        $statement = cc_db('control')->prepare(
            'SELECT id, username, enabled FROM app_admin_users WHERE id = :id LIMIT 1'
        );
        $statement->execute(['id' => (int) $id]);
        $user = $statement->fetch();
    } catch (Throwable $exception) {
        error_log('HULK Control Center admin authorization check failed.');
        cc_clear_admin_identity();
        return null;
    }

    if (!cc_admin_record_matches_session((int) $id, $username, $user)) {
        cc_clear_admin_identity();
        return null;
    }

    return ['id' => (int) $user['id'], 'username' => (string) $user['username']];
}

function cc_require_admin(): array
{
    $admin = cc_admin();
    if ($admin === null) {
        cc_redirect(cc_base_path() . '/login.php');
    }

    return $admin;
}
