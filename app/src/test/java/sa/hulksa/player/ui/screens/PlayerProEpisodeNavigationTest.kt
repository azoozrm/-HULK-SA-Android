package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Episode

class PlayerProEpisodeNavigationTest {
    @Test
    fun middleEpisodeResolvesChronologicalPreviousAndNext() {
        val episodes = listOf(
            episode(id = 20, season = 2, number = 1),
            episode(id = 11, season = 1, number = 2),
            episode(id = 10, season = 1, number = 1),
        )

        val neighbors = playerProEpisodeNeighbors(episodes, currentStreamId = 11)

        assertEquals(10, neighbors.previous?.id)
        assertEquals(20, neighbors.next?.id)
    }

    @Test
    fun firstEpisodeHasNoPreviousAndLastEpisodeHasNoNext() {
        val episodes = listOf(
            episode(id = 10, season = 1, number = 1),
            episode(id = 11, season = 1, number = 2),
            episode(id = 20, season = 2, number = 1),
        )

        assertNull(playerProEpisodeNeighbors(episodes, currentStreamId = 10).previous)
        assertNull(playerProEpisodeNeighbors(episodes, currentStreamId = 20).next)
    }

    @Test
    fun unknownStreamDoesNotGuessEpisodeNeighbors() {
        val neighbors = playerProEpisodeNeighbors(
            episodes = listOf(episode(id = 10, season = 1, number = 1)),
            currentStreamId = 999,
        )

        assertNull(neighbors.previous)
        assertNull(neighbors.next)
    }

    @Test
    fun episodeLabelKeepsSeasonEpisodeAndTitleTogether() {
        assertEquals(
            "الموسم 3 • الحلقة 7 • النهاية",
            playerProEpisodeLabel(episode(id = 70, season = 3, number = 7, title = "النهاية")),
        )
    }

    @Test
    fun episodeMediaShortcutsAreBlockedWhileAForegroundDecisionOwnsInput() {
        assertTrue(
            playerProEpisodeShortcutEligible(
                foregroundModalActive = false,
                panelActive = false,
                errorModalActive = false,
            ),
        )
        // Resume/lock/countdown foreground decision blocks the parent episode shortcuts.
        assertEquals(
            false,
            playerProEpisodeShortcutEligible(
                foregroundModalActive = true,
                panelActive = false,
                errorModalActive = false,
            ),
        )
        // The More panel and the error card own input exactly the same way.
        assertEquals(
            false,
            playerProEpisodeShortcutEligible(
                foregroundModalActive = false,
                panelActive = true,
                errorModalActive = false,
            ),
        )
        assertEquals(
            false,
            playerProEpisodeShortcutEligible(
                foregroundModalActive = false,
                panelActive = false,
                errorModalActive = true,
            ),
        )
    }

    @Test
    fun lockedControlsFlipTheAuthoritativeForegroundSignalEvenWithoutTheUnlockOverlay() {
        // Locked while the unlock overlay is not visible is still a foreground decision owner.
        assertTrue(
            playerVodForegroundDecisionActive(
                isLive = false,
                resumePromptVisible = false,
                errorModalActive = false,
                unlockVisible = false,
                nextCountdownActive = false,
                controlsLocked = true,
            ),
        )
        // Unlocking clears the signal, and each other surface keeps owning input.
        assertEquals(
            false,
            playerVodForegroundDecisionActive(
                isLive = false,
                resumePromptVisible = false,
                errorModalActive = false,
                unlockVisible = false,
                nextCountdownActive = false,
                controlsLocked = false,
            ),
        )
        assertTrue(
            playerVodForegroundDecisionActive(
                isLive = false,
                resumePromptVisible = true,
                errorModalActive = false,
                unlockVisible = false,
                nextCountdownActive = false,
                controlsLocked = false,
            ),
        )
        assertTrue(
            playerVodForegroundDecisionActive(
                isLive = false,
                resumePromptVisible = false,
                errorModalActive = true,
                unlockVisible = false,
                nextCountdownActive = false,
                controlsLocked = false,
            ),
        )
        assertTrue(
            playerVodForegroundDecisionActive(
                isLive = false,
                resumePromptVisible = false,
                errorModalActive = false,
                unlockVisible = true,
                nextCountdownActive = false,
                controlsLocked = false,
            ),
        )
        assertTrue(
            playerVodForegroundDecisionActive(
                isLive = false,
                resumePromptVisible = false,
                errorModalActive = false,
                unlockVisible = false,
                nextCountdownActive = true,
                controlsLocked = false,
            ),
        )
        // Live keeps its independent foreground owners.
        assertEquals(
            false,
            playerVodForegroundDecisionActive(
                isLive = true,
                resumePromptVisible = true,
                errorModalActive = true,
                unlockVisible = true,
                nextCountdownActive = true,
                controlsLocked = true,
            ),
        )
    }

