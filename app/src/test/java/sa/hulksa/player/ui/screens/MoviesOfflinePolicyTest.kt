package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoviesOfflinePolicyTest {

    @Test
    fun movieOfflineCardIsTheSingleMovieOnlySurface() {
        assertTrue(
            movieOfflineCardVisible(
                isMovie = true,
                localPlayback = false,
                offlineFailure = true,
                offlineMessageActive = true,
            ),
        )
        assertFalse(
            movieOfflineCardVisible(
                isMovie = false,
                localPlayback = false,
                offlineFailure = true,
                offlineMessageActive = true,
            ),
        )
        assertFalse(
            movieOfflineCardVisible(
                isMovie = true,
                localPlayback = true,
                offlineFailure = true,
                offlineMessageActive = true,
            ),
        )
        assertFalse(
            movieOfflineCardVisible(
                isMovie = true,
                localPlayback = false,
                offlineFailure = false,
                offlineMessageActive = true,
            ),
        )
        assertFalse(
            movieOfflineCardVisible(
                isMovie = true,
                localPlayback = false,
                offlineFailure = true,
                offlineMessageActive = false,
            ),
        )
    }

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
