package sa.hulksa.player.data

import android.content.Context
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
    private val moviePreferences =
        appContext.getSharedPreferences(MOVIE_PREFERENCES, Context.MODE_PRIVATE)
    private val seriesPreferences =
        appContext.getSharedPreferences(SERIES_PREFERENCES, Context.MODE_PRIVATE)
    private val seriesStore = SeriesCardMetadataStore.get(appContext)
    private val lastMovieAttemptAtMs = mutableMapOf<Int, Long>()

    fun cached(item: ContentItem): HomeHeroTechnicalMetadata = cached(item.type, item.id)

    fun cached(type: ContentType, contentId: Int): HomeHeroTechnicalMetadata = when (type) {
        ContentType.MOVIE -> readMovieCached(contentId)
        ContentType.SERIES -> readSeriesCached(contentId)
        ContentType.LIVE -> HomeHeroTechnicalMetadata()
    }

    suspend fun metadata(item: ContentItem): HomeHeroTechnicalMetadata = metadata(item.type, item.id)

    suspend fun metadata(type: ContentType, contentId: Int): HomeHeroTechnicalMetadata = when (type) {
        ContentType.MOVIE -> movieMetadata(contentId)
        ContentType.SERIES -> seriesMetadata(contentId)
        ContentType.LIVE -> HomeHeroTechnicalMetadata()
    }

    private suspend fun movieMetadata(movieId: Int): HomeHeroTechnicalMetadata {
        val cached = readMovieCached(movieId)
        if (cached.quality != null && (cached.durationMs ?: 0L) > 0L) return cached

        val now = System.currentTimeMillis()
        val lastAttempt = synchronized(lastMovieAttemptAtMs) {
            val previous = lastMovieAttemptAtMs[movieId] ?: 0L
            if (now - previous >= RETRY_COOLDOWN_MS) {
                lastMovieAttemptAtMs[movieId] = now
            }
            previous
        }
        if (now - lastAttempt < RETRY_COOLDOWN_MS) return cached

        val session = AuthenticatedSessionRegistry.current() ?: return cached
        val fetched = runCatching { movieClient.fetch(session, movieId) }
            .getOrDefault(MovieCardTechnicalMetadata())
        val merged = HomeHeroTechnicalMetadata(
            quality = fetched.quality ?: cached.quality,
            durationMs = fetched.durationMs ?: cached.durationMs,
        )
        if (merged.quality != null || merged.durationMs != null) {
            moviePreferences.edit().apply {
                merged.quality?.let { putString("movie:$movieId:quality", it) }
                merged.durationMs
                    ?.takeIf { it > 0L }
                    ?.let { putLong("movie:$movieId:duration_ms", it) }
            }.apply()
        }
        return merged
    }

    private suspend fun seriesMetadata(seriesId: Int): HomeHeroTechnicalMetadata {
        val metadata = seriesStore.metadata(seriesId)
        return HomeHeroTechnicalMetadata(
            quality = metadata.quality,
            seasonCount = metadata.seasonCount,
            episodeCount = metadata.episodeCount,
        )
    }

    private fun readMovieCached(movieId: Int): HomeHeroTechnicalMetadata =
        HomeHeroTechnicalMetadata(
            quality = moviePreferences
                .getString("movie:$movieId:quality", null)
                ?.trim()
                ?.takeIf(String::isNotBlank),
            durationMs = moviePreferences
                .getLong("movie:$movieId:duration_ms", 0L)
                .takeIf { it > 0L },
        )

    private fun readSeriesCached(seriesId: Int): HomeHeroTechnicalMetadata =
        HomeHeroTechnicalMetadata(
            quality = seriesPreferences
                .getString("series:$seriesId:quality", null)
                ?.trim()
                ?.takeIf(String::isNotBlank),
            seasonCount = seriesPreferences
                .getInt("series:$seriesId:season_count", 0)
                .takeIf { it > 0 },
            episodeCount = seriesPreferences
                .getInt("series:$seriesId:episode_count", 0)
                .takeIf { it > 0 },
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
