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
        val scopeId = standardScope("A").providerScopeId
        val scoped = TvDeepLinkTarget.Movie(
            movieId = 41,
            resumePlayback = true,
            profileScopeId = scopeId,
        )

        assertEquals(TvDeepLinkTarget.Movie(41), TvDeepLinkRouter.parse("hulksa://movie/41"))
        assertEquals(scoped, TvDeepLinkRouter.parse(TvDeepLinkRouter.uri(scoped)))
        assertEquals(
            "hulksa://movie/41?resume=true&scope=$scopeId",
            TvDeepLinkRouter.uri(scoped),
        )
    }

    @Test
    fun seriesDeepLinkParsing() {
        assertEquals(TvDeepLinkTarget.Series(82), TvDeepLinkRouter.parse("hulksa://series/82"))
        assertEquals("hulksa://series/82", TvDeepLinkRouter.uri(TvDeepLinkTarget.Series(82)))
    }

    @Test
    fun episodeDeepLinkParsing() {
        val scopeId = standardScope("A").providerScopeId
        val target = TvDeepLinkTarget.Episode(
            seriesId = 82,
            episodeId = 503,
            resumePlayback = true,
            profileScopeId = scopeId,
        )

        assertEquals(target, TvDeepLinkRouter.parse(TvDeepLinkRouter.uri(target)))
        assertEquals(
            "hulksa://episode/82/503?resume=true&scope=$scopeId",
            TvDeepLinkRouter.uri(target),
        )
    }

    @Test
    fun invalidDeepLinkFailsSafely() {
        val scopeId = standardScope("A").providerScopeId
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
            "hulksa://movie/41?resume=true",
            "hulksa://movie/41?scope=$scopeId",
            "hulksa://movie/41?resume=true&scope=profile_A",
            "hulksa://movie/41?resume=true&resume=true",
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
    fun channelMetadataUsesBrandOnlyDisplayName() {
        assertEquals("HULK SA", TV_CHANNEL_DISPLAY_NAME)
        assertEquals("اكمل المشاهدة", TV_CONTINUE_WATCHING_LABEL)
    }

    @Test
    fun movieProgramUsesClearConciseDescription() {
        val item = TvContinueWatchingMapper.map(
            scope = standardScope("A"),
            history = listOf(
                movieHistory(
                    id = 41,
                    position = 6_120_000L,
                    duration = 7_200_000L,
                ),
            ),
            verifiedKidsContentKeys = emptySet(),
        ).single()

        assertEquals(
            "فيلم • تم الوصول إلى 1س 42د • المتبقي 18د",
            item.description,
        )
    }

    @Test
    fun episodeProgramUsesClearConciseDescription() {
        val item = TvContinueWatchingMapper.map(
            scope = standardScope("A"),
            history = listOf(
                episodeHistory().copy(
                    positionMs = 1_380_000L,
                    durationMs = 2_400_000L,
                    episodeNumber = 4,
                ),
            ),
            verifiedKidsContentKeys = emptySet(),
        ).single()

        assertEquals(
            "مسلسل • الموسم 1 • الحلقة 4 • تم الوصول إلى 23د • المتبقي 17د",
            item.description,
        )
    }

    @Test
    fun previewProgramUsesLandscapeAspectRatioAndLocalBackdrop() {
        val item = TvContinueWatchingMapper.map(
            scope = standardScope("A"),
            history = listOf(movieHistory(id = 41)),
            verifiedKidsContentKeys = emptySet(),
            landscapeArtworkByContentKey = mapOf("MOVIE:41" to "https://images.example/41-wide.jpg"),
        ).single()
        val posterFallback = TvContinueWatchingMapper.map(
            scope = standardScope("A"),
            history = listOf(movieHistory(id = 42)),
            verifiedKidsContentKeys = emptySet(),
        ).single()

        assertEquals(TvProgramArtworkAspectRatio.LANDSCAPE_16_9, item.artworkAspectRatio)
        assertEquals("https://images.example/41-wide.jpg", item.landscapeImageUrl)
        assertEquals(25_000L, item.positionMs)
        assertEquals(100_000L, item.durationMs)
        assertTrue(item.providerId.startsWith(TV_PROGRAM_PROVIDER_PREFIX))
        assertTrue("scope=${item.scope.providerScopeId}" in item.deepLinkUri)
        assertEquals("https://images.example/42.jpg", posterFallback.landscapeImageUrl)
    }

    @Test
    fun invalidOrMissingBackdropFallsBackToSafePoster() {
        val missingBackdrop = TvContinueWatchingMapper.map(
            scope = standardScope("A"),
            history = listOf(movieHistory(id = 41)),
            verifiedKidsContentKeys = emptySet(),
        ).single()
        val unsafeBackdrop = TvContinueWatchingMapper.map(
            scope = standardScope("A"),
            history = listOf(movieHistory(id = 41)),
            verifiedKidsContentKeys = emptySet(),
            landscapeArtworkByContentKey = mapOf(
                "MOVIE:41" to "https://user:secret@images.example/unsafe-wide.jpg",
            ),
        ).single()

        assertEquals("https://images.example/41.jpg", missingBackdrop.landscapeImageUrl)
        assertEquals("https://images.example/41.jpg", unsafeBackdrop.landscapeImageUrl)
        assertNull(selectTvProgramArtwork("file:///private/backdrop.jpg", "javascript:bad"))
    }

    @Test
    fun profileAProgramsDoNotRemainAfterSwitchToProfileB() {
        val profileA = TvContinueWatchingMapper.map(
            standardScope("A"),
            listOf(movieHistory(id = 41), episodeHistory()),
            emptySet(),
        )
        val profileB = TvContinueWatchingMapper.map(
            standardScope("B"),
            listOf(movieHistory(id = 42)),
            emptySet(),
        )
        val syncPlan = planTvProgramSync(
            existing = profileA.mapIndexed { index, item ->
                ExistingTvProgram(index.toLong() + 1L, item.providerId)
            },
            desired = profileB,
        )

        assertEquals(setOf(1L, 2L), syncPlan.deleteIds)
        assertEquals(profileB.single().providerId, syncPlan.upserts.single().item.providerId)
        assertTrue(syncPlan.upserts.none { it.item.scope.profileId == "A" })
    }

    @Test
    fun providerIdsDifferBetweenProfilesForSameContent() {
        val profileA = TvContinueWatchingMapper.map(
            standardScope("A"),
            listOf(movieHistory(id = 41)),
            emptySet(),
        ).single()
        val profileB = TvContinueWatchingMapper.map(
            standardScope("B"),
            listOf(movieHistory(id = 41)),
            emptySet(),
        ).single()

        assertTrue(profileA.providerId != profileB.providerId)
        assertTrue(profileA.deepLinkUri != profileB.deepLinkUri)
        assertTrue(profileA.providerId.startsWith("$TV_PROGRAM_PROVIDER_PREFIX${profileA.scope.providerScopeId}:"))
        assertTrue(profileB.providerId.startsWith("$TV_PROGRAM_PROVIDER_PREFIX${profileB.scope.providerScopeId}:"))
    }

    @Test
    fun profileSwitchClearsPreviousThenPublishesNew() {
        val profileA = standardScope("A")
        val profileB = standardScope("B")

        assertEquals(
            listOf(TvProfilePublicationPhase.CLEAR, TvProfilePublicationPhase.PUBLISH),
            planTvProfilePublication(
                previouslyPublishedScopeId = profileA.providerScopeId,
                activeScopeId = profileB.providerScopeId,
                hasSession = true,
                profileResolved = true,
                kidsVerificationRequired = false,
                kidsVerified = true,
            ),
        )
    }

    @Test
    fun kidsPendingDoesNotPublish() {
        val kidsScope = TvProfileScope("account", "kids", ProfileKind.KIDS)

        assertEquals(
            listOf(TvProfilePublicationPhase.CLEAR),
            planTvProfilePublication(
                previouslyPublishedScopeId = standardScope("A").providerScopeId,
                activeScopeId = kidsScope.providerScopeId,
                hasSession = true,
                profileResolved = true,
                kidsVerificationRequired = true,
                kidsVerified = false,
            ),
        )
    }

    @Test
    fun logoutClearsAllHulkPrograms() {
        val desired = TvContinueWatchingMapper.map(
            standardScope("A"),
            listOf(movieHistory(41), episodeHistory()),
            emptySet(),
        )
        val existing = desired.mapIndexed { index, item ->
            ExistingTvProgram(index.toLong() + 1L, item.providerId)
        }
        val clearPlan = planTvProgramSync(existing = existing, desired = emptyList())

        assertTrue(clearPlan.upserts.isEmpty())
        assertEquals(setOf(1L, 2L), clearPlan.deleteIds)
        assertTrue(existing.all { it.providerId.startsWith(TV_PROGRAM_PROVIDER_PREFIX) })
    }

    @Test
    fun invalidSessionUsesClearOnlyPlan() {
        assertEquals(
            listOf(TvProfilePublicationPhase.CLEAR),
            planTvProfilePublication(
                previouslyPublishedScopeId = standardScope("A").providerScopeId,
                activeScopeId = null,
                hasSession = false,
                profileResolved = false,
                kidsVerificationRequired = false,
                kidsVerified = false,
            ),
        )
    }

    @Test
    fun existingSessionDeepLinkDoesNotGoToLogin() {
        assertEquals(
            TvDeepLinkDispatchDecision.DISPATCH,
            decideTvDeepLinkDispatch(
                sessionRestorationComplete = true,
                hasSession = true,
                profileResolved = true,
                kidsVerificationRequired = false,
                kidsVerified = true,
            ),
        )
    }

    @Test
    fun deepLinkWaitsForSessionRestoration() {
        assertEquals(
            TvDeepLinkDispatchDecision.WAIT_FOR_SESSION_RESTORATION,
            decideTvDeepLinkDispatch(
                sessionRestorationComplete = false,
                hasSession = false,
                profileResolved = false,
                kidsVerificationRequired = false,
                kidsVerified = false,
            ),
        )
    }

    @Test
    fun staleProfileProgramDeepLinkFailsSafely() {
        val profileA = standardScope("A")
        val profileB = standardScope("B")
        val resolution = resolveTvDeepLink(
            target = TvDeepLinkTarget.Movie(
                movieId = movie.id,
                resumePlayback = true,
                profileScopeId = profileA.providerScopeId,
            ),
            movieCatalog = movies,
            seriesCatalog = seriesCatalog,
            history = listOf(movieHistory(movie.id)),
            profileKind = ProfileKind.STANDARD,
            verifiedKidsContentKeys = emptySet(),
            activeProfileScopeId = profileB.providerScopeId,
        )

        assertEquals(TvDeepLinkResolution.StaleProfile, resolution)
    }

    @Test
    fun firstPlaybackProgressTriggersSyncQuickly() {
        assertFalse(shouldSyncTvProgress(0L, 4_999L, 100_000L))
        assertTrue(shouldSyncTvProgress(0L, 5_000L, 100_000L))
    }

    @Test
    fun twelveSecondMeaningfulProgressTriggersSync() {
        assertTrue(shouldSyncTvProgress(5_000L, 17_000L, 100_000L))
        assertTrue(shouldSyncTvProgress(30_000L, 18_000L, 100_000L))
    }

    @Test
    fun tinyProgressChangesDoNotSyncRepeatedly() {
        assertFalse(shouldSyncTvProgress(5_000L, 10_000L, 100_000L))
        assertFalse(shouldSyncTvProgress(5_000L, 15_999L, 100_000L))
    }

    @Test
    fun completionAtNinetyTwoPercentRemovesProgram() {
        val completed = movieHistory(id = 41, position = 92_000L, duration = 100_000L)

        assertTrue(shouldSyncTvProgress(80_000L, 92_000L, 100_000L))
        assertTrue(
            TvContinueWatchingMapper.map(
                standardScope("A"),
                listOf(completed),
                emptySet(),
            ).isEmpty(),
        )
    }

    @Test
    fun continueWatchingSortsByUpdatedAtDescending() {
        val items = TvContinueWatchingMapper.map(
            standardScope("A"),
            listOf(
                movieHistory(id = 41, updatedAt = 100L),
                episodeHistory(id = 503, updatedAt = 300L),
                movieHistory(id = 42, updatedAt = 200L),
            ),
            emptySet(),
        )

        assertEquals(listOf(300L, 200L, 100L), items.map(TvContinueWatchingItem::updatedAtEpochMs))
        assertEquals(TvContinueWatchingType.EPISODE, items.first().type)
    }

    @Test
    fun duplicateSyncRemainsIdempotent() {
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
    fun movieAndEpisodeMixedHistoryRemainsCorrect() {
        val scope = standardScope("A")
        val items = TvContinueWatchingMapper.map(
            scope = scope,
            history = listOf(
                episodeHistory(updatedAt = 300L),
                movieHistory(id = 41, updatedAt = 100L),
            ),
            verifiedKidsContentKeys = emptySet(),
            landscapeArtworkByContentKey = mapOf(
                "MOVIE:41" to "https://images.example/movie-wide.jpg",
                "SERIES:82" to "https://images.example/series-wide.jpg",
            ),
        )

        assertEquals(2, items.size)
        assertEquals(setOf(TvContinueWatchingType.MOVIE, TvContinueWatchingType.EPISODE), items.mapTo(mutableSetOf()) { it.type })
        assertEquals("https://images.example/series-wide.jpg", items.first().landscapeImageUrl)
        assertTrue(items.all { "scope=${scope.providerScopeId}" in it.deepLinkUri })
        assertEquals(25_000L, items.first { it.type == TvContinueWatchingType.MOVIE }.positionMs)
        assertEquals(82, items.first { it.type == TvContinueWatchingType.EPISODE }.seriesId)
    }

    @Test
    fun historyMappingIsCappedAtTwenty() {
        val items = TvContinueWatchingMapper.map(
            standardScope("A"),
            List(25) { index -> movieHistory(id = 100 + index, updatedAt = index.toLong()) },
            emptySet(),
        )

        assertEquals(20, items.size)
        assertEquals(124, items.first().contentId)
    }

    @Test
    fun unsupportedTvProviderFailsSafely() {
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
        val episodes = listOf(Episode(503, "Episode", 1, 2, "mp4", null, null))

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
        backdropUrl = "https://images.example/$id-wide.jpg",
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
