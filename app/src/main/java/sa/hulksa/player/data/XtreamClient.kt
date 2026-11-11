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
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.hulkPerfLog
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

open class XtreamClient(
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    open suspend fun authenticate(portal: PortalConfig, credentials: Credentials): AuthenticatedSession {
        val attempt = ProviderRequestTrace.queued(ProviderSurface.AUTH, null, "authenticate")
        val root = requestObject(portal, credentials, attempt = attempt)
        val user = root.optJSONObject("user_info") ?: throw XtreamException.InvalidResponse
        val status = user.optString("status").trim()
        val auth = user.optInt("auth", -1)
        if (auth != 0 && auth != 1) throw XtreamException.InvalidResponse
        val expires = user.optNullableString("exp_date")
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
        // Provider status/date are explicit entitlement evidence even in a failed-auth reply.
        // auth=0 alone never proves expiry, and no failed-auth response can create a session.
        if (status.equals("Expired", ignoreCase = true)) throw XtreamException.SubscriptionExpired
        requireUnexpiredAccountSession(expires, nowEpochSeconds())
        if (status.lowercase() in setOf("disabled", "banned", "inactive", "suspended")) {
            throw XtreamException.SubscriptionInactive
        }
        if (auth != 1) throw XtreamException.InvalidCredentials
        if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
            // Preserve the existing non-Active entitlement denial, including unfamiliar statuses.
            throw XtreamException.SubscriptionInactive
        }
        // Preserve the established auth=1/omitted-status provider compatibility below.
        val account = AccountInfo(
            username = user.optString("username", credentials.username),
            status = status.ifEmpty { "Active" },
            expiresAtEpochSeconds = expires,
            activeConnections = user.optString("active_cons", "0").toIntOrNull() ?: 0,
            maxConnections = user.optString("max_connections", "1").toIntOrNull() ?: 1,
            isTrial = user.optString("is_trial", "0") == "1",
        )
        return AuthenticatedSession(
            portal = portal,
            credentials = credentials,
            account = account,
            allowedLiveOutputFormats = parseAllowedLiveOutputFormats(user.opt("allowed_output_formats")),
        )
    }

    open suspend fun catalog(session: AuthenticatedSession, type: ContentType): Catalog =
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

                val startedNs = System.nanoTime()
                ProviderRequestTrace.beginBatch()
                val categories = async {
                    val attempt = ProviderRequestTrace.queued(
                        ProviderSurface.CATALOG, type, categoryAction,
                    )
                    try {
                        val partStartedNs = System.nanoTime()
                        val parsed = parseCategories(
                            requestArray(session.portal, session.credentials, categoryAction, attempt),
                            type,
                        )
                        hulkPerfLog {
                            "catalogPart type=$type part=categories count=${parsed.size} " +
                                "ms=${(System.nanoTime() - partStartedNs) / 1_000_000}"
                        }
                        parsed
                    } finally {
                        ProviderRequestTrace.terminalIfOpen(attempt, ProviderOutcome.CANCELED)
                    }
                }
                val items = async {
                    val attempt = ProviderRequestTrace.queued(
                        ProviderSurface.CATALOG, type, contentAction,
                    )
                    try {
                        val partStartedNs = System.nanoTime()
                        val parsed = requestCatalogItems(
                            portal = session.portal,
                            credentials = session.credentials,
                            action = contentAction,
                            type = type,
                            attempt = attempt,
                        )
                        hulkPerfLog {
                            "catalogPart type=$type part=items count=${parsed.size} " +
                                "ms=${(System.nanoTime() - partStartedNs) / 1_000_000}"
                        }
                        parsed
                    } finally {
                        ProviderRequestTrace.terminalIfOpen(attempt, ProviderOutcome.CANCELED)
                    }
                }
                Catalog(categories.await(), items.await()).also {
                    hulkPerfLog {
                        "catalogFetch type=$type items=${it.items.size} categories=${it.categories.size} " +
                            "ms=${(System.nanoTime() - startedNs) / 1_000_000}"
                    }
                }
            }
        }

    suspend fun episodes(
        session: AuthenticatedSession,
        seriesId: Int,
        surface: ProviderSurface = ProviderSurface.DETAILS,
    ): List<Episode> = seriesBundle(session, seriesId, surface).episodes

    suspend fun contentDetails(
        session: AuthenticatedSession,
        movieId: Int,
        surface: ProviderSurface = ProviderSurface.DETAILS,
    ): ContentDetails =
        withContext(Dispatchers.IO) {
            val attempt = ProviderRequestTrace.queued(surface, ContentType.MOVIE, "get_vod_info")
            val root = requestObject(
                portal = session.portal,
                credentials = session.credentials,
                action = "get_vod_info",
                extra = mapOf("vod_id" to movieId.toString()),
                attempt = attempt,
            )
            parseDetails(root.optJSONObject("info") ?: root, session.portal.baseUrl)
        }

    open suspend fun seriesBundle(
        session: AuthenticatedSession,
        seriesId: Int,
        surface: ProviderSurface = ProviderSurface.DETAILS,
    ): SeriesBundle =
        withContext(Dispatchers.IO) {
            val attempt = ProviderRequestTrace.queued(surface, ContentType.SERIES, "get_series_info")
            val root = requestObject(
                portal = session.portal,
                credentials = session.credentials,
                action = "get_series_info",
                extra = mapOf("series_id" to seriesId.toString()),
                attempt = attempt,
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
                allowedOutputFormats = session.allowedLiveOutputFormats,
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
                allowedOutputFormats = session.allowedLiveOutputFormats,
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
            originalLanguage = entry.originalLanguage,
            savedAudioPreference = entry.savedAudioPreference,
            savedSubtitlePreference = entry.savedSubtitlePreference,
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
        allowedOutputFormats: Set<String>,
    ): List<String> {
        return orderedLiveOutputFormats(preferredExtension, allowedOutputFormats)
            .map { outputFormat ->
                streamUrl(base, "live", credentials, streamId, outputFormat)
            }
    }

    private suspend fun requestObject(
        portal: PortalConfig,
        credentials: Credentials,
        action: String? = null,
        extra: Map<String, String> = emptyMap(),
        attempt: ProviderAttempt? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            val body = request(portal, credentials, action, extra, attempt)
            currentCoroutineContext().ensureActive()
            val parsed = XtreamJsonParser.parseObject(body)
            currentCoroutineContext().ensureActive()
            parsed
        } catch (error: XtreamJsonGuardException) {
            reportProviderTerminal(attempt, ProviderOutcome.GUARD)
            throw error.asXtreamException()
        }
    }

    private suspend fun requestArray(
        portal: PortalConfig,
        credentials: Credentials,
        action: String,
        attempt: ProviderAttempt? = null,
    ): JSONArray = withContext(Dispatchers.IO) {
        try {
            val startedNs = System.nanoTime()
            val body = request(portal, credentials, action, attempt = attempt)
            val httpNs = System.nanoTime()
            currentCoroutineContext().ensureActive()
            val parsed = XtreamJsonParser.parseArray(body)
            currentCoroutineContext().ensureActive()
            hulkPerfLog {
                "xtreamArray action=$action chars=${body.length} " +
                    "httpMs=${(httpNs - startedNs) / 1_000_000} " +
                    "parseMs=${(System.nanoTime() - httpNs) / 1_000_000}"
            }
            parsed
        } catch (error: XtreamJsonGuardException) {
            reportProviderTerminal(attempt, ProviderOutcome.GUARD)
            throw error.asXtreamException()
        }
    }

    private suspend fun requestCatalogItems(
        portal: PortalConfig,
        credentials: Credentials,
        action: String,
        type: ContentType,
        attempt: ProviderAttempt? = null,
    ): List<ContentItem> = withContext(Dispatchers.IO) {
        val startedNs = System.nanoTime()
        val request = apiRequest(portal, credentials, action)
        val limit = XtreamJsonLimits.forAction(action)
        val scope = "${type.name.lowercase()} catalog"
        val context = currentCoroutineContext()

        try {
            attempt?.let(ProviderRequestTrace::callIssued)
            client.executeCancellable(request) { response ->
                if (!response.isSuccessful) {
                    val body = try {
                        BoundedJsonResponseReader.readResponse(response, limit)
                    } catch (error: XtreamJsonGuardException.PayloadTooLarge) {
                        reportProviderTerminal(
                            attempt, ProviderOutcome.HTTP, response.code,
                            shape = ProviderShape.UNAVAILABLE,
                        )
                        throw XtreamException.Http(response.code)
                    } catch (error: XtreamJsonGuardException.EmptyBody) {
                        reportProviderTerminal(
                            attempt, ProviderOutcome.HTTP, response.code,
                            shape = ProviderShape.UNAVAILABLE,
                        )
                        throw XtreamException.Http(response.code)
                    }
                    val shape = providerResponseShape(body)
                    body.challengeCategory()?.let {
                        reportProviderTerminal(
                            attempt, ProviderOutcome.BLOCKED, response.code, it, shape,
                        )
                        throw XtreamException.ServiceBlocked(response.code, it)
                    }
                    reportProviderTerminal(attempt, ProviderOutcome.HTTP, response.code, shape = shape)
                    throw XtreamException.Http(response.code)
                }

                val body = response.body ?: throw XtreamException.InvalidResponse
                val reader = BoundedJsonArrayItemReader(
                    source = body.source(),
                    maxBytes = limit.maxResponseBytes,
                    declaredLength = body.contentLength(),
                )
                try {
                    val checkCancelled = { context.ensureActive() }
                    val scan = reader.scanPrefix(checkCancelled = checkCancelled)
                    if (scan == PrefixScanResult.LimitReached) {
                        // A pathological whitespace prefix exceeded the bounded peek window.
                        // Fall back to the legacy bounded text parser for this response only.
                        val text = reader.readAllText(checkCancelled)
                        val shape = providerResponseShape(text)
                        text.challengeCategory()?.let {
                            reportProviderTerminal(
                                attempt, ProviderOutcome.BLOCKED, response.code, it, shape,
                            )
                            throw XtreamException.ServiceBlocked(response.code, it)
                        }
                        val parseText = if (text.startsWith('\uFEFF')) text.substring(1) else text
                        val items = parseItems(
                            XtreamJsonParser.parseArray(parseText),
                            type,
                            portal.baseUrl,
                        )
                        reportProviderTerminal(
                            attempt, ProviderOutcome.SUCCESS, response.code,
                            shape = ProviderShape.ARRAY_START,
                        )
                        hulkPerfLog {
                            "xtreamItemsFallback action=$action items=${items.size} " +
                                "chars=${text.length} ms=${(System.nanoTime() - startedNs) / 1_000_000}"
                        }
                        return@executeCancellable items
                    }

                    val first = (scan as? PrefixScanResult.Found)?.value
                    val catalogCandidate = first == null || first == '['.code ||
                        first == 0xEF || first == '/'.code || first == '#'.code
                    if (!catalogCandidate) {
                        val prefix = reader.readChallengePrefix(checkCancelled)
                        val shape = providerResponseShape(prefix)
                        prefix.challengeCategory()?.let {
                            reportProviderTerminal(
                                attempt, ProviderOutcome.BLOCKED, response.code, it, shape,
                            )
                            throw XtreamException.ServiceBlocked(response.code, it)
                        }
                        reportProviderTerminal(
                            attempt, ProviderOutcome.OTHER, response.code, shape = shape,
                        )
                        throw XtreamException.InvalidResponse
                    }

                    val items = reader.readCatalogItems(
                        type = type,
                        portalBaseUrl = portal.baseUrl,
                        maxUniqueItems = XtreamJsonLimits.CATALOG.maxUniqueItems!!,
                        scope = scope,
                        checkCancelled = checkCancelled,
                    )
                    reportProviderTerminal(
                        attempt, ProviderOutcome.SUCCESS, response.code,
                        shape = ProviderShape.ARRAY_START,
                    )
                    hulkPerfLog {
                        "xtreamItems action=$action items=${items.size} bytes=${reader.bytesRead} " +
                            "ms=${(System.nanoTime() - startedNs) / 1_000_000}"
                    }
                    items
                } finally {
                    reader.close()
                }
            }
        } catch (error: CancellationException) {
            reportProviderException(attempt, error)
            throw error
        } catch (error: XtreamJsonGuardException) {
            reportProviderException(attempt, error)
            throw error.asXtreamException()
        } catch (error: XtreamException) {
            reportProviderException(attempt, error)
            throw error
        } catch (error: IOException) {
            reportProviderException(attempt, error)
            throw XtreamException.Network(error)
        } catch (error: Exception) {
            reportProviderTerminal(attempt, ProviderOutcome.OTHER)
            throw XtreamException.InvalidResponse
        }
    }

    private suspend fun request(
        portal: PortalConfig,
        credentials: Credentials,
        action: String?,
        extra: Map<String, String> = emptyMap(),
        attempt: ProviderAttempt? = null,
    ): String = withContext(Dispatchers.IO) {
        val request = apiRequest(portal, credentials, action, extra)
        val limit = XtreamJsonLimits.forAction(action)
        val authenticationRequest = action == null && extra.isEmpty()

        try {
            attempt?.let(ProviderRequestTrace::callIssued)
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
                val shape = providerResponseShape(body)
                if (!response.isSuccessful) {
                    if (!authenticationRequest || !body.trimStart().startsWith("{")) {
                        body.challengeCategory()?.let {
                            reportProviderTerminal(
                                attempt, ProviderOutcome.BLOCKED, response.code, it, shape,
                            )
                            throw XtreamException.ServiceBlocked(response.code, it)
                        }
                    }
                    // Some providers return structured failed-auth evidence with 401/403.
                    // Permit only auth=0 to reach the authentication parser; never accept auth=1
                    // from a rejected response or apply this exception to catalog requests.
                    val failedAuth = authenticationRequest && response.code in setOf(401, 403) &&
                        runCatching {
                            XtreamJsonParser.parseObject(body).optJSONObject("user_info")
                                ?.optInt("auth", -1) == 0
                        }.getOrDefault(false)
                    if (!failedAuth) {
                        reportProviderTerminal(
                            attempt, ProviderOutcome.HTTP, response.code, shape = shape,
                        )
                        throw XtreamException.Http(response.code)
                    }
                }
                if (!authenticationRequest || !body.trimStart().startsWith("{")) {
                    body.challengeCategory()?.let { category ->
                        val prefix = body.trimStart().take(512).lowercase()
                        if (authenticationRequest && response.isSuccessful &&
                            category in setOf("html", "html-doctype") &&
                            "cloudflare" !in prefix && "sorry, you have been blocked" !in prefix
                        ) {
                            reportProviderTerminal(
                                attempt, ProviderOutcome.OTHER, response.code, shape = shape,
                            )
                            throw XtreamException.InvalidResponse
                        }
                        reportProviderTerminal(
                            attempt, ProviderOutcome.BLOCKED, response.code, category, shape,
                        )
                        throw XtreamException.ServiceBlocked(response.code, category)
                    }
                }
                reportProviderTerminal(attempt, ProviderOutcome.SUCCESS, response.code, shape = shape)
                body
            }
        } catch (error: CancellationException) {
            reportProviderException(attempt, error)
            throw error
        } catch (error: XtreamException) {
            reportProviderException(attempt, error)
            throw error
        } catch (error: IOException) {
            reportProviderException(attempt, error)
            throw XtreamException.Network(error)
        } catch (error: Exception) {
            reportProviderTerminal(attempt, ProviderOutcome.OTHER)
            throw XtreamException.InvalidResponse
        }
    }

    private fun apiRequest(
        portal: PortalConfig,
        credentials: Credentials,
        action: String?,
        extra: Map<String, String> = emptyMap(),
    ): Request {
        val url = (portal.baseUrl.trimEnd('/') + "/player_api.php").toHttpUrl().newBuilder()
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .apply {
                if (action != null) addQueryParameter("action", action)
                extra.forEach { (key, value) -> addQueryParameter(key, value) }
            }
            .build()
        return Request.Builder()
            .url(url)
            .get()
            .header("Accept", JSON.toString())
            .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME} Android")
            .build()
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

    internal fun parseItems(
        array: JSONArray,
        type: ContentType,
        portalBaseUrl: String,
    ): List<ContentItem> = try {
        val idKey = if (type == ContentType.SERIES) "series_id" else "stream_id"
        // One base parse for the whole catalog; absolute artwork never needs it at all.
        val artworkBase = (portalBaseUrl.trimEnd('/') + "/").toHttpUrlOrNull()
        buildList {
            array.forEachUniqueObject(
                maxUniqueItems = XtreamJsonLimits.CATALOG.maxUniqueItems!!,
                scope = "${type.name.lowercase()} catalog",
                keyOf = { item -> item.optString(idKey).toIntOrNull()?.toString() },
            ) { item ->
                parseItem(item, type, idKey, artworkBase)?.let(::add)
            }
        }
    } catch (error: XtreamJsonGuardException) {
        throw error.asXtreamException()
    }

    internal fun parseCatalogItemsStreaming(
        source: BufferedSource,
        type: ContentType,
        portalBaseUrl: String,
        maxBytes: Long = XtreamJsonLimits.CATALOG.maxResponseBytes,
        declaredLength: Long = -1L,
        checkCancelled: () -> Unit = {},
    ): List<ContentItem> {
        return BoundedJsonArrayItemReader(source, maxBytes, declaredLength).use { reader ->
            reader.readCatalogItems(
                type = type,
                portalBaseUrl = portalBaseUrl,
                maxUniqueItems = XtreamJsonLimits.CATALOG.maxUniqueItems!!,
                scope = "${type.name.lowercase()} catalog",
                checkCancelled = checkCancelled,
            )
        }
    }

    private fun parseItem(
        item: JSONObject,
        type: ContentType,
        idKey: String,
        artworkBase: HttpUrl?,
    ): ContentItem? {
        val id = item.optString(idKey).toIntOrNull() ?: return null
        return ContentItem(
            id = id,
            name = item.optString("name", "بدون اسم"),
            categoryId = item.optString("category_id", "0"),
            type = type,
            posterUrl = normalizeArtworkUrl(
                item.optNullableString(if (type == ContentType.SERIES) "cover" else "stream_icon"),
                artworkBase,
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
            backdropUrl = normalizeArtworkUrl(item.firstImageUrl("backdrop_path"), artworkBase),
        )
    }

    private fun parseDetails(info: JSONObject, portalBaseUrl: String): ContentDetails = ContentDetails(
        plot = info.optNullableString("plot") ?: info.optNullableString("description"),
        genre = info.optNullableString("genre"),
        duration = info.optNullableString("duration"),
        director = info.optNullableString("director"),
        cast = info.optNullableString("cast") ?: info.optNullableString("actors"),
        releaseDate = info.optNullableString("releasedate")
            ?: info.optNullableString("release_date"),
        backdropUrl = info.xtreamPresentationArtworkUrl(portalBaseUrl),
        // Only explicit original-language fields are trustworthy. Generic `language`/`country`
        // may describe the current dub or production country and must not classify the title.
        originalLanguage = info.optNullableString("original_language")
            ?: info.optNullableString("original_language_iso")
            ?: info.optNullableString("original_language_iso_639_1")
            ?: info.optNullableString("original_language_code")
            ?: info.optNullableString("original_language_name"),
    )

    private fun JSONObject.optNullableString(key: String): String? = xtreamNullableString(key)

    private fun JSONObject.firstImageUrl(key: String): String? = xtreamFirstImageUrl(key)

    private fun String.challengeCategory(): String? = providerChallengeCategory(this)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

/**
 * Shared Xtream presentation parsing used by the Details owner and the bounded card-metadata
 * clients so artwork/plot/genre keep one normalization and null policy. The provider artwork
 * candidate may itself be a portrait poster; callers classify real dimensions before choosing a
 * wide presentation.
 */
internal fun JSONObject.xtreamPresentationArtworkUrl(portalBaseUrl: String): String? =
    normalizeArtworkUrl(
        xtreamFirstImageUrl("backdrop_path") ?: xtreamNullableString("movie_image"),
        portalBaseUrl,
    )

internal fun JSONObject.xtreamNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeUnless { it.isEmpty() || it.equals("null", true) }
}

