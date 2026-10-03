package sa.hulksa.player.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
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
    fun movieDetailsTabIvorySupersedesGoldWithoutChangingGlobalTokens() {
        val colors = HulkColors()
        assertEquals(Color(0xFFFFF9EB), colors.text)
        assertEquals(Color(0xFFE6C352), colors.gold)
    }
}
