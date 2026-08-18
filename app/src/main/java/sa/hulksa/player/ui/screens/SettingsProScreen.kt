package sa.hulksa.player.ui.screens

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val colors = LocalHulkColors.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val profileSwitch = LocalProfileSwitchRequester.current
    val settingsStore = remember(context) { SettingsProStore(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var playback by remember(state.account?.username) { mutableStateOf(settingsStore.playbackSettings()) }
    var cacheBytes by remember { mutableLongStateOf(0L) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
        cacheBytes = withContext(Dispatchers.IO) { settingsCacheBytes(context) }
    }

    val account = state.account
    val downloadedBytes = remember(state.downloads) {
        state.downloads.sumOf { download -> maxOf(download.bytesDownloaded, 0L) }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (isTv) .92f else 1f)
                .widthIn(max = if (isTv) 1080.dp else 720.dp),
            contentPadding = PaddingValues(
                start = if (isTv) 22.dp else 14.dp,
                end = if (isTv) 22.dp else 14.dp,
                top = if (isTv) 22.dp else 16.dp,
                bottom = if (isTv) 42.dp else 86.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
        ) {
            item(key = "settings_header") {
                SettingsHeader(isTv = isTv)
            }

            item(key = "subscription") {
                SettingsPanel(title = "اشتراكي", isTv = isTv) {
                    SubscriptionSummary(account = account, isTv = isTv)
                    SettingsDivider()
                    SettingsMenuRow(
                        label = if (state.isAccountRefreshing) "جاري تحديث الاشتراك" else "تحديث بيانات الاشتراك",
                        value = if (state.isAccountRefreshing) "..." else "تحديث",
                        enabled = !state.isAccountRefreshing && account != null,
                        isTv = isTv,
                        onClick = onRefreshAccount,
                    )
                }
            }

            item(key = "playback") {
                SettingsPanel(title = "التشغيل", isTv = isTv) {
                    SettingsMenuRow(
                        label = "تشغيل الحلقة التالية",
                        value = settingsToggleLabel(playback.autoplayNextEpisode),
                        accentValue = playback.autoplayNextEpisode,
                        isTv = isTv,
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
                        isTv = isTv,
                        onClick = {
                            playback = settingsStore.setResumePlayback(!playback.resumePlayback)
                            toast("تم تحديث استكمال المشاهدة")
                        },
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        label = "التقديم والترجيع",
                        value = "${playback.seekStepSeconds} ث",
                        isTv = isTv,
                        onClick = {
                            playback = settingsStore.cycleSeekStep()
                            toast("التقديم والترجيع ${playback.seekStepSeconds} ثانية")
                        },
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        label = "ابقاء الشاشة اثناء التشغيل",
                        value = settingsToggleLabel(playback.keepScreenOn),
                        accentValue = playback.keepScreenOn,
                        isTv = isTv,
                        onClick = {
                            playback = settingsStore.setKeepScreenOn(!playback.keepScreenOn)
                        },
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        label = "اخفاء ادوات التحكم تلقائيا",
                        value = settingsToggleLabel(playback.autoHideControls),
                        accentValue = playback.autoHideControls,
                        isTv = isTv,
                        onClick = {
                            playback = settingsStore.setAutoHideControls(!playback.autoHideControls)
                        },
                    )
                }
            }

            item(key = "downloads_storage") {
                SettingsPanel(title = "التنزيلات والتخزين", isTv = isTv) {
                    StorageSummary(
                        downloadedBytes = downloadedBytes,
                        cacheBytes = cacheBytes,
                        isTv = isTv,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        label = "التنزيل عبر واي فاي فقط",
                        value = settingsToggleLabel(state.downloadSettings.wifiOnly),
                        accentValue = state.downloadSettings.wifiOnly,
                        isTv = isTv,
                        onClick = onToggleWifiOnly,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        label = "جدولة التنزيل",
                        value = if (state.downloadSettings.scheduleMode == DownloadScheduleMode.NIGHT) "2 ليلا" else "الان",
                        isTv = isTv,
                        onClick = onToggleDownloadSchedule,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        label = "التنزيلات المتزامنة",
                        value = state.downloadSettings.concurrentDownloads.toString(),
                        isTv = isTv,
                        onClick = onCycleConcurrentDownloads,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        label = "ادارة التنزيلات",
                        value = "فتح",
                        accentValue = true,
                        isTv = isTv,
                        onClick = onOpenDownloads,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        label = "مسح الكاش",
                        value = settingsFormatBytes(cacheBytes),
                        isTv = isTv,
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { clearSettingsCache(context) }
                                cacheBytes = withContext(Dispatchers.IO) { settingsCacheBytes(context) }
                                toast("تم مسح الكاش")
                            }
                        },
                    )
                }
            }

            item(key = "account_content") {
                SettingsPanel(title = "الحساب والمحتوى", isTv = isTv) {
                    SettingsMenuRow(
                        label = "تغيير المستخدم",
                        value = "فتح",
                        accentValue = true,
                        isTv = isTv,
                        onClick = profileSwitch,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        label = "تحديث المكتبة",
                        value = "تحديث",
                        isTv = isTv,
                        onClick = onRefreshLibrary,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        label = "مسح سجل المشاهدة",
                        value = if (state.history.isEmpty()) "فارغ" else "مسح",
                        enabled = state.history.isNotEmpty(),
                        isTv = isTv,
                        onClick = onClearHistory,
                    )
                }
            }

            item(key = "services") {
                SettingsPanel(title = "خدمات HULK SA", isTv = isTv) {
                    SettingsMenuRow("الاشتراك او التجديد", "فتح", true, true, isTv) { open(SETTINGS_WEBSITE_URL) }
                    SettingsDivider()
                    SettingsMenuRow("حساب العميل", "فتح", false, true, isTv) { open(SETTINGS_ACCOUNT_URL) }
                    SettingsDivider()
                    SettingsMenuRow("الدعم الفني", "فتح", false, true, isTv) { open(SETTINGS_SUPPORT_URL) }
                    SettingsDivider()
                    SettingsMenuRow("مركز التطبيقات", "فتح", false, true, isTv) { open(SETTINGS_APPS_URL) }
                }
            }

            item(key = "device_app") {
                SettingsPanel(title = "التطبيق والجهاز", isTv = isTv) {
                    SettingsInfoRow("HULK SA", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", isTv)
                    SettingsDivider()
                    SettingsInfoRow("Android", "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}", isTv)
                    SettingsDivider()
                    SettingsInfoRow(
                        "الجهاز",
                        "${Build.MANUFACTURER.orEmpty().trim()} ${Build.MODEL.orEmpty().trim()}".trim().ifBlank { "Android" },
                        isTv,
                    )
                    SettingsDivider()
                    SettingsMenuRow(
                        label = "تسجيل الخروج",
                        value = "خروج",
                        isTv = isTv,
                        onClick = onLogout,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(isTv: Boolean) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isTv) 4.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (isTv) 54.dp else 44.dp)
                .clip(CircleShape)
                .background(Color(0xFF171812))
                .border(1.dp, colors.gold.copy(alpha = .35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = colors.goldBright,
                modifier = Modifier.size(if (isTv) 29.dp else 24.dp),
            )
        }
        Text(
            text = "الاعدادات",
            color = colors.text,
            fontSize = if (isTv) 29.sp else 24.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun SettingsPanel(
    title: String,
    isTv: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isTv) 18.dp else 15.dp))
            .background(Color(0xFF11120E))
            .border(
                width = 1.dp,
                color = colors.gold.copy(alpha = .20f),
                shape = RoundedCornerShape(if (isTv) 18.dp else 15.dp),
            )
            .padding(horizontal = if (isTv) 20.dp else 14.dp, vertical = if (isTv) 16.dp else 13.dp),
    ) {
        Text(
            text = title,
            color = colors.text,
            fontSize = if (isTv) 19.sp else 16.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(if (isTv) 11.dp else 8.dp))
        content()
    }
}

