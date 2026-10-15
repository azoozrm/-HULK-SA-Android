<?php

declare(strict_types=1);

require_once __DIR__ . '/host-health.php';

/**
 * HULK SA Control Center — TV/Mobile pairing backend.
 *
 * Local implementation of the R03 contract as corrected by the backend R01 binding
 * corrections. The feature is disabled by default; nothing in this file enables it.
 *
 * Layering:
 *  - pure helpers (config normalization, crypto, tokens, validation, state labels)
 *    are testable without a database;
 *  - database functions accept a PDO connection and are exercised only when a real
 *    database is available (local lab has none: marked BLOCKED in evidence).
 */

const CC_PAIRING_CONTRACT_VERSION = 1;
const CC_PAIRING_MAX_BODY_BYTES = 4096;
const CC_PAIRING_PAGE_MAX_BODY_BYTES = 16384;
const CC_PAIRING_PROVIDER_BODY_LIMIT = 65536;
const CC_PAIRING_MAINTENANCE_SECONDS = 45;
const CC_PAIRING_MAINTENANCE_FINALIZER_ALLOWANCE_SECONDS = 0.5;
const CC_PAIRING_MAINTENANCE_LOCK = 'hulk_pairing_cleanup';
const CC_PAIRING_USER_CODE_PREFIX = 'HULKTV-';
const CC_PAIRING_USER_CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const CC_PAIRING_USER_CODE_LENGTH = 6;
const CC_PAIRING_USER_CODE_ATTEMPTS = 5;
const CC_PAIRING_MAX_LINE_LENGTH = 8;
const CC_PAIRING_ENVELOPE_VERSION_SODIUM = 1;
const CC_PAIRING_ENVELOPE_VERSION_OPENSSL = 2;
const CC_PAIRING_CLEANUP_BATCH = 200;
const CC_PAIRING_CLEANUP_DRAIN_BATCHES = 25;
const CC_PAIRING_CAPACITY_RETRY_SECONDS = 60;

const CC_PAIRING_TERMINAL_STATES = ['ACKED', 'DENIED', 'CANCELLED', 'EXPIRED'];

class CcPairingException extends RuntimeException
{
    public function __construct(
        public readonly string $apiCode,
        public readonly int $httpStatus,
        string $message,
        public readonly ?int $retryAfterSeconds = null,
    ) {
        parent::__construct($message);
    }
}

/** Internal control-flow signal: the next statement cannot fit the budget. */
final class CcPairingBudgetExceeded extends RuntimeException
{
}

/**
 * One absolute maintenance budget carried from setup through every SQL dispatch.
 *
 * With a verified server-side statement bound B (MariaDB `max_statement_time`), a
 * statement may only start when its own bound plus the reserved summary and a small
 * finalizer allowance still fit before the deadline. The reserve is a SOFT
 * scheduling allowance (MariaDB timeout checks are cooperative and may run past
 * `max_statement_time`); the tool's supervisor provides the hard whole-invocation
 * wall-clock bound and a proven connection-close fallback for release. Work that
 * cannot fit is refused instead of overrunning.
 */
final class CcPairingBudget
{
    private bool $workRefused = false;
    private bool $summaryRefused = false;
    private bool $finalizerRefused = false;
    private readonly Closure $clock;

    public function __construct(
        private readonly float $deadlineAt,
        private readonly float $statementBoundSeconds,
        ?callable $clock = null,
    ) {
        $this->clock = Closure::fromCallable($clock ?? static fn (): float => microtime(true));
    }

    public function remaining(): float
    {
        return $this->deadlineAt - ($this->clock)();
    }

    public function statementBoundSeconds(): float
    {
        return $this->statementBoundSeconds;
    }

    private function finalizerAllowance(): float
    {
        return CC_PAIRING_MAINTENANCE_FINALIZER_ALLOWANCE_SECONDS;
    }

    private function summaryReserve(): float
    {
        return $this->statementBoundSeconds + $this->finalizerAllowance();
    }

    /** True when one more work statement (its bound + summary + finalizer) still fits. */
    public function allowWork(int $statements = 1): bool
    {
        if ($this->remaining() >= $statements * $this->statementBoundSeconds + $this->summaryReserve()) {
            return true;
        }
        $this->workRefused = true;
        return false;
    }

    /** True when the single combined summary query plus finalizer still fits. */
    public function allowSummary(): bool
    {
        if ($this->remaining() >= $this->summaryReserve()) {
            return true;
        }
        $this->summaryRefused = true;
        return false;
    }

    /**
     * True when a RELEASE_LOCK dispatch may be attempted inside the budget. This is
     * only a soft allowance; when it refuses, the caller must take the proven
     * connection-close fallback and report that mechanism instead of "released".
     */
    public function allowFinalizer(): bool
    {
        if ($this->remaining() >= $this->finalizerAllowance()) {
            return true;
        }
        $this->finalizerRefused = true;
        return false;
    }

    public function workRefused(): bool
    {
        return $this->workRefused;
    }

    public function summaryRefused(): bool
    {
        return $this->summaryRefused;
    }

    public function finalizerRefused(): bool
    {
        return $this->finalizerRefused;
    }
}

function cc_pairing_budget(float $startedAt, float $seconds, ?callable $clock = null, float $statementBoundSeconds = 5.0): CcPairingBudget
{
    return new CcPairingBudget($startedAt + max(1.0, $seconds), $statementBoundSeconds, $clock);
}

/* ---------------------------------------------------------------------------
 * Configuration and fresh enable authority
 * ------------------------------------------------------------------------- */

function cc_pairing_config_path(): string
{
    $external = trim((string) getenv('HULK_CONTROL_CENTER_CONFIG'));
    return $external !== '' ? $external : dirname(__DIR__) . '/config.php';
}

/**
 * Reads the private configuration. $fresh bypasses any request-level caching so the
 * authoritative pairing.enabled value is re-read at approval commit and delivery
 * authorization (R03 fresh-authority requirement). Only the same private config path
 * is ever read; an unrelated config source is never consulted.
 *
 * @return array{enabled: bool, block: array}
 */
function cc_pairing_authority(bool $fresh = true): array
{
    static $cache = null;
    if (!$fresh && is_array($cache)) {
        return $cache;
    }

    $path = cc_pairing_config_path();
    if (!is_file($path)) {
        return ['enabled' => false, 'block' => [], 'presence_block' => []];
    }
    $loaded = @include $path;
    $block = is_array($loaded) && is_array($loaded['pairing'] ?? null) ? $loaded['pairing'] : [];
    $presenceBlock = is_array($loaded) && is_array($loaded['presence'] ?? null) ? $loaded['presence'] : [];
    $result = [
        'enabled' => ($block['enabled'] ?? false) === true,
        'block' => $block,
        'presence_block' => $presenceBlock,
    ];
    if (!$fresh) {
        $cache = $result;
    }
    return $result;
}

function cc_pairing_enabled(): bool
{
    try {
        return cc_pairing_authority(true)['enabled'];
    } catch (Throwable) {
        return false;
    }
}

/**
 * Normalized pairing settings with defaults. Values only take effect when the
 * feature is enabled and readiness passes.
 */
function cc_pairing_settings(?array $block = null): array
{
    $block ??= cc_pairing_authority(false)['block'];
    $rateLimits = is_array($block['rate_limits'] ?? null) ? $block['rate_limits'] : [];
    $envelopeKeys = is_array($block['envelope_keys'] ?? null) ? $block['envelope_keys'] : [];

    return [
        'enabled' => ($block['enabled'] ?? false) === true,
        'ttl_seconds' => (int) ($block['ttl_seconds'] ?? 600),
        'retention_seconds' => (int) ($block['retention_seconds'] ?? 86400),
        'max_retained_rows' => (int) ($block['max_retained_rows'] ?? 5000),
        'max_deliveries' => (int) ($block['max_deliveries'] ?? 20),
        'poll_interval_seconds' => (int) ($block['poll_interval_seconds'] ?? 3),
        'claim_secret' => (string) ($block['claim_secret'] ?? ''),
        'rate_limit_secret' => (string) ($block['rate_limit_secret'] ?? ''),
        'envelope_keys' => $envelopeKeys,
        'active_envelope_key_id' => (string) ($block['active_envelope_key_id'] ?? ''),
        'rate_limits' => [
            'create_minute_per_ip' => (int) ($rateLimits['create_minute_per_ip'] ?? 30),
            'create_hour_per_ip' => (int) ($rateLimits['create_hour_per_ip'] ?? 300),
            'claim_minute_per_request' => (int) ($rateLimits['claim_minute_per_request'] ?? 40),
            'claim_minute_per_ip' => (int) ($rateLimits['claim_minute_per_ip'] ?? 600),
            'ack_minute_per_request' => (int) ($rateLimits['ack_minute_per_request'] ?? 10),
            'cancel_minute_per_request' => (int) ($rateLimits['cancel_minute_per_request'] ?? 10),
            'page_lookup_minute_per_ip' => (int) ($rateLimits['page_lookup_minute_per_ip'] ?? 30),
            'page_approve_minute_per_ip' => (int) ($rateLimits['page_approve_minute_per_ip'] ?? 20),
            'page_approve_minute_per_request' => (int) ($rateLimits['page_approve_minute_per_request'] ?? 10),
        ],
    ];
}

function cc_pairing_b64_decode_strict(string $value): ?string
{
    $trimmed = trim($value);
    if ($trimmed === '') {
        return null;
    }
    $decoded = base64_decode($trimmed, true);
    return $decoded === false ? null : $decoded;
}

/**
 * Canonical unpadded base64url decoding for TV wire values (client secrets).
 * Rejects standard-base64 characters, padding, wrong lengths and noncanonical
 * trailing bits. Config keys keep using cc_pairing_b64_decode_strict (standard
 * base64, separate contract).
 */
function cc_pairing_b64url_decode_strict(string $value, int $expectedLength = 43, int $expectedBytes = 32): ?string
{
    if (strlen($value) !== $expectedLength || preg_match('/^[A-Za-z0-9_-]+$/D', $value) !== 1) {
        return null;
    }
    $standard = strtr($value, '-_', '+/');
    $decoded = base64_decode($standard . '=', true);
    if ($decoded === false || strlen($decoded) !== $expectedBytes) {
        return null;
    }
    $canonical = rtrim(strtr(base64_encode($decoded), '+/', '-_'), '=');
    if (!hash_equals($canonical, $value)) {
        return null;
    }
    return $decoded;
}

/**
 * Readiness: feature enabled AND every key/secret/budget valid AND required crypto
 * present AND no duplicated material (including Presence secret reuse). Any failure
 * keeps credential handling closed (no plaintext fallback of any kind).
 *
 * @return array{ready: bool, errors: list<string>}
 */
