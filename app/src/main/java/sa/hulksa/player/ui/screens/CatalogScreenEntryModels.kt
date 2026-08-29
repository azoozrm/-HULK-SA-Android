package sa.hulksa.player.ui.screens

import java.util.EnumMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

internal class CatalogFavoriteSnapshot(
    val persisted: Set<String>,
    val optimistic: Map<String, Boolean> = emptyMap(),
) {
    fun contains(item: ContentItem): Boolean {
        val key = "${item.type.name}:${item.id}"
        return optimistic[key] ?: (key in persisted)
    }
}

/**
 * O(1) composition key for a profile-owned catalog screen model.
 *
 * Catalog, history, and persisted favorites are immutable state snapshots. Referential
 * comparison avoids invoking their structural O(N) equality from Compose on every focus
 * recomposition. A new snapshot identity is the catalog/history/favorites revision boundary.
 */
internal class CatalogScreenModelInput(
    val catalog: Catalog?,
    val history: List<HistoryEntry>,
    val favorites: CatalogFavoriteSnapshot,
    val type: ContentType,
    val destination: MainDestination,
    val categoryId: String?,
    val query: String,
) {
    init {
        require(destination == MainDestination.MOVIES || destination == MainDestination.SERIES)
        require(type == ContentType.MOVIE || type == ContentType.SERIES)
    }

    override fun equals(other: Any?): Boolean =
        other is CatalogScreenModelInput &&
            catalog === other.catalog &&
            history === other.history &&
            favorites.persisted === other.favorites.persisted &&
            favorites.optimistic == other.favorites.optimistic &&
            type == other.type &&
            destination == other.destination &&
            categoryId == other.categoryId &&
            query == other.query

    override fun hashCode(): Int {
        var result = System.identityHashCode(catalog)
        result = 31 * result + System.identityHashCode(history)
        result = 31 * result + System.identityHashCode(favorites.persisted)
        result = 31 * result + favorites.optimistic.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + destination.hashCode()
        result = 31 * result + (categoryId?.hashCode() ?: 0)
        return 31 * result + query.hashCode()
    }
}

internal data class CatalogScreenModel(
    val visible: List<ContentItem>,
    val continueWatching: List<HistoryEntry>,
    val contentKeys: List<String>,
    val contentKeyIndex: Map<String, Int>,
)

internal data class KeyedCatalogScreenModel(
    val input: CatalogScreenModelInput,
    val model: CatalogScreenModel,
)

internal class HomeContentModelInput(
    val movieCatalog: Catalog?,
    val seriesCatalog: Catalog?,
    val liveCatalog: Catalog?,
    val history: List<HistoryEntry>,
    val favorites: Set<String>,
) {
    override fun equals(other: Any?): Boolean =
        other is HomeContentModelInput &&
            movieCatalog === other.movieCatalog &&
            seriesCatalog === other.seriesCatalog &&
            liveCatalog === other.liveCatalog &&
            history === other.history &&
            favorites === other.favorites

    override fun hashCode(): Int {
        var result = System.identityHashCode(movieCatalog)
        result = 31 * result + System.identityHashCode(seriesCatalog)
        result = 31 * result + System.identityHashCode(liveCatalog)
        result = 31 * result + System.identityHashCode(history)
        return 31 * result + System.identityHashCode(favorites)
    }
}

internal data class KeyedHomeContentModel(
    val input: HomeContentModelInput,
    val model: HomeContentSnapshot,
)

/**
 * Cheap first-frame Home presentation for a fresh profile-owned store.
 *
 * It only references the already-loaded account catalogs and deliberately excludes history,
 * favorites, and every smart/personalized recommendation. No sorting, filtering, grouping, or
 * recommendation work is performed here, so the exact Home model can remain on Dispatchers.Default.
 */
internal fun initialHomePresentation(input: HomeContentModelInput): HomeContentSnapshot =
    HomeContentSnapshot(
        movies = input.movieCatalog?.items.orEmpty(),
        series = input.seriesCatalog?.items.orEmpty(),
        live = input.liveCatalog?.items.orEmpty(),
        continueWatching = emptyList(),
        lastLive = null,
        becauseYouWatched = emptyList(),
        suggested = emptyList(),
        personalizedLive = emptyList(),
        popularMovies = emptyList(),
        popularSeries = emptyList(),
        featuredCandidates = emptyList(),
    )

internal fun deriveCatalogScreenModel(input: CatalogScreenModelInput): CatalogScreenModel {
    val ordered = newest(input.catalog?.items.orEmpty())
    val visible = ordered.filter { item ->
        categoryMatches(item, input.categoryId, input.favorites::contains) &&
            item.matchesSearch(input.query)
    }
    val kind = if (input.type == ContentType.MOVIE) "movie" else "series"
    val normalizedQuery = input.query.trim()
    val continueWatching = input.history.filter { entry ->
        entry.streamKind == kind && entry.isResumable() &&
            (normalizedQuery.isBlank() || entry.title.contains(normalizedQuery, ignoreCase = true))
    }
    val contentKeys = visible.map { item -> "${item.type}:${item.id}" }
    return CatalogScreenModel(
        visible = visible,
        continueWatching = continueWatching,
        contentKeys = contentKeys,
        contentKeyIndex = indexContentKeys(contentKeys),
    )
}