    @Test
    fun initialEpisodeShortcutDispatchesOnceAndHeldRepeatsAreConsumed() {
        val initial = playerProEpisodeShortcutDisposition(
            eligible = true,
            isMediaPrevious = true,
            isMediaNext = false,
            repeatCount = 0,
            previousAvailable = true,
            nextAvailable = true,
        )
        assertEquals(PlayerProEpisodeShortcutDisposition.PlayPrevious, initial)

        val nextInitial = playerProEpisodeShortcutDisposition(
            eligible = true,
            isMediaPrevious = false,
            isMediaNext = true,
            repeatCount = 0,
            previousAvailable = true,
            nextAvailable = true,
        )
        assertEquals(PlayerProEpisodeShortcutDisposition.PlayNext, nextInitial)

        for (repeat in 1..4) {
            assertEquals(
                PlayerProEpisodeShortcutDisposition.Consume,
                playerProEpisodeShortcutDisposition(
                    eligible = true,
                    isMediaPrevious = true,
                    isMediaNext = false,
                    repeatCount = repeat,
                    previousAvailable = true,
                    nextAvailable = true,
                ),
            )
        }

        // A missing authoritative neighbor or callback never dispatches.
        assertEquals(
            PlayerProEpisodeShortcutDisposition.Ignore,
            playerProEpisodeShortcutDisposition(
                eligible = true,
                isMediaPrevious = true,
                isMediaNext = false,
                repeatCount = 0,
                previousAvailable = false,
                nextAvailable = true,
            ),
        )
        assertEquals(
            PlayerProEpisodeShortcutDisposition.Ignore,
            playerProEpisodeShortcutDisposition(
                eligible = true,
                isMediaPrevious = false,
                isMediaNext = true,
                repeatCount = 0,
                previousAvailable = true,
                nextAvailable = false,
            ),
        )
        // Non-media keys stay untouched by the episode shortcut owner.
        assertEquals(
            PlayerProEpisodeShortcutDisposition.Ignore,
            playerProEpisodeShortcutDisposition(
                eligible = false,
                isMediaPrevious = false,
                isMediaNext = false,
                repeatCount = 0,
                previousAvailable = true,
                nextAvailable = true,
            ),
        )
    }

