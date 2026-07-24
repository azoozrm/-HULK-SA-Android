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

personalization = r'''    val historySeedItems = remember(movies, series, state.history) {
        val movieById = movies.associateBy(ContentItem::id)
        val seriesByName = series.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        state.history.asSequence()
            .filterNot(HistoryEntry::isLive)
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
        buildMap<String, Int> {
            historySeedItems.forEachIndexed { index, item ->
                val weight = (24 - index).coerceAtLeast(2)
                put(item.categoryId, (get(item.categoryId) ?: 0) + weight)
            }
            favoriteSeedItems.forEach { item ->
                put(item.categoryId, (get(item.categoryId) ?: 0) + 30)
            }
        }
    }
    val interestGenreWeights = remember(historySeedItems, favoriteSeedItems) {
        buildMap<String, Int> {
            fun add(item: ContentItem, weight: Int) {
                item.genre.orEmpty().split(',', '،', '/', '|')
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .filter(String::isNotBlank)
                    .forEach { genre -> put(genre, (get(genre) ?: 0) + weight) }
            }
            historySeedItems.forEachIndexed { index, item -> add(item, (24 - index).coerceAtLeast(2)) }
            favoriteSeedItems.forEach { add(it, 30) }
        }
    }
    val personalizedRecommended = remember(
        movies,
        series,
        recommended,
        historySeedItems,
        favoriteSeedItems,
        interestCategoryWeights,
        interestGenreWeights,
        state.favorites,
    ) {
        val watchedKeys = historySeedItems.map { "${it.type}:${it.id}" }.toSet()
        val candidates = (movies + series).filterNot { item ->
            isFavorite(item) || "${item.type}:${item.id}" in watchedKeys
        }
        val ranked = candidates.sortedWith(
            compareByDescending<ContentItem> { item ->
                val categoryScore = (interestCategoryWeights[item.categoryId] ?: 0) * 100
                val genreScore = item.genre.orEmpty().split(',', '،', '/', '|')
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .filter(String::isNotBlank)
                    .sumOf { genre -> (interestGenreWeights[genre] ?: 0) * 28 }
                categoryScore + genreScore
            }
                .thenByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                .thenByDescending { it.addedAtEpochSeconds ?: 0L },
        )
        val matched = ranked.filter { item ->
            (interestCategoryWeights[item.categoryId] ?: 0) > 0 ||
                item.genre.orEmpty().split(',', '،', '/', '|')
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .any { (interestGenreWeights[it] ?: 0) > 0 }
        }
        (matched + recommended + ranked)
            .distinctBy { "${it.type}:${it.id}" }
            .take(24)
    }
    val personalizedLive = remember(live, state.history, state.favorites) {
        val liveById = live.associateBy(ContentItem::id)
        val viewed = state.history.asSequence().filter(HistoryEntry::isLive)
            .mapNotNull { liveById[it.streamId] }
            .take(30)
            .toList()
        val categoryWeights = buildMap<String, Int> {
            viewed.forEachIndexed { index, item ->
                val weight = (30 - index).coerceAtLeast(1)
                put(item.categoryId, (get(item.categoryId) ?: 0) + weight)
            }
            live.filter(isFavorite).forEach { item ->
                put(item.categoryId, (get(item.categoryId) ?: 0) + 35)
            }
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

featured_anchor = '    val featuredCandidates = remember(movies, series) {'
if featured_anchor not in home:
    raise SystemExit('v0916 featured candidates anchor missing')
home = home.replace(featured_anchor, personalization + featured_anchor, 1)

home, count = re.subn(
    r'val recommendedRow = if \(recommended\.isNotEmpty\(\)\)',
    'val recommendedRow = if (personalizedRecommended.isNotEmpty())',
    home,
    count=1,
)
if count != 1:
    raise SystemExit(f'v0916 recommended row anchor mismatch: {count}')

render_pattern = re.compile(
    r'if \(recommended\.isNotEmpty\(\)\) \{\s*item \{ HomeSectionPadding \{ PosterSection\("مقترح لك", "recommended", recommendedRow, [^,]+,',
    re.S,
)
home, count = render_pattern.subn(
    'if (personalizedRecommended.isNotEmpty()) {\n            item { HomeSectionPadding { PosterSection("مقترح لك", "recommended", recommendedRow, personalizedRecommended,',
    home,
    count=1,
)
if count != 1:
    raise SystemExit(f'v0916 recommended rendering anchor mismatch: {count}')

home = home.replace('live.take(20)', 'personalizedLive.take(20)')
home = home.replace('"قنوات شايعة"', '"قنوات مقترحة لك"')
home = home.replace('"قنوات شائعة"', '"قنوات مقترحة لك"')

if 'personalizedLive.take(20)' not in home:
    raise SystemExit('v0916 personalized live rendering missing')
if 'personalizedRecommended' not in home:
    raise SystemExit('v0916 personalized recommendations missing')

M = M[:home_start] + home + M[home_end:]

if 'LaunchedEffect(remembered.rowKey, featured.id)' in M:
    raise SystemExit('v0916 hero still depends on featured id')
if 'versionName = "0.9.1.6"' not in G:
    raise SystemExit('v0916 version update failed')

main.write_text(M)
gradle.write_text(G)
