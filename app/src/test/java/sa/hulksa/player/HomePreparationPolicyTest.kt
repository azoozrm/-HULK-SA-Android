package sa.hulksa.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.screens.HomeContentModelInput
import sa.hulksa.player.ui.screens.deriveHomeContentModel
import sa.hulksa.player.ui.screens.hasHomeHeroSource
import sa.hulksa.player.ui.screens.homePresentationSourcesEqual
import sa.hulksa.player.ui.screens.initialHomePresentation
import sa.hulksa.player.ui.screens.stableHomePresentationInput

class HomePreparationPolicyTest {
    private fun item(id: Int, type: ContentType) = ContentItem(
        id = id,
        name = "item-$id",
        categoryId = "category",
        type = type,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = null,
    )

    private fun catalog(type: ContentType, vararg ids: Int) = Catalog(
        categories = emptyList(),
        items = ids.map { item(it, type) },
    )

    private fun pending(
        destination: MainDestination = MainDestination.HOME,
        profileReady: Boolean = true,
        hero: Boolean = false,
        cacheActive: Boolean = false,
        cacheTypes: Boolean = false,
        networkLoading: Boolean = false,
        failure: Boolean = false,
        empty: Boolean = false,
    ) = homePreparationPending(
        destination = destination,
        isProfileLibraryReady = profileReady,
        hasUsableHomeHero = hero,
        cacheRestoreActive = cacheActive,
        pendingCacheTypes = cacheTypes,
        networkLoading = networkLoading,
        hasSettledFailure = failure,
        hasSettledHomeHeroAbsence = empty,
    )

    @Test
    fun `initial preparation is pending until profile and source readiness`() {
        assertTrue("profile library not ready", pending(profileReady = false))
        assertTrue("cache restore active", pending(cacheActive = true))
        assertTrue("cache types queued", pending(cacheTypes = true))
        assertTrue("network loading", pending(networkLoading = true))
        assertTrue("no content and no settled outcome", pending())
    }

    @Test
    fun `available hero wins over background revalidation`() {
        assertFalse(pending(hero = true, cacheActive = true, networkLoading = true))
        assertFalse(pending(hero = true, cacheTypes = true))
    }

    @Test
    fun `confirmed empty and settled failure terminate pending`() {
        assertFalse(pending(empty = true, networkLoading = true))
        assertFalse(pending(failure = true, networkLoading = true))
    }

    @Test
    fun `missing live does not block confirmed hero absence`() {
        val loaded = mapOf(
            ContentType.MOVIE to catalog(ContentType.MOVIE),
            ContentType.SERIES to catalog(ContentType.SERIES),
        )
        assertTrue(
            settledHomeHeroAbsence(
                loaded = loaded,
                freshTypes = setOf(ContentType.MOVIE, ContentType.SERIES),
                failedTypes = emptySet(),
                cacheAttemptedTypes = emptySet(),
            ),
        )
    }

    @Test
    fun `missing movie or series is not settled`() {
        val loaded = mapOf(
            ContentType.MOVIE to catalog(ContentType.MOVIE),
        )
        assertFalse(
            settledHomeHeroAbsence(
                loaded = loaded,
                freshTypes = setOf(ContentType.MOVIE),
                failedTypes = emptySet(),
                cacheAttemptedTypes = emptySet(),
            ),
        )
        assertTrue(pending(empty = false, cacheActive = false, networkLoading = false))
    }

    @Test
    fun `hero absence requires movie and series settled empty`() {
        val heroEmpty = mapOf(
            ContentType.MOVIE to catalog(ContentType.MOVIE),
            ContentType.SERIES to catalog(ContentType.SERIES),
            ContentType.LIVE to catalog(ContentType.LIVE),
        )
        assertTrue(
            settledHomeHeroAbsence(
                loaded = heroEmpty,
                freshTypes = ContentType.entries.toSet(),
                failedTypes = emptySet(),
                cacheAttemptedTypes = emptySet(),
            ),
        )
        assertTrue(
            "live-only rows do not provide a hero",
            settledHomeHeroAbsence(
                loaded = heroEmpty + (ContentType.LIVE to catalog(ContentType.LIVE, 1)),
                freshTypes = ContentType.entries.toSet(),
                failedTypes = emptySet(),
                cacheAttemptedTypes = emptySet(),
            ),
        )
        assertFalse(
            settledHomeHeroAbsence(
                loaded = heroEmpty + (ContentType.MOVIE to catalog(ContentType.MOVIE, 1)),
                freshTypes = ContentType.entries.toSet(),
                failedTypes = emptySet(),
                cacheAttemptedTypes = emptySet(),
            ),
        )
    }

    @Test
    fun `cache hit hero types are settled while a cache miss is not`() {
        val emptyHit = mapOf(
            ContentType.MOVIE to catalog(ContentType.MOVIE),
            ContentType.SERIES to catalog(ContentType.SERIES),
        )
        assertTrue(
            settledHomeHeroAbsence(
                loaded = emptyHit,
                freshTypes = emptySet(),
                failedTypes = emptySet(),
                cacheAttemptedTypes = ContentType.entries.toSet(),
            ),
        )
        assertFalse(
            settledHomeHeroAbsence(
                loaded = mapOf(ContentType.MOVIE to catalog(ContentType.MOVIE)),
                freshTypes = emptySet(),
                failedTypes = emptySet(),
                cacheAttemptedTypes = ContentType.entries.toSet(),
            ),
        )
    }

