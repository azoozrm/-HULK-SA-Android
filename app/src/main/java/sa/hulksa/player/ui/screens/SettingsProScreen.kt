package sa.hulksa.player.ui.screens

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.data.SettingsProPlaybackSettings
import sa.hulksa.player.data.SettingsProStore
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.ui.LocalProfileSwitchRequester
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SETTINGS_WEBSITE_URL = "https://hulksa.com/"
private const val SETTINGS_ACCOUNT_URL = "https://hulksa.com/account/login.php"
private const val SETTINGS_APPS_URL = "https://hulksa.com/hulk-app/"
private const val SETTINGS_SUPPORT_URL = "https://wa.me/966506349935"

private data class SettingsProAction(
    val text: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val enabled: Boolean = true,
)

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
    var playback by remember(state.account?.username) {
        mutableStateOf(settingsStore.playbackSettings())
    }
    var cacheBytes by remember { mutableLongStateOf(0L) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    fun updateCacheSize() {
        scope.launch {
            cacheBytes = withContext(Dispatchers.IO) { settingsCacheBytes(context) }
        }
    }

    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
        cacheBytes = withContext(Dispatchers.IO) { settingsCacheBytes(context) }
    }

    val account = state.account
    val downloadedBytes = remember(state.downloads) {
        state.downloads.sumOf { download ->
            maxOf(download.bytesDownloaded, 0L)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (isTv) 26.dp else 14.dp,
            end = if (isTv) 26.dp else 14.dp,
            top = if (isTv) 20.dp else 14.dp,
            bottom = if (isTv) 38.dp else 78.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 14.dp),
    ) {
        item(key = "settings_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrandBadge(Modifier.size(if (isTv) 58.dp else 46.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "الاعدادات",
                        color = colors.text,
                        fontSize = if (isTv) 28.sp else 23.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "اشتراكك وتفضيلات المشاهدة والتخزين",
                        color = colors.textMuted,
                        fontSize = if (isTv) 12.sp else 10.sp,
                    )
                }
            }
        }

        item(key = "subscription") {
            SettingsProSection(
                title = "اشتراكي",
                subtitle = "بيانات حقيقية من سيرفر HULK SA",
                isTv = isTv,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            account?.username?.let(::maskedSettingsUsername) ?: "—",
                            color = colors.text,
                            fontSize = if (isTv) 16.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (account?.isTrial == true) "اشتراك تجريبي" else "اشتراك HULK SA",
                            color = colors.goldBright,
                            fontSize = 10.sp,
                        )
                    }
                    FocusButton(
                        text = if (state.isAccountRefreshing) "جاري التحديث…" else "تحديث الاشتراك",
                        onClick = onRefreshAccount,
                        enabled = !state.isAccountRefreshing && account != null,
                        compact = true,
                    )
                }
                Spacer(Modifier.height(12.dp))
                SubscriptionMetricRows(account = account, isTv = isTv)
            }
        }

        item(key = "playback") {
            SettingsProSection(
                title = "التشغيل",
                subtitle = "خيارات ينفذها المشغل داخل التطبيق فعليا",
                isTv = isTv,
            ) {
                SettingsActionRows(
                    isTv = isTv,
                    actions = listOf(
                        SettingsProAction(
                            text = "الحلقة التالية: ${settingsToggleLabel(playback.autoplayNextEpisode)}",
                            primary = playback.autoplayNextEpisode,
                            onClick = {
                                playback = settingsStore.setAutoplayNextEpisode(!playback.autoplayNextEpisode)
                                toast("تم تحديث تشغيل الحلقة التالية.")
                            },
                        ),
                        SettingsProAction(
                            text = "استكمال المشاهدة: ${settingsToggleLabel(playback.resumePlayback)}",
                            primary = playback.resumePlayback,
                            onClick = {
                                playback = settingsStore.setResumePlayback(!playback.resumePlayback)
                                toast("تم تحديث استكمال المشاهدة.")
                            },
                        ),
                        SettingsProAction(
                            text = "التقديم والترجيع: ${playback.seekStepSeconds} ث",
                            onClick = {
                                playback = settingsStore.cycleSeekStep()
                                toast("مدة التقديم والترجيع ${playback.seekStepSeconds} ثانية.")
                            },
                        ),
                        SettingsProAction(
                            text = "ابقاء الشاشة: ${settingsToggleLabel(playback.keepScreenOn)}",
                            primary = playback.keepScreenOn,
                            onClick = {
                                playback = settingsStore.setKeepScreenOn(!playback.keepScreenOn)
                                toast("تم تحديث ابقاء الشاشة اثناء المشاهدة.")
                            },
                        ),
                        SettingsProAction(
                            text = "اخفاء التحكم: ${settingsToggleLabel(playback.autoHideControls)}",
                            primary = playback.autoHideControls,
                            onClick = {
                                playback = settingsStore.setAutoHideControls(!playback.autoHideControls)
                                toast("تم تحديث اخفاء عناصر التحكم.")
                            },
                        ),
                    ),
                )
            }
        }

        item(key = "downloads_storage") {
            SettingsProSection(
                title = "التنزيلات والتخزين",
                subtitle = "التحميلات محفوظة خارج الكاش ولا يحذفها مسح الكاش",
                isTv = isTv,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsProMetric(
                        label = "مساحة التنزيلات",
                        value = settingsFormatBytes(downloadedBytes),
                        modifier = Modifier.weight(1f),
                    )
                    SettingsProMetric(
                        label = "حجم الكاش",
                        value = settingsFormatBytes(cacheBytes),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                SettingsActionRows(
                    isTv = isTv,
                    actions = listOf(
                        SettingsProAction(
                            text = "واي فاي فقط: ${settingsToggleLabel(state.downloadSettings.wifiOnly)}",
                            primary = state.downloadSettings.wifiOnly,
                            onClick = onToggleWifiOnly,
                        ),
                        SettingsProAction(
                            text = if (state.downloadSettings.scheduleMode == DownloadScheduleMode.NIGHT) {
                                "الجدولة: 2 ليلا"
                            } else {
                                "الجدولة: الان"
                            },
                            onClick = onToggleDownloadSchedule,
                        ),
                        SettingsProAction(
                            text = "تحميلات متزامنة: ${state.downloadSettings.concurrentDownloads}",
                            onClick = onCycleConcurrentDownloads,
                        ),
                        SettingsProAction(
                            text = "ادارة التنزيلات",
                            primary = true,
                            onClick = onOpenDownloads,
                        ),
                        SettingsProAction(
                            text = "مسح الكاش",
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { clearSettingsCache(context) }
                                    cacheBytes = withContext(Dispatchers.IO) { settingsCacheBytes(context) }
                                    toast("تم مسح الكاش بدون حذف التنزيلات او سجل المشاهدة.")
                                }
                            },
                        ),
                        SettingsProAction(
                            text = "تحديث حجم الكاش",
                            onClick = ::updateCacheSize,
                        ),
                    ),
                )
            }
        }

        item(key = "profiles_content") {
            SettingsProSection(
                title = "الملفات والمحتوى",
                subtitle = "ادارة الملف الشخصي والمكتبة المحلية",
                isTv = isTv,
            ) {
                SettingsActionRows(
                    isTv = isTv,
                    actions = listOf(
                        SettingsProAction("تغيير المستخدم", profileSwitch, primary = true),
                        SettingsProAction("تحديث المكتبة", onRefreshLibrary),
                        SettingsProAction(
                            "مسح سجل المشاهدة",
                            onClearHistory,
                            enabled = state.history.isNotEmpty(),
                        ),
                    ),
                )
            }
        }

        item(key = "ratings_help") {
            SettingsProSection(
                title = "تقييماتك",
                subtitle = "تقييم 1 الى 5 لكل فيلم او مسلسل ومحفوظ بشكل مستقل لكل ملف شخصي",
                isTv = isTv,
            ) {
                Text(
                    "ستجد التقييم داخل صفحة تفاصيل الفيلم او المسلسل. اضغط نفس الدرجة مرة اخرى لمسح تقييمك.",
                    color = colors.textMuted,
                    fontSize = if (isTv) 12.sp else 10.sp,
                    lineHeight = if (isTv) 19.sp else 16.sp,
                )
            }
        }

        item(key = "services") {
            SettingsProSection(
                title = "خدمات HULK SA",
                subtitle = "روابط الخدمة الرسمية",
                isTv = isTv,
            ) {
                SettingsActionRows(
                    isTv = isTv,
                    actions = listOf(
                        SettingsProAction("اشتراك او تجديد", { open(SETTINGS_WEBSITE_URL) }, primary = true),
                        SettingsProAction("حساب العميل", { open(SETTINGS_ACCOUNT_URL) }),
                        SettingsProAction("الدعم الفني", { open(SETTINGS_SUPPORT_URL) }),
                        SettingsProAction("مركز التطبيقات", { open(SETTINGS_APPS_URL) }),
                    ),
                )
            }
        }

        item(key = "device_app") {
            SettingsProSection(
                title = "التطبيق والجهاز",
                subtitle = "معلومات محلية فقط بدون عرض السيرفر او بيانات الدخول",
                isTv = isTv,
            ) {
                SettingsProInfoLine("HULK SA", "${BuildConfig.VERSION_NAME}  •  ${BuildConfig.VERSION_CODE}")
                SettingsProInfoLine("Android", "${Build.VERSION.RELEASE}  •  API ${Build.VERSION.SDK_INT}")
                SettingsProInfoLine(
                    "الجهاز",
                    "${Build.MANUFACTURER.orEmpty().trim()} ${Build.MODEL.orEmpty().trim()}".trim().ifBlank { "Android" },
                )
                Spacer(Modifier.height(10.dp))
                SettingsActionRows(
                    isTv = isTv,
                    actions = listOf(SettingsProAction("تسجيل الخروج", onLogout)),
                )
            }
        }
    }
}

