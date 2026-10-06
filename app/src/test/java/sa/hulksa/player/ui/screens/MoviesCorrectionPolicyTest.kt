package sa.hulksa.player.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy
import sa.hulksa.player.ui.components.movieRecentPlayedFraction
import sa.hulksa.player.ui.components.movieRecentTimeText
import sa.hulksa.player.ui.theme.HulkColors

class MoviesCorrectionPolicyTest {

    private fun download(
        status: OfflineStatus,
        bytes: Long = 42L,
        total: Long = 100L,
    ): OfflineDownload = OfflineDownload(
        downloadId = 1L,
        historyKey = "MOVIE:7",
        title = "Movie",
        posterUrl = null,
        streamKind = "movie",
        streamId = 7,
        extension = "mp4",
        status = status,
        bytesDownloaded = bytes,
        totalBytes = total,
    )

    @Test
    fun movieVisibilityRoundTripsAndDefaultsToAllVisible() {
        assertTrue(decodeMovieHiddenCategoryIds(null).isEmpty())
        assertTrue(decodeMovieHiddenCategoryIds("").isEmpty())
        assertEquals(setOf("a", "b"), decodeMovieHiddenCategoryIds(" b, a "))
        assertEquals("a,b", encodeMovieHiddenCategoryIds(setOf("b", "a")))
        assertEquals("", encodeMovieHiddenCategoryIds(emptySet()))
    }

    @Test
    fun hiddenFallbackOnlyAppliesToServerCategorySelection() {
        val hidden = setOf("server-1")
        assertTrue(isHiddenServerCategorySelection("server-1", hidden))
        assertFalse(isHiddenServerCategorySelection("server-2", hidden))
        assertFalse(isHiddenServerCategorySelection(null, hidden))
        assertFalse(isHiddenServerCategorySelection("server-1", emptySet()))
    }

    @Test
    fun movieGridYieldsFiveWideColumnsOnObservedTvAndFewerNarrow() {
        val wide = tvCatalogMetrics(screenWidthDp = 960, screenHeightDp = 540, movieCards = true)
        val available = 960f - wide.horizontalContentPaddingDp - wide.endContentPaddingDp
        assertEquals(
            5,
            movieCatalogColumnCount(available, wide.horizontalSpacingDp, wide.minCellWidthDp),
        )

        val compact = tvCatalogMetrics(screenWidthDp = 540, screenHeightDp = 540, movieCards = true)
        val compactAvailable = 540f - compact.horizontalContentPaddingDp - compact.endContentPaddingDp
        assertTrue(
            movieCatalogColumnCount(
                compactAvailable,
                compact.horizontalSpacingDp,
                compact.minCellWidthDp,
            ) <= 3,
        )

        val medium = tvCatalogMetrics(screenWidthDp = 720, screenHeightDp = 540, movieCards = true)
        val mediumAvailable = 720f - medium.horizontalContentPaddingDp - medium.endContentPaddingDp
        assertTrue(
            movieCatalogColumnCount(
                mediumAvailable,
                medium.horizontalSpacingDp,
                medium.minCellWidthDp,
            ) <= 4,
        )
    }

    @Test
    fun movieColumnCountIsAlwaysPositive() {
        assertEquals(1, movieCatalogColumnCount(0f, 14f, 170f))
        assertEquals(1, movieCatalogColumnCount(100f, 14f, 170f))
    }

    @Test
    fun movieGridKeepsSafePhysicalLeftPaddingWhileSeriesKeepsLegacyPadding() {
        val movie = tvCatalogMetrics(screenWidthDp = 960, screenHeightDp = 540, movieCards = true)
        val series = tvCatalogMetrics(screenWidthDp = 960, screenHeightDp = 540, movieCards = false)
        val premium = tvPremiumWindowPolicy(960, 540)

        assertTrue(movie.endContentPaddingDp >= premium.horizontalSafeInsetDp)
        assertEquals(6f, series.endContentPaddingDp, 0.001f)
    }

    @Test
    fun movieCompactHeroStaysInsideTheWindow() {
        listOf(360, 540, 720, 1080).forEach { height ->
            val hero = movieCompactHeroHeightDp(height)
            assertTrue(hero <= (height - 96).coerceAtLeast(0))
            assertTrue(hero > 0)
        }
        assertTrue(movieCompactHeroHeightDp(1080) <= 460)
    }

