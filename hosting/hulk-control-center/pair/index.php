<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';
require_once dirname(__DIR__) . '/lib/pairing.php';
require_once dirname(__DIR__) . '/views/components.php';
require_once dirname(__DIR__) . '/views/pairing.php';

/**
 * Public TV-activation page (GET/POST /control-center/pair/).
 *
 * HTML form responses only. The page session is independent of admin/Presence
 * sessions, always uses a Secure scoped cookie, stores only CSRF/proof state and
 * never stores IPTV values. Insecure credential handling fails closed before any
 * session/form processing (the local cli/cli-server harness is exempt for tests).
 */

cc_pairing_page_headers();

$remoteAddress = (string) ($_SERVER['REMOTE_ADDR'] ?? '');
$localHarness = PHP_SAPI === 'cli-server'
    && in_array($remoteAddress, ['127.0.0.1', '::1'], true)
    && getenv('HULK_PAIRING_LOCAL_HARNESS') === '1';
if (!cc_is_https_request() && !$localHarness) {
    // Insecure rejection happens before any session/CSRF/credential handling.
    http_response_code(400);
    cc_pairing_render_page(['step' => 'insecure', 'notice' => null, 'csrf' => '']);
    exit;
}

$method = (string) ($_SERVER['REQUEST_METHOD'] ?? 'GET');
if (!in_array($method, ['GET', 'POST'], true)) {
    header('Allow: GET, POST');
    http_response_code(405);
    cc_pairing_render_page(['step' => 'unavailable', 'notice' => null, 'csrf' => '']);
    exit;
}
if ($method === 'POST') {
    $contentType = strtolower(trim(explode(';', (string) ($_SERVER['CONTENT_TYPE'] ?? ''))[0]));
    if ($contentType !== 'application/x-www-form-urlencoded') {
        http_response_code(415);
        cc_pairing_render_page(['step' => 'unavailable', 'notice' => null, 'csrf' => '']);
        exit;
    }
    $declaredLength = filter_var($_SERVER['CONTENT_LENGTH'] ?? null, FILTER_VALIDATE_INT);
    if ($declaredLength !== false && $declaredLength > CC_PAIRING_PAGE_MAX_BODY_BYTES) {
        http_response_code(413);
        cc_pairing_render_page(['step' => 'unavailable', 'notice' => null, 'csrf' => '']);
        exit;
    }
    // Bound the ACTUAL body even when Content-Length is absent or forged.
    $rawBody = file_get_contents('php://input', false, null, 0, CC_PAIRING_PAGE_MAX_BODY_BYTES + 1);
    if (is_string($rawBody) && strlen($rawBody) > CC_PAIRING_PAGE_MAX_BODY_BYTES) {
        http_response_code(413);
        cc_pairing_render_page(['step' => 'unavailable', 'notice' => null, 'csrf' => '']);
        exit;
    }
}

if (!cc_pairing_enabled()) {
    http_response_code(404);
    cc_pairing_render_page(['step' => 'disabled', 'notice' => null, 'csrf' => cc_pairing_csrf_token()]);
    exit;
}

cc_pairing_start_session();
$settings = cc_pairing_settings();

if (!cc_pairing_readiness($settings)['ready']) {
    http_response_code(503);
    cc_pairing_render_page([
        'step' => 'unavailable',
        'notice' => ['tone' => 'danger', 'message' => 'الميزة غير متاحة حاليا'],
        'csrf' => cc_pairing_csrf_token(),
    ]);
    exit;
}

$step = 'code';
$notice = null;
$request = null;
$requestId = null;
$formToken = null;
$maskedAccessCode = null;

function cc_pairing_page_request_context(array $row): array
{
    return [
        'userCode' => (string) $row['user_code'],
        'manufacturer' => (string) $row['device_manufacturer'],
        'model' => (string) $row['device_model'],
        'appVersion' => (string) $row['app_version'],
    ];
}

