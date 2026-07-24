from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
main = root / 'app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
gradle = root / 'app/build.gradle.kts'

M = main.read_text()
G = gradle.read_text()

G = G.replace('versionCode = 27', 'versionCode = 28')
G = G.replace('versionName = "0.9.1.5"', 'versionName = "0.9.1.6"')

# Hero rotation must not request focus again while the side rail is open.
M, focus_count = re.subn(
    r'LaunchedEffect\(remembered\.rowKey,\s*featured\.id\)',
    'LaunchedEffect(remembered.rowKey)',
    M,
    count=1,
)
if focus_count != 1:
    raise SystemExit(f'v0916 hero focus anchor mismatch: {focus_count}')

home_start = M.index('@Composable\nprivate fun CinemaHomeScreen(')
home_end = M.index('\n@Composable\nprivate fun ActiveDownloadsSection', home_start)
home = M[home_start:home_end]

recommendation_start = home.index('    val recentlyWatchedIds = remember(state.history) {')
recommendation_end = home.index('    val popularMovies = remember(movies) {', recommendation_start)

personalization = r'''    val historySeedItems = remember(movies, series, state.history) {
        val movieById = movies.associateBy(ContentItem::id)
        val seriesByName = series.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        state.history.asSequence()
            .filterNot { it.isLive }
            .mapNotNull { entry ->
                when (entry.streamKind) {
                    "movie" -> movieById[entry.streamId]
                    "series" -> seriesByName[
                        entry.title.substringBefore("·").trim().lowercase(Locale.ROOT)
                    ]
                    else -> null
                }
            }
            .distinctBy { "${it.type}:${it.id}" }
            .take(24)
            .toList()
    }
    val favoriteSeedItems = remember(movies, series, state.favorites) {
        (movies + series).filter(isFavorite)
    }
    val interestCategoryWeights = remember(historySeedItems, favoriteSeedItems) {
        val weights = mutableMapOf<String, Int>()
        historySeedItems.forEachIndexed { index, item ->
            val weight = (24 - index).coerceAtLeast(2)
            weights[item.categoryId] = (weights[item.categoryId] ?: 0) + weight
        }
        favoriteSeedItems.forEach { item ->
            weights[item.categoryId] = (weights[item.categoryId] ?: 0) + 30
        }
        weights.toMap()
    }
    val interestGenreWeights = remember(historySeedItems, favoriteSeedItems) {
        val weights = mutableMapOf<String, Int>()
        fun add(item: ContentItem, weight: Int) {
            item.genre.orEmpty().split(',', '،', '/', '|')
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter(String::isNotBlank)
                .forEach { genre -> weights[genre] = (weights[genre] ?: 0) + weight }
        }
        historySeedItems.forEachIndexed { index, item -> add(item, (24 - index).coerceAtLeast(2)) }
        favoriteSeedItems.forEach { add(it, 30) }
        weights.toMap()
    }
    val watchedContentKeys = remember(historySeedItems) {
        historySeedItems.map { "${it.type}:${it.id}" }.toSet()
    }
    val contentInterestScores = remember(movies, series, interestCategoryWeights, interestGenreWeights) {
        (movies + series).associate { item ->
            val categoryScore = (interestCategoryWeights[item.categoryId] ?: 0) * 100
            val genreScore = item.genre.orEmpty().split(',', '،', '/', '|')
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter(String::isNotBlank)
                .sumOf { genre -> (interestGenreWeights[genre] ?: 0) * 28 }
            "${item.type}:${item.id}" to (categoryScore + genreScore)
        }
    }
    val personalizedContentPool = remember(
        movies,
        series,
        watchedContentKeys,
        contentInterestScores,
        state.favorites,
    ) {
        (movies + series).asSequence()
            .filterNot { item ->
                isFavorite(item) || "${item.type}:${item.id}" in watchedContentKeys
            }
            .sortedWith(
                compareByDescending<ContentItem> { item ->
                    contentInterestScores["${item.type}:${item.id}"] ?: 0
                }
                    .thenByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                    .thenByDescending { it.addedAtEpochSeconds ?: 0L },
            )
            .toList()
    }
    val becauseYouWatched = remember(personalizedContentPool, contentInterestScores) {
        personalizedContentPool.filter { item ->
            (contentInterestScores["${item.type}:${item.id}"] ?: 0) > 0
        }.take(14)
    }
    val suggested = remember(personalizedContentPool, becauseYouWatched) {
        val blocked = becauseYouWatched.map { "${it.type}:${it.id}" }.toSet()
        personalizedContentPool.asSequence()
            .filterNot { "${it.type}:${it.id}" in blocked }
            .take(24)
            .toList()
    }
    val personalizedLive = remember(live, state.history, state.favorites) {
        val liveById = live.associateBy(ContentItem::id)
        val viewed = state.history.asSequence()
            .filter { it.isLive }
            .mapNotNull { liveById[it.streamId] }
            .take(30)
            .toList()
        val categoryWeights = mutableMapOf<String, Int>()
        viewed.forEachIndexed { index, item ->
            val weight = (30 - index).coerceAtLeast(1)
            categoryWeights[item.categoryId] = (categoryWeights[item.categoryId] ?: 0) + weight
        }
        live.filter(isFavorite).forEach { item ->
            categoryWeights[item.categoryId] = (categoryWeights[item.categoryId] ?: 0) + 35
        }
        val viewedIds = viewed.map(ContentItem::id).toSet()
        live.sortedWith(
            compareByDescending<ContentItem> { item ->
                (if (isFavorite(item)) 10_000 else 0) +
                    (categoryWeights[item.categoryId] ?: 0) * 100 +
                    (if (item.id in viewedIds) 25 else 0)
            }
                .thenByDescending { !it.nowPlaying.isNullOrBlank() }
                .thenBy { it.name.lowercase(Locale.ROOT) },
        )
    }
'''

home = home[:recommendation_start] + personalization + home[recommendation_end:]
home = home.replace('live.take(20)', 'personalizedLive.take(20)')
home = home.replace('"قنوات شائعة"', '"قنوات مقترحة لك"')
home = home.replace('"قنوات شايعة"', '"قنوات مقترحة لك"')

if 'personalizedLive.take(20)' not in home:
    raise SystemExit('v0916 personalized live rendering missing')
if 'val suggested = remember(personalizedContentPool' not in home:
    raise SystemExit('v0916 personalized suggestions missing')

M = M[:home_start] + home + M[home_end:]

if 'LaunchedEffect(remembered.rowKey, featured.id)' in M:
    raise SystemExit('v0916 hero still depends on featured id')
if 'versionName = "0.9.1.6"' not in G:
    raise SystemExit('v0916 version update failed')

main.write_text(M)
gradle.write_text(G)
