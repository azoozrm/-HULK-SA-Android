package sa.hulksa.player.ui.screens

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sa.hulksa.player.PairingPhase
import sa.hulksa.player.PairingUiState
import sa.hulksa.player.ui.components.EntryActionButton
import sa.hulksa.player.ui.components.EntryBackAction
import sa.hulksa.player.ui.components.EntryNotice
import sa.hulksa.player.ui.theme.LocalHulkColors

/**
 * TV-only quick-login dialog aligned with the accepted R07 reference: centered copy inside the
 * physical RIGHT pane (kicker, «الدخول السريع» / «من جوالك لشاشتك», instruction, manual destination
 * and public code box), a restrained divider, and the QR with its white quiet zone physically
 * LEFT. Footer actions are centered with رجوع physically LEFT of انشاء رمز جديد. Shows only public
 * render fields; the QR encodes exactly the validated canonical public activation URL and no
 * token, credential or password is displayed. First Back cancels through the single dialog owner;
 * initial focus is placement-driven.
 */
@Composable
internal fun PairingLoginDialog(
    state: PairingUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val backRequester = remember { FocusRequester() }
    val retryRequester = remember { FocusRequester() }
    val regenerateRequester = remember { FocusRequester() }
    var backPlaced by remember { mutableStateOf(false) }
    var initialFocusRequested by remember { mutableStateOf(false) }

    // Placement-driven initial focus: request only after the real target has been placed, never
    // from a frame count that can miss delayed attachment.
    LaunchedEffect(backPlaced) {
        if (backPlaced && !initialFocusRequested) {
            initialFocusRequested = true
            runCatching { backRequester.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .82f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val compactHeight = maxHeight < 620.dp
            val wide = maxWidth >= 840.dp
            val shape = RoundedCornerShape(24.dp)
            val actionFloorDp = movieActionHeightDp(isTv = true, compactHeight = false)
            val waiting = state.phase == PairingPhase.WAITING && state.message == null
            val bodyBudget = (maxHeight - 128.dp).coerceAtLeast(110.dp)

            if (wide) {
                val qrSize = minOf(bodyBudget * .62f, 210.dp)
                    .coerceAtLeast(130.dp)
                    .coerceAtMost(bodyBudget)
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color(0xFF11120E))
                        .border(1.dp, colors.gold.copy(alpha = .42f), shape)
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                ) {
                    when {
                        state.phase == PairingPhase.CREATING -> PairingPreparing(compactHeight)
                        waiting -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PairingCopyColumn(
                                state = state,
                                compactHeight = compactHeight,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(24.dp))
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(qrSize + 70.dp)
                                    .background(colors.gold.copy(alpha = .18f)),
                            )
                            Spacer(Modifier.width(24.dp))
                            PairingQrColumn(
                                state = state,
                                qrSize = qrSize,
                                compactHeight = compactHeight,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        else -> state.message?.let { message ->
                            EntryNotice(
                                message = message,
                                networkFailure = state.networkFailure,
                                compact = compactHeight,
                            )
                        }
                    }
                    Spacer(Modifier.height(if (compactHeight) 12.dp else 18.dp))
                    PairingDialogActions(
                        state = state,
                        actionFloorDp = actionFloorDp,
                        backRequester = backRequester,
                        retryRequester = retryRequester,
                        regenerateRequester = regenerateRequester,
                        onBackPlaced = { backPlaced = true },
                        onDismiss = onDismiss,
                        onRetry = onRetry,
                        onRegenerate = onRegenerate,
                    )
                }
            } else {
                val qrSize = minOf(
                    maxWidth * if (compactHeight) .48f else .55f,
                    bodyBudget * if (compactHeight) .60f else .68f,
                    if (compactHeight) 260.dp else 300.dp,
                ).coerceAtLeast(minOf(if (compactHeight) 140.dp else 170.dp, bodyBudget))
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color(0xFF11120E))
                        .border(1.dp, colors.gold.copy(alpha = .42f), shape)
                        .padding(
                            horizontal = if (compactHeight) 22.dp else 26.dp,
                            vertical = if (compactHeight) 12.dp else 18.dp,
                        ),
                ) {
                    when {
                        state.phase == PairingPhase.CREATING -> PairingPreparing(compactHeight)
                        waiting -> Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 7.dp else 9.dp),
                        ) {
                            PairingCopyColumn(
                                state = state,
                                compactHeight = compactHeight,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            PairingQrColumn(
                                state = state,
                                qrSize = qrSize,
                                compactHeight = compactHeight,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        else -> state.message?.let { message ->
                            EntryNotice(
                                message = message,
                                networkFailure = state.networkFailure,
                                compact = compactHeight,
                            )
                        }
                    }
                    Spacer(Modifier.height(if (compactHeight) 10.dp else 14.dp))
                    PairingDialogActions(
                        state = state,
                        actionFloorDp = actionFloorDp,
                        backRequester = backRequester,
                        retryRequester = retryRequester,
                        regenerateRequester = regenerateRequester,
                        onBackPlaced = { backPlaced = true },
                        onDismiss = onDismiss,
                        onRetry = onRetry,
                        onRegenerate = onRegenerate,
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingPreparing(compactHeight: Boolean) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (compactHeight) 22.dp else 26.dp),
            color = colors.gold,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "جاري تجهيز رمز الربط",
            color = colors.textMuted,
            fontSize = if (compactHeight) 14.sp else 15.sp,
        )
    }
}

