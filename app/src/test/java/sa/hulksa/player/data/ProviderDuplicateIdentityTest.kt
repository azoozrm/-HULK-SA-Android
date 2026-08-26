package sa.hulksa.player.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Episode

class ProviderDuplicateIdentityTest {
    @Test
    fun movieCatalogDuplicateSameIdKeepsFirstAndPreservesOrder() {
        val items = normalizedProviderItems(
            type = ContentType.MOVIE,
            raw = """
                [
                  {"stream_id":2,"name":"Movie 2 first"},
                  {"stream_id":1,"name":"Movie 1"},
                  {"stream_id":2,"name":"Movie 2 richer duplicate","stream_icon":"https://example.com/richer.jpg"},
                  {"stream_id":3,"name":"Movie 3"}
                ]
            """.trimIndent(),
        )

        assertEquals(listOf(2, 1, 3), items.map(ContentItem::id))
        assertEquals("Movie 2 first", items.first().name)
    }

    @Test
    fun seriesCatalogDuplicateSameIdKeepsFirst() {
        val items = normalizedProviderItems(
            type = ContentType.SERIES,
            raw = """
                [
                  {"series_id":42,"name":"Series first"},
                  {"series_id":42,"name":"Series duplicate"},
                  {"series_id":43,"name":"Series next"}
                ]
            """.trimIndent(),
        )

        assertEquals(listOf(42, 43), items.map(ContentItem::id))
        assertEquals("Series first", items.first().name)
    }

    @Test
    fun liveCatalogDuplicateSameIdKeepsFirst() {
        val items = normalizedProviderItems(
            type = ContentType.LIVE,
            raw = """
                [
                  {"stream_id":7,"name":"Live first"},
                  {"stream_id":7,"name":"Live duplicate"},
                  {"stream_id":8,"name":"Live next"}
                ]
            """.trimIndent(),
        )

        assertEquals(listOf(7, 8), items.map(ContentItem::id))
        assertEquals("Live first", items.first().name)
    }

    @Test
    fun categoryDuplicateSameIdKeepsFirstStableCategory() {
        val array = JSONArray(
            """
                [
                  {"category_id":"sports","category_name":"Sports first"},
                  {"category_id":"sports","category_name":"Sports duplicate"},
                  {"category_id":"news","category_name":"News"}
                ]
            """.trimIndent(),
        )
        val categories = buildList {
            array.forEachUniqueObject(
                maxUniqueItems = XtreamJsonLimits.CATEGORIES.maxUniqueItems!!,
                scope = "provider category test",
                keyOf = { item -> item.optString("category_id").trim().takeIf(String::isNotBlank) },
            ) { item ->
                add(
                    Category(
                        id = item.getString("category_id"),
                        name = item.getString("category_name"),
                        type = ContentType.LIVE,
                    ),
                )
            }
        }

        assertEquals(listOf("sports", "news"), categories.map(Category::id))
        assertEquals("Sports first", categories.first().name)
        assertEquals("LIVE:sports", categories.first().providerStableIdentity())
    }

    @Test
    fun episodeDuplicateSameIdKeepsFirstWithoutChangingSurvivorOrder() {
        val root = JSONObject(
            """
                {
                  "episodes": {
                    "1": [
                      {"id":"101","title":"Episode first"},
                      {"id":"101","title":"Episode duplicate"},
                      {"id":"102","title":"Episode next"}
                    ]
                  }
                }
            """.trimIndent(),
        )

        val episodes = root.boundedSeriesEpisodeObjects()

        assertEquals(listOf("101", "102"), episodes.map { it.episode.getString("id") })
        assertEquals("Episode first", episodes.first().episode.getString("title"))
    }

    @Test
    fun sameNumericIdAcrossMovieAndSeriesRemainsDistinctForUnifiedSearch() {
        val movie = contentItem(id = 123, type = ContentType.MOVIE, name = "Movie 123")
        val series = contentItem(id = 123, type = ContentType.SERIES, name = "Series 123")

        val unified = listOf(movie, series).distinctBy { it.providerStableIdentity() }

        assertEquals(2, unified.size)
        assertNotEquals(movie.providerStableIdentity(), series.providerStableIdentity())
    }

    @Test
    fun episodeIdentityIncludesParentSeries() {
        val episode = Episode(
            id = 9,
            title = "Episode",
            season = 1,
            episodeNumber = 1,
            containerExtension = "mkv",
            posterUrl = null,
            duration = null,
        )

        assertNotEquals(
            episode.providerStableIdentity(seriesId = 100),
            episode.providerStableIdentity(seriesId = 200),
        )
    }

    @Test
    fun stableIdentityDoesNotChangeWhenMetadataChangesBetweenRefreshes() {
        val before = contentItem(id = 55, type = ContentType.MOVIE, name = "Old title")
        val after = before.copy(name = "Updated title", posterUrl = "https://example.com/new.jpg")

        assertEquals(before.providerStableIdentity(), after.providerStableIdentity())
    }

    @Test
    fun p1JsonItemBudgetsRemainUnchanged() {
        assertEquals(2_000, XtreamJsonLimits.CATEGORIES.maxUniqueItems!!)
        assertEquals(75_000, XtreamJsonLimits.CATALOG.maxUniqueItems!!)
        assertEquals(10_000, XtreamJsonLimits.MAX_SERIES_EPISODES)
        assertEquals(32L * 1024L * 1024L, XtreamJsonLimits.CATALOG.maxResponseBytes)
    }

    private fun normalizedProviderItems(
        type: ContentType,
        raw: String,
    ): List<ContentItem> {
        val array = JSONArray(raw)
        val idKey = if (type == ContentType.SERIES) "series_id" else "stream_id"
        return buildList {
            array.forEachUniqueObject(
                maxUniqueItems = XtreamJsonLimits.CATALOG.maxUniqueItems!!,
                scope = "provider ${type.name.lowercase()} test",
                keyOf = { item -> item.optString(idKey).toIntOrNull()?.toString() },
            ) { item ->
                val id = item.optString(idKey).toIntOrNull() ?: return@forEachUniqueObject
                add(
                    contentItem(
                        id = id,
                        type = type,
                        name = item.optString("name"),
                        categoryId = item.optString("category_id"),
                        posterUrl = item.optString("stream_icon").takeIf(String::isNotBlank),
                    ),
                )
            }
        }
    }

    private fun contentItem(
        id: Int,
        type: ContentType,
        name: String,
        categoryId: String = "category",
        posterUrl: String? = null,
    ): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = categoryId,
        type = type,
        posterUrl = posterUrl,
        rating = null,
        year = null,
        containerExtension = null,
    )
}
