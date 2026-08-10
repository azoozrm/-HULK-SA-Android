package sa.hulksa.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.model.AuthenticatedSession
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SeriesCardTechnicalMetadata(
    val quality: String? = null,
    val seasonCount: Int? = null,
)

class SeriesCardMetadataClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun fetch(
        session: AuthenticatedSession,
        seriesId: Int,
    ): SeriesCardTechnicalMetadata = withContext(Dispatchers.IO) {
        val url = (session.portal.baseUrl.trimEnd('/') + "/player_api.php")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("username", session.credentials.username)
            .addQueryParameter("password", session.credentials.password)
            .addQueryParameter("action", "get_series_info")
            .addQueryParameter("series_id", seriesId.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", JSON.toString())
            .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME} Android")
            .build()

        val root = try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank() || body.looksLikeChallenge()) {
                    return@withContext SeriesCardTechnicalMetadata()
                }
                runCatching { JSONObject(body) }.getOrNull()
                    ?: return@withContext SeriesCardTechnicalMetadata()
            }
        } catch (_: IOException) {
            return@withContext SeriesCardTechnicalMetadata()
        } catch (_: Exception) {
            return@withContext SeriesCardTechnicalMetadata()
        }

        val episodesObject = root.optJSONObject("episodes") ?: root.nestedObject("episodes")
        val episodeObjects = buildList {
            episodesObject?.keys()?.forEach { seasonKey ->
                val entries = episodesObject.optJSONArray(seasonKey) ?: return@forEach
                for (index in 0 until entries.length()) {
                    entries.optJSONObject(index)?.let { add(seasonKey to it) }
                }
            }
        }

        val positiveSeasons = episodeObjects
            .asSequence()
            .mapNotNull { (seasonKey, episode) ->
                episode.positiveInt("season") ?: seasonKey.toIntOrNull()?.takeIf { it > 0 }
            }
            .filter { it > 0 }
            .toSet()

        val seasonCount = when {
            positiveSeasons.isNotEmpty() -> positiveSeasons.size
            episodeObjects.isNotEmpty() -> 1
            else -> null
        }

        val qualityLabels = episodeObjects
            .asSequence()
            .take(MAX_EPISODES_TO_INSPECT)
            .mapNotNull { (_, episode) -> episodeVideoHeight(episode) }
            .mapNotNull(::qualityLabel)
            .toList()

        val rootInfo = root.optJSONObject("info") ?: root.nestedObject("info")
        val fallbackQuality = sequenceOf(
            rootInfo,
            rootInfo?.nestedObject("video"),
            rootInfo?.nestedObject("stream_info"),
            root,
            root.nestedObject("video"),
            root.nestedObject("stream_info"),
        )
            .filterNotNull()
            .mapNotNull(::videoHeightFrom)
            .mapNotNull(::qualityLabel)
            .firstOrNull()

        SeriesCardTechnicalMetadata(
            quality = predominantQuality(qualityLabels) ?: fallbackQuality,
            seasonCount = seasonCount,
        )
    }

    private fun episodeVideoHeight(episode: JSONObject): Int? {
        val info = episode.optJSONObject("info") ?: episode.nestedObject("info")
        return sequenceOf(
            info,
            info?.nestedObject("video"),
            info?.nestedObject("stream_info"),
            episode,
            episode.nestedObject("video"),
            episode.nestedObject("stream_info"),
        )
            .filterNotNull()
            .mapNotNull(::videoHeightFrom)
            .firstOrNull()
    }

    private fun videoHeightFrom(source: JSONObject): Int? {
        listOf(
            "height",
            "video_height",
            "resolution_height",
        )
            .asSequence()
            .mapNotNull { key -> source.positiveInt(key) }
            .firstOrNull()
            ?.let { return it }

        val resolution = listOf(
            "resolution",
            "video_resolution",
            "dimensions",
            "dimension",
        )
            .asSequence()
            .mapNotNull { key -> source.nullableText(key) }
            .firstOrNull()
            ?: return null

        val match = RESOLUTION_REGEX.find(resolution) ?: return null
        val first = match.groupValues.getOrNull(1)?.toIntOrNull()
        val second = match.groupValues.getOrNull(2)?.toIntOrNull()
        if (first == null || second == null || first <= 0 || second <= 0) return null
        return minOf(first, second)
    }

    private fun qualityLabel(height: Int?): String? = when {
        height == null || height <= 0 -> null
        height >= 2160 -> "4K"
        height >= 1440 -> "QHD"
        height >= 1080 -> "FHD"
        height >= 720 -> "HD"
        else -> "SD"
    }

    private fun predominantQuality(labels: List<String>): String? = labels
        .groupingBy { it }
        .eachCount()
        .entries
        .maxWithOrNull(
            compareBy<Map.Entry<String, Int>> { it.value }
                .thenBy { qualityRank(it.key) },
        )
        ?.key

    private fun qualityRank(label: String): Int = when (label) {
        "4K" -> 5
        "QHD" -> 4
        "FHD" -> 3
        "HD" -> 2
        "SD" -> 1
        else -> 0
    }

    private fun JSONObject.positiveInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toDoubleOrNull()?.toInt()
            else -> null
        }?.takeIf { it > 0 }
    }

    private fun JSONObject.nullableText(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key)
            .trim()
            .takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
    }

    private fun JSONObject.nestedObject(key: String): JSONObject? {
        val value = opt(key)
        return when (value) {
            is JSONObject -> value
            is String -> value
                .trim()
                .takeIf { it.startsWith("{") }
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
            else -> null
        }
    }

    private fun String.looksLikeChallenge(): Boolean {
        val prefix = trimStart().take(512).lowercase()
        return prefix.startsWith("<!doctype html") ||
            prefix.startsWith("<html") ||
            "cloudflare" in prefix ||
            "sorry, you have been blocked" in prefix
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        val RESOLUTION_REGEX = Regex("""(\d{3,5})\s*[xX×]\s*(\d{3,5})""")
        const val MAX_EPISODES_TO_INSPECT = 24
    }
}
