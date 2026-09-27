package sa.hulksa.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.ui.theme.HulkColors
import sa.hulksa.player.ui.theme.LocalHulkColors

internal val EntryPanelShape = RoundedCornerShape(24.dp)
internal val EntryActionShape = RoundedCornerShape(14.dp)
internal val EntryFieldShape = RoundedCornerShape(14.dp)

private val EntryPanelBrush = Brush.verticalGradient(
    listOf(
        Color(0xF00E100B),
        Color(0xF2080A07),
    ),
)

internal data class EntryActionVisuals(
    val background: Color,
    val border: Color,
    val borderWidth: Dp,
    val content: Color,
)

internal fun entryActionVisuals(
    colors: HulkColors,
    focused: Boolean,
    selected: Boolean = false,
    primary: Boolean = false,
    enabled: Boolean = true,
): EntryActionVisuals = when {
    !enabled && primary -> EntryActionVisuals(
        background = colors.gold.copy(alpha = .34f),
        border = Color.Transparent,
        borderWidth = 0.dp,
        content = Color(0xFF111006).copy(alpha = .70f),
    )

    !enabled -> EntryActionVisuals(
        background = colors.surface.copy(alpha = .52f),
        border = Color.White.copy(alpha = .06f),
        borderWidth = 1.dp,
        content = colors.textMuted.copy(alpha = .62f),
    )

    primary -> EntryActionVisuals(
        background = if (focused) colors.goldBright else colors.gold,
        border = if (focused) colors.goldBright else Color.Transparent,
        borderWidth = if (focused) 2.dp else 0.dp,
        content = Color(0xFF111006),
    )

    selected -> EntryActionVisuals(
        background = if (focused) colors.goldBright else colors.gold,
        border = if (focused) colors.goldBright else colors.gold.copy(alpha = .55f),
        borderWidth = if (focused) 2.dp else 1.dp,
        content = Color(0xFF111006),
    )

    focused -> EntryActionVisuals(
        background = colors.gold.copy(alpha = .12f),
        border = colors.goldBright,
        borderWidth = 2.dp,
        content = colors.goldBright,
    )

    else -> EntryActionVisuals(
        background = Color(0xFF0D0F0A),
        border = Color.White.copy(alpha = .10f),
        borderWidth = 1.dp,
        content = colors.text,
    )
}

@Composable
internal fun EntrySurface(
    modifier: Modifier = Modifier,
    shape: Shape = EntryPanelShape,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollState: ScrollState? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(EntryPanelBrush)
            .border(1.dp, Color.White.copy(alpha = .07f), shape)
            .then(
                if (scrollState != null) {
                    Modifier.verticalScroll(scrollState)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
internal fun EntryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    primary: Boolean = false,
    selected: Boolean = false,
    minHeight: Dp = 44.dp,
    textSizeSp: Int = 15,
    onFocused: () -> Unit = {},
    leadingIcon: ImageVector? = null,
    horizontalPadding: Dp = 18.dp,
    verticalPadding: Dp = 11.dp,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val visuals = entryActionVisuals(
        colors = colors,
        focused = focused && enabled,
        selected = selected,
        primary = primary,
        enabled = enabled,
    )

    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(EntryActionShape)
            .background(visuals.background)
            .border(visuals.borderWidth, visuals.border, EntryActionShape)
            .semantics(mergeDescendants = true) {
                contentDescription = text
                if (!enabled) disabled()
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(
                enabled = true,
                role = Role.Button,
                onClick = { if (enabled) onClick() },
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(19.dp),
                    color = visuals.content,
                    strokeWidth = 2.dp,
                )
            } else if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = visuals.content,
                    modifier = Modifier.size(19.dp),
                )
            }
            Text(
                text = text,
                color = visuals.content,
                fontSize = textSizeSp.sp,
                lineHeight = (textSizeSp + 5).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
