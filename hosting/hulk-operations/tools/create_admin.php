<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once dirname(__DIR__) . '/bootstrap.php';

$username = trim((string) ($argv[1] ?? ''));
if (!preg_match('/^[A-Za-z0-9._-]{3,64}$/', $username)) {
    fwrite(STDERR, "Usage: php tools/create_admin.php <username>\n");
    exit(2);
}

fwrite(STDOUT, 'Password (12+ characters): ');
$password = trim((string) fgets(STDIN));
if (strlen($password) < 12) {
    fwrite(STDERR, "Password is too short.\n");
    exit(2);
}

$db = ops_db();
$statement = $db->prepare(
    'INSERT INTO app_admin_users (username, password_hash) VALUES (:username, :password_hash)'
);
$statement->execute([
    'username' => $username,
    'password_hash' => password_hash($password, PASSWORD_DEFAULT),
]);
$adminId = (int) $db->lastInsertId();
ops_audit($db, $adminId, 'ADMIN_CREATED', ['username' => $username]);
fwrite(STDOUT, "Administrator created.\n");
