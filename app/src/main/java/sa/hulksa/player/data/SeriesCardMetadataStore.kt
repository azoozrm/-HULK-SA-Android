package sa.hulksa.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import sa.hulksa.player.security.CredentialVault

class SeriesCardMetadataStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val portalResolver = PortalResolver()
    private val credentialVault = CredentialVault(appContext)
    private val client = SeriesCardMetadataClient()
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val inFlight = mutableMapOf<Int, Deferred<SeriesCardTechnicalMetadata>>()
    private val lastAttemptAtMs = mutableMapOf<Int, Long>()

    suspend fun metadata(seriesId: Int): SeriesCardTechnicalMetadata {
        val cached = readCached(seriesId)
        if (cached?.isComplete() == true) return cached

        val deferred = synchronized(inFlight) {
            inFlight[seriesId] ?: run {
                val now = System.currentTimeMillis()
                val lastAttempt = lastAttemptAtMs[seriesId] ?: 0L
                if (now - lastAttempt < RETRY_COOLDOWN_MS) {
                    return cached ?: SeriesCardTechnicalMetadata()
                }
                lastAttemptAtMs[seriesId] = now
                scope.async {
                    semaphore.withPermit { fetchAndCache(seriesId, cached) }
                }.also { inFlight[seriesId] = it }
            }
        }

        return try {
            deferred.await()
        } finally {
            synchronized(inFlight) {
                if (inFlight[seriesId] === deferred) {
                    inFlight.remove(seriesId)
                }
            }
        }
    }

    private suspend fun fetchAndCache(
        seriesId: Int,
        cached: SeriesCardTechnicalMetadata?,
    ): SeriesCardTechnicalMetadata {
        val credentials = credentialVault.load() ?: return cached ?: SeriesCardTechnicalMetadata()
        val portal = AuthenticatedSessionRegistry.current()
            ?.takeIf { it.credentials == credentials }
            ?.portal
            ?: runCatching { portalResolver.resolve(credentials.accessCode) }
                .getOrNull()
            ?: return cached ?: SeriesCardTechnicalMetadata()

        val fetched = runCatching {
            client.fetch(
                portal = portal,
                credentials = credentials,
                seriesId = seriesId,
            )
        }.getOrDefault(SeriesCardTechnicalMetadata())

        val merged = SeriesCardTechnicalMetadata(
            quality = fetched.quality ?: cached?.quality,
            seasonCount = fetched.seasonCount ?: cached?.seasonCount,
        )

        if (merged.quality != null || merged.seasonCount != null) {
            preferences.edit().apply {
                merged.quality?.let { putString("series:$seriesId:quality", it) }
                merged.seasonCount
                    ?.takeIf { it > 0 }
                    ?.let { putInt("series:$seriesId:season_count", it) }
            }.apply()
        }
        return merged
    }

    private fun readCached(seriesId: Int): SeriesCardTechnicalMetadata? {
        val quality = preferences
            .getString("series:$seriesId:quality", null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val seasonCount = preferences
            .getInt("series:$seriesId:season_count", 0)
            .takeIf { it > 0 }
        if (quality == null && seasonCount == null) return null
        return SeriesCardTechnicalMetadata(
            quality = quality,
            seasonCount = seasonCount,
        )
    }

    private fun SeriesCardTechnicalMetadata.isComplete(): Boolean =
        !quality.isNullOrBlank() && (seasonCount ?: 0) > 0

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
