package sa.hulksa.player.ui.screens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.HomeHeroTechnicalMetadata
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class HomeHeroMetadataLayoutPolicyTest {
    @Test
    fun `genre labels keep provider order and compound ampersand wording`() {
        val labels = homeHeroGenreLabels(
            "Action & Adventure, Drama، Action & Adventure / Sci-Fi | Crime; Drama",
        )

        assertEquals(
            listOf("Action & Adventure", "Drama", "Sci-Fi", "Crime"),
            labels,
        )
    }

    @Test
    fun `blank and missing genre metadata produce no labels`() {
        assertTrue(homeHeroGenreLabels(null).isEmpty())
        assertTrue(homeHeroGenreLabels("  , ، / | ; ").isEmpty())
    }

    @Test
    fun `two complete labels and one omitted count fit together`() {
        val selection = homeHeroGenreLineSelection(
            labelWidthsPx = listOf(40, 50, 30),
            availableWidthPx = 130,
            separatorWidthPx = 8,
            countWidthPx = { 20 },
        )

        assertEquals(listOf(0, 1), selection.labelIndices)
        assertEquals(1, selection.omittedCount)
        assertFalse(selection.countOnly)
    }

    @Test
    fun `one complete label and the remainder fit when two labels cannot`() {
        val selection = homeHeroGenreLineSelection(
            labelWidthsPx = listOf(70, 70),
            availableWidthPx = 100,
            separatorWidthPx = 10,
            countWidthPx = { 20 },
        )

        assertEquals(listOf(0), selection.labelIndices)
        assertEquals(1, selection.omittedCount)
    }

    @Test
    fun `no complete label fits so only the LTR remainder count is selected`() {
        val selection = homeHeroGenreLineSelection(
            labelWidthsPx = listOf(200, 200),
            availableWidthPx = 50,
            separatorWidthPx = 10,
            countWidthPx = { 20 },
        )

        assertTrue(selection.labelIndices.isEmpty())
        assertEquals(2, selection.omittedCount)
        assertTrue(selection.countOnly)
        assertEquals("+2", homeHeroGenreRemainderLabel(selection.omittedCount))
    }

    @Test
    fun `all labels fit without a remainder indicator`() {
        val selection = homeHeroGenreLineSelection(
            labelWidthsPx = listOf(20, 30),
            availableWidthPx = 100,
            separatorWidthPx = 5,
            countWidthPx = { 20 },
        )

        assertEquals(listOf(0, 1), selection.labelIndices)
        assertEquals(0, selection.omittedCount)
        assertFalse(selection.countOnly)
    }

    @Test
    fun `home metadata styles inherit the application font family`() {
        val metrics = homeHeroCopyMetrics(isTv = false, fontScale = 1f)
        val base = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
        )

        val genre = homeHeroGenreTextStyle(base, metrics)
        val facts = homeHeroFactTextStyle(base, metrics)

        assertEquals(FontFamily.Monospace, genre.fontFamily)
        assertEquals(FontFamily.Monospace, facts.fontFamily)
        assertEquals(FontWeight.Normal, genre.fontWeight)
        assertEquals(FontWeight.Medium, facts.fontWeight)
        assertEquals(12.sp, genre.fontSize)
        assertEquals(18.sp, genre.lineHeight)
        assertEquals(12.sp, facts.fontSize)
        assertEquals(18.sp, facts.lineHeight)
    }

    @Test
    fun `normal wide facts fit in one measured row`() {
        val layout = homeHeroFactRowLayout(
            groupWidthsPx = listOf(50, 50, 50),
            availableWidthPx = 200,
            separatorWidthPx = 1,
        )

        assertTrue(layout.fits)
        assertEquals(1, layout.rows.size)
        assertEquals(listOf(0, 1, 2), layout.placedGroupIndices)
        assertEquals(listOf(152), layout.rows.map { it.widthPx })
        assertTrue(layout.rows.all { it.widthPx <= 200 })
    }

    @Test
    fun `whole fact groups split into two measured rows that both fit`() {
        val layout = homeHeroFactRowLayout(
            groupWidthsPx = listOf(60, 60, 60, 60),
            availableWidthPx = 121,
            separatorWidthPx = 1,
        )

        assertTrue(layout.fits)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), layout.rows.map { it.groupIndices })
        assertEquals(listOf(121, 121), layout.rows.map { it.widthPx })
        assertEquals(listOf(0, 1, 2, 3), layout.placedGroupIndices)
        assertTrue(layout.rows.all { it.widthPx <= 121 })
    }

    @Test
    fun `caller presentation keeps the normal copy width when facts fit`() {
        val presentation = homeHeroFactPresentation(
            groupWidthsPx = listOf(50, 50, 50),
            preferredWidthPx = 200,
            maxWidthPx = 500,
            separatorWidthPx = 1,
        )

        assertTrue(presentation.layout.fits)
        assertEquals(200, presentation.widthPx)
        assertEquals(listOf(0, 1, 2), presentation.layout.placedGroupIndices)
        assertTrue(presentation.layout.rows.all { it.widthPx <= presentation.widthPx })
    }

    @Test
    fun `caller presentation expands only to the required capacity when preferred is too narrow`() {
        val presentation = homeHeroFactPresentation(
            groupWidthsPx = listOf(60, 60, 60, 60),
            preferredWidthPx = 100,
            maxWidthPx = 200,
            separatorWidthPx = 1,
        )

        assertTrue(presentation.layout.fits)
        assertEquals(121, presentation.widthPx)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), presentation.layout.rows.map { it.groupIndices })
        assertEquals(listOf(0, 1, 2, 3), presentation.layout.placedGroupIndices)
        assertTrue(presentation.layout.rows.all { it.widthPx <= presentation.widthPx })
    }

    @Test
    fun `caller presentation reports unfits when even maximum capacity is too narrow`() {
        val presentation = homeHeroFactPresentation(
            groupWidthsPx = listOf(300),
            preferredWidthPx = 100,
            maxWidthPx = 200,
            separatorWidthPx = 1,
        )

        assertFalse(presentation.layout.fits)
        assertTrue(presentation.layout.rows.isEmpty())
        assertEquals(200, presentation.widthPx)
        assertEquals(300, presentation.layout.requiredWidthPx)
        assertEquals(listOf(0), presentation.layout.overWideGroupIndices)
    }

    @Test
    fun `last-row overflow is surfaced instead of placing an out-of-bounds row`() {
        val layout = homeHeroFactRowLayout(
            groupWidthsPx = listOf(60, 60, 60, 60),
            availableWidthPx = 100,
            separatorWidthPx = 1,
        )

        assertFalse(layout.fits)
        assertTrue(layout.rows.isEmpty())
        assertTrue(layout.requiredWidthPx > 100)
        assertEquals(121, layout.requiredWidthPx)
        assertTrue(layout.overWideGroupIndices.isEmpty())
        assertTrue(layout.rows.all { it.widthPx <= 100 })
    }

    @Test
    fun `over-wide individual group is surfaced without placing or clipping it`() {
        val layout = homeHeroFactRowLayout(
            groupWidthsPx = listOf(300),
            availableWidthPx = 100,
            separatorWidthPx = 1,
        )

        assertFalse(layout.fits)
        assertTrue(layout.rows.isEmpty())
        assertEquals(300, layout.requiredWidthPx)
        assertEquals(listOf(0), layout.overWideGroupIndices)
    }

    @Test
    fun `movie technical facts keep year duration quality rating order and omit genre`() {
        val entries = movieHomeHeroFactEntries(
            item = movie(rating = "8.0", year = "2026", genre = "Action & Adventure"),
            isTv = true,
            metadata = HomeHeroTechnicalMetadata(
                quality = "4K",
                durationMs = 7_200_000L,
            ),
        )

        assertEquals(listOf("2026", "2h", "4K", "8.0"), entries.map { it.text })
        assertTrue(entries[1].clock)
        assertTrue(entries[2].quality)
        assertTrue(entries[3].rating)
    }

    @Test
    fun `series technical facts keep quality seasons episodes rating order and omit genre`() {
        val entries = seriesHomeHeroFactEntries(
            item = series(rating = "8.0", genre = "Drama"),
            isTv = false,
            metadata = HomeHeroTechnicalMetadata(
                quality = "HD",
                seasonCount = 2,
                episodeCount = 10,
            ),
        )

        assertEquals(listOf("HD", "", "", "8.0"), entries.map { it.text })
        assertEquals(2, entries[1].seasonCount)
        assertEquals(10, entries[2].episodeCount)
        assertTrue(entries[0].quality)
        assertTrue(entries[3].rating)
    }

    @Test
    fun `missing technical metadata omits unavailable fields rather than inventing values`() {
        val movieEntries = movieHomeHeroFactEntries(
            item = movie(rating = null, year = "2026", genre = null),
            isTv = true,
            metadata = HomeHeroTechnicalMetadata(quality = "4K"),
        )
        val seriesEntries = seriesHomeHeroFactEntries(
            item = series(rating = null, genre = null),
            isTv = true,
            metadata = HomeHeroTechnicalMetadata(seasonCount = 0, episodeCount = 0),
        )

        assertEquals(listOf("2026", "4K"), movieEntries.map { it.text })
        assertFalse(movieEntries.any { it.rating || it.clock })
        assertTrue(seriesEntries.isEmpty())
    }

    private fun movie(
        rating: String?,
        year: String?,
        genre: String?,
    ): ContentItem = ContentItem(
        id = 1,
        name = "فيلم عربي طويل جدا مع عنوان لاتيني Very Long Latin Movie Title",
        categoryId = "movies",
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = rating,
        year = year,
        containerExtension = "mp4",
        genre = genre,
    )

    private fun series(
        rating: String?,
        genre: String?,
    ): ContentItem = ContentItem(
        id = 2,
        name = "مسلسل عربي طويل جدا مع عنوان لاتيني Very Long Latin Series Title",
        categoryId = "series",
        type = ContentType.SERIES,
        posterUrl = null,
        rating = rating,
        year = null,
        containerExtension = null,
        genre = genre,
    )
}
