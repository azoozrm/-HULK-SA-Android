<?php

declare(strict_types=1);

$subscriberRoot = dirname(__DIR__);
require_once $subscriberRoot . '/lib/subscriber.php';
require_once $subscriberRoot . '/lib/subscriber-check.php';

cc_test(function_exists('cc_subscriber_check_provider'), 'Subscriber provider check is available');
cc_test(function_exists('cc_subscriber_note_save'), 'Subscriber note persistence is available');
cc_test(function_exists('cc_subscriber_resolve_online_subscription'), 'Subscriber identity resolver is available');

$subPublicDns = static function (string $hostname, int $timeoutMs): array {
    return ['status' => 'ok', 'addresses' => ['93.184.216.34']];
};
$subPrivateDns = static function (string $hostname, int $timeoutMs): array {
    return ['status' => 'ok', 'addresses' => ['192.168.1.10']];
};
$subHttpResponse = static function (
    int $status,
    mixed $body,
    int $errno = 0,
    bool $overflowed = false
): callable {
    return static function (string $url, string $resolve) use ($status, $body, $errno, $overflowed): array {
        return [
            'errno' => $errno,
            'status' => $status,
            'body' => is_string($body) ? $body : json_encode($body),
            'primary_ip' => '93.184.216.34',
            'connect_time' => 0.1,
            'overflowed' => $overflowed,
        ];
    };
};

$subActive = cc_subscriber_check_provider(
    'https://provider.test:8443/prefix',
    'synthetic-user',
    'synthetic-password',
    $subPublicDns,
    $subHttpResponse(200, ['user_info' => ['auth' => 1, 'status' => 'Active', 'exp_date' => '1893456000']])
);
cc_test($subActive['state'] === 'ACTIVE', 'active provider response maps to active');
cc_test($subActive['provider_status'] === 'Active', 'provider status is retained');
cc_test($subActive['expires_at'] === 1893456000, 'valid numeric expiration is retained');
cc_test(!array_key_exists('password', $subActive) && !array_key_exists('username', $subActive), 'check result excludes credentials');

$subStatusUnavailableCases = [
    'blank' => ['status' => '', 'exp_date' => '1893456000'],
    'whitespace' => ['status' => " \t\n "],
    'null' => ['status' => null],
    'missing' => [],
    'null-string' => ['status' => 'null'],
    'integer' => ['status' => 1],
    'boolean' => ['status' => true],
    'array' => ['status' => ['Active']],
];
foreach ($subStatusUnavailableCases as $statusCase => $statusFields) {
    $statusUserInfo = ['auth' => 1] + $statusFields;
    set_error_handler(static function (int $severity, string $message): bool {
        throw new ErrorException($message, 0, $severity);
    });
    try {
        $subStatusResult = cc_subscriber_check_provider(
            'https://provider.test',
            'synthetic-user',
            'synthetic-password',
            $subPublicDns,
            $subHttpResponse(200, ['user_info' => $statusUserInfo])
        );
    } finally {
        restore_error_handler();
    }
    cc_test($subStatusResult['state'] === 'STATUS_UNAVAILABLE', $statusCase . ' status never becomes ACTIVE');
    cc_test($subStatusResult['provider_status'] === null, $statusCase . ' status is not presented as a provider status');
    if ($statusCase === 'blank') {
        cc_test($subStatusResult['expires_at'] === 1893456000, 'unavailable status preserves a valid expiration');
    }
}
cc_test(cc_subscriber_check_presentation('STATUS_UNAVAILABLE')['label'] === 'حالة الاشتراك غير متاحة', 'unavailable status has an honest Arabic label');

$subInactive = cc_subscriber_check_provider(
    'https://provider.test',
    'synthetic-user',
    'synthetic-password',
    $subPublicDns,
    $subHttpResponse(200, ['user_info' => ['auth' => 1, 'status' => 'Expired', 'exp_date' => '1609459200']])
);
cc_test($subInactive['state'] === 'INACTIVE', 'inactive provider status maps to inactive');
cc_test($subInactive['expires_at'] === 1609459200, 'expired subscription keeps its supplied expiration');

$subAuthRejected = cc_subscriber_check_provider(
    'https://provider.test',
    'synthetic-user',
    'synthetic-password',
    $subPublicDns,
    $subHttpResponse(200, ['user_info' => ['auth' => 0, 'status' => 'Active']])
);
cc_test($subAuthRejected['state'] === 'AUTH_REJECTED', 'provider auth rejection maps to authentication failure');

