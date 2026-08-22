<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/lib/policies.php';

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
ops_test(ops_is_sha256(hash('sha256', 'HULK SA')), 'server generated SHA-256 is valid');
ops_test(ops_safe_message_key('msg-001') === 'MSG-001', 'message key is stable and normalized');

fwrite(STDOUT, "PASS: {$tests} HULK Operations backend policy checks.\n");
