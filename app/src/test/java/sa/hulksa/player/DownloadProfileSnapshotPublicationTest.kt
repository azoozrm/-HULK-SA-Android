package sa.hulksa.player

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload

class DownloadProfileSnapshotPublicationTest {
    @Test
    fun `profile switch while a snapshot is in flight rejects the stale publication`() = runBlocking {
        val state = MutableStateFlow(HulkUiState(screen = HulkScreen.MAIN))
        val gate = DownloadSnapshotPublicationGate()
        val profileASnapshot = listOf(download(id = 1L, title = "Profile A movie"))
        val profileBSnapshot = listOf(download(id = 2L, title = "Profile B movie"))

        val loaderResolved = CountDownLatch(1)
        val releaseLoader = CountDownLatch(1)
        val published = AtomicReference<List<OfflineDownload>?>(null)
        val mainExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "profile-snapshot-test-main")
        }
        try {
            val publisher = CoroutineScope(SupervisorJob() + mainExecutor.asCoroutineDispatcher()).launch {
                published.set(
                    publishDownloadUiSnapshot(state, gate) {
                        loaderResolved.countDown()
                        check(releaseLoader.await(5, TimeUnit.SECONDS))
                        profileASnapshot
                    },
                )
            }

            assertTrue(loaderResolved.await(5, TimeUnit.SECONDS))

            gate.invalidate()
            state.update { it.copy(downloads = profileBSnapshot) }

            releaseLoader.countDown()
            publisher.join()

            assertEquals(profileBSnapshot, state.value.downloads)
            assertEquals(profileBSnapshot, published.get())
        } finally {
            mainExecutor.shutdownNow()
        }
    }

    @Test
    fun `new owner snapshot after the switch publishes normally`() = runBlocking {
        val state = MutableStateFlow(HulkUiState(screen = HulkScreen.MAIN))
        val gate = DownloadSnapshotPublicationGate()
        gate.invalidate()
        val profileBSnapshot = listOf(download(id = 2L, title = "Profile B movie"))

        val published = publishDownloadUiSnapshot(state, gate) { profileBSnapshot }

        assertEquals(profileBSnapshot, published)
        assertEquals(profileBSnapshot, state.value.downloads)
    }

    @Test
    fun `ownership transition invalidates in-flight attempts and later attempts stay current`() {
        val gate = DownloadSnapshotPublicationGate()
        val beforeTransition = gate.begin()
        assertTrue(gate.isCurrent(beforeTransition))

        gate.invalidate()
        assertFalse(gate.isCurrent(beforeTransition))

        val afterTransition = gate.begin()
        assertTrue(gate.isCurrent(afterTransition))
        assertFalse(gate.isCurrent(beforeTransition))

        gate.invalidate()
        assertFalse(gate.isCurrent(afterTransition))
    }

    private fun download(id: Long, title: String): OfflineDownload = OfflineDownload(
        downloadId = id,
        historyKey = "movie:$id",
        title = title,
        posterUrl = null,
        streamKind = "movie",
        streamId = id.toInt(),
        extension = "mp4",
        sourceCandidates = emptyList(),
    )
}
