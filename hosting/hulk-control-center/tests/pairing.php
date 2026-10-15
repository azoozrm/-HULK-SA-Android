<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/lib/pairing.php';
if (getenv('HULK_PAIRING_TEST_FIXTURE') === '1' && !class_exists('PDO', false)) {
    require_once __DIR__ . '/pairing-db-fixture.php';
}

/**
 * Focused synthetic tests for the pairing backend. No database is available in the
 * local lab, so database-bound behavior is covered by contract tests and marked
 * BLOCKED for real MySQL/MariaDB locking evidence.
 */

$pairingKeyA = base64_encode(str_repeat('A', 32));
$pairingKeyB = base64_encode(str_repeat('B', 32));
$pairingClaimSecret = base64_encode(random_bytes(32));
$pairingRateSecret = base64_encode(random_bytes(32));
$pairingSettings = [
    'enabled' => true,
    'ttl_seconds' => 600,
    'retention_seconds' => 86400,
    'max_retained_rows' => 5000,
    'max_deliveries' => 20,
    'poll_interval_seconds' => 3,
    'claim_secret' => $pairingClaimSecret,
    'rate_limit_secret' => $pairingRateSecret,
    'envelope_keys' => ['v1' => $pairingKeyA, 'v2' => $pairingKeyB],
    'active_envelope_key_id' => 'v1',
    'rate_limits' => [
        'create_minute_per_ip' => 30,
        'create_hour_per_ip' => 300,
        'claim_minute_per_request' => 40,
        'claim_minute_per_ip' => 600,
        'ack_minute_per_request' => 10,
        'cancel_minute_per_request' => 10,
        'page_lookup_minute_per_ip' => 30,
        'page_approve_minute_per_ip' => 20,
        'page_approve_minute_per_request' => 10,
    ],
];

cc_test(cc_pairing_readiness($pairingSettings)['ready'], '32-byte envelope keys and >=32-byte secrets are ready');

$badSettings = $pairingSettings;
$badSettings['envelope_keys'] = ['v1' => base64_encode(str_repeat('A', 31))];
$badReadiness = cc_pairing_readiness($badSettings);
cc_test(!$badReadiness['ready'] && in_array('ENVELOPE_KEY_INVALID', $badReadiness['errors'], true), 'a 31-byte envelope key is rejected');

$badSettings = $pairingSettings;
$badSettings['claim_secret'] = '';
cc_test(in_array('CLAIM_SECRET_INVALID', cc_pairing_readiness($badSettings)['errors'], true), 'a missing claim secret is rejected');

$badSettings = $pairingSettings;
$badSettings['active_envelope_key_id'] = 'v9';
cc_test(in_array('ENVELOPE_ACTIVE_KEY_MISSING', cc_pairing_readiness($badSettings)['errors'], true), 'an unknown active envelope key is rejected');

$badSettings = $pairingSettings;
$badSettings['max_retained_rows'] = 1;
cc_test(in_array('CAPACITY_OUT_OF_RANGE', cc_pairing_readiness($badSettings)['errors'], true), 'an out-of-range capacity bound is rejected');

$pairingRequestId = '11111111-2222-4333-8444-555555555555';
$pairingExpires = '2026-10-14T00:10:00Z';
$pairingCredentials = [
    'accessCode' => 'HULK-TEST-0000-0000-0000',
    'username' => 'synthetic-user',
    'password' => 'synthetic-pass',
];
$pairingSealed = cc_pairing_seal_credentials($pairingCredentials, $pairingRequestId, $pairingExpires, $pairingSettings);
cc_test(
    in_array($pairingSealed['version'], [1, 2], true) && $pairingSealed['key_id'] === 'v1' && strlen($pairingSealed['blob']) > 30,
    'envelope seals with the active key'
);
cc_test(
    cc_pairing_unseal_credentials($pairingSealed['blob'], $pairingRequestId, $pairingExpires, $pairingSettings, $pairingSealed['version'], $pairingSealed['key_id']) === $pairingCredentials,
    'envelope round trip returns the exact credentials'
);
cc_test(
    cc_pairing_unseal_credentials($pairingSealed['blob'], '99999999-2222-4333-8444-555555555555', $pairingExpires, $pairingSettings, $pairingSealed['version'], $pairingSealed['key_id']) === null,
    'AAD request binding rejects an envelope moved to another request'
);
cc_test(
    cc_pairing_unseal_credentials($pairingSealed['blob'], $pairingRequestId, '2026-10-14T00:11:00Z', $pairingSettings, $pairingSealed['version'], $pairingSealed['key_id']) === null,
    'AAD expiry binding rejects a replayed envelope'
);
cc_test(
    cc_pairing_unseal_credentials($pairingSealed['blob'], $pairingRequestId, $pairingExpires, $pairingSettings, $pairingSealed['version'], 'v2') === null,
    'AAD key-id binding rejects a different key id'
);
$pairingTampered = $pairingSealed['blob'];
$pairingTampered[strlen($pairingTampered) - 1] = chr(ord($pairingTampered[strlen($pairingTampered) - 1]) ^ 0x01);
cc_test(
    cc_pairing_unseal_credentials($pairingTampered, $pairingRequestId, $pairingExpires, $pairingSettings, $pairingSealed['version'], $pairingSealed['key_id']) === null,
    'a tampered envelope fails authentication'
);

$pairingTokenA = cc_pairing_claim_token($pairingClaimSecret, $pairingRequestId, base64_encode(str_repeat('C', 32)));
$pairingTokenB = cc_pairing_claim_token($pairingClaimSecret, $pairingRequestId, base64_encode(str_repeat('C', 32)));
cc_test($pairingTokenA === $pairingTokenB, 'claim token derivation is deterministic for idempotent replay');
cc_test(
    $pairingTokenA !== cc_pairing_claim_token($pairingClaimSecret, '99999999-2222-4333-8444-555555555555', base64_encode(str_repeat('C', 32))),
    'claim token derivation binds the request id'
);
cc_test(strlen(cc_pairing_token_hash($pairingTokenA)) === 64, 'only the SHA-256 token hash is persisted');

$pairingUuid = cc_pairing_uuid4();
cc_test(cc_pairing_is_uuid($pairingUuid), 'generated request identifiers are UUID v4');

cc_test(
    cc_pairing_generate_user_code(static fn (int $n): int => 0) === 'HULKTV-AAAAAA',
    'user codes use the HULK alphabet deterministically'
);
cc_test(cc_pairing_normalize_user_code('hulktv aaaaaa') === 'HULKTV-AAAAAA', 'manual code entry is normalized');
cc_test(cc_pairing_normalize_user_code('HULK-AAAA-AAAA-AAAA-AAAA') === null, 'an access code is not a pairing code');
cc_test(cc_pairing_normalize_user_code('HULKTV-A1A1A1') === null, 'excluded alphabet characters are rejected');

