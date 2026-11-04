<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}
fwrite(STDERR, "Reseller creation is retired; add a host in Control Center\n");
exit(2);
