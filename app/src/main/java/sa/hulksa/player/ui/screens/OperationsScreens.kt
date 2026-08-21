package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    LaunchedEffect(isTv, operations?.download?.status) {
        if (isTv) {
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
            operations?.download?.message?.let { error ->
                Spacer(Modifier.height(10.dp))
                Text(
                    error,
                    color = if (operations.download.status == OperationsDownloadStatus.FAILED) {
                        Color(0xFFFF9A9A)
                    } else {
                        colors.goldBright
                    },
                    fontSize = if (isTv) 14.sp else 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(if (isTv) 22.dp else 16.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = if (compact) 1 else 2,
            ) {
                FocusButton(
                    text = primaryLabel,
                    onClick = onPrimary,
                    enabled = operations?.download?.status != OperationsDownloadStatus.DOWNLOADING,
                    scaleOnFocus = false,
                    modifier = Modifier
                        .then(if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = 180.dp))
                        .heightIn(min = 52.dp)
                        .focusRequester(primaryRequester)
                        .focusProperties {
                            right = FocusRequester.Cancel
                            left = if (operations?.download?.status == OperationsDownloadStatus.UNKNOWN_SOURCES_BLOCKED) {
                                settingsRequester
                            } else {
                                FocusRequester.Cancel
                            }
                        },
                )
                if (operations?.download?.status == OperationsDownloadStatus.UNKNOWN_SOURCES_BLOCKED) {
                    FocusButton(
                        text = "فتح إعدادات التثبيت",
                        onClick = onOpenUnknownSourcesSettings,
                        primary = false,
                        outlined = true,
                        scaleOnFocus = false,
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
    LaunchedEffect(isTv, operations.download.status) {
        if (isTv) {
            delay(100L)
            runCatching { updateRequester.requestFocus() }
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
            isTv -> (maxWidth * .46f).coerceIn(500.dp, 740.dp)
            maxWidth >= 700.dp -> 560.dp
            else -> maxWidth
        }
        Column(
            Modifier
                .width(width)
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .background(colors.surfaceRaised, RoundedCornerShape(if (isTv) 24.dp else 18.dp))
                .border(2.dp, colors.gold.copy(alpha = .62f), RoundedCornerShape(if (isTv) 24.dp else 18.dp))
                .padding(if (isTv) 26.dp else 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = colors.goldBright, modifier = Modifier.size(if (isTv) 38.dp else 30.dp))
                Column {
                    Text("يتوفر تحديث جديد لـ HULK SA", color = colors.text, fontSize = if (isTv) 24.sp else 19.sp, fontWeight = FontWeight.Black)
                    Text("الإصدار ${operations.update.latestVersionName}", color = colors.goldBright, fontSize = if (isTv) 14.sp else 12.sp)
                }
            }
            if (operations.update.releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(13.dp))
                Text(operations.update.releaseNotes, color = colors.textMuted, fontSize = if (isTv) 15.sp else 13.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            operations.download.message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = if (operations.download.status == OperationsDownloadStatus.FAILED) Color(0xFFFF9A9A) else colors.goldBright, fontSize = if (isTv) 13.sp else 11.sp)
            }
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp), maxItemsInEachRow = if (compact) 1 else 3) {
                FocusButton(
                    text = operationsUpdateButtonLabel(operations),
                    onClick = onUpdate,
                    enabled = operations.download.status != OperationsDownloadStatus.DOWNLOADING,
                    scaleOnFocus = false,
                    modifier = Modifier.then(if (compact) Modifier.fillMaxWidth() else Modifier).heightIn(min = 50.dp).focusRequester(updateRequester),
                )
                if (operations.download.status == OperationsDownloadStatus.UNKNOWN_SOURCES_BLOCKED) {
                    FocusButton(
                        text = "إعدادات التثبيت",
                        onClick = onOpenUnknownSourcesSettings,
                        primary = false,
                        outlined = true,
                        scaleOnFocus = false,
                        modifier = Modifier.then(if (compact) Modifier.fillMaxWidth() else Modifier).heightIn(min = 50.dp).focusRequester(settingsRequester),
                    )
                }
                FocusButton(
                    text = "لاحقًا",
                    onClick = onLater,
                    primary = false,
                    outlined = true,
                    scaleOnFocus = false,
                    modifier = Modifier.then(if (compact) Modifier.fillMaxWidth() else Modifier).heightIn(min = 50.dp).focusRequester(laterRequester),
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
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .66f)).safeDrawingPadding().padding(if (isTv) 34.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val width = when {
            isTv -> (maxWidth * .44f).coerceIn(480.dp, 720.dp)
            maxWidth >= 700.dp -> 560.dp
            else -> maxWidth
        }
        Column(
            Modifier.width(width).heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                .background(colors.surfaceRaised, RoundedCornerShape(if (isTv) 24.dp else 18.dp))
                .border(2.dp, colors.gold.copy(alpha = .62f), RoundedCornerShape(if (isTv) 24.dp else 18.dp))
                .padding(if (isTv) 26.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLogo(Modifier.size(if (isTv) 68.dp else 52.dp))
            Spacer(Modifier.height(10.dp))
            Icon(icon, null, tint = colors.goldBright, modifier = Modifier.size(if (isTv) 34.dp else 28.dp))
            Text("HULK SA", color = colors.goldBright, fontSize = if (isTv) 13.sp else 11.sp, fontWeight = FontWeight.Bold)
            Text(announcement.title, color = colors.text, fontSize = if (isTv) 25.sp else 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(announcement.message, color = colors.textMuted, fontSize = if (isTv) 16.sp else 14.sp, textAlign = TextAlign.Center, maxLines = 12, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            FocusButton(
                text = "حسنًا",
                onClick = onConfirm,
                scaleOnFocus = false,
                modifier = Modifier.widthIn(min = 150.dp).heightIn(min = 50.dp).focusRequester(confirmRequester),
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
