package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import sa.hulksa.player.data.OperationsAnnouncement
import sa.hulksa.player.data.OperationsAnnouncementSeverity
import sa.hulksa.player.data.OperationsDownloadStatus
import sa.hulksa.player.data.OperationsServiceConfig
import sa.hulksa.player.data.OperationsUiState
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun RequiredUpdateScreen(
    operations: OperationsUiState,
    isTv: Boolean,
    onUpdate: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
) {
    BackHandler(enabled = true) { }
    OperationsBlockingCard(
        icon = Icons.Rounded.SystemUpdate,
        eyebrow = "تحديث مطلوب",
        title = "هذا الإصدار لم يعد مدعومًا",
        message = buildString {
            append("حدّث HULK SA إلى الإصدار ")
            append(operations.update.latestVersionName)
            if (operations.update.releaseNotes.isNotBlank()) {
                append("\n\n")
                append(operations.update.releaseNotes)
            }
        },
        isTv = isTv,
        operations = operations,
        primaryLabel = operationsUpdateButtonLabel(operations),
        onPrimary = onUpdate,
        onOpenUnknownSourcesSettings = onOpenUnknownSourcesSettings,
    )
}

@Composable
fun MaintenanceScreen(
    service: OperationsServiceConfig,
    isTv: Boolean,
    onRetry: () -> Unit,
) {
    BackHandler(enabled = true) { }
    val message = service.message?.takeIf(String::isNotBlank)
        ?: "نعمل حاليًا على تحسين الخدمة. يرجى المحاولة بعد قليل."
    val estimated = service.estimatedEndAtEpochSeconds?.let { epoch ->
        val formatted = SimpleDateFormat("yyyy/MM/dd · HH:mm", Locale.forLanguageTag("ar-SA"))
            .format(Date(epoch * 1_000L))
        "\n\nالوقت المتوقع للانتهاء: $formatted"
    }.orEmpty()
    OperationsBlockingCard(
        icon = Icons.Rounded.Build,
        eyebrow = "HULK SA",
        title = "الخدمة تحت الصيانة",
        message = message + estimated,
        isTv = isTv,
        operations = null,
        primaryLabel = "إعادة المحاولة",
        onPrimary = onRetry,
        onOpenUnknownSourcesSettings = {},
    )
}

