package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

class VodPlayerControlsPolicyTest {

    private fun movie(id: Int = 7): ContentItem = ContentItem(
        id = id,
        name = "Movie $id",
        categoryId = "10",
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "mp4",
    )

    @Test
    fun toolsShareTheRowWidthOnlyWhenIntrinsicCaptionsOverflow() {
        assertFalse(vodToolsUseWeightedSlots(availableWidthPx = 2_000, requiredWidthPx = 320))
        assertFalse(vodToolsUseWeightedSlots(availableWidthPx = 320, requiredWidthPx = 320))
        assertTrue(vodToolsUseWeightedSlots(availableWidthPx = 319, requiredWidthPx = 320))
        assertTrue(vodToolsUseWeightedSlots(availableWidthPx = 0, requiredWidthPx = 1))
    }

    @Test
    fun toolsRequiredWidthUsesTheRenderedSpacingForEverySlot() {
        assertEquals(
            5 * 46 + 4 * 24,
            vodToolsRequiredWidthPx(
                iconBoxesPx = List(5) { 46 },
                captionWidthsPx = List(5) { 30 },
                spacingPx = 24,
            ),
        )
        assertEquals(
            5 * 80 + 4 * 24,
            vodToolsRequiredWidthPx(
                iconBoxesPx = List(5) { 46 },
                captionWidthsPx = List(5) { 80 },
                spacingPx = 24,
            ),
        )
        assertEquals(0, vodToolsRequiredWidthPx(emptyList(), emptyList(), 24))
    }

    @Test
    fun moviesToolSpacingWidensFromTheGlyphAndNeverDropsBelowTheBaseItemSpacing() {
        // Mi Box: 20dp transport -> 24dp movie glyph -> 32dp widened gap shared by fit + render.
        assertEquals(32, vodToolSpacingDp(movieGlyphDp = 24, itemSpacingDp = 8))
        // Narrow fallback: the base item spacing still wins when it is the larger expression.
        assertEquals(40, vodToolSpacingDp(movieGlyphDp = 22, itemSpacingDp = 40))
    }

    @Test
    fun moviesStripSitsInsideTheRealSafeBoundaryAndNeverAboveTheLiveBase() {
        // Mi Box compact TV: overscan inset 16dp + 8dp clearance = 24dp (below the Live 36dp base).
        assertEquals(
            24f,
            vodStripBottomPaddingDp(baseBottomDp = 36f, safeBottomInsetDp = 16f, isTelevision = true),
            0.001f,
        )
        // Standard/large TV: overscan inset 8dp + 8dp clearance = 16dp (below the Live 44dp base).
        assertEquals(
            16f,
            vodStripBottomPaddingDp(baseBottomDp = 44f, safeBottomInsetDp = 8f, isTelevision = true),
            0.001f,
        )
        // Touch already consumes the navigation inset once, so only the clearance remains.
        assertEquals(
            8f,
            vodStripBottomPaddingDp(baseBottomDp = 18f, safeBottomInsetDp = 0f, isTelevision = false),
            0.001f,
        )
        // A base already below the target is never increased (never above the Live base).
        assertEquals(
            12f,
            vodStripBottomPaddingDp(baseBottomDp = 12f, safeBottomInsetDp = 16f, isTelevision = true),
            0.001f,
        )
        // A negative inset cannot shrink the clearance below the constant.
        assertEquals(
            8f,
            vodStripBottomPaddingDp(baseBottomDp = 36f, safeBottomInsetDp = -4f, isTelevision = true),
            0.001f,
        )
    }

    @Test
    fun moviesStripGradientStopsFollowTheRealRowBounds() {
        val stops = vodStripGradientStops(
            stripTopPx = 800f,
            stripHeightPx = 200f,
            timelineTopPx = 860f,
            toolsTopPx = 920f,
        )
        assertEquals(0.30f, stops!!.timelineTopFraction, 0.001f)
        assertEquals(0.60f, stops.toolsTopFraction, 0.001f)
        // The timeline must never be at/above the strip top and the tools must stay below it.
        assertNull(vodStripGradientStops(stripTopPx = 800f, stripHeightPx = 0f, timelineTopPx = 860f, toolsTopPx = 920f))
        assertNull(vodStripGradientStops(stripTopPx = 800f, stripHeightPx = 200f, timelineTopPx = 700f, toolsTopPx = 920f))
        assertNull(vodStripGradientStops(stripTopPx = 800f, stripHeightPx = 200f, timelineTopPx = 920f, toolsTopPx = 920f))
    }