function cc_pairing_readiness(array $settings, ?array $presenceBlock = null): array
{
    $errors = [];

    if ($settings['ttl_seconds'] < 300 || $settings['ttl_seconds'] > 900) {
        $errors[] = 'TTL_OUT_OF_RANGE';
    }
    if ($settings['retention_seconds'] < 3600 || $settings['retention_seconds'] > 2592000) {
        $errors[] = 'RETENTION_OUT_OF_RANGE';
    }
    if ($settings['max_retained_rows'] < 100 || $settings['max_retained_rows'] > 100000) {
        $errors[] = 'CAPACITY_OUT_OF_RANGE';
    }
    if ($settings['max_deliveries'] < 1 || $settings['max_deliveries'] > 100) {
        $errors[] = 'DELIVERY_BOUND_OUT_OF_RANGE';
    }

    $claimSecret = cc_pairing_b64_decode_strict((string) $settings['claim_secret']);
    if ($claimSecret === null || strlen($claimSecret) < 32) {
        $errors[] = 'CLAIM_SECRET_INVALID';
    }
    $rateSecret = cc_pairing_b64_decode_strict((string) $settings['rate_limit_secret']);
    if ($rateSecret === null || strlen($rateSecret) < 32) {
        $errors[] = 'RATE_LIMIT_SECRET_INVALID';
    }

    $decodedKeys = [];
    $envelopeKeys = is_array($settings['envelope_keys']) ? $settings['envelope_keys'] : [];
    if ($envelopeKeys === []) {
        $errors[] = 'ENVELOPE_KEYS_MISSING';
    }
    foreach ($envelopeKeys as $kid => $encoded) {
        if (!is_string($kid) || preg_match('/^[A-Za-z0-9_-]{1,8}$/D', $kid) !== 1) {
            $errors[] = 'ENVELOPE_KEY_ID_INVALID';
            continue;
        }
        $key = is_string($encoded) ? cc_pairing_b64_decode_strict($encoded) : null;
        if ($key === null || strlen($key) !== 32) {
            $errors[] = 'ENVELOPE_KEY_INVALID';
            continue;
        }
        $decodedKeys[] = $key;
    }
    if (!is_string($settings['active_envelope_key_id']) || !isset($envelopeKeys[$settings['active_envelope_key_id']])) {
        $errors[] = 'ENVELOPE_ACTIVE_KEY_MISSING';
    }

    // Independent material: no duplicated decoded secret/key bytes anywhere.
    $material = array_filter([$claimSecret, $rateSecret], 'is_string');
    foreach ($decodedKeys as $key) {
        $material[] = $key;
    }
    $seen = [];
    foreach ($material as $entry) {
        if (isset($seen[$entry])) {
            $errors[] = 'DUPLICATE_KEY_MATERIAL';
            break;
        }
        $seen[$entry] = true;
    }

    // Forbidden reuse of Presence secrets (same private config file, scoped read).
    $presenceBlock ??= cc_pairing_authority(false)['presence_block'];
    if (is_array($presenceBlock)) {
        foreach (['rate_limit_secret', 'token_secret'] as $presenceKey) {
            $presenceSecret = is_string($presenceBlock[$presenceKey] ?? null)
                ? cc_pairing_b64_decode_strict((string) $presenceBlock[$presenceKey])
                : null;
            if ($presenceSecret !== null && $presenceSecret !== '') {
                foreach ($material as $entry) {
                    if (hash_equals($presenceSecret, $entry)) {
                        $errors[] = 'PRESENCE_SECRET_REUSE';
                        break 2;
                    }
                }
            }
        }
    }

    // Required crypto: sodium XChaCha20-Poly1305 or OpenSSL AES-256-GCM, plus randomness.
    $cryptoReady = (function_exists('sodium_crypto_aead_xchacha20poly1305_ietf_encrypt')
            && function_exists('sodium_crypto_aead_xchacha20poly1305_ietf_decrypt'))
        || (function_exists('openssl_encrypt') && function_exists('openssl_decrypt'));
    if (!$cryptoReady || !function_exists('random_bytes') || !function_exists('hash_hmac')) {
        $errors[] = 'CRYPTO_UNAVAILABLE';
    }

    foreach ($settings['rate_limits'] as $budget) {
        if ((int) $budget < 1) {
            $errors[] = 'RATE_BUDGET_INVALID';
            break;
        }
    }

    return ['ready' => $errors === [], 'errors' => array_values(array_unique($errors))];
}

/**
 * Full gate used by TV API handlers and page actions. Fails closed with a controlled
 * code; disabled and not-ready are distinct truths.
 */
function cc_pairing_require_active(array $settings): void
{
    if (!$settings['enabled']) {
        throw new CcPairingException('PAIRING_DISABLED', 404, 'Pairing is disabled.');
    }
    if (!cc_pairing_readiness($settings)['ready']) {
        throw new CcPairingException('PAIRING_UNAVAILABLE', 503, 'Pairing is not ready.');
    }
}

/* ---------------------------------------------------------------------------
 * Crypto: AEAD envelope and derived tokens
 * ------------------------------------------------------------------------- */

function cc_pairing_envelope_aad(string $requestId, string $expiresAtIso, string $kid): string
{
    return "HULKPAIR1\x00" . $requestId . "\x00" . $expiresAtIso . "\x00" . $kid;
}

function cc_pairing_resolve_envelope_key(array $settings, string $kid): ?string
{
    $encoded = $settings['envelope_keys'][$kid] ?? null;
    if (!is_string($encoded)) {
        return null;
    }
    $key = cc_pairing_b64_decode_strict($encoded);
    return $key !== null && strlen($key) === 32 ? $key : null;
}

/**
 * @param array{accessCode: string, username: string, password: string} $credentials
 * @return array{version: int, key_id: string, blob: string}
 */
function cc_pairing_seal_credentials(array $credentials, string $requestId, string $expiresAtIso, array $settings): array
{
    $kid = (string) $settings['active_envelope_key_id'];
    $key = cc_pairing_resolve_envelope_key($settings, $kid);
    if ($key === null) {
        throw new CcPairingException('PAIRING_UNAVAILABLE', 503, 'Pairing envelope key is unavailable.');
    }
    $plaintext = json_encode([
        'v' => 1,
        'accessCode' => (string) $credentials['accessCode'],
        'username' => (string) $credentials['username'],
        'password' => (string) $credentials['password'],
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    if (strlen($plaintext) > 768) {
        throw new CcPairingException('INVALID_REQUEST', 400, 'Credential payload is too large.');
    }

    $aad = cc_pairing_envelope_aad($requestId, $expiresAtIso, $kid);
    if (function_exists('sodium_crypto_aead_xchacha20poly1305_ietf_encrypt')) {
        $nonce = random_bytes(24);
        $cipher = sodium_crypto_aead_xchacha20poly1305_ietf_encrypt($plaintext, $aad, $nonce, $key);
        return [
            'version' => CC_PAIRING_ENVELOPE_VERSION_SODIUM,
            'key_id' => $kid,
            'blob' => chr(CC_PAIRING_ENVELOPE_VERSION_SODIUM) . $nonce . $cipher,
        ];
    }
    if (function_exists('openssl_encrypt')) {
        $nonce = random_bytes(12);
        $tag = '';
        $cipher = openssl_encrypt($plaintext, 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $nonce, $tag, $aad, 16);
        if (!is_string($cipher) || strlen($tag) !== 16) {
            throw new CcPairingException('PAIRING_UNAVAILABLE', 503, 'Pairing envelope encryption failed.');
        }
        return [
            'version' => CC_PAIRING_ENVELOPE_VERSION_OPENSSL,
            'key_id' => $kid,
            'blob' => chr(CC_PAIRING_ENVELOPE_VERSION_OPENSSL) . $nonce . $tag . $cipher,
        ];
    }
    throw new CcPairingException('PAIRING_UNAVAILABLE', 503, 'Pairing envelope crypto is unavailable.');
}

/**
 * Returns null for any tamper/AAD/key mismatch; callers treat null as ENVELOPE_INVALID.
 *
 * @return array{accessCode: string, username: string, password: string}|null
 */
function cc_pairing_unseal_credentials(
    string $blob,
    string $requestId,
    string $expiresAtIso,
    array $settings,
    ?int $version = null,
    ?string $kid = null,
): ?array {
    if ($blob === '' || $version === null || $kid === null) {
        return null;
    }
    $key = cc_pairing_resolve_envelope_key($settings, $kid);
    if ($key === null) {
        return null;
    }
    $aad = cc_pairing_envelope_aad($requestId, $expiresAtIso, $kid);

    try {
        if ($version === CC_PAIRING_ENVELOPE_VERSION_SODIUM) {
            if (strlen($blob) < 25 || !function_exists('sodium_crypto_aead_xchacha20poly1305_ietf_decrypt')) {
                return null;
            }
            $nonce = substr($blob, 1, 24);
            $cipher = substr($blob, 25);
            $plaintext = sodium_crypto_aead_xchacha20poly1305_ietf_decrypt($cipher, $aad, $nonce, $key);
        } elseif ($version === CC_PAIRING_ENVELOPE_VERSION_OPENSSL) {
            if (strlen($blob) < 29 || !function_exists('openssl_decrypt')) {
                return null;
            }
            $nonce = substr($blob, 1, 12);
            $tag = substr($blob, 13, 16);
            $cipher = substr($blob, 29);
            $plaintext = openssl_decrypt($cipher, 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $nonce, $tag, $aad);
        } else {
            return null;
        }
    } catch (Throwable) {
        return null;
    }
    if (!is_string($plaintext)) {
        return null;
    }

    $decoded = json_decode($plaintext, true);
    if (!is_array($decoded) || ($decoded['v'] ?? null) !== 1) {
        return null;
    }
    $accessCode = $decoded['accessCode'] ?? null;
    $username = $decoded['username'] ?? null;
    $password = $decoded['password'] ?? null;
    if (!is_string($accessCode) || !is_string($username) || !is_string($password) || $password === '') {
        return null;
    }
    return ['accessCode' => $accessCode, 'username' => $username, 'password' => $password];
}

function cc_pairing_claim_token(string $claimSecret, string $requestId, string $clientSecret): string
{
    $key = cc_pairing_b64_decode_strict($claimSecret) ?? '';
    $mac = hash_hmac('sha256', $requestId . "\x00" . $clientSecret, $key, true);
    return rtrim(strtr(base64_encode($mac), '+/', '-_'), '=');
}

function cc_pairing_token_hash(string $token): string
{
    return hash('sha256', $token);
}

function cc_pairing_host_fingerprint(string $normalizedHost): string
{
    return hash('sha256', $normalizedHost);
}

/* ---------------------------------------------------------------------------
 * Request generation and validation (pure)
 * ------------------------------------------------------------------------- */

function cc_pairing_uuid4(): string
{
    $bytes = random_bytes(16);
    $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40);
    $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80);
    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($bytes), 4));
}

function cc_pairing_is_uuid(string $value): bool
{
    return preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/D', $value) === 1;
}

function cc_pairing_generate_user_code(?callable $randomIndex = null): string
{
    $alphabet = CC_PAIRING_USER_CODE_ALPHABET;
    $payload = '';
    for ($i = 0; $i < CC_PAIRING_USER_CODE_LENGTH; $i++) {
        $index = $randomIndex === null ? random_int(0, strlen($alphabet) - 1) : (int) $randomIndex(strlen($alphabet));
        $payload .= $alphabet[$index];
    }
    return CC_PAIRING_USER_CODE_PREFIX . $payload;
}

function cc_pairing_normalize_user_code(string $value): ?string
{
    $compact = strtoupper((string) preg_replace('/[\s-]+/', '', trim($value)));
    if (!str_starts_with($compact, 'HULKTV')) {
        return null;
    }
    $payload = substr($compact, strlen('HULKTV'));
    if (strlen($payload) !== CC_PAIRING_USER_CODE_LENGTH) {
        return null;
    }
    if (preg_match('/^[' . CC_PAIRING_USER_CODE_ALPHABET . ']{' . CC_PAIRING_USER_CODE_LENGTH . '}$/D', $payload) !== 1) {
        return null;
    }
    return CC_PAIRING_USER_CODE_PREFIX . $payload;
}

function cc_pairing_clean_text(mixed $value, int $maximum): string
{
    if (!is_string($value)) {
        return '';
    }
    $trimmed = trim($value);
    if (strlen($trimmed) > $maximum || preg_match('/[\x00-\x1F\x7F]/', $trimmed) === 1) {
        return '';
    }
    return $trimmed;
}

/**
 * Strict optional metadata: absent/null becomes '', but a present value must be a
 * valid string within bounds. Invalid fields are rejected, never silently blanked.
 */
function cc_pairing_optional_text(mixed $value, int $maximum, string $field): string
{
    if ($value === null || $value === '') {
        return '';
    }
    if (!is_string($value)) {
        throw new CcPairingException('INVALID_REQUEST', 400, 'Invalid ' . $field . '.');
    }
    $trimmed = trim($value);
    if (strlen($trimmed) > $maximum || preg_match('/[\x00-\x1F\x7F]/', $trimmed) === 1) {
        throw new CcPairingException('INVALID_REQUEST', 400, 'Invalid ' . $field . '.');
    }
    return $trimmed;
}

/** Scoped PHP host label (host and optional port only). */
function cc_pairing_host_display_label(string $normalizedHost): string
{
    $parts = parse_url($normalizedHost);
    if (!is_array($parts) || !isset($parts['host'])) {
        return '';
    }
    $host = (string) $parts['host'];
    $port = isset($parts['port']) ? (int) $parts['port'] : null;
    return $port === null ? $host : $host . ':' . $port;
}

/**
 * @return array{clientRequestId: string, clientSecret: string, installationId: string, device: array}
 */