@Composable
private fun OperationsActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean,
    enabled: Boolean = true,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        !enabled -> colors.surfaceRaised.copy(alpha = .62f)
        primary && focused -> colors.goldBright
        primary -> colors.gold
        focused -> colors.gold.copy(alpha = .22f)
        else -> Color(0xFF151711)
    }
    val borderColor = when {
        focused -> colors.goldBright
        primary -> colors.goldBright.copy(alpha = .38f)
        else -> colors.gold.copy(alpha = .42f)
    }
    val textColor = when {
        !enabled -> colors.textMuted
        primary -> Color.Black
        focused -> colors.goldBright
        else -> colors.text
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OperationsDownloadProgress(
    operations: OperationsUiState,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val progress = operations.download.progressPercent?.coerceIn(0, 100)
    val fraction = (progress ?: 0) / 100f
    val shape = RoundedCornerShape(if (isTv) 14.dp else 12.dp)
    val trackShape = RoundedCornerShape(50)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = .035f), shape)
            .border(1.dp, colors.gold.copy(alpha = .24f), shape)
            .padding(horizontal = if (isTv) 18.dp else 14.dp, vertical = if (isTv) 14.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "جارٍ تنزيل التحديث",
                color = colors.text,
                fontSize = if (isTv) 15.sp else 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                progress?.let { "$it%" } ?: "…",
                color = colors.goldBright,
                fontSize = if (isTv) 16.sp else 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.height(if (isTv) 10.dp else 8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTv) 10.dp else 8.dp)
                .clip(trackShape)
                .background(Color.White.copy(alpha = .12f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(colors.goldBright, trackShape),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OperationsBlockingCard(
    icon: ImageVector,
    eyebrow: String,
    title: String,
    message: String,
    isTv: Boolean,
    operations: OperationsUiState?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val primaryRequester = remember { FocusRequester() }
    val settingsRequester = remember { FocusRequester() }
    val downloading = operations?.download?.status == OperationsDownloadStatus.DOWNLOADING
    LaunchedEffect(isTv, operations?.download?.status) {
        if (isTv && !downloading) {
            delay(110L)
            runCatching { primaryRequester.requestFocus() }
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val compact = !isTv && maxWidth < 430.dp
        val horizontalPadding = if (isTv) (maxWidth * .055f).coerceIn(28.dp, 92.dp) else 16.dp
        val verticalPadding = if (isTv) (maxHeight * .055f).coerceIn(22.dp, 62.dp) else 16.dp
        val cardWidth = when {
            isTv -> ((maxWidth - horizontalPadding * 2) * .58f).coerceIn(520.dp, 940.dp)
            maxWidth >= 700.dp -> 620.dp
            else -> maxWidth - horizontalPadding * 2
        }
        Column(
            modifier = Modifier
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .width(cardWidth)
                .widthIn(max = 940.dp)
                .heightIn(max = maxHeight - verticalPadding * 2)
                .verticalScroll(rememberScrollState())
                .background(colors.surfaceRaised, RoundedCornerShape(if (isTv) 28.dp else 20.dp))
                .border(2.dp, colors.gold.copy(alpha = .7f), RoundedCornerShape(if (isTv) 28.dp else 20.dp))
                .padding(if (isTv) 34.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLogo(Modifier.size(if (isTv) 88.dp else 64.dp))
            Spacer(Modifier.height(if (isTv) 18.dp else 13.dp))
            Icon(icon, null, tint = colors.goldBright, modifier = Modifier.size(if (isTv) 38.dp else 30.dp))
            Text(eyebrow, color = colors.goldBright, fontSize = if (isTv) 15.sp else 12.sp, fontWeight = FontWeight.Bold)
            Text(
                title,
                color = colors.text,
                fontSize = if (isTv) 30.sp else 23.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (isTv) 14.dp else 10.dp))
            Text(
                message,
                color = colors.textMuted,
                fontSize = if (isTv) 17.sp else 14.sp,
                textAlign = TextAlign.Center,
                maxLines = if (isTv) 10 else 12,
                overflow = TextOverflow.Ellipsis,
            )
            if (downloading && operations != null) {
                Spacer(Modifier.height(if (isTv) 18.dp else 14.dp))
                OperationsDownloadProgress(operations = operations, isTv = isTv)
            } else {
                operations?.download?.message?.let { statusMessage ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        statusMessage,
                        color = if (operations.download.status == OperationsDownloadStatus.FAILED) {
                            Color(0xFFFF9A9A)
                        } else {
                            colors.goldBright
                        },
                        fontSize = if (isTv) 14.sp else 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (!downloading) {
                Spacer(Modifier.height(if (isTv) 22.dp else 16.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = if (compact) 1 else 2,
                ) {
                    if (operations != null) {
                        OperationsActionButton(
                            text = primaryLabel,
                            onClick = onPrimary,
                            primary = true,
                            modifier = Modifier
                                .then(if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = 180.dp))
                                .heightIn(min = 52.dp)
                                .focusRequester(primaryRequester)
                                .focusProperties {
                                    right = FocusRequester.Cancel
                                    left = if (operations.download.status == OperationsDownloadStatus.UNKNOWN_SOURCES_BLOCKED) {
                                        settingsRequester
                                    } else {
                                        FocusRequester.Cancel
                                    }
                                },
                        )
                    } else {
                        FocusButton(
                            text = primaryLabel,
                            onClick = onPrimary,
                            scaleOnFocus = false,
                            modifier = Modifier
                                .then(if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = 180.dp))
                                .heightIn(min = 52.dp)
                                .focusRequester(primaryRequester),
                        )
                    }
                    if (operations?.download?.status == OperationsDownloadStatus.UNKNOWN_SOURCES_BLOCKED) {
                        OperationsActionButton(
                            text = "فتح إعدادات التثبيت",
                            onClick = onOpenUnknownSourcesSettings,
                            primary = false,
                            modifier = Modifier
                                .then(if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = 190.dp))
                                .heightIn(min = 52.dp)
                                .focusRequester(settingsRequester)
                                .focusProperties {
                                    right = primaryRequester
                                    left = FocusRequester.Cancel
                                },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionalUpdateOverlay(
    operations: OperationsUiState,
    isTv: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
) {
    BackHandler(onBack = onLater)
    val colors = LocalHulkColors.current
    val updateRequester = remember { FocusRequester() }
    val laterRequester = remember { FocusRequester() }
    val settingsRequester = remember { FocusRequester() }
    val downloading = operations.download.status == OperationsDownloadStatus.DOWNLOADING
    val settingsVisible = operations.download.status == OperationsDownloadStatus.UNKNOWN_SOURCES_BLOCKED

    LaunchedEffect(isTv, operations.download.status) {
        if (isTv) {
            delay(100L)
            runCatching {
                if (downloading) {
                    laterRequester.requestFocus()
                } else {
                    updateRequester.requestFocus()
                }
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .72f))
            .safeDrawingPadding()
            .padding(if (isTv) 34.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val compact = !isTv && maxWidth < 430.dp
        val width = when {
            isTv -> (maxWidth * .46f).coerceIn(500.dp, 760.dp)
            maxWidth >= 700.dp -> 580.dp
            else -> maxWidth
        }
        val shape = RoundedCornerShape(if (isTv) 26.dp else 20.dp)
        Column(
            modifier = Modifier
                .width(width)
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .background(colors.surfaceRaised, shape)
                .border(if (isTv) 2.dp else 1.dp, colors.gold.copy(alpha = .72f), shape)
                .padding(horizontal = if (isTv) 30.dp else 19.dp, vertical = if (isTv) 28.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(if (isTv) 58.dp else 48.dp)
                    .background(colors.gold.copy(alpha = .14f), RoundedCornerShape(16.dp))
                    .border(1.dp, colors.goldBright.copy(alpha = .42f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = colors.goldBright,
                    modifier = Modifier.size(if (isTv) 32.dp else 26.dp),
                )
            }
            Spacer(Modifier.height(if (isTv) 14.dp else 11.dp))
            Text(
                "HULK SA • تحديث التطبيق",
                color = colors.goldBright,
                fontSize = if (isTv) 13.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "يتوفر تحديث جديد",
                color = colors.text,
                fontSize = if (isTv) 28.sp else 22.sp,
                lineHeight = if (isTv) 34.sp else 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "الإصدار ${operations.update.latestVersionName}",
                color = colors.textMuted,
                fontSize = if (isTv) 14.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (operations.update.releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(if (isTv) 16.dp else 13.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = .035f), RoundedCornerShape(14.dp))
                        .border(1.dp, colors.gold.copy(alpha = .18f), RoundedCornerShape(14.dp))
                        .padding(horizontal = if (isTv) 16.dp else 13.dp, vertical = if (isTv) 12.dp else 10.dp),
                ) {
                    Text(
                        operations.update.releaseNotes,
                        color = colors.textMuted,
                        fontSize = if (isTv) 15.sp else 13.sp,
                        lineHeight = if (isTv) 22.sp else 19.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (downloading) {
                Spacer(Modifier.height(if (isTv) 18.dp else 15.dp))
                OperationsDownloadProgress(operations = operations, isTv = isTv)
            } else {
                operations.download.message?.let { statusMessage ->
                    Spacer(Modifier.height(if (isTv) 13.dp else 10.dp))
                    Text(
                        statusMessage,
                        color = if (operations.download.status == OperationsDownloadStatus.FAILED) {
                            Color(0xFFFF9A9A)
                        } else {
                            colors.goldBright
                        },
                        fontSize = if (isTv) 13.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(if (isTv) 22.dp else 18.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = if (compact) 1 else 3,
            ) {
                if (!downloading) {
                    OperationsActionButton(
                        text = operationsUpdateButtonLabel(operations),
                        onClick = onUpdate,
                        primary = true,
                        modifier = Modifier
                            .then(if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = if (isTv) 180.dp else 150.dp))
                            .heightIn(min = if (isTv) 54.dp else 50.dp)
                            .focusRequester(updateRequester)
                            .then(
                                if (isTv) {
                                    Modifier.focusProperties {
                                        right = FocusRequester.Cancel
                                        left = if (settingsVisible) settingsRequester else laterRequester
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
                if (settingsVisible) {
                    OperationsActionButton(
                        text = "إعدادات التثبيت",
                        onClick = onOpenUnknownSourcesSettings,
                        primary = false,
                        modifier = Modifier
                            .then(if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = if (isTv) 190.dp else 155.dp))
                            .heightIn(min = if (isTv) 54.dp else 50.dp)
                            .focusRequester(settingsRequester)
                            .then(
                                if (isTv) {
                                    Modifier.focusProperties {
                                        right = updateRequester
                                        left = laterRequester
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
                OperationsActionButton(
                    text = "لاحقًا",
                    onClick = onLater,
                    primary = false,
                    modifier = Modifier
                        .then(if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = if (isTv) 170.dp else 140.dp))
                        .heightIn(min = if (isTv) 54.dp else 50.dp)
                        .focusRequester(laterRequester)
                        .then(
                            if (isTv && !downloading) {
                                Modifier.focusProperties {
                                    right = if (settingsVisible) settingsRequester else updateRequester
                                    left = FocusRequester.Cancel
                                }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OperationsAnnouncementOverlay(
    announcement: OperationsAnnouncement,
    isTv: Boolean,
    onConfirm: () -> Unit,
) {
    BackHandler(onBack = onConfirm)
    val colors = LocalHulkColors.current
    val confirmRequester = remember(announcement.id) { FocusRequester() }
    LaunchedEffect(announcement.id, isTv) {
        if (isTv) {
            delay(100L)
            runCatching { confirmRequester.requestFocus() }
        }
    }
    val icon = when (announcement.severity) {
        OperationsAnnouncementSeverity.INFO -> Icons.Rounded.Info
        OperationsAnnouncementSeverity.WARNING,
        OperationsAnnouncementSeverity.IMPORTANT,
        -> Icons.Rounded.Warning
    }
    val severityLabel = when (announcement.severity) {
        OperationsAnnouncementSeverity.INFO -> "رسالة من HULK SA"
        OperationsAnnouncementSeverity.WARNING -> "تنبيه من HULK SA"
        OperationsAnnouncementSeverity.IMPORTANT -> "إشعار مهم من HULK SA"
    }
    val accent = when (announcement.severity) {
        OperationsAnnouncementSeverity.INFO -> colors.goldBright
        OperationsAnnouncementSeverity.WARNING -> Color(0xFFFFC857)
        OperationsAnnouncementSeverity.IMPORTANT -> Color(0xFFFFB347)
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .72f))
            .safeDrawingPadding()
            .padding(if (isTv) 34.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val compact = !isTv && maxWidth < 430.dp
        val width = when {
            isTv -> (maxWidth * .42f).coerceIn(500.dp, 690.dp)
            maxWidth >= 700.dp -> 560.dp
            else -> maxWidth
        }
        val shape = RoundedCornerShape(if (isTv) 26.dp else 20.dp)
        Column(
            Modifier
                .width(width)
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .background(colors.surfaceRaised, shape)
                .border(if (isTv) 2.dp else 1.dp, accent.copy(alpha = .68f), shape)
                .padding(horizontal = if (isTv) 34.dp else 20.dp, vertical = if (isTv) 30.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLogo(Modifier.size(if (isTv) 86.dp else 62.dp))
            Spacer(Modifier.height(if (isTv) 10.dp else 8.dp))
            Text(
                "HULK SA",
                color = colors.text,
                fontSize = if (isTv) 18.sp else 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                "رسالة رسمية من التطبيق",
                color = colors.textMuted,
                fontSize = if (isTv) 12.sp else 10.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(if (isTv) 18.dp else 14.dp))
            Box(
                modifier = Modifier
                    .size(if (isTv) 56.dp else 46.dp)
                    .background(accent.copy(alpha = .13f), RoundedCornerShape(50))
                    .border(1.dp, accent.copy(alpha = .48f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(if (isTv) 30.dp else 24.dp),
                )
            }
            Spacer(Modifier.height(if (isTv) 11.dp else 9.dp))
            Text(
                severityLabel,
                color = accent,
                fontSize = if (isTv) 14.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (isTv) 6.dp else 5.dp))
            Text(
                announcement.title,
                color = colors.text,
                fontSize = if (isTv) 29.sp else 22.sp,
                lineHeight = if (isTv) 36.sp else 29.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (isTv) 13.dp else 11.dp))
            Text(
                announcement.message,
                color = colors.textMuted,
                fontSize = if (isTv) 17.sp else 14.sp,
                lineHeight = if (isTv) 26.sp else 21.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (isTv) 24.dp else 19.dp))
            FocusButton(
                text = "حسنًا",
                onClick = onConfirm,
                scaleOnFocus = false,
                modifier = Modifier
                    .then(if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = 190.dp))
                    .heightIn(min = if (isTv) 54.dp else 50.dp)
                    .focusRequester(confirmRequester),
            )
        }
    }
}

@Composable
fun OperationsStatusBanner(
    operations: OperationsUiState,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val announcement = operations.persistentAnnouncement
    val serviceDegraded = operations.service.status == sa.hulksa.player.data.OperationsServiceStatus.DEGRADED
    val serviceMessage = if (serviceDegraded) {
        operations.service.message?.takeIf(String::isNotBlank)
            ?: "قد تواجه بعض البطء في الخدمة حاليًا"
    } else {
        null
    }
    val announcementMessage = announcement?.let { "${it.title}: ${it.message}" }
    val text = listOfNotNull(serviceMessage, announcementMessage)
        .joinToString(" • ")
        .takeIf(String::isNotBlank)
        ?: return
    val colors = LocalHulkColors.current
    Box(
        modifier = modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = if (isTv) 36.dp else 12.dp, vertical = if (isTv) 20.dp else 9.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            Modifier.widthIn(max = if (isTv) 980.dp else 680.dp).background(Color(0xF21A180F), RoundedCornerShape(14.dp))
                .border(1.dp, colors.gold.copy(alpha = .58f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(Icons.Rounded.Warning, null, tint = colors.goldBright, modifier = Modifier.size(if (isTv) 22.dp else 19.dp))
            Text(text, color = colors.text, fontSize = if (isTv) 14.sp else 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun operationsUpdateButtonLabel(operations: OperationsUiState): String = when (operations.download.status) {
    OperationsDownloadStatus.DOWNLOADING -> operations.download.progressPercent?.let { "جارٍ التنزيل $it%" } ?: "جارٍ التنزيل…"
    OperationsDownloadStatus.INSTALLER_OPENED -> "فتح المثبت"
    OperationsDownloadStatus.UNKNOWN_SOURCES_BLOCKED -> "إعادة المحاولة"
    OperationsDownloadStatus.FAILED -> "إعادة المحاولة"
    OperationsDownloadStatus.IDLE -> if (operations.updateDecision == sa.hulksa.player.data.OperationsUpdateDecision.REQUIRED) "تحديث التطبيق" else "تحديث الآن"
}
