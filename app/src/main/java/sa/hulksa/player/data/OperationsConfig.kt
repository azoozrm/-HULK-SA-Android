package sa.hulksa.player.data

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

const val OPERATIONS_CACHE_TTL_MS = 15L * 60L * 1_000L
const val OPERATIONS_MAX_FORCED_CACHE_AGE_MS = 24L * 60L * 60L * 1_000L

enum class OperationsConfigSource {
    NETWORK,
    CACHE,
    DEFAULT,
}

enum class OperationsServiceStatus {
    OPERATIONAL,
    DEGRADED,
    MAINTENANCE,
}

enum class OperationsAnnouncementSeverity {
    INFO,
    WARNING,
    IMPORTANT,
}

enum class OperationsAnnouncementTarget {
    ALL,
    MOBILE,
    TV,
}

enum class OperationsFeatureFlag(val wireName: String) {
    DOWNLOADS("downloads_enabled"),
    EPISODE_NOTIFICATIONS("episode_notifications_enabled"),
    SMART_RECOMMENDATIONS("smart_recommendations_enabled"),
    LIVE_TV_PRO("live_tv_pro_enabled"),
}

data class OperationsFeatureFlags(
    val downloadsEnabled: Boolean = true,
    val episodeNotificationsEnabled: Boolean = true,
    val smartRecommendationsEnabled: Boolean = true,
    val liveTvProEnabled: Boolean = true,
) {
    fun enabled(flag: OperationsFeatureFlag): Boolean = when (flag) {
        OperationsFeatureFlag.DOWNLOADS -> downloadsEnabled
        OperationsFeatureFlag.EPISODE_NOTIFICATIONS -> episodeNotificationsEnabled
        OperationsFeatureFlag.SMART_RECOMMENDATIONS -> smartRecommendationsEnabled
        OperationsFeatureFlag.LIVE_TV_PRO -> liveTvProEnabled
    }

    companion object {
        fun fromRemote(remote: Map<String, Boolean>): OperationsFeatureFlags = OperationsFeatureFlags(
            downloadsEnabled = remote[OperationsFeatureFlag.DOWNLOADS.wireName] ?: true,
            episodeNotificationsEnabled = remote[OperationsFeatureFlag.EPISODE_NOTIFICATIONS.wireName] ?: true,
            smartRecommendationsEnabled = remote[OperationsFeatureFlag.SMART_RECOMMENDATIONS.wireName] ?: true,
            liveTvProEnabled = remote[OperationsFeatureFlag.LIVE_TV_PRO.wireName] ?: true,
        )
    }
}

data class OperationsServiceConfig(
    val status: OperationsServiceStatus = OperationsServiceStatus.OPERATIONAL,
    val message: String? = null,
    val startsAtEpochSeconds: Long? = null,
    val estimatedEndAtEpochSeconds: Long? = null,
)

data class OperationsUpdateConfig(
    val latestVersionCode: Int = 64,
    val latestVersionName: String = "0.9.3.20",
    val minimumSupportedVersionCode: Int = 64,
    val updateType: String = "OPTIONAL",
    val apkUrl: String? = null,
    val apkSha256: String? = null,
    val releaseNotes: String = "",
)

data class OperationsAnnouncement(
    val id: String,
    val enabled: Boolean,
    val title: String,
    val message: String,
    val severity: OperationsAnnouncementSeverity,
    val target: OperationsAnnouncementTarget,
    val showOnce: Boolean,
    val persistent: Boolean,
    val minimumVersionCode: Int?,
    val maximumVersionCode: Int?,
    val startsAtEpochSeconds: Long,
    val endsAtEpochSeconds: Long?,
)

data class OperationsConfig(
    val schemaVersion: Int,
    val generatedAtEpochSeconds: Long,
    val service: OperationsServiceConfig,
    val update: OperationsUpdateConfig,
    val announcements: List<OperationsAnnouncement>,
    val features: OperationsFeatureFlags,
    val growth: OperationsGrowthConfig = OperationsGrowthConfig(),
)

