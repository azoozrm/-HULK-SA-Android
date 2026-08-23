<?php

declare(strict_types=1);

function ops_known_feature_flags(): array
{
    return [
        'downloads_enabled',
        'episode_notifications_enabled',
        'smart_recommendations_enabled',
        'live_tv_pro_enabled',
    ];
}

function ops_default_feature_flags(): array
{
    return array_fill_keys(ops_known_feature_flags(), true);
}

function ops_normalize_feature_flags(array $rows): array
{
    $flags = ops_default_feature_flags();
    foreach ($rows as $row) {
        $key = (string) ($row['flag_key'] ?? '');
        if (array_key_exists($key, $flags)) {
            $flags[$key] = (bool) ($row['enabled'] ?? false);
        }
    }

    return $flags;
}

function ops_release_metadata_errors(
    string $versionName,
    int $versionCode,
    int $minimumVersionCode,
    bool $required,
    string $releaseNotes
): array {
    $errors = [];
    if (!preg_match('/^[0-9A-Za-z][0-9A-Za-z._-]{0,31}$/', $versionName)) {
        $errors[] = 'اسم الإصدار غير صالح.';
    }
    if ($versionCode < 1 || $versionCode > 2147483647) {
        $errors[] = 'رمز الإصدار خارج نطاق Android الصحيح.';
    }
    if (
        $minimumVersionCode < 1 ||
        $minimumVersionCode > 2147483647 ||
        $minimumVersionCode > $versionCode
    ) {
        $errors[] = 'الحد الأدنى للإصدار يجب أن يكون بين 1 ورمز الإصدار.';
    }
    if ($required && $minimumVersionCode !== $versionCode) {
        $errors[] = 'التحديث الإجباري يجب أن يجعل هذا الإصدار هو الحد الأدنى المدعوم.';
    }
    $releaseNotesLength = function_exists('mb_strlen')
        ? mb_strlen($releaseNotes, 'UTF-8')
        : strlen($releaseNotes);
    if ($releaseNotesLength > 10000) {
        $errors[] = 'ملاحظات الإصدار أطول من الحد المسموح.';
    }

    return $errors;
}

function ops_apk_descriptor_errors(
    string $originalName,
    string $mime,
    int $sizeBytes,
    int $maximumBytes,
    string $header
): array {
    $errors = [];
    if (strtolower((string) pathinfo($originalName, PATHINFO_EXTENSION)) !== 'apk') {
        $errors[] = 'يسمح بملفات APK فقط.';
    }
    if ($sizeBytes < 1 || $sizeBytes > $maximumBytes) {
        $errors[] = 'حجم ملف APK غير مسموح.';
    }

    $allowedMimes = [
        'application/vnd.android.package-archive',
        'application/octet-stream',
        'application/zip',
        'application/x-zip-compressed',
        'application/java-archive',
    ];
    if ($mime !== '' && !in_array(strtolower($mime), $allowedMimes, true)) {
        $errors[] = 'نوع الملف لا يطابق APK.';
    }
    if (substr($header, 0, 2) !== 'PK') {
        $errors[] = 'بنية ملف APK غير صالحة.';
    }

    return $errors;
}

function ops_announcement_is_active(array $row, DateTimeImmutable $now): bool
{
    if (!(bool) ($row['enabled'] ?? false)) {
        return false;
    }

    try {
        $startsAt = new DateTimeImmutable((string) ($row['starts_at'] ?? ''));
        $endsAtValue = $row['ends_at'] ?? null;
        $endsAt = $endsAtValue ? new DateTimeImmutable((string) $endsAtValue) : null;
    } catch (Throwable $exception) {
        return false;
    }

    return $startsAt <= $now && ($endsAt === null || $endsAt > $now);
}

function ops_csrf_tokens_match(string $expected, string $provided): bool
{
    return $expected !== '' && $provided !== '' && hash_equals($expected, $provided);
}

function ops_is_sha256(string $value): bool
{
    return (bool) preg_match('/^[a-f0-9]{64}$/', strtolower($value));
}

function ops_safe_message_key(string $value): ?string
{
    $normalized = strtoupper(trim($value));
    if ($normalized === '') {
        return null;
    }

    return preg_match('/^[A-Z0-9][A-Z0-9_-]{2,79}$/', $normalized) ? $normalized : null;
}

function ops_growth_renewal_url(string $value): ?string
{
    $candidate = trim($value);
    if ($candidate === '' || strlen($candidate) > 2048 || !filter_var($candidate, FILTER_VALIDATE_URL)) {
        return null;
    }
    $parts = parse_url($candidate);
    if (!is_array($parts) || !ops_growth_https_parts_are_safe($parts)) {
        return null;
    }
    $host = strtolower((string) $parts['host']);
    if ($host !== 'hulksa.com' && !str_ends_with($host, '.hulksa.com')) {
        return null;
    }
    if (isset($parts['query']) || isset($parts['fragment'])) {
        return null;
    }
    return $candidate;
}