@Composable
private fun SubscriptionSummary(account: AccountInfo?, isTv: Boolean) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = account?.username?.let(::maskedSettingsUsername) ?: "—",
                color = colors.text,
                fontSize = if (isTv) 16.sp else 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (account?.isTrial == true) "تجريبي" else "HULK SA",
                color = colors.textMuted,
                fontSize = if (isTv) 11.sp else 9.sp,
            )
        }
        Text(
            text = settingsSubscriptionStatus(account),
            color = colors.goldBright,
            fontSize = if (isTv) 14.sp else 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
    Spacer(Modifier.height(if (isTv) 14.dp else 11.dp))
    val metrics = listOf(
        "تاريخ الانتهاء" to settingsExpiryDate(account),
        "المتبقي" to settingsRemaining(account),
        "الاتصالات" to account?.let { "${it.activeConnections} / ${it.maxConnections}" }.orEmpty().ifBlank { "—" },
        "نوع الاشتراك" to when (account?.isTrial) {
            true -> "تجريبي"
            false -> "عادي"
            null -> "—"
        },
    )
    val columns = if (isTv) 4 else 2
    Column(verticalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 8.dp)) {
        metrics.chunked(columns).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
            ) {
                rowMetrics.forEach { (label, value) ->
                    SettingsMetric(label, value, isTv, Modifier.weight(1f))
                }
                repeat(columns - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StorageSummary(downloadedBytes: Long, cacheBytes: Long, isTv: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 10.dp),
    ) {
        SettingsMetric("التنزيلات", settingsFormatBytes(downloadedBytes), isTv, Modifier.weight(1f))
        SettingsMetric("الكاش", settingsFormatBytes(cacheBytes), isTv, Modifier.weight(1f))
    }
}

