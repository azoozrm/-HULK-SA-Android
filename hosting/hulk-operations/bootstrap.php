<?php

declare(strict_types=1);

ini_set('display_errors', '0');
ini_set('log_errors', '1');

require_once __DIR__ . '/lib/policies.php';

function ops_load_config(): array
{
    static $config = null;
    if (is_array($config)) {
        return $config;
    }

    $externalPath = trim((string) getenv('HULK_OPERATIONS_CONFIG'));
    $configPath = $externalPath !== '' ? $externalPath : __DIR__ . '/config.php';
    if (!is_file($configPath)) {
        throw new RuntimeException('Operations configuration is not installed.');
    }

    $loaded = require $configPath;
    if (!is_array($loaded) || !isset($loaded['database'], $loaded['app'])) {
        throw new RuntimeException('Operations configuration is invalid.');
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
        throw new RuntimeException('Operations base URL must be HTTPS.');
    }
    $loaded['app']['base_url'] = $baseUrl;

    $timezone = (string) ($loaded['app']['timezone'] ?? 'Asia/Riyadh');
    if (!in_array($timezone, timezone_identifiers_list(), true)) {
        throw new RuntimeException('Operations timezone is invalid.');
    }
    date_default_timezone_set($timezone);

    $config = $loaded;
    return $config;
}

function ops_db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }

    $database = ops_load_config()['database'];
    $options = is_array($database['options'] ?? null) ? $database['options'] : [];
    $options[PDO::ATTR_ERRMODE] = PDO::ERRMODE_EXCEPTION;
    $options[PDO::ATTR_DEFAULT_FETCH_MODE] = PDO::FETCH_ASSOC;
    $options[PDO::ATTR_EMULATE_PREPARES] = false;

    $pdo = new PDO(
        (string) ($database['dsn'] ?? ''),
        (string) ($database['username'] ?? ''),
        (string) ($database['password'] ?? ''),
        $options
    );

    return $pdo;
}

