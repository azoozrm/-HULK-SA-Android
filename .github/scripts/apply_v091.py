from pathlib import Path
import re

root = Path("project")

# Version
p = root / "app/build.gradle.kts"
s = p.read_text(encoding="utf-8")
s = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 91', s)
s = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.9.1"', s)
p.write_text(s, encoding="utf-8")

# Home hero: rotate newest items and exclude the active hero from rows.
p = root / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
s = p.read_text(encoding="utf-8")
start = s.index('    val movies = remember(state.catalogs[ContentType.MOVIE])')
end = s.index('    val continueWatching =', start)
replacement = '''    val movies = remember(state.catalogs[ContentType.MOVIE]) { newest(state.catalogs[ContentType.MOVIE]?.items.orEmpty()) }
    val series = remember(state.catalogs[ContentType.SERIES]) { newest(state.catalogs[ContentType.SERIES]?.items.orEmpty()) }
    val featuredCandidates = remember(movies, series) {
        (movies + series)
            .filter { !it.backdropUrl.isNullOrBlank() || !it.posterUrl.isNullOrBlank() }
            .distinctBy { "${it.type}:${it.id}" }
            .take(8)
    }
    var featuredIndex by remember(featuredCandidates) { mutableIntStateOf(0) }
    LaunchedEffect(featuredCandidates) {
        while (featuredCandidates.size > 1) {
            delay(9_000L)
            featuredIndex = (featuredIndex + 1) % featuredCandidates.size
        }
    }
    val featured = featuredCandidates.getOrNull(featuredIndex)
        ?: movies.firstOrNull()
        ?: series.firstOrNull()
    val homeMovies = remember(movies, featured) { movies.filterNot { it.type == featured?.type && it.id == featured.id } }
    val homeSeries = remember(series, featured) { series.filterNot { it.type == featured?.type && it.id == featured.id } }
'''
s = s[:start] + replacement + s[end:]
s = s.replace('Text("أحدث إضافات HULK SA"', 'Text("احدث اضافات HULK"')
s = s.replace('Text("أحدث إضافات HULK"', 'Text("احدث اضافات HULK"')
s = s.replace('Text("احدث اضافات HULK SA"', 'Text("احدث اضافات HULK"')
# Make rows use lists that exclude the active hero.
s = re.sub(r'items\s*=\s*movies\b', 'items = homeMovies', s)
s = re.sub(r'items\s*=\s*series\b', 'items = homeSeries', s)
p.write_text(s, encoding="utf-8")

# Player: right advances and left rewinds by 10 seconds.
p = root / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"
s = p.read_text(encoding="utf-8")
right_pattern = re.compile(
    r'(AndroidKeyEvent\.KEYCODE_DPAD_RIGHT,\s*\n\s*AndroidKeyEvent\.KEYCODE_MEDIA_FAST_FORWARD,\s*\n\s*->\s*\{.*?player\.seekTo\()([^\n]+)(\)\s*\n)',
    re.S,
)
s = right_pattern.sub(r'\1(player.currentPosition + 10_000L).coerceAtMost(duration)\3', s, count=1)
left_pattern = re.compile(
    r'(AndroidKeyEvent\.KEYCODE_DPAD_LEFT,\s*\n\s*AndroidKeyEvent\.KEYCODE_MEDIA_REWIND,\s*\n\s*->\s*\{\s*\n\s*player\.seekTo\()([^\n]+)(\)\s*\n)',
    re.S,
)
s = left_pattern.sub(r'\1(player.currentPosition - 10_000L).coerceAtLeast(0L)\3', s, count=1)
p.write_text(s, encoding="utf-8")

print("Applied HULK SA v0.9.1 patch")
