<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';
require_once __DIR__ . '/pairing.php';

/**
 * TV JSON API transport for pairing. JSON scope rules apply only to these endpoints;
 * the public page uses HTML form responses.
 */

function cc_pairing_api_headers(): void
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

function cc_pairing_json(array $payload, int $status = 200): never
{
    cc_pairing_api_headers();
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    exit;
}

function cc_pairing_api_error(string $code, string $message, int $status, ?int $retryAfterSeconds = null): never
{
    $error = ['code' => $code, 'message' => $message];
    if ($retryAfterSeconds !== null) {
        $error['retryAfterSeconds'] = $retryAfterSeconds;
    }
    cc_pairing_json(['error' => $error], $status);
}

function cc_pairing_require_request(): array
{
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
        header('Allow: POST');
        cc_pairing_api_error('METHOD_NOT_ALLOWED', 'Pairing endpoints accept POST only.', 405);
    }
    if (!cc_is_https_request() && PHP_SAPI !== 'cli') {
        cc_pairing_api_error('HTTPS_REQUIRED', 'Pairing requires HTTPS.', 400);
    }
    $contentType = strtolower(trim(explode(';', (string) ($_SERVER['CONTENT_TYPE'] ?? ''))[0]));
    if ($contentType !== 'application/json') {
        cc_pairing_api_error('UNSUPPORTED_MEDIA_TYPE', 'Content-Type must be application/json.', 415);
    }
    $contentLength = filter_var($_SERVER['CONTENT_LENGTH'] ?? null, FILTER_VALIDATE_INT);
    if ($contentLength !== false && $contentLength > CC_PAIRING_MAX_BODY_BYTES) {
        cc_pairing_api_error('BODY_TOO_LARGE', 'The request body is too large.', 413);
    }
    $body = file_get_contents('php://input', false, null, 0, CC_PAIRING_MAX_BODY_BYTES + 1);
    if (!is_string($body) || $body === '') {
        cc_pairing_api_error('INVALID_REQUEST', 'A valid JSON body is required.', 400);
    }
    if (strlen($body) > CC_PAIRING_MAX_BODY_BYTES) {
        cc_pairing_api_error('BODY_TOO_LARGE', 'The request body is too large.', 413);
    }
    try {
        $decoded = json_decode($body, true, 16, JSON_THROW_ON_ERROR);
    } catch (JsonException) {
        cc_pairing_api_error('INVALID_REQUEST', 'The request body is not valid JSON.', 400);
    }
    if (!is_array($decoded) || array_is_list($decoded)) {
        cc_pairing_api_error('INVALID_REQUEST', 'The request body must be a JSON object.', 400);
    }
    return $decoded;
}

function cc_pairing_bearer_token(): string
{
    $authorization = trim((string) ($_SERVER['HTTP_AUTHORIZATION'] ?? ''));
    if (!preg_match('/^Bearer\s+([A-Za-z0-9_-]{43,128})$/D', $authorization, $matches)) {
        cc_pairing_api_error('UNAUTHORIZED', 'A valid pairing token is required.', 401);
    }
    return $matches[1];
}