    @Test
    fun movieResumeGatingClampsAndRejectsInvalidDuration() {
        assertNull(movieResumeProgress(positionMs = 0L, durationMs = 1_000L))
        assertNull(movieResumeProgress(positionMs = 1_000L, durationMs = 0L))
        assertNull(movieResumeProgress(positionMs = -5L, durationMs = 1_000L))
        assertNull(movieResumeProgress(positionMs = 960L, durationMs = 1_000L))
        assertEquals(0.5f, movieResumeProgress(positionMs = 500L, durationMs = 1_000L) ?: -1f, 0.001f)
        assertEquals(0.949f, movieResumeProgress(positionMs = 949L, durationMs = 1_000L) ?: -1f, 0.01f)
    }

    @Test
    fun moviePositionFormattingKeepsLtrNumericGroups() {
        assertEquals("00:00", movieFormatPosition(-1L))
        assertEquals("01:25", movieFormatPosition(85_000L))
        assertEquals("1:02:03", movieFormatPosition(3_723_000L))
    }

    @Test
    fun movieDownloadPresentationFollowsAuthoritativeStates() {
        assertEquals("تحميل الفلم", movieDownloadActionLabel(null))
        assertEquals("ايقاف التحميل 42%", movieDownloadActionLabel(download(OfflineStatus.DOWNLOADING)))
        assertEquals("استئناف التحميل", movieDownloadActionLabel(download(OfflineStatus.PAUSED)))
        assertEquals("اعادة التحميل", movieDownloadActionLabel(download(OfflineStatus.FAILED)))
        assertEquals("تم التحميل", movieDownloadActionLabel(download(OfflineStatus.COMPLETED)))

        val downloadIcon = Icons.Rounded.Download
        assertEquals(downloadIcon, movieDownloadActionIcon(null))
        assertEquals(
            Icons.Rounded.Pause,
            movieDownloadActionIcon(download(OfflineStatus.DOWNLOADING)),
        )
        assertEquals(
            downloadIcon,
            movieDownloadActionIcon(download(OfflineStatus.PAUSED)),
        )
        assertEquals(
            Icons.Rounded.Refresh,
            movieDownloadActionIcon(download(OfflineStatus.FAILED)),
        )
        assertEquals(
            Icons.Rounded.Check,
            movieDownloadActionIcon(download(OfflineStatus.COMPLETED)),
        )
    }

    @Test
    fun movieDetailsTabLabelsUseApprovedCopyAndOrder() {
        val labels = MovieDetailsTab.entries.map { it.label }
        assertEquals(listOf("القصة", "معلومات الفلم", "افلام مشابهة"), labels)
        labels.forEach { label ->
            assertFalse(label.contains('أ'))
            assertFalse(label.contains('إ'))
            assertFalse(label.contains('آ'))
        }
    }

    @Test
    fun focusedCardRevealKeepsTheWholeCardInsideTheUsableWindow() {
        assertEquals(
            0,
            focusedCardScrollCorrection(itemTop = 110, itemBottom = 410, usableStart = 100, usableEnd = 500, marginPx = 10),
        )
        assertEquals(
            60,
            focusedCardScrollCorrection(itemTop = 250, itemBottom = 550, usableStart = 100, usableEnd = 500, marginPx = 10),
        )
        assertEquals(
            -60,
            focusedCardScrollCorrection(itemTop = 50, itemBottom = 350, usableStart = 100, usableEnd = 500, marginPx = 10),
        )
        assertEquals(
            -50,
            focusedCardScrollCorrection(itemTop = 50, itemBottom = 440, usableStart = 100, usableEnd = 500, marginPx = 10),
        )
        assertEquals(
            0,
            focusedCardScrollCorrection(itemTop = 100, itemBottom = 500, usableStart = 100, usableEnd = 500, marginPx = 10),
        )
    }

