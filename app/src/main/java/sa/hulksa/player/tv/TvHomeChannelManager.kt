package sa.hulksa.player.tv

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sa.hulksa.player.R
import sa.hulksa.player.data.AccountScopeStore
import sa.hulksa.player.data.KidsContentFilterStore
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.data.UserLibrary
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.ProfileKind

sealed interface TvPlatformSyncResult {
    data object Unsupported : TvPlatformSyncResult
    data class Synced(
        val channelId: Long,
        val previewProgramCount: Int,
        val watchNextProgramCount: Int,
    ) : TvPlatformSyncResult
    data class Cleared(val removedProgramCount: Int) : TvPlatformSyncResult
    data class Failed(val reason: String) : TvPlatformSyncResult
}

/**
 * Small Android TV provider adapter. All provider I/O is serialized and performed off the main
 * thread; no worker, service, polling loop or program-specific network request is involved.
 */
// tvprovider exposes its public PreviewProgram/WatchNextProgram API through restricted base types.
@SuppressLint("RestrictedApi")
class TvHomeChannelManager(context: Context) {
    private val appContext = context.applicationContext
    private val helper = PreviewChannelHelper(appContext)
    private val preferences = TvPlatformPreferences(appContext)

    suspend fun sync(
        scope: TvProfileScope,
        history: List<HistoryEntry>,
        verifiedKidsContentKeys: Set<String>,
    ): TvPlatformSyncResult = providerOperation {
        val suppressed = preferences.suppressedProviderIds(scope.profileId)
        val desired = TvContinueWatchingMapper.map(
            scope = scope,
            history = history,
            verifiedKidsContentKeys = verifiedKidsContentKeys,
            suppressedProviderIds = suppressed,
        )
        val channelId = ensureChannel()
        if (channelId < 0L) throw IOException("Android TV channel insertion failed")
        reconcilePreviewPrograms(channelId, desired)
        reconcileWatchNextPrograms(desired)
        TvPlatformSyncResult.Synced(
            channelId = channelId,
            previewProgramCount = desired.size,
            watchNextProgramCount = desired.size,
        )
    }

    suspend fun clearPublishedPrograms(): TvPlatformSyncResult = providerOperation {
        var removed = 0
        findChannelId()?.let { channelId ->
            queryPreviewPrograms(channelId).forEach { program ->
                helper.deletePreviewProgram(program.id)
                removed++
            }
        }
        queryWatchNextPrograms().forEach { program ->
            appContext.contentResolver.delete(
                TvContractCompat.buildWatchNextProgramUri(program.id),
                null,
                null,
            )
            removed++
        }
        TvPlatformSyncResult.Cleared(removed)
    }

    suspend fun handleProgramDisabled(action: String?, programId: Long) {
        if (programId < 0L || !supportsTvProvider()) return
        withContext(Dispatchers.IO) {
            providerMutex.withLock {
                val providerId = when (action) {
                    TvContractCompat.ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED ->
                        helper.getPreviewProgram(programId)?.tvProviderId()
                    TvContractCompat.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED ->
                        helper.getWatchNextProgram(programId)?.tvProviderId()
                    else -> null
                }

                if (providerId == null) return@withLock
                if (AccountScopeStore(appContext).activeAccountId() != null) {
                    preferences.suppress(ProfileStore(appContext).activeProfileId(), providerId)
                }
                when (action) {
                    TvContractCompat.ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED ->
                        helper.deletePreviewProgram(programId)
                    TvContractCompat.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED ->
                        appContext.contentResolver.delete(
                            TvContractCompat.buildWatchNextProgramUri(programId),
                            null,
                            null,
                        )
                }
                deleteMatchingPrograms(providerId)
            }
        }
    }

    private suspend fun providerOperation(
        operation: () -> TvPlatformSyncResult,
    ): TvPlatformSyncResult {
        if (!supportsTvProvider()) return TvPlatformSyncResult.Unsupported
        return withContext(Dispatchers.IO) {
            providerMutex.withLock {
                try {
                    operation()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    TvPlatformSyncResult.Failed(
                        error.message?.take(160) ?: error::class.java.simpleName,
                    )
                }
            }
        }
    }

