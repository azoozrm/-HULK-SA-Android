package sa.hulksa.player.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

private const val HOME_HERO_TOKEN_PREFIX = "\u2063H:"

internal data class HomeHeroTechnicalMetadata(
    val quality: String? = null,
    val durationMs: Long? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
)

internal data class HomeHeroMetadataToken(
    val type: ContentType,
    val contentId: Int,
    val label: String,
)

internal data class ContentMetadataRequestKey(
    val accountId: String,
    val providerId: String,
    val sessionId: String,
    val type: ContentType,
    val contentId: Int,
)

internal fun AuthenticatedSessionOwner.contentMetadataKey(
    type: ContentType,
    contentId: Int,
): ContentMetadataRequestKey = ContentMetadataRequestKey(
    accountId = accountId,
    providerId = providerId,
    sessionId = sessionId,
    type = type,
    contentId = contentId,
)

internal fun ContentItem.withHomeHeroMetadataToken(): ContentItem = this

internal fun decodeHomeHeroMetadataToken(raw: String): HomeHeroMetadataToken? {
    if (!raw.startsWith(HOME_HERO_TOKEN_PREFIX)) return null
    val payload = raw.removePrefix(HOME_HERO_TOKEN_PREFIX)
    val typeSeparator = payload.indexOf(':')
    if (typeSeparator <= 0) return null
    val idSeparator = payload.indexOf(':', startIndex = typeSeparator + 1)
    if (idSeparator <= typeSeparator + 1) return null
    val type = when (payload.substring(0, typeSeparator)) {
        "m" -> ContentType.MOVIE
        "s" -> ContentType.SERIES
        else -> return null
    }
    val contentId = payload.substring(typeSeparator + 1, idSeparator).toIntOrNull() ?: return null
    val label = payload.substring(idSeparator + 1).trim()
    return HomeHeroMetadataToken(type = type, contentId = contentId, label = label)
}

