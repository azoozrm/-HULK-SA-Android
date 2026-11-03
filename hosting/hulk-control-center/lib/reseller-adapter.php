<?php
declare(strict_types=1);
function cc_reseller_require_domain(): void
{
    if (function_exists('hulk_host_create')) return;
    foreach (array_filter([trim((string)getenv('HULK_RESELLER_BOOTSTRAP')), dirname(__DIR__,2).'/.hulk-reseller-app/bootstrap.php', dirname(__DIR__,3).'/backend/reseller-access/public/.hulk-reseller-app/bootstrap.php']) as $file) {
        if (is_file($file)) { require_once $file; return; }
    }
    throw new RuntimeException('Host authority unavailable');
}
function cc_reseller_modules(): array { return ['hosts','access-codes','resellers']; }
function cc_reseller_handle_post(string $module, array $admin): string
{
    cc_reseller_require_domain();
    $action=cc_post_action();
    if (!in_array($module,['hosts','access-codes'],true) || !in_array($action,['find_code','create_host','update_host','create_code','set_code_status','set_code','rotate_code'],true)) throw new InvalidArgumentException('الاجراء غير متاح');
    $db=cc_db('reseller'); $audit=cc_db('control'); $details=[];
    if ($action==='find_code') {
        $code=hulk_normalize_access_code(is_string($_POST['access_code']??null)?$_POST['access_code']:'');
        $match=$code===null?null:hulk_host_code_lookup($db,$code);
        if(!is_array($match))throw new InvalidArgumentException('لم يتم العثور على الكود');
        $_SESSION['cc_host_focus']=(int)$match['reseller_id'];
        $_SESSION['cc_code_page']=cc_host_code_page_number($db,(int)$match['reseller_id'],(int)$match['code_id']);
        return 'تم العثور على الهوست المرتبط بالكود';
    }

    $db->beginTransaction();
    try {
        if ($action==='create_host') {
            $id=hulk_host_create($db,$_POST); $details=['host_id'=>$id]; $message='تمت اضافة الهوست';
        } elseif ($action==='update_host') {
            $id=hulk_host_id($_POST['host_id']??null); hulk_host_update($db,$id,$_POST); $details=['host_id'=>$id]; $message='تم حفظ التغييرات';
        } elseif ($action==='create_code') {
            $id=hulk_host_id($_POST['host_id']??null); $code=hulk_host_create_code($db,$id,$_POST['access_code']??''); $details=['host_id'=>$id,'code_id'=>(int)$code['code_id']]; $message='تم انشاء الكود مع الاحتفاظ بجميع الاكواد السابقة';
        } else {
            $id=hulk_host_require_code($db,$_POST['code_id']??null);
            $lookup=$db->prepare('SELECT host_id FROM host_access_codes WHERE code_id=:id');
            $lookup->execute(['id'=>$id]);
            $details=['code_id'=>$id,'host_id'=>(int)$lookup->fetchColumn()];
            if ($action==='set_code_status') hulk_host_set_code_status($db,$id,$_POST['status']??null);
            elseif ($action==='set_code') hulk_host_set_code($db,$id,$_POST['access_code']??null);
            else hulk_host_rotate_code($db,$id);
            $message='تم تحديث الكود المحدد فقط';
        }
        $codePageFocus=isset($details['code_id']) && $action!=='create_code'
            ? cc_host_code_page_number($db,(int)$details['host_id'],(int)$details['code_id'])
            : ($action==='update_host' ? max(1,(int)filter_var($_GET['code_page']??1,FILTER_VALIDATE_INT)) : 1);
        ops_audit($audit,(int)$admin['id'],'CONTROL_CENTER_'.strtoupper($action),$details);
        $db->commit();
        if(isset($details['host_id'])) {
            $_SESSION['cc_host_focus']=(int)$details['host_id'];
            $_SESSION['cc_code_page']=$codePageFocus;
        }
        return $message;
    } catch (Throwable $error) { if ($db->inTransaction()) $db->rollBack(); throw $error; }
}
/** Code lists and their usage share the same bounded page */
function cc_host_codes_page(PDO $db, int $hostId, mixed $requestedPage): array
{
    $count = $db->prepare('SELECT COUNT(*) FROM host_access_codes WHERE host_id = :host_id');
    $count->execute(['host_id' => $hostId]);
    $total = (int)$count->fetchColumn();
    $pages = max(1, (int)ceil($total / CC_PRESENCE_PAGE_SIZE));
    $requested = filter_var($requestedPage, FILTER_VALIDATE_INT);
    $page = is_int($requested) && $requested > 0 ? min($requested, $pages) : 1;
    $query = $db->prepare('SELECT code_id, access_code, status, created_at FROM host_access_codes '
        . 'WHERE host_id = :host_id ORDER BY code_id DESC LIMIT ' . CC_PRESENCE_PAGE_SIZE
        . ' OFFSET ' . (($page - 1) * CC_PRESENCE_PAGE_SIZE));
    $query->execute(['host_id' => $hostId]);
    return ['rows' => $query->fetchAll(), 'total' => $total, 'page' => $page, 'pages' => $pages];
}

