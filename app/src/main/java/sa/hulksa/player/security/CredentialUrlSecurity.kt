package sa.hulksa.player.security

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private val XTREAM_QUERY_CREDENTIAL = Regex(
    """(?i)(?:^|[?&])(?:username|password)\s*=""",
)

/**
 * Strict URL-shape detector used for persistence/publication decisions.
 *
 * Xtream media paths carry three segments after the media kind:
 * username / password / numeric stream-or-episode resource. Merely using
 * "movie", "series" or "live" as a normal CDN route must not classify an
 * artwork URL as credential-bearing.
 */
private val XTREAM_MEDIA_URL_PATH_CREDENTIALS = Regex(
    """(?i)/(?:live|movie|series)/[^/?#]+/[^/?#]+/\d+(?:\.[^/?#]+)?(?:[?#]|$)""",
)

/**
 * Broader text pattern retained for defensive error/diagnostic redaction. Text
 * may be incomplete or embedded in a larger message, so this intentionally
 * remains more conservative than the external-URL persistence classifier.
 */
private val XTREAM_TEXT_PATH_CREDENTIALS = Regex(
    """(?i)/(?:live|movie|series)/[^/?#]+/[^/?#]+(?:/|$)""",
)

/**
 * Detects credential-bearing Xtream URLs for persistence/publication guards.
 * Query credentials remain sensitive immediately; media-path detection requires
 * the full kind/username/password/resource shape. Inspection decodes percent
 * encoding once so encoded credential names and path separators cannot bypass
 * the guard.
 */
internal fun isCredentialBearingIptvUrl(raw: String?): Boolean {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return false
    return credentialInspectionVariants(value).any { candidate ->
        XTREAM_QUERY_CREDENTIAL.containsMatchIn(candidate) ||
            XTREAM_MEDIA_URL_PATH_CREDENTIALS.containsMatchIn(candidate)
    }
}

/** Broader text guard used before writing diagnostic/error metadata. */
internal fun containsCredentialBearingIptvMaterial(raw: String?): Boolean {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return false
    return credentialInspectionVariants(value).any { candidate ->
        XTREAM_QUERY_CREDENTIAL.containsMatchIn(candidate) ||
            XTREAM_TEXT_PATH_CREDENTIALS.containsMatchIn(candidate) ||
            candidate.lowercase(Locale.ROOT).let { lower ->
                "username=" in lower && "password=" in lower
            }
    }
}

internal fun redactCredentialBearingUrl(raw: String?): String? {
    if (raw == null) return null
    return if (containsCredentialBearingIptvMaterial(raw)) REDACTED_IPTV_URL else raw
}

/**
 * External artwork/metadata URLs may be persisted, but never when they use the
 * strict Xtream credential-bearing URL forms. Relative/non-network strings are
 * left to their existing callers to validate.
 */
internal fun persistableExternalUrlOrNull(raw: String?): String? = raw
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.takeUnless(::isCredentialBearingIptvUrl)

private fun credentialInspectionVariants(value: String): Sequence<String> = sequence {
    yield(value)
    val decoded = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()
    if (decoded != null && decoded != value) yield(decoded)
}

internal const val REDACTED_IPTV_URL = "[REDACTED_IPTV_URL]"