    private fun supportsTvProvider(): Boolean {
        val uiMode = appContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val packageManager = appContext.packageManager
        val isTv = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        val hasProvider = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && runCatching {
            packageManager.resolveContentProvider(TvContractCompat.AUTHORITY, 0) != null
        }.getOrDefault(false)
        return isTvPlatformSupported(isTv, Build.VERSION.SDK_INT, hasProvider)
    }

    private fun ensureChannel(): Long {
        findChannelId()?.let { return it }
        val logo = ContextCompat.getDrawable(appContext, R.mipmap.ic_launcher_tv)?.toBitmap()
            ?: throw IOException("HULK SA TV channel logo is unavailable")
        val channel = PreviewChannel.Builder()
            .setDisplayName(CHANNEL_NAME)
            .setDescription(CONTINUE_WATCHING_LABEL)
            .setAppLinkIntentUri(Uri.parse(TvDeepLinkRouter.uri(TvDeepLinkTarget.Home)))
            .setInternalProviderId(CHANNEL_PROVIDER_ID)
            .setLogo(logo)
            .build()
        val channelId = helper.publishDefaultChannel(channel)
        if (channelId >= 0L) preferences.setChannelId(channelId)
        return channelId
    }

    private fun findChannelId(): Long? {
        val storedId = preferences.channelId()
        if (storedId >= 0L) {
            val stored = runCatching { helper.getPreviewChannel(storedId) }.getOrNull()
            if (stored?.internalProviderId == CHANNEL_PROVIDER_ID) return storedId
            preferences.clearChannelId()
        }
        val discovered = helper.getAllChannels()
            .firstOrNull { it.internalProviderId == CHANNEL_PROVIDER_ID }
            ?.id
            ?.takeIf { it >= 0L }
        if (discovered != null) preferences.setChannelId(discovered)
        return discovered
    }

    private fun reconcilePreviewPrograms(
        channelId: Long,
        desired: List<TvContinueWatchingItem>,
    ) {
        val plan = planTvProgramSync(queryPreviewPrograms(channelId), desired)
        plan.deleteIds.forEach(helper::deletePreviewProgram)
        plan.upserts.forEach { upsert ->
            val program = buildPreviewProgram(channelId, upsert.item)
            if (upsert.existingId == null) {
                if (helper.publishPreviewProgram(program) < 0L) {
                    throw IOException("Android TV preview program insertion failed")
                }
            } else {
                helper.updatePreviewProgram(upsert.existingId, program)
            }
        }
    }

    private fun reconcileWatchNextPrograms(desired: List<TvContinueWatchingItem>) {
        val plan = planTvProgramSync(queryWatchNextPrograms(), desired)
        plan.deleteIds.forEach { id ->
            appContext.contentResolver.delete(
                TvContractCompat.buildWatchNextProgramUri(id),
                null,
                null,
            )
        }
        plan.upserts.forEach { upsert ->
            val program = buildWatchNextProgram(upsert.item)
            if (upsert.existingId == null) {
                if (helper.publishWatchNextProgram(program) < 0L) {
                    throw IOException("Android TV Watch Next insertion failed")
                }
            } else {
                helper.updateWatchNextProgram(program, upsert.existingId)
            }
        }
    }

