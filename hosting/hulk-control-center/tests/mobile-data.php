<?php
declare(strict_types=1);
$root = dirname(__DIR__, 3);
require $root . '/hosting/hulk-control-center/lib/presence-read-model.php';
$checks = [];
$check = static function (bool $pass, string $name, mixed $actual = null) use (&$checks): void {
    $checks[] = ['check'=>$name, 'state'=>$pass?'PASS':'FAIL', 'actual'=>$actual];
};
$hosts = new PDO('sqlite::memory:', null, null, [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
$hosts->exec('CREATE TABLE hosts(host_id INTEGER,name TEXT,status TEXT,host TEXT,created_at TEXT,updated_at TEXT)');
$hosts->exec("INSERT INTO hosts VALUES(1,'Synthetic host','active','https://synthetic.invalid',NULL,NULL)");
try {
    $mapped = cc_presence_reseller_map($hosts,[1]);
    $check(isset($mapped[1]) && $mapped[1]['reseller_name']==='Synthetic host','fresh read initializes host domain before mapping',count($mapped));
} catch (Throwable $error) {
    $check(false,'fresh read initializes host domain before mapping',get_class($error).': '.$error->getMessage());
}
cc_reseller_require_domain();
$mapped=cc_presence_reseller_map($hosts,[1]);
$check(count($mapped)===1,'healthy host authority retains actual data after initialization',count($mapped));
try {cc_presence_reseller_map(new PDO('sqlite::memory:'),[1]); $check(false,'genuine missing host source remains a read failure');}
catch (PDOException $error) {$check(true,'genuine missing host source remains a read failure',get_class($error));}
$control = new PDO('sqlite::memory:',null,null,[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
// NOCASE deliberately models a database collation that raw GROUP BY must not trust.
$control->exec('CREATE TABLE cc_app_sessions(reseller_id INTEGER,access_code_snapshot TEXT COLLATE NOCASE,iptv_username TEXT COLLATE NOCASE,host_snapshot TEXT,ended_at TEXT,last_seen_at TEXT)');
$insert=$control->prepare('INSERT INTO cc_app_sessions VALUES(?,?,?,?,?,?)');
$canonical='HULK-ABCD-EFGH-JKMN-PQRS';
$alias='hulk abcd efgh jkmn pqrs';
$host='https://synthetic.invalid';
$now=new DateTimeImmutable('2026-11-03 12:00:00 UTC');
$add=static function(string $code,string $user,?string $ended=null,string $seen='2026-11-03 11:59:30',?string $targetHost=null,int $reseller=1) use($insert,$host): void {
    $insert->execute([$reseller,$code,$user,$targetHost??$host,$ended,$seen]);
};
$add($canonical,'00017');
$add($canonical,'00017'); // second device/session must not add a subscription
$add($alias,'00017'); // alternate snapshot spelling on the same subscription
$add($alias,'00018'); // valid alias on another subscription must count
$add($canonical,'17'); // leading-zero identity stays distinct
$add($alias,'ended','2026-11-03 11:59:40');
$add($alias,'stale',null,'2026-11-03 11:55:00');
$add($alias,'wrong-host',null,'2026-11-03 11:59:30','https://other.invalid');
$add($alias,'wrong-owner',null,'2026-11-03 11:59:30',null,2);
$add('CaseCode9','case-sensitive-upper');
$add('casecode9','case-sensitive-lower');
$counts=cc_presence_code_online_subscription_counts($control,['online_ttl_seconds'=>180],1,$host,[$canonical,'CaseCode9','casecode9','000001'],$now);
$check(hulk_access_code_matches($canonical,$alias),'fixture alias is accepted by existing resolver semantics');
$check(($counts[$canonical]??null)===3,'legacy aliases counted once per exact subscription across devices and snapshots',$counts[$canonical]??null);
$check(($counts['CaseCode9']??null)===1 && ($counts['casecode9']??null)===1,'case-sensitive codes stay independent under insensitive SQL collation',['upper'=>$counts['CaseCode9']??null,'lower'=>$counts['casecode9']??null]);
$check(($counts['000001']??null)===0,'unused leading-zero code remains available with actual zero count',$counts['000001']??null);
$control->exec('DELETE FROM cc_app_sessions');
$add($alias,'alias-only');
$counts=cc_presence_code_online_subscription_counts($control,['online_ttl_seconds'=>180],1,$host,[$canonical],$now);
$check(($counts[$canonical]??null)===1,'ordinary alias-only subscriber is not undercounted',$counts[$canonical]??null);
$control->exec('DELETE FROM cc_app_sessions');
$add($canonical,'00017');$add($canonical,'00017');
$counts=cc_presence_code_online_subscription_counts($control,['online_ttl_seconds'=>180],1,$host,[$canonical],$now);
$check(($counts[$canonical]??null)===1,'same code and subscription on two devices counts once',$counts[$canonical]??null);
$hosts->exec('CREATE TABLE host_access_codes(code_id INTEGER PRIMARY KEY,host_id INTEGER,access_code TEXT,access_code_hash TEXT,status TEXT,created_at TEXT)');
$codeInsert=$hosts->prepare("INSERT INTO host_access_codes VALUES(?,1,?,?,'active','2026-11-01 00:00:00')");
for($id=1;$id<=26;$id++){ $value=$id===1?'000001':'SyntheticPagination'.$id; $codeInsert->execute([$id,$value,hulk_access_code_hash($value)]); }
$resolved=hulk_host_code_lookup($hosts,'000001');
$check(is_array($resolved) && (int)$resolved['code_id']===1 && $resolved['access_code']==='000001','actual global resolver finds oldest leading-zero code without first-page dependence',$resolved['code_id']??null);
$page1=cc_host_codes_page($hosts,1,1);$page2=cc_host_codes_page($hosts,1,2);
$check(count($page1['rows'])===25 && $page1['total']===26 && $page1['pages']===2,'first code page reports full retained total',[$page1['total'],$page1['pages'],count($page1['rows'])]);
$check(count($page2['rows'])===1 && $page2['rows'][0]['access_code']==='000001','older page preserves full leading-zero code value',$page2['rows'][0]['access_code']??null);
$check(cc_host_code_page_number($hosts,1,1)===2,'global code identity resolves actual older page',cc_host_code_page_number($hosts,1,1));
$control->exec('DELETE FROM cc_app_sessions');
$add($alias,'ended','2026-11-03 11:59:40');$add($alias,'stale',null,'2026-11-03 11:55:00');$add($alias,'wrong-host',null,'2026-11-03 11:59:30','https://other.invalid');$add($alias,'wrong-owner',null,'2026-11-03 11:59:30',null,2);
$counts=cc_presence_code_online_subscription_counts($control,['online_ttl_seconds'=>180],1,$host,[$canonical],$now);
$check(($counts[$canonical]??null)===0,'ended stale different-host and different-owner records remain excluded',$counts[$canonical]??null);
$summary=['candidate_source'=>$root,'fixture_authority'=>'isolated SQLite in-memory only; production untouched','checks'=>$checks,'passed'=>count(array_filter($checks,static fn(array $c):bool=>$c['state']==='PASS')),'failed'=>count(array_filter($checks,static fn(array $c):bool=>$c['state']==='FAIL'))];
echo json_encode($summary,JSON_PRETTY_PRINT|JSON_UNESCAPED_SLASHES)."\n";
exit($summary['failed']>0?1:0);
