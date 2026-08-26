package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.security.containsCredentialBearingIptvMaterial
import sa.hulksa.player.security.persistableExternalUrlOrNull
import sa.hulksa.player.security.redactCredentialBearingUrl
import java.io.File

private const val LEGACY_DOWNLOAD_SOURCE_CANDIDATES = "sourceCandidates"
private const val LEGACY_DOWNLOAD_SOURCE_URL = "sourceUrl"
private const val LEGACY_DOWNLOAD_FULL_SOURCE_URL = "fullSourceUrl"
private const val LEGACY_DOWNLOAD_MEDIA_URL = "mediaUrl"
private val DOWNLOAD_SOURCE_KEYS = setOf(
    LEGACY_DOWNLOAD_SOURCE_CANDIDATES,
    LEGACY_DOWNLOAD_SOURCE_URL,
    LEGACY_DOWNLOAD_FULL_SOURCE_URL,
    LEGACY_DOWNLOAD_MEDIA_URL,
)
private val PERSISTED_METADATA_URL_FIELDS = setOf("poster", "posterUrl", "poster_url")

/**
 * Removes long-lived transport URLs from download metadata while retaining the
 * stable content identity and the physical-file metadata needed for resume.
 * Invalid legacy JSON that visibly contains credentials is invalidated closed;
 * this never deletes the physical downloaded/partial files themselves.
 */
internal fun sanitizePersistedDownloadJson(raw: String?): String? {
    if (raw.isNullOrBlank()) return raw
    return runCatching {
        val array = JSONArray(raw)
        var changed = false
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            DOWNLOAD_SOURCE_KEYS.forEach { key ->
                if (item.has(key)) {
                    item.remove(key)
                    changed = true
                }
            }
            val poster = item.optString("posterUrl").takeIf(String::isNotBlank)
            if (poster != null && persistableExternalUrlOrNull(poster) == null) {
                item.put("posterUrl", JSONObject.NULL)
                changed = true
            }
            val error = item.optString("errorMessage").takeIf(String::isNotBlank)
            if (error != null && containsCredentialBearingIptvMaterial(error)) {
                item.put("errorMessage", redactCredentialBearingUrl(error) ?: JSONObject.NULL)
                changed = true
            }
        }
        if (changed) array.toString() else raw
    }.getOrElse {
        if (containsCredentialBearingIptvMaterial(raw)) "[]" else raw
    }
}

/**
 * Runtime-only reconstruction from the authenticated owner and stable download
 * identity. Nothing returned here is written back to SharedPreferences.
 */
internal fun downloadRuntimeSourceCandidates(
    record: OfflineDownload,
    expectedAccountId: String,
    session: AuthenticatedSession?,
    metadata: AccountSessionMetadata?,
): List<String> {
    val activeSession = session ?: return emptyList()
    val normalizedAccountId = expectedAccountId.trim().takeIf(String::isNotEmpty) ?: return emptyList()
    if (authenticatedDownloadAccountId(activeSession, metadata) != normalizedAccountId) return emptyList()

    val kind = record.streamKind.trim().lowercase()
    if (kind != "movie" && kind != "series") return emptyList()
    if (record.streamId <= 0) return emptyList()
    val extension = record.extension
        .trim()
        .trimStart('.')
        .lowercase()
        .filter { it.isLetterOrDigit() }
        .take(8)
        .ifBlank { "mp4" }
    val base = activeSession.portal.baseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return emptyList()
    if (base.scheme != "http" && base.scheme != "https") return emptyList()

    return listOf(
        base.newBuilder()
            .addPathSegment(kind)
            .addPathSegment(activeSession.credentials.username)
            .addPathSegment(activeSession.credentials.password)
            .addPathSegment("${record.streamId}.$extension")
            .build()
            .toString(),
    )
}

/**
 * Process-start migration for old global and account-scoped stores. Download
 * source URLs are removed and credential-bearing artwork metadata is scrubbed
 * across inactive account namespaces as well as the active account.
 */
internal fun scrubPersistedDownloadCredentialUrls(context: Context) {
    val appContext = context.applicationContext
    val sharedPrefsDirectory = File(appContext.applicationInfo.dataDir, "shared_prefs")
    val existingNames = sharedPrefsDirectory.listFiles().orEmpty()
        .map { it.name.removeSuffix(".xml") }
        .toSet()

    val downloadBase = DurableDownloadPreferenceStore.PREFERENCES_NAME
    val downloadNames = existingNames.filterTo(linkedSetOf()) {
        it == downloadBase || it.startsWith("$downloadBase.account.")
    }.apply { add(downloadBase) }
    downloadNames.forEach { name ->
        val preferences = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        val raw = preferences.getString(DurableDownloadPreferenceStore.KEY_DOWNLOADS, null)
        val sanitized = sanitizePersistedDownloadJson(raw)
        if (sanitized != raw) {
            preferences.edit()
                .putString(DurableDownloadPreferenceStore.KEY_DOWNLOADS, sanitized)
                .commit()
        }
    }

    existingNames
        .filter { name ->
            name == "hulk_user_library" ||
                name.startsWith("hulk_user_library.account.") ||
                name == "hulk_profile_content_search_history_v2" ||
                name.startsWith("hulk_profile_content_search_history_v2.account.")
        }
        .forEach { name ->
            scrubCredentialBearingMetadataPreferences(
                appContext.getSharedPreferences(name, Context.MODE_PRIVATE),
            )
        }
}

private fun scrubCredentialBearingMetadataPreferences(preferences: SharedPreferences) {
    val replacements = mutableMapOf<String, String>()
    preferences.all.forEach { (key, value) ->
        val raw = value as? String ?: return@forEach
        val sanitized = sanitizePersistedMetadataJson(raw)
        if (sanitized != raw) replacements[key] = sanitized
    }
    if (replacements.isEmpty()) return
    val editor = preferences.edit()
    replacements.forEach { (key, value) -> editor.putString(key, value) }
    editor.commit()
}

private fun sanitizePersistedMetadataJson(raw: String): String = runCatching {
    val array = JSONArray(raw)
    var changed = false
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        for (key in PERSISTED_METADATA_URL_FIELDS) {
            val value = item.optString(key).takeIf(String::isNotBlank) ?: continue
            if (persistableExternalUrlOrNull(value) == null) {
                item.put(key, "")
                changed = true
            }
        }
    }
    if (changed) array.toString() else raw
}.getOrElse {
    if (containsCredentialBearingIptvMaterial(raw)) "[]" else raw
}