internal class HomeHeroMetadataStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val movieClient = MovieCardMetadataClient()
    private val seriesStore = SeriesCardMetadataStore.get(appContext)
    private val lastMovieAttemptAtMs = mutableMapOf<ContentMetadataRequestKey, Long>()

    fun currentOwner(): AuthenticatedSessionOwner? = AuthenticatedSessionRegistry.currentOwner()

    fun isCurrent(owner: AuthenticatedSessionOwner): Boolean =
        AuthenticatedSessionRegistry.isCurrent(owner)

    fun publishIfCurrent(owner: AuthenticatedSessionOwner, publish: () -> Unit): Boolean =
        AuthenticatedSessionRegistry.withCurrentOwner(owner) {
            publish()
            true
        } ?: false

    fun cached(
        owner: AuthenticatedSessionOwner?,
        item: ContentItem,
    ): HomeHeroTechnicalMetadata = cached(owner, item.type, item.id)

    fun cached(
        owner: AuthenticatedSessionOwner?,
        type: ContentType,
        contentId: Int,
    ): HomeHeroTechnicalMetadata {
        if (owner == null || !isCurrent(owner)) return HomeHeroTechnicalMetadata()
        val cached = when (type) {
            ContentType.MOVIE -> readMovieCached(owner, contentId)
            ContentType.SERIES -> readSeriesCached(owner, contentId)
            ContentType.LIVE -> HomeHeroTechnicalMetadata()
        }
        return cached.takeIf { isCurrent(owner) } ?: HomeHeroTechnicalMetadata()
    }

    suspend fun metadata(
        owner: AuthenticatedSessionOwner,
        item: ContentItem,
    ): HomeHeroTechnicalMetadata = metadata(owner, item.type, item.id)

    suspend fun metadata(
        owner: AuthenticatedSessionOwner,
        type: ContentType,
        contentId: Int,
    ): HomeHeroTechnicalMetadata = when (type) {
        ContentType.MOVIE -> movieMetadata(owner, contentId)
        ContentType.SERIES -> seriesMetadata(owner, contentId)
        ContentType.LIVE -> HomeHeroTechnicalMetadata()
    }

    fun cacheMovieMetadata(
        owner: AuthenticatedSessionOwner,
        movieId: Int,
        quality: String? = null,
        durationMs: Long? = null,
    ): Boolean {
        if (quality == null && durationMs == null) return false
        return AuthenticatedSessionRegistry.withCurrentOwner(owner) {
            moviePreferences(owner).edit().apply {
                quality?.trim()?.takeIf(String::isNotBlank)?.let {
                    putString("movie:$movieId:quality", it)
                }
                durationMs?.takeIf { it > 0L }?.let {
                    putLong("movie:$movieId:duration_ms", it)
                }
            }.apply()
            true
        } ?: false
    }

    private suspend fun movieMetadata(
        owner: AuthenticatedSessionOwner,
        movieId: Int,
    ): HomeHeroTechnicalMetadata {
        if (!isCurrent(owner)) return HomeHeroTechnicalMetadata()
        val cached = readMovieCached(owner, movieId)
        if (cached.quality != null && (cached.durationMs ?: 0L) > 0L) {
            return cached.takeIf { isCurrent(owner) } ?: HomeHeroTechnicalMetadata()
        }

        val requestKey = owner.contentMetadataKey(ContentType.MOVIE, movieId)
        val now = System.currentTimeMillis()
        val lastAttempt = synchronized(lastMovieAttemptAtMs) {
            val previous = lastMovieAttemptAtMs[requestKey] ?: 0L
            if (now - previous >= RETRY_COOLDOWN_MS) {
                lastMovieAttemptAtMs[requestKey] = now
            }
            previous
        }
        if (now - lastAttempt < RETRY_COOLDOWN_MS) {
            return cached.takeIf { isCurrent(owner) } ?: HomeHeroTechnicalMetadata()
        }

        val fetched = try {
            movieClient.fetch(owner.session, movieId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MovieCardTechnicalMetadata()
        }
        val merged = HomeHeroTechnicalMetadata(
            quality = fetched.quality ?: cached.quality,
            durationMs = fetched.durationMs ?: cached.durationMs,
        )
        if (!isCurrent(owner)) return HomeHeroTechnicalMetadata()
        if (merged.quality != null || merged.durationMs != null) {
            val cachedForOwner = cacheMovieMetadata(
                owner = owner,
                movieId = movieId,
                quality = merged.quality,
                durationMs = merged.durationMs,
            )
            if (!cachedForOwner) return HomeHeroTechnicalMetadata()
        }
        return merged.takeIf { isCurrent(owner) } ?: HomeHeroTechnicalMetadata()
    }

    private suspend fun seriesMetadata(
        owner: AuthenticatedSessionOwner,
        seriesId: Int,
    ): HomeHeroTechnicalMetadata {
        if (!isCurrent(owner)) return HomeHeroTechnicalMetadata()
        val metadata = seriesStore.metadata(owner, seriesId)
        if (!isCurrent(owner)) return HomeHeroTechnicalMetadata()
        return HomeHeroTechnicalMetadata(
            quality = metadata.quality,
            seasonCount = metadata.seasonCount,
            episodeCount = metadata.episodeCount,
        )
    }

    private fun readMovieCached(
        owner: AuthenticatedSessionOwner,
        movieId: Int,
    ): HomeHeroTechnicalMetadata =
        HomeHeroTechnicalMetadata(
            quality = moviePreferences(owner)
                .getString("movie:$movieId:quality", null)
                ?.trim()
                ?.takeIf(String::isNotBlank),
            durationMs = moviePreferences(owner)
                .getLong("movie:$movieId:duration_ms", 0L)
                .takeIf { it > 0L },
        )

    private fun readSeriesCached(
        owner: AuthenticatedSessionOwner,
        seriesId: Int,
    ): HomeHeroTechnicalMetadata =
        HomeHeroTechnicalMetadata(
            quality = seriesPreferences(owner)
                .getString("series:$seriesId:quality", null)
                ?.trim()
                ?.takeIf(String::isNotBlank),
            seasonCount = seriesPreferences(owner)
                .getInt("series:$seriesId:season_count", 0)
                .takeIf { it > 0 },
            episodeCount = seriesPreferences(owner)
                .getInt("series:$seriesId:episode_count", 0)
                .takeIf { it > 0 },
        )

    private fun moviePreferences(owner: AuthenticatedSessionOwner) = appContext.getSharedPreferences(
        authenticatedOwnerPreferencesName(MOVIE_PREFERENCES, owner),
        Context.MODE_PRIVATE,
    )

    private fun seriesPreferences(owner: AuthenticatedSessionOwner) = appContext.getSharedPreferences(
        authenticatedOwnerPreferencesName(SERIES_PREFERENCES, owner),
        Context.MODE_PRIVATE,
    )

    companion object {
        private const val MOVIE_PREFERENCES = "movie_card_verified_metadata"
        private const val SERIES_PREFERENCES = "series_card_verified_metadata"
        private const val RETRY_COOLDOWN_MS = 30_000L

        @Volatile
        private var instance: HomeHeroMetadataStore? = null

        fun get(context: Context): HomeHeroMetadataStore = instance
            ?: synchronized(this) {
                instance ?: HomeHeroMetadataStore(context).also { instance = it }
            }
    }
}
