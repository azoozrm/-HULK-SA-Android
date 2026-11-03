<?php

declare(strict_types=1);
require_once dirname(__DIR__).'/bootstrap.php';
$navigationChecks=0;
function cc_code_nav_test(bool $condition,string $message):void {
    global $navigationChecks;
    $navigationChecks++;
    if(!$condition){fwrite(STDERR,"FAIL: {$message}\n");exit(1);}
}
cc_code_nav_test(array_keys(cc_context_navigation('hosts'))===['hosts','host-health'],'owner sees one combined hosts/codes destination plus health');
cc_code_nav_test(cc_context_navigation('hosts')['hosts']==='الهوستات والاكواد','combined destination has a clear owner label');
foreach(['hosts','access-codes','resellers'] as $key) {
    cc_code_nav_test(cc_resolve_module($key)['key']==='hosts','legacy owner URL resolves canonical combined management');
    cc_code_nav_test(cc_resolve_module($key)['valid'],'legacy owner URL remains valid');
    cc_code_nav_test(cc_active_main_navigation($key)==='hosts','legacy URL selects the same primary destination');
}
cc_code_nav_test(!cc_resolve_module('unknown-module')['valid'],'unknown routes remain unavailable');
fwrite(STDOUT,"PASS: {$navigationChecks} combined-host navigation checks\n");
