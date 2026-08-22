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
        eyebrow = "HULK SA • تحديث مطلوب",
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
        eyebrow = "HULK SA • حالة الخدمة",
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
    isTv: Boolean = false,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 14.dp else 12.dp)
    val background = when {
        !enabled -> colors.surfaceRaised.copy(alpha = .62f)
        primary && focused -> colors.goldBright
        primary -> colors.gold.copy(alpha = .88f)
        focused -> colors.gold.copy(alpha = .26f)
        else -> Color(0xFF151711)
    }
    val borderColor = when {
        focused -> colors.goldBright
        primary -> colors.goldBright.copy(alpha = .42f)
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
            .border(if (focused) 3.dp else 1.dp, borderColor, shape)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                horizontal = if (isTv) 24.dp else 20.dp,
                vertical = if (isTv) 13.dp else 12.dp,
            ),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = if (isTv) 16.sp else 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OperationsStatusIcon(
    icon: ImageVector,
    accent: Color,
    isTv: Boolean,
) {
    val shape = RoundedCornerShape(if (isTv) 17.dp else 15.dp)
    Box(
        modifier = Modifier
            .size(if (isTv) 60.dp else 50.dp)
            .background(accent.copy(alpha = .13f), shape)
            .border(1.dp, accent.copy(alpha = .46f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(if (isTv) 33.dp else 27.dp),
        )
    }
}

@Composable
private fun OperationsMessagePanel(
    text: String,
    accent: Color,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(if (isTv) 15.dp else 13.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = .035f), shape)
            .border(1.dp, accent.copy(alpha = .20f), shape)
            .padding(
                horizontal = if (isTv) 19.dp else 15.dp,
                vertical = if (isTv) 15.dp else 12.dp,
            ),
    ) {
        Text(
            text = text,
            color = colors.textMuted,
            fontSize = if (isTv) 16.sp else 14.sp,
            lineHeight = if (isTv) 25.sp else 21.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
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
    val settingsVisible = operations?.download?.status == OperationsDownloadStatus.UNKNOWN_SOURCES_BLOCKED

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
            .safeDrawingPadding()
            .padding(if (isTv) 34.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val compact = !isTv && maxWidth < 430.dp
        val cardWidth = when {
            isTv -> (maxWidth * .46f).coerceIn(500.dp, 760.dp)
            maxWidth >= 700.dp -> 580.dp
            else -> maxWidth
        }
        val shape = RoundedCornerShape(if (isTv) 26.dp else 20.dp)
        Column(
            modifier = Modifier
                .width(cardWidth)
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .background(colors.surfaceRaised, shape)
                .border(if (isTv) 2.dp else 1.dp, colors.gold.copy(alpha = .72f), shape)
                .padding(horizontal = if (isTv) 30.dp else 19.dp, vertical = if (isTv) 28.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OperationsStatusIcon(icon = icon, accent = colors.goldBright, isTv = isTv)
            Spacer(Modifier.height(if (isTv) 14.dp else 11.dp))
            Text(
                eyebrow,
                color = colors.goldBright,
                fontSize = if (isTv) 13.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                color = colors.text,
                fontSize = if (isTv) 28.sp else 22.sp,
                lineHeight = if (isTv) 35.sp else 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (isTv) 16.dp else 13.dp))
            OperationsMessagePanel(
                text = message,
                accent = colors.goldBright,
                isTv = isTv,
            )

            if (downloading && operations != null) {
                Spacer(Modifier.height(if (isTv) 18.dp else 14.dp))
                OperationsDownloadProgress(operations = operations, isTv = isTv)
            } else {
                operations?.download?.message?.let { statusMessage ->
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

            if (!downloading) {
                Spacer(Modifier.height(if (isTv) 22.dp else 18.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = if (compact) 1 else 2,
                ) {
                    OperationsActionButton(
                        text = primaryLabel,
                        onClick = onPrimary,
                        primary = true,
                        isTv = isTv,
                        modifier = Modifier
                            .then(
                                if (compact) {
                                    Modifier.fillMaxWidth()
                                } else {
                                    Modifier.widthIn(min = if (isTv) 210.dp else 180.dp)
                                },
                            )
                            .heightIn(min = if (isTv) 56.dp else 50.dp)
                            .focusRequester(primaryRequester)
                            .then(
                                if (isTv && settingsVisible) {
                                    Modifier.focusProperties {
                                        right = FocusRequester.Cancel
                                        left = settingsRequester
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    if (settingsVisible) {
                        OperationsActionButton(
                            text = "فتح إعدادات التثبيت",
                            onClick = onOpenUnknownSourcesSettings,
                            primary = false,
                            isTv = isTv,
                            modifier = Modifier
                                .then(
                                    if (compact) {
                                        Modifier.fillMaxWidth()
                                    } else {
                                        Modifier.widthIn(min = if (isTv) 220.dp else 190.dp)
                                    },
                                )
                                .heightIn(min = if (isTv) 56.dp else 50.dp)
                                .focusRequester(settingsRequester)
                                .then(
                                    if (isTv) {
                                        Modifier.focusProperties {
                                            right = primaryRequester
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
            OperationsStatusIcon(
                icon = Icons.Rounded.SystemUpdate,
                accent = colors.goldBright,
                isTv = isTv,
            )
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
                OperationsMessagePanel(
                    text = operations.update.releaseNotes,
                    accent = colors.goldBright,
                    isTv = isTv,
                )
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
                        isTv = isTv,
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
                        isTv = isTv,
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
                    isTv = isTv,
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

@Composable
fun OperationsAnnouncementOverlay(
    announcement: OperationsAnnouncement,
    isTv: Boolean,
    onConfirm: () -> Unit,
) {
    BackHandler(onBack = onConfirm)
    val colors = LocalHulkColors.current
    val confirmRequester = remember(announcement.id) { FocusRequester() }
    val icon = when (announcement.severity) {
        OperationsAnnouncementSeverity.INFO -> Icons.Rounded.Info
        OperationsAnnouncementSeverity.WARNING,
        OperationsAnnouncementSeverity.IMPORTANT,
        -> Icons.Rounded.Warning
    }
    val severityLabel = when (announcement.severity) {
        OperationsAnnouncementSeverity.INFO -> "HULK SA • رسالة معلومات"
        OperationsAnnouncementSeverity.WARNING -> "HULK SA • تنبيه"
        OperationsAnnouncementSeverity.IMPORTANT -> "HULK SA • رسالة مهمة"
    }
    val accent = when (announcement.severity) {
        OperationsAnnouncementSeverity.INFO -> colors.goldBright
        OperationsAnnouncementSeverity.WARNING -> Color(0xFFFFC857)
        OperationsAnnouncementSeverity.IMPORTANT -> Color(0xFFFFB347)
    }

    LaunchedEffect(announcement.id, isTv) {
        if (isTv) {
            delay(110L)
            runCatching { confirmRequester.requestFocus() }
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
                .border(if (isTv) 2.dp else 1.dp, accent.copy(alpha = .72f), shape)
                .padding(horizontal = if (isTv) 30.dp else 19.dp, vertical = if (isTv) 28.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OperationsStatusIcon(icon = icon, accent = accent, isTv = isTv)
            Spacer(Modifier.height(if (isTv) 14.dp else 11.dp))
            Text(
                severityLabel,
                color = accent,
                fontSize = if (isTv) 13.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                announcement.title,
                color = colors.text,
                fontSize = if (isTv) 28.sp else 22.sp,
                lineHeight = if (isTv) 35.sp else 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (isTv) 16.dp else 13.dp))
            OperationsMessagePanel(
                text = announcement.message,
                accent = accent,
                isTv = isTv,
            )
            Spacer(Modifier.height(if (isTv) 22.dp else 18.dp))
            OperationsActionButton(
                text = "حسنًا",
                onClick = onConfirm,
                primary = true,
                isTv = isTv,
                modifier = Modifier
                    .then(
                        if (isTv) {
                            Modifier.widthIn(min = 220.dp, max = 300.dp)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    )
                    .heightIn(min = if (isTv) 56.dp else 50.dp)
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
        modifier = modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(horizontal = if (isTv) 36.dp else 12.dp, vertical = if (isTv) 20.dp else 9.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            Modifier
                .widthIn(max = if (isTv) 980.dp else 680.dp)
                .background(Color(0xF21A180F), RoundedCornerShape(14.dp))
                .border(1.dp, colors.gold.copy(alpha = .58f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = colors.goldBright,
                modifier = Modifier.size(if (isTv) 22.dp else 19.dp),
            )
            Text(
                text,
                color = colors.text,
                fontSize = if (isTv) 14.sp else 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun operationsUpdateButtonLabel(operations: OperationsUiState): String = when (operations.download.status) {
    OperationsDownloadStatus.DOWNLOADING -> operations.download.progressPercent?.let { "جارٍ التنزيل $it%" } ?: "جارٍ التنزيل…"
    OperationsDownloadStatus.INSTALLER_OPENED -> "فتح المثبت"
    OperationsDownloadStatus.UNKNOWN_SOURCES_BLOCKED -> "إعادة المحاولة"
    OperationsDownloadStatus.FAILED -> "إعادة المحاولة"
    OperationsDownloadStatus.IDLE -> if (operations.updateDecision == sa.hulksa.player.data.OperationsUpdateDecision.REQUIRED) {
        "تحديث التطبيق"
    } else {
        "تحديث الآن"
    }
}
