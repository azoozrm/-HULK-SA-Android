<?php
declare(strict_types=1);

// Public entry only: the existing page controller owns validation and approval.
// Keep its legacy route and cookie isolated for already-open older clients.
define('HULK_PAIRING_PUBLIC_ENTRY', true);
require dirname(__DIR__) . '/control-center/pair/index.php';
