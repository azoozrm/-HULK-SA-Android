#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path,old,new,label,count=1):
 p=root/path; s=p.read_text(encoding='utf-8')
 if new in s:return
 if old not in s:raise SystemExit(f'missing {label}')
 p.write_text(s.replace(old,new,count),encoding='utf-8')

p='app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt'
rep(p,'import androidx.compose.foundation.focusGroup\n','import androidx.compose.foundation.focusGroup\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n','panel scrolling imports')
rep(p,'                    modifier = Modifier.align(Alignment.CenterEnd),','                    modifier = Modifier.align(Alignment.CenterStart),','panel RTL side',6)
rep(p,'''private fun PlayerSidePanel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalHulkColors.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(430.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF080906), Color(0xFA15170F))))
            .border(1.dp, colors.gold.copy(alpha = .32f))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            FocusButton("اغلاق", onClose, primary = false, compact = true)
        }
        Spacer(Modifier.height(18.dp))
        content()
    }
}''','''private fun PlayerSidePanel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val panelShape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)))
    Column(
        modifier = modifier
            .padding(horizontal = if (adaptiveUi.isTelevision) 30.dp else 12.dp, vertical = if (adaptiveUi.isTelevision) 24.dp else 10.dp)
            .fillMaxHeight(if (adaptiveUi.isTelevision) .90f else .94f)
            .width(if (adaptiveUi.isTelevision) 500.dp else 340.dp)
            .clip(panelShape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF080906), Color(0xFA15170F))))
            .border(1.dp, colors.gold.copy(alpha = .42f), panelShape)
            .padding(horizontal = if (adaptiveUi.isTelevision) 26.dp else 18.dp, vertical = if (adaptiveUi.isTelevision) 22.dp else 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = Color.White,
                fontSize = if (adaptiveUi.isTelevision) 23.sp else 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FocusButton("اغلاق", onClose, primary = false, compact = true)
        }
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}''','unified safe player side panel')
print('Prepared v0.9.3.17 player panels')
