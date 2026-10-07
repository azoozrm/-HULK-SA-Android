package sa.hulksa.player.ui.screens

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy
import kotlin.math.roundToInt

private const val ANDROID_KEYCODE_LAST_CHANNEL = 229

internal data class PlayerProEpisodeNeighbors(
    val previous: Episode?,
    val next: Episode?,
)

internal data class PlayerTvPremiumOverlayMetrics(
    val safeHorizontalPaddingDp: Int,
    val safeBottomPaddingDp: Int,
    val zapMinWidthDp: Int,
    val zapMaxWidthDp: Int,
    val zapLogoSizeDp: Int,
    val zapHorizontalPaddingDp: Int,
    val zapVerticalPaddingDp: Int,
    val zapTitleSizeSp: Int,
)

internal fun playerTvPremiumOverlayMetrics(
    screenWidthDp: Int,
    screenHeightDp: Int,
): PlayerTvPremiumOverlayMetrics {
    val width = screenWidthDp.coerceAtLeast(1)
    val height = screenHeightDp.coerceAtLeast(1)
    val premium = tvPremiumWindowPolicy(width, height)
    val compact = width <= 960 || height <= 540
    val large = width >= 1600 && height >= 900

    val safeHorizontal = maxOf(
        premium.horizontalSafeInsetDp,
        if (compact) 24f else 30f,
    ).roundToInt()
    val safeBottom = maxOf(
        premium.verticalSafeInsetDp + if (compact) 20f else 28f,
        if (compact) 36f else 44f,
    ).roundToInt()
    val minWidth = (width * .30f).roundToInt().coerceIn(320, 430)
    val maxWidth = (width * .40f).roundToInt().coerceIn(470, 660).coerceAtLeast(minWidth)

    return PlayerTvPremiumOverlayMetrics(
        safeHorizontalPaddingDp = safeHorizontal,
        safeBottomPaddingDp = safeBottom,
        zapMinWidthDp = minWidth,
        zapMaxWidthDp = maxWidth,
        zapLogoSizeDp = (height * .082f).roundToInt().coerceIn(64, 92),
        zapHorizontalPaddingDp = (width * .012f).roundToInt().coerceIn(14, 24),
        zapVerticalPaddingDp = (height * .016f).roundToInt().coerceIn(12, 20),
        zapTitleSizeSp = when {
            large -> 26
            compact -> 20
            else -> 23
        },
    )
}

internal fun playerProEpisodeNeighbors(
    episodes: List<Episode>,
    currentStreamId: Int,
): PlayerProEpisodeNeighbors {
    val ordered = episodes.sortedWith(
        compareBy(Episode::season, Episode::episodeNumber, Episode::id),
    )
    val currentIndex = ordered.indexOfFirst { it.id == currentStreamId }
    if (currentIndex < 0) return PlayerProEpisodeNeighbors(previous = null, next = null)

    return PlayerProEpisodeNeighbors(
        previous = ordered.getOrNull(currentIndex - 1),
        next = ordered.getOrNull(currentIndex + 1),
    )
}

internal fun playerProEpisodeLabel(episode: Episode): String =
    "الموسم ${episode.season} • الحلقة ${episode.episodeNumber} • ${episode.title}"

internal fun playerProLiveNavigationSequence(
    channels: List<ContentItem>,
    currentStreamId: Int,
    launchContext: String,
    favoriteIds: Set<Int>,
    recentIds: List<Int>,
): List<ContentItem> {
    if (channels.isEmpty()) return emptyList()
    val byId = channels.associateBy(ContentItem::id)
    val contextual = when (launchContext) {
        LIVE_TV_PRO_CONTEXT_FAVORITES -> channels.filter { it.id in favoriteIds }
        LIVE_TV_PRO_CONTEXT_RECENT -> recentIds.mapNotNull(byId::get).distinctBy(ContentItem::id)
        LIVE_TV_PRO_CONTEXT_ALL -> channels
        else -> channels.filter { it.categoryId == launchContext }
    }
    return contextual.takeIf { sequence -> sequence.any { it.id == currentStreamId } }
        ?: liveTvProChannelSequence(channels, currentStreamId)
}