    @Test
    fun moviesSeekThumbStaysInsideTheTrackAtBothEndpoints() {
        // 1000px track, 38px thumb: center travels 19 .. 981 with no fixed-offset overflow.
        assertEquals(19f, vodSeekThumbCenterPx(fraction = 0f, trackWidthPx = 1_000f, thumbDiameterPx = 38f), 0.001f)
        assertEquals(500f, vodSeekThumbCenterPx(fraction = 0.5f, trackWidthPx = 1_000f, thumbDiameterPx = 38f), 0.001f)
        assertEquals(981f, vodSeekThumbCenterPx(fraction = 1f, trackWidthPx = 1_000f, thumbDiameterPx = 38f), 0.001f)
        assertEquals(19f, vodSeekThumbCenterPx(fraction = -1f, trackWidthPx = 1_000f, thumbDiameterPx = 38f), 0.001f)
        assertEquals(981f, vodSeekThumbCenterPx(fraction = 2f, trackWidthPx = 1_000f, thumbDiameterPx = 38f), 0.001f)
        // Narrow track shorter than the thumb degrades to the thumb radius without negatives.
        assertEquals(19f, vodSeekThumbCenterPx(fraction = 1f, trackWidthPx = 20f, thumbDiameterPx = 38f), 0.001f)
    }

    @Test
    fun moviePanelVerticalFocusCycleWrapsAndStaysClosed() {
        // Node 0 is the header; nodes 1..5 are the five More rows.
        assertEquals(1, vodPanelVerticalNeighbor(0, 6, 1))
        assertEquals(5, vodPanelVerticalNeighbor(0, 6, -1))
        assertEquals(0, vodPanelVerticalNeighbor(5, 6, 1))
        assertEquals(0, vodPanelVerticalNeighbor(1, 6, -1))
        assertEquals(4, vodPanelVerticalNeighbor(5, 6, -1))
        assertEquals(3, vodPanelVerticalNeighbor(3, 6, 0))
        assertEquals(0, vodPanelVerticalNeighbor(99, 0, 1))
    }

    @Test
    fun morePanelChildReturnsToMenuAndMenuCloses() {
        assertEquals(VodMorePanelView.MENU, vodMorePanelBack(VodMorePanelView.SPEED))
        assertEquals(VodMorePanelView.MENU, vodMorePanelBack(VodMorePanelView.PICTURE_SIZE))
        assertNull(vodMorePanelBack(VodMorePanelView.MENU))
    }

    @Test
    fun morePanelOriginRowIsDeterministic() {
        assertEquals(VodMoreRow.SPEED, vodMorePanelOriginRow(VodMorePanelView.SPEED))
        assertEquals(VodMoreRow.PICTURE_SIZE, vodMorePanelOriginRow(VodMorePanelView.PICTURE_SIZE))
        assertNull(vodMorePanelOriginRow(VodMorePanelView.MENU))
    }

    @Test
    fun favoriteControlReadsObservedSnapshotAndRequiresItem() {
        val item = movie()
        assertFalse(vodFavoriteControl(null, setOf("MOVIE:7")).enabled)
        assertFalse(vodFavoriteControl(item, emptySet()).favorite)
        val favorite = vodFavoriteControl(item, setOf("MOVIE:7", "SERIES:3"))
        assertTrue(favorite.enabled)
        assertTrue(favorite.favorite)
    }