function cc_pairing_validate_create_input(array $payload): array
{
    $allowed = ['contractVersion', 'clientRequestId', 'clientSecret', 'installationId', 'device', 'requestedAtEpochMs'];
    foreach ($payload as $key => $_) {
        if (!in_array((string) $key, $allowed, true)) {
            throw new CcPairingException('INVALID_REQUEST', 400, 'Unexpected request field.');
        }
    }
    if (($payload['contractVersion'] ?? null) !== CC_PAIRING_CONTRACT_VERSION) {
        throw new CcPairingException('CONTRACT_VERSION_UNSUPPORTED', 409, 'Unsupported contract version.');
    }
    $clientRequestId = is_string($payload['clientRequestId'] ?? null) ? $payload['clientRequestId'] : '';
    if (!cc_pairing_is_uuid($clientRequestId)) {
        throw new CcPairingException('INVALID_REQUEST', 400, 'clientRequestId must be a UUID v4.');
    }
    $clientSecret = is_string($payload['clientSecret'] ?? null) ? $payload['clientSecret'] : '';
    if (cc_pairing_b64url_decode_strict($clientSecret, 43, 32) === null) {
        throw new CcPairingException('INVALID_REQUEST', 400, 'clientSecret must be canonical unpadded base64url of 32 random bytes.');
    }
    $installationId = is_string($payload['installationId'] ?? null) ? $payload['installationId'] : '';
    if (!cc_pairing_is_uuid($installationId)) {
        throw new CcPairingException('INVALID_REQUEST', 400, 'installationId must be a UUID v4.');
    }
    if (array_key_exists('requestedAtEpochMs', $payload) && !is_int($payload['requestedAtEpochMs'])) {
        throw new CcPairingException('INVALID_REQUEST', 400, 'requestedAtEpochMs must be an integer.');
    }
    $deviceInput = $payload['device'] ?? null;
    if ($deviceInput !== null && !is_array($deviceInput)) {
        throw new CcPairingException('INVALID_REQUEST', 400, 'device must be an object.');
    }
    $deviceInput = is_array($deviceInput) ? $deviceInput : [];
    foreach ($deviceInput as $key => $_) {
        if (!in_array((string) $key, ['platform', 'manufacturer', 'model', 'appVersion'], true)) {
            throw new CcPairingException('INVALID_REQUEST', 400, 'Unexpected device field.');
        }
    }
    if (($deviceInput['platform'] ?? null) !== 'TV') {
        throw new CcPairingException('INVALID_REQUEST', 400, 'device.platform must be TV.');
    }
    $device = [
        'platform' => 'TV',
        'manufacturer' => cc_pairing_optional_text($deviceInput['manufacturer'] ?? null, 64, 'device.manufacturer'),
        'model' => cc_pairing_optional_text($deviceInput['model'] ?? null, 64, 'device.model'),
        'appVersion' => cc_pairing_optional_text($deviceInput['appVersion'] ?? null, 32, 'device.appVersion'),
    ];

    return [
        'clientRequestId' => $clientRequestId,
        'clientSecret' => $clientSecret,
        'installationId' => $installationId,
        'device' => $device,
    ];
}

function cc_pairing_validate_credentials_input(string $accessCode, string $username, string $password): array
{
    if (
        $accessCode === '' || strlen($accessCode) > 64
        || $username === '' || strlen($username) > 255 || preg_match('/[\x00-\x1F\x7F]/', $username) === 1
        || $password === '' || strlen($password) > 255 || preg_match('/[\x00-\x1F\x7F]/', $password) === 1
    ) {
        throw new CcPairingException('INVALID_REQUEST', 400, 'Invalid credential input.');
    }
    return ['accessCode' => $accessCode, 'username' => $username, 'password' => $password];
}

function cc_pairing_validate_session_payload(array $payload): string
{
    $allowed = ['contractVersion', 'requestId'];
    foreach ($payload as $key => $_) {
        if (!in_array((string) $key, $allowed, true)) {
            throw new CcPairingException('INVALID_REQUEST', 400, 'Unexpected request field.');
        }
    }
    if (($payload['contractVersion'] ?? null) !== CC_PAIRING_CONTRACT_VERSION) {
        throw new CcPairingException('CONTRACT_VERSION_UNSUPPORTED', 409, 'Unsupported contract version.');
    }
    $requestId = is_string($payload['requestId'] ?? null) ? $payload['requestId'] : '';
    if (!cc_pairing_is_uuid($requestId)) {
        throw new CcPairingException('INVALID_REQUEST', 400, 'requestId must be a UUID v4.');
    }
    return $requestId;
}

/* ---------------------------------------------------------------------------
 * Provider validation (Xtream parity, bounded SSRF-safe transport)
 * ------------------------------------------------------------------------- */

/**
 * Exact provider request URL: scheme-specific default port is omitted only when it
 * matches the scheme; any explicit port is preserved. Root hosts get a single-slash
 * `/player_api.php`; path prefixes keep their exact path and pin authority agrees
 * with the URL authority.
 *
 * @return array{target: string, resolve_host: string, port: int, hostname: string}|null
 */
function cc_pairing_provider_target(string $host, string $username, string $password): ?array
{
    $parts = parse_url($host);
    if (
        !is_array($parts)
        || !isset($parts['host'], $parts['scheme'])
        || isset($parts['user'])
        || isset($parts['pass'])
        || isset($parts['query'])
        || isset($parts['fragment'])
    ) {
        return null;
    }
    $scheme = strtolower((string) $parts['scheme']);
    if (!in_array($scheme, ['http', 'https'], true)) {
        return null;
    }
    $rawHost = (string) $parts['host'];
    $isIpv6 = str_contains($rawHost, ':');
    $hostname = trim($rawHost, '[]');
    if ($hostname === '') {
        return null;
    }
    $port = isset($parts['port']) ? (int) $parts['port'] : ($scheme === 'https' ? 443 : 80);
    if ($port < 1 || $port > 65535) {
        return null;
    }
    $path = (string) ($parts['path'] ?? '');
    $basePath = rtrim($path, '/');
    $apiPath = $basePath . '/player_api.php';
    $resolveHost = $isIpv6 ? '[' . $hostname . ']' : $hostname;
    $defaultPort = ($scheme === 'http' && $port === 80) || ($scheme === 'https' && $port === 443);
    $target = $scheme . '://' . $resolveHost . ($defaultPort ? '' : ':' . $port) . $apiPath
        . '?username=' . rawurlencode($username) . '&password=' . rawurlencode($password);
    return [
        'target' => $target,
        'resolve_host' => $resolveHost,
        'port' => $port,
        'hostname' => $hostname,
    ];
}

/**
 * Streaming response collector with a hard byte ceiling: overflow aborts the
 * transfer and clears the body (never an unbounded curl_exec buffer).
 *
 * @return array{write: callable, has_overflowed: callable, body: callable, written: callable}
 */
function cc_pairing_response_collector(int $limit = CC_PAIRING_PROVIDER_BODY_LIMIT): array
{
    $body = '';
    $overflowed = false;
    $written = 0;
    return [
        'write' => static function ($handle, string $chunk) use (&$body, &$overflowed, &$written, $limit): int {
            if ($overflowed) {
                return 0;
            }
            $written += strlen($chunk);
            if ($written > $limit) {
                $overflowed = true;
                $body = '';
                return 0;
            }
            $body .= $chunk;
            return strlen($chunk);
        },
        'has_overflowed' => static function () use (&$overflowed): bool {
            return $overflowed;
        },
        'body' => static function () use (&$body): string {
            return $body;
        },
        'written' => static function () use (&$written): int {
            return $written;
        },
    ];
}

/**
 * @return array{result: string, http_status: ?int, reason: string}
 */
function cc_pairing_probe_provider(
    string $host,
    string $username,
    string $password,
    array $settings,
    ?callable $dnsLookup = null,
    ?callable $httpGet = null,
): array {
    $target = cc_pairing_provider_target($host, $username, $password);
    if ($target === null) {
        return ['result' => 'TARGET_POLICY_BLOCKED', 'http_status' => null, 'reason' => 'INVALID_TARGET'];
    }
    $hostname = $target['hostname'];
    $port = $target['port'];

    $addresses = [];
    if (filter_var($hostname, FILTER_VALIDATE_IP) !== false) {
        $addresses[] = $hostname;
    } else {
        $lookup = $dnsLookup !== null
            ? $dnsLookup($hostname, 2000)
            : cc_host_health_dns_lookup($hostname, 2000);
        if (($lookup['status'] ?? '') !== 'ok' || !is_array($lookup['addresses'] ?? null)) {
            if (($lookup['status'] ?? '') === 'unavailable') {
                return ['result' => 'PAIRING_UNAVAILABLE', 'http_status' => null, 'reason' => 'DNS_RUNTIME_UNAVAILABLE'];
            }
            return ['result' => 'PROVIDER_UNREACHABLE', 'http_status' => null, 'reason' => 'DNS_FAILURE'];
        }
        $addresses = $lookup['addresses'];
    }
    if ($addresses === []) {
        return ['result' => 'PROVIDER_UNREACHABLE', 'http_status' => null, 'reason' => 'DNS_FAILURE'];
    }
    foreach ($addresses as $address) {
        if (!is_string($address) || !cc_host_health_public_ip($address)) {
            return ['result' => 'TARGET_POLICY_BLOCKED', 'http_status' => null, 'reason' => 'NON_PUBLIC_ADDRESS'];
        }
    }
    sort($addresses, SORT_STRING);
    $pinned = str_contains($addresses[0], ':') ? '[' . $addresses[0] . ']' : $addresses[0];
    $resolve = $target['resolve_host'] . ':' . $port . ':' . $pinned;

    $response = $httpGet !== null
        ? $httpGet($target['target'], $resolve, $settings)
        : cc_pairing_default_http_get($target['target'], $resolve, $settings);
    if (!is_array($response)) {
        return ['result' => 'PROVIDER_UNREACHABLE', 'http_status' => null, 'reason' => 'TRANSPORT_FAILURE'];
    }
    if (($response['overflowed'] ?? false) === true) {
        return ['result' => 'SERVICE_BLOCKED', 'http_status' => null, 'reason' => 'RESPONSE_TOO_LARGE'];
    }
    $errno = (int) ($response['errno'] ?? 1);
    if ($errno !== 0) {
        return ['result' => 'PROVIDER_UNREACHABLE', 'http_status' => null, 'reason' => 'TRANSPORT_ERROR_' . $errno];
    }
    $status = (int) ($response['status'] ?? 0);
    $body = (string) ($response['body'] ?? '');
    if (strlen($body) > CC_PAIRING_PROVIDER_BODY_LIMIT) {
        return ['result' => 'SERVICE_BLOCKED', 'http_status' => $status, 'reason' => 'RESPONSE_TOO_LARGE'];
    }
    // Match Xtream request acceptance: only 2xx responses may be parsed for
    // auth/status. A non-2xx body that still contains success JSON is NOT approval.
    if ($status < 200 || $status >= 300) {
        if (in_array($status, [401, 403], true)) {
            return ['result' => 'INVALID_CREDENTIALS', 'http_status' => $status, 'reason' => 'HTTP_AUTH_STATUS'];
        }
        return ['result' => 'SERVICE_BLOCKED', 'http_status' => $status, 'reason' => 'HTTP_STATUS_' . $status];
    }

    $decoded = json_decode($body, true);
    if (!is_array($decoded)) {
        return ['result' => 'SERVICE_BLOCKED', 'http_status' => $status, 'reason' => 'NON_JSON_RESPONSE'];
    }
    $user = $decoded['user_info'] ?? null;
    if (!is_array($user)) {
        return ['result' => 'SERVICE_BLOCKED', 'http_status' => $status, 'reason' => 'MISSING_USER_INFO'];
    }
    if ((int) ($user['auth'] ?? 0) !== 1) {
        return ['result' => 'INVALID_CREDENTIALS', 'http_status' => $status, 'reason' => 'AUTH_REJECTED'];
    }
    $accountStatus = trim((string) ($user['status'] ?? ''));
    if ($accountStatus !== '' && strcasecmp($accountStatus, 'Active') !== 0) {
        return ['result' => 'SUBSCRIPTION_INACTIVE', 'http_status' => $status, 'reason' => 'STATUS_INACTIVE'];
    }
    return ['result' => 'OK', 'http_status' => $status, 'reason' => 'ACTIVE'];
}

/**
 * @return array{errno: int, status: int, body: string, primary_ip: string, connect_time: float, overflowed: bool}
 */