internal fun playerProQueuedRelativeChannel(
    sequence: List<ContentItem>,
    currentStreamId: Int,
    pendingStreamId: Int?,
    delta: Int,
): ContentItem? {
    if (sequence.isEmpty()) return null
    val anchorStreamId = pendingStreamId
        ?.takeIf { pendingId -> sequence.any { it.id == pendingId } }
        ?: currentStreamId
    val anchorIndex = sequence.indexOfFirst { it.id == anchorStreamId }.takeIf { it >= 0 } ?: 0
    val targetIndex = (((anchorIndex + delta) % sequence.size) + sequence.size) % sequence.size
    return sequence[targetIndex]
}

/**
 * Player Pro entry point shared by VOD/series and Live TV Pro.
 *
 * Series continuity remains isolated here. For Live, this layer owns recent history, hardware
 * Channel +/- and Last Channel actions, the v1.6 TV browser, and Channel Zapping Pro coalescing.
 * PlayerScreen remains the qualified playback/control core.
 */
@Composable
fun PlayerProScreen(
    request: PlaybackRequest,
    liveTvProEnabled: Boolean,
    liveCatalog: Catalog?,
    isFavorite: (ContentItem) -> Boolean,
    favoriteKeys: Set<String>,
    vodItem: ContentItem? = null,
    onSelectLiveChannel: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onBack: () -> Unit,
    onProgress: (request: PlaybackRequest, positionMs: Long, durationMs: Long) -> Unit,
    previousEpisode: Episode?,
    nextEpisode: Episode?,
    onPlayPreviousEpisode: (() -> Unit)?,
    onPlayNextEpisode: (() -> Unit)?,
) {
    val context = LocalContext.current
    val liveChannels = liveCatalog?.items.orEmpty()
    val liveProfileScope = context.liveTvProStateScope()
    var recentChannelIds by remember(liveCatalog, liveProfileScope) {
        mutableStateOf(context.liveTvProRecentChannelIds())
    }
    // Latest queued Live target. It deliberately survives request replacement so newer accepted
    // input is never dropped while an earlier switch is still settling.
    var pendingLiveChannelId by remember(liveCatalog, liveProfileScope) { mutableStateOf<Int?>(null) }
    var lastLiveDispatchAtMs by remember(liveCatalog, liveProfileScope) { mutableLongStateOf(0L) }
    var lastLiveDispatchedTargetId by remember(liveCatalog, liveProfileScope) { mutableStateOf<Int?>(null) }
    // Explicit reveal intent carried into the existing PlayerScreen controls owner. It is set by an
    // accepted remote switch command (so the current request reveals the compact Live strip) and is
    // consumed once by the newly committed request so the reveal survives the request replacement.
    // Every accepted step is also a fresh interaction for the existing reveal/auto-hide owner.
    var liveControlsInteraction by remember { mutableStateOf(LiveControlsInteractionState()) }
    var childErrorModalInputActive by remember(request.historyKey) { mutableStateOf(false) }
    var childLiveBrowserVisible by remember(request.historyKey) { mutableStateOf(false) }
    var childPanelInputActive by remember(request.historyKey) { mutableStateOf(false) }

    LaunchedEffect(request.isLive, request.streamId, liveCatalog, liveProfileScope) {
        if (request.isLive && liveChannels.any { it.id == request.streamId }) {
            val updated = liveTvProUpdateRecentChannelIds(
                existingIds = recentChannelIds,
                currentStreamId = request.streamId,
            )
            if (updated != recentChannelIds) {
                recentChannelIds = updated
                context.saveLiveTvProRecentChannelIds(updated)
            }
        }
    }

    val lastChannel = remember(request.isLive, request.streamId, liveCatalog, recentChannelIds) {
        if (!request.isLive) {
            null
        } else {
            liveTvProLastChannel(
                channels = liveChannels,
                recentIds = recentChannelIds,
                currentStreamId = request.streamId,
            )
        }
    }
    // Observed launch context. The browser can change the saved category and close without
    // replacing the channel request, so the context is re-read on browser close and on request
    // replacement and is a real cache key below.
    var liveLaunchContext by remember(liveProfileScope) {
        mutableStateOf(context.liveTvProLaunchContext())
    }
    LaunchedEffect(request.historyKey, childLiveBrowserVisible) {
        liveLaunchContext = context.liveTvProLaunchContext()
    }
    // Catalog-keyed index plus cached favorites/recent lists remove the repeated O(catalog) scans
    // from every accepted switch; the sequence is rebuilt only when its real inputs change.
    val liveCatalogIndex = remember(liveChannels) {
        LiveChannelCatalogIndex(liveChannels)
    }
    val liveFavoriteIds = remember(liveCatalogIndex, favoriteKeys) {
        liveCatalogIndex.items.asSequence().filter(isFavorite).map(ContentItem::id).toSet()
    }
    val liveFavoriteChannels = remember(liveCatalogIndex, liveFavoriteIds) {
        liveCatalogIndex.favoriteChannels(liveFavoriteIds)
    }
    val liveRecentChannels = remember(liveCatalogIndex, recentChannelIds) {
        liveCatalogIndex.recentChannels(recentChannelIds)
    }
    val liveNavigationSequence = remember(
        request.streamId,
        liveCatalogIndex,
        liveFavoriteChannels,
        liveRecentChannels,
        liveLaunchContext,
    ) {
        liveNavigationSequenceFromIndex(
            index = liveCatalogIndex,
            currentStreamId = request.streamId,
            launchContext = liveLaunchContext,
            favoriteChannels = liveFavoriteChannels,
            recentChannels = liveRecentChannels,
        )
    }

    /**
     * The single Live zap scheduler.
     *
     * A normal accepted press dispatches immediately (leading edge, no quiet-period wait). While
     * input continues, the latest target is committed at a bounded cadence and never starves until
     * release. Only the latest target is kept; no stale FIFO backlog and no player creation for
     * every native repeat. Modal surfaces gate and clear the queue.
     */
    LaunchedEffect(
        request.isLive,
        liveTvProEnabled,
        request.streamId,
        pendingLiveChannelId,
        liveChannels,
        childErrorModalInputActive,
        childLiveBrowserVisible,
        childPanelInputActive,
    ) {
        if (!request.isLive || !liveTvProEnabled) {
            pendingLiveChannelId = null
            return@LaunchedEffect
        }
        if (liveZapDispatchBlocked(
                errorModalInputActive = childErrorModalInputActive,
                browserVisible = childLiveBrowserVisible,
                panelInputActive = childPanelInputActive,
            )
        ) {
            return@LaunchedEffect
        }
        while (true) {
            val targetId = pendingLiveChannelId ?: return@LaunchedEffect
            val plan = planLiveZapDispatch(
                state = LiveZapSchedulerState(
                    pendingTargetId = targetId,
                    lastDispatchAtMs = lastLiveDispatchAtMs,
                    lastDispatchedTargetId = lastLiveDispatchedTargetId,
                ),
                currentStreamId = request.streamId,
                pendingTargetExists = liveChannels.any { it.id == targetId },
                nowMs = SystemClock.uptimeMillis(),
            )
            pendingLiveChannelId = plan.state.pendingTargetId
            lastLiveDispatchAtMs = plan.state.lastDispatchAtMs
            lastLiveDispatchedTargetId = plan.state.lastDispatchedTargetId
            if (plan.waitMs > 0L) {
                delay(plan.waitMs)
                if (!request.isLive) return@LaunchedEffect
                if (liveZapDispatchBlocked(
                        errorModalInputActive = childErrorModalInputActive,
                        browserVisible = childLiveBrowserVisible,
                        panelInputActive = childPanelInputActive,
                    )
                ) {
                    return@LaunchedEffect
                }
                continue
            }
            val dispatchId = plan.dispatchTargetId ?: return@LaunchedEffect
            val target = liveChannels.firstOrNull { it.id == dispatchId } ?: run {
                pendingLiveChannelId = null
                lastLiveDispatchedTargetId = null
                return@LaunchedEffect
            }
            android.util.Log.i(
                "HulkPlayer",
                "live zap dispatch t=${SystemClock.elapsedRealtime()} targetId=$dispatchId " +
                    "fromStreamId=${request.streamId}",
            )
            onSelectLiveChannel(target)
            return@LaunchedEffect
        }
    }

    // A request change consumes the reveal intent after the new request has composed with it. A
    // newer accepted switch that arrived in the same window keeps the intent for its own request.
    LaunchedEffect(request.historyKey) {
        liveControlsInteraction = liveControlsInteraction.onRequestReplacement(
            pendingSwitchPresent = pendingLiveChannelId != null,
        )
    }

    // A foreground panel, browser or error surface owns input; drop queued switching so nothing
    // zaps behind it.
    LaunchedEffect(
        liveTvProEnabled,
        childErrorModalInputActive,
        childLiveBrowserVisible,
        childPanelInputActive,
    ) {
        if (!liveTvProEnabled ||
            liveZapDispatchBlocked(
                errorModalInputActive = childErrorModalInputActive,
                browserVisible = childLiveBrowserVisible,
                panelInputActive = childPanelInputActive,
            )
        ) {
            pendingLiveChannelId = null
            lastLiveDispatchedTargetId = null
        }
    }

    fun requestLiveControlsReveal() {
        liveControlsInteraction = liveControlsInteraction.onAcceptedSwitchInteraction()
    }

    fun cancelPendingLiveZap() {
        pendingLiveChannelId = null
        lastLiveDispatchedTargetId = null
        liveControlsInteraction = liveControlsInteraction.onCancel()
    }

    /**
     * Settle the latest queued target immediately on release, without waiting for the next bounded
     * dispatch slot. Re-dispatches are skipped when the target is already in flight or committed.
     */
    fun flushPendingLiveTarget() {
        if (!request.isLive || !liveTvProEnabled) return
        if (liveZapDispatchBlocked(
                errorModalInputActive = childErrorModalInputActive,
                browserVisible = childLiveBrowserVisible,
                panelInputActive = childPanelInputActive,
            )
        ) {
            return
        }
        val targetId = pendingLiveChannelId ?: return
        val target = liveChannels.firstOrNull { it.id == targetId }
        if (target == null || target.id == request.streamId) {
            pendingLiveChannelId = null
            lastLiveDispatchedTargetId = null
            return
        }
        val releaseTargetId = liveZapReleaseDispatchTargetId(
            state = LiveZapSchedulerState(
                pendingTargetId = targetId,
                lastDispatchAtMs = lastLiveDispatchAtMs,
                lastDispatchedTargetId = lastLiveDispatchedTargetId,
            ),
            currentStreamId = request.streamId,
        ) ?: return
        lastLiveDispatchAtMs = SystemClock.uptimeMillis()
        lastLiveDispatchedTargetId = releaseTargetId
        onSelectLiveChannel(target)
    }

    /**
     * Shared last-channel action for the hardware Last Channel key and the Live HUD button.
     * Returns false when the profile-scoped history has no available, non-current target.
     */
    fun playLastChannel(): Boolean {
        val channel = lastChannel ?: return false
        cancelPendingLiveZap()
        requestLiveControlsReveal()
        onSelectLiveChannel(channel)
        return true
    }

    /**
     * Accept one relative switch step and return the interaction token of that acceptance, or -1
     * when it was not accepted. The token lets a cancelled touch gesture discard only its own
     * uncommitted target without touching a newer interaction.
     */
    fun queueLiveRelativeWithToken(delta: Int): Int {
        if (!request.isLive || !liveTvProEnabled) return -1
        val channel = playerProQueuedRelativeChannel(
            sequence = liveNavigationSequence,
            currentStreamId = request.streamId,
            pendingStreamId = pendingLiveChannelId,
            delta = delta,
        ) ?: return -1
        if (channel.id == request.streamId && pendingLiveChannelId == null) return -1
        pendingLiveChannelId = channel.id
        requestLiveControlsReveal()
        android.util.Log.i(
            "HulkPlayer",
            "live zap accept t=${SystemClock.elapsedRealtime()} tick=${liveControlsInteraction.interactionTick} " +
                "targetId=${channel.id} fromStreamId=${request.streamId}",
        )
        return liveControlsInteraction.interactionTick
    }

    fun queueLiveRelative(delta: Int): Boolean = queueLiveRelativeWithToken(delta) >= 0

    /**
     * Cancelled touch gesture cleanup for the queue owner. It discards the uncommitted target and
     * reveal intent only when the cancelling gesture still owns the latest accepted interaction, so
     * cleanup from an obsolete gesture cannot cancel a newer interaction or undo a committed
     * channel (commits never revert here; only the pending/uncommitted target is dropped).
     */
    fun cancelLiveHoldPending(interactionToken: Int) {
        if (interactionToken < 0) return
        if (liveControlsInteraction.interactionTick != interactionToken) return
        if (pendingLiveChannelId == null) return
        android.util.Log.i(
            "HulkPlayer",
            "live zap cancel t=${SystemClock.elapsedRealtime()} tick=$interactionToken " +
                "targetId=$pendingLiveChannelId",
        )
        cancelPendingLiveZap()
    }

    fun queuePlayerRequestedLiveChannel(channel: ContentItem) {
        if (!request.isLive || !liveTvProEnabled) {
            onSelectLiveChannel(channel)
            return
        }
        if (channel.id == request.streamId && pendingLiveChannelId == null) return

        val sequence = liveCatalogIndex.sequenceFor(request.streamId)
        val currentIndex = sequence.indexOfFirst { it.id == request.streamId }
        val requestedIndex = sequence.indexOfFirst { it.id == channel.id }
        val relativeDelta = if (currentIndex >= 0 && requestedIndex >= 0 && sequence.size > 1) {
            val nextIndex = (currentIndex + 1) % sequence.size
            val previousIndex = (currentIndex - 1 + sequence.size) % sequence.size
            when (requestedIndex) {
                nextIndex -> 1
                previousIndex -> -1
                else -> null
            }
        } else {
            null
        }

        if (relativeDelta != null) {
            queueLiveRelative(relativeDelta)
        } else {
            cancelPendingLiveZap()
            onSelectLiveChannel(channel)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (request.isLive && liveTvProEnabled && event.type == KeyEventType.KeyUp) {
                    // A held/native-repeat sequence settles on its latest target as soon as the key
                    // is released, without waiting for the next bounded dispatch slot.
                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        -> flushPendingLiveTarget()
                    }
                    return@onPreviewKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (request.isLive && liveTvProEnabled) {
                    if (
                        !playerLiveProLayerOwnsInput(
                            isLive = request.isLive,
                            liveTvProEnabled = liveTvProEnabled,
                            errorModalInputActive = childErrorModalInputActive,
                            browserVisible = childLiveBrowserVisible,
                            panelInputActive = childPanelInputActive,
                        )
                    ) {
                        return@onPreviewKeyEvent false
                    }

                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                        -> {
                            return@onPreviewKeyEvent queueLiveRelative(1)
                        }

                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        -> {
                            return@onPreviewKeyEvent queueLiveRelative(-1)
                        }

                        ANDROID_KEYCODE_LAST_CHANNEL -> {
                            return@onPreviewKeyEvent playLastChannel()
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        -> {
                            cancelPendingLiveZap()
                            return@onPreviewKeyEvent false
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        -> return@onPreviewKeyEvent queueLiveRelative(
                            if (keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP) 1 else -1,
                        )

                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_ESCAPE,
                        -> {
                            cancelPendingLiveZap()
                            return@onPreviewKeyEvent false
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                if (!request.streamKind.equals("series", ignoreCase = true)) {
                    return@onPreviewKeyEvent false
                }

                when (keyCode) {
                    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (previousEpisode != null && onPlayPreviousEpisode != null) {
                            onPlayPreviousEpisode()
                            true
                        } else {
                            false
                        }
                    }

                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (nextEpisode != null && onPlayNextEpisode != null) {
                            onPlayNextEpisode()
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            },
    ) {
        PlayerScreen(
            request = request,
            liveCatalog = liveCatalog,
            isFavorite = isFavorite,
            favoriteKeys = favoriteKeys,
            vodItem = vodItem,
            onSelectLiveChannel = ::queuePlayerRequestedLiveChannel,
            onToggleFavorite = onToggleFavorite,
            onLastChannel = if (
                playerProLiveLastChannelActionEnabled(
                    isLive = request.isLive,
                    liveTvProEnabled = liveTvProEnabled,
                    hasLastChannel = lastChannel != null,
                )
            ) {
                { playLastChannel() }
            } else {
                null
            },
            onBack = onBack,
            onProgress = onProgress,
            nextEpisodeTitle = nextEpisode?.let(::playerProEpisodeLabel),
            onPlayNextEpisode = onPlayNextEpisode,
            liveControlsRevealRequested = liveControlsInteraction.revealRequested,
            liveControlsInteractionTick = liveControlsInteraction.interactionTick,
            liveChannelHoldRepeat = liveTvProEnabled && request.isLive,
            onPreviousLiveChannel = if (liveTvProEnabled && request.isLive) {
                { queueLiveRelativeWithToken(-1) }
            } else {
                null
            },
            onNextLiveChannel = if (liveTvProEnabled && request.isLive) {
                { queueLiveRelativeWithToken(1) }
            } else {
                null
            },
            onLiveChannelHoldRelease = if (liveTvProEnabled && request.isLive) {
                { flushPendingLiveTarget() }
            } else {
                null
            },
            onLiveChannelHoldCancelled = if (liveTvProEnabled && request.isLive) {
                { token -> cancelLiveHoldPending(token) }
            } else {
                null
            },
            liveCatalogIndex = liveCatalogIndex,
            onErrorModalActiveChanged = { childErrorModalInputActive = it },
            onBrowserVisibilityChanged = { childLiveBrowserVisible = it },
            onPanelActiveChanged = { childPanelInputActive = it },
        )
    }
}
