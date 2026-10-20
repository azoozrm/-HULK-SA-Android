<?php

declare(strict_types=1);

require_once __DIR__ . '/subscriber-identity.php';

const CC_SUBSCRIBER_CHECK_MAX_STORED_RESULTS = 20;

function cc_subscriber_check_store_result(array $subscription, array $result): void
{
    cc_start_admin_session();
    $key = cc_subscriber_identity_key(
        (int) ($subscription['reseller_id'] ?? 0),
        (string) ($subscription['host_snapshot'] ?? ''),
        (string) ($subscription['iptv_username'] ?? '')
    );
    if (!isset($_SESSION['cc_subscriber_check_results']) || !is_array($_SESSION['cc_subscriber_check_results'])) {
        $_SESSION['cc_subscriber_check_results'] = [];
    }
    $_SESSION['cc_subscriber_check_results'][$key] = $result;
    while (count($_SESSION['cc_subscriber_check_results']) > CC_SUBSCRIBER_CHECK_MAX_STORED_RESULTS) {
        array_shift($_SESSION['cc_subscriber_check_results']);
    }
}

function cc_subscriber_check_attach_results(array $pageData): array
{
    if (empty($pageData['groups']) || !is_array($pageData['groups'])) {
        return $pageData;
    }
    $stored = $_SESSION['cc_subscriber_check_results'] ?? [];
    if (!is_array($stored) || $stored === []) {
        return $pageData;
    }
    foreach ($pageData['groups'] as &$group) {
        $key = cc_subscriber_identity_key(
            (int) ($group['reseller_id'] ?? 0),
            (string) ($group['host_snapshot'] ?? ''),
            (string) ($group['iptv_username'] ?? '')
        );
        if (isset($stored[$key]) && is_array($stored[$key])) {
            $group['check_result'] = $stored[$key];
            unset($stored[$key]);
        }
    }
    unset($group);
    $_SESSION['cc_subscriber_check_results'] = $stored;
    return $pageData;
}