function cc_pairing_default_http_get(string $url, string $resolve, array $settings): array
{
    $collector = cc_pairing_response_collector();
    $handle = curl_init($url);
    if ($handle === false) {
        return ['errno' => 1, 'status' => 0, 'body' => '', 'primary_ip' => '', 'connect_time' => 0.0, 'overflowed' => false];
    }
    curl_setopt_array($handle, [
        CURLOPT_FOLLOWLOCATION => false,
        CURLOPT_PROXY => '',
        CURLOPT_CONNECTTIMEOUT_MS => 3000,
        CURLOPT_TIMEOUT_MS => 8000,
        CURLOPT_NOSIGNAL => true,
        CURLOPT_PROTOCOLS => CURLPROTO_HTTP | CURLPROTO_HTTPS,
        CURLOPT_RESOLVE => [$resolve],
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_SSL_VERIFYHOST => 2,
        CURLOPT_USERAGENT => 'HULK-SA-Pairing/1.0',
        CURLOPT_HTTPHEADER => ['Accept: application/json'],
        CURLOPT_WRITEFUNCTION => $collector['write'],
    ]);
    curl_exec($handle);
    $errno = curl_errno($handle);
    $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    $primaryIp = (string) curl_getinfo($handle, CURLINFO_PRIMARY_IP);
    $connectTime = (float) curl_getinfo($handle, CURLINFO_CONNECT_TIME);
    curl_close($handle);
    $overflowed = $collector['has_overflowed']();
    return [
        'errno' => $overflowed ? 0 : $errno,
        'status' => $status,
        'body' => $overflowed ? '' : $collector['body'](),
        'primary_ip' => $primaryIp,
        'connect_time' => $connectTime,
        'overflowed' => $overflowed,
    ];
}

/* ---------------------------------------------------------------------------
 * Database helpers (pairing-owned bounded connections)
 * ------------------------------------------------------------------------- */

function cc_pairing_driver(PDO $db): string
{
    return strtolower((string) $db->getAttribute(PDO::ATTR_DRIVER_NAME));
}

/**
 * Pairing-owned connection built from the shared private config but independent of
 * cc_db()/Presence connections: 3 s connect timeout plus server-side lock and
 * statement deadlines applied at setup. Required deadline setup failures fail
 * closed (throw) instead of silently claiming a bound. Non-MySQL drivers (test
 * fixtures) skip server deadlines.
 */
function cc_pairing_database(string $authority, ?CcPairingBudget $budget = null): PDO
{
    static $connections = [];
    if (isset($connections[$authority]) && $connections[$authority] instanceof PDO) {
        return $connections[$authority];
    }
    if (!in_array($authority, ['control', 'reseller'], true)) {
        throw new InvalidArgumentException('Unknown pairing database authority.');
    }
    $database = cc_load_config()['databases'][$authority] ?? null;
    if (!is_array($database)) {
        throw new RuntimeException('Pairing database configuration is unavailable.');
    }
    // The connection itself is bounded by ATTR_TIMEOUT; the budget only starts it
    // when its verified statement bound plus finalization still fit.
    if ($budget !== null && !$budget->allowWork()) {
        throw new CcPairingBudgetExceeded('Maintenance budget cannot fit database setup.');
    }
    $options = is_array($database['options'] ?? null) ? $database['options'] : [];
    $options[PDO::ATTR_ERRMODE] = PDO::ERRMODE_EXCEPTION;
    $options[PDO::ATTR_DEFAULT_FETCH_MODE] = PDO::FETCH_ASSOC;
    $options[PDO::ATTR_EMULATE_PREPARES] = false;
    $options[PDO::ATTR_TIMEOUT] = 3;
    $options[PDO::ATTR_PERSISTENT] = false;
    $db = new PDO(
        (string) ($database['dsn'] ?? ''),
        (string) ($database['username'] ?? ''),
        (string) ($database['password'] ?? ''),
        $options
    );
    cc_pairing_apply_database_deadlines($db, $budget);
    return $connections[$authority] = $db;
}

function cc_pairing_db(): PDO
{
    return cc_pairing_database('control');
}

function cc_pairing_reseller_db(): PDO
{
    return cc_pairing_database('reseller');
}

/** Maintenance uses the same bounded pairing-owned control connection. */
function cc_pairing_maintenance_database(?CcPairingBudget $budget = null): PDO
{
    return cc_pairing_database('control', $budget);
}

/**
 * Pure deadline plan for a detected flavor. MariaDB supports a generic statement
 * time bound (`max_statement_time`, applies to reads and DML). MySQL's
 * `max_execution_time` bounds read-only SELECTs only, so MySQL cannot provide the
 * bounded-DML contract pairing requires: it fails closed explicitly instead of
 * advertising DML readiness because the SET succeeded.
 *
 * @return array{flavor: string, dml_bounded: bool, supported: bool, statements: list<string>, note: string, statement_bound_seconds: float}
 */
function cc_pairing_deadline_plan(string $driver, string $version): array
{
    if ($driver !== 'mysql') {
        return [
            'flavor' => $driver,
            'dml_bounded' => false,
            'supported' => false,
            'statements' => [],
            'note' => 'unsupported driver',
            'statement_bound_seconds' => 0.0,
        ];
    }
    if (stripos($version, 'mariadb') !== false) {
        return [
            'flavor' => 'mariadb',
            'dml_bounded' => true,
            'supported' => true,
            'statements' => [
                'SET SESSION innodb_lock_wait_timeout = 3',
                'SET SESSION lock_wait_timeout = 5',
                'SET SESSION max_statement_time = 5',
            ],
            'note' => 'mariadb: generic statement bound applies to reads and DML',
            'statement_bound_seconds' => 5.0,
        ];
    }
    return [
        'flavor' => 'mysql',
        'dml_bounded' => false,
        'supported' => false,
        'statements' => [
            'SET SESSION innodb_lock_wait_timeout = 3',
            'SET SESSION lock_wait_timeout = 5',
        ],
        'note' => 'mysql: max_execution_time bounds SELECT only; DML execution unbounded',
        'statement_bound_seconds' => 0.0,
    ];
}

/**
 * Applies the flavor plan. Unsupported flavors fail closed BEFORE any pairing work;
 * required deadline statements are NOT swallowed (a failure propagates).
 *
 * @return array{flavor: string, version: string, dml_bounded: bool, statements: list<string>, statement_bound_seconds: float}
 */
function cc_pairing_apply_database_deadlines(PDO $db, ?CcPairingBudget $budget = null): array
{
    $driver = cc_pairing_driver($db);
    $version = (string) $db->getAttribute(PDO::ATTR_SERVER_VERSION);
    $plan = cc_pairing_deadline_plan($driver, $version);
    if (!$plan['supported']) {
        throw new RuntimeException(
            'Pairing requires a database flavor with bounded DML statements (MariaDB); detected '
            . $plan['flavor'] . ' is unsupported.'
        );
    }
    foreach ($plan['statements'] as $statement) {
        if ($budget !== null && !$budget->allowWork()) {
            throw new CcPairingBudgetExceeded('Maintenance budget cannot fit deadline setup.');
        }
        $db->exec($statement);
    }
    return [
        'flavor' => $plan['flavor'],
        'version' => $version,
        'dml_bounded' => $plan['dml_bounded'],
        'statements' => $plan['statements'],
        'statement_bound_seconds' => $plan['statement_bound_seconds'],
    ];
}

function cc_pairing_connection_info(PDO $db): array
{
    $driver = cc_pairing_driver($db);
    $version = (string) $db->getAttribute(PDO::ATTR_SERVER_VERSION);
    $plan = cc_pairing_deadline_plan($driver, $version);
    return [
        'driver' => $driver,
        'flavor' => $plan['flavor'],
        'version' => $version,
        'dml_bounded' => $plan['dml_bounded'],
        'supported' => $plan['supported'],
        'statement_bound_seconds' => $plan['statement_bound_seconds'],
        'bounds' => array_map(
            static fn (string $statement): string => trim(str_replace('SET SESSION ', '', $statement)),
            $plan['statements']
        ),
        'note' => $plan['note'],
    ];
}

function cc_pairing_acquire_maintenance_lock(PDO $db): bool
{
    if (cc_pairing_driver($db) !== 'mysql') {
        return true;
    }
    $statement = $db->prepare('SELECT GET_LOCK(:name, 0)');
    $statement->execute(['name' => CC_PAIRING_MAINTENANCE_LOCK]);
    return (int) $statement->fetchColumn() === 1;
}

/**
 * Attempts RELEASE_LOCK and reports the ACTUAL outcome instead of swallowing it.
 * Returns one of: `released` (server confirmed), `not_held` (lock was not held by
 * this connection), `refused` (budget cannot fit the dispatch; caller must use the
 * connection-close fallback) or `failed` (statement/connection error; unconfirmed).
 */
function cc_pairing_release_maintenance_lock(PDO $db, ?CcPairingBudget $budget = null): string
{
    if (cc_pairing_driver($db) !== 'mysql') {
        return 'not_held';
    }
    if ($budget !== null && !$budget->allowFinalizer()) {
        return 'refused';
    }
    try {
        $statement = $db->prepare('SELECT RELEASE_LOCK(:name)');
        $statement->execute(['name' => CC_PAIRING_MAINTENANCE_LOCK]);
        $value = $statement->fetchColumn();
        if ($value === false || $value === null) {
            return 'not_held';
        }
        return ((int) $value === 1) ? 'released' : 'not_held';
    } catch (Throwable) {
        return 'failed';
    }
}

function cc_pairing_now(): DateTimeImmutable
{
    return new DateTimeImmutable('now', new DateTimeZone('UTC'));
}

function cc_pairing_datetime(DateTimeImmutable $value): string
{
    return $value->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s');
}

function cc_pairing_iso8601(DateTimeImmutable $value): string
{
    return $value->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d\TH:i:s\Z');
}

function cc_pairing_epoch_ms(DateTimeImmutable $value): int
{
    return (int) $value->format('U') * 1000;
}

function cc_pairing_is_terminal(string $state): bool
{
    return in_array($state, CC_PAIRING_TERMINAL_STATES, true);
}

function cc_pairing_count_rows(PDO $db): int
{
    $statement = $db->query('SELECT COUNT(*) FROM cc_pairing_requests');
    return (int) $statement->fetchColumn();
}

/**
 * Presence-style atomic rate limiting. Scope names are pairing-only and additive;
 * Presence scopes/budgets/cleanup are untouched.
 */
/**
 * Atomic rate limiting for pairing-only scopes.
 *
 * The first row is allocated and every increment is applied in ONE upsert statement
 * (`INSERT ... ON DUPLICATE KEY UPDATE`), so concurrent absent-key requests cannot
 * lose a count or fail a bare duplicate INSERT. `LAST_INSERT_ID(expr)` carries this
 * connection's assigned attempt value back. Transaction ownership stays with the
 * caller (no implicit transaction); deterministic retries are bounded to two
 * retryable conflicts. Presence scopes/budgets/cleanup are untouched.
 */
function cc_pairing_rate_limit(
    PDO $db,
    string $scope,
    string $identifier,
    int $limit,
    int $windowSeconds,
    string $secret,
    DateTimeImmutable $now,
): void {
    $key = cc_pairing_b64_decode_strict($secret) ?? '';
    $hash = hash_hmac('sha256', $scope . "\x00" . $identifier, $key);
    $nowString = cc_pairing_datetime($now);
    $cutoff = cc_pairing_datetime($now->modify('-' . max(1, $windowSeconds) . ' seconds'));

    $sql = 'INSERT INTO cc_api_rate_limits (scope, client_hash, window_started_at, attempts) '
        . 'VALUES (:scope, :client_hash, :window_now, 1) '
        . 'ON DUPLICATE KEY UPDATE '
        . 'attempts = IF(window_started_at <= :cutoff_a, LAST_INSERT_ID(1), LAST_INSERT_ID(attempts + 1)), '
        . 'window_started_at = IF(window_started_at <= :cutoff_b, :window_now_b, window_started_at)';

    $attempts = 0;
    $rowCount = 0;
    for ($attempt = 0; $attempt < 3; $attempt++) {
        try {
            $statement = $db->prepare($sql);
            $statement->execute([
                'scope' => $scope,
                'client_hash' => $hash,
                'window_now' => $nowString,
                'window_now_b' => $nowString,
                'cutoff_a' => $cutoff,
                'cutoff_b' => $cutoff,
            ]);
            $rowCount = $statement->rowCount();
            break;
        } catch (PDOException $exception) {
            if ($attempt < 2 && cc_pairing_retryable_conflict($exception)) {
                continue;
            }
            throw $exception;
        }
    }

    if ($rowCount === 1) {
        // Fresh row: this statement inserted attempt 1.
        $attempts = 1;
        $windowStart = $now;
    } else {
        $attempts = (int) $db->query('SELECT LAST_INSERT_ID()')->fetchColumn();
        if ($attempts <= 1) {
            // The window was reset by this statement.
            $windowStart = $now;
        } else {
            $windowStatement = $db->prepare(
                'SELECT window_started_at FROM cc_api_rate_limits WHERE scope = :scope AND client_hash = :client_hash'
            );
            $windowStatement->execute(['scope' => $scope, 'client_hash' => $hash]);
            $windowStart = new DateTimeImmutable(
                (string) $windowStatement->fetchColumn(),
                new DateTimeZone('UTC')
            );
        }
    }

    if ($attempts > $limit) {
        $retryAfter = max(1, $windowSeconds - max(0, $now->getTimestamp() - $windowStart->getTimestamp()));
        throw new CcPairingException('RATE_LIMITED', 429, 'Too many pairing requests.', $retryAfter);
    }
}

