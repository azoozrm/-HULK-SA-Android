<?php

declare(strict_types=1);

/**
 * Lab-only silent TCP listener for the R06 supervisor hard-bound test: accepts
 * connections and never speaks, so a PDO connect blocks past ATTR_TIMEOUT and the
 * supervisor must kill the child at its hard deadline.
 */

if (PHP_SAPI !== 'cli') {
    exit(2);
}

$port = (int) (getenv('HULK_PAIRING_LISTENER_PORT') !== false ? getenv('HULK_PAIRING_LISTENER_PORT') : 33999);
$server = stream_socket_server('tcp://127.0.0.1:' . $port, $errno, $errstr);
if ($server === false) {
    fwrite(STDERR, 'listen failed: ' . $errstr . "\n");
    exit(1);
}
fwrite(STDOUT, "ready\n");
$clients = [];
while (true) {
    $read = array_merge([$server], $clients);
    $write = null;
    $except = null;
    if (stream_select($read, $write, $except, 1) > 0) {
        foreach ($read as $stream) {
            if ($stream === $server) {
                $client = @stream_socket_accept($server, 0);
                if ($client !== false) {
                    $clients[] = $client;
                }
            }
        }
    }
}
