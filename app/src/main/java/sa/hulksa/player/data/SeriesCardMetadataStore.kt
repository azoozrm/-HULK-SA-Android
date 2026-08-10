package sa.hulksa.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.security.CredentialVault

class SeriesCardMetadataStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val portalResolver = PortalResolver(appContext)
    private val credentialVault = CredentialVault(appContext)
    private val client = SeriesCardMetadataClient()
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val inFlight = mutableMapOf<Int, Deferred<SeriesCardTechnicalMetadata>>()
    private val attemptedThisProcess = mutableSetOf<Int>()

    @Volatile
    private var resolvedPortal: PortalConfig? = null

    suspend fun metadata(seriesId: Int): SeriesCardTechnicalMetadata {
        readCached(seriesId)?.let { return it }

        val deferred = synchronized(inFlight) {
            inFlight[seriesId] ?: run {
                if (seriesId in attemptedThisProcess) {
                    return SeriesCardTechnicalMetadata()
                }
                attemptedThisProcess += seriesId
                scope.async {
                    semaphore.withPermit { fetchAndCache(seriesId) }
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

    private suspend fun fetchAndCache(seriesId: Int): SeriesCardTechnicalMetadata {
        val credentials = credentialVault.load() ?: return SeriesCardTechnicalMetadata()
        val portal = resolvedPortal ?: runCatching { portalResolver.resolve() }
            .getOrNull()
            ?.also { resolvedPortal = it }
            ?: return SeriesCardTechnicalMetadata()

        val metadata = runCatching {
            client.fetch(
                portal = portal,
                credentials = credentials,
                seriesId = seriesId,
            )
        }.getOrDefault(SeriesCardTechnicalMetadata())

        if (metadata.quality != null || metadata.seasonCount != null) {
            preferences.edit().apply {
                metadata.quality?.let { putString("series:$seriesId:quality", it) }
                metadata.seasonCount
                    ?.takeIf { it > 0 }
                    ?.let { putInt("series:$seriesId:season_count", it) }
            }.apply()
        }
        return metadata
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

    companion object {
        private const val PREFERENCES = "series_card_verified_metadata"
        private const val MAX_CONCURRENT_REQUESTS = 2

        @Volatile
        private var instance: SeriesCardMetadataStore? = null

        fun get(context: Context): SeriesCardMetadataStore = instance
            ?: synchronized(this) {
                instance ?: SeriesCardMetadataStore(context).also { instance = it }
            }
    }
}
