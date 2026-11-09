package sa.hulksa.player.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

class HomeBootstrapSeedTest {
    private fun live(id: Int) = ContentItem(
        id = id,
        name = "live-$id",
        categoryId = "live",
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "m3u8",
    )

    private fun movie(id: Int, added: Long = id.toLong()) = ContentItem(
        id = id,
        name = "movie-$id",
        categoryId = "movie",
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "mp4",
        addedAtEpochSeconds = added,
    )

    private fun liveHistory(id: Int) = HistoryEntry(
        key = "LIVE:$id",
        title = "live-$id",
        posterUrl = null,
        streamKind = "live",
        streamId = id,
        extension = "m3u8",
        isLive = true,
        positionMs = 0L,
        durationMs = 0L,
        updatedAtEpochMs = 1_000L,
    )

    private fun movieHistory(id: Int, positionMs: Long = 10L) = HistoryEntry(
        key = "MOVIE:$id",
        title = "movie-$id",
        posterUrl = null,
        streamKind = "movie",
        streamId = id,
        extension = "mp4",
        isLive = false,
        positionMs = positionMs,
        durationMs = 100L,
        updatedAtEpochMs = 1_000L,
    )

    @Test
    fun `live seed keeps a favorite beyond the first 320 provider items`() {
        val full = Catalog(
            categories = emptyList(),
            items = (1..400).map(::live),
        )

        val seed = compactHomeLiveCatalogForHome(
            catalog = full,
            favorites = setOf("LIVE:399"),
            history = listOf(liveHistory(398)),
        )

        assertEquals(HOME_CATALOG_LIMIT, seed.items.size)
        assertTrue(seed.items.any { it.id == 399 })
        assertTrue(seed.items.any { it.id == 398 })
        assertEquals(setOf(398, 399), seed.items.take(2).map(ContentItem::id).toSet())
    }

    @Test
    fun `personalization key tracks selection identities but not playback position`() {
        val favorites = setOf("MOVIE:1")
        val history = listOf(movieHistory(2, positionMs = 10L))
        val sameSelection = listOf(movieHistory(2, positionMs = 90L))
        val changedHistory = listOf(movieHistory(3, positionMs = 10L))

        assertEquals(
            homeBootstrapPersonalizationKey(favorites, history),
            homeBootstrapPersonalizationKey(favorites, sameSelection),
        )
        assertNotEquals(
            homeBootstrapPersonalizationKey(favorites, history),
            homeBootstrapPersonalizationKey(favorites, changedHistory),
        )
        assertNotEquals(
            homeBootstrapPersonalizationKey(favorites, history),
            homeBootstrapPersonalizationKey(emptySet(), history),
        )
    }

    @Test
    fun `empty types are omitted while healthy types remain`() {
        val selection = selectHomeBootstrapSeed(
            seeds = mapOf(
                ContentType.MOVIE to Catalog(emptyList(), emptyList()),
                ContentType.SERIES to Catalog(emptyList(), listOf(movie(1))),
            ),
            sourceGenerations = mapOf(
                ContentType.MOVIE to 1L,
                ContentType.SERIES to 2L,
            ),
        )

        assertEquals(setOf(ContentType.SERIES), selection.catalogs.keys)
        assertEquals(setOf(ContentType.MOVIE), selection.confirmedEmptyTypes)
        assertTrue(selection.unboundTypes.isEmpty())
        assertEquals(setOf(ContentType.LIVE), selection.missingTypes)
    }

    @Test
    fun `empty series and live do not invalidate populated movie`() {
        val selection = selectHomeBootstrapSeed(
            seeds = mapOf(
                ContentType.MOVIE to Catalog(emptyList(), listOf(movie(1))),
                ContentType.SERIES to Catalog(emptyList(), emptyList()),
                ContentType.LIVE to Catalog(emptyList(), emptyList()),
            ),
            sourceGenerations = mapOf(
                ContentType.MOVIE to 1L,
                ContentType.SERIES to 2L,
                ContentType.LIVE to 3L,
            ),
        )

        assertEquals(setOf(ContentType.MOVIE), selection.catalogs.keys)
        assertEquals(
            setOf(ContentType.SERIES, ContentType.LIVE),
            selection.confirmedEmptyTypes,
        )
        assertTrue(selection.missingTypes.isEmpty())
        assertTrue(selection.unboundTypes.isEmpty())
    }

