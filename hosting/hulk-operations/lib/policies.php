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