$subHttpAuth = cc_subscriber_check_provider(
    'https://provider.test',
    'synthetic-user',
    'synthetic-password',
    $subPublicDns,
    $subHttpResponse(401, ['error' => 'unauthorized'])
);
cc_test($subHttpAuth['state'] === 'AUTH_REJECTED', 'HTTP auth status maps to authentication failure');

$subTimeout = cc_subscriber_check_provider(
    'https://provider.test',
    'synthetic-user',
    'synthetic-password',
    $subPublicDns,
    $subHttpResponse(0, '', 28)
);
cc_test($subTimeout['state'] === 'TIMEOUT', 'transport timeout maps to timeout');

$subNetwork = cc_subscriber_check_provider(
    'https://provider.test',
    'synthetic-user',
    'synthetic-password',
    $subPublicDns,
    $subHttpResponse(0, '', 7)
);
cc_test($subNetwork['state'] === 'NETWORK_ERROR', 'transport error maps to network failure');

$subMalformed = cc_subscriber_check_provider(
    'https://provider.test',
    'synthetic-user',
    'synthetic-password',
    $subPublicDns,
    $subHttpResponse(200, '{not-json')
);
cc_test($subMalformed['state'] === 'INVALID_RESPONSE', 'malformed JSON maps to invalid response');

$subMissingUser = cc_subscriber_check_provider(
    'https://provider.test',
    'synthetic-user',
    'synthetic-password',
    $subPublicDns,
    $subHttpResponse(200, ['server_info' => []])
);
cc_test($subMissingUser['state'] === 'INVALID_RESPONSE', 'missing user_info maps to invalid response');

$subHttpFailure = cc_subscriber_check_provider(
    'https://provider.test',
    'synthetic-user',
    'synthetic-password',
    $subPublicDns,
    $subHttpResponse(500, ['error' => 'server'])
);
cc_test($subHttpFailure['state'] === 'UNAVAILABLE', 'non-auth HTTP failure maps to unavailable provider');

$subPrivateCalls = 0;
$subPrivate = cc_subscriber_check_provider(
    'https://provider.test',
    'synthetic-user',
    'synthetic-password',
    $subPrivateDns,
    static function (string $url, string $resolve) use (&$subPrivateCalls): array {
        $subPrivateCalls++;
        return ['errno' => 0, 'status' => 200, 'body' => '{}', 'primary_ip' => '192.168.1.10', 'connect_time' => 0.1, 'overflowed' => false];
    }
);
cc_test($subPrivate['state'] === 'UNAVAILABLE' && $subPrivate['reason'] === 'NON_PUBLIC_ADDRESS', 'private provider target is blocked');
cc_test($subPrivateCalls === 0, 'blocked provider target never makes an HTTP request');

cc_test(cc_subscriber_check_expiry('0') === null, 'zero expiration is unavailable');
cc_test(cc_subscriber_check_expiry('not-a-date') === null, 'non-numeric expiration is unavailable');
cc_test(cc_subscriber_check_expiry(null) === null, 'absent expiration is unavailable');
cc_test(cc_subscriber_check_expiry('1893456000') === 1893456000, 'numeric string expiration is accepted');

