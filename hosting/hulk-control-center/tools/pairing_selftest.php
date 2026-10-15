<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once dirname(__DIR__) . '/bootstrap.php';
require_once dirname(__DIR__) . '/lib/pairing.php';

/**
 * Readiness self-test: validates key/secret sizes and performs one AEAD envelope
 * round trip with synthetic values only. It never touches pairing rows, credentials
 * or the reseller authority, and it does not enable the feature.
 */

$settings = cc_pairing_settings();
$readiness = cc_pairing_readiness($settings);

$lines = [];
$lines[] = 'enabled=' . ($settings['enabled'] ? 'true' : 'false');
$lines[] = 'ready=' . ($readiness['ready'] ? 'true' : 'false');
if ($readiness['errors'] !== []) {
    $lines[] = 'errors=' . implode(',', $readiness['errors']);
}

$roundTrip = 'skipped';
if ($readiness['ready']) {
    $synthetic = ['accessCode' => 'HULK-TEST-0000-0000-0000', 'username' => 'synthetic-user', 'password' => 'synthetic-pass'];
    $requestId = '00000000-0000-4000-8000-000000000000';
    $expiresAt = cc_pairing_iso8601(cc_pairing_now()->modify('+600 seconds'));
    try {
        $sealed = cc_pairing_seal_credentials($synthetic, $requestId, $expiresAt, $settings);
        $opened = cc_pairing_unseal_credentials(
            (string) $sealed['blob'],
            $requestId,
            $expiresAt,
            $settings,
            (int) $sealed['version'],
            (string) $sealed['key_id'],
        );
        $roundTrip = $opened === $synthetic ? 'ok' : 'mismatch';
    } catch (Throwable) {
        $roundTrip = 'failed';
    }
}
$lines[] = 'envelope_round_trip=' . $roundTrip;

fwrite(STDOUT, implode("\n", $lines) . "\n");

if (!$readiness['ready'] || $roundTrip !== 'ok') {
    exit(1);
}
