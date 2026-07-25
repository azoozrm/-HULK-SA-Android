#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def rw(rel: str, transform) -> None:
    path = root / rel
    text = path.read_text()
    updated = transform(text)
    if updated == text:
        raise SystemExit(f"No change applied to {rel}")
    path.write_text(updated)


def update_gradle(text: str) -> str:
    text = text.replace('versionCode = 36', 'versionCode = 41', 1)
    text = text.replace('versionName = "0.9.1.14"', 'versionName = "0.9.1.19"', 1)
    return text


rw('app/build.gradle.kts', update_gradle)


def update_view_model(text: str) -> str:
    fields_anchor = '''    private var diagnosticsJob: Job? = null
    private var playerReturnScreen = HulkScreen.MAIN
'''
    fields_replacement = '''    private var diagnosticsJob: Job? = null
    private var playerReturnScreen = HulkScreen.MAIN
    private val selectedCategoryByDestination = mutableMapOf<MainDestination, String?>()
    private val searchQueryByDestination = mutableMapOf<MainDestination, String>()
'''
    if fields_anchor not in text:
        raise SystemExit('HulkViewModel state-field anchor missing')
    text = text.replace(fields_anchor, fields_replacement, 1)

    text = text.replace(
        '                delay(if (hasActive) 1_250L else 5_000L)',
        '                delay(if (hasActive) 2_000L else 8_000L)',
        1,
    )

    old_destination = '''    fun selectDestination(destination: MainDestination) {
        val selectedType = when (destination) {
            MainDestination.LIVE -> ContentType.LIVE
            MainDestination.SERIES -> ContentType.SERIES
            else -> ContentType.MOVIE
        }
        mutableState.update {
            it.copy(
                destination = destination,
                selectedType = selectedType,
                selectedCategoryId = null,
                searchQuery = "",
                errorMessage = null,
            )
        }
        when (destination) {
            MainDestination.HOME -> {
                ensureCatalog(ContentType.MOVIE)
                ensureCatalog(ContentType.SERIES)
            }
            MainDestination.LIVE -> ensureCatalog(ContentType.LIVE)
            MainDestination.MOVIES -> ensureCatalog(ContentType.MOVIE)
            MainDestination.SERIES -> ensureCatalog(ContentType.SERIES)
            MainDestination.FAVORITES,
            MainDestination.SEARCH,
            -> ContentType.entries.forEach(::ensureCatalog)
            MainDestination.DOWNLOADS,
            MainDestination.SETTINGS -> Unit
        }
    }
'''
    new_destination = '''    fun selectDestination(destination: MainDestination) {
        val current = mutableState.value
        if (destination == current.destination) {
            ensureDestinationCatalogs(destination)
            return
        }

        if (current.destination.remembersCatalogFilters()) {
            selectedCategoryByDestination[current.destination] = current.selectedCategoryId
            searchQueryByDestination[current.destination] = current.searchQuery
        }

        val selectedType = destination.contentType()
        val restoredCategory = if (destination.remembersCatalogFilters()) {
            selectedCategoryByDestination[destination]
        } else {
            null
        }
        val restoredQuery = if (destination.remembersCatalogFilters()) {
            searchQueryByDestination[destination].orEmpty()
        } else {
            ""
        }

        mutableState.update {
            it.copy(
                destination = destination,
                selectedType = selectedType,
                selectedCategoryId = restoredCategory,
                searchQuery = restoredQuery,
                errorMessage = null,
            )
        }
        ensureDestinationCatalogs(destination)
    }

    private fun MainDestination.contentType(): ContentType = when (this) {
        MainDestination.LIVE -> ContentType.LIVE
        MainDestination.SERIES -> ContentType.SERIES
        else -> ContentType.MOVIE
    }

    private fun MainDestination.remembersCatalogFilters(): Boolean =
        this == MainDestination.LIVE || this == MainDestination.MOVIES || this == MainDestination.SERIES

    private fun ensureDestinationCatalogs(destination: MainDestination) {
        when (destination) {
            MainDestination.HOME -> {
                ensureCatalog(ContentType.MOVIE)
                ensureCatalog(ContentType.SERIES)
            }
            MainDestination.LIVE -> ensureCatalog(ContentType.LIVE)
            MainDestination.MOVIES -> ensureCatalog(ContentType.MOVIE)
            MainDestination.SERIES -> ensureCatalog(ContentType.SERIES)
            MainDestination.FAVORITES,
            MainDestination.SEARCH,
            -> ContentType.entries.forEach(::ensureCatalog)
            MainDestination.DOWNLOADS,
            MainDestination.SETTINGS -> Unit
        }
    }
'''
    if old_destination not in text:
        raise SystemExit('selectDestination source block missing')
    text = text.replace(old_destination, new_destination, 1)

    old_category = '''    fun selectCategory(categoryId: String?) {
        mutableState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun updateSearch(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }
'''
    new_category = '''    fun selectCategory(categoryId: String?) {
        val destination = mutableState.value.destination
        if (destination.remembersCatalogFilters()) {
            selectedCategoryByDestination[destination] = categoryId
        }
        mutableState.update { current ->
            if (current.selectedCategoryId == categoryId) current else current.copy(selectedCategoryId = categoryId)
        }
    }

    fun updateSearch(query: String) {
        val destination = mutableState.value.destination
        if (destination.remembersCatalogFilters()) {
            searchQueryByDestination[destination] = query
        }
        mutableState.update { current ->
            if (current.searchQuery == query) current else current.copy(searchQuery = query)
        }
    }
'''
    if old_category not in text:
        raise SystemExit('category/search source block missing')
    text = text.replace(old_category, new_category, 1)

    text = text.replace(
        '''    fun logout() {
        repository.logout()
        session = null
''',
        '''    fun logout() {
        repository.logout()
        session = null
        selectedCategoryByDestination.clear()
        searchQueryByDestination.clear()
''',
        1,
    )
    text = text.replace(
        '''                .onSuccess { authenticated ->
                    session = authenticated
                    mutableState.update {
''',
        '''                .onSuccess { authenticated ->
                    session = authenticated
                    selectedCategoryByDestination.clear()
                    searchQueryByDestination.clear()
                    mutableState.update {
''',
        1,
    )
    return text


rw('app/src/main/java/sa/hulksa/player/HulkViewModel.kt', update_view_model)
print('Applied v0.9.1.19 source navigation-memory and low-churn state fixes')
