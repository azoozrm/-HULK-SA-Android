package sa.hulksa.player.ui.screens

/**
 * One explicit Live focus decision. Empty projections only complete a matching category request;
 * they never request channel focus. Nonempty projections focus only for an explicit category
 * handoff or a still-pending entry restoration.
 */
internal sealed interface LiveFocusDecision {
    data class Focus(val index: Int, val requestId: Long?) : LiveFocusDecision
    data class CompleteRequest(val requestId: Long) : LiveFocusDecision
    data object None : LiveFocusDecision
}

internal fun decideLiveFocusDecision(
    exactAvailable: Boolean,
    exactItemCount: Int,
    categoryRequest: CategoryContentFocusRequest?,
    selectedCategoryId: String?,
    searchQuery: String,
    rememberedItemKey: String,
    rememberedIndex: Int,
    liveLoading: Boolean,
    restorationPending: Boolean,
    categoryBarFocused: Boolean,
): LiveFocusDecision {
    if (!exactAvailable) return LiveFocusDecision.None
    val matchingRequest = categoryRequest?.takeIf { it.categoryId == selectedCategoryId }
    if (exactItemCount == 0) {
        return if (matchingRequest != null && restorationPending && !liveLoading) {
            LiveFocusDecision.CompleteRequest(matchingRequest.requestId)
        } else {
            LiveFocusDecision.None
        }
    }

    val index = if (matchingRequest?.focusFirstItem == true) {
        0
    } else {
        rememberedIndex.coerceIn(0, exactItemCount - 1)
    }
    return when {
        matchingRequest != null && restorationPending ->
            LiveFocusDecision.Focus(index, matchingRequest.requestId)
        matchingRequest != null -> LiveFocusDecision.None
        restorationPending &&
            !categoryBarFocused &&
            searchQuery.isBlank() &&
            rememberedItemKey.isNotBlank() -> LiveFocusDecision.Focus(index, null)
        else -> LiveFocusDecision.None
    }
}
