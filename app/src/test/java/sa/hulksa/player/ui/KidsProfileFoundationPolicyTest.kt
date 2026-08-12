package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.VerifiedKidsCatalogSnapshot
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class KidsProfileFoundationPolicyTest {
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

    @Test
    fun kidsNavigationOnlyIncludesServerAvailableSections() {
        assertEquals(
            listOf(KidsSection.HOME, KidsSection.LIVE, KidsSection.MOVIES, KidsSection.SERIES, KidsSection.SEARCH),
            availableKidsSections(snapshot),
        )

        val moviesOnly = snapshot.copy(
            catalogs = mapOf(ContentType.MOVIE to snapshot.catalog(ContentType.MOVIE)),
        )
        assertEquals(
            listOf(KidsSection.HOME, KidsSection.MOVIES, KidsSection.SEARCH),
            availableKidsSections(moviesOnly),
        )
    }

    @Test
    fun sectionAndSearchNeverInventItemsOutsideVerifiedSnapshot() {
        assertEquals(listOf(movie), kidsItemsForSection(snapshot, KidsSection.MOVIES))
        assertEquals(listOf(series), kidsItemsForSection(snapshot, KidsSection.SEARCH, query = "مسلسل"))
        assertTrue(kidsItemsForSection(snapshot, KidsSection.SEARCH, query = "محتوى بالغين").isEmpty())
    }

    @Test
    fun itemGuardRequiresExactVerifiedTypeIdAndCategory() {
        assertTrue(isVerifiedKidsItem(snapshot, movie))
        assertFalse(isVerifiedKidsItem(snapshot, movie.copy(categoryId = "adult")))
        assertFalse(isVerifiedKidsItem(snapshot, movie.copy(id = 999)))
        assertFalse(isVerifiedKidsItem(snapshot, movie.copy(type = ContentType.SERIES)))
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
}
