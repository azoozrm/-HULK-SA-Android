package sa.hulksa.player.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.ProfileKind

class TvPlatformIntegrationTest {
    private val movie = content(41, "Movie", ContentType.MOVIE)
    private val series = content(82, "Series", ContentType.SERIES)
    private val movies = Catalog(categories = emptyList(), items = listOf(movie))
    private val seriesCatalog = Catalog(categories = emptyList(), items = listOf(series))

    @Test
    fun movieDeepLinkParsing() {
        assertEquals(TvDeepLinkTarget.Movie(41), TvDeepLinkRouter.parse("hulksa://movie/41"))
        assertEquals(
            TvDeepLinkTarget.Movie(41, resumePlayback = true),
            TvDeepLinkRouter.parse("hulksa://movie/41?resume=true"),
        )
        assertEquals("hulksa://movie/41?resume=true", TvDeepLinkRouter.uri(
            TvDeepLinkTarget.Movie(41, resumePlayback = true),
        ))
    }

    @Test
    fun seriesDeepLinkParsing() {
        assertEquals(TvDeepLinkTarget.Series(82), TvDeepLinkRouter.parse("hulksa://series/82"))
        assertEquals("hulksa://series/82", TvDeepLinkRouter.uri(TvDeepLinkTarget.Series(82)))
    }

    @Test
    fun episodeDeepLinkParsing() {
        val target = TvDeepLinkTarget.Episode(82, 503, resumePlayback = true)
        assertEquals(target, TvDeepLinkRouter.parse("hulksa://episode/82/503?resume=true"))
        assertEquals("hulksa://episode/82/503?resume=true", TvDeepLinkRouter.uri(target))
    }

    @Test
    fun invalidDeepLinkFailsSafely() {
        listOf(
            null,
            "",
            "https://movie/41",
            "hulksa://movie/0",
            "hulksa://movie/-1",
            "hulksa://movie/41/extra",
            "hulksa://series/82?resume=true",
            "hulksa://episode/82",
            "hulksa://user:secret@movie/41",
            "hulksa://movie/41?username=secret",
            "hulksa://movie/41#fragment",
        ).forEach { assertNull(TvDeepLinkRouter.parse(it)) }
    }

    @Test
    fun deepLinkCannotBypassKidsRestrictions() {
        val blocked = resolveTvDeepLink(
            target = TvDeepLinkTarget.Movie(movie.id),
            movieCatalog = movies,
            seriesCatalog = seriesCatalog,
            history = emptyList(),
            profileKind = ProfileKind.KIDS,
            verifiedKidsContentKeys = emptySet(),
        )
        val allowed = resolveTvDeepLink(
            target = TvDeepLinkTarget.Movie(movie.id),
            movieCatalog = movies,
            seriesCatalog = seriesCatalog,
            history = emptyList(),
            profileKind = ProfileKind.KIDS,
            verifiedKidsContentKeys = setOf("MOVIE:${movie.id}"),
        )

        assertEquals(TvDeepLinkResolution.BlockedForKids, blocked)
        assertTrue(allowed is TvDeepLinkResolution.OpenMovie)
    }

    @Test
    fun watchHistoryMapsToContinueWatching() {
        val history = listOf(
            episodeHistory(updatedAt = 300L),
            movieHistory(id = 41, updatedAt = 100L),
        )
        val items = TvContinueWatchingMapper.map(standardScope("A"), history, emptySet())
        val capped = TvContinueWatchingMapper.map(
            standardScope("A"),
            List(25) { index -> movieHistory(id = 100 + index, updatedAt = index.toLong()) },
            emptySet(),
        )

        assertEquals(2, items.size)
        assertEquals(20, capped.size)
        assertTrue(items.first().type == TvContinueWatchingType.MOVIE)
        assertTrue(items.any { it.type == TvContinueWatchingType.EPISODE })
        assertEquals("hulksa://movie/41?resume=true", items.first { it.contentId == 41 }.deepLinkUri)
        assertEquals(
            "hulksa://episode/82/503?resume=true",
            items.first { it.type == TvContinueWatchingType.EPISODE }.deepLinkUri,
        )
        assertEquals(25_000L, items.first { it.contentId == 41 }.positionMs)
        assertTrue(
            resolveTvDeepLink(
                target = TvDeepLinkTarget.Movie(41, resumePlayback = true),
                movieCatalog = movies,
                seriesCatalog = seriesCatalog,
                history = history,
                profileKind = ProfileKind.STANDARD,
                verifiedKidsContentKeys = emptySet(),
            ) is TvDeepLinkResolution.OpenMovie,
        )
    }

    @Test
    fun completedContentIsNotPublished() {
        val completed = movieHistory(id = 41, position = 92_000L, duration = 100_000L)
        val zeroPosition = movieHistory(id = 42, position = 0L, duration = 100_000L)
        val unknownDuration = movieHistory(id = 43, position = 10_000L, duration = 0L)

        assertTrue(
            TvContinueWatchingMapper.map(
                standardScope("A"),
                listOf(completed, zeroPosition, unknownDuration),
                emptySet(),
            ).isEmpty(),
        )
    }