$subSession = new PDO('sqlite::memory:');
$subSession->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$subSession->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
$subSession->exec(
    'CREATE TABLE cc_app_sessions ('
    . 'id INTEGER PRIMARY KEY, session_id TEXT, device_id INTEGER, reseller_id INTEGER, '
    . 'access_code_snapshot TEXT, iptv_username TEXT, iptv_password TEXT, host_snapshot TEXT, '
    . 'last_seen_at TEXT, ended_at TEXT)'
);
$subRecent = cc_presence_datetime(cc_presence_now()->modify('-30 seconds'));
$subInsert = $subSession->prepare(
    'INSERT INTO cc_app_sessions '
    . '(id, session_id, device_id, reseller_id, access_code_snapshot, iptv_username, iptv_password, host_snapshot, last_seen_at, ended_at) '
    . 'VALUES (:id, :session_id, 1, 7, :code, :username, :password, :host, :last_seen, NULL)'
);
$subInsert->execute([
    'id' => 1,
    'session_id' => 'synthetic-session-1',
    'code' => 'CODE-ALICE-UPPER',
    'username' => 'Alice',
    'password' => 'synthetic-password-alice-upper',
    'host' => 'https://provider.test',
    'last_seen' => $subRecent,
]);
$subInsert->execute([
    'id' => 2,
    'session_id' => 'synthetic-session-2',
    'code' => 'CODE-ALICE-LOWER',
    'username' => 'alice',
    'password' => 'synthetic-password-alice-lower',
    'host' => 'https://provider.test',
    'last_seen' => $subRecent,
]);
$subInsert->execute([
    'id' => 3,
    'session_id' => 'synthetic-session-3',
    'code' => 'CODE-USER-E',
    'username' => 'user-e',
    'password' => 'synthetic-password-user-e',
    'host' => 'https://provider.test',
    'last_seen' => $subRecent,
]);
$subInsert->execute([
    'id' => 4,
    'session_id' => 'synthetic-session-4',
    'code' => 'CODE-USER-ACCENT',
    'username' => 'user-é',
    'password' => 'synthetic-password-user-accent',
    'host' => 'https://provider.test',
    'last_seen' => $subRecent,
]);
$subConfig = cc_presence_config([
    'presence' => [
        'heartbeat_seconds' => 60,
        'online_ttl_seconds' => 180,
        'session_retention_days' => 180,
        'device_retention_days' => 180,
        'cleanup_batch_size' => 200,
        'rate_limit_window_seconds' => 60,
        'rate_limits' => ['start' => 100, 'heartbeat' => 100, 'end' => 100],
        'rate_limit_secret' => str_repeat('s', 32),
        'token_secret' => str_repeat('t', 32),
    ],
]);
$subNow = new DateTimeImmutable('2026-09-19 12:00:00 UTC');
$subSelector = static function (int $resellerId, string $host, string $username): array {
    return [
        'reseller_id' => $resellerId,
        'host_hash' => strtoupper(bin2hex($host)),
        'user_hash' => strtoupper(bin2hex($username)),
    ];
};

$subAlice = cc_subscriber_resolve_online_subscription(
    $subSession,
    $subSelector(7, 'https://provider.test', 'Alice'),
    $subConfig
);
cc_test($subAlice['iptv_username'] === 'Alice' && $subAlice['iptv_password'] === 'synthetic-password-alice-upper', 'resolver preserves exact case identity');
$subAliceLower = cc_subscriber_resolve_online_subscription(
    $subSession,
    $subSelector(7, 'https://provider.test', 'alice'),
    $subConfig
);
cc_test($subAliceLower['iptv_username'] === 'alice' && $subAliceLower['iptv_password'] === 'synthetic-password-alice-lower', 'resolver separates case-distinct identity');
$subUserE = cc_subscriber_resolve_online_subscription(
    $subSession,
    $subSelector(7, 'https://provider.test', 'user-e'),
    $subConfig
);
$subUserAccent = cc_subscriber_resolve_online_subscription(
    $subSession,
    $subSelector(7, 'https://provider.test', 'user-é'),
    $subConfig
);
cc_test($subUserE['iptv_username'] === 'user-e' && $subUserAccent['iptv_username'] === 'user-é', 'resolver separates accent-distinct identity');
try {
    cc_subscriber_resolve_online_subscription(
        $subSession,
        $subSelector(7, 'https://provider.test', 'missing-user'),
        $subConfig
    );
    cc_test(false, 'unknown exact identity is rejected');
} catch (InvalidArgumentException $exception) {
    cc_test(true, 'unknown exact identity is rejected');
}