function ops_growth_support_url(string $value): ?string
{
    $candidate = trim($value);
    if ($candidate === '' || strlen($candidate) > 2048 || !filter_var($candidate, FILTER_VALIDATE_URL)) {
        return null;
    }
    $parts = parse_url($candidate);
    if (!is_array($parts) || !ops_growth_https_parts_are_safe($parts) || isset($parts['fragment'])) {
        return null;
    }

    $host = strtolower((string) $parts['host']);
    $path = (string) ($parts['path'] ?? '');
    if ($host === 'wa.me') {
        return !isset($parts['query']) && preg_match('#^/[1-9][0-9]{6,14}$#', $path)
            ? $candidate
            : null;
    }
    if ($host !== 'whatsapp.com' && !str_ends_with($host, '.whatsapp.com')) {
        return null;
    }
    if ($path !== '/send' || !isset($parts['query']) || strlen((string) $parts['query']) > 1024) {
        return null;
    }
    $query = ops_growth_query_pairs((string) $parts['query']);
    if ($query === null || !isset($query['phone']) || !preg_match('/^[1-9][0-9]{6,14}$/', $query['phone'])) {
        return null;
    }
    foreach (array_keys($query) as $key) {
        if (!in_array($key, ['phone', 'app_absent'], true)) {
            return null;
        }
    }
    if (isset($query['app_absent']) && !in_array($query['app_absent'], ['0', '1'], true)) {
        return null;
    }
    return $candidate;
}

function ops_growth_qr_mode(string $value): ?string
{
    $mode = strtoupper(trim($value));
    return in_array($mode, ['AUTO', 'CUSTOM'], true) ? $mode : null;
}

function ops_growth_days_before_expiry(mixed $value): ?int
{
    $days = filter_var($value, FILTER_VALIDATE_INT);
    return $days !== false && $days >= 1 && $days <= 30 ? (int) $days : null;
}

function ops_growth_custom_qr_path_is_safe(string $value, ?string $slot = null): bool
{
    $path = trim($value);
    if (!preg_match('#^growth-media/growth-(renewal|support)-[a-f0-9]{32}\.(png|webp)$#i', $path, $matches)) {
        return false;
    }
    return $slot === null || strtolower($matches[1]) === strtolower($slot);
}

function ops_growth_qr_file_name(string $slot, string $extension, ?string $randomBytes = null): string
{
    $safeSlot = strtolower(trim($slot));
    $safeExtension = strtolower(trim($extension));
    if (!in_array($safeSlot, ['renewal', 'support'], true)) {
        throw new InvalidArgumentException('نوع QR غير صالح.');
    }
    if (!in_array($safeExtension, ['png', 'webp'], true)) {
        throw new InvalidArgumentException('امتداد QR غير صالح.');
    }
    $entropy = $randomBytes ?? random_bytes(16);
    if (strlen($entropy) !== 16) {
        throw new InvalidArgumentException('قيمة اسم QR غير صالحة.');
    }
    return sprintf('growth-%s-%s.%s', $safeSlot, bin2hex($entropy), $safeExtension);
}

function ops_growth_qr_descriptor_errors(
    string $originalName,
    string $mime,
    int $sizeBytes,
    int $maximumBytes,
    string $header,
    int $width,
    int $height
): array {
    $errors = [];
    $extension = strtolower((string) pathinfo($originalName, PATHINFO_EXTENSION));
    if (!in_array($extension, ['png', 'webp'], true)) {
        $errors[] = 'يسمح بصور PNG أو WebP فقط.';
    }
    if ($sizeBytes < 1 || $sizeBytes > $maximumBytes) {
        $errors[] = 'حجم صورة QR غير مسموح.';
    }
    $expectedMime = ['png' => 'image/png', 'webp' => 'image/webp'][$extension] ?? null;
    if ($expectedMime === null || strtolower($mime) !== $expectedMime) {
        $errors[] = 'نوع صورة QR لا يطابق امتدادها.';
    }
    $signatureIsValid = ($extension === 'png' && str_starts_with($header, "\x89PNG\r\n\x1a\n")) ||
        ($extension === 'webp' && strlen($header) >= 12 && substr($header, 0, 4) === 'RIFF' && substr($header, 8, 4) === 'WEBP');
    if (!$signatureIsValid) {
        $errors[] = 'بنية صورة QR غير صالحة.';
    }
    if ($width < 128 || $height < 128 || $width > 4096 || $height > 4096 || $width !== $height) {
        $errors[] = 'صورة QR يجب أن تكون مربعة بين 128 و4096 بكسل.';
    }
    return $errors;
}

function ops_growth_safe_text(string $value, string $default, int $maximum = 80): string
{
    $candidate = trim($value);
    $length = function_exists('mb_strlen') ? mb_strlen($candidate, 'UTF-8') : strlen($candidate);
    return $candidate !== '' && $length <= $maximum ? $candidate : $default;
}

function ops_growth_https_parts_are_safe(array $parts): bool
{
    return strtolower((string) ($parts['scheme'] ?? '')) === 'https' &&
        !empty($parts['host']) &&
        !isset($parts['user']) &&
        !isset($parts['pass']) &&
        (!isset($parts['port']) || (int) $parts['port'] === 443);
}

function ops_growth_query_pairs(string $query): ?array
{
    $values = [];
    foreach (explode('&', $query) as $part) {
        $separator = strpos($part, '=');
        if ($separator === false || $separator < 1) {
            return null;
        }
        $key = strtolower(substr($part, 0, $separator));
        if ($key === '' || isset($values[$key])) {
            return null;
        }
        $values[$key] = substr($part, $separator + 1);
    }
    return $values;
}
