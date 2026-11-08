package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFocusRestorationPolicyTest {
    @Test
    fun `unrelated loading changes never focus when the user already moved elsewhere`() {
        val decisions = listOf(false, true, false, true).map { loading ->
            decideLiveFocusDecision(
                exactAvailable = true,
                exactItemCount = 8,
                categoryRequest = null,
                selectedCategoryId = null,
                searchQuery = "",
                rememberedItemKey = "LIVE:4",
                rememberedIndex = 4,
                liveLoading = loading,
                restorationPending = false,
                categoryBarFocused = false,
            )
        }

        assertTrue(decisions.all { it == LiveFocusDecision.None })
    }

    @Test
    fun `exact empty request completes when LIVE loading settles without focus`() {
        val request = CategoryContentFocusRequest(
            categoryId = "news",
            requestId = 42L,
            focusFirstItem = true,
        )
        val loading = decideLiveFocusDecision(
            exactAvailable = true,
            exactItemCount = 0,
            categoryRequest = request,
            selectedCategoryId = "news",
            searchQuery = "",
            rememberedItemKey = "LIVE:4",
            rememberedIndex = 4,
            liveLoading = true,
            restorationPending = true,
            categoryBarFocused = false,
        )
        val settled = decideLiveFocusDecision(
            exactAvailable = true,
            exactItemCount = 0,
            categoryRequest = request,
            selectedCategoryId = "news",
            searchQuery = "",
            rememberedItemKey = "LIVE:4",
            rememberedIndex = 4,
            liveLoading = false,
            restorationPending = true,
            categoryBarFocused = false,
        )

        assertEquals(LiveFocusDecision.None, loading)
        assertEquals(LiveFocusDecision.CompleteRequest(42L), settled)
    }

    @Test
    fun `explicit category handoff waits for exact result and runs once`() {
        val request = CategoryContentFocusRequest(
            categoryId = "sport",
            requestId = 7L,
            focusFirstItem = true,
        )
        val waiting = decideLiveFocusDecision(
            exactAvailable = false,
            exactItemCount = 0,
            categoryRequest = request,
            selectedCategoryId = "sport",
            searchQuery = "",
            rememberedItemKey = "LIVE:4",
            rememberedIndex = 4,
            liveLoading = false,
            restorationPending = false,
            categoryBarFocused = true,
        )
        val ready = decideLiveFocusDecision(
            exactAvailable = true,
            exactItemCount = 3,
            categoryRequest = request,
            selectedCategoryId = "sport",
            searchQuery = "",
            rememberedItemKey = "LIVE:4",
            rememberedIndex = 4,
            liveLoading = false,
            restorationPending = true,
            categoryBarFocused = true,
        )
        val afterHandoff = decideLiveFocusDecision(
            exactAvailable = true,
            exactItemCount = 3,
            categoryRequest = null,
            selectedCategoryId = "sport",
            searchQuery = "",
            rememberedItemKey = "LIVE:4",
            rememberedIndex = 4,
            liveLoading = true,
            restorationPending = false,
            categoryBarFocused = true,
        )

        assertEquals(LiveFocusDecision.None, waiting)
        assertEquals(LiveFocusDecision.Focus(index = 0, requestId = 7L), ready)
        assertEquals(LiveFocusDecision.None, afterHandoff)
    }

    @Test
    fun `delayed exact completion cannot override deliberate user movement`() {
        val waiting = decideLiveFocusDecision(
            exactAvailable = false,
            exactItemCount = 0,
            categoryRequest = null,
            selectedCategoryId = null,
            searchQuery = "",
            rememberedItemKey = "LIVE:4",
            rememberedIndex = 4,
            liveLoading = true,
            restorationPending = true,
            categoryBarFocused = false,
        )
        val userMoved = decideLiveFocusDecision(
            exactAvailable = true,
            exactItemCount = 8,
            categoryRequest = null,
            selectedCategoryId = null,
            searchQuery = "",
            rememberedItemKey = "LIVE:4",
            rememberedIndex = 4,
            liveLoading = true,
            restorationPending = false,
            categoryBarFocused = false,
        )

        assertEquals(LiveFocusDecision.None, waiting)
        assertEquals(LiveFocusDecision.None, userMoved)
    }

    @Test
    fun `entry restoration still focuses the remembered channel once`() {
        val first = decideLiveFocusDecision(
            exactAvailable = true,
            exactItemCount = 8,
            categoryRequest = null,
            selectedCategoryId = null,
            searchQuery = "",
            rememberedItemKey = "LIVE:4",
            rememberedIndex = 4,
            liveLoading = true,
            restorationPending = true,
            categoryBarFocused = false,
        )
        val afterRestore = decideLiveFocusDecision(
            exactAvailable = true,
            exactItemCount = 8,
            categoryRequest = null,
            selectedCategoryId = null,
            searchQuery = "",
            rememberedItemKey = "LIVE:4",
            rememberedIndex = 4,
            liveLoading = false,
            restorationPending = false,
            categoryBarFocused = false,
        )

        assertEquals(LiveFocusDecision.Focus(index = 4, requestId = null), first)
        assertEquals(LiveFocusDecision.None, afterRestore)
    }

    @Test
    fun `category bar focus suppresses the remembered-item fallback`() {
        val decision = decideLiveFocusDecision(
            exactAvailable = true,
            exactItemCount = 8,
            categoryRequest = null,
            selectedCategoryId = null,
            searchQuery = "",
            rememberedItemKey = "LIVE:4",
            rememberedIndex = 4,
            liveLoading = false,
            restorationPending = true,
            categoryBarFocused = true,
        )

        assertEquals(LiveFocusDecision.None, decision)
    }

    @Test
    fun `exact empty without a matching request never focuses`() {
        val decision = decideLiveFocusDecision(
            exactAvailable = true,
            exactItemCount = 0,
            categoryRequest = null,
            selectedCategoryId = "news",
            searchQuery = "",
            rememberedItemKey = "LIVE:4",
            rememberedIndex = 4,
            liveLoading = false,
            restorationPending = true,
            categoryBarFocused = false,
        )

        assertEquals(LiveFocusDecision.None, decision)
    }
}
