package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.PlaybackRequest

class KidsContentFilteringPolicyTest {
    private val movie = item(10, "فيلم أطفال", "kids-movies", ContentType.MOVIE)
    private val series = item(20, "مسلسل أطفال", "kids-series", ContentType.SERIES)
    private val live = item(30, "قناة أطفال", "kids-live", ContentType.LIVE)

    private val snapshot = VerifiedKidsCatalogSnapshot(
        catalogs = mapOf(
            ContentType.MOVIE to Catalog(
                categories = listOf(Category("kids-movies", "Kids Movies", ContentType.MOVIE)),
                items = listOf(movie),
            ),
            ContentType.SERIES to Catalog(
                categories = listOf(Category("kids-series", "Kids Series", ContentType.SERIES)),
                items = listOf(series),
            ),
            ContentType.LIVE to Catalog(
                categories = listOf(Category("kids-live", "Kids Live", ContentType.LIVE)),
                items = listOf(live),
            ),
        ),
        blockedTypes = emptyMap(),
    )

    private val allowed = verifiedKidsContentKeys(snapshot)

    @Test
    fun verifiedSnapshotBuildsExactAllowList() {
        assertEquals(
            setOf("MOVIE:10", "SERIES:20", "LIVE:30"),
            allowed,
        )
        assertTrue(isAllowedKidsItem(allowed, movie))
        assertFalse(isAllowedKidsItem(allowed, movie.copy(id = 999)))
        assertFalse(isAllowedKidsItem(allowed, movie.copy(type = ContentType.SERIES)))
    }

    @Test
    fun movieAndLiveHistoryRequireExactVerifiedKeys() {
        assertTrue(
            isAllowedKidsHistoryEntry(
                allowed,
                history(key = "MOVIE:10", kind = "movie", id = 10),
            ),
        )
        assertTrue(
            isAllowedKidsHistoryEntry(
                allowed,
                history(key = "LIVE:30", kind = "live", id = 30, live = true),
            ),
        )
        assertFalse(
            isAllowedKidsHistoryEntry(
                allowed,
                history(key = "MOVIE:999", kind = "movie", id = 999),
            ),
        )
        assertFalse(
            isAllowedKidsHistoryEntry(
                allowed,
                history(key = "MOVIE:10", kind = "movie", id = 999),
            ),
        )
    }

    @Test
    fun seriesHistoryRequiresVerifiedParentSeries() {
        assertTrue(
            isAllowedKidsHistoryEntry(
                allowed,
                history(
                    key = "SERIES:201",
                    kind = "series",
                    id = 201,
                    parentContentId = 20,
                ),
            ),
        )
        assertFalse(
            isAllowedKidsHistoryEntry(
                allowed,
                history(key = "SERIES:201", kind = "series", id = 201),
            ),
        )
        assertFalse(
            isAllowedKidsHistoryEntry(
                allowed,
                history(
                    key = "SERIES:201",
                    kind = "series",
                    id = 201,
                    parentContentId = 999,
                ),
            ),
        )
    }

    @Test
    fun playbackRequestsUseSameFailClosedParentRule() {
        assertTrue(
            isAllowedKidsPlaybackRequest(
                allowed,
                playback(kind = "movie", id = 10, key = "MOVIE:10"),
            ),
        )
        assertFalse(
            isAllowedKidsPlaybackRequest(
                allowed,
                playback(kind = "movie", id = 999, key = "MOVIE:999"),
            ),
        )
        assertTrue(
            isAllowedKidsPlaybackRequest(
                allowed,
                playback(
                    kind = "series",
                    id = 201,
                    key = "SERIES:201",
                    parentContentId = 20,
                ),
            ),
        )
        assertFalse(
            isAllowedKidsPlaybackRequest(
                allowed,
                playback(kind = "series", id = 201, key = "SERIES:201"),
            ),
        )
    }

    private fun item(
        id: Int,
        name: String,
        categoryId: String,
        type: ContentType,
    ) = ContentItem(
        id = id,
        name = name,
        categoryId = categoryId,
        type = type,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = if (type == ContentType.LIVE) "ts" else "mp4",
    )

    private fun history(
        key: String,
        kind: String,
        id: Int,
        live: Boolean = false,
        parentContentId: Int? = null,
    ) = HistoryEntry(
        key = key,
        title = "اختبار",
        posterUrl = null,
        streamKind = kind,
        streamId = id,
        extension = if (live) "ts" else "mp4",
        isLive = live,
        positionMs = 1_000L,
        durationMs = 10_000L,
        updatedAtEpochMs = 1L,
        parentContentId = parentContentId,
    )

    private fun playback(
        kind: String,
        id: Int,
        key: String,
        parentContentId: Int? = null,
    ) = PlaybackRequest(
        title = "اختبار",
        posterUrl = null,
        candidates = listOf("https://example.invalid/test"),
        isLive = kind == "live",
        historyKey = key,
        streamKind = kind,
        streamId = id,
        extension = if (kind == "live") "ts" else "mp4",
        parentContentId = parentContentId,
    )
}