$pairingCreateInput = [
    'contractVersion' => 1,
    'clientRequestId' => $pairingUuid,
    'clientSecret' => rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '='),
    'installationId' => '11111111-2222-4333-8444-555555555555',
    'device' => ['platform' => 'TV', 'manufacturer' => 'Example', 'model' => 'ExampleTV', 'appVersion' => '0.0.0'],
    'requestedAtEpochMs' => 0,
];
$pairingValidated = cc_pairing_validate_create_input($pairingCreateInput);
cc_test($pairingValidated['device']['platform'] === 'TV' && $pairingValidated['device']['model'] === 'ExampleTV', 'a valid create payload normalizes');

$rejected = false;
try {
    cc_pairing_validate_create_input($pairingCreateInput + ['extra' => true]);
} catch (CcPairingException $exception) {
    $rejected = $exception->apiCode === 'INVALID_REQUEST' && $exception->httpStatus === 400;
}
cc_test($rejected, 'unexpected create fields are rejected');

$rejected = false;
try {
    cc_pairing_validate_create_input(array_replace($pairingCreateInput, ['contractVersion' => 2]));
} catch (CcPairingException $exception) {
    $rejected = $exception->apiCode === 'CONTRACT_VERSION_UNSUPPORTED' && $exception->httpStatus === 409;
}
cc_test($rejected, 'an unsupported contract version is rejected');

$rejected = false;
try {
    cc_pairing_validate_create_input(array_replace($pairingCreateInput, ['clientSecret' => base64_encode(random_bytes(31))]));
} catch (CcPairingException $exception) {
    $rejected = $exception->apiCode === 'INVALID_REQUEST';
}
cc_test($rejected, 'a client secret that is not exactly 32 bytes is rejected');

$rejected = false;
try {
    cc_pairing_validate_create_input(array_replace($pairingCreateInput, ['device' => ['platform' => 'PHONE']]));
} catch (CcPairingException $exception) {
    $rejected = $exception->apiCode === 'INVALID_REQUEST';
}
cc_test($rejected, 'non-TV pairing creation is rejected');

cc_test(cc_pairing_validate_session_payload(['contractVersion' => 1, 'requestId' => $pairingUuid]) === $pairingUuid, 'a valid session payload normalizes');
$rejected = false;
try {
    cc_pairing_validate_session_payload(['contractVersion' => 1, 'requestId' => 'not-a-uuid']);
} catch (CcPairingException $exception) {
    $rejected = $exception->apiCode === 'INVALID_REQUEST';
}
cc_test($rejected, 'an invalid request id is rejected');

$rejected = false;
try {
    cc_pairing_validate_credentials_input('code', 'user', "bad\x00pass");
} catch (CcPairingException $exception) {
    $rejected = $exception->apiCode === 'INVALID_REQUEST';
}
cc_test($rejected, 'control characters in credentials are rejected');

cc_test(cc_pairing_mask_access_code('HULK-ABCD-EFGH-JKMN-PQRS') === 'HULK-****-****-****-PQRS', 'access-code display is masked to the tail only');

$publicDns = static fn (string $hostname, int $timeoutMs): array => ['status' => 'ok', 'addresses' => ['93.184.216.34']];
$privateDns = static fn (string $hostname, int $timeoutMs): array => ['status' => 'ok', 'addresses' => ['192.168.1.10']];
$unavailableDns = static fn (string $hostname, int $timeoutMs): array => ['status' => 'unavailable', 'addresses' => []];
$providerHttp = static fn (string $url, string $resolve, array $settings): array => [
    'errno' => 0,
    'status' => 200,
    'body' => json_encode(['user_info' => ['auth' => 1, 'status' => 'Active']]),
    'primary_ip' => '93.184.216.34',
    'connect_time' => 0.1,
];

$pairingProbe = cc_pairing_probe_provider('http://example.test', 'user', 'pass', $pairingSettings, $publicDns, $providerHttp);
cc_test($pairingProbe['result'] === 'OK', 'auth=1 with Active status is accepted');

$blankStatusHttp = static fn (string $url, string $resolve, array $settings): array => [
    'errno' => 0, 'status' => 200,
    'body' => json_encode(['user_info' => ['auth' => 1]]),
    'primary_ip' => '93.184.216.34', 'connect_time' => 0.1,
];
cc_test(
    cc_pairing_probe_provider('http://example.test', 'user', 'pass', $pairingSettings, $publicDns, $blankStatusHttp)['result'] === 'OK',
    'blank status is accepted as Active (exact Xtream parity)'
);

$inactiveHttp = static fn (string $url, string $resolve, array $settings): array => [
    'errno' => 0, 'status' => 200,
    'body' => json_encode(['user_info' => ['auth' => 1, 'status' => 'Expired']]),
    'primary_ip' => '93.184.216.34', 'connect_time' => 0.1,
];
cc_test(
    cc_pairing_probe_provider('http://example.test', 'user', 'pass', $pairingSettings, $publicDns, $inactiveHttp)['result'] === 'SUBSCRIPTION_INACTIVE',
    'a non-empty non-Active status is rejected'
);

$rejectedAuthHttp = static fn (string $url, string $resolve, array $settings): array => [
    'errno' => 0, 'status' => 200,
    'body' => json_encode(['user_info' => ['auth' => 0, 'status' => 'Active']]),
    'primary_ip' => '93.184.216.34', 'connect_time' => 0.1,
];
cc_test(
    cc_pairing_probe_provider('http://example.test', 'user', 'pass', $pairingSettings, $publicDns, $rejectedAuthHttp)['result'] === 'INVALID_CREDENTIALS',
    'auth other than 1 is rejected'
);

$blockedHttp = static fn (string $url, string $resolve, array $settings): array => [
    'errno' => 0, 'status' => 200, 'body' => '<html>challenge</html>',
    'primary_ip' => '93.184.216.34', 'connect_time' => 0.1,
];
cc_test(
    cc_pairing_probe_provider('http://example.test', 'user', 'pass', $pairingSettings, $publicDns, $blockedHttp)['result'] === 'SERVICE_BLOCKED',
    'a challenge/non-JSON response is classified as blocked'
);

$transportHttp = static fn (string $url, string $resolve, array $settings): array => [
    'errno' => 7, 'status' => 0, 'body' => '', 'primary_ip' => '', 'connect_time' => 0.0,
];
cc_test(
    cc_pairing_probe_provider('http://example.test', 'user', 'pass', $pairingSettings, $publicDns, $transportHttp)['result'] === 'PROVIDER_UNREACHABLE',
    'a transport failure is classified truthfully'
);

cc_test(
    cc_pairing_probe_provider('http://example.test', 'user', 'pass', $pairingSettings, $privateDns, $providerHttp)['result'] === 'TARGET_POLICY_BLOCKED',
    'a non-public resolved address is policy-blocked before any connection'
);
cc_test(
    cc_pairing_probe_provider('http://example.test', 'user', 'pass', $pairingSettings, $unavailableDns, $providerHttp)['result'] === 'PAIRING_UNAVAILABLE',
    'a missing bounded DNS runtime fails closed as unavailable'
);

