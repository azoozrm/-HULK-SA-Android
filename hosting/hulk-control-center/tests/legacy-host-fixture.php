<?php
declare(strict_types=1);
// Read-only test views keep old fixtures exercising the new authority while
// hosts-codes.php independently tests actual host/code tables and migration
function cc_test_legacy_host_views(PDO $db): void {
    $native=$db->getAttribute(PDO::ATTR_DRIVER_NAME)==='mysql';
    $columns=$db->query($native ? "SHOW COLUMNS FROM resellers" : "PRAGMA table_info(resellers)")->fetchAll(PDO::FETCH_ASSOC);
    $names=array_column($columns,$native?'Field':'name');
    $name=in_array('reseller_name',$names,true)?'reseller_name':"'Synthetic'";
    $db->exec("CREATE VIEW hosts AS SELECT reseller_id AS host_id, $name AS name, host, status, NULL AS created_at, NULL AS updated_at FROM resellers");
    $db->exec("CREATE VIEW host_access_codes AS SELECT reseller_id AS code_id, reseller_id AS host_id, access_code, access_code_hash, 'active' AS status FROM resellers");
}