function cc_pairing_retryable_conflict(PDOException $exception): bool
{
    $sqlState = (string) $exception->getCode();
    $driverCode = (int) ($exception->errorInfo[1] ?? 0);
    return in_array($sqlState, ['40001', '23000'], true)
        || in_array($driverCode, [1062, 1205, 1213], true);
}

function cc_pairing_context_identifier(): string
{
    $address = trim((string) ($_SERVER['REMOTE_ADDR'] ?? ''));
    return filter_var($address, FILTER_VALIDATE_IP) ? $address : 'unknown-client';
}

function cc_pairing_request_identifier(string $requestId): string
{
    return hash('sha256', $requestId);
}

/* ---------------------------------------------------------------------------
 * Persistence operations
 * ------------------------------------------------------------------------- */

function cc_pairing_row_payload(array $row): array
{
    return [
        'requestId' => (string) $row['request_id'],
        'userCode' => (string) $row['user_code'],
        'state' => (string) $row['state'],
        'expiresAtEpochMs' => (int) (new DateTimeImmutable((string) $row['expires_at'], new DateTimeZone('UTC')))->format('U') * 1000,
    ];
}

function cc_pairing_build_create_response(array $row, array $settings, string $clientSecret): array
{
    $expiresAt = new DateTimeImmutable((string) $row['expires_at'], new DateTimeZone('UTC'));
    $token = cc_pairing_claim_token(
        (string) $settings['claim_secret'],
        (string) $row['request_id'],
        $clientSecret,
    );

    return [
        'contractVersion' => CC_PAIRING_CONTRACT_VERSION,
        'requestId' => (string) $row['request_id'],
        'userCode' => (string) $row['user_code'],
        'claimToken' => $token,
        'expiresAtEpochMs' => cc_pairing_epoch_ms($expiresAt),
        'verificationUrl' => 'https://hulksa.com/control-center/pair/#C=' . (string) $row['user_code'],
        'pollIntervalSeconds' => (int) $settings['poll_interval_seconds'],
        'maxDeliveries' => (int) $settings['max_deliveries'],
    ];
}

/**
 * Secret-proven idempotent create with capacity admission.
 *
 * @return array{status: int, body: array}
 */
function cc_pairing_create(PDO $db, array $input, array $settings, ?DateTimeImmutable $now = null): array
{
    $now ??= cc_pairing_now();
    $data = cc_pairing_validate_create_input($input);
    $secretHash = cc_pairing_token_hash($data['clientSecret']);

    $existing = cc_pairing_find_by_client($db, $data['installationId'], $data['clientRequestId']);
    if ($existing !== null) {
        return cc_pairing_resolve_existing_create($existing, $data['clientSecret'], $settings);
    }

    $started = !$db->inTransaction();
    if ($started) {
        $db->beginTransaction();
    }
    try {
        // Capacity admission: one short pairing-only row lock; no network IO here.
        $lock = $db->prepare('SELECT lock_id FROM cc_pairing_admission WHERE lock_id = 1 FOR UPDATE');
        $lock->execute();
        if ($lock->fetch() === false) {
            throw new CcPairingException('PAIRING_UNAVAILABLE', 503, 'Pairing admission lock is missing.');
        }
        $recheck = cc_pairing_find_by_client($db, $data['installationId'], $data['clientRequestId']);
        if ($recheck !== null) {
            if ($started) {
                $db->commit();
            }
            return cc_pairing_resolve_existing_create($recheck, $data['clientSecret'], $settings);
        }
        if (cc_pairing_count_rows($db) >= (int) $settings['max_retained_rows']) {
            if ($started) {
                $db->commit();
            }
            throw new CcPairingException(
                'PAIRING_CAPACITY',
                503,
                'Pairing capacity reached.',
                CC_PAIRING_CAPACITY_RETRY_SECONDS,
            );
        }

        $requestId = cc_pairing_uuid4();
        $expiresAt = $now->modify('+' . (int) $settings['ttl_seconds'] . ' seconds');
        $claimToken = cc_pairing_claim_token((string) $settings['claim_secret'], $requestId, $data['clientSecret']);
        $insert = $db->prepare(
            'INSERT INTO cc_pairing_requests '
            . '(request_id, client_request_id, installation_id, client_secret_hash, user_code, claim_token_hash, '
            . 'state, device_platform, device_manufacturer, device_model, app_version, created_at, expires_at) '
            . 'VALUES (:request_id, :client_request_id, :installation_id, :client_secret_hash, :user_code, :claim_token_hash, '
            . '\'PENDING\', :platform, :manufacturer, :model, :app_version, :created_at, :expires_at)'
        );

        $inserted = false;
        for ($attempt = 0; $attempt < CC_PAIRING_USER_CODE_ATTEMPTS; $attempt++) {
            $userCode = cc_pairing_generate_user_code();
            try {
                $insert->execute([
                    'request_id' => $requestId,
                    'client_request_id' => $data['clientRequestId'],
                    'installation_id' => $data['installationId'],
                    'client_secret_hash' => $secretHash,
                    'user_code' => $userCode,
                    'claim_token_hash' => cc_pairing_token_hash($claimToken),
                    'platform' => $data['device']['platform'],
                    'manufacturer' => $data['device']['manufacturer'],
                    'model' => $data['device']['model'],
                    'app_version' => $data['device']['appVersion'],
                    'created_at' => cc_pairing_datetime($now),
                    'expires_at' => cc_pairing_datetime($expiresAt),
                ]);
                $inserted = true;
                break;
            } catch (PDOException $exception) {
                if (!cc_pairing_duplicate_key($exception)) {
                    throw $exception;
                }
            }
        }
        if (!$inserted) {
            throw new CcPairingException('PAIRING_UNAVAILABLE', 503, 'Unable to allocate a pairing code.');
        }
        if ($started) {
            $db->commit();
        }

        return [
            'status' => 201,
            'body' => cc_pairing_build_create_response(
                [
                    'request_id' => $requestId,
                    'client_request_id' => $data['clientRequestId'],
                    'user_code' => $userCode,
                    'expires_at' => cc_pairing_datetime($expiresAt),
                ],
                $settings,
                $data['clientSecret'],
            ),
        ];
    } catch (Throwable $exception) {
        if ($started && $db->inTransaction()) {
            $db->rollBack();
        }
        throw $exception;
    }
}

function cc_pairing_duplicate_key(PDOException $exception): bool
{
    $sqlState = (string) $exception->getCode();
    $driverCode = (int) ($exception->errorInfo[1] ?? 0);
    return $sqlState === '23000' || in_array($driverCode, [1062], true);
}

function cc_pairing_find_by_client(PDO $db, string $installationId, string $clientRequestId): ?array
{
    $statement = $db->prepare(
        'SELECT * FROM cc_pairing_requests WHERE installation_id = :installation_id '
        . 'AND client_request_id = :client_request_id LIMIT 1'
    );
    $statement->execute([
        'installation_id' => $installationId,
        'client_request_id' => $clientRequestId,
    ]);
    $row = $statement->fetch();
    return is_array($row) ? $row : null;
}

function cc_pairing_resolve_existing_create(array $row, string $clientSecret, array $settings): array
{
    if (!hash_equals((string) $row['client_secret_hash'], cc_pairing_token_hash($clientSecret))) {
        throw new CcPairingException('IDEMPOTENCY_MISMATCH', 409, 'Idempotency proof does not match.');
    }
    if (cc_pairing_is_terminal((string) $row['state'])) {
        throw new CcPairingException('CREATE_CONFLICT', 409, 'The attempt is already ' . (string) $row['state'] . '.');
    }
    return ['status' => 200, 'body' => cc_pairing_build_create_response($row, $settings, $clientSecret)];
}

function cc_pairing_load_by_request_id(PDO $db, string $requestId, bool $forUpdate = false): ?array
{
    $statement = $db->prepare(
        'SELECT * FROM cc_pairing_requests WHERE request_id = :request_id LIMIT 1'
        . ($forUpdate && strtolower((string) $db->getAttribute(PDO::ATTR_DRIVER_NAME)) === 'mysql' ? ' FOR UPDATE' : '')
    );
    $statement->execute(['request_id' => $requestId]);
    $row = $statement->fetch();
    return is_array($row) ? $row : null;
}

/**
 * Pre-transaction TV authentication: verifies the request exists and the bearer
 * token hash matches WITHOUT allocating per-request rate counters or changing
 * state. Handlers re-verify inside their transaction for race safety.
 */
function cc_pairing_authenticate(PDO $db, string $requestId, string $claimToken): array
{
    $row = cc_pairing_load_by_request_id($db, $requestId);
    if ($row === null) {
        throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
    }
    if (!hash_equals((string) $row['claim_token_hash'], cc_pairing_token_hash($claimToken))) {
        throw new CcPairingException('UNAUTHORIZED', 401, 'A valid pairing token is required.');
    }
    return $row;
}

/** @return array<string, int> pairing-owned rate scopes and their window seconds */
function cc_pairing_rate_scope_windows(array $settings = []): array
{
    $seconds = max(10, min(3600, (int) ($settings['rate_limit_window_seconds'] ?? 60)));
    return [
        'pairing_create_min' => $seconds,
        'pairing_create_hour' => 3600,
        'pairing_claim_req' => $seconds,
        'pairing_claim_ip' => $seconds,
        'pairing_ack_req' => $seconds,
        'pairing_cancel_req' => $seconds,
        'pairing_page_lookup' => $seconds,
        'pairing_page_approve_ip' => $seconds,
        'pairing_page_approve_req' => $seconds,
    ];
}

/**
 * Bounded cleanup for pairing-owned rate scopes only. Presence scopes and its
 * shared cleanup are untouched; live windows are preserved (only windows older
 * than their own scope window are deleted).
 */
/**
 * Scoped pairing rate cleanup that preserves confirmed partial progress.
 *
 * Each DELETE is one statement. A budget refusal before the next statement stops
 * cleanly (`stopped=true`, `unconfirmed=false`) and keeps the already-committed
 * count. A statement/transport error returns the confirmed count with
 * `unconfirmed=true` because the in-flight DELETE's outcome is unknown. The count
 * is never reset to zero on refusal or abort.
 *
 * @return array{deleted: int, stopped: bool, unconfirmed: bool}
 */
function cc_pairing_rate_limit_cleanup_outcome(
    PDO $db,
    DateTimeImmutable $now,
    int $batch = CC_PAIRING_CLEANUP_BATCH,
    array $settings = [],
    ?CcPairingBudget $budget = null,
    ?float $deadlineAt = null,
    ?callable $clock = null,
): array {
    $clock ??= static fn (): float => microtime(true);
    $deleted = 0;
    $stopped = false;
    foreach (cc_pairing_rate_scope_windows($settings) as $scope => $windowSeconds) {
        if ($deleted >= $batch) {
            break;
        }
        if ($budget !== null) {
            if (!$budget->allowWork()) {
                return ['deleted' => $deleted, 'stopped' => true, 'unconfirmed' => false];
            }
        } elseif ($deadlineAt !== null && $clock() >= $deadlineAt) {
            return ['deleted' => $deleted, 'stopped' => true, 'unconfirmed' => false];
        }
        try {
            $statement = $db->prepare(
                'DELETE FROM cc_api_rate_limits WHERE scope = :scope AND window_started_at <= :cutoff LIMIT '
                . max(0, $batch - $deleted)
            );
            $statement->execute([
                'scope' => $scope,
                'cutoff' => cc_pairing_datetime($now->modify('-' . $windowSeconds . ' seconds')),
            ]);
        } catch (Throwable) {
            return ['deleted' => $deleted, 'stopped' => true, 'unconfirmed' => true];
        }
        $deleted += $statement->rowCount();
    }
    return ['deleted' => $deleted, 'stopped' => $stopped, 'unconfirmed' => false];
}

