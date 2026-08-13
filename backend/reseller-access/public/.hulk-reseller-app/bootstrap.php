<?php

declare(strict_types=1);

const HULK_ACCESS_CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const HULK_ACCESS_CODE_PAYLOAD_LENGTH = 16;
const HULK_ACTIVE_STATUS = 'active';

function hulk_config(): array
{
    static $config = null;
    if (is_array($config)) {
        return $config;
    }

    $path = __DIR__ . '/config.php';
    if (!is_file($path)) {
        throw new RuntimeException('HULK reseller configuration is missing.');
    }
    $loaded = require $path;
    if (!is_array($loaded)) {
        throw new RuntimeException('HULK reseller configuration is invalid.');
    }
    $config = $loaded;
    return $config;
}

function hulk_db(): PDO
{
    static $database = null;
    if ($database instanceof PDO) {
        return $database;
    }

    $databaseConfig = hulk_config()['database'] ?? null;
    if (!is_array($databaseConfig)) {
        throw new RuntimeException('Database configuration is missing.');
    }
    $dsn = (string) ($databaseConfig['dsn'] ?? '');
    $username = (string) ($databaseConfig['username'] ?? '');
    $password = (string) ($databaseConfig['password'] ?? '');
    if ($dsn === '' || $username === '' || $password === '') {
        throw new RuntimeException('Database configuration is incomplete.');
    }

    $database = new PDO($dsn, $username, $password, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
        PDO::ATTR_STRINGIFY_FETCHES => false,
    ]);
    return $database;
}

function hulk_is_https(): bool
{
    if (isset($_SERVER['HTTPS']) && strtolower((string) $_SERVER['HTTPS']) !== 'off') {
        return true;
    }
    return (int) ($_SERVER['SERVER_PORT'] ?? 0) === 443;
}

function hulk_security_headers(): void
{
    header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
    header('Pragma: no-cache');
    header('Referrer-Policy: no-referrer');
    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: DENY');
    header("Content-Security-Policy: default-src 'none'; style-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
    header('Permissions-Policy: camera=(), geolocation=(), microphone=()');
    if (hulk_is_https()) {
        header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
    }
}

function hulk_start_session(): void
{
    if (session_status() === PHP_SESSION_ACTIVE) {
        return;
    }

    $sessionConfig = hulk_config()['session'] ?? [];
    $lifetime = max(900, min(86400, (int) ($sessionConfig['lifetime_seconds'] ?? 43200)));
    ini_set('session.use_strict_mode', '1');
    ini_set('session.use_only_cookies', '1');
    ini_set('session.gc_maxlifetime', (string) $lifetime);
    ini_set('session.cookie_httponly', '1');
    ini_set('session.cookie_samesite', 'Strict');
    session_name('HULK_RESELLER_SESSION');
    session_set_cookie_params([
        'lifetime' => $lifetime,
        'path' => '/reseller/',
        'secure' => hulk_is_https(),
        'httponly' => true,
        'samesite' => 'Strict',
    ]);
    session_start();

    $now = time();
    $lastActivity = (int) ($_SESSION['last_activity'] ?? 0);
    if ($lastActivity > 0 && $now - $lastActivity > $lifetime) {
        hulk_destroy_session();
        session_id('');
        session_start();
    }
    $_SESSION['last_activity'] = $now;
    if (!isset($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(24));
    }
}

function hulk_destroy_session(): void
{
    $_SESSION = [];
    if (session_status() === PHP_SESSION_ACTIVE) {
        $params = session_get_cookie_params();
        setcookie(session_name(), '', [
            'expires' => time() - 3600,
            'path' => $params['path'] ?: '/reseller/',
            'secure' => (bool) $params['secure'],
            'httponly' => true,
            'samesite' => 'Strict',
        ]);
        session_destroy();
    }
}

function hulk_csrf_token(): string
{
    return (string) ($_SESSION['csrf_token'] ?? '');
}

function hulk_verify_csrf(?string $token): bool
{
    $expected = hulk_csrf_token();
    return $expected !== '' && is_string($token) && hash_equals($expected, $token);
}

function hulk_normalize_reseller_name(string $value): string
{
    $value = trim($value);
    return function_exists('mb_strtolower') ? mb_strtolower($value, 'UTF-8') : strtolower($value);
}

function hulk_generate_access_code(): string
{
    $payload = '';
    $maximum = strlen(HULK_ACCESS_CODE_ALPHABET) - 1;
    for ($index = 0; $index < HULK_ACCESS_CODE_PAYLOAD_LENGTH; $index++) {
        $payload .= HULK_ACCESS_CODE_ALPHABET[random_int(0, $maximum)];
    }
    return 'HULK-' . implode('-', str_split($payload, 4));
}

function hulk_normalize_access_code(string $value): ?string
{
    $compact = strtoupper((string) preg_replace('/[\s-]+/', '', trim($value)));
    if (!str_starts_with($compact, 'HULK')) {
        return null;
    }
    $payload = substr($compact, 4);
    if (strlen($payload) !== HULK_ACCESS_CODE_PAYLOAD_LENGTH) {
        return null;
    }
    if (!preg_match('/^[' . HULK_ACCESS_CODE_ALPHABET . ']{16}$/D', $payload)) {
        return null;
    }
    return 'HULK-' . implode('-', str_split($payload, 4));
}

function hulk_access_code_hash(string $code): string
{
    return hash('sha256', $code);
}

function hulk_normalize_host(string $value): ?string
{
    $value = trim($value);
    if ($value === '' || strlen($value) > 2048 || preg_match('/[\x00-\x1F\x7F]/', $value)) {
        return null;
    }
    if (filter_var($value, FILTER_VALIDATE_URL) === false) {
        return null;
    }
    $parts = parse_url($value);
    if (!is_array($parts)) {
        return null;
    }
    $scheme = strtolower((string) ($parts['scheme'] ?? ''));
    if (($scheme !== 'http' && $scheme !== 'https') || empty($parts['host'])) {
        return null;
    }
    if (isset($parts['user']) || isset($parts['pass']) || isset($parts['query']) || isset($parts['fragment'])) {
        return null;
    }
    return rtrim($value, '/');
}

function hulk_json(array $body, int $status = 200): never
{
    hulk_security_headers();
    header('Content-Type: application/json; charset=utf-8');
    http_response_code($status);
    echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    exit;
}

function hulk_api_error(string $code, string $message, int $status): never
{
    hulk_json(['error' => ['code' => $code, 'message' => $message]], $status);
}

function hulk_redirect(string $location): never
{
    hulk_security_headers();
    header('Location: ' . $location, true, 303);
    exit;
}

function hulk_escape(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function hulk_current_reseller(): ?array
{
    $resellerId = filter_var($_SESSION['reseller_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$resellerId) {
        return null;
    }
    $statement = hulk_db()->prepare(
        'SELECT reseller_id, reseller_name, host, access_code, status FROM resellers WHERE reseller_id = :id LIMIT 1'
    );
    $statement->execute(['id' => $resellerId]);
    $reseller = $statement->fetch();
    if (!is_array($reseller) || ($reseller['status'] ?? '') !== HULK_ACTIVE_STATUS) {
        unset($_SESSION['reseller_id']);
        return null;
    }
    return $reseller;
}