    @Test
    fun signedSeekClampsToValidDurationAndRejectsUnknownDuration() {
        assertNull(vodSeekTargetMs(currentMs = 10_000L, deltaMs = -10_000L, durationMs = 0L))
        assertEquals(0L, vodSeekTargetMs(currentMs = 5_000L, deltaMs = -10_000L, durationMs = 120_000L))
        assertEquals(120_000L, vodSeekTargetMs(currentMs = 115_000L, deltaMs = 10_000L, durationMs = 120_000L))
        assertEquals(40_000L, vodSeekTargetMs(currentMs = 30_000L, deltaMs = 10_000L, durationMs = 120_000L))
    }

    @Test
    fun goToTimeValidatesComponentsAndDuration() {
        assertNull(vodGoToTimeTargetMs(-1, 0, 0, 7_200_000L))
        assertNull(vodGoToTimeTargetMs(0, 60, 0, 7_200_000L))
        assertNull(vodGoToTimeTargetMs(0, 0, 60, 7_200_000L))
        assertNull(vodGoToTimeTargetMs(0, 0, 0, 0L))
        assertNull(vodGoToTimeTargetMs(3, 0, 0, 7_200_000L))
        assertEquals(3_723_000L, vodGoToTimeTargetMs(1, 2, 3, 7_200_000L))
        assertTrue(vodGoToTimeConfirmEnabled(1, 2, 3, 7_200_000L))
        assertFalse(vodGoToTimeConfirmEnabled(3, 0, 0, 7_200_000L))
    }

    @Test
    fun goToTimeFieldsSplitAndCarryFromCommittedTime() {
        val fields = vodGoToTimeFields(3_723_000L)
        assertEquals(VodGoToTimeFields(1, 2, 3), fields)
        assertEquals(VodGoToTimeFields(1, 2, 4), vodGoToTimeFieldsStepped(fields, VodTimeField.SECONDS, 1, 7_200_000L))
        assertEquals(VodGoToTimeFields(1, 3, 3), vodGoToTimeFieldsStepped(fields, VodTimeField.MINUTES, 1, 7_200_000L))
        assertEquals(VodGoToTimeFields(0, 0, 0), vodGoToTimeFieldsStepped(fields, VodTimeField.HOURS, -5, 7_200_000L))
        assertEquals(VodGoToTimeFields(2, 0, 0), vodGoToTimeFieldsStepped(fields, VodTimeField.HOURS, 5, 7_200_000L))
    }

    @Test
    fun goToTimeSteppingIsIgnoredWhenDurationUnknown() {
        val fields = VodGoToTimeFields(0, 42, 16)
        assertEquals(fields, vodGoToTimeFieldsStepped(fields, VodTimeField.SECONDS, 1, 0L))
    }

    @Test
    fun goToTimeSteppingHoldsStartAndEndBoundsWithCarry() {
        // Start clamp: decrementing at zero stays at zero.
        assertEquals(
            VodGoToTimeFields(0, 0, 0),
            vodGoToTimeFieldsStepped(VodGoToTimeFields(0, 0, 0), VodTimeField.SECONDS, -1, 3_723_000L),
        )
        // End clamp: incrementing past the media duration clamps to the duration.
        assertEquals(
            VodGoToTimeFields(1, 2, 3),
            vodGoToTimeFieldsStepped(VodGoToTimeFields(1, 2, 3), VodTimeField.SECONDS, 1, 3_723_000L),
        )
        // Carry/borrow across second and minute boundaries.
        assertEquals(
            VodGoToTimeFields(1, 3, 0),
            vodGoToTimeFieldsStepped(VodGoToTimeFields(1, 2, 59), VodTimeField.SECONDS, 1, 7_200_000L),
        )
        assertEquals(
            VodGoToTimeFields(1, 1, 59),
            vodGoToTimeFieldsStepped(VodGoToTimeFields(1, 2, 0), VodTimeField.SECONDS, -1, 7_200_000L),
        )
        assertEquals(
            VodGoToTimeFields(2, 0, 0),
            vodGoToTimeFieldsStepped(VodGoToTimeFields(1, 59, 0), VodTimeField.MINUTES, 1, 7_200_000L),
        )
    }

