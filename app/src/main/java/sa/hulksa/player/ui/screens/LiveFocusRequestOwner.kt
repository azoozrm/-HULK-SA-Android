package sa.hulksa.player.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Single Live focus-request owner. A deliberate departure invalidates both entry restoration and
 * an explicit category handoff, including an attempt already waiting for row layout/frame.
 * The activating category chip may remain focused; only moving to another chip or leaving the
 * category/toolbar/preview/list owner cancels.
 */
internal class LiveFocusRequestOwner(initialEntryRestorePending: Boolean = true) {
    var focusRestorationPending by mutableStateOf(initialEntryRestorePending)
        private set

    var categoryRequest by mutableStateOf<CategoryContentFocusRequest?>(null)
        private set

    private var revision = 0L
    private var nextRequestId = 0L
    private var categoryActivationConfirmed = false

    fun attemptToken(): Long = revision

    fun isCurrent(token: Long): Boolean = revision == token

    fun requestCategory(
        categoryId: String?,
        focusFirstItem: Boolean,
        activatingChipFocused: Boolean,
    ): CategoryContentFocusRequest {
        revision += 1L
        focusRestorationPending = true
        categoryActivationConfirmed = activatingChipFocused
        return CategoryContentFocusRequest(
            categoryId = categoryId,
            requestId = ++nextRequestId,
            focusFirstItem = focusFirstItem,
        ).also { categoryRequest = it }
    }

    /**
     * The activating chip is allowed to stay focused while its category result arrives. Once the
     * activation is confirmed, any focus movement to a different chip (including the "all" chip)
     * is a deliberate departure. Before confirmation, only a different named chip is treated as
     * departure so the request survives until its activating chip receives focus.
     */
    fun onCategoryFocusChanged(focusedCategoryId: String?) {
        val request = categoryRequest
        if (request == null) {
            // A named category chip gaining focus is a deliberate departure from content
            // restoration. The all chip (null) is the initial entry target and must not cancel.
            if (focusedCategoryId != null && focusRestorationPending) {
                cancelForUserDeparture()
            }
            return
        }
        if (!categoryActivationConfirmed) {
            when {
                focusedCategoryId == request.categoryId -> categoryActivationConfirmed = true
                focusedCategoryId != null -> cancelForUserDeparture()
            }
            return
        }
        if (focusedCategoryId != request.categoryId) {
            cancelForUserDeparture()
        }
    }

    fun cancelForUserDeparture() {
        revision += 1L
        focusRestorationPending = false
        categoryRequest = null
        categoryActivationConfirmed = false
    }

    /**
     * Consumes a completed nonempty focus attempt; stale attempts are ignored. A failed explicit
     * category handoff keeps its request and restoration intent so a later exact projection can
     * retry, while a completed entry restoration is consumed either way.
     */
    fun completeFocus(attemptToken: Long, requestId: Long?, focused: Boolean) {
        if (!isCurrent(attemptToken)) return
        if (requestId != null && !focused) return
        revision += 1L
        focusRestorationPending = false
        if (requestId != null && categoryRequest?.requestId == requestId) {
            categoryRequest = null
        }
        categoryActivationConfirmed = false
    }

    /** Consumes an exact-empty matching request without focusing. */
    fun completeEmptyRequest(requestId: Long) {
        if (categoryRequest?.requestId != requestId) return
        revision += 1L
        focusRestorationPending = false
        categoryRequest = null
        categoryActivationConfirmed = false
    }
}
