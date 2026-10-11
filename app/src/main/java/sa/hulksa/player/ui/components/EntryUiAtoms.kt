package sa.hulksa.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.ui.screens.moviesErrorIcon
import sa.hulksa.player.ui.theme.LocalHulkColors

/**
 * Scoped bounded-panel maximum width for the entry section, reusing the already accepted
 * AccountIdentityDecisionDialog expression: 560dp on TV/expanded windows, 440dp otherwise.
 * Apply the cap BEFORE fillMaxWidth so an inherited full-width minimum cannot defeat it.
 */
internal fun entryPanelMaxWidthDp(expanded: Boolean): Int = if (expanded) 560 else 440

/**
 * Surface role for a scoped entry-section action. A genuinely disabled non-loading action uses the
 * dimmed surface; a still-loading primary keeps the theme gold surface (the owner's readable busy
 * state) even though submission is guarded and the action is semantically disabled.
 */
internal enum class EntryActionSurface { DISABLED, DANGER, PRIMARY, OUTLINED, FOCUSED, IDLE }

internal fun entryActionSurface(
    primary: Boolean,
    outlined: Boolean,
    danger: Boolean,
    enabled: Boolean,
    loading: Boolean,
    focused: Boolean,
): EntryActionSurface = when {
    !enabled && !loading -> EntryActionSurface.DISABLED
    danger -> EntryActionSurface.DANGER
    primary -> EntryActionSurface.PRIMARY
    outlined -> EntryActionSurface.OUTLINED
    focused -> EntryActionSurface.FOCUSED
    else -> EntryActionSurface.IDLE
}

/**
 * Existing truthful generic error copy (same wording the authentication failure fallback uses),
 * shown when terminal-decoration cleanup would otherwise leave an empty notice.
 */
internal const val ENTRY_ERROR_FALLBACK_TEXT = "حدث خطا غير متوقع. حاول مرة اخرى"

private fun isTerminalErrorDecoration(character: Char): Boolean =
    character == '.' ||
        character == '\u2026' ||
        character == '\u06D4' ||
        character.isWhitespace() ||
        character == '\u200B' ||
        character == '\u200E' ||
        character == '\u200F' ||
        character == '\u061C' ||
        character == '\u2066' ||
        character == '\u2067' ||
        character == '\u2068' ||
        character == '\u2069'

/**
 * Display-copy rule for app-presented errors: remove only trailing full stops / ellipsis (and
 * trailing whitespace/directional marks), never interior punctuation, decimals, domains or
 * meaningful content. The raw error remains the classification/identity source.
 */
internal fun entryErrorDisplayText(raw: String): String {
    var end = raw.length
    while (end > 0 && isTerminalErrorDecoration(raw[end - 1])) end--
    val stripped = raw.substring(0, end)
    return stripped.ifBlank { ENTRY_ERROR_FALLBACK_TEXT }
}

/**
 * Scoped entry-section notice family (Login / Who's Watching / entry options / management / PIN /
 * account choice / Growth QR). One bounded dark surface with the accepted gold line, the gold
 * status glyph physically LEFT of right-aligned ivory wording and a muted explanation when the
 * caller supplies a title. Complete copy wraps; no fixed failure slot elides the message.
 */
@Composable
internal fun EntryNotice(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    networkFailure: Boolean = false,
    compact: Boolean = false,
    liveRegionPolite: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    val displayMessage = entryErrorDisplayText(message)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF11120D))
            .border(1.dp, colors.gold.copy(alpha = .35f), shape)
            .padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 8.dp else 10.dp,
            )
            .then(
                if (liveRegionPolite) {
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        color = colors.text,
                        fontSize = if (compact) 12.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = displayMessage,
                    color = if (title.isNullOrBlank()) colors.text else colors.textMuted,
                    fontSize = if (compact) 12.sp else 13.sp,
                    lineHeight = if (compact) 16.sp else 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = moviesErrorIcon(networkFailure),
                contentDescription = null,
                tint = colors.gold,
                modifier = Modifier.size(if (compact) 16.dp else 18.dp),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                action()
            }
        }
    }
}

/**
 * Scoped compact simple action for the entry section. Uses the accepted Details action atom:
 * 13sp bold complete caption, 17dp single glyph, 6dp internal gap, 12dp/9dp padding, 12dp shape,
 * `movieActionHeightDp` minimum height and in-bounds pale focus with no scale or position change.
 * Under RTL the caption sits physically RIGHT and the glyph physically LEFT. Loading replaces only
 * the glyph slot; a loading or disabled action stays focusable and executes nothing twice.
 */
@Composable
internal fun EntryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    outlined: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    glyph: ImageVector? = null,
    glyphTint: Color? = null,
    minHeightDp: Int = 46,
    captionGlyphGap: Dp = 6.dp,
    onFocused: (() -> Unit)? = null,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val interactive = enabled && !loading
    val goldFocused = primary && focused
    val background = when (
        entryActionSurface(
            primary = primary,
            outlined = outlined,
            danger = danger,
            enabled = enabled,
            loading = loading,
            focused = focused,
        )
    ) {
        EntryActionSurface.DISABLED -> colors.surfaceRaised.copy(alpha = .5f)
        EntryActionSurface.DANGER -> colors.danger.copy(alpha = if (focused) .22f else .14f)
        EntryActionSurface.PRIMARY -> colors.gold
        EntryActionSurface.OUTLINED -> Color(0xFF151711)
        EntryActionSurface.FOCUSED -> Color(0xFF1E2019)
        EntryActionSurface.IDLE -> Color(0xFF181914)
    }
    val foreground = when {
        primary -> Color(0xFF100E04)
        danger -> colors.danger
        else -> colors.text
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = minHeightDp.dp)
            .clip(shape)
            .background(background)
            .goldFocusEdge(shape = shape, visible = goldFocused)
            .border(
                width = when {
                    goldFocused -> 0.dp
                    focused -> 2.dp
                    danger || outlined -> 1.dp
                    else -> 0.dp
                },
                color = when {
                    goldFocused -> Color.Transparent
                    focused -> colors.goldBright
                    danger -> colors.danger.copy(alpha = .45f)
                    outlined -> colors.gold.copy(alpha = .42f)
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = text
                if (!enabled) disabled()
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .onPreviewKeyEvent { event ->
                val remoteSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!remoteSelect) {
                    false
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> true
                        KeyEventType.KeyUp -> {
                            if (interactive) onClick()
                            true
                        }
                        else -> false
                    }
                }
            }
            .clickable(role = Role.Button, onClick = { if (interactive) onClick() })
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(captionGlyphGap),
        ) {
            Text(
                text = text,
                color = foreground,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    color = if (primary) Color(0xFF100E04) else colors.gold,
                    strokeWidth = 2.dp,
                )
                glyph != null -> Icon(
                    imageVector = glyph,
                    contentDescription = null,
                    tint = glyphTint ?: when {
                        primary -> Color(0xFF100E04)
                        danger -> colors.danger
                        else -> colors.gold
                    },
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}