/** Compatibility wrapper returning only the confirmed deleted count. */
function cc_pairing_rate_limit_cleanup(
    PDO $db,
    DateTimeImmutable $now,
    int $batch = CC_PAIRING_CLEANUP_BATCH,
    array $settings = [],
    ?CcPairingBudget $budget = null,
    ?float $deadlineAt = null,
    ?callable $clock = null,
): int {
    return cc_pairing_rate_limit_cleanup_outcome($db, $now, $batch, $settings, $budget, $deadlineAt, $clock)['deleted'];
}

/**
 * Bounded maintenance driven by ONE absolute budget.
 *
 * Pass either a prepared `CcPairingBudget` (preferred; started before DB setup) or
 * an absolute `$deadlineAt` (a budget is derived from the connection's verified
 * flavor statement bound). Every SELECT/UPDATE/DELETE and the single combined
 * summary query are guarded: a statement only starts when its verified bound plus
 * the summary and release reserve still fit before the deadline, so setup, all
 * batches, rate cleanup, both counts and lock release complete inside the budget
 * without giving late statements a fresh allowance. Work that cannot fit is refused
 * (`budget_exceeded=true`) with counts confirmed only when the summary actually ran.
 * Each batch update is one statement, so a stop leaves a known retry-safe state.
 * A dispatched statement abort keeps confirmed partial counts and reports
 * `aborted`/`aborted_reason`; a refused rate-cleanup statement keeps the
 * already-committed deletions and reports `rate_cleanup_stopped`; an unknown
 * in-flight DELETE outcome reports `rate_cleanup_unconfirmed` instead of zero.
 *
 * @return array{expired: int, deleted: int, purged: int, rate_limits_deleted: int,
 *   rate_cleanup_stopped: bool, rate_cleanup_unconfirmed: bool, batches: int, aborted: bool,
 *   aborted_reason: ?string, handoff_remaining: ?int, metadata_retained: ?int,
 *   budget_exceeded: bool, incomplete: bool, counts_confirmed: bool,
 *   statement_bound_seconds: ?float}
 */
function cc_pairing_cleanup(
    PDO $db,
    array $settings,
    ?DateTimeImmutable $now = null,
    bool $drain = false,
    int $maxBatches = CC_PAIRING_CLEANUP_DRAIN_BATCHES,
    ?float $deadlineAt = null,
    ?callable $clock = null,
    ?CcPairingBudget $budget = null,
): array {
    $now ??= cc_pairing_now();
    $clock ??= static fn (): float => microtime(true);
    if ($budget === null && $deadlineAt !== null) {
        $plan = cc_pairing_deadline_plan(cc_pairing_driver($db), (string) $db->getAttribute(PDO::ATTR_SERVER_VERSION));
        if (!$plan['supported']) {
            throw new RuntimeException('Cleanup requires a database flavor with a verified statement bound (MariaDB).');
        }
        $budget = new CcPairingBudget($deadlineAt, $plan['statement_bound_seconds'], $clock);
    }

    $expired = 0;
    $deleted = 0;
    $purged = 0;
    $batches = 0;
    $aborted = false;
    $abortedReason = null;
    $drainBound = $drain ? max(1, $maxBatches) : 1;
    $guarded = static function (callable $statement) use ($budget): mixed {
        if ($budget !== null && !$budget->allowWork()) {
            throw new CcPairingBudgetExceeded('Maintenance budget cannot fit the next statement.');
        }
        return $statement();
    };

    // Every SELECT/UPDATE/DELETE below is dispatched through $guarded, so a statement
    // is never started when its verified bound plus summary/finalizer no longer fits.
    try {
        for ($batch = 0; $batch < $drainBound; $batch++) {
            $batches++;

            if ($drain) {
                $purgeIds = $guarded(function () use ($db): array {
                    $selectPurge = $db->prepare(
                        'SELECT pairing_id FROM cc_pairing_requests WHERE state IN (\'PENDING\', \'APPROVED\') '
                        . 'ORDER BY pairing_id LIMIT ' . CC_PAIRING_CLEANUP_BATCH
                    );
                    $selectPurge->execute();
                    return array_map('intval', $selectPurge->fetchAll(PDO::FETCH_COLUMN));
                });
                if ($purgeIds === []) {
                    $batches--;
                    break;
                }
                $purgedBefore = $purged;
                $purged += $guarded(function () use ($db, $now, $purgeIds): int {
                    $placeholders = implode(',', array_fill(0, count($purgeIds), '?'));
                    $update = $db->prepare(
                        'UPDATE cc_pairing_requests SET state = \'CANCELLED\', ' . cc_pairing_destroy_envelope_sql()
                        . ', terminal_at = ?, terminal_reason = \'DISABLED_PURGE\' WHERE pairing_id IN (' . $placeholders . ') '
                        . 'AND state IN (\'PENDING\', \'APPROVED\')'
                    );
                    $update->execute(array_merge([cc_pairing_datetime($now)], $purgeIds));
                    return $update->rowCount();
                });
                if ($purged === $purgedBefore) {
                    $batches--;
                    break;
                }
                continue;
            }

            $expireIds = $guarded(function () use ($db, $now): array {
                $selectExpire = $db->prepare(
                    'SELECT pairing_id FROM cc_pairing_requests WHERE state IN (\'PENDING\', \'APPROVED\') '
                    . 'AND expires_at <= :now ORDER BY expires_at LIMIT ' . CC_PAIRING_CLEANUP_BATCH
                );
                $selectExpire->execute(['now' => cc_pairing_datetime($now)]);
                return array_map('intval', $selectExpire->fetchAll(PDO::FETCH_COLUMN));
            });
            if ($expireIds !== []) {
                $expired += $guarded(function () use ($db, $now, $expireIds): int {
                    $placeholders = implode(',', array_fill(0, count($expireIds), '?'));
                    $update = $db->prepare(
                        'UPDATE cc_pairing_requests SET state = \'EXPIRED\', ' . cc_pairing_destroy_envelope_sql()
                        . ', terminal_at = ?, terminal_reason = \'EXPIRED\' WHERE pairing_id IN (' . $placeholders . ') '
                        . 'AND state IN (\'PENDING\', \'APPROVED\')'
                    );
                    $update->execute(array_merge([cc_pairing_datetime($now)], $expireIds));
                    return $update->rowCount();
                });
            }

            $retentionCutoff = $now->modify('-' . (int) $settings['retention_seconds'] . ' seconds');
            $deleteIds = $guarded(function () use ($db, $retentionCutoff): array {
                $selectDelete = $db->prepare(
                    'SELECT pairing_id FROM cc_pairing_requests WHERE state IN (\'ACKED\', \'DENIED\', \'CANCELLED\', \'EXPIRED\') '
                    . 'AND terminal_at IS NOT NULL AND terminal_at <= :cutoff ORDER BY terminal_at LIMIT ' . CC_PAIRING_CLEANUP_BATCH
                );
                $selectDelete->execute(['cutoff' => cc_pairing_datetime($retentionCutoff)]);
                return array_map('intval', $selectDelete->fetchAll(PDO::FETCH_COLUMN));
            });
            if ($deleteIds !== []) {
                $deleted += $guarded(function () use ($db, $deleteIds): int {
                    $placeholders = implode(',', array_fill(0, count($deleteIds), '?'));
                    $delete = $db->prepare('DELETE FROM cc_pairing_requests WHERE pairing_id IN (' . $placeholders . ')');
                    $delete->execute($deleteIds);
                    return $delete->rowCount();
                });
            }

            if ($expireIds === [] || $deleteIds === []) {
                break;
            }
        }
    } catch (CcPairingBudgetExceeded) {
        // Partial work is already committed statement-by-statement; stop cleanly.
        $batches = max(0, $batches - 1);
    } catch (Throwable $exception) {
        // A dispatched statement aborted (e.g. lock-wait/statement timeout or a
        // transport error): stop all further maintenance work, keep the confirmed
        // counts so far and report the abort truthfully.
        $aborted = true;
        $abortedReason = (new ReflectionClass($exception))->getShortName();
    }

    $rateLimitsDeleted = 0;
    $rateCleanupStopped = false;
    $rateCleanupUnconfirmed = false;
    if (!$aborted) {
        $rateOutcome = cc_pairing_rate_limit_cleanup_outcome(
            $db,
            $now,
            CC_PAIRING_CLEANUP_BATCH,
            $settings,
            $budget
        );
        $rateLimitsDeleted = $rateOutcome['deleted'];
        $rateCleanupStopped = $rateOutcome['stopped'];
        $rateCleanupUnconfirmed = $rateOutcome['unconfirmed'];
    } else {
        $rateCleanupStopped = true;
    }

    $countsConfirmed = false;
    $handoffRemaining = null;
    $metadataRetained = null;
    if ($budget === null || $budget->allowSummary()) {
        // One combined summary query bounds BOTH counts in a single statement.
        try {
            $summary = $db->query(
                'SELECT SUM(state IN (\'PENDING\', \'APPROVED\')) AS handoff_count, COUNT(*) AS total_count '
                . 'FROM cc_pairing_requests'
            )->fetch();
        } catch (Throwable) {
            $summary = null;
        }
        if (is_array($summary)) {
            $handoffRemaining = (int) ($summary['handoff_count'] ?? 0);
            $metadataRetained = max(0, (int) ($summary['total_count'] ?? 0) - $handoffRemaining);
            $countsConfirmed = true;
        }
    }

    $budgetExceeded = $budget !== null && ($budget->workRefused() || $budget->summaryRefused());
    return [
        'expired' => $expired,
        'deleted' => $deleted,
        'purged' => $purged,
        'rate_limits_deleted' => $rateLimitsDeleted,
        'rate_cleanup_stopped' => $rateCleanupStopped,
        'rate_cleanup_unconfirmed' => $rateCleanupUnconfirmed,
        'batches' => $batches,
        'aborted' => $aborted,
        'aborted_reason' => $abortedReason,
        'handoff_remaining' => $handoffRemaining,
        'metadata_retained' => $metadataRetained,
        'budget_exceeded' => $budgetExceeded,
        'incomplete' => $aborted
            || $budgetExceeded
            || !$countsConfirmed
            || $rateCleanupStopped
            || $rateCleanupUnconfirmed
            || ($drain && ($handoffRemaining ?? 0) > 0),
        'counts_confirmed' => $countsConfirmed,
        'statement_bound_seconds' => $budget?->statementBoundSeconds(),
    ];
}

function cc_pairing_destroy_envelope_sql(): string
{
    return 'envelope_version = NULL, envelope_key_id = NULL, envelope_blob = NULL, envelope_set_at = NULL';
}

function cc_pairing_load_by_user_code(PDO $db, string $userCode): ?array
{
    $statement = $db->prepare('SELECT * FROM cc_pairing_requests WHERE user_code = :user_code LIMIT 1');
    $statement->execute(['user_code' => $userCode]);
    $row = $statement->fetch();
    return is_array($row) ? $row : null;
}

/** Display-only mask for a previously entered access code; never reveals the payload. */
function cc_pairing_mask_access_code(string $accessCode): string
{
    $trimmed = strtoupper(trim($accessCode));
    if ($trimmed === '') {
        return '';
    }
    $tail = substr($trimmed, -4);
    return 'HULK-****-****-****-' . $tail;
}

/**
 * @return array{state: string, retryAfterMs: int, credentials: ?array, deliveryCount: int, expiresAtEpochMs: int}
 */
function cc_pairing_state_response(array $row, array $settings): array
{
    $expiresAt = new DateTimeImmutable((string) $row['expires_at'], new DateTimeZone('UTC'));
    return [
        'state' => (string) $row['state'],
        'retryAfterMs' => max(1, (int) $settings['poll_interval_seconds']) * 1000,
        'credentials' => null,
        'deliveryCount' => (int) $row['delivery_count'],
        'expiresAtEpochMs' => cc_pairing_epoch_ms($expiresAt),
    ];
}

