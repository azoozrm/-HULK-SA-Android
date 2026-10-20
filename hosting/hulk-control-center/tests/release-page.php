<?php

declare(strict_types=1);

/**
 * R06 rendered-view contract. This renders the real Releases branch with
 * fixture rows and proves the active/staged/older states, review binding,
 * escaping and stable copy values without touching a production database.
 */

$testRoot = dirname(__DIR__);
$testDirectory = sys_get_temp_dir() . '/hulk-r06-page-' . bin2hex(random_bytes(6));
if (!mkdir($testDirectory, 0700)) {
    fwrite(STDERR, "FAIL: isolated render directory could not be created\n");
    exit(1);
}
$configPath = $testDirectory . '/config.php';
$config = [
    'databases' => [
        'control' => ['dsn' => 'test:', 'username' => '', 'password' => '', 'options' => []],
        'reseller' => ['dsn' => 'test:', 'username' => '', 'password' => '', 'options' => []],
    ],
    'app' => [
        'base_url' => 'https://hulksa.com/control-center',
        'timezone' => 'Asia/Riyadh',
        'session_name' => 'hulk_cc_r06_render',
    ],
];
file_put_contents($configPath, '<?php return ' . var_export($config, true) . ';');
putenv('HULK_CONTROL_CENTER_CONFIG=' . $configPath);
$_SERVER['HTTPS'] = 'on';
session_save_path($testDirectory);
session_id(bin2hex(random_bytes(16)));

require_once $testRoot . '/bootstrap.php';
require_once $testRoot . '/views/components.php';

$sha = str_repeat('a', 64);
$pageData = [
    'releases' => [
        [
            'id' => 6,
            'version_name' => '1.0.0',
            'version_code' => 68,
            'minimum_supported_version_code' => 68,
            'required' => true,
            'enabled' => true,
            'is_active' => true,
            'is_newer' => false,
            'is_older' => false,
            'below_active_minimum' => false,
            'file_name' => 'hulk-sa-v1.0.0-68-1d957b42.apk',
            'public_apk_url' => 'https://hulksa.com/hulk-operations/releases/hulk-sa-v1.0.0-68-1d957b42.apk',
            'sha256' => $sha,
            'size_bytes' => 5336391,
            'release_notes' => 'الإصدار الرسمي',
            'created_at' => '2026-10-19 03:53:22',
            'artifact_ready' => true,
        ],
        [
            'id' => 7,
            'version_name' => '1.0.1',
            'version_code' => 69,
            'minimum_supported_version_code' => 68,
            'required' => false,
            'enabled' => false,
            'is_active' => false,
            'is_newer' => true,
            'is_older' => false,
            'below_active_minimum' => false,
            'file_name' => 'hulk-sa-v1.0.1-69-abcdef12.apk',
            'public_apk_url' => 'https://hulksa.com/hulk-operations/releases/hulk-sa-v1.0.1-69-abcdef12.apk',
            'sha256' => str_repeat('b', 64),
            'size_bytes' => 5336391,
            'release_notes' => '<script>alert(2)</script>',
            'created_at' => '2026-10-20 10:00:00',
            'artifact_ready' => true,
        ],
        [
            'id' => 5,
            'version_name' => '0.9.3.23',
            'version_code' => 67,
            'minimum_supported_version_code' => 67,
            'required' => false,
            'enabled' => false,
            'is_active' => false,
            'is_newer' => false,
            'is_older' => true,
            'below_active_minimum' => false,
            'file_name' => 'hulk-sa-v0.9.3.23-67-28d99aed.apk',
            'public_apk_url' => null,
            'sha256' => '',
            'size_bytes' => 0,
            'release_notes' => '',
            'created_at' => '2026-10-18 20:00:00',
            'artifact_ready' => false,
        ],
    ],
    'review_release_id' => 7,
];

$moduleKey = 'releases';
ob_start();
require $testRoot . '/views/operations.php';
$html = (string) ob_get_clean();

$pageData = [];
ob_start();
require $testRoot . '/views/operations.php';
$emptyHtml = (string) ob_get_clean();

$checks = [
    'active release is unmistakable' => str_contains($html, 'المنشور الآن') && str_contains($html, 'is-live'),
    'official URL and Downloader are present' => str_contains($html, 'https://hulksa.com/app/') && str_contains($html, '5672332'),
    'staged review is bound to the uploaded record' => str_contains($html, 'release-review-panel') && str_contains($html, 'hulk-sa-v1.0.1-69-abcdef12.apk'),
    'publish action is explicit and CSRF protected' => str_contains($html, 'name="action" value="activate_release"') && str_contains($html, 'name="csrf_token"'),
    'save without publish is available' => str_contains($html, 'حفظ دون نشر'),
    'older release is not offered as publishable' => substr_count($html, 'مراجعة ونشر') === 1,
    'notes are escaped' => str_contains($html, '&lt;script&gt;alert(2)&lt;/script&gt;') && !str_contains($html, '<script>alert(2)</script>'),
    'copy failure fallback is present' => str_contains($html, 'data-copy-fallback') && str_contains($html, 'readonly hidden'),
    'no mock preview text is rendered' => !str_contains($html, 'نموذج للمراجعة') && !str_contains($html, 'محاكاة'),
    'no raw filesystem path is rendered' => !str_contains($html, 'apk_path') && !str_contains($html, 'apk_sha256'),
    'empty state is honest and invents no release' => str_contains($emptyHtml, 'لا يوجد إصدار منشور')
        && str_contains($emptyHtml, 'لا توجد إصدارات')
        && !str_contains($emptyHtml, '1.0.0'),
];

$failed = array_keys(array_filter($checks, static fn (bool $passed): bool => !$passed));
if ($failed !== []) {
    fwrite(STDERR, 'FAIL: ' . implode(', ', $failed) . "\n");
    exit(1);
}

@unlink($configPath);
@rmdir($testDirectory);
fwrite(STDOUT, 'PASS: ' . count($checks) . " rendered Releases page checks.\n");
