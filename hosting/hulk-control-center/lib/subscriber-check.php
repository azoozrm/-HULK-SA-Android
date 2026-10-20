<?php

declare(strict_types=1);

require_once __DIR__ . '/pairing.php';
require_once __DIR__ . '/subscriber-identity.php';
require_once __DIR__ . '/subscriber-result.php';

const CC_SUBSCRIBER_CHECK_CONNECT_TIMEOUT_MS = 3000;
const CC_SUBSCRIBER_CHECK_REQUEST_TIMEOUT_MS = 8000;
const CC_SUBSCRIBER_CHECK_BODY_LIMIT = 65536;

function cc_subscriber_check_provider(
    string $host,
    string $username,
    string $password,
    ?callable $dnsLookup = null,
    ?callable $httpGet = null
): array {
    $target = cc_pairing_provider_target($host, $username, $password);
    if ($target === null) {
        return cc_subscriber_check_failure('UNAVAILABLE', 'INVALID_TARGET');
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
            return cc_subscriber_check_failure('NETWORK_ERROR', 'DNS_FAILURE');
        }
        $addresses = $lookup['addresses'];
    }
    if ($addresses === []) {
        return cc_subscriber_check_failure('NETWORK_ERROR', 'DNS_FAILURE');
    }
    foreach ($addresses as $address) {
        if (!is_string($address) || !cc_host_health_public_ip($address)) {
            return cc_subscriber_check_failure('UNAVAILABLE', 'NON_PUBLIC_ADDRESS');
        }
    }
    sort($addresses, SORT_STRING);
    $pinned = str_contains($addresses[0], ':') ? '[' . $addresses[0] . ']' : $addresses[0];
    $resolve = $target['resolve_host'] . ':' . $port . ':' . $pinned;

    $response = $httpGet !== null
        ? $httpGet($target['target'], $resolve)
        : cc_subscriber_http_get($target['target'], $resolve);
    if (!is_array($response)) {
        return cc_subscriber_check_failure('NETWORK_ERROR', 'TRANSPORT_FAILURE');
    }
    if (($response['overflowed'] ?? false) === true) {
        return cc_subscriber_check_failure('INVALID_RESPONSE', 'RESPONSE_TOO_LARGE');
    }
    $errno = (int) ($response['errno'] ?? 1);
    if ($errno !== 0) {
        if (defined('CURLE_OPERATION_TIMEDOUT') && $errno === CURLE_OPERATION_TIMEDOUT) {
            return cc_subscriber_check_failure('TIMEOUT', 'TIMEOUT');
        }
        return cc_subscriber_check_failure('NETWORK_ERROR', 'TRANSPORT_ERROR');
    }
    $status = (int) ($response['status'] ?? 0);
    $body = (string) ($response['body'] ?? '');
    if (strlen($body) > CC_SUBSCRIBER_CHECK_BODY_LIMIT) {
        return cc_subscriber_check_failure('INVALID_RESPONSE', 'RESPONSE_TOO_LARGE');
    }
    if ($status === 401 || $status === 403) {
        return cc_subscriber_check_failure('AUTH_REJECTED', 'HTTP_AUTH_STATUS', $status);
    }
    if ($status < 200 || $status >= 300) {
        return cc_subscriber_check_failure('UNAVAILABLE', 'HTTP_STATUS', $status);
    }

    $decoded = json_decode($body, true);
    if (!is_array($decoded)) {
        return cc_subscriber_check_failure('INVALID_RESPONSE', 'NON_JSON_RESPONSE', $status);
    }
    $user = $decoded['user_info'] ?? null;
    if (!is_array($user)) {
        return cc_subscriber_check_failure('INVALID_RESPONSE', 'MISSING_USER_INFO', $status);
    }
    if ((int) ($user['auth'] ?? 0) !== 1) {
        return cc_subscriber_check_failure('AUTH_REJECTED', 'AUTH_REJECTED', $status);
    }

    $expiresAt = cc_subscriber_check_expiry($user['exp_date'] ?? null);
    $rawStatus = $user['status'] ?? null;
    if (!is_string($rawStatus)) {
        return cc_subscriber_check_status_unavailable($expiresAt, $status);
    }
    $providerStatus = trim($rawStatus);
    if ($providerStatus === '' || strcasecmp($providerStatus, 'null') === 0) {
        return cc_subscriber_check_status_unavailable($expiresAt, $status);
    }
    $providerStatus = function_exists('mb_substr')
        ? mb_substr($providerStatus, 0, CC_SUBSCRIBER_CHECK_MAX_STATUS_LENGTH, 'UTF-8')
        : substr($providerStatus, 0, CC_SUBSCRIBER_CHECK_MAX_STATUS_LENGTH);
    $inactive = strcasecmp($providerStatus, 'Active') !== 0;

    return [
        'state' => $inactive ? 'INACTIVE' : 'ACTIVE',
        'reason' => $inactive ? 'STATUS_INACTIVE' : 'ACTIVE',
        'provider_status' => $providerStatus,
        'expires_at' => $expiresAt,
        'http_status' => $status,
    ];
}

function cc_subscriber_check_status_unavailable(?int $expiresAt, ?int $httpStatus): array
{
    return [
        'state' => 'STATUS_UNAVAILABLE',
        'reason' => 'STATUS_UNAVAILABLE',
        'provider_status' => null,
        'expires_at' => $expiresAt,
        'http_status' => $httpStatus,
    ];
}

function cc_subscriber_check_failure(string $state, string $reason, ?int $httpStatus = null): array
{
    return [
        'state' => $state,
        'reason' => $reason,
        'provider_status' => null,
        'expires_at' => null,
        'http_status' => $httpStatus,
    ];
}

function cc_subscriber_http_get(string $url, string $resolve): array
{
    $collector = cc_pairing_response_collector(CC_SUBSCRIBER_CHECK_BODY_LIMIT);
    $handle = curl_init($url);
    if ($handle === false) {
        return ['errno' => 1, 'status' => 0, 'body' => '', 'primary_ip' => '', 'connect_time' => 0.0, 'overflowed' => false];
    }
    curl_setopt_array($handle, [
        CURLOPT_FOLLOWLOCATION => false,
        CURLOPT_PROXY => '',
        CURLOPT_CONNECTTIMEOUT_MS => CC_SUBSCRIBER_CHECK_CONNECT_TIMEOUT_MS,
        CURLOPT_TIMEOUT_MS => CC_SUBSCRIBER_CHECK_REQUEST_TIMEOUT_MS,
        CURLOPT_NOSIGNAL => true,
        CURLOPT_PROTOCOLS => CURLPROTO_HTTP | CURLPROTO_HTTPS,
        CURLOPT_RESOLVE => [$resolve],
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_SSL_VERIFYHOST => 2,
        CURLOPT_USERAGENT => 'HULK-SA-Subscriber-Check/1.0',
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
