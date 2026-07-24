#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def rw(rel, fn):
    p = root / rel
    text = p.read_text()
    new = fn(text)
    if new == text:
        raise SystemExit(f'No change applied to {rel}')
    p.write_text(new)


rw(
    'app/build.gradle.kts',
    lambda t: t.replace('versionCode = 35', 'versionCode = 36')
               .replace('versionName = "0.9.1.13"', 'versionName = "0.9.1.14"')
)


def library(t):
    return t.replace('const val MAX_HISTORY = 40', 'const val MAX_HISTORY = 100')


rw('app/src/main/java/sa/hulksa/player/data/UserLibrary.kt', library)


def shell(t):
    # Use the same resume eligibility rule in every Continue Watching surface.
    t = t.replace(
        '!entry.isLive && entry.positionMs > 0L &&\n                (entry.durationMs <= 0L || entry.positionMs.toDouble() / entry.durationMs < .92)',
        'entry.isResumable()'
    )
    t = t.replace(
        '!entry.isLive && entry.streamKind == kind && entry.positionMs > 0L &&\n                (state.searchQuery.isBlank() || entry.title.contains(state.searchQuery.trim(), ignoreCase = true))',
        'entry.streamKind == kind && entry.isResumable() &&\n                (state.searchQuery.isBlank() || entry.title.contains(state.searchQuery.trim(), ignoreCase = true))'
    )

    # Upgrade all catalog/search matching from title-only to useful metadata fields.
    t = t.replace(
        '(state.searchQuery.isBlank() || item.name.contains(state.searchQuery.trim(), ignoreCase = true))',
        'item.matchesSearch(state.searchQuery)'
    )
    t = t.replace(
        '.filter { it.name.contains(query, ignoreCase = true) }',
        '.filter { it.matchesSearch(query) }'
    )
    t = t.replace('اكتب اسم المحتوى…', 'ابحث بالاسم او السنة او النوع…')
    t = t.replace('ابدا بكتابة اسم القناة او الفيلم او المسلسل', 'ابدا بكتابة الاسم او السنة او النوع او وصف المحتوى')

    marker = 'private fun newest(content: List<ContentItem>): List<ContentItem> =\n    content.sortedByDescending { it.addedAtEpochSeconds ?: 0L }\n'
    helpers = '''private fun newest(content: List<ContentItem>): List<ContentItem> =
    content.sortedByDescending { it.addedAtEpochSeconds ?: 0L }

private fun ContentItem.matchesSearch(rawQuery: String): Boolean {
    val query = rawQuery.trim()
    if (query.isBlank()) return true
    return sequenceOf(name, year, genre, plot, nowPlaying)
        .filterNotNull()
        .any { value -> value.contains(query, ignoreCase = true) }
}

private fun HistoryEntry.isResumable(): Boolean =
    !isLive && positionMs > 0L &&
        (durationMs <= 0L || positionMs.toDouble() / durationMs < .92)
'''
    if marker not in t:
        raise SystemExit('newest helper marker not found')
    t = t.replace(marker, helpers)
    return t


rw('app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt', shell)
print('Applied v0.9.1.14 phase one stabilization: consistent resume, richer search, larger history')