@Composable
private fun SettingsMetric(label: String, value: String, isTv: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Column(modifier = modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = if (isTv) 10.sp else 9.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = colors.text,
            fontSize = if (isTv) 13.sp else 12.sp,
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
    isTv: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 11.dp else 10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else .45f }
            .clip(shape)
            .background(if (focused) colors.gold.copy(alpha = .10f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (focused) colors.goldBright.copy(alpha = .85f) else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .focusable(enabled = enabled)
            .padding(
                horizontal = if (isTv) 14.dp else 10.dp,
                vertical = if (isTv) 13.dp else 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = colors.text,
            fontSize = if (isTv) 14.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = if (accentValue) colors.goldBright else colors.textMuted,
            fontSize = if (isTv) 12.sp else 11.sp,
            fontWeight = if (accentValue) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String, isTv: Boolean) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTv) 14.dp else 10.dp, vertical = if (isTv) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = if (isTv) 12.sp else 11.sp,
            modifier = Modifier.weight(.38f),
        )
        Text(
            text = value,
            color = colors.text,
            fontSize = if (isTv) 13.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsDivider() {
    val colors = LocalHulkColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(1.dp)
            .background(colors.textMuted.copy(alpha = .10f)),
    )
}

private fun settingsToggleLabel(enabled: Boolean): String = if (enabled) "مفعل" else "متوقف"

private fun maskedSettingsUsername(username: String): String {
    val clean = username.trim()
    if (clean.isBlank()) return "—"
    val visible = clean.takeLast(minOf(4, clean.length))
    return "الحساب ••••$visible"
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
    return SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar-SA")).format(Date(epoch * 1000L))
}

private fun settingsRemaining(account: AccountInfo?): String {
    val epoch = account?.expiresAtEpochSeconds?.takeIf { it > 0L } ?: return "غير محدد"
    val remainingSeconds = epoch - System.currentTimeMillis() / 1000L
    if (remainingSeconds <= 0L) return "منتهي"
    val days = (remainingSeconds + 86_399L) / 86_400L
    return if (days == 1L) "يوم واحد" else "$days يوم"
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
