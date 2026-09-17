<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';
require_once __DIR__ . '/presence.php';

const CC_PRESENCE_MAX_BODY_BYTES = 16384;

function cc_presence_api_headers(): void
{
    header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
    header('Pragma: no-cache');
    header('Content-Type: application/json; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: DENY');
    header('Referrer-Policy: no-referrer');
    header("Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
    header('Permissions-Policy: camera=(), geolocation=(), microphone=(), payment=(), usb=()');
    if (cc_is_https_request()) {
        header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
    }
}

function cc_presence_json(array $payload, int $status = 200): never
{
    cc_presence_api_headers();
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    exit;
}

function cc_presence_api_error(string $code, string $message, int $status): never
{
    cc_presence_json(['error' => ['code' => $code, 'message' => $message]], $status);
}

function cc_presence_decode_json_body(string $body, ?int $declaredLength = null): array
{
    if (
        ($declaredLength !== null && $declaredLength > CC_PRESENCE_MAX_BODY_BYTES)
        || strlen($body) > CC_PRESENCE_MAX_BODY_BYTES
    ) {
        throw new CcPresenceException('BODY_TOO_LARGE', 413, 'The request body is too large.');
    }
    if ($body === '') {
        throw new CcPresenceException('INVALID_REQUEST', 400, 'The request body is missing.');
    }
    try {
        $decoded = json_decode($body, true, 32, JSON_THROW_ON_ERROR);
    } catch (JsonException $exception) {
        throw new CcPresenceException('INVALID_JSON', 400, 'The request body is not valid JSON.');
    }
    if (!is_array($decoded) || array_is_list($decoded)) {
        throw new CcPresenceException('INVALID_REQUEST', 422, 'The request body must be a JSON object.');
    }
    return $decoded;
}

function cc_presence_require_request(): array
{
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
        header('Allow: POST');
        cc_presence_api_error('METHOD_NOT_ALLOWED', 'Presence endpoints accept POST only.', 405);
    }
    if (!cc_is_https_request() && PHP_SAPI !== 'cli') {
        cc_presence_api_error('HTTPS_REQUIRED', 'Presence requires HTTPS.', 400);
    }
    $contentType = strtolower(trim(explode(';', (string) ($_SERVER['CONTENT_TYPE'] ?? ''))[0]));
    if ($contentType !== 'application/json') {
        cc_presence_api_error('UNSUPPORTED_MEDIA_TYPE', 'Content-Type must be application/json.', 415);
    }
    $contentLength = filter_var($_SERVER['CONTENT_LENGTH'] ?? null, FILTER_VALIDATE_INT);
    if ($contentLength !== false && $contentLength > CC_PRESENCE_MAX_BODY_BYTES) {
        throw new CcPresenceException('BODY_TOO_LARGE', 413, 'The request body is too large.');
    }
    $body = file_get_contents('php://input', false, null, 0, CC_PRESENCE_MAX_BODY_BYTES + 1);
    if (!is_string($body)) {
        throw new RuntimeException('Presence request body could not be read.');
    }
    return cc_presence_decode_json_body($body, $contentLength === false ? null : $contentLength);
}

function cc_presence_bearer_token(): string
{
    $authorization = trim((string) ($_SERVER['HTTP_AUTHORIZATION'] ?? ''));
    if (!preg_match('/^Bearer\s+([A-Za-z0-9_-]{43,128})$/D', $authorization, $matches)) {
        cc_presence_api_error('UNAUTHORIZED', 'A valid Presence bearer token is required.', 401);
    }
    return $matches[1];
}

function cc_presence_client_identifier(): string
{
    $address = trim((string) ($_SERVER['REMOTE_ADDR'] ?? ''));
    return filter_var($address, FILTER_VALIDATE_IP) ? $address : 'unknown-client';
}

function cc_presence_dispatch(string $operation): never
{
    cc_presence_api_headers();
    try {
        $payload = cc_presence_require_request();
        $config = cc_presence_config();
        $client = cc_presence_client_identifier();
        if ($operation === 'start') {
            $response = cc_presence_start(
                cc_db('control'),
                cc_db('reseller'),
                $payload,
                $config,
                $client
            );
            unset($response['server_session_id']);
            cc_presence_json($response, 201);
        }
        $token = cc_presence_bearer_token();
        if ($operation === 'heartbeat') {
            cc_presence_json(cc_presence_heartbeat(cc_db('control'), $payload, $token, $config, $client));
        }
        if ($operation === 'end') {
            cc_presence_json(cc_presence_end(cc_db('control'), $payload, $token, $config, $client));
        }
        throw new LogicException('Unknown Presence operation.');
    } catch (CcPresenceException $exception) {
        cc_presence_api_error($exception->apiCode, $exception->getMessage(), $exception->httpStatus);
    } catch (Throwable $exception) {
        error_log('HULK Control Center Presence request failed.');
        cc_presence_api_error('SERVICE_UNAVAILABLE', 'Presence is temporarily unavailable.', 503);
    }
}
