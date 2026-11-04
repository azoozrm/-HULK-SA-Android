<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/public/.hulk-reseller-app/bootstrap.php';

$tests = 0;
function host_test(bool $condition, string $message): void
{
    global $tests;
    $tests++;
    if (!$condition) {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    }
}

host_test(function_exists('hulk_host_create'), 'direct host authority is available without a reseller account');

$dsn = getenv('HULK_HOST_TEST_DSN') ?: 'sqlite::memory:';
if ($dsn !== 'sqlite::memory:' && !str_contains($dsn, 'dbname=cc371_test')) {
    throw new RuntimeException('Only the isolated cc371_test database is allowed');
}
$db = new PDO($dsn, getenv('HULK_HOST_TEST_USER') ?: null, getenv('HULK_HOST_TEST_PASS') ?: null, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC]);
$native = $db->getAttribute(PDO::ATTR_DRIVER_NAME) === 'mysql';
if ($native) {
    $db->exec('DROP TABLE IF EXISTS host_access_codes');
    $db->exec('DROP TABLE IF EXISTS hosts');
    $db->exec('DROP TABLE IF EXISTS host_schema_migrations');
    $db->exec('DROP TABLE IF EXISTS resellers');
    $db->exec((string) file_get_contents(dirname(__DIR__) . '/schema.sql'));
} else {
    $db->exec('CREATE TABLE resellers (reseller_id INTEGER PRIMARY KEY, reseller_name TEXT, reseller_name_key TEXT, password_hash TEXT, host TEXT, access_code TEXT UNIQUE, access_code_hash TEXT UNIQUE, alias_key TEXT UNIQUE, status TEXT, created_at TEXT, updated_at TEXT)');
    $db->exec('CREATE TABLE hosts (host_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, host TEXT, host_key TEXT UNIQUE, status TEXT, created_at TEXT, updated_at TEXT)');
    $db->exec('CREATE TABLE host_access_codes (code_id INTEGER PRIMARY KEY AUTOINCREMENT, host_id INTEGER NOT NULL REFERENCES hosts(host_id), access_code TEXT UNIQUE, access_code_hash TEXT UNIQUE, alias_key TEXT UNIQUE, status TEXT, created_at TEXT, updated_at TEXT)');
    $db->exec('CREATE TABLE host_schema_migrations (migration_key TEXT PRIMARY KEY)');
    $db->exec('PRAGMA foreign_keys = ON');
}
$legacyCode = 'h5AbCdE29Z';
$seed = $db->prepare('INSERT INTO resellers (reseller_id,reseller_name,reseller_name_key,password_hash,host,access_code,access_code_hash,status,created_at,updated_at) VALUES (:id,:name,:name_key,:password,:host,:code,:hash,:status,:created,:updated)');
foreach ([
    [7, 'Legacy One', 'https://legacy.example.test', $legacyCode, 'active'],
    [8, 'Legacy Duplicate', 'https://legacy.example.test', 'legacy2Code', 'inactive'],
    [9, 'Missing Host', '', 'missingHost', 'active'],
    [10, 'Invalid Host', 'ftp://invalid.example.test', 'invalidHost', 'inactive'],
] as [$id, $name, $host, $code, $status]) {
    $seed->execute(['id'=>$id,'name'=>$name,'name_key'=>$name,'password'=>'frozen-hash','host'=>$host,'code'=>$code,'hash'=>hash('sha256',$code),'status'=>$status,'created'=>'2026-10-01 00:00:00','updated'=>'2026-10-02 00:00:00']);
}
host_test(hulk_hosts_migrate($db), 'first migration copies legacy records');
host_test(!hulk_hosts_migrate($db), 'repeat migration is a no-op');
host_test((int) $db->query('SELECT COUNT(*) FROM hosts')->fetchColumn() === 4, 'duplicate and invalid hosts retained as separate historical identities');
host_test((int) $db->query('SELECT host_id FROM hosts WHERE name = \'Legacy One\'')->fetchColumn() === 7, 'legacy association ID is unchanged');
$row = hulk_host_code_lookup($db, $legacyCode);
host_test($row['access_code'] === 'h5AbCdE29Z' && $row['access_code_hash'] === hash('sha256', 'h5AbCdE29Z'), 'deployed mixed-case code value and hash preserved exactly');
host_test(hulk_host_code_lookup($db, 'H5ABCDE29Z') === null, 'deployed codes remain case sensitive');
host_test(hulk_host_code_lookup($db, 'legacy2Code')['status'] === 'inactive', 'inactive legacy effective state preserved');
$newHost = hulk_host_create($db, ['name'=>'Direct Host', 'host'=>'https://direct.example.test/', 'status'=>'active']);
host_test($newHost > 10 && (int) $db->query('SELECT COUNT(*) FROM resellers')->fetchColumn() === 4, 'new host uses no reseller account and cannot collide with historical IDs');
$codes = [];
foreach (['HULK-ABCD-EFGH-JKMN-PQRS', '', 'ThirdCustomCode'] as $value) {
    $codes[] = hulk_host_create_code($db, $newHost, $value);
}
foreach ($codes as $code) {
    $resolved = hulk_host_code_lookup($db, $code['access_code']);
    host_test(is_array($resolved) && (int) $resolved['reseller_id'] === $newHost && $resolved['status'] === 'active', 'second and third creation preserve every earlier resolver mapping');
}
host_test(hulk_host_code_lookup($db, 'hulk abcd efgh jkmn pqrs')['access_code'] === 'HULK-ABCD-EFGH-JKMN-PQRS', 'legacy HULK aliases remain compatible');
host_test(strlen($codes[1]['access_code']) === 10 && preg_match('/[a-z]/', $codes[1]['access_code']) && preg_match('/[A-Z]/', $codes[1]['access_code']) && preg_match('/[0-9]/', $codes[1]['access_code']), 'generation preserves deployed mixed-case format');
try {
    hulk_host_create_code($db, $newHost, 'hulk abcd efgh jkmn pqrs');
    host_test(false,'new custom codes cannot shadow accepted aliases');
} catch (InvalidArgumentException) { host_test(true,'new custom codes cannot shadow accepted aliases'); }
$unicode=hulk_host_create_code($db,$newHost,'رمز-تجريبي');
host_test(hulk_host_code_lookup($db,$unicode['access_code'])['access_code']==='رمز-تجريبي','Unicode deployed-compatible values preserve exact code and hash');
hulk_host_set_code_status($db, $codes[1]['code_id'], 'inactive');
host_test(hulk_host_code_lookup($db, $codes[0]['access_code'])['status'] === 'active' && hulk_host_code_lookup($db, $codes[1]['access_code'])['status'] === 'inactive' && hulk_host_code_lookup($db, $codes[2]['access_code'])['status'] === 'active', 'code state is independent of sibling codes');
hulk_host_update($db, $newHost, ['name'=>'Renamed', 'host'=>'https://direct.example.test', 'status'=>'inactive']);
host_test(hulk_host_code_lookup($db, $codes[0]['access_code'])['status'] === 'inactive', 'host inactivity gates all codes');
hulk_host_update($db, $newHost, ['name'=>'Renamed', 'host'=>'https://edited.example.test', 'status'=>'active']);
host_test(hulk_host_code_lookup($db, $codes[0]['access_code'])['host'] === 'https://edited.example.test', 'ordinary edit updates selected host resolution');
host_test(hulk_host_code_lookup($db, $codes[1]['access_code'])['status'] === 'inactive', 'reactivating host preserves independent disabled code');
foreach ([
    static fn()=>hulk_host_create_code($db, $newHost, $codes[0]['access_code']),
    static fn()=>hulk_host_create_code($db, 9, ''),
    static fn()=>hulk_host_create_code($db, 99999, ''),
    static fn()=>hulk_host_create($db, ['name'=>'Duplicate', 'host'=>'https://legacy.example.test/', 'status'=>'active']),
    static fn()=>hulk_host_create($db, ['name'=>'Invalid', 'host'=>'https://user:pass@invalid.example.test', 'status'=>'active']),
    static fn()=>hulk_host_set_code_status($db, $codes[0]['code_id'], 'invalid'),
    static fn()=>hulk_host_set_code_status($db, 999999, 'active'),
    static fn()=>hulk_host_create_code($db, $newHost, str_repeat('a',65)),
] as $invalid) {
    try { $invalid(); host_test(false, 'invalid, duplicate or missing record rejected'); }
    catch (InvalidArgumentException | PDOException) { host_test(true, 'invalid, duplicate or missing record rejected'); }
}
$oldThird = $codes[2]['access_code'];
$replacement = hulk_host_rotate_code($db, $codes[2]['code_id']);
host_test(hulk_host_code_lookup($db, $oldThird) === null && hulk_host_code_lookup($db, $replacement) !== null, 'explicit per-code rotation retires only its old value');
host_test(hulk_host_code_lookup($db, $codes[0]['access_code']) !== null, 'rotation never changes a sibling code');
host_test(!hulk_hosts_migrate($db) && hulk_host_code_lookup($db, $oldThird) === null, 'migration retry never resurrects replaced values');
foreach ([
    static fn()=>hulk_admin_create_reseller($db, []),
    static fn()=>hulk_admin_update_host($db, 7, 'https://bad.example.test'),
    static fn()=>hulk_admin_set_code($db, 7, 'changed'),
    static fn()=>hulk_admin_set_status($db, 7, 'inactive'),
    static fn()=>hulk_admin_rotate_code($db, 7),
    static fn()=>hulk_admin_reset_password($db, 7, 'password1234', 'password1234'),
] as $retired) {
    try { $retired(); host_test(false, 'retired reseller writer must reject'); }
    catch (RuntimeException) { host_test(true, 'retired reseller writer rejects'); }
}
host_test((string) $db->query('SELECT access_code FROM resellers WHERE reseller_id = 7')->fetchColumn() === 'h5AbCdE29Z', 'legacy records remain untouched after all owner writes');
$longName=str_repeat('اسم',30);
$namedHost=hulk_host_create($db,['name'=>$longName,'host'=>'https://long-name.example.test','status'=>'active']);
host_test($namedHost>0,'Arabic host name limit counts characters rather than UTF8 bytes');
require_once dirname(__DIR__,3) . '/hosting/hulk-control-center/lib/presence.php';
$presenceHost=hulk_host_create($db,['name'=>'Presence fixture','host'=>'https://presence.example.test','status'=>'active']);
$presenceA=hulk_host_create_code($db,$presenceHost,'PresenceFirst');
$presenceB=hulk_host_create_code($db,$presenceHost,'PresenceSecond');
foreach([$presenceA,$presenceB] as $presenceCode) {
    $authorized=cc_presence_validate_reseller($db,['access_code'=>$presenceCode['access_code'],'host'=>'https://presence.example.test']);
    host_test($authorized['reseller_id']===$presenceHost,'Presence accepts both actual code rows on one host');
}
hulk_host_set_code_status($db,$presenceA['code_id'],'inactive');
try { cc_presence_validate_reseller($db,['access_code'=>$presenceA['access_code'],'host'=>'https://presence.example.test']);host_test(false,'disabled code must reject new Presence'); }
catch(CcPresenceException $error) { host_test($error->apiCode==='RESELLER_INACTIVE' && $error->httpStatus===403,'disabled code preserves compatible Presence status'); }
$authorized=cc_presence_validate_reseller($db,['access_code'=>$presenceB['access_code'],'host'=>'https://presence.example.test']);
host_test($authorized['reseller_id']===$presenceHost,'disabling one code preserves sibling Presence authorization');
fwrite(STDOUT, "PASS: {$tests} direct host and multiple-code checks (" . ($native ? 'native MariaDB/MySQL' : 'SQLite') . ")\n");