    @Test
    fun zeroEpisodeCallbacksRunBehindEveryForegroundOwnerIncludingLock() {
        val surfaces = mapOf(
            "resume" to playerVodForegroundDecisionActive(
                isLive = false,
                resumePromptVisible = true,
                errorModalActive = false,
                unlockVisible = false,
                nextCountdownActive = false,
                controlsLocked = false,
            ),
            "error" to playerVodForegroundDecisionActive(
                isLive = false,
                resumePromptVisible = false,
                errorModalActive = true,
                unlockVisible = false,
                nextCountdownActive = false,
                controlsLocked = false,
            ),
            "unlock" to playerVodForegroundDecisionActive(
                isLive = false,
                resumePromptVisible = false,
                errorModalActive = false,
                unlockVisible = true,
                nextCountdownActive = false,
                controlsLocked = false,
            ),
            "countdown" to playerVodForegroundDecisionActive(
                isLive = false,
                resumePromptVisible = false,
                errorModalActive = false,
                unlockVisible = false,
                nextCountdownActive = true,
                controlsLocked = false,
            ),
            "lock" to playerVodForegroundDecisionActive(
                isLive = false,
                resumePromptVisible = false,
                errorModalActive = false,
                unlockVisible = false,
                nextCountdownActive = false,
                controlsLocked = true,
            ),
        )
        var callbacks = 0
        for ((surface, foregroundActive) in surfaces) {
            for (mediaPrevious in listOf(true, false)) {
                val disposition = playerProEpisodeShortcutDisposition(
                    eligible = playerProEpisodeShortcutEligible(
                        foregroundModalActive = foregroundActive,
                        panelActive = false,
                        errorModalActive = false,
                    ),
                    isMediaPrevious = mediaPrevious,
                    isMediaNext = !mediaPrevious,
                    repeatCount = 0,
                    previousAvailable = true,
                    nextAvailable = true,
                )
                if (
                    disposition == PlayerProEpisodeShortcutDisposition.PlayPrevious ||
                    disposition == PlayerProEpisodeShortcutDisposition.PlayNext
                ) {
                    callbacks += 1
                }
                assertEquals(
                    "no dispatch behind $surface",
                    PlayerProEpisodeShortcutDisposition.Consume,
                    disposition,
                )
            }
        }
        // The More child panel owns input through the panel signal, not the foreground signal.
        val panelDisposition = playerProEpisodeShortcutDisposition(
            eligible = playerProEpisodeShortcutEligible(
                foregroundModalActive = false,
                panelActive = true,
                errorModalActive = false,
            ),
            isMediaPrevious = false,
            isMediaNext = true,
            repeatCount = 0,
            previousAvailable = true,
            nextAvailable = true,
        )
        assertEquals(PlayerProEpisodeShortcutDisposition.Consume, panelDisposition)
        assertEquals(0, callbacks)
    }