data class CachedOperationsConfig(
    val config: OperationsConfig,
    val rawJson: String,
    val fetchedAtEpochMs: Long,
)

enum class OperationsUpdateDecision {
    NONE,
    OPTIONAL,
    REQUIRED,
}

enum class OperationsDownloadStatus {
    IDLE,
    DOWNLOADING,
    INSTALLER_OPENED,
    UNKNOWN_SOURCES_BLOCKED,
    FAILED,
}

data class OperationsDownloadUiState(
    val status: OperationsDownloadStatus = OperationsDownloadStatus.IDLE,
    val progressPercent: Int? = null,
    val message: String? = null,
)

data class OperationsUiState(
    val source: OperationsConfigSource = OperationsConfigSource.DEFAULT,
    val updateDecision: OperationsUpdateDecision = OperationsUpdateDecision.NONE,
    val update: OperationsUpdateConfig = OperationsUpdateConfig(),
    val service: OperationsServiceConfig = OperationsServiceConfig(),
    val features: OperationsFeatureFlags = OperationsFeatureFlags(),
    val growth: OperationsGrowthConfig = OperationsGrowthConfig(),
    val announcementPopup: OperationsAnnouncement? = null,
    val persistentAnnouncement: OperationsAnnouncement? = null,
    val download: OperationsDownloadUiState = OperationsDownloadUiState(),
)

fun evaluateOperationsUpdatePolicy(
    currentVersionCode: Int,
    update: OperationsUpdateConfig?,
    source: OperationsConfigSource,
    cacheAgeMs: Long,
): OperationsUpdateDecision {
    if (update == null || currentVersionCode <= 0) return OperationsUpdateDecision.NONE
    if (update.latestVersionCode <= currentVersionCode) return OperationsUpdateDecision.NONE
    if (!isInstallableOperationsUpdate(update)) return OperationsUpdateDecision.NONE

    val requiresUpdate = currentVersionCode < update.minimumSupportedVersionCode
    if (!requiresUpdate) return OperationsUpdateDecision.OPTIONAL

    val forcedConfigIsTrusted = source == OperationsConfigSource.NETWORK ||
        (source == OperationsConfigSource.CACHE && cacheAgeMs in 0..OPERATIONS_MAX_FORCED_CACHE_AGE_MS)
    return if (forcedConfigIsTrusted) {
        OperationsUpdateDecision.REQUIRED
    } else {
        OperationsUpdateDecision.OPTIONAL
    }
}

fun effectiveOperationsServiceStatus(
    service: OperationsServiceConfig?,
    source: OperationsConfigSource,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L,
): OperationsServiceStatus {
    val status = service?.status ?: return OperationsServiceStatus.OPERATIONAL
    if (
        status != OperationsServiceStatus.OPERATIONAL &&
        service.startsAtEpochSeconds?.let { it > nowEpochSeconds } == true
    ) {
        return OperationsServiceStatus.OPERATIONAL
    }
    return if (status == OperationsServiceStatus.MAINTENANCE && source != OperationsConfigSource.NETWORK) {
        OperationsServiceStatus.OPERATIONAL
    } else {
        status
    }
}

fun isInstallableOperationsUpdate(update: OperationsUpdateConfig): Boolean {
    val url = update.apkUrl?.trim().orEmpty()
    val sha = update.apkSha256?.trim()?.lowercase().orEmpty()
    val parsed = runCatching { URI(url) }.getOrNull()
    return parsed != null &&
        parsed.scheme.equals("https", ignoreCase = true) &&
        parsed.host.equals("hulksa.com", ignoreCase = true) &&
        (parsed.port == -1 || parsed.port == 443) &&
        parsed.userInfo == null &&
        parsed.query == null &&
        parsed.fragment == null &&
        parsed.path?.startsWith("/hulk-operations/releases/") == true &&
        parsed.path?.lowercase()?.endsWith(".apk") == true &&
        sha.matches(Regex("[a-f0-9]{64}"))
}

