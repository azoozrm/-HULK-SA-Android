<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require __DIR__ . '/bootstrap.php';

$resellerName = trim(implode(' ', array_slice($argv, 1)));
if ($resellerName === '' || strlen($resellerName) > 100) {
    fwrite(STDERR, "Usage: printf '%s' \"password\" | php create-reseller.php \"Reseller name\"\n");
    exit(2);
}

$password = rtrim((string) stream_get_contents(STDIN), "\r\n");
if (strlen($password) < 10 || strlen($password) > 256) {
    fwrite(STDERR, "Password must contain 10-256 characters.\n");
    exit(2);
}

for ($attempt = 0; $attempt < 5; $attempt++) {
    $accessCode = hulk_generate_access_code();
    try {
        $statement = hulk_db()->prepare(
            'INSERT INTO resellers '
            . '(reseller_name, reseller_name_key, password_hash, host, access_code, access_code_hash, status) '
            . 'VALUES (:name, :name_key, :password_hash, :host, :access_code, :access_code_hash, :status)'
        );
        $statement->execute([
            'name' => $resellerName,
            'name_key' => hulk_normalize_reseller_name($resellerName),
            'password_hash' => password_hash($password, PASSWORD_DEFAULT),
            'host' => '',
            'access_code' => $accessCode,
            'access_code_hash' => hulk_access_code_hash($accessCode),
            'status' => HULK_ACTIVE_STATUS,
        ]);
        fwrite(STDOUT, "Reseller created.\nAccess code: {$accessCode}\n");
        exit(0);
    } catch (PDOException $error) {
        if ((string) $error->getCode() !== '23000' || $attempt === 4) {
            throw $error;
        }
    }
}