internal fun indexContentKeys(contentKeys: List<String>): Map<String, Int> {
    val indexByKey = HashMap<String, Int>(contentKeys.size)
    contentKeys.forEachIndexed { index, key ->
        if (!indexByKey.containsKey(key)) indexByKey[key] = index
    }
    return indexByKey
}

internal fun deriveHomeContentModel(input: HomeContentModelInput): HomeContentSnapshot {
    val movies = newest(input.movieCatalog?.items.orEmpty())
    val series = newest(input.seriesCatalog?.items.orEmpty())
    val live = input.liveCatalog?.items.orEmpty()
    val smartHome = buildSmartHomeRecommendations(
        movies = movies,
        series = series,
        live = live,
        history = input.history,
        favorites = input.favorites,
    )
    return HomeContentSnapshot(
        movies = movies,
        series = series,
        live = live,
        continueWatching = smartHome.continueWatching,
        lastLive = smartHome.lastLive,
        becauseYouWatched = smartHome.becauseYouWatched,
        suggested = smartHome.suggested,
        personalizedLive = smartHome.personalizedLive,
        popularMovies = smartHome.popularMovies,
        popularSeries = smartHome.popularSeries,
        featuredCandidates = smartHome.featuredCandidates,
    )
}

/** Profile-owned, bounded derived-model cache. */
internal class CatalogScreenEntryModelStore(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val catalogBuilder: (CatalogScreenModelInput) -> CatalogScreenModel = ::deriveCatalogScreenModel,
    private val homeBuilder: (HomeContentModelInput) -> HomeContentSnapshot = ::deriveHomeContentModel,
) {
    private val lock = Any()
    private val catalogModels = EnumMap<MainDestination, KeyedCatalogScreenModel>(MainDestination::class.java)
    private val latestCatalogInputs = EnumMap<MainDestination, CatalogScreenModelInput>(MainDestination::class.java)
    private var homeModel: KeyedHomeContentModel? = null
    private var homePresentationFallback: KeyedHomeContentModel? = null
    private var latestHomeInput: HomeContentModelInput? = null

    fun cachedCatalog(input: CatalogScreenModelInput): KeyedCatalogScreenModel? = synchronized(lock) {
        catalogModels[input.destination]?.takeIf { it.input == input }
    }

    fun lastGoodCatalog(destination: MainDestination): KeyedCatalogScreenModel? = synchronized(lock) {
        catalogModels[destination]
    }

    suspend fun catalog(input: CatalogScreenModelInput): KeyedCatalogScreenModel {
        synchronized(lock) {
            latestCatalogInputs[input.destination] = input
            catalogModels[input.destination]?.takeIf { it.input == input }
        }?.let { return it }
        val derived = withContext(dispatcher) { catalogBuilder(input) }
        val keyed = KeyedCatalogScreenModel(input, derived)
        return synchronized(lock) {
            catalogModels[input.destination]
                ?.takeIf { it.input == input }
                ?: keyed.also {
                    if (latestCatalogInputs[input.destination] == input) {
                        catalogModels[input.destination] = it
                    }
                }
        }
    }

    fun cachedHome(input: HomeContentModelInput): KeyedHomeContentModel? = synchronized(lock) {
        // Register the current profile-owned input before presentation asks for last-good.
        // This lets a fresh store expose a cheap current-input fallback without sharing state.
        latestHomeInput = input
        homeModel?.takeIf { it.input == input }
    }

    fun lastGoodHome(): KeyedHomeContentModel? = synchronized(lock) {
        val input = latestHomeInput ?: return@synchronized homeModel
        homeModel
            ?.takeIf { it.input == input }
            ?: homePresentationFallback
                ?.takeIf { it.input == input }
            ?: KeyedHomeContentModel(
                input = input,
                model = initialHomePresentation(input),
            ).also { homePresentationFallback = it }
    }

    suspend fun home(input: HomeContentModelInput): KeyedHomeContentModel {
        synchronized(lock) {
            latestHomeInput = input
            homeModel?.takeIf { it.input == input }
        }?.let { return it }
        val derived = withContext(dispatcher) { homeBuilder(input) }
        val keyed = KeyedHomeContentModel(input, derived)
        return synchronized(lock) {
            homeModel
                ?.takeIf { it.input == input }
                ?: keyed.also {
                    if (latestHomeInput == input) {
                        homeModel = it
                        homePresentationFallback = null
                    }
                }
        }
    }
}