    @Test
    fun duplicateSyncDoesNotCreateDuplicatePrograms() {
        val desired = TvContinueWatchingMapper.map(
            standardScope("A"),
            listOf(movieHistory(id = 41)),
            emptySet(),
        )
        val providerId = desired.single().providerId
        val firstPlan = planTvProgramSync(
            existing = listOf(
                ExistingTvProgram(10L, providerId),
                ExistingTvProgram(11L, providerId),
            ),
            desired = desired,
        )
        val repeatedPlan = planTvProgramSync(
            existing = listOf(ExistingTvProgram(10L, providerId)),
            desired = desired,
        )

        assertEquals(10L, firstPlan.upserts.single().existingId ?: -1L)
        assertEquals(setOf(11L), firstPlan.deleteIds)
        assertEquals(10L, repeatedPlan.upserts.single().existingId ?: -1L)
        assertTrue(repeatedPlan.deleteIds.isEmpty())
    }

    @Test
    fun profileAContentDoesNotAppearInProfileB() {
        val profileAItems = TvContinueWatchingMapper.map(
            standardScope("A"),
            listOf(movieHistory(id = 41)),
            emptySet(),
        )
        val profileBItems = TvContinueWatchingMapper.map(
            standardScope("B"),
            listOf(episodeHistory(id = 504)),
            emptySet(),
        )

        assertTrue(profileAItems.all { it.scope.profileId == "A" })
        assertTrue(profileBItems.all { it.scope.profileId == "B" })
        assertTrue(profileBItems.none { it.contentId == 41 })
    }

    @Test
    fun logoutClearsUserSpecificPublishedEntries() {
        val plan = planTvProgramSync(
            existing = listOf(
                ExistingTvProgram(1L, "${TV_PROGRAM_PROVIDER_PREFIX}movie:41"),
                ExistingTvProgram(2L, "${TV_PROGRAM_PROVIDER_PREFIX}episode:82:503"),
            ),
            desired = emptyList(),
        )

        assertTrue(plan.upserts.isEmpty())
        assertEquals(setOf(1L, 2L), plan.deleteIds)
    }

    @Test
    fun unsupportedOrNonTvDeviceFailsSafely() {
        assertFalse(isTvPlatformSupported(false, 36, true))
        assertFalse(isTvPlatformSupported(true, 25, true))
        assertFalse(isTvPlatformSupported(true, 36, false))
        assertTrue(isTvPlatformSupported(true, 36, true))
    }

    @Test
    fun missingContentDoesNotCrash() {
        val missingMovie = resolveTvDeepLink(
            target = TvDeepLinkTarget.Movie(999),
            movieCatalog = movies,
            seriesCatalog = seriesCatalog,
            history = emptyList(),
            profileKind = ProfileKind.STANDARD,
            verifiedKidsContentKeys = emptySet(),
        )
        val episodes = listOf(
            Episode(503, "Episode", 1, 2, "mp4", null, null),
        )

        assertEquals(TvDeepLinkResolution.MissingContent, missingMovie)
        assertNull(findTvDeepLinkEpisode(episodes, 999))
    }

    private fun standardScope(profileId: String) = TvProfileScope(
        accountId = "account",
        profileId = profileId,
        profileKind = ProfileKind.STANDARD,
    )

    private fun content(id: Int, name: String, type: ContentType) = ContentItem(
        id = id,
        name = name,
        categoryId = "category",
        type = type,
        posterUrl = "https://images.example/$id.jpg",
        rating = null,
        year = null,
        containerExtension = "mp4",
    )

    private fun movieHistory(
        id: Int,
        position: Long = 25_000L,
        duration: Long = 100_000L,
        updatedAt: Long = 100L,
    ) = HistoryEntry(
        key = "MOVIE:$id",
        title = "Movie $id",
        posterUrl = "https://images.example/$id.jpg",
        streamKind = "movie",
        streamId = id,
        extension = "mp4",
        isLive = false,
        positionMs = position,
        durationMs = duration,
        updatedAtEpochMs = updatedAt,
    )

    private fun episodeHistory(
        id: Int = 503,
        updatedAt: Long = 200L,
    ) = HistoryEntry(
        key = "SERIES:$id",
        title = "Series · Episode",
        posterUrl = "https://images.example/episode-$id.jpg",
        streamKind = "series",
        streamId = id,
        extension = "mp4",
        isLive = false,
        positionMs = 30_000L,
        durationMs = 100_000L,
        updatedAtEpochMs = updatedAt,
        seriesTitle = "Series",
        season = 1,
        episodeNumber = 2,
        episodeTitle = "Episode",
        parentContentId = 82,
    )
}