    @Test
    fun `production seed assembly uses the filtered selection and guarded deletion`() {
        val source = File("src/main/java/sa/hulksa/player/HulkViewModel.kt").readText()
        val schedule = source
            .substringAfter("private fun scheduleHomeBootstrapPersist()")
            .substringBefore("private fun resolvePendingTvDeepLink()")

        assertTrue(schedule.contains("selectHomeBootstrapSeed(seeds, sourceGenerationsSnapshot)"))
        assertTrue(schedule.contains("selection.catalogs.isNotEmpty()"))
        assertTrue(schedule.contains("selection.sourceGenerations"))
        assertTrue(schedule.contains("repository.deleteHomeBootstrap("))
        assertTrue(schedule.contains("selection.missingTypes.isEmpty()"))
        assertTrue(schedule.contains("selection.unboundTypes.isEmpty()"))
    }

    @Test
    fun `authoritative all-empty selection has no healthy catalogs`() {
        val selection = selectHomeBootstrapSeed(
            seeds = mapOf(
                ContentType.MOVIE to Catalog(emptyList(), emptyList()),
                ContentType.SERIES to Catalog(emptyList(), emptyList()),
                ContentType.LIVE to Catalog(emptyList(), emptyList()),
            ),
            sourceGenerations = mapOf(
                ContentType.MOVIE to 1L,
                ContentType.SERIES to 2L,
                ContentType.LIVE to 3L,
            ),
        )

        assertTrue(selection.catalogs.isEmpty())
        assertEquals(
            setOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE),
            selection.confirmedEmptyTypes,
        )
        assertTrue(selection.unboundTypes.isEmpty())
        assertTrue(selection.missingTypes.isEmpty())
    }

    @Test
    fun `missing type is distinct from confirmed empty`() {
        val selection = selectHomeBootstrapSeed(
            seeds = mapOf(
                ContentType.MOVIE to Catalog(emptyList(), emptyList()),
            ),
            sourceGenerations = mapOf(ContentType.MOVIE to 1L),
        )

        assertEquals(setOf(ContentType.MOVIE), selection.confirmedEmptyTypes)
        assertEquals(
            setOf(ContentType.SERIES, ContentType.LIVE),
            selection.missingTypes,
        )
        assertTrue(selection.unboundTypes.isEmpty())
    }

    @Test
    fun `unbound non-empty type blocks destructive all-empty deletion`() {
        val selection = selectHomeBootstrapSeed(
            seeds = mapOf(
                ContentType.MOVIE to Catalog(emptyList(), emptyList()),
                ContentType.LIVE to Catalog(emptyList(), listOf(live(1))),
            ),
            sourceGenerations = mapOf(ContentType.MOVIE to 1L),
        )

        assertTrue(selection.catalogs.isEmpty())
        assertEquals(setOf(ContentType.MOVIE), selection.confirmedEmptyTypes)
        assertEquals(setOf(ContentType.LIVE), selection.unboundTypes)
        assertEquals(setOf(ContentType.SERIES), selection.missingTypes)
    }

    @Test
    fun `reconcile re-applies current pins inside a bounded movie seed`() {
        val seed = Catalog(
            categories = emptyList(),
            items = (1..5).map { movie(it, added = it.toLong()) },
        )

        val reconciled = reconcileHomeBootstrapCatalogs(
            catalogs = mapOf(ContentType.MOVIE to seed),
            favorites = setOf("MOVIE:5"),
            history = emptyList(),
        ).getValue(ContentType.MOVIE)

        assertEquals(5, reconciled.items.first().id)
        assertEquals(seed.items.map(ContentItem::id).toSet(), reconciled.items.map(ContentItem::id).toSet())
    }

    @Test
    fun `reconcile re-applies a current live favorite inside a bounded seed`() {
        val seed = Catalog(
            categories = emptyList(),
            items = (1..5).map(::live),
        )

        val reconciled = reconcileHomeBootstrapCatalogs(
            catalogs = mapOf(ContentType.LIVE to seed),
            favorites = setOf("LIVE:4"),
            history = emptyList(),
        ).getValue(ContentType.LIVE)

        assertEquals(4, reconciled.items.first().id)
        assertEquals(seed.items.map(ContentItem::id).toSet(), reconciled.items.map(ContentItem::id).toSet())
    }
}