    @Test
    fun movieCompactArtworkOnlyCapsWhenTheAcceptedCardCannotFit() {
        assertNull(
            movieCompactArtworkHeightPx(
                cellWidthPx = 400,
                footerHeightPx = 120,
                usableHeightPx = 600,
                minArtworkHeightPx = 96,
            ),
        )
        assertEquals(
            360,
            movieCompactArtworkHeightPx(
                cellWidthPx = 400,
                footerHeightPx = 120,
                usableHeightPx = 480,
                minArtworkHeightPx = 96,
            ),
        )
        assertEquals(
            96,
            movieCompactArtworkHeightPx(
                cellWidthPx = 400,
                footerHeightPx = 120,
                usableHeightPx = 180,
                minArtworkHeightPx = 96,
            ),
        )
        assertNull(
            movieCompactArtworkHeightPx(
                cellWidthPx = 0,
                footerHeightPx = 120,
                usableHeightPx = 480,
                minArtworkHeightPx = 96,
            ),
        )
    }

    @Test
    fun movieDetailsRevealUsesKeyedSectionAndParentListIndex() {
        assertEquals(1, movieDetailsTabsItemIndex(hasError = false))
        assertEquals(2, movieDetailsTabsItemIndex(hasError = true))
        assertEquals(
            "movie_tv_polished_information",
            movieDetailsSectionItemKey(MovieDetailsTab.INFORMATION, tvPolished = true),
        )
        assertEquals(
            "movie_tv_polished_related_tab",
            movieDetailsSectionItemKey(MovieDetailsTab.RELATED, tvPolished = true),
        )
        assertEquals("movie_information", movieDetailsSectionItemKey(MovieDetailsTab.INFORMATION, tvPolished = false))
        assertEquals("movie_related", movieDetailsSectionItemKey(MovieDetailsTab.RELATED, tvPolished = false))
        assertEquals("movie_story", movieDetailsSectionItemKey(MovieDetailsTab.STORY, tvPolished = false))
    }

    @Test
    fun movieDetailsRevealOnlyFiresWhenTheSelectedSectionIsNotComplete() {
        assertTrue(movieDetailsSectionNeedsReveal(sectionPresent = false, 0, 0, 0, 200))
        assertFalse(movieDetailsSectionNeedsReveal(sectionPresent = true, 10, 180, 0, 200))
        assertTrue(movieDetailsSectionNeedsReveal(sectionPresent = true, 10, 260, 0, 200))
        assertTrue(movieDetailsSectionNeedsReveal(sectionPresent = true, -40, 100, 0, 200))
    }

    @Test
    fun movieDetailsReadScrollStopsWhenTheSelectedSectionEndIsVisible() {
        assertTrue(movieDetailsPanelNeedsMoreScroll(sectionPresent = false, sectionBottom = 0, viewportEnd = 200))
        assertTrue(movieDetailsPanelNeedsMoreScroll(sectionPresent = true, sectionBottom = 260, viewportEnd = 200))
        assertFalse(movieDetailsPanelNeedsMoreScroll(sectionPresent = true, sectionBottom = 200, viewportEnd = 200))
    }

    @Test
    fun movieRecentTimeAndProgressStayTruthful() {
        assertEquals(
            "03:52 / 1:43:22",
            movieRecentTimeText(positionMs = 232_000L, durationMs = 6_202_000L),
        )
        assertEquals(
            "00:02 / 1:34:26",
            movieRecentTimeText(positionMs = 2_000L, durationMs = 5_666_000L),
        )
        assertEquals("00:02", movieRecentTimeText(positionMs = 2_000L, durationMs = 0L))
        assertEquals("00:00", movieRecentTimeText(positionMs = -5L, durationMs = 0L))
        assertEquals(0f, movieRecentPlayedFraction(positionMs = 5_000L, durationMs = 0L), 0.0001f)
        assertEquals(1f, movieRecentPlayedFraction(positionMs = 5_000L, durationMs = 1_000L), 0.0001f)
        assertEquals(
            0.00035f,
            movieRecentPlayedFraction(positionMs = 2_000L, durationMs = 5_666_000L),
            0.0001f,
        )
    }