$pairingAuthorityDirectory = sys_get_temp_dir() . '/hulk-pairing-authority-' . bin2hex(random_bytes(6));
cc_test(mkdir($pairingAuthorityDirectory, 0700), 'isolated pairing authority directory is created');
$pairingAuthorityPath = $pairingAuthorityDirectory . '/config.php';
$pairingAuthorityConfig = static function (bool $enabled): string {
    return '<?php return ' . var_export([
        'databases' => [
            'control' => ['dsn' => 'test:', 'username' => '', 'password' => '', 'options' => []],
            'reseller' => ['dsn' => 'test:', 'username' => '', 'password' => '', 'options' => []],
        ],
        'app' => ['base_url' => 'https://hulksa.com/control-center'],
        'pairing' => ['enabled' => $enabled],
    ], true) . ';';
};
file_put_contents($pairingAuthorityPath, $pairingAuthorityConfig(false));
$pairingAuthorityPriorConfig = getenv('HULK_CONTROL_CENTER_CONFIG');
putenv('HULK_CONTROL_CENTER_CONFIG=' . $pairingAuthorityPath);
cc_test(cc_pairing_authority(true)['enabled'] === false, 'a disabled pairing config is not enabled');
file_put_contents($pairingAuthorityPath, $pairingAuthorityConfig(true));
cc_test(cc_pairing_authority(true)['enabled'] === true, 'the fresh authority read observes an enable flip mid-request');
if ($pairingAuthorityPriorConfig !== false && $pairingAuthorityPriorConfig !== '') {
    putenv('HULK_CONTROL_CENTER_CONFIG=' . $pairingAuthorityPriorConfig);
} else {
    putenv('HULK_CONTROL_CENTER_CONFIG');
}
unlink($pairingAuthorityPath);
rmdir($pairingAuthorityDirectory);

/* ---------------------------------------------------------------------------
 * Behavioral regressions against the actual pairing handlers.
 *
 * Selected explicitly (never implicitly):
 *  - fixture mode: tests/run-mock.php loads the narrow in-memory PDO test double
 *    (lab machines without a PDO extension);
 *  - native mode: HULK_PAIRING_TEST_DSN points at an isolated disposable
 *    MySQL/MariaDB test database (see tests/pairing-native.php).
 * ------------------------------------------------------------------------- */

$pairingBehavioralDriver = null;
if (defined('CC_PAIRING_TEST_FIXTURE_ACTIVE')) {
    $pairingBehavioralDriver = 'fixture';
} elseif ((string) getenv('HULK_PAIRING_TEST_DSN') !== '') {
    $pairingBehavioralDriver = 'dsn';
}