    private fun queryPreviewPrograms(channelId: Long): List<ExistingTvProgram> {
        val uri = TvContractCompat.buildPreviewProgramsUriForChannel(channelId)
        return buildList {
            appContext.contentResolver.query(
                uri,
                PreviewProgram.PROJECTION,
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val program = PreviewProgram.fromCursor(cursor)
                    add(
                        ExistingTvProgram(
                            id = program.id,
                            providerId = program.tvProviderId().orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    private fun queryWatchNextPrograms(): List<ExistingTvProgram> = buildList {
        appContext.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION,
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val program = WatchNextProgram.fromCursor(cursor)
                val providerId = program.tvProviderId()
                if (providerId != null) {
                    add(ExistingTvProgram(id = program.id, providerId = providerId))
                }
            }
        }
    }

    private fun deleteMatchingPrograms(providerId: String) {
        findChannelId()?.let { channelId ->
            queryPreviewPrograms(channelId)
                .filter { it.providerId == providerId }
                .forEach { helper.deletePreviewProgram(it.id) }
        }
        queryWatchNextPrograms()
            .filter { it.providerId == providerId }
            .forEach {
                appContext.contentResolver.delete(
                    TvContractCompat.buildWatchNextProgramUri(it.id),
                    null,
                    null,
                )
            }
    }

    private fun buildPreviewProgram(
        channelId: Long,
        item: TvContinueWatchingItem,
    ): PreviewProgram {
        val builder = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(item.programType())
            .setTitle(item.title)
            .setDescription(CONTINUE_WATCHING_LABEL)
            .setPosterArtUri(item.safePosterUri())
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_MOVIE_POSTER)
            .setIntentUri(Uri.parse(item.deepLinkUri))
            .setInternalProviderId(item.providerId)
            .setContentId(item.providerId)
            .setDurationMillis(item.durationAsInt())
            .setLastPlaybackPositionMillis(item.positionAsInt())
            .setWeight(item.weight)
        builder.applyEpisodeMetadata(item)
        return builder.build()
    }

    private fun buildWatchNextProgram(item: TvContinueWatchingItem): WatchNextProgram {
        val builder = WatchNextProgram.Builder()
            .setType(item.programType())
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(item.updatedAtEpochMs)
            .setTitle(item.title)
            .setDescription(CONTINUE_WATCHING_LABEL)
            .setPosterArtUri(item.safePosterUri())
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_MOVIE_POSTER)
            .setIntentUri(Uri.parse(item.deepLinkUri))
            .setInternalProviderId(item.providerId)
            .setContentId(item.providerId)
            .setDurationMillis(item.durationAsInt())
            .setLastPlaybackPositionMillis(item.positionAsInt())
        builder.applyEpisodeMetadata(item)
        return builder.build()
    }

    private fun PreviewProgram.Builder.applyEpisodeMetadata(item: TvContinueWatchingItem) {
        if (item.type != TvContinueWatchingType.EPISODE) return
        setSeriesId(item.seriesId?.toString())
        item.seasonNumber?.takeIf { it >= 0 }?.let { setSeasonNumber(it) }
        item.episodeNumber?.takeIf { it >= 0 }?.let { setEpisodeNumber(it) }
        item.episodeTitle?.let { setEpisodeTitle(it) }
    }

    private fun WatchNextProgram.Builder.applyEpisodeMetadata(item: TvContinueWatchingItem) {
        if (item.type != TvContinueWatchingType.EPISODE) return
        setSeriesId(item.seriesId?.toString())
        item.seasonNumber?.takeIf { it >= 0 }?.let { setSeasonNumber(it) }
        item.episodeNumber?.takeIf { it >= 0 }?.let { setEpisodeNumber(it) }
        item.episodeTitle?.let { setEpisodeTitle(it) }
    }

    private fun TvContinueWatchingItem.programType(): Int = when (type) {
        TvContinueWatchingType.MOVIE -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
        TvContinueWatchingType.EPISODE -> TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
    }

    private fun PreviewProgram.tvProviderId(): String? =
        internalProviderId?.takeIf { it.startsWith(TV_PROGRAM_PROVIDER_PREFIX) }
            ?: contentId?.takeIf { it.startsWith(TV_PROGRAM_PROVIDER_PREFIX) }

    private fun WatchNextProgram.tvProviderId(): String? =
        internalProviderId?.takeIf { it.startsWith(TV_PROGRAM_PROVIDER_PREFIX) }
            ?: contentId?.takeIf { it.startsWith(TV_PROGRAM_PROVIDER_PREFIX) }

    private fun TvContinueWatchingItem.safePosterUri(): Uri {
        val fallback = Uri.parse("android.resource://${appContext.packageName}/${R.mipmap.ic_launcher_tv}")
        val raw = posterUrl?.trim()?.takeIf(String::isNotEmpty) ?: return fallback
        return runCatching {
            val parsed = Uri.parse(raw)
            val schemeAllowed = parsed.scheme.equals("http", true) || parsed.scheme.equals("https", true)
            val sensitiveQuery = parsed.query.orEmpty().lowercase().let { query ->
                listOf("username=", "password=", "access_code=", "token=").any(query::contains)
            }
            parsed.takeIf {
                schemeAllowed && !parsed.host.isNullOrBlank() && parsed.userInfo == null && !sensitiveQuery
            } ?: fallback
        }.getOrDefault(fallback)
    }

    private fun TvContinueWatchingItem.durationAsInt(): Int =
        durationMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    private fun TvContinueWatchingItem.positionAsInt(): Int =
        positionMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private companion object {
        const val CHANNEL_NAME = "HULK SA"
        const val CONTINUE_WATCHING_LABEL = "أكمل المشاهدة"
        const val CHANNEL_PROVIDER_ID = "hulk:v230:continue-watching-channel"
        val providerMutex = Mutex()
    }
}

class TvPlatformIntegration(context: Context) {
    private val appContext = context.applicationContext
    private val accountScopeStore = AccountScopeStore(appContext)
    private val profileStore = ProfileStore(appContext)
    private val userLibrary = UserLibrary(appContext)
    private val kidsContentFilterStore = KidsContentFilterStore(appContext)
    private val channelManager = TvHomeChannelManager(appContext)