function cc_pairing_page_error_notice(CcPairingException $exception): array
{
    $message = match ($exception->apiCode) {
        'INVALID_USER_CODE' => 'الرمز غير صحيح',
        'PAIRING_EXPIRED' => 'انتهت صلاحية الرمز',
        'PAIRING_STATE_CONFLICT' => 'تم التعامل مع هذا الطلب مسبقا',
        'HOST_ROTATED' => 'تغير المضيف اثناء التحقق ، حاول مرة اخرى',
        'INVALID_ACCESS_CODE' => 'كود الدخول غير صحيح',
        'RESELLER_INACTIVE' => 'حساب الموزع متوقف. تواصل مع الدعم',
        'INVALID_HOST' => 'هوست الموزع غير صالح. تواصل مع الموزع',
        'INVALID_CREDENTIALS' => 'بيانات الاشتراك غير صحيحة او غير فعالة',
        'SUBSCRIPTION_INACTIVE' => 'الاشتراك منتهي او غير فعال. تواصل مع الدعم للتجديد',
        'SERVICE_BLOCKED' => 'الخدمة رفضت الاتصال مؤقتا ، جرب من شبكة اخرى او تواصل مع الدعم',
        'PROVIDER_UNREACHABLE', 'TARGET_POLICY_BLOCKED', 'PAIRING_UNAVAILABLE' => 'تعذر الاتصال بخدمة HULK ، حاول مرة اخرى',
        'RATE_LIMITED', 'DELIVERY_LIMIT' => 'حاول بعد قليل',
        'INVALID_REQUEST' => 'تحقق من البيانات المدخلة',
        default => 'تعذر اكمال العملية ، حاول مرة اخرى',
    };
    return ['tone' => 'danger', 'message' => $message];
}

function cc_pairing_page_load_pending(string $requestId): ?array
{
    try {
        $row = cc_pairing_load_by_request_id(cc_pairing_db(), $requestId);
    } catch (Throwable) {
        return null;
    }
    if (
        $row === null
        || (string) $row['state'] !== 'PENDING'
        || (new DateTimeImmutable((string) $row['expires_at'], new DateTimeZone('UTC')))->getTimestamp() <= time()
    ) {
        return null;
    }
    return cc_pairing_page_request_context($row);
}

