<?php
require dirname(__DIR__) . '/lib/presence-read-model.php';
$f=cc_presence_session_filters('sessions',['days'=>'7']);
if (($f['days']??null)!==7) {fwrite(STDERR,"FAIL duration filter absent\n");exit(1);}
$p=[];$sql=cc_presence_session_conditions($f,'2026-11-03 11:57:00',$p,new DateTimeImmutable('2026-11-03 12:00:00 UTC'));
$db=new PDO('sqlite::memory:');$db->exec('CREATE TABLE cc_app_sessions(started_at TEXT)');
$db->exec("INSERT INTO cc_app_sessions VALUES ('2026-11-02 00:00:00'),('2026-10-01 00:00:00')");
$q=$db->prepare('SELECT COUNT(*) FROM cc_app_sessions s WHERE '.implode(' AND ',$sql));$q->execute($p);
if ((int)$q->fetchColumn()!==1) {fwrite(STDERR,"FAIL duration ignores dates\n");exit(1);}
$all = cc_presence_session_filters('sessions', ['days'=>'0']);
$invalid = cc_presence_session_filters('sessions', ['days'=>'999']);
if ($all['days'] !== 0 || $invalid['days'] !== 0) exit(1);
echo "PASS duration filters actual rows; all-history and invalid values retain older access\n";