if ($pairingBehavioralDriver === null) {
    fwrite(STDOUT, "SKIP: pairing behavioral checks require tests/run-mock.php or HULK_PAIRING_TEST_DSN.\n");
} elseif ($pairingBehavioralDriver === 'dsn') {
    require __DIR__ . '/pairing-native.php';
} else {

$behaviorDirectory = sys_get_temp_dir() . '/hulk-pairing-behavior-' . bin2hex(random_bytes(6));
cc_test(mkdir($behaviorDirectory, 0700), 'isolated pairing behavior directory is created');
$behaviorPath = $behaviorDirectory . '/config.php';
$behaviorWriteConfig = static function (bool $enabled) use ($behaviorPath, $pairingClaimSecret, $pairingRateSecret, $pairingKeyA, $pairingKeyB): void {
    $presenceRate = base64_encode(random_bytes(32));
    $presenceToken = base64_encode(random_bytes(32));
    file_put_contents($behaviorPath, '<?php return ' . var_export([
        'databases' => [
            'control' => ['dsn' => 'test:', 'username' => '', 'password' => '', 'options' => []],
            'reseller' => ['dsn' => 'test:', 'username' => '', 'password' => '', 'options' => []],
        ],
        'app' => ['base_url' => 'https://hulksa.com/control-center'],
        'presence' => ['rate_limit_secret' => $presenceRate, 'token_secret' => $presenceToken],
        'pairing' => [
            'enabled' => $enabled,
            'claim_secret' => $pairingClaimSecret,
            'rate_limit_secret' => $pairingRateSecret,
            'envelope_keys' => ['v1' => $pairingKeyA, 'v2' => $pairingKeyB],
            'active_envelope_key_id' => 'v1',
        ],
    ], true) . ';');
};
$behaviorWriteConfig(true);
$behaviorPriorConfig = getenv('HULK_CONTROL_CENTER_CONFIG');
putenv('HULK_CONTROL_CENTER_CONFIG=' . $behaviorPath);

$behaviorBlock = [
    'enabled' => true,
    'ttl_seconds' => 600,
    'retention_seconds' => 86400,
    'max_retained_rows' => 5000,
    'max_deliveries' => 20,
    'poll_interval_seconds' => 3,
    'claim_secret' => $pairingClaimSecret,
    'rate_limit_secret' => $pairingRateSecret,
    'envelope_keys' => ['v1' => $pairingKeyA, 'v2' => $pairingKeyB],
    'active_envelope_key_id' => 'v1',
];
$behaviorSettings = cc_pairing_settings($behaviorBlock);
cc_test($behaviorSettings['enabled'] === true && cc_pairing_enabled() === true, 'behavior fixture: enabled authority is observed fresh');

$makeSecret = static fn (): string => rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
$makeCreateInput = static function (string $secret): array {
    return [
        'contractVersion' => 1,
        'clientRequestId' => cc_pairing_uuid4(),
        'clientSecret' => $secret,
        'installationId' => '11111111-2222-4333-8444-555555555555',
        'device' => ['platform' => 'TV', 'manufacturer' => 'Example', 'model' => 'ExampleTV', 'appVersion' => '0.0.0'],
        'requestedAtEpochMs' => 0,
    ];
};
$resellerCode = 'HULK-ABCD-EFGH-JKMN-PQRS';
$seedReseller = static function (array $overrides = []) use ($resellerCode): void {
    $store = CcPairingTestStore::instance();
    $store->resellers = [array_replace([
        'reseller_id' => 7,
        'access_code' => $resellerCode,
        'access_code_hash' => hulk_access_code_hash($resellerCode),
        'host' => 'http://reseller.test:8080',
        'status' => 'active',
    ], $overrides)];
};
$probeOk = static fn (string $host, string $user, string $pass, array $cfg): array => ['result' => 'OK', 'http_status' => 200, 'reason' => 'ACTIVE'];
$baseClock = static fn (): DateTimeImmutable => new DateTimeImmutable('2026-10-14T00:00:00Z', new DateTimeZone('UTC'));

$store = CcPairingTestStore::instance();
$store->reset();

// create -> stored hash matches response token -> replay identical -> wrong secret rejected
$createSecret = $makeSecret();
$createInput = $makeCreateInput($createSecret);
$created = cc_pairing_create(cc_pairing_db(), $createInput, $behaviorSettings);
cc_test($created['status'] === 201 && strlen($created['body']['claimToken']) === 43, 'create returns a 43-char secret-proven token');
$createdRowIndex = $store->findRequestId($created['body']['requestId']);
cc_test($createdRowIndex !== null, 'created request row exists');
$createdRow = $store->requests[$createdRowIndex];
cc_test(
    hash_equals((string) $createdRow['claim_token_hash'], cc_pairing_token_hash($created['body']['claimToken'])),
    'stored hash derives from the returned (secret-proven) token'
);
$replayed = cc_pairing_create(cc_pairing_db(), $createInput, $behaviorSettings);
cc_test(
    $replayed['status'] === 200
    && $replayed['body']['requestId'] === $created['body']['requestId']
    && $replayed['body']['claimToken'] === $created['body']['claimToken'],
    'idempotent replay returns the identical token'
);
$wrongSecretInput = $createInput;
$wrongSecretInput['clientSecret'] = $makeSecret();
$mismatch = false;
try {
    cc_pairing_create(cc_pairing_db(), $wrongSecretInput, $behaviorSettings);
} catch (CcPairingException $exception) {
    $mismatch = $exception->apiCode === 'IDEMPOTENCY_MISMATCH';
}
cc_test($mismatch, 'a wrong client secret cannot retrieve the token');

// approve -> claim (retryable) -> lost response -> ack -> idempotent ack -> once-only terminal
$seedReseller();
$claimedToken = $created['body']['claimToken'];
$createdRequestId = $created['body']['requestId'];
$approval = cc_pairing_approve(cc_pairing_db(), $createdRequestId, $resellerCode, 'user', 'pass', $behaviorSettings, $probeOk, $baseClock);
cc_test($approval['hostLabel'] === 'reseller.test:8080', 'approval returns a PHP host label');
$claimOne = cc_pairing_claim(cc_pairing_db(), $createdRequestId, $claimedToken, $behaviorSettings, $baseClock);
cc_test(
    $claimOne['state'] === 'APPROVED'
    && $claimOne['credentials'] === ['accessCode' => $resellerCode, 'username' => 'user', 'password' => 'pass']
    && $claimOne['deliveryCount'] === 1,
    'claim delivers the exact credentials and stays APPROVED'
);
$claimTwo = cc_pairing_claim(cc_pairing_db(), $createdRequestId, $claimedToken, $behaviorSettings, $baseClock);
cc_test($claimTwo['state'] === 'APPROVED' && $claimTwo['deliveryCount'] === 2 && $claimTwo['credentials'] === $claimOne['credentials'], 'lost-response claim is retryable with the same credentials');
$acked = cc_pairing_ack(cc_pairing_db(), $createdRequestId, $claimedToken, $baseClock);
cc_test($acked['state'] === 'ACKED', 'ack terminates the attempt');
$ackedRow = $store->requests[$store->findRequestId($createdRequestId)];
cc_test($ackedRow['envelope_blob'] === null && $ackedRow['envelope_version'] === null, 'ack destroys the envelope');
cc_test(cc_pairing_ack(cc_pairing_db(), $createdRequestId, $claimedToken, $baseClock)['state'] === 'ACKED', 'ack is idempotent');
cc_test(cc_pairing_claim(cc_pairing_db(), $createdRequestId, $claimedToken, $behaviorSettings, $baseClock)['state'] === 'ACKED', 'claim after ack returns terminal state only');
$conflict = false;
try {
    cc_pairing_create(cc_pairing_db(), $createInput, $behaviorSettings);
} catch (CcPairingException $exception) {
    $conflict = $exception->apiCode === 'CREATE_CONFLICT';
}
cc_test($conflict, 'replay after terminal state reports CREATE_CONFLICT');

// cancel orderings: cancel before claim, cancel after claim, cancel then approve
$cancelSecret = $makeSecret();
$cancelInput = $makeCreateInput($cancelSecret);
$cancelCreated = cc_pairing_create(cc_pairing_db(), $cancelInput, $behaviorSettings);
$cancelRequestId = $cancelCreated['body']['requestId'];
$cancelToken = $cancelCreated['body']['claimToken'];
$seedReseller();
cc_pairing_approve(cc_pairing_db(), $cancelRequestId, $resellerCode, 'user', 'pass', $behaviorSettings, $probeOk, $baseClock);
cc_test(cc_pairing_cancel(cc_pairing_db(), $cancelRequestId, $cancelToken)['state'] === 'CANCELLED', 'cancel after claim authorization closes APPROVED');
cc_test(cc_pairing_claim(cc_pairing_db(), $cancelRequestId, $cancelToken, $behaviorSettings, $baseClock)['state'] === 'CANCELLED', 'claim after cancel returns CANCELLED without credentials');
$lateSecret = $makeSecret();
$lateInput = $makeCreateInput($lateSecret);
$lateCreated = cc_pairing_create(cc_pairing_db(), $lateInput, $behaviorSettings);
$lateRequestId = $lateCreated['body']['requestId'];
$lateToken = $lateCreated['body']['claimToken'];
cc_pairing_cancel(cc_pairing_db(), $lateRequestId, $lateToken);
$lateConflict = false;
try {
    cc_pairing_approve(cc_pairing_db(), $lateRequestId, $resellerCode, 'user', 'pass', $behaviorSettings, $probeOk, $baseClock);
} catch (CcPairingException $exception) {
    $lateConflict = $exception->apiCode === 'PAIRING_STATE_CONFLICT';
}
cc_test($lateConflict, 'approve after cancel is a truthful conflict');

// live clock: expiry enforced after provider IO without storing anything
$advance = 0;
$advancingClock = static function () use (&$advance): DateTimeImmutable {
    return (new DateTimeImmutable('2026-10-14T00:00:00Z', new DateTimeZone('UTC')))->modify('+' . $advance . ' seconds');
};
$slowProbe = static function (string $host, string $user, string $pass, array $cfg) use (&$advance): array {
    $advance = 700;
    return ['result' => 'OK', 'http_status' => 200, 'reason' => 'ACTIVE'];
};
$expireSecret = $makeSecret();
$expireCreated = cc_pairing_create(cc_pairing_db(), $makeCreateInput($expireSecret), $behaviorSettings, $baseClock());
$expireRequestId = $expireCreated['body']['requestId'];
$expiredAtApproval = false;
try {
    cc_pairing_approve(cc_pairing_db(), $expireRequestId, $resellerCode, 'user', 'pass', $behaviorSettings, $slowProbe, $advancingClock);
} catch (CcPairingException $exception) {
    $expiredAtApproval = $exception->apiCode === 'PAIRING_EXPIRED';
}
cc_test($expiredAtApproval, 'expiry is re-checked at commit after provider IO');
$expireRow = $store->requests[$store->findRequestId($expireRequestId)];
cc_test($expireRow['state'] === 'PENDING' && $expireRow['envelope_blob'] === null, 'expired approval stores nothing');
$advance = 0;
$claimExpireSecret = $makeSecret();
$claimExpireCreated = cc_pairing_create(cc_pairing_db(), $makeCreateInput($claimExpireSecret), $behaviorSettings, $baseClock());
$claimExpireId = $claimExpireCreated['body']['requestId'];
$claimExpireToken = $claimExpireCreated['body']['claimToken'];
$seedReseller();
cc_pairing_approve(cc_pairing_db(), $claimExpireId, $resellerCode, 'user', 'pass', $behaviorSettings, $probeOk, $baseClock);
$advance = 700;
$claimExpired = cc_pairing_claim(cc_pairing_db(), $claimExpireId, $claimExpireToken, $behaviorSettings, $advancingClock);
cc_test($claimExpired['state'] === 'EXPIRED', 'claim enforces logical expiry with a refreshed clock');
$claimExpireRow = $store->requests[$store->findRequestId($claimExpireId)];
cc_test($claimExpireRow['envelope_blob'] === null && $claimExpireRow['terminal_reason'] === 'EXPIRED', 'expiry transition clears the envelope truthfully');
$advance = 0;

// commit rechecks: access-code rotation, host rotation, reseller deactivation
$rotationSecret = $makeSecret();
$rotationCreated = cc_pairing_create(cc_pairing_db(), $makeCreateInput($rotationSecret), $behaviorSettings);
$rotationRequestId = $rotationCreated['body']['requestId'];
$seedReseller();
$rotateCodeProbe = static function (string $host, string $user, string $pass, array $cfg) use ($store): array {
    $store->resellers[0]['access_code'] = 'HULK-AAAA-BBBB-CCCC-DDDD';
    $store->resellers[0]['access_code_hash'] = hulk_access_code_hash('HULK-AAAA-BBBB-CCCC-DDDD');
    return ['result' => 'OK', 'http_status' => 200, 'reason' => 'ACTIVE'];
};
$codeRotated = false;
try {
    cc_pairing_approve(cc_pairing_db(), $rotationRequestId, $resellerCode, 'user', 'pass', $behaviorSettings, $rotateCodeProbe, $baseClock);
} catch (CcPairingException $exception) {
    $codeRotated = $exception->apiCode === 'INVALID_ACCESS_CODE';
}
cc_test($codeRotated, 'access-code rotation at commit is rejected');
$hostSecret = $makeSecret();
$hostCreated = cc_pairing_create(cc_pairing_db(), $makeCreateInput($hostSecret), $behaviorSettings);
$hostRequestId = $hostCreated['body']['requestId'];
$seedReseller();
$rotateHostProbe = static function (string $host, string $user, string $pass, array $cfg) use ($store): array {
    $store->resellers[0]['host'] = 'http://rotated.test:9090';
    return ['result' => 'OK', 'http_status' => 200, 'reason' => 'ACTIVE'];
};
$hostRotated = false;
try {
    cc_pairing_approve(cc_pairing_db(), $hostRequestId, $resellerCode, 'user', 'pass', $behaviorSettings, $rotateHostProbe, $baseClock);
} catch (CcPairingException $exception) {
    $hostRotated = $exception->apiCode === 'HOST_ROTATED';
}
cc_test($hostRotated, 'host rotation at commit is rejected');
$statusSecret = $makeSecret();
$statusCreated = cc_pairing_create(cc_pairing_db(), $makeCreateInput($statusSecret), $behaviorSettings);
$statusRequestId = $statusCreated['body']['requestId'];
$seedReseller();
$deactivateProbe = static function (string $host, string $user, string $pass, array $cfg) use ($store): array {
    $store->resellers[0]['status'] = 'inactive';
    return ['result' => 'OK', 'http_status' => 200, 'reason' => 'ACTIVE'];
};
$statusRejected = false;
try {
    cc_pairing_approve(cc_pairing_db(), $statusRequestId, $resellerCode, 'user', 'pass', $behaviorSettings, $deactivateProbe, $baseClock);
} catch (CcPairingException $exception) {
    $statusRejected = $exception->apiCode === 'RESELLER_INACTIVE';
}
cc_test($statusRejected, 'reseller deactivation at commit is rejected');

// deny truthfulness: pending denial succeeds; concurrent approval wins
$denySecret = $makeSecret();
$denyCreated = cc_pairing_create(cc_pairing_db(), $makeCreateInput($denySecret), $behaviorSettings);
$denyResult = cc_pairing_deny(cc_pairing_db(), $denyCreated['body']['requestId'], $baseClock);
cc_test($denyResult === ['denied' => true, 'state' => 'DENIED'], 'deny closes a pending request');
$denyRaceSecret = $makeSecret();
$denyRaceCreated = cc_pairing_create(cc_pairing_db(), $makeCreateInput($denyRaceSecret), $behaviorSettings);
$seedReseller();
cc_pairing_approve(cc_pairing_db(), $denyRaceCreated['body']['requestId'], $resellerCode, 'user', 'pass', $behaviorSettings, $probeOk, $baseClock);
$denyRace = cc_pairing_deny(cc_pairing_db(), $denyRaceCreated['body']['requestId'], $baseClock);
cc_test($denyRace['denied'] === false && $denyRace['state'] === 'APPROVED', 'deny after concurrent approval reports the conflict truthfully');

// capacity admission: at the bound, new creates are rejected while replay/ack/cancel still work
$store->reset();
for ($seedIndex = 0; $seedIndex < 100; $seedIndex++) {
    $store->requests[] = [
        'pairing_id' => $store->nextPairingId++,
        'request_id' => cc_pairing_uuid4(),
        'client_request_id' => cc_pairing_uuid4(),
        'installation_id' => 'seed-' . $seedIndex,
        'client_secret_hash' => str_repeat('a', 64),
        'user_code' => 'HULKTV-' . substr(str_repeat('A', 6) . $seedIndex, -6),
        'claim_token_hash' => str_repeat('b', 64),
        'state' => 'ACKED',
        'delivery_count' => 0,
        'created_at' => '2026-10-13 00:00:00',
        'expires_at' => '2026-10-13 00:10:00',
        'terminal_at' => '2026-10-13 00:05:00',
    ];
}
$capacitySettings = $behaviorSettings;
$capacitySettings['max_retained_rows'] = 100;
$capacitySecret = $makeSecret();
$capacityInput = $makeCreateInput($capacitySecret);
$capacityRejected = false;
try {
    cc_pairing_create(cc_pairing_db(), $capacityInput, $capacitySettings);
} catch (CcPairingException $exception) {
    $capacityRejected = $exception->apiCode === 'PAIRING_CAPACITY' && $exception->httpStatus === 503 && $exception->retryAfterSeconds === 60;
}
cc_test($capacityRejected, 'capacity admission rejects new creates with retryAfterSeconds=60');
$store->requests[0]['state'] = 'PENDING';
$store->requests[0]['installation_id'] = $capacityInput['installationId'];
$store->requests[0]['client_request_id'] = $capacityInput['clientRequestId'];
$store->requests[0]['client_secret_hash'] = cc_pairing_token_hash($capacitySecret);
$store->requests[0]['expires_at'] = '2026-10-14 00:10:00';
$capacityReplay = cc_pairing_create(cc_pairing_db(), $capacityInput, $capacitySettings);
cc_test($capacityReplay['status'] === 200, 'matching replay works at capacity');
$store->requests[0]['claim_token_hash'] = cc_pairing_token_hash($capacityReplay['body']['claimToken']);
cc_test(cc_pairing_cancel(cc_pairing_db(), (string) $store->requests[0]['request_id'], $capacityReplay['body']['claimToken'])['state'] === 'CANCELLED', 'cancel works at capacity');

// independent minute/hour rate windows with atomic counting semantics
$store->reset();
$rateNow = new DateTimeImmutable('2026-10-14T00:00:00Z', new DateTimeZone('UTC'));
for ($rateIndex = 0; $rateIndex < 3; $rateIndex++) {
    cc_pairing_rate_limit(cc_pairing_db(), 'pairing_claim_req', 'request-key', 3, 60, $pairingRateSecret, $rateNow);
}
$rateLimited = false;
try {
    cc_pairing_rate_limit(cc_pairing_db(), 'pairing_claim_req', 'request-key', 3, 60, $pairingRateSecret, $rateNow);
} catch (CcPairingException $exception) {
    $rateLimited = $exception->apiCode === 'RATE_LIMITED' && $exception->httpStatus === 429 && ($exception->retryAfterSeconds ?? 0) >= 1;
}
cc_test($rateLimited, 'rate limiting rejects the over-budget request with retry timing');
cc_pairing_rate_limit(cc_pairing_db(), 'pairing_claim_ip', 'request-key', 3, 60, $pairingRateSecret, $rateNow);
cc_test(true, 'a separate scope window stays independent');
cc_pairing_rate_limit(cc_pairing_db(), 'pairing_claim_req', 'request-key', 3, 60, $pairingRateSecret, $rateNow->modify('+61 seconds'));
cc_test(true, 'the minute window resets after the window passes');

// provider target/pin/path agreement
$targetCases = [
    ['http://reseller.test', 'http://reseller.test/player_api.php?username=u&password=p', 'reseller.test:80:93.184.216.34'],
    ['http://reseller.test:80/x', 'http://reseller.test/x/player_api.php?username=u&password=p', 'reseller.test:80:93.184.216.34'],
    ['http://reseller.test:443', 'http://reseller.test:443/player_api.php?username=u&password=p', 'reseller.test:443:93.184.216.34'],
    ['https://reseller.test:80', 'https://reseller.test:80/player_api.php?username=u&password=p', 'reseller.test:80:93.184.216.34'],
    ['https://reseller.test:8443/prefix', 'https://reseller.test:8443/prefix/player_api.php?username=u&password=p', 'reseller.test:8443:93.184.216.34'],
    ['https://reseller.test/', 'https://reseller.test/player_api.php?username=u&password=p', 'reseller.test:443:93.184.216.34'],
];
foreach ($targetCases as [$hostValue, $expectedTarget, $expectedResolve]) {
    $parts = cc_pairing_provider_target($hostValue, 'u', 'p');
    cc_test($parts !== null && $parts['target'] === $expectedTarget, 'provider target preserves scheme/port/path: ' . $hostValue);
    $captured = [];
    $captureResolve = static function (string $hostname, int $timeoutMs): array {
        return ['status' => 'ok', 'addresses' => ['93.184.216.34']];
    };
    $captureGet = static function (string $url, string $resolve, array $settings) use (&$captured): array {
        $captured[] = [$url, $resolve];
        return ['errno' => 0, 'status' => 200, 'body' => json_encode(['user_info' => ['auth' => 1, 'status' => 'Active']]), 'primary_ip' => '93.184.216.34', 'connect_time' => 0.1];
    };
    cc_pairing_probe_provider($hostValue, 'u', 'p', $pairingSettings, $captureResolve, $captureGet);
    cc_test($captured[0][1] === $expectedResolve, 'pin authority agrees with the URL authority: ' . $hostValue);
}
$ipv6Target = cc_pairing_provider_target('http://[2001:db8::1]:8080', 'u', 'p');
cc_test(
    $ipv6Target !== null
    && $ipv6Target['hostname'] === '2001:db8::1'
    && $ipv6Target['target'] === 'http://[2001:db8::1]:8080/player_api.php?username=u&password=p',
    'IPv6 targets keep brackets, host and port'
);

// streaming overflow aborts and clears, and probe classifies it fail-closed
$collector = cc_pairing_response_collector(64);
($collector['write'])(null, str_repeat('A', 32));
($collector['write'])(null, str_repeat('B', 32));
($collector['write'])(null, 'C');
cc_test($collector['has_overflowed']() === true && $collector['body']() === '', 'overflow aborts streaming and clears the body');
$overflowHttp = static fn (string $url, string $resolve, array $settings): array => [
    'errno' => 0, 'status' => 200, 'body' => '', 'primary_ip' => '', 'connect_time' => 0.0, 'overflowed' => true,
];
cc_test(
    cc_pairing_probe_provider('http://example.test', 'u', 'p', $pairingSettings, $publicDns, $overflowHttp)['result'] === 'SERVICE_BLOCKED',
    'an overflowing provider response fails closed'
);

// wire validation: canonical base64url only; metadata types are enforced
$standardEncoded = null;
for ($probe = 0; $probe < 64; $probe++) {
    $candidate = base64_encode(random_bytes(32));
    if (strpbrk($candidate, '+/') !== false) {
        $standardEncoded = $candidate;
        break;
    }
}
cc_test($standardEncoded !== null, 'found a standard-base64 secret with +/ for the rejection test');
$wireRejects = static function (array $overrides) use ($pairingUuid): bool {
    $input = [
        'contractVersion' => 1,
        'clientRequestId' => $pairingUuid,
        'clientSecret' => rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '='),
        'installationId' => '11111111-2222-4333-8444-555555555555',
        'device' => ['platform' => 'TV'],
    ];
    try {
        cc_pairing_validate_create_input(array_replace($input, $overrides));
        return false;
    } catch (CcPairingException $exception) {
        return $exception->apiCode === 'INVALID_REQUEST';
    }
};
cc_test($wireRejects(['clientSecret' => $standardEncoded . '=']), 'padded standard base64 is rejected');
cc_test($wireRejects(['clientSecret' => substr((string) $standardEncoded, 0, 43)]), 'standard-base64 characters are rejected');
$canonicalSecret = rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
$lastChar = substr($canonicalSecret, -1);
$value = strpos('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_', $lastChar);
cc_test($value !== false && ($value & 0x03) === 0, 'canonical secret has zero trailing bits');
$nonCanonical = substr($canonicalSecret, 0, -1)
    . 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'[min(63, $value + 1)];