    @Test
    fun favoritesZappingWrapsInsideFavoritesAcrossUnderlyingCategories() {
        val channels = listOf(
            channel(1, "news"),
            channel(2, "news"),
            channel(3, "sports"),
            channel(4, "sports"),
            channel(5, "movies"),
        )
        val sequence = playerProLiveNavigationSequence(
            channels = channels,
            currentStreamId = 5,
            launchContext = LIVE_TV_PRO_CONTEXT_FAVORITES,
            favoriteIds = setOf(1, 3, 5),
            recentIds = emptyList(),
        )

        assertEquals(listOf(1, 3, 5), sequence.map(ContentItem::id))
        assertEquals(
            1,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 5,
                pendingStreamId = null,
                delta = 1,
            )?.id,
        )
        assertEquals(
            5,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 1,
                pendingStreamId = null,
                delta = -1,
            )?.id,
        )
    }

    @Test
    fun rapidLiveZappingUsesPendingTargetAsTheNextAnchor() {
        val sequence = listOf(
            channel(1, "news"),
            channel(2, "news"),
            channel(3, "news"),
            channel(4, "news"),
        )

        assertEquals(
            3,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 1,
                pendingStreamId = 2,
                delta = 1,
            )?.id,
        )
        assertEquals(
            2,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 1,
                pendingStreamId = 3,
                delta = -1,
            )?.id,
        )
    }

    @Test
    fun explicitCategoryNavigationNeverLeaksIntoAnotherCategory() {
        val channels = listOf(
            channel(1, "news"),
            channel(2, "sports"),
            channel(3, "movies"),
        )
        val sequence = playerProLiveNavigationSequence(
            channels = channels,
            currentStreamId = 1,
            launchContext = "news",
            favoriteIds = emptySet(),
            recentIds = emptyList(),
        )

        assertEquals(listOf(1), sequence.map(ContentItem::id))
        assertEquals(
            1,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 1,
                pendingStreamId = null,
                delta = 1,
            )?.id,
        )
    }

    @Test
    fun tvPremiumPlayerOverlayProtectsCompactSafeAreaAndScalesOnLargeTv() {
        val compact = playerTvPremiumOverlayMetrics(screenWidthDp = 960, screenHeightDp = 540)
        val standard = playerTvPremiumOverlayMetrics(screenWidthDp = 1280, screenHeightDp = 720)
        val large = playerTvPremiumOverlayMetrics(screenWidthDp = 1920, screenHeightDp = 1080)

        assertEquals(24, compact.safeHorizontalPaddingDp)
        assertEquals(36, compact.safeBottomPaddingDp)
        assertEquals(320, compact.zapMinWidthDp)
        assertEquals(470, compact.zapMaxWidthDp)
        assertEquals(64, compact.zapLogoSizeDp)
        assertEquals(20, compact.zapTitleSizeSp)

        assertEquals(30, standard.safeHorizontalPaddingDp)
        assertEquals(44, standard.safeBottomPaddingDp)
        assertEquals(23, standard.zapTitleSizeSp)

        assertTrue(large.zapMaxWidthDp > standard.zapMaxWidthDp)
        assertTrue(large.zapLogoSizeDp > standard.zapLogoSizeDp)
        assertEquals(26, large.zapTitleSizeSp)
    }

    @Test
    fun rapidRepeatBurstAccumulatesFromPendingWrapsReversesAndRestartsAfterCancel() {
        val sequence = (1..5).map { channel(it, "news") }
        // Production queueLiveRelative anchors every accepted step (including native repeats) on the
        // current pending target; this loop mirrors that accumulation exactly.
        var pending: Int? = null
        val visited = mutableListOf<Int>()
        repeat(5) {
            val target = playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 1,
                pendingStreamId = pending,
                delta = 1,
            ) ?: error("burst step must be accepted")
            pending = target.id
            visited += target.id
        }

        assertEquals(listOf(2, 3, 4, 5, 1), visited)

        // A reverse step re-anchors on the pending target, not the committed channel.
        assertEquals(
            5,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 1,
                pendingStreamId = pending,
                delta = -1,
            )?.id,
        )

        // Cancellation restores the committed channel as the anchor for the next burst.
        assertEquals(
            2,
            playerProQueuedRelativeChannel(
                sequence = sequence,
                currentStreamId = 1,
                pendingStreamId = null,
                delta = 1,
            )?.id,
        )
    }

    @Test
    fun catalogIndexNavigationSequenceMatchesTheAcceptedScanSemantics() {
        val channels = listOf(
            channel(1, "news"),
            channel(2, "news"),
            channel(3, "sports"),
            channel(4, "sports"),
            channel(5, "movies"),
        )
        val index = LiveChannelCatalogIndex(channels)
        val favoriteIds = setOf(1, 3, 5)
        val recentIds = listOf(5, 2)

        listOf(1, 2, 3, 5, 99).forEach { current ->
            listOf(
                LIVE_TV_PRO_CONTEXT_ALL,
                LIVE_TV_PRO_CONTEXT_FAVORITES,
                LIVE_TV_PRO_CONTEXT_RECENT,
                "sports",
                "missing",
            ).forEach { context ->
                assertEquals(
                    playerProLiveNavigationSequence(
                        channels = channels,
                        currentStreamId = current,
                        launchContext = context,
                        favoriteIds = favoriteIds,
                        recentIds = recentIds,
                    ).map(ContentItem::id),
                    liveNavigationSequenceFromIndex(
                        index = index,
                        currentStreamId = current,
                        launchContext = context,
                        favoriteChannels = index.favoriteChannels(favoriteIds),
                        recentChannels = index.recentChannels(recentIds),
                    ).map(ContentItem::id),
                )
            }
            assertEquals(
                liveTvProChannelSequence(channels, current).map(ContentItem::id),
                index.sequenceFor(current).map(ContentItem::id),
            )
        }
        assertEquals(channels, index.channelsInCategory(null))
        assertEquals(listOf(1, 2), index.channelsInCategory("news").map(ContentItem::id))
        assertEquals(listOf(1, 3, 5), index.favoriteChannels(favoriteIds).map(ContentItem::id))
        assertEquals(listOf(5, 2), index.recentChannels(recentIds).map(ContentItem::id))
    }

    private fun episode(
        id: Int,
        season: Int,
        number: Int,
        title: String = "Episode $number",
    ) = Episode(
        id = id,
        title = title,
        season = season,
        episodeNumber = number,
        containerExtension = "mp4",
        posterUrl = null,
        duration = "00:45:00",
    )

    private fun channel(id: Int, categoryId: String) = ContentItem(
        id = id,
        name = "Channel $id",
        categoryId = categoryId,
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "ts",
    )
}