function ops_is_https_request(): bool
{
    $https = strtolower((string) ($_SERVER['HTTPS'] ?? ''));
    if ($https !== '' && $https !== 'off') {
        return true;
    }

    return strtolower((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')) === 'https';
}

function ops_start_admin_session(): void
{
    if (session_status() === PHP_SESSION_ACTIVE) {
        return;
    }

    $app = ops_load_config()['app'];
    ini_set('session.use_strict_mode', '1');
    ini_set('session.use_only_cookies', '1');
    session_name((string) ($app['session_name'] ?? 'hulk_operations_admin'));
    $cookiePath = rtrim((string) (parse_url((string) $app['base_url'], PHP_URL_PATH) ?: ''), '/') . '/';
    session_set_cookie_params([
        'lifetime' => 0,
        'path' => $cookiePath,
        'domain' => '',
        'secure' => true,
        'httponly' => true,
        'samesite' => 'Strict',
    ]);

    if (!ops_is_https_request() && PHP_SAPI !== 'cli') {
        throw new RuntimeException('The administration panel requires HTTPS.');
    }

    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: DENY');
    header('Referrer-Policy: no-referrer');
    header('Cache-Control: no-store');
    session_start();
}

function ops_e(?string $value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function ops_text_length(string $value): int
{
    return function_exists('mb_strlen') ? mb_strlen($value, 'UTF-8') : strlen($value);
}

function ops_text_excerpt(string $value, int $maximum): string
{
    if (ops_text_length($value) <= $maximum) {
        return $value;
    }
    if (function_exists('mb_substr')) {
        return mb_substr($value, 0, $maximum - 1, 'UTF-8') . '…';
    }
    return substr($value, 0, $maximum - 3) . '...';
}

function ops_csrf_token(): string
{
    ops_start_admin_session();
    if (!isset($_SESSION['csrf_token']) || !is_string($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }

    return $_SESSION['csrf_token'];
}

function ops_require_csrf(): void
{
    ops_start_admin_session();
    $expected = (string) ($_SESSION['csrf_token'] ?? '');
    $provided = (string) ($_POST['csrf_token'] ?? '');
    if (!ops_csrf_tokens_match($expected, $provided)) {
        http_response_code(419);
        exit('انتهت صلاحية الطلب. أعد تحميل الصفحة وحاول مرة أخرى.');
    }
}

function ops_admin(): ?array
{
    ops_start_admin_session();
    $id = filter_var($_SESSION['admin_user_id'] ?? null, FILTER_VALIDATE_INT);
    $username = $_SESSION['admin_username'] ?? null;
    if (!$id || !is_string($username) || $username === '') {
        return null;
    }

    return ['id' => (int) $id, 'username' => $username];
}

function ops_require_admin(): array
{
    $admin = ops_admin();
    if ($admin === null) {
        ops_redirect('login.php');
    }

    return $admin;
}

function ops_redirect(string $path): never
{
    header('Location: ' . $path, true, 303);
    exit;
}

function ops_json(array $payload, int $status = 200): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    exit;
}

function ops_setting(PDO $db, string $key, ?string $default = null): ?string
{
    $statement = $db->prepare('SELECT setting_value FROM app_settings WHERE setting_key = :setting_key LIMIT 1');
    $statement->execute(['setting_key' => $key]);
    $value = $statement->fetchColumn();
    return $value === false ? $default : (string) $value;
}

function ops_set_setting(PDO $db, string $key, string $value): void
{
    $statement = $db->prepare(
        'INSERT INTO app_settings (setting_key, setting_value) VALUES (:setting_key, :setting_value) '
        . 'ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)'
    );
    $statement->execute(['setting_key' => $key, 'setting_value' => $value]);
}

function ops_audit(PDO $db, ?int $adminId, string $action, array $details = []): void
{
    $sanitized = [];
    foreach ($details as $key => $value) {
        if (preg_match('/password|secret|token|credential/i', (string) $key)) {
            continue;
        }
        $sanitized[(string) $key] = is_scalar($value) || $value === null ? $value : '[complex]';
    }

    $statement = $db->prepare(
        'INSERT INTO app_admin_audit (admin_user_id, action, details) '
        . 'VALUES (:admin_user_id, :action, :details)'
    );
    $statement->execute([
        'admin_user_id' => $adminId,
        'action' => substr($action, 0, 80),
        'details' => $sanitized === []
            ? null
            : json_encode($sanitized, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
    ]);
}

function ops_public_url(string $relativePath): string
{
    $baseUrl = (string) ops_load_config()['app']['base_url'];
    return $baseUrl . '/' . ltrim($relativePath, '/');
}

function ops_flash(string $type, string $message): void
{
    ops_start_admin_session();
    $_SESSION['flash'] = ['type' => $type, 'message' => $message];
}

function ops_take_flash(): ?array
{
    ops_start_admin_session();
    $flash = $_SESSION['flash'] ?? null;
    unset($_SESSION['flash']);
    return is_array($flash) ? $flash : null;
}

function ops_parse_local_datetime(?string $value): ?string
{
    $clean = trim((string) $value);
    if ($clean === '') {
        return null;
    }

    $date = DateTimeImmutable::createFromFormat('Y-m-d\TH:i', $clean);
    $errors = DateTimeImmutable::getLastErrors();
    if (!$date || ($errors !== false && ($errors['warning_count'] > 0 || $errors['error_count'] > 0))) {
        return null;
    }

    return $date->format('Y-m-d H:i:s');
}

function ops_datetime_local_value(?string $value): string
{
    if (!$value) {
        return '';
    }

    try {
        return (new DateTimeImmutable($value))->format('Y-m-d\TH:i');
    } catch (Throwable $exception) {
        return '';
    }
}

function ops_datetime_epoch(?string $value): ?int
{
    if (!$value) {
        return null;
    }

    try {
        return (new DateTimeImmutable($value))->getTimestamp();
    } catch (Throwable $exception) {
        return null;
    }
}

require_once __DIR__ . '/lib/operations.php';