cc_test($wireRejects(['clientSecret' => $nonCanonical]), 'noncanonical trailing bits are rejected');
cc_test($wireRejects(['clientSecret' => substr($canonicalSecret, 0, 42)]), 'a 42-char secret is rejected');
cc_test($wireRejects(['clientSecret' => $canonicalSecret . 'A']), 'a 44-char secret is rejected');
cc_test($wireRejects(['device' => ['platform' => 'TV', 'manufacturer' => 123]]), 'a non-string device manufacturer is rejected');
cc_test($wireRejects(['requestedAtEpochMs' => '0']), 'a non-integer requestedAtEpochMs is rejected');
cc_test($wireRejects(['device' => ['platform' => 'PHONE']]), 'a non-TV platform is rejected');

// readiness independence and forbidden reuse
$duplicateSettings = $pairingSettings;
$duplicateSettings['claim_secret'] = $pairingSettings['rate_limit_secret'];
cc_test(in_array('DUPLICATE_KEY_MATERIAL', cc_pairing_readiness($duplicateSettings)['errors'], true), 'duplicated decoded secret material is rejected');
$keyReuseSettings = $pairingSettings;
$keyReuseSettings['envelope_keys'] = ['v1' => $pairingSettings['claim_secret']];
$keyReuseSettings['active_envelope_key_id'] = 'v1';
cc_test(in_array('DUPLICATE_KEY_MATERIAL', cc_pairing_readiness($keyReuseSettings)['errors'], true), 'a secret reused as an AEAD key is rejected');
cc_test(
    in_array('PRESENCE_SECRET_REUSE', cc_pairing_readiness($pairingSettings, ['rate_limit_secret' => $pairingSettings['claim_secret']])['errors'], true),
    'reusing a Presence secret is rejected'
);

