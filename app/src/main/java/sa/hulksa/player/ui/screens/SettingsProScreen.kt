package sa.hulksa.player.ui.screens

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.data.SettingsProStore
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.ui.LocalProfileSwitchRequester
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SETTINGS_WEBSITE_URL = "https://hulksa.com/"
private const val SETTINGS_ACCOUNT_URL = "https://hulksa.com/account/login.php"
private const val SETTINGS_APPS_URL = "https://hulksa.com/hulk-app/"
private const val SETTINGS_SUPPORT_URL = "https://wa.me/966506349935"

private enum class SettingsConfirmation {
    CLEAR_HISTORY,
    RESET_PLAYBACK,
    LOGOUT,
}

private class SettingsFocusGraph {
    val refreshAccount = FocusRequester()
    val autoplayNext = FocusRequester()
    val resumePlayback = FocusRequester()
    val seekStep = FocusRequester()
    val keepScreenOn = FocusRequester()
    val autoHideControls = FocusRequester()
    val resetPlayback = FocusRequester()
    val wifiOnly = FocusRequester()
    val downloadSchedule = FocusRequester()
    val concurrentDownloads = FocusRequester()
    val manageDownloads = FocusRequester()
    val clearCache = FocusRequester()
    val switchProfile = FocusRequester()
    val refreshLibrary = FocusRequester()
    val clearHistory = FocusRequester()
    val subscribe = FocusRequester()
    val customerAccount = FocusRequester()
    val support = FocusRequester()
    val apps = FocusRequester()
    val logout = FocusRequester()
}

