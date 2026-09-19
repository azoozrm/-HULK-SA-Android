<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$hostname = stream_get_contents(STDIN, 254);
if (
    !is_string($hostname)
    || $hostname === ''
    || strlen($hostname) > 253
    || filter_var($hostname, FILTER_VALIDATE_IP) !== false
    || preg_match('/^[A-Za-z0-9.-]+$/D', $hostname) !== 1
) {
    fwrite(STDOUT, json_encode(['status' => 'invalid', 'addresses' => []]));
    exit(2);
}

$addresses = [];
$records = @dns_get_record($hostname, DNS_A | DNS_AAAA);
if (is_array($records)) {
    foreach ($records as $record) {
        if (!is_array($record)) {
            continue;
        }
        $address = (string) ($record['ip'] ?? $record['ipv6'] ?? '');
        if ($address !== '' && filter_var($address, FILTER_VALIDATE_IP) !== false) {
            $addresses[$address] = $address;
        }
        if (count($addresses) >= 16) {
            break;
        }
    }
}

fwrite(STDOUT, json_encode([
    'status' => $addresses === [] ? 'failure' : 'ok',
    'addresses' => array_values($addresses),
]));
exit($addresses === [] ? 1 : 0);