// claim THEN cancel on the SAME request (explicit claim before cancel)
$claimThenCancelSecret = $makeSecret();
$claimThenCancelCreated = cc_pairing_create(cc_pairing_db(), $makeCreateInput($claimThenCancelSecret), $behaviorSettings);
$claimThenCancelId = $claimThenCancelCreated['body']['requestId'];
$claimThenCancelToken = $claimThenCancelCreated['body']['claimToken'];
$seedReseller();
cc_pairing_approve(cc_pairing_db(), $claimThenCancelId, $resellerCode, 'user', 'pass', $behaviorSettings, $probeOk, $baseClock, cc_pairing_reseller_db());
$claimBeforeCancel = cc_pairing_claim(cc_pairing_db(), $claimThenCancelId, $claimThenCancelToken, $behaviorSettings, $baseClock);
cc_test($claimBeforeCancel['state'] === 'APPROVED' && is_array($claimBeforeCancel['credentials']), 'claim succeeds before a same-request cancel');
cc_test(cc_pairing_cancel(cc_pairing_db(), $claimThenCancelId, $claimThenCancelToken)['state'] === 'CANCELLED', 'cancel closes a previously claimed request');
cc_test(cc_pairing_claim(cc_pairing_db(), $claimThenCancelId, $claimThenCancelToken, $behaviorSettings, $baseClock)['state'] === 'CANCELLED', 'claim after cancel returns terminal state without credentials');

