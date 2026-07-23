package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sa.hulksa.player.MainDestination
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun DetailsNavigationDrawer(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val firstFocus = remember { FocusRequester() }
    val destinations = listOf(
        MainDestination.HOME to "الرئيسية",
        MainDestination.LIVE to "البث المباشر",
        MainDestination.MOVIES to "الأفلام",
        MainDestination.SERIES to "المسلسلات",
        MainDestination.FAVORITES to "قائمتي",
        MainDestination.SEARCH to "البحث",
        MainDestination.DOWNLOADS to "التحميلات",
        MainDestination.SETTINGS to "الإعدادات",
    )

    LaunchedEffect(Unit) {
        delay(100L)
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .62f)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(248.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF090A07), Color(0xFC15170F)),
                    ),
                    RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                )
                .border(
                    1.dp,
                    colors.gold.copy(alpha = .38f),
                    RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                )
                .padding(horizontal = 14.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            BrandBadge(Modifier.size(82.dp))
            Spacer(Modifier.height(14.dp))
            destinations.forEachIndexed { index, (destination, label) ->
                FocusButton(
                    text = label,
                    onClick = { onSelect(destination) },
                    primary = selected == destination,
                    outlined = selected != destination,
                    compact = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                )
                Spacer(Modifier.height(5.dp))
            }
            Spacer(Modifier.weight(1f))
            FocusButton(
                text = "إغلاق القائمة",
                onClick = onClose,
                primary = false,
                outlined = true,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
