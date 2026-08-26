package sa.hulksa.player.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.model.SeriesBundle
import java.io.IOException
import java.util.concurrent.TimeUnit

class XtreamClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun authenticate(portal: PortalConfig, credentials: Credentials): AuthenticatedSession {
        val root = requestObject(portal, credentials)
        val user = root.optJSONObject("user_info") ?: throw XtreamException.InvalidResponse
        val status = user.optString("status").trim()
        if (user.optInt("auth", 0) != 1) throw XtreamException.InvalidCredentials
        if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
            throw XtreamException.SubscriptionInactive
        }

        val expires = user.optNullableString("exp_date")
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
        val account = AccountInfo(
            username = user.optString("username", credentials.username),
            status = status.ifEmpty { "Active" },
            expiresAtEpochSeconds = expires,
            activeConnections = user.optString("active_cons", "0").toIntOrNull() ?: 0,
            maxConnections = user.optString("max_connections", "1").toIntOrNull() ?: 1,
            isTrial = user.optString("is_trial", "0") == "1",
        )
        return AuthenticatedSession(portal, credentials, account)
    }

    suspend fun catalog(session: AuthenticatedSession, type: ContentType): Catalog =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val categoryAction = when (type) {
                    ContentType.LIVE -> "get_live_categories"
                    ContentType.MOVIE -> "get_vod_categories"
                    ContentType.SERIES -> "get_series_categories"
                }
                val contentAction = when (type) {
                    ContentType.LIVE -> "get_live_streams"
                    ContentType.MOVIE -> "get_vod_streams"
                    ContentType.SERIES -> "get_series"
                }

                val categories = async {
                    parseCategories(requestArray(session.portal, session.credentials, categoryAction), type)
                }
                val items = async {
                    parseItems(
                        requestArray(session.portal, session.credentials, contentAction),
                        type,
                        session.portal.baseUrl,
                    )
                }
                Catalog(categories.await(), items.await())
            }
        }

    suspend fun episodes(session: AuthenticatedSession, seriesId: Int): List<Episode> =
        seriesBundle(session, seriesId).episodes

    suspend fun contentDetails(session: AuthenticatedSession, movieId: Int): ContentDetails =
        withContext(Dispatchers.IO) {
            val root = requestObject(
                portal = session.portal,
                credentials = session.credentials,
                action = "get_vod_info",
                extra = mapOf("vod_id" to movieId.toString()),
            )
            parseDetails(root.optJSONObject("info") ?: root, session.portal.baseUrl)
        }

    suspend fun seriesBundle(session: AuthenticatedSession, seriesId: Int): SeriesBundle =
        withContext(Dispatchers.IO) {
            val root = requestObject(
                portal = session.portal,
                credentials = session.credentials,
                action = "get_series_info",
                extra = mapOf("series_id" to seriesId.toString()),
            )
            val episodeObjects = try {
                root.boundedSeriesEpisodeObjects()
            } catch (error: XtreamJsonGuardException) {
                throw error.asXtreamException()
            }
            val episodes = buildList {
                episodeObjects.forEach { entry ->
                    val episode = entry.episode
                    val info = episode.optJSONObject("info")
                    val id = episode.optString("id").toIntOrNull() ?: return@forEach
                    val seasonNumber = entry.seasonKey.toIntOrNull() ?: 0
                    add(
                        Episode(
                            id = id,
                            title = episode.optString("title", "الحلقة ${entry.indexInSeason + 1}"),
                            season = episode.optString("season", seasonNumber.toString()).toIntOrNull()
                                ?: seasonNumber,
                            episodeNumber = episode.optString(
                                "episode_num",
                                (entry.indexInSeason + 1).toString(),
                            ).toIntOrNull() ?: entry.indexInSeason + 1,
                            containerExtension = episode.optString("container_extension", "mp4"),
                            posterUrl = normalizeArtworkUrl(
                                info?.optNullableString("movie_image"),
                                session.portal.baseUrl,
                            ),
                            duration = info?.optNullableString("duration"),
                        ),
                    )
                }
            }.sortedWith(compareBy(Episode::season, Episode::episodeNumber))

            SeriesBundle(
                details = parseDetails(root.optJSONObject("info") ?: root, session.portal.baseUrl),
                episodes = episodes,
            )
        }

    fun playback(session: AuthenticatedSession, item: ContentItem): PlaybackRequest {
        val credentials = session.credentials
        val base = session.portal.baseUrl.toHttpUrl()
        val candidates = when (item.type) {
            ContentType.LIVE -> liveCandidates(
                base = base,
                credentials = credentials,
                streamId = item.id,
                preferredExtension = item.containerExtension,
            )
            ContentType.MOVIE -> listOf(
                streamUrl(base, "movie", credentials, item.id, item.containerExtension ?: "mp4"),
            )
            ContentType.SERIES -> emptyList()
        }
        val kind = when (item.type) {
            ContentType.LIVE -> "live"
            ContentType.MOVIE -> "movie"
            ContentType.SERIES -> "series"
        }
        return PlaybackRequest(
            title = item.name,
            posterUrl = item.posterUrl,
            candidates = candidates,
            isLive = item.type == ContentType.LIVE,
            historyKey = "${item.type.name}:${item.id}",
            streamKind = kind,
            streamId = item.id,
            extension = item.containerExtension ?: if (item.type == ContentType.LIVE) "ts" else "mp4",
        )
    }

    fun playback(session: AuthenticatedSession, series: ContentItem, episode: Episode): PlaybackRequest {
        val url = streamUrl(
            session.portal.baseUrl.toHttpUrl(),
            "series",
            session.credentials,
            episode.id,
            episode.containerExtension,
        )
        return PlaybackRequest(
            title = "${series.name} · ${episode.title}",
            posterUrl = episode.posterUrl ?: series.posterUrl,
            candidates = listOf(url),
            isLive = false,
            historyKey = "SERIES:${episode.id}",
            streamKind = "series",
            streamId = episode.id,
            extension = episode.containerExtension,
            seriesTitle = series.name,
            season = episode.season,
            episodeNumber = episode.episodeNumber,
            episodeTitle = episode.title,
        )
    }

    fun playback(session: AuthenticatedSession, entry: HistoryEntry): PlaybackRequest {
        val base = session.portal.baseUrl.toHttpUrl()
        val extension = entry.extension.ifBlank { if (entry.isLive) "ts" else "mp4" }
        val candidates = if (entry.isLive) {
            liveCandidates(
                base = base,
                credentials = session.credentials,
                streamId = entry.streamId,
                preferredExtension = extension,
            )
        } else {
            listOf(streamUrl(base, entry.streamKind, session.credentials, entry.streamId, extension))
        }
        return PlaybackRequest(
            title = entry.title,
            posterUrl = entry.posterUrl,
            candidates = candidates,
            isLive = entry.isLive,
            historyKey = entry.key,
            streamKind = entry.streamKind,
            streamId = entry.streamId,
            extension = extension,
            resumePositionMs = entry.positionMs,
            seriesTitle = entry.seriesTitle,
            season = entry.season,
            episodeNumber = entry.episodeNumber,
            episodeTitle = entry.episodeTitle,
        )
    }

    private fun streamUrl(
        base: HttpUrl,
        kind: String,
        credentials: Credentials,
        streamId: Int,
        extension: String,
    ): String = base.newBuilder()
        .addPathSegment(kind)
        .addPathSegment(credentials.username)
        .addPathSegment(credentials.password)
        .addPathSegment("$streamId.${extension.trimStart('.')}")
        .build()
        .toString()

    private fun liveCandidates(
        base: HttpUrl,
        credentials: Credentials,
        streamId: Int,
        preferredExtension: String?,
    ): List<String> {
        val preferred = preferredExtension
            ?.trim()
            ?.trimStart('.')
            ?.lowercase()
            ?.takeIf { it == "ts" || it == "m3u8" || it == "mpegts" }
        return listOf(
            streamUrl(base, "live", credentials, streamId, preferred ?: "ts"),
        )
    }

    private suspend fun requestObject(
        portal: PortalConfig,
        credentials: Credentials,
        action: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            val body = request(portal, credentials, action, extra)
            currentCoroutineContext().ensureActive()
            val parsed = XtreamJsonParser.parseObject(body)
            currentCoroutineContext().ensureActive()
            parsed
        } catch (error: XtreamJsonGuardException) {
            throw error.asXtreamException()
        }
    }

    private suspend fun requestArray(
        portal: PortalConfig,
        credentials: Credentials,
        action: String,
    ): JSONArray = withContext(Dispatchers.IO) {
        try {
            val body = request(portal, credentials, action)
            currentCoroutineContext().ensureActive()
            val parsed = XtreamJsonParser.parseArray(body)
            currentCoroutineContext().ensureActive()
            parsed
        } catch (error: XtreamJsonGuardException) {
            throw error.asXtreamException()
        }
    }

    private suspend fun request(
        portal: PortalConfig,
        credentials: Credentials,
        action: String?,
        extra: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val url = (portal.baseUrl.trimEnd('/') + "/player_api.php").toHttpUrl().newBuilder()
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .apply {
                if (action != null) addQueryParameter("action", action)
                extra.forEach { (key, value) -> addQueryParameter(key, value) }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", JSON.toString())
            .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME} Android")
            .build()
        val limit = XtreamJsonLimits.forAction(action)

        try {
            client.executeCancellable(request) { response ->
                val body = try {
                    BoundedJsonResponseReader.readResponse(response, limit)
                } catch (error: XtreamJsonGuardException.PayloadTooLarge) {
                    if (!response.isSuccessful) throw XtreamException.Http(response.code)
                    throw XtreamException.PayloadLimitExceeded
                } catch (error: XtreamJsonGuardException.EmptyBody) {
                    if (!response.isSuccessful) throw XtreamException.Http(response.code)
                    throw XtreamException.InvalidResponse
                }
                if (!response.isSuccessful) {
                    if (body.looksLikeChallenge()) throw XtreamException.ServiceBlocked
                    throw XtreamException.Http(response.code)
                }
                if (body.looksLikeChallenge()) throw XtreamException.ServiceBlocked
                body
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: XtreamException) {
            throw error
        } catch (error: IOException) {
            throw XtreamException.Network(error)
        } catch (error: Exception) {
            throw XtreamException.InvalidResponse
        }
    }

    private fun parseCategories(array: JSONArray, type: ContentType): List<Category> = try {
        buildList {
            array.forEachUniqueObject(
                maxUniqueItems = XtreamJsonLimits.CATEGORIES.maxUniqueItems!!,
                scope = "categories",
                keyOf = { item -> item.optNullableString("category_id") },
            ) { item ->
                val id = item.optNullableString("category_id") ?: return@forEachUniqueObject
                add(Category(id, item.optString("category_name", "بدون اسم"), type))
            }
        }
    } catch (error: XtreamJsonGuardException) {
        throw error.asXtreamException()
    }

    private fun parseItems(
        array: JSONArray,
        type: ContentType,
        portalBaseUrl: String,
    ): List<ContentItem> = try {
        val idKey = if (type == ContentType.SERIES) "series_id" else "stream_id"
        buildList {
            array.forEachUniqueObject(
                maxUniqueItems = XtreamJsonLimits.CATALOG.maxUniqueItems!!,
                scope = "${type.name.lowercase()} catalog",
                keyOf = { item -> item.optString(idKey).toIntOrNull()?.toString() },
            ) { item ->
                val id = item.optString(idKey).toIntOrNull() ?: return@forEachUniqueObject
                add(
                    ContentItem(
                        id = id,
                        name = item.optString("name", "بدون اسم"),
                        categoryId = item.optString("category_id", "0"),
                        type = type,
                        posterUrl = normalizeArtworkUrl(
                            item.optNullableString(if (type == ContentType.SERIES) "cover" else "stream_icon"),
                            portalBaseUrl,
                        ),
                        rating = item.optNullableString("rating") ?: item.optNullableString("rating_5based"),
                        year = item.optNullableString("year"),
                        containerExtension = item.optNullableString("container_extension"),
                        nowPlaying = item.optNullableString("epg_channel_id"),
                        addedAtEpochSeconds = when (type) {
                            ContentType.SERIES -> item.optNullableString("last_modified")
                                ?: item.optNullableString("added")
                            else -> item.optNullableString("added")
                        }?.toLongOrNull(),
                        plot = item.optNullableString("plot"),
                        genre = item.optNullableString("genre"),
                        backdropUrl = normalizeArtworkUrl(item.firstImageUrl("backdrop_path"), portalBaseUrl),
                    ),
                )
            }
        }
    } catch (error: XtreamJsonGuardException) {
        throw error.asXtreamException()
    }

    private fun parseDetails(info: JSONObject, portalBaseUrl: String): ContentDetails = ContentDetails(
        plot = info.optNullableString("plot") ?: info.optNullableString("description"),
        genre = info.optNullableString("genre"),
        duration = info.optNullableString("duration"),
        director = info.optNullableString("director"),
        cast = info.optNullableString("cast") ?: info.optNullableString("actors"),
        releaseDate = info.optNullableString("releasedate")
            ?: info.optNullableString("release_date"),
        backdropUrl = normalizeArtworkUrl(
            info.firstImageUrl("backdrop_path") ?: info.optNullableString("movie_image"),
            portalBaseUrl,
        ),
    )

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeUnless { it.isEmpty() || it.equals("null", true) }
    }

    private fun JSONObject.firstImageUrl(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key)
        return when (value) {
            is JSONArray -> (0 until value.length())
                .asSequence()
                .mapNotNull { value.optString(it).trim().takeUnless(String::isBlank) }
                .firstOrNull()
            is String -> {
                val clean = value.trim()
                if (clean.startsWith("[")) {
                    runCatching {
                        XtreamJsonParser.parseArray(clean).optString(0).trim().takeUnless(String::isBlank)
                    }.getOrNull()
                } else {
                    clean.takeUnless { it.isBlank() || it.equals("null", true) }
                }
            }
            else -> null
        }
    }

    private fun String.looksLikeChallenge(): Boolean {
        val prefix = trimStart().take(512).lowercase()
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html") ||
            "cloudflare" in prefix || "sorry, you have been blocked" in prefix
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

internal fun normalizeArtworkUrl(raw: String?, portalBaseUrl: String): String? {
    val clean = raw
        ?.trim()
        ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        ?: return null
    val base = (portalBaseUrl.trimEnd('/') + "/").toHttpUrlOrNull()
    return when {
        clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true) -> clean
        clean.startsWith("//") && base != null -> "${base.scheme}:$clean"
        base != null -> base.resolve(clean.replace('\\', '/'))?.toString() ?: clean
        else -> clean
    }
}

sealed class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidCredentials : XtreamException("بيانات الاشتراك غير صحيحة او غير فعالة.")
    data object SubscriptionInactive : XtreamException("الاشتراك منتهي او غير فعال. تواصل مع الدعم للتجديد.")
    data object InvalidResponse : XtreamException("وصل رد غير صالح من الخدمة.")
    data object PayloadLimitExceeded : XtreamException("رد الخدمة تجاوز حدود الأمان المدعومة.")
    data object ServiceBlocked : XtreamException("الخدمة رفضت الاتصال مؤقتا. جرب من شبكة اخرى او تواصل مع الدعم.")
    data class Http(val statusCode: Int) : XtreamException("تعذر الاتصال بالخدمة (رمز $statusCode).")
    data class Network(val error: IOException) : XtreamException("تحقق من اتصال الانترنت ثم حاول مرة اخرى.", error)
}

private fun XtreamJsonGuardException.asXtreamException(): XtreamException = when (this) {
    is XtreamJsonGuardException.PayloadTooLarge,
    is XtreamJsonGuardException.TooManyItems -> XtreamException.PayloadLimitExceeded
    is XtreamJsonGuardException.EmptyBody,
    is XtreamJsonGuardException.InvalidJson -> XtreamException.InvalidResponse
}
