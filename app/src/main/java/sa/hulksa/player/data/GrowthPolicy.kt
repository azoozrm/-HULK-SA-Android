package sa.hulksa.player.data

import java.net.URI
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

const val GROWTH_RENEWAL_BANNER_MIN_DAYS = 1
const val GROWTH_RENEWAL_BANNER_MAX_DAYS = 30
const val GROWTH_RENEWAL_BANNER_DEFAULT_DAYS = 7
private const val GROWTH_MAX_EPOCH_SECONDS = 253_402_300_799L

enum class GrowthDestination {
    RENEWAL,
    SUPPORT,
}

enum class GrowthQrMode {
    AUTO,
    CUSTOM,
}

enum class GrowthAction {
    OPEN_QR,
    OPEN_URL,
    NO_ACTION,
}

enum class GrowthQrPresentation {
    GENERATED_QR,
    CUSTOM_IMAGE,
}

data class OperationsGrowthLinkConfig(
    val enabled: Boolean = false,
    val title: String = "",
    val url: String? = null,
    val displayText: String = "",
    val qrMode: GrowthQrMode = GrowthQrMode.AUTO,
    val customQrUrl: String? = null,
)

data class OperationsRenewalBannerConfig(
    val enabled: Boolean = false,
    val daysBeforeExpiry: Int = GROWTH_RENEWAL_BANNER_DEFAULT_DAYS,
)

data class OperationsGrowthConfig(
    val enabled: Boolean = false,
    val renewal: OperationsGrowthLinkConfig = OperationsGrowthLinkConfig(
        title = "التجديد والموقع",
        displayText = "hulksa.com",
    ),
    val support: OperationsGrowthLinkConfig = OperationsGrowthLinkConfig(
        title = "الدعم الفني",
    ),
    val renewalBanner: OperationsRenewalBannerConfig = OperationsRenewalBannerConfig(),
)

data class RenewalBannerContent(
    val daysRemaining: Int,
    val title: String,
    val subtitle: String,
)

fun OperationsGrowthConfig.link(destination: GrowthDestination): OperationsGrowthLinkConfig =
    when (destination) {
        GrowthDestination.RENEWAL -> renewal
        GrowthDestination.SUPPORT -> support
    }

fun normalizeGrowthRenewalUrl(value: String?): String? {
    val candidate = value?.trim()?.takeIf { it.length in 1..2_048 } ?: return null
    val uri = strictHttpsUri(candidate) ?: return null
    val host = uri.host.lowercase(Locale.ROOT)
    if (host != "hulksa.com" && !host.endsWith(".hulksa.com")) return null
    if (uri.rawQuery != null || uri.rawFragment != null) return null
    return candidate
}

fun normalizeGrowthSupportUrl(value: String?): String? {
    val candidate = value?.trim()?.takeIf { it.length in 1..2_048 } ?: return null
    val uri = strictHttpsUri(candidate) ?: return null
    if (uri.rawFragment != null) return null

    val host = uri.host.lowercase(Locale.ROOT)
    val path = uri.rawPath.orEmpty()
    return when {
        host == "wa.me" -> candidate.takeIf {
            uri.rawQuery == null && path.matches(Regex("/[1-9][0-9]{6,14}"))
        }

        host == "whatsapp.com" || host.endsWith(".whatsapp.com") -> candidate.takeIf {
            path == "/send" && isSafeWhatsAppQuery(uri.rawQuery)
        }

        else -> null
    }
}

fun normalizeGrowthCustomQrUrl(value: String?): String? {
    val candidate = value?.trim()?.takeIf { it.length in 1..1_024 } ?: return null
    val uri = strictHttpsUri(candidate) ?: return null
    if (!uri.host.equals("hulksa.com", ignoreCase = true)) return null
    if (uri.rawQuery != null || uri.rawFragment != null) return null
    val path = uri.rawPath.orEmpty()
    return candidate.takeIf {
        path.matches(
            Regex(
                "/hulk-operations/growth-media/" +
                    "growth-(renewal|support)-[a-f0-9]{32}\\.(png|webp)",
                RegexOption.IGNORE_CASE,
            ),
        )
    }
}

fun isGrowthLinkUsable(
    destination: GrowthDestination,
    link: OperationsGrowthLinkConfig,
): Boolean {
    if (!link.enabled) return false
    return when (destination) {
        GrowthDestination.RENEWAL -> normalizeGrowthRenewalUrl(link.url) != null
        GrowthDestination.SUPPORT -> normalizeGrowthSupportUrl(link.url) != null
    }
}

fun resolveGrowthAction(
    growth: OperationsGrowthConfig,
    destination: GrowthDestination,
    isTv: Boolean,
): GrowthAction {
    if (!growth.enabled || !isGrowthLinkUsable(destination, growth.link(destination))) {
        return GrowthAction.NO_ACTION
    }
    return if (isTv) GrowthAction.OPEN_QR else GrowthAction.OPEN_URL
}

fun resolveGrowthQrPresentation(
    link: OperationsGrowthLinkConfig,
    customImageFailed: Boolean = false,
): GrowthQrPresentation = if (
    link.qrMode == GrowthQrMode.CUSTOM &&
    !customImageFailed &&
    normalizeGrowthCustomQrUrl(link.customQrUrl) != null
) {
    GrowthQrPresentation.CUSTOM_IMAGE
} else {
    GrowthQrPresentation.GENERATED_QR
}

