package sa.hulksa.player.ui.screens

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import sa.hulksa.player.ui.components.EntrySwitchVisual
import sa.hulksa.player.ui.components.goldFocusEdge
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

/** The TV section list / phone list-page entries; each owns its existing detail controls. */
private enum class SettingsSection(val title: String, val icon: ImageVector) {
    SUBSCRIPTION("بيانات الاشتراك", Icons.Rounded.CreditCard),
    PLAYBACK("التشغيل", Icons.Rounded.PlayCircle),
    NOTIFICATIONS("الاشعارات", Icons.Rounded.Notifications),
    DOWNLOADS("التنزيلات والتخزين", Icons.Rounded.Download),
    ACCOUNT("الحساب", Icons.Rounded.Person),
    SERVICES("خدمات HULK", Icons.Rounded.GridView),
    ABOUT("حول التطبيق", Icons.Rounded.Info),
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
    val logout = FocusRequester()
    val subscribe = FocusRequester()
    val support = FocusRequester()
    val updateApp = FocusRequester()
    val subscriptionSection = FocusRequester()
    val playbackSection = FocusRequester()
    val notificationsSection = FocusRequester()
    val downloadsSection = FocusRequester()
    val accountSection = FocusRequester()
    val servicesSection = FocusRequester()
    val aboutSection = FocusRequester()

    fun sectionRequester(section: SettingsSection): FocusRequester = when (section) {
        SettingsSection.SUBSCRIPTION -> subscriptionSection
        SettingsSection.PLAYBACK -> playbackSection
        SettingsSection.NOTIFICATIONS -> notificationsSection
        SettingsSection.DOWNLOADS -> downloadsSection
        SettingsSection.ACCOUNT -> accountSection
        SettingsSection.SERVICES -> servicesSection
        SettingsSection.ABOUT -> aboutSection
    }
}

private fun settingsSectionFirstRequester(
    section: SettingsSection,
    focus: SettingsFocusGraph,
    renewalAvailable: Boolean,
    supportAvailable: Boolean,
): FocusRequester = when (section) {
    SettingsSection.SUBSCRIPTION -> focus.refreshAccount
    SettingsSection.PLAYBACK -> focus.autoplayNext
    SettingsSection.NOTIFICATIONS -> focus.episodeNotifications
    SettingsSection.DOWNLOADS -> focus.wifiOnly
    SettingsSection.ACCOUNT -> focus.switchProfile
    SettingsSection.SERVICES -> when {
        renewalAvailable -> focus.subscribe
        supportAvailable -> focus.support
        else -> focus.updateApp
    }
    SettingsSection.ABOUT -> focus.updateApp
}