@Composable
private fun PairingCopyColumn(
    state: PairingUiState,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val titleSize = if (compactHeight) 24.sp else 28.sp
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 7.dp else 9.dp),
    ) {
        Text(
            text = "الدخول السريع",
            color = colors.text,
            fontSize = titleSize,
            lineHeight = (titleSize.value + 5).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "من جوالك لشاشتك",
            color = colors.gold,
            fontSize = titleSize,
            lineHeight = (titleSize.value + 5).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "امسح الرمز بجوالك لتسجيل الدخول على التلفزيون",
            color = colors.textMuted,
            fontSize = if (compactHeight) 13.sp else 15.sp,
            lineHeight = if (compactHeight) 20.sp else 23.sp,
            textAlign = TextAlign.Center,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "او افتح \u2066hulksa.com/pair\u2069",
                color = colors.textMuted,
                fontSize = if (compactHeight) 12.sp else 13.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "وادخل رمز الربط",
                color = colors.textMuted,
                fontSize = if (compactHeight) 12.sp else 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        val codeShape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(codeShape)
                .background(Color(0xFF1A1B15))
                .border(1.dp, colors.gold.copy(alpha = .25f), codeShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.userCode.orEmpty(),
                color = colors.text,
                fontSize = if (compactHeight) 19.sp else 22.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center,
                style = TextStyle(textDirection = TextDirection.Ltr),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.gold),
            )
            Text(
                text = "بانتظار موافقتك على الجوال",
                color = colors.textMuted,
                fontSize = if (compactHeight) 12.sp else 13.sp,
            )
        }
    }
}

@Composable
private fun PairingQrColumn(
    state: PairingUiState,
    qrSize: Dp,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 7.dp else 9.dp),
    ) {
        PairingQrPane(
            verificationUrl = state.verificationUrl,
            qrSize = qrSize,
        )
        Text(
            text = "افتح كاميرا الجوال وامسح الرمز",
            color = colors.textMuted,
            fontSize = if (compactHeight) 12.sp else 13.sp,
            textAlign = TextAlign.Center,
        )
        PairingExpiryPill(deadlineElapsedRealtimeMs = state.deadlineElapsedRealtimeMs)
    }
}

