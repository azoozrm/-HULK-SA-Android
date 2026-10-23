package sa.hulksa.player.data

import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import kotlin.coroutines.CoroutineContext

/**
 * R03: the dispatcher must suppress an outcome that was superseded after its persistence block
 * completed but before its continuation was delivered back to the caller.
 *
 * A paused caller dispatcher is the deterministic barrier: A finishes persistence on IO, B (or
 * invalidate) advances ownership, and only then is A's queued caller continuation allowed to run.
 */
class DownloadPrimaryActionSupersessionTest {
    @Test
    fun `a newer action supersedes A after IO and before caller delivery`() = runBlocking {
        val store = BarrierActionStore()
        val dispatcher = DownloadPrimaryActionDispatcher(store)
        val caller = PausableDispatcher()
        val scope = CoroutineScope(caller + SupervisorJob())
        val aResult = CompletableDeferred<DownloadActionOutcome>()

        scope.launch {
            aResult.complete(
                dispatcher.dispatch(
                    downloadId = DOWNLOAD_ID,
                    expectedAccountId = ACCOUNT_ID,
                    expectedProfileId = PROFILE_ID,
                    intent = DownloadActionIntent.RETRY,
                ),
            )
        }
        try {
            caller.runNext()
            store.persistenceDone.await()

            // B is admitted while A's caller continuation is still held.
            val b = dispatcher.dispatch(
                downloadId = DOWNLOAD_ID,
                expectedAccountId = ACCOUNT_ID,
                expectedProfileId = PROFILE_ID,
                intent = DownloadActionIntent.RETRY,
            )
            assertEquals(DownloadActionClaim.ALREADY_QUEUED, b.claim)
            assertEquals(1, store.resumeCalls)

            caller.runNext()
            val a = aResult.await()
            assertEquals(DownloadActionClaim.SUPERSEDED, a.claim)
            assertTrue(a.downloads.isEmpty())
            assertEquals(OfflineStatus.QUEUED, store.record(DOWNLOAD_ID, ACCOUNT_ID, PROFILE_ID)?.status)
            assertEquals(1, store.resumeCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `dispatcher invalidation supersedes A after IO and before caller delivery`() = runBlocking {
        val store = BarrierActionStore()
        val dispatcher = DownloadPrimaryActionDispatcher(store)
        val caller = PausableDispatcher()
        val scope = CoroutineScope(caller + SupervisorJob())
        val aResult = CompletableDeferred<DownloadActionOutcome>()

        scope.launch {
            aResult.complete(
                dispatcher.dispatch(
                    downloadId = DOWNLOAD_ID,
                    expectedAccountId = ACCOUNT_ID,
                    expectedProfileId = PROFILE_ID,
                    intent = DownloadActionIntent.RETRY,
                ),
            )
        }
        try {
            caller.runNext()
            store.persistenceDone.await()

            dispatcher.invalidate()

            caller.runNext()
            val a = aResult.await()
            assertEquals(DownloadActionClaim.SUPERSEDED, a.claim)
            assertTrue(a.downloads.isEmpty())
            assertEquals(1, store.resumeCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `an ordinary current action still returns its actual outcome`() = runBlocking {
        val store = BarrierActionStore()
        val dispatcher = DownloadPrimaryActionDispatcher(store)
        val caller = PausableDispatcher()
        val scope = CoroutineScope(caller + SupervisorJob())
        val aResult = CompletableDeferred<DownloadActionOutcome>()

        scope.launch {
            aResult.complete(
                dispatcher.dispatch(
                    downloadId = DOWNLOAD_ID,
                    expectedAccountId = ACCOUNT_ID,
                    expectedProfileId = PROFILE_ID,
                    intent = DownloadActionIntent.RETRY,
                ),
            )
        }
        try {
            caller.runNext()
            store.persistenceDone.await()
            caller.runNext()

            val a = aResult.await()
            assertEquals(DownloadActionClaim.APPLIED, a.claim)
            assertEquals(OfflineStatus.QUEUED, a.status)
            assertEquals(1, a.downloads.size)
            assertEquals(1, store.resumeCalls)
        } finally {
            scope.cancel()
        }
    }

    private class BarrierActionStore : DownloadPrimaryActionStore {
        val persistenceDone = CompletableDeferred<Unit>()
        var resumeCalls: Int = 0

        private var status: OfflineStatus = OfflineStatus.FAILED

        override fun record(
            downloadId: Long,
            expectedAccountId: String,
            expectedProfileId: String,
        ): OfflineDownload? = if (
            downloadId == DOWNLOAD_ID &&
            expectedAccountId == ACCOUNT_ID &&
            expectedProfileId == PROFILE_ID
        ) {
            download(status)
        } else {
            null
        }

        override fun snapshot(): List<OfflineDownload> = listOf(download(status))

        override fun pause(
            downloadId: Long,
            expectedAccountId: String,
            expectedProfileId: String,
        ): ProfileDownloadPauseOutcome = ProfileDownloadPauseOutcome(
            downloads = snapshot(),
            applied = false,
            persisted = true,
        )

        override fun resume(
            downloadId: Long,
            expectedAccountId: String,
            expectedProfileId: String,
        ): ProfileDownloadResumeOutcome {
            resumeCalls += 1
            status = OfflineStatus.QUEUED
            persistenceDone.complete(Unit)
            return ProfileDownloadResumeOutcome(
                downloads = snapshot(),
                resumed = true,
                status = status,
            )
        }

        private fun download(status: OfflineStatus): OfflineDownload = OfflineDownload(
            downloadId = DOWNLOAD_ID,
            historyKey = "movie:1",
            title = "Movie 1",
            posterUrl = null,
            streamKind = "movie",
            streamId = 1,
            extension = "mp4",
            status = status,
        )
    }

    private class PausableDispatcher : CoroutineDispatcher() {
        private val queue = LinkedBlockingQueue<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.put(block)
        }

        fun runNext() {
            queue.take().run()
        }
    }

    private companion object {
        const val DOWNLOAD_ID = 1L
        const val ACCOUNT_ID = "account-a"
        const val PROFILE_ID = "profile-1"
    }
}
