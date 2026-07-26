#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path, old, new, label):
    p=root/path
    s=p.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'missing {label}')
    p.write_text(s.replace(old,new,1),encoding='utf-8')

rep('app/build.gradle.kts','versionCode = 53','versionCode = 54','versionCode')
rep('app/build.gradle.kts','versionName = "0.9.3.9"','versionName = "0.9.3.10"','versionName')

main='app/src/main/java/sa/hulksa/player/MainActivity.kt'
rep(main,
'''import android.content.Context
''',
'''import android.content.Context
import android.content.Intent
''','intent import')
rep(main,
'''        val isTelevisionDevice = isTelevisionDevice()
        if (isTelevisionDevice) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            enterImmersiveMode()
        }

        setContent {
''',
'''        val isTelevisionDevice = isTelevisionDevice()
        if (isTelevisionDevice) {
            startActivity(Intent(this, TvMainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
            return
        }

        setContent {
''','installer tv redirect')

vm='app/src/main/java/sa/hulksa/player/HulkViewModel.kt'
rep(vm,
'''import android.app.Application
''',
'''import android.app.Application
import android.os.SystemClock
''','system clock import')
rep(vm,
'''class HulkViewModel(application: Application) : AndroidViewModel(application) {
''',
'''class HulkViewModel(application: Application) : AndroidViewModel(application) {
    private var lastFavoriteToggleAtMs: Long = 0L
''','favorite debounce field')
rep(vm,
'''    fun toggleFavorite(item: ContentItem) {
        mutableState.update { it.copy(favorites = userLibrary.toggle(item)) }
    }
''',
'''    fun toggleFavorite(item: ContentItem) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFavoriteToggleAtMs < 700L) return
        lastFavoriteToggleAtMs = now
        val key = userLibrary.keyFor(item)
        val current = mutableState.value.favorites
        val updated = current.toMutableSet().apply {
            if (!add(key)) remove(key)
        }.toSet()
        userLibrary.replaceFavorites(updated)
        mutableState.update { it.copy(favorites = updated) }
    }
''','single favorite toggle')

library='app/src/main/java/sa/hulksa/player/data/UserLibrary.kt'
rep(library,
'''    fun isFavorite(item: ContentItem, favorites: Set<String>): Boolean = keyFor(item) in favorites
''',
'''    fun replaceFavorites(favorites: Set<String>) {
        preferences.edit().putStringSet(KEY_FAVORITES, favorites.toSet()).commit()
    }

    fun isFavorite(item: ContentItem, favorites: Set<String>): Boolean = keyFor(item) in favorites
''','replace favorites')

print('Prepared v0.9.3.10 installer launch and favorites fix')