function cc_pairing_claim(
    PDO $db,
    string $requestId,
    string $claimToken,
    array $settings,
    ?callable $clock = null,
): array {
    $clock ??= static fn (): DateTimeImmutable => cc_pairing_now();
    $now = $clock();
    $started = !$db->inTransaction();
    if ($started) {
        $db->beginTransaction();
    }
    try {
        $row = cc_pairing_load_by_request_id($db, $requestId, true);
        if ($row === null) {
            throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
        }
        if (!hash_equals((string) $row['claim_token_hash'], cc_pairing_token_hash($claimToken))) {
            throw new CcPairingException('UNAUTHORIZED', 401, 'A valid pairing token is required.');
        }
        // Refresh server time AFTER the row-lock wait so expiry reflects lock latency.
        $now = $clock();

        $state = (string) $row['state'];
        $expiresAt = new DateTimeImmutable((string) $row['expires_at'], new DateTimeZone('UTC'));

        if (!cc_pairing_is_terminal($state) && $expiresAt->getTimestamp() <= $now->getTimestamp()) {
            $update = $db->prepare(
                'UPDATE cc_pairing_requests SET state = \'EXPIRED\', ' . cc_pairing_destroy_envelope_sql()
                . ', terminal_at = :terminal_at, terminal_reason = \'EXPIRED\' '
                . 'WHERE pairing_id = :pairing_id AND state IN (\'PENDING\', \'APPROVED\')'
            );
            $update->execute(['terminal_at' => cc_pairing_datetime($now), 'pairing_id' => (int) $row['pairing_id']]);
            if ($update->rowCount() === 1) {
                if ($started) {
                    $db->commit();
                }
                $row['state'] = 'EXPIRED';
                $row['terminal_reason'] = 'EXPIRED';
                return cc_pairing_state_response($row, $settings);
            }
            $row = cc_pairing_load_by_request_id($db, $requestId, true);
            if ($row === null) {
                throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
            }
            if (!cc_pairing_is_terminal((string) $row['state'])) {
                throw new CcPairingException('SERVICE_UNAVAILABLE', 503, 'Pairing state unavailable.');
            }
            if ($started) {
                $db->commit();
            }
            return cc_pairing_state_response($row, $settings);
        }

        if (cc_pairing_is_terminal($state)) {
            if ($started) {
                $db->commit();
            }
            return cc_pairing_state_response($row, $settings);
        }

        if ($state === 'PENDING') {
            if ($started) {
                $db->commit();
            }
            return cc_pairing_state_response($row, $settings);
        }

        if ($state !== 'APPROVED') {
            throw new CcPairingException('PAIRING_STATE_CONFLICT', 409, 'Unexpected pairing state.');
        }

        // Delivery authorization: fresh enable authority (R03 binding correction 5).
        if (!cc_pairing_enabled()) {
            if ($started) {
                $db->commit();
            }
            throw new CcPairingException('PAIRING_DISABLED', 404, 'Pairing is disabled.');
        }
        if ((int) $row['delivery_count'] >= (int) $settings['max_deliveries']) {
            if ($started) {
                $db->commit();
            }
            throw new CcPairingException('DELIVERY_LIMIT', 429, 'Delivery limit reached.');
        }

        $credentials = cc_pairing_unseal_credentials(
            (string) ($row['envelope_blob'] ?? ''),
            $requestId,
            cc_pairing_iso8601($expiresAt),
            $settings,
            $row['envelope_version'] === null ? null : (int) $row['envelope_version'],
            $row['envelope_key_id'] === null ? null : (string) $row['envelope_key_id'],
        );
        if ($credentials === null) {
            $update = $db->prepare(
                'UPDATE cc_pairing_requests SET state = \'CANCELLED\', ' . cc_pairing_destroy_envelope_sql()
                . ', terminal_at = :terminal_at, terminal_reason = \'ENVELOPE_INVALID\' '
                . 'WHERE pairing_id = :pairing_id AND state = \'APPROVED\''
            );
            $update->execute(['terminal_at' => cc_pairing_datetime($now), 'pairing_id' => (int) $row['pairing_id']]);
            if ($update->rowCount() !== 1) {
                $fresh = cc_pairing_load_by_request_id($db, $requestId, true);
                if ($fresh === null) {
                    throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
                }
                if (!cc_pairing_is_terminal((string) $fresh['state'])) {
                    throw new CcPairingException('SERVICE_UNAVAILABLE', 503, 'Envelope handling failed.');
                }
                if ($started) {
                    $db->commit();
                }
                return cc_pairing_state_response($fresh, $settings);
            }
            if ($started) {
                $db->commit();
            }
            $row['state'] = 'CANCELLED';
            $row['terminal_reason'] = 'ENVELOPE_INVALID';
            return cc_pairing_state_response($row, $settings);
        }

        $update = $db->prepare(
            'UPDATE cc_pairing_requests SET delivery_count = delivery_count + 1, last_delivered_at = :delivered_at '
            . 'WHERE pairing_id = :pairing_id AND state = \'APPROVED\''
        );
        $update->execute(['delivered_at' => cc_pairing_datetime($now), 'pairing_id' => (int) $row['pairing_id']]);
        if ($update->rowCount() !== 1) {
            $fresh = cc_pairing_load_by_request_id($db, $requestId, true);
            if ($fresh === null) {
                throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
            }
            if ($started) {
                $db->commit();
            }
            return cc_pairing_state_response($fresh, $settings);
        }
        if ($started) {
            $db->commit();
        }
        return [
            'state' => 'APPROVED',
            'retryAfterMs' => max(1, (int) $settings['poll_interval_seconds']) * 1000,
            'credentials' => $credentials,
            'deliveryCount' => (int) $row['delivery_count'] + 1,
            'expiresAtEpochMs' => cc_pairing_epoch_ms($expiresAt),
        ];
    } catch (Throwable $exception) {
        if ($started && $db->inTransaction()) {
            $db->rollBack();
        }
        throw $exception;
    }
}

function cc_pairing_ack(PDO $db, string $requestId, string $claimToken, ?callable $clock = null): array
{
    $clock ??= static fn (): DateTimeImmutable => cc_pairing_now();
    $now = $clock();
    $started = !$db->inTransaction();
    if ($started) {
        $db->beginTransaction();
    }
    try {
        $row = cc_pairing_load_by_request_id($db, $requestId, true);
        if ($row === null) {
            throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
        }
        if (!hash_equals((string) $row['claim_token_hash'], cc_pairing_token_hash($claimToken))) {
            throw new CcPairingException('UNAUTHORIZED', 401, 'A valid pairing token is required.');
        }
        $now = $clock();
        $state = (string) $row['state'];
        if ($state === 'ACKED') {
            if ($started) {
                $db->commit();
            }
            return ['state' => 'ACKED'];
        }
        if ($state !== 'APPROVED') {
            throw new CcPairingException('PAIRING_STATE_CONFLICT', 409, 'The attempt is already ' . $state . '.');
        }
        $update = $db->prepare(
            'UPDATE cc_pairing_requests SET state = \'ACKED\', ' . cc_pairing_destroy_envelope_sql()
            . ', acked_at = :acked_at, terminal_at = :terminal_at, terminal_reason = \'ACKED\' '
            . 'WHERE pairing_id = :pairing_id AND state = \'APPROVED\''
        );
        $update->execute([
            'acked_at' => cc_pairing_datetime($now),
            'terminal_at' => cc_pairing_datetime($now),
            'pairing_id' => (int) $row['pairing_id'],
        ]);
        if ($update->rowCount() !== 1) {
            $fresh = cc_pairing_load_by_request_id($db, $requestId, true);
            if ($fresh === null) {
                throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
            }
            if ((string) $fresh['state'] === 'ACKED') {
                if ($started) {
                    $db->commit();
                }
                return ['state' => 'ACKED'];
            }
            throw new CcPairingException('PAIRING_STATE_CONFLICT', 409, 'The attempt is already ' . (string) $fresh['state'] . '.');
        }
        if ($started) {
            $db->commit();
        }
        return ['state' => 'ACKED'];
    } catch (Throwable $exception) {
        if ($started && $db->inTransaction()) {
            $db->rollBack();
        }
        throw $exception;
    }
}

function cc_pairing_cancel(PDO $db, string $requestId, string $claimToken, ?callable $clock = null): array
{
    $clock ??= static fn (): DateTimeImmutable => cc_pairing_now();
    $now = $clock();
    $started = !$db->inTransaction();
    if ($started) {
        $db->beginTransaction();
    }
    try {
        $row = cc_pairing_load_by_request_id($db, $requestId, true);
        if ($row === null) {
            throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
        }
        if (!hash_equals((string) $row['claim_token_hash'], cc_pairing_token_hash($claimToken))) {
            throw new CcPairingException('UNAUTHORIZED', 401, 'A valid pairing token is required.');
        }
        $now = $clock();
        $state = (string) $row['state'];
        if (cc_pairing_is_terminal($state)) {
            if ($started) {
                $db->commit();
            }
            return ['state' => $state];
        }
        $update = $db->prepare(
            'UPDATE cc_pairing_requests SET state = \'CANCELLED\', ' . cc_pairing_destroy_envelope_sql()
            . ', terminal_at = :terminal_at, terminal_reason = \'CANCELLED\' '
            . 'WHERE pairing_id = :pairing_id AND state IN (\'PENDING\', \'APPROVED\')'
        );
        $update->execute(['terminal_at' => cc_pairing_datetime($now), 'pairing_id' => (int) $row['pairing_id']]);
        if ($update->rowCount() !== 1) {
            $fresh = cc_pairing_load_by_request_id($db, $requestId, true);
            if ($fresh === null) {
                throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
            }
            if ($started) {
                $db->commit();
            }
            return ['state' => (string) $fresh['state']];
        }
        if ($started) {
            $db->commit();
        }
        return ['state' => 'CANCELLED'];
    } catch (Throwable $exception) {
        if ($started && $db->inTransaction()) {
            $db->rollBack();
        }
        throw $exception;
    }
}

/**
 * Ordered approval: request → code → reseller host → provider probe (no DB row lock
 * during provider IO) → commit rechecking state, expiry, host and fresh enable
 * authority. A concurrent approval is never overwritten.
 *
 * @return array{hostLabel: string, requestId: string, userCode: string}
 */
