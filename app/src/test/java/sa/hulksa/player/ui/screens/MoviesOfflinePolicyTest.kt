package sa.hulksa.player.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.WifiOff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoviesOfflinePolicyTest {

    @Test
    fun movieOfflineRestorePreservesPausedIntentAndPendingDecision() {
        assertTrue(
            movieOfflineRestoredPlayWhenReady(
                wasPlayingBeforeOffline = true,
                resumePromptPending = false,
            ),
        )
        assertFalse(
            movieOfflineRestoredPlayWhenReady(
                wasPlayingBeforeOffline = false,
                resumePromptPending = false,
            ),
        )
        assertFalse(
            movieOfflineRestoredPlayWhenReady(
                wasPlayingBeforeOffline = true,
                resumePromptPending = true,
            ),
        )
    }

    @Test
    fun movieOfflineCardCopyMatchesTheOwnerWording() {
        val interrupted = movieOfflineCardCopy(resumePending = false, formattedSavedTime = "03:52")
        assertEquals("انقطع اتصال الانترنت", interrupted.title)
        assertEquals("سيعود التشغيل تلقائيا عند عودة الاتصال", interrupted.body)
        assertEquals("مكان توقفك محفوظ", interrupted.context)

        val pending = movieOfflineCardCopy(resumePending = true, formattedSavedTime = "03:52")
        assertEquals("لا يوجد اتصال بالانترنت", pending.title)
        assertEquals("اتصل بالانترنت لاكمال المشاهدة", pending.body)
        assertEquals("توقفت عند 03:52", pending.context)

        listOf(interrupted, pending).forEach { copy ->
            listOf(copy.title, copy.body, copy.context).forEach { line ->
                assertFalse(line.contains('أ'))
                assertFalse(line.contains('إ'))
                assertFalse(line.contains('آ'))
            }
        }
    }

    @Test
    fun moviesErrorIconReflectsTheFailureClass() {
        assertEquals(Icons.Rounded.WifiOff, moviesErrorIcon(networkFailure = true))
        assertEquals(Icons.Outlined.ErrorOutline, moviesErrorIcon(networkFailure = false))
    }

    @Test
    fun moviesErrorNoticeUsesTheRowOnlyWhenTheMeasuredGroupFits() {
        assertTrue(
            moviesErrorNoticeFitsRow(
                availableWidthPx = 700,
                messageWidthPx = 380,
                retryWidthPx = 150,
                gapPx = 24,
            ),
        )
        assertFalse(
            moviesErrorNoticeFitsRow(
                availableWidthPx = 500,
                messageWidthPx = 380,
                retryWidthPx = 150,
                gapPx = 24,
            ),
        )
        assertFalse(
            moviesErrorNoticeFitsRow(
                availableWidthPx = 0,
                messageWidthPx = 380,
                retryWidthPx = 150,
                gapPx = 24,
            ),
        )
    }

    @Test
    fun moviePlayerPresentationIsExclusiveForEveryCombination() {
        // Remote movie, pending Resume, offline at entry, no final error yet.
        assertEquals(
            MoviePlayerPresentation.ERROR_CARD,
            moviePlayerPresentation(
                isMovie = true,
                localPlayback = false,
                networkAvailable = false,
                offlineFailure = false,
                finalErrorPresent = false,
                resumePromptPending = true,
                offlineInitial = true,
            ),
        )
        // Remote movie, pending Resume, offline, generic final error already present.
        assertEquals(
            MoviePlayerPresentation.ERROR_CARD,
            moviePlayerPresentation(
                isMovie = true,
                localPlayback = false,
                networkAvailable = false,
                offlineFailure = true,
                finalErrorPresent = true,
                resumePromptPending = true,
                offlineInitial = false,
            ),
        )
        // Connected pending Resume with a generic final failure.
        assertEquals(
            MoviePlayerPresentation.ERROR_CARD,
            moviePlayerPresentation(
                isMovie = true,
                localPlayback = false,
                networkAvailable = true,
                offlineFailure = false,
                finalErrorPresent = true,
                resumePromptPending = true,
                offlineInitial = false,
            ),
        )
        // Connected pending Resume only.
        assertEquals(
            MoviePlayerPresentation.RESUME,
            moviePlayerPresentation(
                isMovie = true,
                localPlayback = false,
                networkAvailable = true,
                offlineFailure = false,
                finalErrorPresent = false,
                resumePromptPending = true,
                offlineInitial = false,
            ),
        )
        // Normal player.
        assertEquals(
            MoviePlayerPresentation.PLAYER,
            moviePlayerPresentation(
                isMovie = true,
                localPlayback = false,
                networkAvailable = true,
                offlineFailure = false,
                finalErrorPresent = false,
                resumePromptPending = false,
                offlineInitial = false,
            ),
        )
        // Local downloaded movie never takes the offline classification; a real local media
        // failure still uses the generic Movie error card.
        assertEquals(
            MoviePlayerPresentation.ERROR_CARD,
            moviePlayerPresentation(
                isMovie = true,
                localPlayback = true,
                networkAvailable = false,
                offlineFailure = true,
                finalErrorPresent = true,
                resumePromptPending = false,
                offlineInitial = false,
            ),
        )
        // A local downloaded movie with usable local media plays without the offline card.
        assertEquals(
            MoviePlayerPresentation.PLAYER,
            moviePlayerPresentation(
                isMovie = true,
                localPlayback = true,
                networkAvailable = false,
                offlineFailure = false,
                finalErrorPresent = false,
                resumePromptPending = false,
                offlineInitial = false,
            ),
        )
        // Series keeps its own legacy route.
        assertEquals(
            MoviePlayerPresentation.RESUME,
            moviePlayerPresentation(
                isMovie = false,
                localPlayback = false,
                networkAvailable = true,
                offlineFailure = false,
                finalErrorPresent = false,
                resumePromptPending = true,
                offlineInitial = false,
            ),
        )
        assertEquals(
            MoviePlayerPresentation.PLAYER,
            moviePlayerPresentation(
                isMovie = false,
                localPlayback = false,
                networkAvailable = true,
                offlineFailure = false,
                finalErrorPresent = true,
                resumePromptPending = false,
                offlineInitial = false,
            ),
        )
    }

    @Test
    fun moviePlayerErrorCopyIsOfflineOnlyForRealConnectivityLoss() {
        val offlinePending = moviePlayerErrorCopy(
            offline = true,
            resumePending = true,
            formattedSavedTime = "03:52",
            failureMessage = null,
        )
        assertEquals("لا يوجد اتصال بالانترنت", offlinePending.title)
        assertEquals("اتصل بالانترنت لاكمال المشاهدة", offlinePending.body)
        assertEquals("توقفت عند 03:52", offlinePending.context)

        val offlineInterrupted = moviePlayerErrorCopy(
            offline = true,
            resumePending = false,
            formattedSavedTime = "03:52",
            failureMessage = null,
        )
        assertEquals("انقطع اتصال الانترنت", offlineInterrupted.title)
        assertEquals("سيعود التشغيل تلقائيا عند عودة الاتصال", offlineInterrupted.body)

        val generic = moviePlayerErrorCopy(
            offline = false,
            resumePending = false,
            formattedSavedTime = "03:52",
            failureMessage = "تعذر تشغيل المحتوى. اعد المحاولة او اختر مصدرا اخر عند توفره.",
        )
        assertEquals("تعذر تشغيل الفلم", generic.title)
        assertEquals("تعذر تشغيل المحتوى. اعد المحاولة او اختر مصدرا اخر عند توفره.", generic.body)
        assertFalse(generic.body.contains('أ'))
        assertFalse(generic.body.contains('إ'))
        assertFalse(generic.body.contains('آ'))

        val genericFallback = moviePlayerErrorCopy(
            offline = false,
            resumePending = true,
            formattedSavedTime = "03:52",
            failureMessage = "  ",
        )
        assertEquals("تعذر تشغيل الفلم", genericFallback.title)
        assertEquals("حدث خطا اثناء التشغيل. حاول مرة اخرى.", genericFallback.body)
        assertEquals("توقفت عند 03:52", genericFallback.context)
    }

    @Test
    fun movieOfflineInitialEntryNeverWaitsForTheDelayedEffect() {
        assertTrue(
            movieOfflineInitialVisible(
                isMovie = true,
                localPlayback = false,
                networkAvailable = false,
                isPlaying = false,
                playbackReady = false,
            ),
        )
        assertFalse(
            movieOfflineInitialVisible(
                isMovie = true,
                localPlayback = false,
                networkAvailable = true,
                isPlaying = false,
                playbackReady = false,
            ),
        )
        assertFalse(
            movieOfflineInitialVisible(
                isMovie = true,
                localPlayback = false,
                networkAvailable = false,
                isPlaying = true,
                playbackReady = true,
            ),
        )
        assertFalse(
            movieOfflineInitialVisible(
                isMovie = false,
                localPlayback = false,
                networkAvailable = false,
                isPlaying = false,
                playbackReady = false,
            ),
        )
        assertFalse(
            movieOfflineInitialVisible(
                isMovie = true,
                localPlayback = true,
                networkAvailable = false,
                isPlaying = false,
                playbackReady = false,
            ),
        )
    }

    @Test
    fun moviesErrorCopySelectsTruthfulWordingWithoutParsingMessages() {
        val offlineCatalog = moviesCatalogErrorCopy(offline = true, serverMessage = "raw server text")
        assertEquals("لا يوجد اتصال بالانترنت", offlineCatalog.title)
        assertEquals("تعذر تحديث المحتوى ، حاول مرة اخرى", offlineCatalog.body)

        val onlineCatalog = moviesCatalogErrorCopy(offline = false, serverMessage = "raw server text")
        assertEquals("تعذر تحديث المحتوى", onlineCatalog.title)
        assertEquals("raw server text", onlineCatalog.body)

        val blankOnline = moviesCatalogErrorCopy(offline = false, serverMessage = null)
        assertEquals("حاول مرة اخرى", blankOnline.body)

        val offlineDetails = moviesDetailsErrorCopy(offline = true, serverMessage = "raw")
        assertEquals("لا يوجد اتصال بالانترنت", offlineDetails.title)
        assertEquals("تعذر تحميل بيانات الفلم ، تحقق من الاتصال وحاول مرة اخرى", offlineDetails.body)

        val onlineDetails = moviesDetailsErrorCopy(offline = false, serverMessage = "تعذر تحميل التفاصيل")
        assertEquals("تعذر تحميل بيانات الفلم", onlineDetails.title)
        assertEquals("تعذر تحميل التفاصيل", onlineDetails.body)

        listOf(offlineCatalog, onlineCatalog, blankOnline, offlineDetails, onlineDetails).forEach { copy ->
            assertFalse(copy.title.contains('أ'))
            assertFalse(copy.title.contains('إ'))
            assertFalse(copy.title.contains('آ'))
            assertFalse(copy.body.contains('أ'))
            assertFalse(copy.body.contains('إ'))
            assertFalse(copy.body.contains('آ'))
        }
    }

    @Test
    fun directFocusUsesTheSameUsableBoundsAsTheReveal() {
        // Bottom clipped by the Movie safe area plus focus margin -> assisted.
        assertEquals(
            TvCatalogFocusPath.SCROLL_ASSISTED,
            tvCatalogFocusPath(4, 12, 260, 420, 100, 400, extraMargin = 20),
        )
        // Inside the full usable bounds -> direct with no scroll.
        assertEquals(
            TvCatalogFocusPath.DIRECT,
            tvCatalogFocusPath(4, 12, 240, 370, 100, 400, extraMargin = 20),
        )
        // Default margin keeps the historical Series decision.
        assertEquals(
            TvCatalogFocusPath.DIRECT,
            tvCatalogFocusPath(4, 12, 120, 380, 100, 400),
        )
    }

    @Test
    fun moviesOfflineNoticeRequiresValidatedConnectivity() {
        assertTrue(
            moviesOfflineFailureVisible(
                isMovies = true,
                hasErrorMessage = true,
                networkUsable = false,
            ),
        )
        assertFalse(
            moviesOfflineFailureVisible(
                isMovies = true,
                hasErrorMessage = true,
                networkUsable = true,
            ),
        )
        assertFalse(
            moviesOfflineFailureVisible(
                isMovies = false,
                hasErrorMessage = true,
                networkUsable = false,
            ),
        )
        assertFalse(
            moviesOfflineFailureVisible(
                isMovies = true,
                hasErrorMessage = false,
                networkUsable = false,
            ),
        )
    }

    @Test
    fun moviesOfflineEmptyStateKeepsCachedAndSearchResultsTruthful() {
        assertTrue(
            moviesOfflineEmptyVisible(
                isMovies = true,
                hasErrorMessage = true,
                networkUsable = false,
                hasCachedContent = false,
                searchActive = false,
            ),
        )
        assertFalse(
            moviesOfflineEmptyVisible(
                isMovies = true,
                hasErrorMessage = true,
                networkUsable = false,
                hasCachedContent = true,
                searchActive = false,
            ),
        )
        assertFalse(
            moviesOfflineEmptyVisible(
                isMovies = true,
                hasErrorMessage = true,
                networkUsable = false,
                hasCachedContent = false,
                searchActive = true,
            ),
        )
        assertFalse(
            moviesOfflineEmptyVisible(
                isMovies = true,
                hasErrorMessage = true,
                networkUsable = true,
                hasCachedContent = false,
                searchActive = false,
            ),
        )
    }
}
