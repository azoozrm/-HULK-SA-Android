package sa.hulksa.player.ui.screens

/**
 * Resolves the exact selected category position inside a lazy category strip/pane.
 * Leading entries are special categories (for example All, Favorites, Continue),
 * followed by the server categories in their current user-defined order.
 */
internal fun selectedCategoryFocusIndex(
    selectedId: String?,
    leadingIds: List<String?>,
    orderedIds: List<String>,
): Int? {
    val leadingIndex = leadingIds.indexOf(selectedId)
    if (leadingIndex >= 0) return leadingIndex

    val categoryId = selectedId ?: return null
    val orderedIndex = orderedIds.indexOf(categoryId)
    return orderedIndex.takeIf { it >= 0 }?.plus(leadingIds.size)
}