if ($method === 'POST') {
    $csrf = is_string($_POST['csrf_token'] ?? null) ? (string) $_POST['csrf_token'] : null;
    $action = is_string($_POST['action'] ?? null) ? (string) $_POST['action'] : '';
    $postedRequestId = is_string($_POST['request_id'] ?? null) ? (string) $_POST['request_id'] : '';
    $postedFormToken = is_string($_POST['form_token'] ?? null) ? (string) $_POST['form_token'] : null;
    $submittedAccessCode = is_string($_POST['access_code'] ?? null) ? (string) $_POST['access_code'] : '';

    if (!cc_pairing_verify_csrf($csrf)) {
        http_response_code(419);
        cc_pairing_render_page([
            'step' => 'code',
            'notice' => ['tone' => 'danger', 'message' => 'انتهت صلاحية الطلب ، اعد تحميل الصفحة وحاول مرة اخرى'],
            'csrf' => cc_pairing_csrf_token(),
        ]);
        exit;
    }

    try {
        if ($action === 'lookup') {
            cc_pairing_rate_limit(
                cc_pairing_db(),
                'pairing_page_lookup',
                cc_pairing_context_identifier(),
                (int) $settings['rate_limits']['page_lookup_minute_per_ip'],
                60,
                (string) $settings['rate_limit_secret'],
                cc_pairing_now(),
            );
            $code = cc_pairing_normalize_user_code(is_string($_POST['code'] ?? null) ? (string) $_POST['code'] : '');
            if ($code === null) {
                throw new CcPairingException('INVALID_USER_CODE', 404, 'Invalid pairing code.');
            }
            $row = cc_pairing_load_by_user_code(cc_pairing_db(), $code);
            if ($row === null) {
                throw new CcPairingException('INVALID_USER_CODE', 404, 'Invalid pairing code.');
            }
            if ((string) $row['state'] !== 'PENDING') {
                throw new CcPairingException('PAIRING_STATE_CONFLICT', 409, 'Request is not pending.');
            }
            if ((new DateTimeImmutable((string) $row['expires_at'], new DateTimeZone('UTC')))->getTimestamp() <= time()) {
                throw new CcPairingException('PAIRING_EXPIRED', 410, 'Request expired.');
            }
            $requestId = (string) $row['request_id'];
            $formToken = cc_pairing_form_proof_issue($requestId);
            $step = 'confirm';
            $request = cc_pairing_page_request_context($row);
        } elseif ($action === 'approve' || $action === 'deny') {
            if (
                $postedRequestId === ''
                || !cc_pairing_form_proof_verify($postedRequestId, $postedFormToken)
            ) {
                throw new CcPairingException('PAIRING_STATE_CONFLICT', 409, 'Form proof is stale.');
            }
            // The verified proof is the authorization target: always act on the
            // request that was rendered for this form, never a mutable session id.
            $requestId = $postedRequestId;

            if ($action === 'deny') {
                $result = cc_pairing_deny(cc_pairing_db(), $requestId);
                if ($result['denied']) {
                    $step = 'denied';
                    $requestId = null;
                } elseif ($result['state'] === 'EXPIRED') {
                    $notice = ['tone' => 'danger', 'message' => 'انتهت صلاحية الرمز'];
                    $step = 'code';
                } else {
                    $notice = ['tone' => 'danger', 'message' => 'تم التعامل مع هذا الطلب مسبقا'];
                    $step = 'code';
                }
            } else {
                $username = is_string($_POST['username'] ?? null) ? trim((string) $_POST['username']) : '';
                $password = is_string($_POST['password'] ?? null) ? (string) $_POST['password'] : '';
                cc_pairing_rate_limit(
                    cc_pairing_db(),
                    'pairing_page_approve_ip',
                    cc_pairing_context_identifier(),
                    (int) $settings['rate_limits']['page_approve_minute_per_ip'],
                    60,
                    (string) $settings['rate_limit_secret'],
                    cc_pairing_now(),
                );
                cc_pairing_rate_limit(
                    cc_pairing_db(),
                    'pairing_page_approve_req',
                    cc_pairing_request_identifier($requestId),
                    (int) $settings['rate_limits']['page_approve_minute_per_request'],
                    60,
                    (string) $settings['rate_limit_secret'],
                    cc_pairing_now(),
                );
                cc_pairing_approve(
                    cc_pairing_db(),
                    $requestId,
                    $submittedAccessCode,
                    $username,
                    $password,
                    $settings,
                );
                $step = 'approved';
                $requestId = null;
            }
        } elseif ($action === 'change') {
            cc_pairing_form_proof_clear();
            $step = 'code';
        } else {
            throw new CcPairingException('INVALID_REQUEST', 400, 'Unknown action.');
        }
    } catch (CcPairingException $exception) {
        if ($exception->retryAfterSeconds !== null) {
            header('Retry-After: ' . $exception->retryAfterSeconds);
            http_response_code(429);
        }
        if ($exception->apiCode === 'PAIRING_DISABLED') {
            http_response_code(404);
            cc_pairing_render_page(['step' => 'disabled', 'notice' => null, 'csrf' => cc_pairing_csrf_token()]);
            exit;
        }
        $notice = cc_pairing_page_error_notice($exception);
        $terminal = in_array($exception->apiCode, [
            'PAIRING_EXPIRED',
            'PAIRING_STATE_CONFLICT',
            'INVALID_USER_CODE',
            'HOST_ROTATED',
            'INVALID_ACCESS_CODE',
            'RESELLER_INACTIVE',
            'INVALID_HOST',
            'SUBSCRIPTION_INACTIVE',
        ], true);
        if ($action === 'lookup' || $terminal || $requestId === null) {
            $step = 'code';
        } else {
            // Transient failure on a bound confirm form: re-render the same request
            // with a fresh proof, fields cleared and the access code masked.
            $step = 'confirm';
            $context = cc_pairing_page_load_pending($requestId);
            if ($context === null) {
                $step = 'code';
                $requestId = null;
            } else {
                $request = $context;
                $formToken = cc_pairing_form_proof_issue($requestId);
                $maskedAccessCode = $submittedAccessCode !== '' ? cc_pairing_mask_access_code($submittedAccessCode) : null;
            }
        }
    } catch (Throwable $exception) {
        error_log('HULK Control Center pairing page failed.');
        $notice = ['tone' => 'danger', 'message' => 'تعذر الاتصال بخدمة HULK ، حاول مرة اخرى'];
        $step = 'confirm';
        $context = $requestId !== null ? cc_pairing_page_load_pending($requestId) : null;
        if ($context === null) {
            $step = 'code';
            $requestId = null;
        } else {
            $request = $context;
            $formToken = cc_pairing_form_proof_issue($requestId);
            $maskedAccessCode = $submittedAccessCode !== '' ? cc_pairing_mask_access_code($submittedAccessCode) : null;
        }
    }
}

// GET always starts at code entry; a previous confirm is never silently resumed,
// so a new QR fragment cannot leave an earlier TV silently selected.
if ($method === 'GET') {
    cc_pairing_form_proof_clear();
}

cc_pairing_render_page([
    'step' => $step,
    'notice' => $notice,
    'request' => $request,
    'csrf' => cc_pairing_csrf_token(),
    'requestId' => $requestId,
    'formToken' => $formToken,
    'maskedAccessCode' => $maskedAccessCode,
]);