    @Test
    fun focusPathAndRevealUseIdenticalUsableBounds() {
        // Same usable window for every scenario: start 100, end 400, Movie margin 20.
        data class Scenario(val top: Int, val bottom: Int, val expectedDirect: Boolean)
        val scenarios = listOf(
            Scenario(240, 370, true),
            Scenario(110, 380, true),
            Scenario(260, 420, false),
            Scenario(60, 200, false),
            Scenario(395, 560, false),
        )
        scenarios.forEach { scenario ->
            val path = tvCatalogFocusPath(
                targetIndex = 4,
                itemCount = 12,
                itemTop = scenario.top,
                itemBottom = scenario.bottom,
                viewportStart = 100,
                viewportEnd = 400,
                extraMargin = 20,
            )
            val correction = focusedCardScrollCorrection(
                itemTop = scenario.top,
                itemBottom = scenario.bottom,
                usableStart = 100,
                usableEnd = 400,
                marginPx = 20,
            )
            assertEquals(
                "path and correction disagree for " + scenario,
                if (scenario.expectedDirect) TvCatalogFocusPath.DIRECT else TvCatalogFocusPath.SCROLL_ASSISTED,
                path,
            )
            assertEquals(
                "direct decision must mean zero scroll for " + scenario,
                scenario.expectedDirect,
                correction == 0,
            )
        }
    }

    @Test
    fun fullyVisibleHorizontalTargetNeedsNoVerticalScroll() {
        // A card inside the usable bounds (with Movie margin) is DIRECT with a zero correction:
        // a LEFT/RIGHT move can only transfer focus.
        assertEquals(
            TvCatalogFocusPath.DIRECT,
            tvCatalogFocusPath(6, 12, 130, 260, 100, 400, extraMargin = 20),
        )
        assertEquals(
            0,
            focusedCardScrollCorrection(130, 260, 100, 400, 20),
        )
    }

    @Test
    fun newerFocusTargetsReplaceStalePendingTargets() {
        val state = TvCatalogFocusMoveState()
        assertEquals(3, state.baseIndex(3))
        state.begin(7)
        assertEquals(7, state.baseIndex(3))
        assertEquals(7, state.pendingTargetIndex())
        // A late completion for an older target must not clear the newer pending target.
        state.complete(5)
        assertEquals(7, state.pendingTargetIndex())
        state.begin(11)
        assertEquals(11, state.pendingTargetIndex())
        state.complete(11)
        assertNull(state.pendingTargetIndex())
        assertEquals(9, state.baseIndex(9))
    }

    @Test
    fun moviesRelocationSpecNeverScrollsOnFocus() {
        val spec = moviesNoBringIntoViewSpec()
        assertEquals(0f, spec.calculateScrollDistance(offset = 800f, size = 400f, containerSize = 700f), 0.001f)
        assertEquals(0f, spec.calculateScrollDistance(offset = -50f, size = 120f, containerSize = 700f), 0.001f)
    }

    @Test
    fun movieDetailsTabIvorySupersedesGoldWithoutChangingGlobalTokens() {
        val colors = HulkColors()
        assertEquals(Color(0xFFFFF9EB), colors.text)
        assertEquals(Color(0xFFE6C352), colors.gold)
    }

    @Test
    fun movieDownloadStatusCaptionsAreTruthfulPerState() {
        assertEquals("تحميل الفلم", movieDownloadStatusCaption(null))
        assertEquals("في انتظار التحميل", movieDownloadStatusCaption(OfflineStatus.QUEUED))
        assertEquals("جاري تجهيز التحميل", movieDownloadStatusCaption(OfflineStatus.CHECKING))
        assertEquals("جاري التحميل", movieDownloadStatusCaption(OfflineStatus.DOWNLOADING))
        assertEquals("استئناف التحميل", movieDownloadStatusCaption(OfflineStatus.PAUSED))
        assertEquals("في انتظار الموعد", movieDownloadStatusCaption(OfflineStatus.WAITING_SCHEDULE))
        assertEquals("في انتظار الشبكة", movieDownloadStatusCaption(OfflineStatus.WAITING_NETWORK))
        assertEquals("في انتظار المساحة", movieDownloadStatusCaption(OfflineStatus.WAITING_STORAGE))
        assertEquals("اعادة التحميل", movieDownloadStatusCaption(OfflineStatus.FAILED))
        assertEquals("تم التحميل", movieDownloadStatusCaption(OfflineStatus.COMPLETED))

        val captions = OfflineStatus.entries.map { movieDownloadStatusCaption(it) } + listOf("تحميل الفلم")
        captions.forEach { caption ->
            assertFalse(caption.contains('أ'))
            assertFalse(caption.contains('إ'))
            assertFalse(caption.contains('آ'))
        }
        assertTrue(captions.contains("استئناف التحميل"))
    }

