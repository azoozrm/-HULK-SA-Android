package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.data.SettingsProStore
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
internal fun UserRatingCard(
    item: ContentItem,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
) {
    if (item.type == ContentType.LIVE) return
    val context = LocalContext.current
    val colors = LocalHulkColors.current
    val store = remember(context) { SettingsProStore(context) }
    var rating by remember(item.type, item.id) { mutableStateOf(store.userRating(item)) }
    val localFirstRequester = remember(item.type, item.id) { FocusRequester() }
    val resolvedFirstRequester = firstFocusRequester ?: localFirstRequester
    val requesters = remember(item.type, item.id, resolvedFirstRequester) {
        listOf(resolvedFirstRequester) + List(4) { FocusRequester() }
    }
    val shape = RoundedCornerShape(if (isTv) 16.dp else 14.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF11120E))
            .border(1.dp, colors.gold.copy(alpha = .30f), shape)
            .padding(horizontal = if (isTv) 18.dp else 14.dp, vertical = if (isTv) 14.dp else 12.dp),
    ) {
        Text(
            "تقييمك",
            color = colors.text,
            fontSize = if (isTv) 17.sp else 15.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            rating?.let { "تقييمك الحالي $it من 5" } ?: "اختر من 1 الى 5",
            color = if (rating != null) colors.goldBright else colors.textMuted,
            fontSize = if (isTv) 10.sp else 9.sp,
        )
        Spacer(Modifier.height(if (isTv) 10.dp else 8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 9.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..5).forEach { score ->
                RatingStarButton(
                    score = score,
                    selected = rating == score,
                    isTv = isTv,
                    requester = requesters[score - 1],
                    upRequester = upRequester,
                    downRequester = downRequester,
                    onClick = { rating = store.toggleUserRating(item, score) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "اضغط نفس الدرجة مرة اخرى لمسح التقييم",
            color = colors.textMuted,
            fontSize = if (isTv) 9.sp else 8.sp,
        )
    }
}

@Composable
private fun RatingStarButton(
    score: Int,
    selected: Boolean,
    isTv: Boolean,
    requester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .size(if (isTv) 54.dp else 46.dp)
            .clip(shape)
            .background(
                when {
                    focused -> colors.goldBright
                    selected -> colors.gold
                    else -> Color(0xFF1A1B16)
                },
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused || selected) colors.goldBright else colors.line.copy(alpha = .55f),
                shape,
            )
            .focusRequester(requester)
            .focusProperties {
                upRequester?.let { up = it }
                downRequester?.let { down = it }
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$score ★",
            color = if (focused || selected) Color.Black else colors.text,
            fontSize = if (isTv) 14.sp else 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
