package sa.hulksa.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class MediaCatalogPresentationPolicyTest {
    private fun item(
        type: ContentType,
        rating: String? = null,
        year: String? = null,
        name: String = "عنصر",
    ) = ContentItem(
        id = 1,
        name = name,
        categoryId = "0",
        type = type,
        posterUrl = null,
        rating = rating,
        year = year,
        containerExtension = null,
    )

    @Test
    fun `movie metadata stays movie specific with rating and duration only`() {
        val movie = item(ContentType.MOVIE, rating = "7.9")

        assertEquals(
            "★ 7.9 · 1س 45د",
            mediaMetadataLine(movie, MediaCardMetadata(durationMs = 105 * 60_000L, seasonCount = 4)),
        )
        assertEquals(
            "★ 7.9",
            mediaMetadataLine(movie, MediaCardMetadata()),
        )
        assertEquals(
            "45د",
            mediaMetadataLine(item(ContentType.MOVIE), MediaCardMetadata(durationMs = 45 * 60_000L)),
        )
    }

    @Test
    fun `series metadata stays series specific and never substitutes movie duration`() {
        val series = item(ContentType.SERIES, rating = "8.2")

        assertEquals(
            "★ 8.2 · 3 مواسم",
            mediaMetadataLine(series, MediaCardMetadata(durationMs = 105 * 60_000L, seasonCount = 3)),
        )
        assertNull(mediaMetadataLine(item(ContentType.SERIES), MediaCardMetadata(durationMs = 105 * 60_000L)))
    }

    @Test
    fun `season count uses correct arabic forms`() {
        assertEquals("موسم واحد", formatMediaSeasonCount(1))
        assertEquals("موسمان", formatMediaSeasonCount(2))
        assertEquals("3 مواسم", formatMediaSeasonCount(3))
        assertEquals("10 مواسم", formatMediaSeasonCount(10))
        assertEquals("11 موسم", formatMediaSeasonCount(11))
        assertNull(formatMediaSeasonCount(0))
        assertNull(formatMediaSeasonCount(null))
    }

    @Test
    fun `live metadata uses only real rating and year fields`() {
        val live = item(ContentType.LIVE, rating = "6.5", year = "2024")

        assertEquals("★ 6.5 · 2024", mediaMetadataLine(live, MediaCardMetadata(seasonCount = 2)))
        assertNull(mediaMetadataLine(item(ContentType.LIVE), MediaCardMetadata()))
    }

    @Test
    fun `invalid or absent rating and duration never fabricate a value`() {
        assertNull(formatMediaRating(null))
        assertNull(formatMediaRating(""))
        assertNull(formatMediaRating("0"))
        assertNull(formatMediaRating("غير متوفر"))
        assertNull(formatMediaDuration(null))
        assertNull(formatMediaDuration(0L))
        assertEquals("1س", formatMediaDuration(60 * 60_000L))
        assertEquals("1س 05د", formatMediaDuration(65 * 60_000L))
    }

    @Test
    fun `artwork kind keeps live logos out of the cropped poster stage`() {
        assertEquals(MediaArtworkKind.POSTER, mediaArtworkKind(ContentType.MOVIE))
        assertEquals(MediaArtworkKind.POSTER, mediaArtworkKind(ContentType.SERIES))
        assertEquals(MediaArtworkKind.LIVE_LOGO, mediaArtworkKind(ContentType.LIVE))
    }

    @Test
    fun `focus never scales or changes card dimensions`() {
        val focused = mediaCardFocusStyle(showFocused = true)
        val idle = mediaCardFocusStyle(showFocused = false)

        assertEquals(1f, focused.scale)
        assertEquals(1f, idle.scale)
        assertEquals(MEDIA_CARD_FOCUS_BORDER_WIDTH_DP, focused.borderWidthDp)
        assertEquals(0f, idle.borderWidthDp)
        assertNotEquals(focused.borderWidthDp, idle.borderWidthDp)
    }

    @Test
    fun `quality label stays restrained and blank values are not shown`() {
        assertEquals("FHD", mediaQualityLabel(" FHD "))
        assertNull(mediaQualityLabel(null))
        assertNull(mediaQualityLabel("   "))
    }
}