    suspend fun syncActiveProfile(): TvPlatformSyncResult = withContext(Dispatchers.IO) {
        val accountId = accountScopeStore.activeAccountId()
            ?: return@withContext channelManager.clearPublishedPrograms()
        val profile = profileStore.activeProfile()
        channelManager.sync(
            scope = TvProfileScope(
                accountId = accountId,
                profileId = profile.id,
                profileKind = profile.kind,
            ),
            history = userLibrary.history(),
            verifiedKidsContentKeys = if (profile.kind == ProfileKind.KIDS) {
                kidsContentFilterStore.allowedKeys()
            } else {
                emptySet()
            },
        )
    }

    suspend fun clearUserContent(): TvPlatformSyncResult =
        channelManager.clearPublishedPrograms()
}

private class TvPlatformPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val global = appContext.getSharedPreferences(GLOBAL_PREFERENCES, Context.MODE_PRIVATE)
    private val accountScope = AccountScopeStore(appContext)
    private val scoped
        get() = accountScope.preferences(SCOPED_PREFERENCES)

    fun channelId(): Long = global.getLong(KEY_CHANNEL_ID, -1L)

    fun setChannelId(channelId: Long) {
        global.edit().putLong(KEY_CHANNEL_ID, channelId).apply()
    }

    fun clearChannelId() {
        global.edit().remove(KEY_CHANNEL_ID).apply()
    }

    fun suppressedProviderIds(profileId: String): Set<String> =
        scoped.getStringSet(suppressedKey(profileId), emptySet()).orEmpty().toSet()

    fun suppress(profileId: String, providerId: String) {
        if (!providerId.startsWith(TV_PROGRAM_PROVIDER_PREFIX)) return
        val updated = suppressedProviderIds(profileId) + providerId
        scoped.edit().putStringSet(suppressedKey(profileId), updated).apply()
    }

    private fun suppressedKey(profileId: String): String =
        "profile:${profileId.trim()}:suppressed_programs"

    private companion object {
        const val GLOBAL_PREFERENCES = "hulk_tv_platform_v230"
        const val SCOPED_PREFERENCES = "hulk_tv_platform_v230_suppressed"
        const val KEY_CHANNEL_ID = "channel_id"
    }
}
