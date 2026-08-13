<?php

declare(strict_types=1);

const HULK_ACCESS_CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const HULK_ACCESS_CODE_PAYLOAD_LENGTH = 8;
const HULK_CUSTOM_ACCESS_CODE_MAX_LENGTH = 12;
const HULK_LEGACY_ACCESS_CODE_PAYLOAD_LENGTH = 16;
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
    header('Cross-Origin-Opener-Policy: same-origin');
    header('Cross-Origin-Resource-Policy: same-origin');
    header(
        "Content-Security-Policy: default-src 'none'; "
        . "script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; "
        . "form-action 'self'; base-uri 'none'; frame-ancestors 'none'"
    );
    header('Permissions-Policy: camera=(), geolocation=(), microphone=()');
    if (hulk_is_https()) {
        header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
    }
}

function hulk_start_session(string $area = 'reseller'): void
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
    $isAdmin = $area === 'admin';
    $sessionPath = $isAdmin ? '/hulk-reseller-admin/' : '/reseller/';
    session_name($isAdmin ? 'HULK_ADMIN_SESSION' : 'HULK_RESELLER_SESSION');
    session_set_cookie_params([
        'lifetime' => $lifetime,
        'path' => $sessionPath,
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
    return hulk_format_access_code($payload);
}

function hulk_normalize_access_code(string $value): ?string
{
    $compact = strtoupper((string) preg_replace('/[\s-]+/', '', trim($value)));
    if (!str_starts_with($compact, 'HULK')) {
        return null;
    }
    $payload = substr($compact, 4);
    $payloadLength = strlen($payload);
    $isCurrentLength = $payloadLength >= HULK_ACCESS_CODE_PAYLOAD_LENGTH
        && $payloadLength <= HULK_CUSTOM_ACCESS_CODE_MAX_LENGTH;
    if (!$isCurrentLength && $payloadLength !== HULK_LEGACY_ACCESS_CODE_PAYLOAD_LENGTH) {
        return null;
    }
    if (!preg_match('/^[A-Z0-9]+$/D', $payload)) {
        return null;
    }
    return hulk_format_access_code($payload);
}

function hulk_normalize_custom_access_code(string $value): ?string
{
    $compact = (string) preg_replace('/[\s-]+/', '', trim($value));
    if (str_starts_with(strtoupper($compact), 'HULK')) {
        $compact = substr($compact, 4);
    }
    $payloadLength = strlen($compact);
    if ($payloadLength < HULK_ACCESS_CODE_PAYLOAD_LENGTH || $payloadLength > HULK_CUSTOM_ACCESS_CODE_MAX_LENGTH) {
        return null;
    }
    if (!preg_match('/^[A-Za-z0-9]+$/D', $compact) || !preg_match('/[A-Z]/', $compact)) {
        return null;
    }
    return hulk_format_access_code(strtoupper($compact));
}

function hulk_format_access_code(string $payload): string
{
    return 'HULK-' . implode('-', str_split($payload, 4));
}

function hulk_access_code_hash(string $code): string
{
    return hash('sha256', $code);
}

function hulk_enforce_resolver_rate_limit(): void
{
    $clientAddress = trim((string) ($_SERVER['REMOTE_ADDR'] ?? 'unknown'));
    $clientHash = hash('sha256', $clientAddress);
    $database = hulk_db();
    $statement = $database->prepare(
        'INSERT INTO resolver_rate_limits (client_hash, window_started_at, attempts) '
        . 'VALUES (:client_hash, UTC_TIMESTAMP(), 1) '
        . 'ON DUPLICATE KEY UPDATE '
        . 'attempts = IF(window_started_at <= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 5 MINUTE), 1, LEAST(attempts + 1, 31)), '
        . 'window_started_at = IF(window_started_at <= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 5 MINUTE), '
        . 'UTC_TIMESTAMP(), window_started_at)'
    );
    $statement->execute(['client_hash' => $clientHash]);

    $lookup = $database->prepare(
        'SELECT attempts FROM resolver_rate_limits WHERE client_hash = :client_hash LIMIT 1'
    );
    $lookup->execute(['client_hash' => $clientHash]);
    $attempts = (int) ($lookup->fetchColumn() ?: 0);
    if ($attempts > 30) {
        hulk_api_error('RATE_LIMITED', 'محاولات كثيرة. حاول مرة أخرى بعد خمس دقائق.', 429);
    }

    if (random_int(1, 100) === 1) {
        $database->exec(
            'DELETE FROM resolver_rate_limits WHERE window_started_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)'
        );
    }
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
        'SELECT reseller_id, reseller_name, host, access_code, status, created_at, updated_at '
        . 'FROM resellers WHERE reseller_id = :id LIMIT 1'
    );
    $statement->execute(['id' => $resellerId]);
    $reseller = $statement->fetch();
    if (!is_array($reseller) || ($reseller['status'] ?? '') !== HULK_ACTIVE_STATUS) {
        unset($_SESSION['reseller_id']);
        return null;
    }
    return $reseller;
}

function hulk_current_admin(): ?array
{
    $adminId = filter_var($_SESSION['admin_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$adminId) {
        return null;
    }
    $statement = hulk_db()->prepare(
        'SELECT admin_id, username, status FROM admins WHERE admin_id = :id LIMIT 1'
    );
    $statement->execute(['id' => $adminId]);
    $admin = $statement->fetch();
    if (!is_array($admin) || ($admin['status'] ?? '') !== HULK_ACTIVE_STATUS) {
        unset($_SESSION['admin_id']);
        return null;
    }
    return $admin;
}
