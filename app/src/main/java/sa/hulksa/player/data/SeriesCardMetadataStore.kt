package sa.hulksa.player.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import sa.hulksa.player.model.ContentType

class SeriesCardMetadataStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val client = SeriesCardMetadataClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val inFlight =
        mutableMapOf<ContentMetadataRequestKey, Deferred<SeriesCardTechnicalMetadata>>()
    private val lastAttemptAtMs = mutableMapOf<ContentMetadataRequestKey, Long>()

    internal fun currentOwner(): AuthenticatedSessionOwner? =
        AuthenticatedSessionRegistry.currentOwner()

    internal fun publishIfCurrent(
        owner: AuthenticatedSessionOwner,
        publish: () -> Unit,
    ): Boolean =
        AuthenticatedSessionRegistry.withCurrentOwner(owner) {
            publish()
            true
        } ?: false

    internal fun cached(
        owner: AuthenticatedSessionOwner?,
        seriesId: Int,
    ): SeriesCardTechnicalMetadata {
        if (owner == null || !AuthenticatedSessionRegistry.isCurrent(owner)) {
            return SeriesCardTechnicalMetadata()
        }
        val cached = readCached(owner, seriesId) ?: SeriesCardTechnicalMetadata()
        return cached.takeIf { AuthenticatedSessionRegistry.isCurrent(owner) }
            ?: SeriesCardTechnicalMetadata()
    }

    suspend fun metadata(seriesId: Int): SeriesCardTechnicalMetadata {
        val owner = currentOwner() ?: return SeriesCardTechnicalMetadata()
        return metadata(owner, seriesId)
    }

    internal suspend fun metadata(
        owner: AuthenticatedSessionOwner,
        seriesId: Int,
    ): SeriesCardTechnicalMetadata {
        if (!AuthenticatedSessionRegistry.isCurrent(owner)) {
            return SeriesCardTechnicalMetadata()
        }
        val cached = readCached(owner, seriesId)
        if (cached?.isComplete() == true) {
            return cached.takeIf { AuthenticatedSessionRegistry.isCurrent(owner) }
                ?: SeriesCardTechnicalMetadata()
        }

        val requestKey = owner.contentMetadataKey(ContentType.SERIES, seriesId)
        val deferred = synchronized(inFlight) {
            inFlight[requestKey] ?: run {
                val now = System.currentTimeMillis()
                val lastAttempt = lastAttemptAtMs[requestKey] ?: 0L
                if (now - lastAttempt < RETRY_COOLDOWN_MS) {
                    return cached
                        ?.takeIf { AuthenticatedSessionRegistry.isCurrent(owner) }
                        ?: SeriesCardTechnicalMetadata()
                }
                lastAttemptAtMs[requestKey] = now
                scope.async {
                    semaphore.withPermit { fetchAndCache(owner, seriesId, cached) }
                }.also { inFlight[requestKey] = it }
            }
        }

        val result = try {
            deferred.await()
        } finally {
            synchronized(inFlight) {
                if (inFlight[requestKey] === deferred) {
                    inFlight.remove(requestKey)
                }
            }
        }
        return result.takeIf { AuthenticatedSessionRegistry.isCurrent(owner) }
            ?: SeriesCardTechnicalMetadata()
    }

    private suspend fun fetchAndCache(
        owner: AuthenticatedSessionOwner,
        seriesId: Int,
        cached: SeriesCardTechnicalMetadata?,
    ): SeriesCardTechnicalMetadata {
        val fetched = try {
            client.fetch(
                portal = owner.session.portal,
                credentials = owner.session.credentials,
                seriesId = seriesId,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SeriesCardTechnicalMetadata()
        }

        val merged = SeriesCardTechnicalMetadata(
            quality = fetched.quality ?: cached?.quality,
            seasonCount = fetched.seasonCount ?: cached?.seasonCount,
            episodeCount = fetched.episodeCount ?: cached?.episodeCount,
        )

        if (merged.quality != null || merged.seasonCount != null || merged.episodeCount != null) {
            val cachedForOwner = AuthenticatedSessionRegistry.withCurrentOwner(owner) {
                preferences(owner).edit().apply {
                    merged.quality?.let { putString("series:$seriesId:quality", it) }
                    merged.seasonCount
                        ?.takeIf { it > 0 }
                        ?.let { putInt("series:$seriesId:season_count", it) }
                    merged.episodeCount
                        ?.takeIf { it > 0 }
                        ?.let { putInt("series:$seriesId:episode_count", it) }
                }.apply()
                true
            } ?: false
            if (!cachedForOwner) return SeriesCardTechnicalMetadata()
        }
        return merged.takeIf { AuthenticatedSessionRegistry.isCurrent(owner) }
            ?: SeriesCardTechnicalMetadata()
    }

    private fun readCached(
        owner: AuthenticatedSessionOwner,
        seriesId: Int,
    ): SeriesCardTechnicalMetadata? {
        val preferences = preferences(owner)
        val quality = preferences
            .getString("series:$seriesId:quality", null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val seasonCount = preferences
            .getInt("series:$seriesId:season_count", 0)
            .takeIf { it > 0 }
        val episodeCount = preferences
            .getInt("series:$seriesId:episode_count", 0)
            .takeIf { it > 0 }
        if (quality == null && seasonCount == null && episodeCount == null) return null
        return SeriesCardTechnicalMetadata(
            quality = quality,
            seasonCount = seasonCount,
            episodeCount = episodeCount,
        )
    }

    private fun preferences(owner: AuthenticatedSessionOwner) = appContext.getSharedPreferences(
        authenticatedOwnerPreferencesName(PREFERENCES, owner),
        Context.MODE_PRIVATE,
    )

    private fun SeriesCardTechnicalMetadata.isComplete(): Boolean =
        !quality.isNullOrBlank() && (seasonCount ?: 0) > 0 && (episodeCount ?: 0) > 0

    companion object {
        private const val PREFERENCES = "series_card_verified_metadata"
        private const val MAX_CONCURRENT_REQUESTS = 2
        private const val RETRY_COOLDOWN_MS = 1_000L

        @Volatile
        private var instance: SeriesCardMetadataStore? = null

        fun get(context: Context): SeriesCardMetadataStore = instance
            ?: synchronized(this) {
                instance ?: SeriesCardMetadataStore(context).also { instance = it }
            }
    }
}