@Composable
internal fun SettingsProScreen(
    state: HulkUiState,
    isTv: Boolean,
    onRefreshAccount: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onToggleWifiOnly: () -> Unit,
    onToggleDownloadSchedule: () -> Unit,
    onCycleConcurrentDownloads: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val profileSwitch = LocalProfileSwitchRequester.current
    val settingsStore = remember(context) { SettingsProStore(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focus = remember { SettingsFocusGraph() }
    var playback by remember(state.account?.username) { mutableStateOf(settingsStore.playbackSettings()) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var pendingConfirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    fun restoreFocusAfterDialog(requester: FocusRequester) {
        if (!isTv) return
        scope.launch {
            delay(90L)
            runCatching { requester.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { settingsCacheBytes(context) }
    }

    LaunchedEffect(isTv) {
        scrollState.scrollTo(0)
        if (isTv) {
            yield()
            val focused = runCatching { focus.refreshAccount.requestFocus() }.getOrDefault(false)
            if (!focused) {
                delay(40L)
                runCatching { focus.refreshAccount.requestFocus() }
            }
            scrollState.scrollTo(0)
        }
    }

    val account = state.account
    val downloadedBytes = remember(state.downloads) {
        state.downloads.sumOf { download -> maxOf(download.bytesDownloaded, 0L) }
    }
    val historyAvailable = state.history.isNotEmpty()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val expandedLayout = isTv || maxWidth >= 720.dp
        val metricColumns = if (maxWidth >= 720.dp) 4 else 2
        val tvTopInset = if (isTv) {
            tvPageSafeInsets(
                screenWidthDp = maxWidth.value.toInt(),
                screenHeightDp = maxHeight.value.toInt(),
            ).verticalDp.dp
        } else {
            0.dp
        }
        val horizontalPadding = when {
            isTv -> 12.dp
            maxWidth >= 1000.dp -> 40.dp
            maxWidth >= 600.dp -> 24.dp
            else -> 14.dp
        }
        val topPadding = if (isTv) 0.dp else if (expandedLayout) 24.dp else 16.dp
        val bottomPadding = if (isTv) 24.dp else 96.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = topPadding,
                    bottom = bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(if (expandedLayout) 18.dp else 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isTv) {
                            Modifier.padding(start = 2.dp, top = tvTopInset, end = 2.dp)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                SettingsHeader(expanded = expandedLayout, isTv = isTv)
            }

            SettingsPanel(title = "بيانات الاشتراك", expanded = expandedLayout, emphasized = true) {
                SubscriptionSummary(
                    account = account,
                    expanded = expandedLayout,
                    columns = metricColumns,
                )
                SettingsDivider(
                    verticalPadding = if (expandedLayout) 18.dp else 14.dp,
                )
                SettingsMenuRow(
                    label = if (state.isAccountRefreshing) "جاري تحديث بيانات الاشتراك" else "تحديث بيانات الاشتراك",
                    value = if (state.isAccountRefreshing) "..." else "تحديث",
                    accentValue = true,
                    enabled = account != null || state.isAccountRefreshing,
                    expanded = expandedLayout,
                    focusRequester = focus.refreshAccount,
                    upRequester = FocusRequester.Cancel,
                    downRequester = focus.autoplayNext,
                    onFocused = {
                        if (isTv) {
                            scope.launch {
                                // Let Compose finish moving focus, then make the page header
                                // the authoritative top anchor instead of the focused row.
                                yield()
                                scrollState.scrollTo(0)
                            }
                        }
                    },
                    onClick = {
                        if (!state.isAccountRefreshing) onRefreshAccount()
                    },
                )
            }

            SettingsPanel(title = "التشغيل", expanded = expandedLayout) {
                SettingsMenuRow(
                    label = "تشغيل الحلقة التالية",
                    value = settingsToggleLabel(playback.autoplayNextEpisode),
                    accentValue = playback.autoplayNextEpisode,
                    expanded = expandedLayout,
                    focusRequester = focus.autoplayNext,
                    upRequester = focus.refreshAccount,
                    downRequester = focus.resumePlayback,
                    onClick = {
                        playback = settingsStore.setAutoplayNextEpisode(!playback.autoplayNextEpisode)
                        toast("تم تحديث تشغيل الحلقة التالية")
                    },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "استكمال المشاهدة",
                    value = settingsToggleLabel(playback.resumePlayback),
                    accentValue = playback.resumePlayback,
                    expanded = expandedLayout,
                    focusRequester = focus.resumePlayback,
                    upRequester = focus.autoplayNext,
                    downRequester = focus.seekStep,
                    onClick = {
                        playback = settingsStore.setResumePlayback(!playback.resumePlayback)
                        toast("تم تحديث استكمال المشاهدة")
                    },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "التقديم والترجيع",
                    value = "${playback.seekStepSeconds} ث",
                    expanded = expandedLayout,
                    focusRequester = focus.seekStep,
                    upRequester = focus.resumePlayback,
                    downRequester = focus.keepScreenOn,
                    onClick = {
                        playback = settingsStore.cycleSeekStep()
                        toast("التقديم والترجيع ${playback.seekStepSeconds} ثانية")
                    },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "إبقاء الشاشة أثناء التشغيل",
                    value = settingsToggleLabel(playback.keepScreenOn),
                    accentValue = playback.keepScreenOn,
                    expanded = expandedLayout,
                    focusRequester = focus.keepScreenOn,
                    upRequester = focus.seekStep,
                    downRequester = focus.autoHideControls,
                    onClick = {
                        playback = settingsStore.setKeepScreenOn(!playback.keepScreenOn)
                    },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "إخفاء أدوات التحكم تلقائيًا",
                    value = settingsToggleLabel(playback.autoHideControls),
                    accentValue = playback.autoHideControls,
                    expanded = expandedLayout,
                    focusRequester = focus.autoHideControls,
                    upRequester = focus.keepScreenOn,
                    downRequester = focus.resetPlayback,
                    onClick = {
                        playback = settingsStore.setAutoHideControls(!playback.autoHideControls)
                    },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "إعادة ضبط إعدادات التشغيل",
                    value = "",
                    expanded = expandedLayout,
                    focusRequester = focus.resetPlayback,
                    upRequester = focus.autoHideControls,
                    downRequester = focus.wifiOnly,
                    onClick = { pendingConfirmation = SettingsConfirmation.RESET_PLAYBACK },
                )
            }

            SettingsPanel(title = "التنزيلات والتخزين", expanded = expandedLayout) {
                StorageSummary(
                    downloadedBytes = downloadedBytes,
                    cacheBytes = cacheBytes,
                    expanded = expandedLayout,
                )
                SettingsDivider(
                    verticalPadding = if (expandedLayout) 18.dp else 14.dp,
                )
                SettingsMenuRow(
                    label = "التنزيل عبر Wi-Fi فقط",
                    value = settingsToggleLabel(state.downloadSettings.wifiOnly),
                    accentValue = state.downloadSettings.wifiOnly,
                    expanded = expandedLayout,
                    focusRequester = focus.wifiOnly,
                    upRequester = focus.resetPlayback,
                    downRequester = focus.downloadSchedule,
                    onClick = onToggleWifiOnly,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "جدولة التنزيل",
                    value = if (state.downloadSettings.scheduleMode == DownloadScheduleMode.NIGHT) "2 ليلًا" else "الآن",
                    expanded = expandedLayout,
                    focusRequester = focus.downloadSchedule,
                    upRequester = focus.wifiOnly,
                    downRequester = focus.concurrentDownloads,
                    onClick = onToggleDownloadSchedule,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "التنزيلات المتزامنة",
                    value = state.downloadSettings.concurrentDownloads.toString(),
                    expanded = expandedLayout,
                    focusRequester = focus.concurrentDownloads,
                    upRequester = focus.downloadSchedule,
                    downRequester = focus.manageDownloads,
                    onClick = onCycleConcurrentDownloads,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "إدارة التنزيلات",
                    value = "",
                    accentValue = true,
                    expanded = expandedLayout,
                    focusRequester = focus.manageDownloads,
                    upRequester = focus.concurrentDownloads,
                    downRequester = focus.clearCache,
                    onClick = onOpenDownloads,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "مسح الكاش",
                    value = settingsFormatBytes(cacheBytes),
                    expanded = expandedLayout,
                    focusRequester = focus.clearCache,
                    upRequester = focus.manageDownloads,
                    downRequester = focus.switchProfile,
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { clearSettingsCache(context) }
                            cacheBytes = withContext(Dispatchers.IO) { settingsCacheBytes(context) }
                            toast("تم مسح الكاش")
                        }
                    },
                )
            }

            SettingsPanel(title = "الحساب", expanded = expandedLayout) {
                SettingsMenuRow(
                    label = "تغيير المستخدم",
                    value = "",
                    accentValue = true,
                    expanded = expandedLayout,
                    focusRequester = focus.switchProfile,
                    upRequester = focus.clearCache,
                    downRequester = focus.refreshLibrary,
                    onClick = profileSwitch,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "تحديث المكتبة",
                    value = "",
                    expanded = expandedLayout,
                    focusRequester = focus.refreshLibrary,
                    upRequester = focus.switchProfile,
                    downRequester = if (historyAvailable) focus.clearHistory else focus.subscribe,
                    onClick = onRefreshLibrary,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "مسح سجل المشاهدة",
                    value = if (state.history.isEmpty()) "فارغ" else "",
                    enabled = historyAvailable,
                    expanded = expandedLayout,
                    focusRequester = focus.clearHistory,
                    upRequester = focus.refreshLibrary,
                    downRequester = focus.subscribe,
                    onClick = { pendingConfirmation = SettingsConfirmation.CLEAR_HISTORY },
                )
            }

            SettingsPanel(title = "الخدمات", expanded = expandedLayout) {
                SettingsMenuRow(
                    label = "الاشتراك أو التجديد",
                    value = "",
                    accentValue = true,
                    expanded = expandedLayout,
                    focusRequester = focus.subscribe,
                    upRequester = if (historyAvailable) focus.clearHistory else focus.refreshLibrary,
                    downRequester = focus.customerAccount,
                    onClick = { open(SETTINGS_WEBSITE_URL) },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "حساب العميل",
                    value = "",
                    expanded = expandedLayout,
                    focusRequester = focus.customerAccount,
                    upRequester = focus.subscribe,
                    downRequester = focus.support,
                    onClick = { open(SETTINGS_ACCOUNT_URL) },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "الدعم الفني",
                    value = "",
                    expanded = expandedLayout,
                    focusRequester = focus.support,
                    upRequester = focus.customerAccount,
                    downRequester = focus.apps,
                    onClick = { open(SETTINGS_SUPPORT_URL) },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "مركز التطبيقات",
                    value = "",
                    expanded = expandedLayout,
                    focusRequester = focus.apps,
                    upRequester = focus.support,
                    downRequester = focus.logout,
                    onClick = { open(SETTINGS_APPS_URL) },
                )
            }

            SettingsPanel(title = "حول التطبيق", expanded = expandedLayout) {
                SettingsInfoRow("HULK SA", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", expandedLayout)
                SettingsDivider()
                SettingsInfoRow("Android", "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}", expandedLayout)
                SettingsDivider()
                SettingsInfoRow(
                    "الجهاز",
                    "${Build.MANUFACTURER.orEmpty().trim()} ${Build.MODEL.orEmpty().trim()}".trim().ifBlank { "Android" },
                    expandedLayout,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "تسجيل الخروج",
                    value = "",
                    expanded = expandedLayout,
                    focusRequester = focus.logout,
                    upRequester = focus.apps,
                    downRequester = FocusRequester.Cancel,
                    onClick = { pendingConfirmation = SettingsConfirmation.LOGOUT },
                )
            }
        }

        pendingConfirmation?.let { confirmation ->
            val (title, message, confirmLabel) = when (confirmation) {
                SettingsConfirmation.CLEAR_HISTORY -> Triple(
                    "مسح سجل المشاهدة؟",
                    "سيتم حذف سجل المشاهدة لهذا المستخدم، " +
                        "ولا يمكن التراجع عن هذا الإجراء.",
                    "مسح السجل",
                )

                SettingsConfirmation.RESET_PLAYBACK -> Triple(
                    "إعادة ضبط إعدادات التشغيل؟",
                    "ستعود إعدادات التشغيل إلى قيمها الافتراضية دون التأثير على الحساب " +
                        "أو التنزيلات أو سجل المشاهدة.",
                    "إعادة الضبط",
                )

                SettingsConfirmation.LOGOUT -> Triple(
                    "تسجيل الخروج؟",
                    "سيتم إنهاء جلسة هذا المستخدم وستحتاج إلى تسجيل الدخول مرة أخرى.",
                    "تسجيل الخروج",
                )
            }

            SettingsConfirmationDialog(
                title = title,
                message = message,
                confirmLabel = confirmLabel,
                isTv = isTv,
                expanded = expandedLayout,
                onDismiss = {
                    pendingConfirmation = null
                    val requester = when (confirmation) {
                        SettingsConfirmation.CLEAR_HISTORY -> focus.clearHistory
                        SettingsConfirmation.RESET_PLAYBACK -> focus.resetPlayback
                        SettingsConfirmation.LOGOUT -> focus.logout
                    }
                    restoreFocusAfterDialog(requester)
                },
                onConfirm = {
                    pendingConfirmation = null
                    when (confirmation) {
                        SettingsConfirmation.CLEAR_HISTORY -> {
                            onClearHistory()
                            restoreFocusAfterDialog(focus.refreshLibrary)
                        }

                        SettingsConfirmation.RESET_PLAYBACK -> {
                            playback = settingsStore.resetPlaybackSettings()
                            toast("تمت إعادة ضبط إعدادات التشغيل")
                            restoreFocusAfterDialog(focus.resetPlayback)
                        }

                        SettingsConfirmation.LOGOUT -> onLogout()
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isTv: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val cancelRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            delay(90L)
            runCatching { cancelRequester.requestFocus() }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(if (expanded) 48.dp else 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(if (expanded) 24.dp else 19.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = if (expanded) 620.dp else 460.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xFF13140F))
                    .border(1.dp, colors.gold.copy(alpha = .38f), shape)
                    .padding(
                        horizontal = if (expanded) 28.dp else 20.dp,
                        vertical = if (expanded) 26.dp else 20.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(if (expanded) 18.dp else 14.dp),
            ) {
                Text(
                    text = title,
                    color = colors.text,
                    fontSize = if (expanded) 23.sp else 19.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = message,
                    color = colors.textMuted,
                    fontSize = if (expanded) 15.sp else 14.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (expanded) 12.dp else 9.dp),
                ) {
                    SettingsDialogAction(
                        label = confirmLabel,
                        primary = true,
                        expanded = expanded,
                        modifier = Modifier.weight(1f),
                        onClick = onConfirm,
                    )
                    SettingsDialogAction(
                        label = "إلغاء",
                        primary = false,
                        expanded = expanded,
                        modifier = Modifier.weight(1f),
                        focusRequester = cancelRequester,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDialogAction(
    label: String,
    primary: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (expanded) 13.dp else 11.dp)

    Box(
        modifier = modifier
            .heightIn(min = if (expanded) 56.dp else 50.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
            .clip(shape)
            .background(
                when {
                    focused -> colors.gold.copy(alpha = .18f)
                    primary -> colors.gold.copy(alpha = .10f)
                    else -> Color.White.copy(alpha = .04f)
                },
            )
            .border(
                width = 1.dp,
                color = when {
                    focused -> colors.goldBright.copy(alpha = .90f)
                    primary -> colors.gold.copy(alpha = .28f)
                    else -> Color.White.copy(alpha = .07f)
                },
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (expanded) 16.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary || focused) colors.goldBright else colors.text,
            fontSize = if (expanded) 15.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsHeader(expanded: Boolean, isTv: Boolean) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isTv) 0.dp else if (expanded) 5.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 11.dp else if (expanded) 14.dp else 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (isTv) 42.dp else if (expanded) 54.dp else 44.dp)
                .clip(CircleShape)
                .background(Color(0xFF171812))
                .border(1.dp, colors.gold.copy(alpha = .34f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = colors.goldBright,
                modifier = Modifier.size(if (isTv) 21.dp else if (expanded) 28.dp else 23.dp),
            )
        }
        Text(
            text = "الاعدادات",
            color = colors.text,
            fontSize = if (isTv) 27.sp else if (expanded) 30.sp else 24.sp,
            fontWeight = if (isTv) FontWeight.Bold else FontWeight.Black,
        )
    }
}

@Composable
private fun SettingsPanel(
    title: String,
    expanded: Boolean,
    emphasized: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(if (expanded) 22.dp else 17.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (emphasized) Color(0xFF14150F) else Color(0xFF10110E))
            .border(
                width = 1.dp,
                color = colors.gold.copy(alpha = if (emphasized) .34f else .11f),
                shape = shape,
            )
            .padding(
                horizontal = if (expanded) 24.dp else 16.dp,
                vertical = if (expanded) 20.dp else 15.dp,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (expanded) 11.dp else 9.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (expanded) 25.dp else 20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.goldBright.copy(alpha = if (emphasized) .95f else .65f)),
            )
            Text(
                text = title,
                color = colors.text,
                fontSize = if (expanded) 22.sp else 18.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.height(if (expanded) 16.dp else 12.dp))
        content()
    }
}

@Composable
private fun SubscriptionSummary(
    account: AccountInfo?,
    expanded: Boolean,
    columns: Int,
) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (expanded) 18.dp else 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "الحساب",
                color = colors.textMuted,
                fontSize = if (expanded) 12.sp else 10.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = account?.username?.let(::maskedSettingsUsername) ?: "—",
                color = colors.text,
                fontSize = if (expanded) 18.sp else 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "الحالة",
                color = colors.textMuted,
                fontSize = if (expanded) 12.sp else 10.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = settingsSubscriptionStatus(account),
                color = colors.goldBright,
                fontSize = if (expanded) 14.sp else 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.gold.copy(alpha = .10f))
                    .border(1.dp, colors.gold.copy(alpha = .24f), RoundedCornerShape(50))
                    .padding(horizontal = if (expanded) 12.dp else 10.dp, vertical = 5.dp),
            )
        }
    }
    Spacer(Modifier.height(if (expanded) 18.dp else 14.dp))
    val metrics = listOf(
        "تاريخ الانتهاء" to settingsExpiryDate(account),
        "المتبقي" to settingsRemaining(account),
        "الاتصالات" to settingsConnectionUsage(account),
        "نوع الاشتراك" to when (account?.isTrial) {
            true -> "تجريبي"
            false -> "عادي"
            null -> "—"
        },
    )
    val safeColumns = columns.coerceIn(1, metrics.size)
    Column(verticalArrangement = Arrangement.spacedBy(if (expanded) 10.dp else 8.dp)) {
        metrics.chunked(safeColumns).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (expanded) 12.dp else 8.dp),
            ) {
                rowMetrics.forEach { (label, value) ->
                    SettingsMetric(label, value, expanded, Modifier.weight(1f))
                }
                repeat(safeColumns - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StorageSummary(downloadedBytes: Long, cacheBytes: Long, expanded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (expanded) 12.dp else 8.dp),
    ) {
        SettingsMetric("التنزيلات", settingsFormatBytes(downloadedBytes), expanded, Modifier.weight(1f))
        SettingsMetric("الكاش", settingsFormatBytes(cacheBytes), expanded, Modifier.weight(1f))
    }
}

@Composable
private fun SettingsMetric(label: String, value: String, expanded: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier
            .heightIn(min = if (expanded) 76.dp else 64.dp)
            .clip(RoundedCornerShape(if (expanded) 14.dp else 11.dp))
            .background(Color(0xFF181914))
            .padding(
                horizontal = if (expanded) 15.dp else 11.dp,
                vertical = if (expanded) 13.dp else 10.dp,
            ),
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = if (expanded) 12.sp else 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (expanded) 7.dp else 5.dp))
        Text(
            text = value,
            color = colors.text,
            fontSize = if (expanded) 16.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsMenuRow(
    label: String,
    value: String,
    accentValue: Boolean = false,
    enabled: Boolean = true,
    expanded: Boolean,
    focusRequester: FocusRequester? = null,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (expanded) 13.dp else 11.dp)
    val focusModifier = Modifier
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .focusProperties {
            if (upRequester != null) up = upRequester
            if (downRequester != null) down = downRequester
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (expanded) 62.dp else 54.dp)
            .then(focusModifier)
            .graphicsLayer { alpha = if (enabled) 1f else .38f }
            .clip(shape)
            .background(if (focused) colors.gold.copy(alpha = .13f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (focused) colors.goldBright.copy(alpha = .86f) else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (nowFocused && !focused) onFocused?.invoke()
                focused = nowFocused
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .focusable(enabled = enabled)
            .padding(
                horizontal = if (expanded) 16.dp else 12.dp,
                vertical = if (expanded) 14.dp else 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 12.dp),
    ) {
        Text(
            text = label,
            color = colors.text,
            fontSize = if (expanded) 16.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = if (expanded) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (value.isNotBlank()) {
            Text(
                text = value,
                color = if (accentValue) colors.goldBright else colors.textMuted,
                fontSize = if (expanded) 13.sp else 12.sp,
                fontWeight = if (accentValue) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (focused) colors.gold.copy(alpha = .10f) else Color(0xFF1A1C16),
                    )
                    .padding(
                        horizontal = if (expanded) 11.dp else 9.dp,
                        vertical = if (expanded) 6.dp else 5.dp,
                    ),
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String, expanded: Boolean) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (expanded) 16.dp else 12.dp,
                vertical = if (expanded) 14.dp else 11.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 12.dp),
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = if (expanded) 13.sp else 11.sp,
            modifier = Modifier.weight(.30f),
        )
        Text(
            text = value,
            color = colors.text,
            fontSize = if (expanded) 15.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(.70f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsDivider(
    verticalPadding: Dp = 0.dp,
    horizontalInset: Dp = 12.dp,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalInset, vertical = verticalPadding)
            .height(1.dp)
            .background(colors.textMuted.copy(alpha = .09f)),
    )
}

private fun settingsToggleLabel(enabled: Boolean): String = if (enabled) "مفعل" else "متوقف"

private fun maskedSettingsUsername(username: String): String {
    val clean = username.trim()
    if (clean.isBlank()) return "—"
    val visible = clean.takeLast(minOf(4, clean.length))
    return "\u200E••••$visible\u200E"
}

private fun settingsSubscriptionStatus(account: AccountInfo?): String {
    account ?: return "—"
    val expiry = account.expiresAtEpochSeconds
    if (expiry != null && expiry > 0L && expiry <= System.currentTimeMillis() / 1000L) return "منتهي"
    return when (account.status.trim().lowercase(Locale.ROOT)) {
        "active", "enabled" -> "نشط"
        "expired" -> "منتهي"
        "disabled", "banned", "blocked" -> "موقوف"
        else -> account.status.trim().ifBlank { "غير معروف" }
    }
}

private fun settingsExpiryDate(account: AccountInfo?): String {
    val epoch = account?.expiresAtEpochSeconds?.takeIf { it > 0L } ?: return "غير محدد"
    val formatted = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(epoch * 1000L))
    return "\u200E$formatted\u200E"
}

private fun settingsRemaining(account: AccountInfo?): String {
    val epoch = account?.expiresAtEpochSeconds?.takeIf { it > 0L } ?: return "غير محدد"
    val remainingSeconds = epoch - System.currentTimeMillis() / 1000L
    if (remainingSeconds <= 0L) return "منتهي"
    val days = (remainingSeconds + 86_399L) / 86_400L
    return if (days == 1L) "يوم واحد" else "$days يوم"
}

private fun settingsConnectionUsage(account: AccountInfo?): String {
    account ?: return "—"
    val maxConnections = account.maxConnections
    if (maxConnections <= 0) return "—"
    val currentConnections = account.activeConnections
        .coerceAtLeast(1)
        .coerceAtMost(maxConnections)
    return "$currentConnections / $maxConnections"
}

private fun settingsFormatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    if (safe < 1024L) return "$safe B"
    val kb = safe / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private fun settingsCacheDirectories(context: Context): List<File> = listOfNotNull(
    context.cacheDir,
    context.externalCacheDir,
).distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }

private fun settingsCacheBytes(context: Context): Long = settingsCacheDirectories(context).sumOf { root ->
    runCatching {
        root.walkTopDown().filter(File::isFile).sumOf(File::length)
    }.getOrDefault(0L)
}

private fun clearSettingsCache(context: Context) {
    settingsCacheDirectories(context).forEach { root ->
        root.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
    }
}