    @Test
    fun speedOptionsAreTheApprovedBoundedSet() {
        assertEquals(listOf(0.75f, 1f, 1.25f, 1.5f, 2f), VOD_PLAYER_SPEED_OPTIONS)
        assertEquals("1x", vodSpeedLabel(1f))
        assertEquals("0.75x", vodSpeedLabel(0.75f))
        assertEquals("1.25x", vodSpeedLabel(1.25f))
        assertEquals("2x", vodSpeedLabel(2f))
        assertEquals(1f, vodNormalizedSpeed(3f), 0f)
        assertEquals(1.5f, vodNormalizedSpeed(1.5f), 0f)
    }

    @Test
    fun pictureSizeLabelsKeepHamzaAndMapToExistingModes() {
        assertEquals("ملائم", livePlayerResizeLabel(0))
        assertEquals("تكبير", livePlayerResizeLabel(1))
        assertEquals("ملء الشاشة", livePlayerResizeLabel(2))
    }

    @Test
    fun seekFractionIsDirectForForwardReverseAndEndpoints() {
        // The same helper feeds the Movies fill/knob and the preview pointer with no positional
        // easing, so reverse movement and endpoints map immediately to the current target.
        assertEquals(0.25f, vodPreviewCardFraction(30_000L, 120_000L), 0.001f)
        assertEquals(0.75f, vodPreviewCardFraction(90_000L, 120_000L), 0.001f)
        assertEquals(0.25f, vodPreviewCardFraction(30_000L, 120_000L), 0.001f)
        assertEquals(19f, vodSeekThumbCenterPx(vodPreviewCardFraction(0L, 120_000L), 1_000f, 38f), 0.001f)
        assertEquals(981f, vodSeekThumbCenterPx(vodPreviewCardFraction(120_000L, 120_000L), 1_000f, 38f), 0.001f)
    }

    @Test
    fun activeSeekHoldsControlsOnlyForVodWithRemoteInput() {
        // VOD TV timeline focus or direct seeking keeps the tools usable.
        assertTrue(
            vodActiveSeekHoldsControls(
                isVod = true, remoteInput = true, timelineFocused = true, directSeekActive = false,
            ),
        )
        assertTrue(
            vodActiveSeekHoldsControls(
                isVod = true, remoteInput = true, timelineFocused = false, directSeekActive = true,
            ),
        )
        // Leaving the interaction restores ordinary auto-hide.
        assertFalse(
            vodActiveSeekHoldsControls(
                isVod = true, remoteInput = true, timelineFocused = false, directSeekActive = false,
            ),
        )
        // Phone touch and Live callers keep their existing behavior.
        assertFalse(
            vodActiveSeekHoldsControls(
                isVod = true, remoteInput = false, timelineFocused = true, directSeekActive = true,
            ),
        )
        assertFalse(
            vodActiveSeekHoldsControls(
                isVod = false, remoteInput = true, timelineFocused = true, directSeekActive = true,
            ),
        )
    }

    @Test
    fun vodProgressPersistenceStaysBlockedUntilTheResumeDecisionIsAccepted() {
        // A Movie or Series episode with a saved position and resume enabled: preparation and
        // cancellation cannot overwrite the authoritative progress while the decision is pending.
        assertTrue(
            vodProgressPersistenceBlockedInitially(
                isVod = true,
                resumePlaybackEnabled = true,
                resumePositionMs = 1_200_000L,
            ),
        )
        // No pending decision keeps normal saving.
        assertFalse(
            vodProgressPersistenceBlockedInitially(
                isVod = true,
                resumePlaybackEnabled = true,
                resumePositionMs = 0L,
            ),
        )
        assertFalse(
            vodProgressPersistenceBlockedInitially(
                isVod = true,
                resumePlaybackEnabled = false,
                resumePositionMs = 1_200_000L,
            ),
        )
        // Live has no saved-position Resume decision and keeps normal behavior.
        assertFalse(
            vodProgressPersistenceBlockedInitially(
                isVod = false,
                resumePlaybackEnabled = true,
                resumePositionMs = 1_200_000L,
            ),
        )
        // Cancellation (no acceptance) keeps the block through lifecycle/disposal/late callbacks.
        assertTrue(
            vodProgressPersistenceBlockedAfterDecision(
                blocked = vodProgressPersistenceBlockedInitially(true, true, 1_200_000L),
                decisionAccepted = false,
            ),
        )
        // One explicit Resume/Restart clears it; later ticks save normally.
        assertFalse(
            vodProgressPersistenceBlockedAfterDecision(
                blocked = vodProgressPersistenceBlockedInitially(true, true, 1_200_000L),
                decisionAccepted = true,
            ),
        )
        assertFalse(vodProgressPersistenceBlockedAfterDecision(blocked = false, decisionAccepted = false))
        assertFalse(vodProgressPersistenceBlockedAfterDecision(blocked = false, decisionAccepted = true))
    }

