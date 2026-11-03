<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/bootstrap.php';
require_once dirname(__DIR__) . '/lib/presence-read-model.php';
cc_reseller_require_domain();

$codePageChecks = 0;
function cc_code_page_test(bool $condition, string $message): void
{
    global $codePageChecks;
    $codePageChecks++;
    if (!$condition) {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    }
}

cc_code_page_test(function_exists('cc_host_codes_page'), 'code list has a bounded paginated read owner');
$codePageDsn = getenv('HULK_CODE_PAGE_TEST_DSN') ?: 'sqlite::memory:';
if ($codePageDsn !== 'sqlite::memory:' && $codePageDsn !== 'mysql:unix_socket=/tmp/hulk-cc371-db.sock;dbname=cc371_code_pages_test;charset=utf8mb4') {
    throw new RuntimeException('Only the isolated code-pages fixture database is allowed');
}
$codePageDb = new PDO($codePageDsn, getenv('HULK_CODE_PAGE_TEST_USER') ?: null, null, [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES=>false]);
$codePageNative = $codePageDb->getAttribute(PDO::ATTR_DRIVER_NAME) === 'mysql';
if ($codePageNative) {
    $codePageDb->exec('DROP TABLE IF EXISTS host_access_codes');
    $codePageDb->exec('DROP TABLE IF EXISTS hosts');
    $codePageDb->exec('DROP TABLE IF EXISTS cc_app_sessions');
    $codePageDb->exec((string)file_get_contents(dirname(__DIR__,3).'/backend/reseller-access/migrations/2026-11-01-direct-hosts.sql'));
} else {
    $codePageDb->exec('CREATE TABLE hosts (host_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, host TEXT, host_key TEXT UNIQUE, status TEXT, created_at TEXT, updated_at TEXT)');
    $codePageDb->exec('CREATE TABLE host_access_codes (code_id INTEGER PRIMARY KEY AUTOINCREMENT, host_id INTEGER, access_code TEXT UNIQUE, access_code_hash TEXT UNIQUE, alias_key TEXT UNIQUE, status TEXT, created_at TEXT, updated_at TEXT)');
}
$codePageDb->exec('CREATE TABLE cc_app_sessions (reseller_id INTEGER, access_code_snapshot VARCHAR(64), host_snapshot VARCHAR(2048), ended_at DATETIME NULL, last_seen_at DATETIME)');
$codePageHost = hulk_host_create($codePageDb, ['name'=>'Pagination fixture','host'=>'https://code-pages.example.test','status'=>'active']);
$codePageCodes = [];
for ($i=1; $i<=26; $i++) $codePageCodes[] = hulk_host_create_code($codePageDb,$codePageHost,'SyntheticPageCode'.$i);
$codePageTime = new DateTimeImmutable('2026-11-02 12:00:00 UTC');
$codePageOldest = $codePageCodes[0];
$codePageInsert = $codePageDb->prepare('INSERT INTO cc_app_sessions (reseller_id,access_code_snapshot,host_snapshot,ended_at,last_seen_at) VALUES (?,?,?,?,?)');
$codePageInsert->execute([$codePageHost,$codePageOldest['access_code'],'https://code-pages.example.test',null,'2026-11-02 11:59:30']);
$codePageInsert->execute([$codePageHost,$codePageOldest['access_code'],'https://code-pages.example.test','2026-11-01 12:00:00','2026-11-01 12:00:00']);

$codePageFirst = cc_host_codes_page($codePageDb,$codePageHost,1);
cc_code_page_test(count($codePageFirst['rows'])===25 && $codePageFirst['total']===26 && $codePageFirst['pages']===2,'first code page is bounded and reports the full26-code total');
cc_code_page_test(!in_array($codePageOldest['code_id'],array_column($codePageFirst['rows'],'code_id')),'oldest code is beyond the first code page');
$codePageLast = cc_host_codes_page($codePageDb,$codePageHost,2);
cc_code_page_test(count($codePageLast['rows'])===1 && (int)$codePageLast['rows'][0]['code_id']===$codePageOldest['code_id'],'later page exposes the oldest retained code');
foreach ([$codePageFirst,$codePageLast] as $codePageVisible) {
    $codePageUsageRows=array_map(static fn(array $row):array=>$row+['reseller_id'=>$codePageHost],$codePageVisible['rows']);
    $codePageUsage=cc_presence_usage_for_resellers($codePageDb,$codePageUsageRows,'access-codes',$codePageTime,180);
    cc_code_page_test(count($codePageUsage)===count($codePageVisible['rows']),'every displayed code receives a truthful usage result');
    if ($codePageVisible['page']===2) cc_code_page_test($codePageUsage[$codePageOldest['code_id']]['sessions']===2 && $codePageUsage[$codePageOldest['code_id']]['online']===1,'later-page code retains exact historical and recent session counts');
}
cc_code_page_test(cc_host_code_page_number($codePageDb,$codePageHost,$codePageOldest['code_id'])===2,'code search can focus the actual later page');
hulk_host_set_code_status($codePageDb,$codePageOldest['code_id'],'inactive');
cc_code_page_test(hulk_host_code_lookup($codePageDb,$codePageOldest['access_code'])['status']==='inactive','later-page code can be disabled independently');
foreach (array_slice($codePageCodes,1) as $codePageSibling) {
    $codePageResolved=hulk_host_code_lookup($codePageDb,$codePageSibling['access_code']);
    cc_code_page_test((int)$codePageResolved['reseller_id']===$codePageHost && $codePageResolved['status']==='active','later-page state mutation preserves every sibling mapping/state');
}
hulk_host_create_code($codePageDb,$codePageHost,'SyntheticAdditionalCode');
cc_code_page_test(cc_host_codes_page($codePageDb,$codePageHost,2)['total']===27,'new code preserves all earlier pages and records');
cc_code_page_test(count(cc_host_codes_page($codePageDb,$codePageHost,2)['rows'])===2,'later page remains reachable after another code is created');
foreach ([0,-1,'bad',[]] as $codePageInvalid) cc_code_page_test(cc_host_codes_page($codePageDb,$codePageHost,$codePageInvalid)['page']===1,'invalid code page defaults safely to first page');
cc_code_page_test(cc_host_codes_page($codePageDb,$codePageHost,PHP_INT_MAX)['page']===2,'oversized page clamps before calculating SQL offset');
$codePageEmpty=hulk_host_create($codePageDb,['name'=>'Empty fixture','host'=>'https://empty-code-pages.example.test','status'=>'inactive']);
cc_code_page_test(cc_host_codes_page($codePageDb,$codePageEmpty,10)===['rows'=>[],'total'=>0,'page'=>1,'pages'=>1],'empty host returns truthful bounded pagination');
fwrite(STDOUT,"PASS: {$codePageChecks} host-code pagination checks (".($codePageNative?'native MariaDB':'SQLite').")\n");