@Composable
private fun SettingsProSection(
    title: String,
    subtitle: String,
    isTv: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isTv) 20.dp else 16.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF11120E))
            .border(
                1.dp,
                colors.gold.copy(alpha = .22f),
                RoundedCornerShape(if (isTv) 20.dp else 16.dp),
            )
            .padding(if (isTv) 20.dp else 15.dp),
    ) {
        Text(
            title,
            color = colors.text,
            fontSize = if (isTv) 20.sp else 17.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            subtitle,
            color = colors.textMuted,
            fontSize = if (isTv) 11.sp else 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (isTv) 14.dp else 11.dp))
        content()
    }
}

@Composable
private fun SubscriptionMetricRows(account: AccountInfo?, isTv: Boolean) {
    val values = listOf(
        "الحالة" to settingsSubscriptionStatus(account),
        "تاريخ الانتهاء" to settingsExpiryDate(account),
        "المتبقي" to settingsRemaining(account),
        "الاتصالات" to account?.let { "${it.activeConnections} مستخدم · الحد ${it.maxConnections}" }.orEmpty().ifBlank { "—" },
        "نوع الاشتراك" to when (account?.isTrial) {
            true -> "تجريبي"
            false -> "عادي"
            null -> "—"
        },
    )
    val columns = if (isTv) 3 else 2
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        values.chunked(columns).forEach { rowValues ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                rowValues.forEach { (label, value) ->
                    SettingsProMetric(label, value, Modifier.weight(1f))
                }
                repeat(columns - rowValues.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SettingsProMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF191A15))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = colors.textMuted, fontSize = 9.sp, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsActionRows(isTv: Boolean, actions: List<SettingsProAction>) {
    val columns = if (isTv) 3 else 2
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        actions.chunked(columns).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                rowActions.forEach { action ->
                    FocusButton(
                        text = action.text,
                        onClick = action.onClick,
                        primary = action.primary,
                        compact = true,
                        enabled = action.enabled,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowActions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SettingsProInfoLine(label: String, value: String) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.textMuted, fontSize = 10.sp, modifier = Modifier.weight(.35f))
        Text(
            value,
            color = colors.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(.65f),
        )
    }
}

private fun settingsToggleLabel(enabled: Boolean): String = if (enabled) "مفعل" else "متوقف"

private fun maskedSettingsUsername(username: String): String {
    val clean = username.trim()
    if (clean.isBlank()) return "—"
    val visible = clean.takeLast(minOf(4, clean.length))
    return "الحساب  ••••$visible"
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
