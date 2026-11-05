package sa.hulksa.player.ui.screens

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class DetailsRelatedItemsDispatchTest {

    @Test
    fun `related scoring runs on the requested background dispatcher`() = runBlocking {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "details-related-test")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val source = item(1, "Source")
            val recording = RecordingList(
                listOf(
                    item(2, "Candidate", genre = "Drama"),
                    item(3, "Other", genre = "Comedy"),
                ),
            )
            val result = detailsProRelatedItemsOffMain(
                source = source,
                candidates = recording,
                dispatcher = dispatcher,
            )
            assertEquals(true, recording.threadName?.startsWith("details-related-test") == true)
            assertEquals(2, result.size)
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `related ranking remains deterministic and bounded`() {
        val source = item(1, "Source", genre = "Action, Drama")
        val candidates = listOf(
            item(2, "Drama hit", genre = "Drama", rating = "8.0"),
            item(3, "Action hit", genre = "Action", rating = "9.0"),
            item(4, "Unrelated", genre = "Comedy", rating = "10.0"),
        )
        val first = detailsProRelatedItems(source, candidates, limit = 2)
        val second = detailsProRelatedItems(source, candidates, limit = 2)
        assertEquals(2, first.size)
        assertEquals(first.map(ContentItem::id), second.map(ContentItem::id))
        assertNotNull(first.firstOrNull())
    }

    private fun item(
        id: Int,
        name: String,
        genre: String = "Drama",
        rating: String = "7.0",
    ) = ContentItem(
        id = id,
        name = name,
        categoryId = "1",
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = rating,
        year = "2024",
        containerExtension = "mp4",
        genre = genre,
    )

    private class RecordingList(
        private val delegate: List<ContentItem>,
    ) : AbstractList<ContentItem>() {
        var threadName: String? = null
        override val size: Int get() = delegate.size
        override fun get(index: Int): ContentItem {
            if (threadName == null) threadName = Thread.currentThread().name
            return delegate[index]
        }
    }
}