@Composable
private fun PairingExpiryPill(deadlineElapsedRealtimeMs: Long?) {
    val colors = LocalHulkColors.current
    val remaining by produceState(
        initialValue = pairingRemainingSeconds(
            deadlineElapsedRealtimeMs = deadlineElapsedRealtimeMs,
            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
        ),
        key1 = deadlineElapsedRealtimeMs,
    ) {
        while (deadlineElapsedRealtimeMs != null) {
            value = pairingRemainingSeconds(
                deadlineElapsedRealtimeMs = deadlineElapsedRealtimeMs,
                nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            )
            delay(1_000L)
        }
    }
    val active = remaining > 0L
    val text = if (active) {
        val minutes = (remaining / 60L).toString().padStart(2, '0')
        val seconds = (remaining % 60L).toString().padStart(2, '0')
        "ينتهي خلال \u2066$minutes:$seconds\u2069"
    } else {
        "انتهت صلاحية رمز الربط"
    }
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(colors.gold.copy(alpha = .06f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // Physical RIGHT: «ينتهي خلال» then the live ASCII LTR value; physical LEFT: the single
        // existing clock glyph immediately after the complete sentence group.
        Text(
            text = text,
            color = if (active) colors.gold else colors.danger,
            fontSize = 13.sp,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            tint = if (active) colors.gold else colors.danger,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun PairingQrPane(
    verificationUrl: String?,
    qrSize: Dp,
) {
    val qrPixels = with(LocalDensity.current) { qrSize.roundToPx().coerceIn(256, 1_400) }
    val generated by produceState<Bitmap?>(
        initialValue = null,
        key1 = verificationUrl,
        key2 = qrPixels,
    ) {
        value = verificationUrl?.takeIf(String::isNotBlank)?.let { url ->
            withContext(Dispatchers.Default) { encodeQrBitmap(url, qrPixels) }
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(qrSize)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color.White, shape)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        generated?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "رمز QR لتسجيل الدخول بالجوال",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PairingDialogActions(
    state: PairingUiState,
    actionFloorDp: Int,
    backRequester: FocusRequester,
    retryRequester: FocusRequester,
    regenerateRequester: FocusRequester,
    onBackPlaced: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val waiting = state.phase == PairingPhase.WAITING && state.message == null
    val hasRetry = state.canRetry
    // The accepted R07 waiting state keeps "انشاء رمز جديد" available next to رجوع; regeneration
    // is already allowed by the controller while the attempt is not consumed.
    val hasRegenerate = state.canRegenerate || waiting
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.gold.copy(alpha = .14f)),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasRegenerate) {
                EntryActionButton(
                    text = "انشاء رمز جديد",
                    onClick = onRegenerate,
                    outlined = true,
                    glyph = Icons.Rounded.Refresh,
                    minHeightDp = actionFloorDp,
                    modifier = Modifier
                        .focusRequester(regenerateRequester)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            left = if (hasRetry) retryRequester else backRequester
                            right = FocusRequester.Cancel
                        },
                )
            }
            if (hasRegenerate && hasRetry) Spacer(Modifier.width(10.dp))
            if (hasRetry) {
                EntryActionButton(
                    text = "اعادة المحاولة",
                    onClick = onRetry,
                    outlined = true,
                    glyph = Icons.Rounded.Autorenew,
                    minHeightDp = actionFloorDp,
                    modifier = Modifier
                        .focusRequester(retryRequester)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            left = backRequester
                            right = if (hasRegenerate) regenerateRequester else FocusRequester.Cancel
                        },
                )
            }
            if (hasRetry || hasRegenerate) Spacer(Modifier.width(10.dp))
            EntryBackAction(
                onClick = onDismiss,
                minHeightDp = actionFloorDp,
                modifier = Modifier
                    .focusRequester(backRequester)
                    .onPlaced { onBackPlaced() }
                    .focusProperties {
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = when {
                            hasRetry -> retryRequester
                            hasRegenerate -> regenerateRequester
                            else -> FocusRequester.Cancel
                        }
                    },
            )
        }
    }
}

/**
 * Displayed countdown derived from the same monotonic deadline the controller uses, so wall-clock
 * shifts cannot extend or shorten the displayed validity. Returns 0 at or after the local deadline.
 */
internal fun pairingRemainingSeconds(
    deadlineElapsedRealtimeMs: Long?,
    nowElapsedRealtimeMs: Long,
): Long {
    if (deadlineElapsedRealtimeMs == null) return 0L
    val remainingMs = deadlineElapsedRealtimeMs - nowElapsedRealtimeMs
    if (remainingMs <= 0L) return 0L
    return (remainingMs + 999L) / 1_000L
}
