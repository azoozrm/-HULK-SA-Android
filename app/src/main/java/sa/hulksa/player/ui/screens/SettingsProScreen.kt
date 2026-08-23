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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SupportAgent
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import sa.hulksa.player.data.GrowthDestination
import sa.hulksa.player.data.OperationsApkInstaller
import sa.hulksa.player.data.OperationsDownloadStatus
import sa.hulksa.player.data.OperationsInstallResult
import sa.hulksa.player.data.SettingsProStore
import sa.hulksa.player.data.isGrowthLinkUsable
import sa.hulksa.player.data.isInstallableOperationsUpdate
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.ui.LocalProfileSwitchRequester
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val episodeNotifications = FocusRequester()
    val wifiOnly = FocusRequester()
    val downloadSchedule = FocusRequester()
    val concurrentDownloads = FocusRequester()
    val manageDownloads = FocusRequester()
    val clearCache = FocusRequester()
    val switchProfile = FocusRequester()
    val refreshLibrary = FocusRequester()
    val clearHistory = FocusRequester()
    val subscribe = FocusRequester()
    val support = FocusRequester()
    val updateApp = FocusRequester()
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
    downloadsEnabled: Boolean,
    onToggleWifiOnly: () -> Unit,
    onToggleDownloadSchedule: () -> Unit,
    onCycleConcurrentDownloads: () -> Unit,
    notificationMasterEnabled: Boolean,
    episodeNotificationsAvailable: Boolean,
    onToggleEpisodeNotificationMaster: () -> Unit,
    onGrowthAction: (GrowthDestination) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val profileSwitch = LocalProfileSwitchRequester.current
    val settingsStore = remember(context) { SettingsProStore(context) }
    val updateInstaller = remember(context) { OperationsApkInstaller(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focus = remember { SettingsFocusGraph() }
    var playback by remember(state.account?.username) { mutableStateOf(settingsStore.playbackSettings()) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var pendingConfirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }
    var isManualUpdateRunning by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun restoreFocusAfterDialog(requester: FocusRequester) {
        if (!isTv) return
        scope.launch {
            delay(90L)
            runCatching { requester.requestFocus() }
        }
    }

    fun updateApp() {
        if (
            isManualUpdateRunning ||
            state.operations.download.status == OperationsDownloadStatus.DOWNLOADING
        ) return

        val update = state.operations.update
        when {
            update.latestVersionCode <= BuildConfig.VERSION_CODE -> {
                toast("لديك أحدث إصدار")
            }

            !isInstallableOperationsUpdate(update) -> {
                toast("التحديث غير متاح للتثبيت حاليا")
            }

            else -> {
                isManualUpdateRunning = true
                scope.launch {
                    try {
                        when (val result = updateInstaller.downloadAndOpen(update) { }) {
                            OperationsInstallResult.InstallerOpened -> Unit
                            OperationsInstallResult.UnknownSourcesBlocked -> {
                                val opened = updateInstaller.openUnknownSourcesSettings()
                                toast(
                                    if (opened) {
                                        "فعل تثبيت التطبيقات ثم ارجع واضغط تحديث التطبيق"
                                    } else {
                                        "فعل تثبيت التطبيقات من مصادر غير معروفة ثم حاول مرة أخرى"
                                    },
                                )
                            }

                            is OperationsInstallResult.Failure -> toast(result.message)
                        }
                    } finally {
                        isManualUpdateRunning = false
                    }
                }
            }
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
    val growth = state.operations.growth
    val renewalAvailable = growth.enabled && isGrowthLinkUsable(
        GrowthDestination.RENEWAL,
        growth.renewal,
    )
    val supportAvailable = growth.enabled && isGrowthLinkUsable(
        GrowthDestination.SUPPORT,
        growth.support,
    )
    val firstGrowthRequester = when {
        renewalAvailable -> focus.subscribe
        supportAvailable -> focus.support
        else -> focus.updateApp
    }
    val lastGrowthRequester = when {
        supportAvailable -> focus.support
        renewalAvailable -> focus.subscribe
        historyAvailable -> focus.clearHistory
        else -> focus.refreshLibrary
    }
    val updateBusy =
        isManualUpdateRunning || state.operations.download.status == OperationsDownloadStatus.DOWNLOADING

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
        val topPadding = if (isTv) 0.dp else if (expandedLayout) 24.dp else 0.dp
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
                    downRequester = if (episodeNotificationsAvailable) {
                        focus.episodeNotifications
                    } else {
                        focus.wifiOnly
                    },
                    onClick = { pendingConfirmation = SettingsConfirmation.RESET_PLAYBACK },
                )
            }

            SettingsPanel(title = "الإشعارات", expanded = expandedLayout) {
                SettingsMenuRow(
                    label = "تنبيهات الحلقات الجديدة",
                    value = if (episodeNotificationsAvailable) {
                        settingsToggleLabel(notificationMasterEnabled)
                    } else {
                        "متوقفة مؤقتًا"
                    },
                    accentValue = notificationMasterEnabled,
                    enabled = episodeNotificationsAvailable,
                    expanded = expandedLayout,
                    focusRequester = focus.episodeNotifications,
                    upRequester = focus.resetPlayback,
                    downRequester = focus.wifiOnly,
                    onClick = onToggleEpisodeNotificationMaster,
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
                    upRequester = if (episodeNotificationsAvailable) {
                        focus.episodeNotifications
                    } else {
                        focus.resetPlayback
                    },
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
                    downRequester = if (downloadsEnabled) focus.manageDownloads else focus.clearCache,
                    onClick = onCycleConcurrentDownloads,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "إدارة التنزيلات",
                    value = if (downloadsEnabled) "" else "متوقفة مؤقتًا",
                    accentValue = downloadsEnabled,
                    enabled = downloadsEnabled,
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
                    upRequester = if (downloadsEnabled) focus.manageDownloads else focus.concurrentDownloads,
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
                    downRequester = if (historyAvailable) focus.clearHistory else firstGrowthRequester,
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
                    downRequester = firstGrowthRequester,
                    onClick = { pendingConfirmation = SettingsConfirmation.CLEAR_HISTORY },
                )
            }

            if (renewalAvailable || supportAvailable) {
                SettingsPanel(title = "خدمات HULK SA", expanded = expandedLayout) {
                    if (renewalAvailable) {
                        SettingsMenuRow(
                            label = "الموقع الالكتروني",
                            subtitle = "جدد اشتراكك من موقع HULK SA",
                            value = if (isTv) "اضغط OK" else "",
                            accentValue = true,
                            expanded = expandedLayout,
                            leadingIcon = Icons.Rounded.Language,
                            focusRequester = focus.subscribe,
                            upRequester = if (historyAvailable) focus.clearHistory else focus.refreshLibrary,
                            downRequester = if (supportAvailable) focus.support else focus.updateApp,
                            onClick = { onGrowthAction(GrowthDestination.RENEWAL) },
                        )
                    }
                    if (renewalAvailable && supportAvailable) SettingsDivider()
                    if (supportAvailable) {
                        SettingsMenuRow(
                            label = "الدعم الفني",
                            subtitle = "تواصل معنا عبر واتساب",
                            value = if (isTv) "اضغط OK" else "",
                            accentValue = true,
                            expanded = expandedLayout,
                            leadingIcon = Icons.Rounded.SupportAgent,
                            focusRequester = focus.support,
                            upRequester = if (renewalAvailable) {
                                focus.subscribe
                            } else if (historyAvailable) {
                                focus.clearHistory
                            } else {
                                focus.refreshLibrary
                            },
                            downRequester = focus.updateApp,
                            onClick = { onGrowthAction(GrowthDestination.SUPPORT) },
                        )
                    }
                }
            }

            SettingsPanel(title = "حول التطبيق", expanded = expandedLayout) {
                SettingsInfoRow("اصدار HULK SA", BuildConfig.VERSION_NAME, expandedLayout)
                SettingsDivider()
                SettingsInfoRow(
                    "اصدار Android",
                    Build.VERSION.RELEASE.orEmpty().trim().ifBlank { "Android" },
                    expandedLayout,
                )
                SettingsDivider()
                SettingsInfoRow(
                    "نوع الجهاز",
                    "${Build.MANUFACTURER.orEmpty().trim()} ${Build.MODEL.orEmpty().trim()}".trim().ifBlank { "Android" },
                    expandedLayout,
                )
                SettingsDivider()
                SettingsInfoRow("مطور التطبيق", "Mega Store", expandedLayout)
            }

            SettingsAboutActions(
                expanded = expandedLayout,
                updateBusy = updateBusy,
                updateRequester = focus.updateApp,
                logoutRequester = focus.logout,
                upRequester = lastGrowthRequester,
                onUpdate = ::updateApp,
                onLogout = { pendingConfirmation = SettingsConfirmation.LOGOUT },
            )
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
private fun SettingsAboutActions(
    expanded: Boolean,
    updateBusy: Boolean,
    updateRequester: FocusRequester,
    logoutRequester: FocusRequester,
    upRequester: FocusRequester,
    onUpdate: () -> Unit,
    onLogout: () -> Unit,
) {
    if (expanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsStandaloneAction(
                label = if (updateBusy) "جاري تنزيل التحديث" else "تحديث التطبيق",
                primary = true,
                busy = updateBusy,
                expanded = true,
                modifier = Modifier.weight(1f),
                focusRequester = updateRequester,
                upRequester = upRequester,
                downRequester = FocusRequester.Cancel,
                leftRequester = logoutRequester,
                onClick = onUpdate,
            )
            SettingsStandaloneAction(
                label = "تسجيل الخروج",
                primary = false,
                expanded = true,
                modifier = Modifier.weight(1f),
                focusRequester = logoutRequester,
                upRequester = upRequester,
                downRequester = FocusRequester.Cancel,
                rightRequester = updateRequester,
                onClick = onLogout,
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            SettingsStandaloneAction(
                label = if (updateBusy) "جاري تنزيل التحديث" else "تحديث التطبيق",
                primary = true,
                busy = updateBusy,
                expanded = false,
                modifier = Modifier.fillMaxWidth(),
                focusRequester = updateRequester,
                upRequester = upRequester,
                downRequester = logoutRequester,
                onClick = onUpdate,
            )
            SettingsStandaloneAction(
                label = "تسجيل الخروج",
                primary = false,
                expanded = false,
                modifier = Modifier.fillMaxWidth(),
                focusRequester = logoutRequester,
                upRequester = updateRequester,
                downRequester = FocusRequester.Cancel,
                onClick = onLogout,
            )
        }
    }
}

@Composable
private fun SettingsStandaloneAction(
    label: String,
    primary: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    focusRequester: FocusRequester? = null,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    leftRequester: FocusRequester? = null,
    rightRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (expanded) 14.dp else 12.dp)

    Box(
        modifier = modifier
            .heightIn(min = if (expanded) 60.dp else 54.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (upRequester != null) up = upRequester
                if (downRequester != null) down = downRequester
                if (leftRequester != null) left = leftRequester
                if (rightRequester != null) right = rightRequester
            }
            .graphicsLayer { alpha = if (busy) .72f else 1f }
            .clip(shape)
            .background(
                when {
                    focused -> colors.gold.copy(alpha = .20f)
                    primary -> colors.gold.copy(alpha = .11f)
                    else -> Color(0xFF10110E)
                },
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> colors.goldBright.copy(alpha = .92f)
                    primary -> colors.gold.copy(alpha = .30f)
                    else -> colors.gold.copy(alpha = .13f)
                },
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .focusable()
            .padding(horizontal = if (expanded) 18.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary || focused) colors.goldBright else colors.text,
            fontSize = if (expanded) 16.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    if (!isTv && !expanded) {
        Text(
            text = "الاعدادات",
            color = colors.text,
            fontSize = MOBILE_SECTION_TITLE_SIZE,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isTv) 0.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 11.dp else 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (isTv) 42.dp else 54.dp)
                .clip(CircleShape)
                .background(Color(0xFF171812))
                .border(1.dp, colors.gold.copy(alpha = .34f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = colors.goldBright,
                modifier = Modifier.size(if (isTv) 21.dp else 28.dp),
            )
        }
        Text(
            text = "الاعدادات",
            color = colors.text,
            fontSize = if (isTv) 27.sp else 30.sp,
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
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
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
        leadingIcon?.let { icon ->
            Box(
                modifier = Modifier
                    .size(if (expanded) 39.dp else 35.dp)
                    .clip(CircleShape)
                    .background(colors.gold.copy(alpha = if (focused) .18f else .09f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.goldBright,
                    modifier = Modifier.size(if (expanded) 21.dp else 19.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.text,
                fontSize = if (expanded) 16.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf(String::isNotBlank)?.let { supportingText ->
                Spacer(Modifier.height(3.dp))
                Text(
                    text = supportingText,
                    color = colors.textMuted,
                    fontSize = if (expanded) 12.sp else 11.sp,
                    maxLines = if (expanded) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
