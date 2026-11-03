<?php
declare(strict_types=1);
if (!empty($pageData['load_error'])) { cc_error_state('تعذر تحميل الهوستات','الخدمة غير متاحة حاليا ، اعد المحاولة من دون اجراء اي تعديل',cc_url('hosts'));return; }
$rows=$pageData['rows']??[]; $csrf=cc_csrf_token();
$hidden=static function(string $action,array $ids=[])use($csrf):void { ?><input type="hidden" name="csrf_token" value="<?=cc_e($csrf)?>"><input type="hidden" name="action" value="<?=cc_e($action)?>"><?php foreach($ids as $key=>$id):?><input type="hidden" name="<?=cc_e($key)?>" value="<?=(int)$id?>"><?php endforeach; };
$fields=static function(array $row=[]):void { ?>
<label class="form-field"><span>اسم الهوست</span><input name="name" maxlength="100" value="<?=cc_e($row['name']??'')?>" required></label>
<label class="form-field"><span>رابط الهوست</span><input name="host" type="url" dir="ltr" maxlength="2048" value="<?=cc_e($row['host']??'')?>" required placeholder="https://server.example:8080"></label>
<label class="form-field"><span>الحالة</span><select name="status"><option value="active" <?=($row['status']??'active')==='active'?'selected':''?>>نشط</option><option value="inactive" <?=($row['status']??'')==='inactive'?'selected':''?>>متوقف</option></select></label>
<?php }; ?>
<section class="module-stack host-code-page">
<details class="form-section"><summary>البحث بكود</summary><form method="post" class="admin-form" data-submit-once><?php $hidden('find_code');?><label class="form-field"><span>كود الدخول الكامل</span><input name="access_code" maxlength="64" dir="ltr" required autocomplete="off"></label><button type="submit" class="button button--secondary">عرض الهوست المرتبط</button></form></details>
<details class="form-section"><summary>اضافة هوست</summary><form class="admin-form admin-form--grid" method="post" data-submit-once><?php $hidden('create_host');$fields();?><button class="button button--primary" type="submit">اضافة هوست</button></form></details>
<form method="get" class="filter-bar" data-saved-filters="<?=cc_e($moduleKey)?>" data-saved-filter-fields="status,reseller"><label class="form-field"><span>بحث بالاسم او الرابط</span><input name="q" maxlength="100" value="<?=cc_e($pageData['search'])?>"></label><label class="form-field"><span>الحالة</span><select name="status"><option value="">الكل</option><option value="active" <?=$pageData['status']==='active'?'selected':''?>>نشط</option><option value="inactive" <?=$pageData['status']==='inactive'?'selected':''?>>متوقف</option></select></label><button type="submit" class="button button--secondary">بحث</button><a class="button button--quiet" href="<?=cc_e(cc_url($moduleKey))?>">مسح الفلاتر</a><?php cc_saved_filter_controls($moduleKey,['status','reseller']);?></form>
<p class="muted-copy"><?=(int)$pageData['total']?> هوست</p>
<?php if($rows===[]): cc_empty_state('لا توجد هوستات','اضف هوستا ثم افتحه لانشاء كود او اكثر');endif;?>
<div class="host-records">
<?php foreach($rows as $row):$id=(int)$row['host_id'];$valid=hulk_normalize_host((string)$row['host'])!==null;?>
<details class="host-record" <?=($pageData['reseller']??null)===$id?'open':''?>><summary class="host-row"><strong><?=cc_e($row['name'])?></strong><bdi dir="ltr"><?=cc_e($row['host']!==''?$row['host']:'رابط غير محدد')?></bdi><span><?php cc_status_badge($row['status']==='active'?'نشط':'متوقف',$row['status']==='active'?'success':'neutral');?></span><span><?=(int)$row['codes_total']?> كود</span><span>التفاصيل</span></summary>
<div class="host-details">
<p class="muted-copy"><?php $usage=$pageData['presence_usage'][$id]??null; ?><?=is_array($usage)?((int)$usage['online'].' جلسة حديثة ، '.(int)$usage['sessions'].' جلسة مسجلة ، اخر استخدام '.cc_e(cc_presence_display_time($usage['last_used_at']))):'احصاءات الاستخدام غير متاحة'?></p>
<?php if(!$valid):?><p role="status" class="muted-copy">رابط الهوست يحتاج الى تصحيح قبل استخدام الاكواد</p><?php endif;?>
<details class="inline-editor"><summary>تعديل الهوست</summary><form class="admin-form admin-form--grid" method="post" data-submit-once><?php $hidden('update_host',['host_id'=>$id]);$fields($row);?><button type="submit" class="button button--primary">حفظ التغييرات</button></form></details>
<form class="admin-form admin-form--grid" method="post" data-submit-once><?php $hidden('create_code',['host_id'=>$id]);?><label class="form-field"><span>كود مخصص <small>اتركه فارغا لتوليد كود</small></span><input name="access_code" maxlength="64" dir="ltr" autocomplete="off"></label><button type="submit" class="button button--primary" <?=$valid?'':'disabled'?>>انشاء كود</button><p class="muted-copy">الكود يحدد الهوست فقط ، اضافة كود تحفظ جميع الاكواد السابقة</p></form>
<?php foreach($row['codes'] as $code):$cid=(int)$code['code_id'];$active=$code['status']==='active';?>
<div class="code-row"><?php $codeUsage=$row['code_usage'][$cid]??null;?><small><?=is_array($codeUsage)?((int)$codeUsage['online'].' جلسة حديثة ، اخر استخدام '.cc_e(cc_presence_display_time($codeUsage['last_used_at']))):'احصاءات الكود غير متاحة'?></small><details class="code-reveal"><summary>اظهار الكود #<?=$cid?></summary><code class="credential-value" dir="ltr"><?=cc_e($code['access_code'])?></code></details><?php cc_status_badge($active?($row['status']==='active'?'نشط':'نشط ، الهوست متوقف'):'متوقف',$active?'success':'neutral');?><form method="post" data-submit-once><?php $hidden('set_code_status',['code_id'=>$cid]);?><input type="hidden" name="status" value="<?=$active?'inactive':'active'?>"><button type="submit" class="button button--secondary"><?=$active?'ايقاف الكود':'تفعيل الكود'?></button></form>
<details class="inline-editor"><summary>تعديل الكود</summary><form method="post" data-confirm="سيتم استبدال هذا الكود فقط ، هل تريد المتابعة؟"><?php $hidden('set_code',['code_id'=>$cid]);?><label class="form-field"><span>القيمة الجديدة</span><input name="access_code" maxlength="64" dir="ltr" required autocomplete="off"></label><button type="submit" class="button button--secondary">حفظ التغييرات</button></form><form method="post" data-confirm="سيتم استبدال هذا الكود فقط بكود مولد ، هل تريد المتابعة؟"><?php $hidden('rotate_code',['code_id'=>$cid]);?><button type="submit" class="button button--quiet">توليد بديل لهذا الكود</button></form></details>
</div><?php endforeach;?>
<?php if($row['code_pages']>1):?>
<nav class="pagination code-pagination" aria-label="صفحات اكواد الهوست">
<span>صفحة الاكواد <?=(int)$row['code_page']?> من <?=(int)$row['code_pages']?> ، <?=(int)$row['codes_total']?> كود</span>
<?php foreach([-1,1] as $codeDelta):$codeTarget=$row['code_page']+$codeDelta;if($codeTarget<1||$codeTarget>$row['code_pages'])continue;?>
<a class="button button--quiet" href="<?=cc_e(cc_url('hosts').'?' .http_build_query(['q'=>$pageData['search'],'status'=>$pageData['status'],'page'=>$pageData['page'],'reseller'=>$id,'code_page'=>$codeTarget]))?>"><?=$codeDelta<0?'الاكواد السابقة':'الاكواد التالية'?></a>
<?php endforeach;?></nav>
<?php endif;?>
<p class="muted-copy">ايقاف الكود يمنع استخدامه للدخول مجددا ، ولا ينهي بثا قائما</p>
<a href="<?=cc_e(cc_context_url('sessions',['reseller'=>$id]))?>">سجل الجلسات</a>
</div></details><?php endforeach;?></div>
<nav class="pagination" aria-label="صفحات الهوستات"><span>الصفحة <?=(int)$pageData['page']?> من <?=(int)$pageData['pages']?></span><?php foreach([-1,1] as $delta):$target=$pageData['page']+$delta;if($target<1||$target>$pageData['pages'])continue;?><a class="button button--quiet" href="?<?=cc_e(http_build_query(['q'=>$pageData['search'],'status'=>$pageData['status'],'reseller'=>$pageData['reseller'],'page'=>$target]))?>"><?=$delta<0?'السابقة':'التالية'?></a><?php endforeach;?></nav>
</section>
