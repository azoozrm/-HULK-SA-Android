<?php

declare(strict_types=1);

const CC_SUBSCRIBER_CHECK_MAX_STATUS_LENGTH = 64;
const CC_SUBSCRIBER_CHECK_MIN_EXPIRY = 946684800;
const CC_SUBSCRIBER_CHECK_MAX_EXPIRY = 4102444800;

function cc_subscriber_check_expiry(mixed $value): ?int
{
    if (is_int($value)) {
        $timestamp = $value;
    } elseif (is_string($value) && preg_match('/^\d{1,10}$/D', trim($value)) === 1) {
        $timestamp = (int) trim($value);
    } else {
        return null;
    }
    if ($timestamp < CC_SUBSCRIBER_CHECK_MIN_EXPIRY || $timestamp > CC_SUBSCRIBER_CHECK_MAX_EXPIRY) {
        return null;
    }
    return $timestamp;
}

function cc_subscriber_expiration_label(?int $timestamp): string
{
    if ($timestamp === null) {
        return 'غير متاح';
    }
    try {
        $timezone = new DateTimeZone((string) (cc_load_config()['app']['timezone'] ?? 'Asia/Riyadh'));
        return (new DateTimeImmutable('@' . $timestamp))->setTimezone($timezone)->format('Y-m-d');
    } catch (Throwable $exception) {
        return 'غير متاح';
    }
}

function cc_subscriber_check_presentation(string $state): array
{
    return match ($state) {
        'ACTIVE' => ['label' => 'اشتراك نشط', 'tone' => 'success'],
        'INACTIVE' => ['label' => 'اشتراك غير نشط', 'tone' => 'warning'],
        'STATUS_UNAVAILABLE' => ['label' => 'حالة الاشتراك غير متاحة', 'tone' => 'warning'],
        'AUTH_REJECTED' => ['label' => 'رفض المصادقة', 'tone' => 'danger'],
        'TIMEOUT' => ['label' => 'انتهت مهلة الفحص', 'tone' => 'warning'],
        'NETWORK_ERROR' => ['label' => 'تعذر الوصول إلى المزود', 'tone' => 'danger'],
        'INVALID_RESPONSE' => ['label' => 'رد غير صالح من المزود', 'tone' => 'danger'],
        default => ['label' => 'المزود غير متاح', 'tone' => 'danger'],
    };
}
