package sa.hulksa.player.data

import android.content.Context
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

internal data class HomeHeroTechnicalMetadata(
    val quality: String? = null,
    val durationMs: Long? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
)

internal class HomeHeroMetadataStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val movieClient = MovieCardMetadataClient()
    private val moviePreferences =
        appContext.getSharedPreferences(MOVIE_PREFERENCES, Context.MODE_PRIVATE)
    private val seriesPreferences =
        appContext.getSharedPreferences(SERIES_PREFERENCES, Context.MODE_PRIVATE)
    private val seriesStore = SeriesCardMetadataStore.get(appContext)
    private val lastMovieAttemptAtMs = mutableMapOf<Int, Long>()

    fun cached(item: ContentItem): HomeHeroTechnicalMetadata = when (item.type) {
        ContentType.MOVIE -> readMovieCached(item.id)
        ContentType.SERIES -> readSeriesCached(item.id)
        ContentType.LIVE -> HomeHeroTechnicalMetadata()
    }

    suspend fun metadata(item: ContentItem): HomeHeroTechnicalMetadata = when (item.type) {
        ContentType.MOVIE -> movieMetadata(item.id)
        ContentType.SERIES -> seriesMetadata(item.id)
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
