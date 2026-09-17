<?php

declare(strict_types=1);

function cc_csrf_tokens_match(string $expected, string $provided): bool
{
    return $expected !== '' && $provided !== '' && hash_equals($expected, $provided);
}

function cc_dummy_password_hash(): string
{
    // Fixed bcrypt hash used only to keep unknown-user verification on the
    // same password_verify() path. It is not a credential or an account hash.
    return '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi';
}

function cc_login_record_accepts_password(mixed $record, string $password, int $now): bool
{
    $hash = is_array($record) ? (string) ($record['password_hash'] ?? '') : cc_dummy_password_hash();
    $passwordMatches = $hash !== '' && password_verify($password, $hash);

    return cc_login_record_is_valid($record, $passwordMatches, $now);
}

function cc_admin_record_matches_session(int $sessionId, string $sessionUsername, mixed $record): bool
{
    if (
        $sessionId < 1 ||
        $sessionUsername === '' ||
        !is_array($record) ||
        !(bool) ($record['enabled'] ?? false)
    ) {
        return false;
    }

    $recordId = filter_var($record['id'] ?? null, FILTER_VALIDATE_INT);
    $recordUsername = $record['username'] ?? null;

    return $recordId !== false &&
        (int) $recordId === $sessionId &&
        is_string($recordUsername) &&
        hash_equals($sessionUsername, $recordUsername);
}

function cc_login_record_is_valid(mixed $record, bool $passwordMatches, int $now): bool
{
    if (!is_array($record) || !(bool) ($record['enabled'] ?? false) || !$passwordMatches) {
        return false;
    }

    $lockedUntil = !empty($record['locked_until'])
        ? strtotime((string) $record['locked_until'])
        : 0;

    return $lockedUntil !== false && $lockedUntil <= $now;
}

function cc_next_login_attempt_state(
    int $sessionAttempts,
    int $databaseAttempts,
    int $maximumAttempts,
    int $lockSeconds,
    int $now
): array {
    $maximumAttempts = max(3, $maximumAttempts);
    $lockSeconds = max(60, $lockSeconds);
    $nextSessionAttempts = max(0, $sessionAttempts) + 1;
    $nextDatabaseAttempts = max(0, $databaseAttempts) + 1;

    return [
        'session_attempts' => $nextSessionAttempts,
        'session_locked_until' => $nextSessionAttempts >= $maximumAttempts ? $now + $lockSeconds : 0,
        'database_attempts' => $nextDatabaseAttempts,
        'database_locked_until' => $nextDatabaseAttempts >= $maximumAttempts
            ? date('Y-m-d H:i:s', $now + $lockSeconds)
            : null,
    ];
}

function cc_security_headers(bool $https): array
{
    $headers = [
        'Cache-Control' => 'no-store',
        'Content-Security-Policy' => "default-src 'self'; base-uri 'none'; connect-src 'self'; font-src 'self'; form-action 'self'; frame-ancestors 'none'; img-src 'self' data:; object-src 'none'; script-src 'self'; style-src 'self'",
        'Cross-Origin-Opener-Policy' => 'same-origin',
        'Permissions-Policy' => 'camera=(), geolocation=(), microphone=(), payment=(), usb=()',
        'Referrer-Policy' => 'no-referrer',
        'X-Content-Type-Options' => 'nosniff',
        'X-Frame-Options' => 'DENY',
    ];

    if ($https) {
        $headers['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains';
    }

    return $headers;
}

function cc_session_cookie_options(string $baseUrl): array
{
    $path = rtrim((string) (parse_url($baseUrl, PHP_URL_PATH) ?: ''), '/') . '/';

    return [
        'lifetime' => 0,
        'path' => $path,
        'domain' => '',
        'secure' => true,
        'httponly' => true,
        'samesite' => 'Strict',
    ];
}