    @Test
    fun movieResumeBackHandlesOneNavigationPerPress() {
        fun disposition(
            key: String,
            keyDown: Boolean = true,
            isRepeat: Boolean = false,
            remoteInput: Boolean,
        ): MovieResumeBackDisposition = movieResumeBackDisposition(
            isBackKey = key == "BACK",
            isEscapeKey = key == "ESCAPE",
            keyDown = keyDown,
            isRepeat = isRepeat,
            remoteInput = remoteInput,
        )

        // Remote BACK: the first down dispatches the cancellation exactly once; the release and
        // any repeat are never handled again.
        val remotePress = listOf(
            disposition("BACK", keyDown = true, remoteInput = true),
            disposition("BACK", keyDown = false, remoteInput = true),
        )
        assertEquals(MovieResumeBackDisposition.HANDLE_AND_CONSUME, remotePress[0])
        assertEquals(MovieResumeBackDisposition.PASS_TO_DIALOG, remotePress[1])
        assertEquals(1, remotePress.count { it == MovieResumeBackDisposition.HANDLE_AND_CONSUME })
        assertEquals(
            MovieResumeBackDisposition.PASS_TO_DIALOG,
            disposition("BACK", keyDown = true, isRepeat = true, remoteInput = true),
        )

        // Touch BACK: the system/BackHandler owner performs the single cancellation.
        val touchPress = listOf(
            disposition("BACK", keyDown = true, remoteInput = false),
            disposition("BACK", keyDown = false, remoteInput = false),
        )
        assertEquals(MovieResumeBackDisposition.PASS_TO_SYSTEM, touchPress[0])
        assertEquals(1, touchPress.count { it == MovieResumeBackDisposition.PASS_TO_SYSTEM })

        // ESCAPE is always handled; every other key keeps the dialog focus graph and background
        // suppression unchanged.
        assertEquals(
            MovieResumeBackDisposition.HANDLE_AND_CONSUME,
            disposition("ESCAPE", remoteInput = false),
        )
        assertEquals(
            MovieResumeBackDisposition.PASS_TO_DIALOG,
            disposition("DPAD_DOWN", remoteInput = true),
        )
        assertEquals(
            MovieResumeBackDisposition.PASS_TO_DIALOG,
            disposition("DPAD_CENTER", remoteInput = true),
        )

        // Cancelling through Back never enables progress persistence (R27 protection).
        assertTrue(vodProgressPersistenceBlockedAfterDecision(blocked = true, decisionAccepted = false))
    }

    @Test
    fun downloadActionLabelsStayTruthfulAndGlyphFree() {
        val base = OfflineDownload(
            downloadId = 1L,
            historyKey = "MOVIE:7",
            title = "Movie",
            posterUrl = null,
            streamKind = "movie",
            streamId = 7,
            extension = "mp4",
            status = OfflineStatus.DOWNLOADING,
            bytesDownloaded = 42L,
            totalBytes = 100L,
        )
        assertEquals("تحميل الفلم", movieDownloadActionLabel(null))
        assertEquals("ايقاف التحميل 42%", movieDownloadActionLabel(base))
        assertEquals("استئناف التحميل", movieDownloadActionLabel(base.copy(status = OfflineStatus.PAUSED)))
        assertEquals("اعادة التحميل", movieDownloadActionLabel(base.copy(status = OfflineStatus.FAILED)))
        assertEquals("تم التحميل", movieDownloadActionLabel(base.copy(status = OfflineStatus.COMPLETED)))
    }
}
