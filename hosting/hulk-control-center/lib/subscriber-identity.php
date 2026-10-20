<?php

declare(strict_types=1);

function cc_subscriber_identity_key(int $resellerId, string $host, string $username): string
{
    return hash('sha256', $resellerId . "\0" . $host . "\0" . $username);
}

function cc_subscriber_note_host_key(string $host): string
{
    return hash('sha256', $host);
}

function cc_subscriber_note_username_key(string $username): string
{
    return hash('sha256', $username);
}