// deny after logical expiry (no prior cleanup)
$denyExpireSecret = $makeSecret();
$denyExpireCreated = cc_pairing_create(cc_pairing_db(), $makeCreateInput($denyExpireSecret), $behaviorSettings, $baseClock());
$denyExpireId = $denyExpireCreated['body']['requestId'];
$denyExpireClock = static fn (): DateTimeImmutable => new DateTimeImmutable('2026-10-14T00:11:00Z', new DateTimeZone('UTC'));
$denyExpireResult = cc_pairing_deny(cc_pairing_db(), $denyExpireId, $denyExpireClock);
$denyExpireRow = $store->requests[$store->findRequestId($denyExpireId)];
cc_test(
    $denyExpireResult === ['denied' => false, 'state' => 'EXPIRED']
    && $denyExpireRow['state'] === 'EXPIRED'
    && $denyExpireRow['terminal_reason'] === 'EXPIRED'
    && $denyExpireRow['envelope_blob'] === null,
    'deny after logical expiry reports EXPIRED and destroys the envelope'
);

// provider HTTP acceptance: non-2xx bodies are never approval
foreach ([[302, 'SERVICE_BLOCKED'], [401, 'INVALID_CREDENTIALS'], [503, 'SERVICE_BLOCKED']] as [$httpStatus, $expectedResult]) {
    $nonSuccessHttp = static function (string $url, string $resolve, array $settings) use ($httpStatus): array {
        return [
            'errno' => 0,
            'status' => $httpStatus,
            'body' => json_encode(['user_info' => ['auth' => 1, 'status' => 'Active']]),
            'primary_ip' => '93.184.216.34',
            'connect_time' => 0.1,
        ];
    };
    cc_test(
        cc_pairing_probe_provider('http://example.test', 'user', 'pass', $pairingSettings, $publicDns, $nonSuccessHttp)['result'] === $expectedResult,
        'provider status ' . $httpStatus . ' with success JSON is not approval'
    );
}

// failed authentication allocates no rate rows; pairing cleanup preserves live and Presence windows
$rateRowsBefore = count($store->rateLimits);
$authFailed = false;
try {
    cc_pairing_authenticate(cc_pairing_db(), cc_pairing_uuid4(), str_repeat('A', 43));
} catch (CcPairingException $exception) {
    $authFailed = $exception->apiCode === 'NOT_FOUND';
}
cc_test($authFailed && count($store->rateLimits) === $rateRowsBefore, 'failed authentication allocates no rate rows');
$store->rateLimits = [
    'pairing_claim_req' . "\x00" . str_repeat('c', 64) => ['scope' => 'pairing_claim_req', 'client_hash' => str_repeat('c', 64), 'window_started_at' => '2026-10-14 00:09:30', 'attempts' => 2],
    'pairing_claim_req' . "\x00" . str_repeat('d', 64) => ['scope' => 'pairing_claim_req', 'client_hash' => str_repeat('d', 64), 'window_started_at' => '2026-10-13 22:00:00', 'attempts' => 2],
    'start' . "\x00" . str_repeat('e', 64) => ['scope' => 'start', 'client_hash' => str_repeat('e', 64), 'window_started_at' => '2026-10-13 22:00:00', 'attempts' => 2],
];
$rateCleanupNow = new DateTimeImmutable('2026-10-14T00:10:00Z', new DateTimeZone('UTC'));
$rateCleanupDeleted = cc_pairing_rate_limit_cleanup(cc_pairing_db(), $rateCleanupNow, 200, $behaviorSettings);
cc_test(
    $rateCleanupDeleted === 1
    && isset($store->rateLimits['pairing_claim_req' . "\x00" . str_repeat('c', 64)])
    && isset($store->rateLimits['start' . "\x00" . str_repeat('e', 64)])
    && !isset($store->rateLimits['pairing_claim_req' . "\x00" . str_repeat('d', 64)]),
    'pairing rate cleanup deletes only expired pairing windows'
);
$store->rateLimits = [];

