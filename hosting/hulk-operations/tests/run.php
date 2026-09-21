<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/lib/policies.php';
require_once dirname(__DIR__) . '/lib/operations.php';

$tests = 0;

function ops_test(bool $condition, string $message): void
{
    global $tests;
    $tests++;
    if (!$condition) {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    }
}

ops_test(
    ops_default_feature_flags() === [
        'downloads_enabled' => true,
        'episode_notifications_enabled' => true,
        'smart_recommendations_enabled' => true,
        'live_tv_pro_enabled' => true,
    ],
    'safe feature defaults'
);

$features = ops_normalize_feature_flags([
    ['flag_key' => 'downloads_enabled', 'enabled' => 0],
    ['flag_key' => 'unknown_remote_command', 'enabled' => 0],
]);
ops_test($features['downloads_enabled'] === false, 'known feature flag is applied');
ops_test(!array_key_exists('unknown_remote_command', $features), 'unknown feature flag is ignored');
ops_test($features['live_tv_pro_enabled'] === true, 'missing feature flag keeps safe default');

ops_test(
    ops_release_metadata_errors('0.9.3.21', 65, 64, false, 'notes') === [],
    'optional update metadata is accepted'
);
ops_test(
    ops_release_metadata_errors('0.9.3.21', 65, 64, true, 'notes') !== [],
    'required update cannot leave an older minimum'
);
ops_test(
    ops_release_metadata_errors('0.9.3.21', 65, 65, true, 'notes') === [],
    'required update sets itself as minimum'
);
ops_test(
    ops_release_metadata_errors('0.9.3.21', 2147483648, 64, false, 'notes') !== [],
    'version code outside the Android Int range is rejected'
);

ops_test(
    ops_apk_descriptor_errors(
        'hulk.apk',
        'application/vnd.android.package-archive',
        2048,
        4096,
        "PK\x03\x04"
    ) === [],
    'valid APK descriptor is accepted'
);
ops_test(
    ops_apk_descriptor_errors('shell.php', 'text/x-php', 100, 4096, '<?ph') !== [],
    'executable upload is rejected'
);

$now = new DateTimeImmutable('2026-08-21 12:00:00');
ops_test(ops_announcement_is_active([
    'enabled' => 1,
    'starts_at' => '2026-08-21 11:00:00',
    'ends_at' => '2026-08-21 13:00:00',
], $now), 'scheduled announcement is active inside its window');
ops_test(!ops_announcement_is_active([
    'enabled' => 1,
    'starts_at' => '2026-08-20 11:00:00',
    'ends_at' => '2026-08-21 11:59:59',
], $now), 'expired announcement is inactive');
ops_test(!ops_announcement_is_active([
    'enabled' => 0,
    'starts_at' => '2026-08-20 11:00:00',
    'ends_at' => null,
], $now), 'disabled announcement is inactive');

ops_test(ops_csrf_tokens_match('abc', 'abc'), 'matching CSRF token is accepted');
ops_test(!ops_csrf_tokens_match('abc', 'different'), 'mismatched CSRF token is rejected');
ops_test(
    ops_admin_record_matches_session(7, 'admin', ['id' => 7, 'username' => 'admin', 'enabled' => 1]),
    'enabled authoritative admin matches the current session'
);
ops_test(
    !ops_admin_record_matches_session(7, 'admin', ['id' => 7, 'username' => 'admin', 'enabled' => 0]),
    'disabled admin is rejected even when the session identity remains'
);
ops_test(
    !ops_admin_record_matches_session(7, 'admin', null),
    'deleted admin is rejected even when the session identity remains'
);
ops_test(
    !ops_admin_record_matches_session(7, 'admin', ['id' => 7, 'username' => 'renamed', 'enabled' => 1]),
    'session username must still match the authoritative admin record'
);
ops_test(ops_is_sha256(hash('sha256', 'HULK SA')), 'server generated SHA-256 is valid');
ops_test(ops_safe_message_key('msg-001') === 'MSG-001', 'message key is stable and normalized');

