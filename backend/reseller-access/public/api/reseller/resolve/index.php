<?php

declare(strict_types=1);

require dirname(__DIR__, 3) . '/.hulk-reseller-app/bootstrap.php';

if (!hulk_is_https()) {
    hulk_api_error('HTTPS_REQUIRED', 'يجب استخدام اتصال HTTPS.', 426);
}
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    header('Allow: POST');
    hulk_api_error('METHOD_NOT_ALLOWED', 'هذه العملية تقبل POST فقط.', 405);
}

try {
    hulk_enforce_resolver_rate_limit();
} catch (Throwable) {
    hulk_api_error('SERVICE_UNAVAILABLE', 'خدمة HULK غير متاحة موقتا', 503);
}

$contentType = strtolower(trim(explode(';', (string) ($_SERVER['CONTENT_TYPE'] ?? ''))[0]));
if ($contentType !== 'application/json') {
    hulk_api_error('INVALID_REQUEST', 'يجب إرسال الطلب بصيغة JSON.', 415);
}

$declaredLength = (int) ($_SERVER['CONTENT_LENGTH'] ?? 0);
if ($declaredLength > 1024) {
    hulk_api_error('INVALID_REQUEST', 'حجم الطلب غير صالح.', 413);
}

$rawBody = file_get_contents('php://input', false, null, 0, 1025);
if (!is_string($rawBody) || $rawBody === '' || strlen($rawBody) > 1024) {
    hulk_api_error('INVALID_REQUEST', 'حجم الطلب غير صالح.', 400);
}

try {
    $payload = json_decode($rawBody, true, 8, JSON_THROW_ON_ERROR);
} catch (JsonException) {
    hulk_api_error('INVALID_REQUEST', 'تعذر قراءة الطلب.', 400);
}
if (!is_array($payload) || !is_string($payload['code'] ?? null)) {
    hulk_api_error('INVALID_CODE', 'كود الدخول غير صحيح.', 404);
}

$code = hulk_normalize_access_code($payload['code']);
if ($code === null) {
    hulk_api_error('INVALID_CODE', 'كود الدخول غير صحيح.', 404);
}

try {
    $reseller = hulk_host_code_lookup(hulk_db(), $code);
} catch (Throwable) {
    hulk_api_error('SERVICE_UNAVAILABLE', 'خدمة HULK غير متاحة مؤقتا.', 503);
}

if (!is_array($reseller)) {
    hulk_api_error('INVALID_CODE', 'كود الدخول غير صحيح.', 404);
}
if (($reseller['status'] ?? '') !== HULK_ACTIVE_STATUS) {
    hulk_api_error('RESELLER_INACTIVE', 'حساب الموزع متوقف.', 403);
}

$host = hulk_normalize_host((string) ($reseller['host'] ?? ''));
if ($host === null) {
    hulk_api_error('INVALID_HOST', 'مضيف الموزع غير صالح.', 422);
}

hulk_json(['host' => $host]);

