<?php

declare(strict_types=1);

require_once __DIR__ . '/presence-http.php';
require_once __DIR__ . '/diagnostics.php';

const CC_DIAGNOSTICS_MAX_BODY_BYTES = 4096;

function cc_diagnostics_decode_body(string $body, ?int $contentLength = null): array
{
    if (
        ($contentLength !== null && $contentLength > CC_DIAGNOSTICS_MAX_BODY_BYTES)
        || strlen($body) > CC_DIAGNOSTICS_MAX_BODY_BYTES
    ) {
        throw new CcPresenceException('BODY_TOO_LARGE', 413, 'The request body is too large.');
    }
    return cc_presence_decode_json_body($body, $contentLength);
}

function cc_diagnostics_require_request(): array
{
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
        header('Allow: POST');
        cc_presence_api_error('METHOD_NOT_ALLOWED', 'Diagnostics accepts POST only.', 405);
    }
    if (!cc_is_https_request() && PHP_SAPI !== 'cli') {
        cc_presence_api_error('HTTPS_REQUIRED', 'Diagnostics requires HTTPS.', 400);
    }
    $contentType = strtolower(trim(explode(';', (string) ($_SERVER['CONTENT_TYPE'] ?? ''))[0]));
    if ($contentType !== 'application/json') {
        cc_presence_api_error('UNSUPPORTED_MEDIA_TYPE', 'Content-Type must be application/json.', 415);
    }
    $contentLength = filter_var($_SERVER['CONTENT_LENGTH'] ?? null, FILTER_VALIDATE_INT);
    $body = file_get_contents('php://input', false, null, 0, CC_DIAGNOSTICS_MAX_BODY_BYTES + 1);
    if (!is_string($body)) {
        throw new RuntimeException('Diagnostics request body could not be read.');
    }
    return cc_diagnostics_decode_body($body, $contentLength === false ? null : $contentLength);
}

function cc_diagnostics_dispatch(): never
{
    cc_presence_api_headers();
    try {
        $payload = cc_diagnostics_require_request();
        $response = cc_diagnostics_ingest(
            cc_db('control'),
            $payload,
            cc_presence_bearer_token(),
            cc_diagnostics_config(),
            cc_presence_client_identifier()
        );
        cc_presence_json($response, 202);
    } catch (CcPresenceException $exception) {
        cc_presence_api_error($exception->apiCode, $exception->getMessage(), $exception->httpStatus);
    } catch (Throwable $exception) {
        error_log('HULK Control Center Diagnostics request failed.');
        cc_presence_api_error('SERVICE_UNAVAILABLE', 'Diagnostics is temporarily unavailable.', 503);
    }
}