function cc_pairing_dispatch(string $operation): never
{
    cc_pairing_api_headers();
    try {
        $settings = cc_pairing_settings();
        $now = cc_pairing_now();

        if ($operation === 'create') {
            cc_pairing_require_active($settings);
            $payload = cc_pairing_require_request();
            cc_pairing_rate_limit(
                cc_pairing_db(),
                'pairing_create_min',
                cc_pairing_context_identifier(),
                (int) $settings['rate_limits']['create_minute_per_ip'],
                60,
                (string) $settings['rate_limit_secret'],
                $now,
            );
            cc_pairing_rate_limit(
                cc_pairing_db(),
                'pairing_create_hour',
                cc_pairing_context_identifier(),
                (int) $settings['rate_limits']['create_hour_per_ip'],
                3600,
                (string) $settings['rate_limit_secret'],
                $now,
            );
            $result = cc_pairing_create(cc_pairing_db(), $payload, $settings, $now);
            cc_pairing_json($result['body'], $result['status']);
        }

        if ($operation === 'claim' || $operation === 'ack' || $operation === 'cancel') {
            if ($operation === 'claim') {
                cc_pairing_require_active($settings);
            }
            $token = cc_pairing_bearer_token();
            $payload = cc_pairing_require_request();
            $requestId = cc_pairing_validate_session_payload($payload);
            $requestKey = cc_pairing_request_identifier($requestId);

            if ($operation === 'claim') {
                // Claim IP guard runs first; per-request counters are only allocated
                // AFTER the bearer token is authenticated (invalid tokens create none).
                cc_pairing_rate_limit(
                    cc_pairing_db(),
                    'pairing_claim_ip',
                    cc_pairing_context_identifier(),
                    (int) $settings['rate_limits']['claim_minute_per_ip'],
                    60,
                    (string) $settings['rate_limit_secret'],
                    $now,
                );
                cc_pairing_authenticate(cc_pairing_db(), $requestId, $token);
                cc_pairing_rate_limit(
                    cc_pairing_db(),
                    'pairing_claim_req',
                    $requestKey,
                    (int) $settings['rate_limits']['claim_minute_per_request'],
                    60,
                    (string) $settings['rate_limit_secret'],
                    $now,
                );
                $result = cc_pairing_claim(cc_pairing_db(), $requestId, $token, $settings);
                $body = [
                    'contractVersion' => CC_PAIRING_CONTRACT_VERSION,
                    'state' => $result['state'],
                ];
                if ($result['state'] === 'PENDING' || $result['state'] === 'APPROVED') {
                    $body['expiresAtEpochMs'] = $result['expiresAtEpochMs'];
                    $body['retryAfterMs'] = $result['retryAfterMs'];
                }
                if ($result['state'] === 'APPROVED' && is_array($result['credentials'])) {
                    $body['deliveryCount'] = $result['deliveryCount'];
                    $body['credentials'] = [
                        'accessCode' => $result['credentials']['accessCode'],
                        'username' => $result['credentials']['username'],
                        'password' => $result['credentials']['password'],
                    ];
                }
                cc_pairing_json($body, 200);
            }

            if ($operation === 'ack') {
                cc_pairing_authenticate(cc_pairing_db(), $requestId, $token);
                cc_pairing_rate_limit(
                    cc_pairing_db(),
                    'pairing_ack_req',
                    $requestKey,
                    (int) $settings['rate_limits']['ack_minute_per_request'],
                    60,
                    (string) $settings['rate_limit_secret'],
                    $now,
                );
                $result = cc_pairing_ack(cc_pairing_db(), $requestId, $token);
                cc_pairing_json(['contractVersion' => CC_PAIRING_CONTRACT_VERSION, 'state' => $result['state']], 200);
            }

            cc_pairing_authenticate(cc_pairing_db(), $requestId, $token);
            cc_pairing_rate_limit(
                cc_pairing_db(),
                'pairing_cancel_req',
                $requestKey,
                (int) $settings['rate_limits']['cancel_minute_per_request'],
                60,
                (string) $settings['rate_limit_secret'],
                $now,
            );
            $result = cc_pairing_cancel(cc_pairing_db(), $requestId, $token);
            cc_pairing_json(['contractVersion' => CC_PAIRING_CONTRACT_VERSION, 'state' => $result['state']], 200);
        }

        throw new LogicException('Unknown pairing operation.');
    } catch (CcPairingException $exception) {
        cc_pairing_api_error(
            $exception->apiCode,
            $exception->getMessage(),
            $exception->httpStatus,
            $exception->retryAfterSeconds,
        );
    } catch (Throwable $exception) {
        error_log('HULK Control Center pairing request failed.');
        cc_pairing_api_error('SERVICE_UNAVAILABLE', 'Pairing is temporarily unavailable.', 503);
    }
}