ops_test(
    ops_growth_renewal_url('https://hulksa.com/') === 'https://hulksa.com/',
    'trusted renewal URL is accepted'
);
ops_test(
    ops_growth_renewal_url('https://renew.hulksa.com/path') === 'https://renew.hulksa.com/path',
    'trusted renewal subdomain is accepted'
);
ops_test(ops_growth_renewal_url('http://hulksa.com/') === null, 'HTTP renewal URL is rejected');
ops_test(ops_growth_renewal_url('https://example.com/') === null, 'off-host renewal URL is rejected');
ops_test(
    ops_growth_renewal_url('https://user:pass@hulksa.com/') === null,
    'renewal URL credentials are rejected'
);
ops_test(
    ops_growth_renewal_url('https://hulksa.com/?token=secret') === null,
    'renewal URL query data is rejected'
);
ops_test(
    ops_growth_support_url('https://wa.me/966506349935') === 'https://wa.me/966506349935',
    'official wa.me URL is accepted'
);
ops_test(
    ops_growth_support_url('https://api.whatsapp.com/send?phone=966506349935&app_absent=0') !== null,
    'official whatsapp.com URL is accepted'
);
ops_test(ops_growth_support_url('https://example.com/support') === null, 'off-host support URL is rejected');
ops_test(ops_growth_support_url('https://wa.me/not-a-number') === null, 'invalid wa.me number is rejected');
ops_test(
    ops_growth_support_url('https://api.whatsapp.com/send?phone=966506349935&token=x') === null,
    'unknown WhatsApp query key is rejected'
);
ops_test(
    ops_growth_support_url('https://api.whatsapp.com/send?ph%6fne=966506349935') === null,
    'encoded WhatsApp query keys are rejected'
);
ops_test(ops_growth_qr_mode('auto') === 'AUTO', 'AUTO QR mode is normalized');
ops_test(ops_growth_qr_mode('CUSTOM') === 'CUSTOM', 'CUSTOM QR mode is accepted');
ops_test(ops_growth_qr_mode('REMOTE') === null, 'unknown QR mode is rejected');
ops_test(ops_growth_days_before_expiry(1) === 1, 'renewal day lower bound is accepted');
ops_test(ops_growth_days_before_expiry(30) === 30, 'renewal day upper bound is accepted');
ops_test(ops_growth_days_before_expiry(0) === null, 'renewal day below range is rejected');
ops_test(ops_growth_days_before_expiry(31) === null, 'renewal day above range is rejected');

$growthFileName = ops_growth_qr_file_name('renewal', 'png', str_repeat("\x01", 16));
ops_test(
    $growthFileName === 'growth-renewal-' . str_repeat('01', 16) . '.png',
    'growth QR filename is server generated'
);
ops_test(
    ops_growth_custom_qr_path_is_safe('growth-media/' . $growthFileName, 'renewal'),
    'generated renewal QR path is accepted'
);
ops_test(
    !ops_growth_custom_qr_path_is_safe('https://example.com/qr.png', 'renewal'),
    'external custom QR path is rejected'
);
ops_test(
    ops_growth_qr_descriptor_errors(
        'qr.png',
        'image/png',
        1024,
        2048,
        "\x89PNG\r\n\x1a\nDATA",
        512,
        512
    ) === [],
    'valid PNG QR descriptor is accepted'
);
ops_test(
    ops_growth_qr_descriptor_errors(
        'qr.webp',
        'image/webp',
        1024,
        2048,
        "RIFF\x00\x00\x00\x00WEBP",
        512,
        512
    ) === [],
    'valid WebP QR descriptor is accepted'
);
ops_test(
    ops_growth_qr_descriptor_errors('shell.php', 'text/x-php', 100, 2048, '<?php echo 1;', 0, 0) !== [],
    'PHP QR upload is rejected'
);
ops_test(
    ops_growth_qr_descriptor_errors('qr.png', 'image/png', 4096, 2048, "\x89PNG\r\n\x1a\nDATA", 512, 512) !== [],
    'oversized QR upload is rejected'
);
ops_test(
    ops_growth_qr_descriptor_errors('qr.png', 'image/png', 1024, 2048, "\x89PNG\r\n\x1a\nDATA", 600, 400) !== [],
    'non-square QR image is rejected'
);

