<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/public/.hulk-reseller-app/bootstrap.php';

$tests = 0;
function reseller_test(bool $condition, string $message): void
{
    global $tests;
    $tests++;
    if (!$condition) {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    }
}

$db = new PDO('sqlite::memory:');
$db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
$db->exec(
    'CREATE TABLE resellers ('
    . 'reseller_id INTEGER PRIMARY KEY AUTOINCREMENT, reseller_name TEXT NOT NULL, '
    . 'reseller_name_key TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, host TEXT NOT NULL, '
    . 'access_code TEXT NOT NULL UNIQUE, access_code_hash TEXT NOT NULL UNIQUE, '
    . "status TEXT NOT NULL DEFAULT 'active', created_at TEXT, updated_at TEXT)"
);

$created = hulk_admin_create_reseller($db, [
    'reseller_name' => 'Owner Test',
    'password' => 'correct-password',
    'host' => 'https://iptv.example:8080/',
    'access_code' => '',
]);
reseller_test((int) $created['reseller_id'] === 1, 'create returns the inserted reseller ID');
reseller_test(hulk_normalize_access_code((string) $created['access_code']) === $created['access_code'], 'generated access code matches the public resolver format');
$row = $db->query('SELECT * FROM resellers WHERE reseller_id = 1')->fetch();
reseller_test(is_array($row) && password_verify('correct-password', (string) $row['password_hash']), 'create stores a password hash');
reseller_test(($row['host'] ?? '') === 'https://iptv.example:8080', 'create normalizes the host');
reseller_test(($row['access_code_hash'] ?? '') === hulk_access_code_hash((string) $row['access_code']), 'create stores the canonical code hash');

hulk_admin_set_status($db, 1, 'inactive');
reseller_test($db->query('SELECT status FROM resellers WHERE reseller_id = 1')->fetchColumn() === 'inactive', 'status mutation uses the reseller authority');
hulk_admin_update_host($db, 1, '');
reseller_test($db->query('SELECT host FROM resellers WHERE reseller_id = 1')->fetchColumn() === '', 'host may be cleared');
hulk_admin_update_host($db, 1, 'http://server.example:8080/');
reseller_test($db->query('SELECT host FROM resellers WHERE reseller_id = 1')->fetchColumn() === 'http://server.example:8080', 'host update is normalized');

$customCode = 'HULK-ABCD-EFGH-JKMN-PQRS';
hulk_admin_set_code($db, 1, $customCode);
$row = $db->query('SELECT access_code, access_code_hash FROM resellers WHERE reseller_id = 1')->fetch();
reseller_test(($row['access_code'] ?? '') === $customCode, 'custom canonical access code is stored directly');
reseller_test(($row['access_code_hash'] ?? '') === hulk_access_code_hash($customCode), 'custom access-code hash stays in sync');
$rotated = hulk_admin_rotate_code($db, 1);
reseller_test($rotated !== $customCode && hulk_normalize_access_code($rotated) === $rotated, 'rotation creates a new resolver-compatible code');

hulk_admin_reset_password($db, 1, 'new-password-123', 'new-password-123');
$hash = (string) $db->query('SELECT password_hash FROM resellers WHERE reseller_id = 1')->fetchColumn();
reseller_test(password_verify('new-password-123', $hash), 'password reset writes a verifiable hash');

foreach ([
    static fn () => hulk_admin_create_reseller($db, ['reseller_name' => 'Short', 'password' => 'short']),
    static fn () => hulk_admin_set_code($db, 1, 'not-a-public-code'),
    static fn () => hulk_admin_update_host($db, 1, 'https://user:pass@example.com'),
] as $invalidMutation) {
    try {
        $invalidMutation();
        reseller_test(false, 'invalid mutation is rejected');
    } catch (InvalidArgumentException) {
        reseller_test(true, 'invalid mutation is rejected');
    }
}

fwrite(STDOUT, "PASS: {$tests} reseller administration domain checks.\n");
