<?php

declare(strict_types=1);

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    header('Allow: GET');
    http_response_code(405);
    exit;
}

try {
    require_once dirname(__DIR__, 4) . '/bootstrap.php';
    require_once dirname(__DIR__, 4) . '/lib/football-feed.php';

    $config = ops_load_config();
    $football = is_array($config['football'] ?? null) ? $config['football'] : [];
    if (($football['enabled'] ?? false) !== true) {
        http_response_code(404);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        header('X-Content-Type-Options: nosniff');
        echo '{"error":"not_found"}';
        exit;
    }

    $result = ops_football_build_team_catalog($football);
    $payload = $result['payload'];
    $json = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if (!is_string($json)) {
        throw new RuntimeException('Football team catalog encoding failed.');
    }

    header('Content-Type: application/json; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    header('Cache-Control: public, max-age=3600');
    if (is_int($result['retryAfter']) && $result['retryAfter'] > 0) {
        header('Retry-After: ' . $result['retryAfter']);
    }
    $etag = 'W/"' . hash('sha256', $json) . '"';
    header('ETag: ' . $etag);
    if (trim((string) ($_SERVER['HTTP_IF_NONE_MATCH'] ?? '')) === $etag) {
        http_response_code(304);
        exit;
    }

    http_response_code((int) $result['httpStatus']);
    echo $json;
} catch (Throwable $exception) {
    error_log('HULK football team catalog unavailable: ' . $exception->getMessage());
    http_response_code(503);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    header('X-Content-Type-Options: nosniff');
    echo '{"error":"service_unavailable"}';
}