fun evaluateRenewalBanner(
    growth: OperationsGrowthConfig,
    expiresAtEpochSeconds: Long?,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L,
    timeZone: TimeZone = TimeZone.getDefault(),
): RenewalBannerContent? {
    if (
        !growth.enabled ||
        !growth.renewalBanner.enabled ||
        !isGrowthLinkUsable(GrowthDestination.RENEWAL, growth.renewal)
    ) {
        return null
    }
    val expiry = expiresAtEpochSeconds
        ?.takeIf { it in 1L..GROWTH_MAX_EPOCH_SECONDS }
        ?: return null
    if (nowEpochSeconds !in 1L..GROWTH_MAX_EPOCH_SECONDS) return null

    val remainingDays = if (expiry < nowEpochSeconds) {
        -1L
    } else {
        localEpochDay(expiry, timeZone) - localEpochDay(nowEpochSeconds, timeZone)
    }
    val safeRemainingDays = remainingDays.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    val threshold = growth.renewalBanner.daysBeforeExpiry.coerceIn(
        GROWTH_RENEWAL_BANNER_MIN_DAYS,
        GROWTH_RENEWAL_BANNER_MAX_DAYS,
    )
    if (safeRemainingDays > threshold) return null

    val title = when (safeRemainingDays) {
        in 3..Int.MAX_VALUE -> "اشتراكك ينتهي خلال $safeRemainingDays أيام"
        2 -> "اشتراكك ينتهي خلال يومين"
        1 -> "اشتراكك ينتهي غدًا"
        0 -> "اشتراكك ينتهي اليوم"
        else -> "انتهى اشتراكك"
    }
    val subtitle = when {
        safeRemainingDays >= 2 -> "جدد اشتراكك بسهولة من جوالك"
        safeRemainingDays >= 0 -> "جدد اشتراكك الآن"
        else -> "جدد اشتراكك للمتابعة"
    }
    return RenewalBannerContent(safeRemainingDays, title, subtitle)
}

internal fun parseOperationsGrowth(value: JSONObject?): OperationsGrowthConfig {
    value ?: return OperationsGrowthConfig()
    val renewal = parseGrowthLink(value.optJSONObject("renewal"), GrowthDestination.RENEWAL)
    val support = parseGrowthLink(value.optJSONObject("support"), GrowthDestination.SUPPORT)
    val bannerObject = value.optJSONObject("renewalBanner")
    val banner = OperationsRenewalBannerConfig(
        enabled = bannerObject?.optBoolean("enabled", false) == true,
        daysBeforeExpiry = bannerObject
            ?.optInt("daysBeforeExpiry", GROWTH_RENEWAL_BANNER_DEFAULT_DAYS)
            ?.coerceIn(GROWTH_RENEWAL_BANNER_MIN_DAYS, GROWTH_RENEWAL_BANNER_MAX_DAYS)
            ?: GROWTH_RENEWAL_BANNER_DEFAULT_DAYS,
    )
    return OperationsGrowthConfig(
        enabled = value.optBoolean("enabled", false),
        renewal = renewal,
        support = support,
        renewalBanner = banner,
    )
}

private fun parseGrowthLink(
    value: JSONObject?,
    destination: GrowthDestination,
): OperationsGrowthLinkConfig {
    val defaultTitle = when (destination) {
        GrowthDestination.RENEWAL -> "التجديد والموقع"
        GrowthDestination.SUPPORT -> "الدعم الفني"
    }
    value ?: return OperationsGrowthLinkConfig(title = defaultTitle)

    return runCatching {
        val rawUrl = value.optString("url", "").trim()
        val normalizedUrl = when (destination) {
            GrowthDestination.RENEWAL -> normalizeGrowthRenewalUrl(rawUrl)
            GrowthDestination.SUPPORT -> normalizeGrowthSupportUrl(rawUrl)
        }
        val title = value.optString("title", defaultTitle).trim()
            .takeIf { it.isNotEmpty() && it.length <= 80 }
            ?: defaultTitle
        val displayText = value.optString("displayText", "").trim()
            .takeIf { it.isNotEmpty() && it.length <= 80 }
            .orEmpty()
        val mode = runCatching {
            GrowthQrMode.valueOf(value.optString("qrMode", GrowthQrMode.AUTO.name).uppercase())
        }.getOrDefault(GrowthQrMode.AUTO)

        OperationsGrowthLinkConfig(
            enabled = value.optBoolean("enabled", false) && normalizedUrl != null,
            title = title,
            url = normalizedUrl,
            displayText = displayText,
            qrMode = mode,
            customQrUrl = normalizeGrowthCustomQrUrl(value.optString("customQrUrl", "")),
        )
    }.getOrDefault(OperationsGrowthLinkConfig(title = defaultTitle))
}

private fun strictHttpsUri(value: String): URI? {
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (
        uri.isOpaque ||
        !uri.scheme.equals("https", ignoreCase = true) ||
        uri.host.isNullOrBlank() ||
        uri.userInfo != null ||
        (uri.port != -1 && uri.port != 443)
    ) {
        return null
    }
    return uri
}

private fun isSafeWhatsAppQuery(rawQuery: String?): Boolean {
    val query = rawQuery?.takeIf { it.isNotBlank() && it.length <= 1_024 } ?: return false
    val values = mutableMapOf<String, String>()
    for (part in query.split('&')) {
        val separator = part.indexOf('=')
        if (separator <= 0) return false
        val key = part.substring(0, separator).lowercase(Locale.ROOT)
        val rawValue = part.substring(separator + 1)
        if (key !in setOf("phone", "app_absent") || key in values) return false
        values[key] = rawValue
    }
    val phone = values["phone"] ?: return false
    if (!phone.matches(Regex("[1-9][0-9]{6,14}"))) return false
    return values["app_absent"]?.let { it == "0" || it == "1" } != false
}

private fun localEpochDay(epochSeconds: Long, timeZone: TimeZone): Long {
    val millis = epochSeconds * 1_000L
    val offsetSeconds = timeZone.getOffset(millis) / 1_000L
    return Math.floorDiv(epochSeconds + offsetSeconds, 86_400L)
}
