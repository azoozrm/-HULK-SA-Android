from pathlib import Path
import re, sys
r=Path(sys.argv[1]); m=r/'app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'; p=r/'app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt'; c=r/'app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt'; g=r/'app/build.gradle.kts'
M=m.read_text(); P=p.read_text(); C=c.read_text(); G=g.read_text()
G=G.replace('versionCode = 23','versionCode = 24').replace('versionName = "0.9.1.1"','versionName = "0.9.1.2"')
old='''Text(entry.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(7.dp))'''
new='''Text(entry.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("استكمال المشاهدة  •  ${formatHistoryTime(entry.positionMs)}", color = colors.goldBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))'''
if old not in C: raise SystemExit('history block missing')
C=C.replace(old,new)
if 'fun formatHistoryTime' not in C:C+='''\nprivate fun formatHistoryTime(ms:Long):String{val s=ms.coerceAtLeast(0)/1000;return if(s>=3600)"%d:%02d:%02d".format(s/3600,(s%3600)/60,s%60) else "%02d:%02d".format(s/60,s%60)}\n'''
P=P.replace('var pendingSeekMs by remember(request) { mutableLongStateOf(0L) }','var pendingSeekMs by remember(request) { mutableLongStateOf(0L) }\n    var lastManualSeekAtMs by remember(request) { mutableLongStateOf(0L) }')
P=P.replace('val target = (player.currentPosition + deltaMs).coerceIn(0L, durationMs)','val target = ((currentPositionMs.takeIf { it > 0L } ?: player.currentPosition) + deltaMs).coerceIn(0L, durationMs)')
P=P.replace('player.seekTo(target)\n        currentPositionMs = target','lastManualSeekAtMs = android.os.SystemClock.elapsedRealtime()\n        pendingSeekMs = target\n        currentPositionMs = target\n        player.seekTo(target)',1)
P=P.replace('currentPositionMs = player.currentPosition.coerceAtLeast(0L)\n            durationMs = player.duration.takeIf { it > 0L } ?: 0L','if(android.os.SystemClock.elapsedRealtime()-lastManualSeekAtMs>1400L){currentPositionMs=player.currentPosition.coerceAtLeast(0L);pendingSeekMs=0L}\n            durationMs = player.duration.takeIf { it > 0L } ?: 0L')
M=M.replace('modifier = Modifier.restoreFocus(restore, channelRequester).focusProperties { right = playRequester },','modifier = Modifier.restoreFocus(restore, channelRequester),')
P=P.replace('.fillMaxHeight(.92f)\n            .fillMaxWidth(.88f)','.fillMaxHeight(.86f)\n            .fillMaxWidth(.80f)')
P=P.replace('Text("الفيات",','Text("الفئات",')
P=re.sub(r'\n\s*val count = catalog\?\.items\.orEmpty\(\)\.count \{ it\.categoryId == category\.id \}','',P)
P=P.replace('text = "${category.name}  $count",','text = category.name,')
P=re.sub(r'\n\s*Text\("\$\{visible\.size\}", color = colors\.textMuted, fontSize = 12\.sp\)','',P)
M=M.replace('CategoryBar(catalog?.categories.orEmpty(), state.selectedCategoryId, onSelectCategory, showFavorites = true, showAll = false)','ReorderableLiveCategoryBar(catalog?.categories.orEmpty(), state.selectedCategoryId, onSelectCategory)')
helper='''\n@Composable
private fun ReorderableLiveCategoryBar(categories:List<Category>,selectedId:String?,onSelect:(String?)->Unit){
 val context=LocalContext.current;val prefs=remember{context.getSharedPreferences("live_category_order",android.content.Context.MODE_PRIVATE)}
 var ids by remember(categories){mutableStateOf(prefs.getString("ids","").orEmpty().split(',').filter{it.isNotBlank()})};var moving by remember{mutableStateOf<String?>(null)}
 val ordered=remember(categories,ids){val map=categories.associateBy{it.id};(ids.mapNotNull(map::get)+categories.filterNot{it.id in ids}).distinctBy{it.id}}
 fun move(id:String,d:Int){val x=ordered.map{it.id}.toMutableList();val a=x.indexOf(id);val b=(a+d).coerceIn(0,x.lastIndex);if(a>=0&&a!=b){x.add(b,x.removeAt(a));ids=x;prefs.edit().putString("ids",x.joinToString(",")).apply()}}
 LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(horizontal=3.dp,vertical=4.dp)){item{FocusButton("★ المفضلة",{onSelect(FAVORITES_CATEGORY_ID)},primary=selectedId==FAVORITES_CATEGORY_ID,compact=true)};items(ordered,key=Category::id){cat->FocusButton(if(moving==cat.id)"↔ ${cat.name}" else cat.name,{if(moving==cat.id)moving=null else onSelect(cat.id)},modifier=Modifier.onPreviewKeyEvent{e->if(e.type!=KeyEventType.KeyDown||moving!=cat.id)false else when(e.key){Key.DirectionLeft->{move(cat.id,1);true};Key.DirectionRight->{move(cat.id,-1);true};Key.Enter,Key.DirectionCenter->{moving=null;true};else->false}},primary=selectedId==cat.id,compact=true,onLongClick={moving=cat.id})}}
}
'''
M=M.replace('\n@Composable\nprivate fun FavoriteHint',helper+'\n@Composable\nprivate fun FavoriteHint')
C=C.replace('outlined: Boolean = false,\n    onFocused: (() -> Unit)? = null,','outlined: Boolean = false,\n    onFocused: (() -> Unit)? = null,\n    onLongClick: (() -> Unit)? = null,')
C=C.replace('.clickable(enabled = enabled, role = Role.Button, onClick = onClick)','.onPreviewKeyEvent { e -> if(enabled && onLongClick!=null && e.type==KeyEventType.KeyDown && (e.key==Key.Enter||e.key==Key.DirectionCenter) && e.nativeKeyEvent.repeatCount>0){onLongClick();true}else false }\n            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)',1)

def add_key_imports(text: str) -> str:
    imports = [
        'import androidx.compose.ui.input.key.Key',
        'import androidx.compose.ui.input.key.KeyEventType',
        'import androidx.compose.ui.input.key.key',
        'import androidx.compose.ui.input.key.nativeKeyEvent',
        'import androidx.compose.ui.input.key.onPreviewKeyEvent',
        'import androidx.compose.ui.input.key.type',
    ]
    anchor = 'import androidx.compose.ui.Modifier\n'
    missing = [line for line in imports if line + '\n' not in text]
    if missing:
        if anchor not in text:
            raise SystemExit('Modifier import anchor missing')
        text = text.replace(anchor, anchor + ''.join(line + '\n' for line in missing), 1)
    return text

M=add_key_imports(M)
C=add_key_imports(C)
m.write_text(M);p.write_text(P);c.write_text(C);g.write_text(G)
