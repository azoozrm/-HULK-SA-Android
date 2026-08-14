package sa.hulksa.player.data

import android.content.Context
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.CapabilityFinding
import sa.hulksa.player.model.CapabilityStatus
import sa.hulksa.player.model.DiagnosticEndpoint
import sa.hulksa.player.model.DiagnosticIssue
import sa.hulksa.player.model.DiagnosticSeverity
import sa.hulksa.player.model.FeatureRecommendation
import sa.hulksa.player.model.ServerDiagnosticsReport
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class ServerDiagnosticsEngine(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun run(
        session: AuthenticatedSession,
        onProgress: (progress: Int, stage: String) -> Unit,
    ): ServerDiagnosticsReport = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val startedElapsed = SystemClock.elapsedRealtime()
        val base = session.portal.baseUrl.trimEnd('/').toHttpUrl()
        val endpoints = mutableListOf<DiagnosticEndpoint>()
        val issues = mutableListOf<DiagnosticIssue>()
        val capabilities = mutableListOf<CapabilityFinding>()

        fun progress(value: Int, stage: String) = onProgress(value.coerceIn(0, 100), stage)

        progress(3, "فحص الاتصال وبيانات الحساب")
        val rootProbe = jsonProbe(session, null, expectedArray = false)
        endpoints += rootProbe.endpoint("المصادقة وبيانات الحساب")
        val root = rootProbe.objectValue
        val serverInfo = root?.optJSONObject("server_info")
        val serverTimezone = serverInfo?.optString("timezone")?.takeIf(String::isNotBlank)
        val serverProtocol = serverInfo?.optString("server_protocol")?.takeIf(String::isNotBlank)
        val serverUrl = serverInfo?.optString("url")?.takeIf(String::isNotBlank)

        if (!rootProbe.success) {
            issues += DiagnosticIssue(
                id = "account_api",
                title = "تعذر التحقق من واجهة الحساب",
                severity = DiagnosticSeverity.CRITICAL,
                details = rootProbe.errorMessage ?: "لم تصل استجابة صالحة من player_api.php.",
                action = "تحقق من اتصال الجهاز وصلاحية بيانات الاشتراك ثم اعد الفحص.",
            )
        }

        progress(12, "فحص فئات القنوات والافلام والمسلسلات")
        val liveCategoriesProbe = jsonProbe(session, "get_live_categories", expectedArray = true)
        val vodCategoriesProbe = jsonProbe(session, "get_vod_categories", expectedArray = true)
        val seriesCategoriesProbe = jsonProbe(session, "get_series_categories", expectedArray = true)
        endpoints += liveCategoriesProbe.endpoint("فئات القنوات")
        endpoints += vodCategoriesProbe.endpoint("فئات الافلام")
        endpoints += seriesCategoriesProbe.endpoint("فئات المسلسلات")

        progress(25, "تحليل مكتبة القنوات المباشرة")
        val liveProbe = jsonProbe(session, "get_live_streams", expectedArray = true)
        endpoints += liveProbe.endpoint("بيانات القنوات المباشرة")
        val liveItems = liveProbe.arrayValue ?: JSONArray()

        progress(38, "تحليل مكتبة الافلام")
        val vodProbe = jsonProbe(session, "get_vod_streams", expectedArray = true)
        endpoints += vodProbe.endpoint("بيانات الافلام")
        val vodItems = vodProbe.arrayValue ?: JSONArray()

        progress(50, "تحليل مكتبة المسلسلات")
        val seriesProbe = jsonProbe(session, "get_series", expectedArray = true)
        endpoints += seriesProbe.endpoint("بيانات المسلسلات")
        val seriesItems = seriesProbe.arrayValue ?: JSONArray()

        val liveSample = liveItems.firstObject()
        val vodSample = vodItems.firstObject()
        val seriesSample = seriesItems.firstObject()
        val liveId = liveSample?.optInt("stream_id")?.takeIf { it > 0 }
        val vodId = vodSample?.optInt("stream_id")?.takeIf { it > 0 }
        val seriesId = seriesSample?.optInt("series_id")?.takeIf { it > 0 }

        progress(60, "فحص EPG وبيانات المحتوى التفصيلية")
        val epgProbe = liveId?.let {
            jsonProbe(
                session = session,
                action = "get_short_epg",
                expectedArray = false,
                extra = mapOf("stream_id" to it.toString(), "limit" to "4"),
            )
        }
        epgProbe?.let { endpoints += it.endpoint("دليل البرامج EPG") }

        val vodInfoProbe = vodId?.let {
            jsonProbe(
                session = session,
                action = "get_vod_info",
                expectedArray = false,
                extra = mapOf("vod_id" to it.toString()),
            )
        }
        vodInfoProbe?.let { endpoints += it.endpoint("تفاصيل الفيلم") }

        val seriesInfoProbe = seriesId?.let {
            jsonProbe(
                session = session,
                action = "get_series_info",
                expectedArray = false,
                extra = mapOf("series_id" to it.toString()),
            )
        }
        seriesInfoProbe?.let { endpoints += it.endpoint("تفاصيل المسلسل والحلقات") }

        val epgCount = epgProbe?.objectValue?.optJSONArray("epg_list")?.length() ?: 0
        val catchupCount = liveItems.countObjects { item ->
            item.optString("tv_archive") == "1" || item.optInt("tv_archive_duration", 0) > 0
        }
        val epgLinkedCount = liveItems.countObjects { item -> item.optString("epg_channel_id").isNotBlank() }
        val liveArtworkPercent = liveItems.percentWith("stream_icon")
        val vodArtworkPercent = vodItems.percentWith("stream_icon")
        val seriesArtworkPercent = seriesItems.percentWith("cover")
        val overallArtworkPercent = weightedPercent(
            liveArtworkPercent to liveItems.length(),
            vodArtworkPercent to vodItems.length(),
            seriesArtworkPercent to seriesItems.length(),
        )
        val vodMetadataPercent = vodItems.percentMatching { item ->
            item.optString("rating").isNotBlank() || item.optString("rating_5based").isNotBlank() ||
                item.optString("plot").isNotBlank() || item.optString("year").isNotBlank()
        }
        val seriesMetadataPercent = seriesItems.percentMatching { item ->
            item.optString("rating").isNotBlank() || item.optString("plot").isNotBlank() ||
                item.optString("genre").isNotBlank() || item.optString("releaseDate").isNotBlank()
        }

        progress(70, "غرفة العمليات: اختبار عينات البث وتحليل سبب الفشل")
        val liveTsProbe = liveId?.let {
            streamProbe(
                label = "عينة بث مباشر TS",
                url = streamUrl(base, "live", session, it, "ts"),
            )
        }
        val liveHlsProbe = liveId?.let {
            streamProbe(
                label = "عينة بث مباشر HLS",
                url = streamUrl(base, "live", session, it, "m3u8"),
            )
        }
        val vodExtension = vodSample?.optString("container_extension")?.ifBlank { "mp4" } ?: "mp4"
        val vodStreamProbe = vodId?.let {
            streamProbe(
                label = "عينة فيلم",
                url = streamUrl(base, "movie", session, it, vodExtension),
            )
        }
        listOfNotNull(liveTsProbe, liveHlsProbe, vodStreamProbe).forEach { endpoints += it.toEndpoint() }

        val episodeSample = seriesInfoProbe?.objectValue?.firstEpisode()
        val episodeStreamProbe = episodeSample?.let { (episodeId, extension) ->
            streamProbe(
                label = "عينة حلقة",
                url = streamUrl(base, "series", session, episodeId, extension),
            )
        }
        episodeStreamProbe?.let { endpoints += it.toEndpoint() }

        progress(82, "فحص قدرات الجهاز والشبكة والتخزين")
        val device = deviceSnapshot()
        val network = networkSnapshot()
        val availableStorageBytes = StatFs(appContext.filesDir.absolutePath).availableBytes

        if (!network.validated) {
            issues += DiagnosticIssue(
                id = "network_validation",
                title = "اتصال الانترنت غير مستقر او غير موثق",
                severity = DiagnosticSeverity.CRITICAL,
                details = "النظام لم يؤكد وصول الشبكة الحالية الى الانترنت.",
                action = "افحص الواي فاي او الكيبل ثم اعد الاختبار.",
            )
        }
        if (availableStorageBytes < 2L * 1024L * 1024L * 1024L) {
            issues += DiagnosticIssue(
                id = "low_storage",
                title = "المساحة المتاحة منخفضة",
                severity = DiagnosticSeverity.WARNING,
                details = "المتاح اقل من 2 جيجابايت وقد يؤثر على التحميلات والكاش.",
                action = "وفر مساحة اضافية قبل تنزيل الافلام او الحلقات.",
            )
        }
        if (session.account.maxConnections > 0 && session.account.activeConnections >= session.account.maxConnections) {
            issues += DiagnosticIssue(
                id = "connection_limit",
                title = "حد الاتصالات مستخدم بالكامل",
                severity = DiagnosticSeverity.WARNING,
                details = "الاتصالات الحالية ${session.account.activeConnections} من ${session.account.maxConnections}.",
                action = "اغلق التشغيل من جهاز اخر قبل بدء بث جديد.",
            )
        }
        val slowApi = endpoints.filter { it.success && it.latencyMs >= 2_000L }
        if (slowApi.isNotEmpty()) {
            issues += DiagnosticIssue(
                id = "slow_api",
                title = "بعض واجهات السيرفر بطيئة",
                severity = DiagnosticSeverity.WARNING,
                details = slowApi.joinToString("، ") { "${it.name} ${it.latencyMs}ms" },
                action = "راقب النتيجة في اوقات مختلفة قبل اعتبارها مشكلة دائمة.",
            )
        }
        val failedEndpoints = endpoints.filter { it.kind == "api" && !it.success }
        if (failedEndpoints.isNotEmpty()) {
            issues += DiagnosticIssue(
                id = "failed_endpoints",
                title = "واجهات API لم تستجب للفحص",
                severity = if (failedEndpoints.size >= 3) DiagnosticSeverity.CRITICAL else DiagnosticSeverity.WARNING,
                details = failedEndpoints.joinToString("، ") { it.name },
                action = "اعد الفحص تلقائيا، ثم راجع الشبكة او السيرفر اذا تكرر فشل واجهات API الاساسية.",
            )
        }
        if (overallArtworkPercent in 0..69) {
            issues += DiagnosticIssue(
                id = "artwork_quality",
                title = "تغطية الشعارات والبوسترات ناقصة",
                severity = DiagnosticSeverity.WARNING,
                details = "التغطية الحالية تقريبا $overallArtworkPercent٪ من عناصر المكتبة.",
                action = "استكمال صور المحتوى من السيرفر يرفع جودة الواجهة والتوصيات.",
            )
        }
        if (vodStreamProbe != null && vodStreamProbe.success && !vodStreamProbe.supportsRange) {
            issues += DiagnosticIssue(
                id = "range_missing",
                title = "السيرفر لا يؤكد استكمال التحميل",
                severity = DiagnosticSeverity.WARNING,
                details = "عينة الفيلم لم ترجع Partial Content او Accept-Ranges.",
                action = "التحميل قد يبدأ من الصفر بعد الانقطاع؛ يلزم دعم Range من السيرفر.",
            )
        }

        capabilities += CapabilityFinding(
            id = "xtream_api",
            title = "واجهة Xtream والحساب",
            status = if (rootProbe.success) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSTABLE,
            details = if (rootProbe.success) "تمت المصادقة وقراءة معلومات الحساب والسيرفر." else "تعذر الحصول على استجابة سليمة.",
            evidence = "زمن الاستجابة ${rootProbe.latencyMs}ms",
        )
        capabilities += catalogCapability("live", "القنوات المباشرة", liveProbe, liveItems.length())
        capabilities += catalogCapability("vod", "مكتبة الافلام", vodProbe, vodItems.length())
        capabilities += catalogCapability("series", "مكتبة المسلسلات", seriesProbe, seriesItems.length())
        capabilities += CapabilityFinding(
            id = "epg",
            title = "دليل البرامج EPG",
            status = when {
                epgCount > 0 -> CapabilityStatus.SUPPORTED
                epgLinkedCount > 0 -> CapabilityStatus.PARTIAL
                else -> CapabilityStatus.UNSUPPORTED
            },
            details = when {
                epgCount > 0 -> "تم جلب $epgCount برامج من عينة قناة بنجاح."
                epgLinkedCount > 0 -> "توجد معرفات EPG في $epgLinkedCount قناة لكن العينة لم ترجع برامج."
                else -> "لم تظهر بيانات EPG قابلة للاستخدام في المكتبة."
            },
            evidence = "قنوات مرتبطة بـ EPG: $epgLinkedCount",
        )
        capabilities += CapabilityFinding(
            id = "catchup",
            title = "الاعادة التلفزيونية Catch-up",
            status = if (catchupCount > 0) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED,
            details = if (catchupCount > 0) "$catchupCount قناة تعلن دعم الارشيف." else "لم تعلن القنوات عن tv_archive.",
            evidence = "عدد القنوات المدعومة: $catchupCount",
        )
        capabilities += streamCapability("mpeg_ts", "البث المباشر MPEG-TS", liveTsProbe)
        capabilities += streamCapability("hls", "البث المتكيف HLS", liveHlsProbe)
        capabilities += CapabilityFinding(
            id = "download_resume",
            title = "استكمال التحميل بعد الانقطاع",
            status = when {
                vodStreamProbe == null -> CapabilityStatus.UNSUPPORTED
                vodStreamProbe.success && vodStreamProbe.supportsRange -> CapabilityStatus.SUPPORTED
                vodStreamProbe.success -> CapabilityStatus.PARTIAL
                else -> CapabilityStatus.UNSTABLE
            },
            details = when {
                vodStreamProbe?.supportsRange == true -> "السيرفر قبل طلب Range على عينة الفيلم."
                vodStreamProbe?.success == true -> "الفيلم يعمل لكن دعم Range غير مؤكد."
                else -> "تعذر التحقق من عينة فيلم."
            },
            evidence = vodStreamProbe?.evidence() ?: "لا توجد عينة",
        )
        capabilities += CapabilityFinding(
            id = "metadata",
            title = "بيانات المحتوى التفصيلية",
            status = when {
                vodInfoProbe?.success == true && seriesInfoProbe?.success == true -> CapabilityStatus.SUPPORTED
                vodInfoProbe?.success == true || seriesInfoProbe?.success == true -> CapabilityStatus.PARTIAL
                else -> CapabilityStatus.UNSUPPORTED
            },
            details = "بيانات الافلام $vodMetadataPercent٪، وبيانات المسلسلات $seriesMetadataPercent٪.",
            evidence = "تفاصيل الفيلم: ${yesNo(vodInfoProbe?.success)} • تفاصيل المسلسل: ${yesNo(seriesInfoProbe?.success)}",
        )
        capabilities += CapabilityFinding(
            id = "artwork",
            title = "الشعارات والبوسترات",
            status = when {
                overallArtworkPercent >= 85 -> CapabilityStatus.SUPPORTED
                overallArtworkPercent >= 45 -> CapabilityStatus.PARTIAL
                else -> CapabilityStatus.UNSUPPORTED
            },
            details = "تغطية الصور الاجمالية $overallArtworkPercent٪.",
            evidence = "قنوات $liveArtworkPercent٪ • افلام $vodArtworkPercent٪ • مسلسلات $seriesArtworkPercent٪",
        )
        capabilities += CapabilityFinding(
            id = "modern_video_decode",
            title = "فك ترميز الفيديو الحديث على الجهاز",
            status = if (device.supportedVideoMimes.size >= 2) CapabilityStatus.SUPPORTED else CapabilityStatus.PARTIAL,
            details = device.videoSummary,
            evidence = "Android ${Build.VERSION.RELEASE} • ${Build.MODEL}",
        )
        capabilities += CapabilityFinding(
            id = "dolby_audio_decode",
            title = "صوت Dolby على الجهاز",
            status = if (device.dolbySupported) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED,
            details = if (device.dolbySupported) "الجهاز يعلن وجود Decoder لصوت AC3 او E-AC3." else "لم يعلن الجهاز عن Decoder Dolby.",
            evidence = device.audioSummary,
        )
        capabilities += CapabilityFinding(
            id = "portal_protocol",
            title = "بروتوكول بوابة IPTV",
            status = CapabilityStatus.SUPPORTED,
            details = if (base.isHttps) {
                "البوابة تعمل عبر HTTPS. هذا تحسين اختياري ولا يغير دعم ميزات IPTV."
            } else {
                "البوابة تعمل عبر HTTP، وهو الوضع الشائع في مزودي IPTV ولا يخصم من التقييم."
            },
            evidence = "${base.scheme.uppercase(Locale.US)} • الهوست: ${base.host}",
        )

        val recommendations = buildRecommendations(capabilities)
        val criticalCount = issues.count { it.severity == DiagnosticSeverity.CRITICAL }
        val warningCount = issues.count { it.severity == DiagnosticSeverity.WARNING }
        val endpointFailureCount = endpoints.count { it.kind == "api" && !it.success }
        val score = (100 - criticalCount * 18 - warningCount * 6 - endpointFailureCount * 5).coerceIn(0, 100) // اختبارات البث غير الحاسمة وHTTP لا تخصم
        val overallStatus = when {
            score >= 90 -> "ممتاز"
            score >= 75 -> "جيد جدا"
            score >= 60 -> "جيد مع ملاحظات"
            score >= 40 -> "يحتاج تحسين"
            else -> "غير مستقر"
        }
        val apiLatencies = endpoints.filter { it.success && it.kind == "api" }
            .map(DiagnosticEndpoint::latencyMs)
        val averageApiLatency = if (apiLatencies.isNotEmpty()) {
            apiLatencies.average().roundToInt().toLong()
        } else {
            0L
        }
        val sampleThroughput = listOfNotNull(liveTsProbe, liveHlsProbe, vodStreamProbe, episodeStreamProbe)
            .filter(StreamProbe::success)
            .maxOfOrNull(StreamProbe::megabitsPerSecond)
            ?: 0.0

        progress(100, "اكتمل التحليل الهندسي وفصل مشاكل السيرفر والتطبيق والشبكة")
        return@withContext ServerDiagnosticsReport(
            generatedAtEpochMs = System.currentTimeMillis(),
            durationMs = SystemClock.elapsedRealtime() - startedElapsed,
            portalHost = base.host,
            portalScheme = base.scheme,
            serverTimezone = serverTimezone,
            serverProtocol = serverProtocol,
            serverReportedHost = serverUrl,
            overallScore = score,
            overallStatus = overallStatus,
            averageApiLatencyMs = averageApiLatency,
            bestSampleThroughputMbps = sampleThroughput,
            liveCount = liveItems.length(),
            movieCount = vodItems.length(),
            seriesCount = seriesItems.length(),
            categoryCount = (liveCategoriesProbe.arrayValue?.length() ?: 0) +
                (vodCategoriesProbe.arrayValue?.length() ?: 0) +
                (seriesCategoriesProbe.arrayValue?.length() ?: 0),
            availableStorageBytes = availableStorageBytes,
            networkSummary = network.summary,
            deviceSummary = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}",
            endpoints = endpoints,
            capabilities = capabilities,
            issues = issues,
            recommendations = recommendations,
        )
    }

    private fun jsonProbe(
        session: AuthenticatedSession,
        action: String?,
        expectedArray: Boolean,
        extra: Map<String, String> = emptyMap(),
    ): JsonProbe {
        val started = SystemClock.elapsedRealtime()
        val url = apiUrl(session, action, extra)
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", JSON.toString())
            .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME} Diagnostics")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val latency = SystemClock.elapsedRealtime() - started
                if (!response.isSuccessful || body.isBlank()) {
                    JsonProbe(false, latency, response.code, null, null, "HTTP ${response.code}")
                } else if (expectedArray) {
                    val array = JSONArray(body)
                    JsonProbe(true, latency, response.code, null, array, null)
                } else {
                    val obj = JSONObject(body)
                    JsonProbe(true, latency, response.code, obj, null, null)
                }
            }
        } catch (error: IOException) {
            JsonProbe(false, SystemClock.elapsedRealtime() - started, null, null, null, "خطا شبكة: ${error.javaClass.simpleName}")
        } catch (error: Exception) {
            JsonProbe(false, SystemClock.elapsedRealtime() - started, null, null, null, "استجابة غير صالحة: ${error.javaClass.simpleName}")
        }
    }

    private fun streamProbe(label: String, url: String): StreamProbe {
        val started = SystemClock.elapsedRealtime()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=0-65535")
            .header("Accept", "*/*")
            .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME} Diagnostics")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val buffer = Buffer()
                val source = response.body?.source()
                var total = 0L
                while (source != null && total < 65_536L && SystemClock.elapsedRealtime() - started < 5_500L) {
                    val read = source.read(buffer, minOf(8_192L, 65_536L - total))
                    if (read <= 0L) break
                    total += read
                }
                val bytes = buffer.readByteArray()
                val elapsed = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1L)
                val contentType = response.header("Content-Type") ?: response.body?.contentType()?.toString()
                val acceptRanges = response.header("Accept-Ranges").orEmpty()
                val supportsRange = response.code == 206 || acceptRanges.contains("bytes", ignoreCase = true)
                val textPrefix = bytes.take(256).toByteArray().toString(Charsets.UTF_8).trim()
                val looksHls = textPrefix.startsWith("#EXTM3U") || contentType.orEmpty().contains("mpegurl", ignoreCase = true)
                val looksTs = contentType.orEmpty().contains("mp2t", ignoreCase = true) ||
                    (bytes.size >= 376 && bytes[0] == 0x47.toByte() && bytes[188] == 0x47.toByte())
                val success = response.isSuccessful && bytes.isNotEmpty()
                StreamProbe(
                    label = label,
                    success = success,
                    latencyMs = elapsed,
                    httpCode = response.code,
                    contentType = contentType,
                    bytesRead = total,
                    supportsRange = supportsRange,
                    looksHls = looksHls,
                    looksTs = looksTs,
                    megabitsPerSecond = total * 8.0 / elapsed.toDouble() / 1_000.0,
                    errorMessage = if (success) null else "لم تصل بيانات بث صالحة",
                )
            }
        } catch (error: Exception) {
            StreamProbe(
                label = label,
                success = false,
                latencyMs = SystemClock.elapsedRealtime() - started,
                httpCode = null,
                contentType = null,
                bytesRead = 0L,
                supportsRange = false,
                looksHls = false,
                looksTs = false,
                megabitsPerSecond = 0.0,
                errorMessage = "${error.javaClass.simpleName}",
            )
        }
    }

    private fun apiUrl(
        session: AuthenticatedSession,
        action: String?,
        extra: Map<String, String>,
    ): HttpUrl = (session.portal.baseUrl.trimEnd('/') + "/player_api.php").toHttpUrl().newBuilder()
        .addQueryParameter("username", session.credentials.username)
        .addQueryParameter("password", session.credentials.password)
        .apply {
            if (action != null) addQueryParameter("action", action)
            extra.forEach { (key, value) -> addQueryParameter(key, value) }
        }
        .build()

    private fun streamUrl(
        base: HttpUrl,
        kind: String,
        session: AuthenticatedSession,
        streamId: Int,
        extension: String,
    ): String = base.newBuilder()
        .addPathSegment(kind)
        .addPathSegment(session.credentials.username)
        .addPathSegment(session.credentials.password)
        .addPathSegment("$streamId.${extension.trimStart('.')}")
        .build()
        .toString()

    private fun deviceSnapshot(): DeviceSnapshot {
        return runCatching {
            val decoderTypes = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .asSequence()
                .filterNot { it.isEncoder }
                .flatMap { it.supportedTypes.asSequence() }
                .map { it.lowercase(Locale.ROOT) }
                .toSet()
            val video = listOf("video/avc", "video/hevc", "video/x-vnd.on2.vp9", "video/av01")
                .filter(decoderTypes::contains)
            val audio = listOf("audio/mp4a-latm", "audio/ac3", "audio/eac3", "audio/opus")
                .filter(decoderTypes::contains)
            DeviceSnapshot(
                supportedVideoMimes = video,
                dolbySupported = "audio/ac3" in audio || "audio/eac3" in audio,
                videoSummary = video.ifEmpty { listOf("لا توجد معلومات") }.joinToString("، ") { prettyMime(it) },
                audioSummary = audio.ifEmpty { listOf("لا توجد معلومات") }.joinToString("، ") { prettyMime(it) },
            )
        }.getOrElse {
            DeviceSnapshot(emptyList(), false, "تعذر قراءة Decoders الجهاز", "تعذر قراءة Decoders الصوت")
        }
    }

    private fun networkSnapshot(): NetworkSnapshot {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val metered = manager.isActiveNetworkMetered
        val transport = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "كيبل Ethernet"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "شبكة جوال"
            else -> "شبكة غير معروفة"
        }
        return NetworkSnapshot(validated, "$transport • ${if (validated) "متصل بالانترنت" else "غير موثق"} • ${if (metered) "محدود" else "غير محدود"}")
    }

    private fun buildRecommendations(capabilities: List<CapabilityFinding>): List<FeatureRecommendation> {
        fun status(id: String) = capabilities.firstOrNull { it.id == id }?.status
        val result = mutableListOf<FeatureRecommendation>()
        if (status("epg") == CapabilityStatus.SUPPORTED) {
            result += FeatureRecommendation("دليل برامج تلفزيوني كامل", "جاهزة للبناء", 1, "السيرفر يعيد بيانات EPG فعلية ويمكن تحويلها الى Grid زمني وتذكيرات.")
        } else {
            result += FeatureRecommendation("دليل برامج تلفزيوني كامل", "ينتظر تحسين EPG", 2, "الميزة تحتاج بيانات برامج مستقرة وربط صحيح للقنوات.")
        }
        if (status("catchup") == CapabilityStatus.SUPPORTED) {
            result += FeatureRecommendation("الاعادة التلفزيونية والارشيف", "جاهزة للبناء", 1, "السيرفر يعلن قنوات تدعم Catch-up.")
        }
        if (status("hls") == CapabilityStatus.SUPPORTED && status("mpeg_ts") == CapabilityStatus.SUPPORTED) {
            result += FeatureRecommendation("محرك تبديل تلقائي بين HLS وTS", "جاهزة للبناء", 1, "توفر مساران للبث يسمح بالتعافي التلقائي وتقليل التقطيع.")
        }
        if (status("download_resume") == CapabilityStatus.SUPPORTED) {
            result += FeatureRecommendation("مدير تحميلات احترافي واستكمال مضمون", "جاهزة للتوسعة", 1, "السيرفر يدعم Range ويمكن بناء تحقق سلامة وجدولة متقدمة.")
        }
        if (status("metadata") != CapabilityStatus.UNSUPPORTED && status("artwork") != CapabilityStatus.UNSUPPORTED) {
            result += FeatureRecommendation("صفحات اكتشاف ومجموعات ذكية", "جاهزة جزئيا", 2, "توفر البيانات والصور يسمح ببناء Collections وتوصيات اغنى.")
        }
        result += FeatureRecommendation("مراقبة جودة القنوات والتنبيه عن العطل", "جاهزة للبناء", 1, "محرك التشخيص الحالي يوفر اساس القياس الدوري بدون تشغيل كل القنوات.")
        result += FeatureRecommendation("رقابة ابوية وملفات شخصية", "جاهزة داخل التطبيق", 2, "لا تعتمد على قدرات Xtream ويمكن تنفيذها محليا ثم مزامنتها لاحقا.")
        result += FeatureRecommendation("مزامنة المشاهدة والمفضلة بين الاجهزة", "تحتاج Backend للمنصة", 2, "تحتاج حساب عميل وواجهة مزامنة خاصة بـ HULK SA.")
        result += FeatureRecommendation("تحليل مسارات الصوت والترجمة والجودة", "تحتاج فحص تشغيل عميق", 3, "يلزم قراءة Tracks من عينات فعلية اثناء التشغيل لتجنب نتائج تخمينية.")
        return result.sortedWith(compareBy(FeatureRecommendation::priority, FeatureRecommendation::title))
    }

    private fun catalogCapability(id: String, title: String, probe: JsonProbe, count: Int): CapabilityFinding = CapabilityFinding(
        id = id,
        title = title,
        status = when {
            probe.success && count > 0 -> CapabilityStatus.SUPPORTED
            probe.success -> CapabilityStatus.PARTIAL
            else -> CapabilityStatus.UNSTABLE
        },
        details = when {
            probe.success && count > 0 -> "تمت قراءة $count عنصر."
            probe.success -> "الواجهة تعمل لكن القائمة فارغة."
            else -> "تعذر قراءة القائمة."
        },
        evidence = "الاستجابة ${probe.latencyMs}ms",
    )

    private fun streamCapability(id: String, title: String, probe: StreamProbe?): CapabilityFinding = CapabilityFinding(
        id = id,
        title = title,
        status = when {
            probe == null -> CapabilityStatus.UNSUPPORTED
            probe.success && ((id == "hls" && probe.looksHls) || (id == "mpeg_ts" && probe.looksTs)) -> CapabilityStatus.SUPPORTED
            probe.success -> CapabilityStatus.PARTIAL
            else -> CapabilityStatus.UNSTABLE
        },
        details = when {
            probe == null -> "لا توجد عينة قناة للفحص."
            probe.success -> "استجابت عينة البث بكود ${probe.httpCode}."
            else -> "فشلت عينة البث: ${probe.errorMessage ?: "سبب غير معروف"}."
        },
        evidence = probe?.evidence() ?: "لا توجد عينة",
    )

    private data class JsonProbe(
        val success: Boolean,
        val latencyMs: Long,
        val httpCode: Int?,
        val objectValue: JSONObject?,
        val arrayValue: JSONArray?,
        val errorMessage: String?,
    ) {
        fun endpoint(name: String) = DiagnosticEndpoint(
            name = name,
            kind = "api",
            success = success,
            latencyMs = latencyMs,
            httpCode = httpCode,
            itemCount = arrayValue?.length(),
            details = errorMessage ?: if (arrayValue != null) "${arrayValue.length()} عنصر" else "استجابة JSON صالحة",
        )
    }

    private data class StreamProbe(
        val label: String,
        val success: Boolean,
        val latencyMs: Long,
        val httpCode: Int?,
        val contentType: String?,
        val bytesRead: Long,
        val supportsRange: Boolean,
        val looksHls: Boolean,
        val looksTs: Boolean,
        val megabitsPerSecond: Double,
        val errorMessage: String?,
    ) {
        fun toEndpoint() = DiagnosticEndpoint(
            name = label,
            kind = "stream",
            success = success,
            latencyMs = latencyMs,
            httpCode = httpCode,
            itemCount = null,
            details = evidence(),
        )

        fun evidence(): String = buildString {
            append(contentType ?: "نوع غير معروف")
            append(" • ")
            append(String.format(Locale.US, "%.2f Mbps", megabitsPerSecond))
            if (supportsRange) append(" • Range")
        }
    }

    private data class DeviceSnapshot(
        val supportedVideoMimes: List<String>,
        val dolbySupported: Boolean,
        val videoSummary: String,
        val audioSummary: String,
    )

    private data class NetworkSnapshot(val validated: Boolean, val summary: String)

    private fun DiagnosticEndpoint.successLabel(): String = if (success) "سليم" else "فشل"

    private fun StreamProbe?.successOrFalse(): Boolean = this?.success == true

    private fun JSONArray.firstObject(): JSONObject? = if (length() > 0) optJSONObject(0) else null

    private fun JSONArray.countObjects(predicate: (JSONObject) -> Boolean): Int {
        var count = 0
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            if (predicate(item)) count++
        }
        return count
    }

    private fun JSONArray.percentWith(key: String): Int = percentMatching { it.optString(key).isNotBlank() }

    private fun JSONArray.percentMatching(predicate: (JSONObject) -> Boolean): Int {
        if (length() == 0) return 0
        return (countObjects(predicate) * 100.0 / length().toDouble()).roundToInt()
    }

    private fun JSONObject.firstEpisode(): Pair<Int, String>? {
        val episodes = optJSONObject("episodes") ?: return null
        val keys = episodes.keys()
        while (keys.hasNext()) {
            val seasonKey = keys.next()
            val list = episodes.optJSONArray(seasonKey) ?: continue
            val first = list.firstObject() ?: continue
            val id = first.optString("id").toIntOrNull() ?: continue
            val extension = first.optString("container_extension", "mp4").ifBlank { "mp4" }
            return id to extension
        }
        return null
    }

    private fun weightedPercent(vararg values: Pair<Int, Int>): Int {
        val total = values.sumOf { it.second }
        if (total <= 0) return 0
        return (values.sumOf { it.first.toDouble() * it.second.toDouble() } / total.toDouble()).roundToInt()
    }

    private fun yesNo(value: Boolean?): String = if (value == true) "نعم" else "لا"

    private fun prettyMime(mime: String): String = when (mime) {
        "video/avc" -> "H.264"
        "video/hevc" -> "HEVC"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/av01" -> "AV1"
        "audio/mp4a-latm" -> "AAC"
        "audio/ac3" -> "AC3"
        "audio/eac3" -> "E-AC3"
        "audio/opus" -> "Opus"
        else -> mime.substringAfter('/')
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