ops_test(ops_presence_discovery([]) === null, 'Presence discovery is absent by default');
$presenceDiscovery = ops_presence_discovery([
    'enabled' => true,
    'base_url' => 'https://hulksa.com/control-center/api/app/v1/presence/',
    'heartbeat_seconds' => 60,
    'online_ttl_seconds' => 180,
]);
ops_test(
    $presenceDiscovery === [
        'enabled' => true,
        'baseUrl' => 'https://hulksa.com/control-center/api/app/v1/presence/',
        'heartbeatSeconds' => 60,
        'onlineTtlSeconds' => 180,
    ],
    'enabled Presence discovery is bounded and additive'
);

require __DIR__ . '/release-deletion.php';
require __DIR__ . '/phase9a-read-only.php';

$publicConfigFixture = [
    'schemaVersion' => 1,
    'generatedAt' => 1770000000,
    'service' => ['status' => 'OPERATIONAL', 'message' => '', 'startsAt' => null, 'estimatedEndAt' => null],
    'update' => [
        'latestVersionCode' => 67,
        'latestVersionName' => '0.9.3.23',
        'minimumSupportedVersionCode' => 64,
        'updateType' => 'OPTIONAL',
        'required' => false,
        'apkUrl' => 'https://hulksa.com/hulk-operations/releases/hulk-sa.apk',
        'apkSha256' => str_repeat('a', 64),
        'releaseNotes' => '',
    ],
    'announcement' => null,
    'announcements' => [],
    'features' => ['downloads_enabled' => true],
    'growth' => ['enabled' => true],
];
$publicConfigValidator = ops_public_config_validator($publicConfigFixture);
ops_test(
    preg_match('/^W\/"[0-9a-f]{64}"$/D', $publicConfigValidator) === 1,
    'the public config validator is a weak SHA-256 entity-tag'
);
$publicConfigLater = $publicConfigFixture;
$publicConfigLater['generatedAt'] = 1770003600;
ops_test(
    ops_public_config_validator($publicConfigLater) === $publicConfigValidator,
    'a changed generatedAt keeps the same weak validator'
);
$publicConfigJsonLater = ops_public_config_json($publicConfigLater);
ops_test(
    str_contains($publicConfigJsonLater, '"generatedAt":1770003600'),
    'the 200 response body still carries generatedAt'
);
$publicConfigService = $publicConfigFixture;
$publicConfigService['service']['status'] = 'MAINTENANCE';
ops_test(
    ops_public_config_validator($publicConfigService) !== $publicConfigValidator,
    'a service change changes the validator'
);
$publicConfigFeature = $publicConfigFixture;
$publicConfigFeature['features']['downloads_enabled'] = false;
ops_test(
    ops_public_config_validator($publicConfigFeature) !== $publicConfigValidator,
    'a feature change changes the validator'
);
$publicConfigAnnouncement = $publicConfigFixture;
$publicConfigAnnouncement['announcements'] = [['id' => 'MSG-X', 'enabled' => true]];
$publicConfigAnnouncement['announcement'] = $publicConfigAnnouncement['announcements'][0];
ops_test(
    ops_public_config_validator($publicConfigAnnouncement) !== $publicConfigValidator,
    'an announcement activation change changes the validator'
);
ops_test(
    ops_public_config_validator_matches($publicConfigValidator, $publicConfigValidator),
    'the exact emitted validator matches If-None-Match'
);
ops_test(
    ops_public_config_validator_matches(substr($publicConfigValidator, 2), $publicConfigValidator),
    'the strong-equivalent opaque tag matches If-None-Match by weak comparison'
);
ops_test(
    ops_public_config_validator_matches('*', $publicConfigValidator),
    'the wildcard If-None-Match condition matches'
);
ops_test(
    !ops_public_config_validator_matches('W/"' . str_repeat('0', 64) . '"', $publicConfigValidator),
    'a different validator does not match If-None-Match'
);
ops_test(
    !ops_public_config_validator_matches('', $publicConfigValidator),
    'an absent If-None-Match condition does not match'
);

fwrite(STDOUT, "PASS: {$tests} HULK Operations backend policy checks.\n");