fun eligibleOperationsAnnouncements(
    announcements: List<OperationsAnnouncement>,
    currentVersionCode: Int,
    isTv: Boolean,
    nowEpochSeconds: Long,
    acknowledgedMessageIds: Set<String>,
    presentedMessageIds: Set<String>,
): List<OperationsAnnouncement> = announcements.asSequence()
    .filter(OperationsAnnouncement::enabled)
    .filter { it.startsAtEpochSeconds <= nowEpochSeconds }
    .filter { it.endsAtEpochSeconds == null || nowEpochSeconds < it.endsAtEpochSeconds }
    .filter { it.minimumVersionCode == null || currentVersionCode >= it.minimumVersionCode }
    .filter { it.maximumVersionCode == null || currentVersionCode <= it.maximumVersionCode }
    .filter {
        it.target == OperationsAnnouncementTarget.ALL ||
            (isTv && it.target == OperationsAnnouncementTarget.TV) ||
            (!isTv && it.target == OperationsAnnouncementTarget.MOBILE)
    }
    .filter { !it.showOnce || it.id !in acknowledgedMessageIds }
    .filter { it.id !in presentedMessageIds }
    .distinctBy(OperationsAnnouncement::id)
    .sortedWith(
        compareByDescending<OperationsAnnouncement> { it.severity.ordinal }
            .thenByDescending(OperationsAnnouncement::startsAtEpochSeconds),
    )
    .toList()

fun activePersistentOperationsAnnouncement(
    announcements: List<OperationsAnnouncement>,
    currentVersionCode: Int,
    isTv: Boolean,
    nowEpochSeconds: Long,
): OperationsAnnouncement? = eligibleOperationsAnnouncements(
    announcements = announcements.filter(OperationsAnnouncement::persistent),
    currentVersionCode = currentVersionCode,
    isTv = isTv,
    nowEpochSeconds = nowEpochSeconds,
    acknowledgedMessageIds = emptySet(),
    presentedMessageIds = emptySet(),
).firstOrNull()