internal fun JSONObject.xtreamFirstImageUrl(key: String): String? {
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

internal fun parseAllowedLiveOutputFormats(rawValue: Any?): Set<String> {
    val rawFormats = when (rawValue) {
        is JSONArray -> buildList {
            for (index in 0 until rawValue.length()) add(rawValue.optString(index))
        }
        is String -> {
            val value = rawValue.trim()
            if (value.startsWith("[") && value.endsWith("]")) {
                runCatching {
                    val array = JSONArray(value)
                    buildList {
                        for (index in 0 until array.length()) add(array.optString(index))
                    }
                }.getOrDefault(emptyList())
            } else {
                value.split(',', ';', '|')
            }
        }
        else -> emptyList()
    }
    return rawFormats
        .mapNotNull(::normalizeLiveOutputFormat)
        .toCollection(linkedSetOf())
}

internal fun orderedLiveOutputFormats(
    preferredExtension: String?,
    allowedOutputFormats: Set<String>,
): List<String> {
    val preferred = normalizeLiveOutputFormat(preferredExtension)
    val allowed = allowedOutputFormats.mapNotNull(::normalizeLiveOutputFormat).distinct()
    val primary = preferred ?: allowed.firstOrNull() ?: "ts"
    return buildList {
        add(primary)
        allowed.forEach { format -> if (format != primary) add(format) }
    }
}

private fun normalizeLiveOutputFormat(rawValue: String?): String? = rawValue
    ?.trim()
    ?.trimStart('.')
    ?.lowercase()
    ?.let { value -> if (value == "mpegts") "ts" else value }
    ?.takeIf { it == "ts" || it == "m3u8" }

internal fun normalizeArtworkUrl(raw: String?, portalBaseUrl: String): String? {
    val clean = cleanArtworkUrl(raw) ?: return null
    if (clean.isAbsoluteHttpUrl()) return clean
    return normalizeArtworkUrl(clean, (portalBaseUrl.trimEnd('/') + "/").toHttpUrlOrNull())
}

internal fun normalizeArtworkUrl(raw: String?, base: HttpUrl?): String? {
    val clean = cleanArtworkUrl(raw) ?: return null
    if (clean.isAbsoluteHttpUrl()) return clean
    return when {
        clean.startsWith("//") && base != null -> "${base.scheme}:$clean"
        base != null -> base.resolve(clean.replace('\\', '/'))?.toString() ?: clean
        else -> clean
    }
}

private fun cleanArtworkUrl(raw: String?): String? = raw
    ?.trim()
    ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }

