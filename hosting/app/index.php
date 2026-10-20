<?php

declare(strict_types=1);

/**
 * Permanent HULK SA download resolver.
 *
 * This route is intentionally read-only and parameter-free. It resolves the
 * same enabled-active release row used by the public Operations update API and
 * redirects to the validated Operations APK URL. It never chooses a release
 * from request input and never falls back to a historical APK.
 */

function hulk_app_failure(int $status, string $message): never
{
    http_response_code($status);
    header('Content-Type: text/plain; charset=utf-8');
    header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
    header('Pragma: no-cache');
    header('X-Content-Type-Options: nosniff');
    header('Referrer-Policy: no-referrer');
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'HEAD') {
        echo $message;
    }
    exit;
}

$method = strtoupper((string) ($_SERVER['REQUEST_METHOD'] ?? 'GET'));
if (!in_array($method, ['GET', 'HEAD'], true)) {
    header('Allow: GET, HEAD');
    hulk_app_failure(405, 'Method Not Allowed');
}

try {
    require_once dirname(__DIR__) . '/hulk-operations/bootstrap.php';

    $release = ops_active_release(ops_db());
    if (!is_array($release)) {
        hulk_app_failure(404, 'لا يوجد إصدار منشور.');
    }

    $destination = ops_release_public_apk_url($release);
    if ($destination === null || !ops_release_artifact_is_readable($release)) {
        hulk_app_failure(503, 'رابط التحميل غير متاح مؤقتًا.');
    }

    header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
    header('Pragma: no-cache');
    header('X-Content-Type-Options: nosniff');
    header('Referrer-Policy: no-referrer');
    header('Location: ' . $destination, true, 302);
    exit;
} catch (Throwable $exception) {
    error_log('HULK app download resolver: authority unavailable.');
    hulk_app_failure(503, 'رابط التحميل غير متاح مؤقتًا.');
}