function cc_pairing_approve(
    PDO $controlDb,
    string $requestId,
    string $accessCode,
    string $username,
    string $password,
    array $settings,
    ?callable $probe = null,
    ?callable $clock = null,
    ?PDO $resellerDb = null,
): array {
    $clock ??= static fn (): DateTimeImmutable => cc_pairing_now();
    $now = $clock();
    $credentials = cc_pairing_validate_credentials_input($accessCode, $username, $password);

    $row = cc_pairing_load_by_request_id($controlDb, $requestId);
    if ($row === null) {
        throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
    }
    if ((string) $row['state'] !== 'PENDING') {
        throw new CcPairingException('PAIRING_STATE_CONFLICT', 409, 'The request is already ' . (string) $row['state'] . '.');
    }
    $expiresAt = new DateTimeImmutable((string) $row['expires_at'], new DateTimeZone('UTC'));
    if ($expiresAt->getTimestamp() <= $now->getTimestamp()) {
        throw new CcPairingException('PAIRING_EXPIRED', 410, 'The pairing request has expired.');
    }
    if (!cc_pairing_enabled()) {
        throw new CcPairingException('PAIRING_DISABLED', 404, 'Pairing is disabled.');
    }
    if (!cc_pairing_readiness($settings)['ready']) {
        throw new CcPairingException('PAIRING_UNAVAILABLE', 503, 'Pairing is not ready.');
    }

    cc_reseller_require_domain();
    $normalizedCode = hulk_normalize_access_code($credentials['accessCode']);
    if ($normalizedCode === null) {
        throw new CcPairingException('INVALID_ACCESS_CODE', 404, 'The access code is invalid.');
    }

    $resellerDb ??= cc_pairing_reseller_db();
    $lookup = $resellerDb->prepare(
        'SELECT reseller_id, access_code, access_code_hash, host, status FROM resellers '
        . 'WHERE access_code_hash = :access_code_hash LIMIT 1'
    );
    $lookup->execute(['access_code_hash' => hulk_access_code_hash($normalizedCode)]);
    $reseller = $lookup->fetch();
    if (!is_array($reseller)) {
        throw new CcPairingException('INVALID_ACCESS_CODE', 404, 'The access code is invalid.');
    }
    $storedCode = hulk_normalize_access_code((string) ($reseller['access_code'] ?? ''));
    $storedHash = strtolower((string) ($reseller['access_code_hash'] ?? ''));
    if (
        $storedCode === null
        || !hash_equals($storedCode, $normalizedCode)
        || preg_match('/^[a-f0-9]{64}$/D', $storedHash) !== 1
        || !hash_equals(hulk_access_code_hash($storedCode), $storedHash)
    ) {
        throw new CcPairingException('INVALID_ACCESS_CODE', 404, 'The access code is invalid.');
    }
    if ((string) ($reseller['status'] ?? '') !== HULK_ACTIVE_STATUS) {
        throw new CcPairingException('RESELLER_INACTIVE', 403, 'The reseller is inactive.');
    }
    $probeHost = hulk_normalize_host((string) ($reseller['host'] ?? ''));
    if ($probeHost === null) {
        throw new CcPairingException('INVALID_HOST', 422, 'The reseller host is invalid.');
    }

    $probeFn = $probe ?? static fn (string $host, string $user, string $pass, array $cfg): array
        => cc_pairing_probe_provider($host, $user, $pass, $cfg);
    $probeResult = $probeFn($probeHost, $credentials['username'], $credentials['password'], $settings);
    $result = (string) ($probeResult['result'] ?? 'PROVIDER_UNREACHABLE');
    if ($result !== 'OK') {
        [$code, $status] = match ($result) {
            'INVALID_CREDENTIALS' => ['INVALID_CREDENTIALS', 401],
            'SUBSCRIPTION_INACTIVE' => ['SUBSCRIPTION_INACTIVE', 403],
            'SERVICE_BLOCKED' => ['SERVICE_BLOCKED', 502],
            'TARGET_POLICY_BLOCKED' => ['TARGET_POLICY_BLOCKED', 503],
            'PAIRING_UNAVAILABLE' => ['PAIRING_UNAVAILABLE', 503],
            default => ['PROVIDER_UNREACHABLE', 503],
        };
        throw new CcPairingException($code, $status, 'Provider validation failed.');
    }

    $started = !$controlDb->inTransaction();
    if ($started) {
        $controlDb->beginTransaction();
    }
    try {
        $locked = cc_pairing_load_by_request_id($controlDb, $requestId, true);
        if ($locked === null) {
            throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
        }
        // Refresh server time after the row-lock wait and provider IO.
        $commitNow = $clock();
        if ((string) $locked['state'] !== 'PENDING') {
            throw new CcPairingException('PAIRING_STATE_CONFLICT', 409, 'A concurrent decision already decided this request.');
        }
        $lockedExpires = new DateTimeImmutable((string) $locked['expires_at'], new DateTimeZone('UTC'));
        if ($lockedExpires->getTimestamp() <= $commitNow->getTimestamp()) {
            throw new CcPairingException('PAIRING_EXPIRED', 410, 'The pairing request has expired.');
        }
        if (!cc_pairing_enabled()) {
            throw new CcPairingException('PAIRING_DISABLED', 404, 'Pairing is disabled.');
        }

        $recheck = $resellerDb->prepare(
            'SELECT access_code, access_code_hash, host, status FROM resellers WHERE reseller_id = :reseller_id LIMIT 1'
        );
        $recheck->execute(['reseller_id' => (int) $reseller['reseller_id']]);
        $current = $recheck->fetch();
        $currentCode = is_array($current) ? hulk_normalize_access_code((string) ($current['access_code'] ?? '')) : null;
        $currentHash = is_array($current) ? strtolower((string) ($current['access_code_hash'] ?? '')) : '';
        if (
            $currentCode === null
            || !hash_equals($normalizedCode, $currentCode)
            || preg_match('/^[a-f0-9]{64}$/D', $currentHash) !== 1
            || !hash_equals(hulk_access_code_hash($currentCode), $currentHash)
        ) {
            throw new CcPairingException('INVALID_ACCESS_CODE', 404, 'The access code changed during validation.');
        }
        if ((string) ($current['status'] ?? '') !== HULK_ACTIVE_STATUS) {
            throw new CcPairingException('RESELLER_INACTIVE', 403, 'The reseller became inactive during validation.');
        }
        $currentHost = hulk_normalize_host((string) ($current['host'] ?? ''));
        if ($currentHost === null || !hash_equals($probeHost, $currentHost)) {
            throw new CcPairingException('HOST_ROTATED', 409, 'The reseller host changed during validation.');
        }

        $expiresIso = cc_pairing_iso8601($lockedExpires);
        $sealed = cc_pairing_seal_credentials($credentials, $requestId, $expiresIso, $settings);
        $update = $controlDb->prepare(
            'UPDATE cc_pairing_requests SET state = \'APPROVED\', reseller_id = :reseller_id, '
            . 'host_fingerprint = :host_fingerprint, envelope_version = :envelope_version, '
            . 'envelope_key_id = :envelope_key_id, envelope_blob = :envelope_blob, '
            . 'envelope_set_at = :envelope_set_at, approved_at = :approved_at '
            . 'WHERE pairing_id = :pairing_id AND state = \'PENDING\''
        );
        $update->execute([
            'reseller_id' => (int) $reseller['reseller_id'],
            'host_fingerprint' => cc_pairing_host_fingerprint($probeHost),
            'envelope_version' => (int) $sealed['version'],
            'envelope_key_id' => (string) $sealed['key_id'],
            'envelope_blob' => $sealed['blob'],
            'envelope_set_at' => cc_pairing_datetime($commitNow),
            'approved_at' => cc_pairing_datetime($commitNow),
            'pairing_id' => (int) $locked['pairing_id'],
        ]);
        if ($update->rowCount() !== 1) {
            throw new CcPairingException('PAIRING_STATE_CONFLICT', 409, 'A concurrent decision already decided this request.');
        }
        if ($started) {
            $controlDb->commit();
        }

        return [
            'requestId' => $requestId,
            'userCode' => (string) $locked['user_code'],
            'hostLabel' => cc_pairing_host_display_label($probeHost),
        ];
    } catch (Throwable $exception) {
        if ($started && $controlDb->inTransaction()) {
            $controlDb->rollBack();
        }
        throw $exception;
    }
}

/**
 * Explicit denial from the page. Serialized under the request row lock with a fresh
 * clock: a logically expired request transitions to EXPIRED (envelope destroyed)
 * and is reported as expired, never as a successful denial. A concurrent
 * approval/other state is reported truthfully.
 *
 * @return array{denied: bool, state: string}
 */
function cc_pairing_deny(PDO $db, string $requestId, ?callable $clock = null): array
{
    $clock ??= static fn (): DateTimeImmutable => cc_pairing_now();
    $now = $clock();
    $started = !$db->inTransaction();
    if ($started) {
        $db->beginTransaction();
    }
    try {
        $row = cc_pairing_load_by_request_id($db, $requestId, true);
        if ($row === null) {
            throw new CcPairingException('NOT_FOUND', 404, 'Pairing request not found.');
        }
        $now = $clock();
        $state = (string) $row['state'];
        if ($state !== 'PENDING') {
            if ($started) {
                $db->commit();
            }
            return ['denied' => false, 'state' => $state];
        }
        $expiresAt = new DateTimeImmutable((string) $row['expires_at'], new DateTimeZone('UTC'));
        if ($expiresAt->getTimestamp() <= $now->getTimestamp()) {
            $update = $db->prepare(
                'UPDATE cc_pairing_requests SET state = \'EXPIRED\', ' . cc_pairing_destroy_envelope_sql()
                . ', terminal_at = :terminal_at, terminal_reason = \'EXPIRED\' '
                . 'WHERE pairing_id = :pairing_id AND state = \'PENDING\''
            );
            $update->execute(['terminal_at' => cc_pairing_datetime($now), 'pairing_id' => (int) $row['pairing_id']]);
            if ($update->rowCount() !== 1) {
                $fresh = cc_pairing_load_by_request_id($db, $requestId, true);
                if ($started) {
                    $db->commit();
                }
                return ['denied' => false, 'state' => $fresh === null ? 'EXPIRED' : (string) $fresh['state']];
            }
            if ($started) {
                $db->commit();
            }
            return ['denied' => false, 'state' => 'EXPIRED'];
        }
        $update = $db->prepare(
            'UPDATE cc_pairing_requests SET state = \'DENIED\', ' . cc_pairing_destroy_envelope_sql()
            . ', terminal_at = :terminal_at, terminal_reason = \'DENIED\' '
            . 'WHERE pairing_id = :pairing_id AND state = \'PENDING\''
        );
        $update->execute(['terminal_at' => cc_pairing_datetime($now), 'pairing_id' => (int) $row['pairing_id']]);
        if ($update->rowCount() !== 1) {
            $fresh = cc_pairing_load_by_request_id($db, $requestId, true);
            if ($started) {
                $db->commit();
            }
            return ['denied' => false, 'state' => $fresh === null ? 'PENDING' : (string) $fresh['state']];
        }
        if ($started) {
            $db->commit();
        }
        return ['denied' => true, 'state' => 'DENIED'];
    } catch (Throwable $exception) {
        if ($started && $db->inTransaction()) {
            $db->rollBack();
        }
        throw $exception;
    }
}



/* ---------------------------------------------------------------------------
 * Page session, CSRF and page rendering helpers
 * ------------------------------------------------------------------------- */

function cc_pairing_start_session(): void
{
    if (session_status() === PHP_SESSION_ACTIVE) {
        return;
    }
    ini_set('session.use_strict_mode', '1');
    ini_set('session.use_only_cookies', '1');
    ini_set('session.cookie_httponly', '1');
    ini_set('session.cookie_samesite', 'Strict');
    session_name('HULK_PAIRING_SESSION');
    session_set_cookie_params([
        'lifetime' => 1800,
        'path' => defined('HULK_PAIRING_PUBLIC_ENTRY') && HULK_PAIRING_PUBLIC_ENTRY === true
            ? '/pair/'
            : '/control-center/pair/',
        // Pairing cookies are always Secure; the page refuses insecure credential
        // handling before any session/form processing (local cli/cli-server harness
        // exception is documented in the effective contract).
        'secure' => true,
        'httponly' => true,
        'samesite' => 'Strict',
    ]);
    session_start();
}

function cc_pairing_csrf_token(): string
{
    cc_pairing_start_session();
    if (!isset($_SESSION['csrf_token']) || !is_string($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }
    return (string) $_SESSION['csrf_token'];
}

function cc_pairing_verify_csrf(?string $token): bool
{
    cc_pairing_start_session();
    $expected = (string) ($_SESSION['csrf_token'] ?? '');
    return $expected !== '' && is_string($token) && hash_equals($expected, $token);
}

const CC_PAIRING_FORM_PROOF_TTL = 1800;
const CC_PAIRING_FORM_PROOF_MAX = 5;

/**
 * Per-render/attempt form proof binding a rendered action to the matched TV request.
 * IDs alone are not authorization: every approve/deny submission must present the
 * proof issued when that specific confirm view was rendered for that request.
 * Session holds hashes only; credentials are never stored.
 */
function cc_pairing_form_proof_issue(string $requestId): string
{
    cc_pairing_start_session();
    $token = rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
    $forms = is_array($_SESSION['pairing_forms'] ?? null) ? $_SESSION['pairing_forms'] : [];
    $now = time();
    foreach ($forms as $id => $entry) {
        if (!is_array($entry) || (int) ($entry['expires'] ?? 0) <= $now) {
            unset($forms[$id]);
        }
    }
    $forms[$requestId] = [
        'hash' => cc_pairing_token_hash($token),
        'expires' => $now + CC_PAIRING_FORM_PROOF_TTL,
    ];
    if (count($forms) > CC_PAIRING_FORM_PROOF_MAX) {
        $forms = array_slice($forms, -CC_PAIRING_FORM_PROOF_MAX, null, true);
    }
    $_SESSION['pairing_forms'] = $forms;
    return $token;
}

function cc_pairing_form_proof_verify(string $requestId, ?string $token): bool
{
    cc_pairing_start_session();
    if ($token === null || preg_match('/^[A-Za-z0-9_-]{43}$/D', $token) !== 1) {
        return false;
    }
    $forms = is_array($_SESSION['pairing_forms'] ?? null) ? $_SESSION['pairing_forms'] : [];
    $entry = $forms[$requestId] ?? null;
    if (!is_array($entry)) {
        return false;
    }
    $valid = (int) ($entry['expires'] ?? 0) > time()
        && hash_equals((string) ($entry['hash'] ?? ''), cc_pairing_token_hash($token));
    if ($valid) {
        unset($forms[$requestId]);
        $_SESSION['pairing_forms'] = $forms;
    }
    return $valid;
}

function cc_pairing_form_proof_clear(): void
{
    cc_pairing_start_session();
    unset($_SESSION['pairing_forms']);
}

function cc_pairing_page_notice(string $tone, string $message): void
{
    cc_pairing_start_session();
    $_SESSION['pairing_notice'] = ['tone' => $tone, 'message' => $message];
}

function cc_pairing_take_notice(): ?array
{
    cc_pairing_start_session();
    $notice = $_SESSION['pairing_notice'] ?? null;
    unset($_SESSION['pairing_notice']);
    return is_array($notice) && isset($notice['tone'], $notice['message']) ? $notice : null;
}