fun parseOperationsConfig(rawJson: String): OperationsConfig? = runCatching {
    val root = JSONObject(rawJson)
    val schemaVersion = root.getInt("schemaVersion")
    require(schemaVersion == 1)
    val generatedAt = root.getLong("generatedAt")
    require(generatedAt > 0L)

    val serviceObject = root.getJSONObject("service")
    val serviceStartsAt = serviceObject.nullableLong("startsAt")
    val serviceEstimatedEndAt = serviceObject.nullableLong("estimatedEndAt")
    require(serviceStartsAt == null || serviceStartsAt >= 0L)
    require(serviceEstimatedEndAt == null || serviceEstimatedEndAt >= 0L)
    val service = OperationsServiceConfig(
        status = OperationsServiceStatus.valueOf(serviceObject.getString("status").uppercase()),
        message = serviceObject.nullableString("message")?.take(2_000),
        startsAtEpochSeconds = serviceStartsAt,
        estimatedEndAtEpochSeconds = serviceEstimatedEndAt,
    )

    val updateObject = root.getJSONObject("update")
    val latestVersionCode = updateObject.getInt("latestVersionCode")
    val minimumSupportedVersionCode = updateObject.getInt("minimumSupportedVersionCode")
    require(latestVersionCode > 0)
    require(minimumSupportedVersionCode > 0)
    require(minimumSupportedVersionCode <= latestVersionCode)
    val apkUrl = updateObject.nullableString("apkUrl")
    val apkSha256 = updateObject.nullableString("apkSha256")?.lowercase()
    require((apkUrl == null) == (apkSha256 == null))
    val latestVersionName = updateObject.getString("latestVersionName").trim()
    require(latestVersionName.isNotEmpty() && latestVersionName.length <= 32)
    val updateType = updateObject.optString(
        "updateType",
        if (updateObject.optBoolean("required", false)) "REQUIRED" else "OPTIONAL",
    ).uppercase()
    require(updateType == "OPTIONAL" || updateType == "REQUIRED")
    val update = OperationsUpdateConfig(
        latestVersionCode = latestVersionCode,
        latestVersionName = latestVersionName,
        minimumSupportedVersionCode = minimumSupportedVersionCode,
        updateType = updateType,
        apkUrl = apkUrl,
        apkSha256 = apkSha256,
        releaseNotes = updateObject.optString("releaseNotes", "").take(10_000),
    )
    if (apkUrl != null) require(isInstallableOperationsUpdate(update))

    val announcementObjects = when {
        root.optJSONArray("announcements") != null -> root.getJSONArray("announcements")
        root.optJSONObject("announcement") != null -> JSONArray().put(root.getJSONObject("announcement"))
        else -> JSONArray()
    }
    val announcements = buildList {
        for (index in 0 until announcementObjects.length()) {
            parseOperationsAnnouncement(announcementObjects.optJSONObject(index))?.let(::add)
        }
    }.distinctBy(OperationsAnnouncement::id)

    val featureObject = root.optJSONObject("features")
    val remoteFeatures = buildMap {
        if (featureObject != null) {
            OperationsFeatureFlag.entries.forEach { flag ->
                if (featureObject.has(flag.wireName) && featureObject.get(flag.wireName) is Boolean) {
                    put(flag.wireName, featureObject.getBoolean(flag.wireName))
                }
            }
        }
    }
    val growth = parseOperationsGrowth(root.optJSONObject("growth"))

    OperationsConfig(
        schemaVersion = schemaVersion,
        generatedAtEpochSeconds = generatedAt,
        service = service,
        update = update,
        announcements = announcements,
        features = OperationsFeatureFlags.fromRemote(remoteFeatures),
        growth = growth,
    )
}.getOrNull()

private fun parseOperationsAnnouncement(value: JSONObject?): OperationsAnnouncement? = runCatching {
    requireNotNull(value)
    val id = value.getString("id").trim()
    val title = value.getString("title").trim()
    val message = value.getString("message").trim()
    require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{2,79}")))
    require(title.isNotEmpty() && title.length <= 160)
    require(message.isNotEmpty() && message.length <= 10_000)
    val minimum = value.nullableInt("minimumVersionCode")
    val maximum = value.nullableInt("maximumVersionCode")
    require(minimum == null || minimum > 0)
    require(maximum == null || maximum > 0)
    require(minimum == null || maximum == null || minimum <= maximum)
    val startsAt = value.optLong("startsAt", 0L)
    val endsAt = value.nullableLong("endsAt")
    require(startsAt >= 0L)
    require(endsAt == null || endsAt > startsAt)

    OperationsAnnouncement(
        id = id,
        enabled = value.optBoolean("enabled", false),
        title = title,
        message = message,
        severity = OperationsAnnouncementSeverity.valueOf(
            value.optString("severity", "INFO").uppercase(),
        ),
        target = OperationsAnnouncementTarget.valueOf(
            value.optString("target", "ALL").uppercase(),
        ),
        showOnce = value.optBoolean("showOnce", true),
        persistent = value.optBoolean("persistent", false),
        minimumVersionCode = minimum,
        maximumVersionCode = maximum,
        startsAtEpochSeconds = startsAt,
        endsAtEpochSeconds = endsAt,
    )
}.getOrNull()

private fun JSONObject.nullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf(String::isNotEmpty)
}

private fun JSONObject.nullableLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    val value = optLong(key, Long.MIN_VALUE)
    return value.takeUnless { it == Long.MIN_VALUE }
}

private fun JSONObject.nullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val value = optInt(key, Int.MIN_VALUE)
    return value.takeUnless { it == Int.MIN_VALUE }
}