/** Stable code identity selects the right page after search or a specific mutation */
function cc_host_code_page_number(PDO $db, int $hostId, int $codeId): int
{
    $query = $db->prepare('SELECT COUNT(*) FROM host_access_codes WHERE host_id = :host_id AND code_id > :code_id');
    $query->execute(['host_id' => $hostId, 'code_id' => $codeId]);
    return intdiv((int)$query->fetchColumn(), CC_PRESENCE_PAGE_SIZE) + 1;
}

function cc_reseller_page(string $module): array
{
    cc_reseller_require_domain(); $db=cc_db('reseller');
    $search=mb_substr(trim(is_string($_GET['q']??null)?$_GET['q']:''),0,100);
    $status=in_array($_GET['status']??'',['active','inactive'],true)?$_GET['status']:'';
    $hostId=filter_var($_GET['reseller']??null,FILTER_VALIDATE_INT); $hostId=$hostId>0?(int)$hostId:null;
    $where=[]; $params=[];
    if ($search!=='') { $where[]='(h.name LIKE :name OR h.host LIKE :host)'; $params+=['name'=>'%'.$search.'%','host'=>'%'.$search.'%']; }
    if ($status!=='') { $where[]='h.status=:status'; $params['status']=$status; }
    if ($hostId!==null) { $where[]='h.host_id=:id'; $params['id']=$hostId; }
    $condition=$where===[]?'':' WHERE '.implode(' AND ',$where);
    $count=$db->prepare('SELECT COUNT(*) FROM hosts h'.$condition); $count->execute($params); $total=(int)$count->fetchColumn();
    $pages=max(1,(int)ceil($total/25)); $page=min($pages,max(1,(int)($_GET['page']??1)));
    $q=$db->prepare('SELECT h.* FROM hosts h'.$condition.' ORDER BY h.host_id DESC LIMIT 25 OFFSET '.(($page-1)*25)); $q->execute($params); $rows=$q->fetchAll();
    foreach ($rows as &$row) {
        $codePage=cc_host_codes_page($db,(int)$row['host_id'],$hostId===(int)$row['host_id']?($_GET['code_page']??1):1);
        $row['codes']=$codePage['rows'];
        $row['codes_total']=$codePage['total'];
        $row['code_page']=$codePage['page'];
        $row['code_pages']=$codePage['pages'];
    } unset($row);
    $usageAvailable=true; $usage=[];
    try {
        $config=cc_presence_config(); $now=cc_presence_now();
        $hostRows=array_map(static fn(array $r):array => $r+['reseller_id'=>$r['host_id']],$rows);
        $usage=cc_presence_usage_for_resellers(cc_db('control'),$hostRows,'hosts',$now,(int)$config['online_ttl_seconds']);
        foreach ($rows as &$row) {
            $codeRows=array_map(static fn(array $c):array => $c+['reseller_id'=>$row['host_id']],$row['codes']);
            $row['code_usage']=cc_presence_usage_for_resellers(cc_db('control'),$codeRows,'access-codes',$now,(int)$config['online_ttl_seconds']);
        } unset($row);
    } catch(Throwable $error) { $usageAvailable=false; }
    return ['presence_usage'=>$usage,'presence_usage_available'=>$usageAvailable,'rows'=>$rows,'total'=>$total,'page'=>$page,'pages'=>$pages,'search'=>$search,'status'=>$status,'reseller'=>$hostId];
}
