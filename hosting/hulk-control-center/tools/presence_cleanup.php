<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once dirname(__DIR__) . '/bootstrap.php';
require_once dirname(__DIR__) . '/lib/presence.php';

try {
    $result = cc_presence_cleanup(cc_db('control'), cc_presence_config());
    fwrite(
        STDOUT,
        sprintf(
            "Presence cleanup complete: sessions=%d devices=%d rate_limits=%d\n",
            $result['sessions'],
            $result['devices'],
            $result['rate_limits']
        )
    );
} catch (Throwable $exception) {
    fwrite(STDERR, "Presence cleanup failed.\n");
    exit(1);
}