/**
 * Settings adopts the selected TV section-list/detail composition (list physical RIGHT beside the
 * rail, one authoritative detail pane LEFT) and the phone list-then-detail page with Back
 * restoration. Selection is separate from focus; every existing control, value, callback and
 * confirmation owner is preserved.
 */
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
    onNotice: (String, ImageVector) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalHulkColors.current
    val profileSwitch = LocalProfileSwitchRequester.current
    val settingsStore = remember(context) { SettingsProStore(context) }
    val updateInstaller = remember(context) { OperationsApkInstaller(context) }
    val scope = rememberCoroutineScope()
    val focus = remember { SettingsFocusGraph() }
    var playback by remember(state.account?.username) { mutableStateOf(settingsStore.playbackSettings()) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var pendingConfirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }
    var isManualUpdateRunning by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf(SettingsSection.SUBSCRIPTION) }
    var phoneDetailOpen by remember { mutableStateOf(false) }
    var pendingSectionFocus by remember { mutableStateOf<SettingsSection?>(null) }
    var detailOwnsFocus by remember { mutableStateOf(false) }
    val sectionScrollStates = remember {
        SettingsSection.entries.associateWith { ScrollState(initial = 0) }
    }

    fun restoreFocusAfterDialog(requester: FocusRequester) {
        if (!isTv) return
        scope.launch {
            withFrameNanos { }
            withFrameNanos { }
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
                onNotice("لديك احدث اصدار", Icons.Rounded.Check)
            }

            !isInstallableOperationsUpdate(update) -> {
                onNotice("التحديث غير متاح للتثبيت حاليا", Icons.Outlined.ErrorOutline)
            }

            else -> {
                isManualUpdateRunning = true
                scope.launch {
                    try {
                        when (val result = updateInstaller.downloadAndOpen(update) { }) {
                            OperationsInstallResult.InstallerOpened -> Unit
                            OperationsInstallResult.UnknownSourcesBlocked -> {
                                val opened = updateInstaller.openUnknownSourcesSettings()
                                onNotice(
                                    if (opened) {
                                        "فعل تثبيت التطبيقات ثم ارجع واضغط تحديث التطبيق"
                                    } else {
                                        "فعل تثبيت التطبيقات من مصادر غير معروفة ثم حاول مرة اخرى"
                                    },
                                    if (opened) Icons.Rounded.Settings else Icons.Outlined.ErrorOutline,
                                )
                            }

                            is OperationsInstallResult.Failure -> onNotice(
                                scopedNoticeDisplayText(result.message),
                                Icons.Outlined.ErrorOutline,
                            )
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
    val sections = remember(renewalAvailable, supportAvailable) {
        SettingsSection.entries.filter { section ->
            section != SettingsSection.SERVICES || renewalAvailable || supportAvailable
        }
    }
    val updateBusy =
        isManualUpdateRunning || state.operations.download.status == OperationsDownloadStatus.DOWNLOADING

    val openSection: (SettingsSection) -> Unit = { section ->
        selectedSection = section
        if (isTv) {
            pendingSectionFocus = section
        } else {
            phoneDetailOpen = true
        }
    }
    val returnToSectionList: () -> Unit = {
        if (isTv) {
            scope.launch {
                withFrameNanos { }
                runCatching { focus.sectionRequester(selectedSection).requestFocus() }
            }
        } else {
            phoneDetailOpen = false
        }
    }

    // Entry/selection focus uses real frame signals: the target exists only after the selected
    // detail composes, and focus is requested on the attached control without sleeps.
    LaunchedEffect(isTv, pendingSectionFocus, selectedSection) {
        val pending = pendingSectionFocus ?: return@LaunchedEffect
        if (!isTv || pending != selectedSection) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val focused = runCatching {
            settingsSectionFirstRequester(pending, focus, renewalAvailable, supportAvailable)
                .requestFocus()
        }.getOrDefault(false)
        if (focused) pendingSectionFocus = null
    }
    // First Settings entry focuses the selected section row in the RIGHT section column; the
    // detail callback is not invoked and no detail action is focused until OK/LEFT opens it.
    LaunchedEffect(isTv) {
        if (!isTv) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        runCatching { focus.sectionRequester(selectedSection).requestFocus() }
    }

    val detailContent: @Composable () -> Unit = {
        when (selectedSection) {
            SettingsSection.SUBSCRIPTION -> {
                SubscriptionSummary(account = account, expanded = isTv)
                SettingsDivider(
                    verticalPadding = if (isTv) 18.dp else 14.dp,
                )
                SettingsMenuRow(
                    label = if (state.isAccountRefreshing) "جاري تحديث بيانات الاشتراك" else "تحديث بيانات الاشتراك",
                    value = if (state.isAccountRefreshing) "..." else "تحديث",
                    accentValue = true,
                    leadingIcon = Icons.Rounded.Refresh,
                    enabled = account != null || state.isAccountRefreshing,
                    expanded = isTv,
                    focusRequester = focus.refreshAccount,
                    upRequester = focus.sectionRequester(SettingsSection.SUBSCRIPTION),
                    rightRequester = focus.sectionRequester(SettingsSection.SUBSCRIPTION),
                    downRequester = FocusRequester.Cancel,
                    onClick = {
                        if (!state.isAccountRefreshing) onRefreshAccount()
                    },
                )
            }

            SettingsSection.PLAYBACK -> {
                SettingsMenuRow(
                    label = "تشغيل الحلقة التالية",
                    value = settingsToggleLabel(playback.autoplayNextEpisode),
                    toggle = playback.autoplayNextEpisode,
                    expanded = isTv,
                    focusRequester = focus.autoplayNext,
                    upRequester = focus.sectionRequester(SettingsSection.PLAYBACK),
                    rightRequester = focus.sectionRequester(SettingsSection.PLAYBACK),
                    downRequester = focus.resumePlayback,
                    onClick = {
                        playback = settingsStore.setAutoplayNextEpisode(!playback.autoplayNextEpisode)
                        onNotice("تم تحديث تشغيل الحلقة التالية", Icons.Rounded.Check)
                    },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "استكمال المشاهدة",
                    value = settingsToggleLabel(playback.resumePlayback),
                    toggle = playback.resumePlayback,
                    expanded = isTv,
                    focusRequester = focus.resumePlayback,
                    upRequester = focus.autoplayNext,
                    rightRequester = focus.sectionRequester(SettingsSection.PLAYBACK),
                    downRequester = focus.seekStep,
                    onClick = {
                        playback = settingsStore.setResumePlayback(!playback.resumePlayback)
                        onNotice("تم تحديث استكمال المشاهدة", Icons.Rounded.Check)
                    },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "التقديم والترجيع",
                    value = "${playback.seekStepSeconds} ث",
                    trailingChevron = true,
                    expanded = isTv,
                    focusRequester = focus.seekStep,
                    upRequester = focus.resumePlayback,
                    rightRequester = focus.sectionRequester(SettingsSection.PLAYBACK),
                    downRequester = focus.keepScreenOn,
                    onClick = {
                        playback = settingsStore.cycleSeekStep()
                        onNotice(
                            "التقديم والترجيع ${playback.seekStepSeconds} ثانية",
                            Icons.Rounded.Check,
                        )
                    },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "ابقاء الشاشة اثناء التشغيل",
                    value = settingsToggleLabel(playback.keepScreenOn),
                    toggle = playback.keepScreenOn,
                    expanded = isTv,
                    focusRequester = focus.keepScreenOn,
                    upRequester = focus.seekStep,
                    rightRequester = focus.sectionRequester(SettingsSection.PLAYBACK),
                    downRequester = focus.autoHideControls,
                    onClick = {
                        playback = settingsStore.setKeepScreenOn(!playback.keepScreenOn)
                    },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "اخفاء ادوات التحكم تلقائيا",
                    value = settingsToggleLabel(playback.autoHideControls),
                    toggle = playback.autoHideControls,
                    expanded = isTv,
                    focusRequester = focus.autoHideControls,
                    upRequester = focus.keepScreenOn,
                    rightRequester = focus.sectionRequester(SettingsSection.PLAYBACK),
                    downRequester = focus.resetPlayback,
                    onClick = {
                        playback = settingsStore.setAutoHideControls(!playback.autoHideControls)
                    },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "اعادة ضبط اعدادات التشغيل",
                    value = "",
                    expanded = isTv,
                    focusRequester = focus.resetPlayback,
                    upRequester = focus.autoHideControls,
                    rightRequester = focus.sectionRequester(SettingsSection.PLAYBACK),
                    downRequester = FocusRequester.Cancel,
                    onClick = { pendingConfirmation = SettingsConfirmation.RESET_PLAYBACK },
                )
            }

            SettingsSection.NOTIFICATIONS -> {
                SettingsMenuRow(
                    label = "تنبيهات الحلقات الجديدة",
                    value = if (episodeNotificationsAvailable) {
                        settingsToggleLabel(notificationMasterEnabled)
                    } else {
                        "متوقفة مؤقتًا"
                    },
                    subtitle = if (episodeNotificationsAvailable) {
                        "تلقي تنبيه عند توفر حلقة جديدة"
                    } else {
                        null
                    },
                    toggle = if (episodeNotificationsAvailable) notificationMasterEnabled else null,
                    enabled = episodeNotificationsAvailable,
                    expanded = isTv,
                    focusRequester = focus.episodeNotifications,
                    upRequester = focus.sectionRequester(SettingsSection.NOTIFICATIONS),
                    rightRequester = focus.sectionRequester(SettingsSection.NOTIFICATIONS),
                    downRequester = FocusRequester.Cancel,
                    onClick = onToggleEpisodeNotificationMaster,
                )
            }

            SettingsSection.DOWNLOADS -> {
                StorageSummary(
                    downloadedBytes = downloadedBytes,
                    cacheBytes = cacheBytes,
                    expanded = isTv,
                )
                SettingsDivider(
                    verticalPadding = if (isTv) 18.dp else 14.dp,
                )
                SettingsMenuRow(
                    label = "التنزيل عبر Wi-Fi فقط",
                    value = settingsToggleLabel(state.downloadSettings.wifiOnly),
                    toggle = state.downloadSettings.wifiOnly,
                    expanded = isTv,
                    focusRequester = focus.wifiOnly,
                    upRequester = focus.sectionRequester(SettingsSection.DOWNLOADS),
                    rightRequester = focus.sectionRequester(SettingsSection.DOWNLOADS),
                    downRequester = focus.downloadSchedule,
                    onClick = onToggleWifiOnly,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "جدولة التنزيل",
                    value = if (state.downloadSettings.scheduleMode == DownloadScheduleMode.NIGHT) "2 ليلًا" else "الآن",
                    trailingChevron = true,
                    expanded = isTv,
                    focusRequester = focus.downloadSchedule,
                    upRequester = focus.wifiOnly,
                    rightRequester = focus.sectionRequester(SettingsSection.DOWNLOADS),
                    downRequester = focus.concurrentDownloads,
                    onClick = onToggleDownloadSchedule,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "التنزيلات المتزامنة",
                    value = state.downloadSettings.concurrentDownloads.toString(),
                    trailingChevron = true,
                    expanded = isTv,
                    focusRequester = focus.concurrentDownloads,
                    upRequester = focus.downloadSchedule,
                    rightRequester = focus.sectionRequester(SettingsSection.DOWNLOADS),
                    downRequester = if (downloadsEnabled) focus.manageDownloads else focus.clearCache,
                    onClick = onCycleConcurrentDownloads,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "ادارة التنزيلات",
                    value = if (downloadsEnabled) "" else "متوقفة مؤقتًا",
                    accentValue = downloadsEnabled,
                    trailingChevron = downloadsEnabled,
                    enabled = downloadsEnabled,
                    expanded = isTv,
                    focusRequester = focus.manageDownloads,
                    upRequester = focus.concurrentDownloads,
                    rightRequester = focus.sectionRequester(SettingsSection.DOWNLOADS),
                    downRequester = focus.clearCache,
                    onClick = onOpenDownloads,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "مسح الكاش",
                    value = settingsFormatBytes(cacheBytes),
                    leadingIcon = Icons.Rounded.DeleteOutline,
                    expanded = isTv,
                    focusRequester = focus.clearCache,
                    upRequester = if (downloadsEnabled) focus.manageDownloads else focus.concurrentDownloads,
                    rightRequester = focus.sectionRequester(SettingsSection.DOWNLOADS),
                    downRequester = FocusRequester.Cancel,
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { clearSettingsCache(context) }
                            cacheBytes = withContext(Dispatchers.IO) { settingsCacheBytes(context) }
                            onNotice("تم مسح الكاش", Icons.Rounded.DeleteOutline)
                        }
                    },
                )
            }

            SettingsSection.ACCOUNT -> {
                SettingsMenuRow(
                    label = "تغيير المستخدم",
                    value = "",
                    accentValue = true,
                    trailingChevron = true,
                    expanded = isTv,
                    focusRequester = focus.switchProfile,
                    upRequester = focus.sectionRequester(SettingsSection.ACCOUNT),
                    rightRequester = focus.sectionRequester(SettingsSection.ACCOUNT),
                    downRequester = focus.refreshLibrary,
                    onClick = profileSwitch,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "تحديث المكتبة",
                    value = "",
                    trailingChevron = true,
                    expanded = isTv,
                    focusRequester = focus.refreshLibrary,
                    upRequester = focus.switchProfile,
                    rightRequester = focus.sectionRequester(SettingsSection.ACCOUNT),
                    downRequester = if (historyAvailable) focus.clearHistory else focus.logout,
                    onClick = onRefreshLibrary,
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "مسح سجل المشاهدة",
                    value = if (state.history.isEmpty()) "فارغ" else "",
                    enabled = historyAvailable,
                    expanded = isTv,
                    focusRequester = focus.clearHistory,
                    upRequester = focus.refreshLibrary,
                    rightRequester = focus.sectionRequester(SettingsSection.ACCOUNT),
                    downRequester = focus.logout,
                    onClick = { pendingConfirmation = SettingsConfirmation.CLEAR_HISTORY },
                )
                SettingsDivider()
                SettingsMenuRow(
                    label = "تسجيل الخروج",
                    value = "",
                    leadingIcon = Icons.AutoMirrored.Rounded.Logout,
                    leadingIconTint = colors.danger,
                    expanded = isTv,
                    focusRequester = focus.logout,
                    upRequester = focus.clearHistory,
                    rightRequester = focus.sectionRequester(SettingsSection.ACCOUNT),
                    downRequester = FocusRequester.Cancel,
                    onClick = { pendingConfirmation = SettingsConfirmation.LOGOUT },
                )
            }

            SettingsSection.SERVICES -> {
                if (renewalAvailable) {
                    SettingsMenuRow(
                        label = "الموقع الالكتروني",
                        subtitle = "الاشتراك والتجديد عبر hulksa.com",
                        value = if (isTv) "اضغط OK" else "",
                        accentValue = true,
                        leadingIcon = Icons.Rounded.Language,
                        iconBesideLabel = true,
                        expanded = isTv,
                        focusRequester = focus.subscribe,
                        upRequester = focus.sectionRequester(SettingsSection.SERVICES),
                        rightRequester = focus.sectionRequester(SettingsSection.SERVICES),
                        downRequester = if (supportAvailable) focus.support else FocusRequester.Cancel,
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
                        leadingIcon = Icons.Rounded.SupportAgent,
                        iconBesideLabel = true,
                        expanded = isTv,
                        focusRequester = focus.support,
                        upRequester = if (renewalAvailable) {
                            focus.subscribe
                        } else {
                            focus.sectionRequester(SettingsSection.SERVICES)
                        },
                        rightRequester = focus.sectionRequester(SettingsSection.SERVICES),
                        downRequester = FocusRequester.Cancel,
                        onClick = { onGrowthAction(GrowthDestination.SUPPORT) },
                    )
                }
            }

            SettingsSection.ABOUT -> {
                SettingsAboutInfoRows(expanded = isTv)
                SettingsStandaloneAction(
                    label = if (updateBusy) "جاري تنزيل التحديث" else "تحديث التطبيق",
                    primary = true,
                    busy = updateBusy,
                    expanded = isTv,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isTv) 18.dp else 14.dp),
                    focusRequester = focus.updateApp,
                    upRequester = focus.sectionRequester(SettingsSection.ABOUT),
                    downRequester = FocusRequester.Cancel,
                    rightRequester = focus.sectionRequester(SettingsSection.ABOUT),
                    onClick = ::updateApp,
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val expandedLayout = isTv || maxWidth >= 720.dp
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

        if (isTv) {
            val sectionListWidth = (maxWidth * .27f).coerceIn(300.dp, 420.dp)
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(sectionListWidth)
                        .fillMaxHeight()
                        .padding(
                            start = 12.dp,
                            end = 10.dp,
                            top = tvTopInset,
                            bottom = bottomPadding,
                        )
                        .verticalScroll(rememberScrollState()),
                ) {
                    SettingsSectionList(
                        sections = sections,
                        selected = selectedSection,
                        focus = focus,
                        expanded = true,
                        onOpen = openSection,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(colors.line.copy(alpha = .22f)),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            start = 30.dp,
                            end = 22.dp,
                            top = tvTopInset,
                            bottom = bottomPadding,
                        ),
                ) {
                    SettingsDetailHeader(section = selectedSection)
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onFocusChanged { detailOwnsFocus = it.hasFocus }
                            .verticalScroll(sectionScrollStates.getValue(selectedSection)),
                    ) {
                        detailContent()
                    }
                }
            }
        } else {
            BackHandler(enabled = phoneDetailOpen, onBack = returnToSectionList)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = topPadding,
                        bottom = bottomPadding,
                    ),
            ) {
                if (!phoneDetailOpen) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SettingsHeader(expanded = expandedLayout, isTv = false)
                    }
                    Spacer(Modifier.height(if (expandedLayout) 18.dp else 12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SettingsSectionList(
                            sections = sections,
                            selected = selectedSection,
                            focus = focus,
                            expanded = expandedLayout,
                            onOpen = openSection,
                        )
                    }
                } else {
                    SettingsPhoneDetailHeader(
                        title = selectedSection.title,
                        onBack = returnToSectionList,
                    )
                    Spacer(Modifier.height(if (expandedLayout) 16.dp else 12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(sectionScrollStates.getValue(selectedSection)),
                    ) {
                        detailContent()
                    }
                }
            }
        }

        BackHandler(enabled = isTv && detailOwnsFocus, onBack = returnToSectionList)

        pendingConfirmation?.let { confirmation ->
            val (title, message, confirmLabel) = when (confirmation) {
                SettingsConfirmation.CLEAR_HISTORY -> Triple(
                    "مسح سجل المشاهدة؟",
                    "سيتم حذف سجل المشاهدة لهذا المستخدم، " +
                        "ولا يمكن التراجع عن هذا الاجراء",
                    "مسح السجل",
                )

                SettingsConfirmation.RESET_PLAYBACK -> Triple(
                    "اعادة ضبط اعدادات التشغيل؟",
                    "ستعود اعدادات التشغيل الى قيمها الافتراضية دون التاثير على الحساب " +
                        "او التنزيلات او سجل المشاهدة",
                    "اعادة الضبط",
                )

                SettingsConfirmation.LOGOUT -> Triple(
                    "تسجيل الخروج؟",
                    "سيتم انهاء جلسة هذا المستخدم وستحتاج الى تسجيل الدخول مرة اخرى",
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
                            onNotice("تمت اعادة ضبط اعدادات التشغيل", Icons.Rounded.Refresh)
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
private fun SettingsSectionList(
    sections: List<SettingsSection>,
    selected: SettingsSection,
    focus: SettingsFocusGraph,
    expanded: Boolean,
    onOpen: (SettingsSection) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        sections.forEachIndexed { index, section ->
            SettingsSectionRow(
                section = section,
                selected = section == selected,
                expanded = expanded,
                focusRequester = focus.sectionRequester(section),
                upRequester = sections.getOrNull(index - 1)?.let(focus::sectionRequester),
                downRequester = sections.getOrNull(index + 1)?.let(focus::sectionRequester),
                onOpen = { onOpen(section) },
            )
            if (index != sections.lastIndex) {
                Spacer(Modifier.height(if (expanded) 8.dp else 6.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionRow(
    section: SettingsSection,
    selected: Boolean,
    expanded: Boolean,
    focusRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onOpen: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (expanded) 14.dp else 12.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = if (expanded) 58.dp else 50.dp)
                .focusRequester(focusRequester)
                .focusProperties {
                    if (upRequester != null) up = upRequester
                    if (downRequester != null) down = downRequester
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                        onOpen()
                        true
                    } else {
                        false
                    }
                }
                .clip(shape)
                .background(
                    when {
                        selected -> colors.gold.copy(alpha = .13f)
                        focused -> Color(0xFF181914)
                        else -> Color.Transparent
                    },
                )
                .goldFocusEdge(shape = shape, visible = focused)
                .border(
                    width = 1.dp,
                    color = if (selected) colors.gold.copy(alpha = .32f) else Color.Transparent,
                    shape = shape,
                )
                .onFocusChanged { focused = it.isFocused }
                .clickable(role = Role.Button, onClick = onOpen)
                .focusable()
                .padding(horizontal = if (expanded) 16.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = section.title,
                color = if (selected) colors.goldBright else colors.text,
                fontSize = if (expanded) 16.sp else 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = if (selected) colors.goldBright else colors.textMuted,
                modifier = Modifier.size(if (expanded) 21.dp else 19.dp),
            )
        }
    }
}

@Composable
private fun SettingsDetailHeader(section: SettingsSection) {
    val colors = LocalHulkColors.current
    Text(
        text = section.title,
        color = colors.text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun SettingsPhoneDetailHeader(title: String, onBack: () -> Unit) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = colors.text,
            fontSize = MOBILE_SECTION_TITLE_SIZE,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF171812))
                .border(1.dp, colors.gold.copy(alpha = .24f), RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "رجوع",
                color = colors.goldBright,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = colors.goldBright,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** The shared "حول التطبيق" table used by both the services and about detail panes. */
@Composable
private fun SettingsAboutInfoRows(expanded: Boolean) {
    SettingsInfoRow("اصدار HULK SA", BuildConfig.VERSION_NAME, expanded)
    SettingsDivider()
    SettingsInfoRow(
        "اصدار Android",
        Build.VERSION.RELEASE.orEmpty().trim().ifBlank { "Android" },
        expanded,
    )
    SettingsDivider()
    SettingsInfoRow(
        "نوع الجهاز",
        "${Build.MANUFACTURER.orEmpty().trim()} ${Build.MODEL.orEmpty().trim()}".trim().ifBlank { "Android" },
        expanded,
    )
    SettingsDivider()
    SettingsInfoRow("مطور التطبيق", "Mega Store", expanded)
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
                        label = "الغاء",
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
private fun SubscriptionSummary(account: AccountInfo?, expanded: Boolean) {
    SettingsSubscriptionValueRow(
        label = "الحساب",
        value = account?.username?.let(::maskedSettingsUsername) ?: "—",
        expanded = expanded,
    )
    SettingsDivider()
    SettingsSubscriptionValueRow(
        label = "الحالة",
        value = settingsSubscriptionStatus(account),
        expanded = expanded,
        pill = true,
    )
    SettingsDivider()
    SettingsSubscriptionValueRow(
        label = "تاريخ الانتهاء",
        value = settingsExpiryDate(account),
        expanded = expanded,
    )
    SettingsDivider()
    SettingsSubscriptionValueRow(
        label = "المتبقي",
        value = settingsRemaining(account),
        expanded = expanded,
    )
    SettingsDivider()
    SettingsSubscriptionValueRow(
        label = "الاتصالات",
        value = settingsConnectionUsage(account),
        expanded = expanded,
    )
    SettingsDivider()
    SettingsSubscriptionValueRow(
        label = "نوع الاشتراك",
        value = when (account?.isTrial) {
            true -> "تجريبي"
            false -> "عادي"
            null -> "—"
        },
        expanded = expanded,
    )
}

/** Board 09 detail row: label physical RIGHT, real value physical LEFT, optional status pill. */
@Composable
private fun SettingsSubscriptionValueRow(
    label: String,
    value: String,
    expanded: Boolean,
    pill: Boolean = false,
) {
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
        Box(modifier = Modifier.weight(.70f), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = value,
                color = if (pill) colors.goldBright else colors.text,
                fontSize = if (expanded) 15.sp else 13.sp,
                fontWeight = if (pill) FontWeight.Black else FontWeight.SemiBold,
                maxLines = 1,
                modifier = if (pill) {
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.gold.copy(alpha = .10f))
                        .border(1.dp, colors.gold.copy(alpha = .24f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                } else {
                    Modifier
                },
            )
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
    leadingIconTint: Color? = null,
    accentValue: Boolean = false,
    toggle: Boolean? = null,
    trailingChevron: Boolean = false,
    iconBesideLabel: Boolean = false,
    enabled: Boolean = true,
    expanded: Boolean,
    focusRequester: FocusRequester? = null,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    leftRequester: FocusRequester? = null,
    rightRequester: FocusRequester? = null,
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
            if (leftRequester != null) left = leftRequester
            if (rightRequester != null) right = rightRequester
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
        // Services-only anatomy: glyph begins at physical RIGHT beside the title; the value hint
        // stays the last physical LEFT element. All other rows keep their corrected defaults.
        val glyphContent: @Composable (() -> Unit)? = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = leadingIconTint ?: colors.goldBright,
                    modifier = Modifier.size(if (expanded) 21.dp else 19.dp),
                )
            }
        }
        if (iconBesideLabel) {
            glyphContent?.invoke()
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
        if (toggle != null) {
            EntrySwitchVisual(checked = toggle, expanded = expanded)
        } else if (value.isNotBlank()) {
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
        if (trailingChevron) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(if (expanded) 19.dp else 17.dp),
            )
        }
        if (!iconBesideLabel) {
            glyphContent?.invoke()
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
            textAlign = TextAlign.End,
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