    @Test
    fun movieDownloadTelemetryNeverFabricatesUnknownTotals() {
        val known = download(OfflineStatus.DOWNLOADING).copy(bytesDownloaded = 42L, totalBytes = 100L)
        assertEquals("42%", movieDownloadPercentLabel(known))
        assertNull(
            movieDownloadPercentLabel(
                download(OfflineStatus.DOWNLOADING).copy(bytesDownloaded = 5L, totalBytes = -1L),
            ),
        )
        assertNull(movieDownloadPercentLabel(null))
        assertEquals(
            "100%",
            movieDownloadPercentLabel(
                download(OfflineStatus.DOWNLOADING).copy(bytesDownloaded = 500L, totalBytes = 100L),
            ),
        )
        assertEquals(
            "99%",
            movieDownloadPercentLabel(
                download(OfflineStatus.DOWNLOADING).copy(bytesDownloaded = 999L, totalBytes = 1000L),
            ),
        )
        assertEquals(Icons.Rounded.Download, movieDownloadControlIcon(known))
        assertEquals(
            Icons.Rounded.Check,
            movieDownloadControlIcon(download(OfflineStatus.COMPLETED)),
        )
    }

    @Test
    fun movieDownloadSpeedIsShownOnlyWhileActiveAndNeverStale() {
        assertTrue(movieDownloadShowsSpeed(OfflineStatus.DOWNLOADING))
        listOf(
            OfflineStatus.QUEUED,
            OfflineStatus.CHECKING,
            OfflineStatus.PAUSED,
            OfflineStatus.WAITING_SCHEDULE,
            OfflineStatus.WAITING_NETWORK,
            OfflineStatus.WAITING_STORAGE,
            OfflineStatus.FAILED,
            OfflineStatus.COMPLETED,
        ).forEach { assertFalse(movieDownloadShowsSpeed(it)) }
        assertFalse(movieDownloadShowsSpeed(null))
        assertNull(movieDownloadSpeedLabel(0L))
        assertNull(movieDownloadSpeedLabel(-10L))
        assertEquals("3.2 MB/s", movieDownloadSpeedLabel(3_355_443L))
        assertEquals("20 KB/s", movieDownloadSpeedLabel(20_480L))
    }

    @Test
    fun movieDownloadSizesAndEtaUseRealValuesOnly() {
        assertEquals("1.7 GB", movieDownloadSizeLabel(1_800_000_000L))
        assertEquals("120 MB", movieDownloadSizeLabel(125_829_120L))
        val known = download(OfflineStatus.DOWNLOADING).copy(
            bytesDownloaded = 44_040_192L,
            totalBytes = 104_857_600L,
        )
        assertEquals("42 MB / 100 MB", movieDownloadSizePairLabel(known))
        assertEquals(
            "5 MB",
            movieDownloadSizePairLabel(known.copy(bytesDownloaded = 5_242_880L, totalBytes = -1L)),
        )
        assertNull(movieDownloadSizePairLabel(null))
        assertNull(movieDownloadEtaLabel(0L))
        assertNull(movieDownloadEtaLabel(-5L))
        assertEquals("متبقي 1 د", movieDownloadEtaLabel(30L))
        assertEquals("متبقي 12 د", movieDownloadEtaLabel(720L))
        assertEquals("متبقي 1 س", movieDownloadEtaLabel(3_600L))
        assertEquals("متبقي 1 س 5 د", movieDownloadEtaLabel(3_900L))
    }