// drain: purge all nonterminal handoff material with DISABLED_PURGE; metadata separated
$store->reset();
$behaviorWriteConfig(false);
$drainSettings = $behaviorSettings;
$drainSettings['enabled'] = false;
$store->requests[] = [
    'pairing_id' => 1, 'request_id' => cc_pairing_uuid4(), 'client_request_id' => cc_pairing_uuid4(),
    'installation_id' => 'drain-1', 'client_secret_hash' => str_repeat('a', 64), 'user_code' => 'HULKTV-AAAAA1',
    'claim_token_hash' => str_repeat('b', 64), 'state' => 'PENDING', 'delivery_count' => 0,
    'created_at' => '2026-10-14 00:00:00', 'expires_at' => '2026-10-14 00:10:00',
];
$store->requests[] = [
    'pairing_id' => 2, 'request_id' => cc_pairing_uuid4(), 'client_request_id' => cc_pairing_uuid4(),
    'installation_id' => 'drain-2', 'client_secret_hash' => str_repeat('a', 64), 'user_code' => 'HULKTV-AAAAA2',
    'claim_token_hash' => str_repeat('b', 64), 'state' => 'APPROVED', 'delivery_count' => 0,
    'created_at' => '2026-10-14 00:00:00', 'expires_at' => '2026-10-14 00:10:00',
    'envelope_version' => 1, 'envelope_key_id' => 'v1', 'envelope_blob' => 'sealed-bytes',
];
$store->requests[] = [
    'pairing_id' => 3, 'request_id' => cc_pairing_uuid4(), 'client_request_id' => cc_pairing_uuid4(),
    'installation_id' => 'drain-3', 'client_secret_hash' => str_repeat('a', 64), 'user_code' => 'HULKTV-AAAAA3',
    'claim_token_hash' => str_repeat('b', 64), 'state' => 'ACKED', 'delivery_count' => 1,
    'created_at' => '2026-10-01 00:00:00', 'expires_at' => '2026-10-01 00:10:00', 'terminal_at' => '2026-10-01 00:05:00',
];
$store->requests[] = [
    'pairing_id' => 4, 'request_id' => cc_pairing_uuid4(), 'client_request_id' => cc_pairing_uuid4(),
    'installation_id' => 'drain-4', 'client_secret_hash' => str_repeat('a', 64), 'user_code' => 'HULKTV-AAAAA4',
    'claim_token_hash' => str_repeat('b', 64), 'state' => 'CANCELLED', 'delivery_count' => 0,
    'created_at' => '2026-10-13 23:00:00', 'expires_at' => '2026-10-13 23:10:00', 'terminal_at' => '2026-10-13 23:59:00',
];
$drainNow = new DateTimeImmutable('2026-10-14T00:05:00Z', new DateTimeZone('UTC'));
$drainResult = cc_pairing_cleanup(cc_pairing_db(), $drainSettings, $drainNow, true);
cc_test(
    $drainResult['purged'] === 2
    && $drainResult['handoff_remaining'] === 0
    && $drainResult['metadata_retained'] === 4,
    'drain purges all nonterminal handoff material while retaining terminal metadata'
);
$drainedApproved = $store->requests[$store->findPairingId(2)];
cc_test(
    (string) $drainedApproved['state'] === 'CANCELLED'
    && (string) $drainedApproved['terminal_reason'] === 'DISABLED_PURGE'
    && $drainedApproved['envelope_blob'] === null,
    'drain uses DISABLED_PURGE semantics and clears envelopes'
);

// ordinary cleanup bounds: one expiry batch, one metadata deletion batch, truth on failure
$ordinarySettings = $behaviorSettings;
$ordinarySettings['retention_seconds'] = 3600;
for ($extraIndex = 0; $extraIndex < 3; $extraIndex++) {
    $store->requests[] = [
        'pairing_id' => 10 + $extraIndex, 'request_id' => cc_pairing_uuid4(), 'client_request_id' => cc_pairing_uuid4(),
        'installation_id' => 'expired-' . $extraIndex, 'client_secret_hash' => str_repeat('a', 64),
        'user_code' => 'HULKTV-EXPD0' . $extraIndex, 'claim_token_hash' => str_repeat('b', 64), 'state' => 'PENDING',
        'delivery_count' => 0, 'created_at' => '2026-10-13 22:00:00', 'expires_at' => '2026-10-13 22:10:00',
    ];
}
$ordinaryResult = cc_pairing_cleanup(cc_pairing_db(), $ordinarySettings, $drainNow, false);
cc_test($ordinaryResult['expired'] === 3 && $ordinaryResult['deleted'] === 1, 'ordinary cleanup bounds expire and delete batches');
$store->deleteFailure = true;
$store->requests[] = [
    'pairing_id' => 90, 'request_id' => cc_pairing_uuid4(), 'client_request_id' => cc_pairing_uuid4(),
    'installation_id' => 'fail-1', 'client_secret_hash' => str_repeat('a', 64), 'user_code' => 'HULKTV-FAIL01',
    'claim_token_hash' => str_repeat('b', 64), 'state' => 'PENDING', 'delivery_count' => 0,
    'created_at' => '2026-10-13 22:00:00', 'expires_at' => '2026-10-13 22:10:00',
];
$abortedRun = cc_pairing_cleanup(cc_pairing_db(), $ordinarySettings, $drainNow, false);
cc_test(
    $abortedRun['aborted'] === true
    && is_string($abortedRun['aborted_reason'])
    && $abortedRun['incomplete'] === true
    && $abortedRun['budget_exceeded'] === false,
    'an aborted cleanup reports aborted/incomplete truthfully instead of a false success'
);
$store->deleteFailure = false;
$recovered = cc_pairing_cleanup(cc_pairing_db(), $ordinarySettings, $drainNow, false);
cc_test($recovered['expired'] >= 1 && $recovered['handoff_remaining'] === 0, 'cleanup is idempotent after failure');

$behaviorWriteConfig(true);
if ($behaviorPriorConfig !== false && $behaviorPriorConfig !== '') {
    putenv('HULK_CONTROL_CENTER_CONFIG=' . $behaviorPriorConfig);
} else {
    putenv('HULK_CONTROL_CENTER_CONFIG');
}
unlink($behaviorPath);
rmdir($behaviorDirectory);

}