private fun String.isAbsoluteHttpUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

sealed class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause), LoginFailureSource {
    override val loginFailure: LoginFailure
        get() = LoginFailure(
            when (this) {
                InvalidCredentials -> LoginFailureCategory.INVALID_CREDENTIALS
                SubscriptionExpired -> LoginFailureCategory.EXPIRED_SUBSCRIPTION
                SubscriptionInactive -> LoginFailureCategory.INACTIVE_SUBSCRIPTION
                InvalidResponse, PayloadLimitExceeded -> LoginFailureCategory.MALFORMED
                is Network -> LoginFailureCategory.TRANSPORT
                is ServiceBlocked -> LoginFailureCategory.SERVICE_REJECTED
                is Http -> if (statusCode == 408 || statusCode == 425) {
                    LoginFailureCategory.CONNECTION_UNCERTAIN
                } else LoginFailureCategory.SERVICE_REJECTED
            },
            LoginFailureStage.PROVIDER,
        )

    data object InvalidCredentials : XtreamException("بيانات الاشتراك غير صحيحة او غير فعالة.")
    data object SubscriptionInactive : XtreamException("الاشتراك منتهي او غير فعال. تواصل مع الدعم للتجديد.")
    data object SubscriptionExpired : XtreamException("انتهت صلاحية الاشتراك")
    data object InvalidResponse : XtreamException("وصل رد غير صالح من الخدمة.")
    data object PayloadLimitExceeded : XtreamException("رد الخدمة تجاوز حدود الأمان المدعومة.")
    data class ServiceBlocked(val statusCode: Int, val category: String?) :
        XtreamException("الخدمة رفضت الاتصال مؤقتا. جرب من شبكة اخرى او تواصل مع الدعم.")
    data class Http(val statusCode: Int) : XtreamException("تعذر الاتصال بالخدمة (رمز $statusCode).")
    data class Network(val error: IOException) : XtreamException("تحقق من اتصال الانترنت ثم حاول مرة اخرى.", error)
}

private fun XtreamJsonGuardException.asXtreamException(): XtreamException = when (this) {
    is XtreamJsonGuardException.PayloadTooLarge,
    is XtreamJsonGuardException.TooManyItems -> XtreamException.PayloadLimitExceeded
    is XtreamJsonGuardException.EmptyBody,
    is XtreamJsonGuardException.InvalidJson -> XtreamException.InvalidResponse
}