    @Test
    fun `non-home destination is never pending`() {
        assertFalse(pending(destination = MainDestination.MOVIES, cacheActive = true))
        assertFalse(pending(destination = MainDestination.SEARCH, networkLoading = true))
    }

    @Test
    fun `empty presented model keeps loading until bootstrap presentation is adopted`() {
        val oldInput = HomeContentModelInput(
            movieCatalog = null,
            seriesCatalog = null,
            liveCatalog = null,
            history = emptyList(),
            favorites = emptySet(),
        )
        val presented = initialHomePresentation(oldInput)
        assertFalse(presented.hasHomeHeroSource())

        val bootstrap = Catalog(emptyList(), listOf(item(7, ContentType.MOVIE)))
        val newInput = HomeContentModelInput(
            movieCatalog = bootstrap,
            seriesCatalog = null,
            liveCatalog = null,
            history = emptyList(),
            favorites = emptySet(),
        )
        val sourceHasHero = newInput.movieCatalog?.items?.isNotEmpty() == true
        val transition = homePresentationTransitionPending(
            hasUsableHomeHeroSource = sourceHasHero,
            presentedHasHero = presented.hasHomeHeroSource(),
            homeModelSettled = false,
        )

        assertTrue("bootstrap source exists but old empty presentation is still shown", transition)
        assertEquals(
            HomePlaceholderMode.LOADING,
            homePlaceholderMode(hasError = false, loading = transition),
        )

        val cheap = initialHomePresentation(newInput)
        assertTrue("cheap bootstrap presentation carries a hero", cheap.hasHomeHeroSource())
        assertFalse(
            homePresentationTransitionPending(
                hasUsableHomeHeroSource = sourceHasHero,
                presentedHasHero = cheap.hasHomeHeroSource(),
                homeModelSettled = false,
            ),
        )

        val exact = deriveHomeContentModel(newInput)
        assertTrue(exact.hasHomeHeroSource())
        assertFalse(
            homePresentationTransitionPending(
                hasUsableHomeHeroSource = sourceHasHero,
                presentedHasHero = exact.hasHomeHeroSource(),
                homeModelSettled = true,
            ),
        )
    }

    @Test
    fun `presented hero survives revalidation and favorites-only source stability`() {
        val bootstrap = Catalog(emptyList(), listOf(item(7, ContentType.MOVIE)))
        val accepted = HomeContentModelInput(
            movieCatalog = bootstrap,
            seriesCatalog = null,
            liveCatalog = null,
            history = emptyList(),
            favorites = emptySet(),
        )
        val committed = HomeContentModelInput(
            movieCatalog = bootstrap,
            seriesCatalog = null,
            liveCatalog = null,
            history = emptyList(),
            favorites = setOf("MOVIE:7"),
        )
        val presented = deriveHomeContentModel(accepted)

        assertTrue(homePresentationSourcesEqual(accepted, committed))
        assertSame(accepted, stableHomePresentationInput(accepted, committed))
        assertFalse(
            "a presented hero is not replaced by loading during revalidation/favorite commit",
            homePresentationTransitionPending(
                hasUsableHomeHeroSource = true,
                presentedHasHero = presented.hasHomeHeroSource(),
                homeModelSettled = false,
            ),
        )
    }

    @Test
    fun `settled empty and source cancellation do not force loading`() {
        val emptyInput = HomeContentModelInput(
            movieCatalog = null,
            seriesCatalog = null,
            liveCatalog = null,
            history = emptyList(),
            favorites = emptySet(),
        )
        val empty = deriveHomeContentModel(emptyInput)
        assertFalse(empty.hasHomeHeroSource())
        assertFalse(
            homePresentationTransitionPending(
                hasUsableHomeHeroSource = false,
                presentedHasHero = empty.hasHomeHeroSource(),
                homeModelSettled = true,
            ),
        )
        assertEquals(
            HomePlaceholderMode.EMPTY,
            homePlaceholderMode(hasError = false, loading = false),
        )
        assertFalse(
            "cleared/obsolete source does not keep loading",
            homePresentationTransitionPending(
                hasUsableHomeHeroSource = false,
                presentedHasHero = false,
                homeModelSettled = false,
            ),
        )
    }

    @Test
    fun `placeholder mode is error then loading then settled empty`() {
        assertEquals(
            HomePlaceholderMode.ERROR,
            homePlaceholderMode(hasError = true, loading = true),
        )
        assertEquals(
            HomePlaceholderMode.LOADING,
            homePlaceholderMode(hasError = false, loading = true),
        )
        assertEquals(
            HomePlaceholderMode.EMPTY,
            homePlaceholderMode(hasError = false, loading = false),
        )
    }

    @Test
    fun `production home rendering uses the authoritative pending owner`() {
        val screen = File("src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt").readText()
        assertTrue(screen.contains("state.isHomePreparationPending"))
        assertTrue(screen.contains("homePresentationTransitionPending("))
        assertTrue(screen.contains("homePlaceholderMode("))
        assertTrue(screen.contains("HomePlaceholderMode.LOADING"))
        assertTrue(screen.contains("HomePlaceholderMode.EMPTY"))

        val viewModel = File("src/main/java/sa/hulksa/player/HulkViewModel.kt").readText()
        assertTrue(viewModel.contains("isHomePreparationPending = true"))
        assertTrue(viewModel.contains("refreshHomePreparationState()"))
        assertTrue(viewModel.contains("settledHomeHeroAbsence("))
    }
}
