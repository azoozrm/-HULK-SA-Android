package sa.hulksa.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.model.AuthenticatedSession
import java.io.IOException
import java.util.concurrent.TimeUnit

data class MovieCardTechnicalMetadata(
    val quality: String? = null,
    val durationMs: Long? = null,
)

class MovieCardMetadataClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun fetch(
        session: AuthenticatedSession,
        movieId: Int,
    ): MovieCardTechnicalMetadata = withContext(Dispatchers.IO) {
        val url = (session.portal.baseUrl.trimEnd('/') + "/player_api.php")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("username", session.credentials.username)
            .addQueryParameter("password", session.credentials.password)
            .addQueryParameter("action", "get_vod_info")
            .addQueryParameter("vod_id", movieId.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", JSON.toString())
            .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME} Android")
            .build()

        val root = try {
            client.newCall(request).execute().use { response ->
                val body = try {
                    BoundedJsonResponseReader.readResponse(response, XtreamJsonLimits.VOD_INFO)
                } catch (_: XtreamJsonGuardException) {
                    return@withContext MovieCardTechnicalMetadata()
                }
                if (!response.isSuccessful || body.looksLikeChallenge()) {
                    return@withContext MovieCardTechnicalMetadata()
                }
                try {
                    XtreamJsonParser.parseObject(body)
                } catch (_: XtreamJsonGuardException) {
                    return@withContext MovieCardTechnicalMetadata()
                }
            }
        } catch (_: IOException) {
            return@withContext MovieCardTechnicalMetadata()
        } catch (_: Exception) {
            return@withContext MovieCardTechnicalMetadata()
        }

        val info = root.optJSONObject("info")
        val movieData = root.optJSONObject("movie_data")

        val durationMs = sequenceOf(info, movieData, root)
            .filterNotNull()
            .mapNotNull(::durationMsFrom)
            .firstOrNull()

        val height = sequenceOf(
            info,
            info?.nestedObject("video"),
            info?.nestedObject("stream_info"),
            movieData,
            movieData?.nestedObject("video"),
            movieData?.nestedObject("stream_info"),
            root.nestedObject("video"),
            root.nestedObject("stream_info"),
            root,
        )
            .filterNotNull()
            .mapNotNull(::videoHeightFrom)
            .firstOrNull()

        MovieCardTechnicalMetadata(
            quality = qualityLabel(height),
            durationMs = durationMs,
        )
    }

    private fun durationMsFrom(source: JSONObject): Long? {
        val seconds = listOf(
            "duration_secs",
            "duration_seconds",
            "duration_sec",
        )
            .asSequence()
            .mapNotNull { key -> source.positiveLong(key) }
            .firstOrNull()
        if (seconds != null) return seconds * 1_000L

        source.nullableText("duration")
            ?.let { parseDurationTextMs(it, numericValueIsMinutes = false) }
            ?.let { return it }

        return source.nullableText("runtime")
            ?.let { parseDurationTextMs(it, numericValueIsMinutes = true) }
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

        val match = resolution
            ?.let(RESOLUTION_REGEX::find)
            ?: return null

        val first = match.groupValues.getOrNull(1)?.toIntOrNull()
        val second = match.groupValues.getOrNull(2)?.toIntOrNull()
        if (first == null || second == null || first <= 0 || second <= 0) return null

        // Resolution may arrive as 1920x1080 or 1080x1920.
        // The smaller dimension is the stable vertical-resolution signal for
        // normal landscape and portrait video without guessing orientation.
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

    private fun parseDurationTextMs(
        raw: String,
        numericValueIsMinutes: Boolean,
    ): Long? {
        val clean = raw
            .trim()
            .lowercase()
            .replace("minutes", "")
            .replace("minute", "")
            .replace("mins", "")
            .replace("min", "")
            .trim()
        if (clean.isEmpty()) return null

        if (':' !in clean) {
            val value = clean.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
            val seconds = if (numericValueIsMinutes) value * 60.0 else value
            return (seconds * 1_000.0).toLong().takeIf { it > 0L }
        }

        val parts = clean.split(':').map(String::trim)
        val totalSeconds = when (parts.size) {
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val seconds = parts[2].toDoubleOrNull() ?: return null
                hours * 3_600.0 + minutes * 60.0 + seconds
            }
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return null
                val seconds = parts[1].toDoubleOrNull() ?: return null
                minutes * 60.0 + seconds
            }
            else -> return null
        }
        return (totalSeconds * 1_000.0).toLong().takeIf { it > 0L }
    }

    private fun JSONObject.positiveLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toDoubleOrNull()?.toLong()
            else -> null
        }?.takeIf { it > 0L }
    }

    private fun JSONObject.positiveInt(key: String): Int? =
        positiveLong(key)?.toInt()?.takeIf { it > 0 }

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
                ?.let { runCatching { XtreamJsonParser.parseObject(it) }.getOrNull() }
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
    }
}