$subNotes = new PDO('sqlite::memory:');
$subNotes->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$subNotes->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
$subNotes->exec(
    'CREATE TABLE cc_subscriber_notes ('
    . 'reseller_id INTEGER NOT NULL, host_key TEXT NOT NULL, username_key TEXT NOT NULL, '
    . 'note_text TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, '
    . 'PRIMARY KEY (reseller_id, host_key, username_key))'
);
$subGroups = [
    ['reseller_id' => 7, 'host_snapshot' => 'https://provider.test', 'iptv_username' => 'Alice'],
    ['reseller_id' => 7, 'host_snapshot' => 'https://provider.test', 'iptv_username' => 'alice'],
    ['reseller_id' => 7, 'host_snapshot' => 'https://provider.test', 'iptv_username' => 'user-e'],
    ['reseller_id' => 7, 'host_snapshot' => 'https://provider.test', 'iptv_username' => 'user-é'],
    ['reseller_id' => 8, 'host_snapshot' => 'https://other.test', 'iptv_username' => 'Alice'],
];
$subSubscriptions = [];
foreach ($subGroups as $subGroup) {
    $subSubscriptions[] = $subGroup + ['iptv_password' => 'synthetic-password'];
}
cc_subscriber_note_save($subNotes, $subSubscriptions[0], 'ملاحظة Alice');
cc_subscriber_note_save($subNotes, $subSubscriptions[1], 'ملاحظة alice');
cc_subscriber_note_save($subNotes, $subSubscriptions[2], 'ملاحظة user-e');
cc_subscriber_note_save($subNotes, $subSubscriptions[3], 'ملاحظة user-é');
cc_subscriber_note_save($subNotes, $subSubscriptions[4], 'ملاحظة مورد آخر');
$subLoaded = cc_subscriber_notes_attach($subNotes, $subGroups);
cc_test($subLoaded['notes_available'] === true, 'notes table loads successfully');
$subNotesByKey = [];
foreach ($subLoaded['groups'] as $subGroup) {
    $subNotesByKey[$subGroup['reseller_id'] . '|' . $subGroup['iptv_username']] = $subGroup['note'];
}
cc_test(($subNotesByKey['7|Alice'] ?? '') === 'ملاحظة Alice', 'Alice note is isolated from alice');
cc_test(($subNotesByKey['7|alice'] ?? '') === 'ملاحظة alice', 'alice note is isolated from Alice');
cc_test(($subNotesByKey['7|user-e'] ?? '') === 'ملاحظة user-e', 'user-e note is isolated');
cc_test(($subNotesByKey['7|user-é'] ?? '') === 'ملاحظة user-é', 'user-é note is isolated');
cc_test(($subNotesByKey['8|Alice'] ?? '') === 'ملاحظة مورد آخر', 'same username under another reseller is isolated');

$subSubscriptions[0]['iptv_password'] = 'rotated-password';
cc_subscriber_note_save($subNotes, $subSubscriptions[0], 'ملاحظة Alice محدثة');
$subUpdated = cc_subscriber_notes_attach($subNotes, [$subGroups[0]]);
cc_test($subUpdated['groups'][0]['note'] === 'ملاحظة Alice محدثة', 'credential rotation does not detach the note');
cc_test((int) $subNotes->query('SELECT COUNT(*) FROM cc_subscriber_notes WHERE reseller_id = 7')->fetchColumn() === 4, 'note upsert does not duplicate identities');
cc_test(cc_e('<script>alert(1)</script>') === '&lt;script&gt;alert(1)&lt;/script&gt;', 'note text output escaping contract');

$subEscaped = '<b>private</b>';
cc_subscriber_note_save($subNotes, $subSubscriptions[1], $subEscaped);
$subEscapedLoad = cc_subscriber_notes_attach($subNotes, [$subGroups[1]]);
cc_test($subEscapedLoad['groups'][0]['note'] === $subEscaped && cc_e($subEscapedLoad['groups'][0]['note']) === '&lt;b&gt;private&lt;/b&gt;', 'note persistence preserves text and escaped rendering does not execute markup');

$subMissing = new PDO('sqlite::memory:');
$subMissing->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$subMissing->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
$subMissingLoad = cc_subscriber_notes_attach($subMissing, [$subGroups[0]]);
cc_test($subMissingLoad['notes_available'] === false && count($subMissingLoad['groups']) === 1, 'missing notes store does not break the subscription page');

try {
    cc_subscriber_note_save($subNotes, $subSubscriptions[0], str_repeat('x', 2001));
    cc_test(false, 'oversized note is rejected');
} catch (InvalidArgumentException $exception) {
    cc_test(true, 'oversized note is rejected');
}

$subSessionGroups = [[
    'reseller_id' => 7,
    'host_snapshot' => 'https://provider.test',
    'iptv_username' => 'Alice',
    'check_result' => ['state' => 'ACTIVE'],
]];
$subKey = cc_subscriber_identity_key(7, 'https://provider.test', 'Alice');
$_SESSION['cc_subscriber_check_results'] = [$subKey => ['state' => 'ACTIVE', 'provider_status' => 'Active']];
$_SESSION['cc_subscriber_note_drafts'] = [$subKey => 'draft text'];
$subState = ['groups' => $subSessionGroups];
$subState = cc_subscriber_attach_session_state($subState);
cc_test(($subState['groups'][0]['check_result']['state'] ?? '') === 'ACTIVE', 'session check result attaches to the exact card');
cc_test(($subState['groups'][0]['note_draft'] ?? '') === 'draft text', 'failed-note draft attaches to the exact card');
cc_test(($_SESSION['cc_subscriber_check_results'] ?? []) === [] && ($_SESSION['cc_subscriber_note_drafts'] ?? []) === [], 'session state is consumed after rendering');