    @Test
    fun movieDownloadPanelActionsMatchExistingDispatchers() {
        listOf(
            OfflineStatus.QUEUED,
            OfflineStatus.CHECKING,
            OfflineStatus.DOWNLOADING,
        ).forEach {
            assertEquals(
                listOf(MovieDownloadPanelActionKind.PAUSE, MovieDownloadPanelActionKind.CANCEL),
                movieDownloadPanelActionKinds(it),
            )
        }
        listOf(
            OfflineStatus.PAUSED,
            OfflineStatus.WAITING_SCHEDULE,
            OfflineStatus.WAITING_NETWORK,
            OfflineStatus.WAITING_STORAGE,
        ).forEach {
            assertEquals(
                listOf(MovieDownloadPanelActionKind.RESUME, MovieDownloadPanelActionKind.CANCEL),
                movieDownloadPanelActionKinds(it),
            )
        }
        assertEquals(
            listOf(MovieDownloadPanelActionKind.RETRY, MovieDownloadPanelActionKind.CANCEL),
            movieDownloadPanelActionKinds(OfflineStatus.FAILED),
        )
        assertTrue(movieDownloadPanelActionKinds(OfflineStatus.COMPLETED).isEmpty())

        assertEquals("ايقاف التحميل", movieDownloadPanelActionLabel(MovieDownloadPanelActionKind.PAUSE))
        assertEquals("استئناف التحميل", movieDownloadPanelActionLabel(MovieDownloadPanelActionKind.RESUME))
        assertEquals("اعادة التحميل", movieDownloadPanelActionLabel(MovieDownloadPanelActionKind.RETRY))
        assertEquals("الغاء التحميل", movieDownloadPanelActionLabel(MovieDownloadPanelActionKind.CANCEL))
        assertEquals(Icons.Rounded.Pause, movieDownloadPanelActionIcon(MovieDownloadPanelActionKind.PAUSE))
        assertEquals(Icons.Rounded.PlayArrow, movieDownloadPanelActionIcon(MovieDownloadPanelActionKind.RESUME))
        assertEquals(Icons.Rounded.Refresh, movieDownloadPanelActionIcon(MovieDownloadPanelActionKind.RETRY))
        assertEquals(Icons.Rounded.Close, movieDownloadPanelActionIcon(MovieDownloadPanelActionKind.CANCEL))
    }

    @Test
    fun movieActionHeightFollowsTheCompactPolicy() {
        assertEquals(46, movieActionHeightDp(isTv = true, compactHeight = true))
        assertEquals(46, movieActionHeightDp(isTv = true, compactHeight = false))
        assertEquals(42, movieActionHeightDp(isTv = false, compactHeight = true))
        assertEquals(46, movieActionHeightDp(isTv = false, compactHeight = false))
    }

    @Test
    fun movieDownloadControlCaptionShowsOnlyRealInlinePercentage() {
        assertEquals("تحميل الفلم", movieDownloadControlCaption(null))
        val downloading = download(OfflineStatus.DOWNLOADING).copy(bytesDownloaded = 42L, totalBytes = 100L)
        assertEquals("جاري التحميل 42%", movieDownloadControlCaption(downloading))
        assertEquals(
            "جاري التحميل",
            movieDownloadControlCaption(downloading.copy(totalBytes = -1L)),
        )
        assertEquals(
            "استئناف التحميل 50%",
            movieDownloadControlCaption(
                downloading.copy(status = OfflineStatus.PAUSED, bytesDownloaded = 50L),
            ),
        )
    }

    @Test
    fun movieActionLayoutAllocatesFullCaptionsByMeasuredWidth() {
        assertEquals(
            147,
            movieActionRequiredWidthPx(
                captionWidthPx = 100,
                iconSizePx = 17,
                horizontalPaddingPx = 12,
                gapPx = 6,
            ),
        )
        val required = listOf(100, 100, 140)
        assertEquals(
            MovieActionLayout.SINGLE_ROW,
            movieActionLayoutMode(availableWidthPx = 450, requiredWidthsPx = required, gapPx = 9),
        )
        assertEquals(
            MovieActionLayout.SINGLE_ROW,
            movieActionLayoutMode(availableWidthPx = 438, requiredWidthsPx = required, gapPx = 9),
        )
        assertEquals(
            MovieActionLayout.WATCH_THEN_PAIR,
            movieActionLayoutMode(availableWidthPx = 320, requiredWidthsPx = required, gapPx = 9),
        )
        assertEquals(
            MovieActionLayout.STACKED,
            movieActionLayoutMode(availableWidthPx = 250, requiredWidthsPx = required, gapPx = 9),
        )
        assertEquals(
            MovieActionLayout.STACKED,
            movieActionLayoutMode(availableWidthPx = 0, requiredWidthsPx = required, gapPx = 9),
        )
    }
}
