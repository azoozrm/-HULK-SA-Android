<?php

declare(strict_types=1);

require_once __DIR__ . '/../lib/host-health.php';

$binary = cc_host_health_dns_cli_binary();
if (!is_string($binary) || !is_executable($binary)) {
    fwrite(STDERR, "FAIL: no executable CLI PHP binary selected\n");
    exit(1);
}
if (strtolower(basename($binary)) !== 'php') {
    fwrite(STDERR, "FAIL: selected binary is not named php\n");
    exit(1);
}

$lookup = cc_host_health_dns_lookup('hulksa.com', 2000);
if (($lookup['status'] ?? '') !== 'ok' || !is_array($lookup['addresses'] ?? null) || $lookup['addresses'] === []) {
    fwrite(STDERR, "FAIL: bounded DNS lookup did not return an address\n");
    exit(1);
}

fwrite(STDOUT, "PASS: CLI binary selected and bounded DNS lookup returned an address\n");
