<?php

declare(strict_types=1);

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    header('Allow: GET');
    http_response_code(405);
    exit;
}

try {
    require_once dirname(__DIR__, 4) . '/bootstrap.php';
    $payload = ops_build_public_config(ops_db());
    $json = ops_public_config_json($payload);
    $etag = ops_public_config_validator($payload);

    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: public, max-age=' . (int) (ops_load_config()['app']['api_cache_seconds'] ?? 60));
    header('ETag: ' . $etag);
    header('X-Content-Type-Options: nosniff');

    if (ops_public_config_validator_matches((string) ($_SERVER['HTTP_IF_NONE_MATCH'] ?? ''), $etag)) {
        http_response_code(304);
        exit;
    }

    echo $json;
} catch (Throwable $exception) {
    error_log('HULK Operations API unavailable: ' . $exception->getMessage());
    http_response_code(503);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    header('X-Content-Type-Options: nosniff');
    echo '{"error":"service_unavailable"}';
}
