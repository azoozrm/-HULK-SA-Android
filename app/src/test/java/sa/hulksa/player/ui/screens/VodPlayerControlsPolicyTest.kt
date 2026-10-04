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
    fun compactStripStaysSingleRowOnlyWhenMeasuredCaptionsFit() {
        assertFalse(vodCompactSingleRow(approvedSingleRow = false, availableWidthPx = 2_000, requiredWidthPx = 100))
        assertTrue(vodCompactSingleRow(approvedSingleRow = true, availableWidthPx = 2_000, requiredWidthPx = 100))
        assertFalse(vodCompactSingleRow(approvedSingleRow = true, availableWidthPx = 100, requiredWidthPx = 101))
        assertTrue(vodCompactSingleRow(approvedSingleRow = true, availableWidthPx = 100, requiredWidthPx = 100))
        assertFalse(vodCompactSingleRow(approvedSingleRow = true, availableWidthPx = 100, requiredWidthPx = 0))
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
    fun previewPointerStaysUnderTheThumbAndClampsAtEdges() {
        assertEquals(
            0f,
            vodPreviewPointerOffsetPx(thumbXpx = 10f, cardLeftPx = 100f, cardWidthPx = 200f, pointerWidthPx = 14f),
            0.001f,
        )
        assertEquals(
            43f,
            vodPreviewPointerOffsetPx(thumbXpx = 150f, cardLeftPx = 100f, cardWidthPx = 200f, pointerWidthPx = 14f),
            0.001f,
        )
        assertEquals(
            186f,
            vodPreviewPointerOffsetPx(thumbXpx = 900f, cardLeftPx = 0f, cardWidthPx = 200f, pointerWidthPx = 14f),
            0.001f,
        )
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
    fun previewDecodingAcceptsDirectMediaAndRejectsPlaylists() {
        assertTrue(vodPreviewDecodableCandidate("https://host/movie/user/pass/7.mp4"))
        assertTrue(vodPreviewDecodableCandidate("file:///data/user/0/app/files/movie.mkv"))
        assertTrue(vodPreviewDecodableCandidate("content://media/movie/7"))
        assertTrue(vodPreviewDecodableCandidate("/storage/emulated/0/movie.avi"))
        assertFalse(vodPreviewDecodableCandidate("https://host/live/user/pass/7.m3u8"))
        assertFalse(vodPreviewDecodableCandidate("https://host/list.m3u"))
        assertFalse(vodPreviewDecodableCandidate(""))
        assertFalse(vodPreviewDecodableCandidate(null))
    }

    @Test
    fun previewBucketAndFractionStayInRange() {
        assertEquals(0L, vodPreviewBucketMs(4_999L))
        assertEquals(5_000L, vodPreviewBucketMs(5_000L))
        assertEquals(10_000L, vodPreviewBucketMs(12_001L))
        assertEquals(0f, vodPreviewCardFraction(-1L, 60_000L), 0.001f)
        assertEquals(0.5f, vodPreviewCardFraction(30_000L, 60_000L), 0.001f)
        assertEquals(1f, vodPreviewCardFraction(90_000L, 60_000L), 0.001f)
        assertEquals(0f, vodPreviewCardFraction(30_000L, 0L), 0.001f)
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
