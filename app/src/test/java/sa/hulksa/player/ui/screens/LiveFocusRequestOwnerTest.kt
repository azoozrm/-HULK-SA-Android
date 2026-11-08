package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFocusRequestOwnerTest {
    private fun decision(
        owner: LiveFocusRequestOwner,
        exactItemCount: Int = 5,
        selectedCategoryId: String? = owner.categoryRequest?.categoryId,
        liveLoading: Boolean = false,
        categoryBarFocused: Boolean = false,
    ): LiveFocusDecision = decideLiveFocusDecision(
        exactAvailable = true,
        exactItemCount = exactItemCount,
        categoryRequest = owner.categoryRequest,
        selectedCategoryId = selectedCategoryId,
        searchQuery = "",
        rememberedItemKey = "LIVE:4",
        rememberedIndex = 4,
        liveLoading = liveLoading,
        restorationPending = owner.focusRestorationPending,
        categoryBarFocused = categoryBarFocused,
    )

    @Test
    fun `toolbar preview or channel departure cancels pending entry restoration`() {
        repeat(3) {
            val owner = LiveFocusRequestOwner()
            assertTrue(owner.focusRestorationPending)

            owner.cancelForUserDeparture()

            assertFalse(owner.focusRestorationPending)
            assertEquals(LiveFocusDecision.None, decision(owner))
        }
    }

    @Test
    fun `named category chip focus cancels pending entry restoration`() {
        val owner = LiveFocusRequestOwner()
        assertTrue(owner.focusRestorationPending)

        owner.onCategoryFocusChanged("sport")

        assertFalse(owner.focusRestorationPending)
        assertEquals(LiveFocusDecision.None, decision(owner))
    }

    @Test
    fun `initial all chip focus does not cancel entry restoration`() {
        val owner = LiveFocusRequestOwner()
        assertTrue(owner.focusRestorationPending)

        owner.onCategoryFocusChanged(null)

        assertTrue(owner.focusRestorationPending)
        assertEquals(
            LiveFocusDecision.Focus(index = 4, requestId = null),
            decision(owner),
        )
    }

    @Test
    fun `category request is cancelled by deliberate departure before its result`() {
        val owner = LiveFocusRequestOwner()
        owner.requestCategory(
            categoryId = "sport",
            focusFirstItem = true,
            activatingChipFocused = true,
        )
        assertNotNull(owner.categoryRequest)

        owner.cancelForUserDeparture()

        assertNull(owner.categoryRequest)
        assertFalse(owner.focusRestorationPending)
        assertEquals(LiveFocusDecision.None, decision(owner, selectedCategoryId = "sport"))
    }

    @Test
    fun `held layout attempt cannot focus after departure`() {
        val owner = LiveFocusRequestOwner()
        val request = owner.requestCategory(
            categoryId = "sport",
            focusFirstItem = true,
            activatingChipFocused = true,
        )
        val heldToken = owner.attemptToken()

        owner.cancelForUserDeparture()
        assertFalse(owner.isCurrent(heldToken))

        // The suspended attempt resumes after the user already moved elsewhere.
        owner.completeFocus(heldToken, request.requestId, focused = true)

        assertNull(owner.categoryRequest)
        assertFalse(owner.focusRestorationPending)
        assertEquals(LiveFocusDecision.None, decision(owner, selectedCategoryId = "sport"))
    }

    @Test
    fun `held entry restoration cannot focus after departure`() {
        val owner = LiveFocusRequestOwner()
        val heldToken = owner.attemptToken()

        owner.cancelForUserDeparture()
        owner.completeFocus(heldToken, requestId = null, focused = true)

        assertFalse(owner.focusRestorationPending)
        assertEquals(LiveFocusDecision.None, decision(owner))
    }

    @Test
    fun `exact category result hands off once without departure`() {
        val owner = LiveFocusRequestOwner()
        val request = owner.requestCategory(
            categoryId = "sport",
            focusFirstItem = true,
            activatingChipFocused = true,
        )
        val token = owner.attemptToken()

        // The activating chip remaining focused is a valid initial confirmation, not departure.
        owner.onCategoryFocusChanged("sport")
        assertNotNull(owner.categoryRequest)
        assertEquals(
            LiveFocusDecision.Focus(index = 0, requestId = request.requestId),
            decision(owner, selectedCategoryId = "sport"),
        )

        owner.completeFocus(token, request.requestId, focused = true)

        assertNull(owner.categoryRequest)
        assertFalse(owner.focusRestorationPending)
        assertEquals(LiveFocusDecision.None, decision(owner, selectedCategoryId = "sport"))
    }

    @Test
    fun `activating chip confirmation survives until a different chip is focused`() {
        val owner = LiveFocusRequestOwner()
        owner.requestCategory(
            categoryId = "sport",
            focusFirstItem = true,
            activatingChipFocused = false,
        )

        owner.onCategoryFocusChanged(null)
        assertNotNull(owner.categoryRequest)

        owner.onCategoryFocusChanged("sport")
        assertNotNull(owner.categoryRequest)
        assertTrue(owner.focusRestorationPending)

        owner.onCategoryFocusChanged("news")
        assertNull(owner.categoryRequest)
        assertEquals(LiveFocusDecision.None, decision(owner, selectedCategoryId = "sport"))
    }

    @Test
    fun `moving to the all chip after confirmation cancels the handoff`() {
        val owner = LiveFocusRequestOwner()
        owner.requestCategory(
            categoryId = "sport",
            focusFirstItem = true,
            activatingChipFocused = true,
        )

        owner.onCategoryFocusChanged(null)

        assertNull(owner.categoryRequest)
        assertEquals(LiveFocusDecision.None, decision(owner, selectedCategoryId = "sport"))
    }

    @Test
    fun `exact empty request completes through the owner without focusing`() {
        val owner = LiveFocusRequestOwner()
        val request = owner.requestCategory(
            categoryId = "news",
            focusFirstItem = true,
            activatingChipFocused = true,
        )

        assertEquals(
            LiveFocusDecision.None,
            decision(owner, exactItemCount = 0, selectedCategoryId = "news", liveLoading = true),
        )
        val settled = decision(
            owner,
            exactItemCount = 0,
            selectedCategoryId = "news",
            liveLoading = false,
        )
        assertEquals(LiveFocusDecision.CompleteRequest(request.requestId), settled)

        owner.completeEmptyRequest(request.requestId)

        assertNull(owner.categoryRequest)
        assertFalse(owner.focusRestorationPending)
        assertEquals(
            LiveFocusDecision.None,
            decision(owner, exactItemCount = 0, selectedCategoryId = "news"),
        )
    }

    @Test
    fun `stale empty completion cannot clear a newer request`() {
        val owner = LiveFocusRequestOwner()
        val request = owner.requestCategory(
            categoryId = "news",
            focusFirstItem = true,
            activatingChipFocused = true,
        )

        owner.completeEmptyRequest(request.requestId + 1L)

        assertEquals(request, owner.categoryRequest)
        assertTrue(owner.focusRestorationPending)
    }

    @Test
    fun `stale attempt completion cannot clear a newer request`() {
        val owner = LiveFocusRequestOwner()
        val oldRequest = owner.requestCategory(
            categoryId = "sport",
            focusFirstItem = true,
            activatingChipFocused = true,
        )
        val oldToken = owner.attemptToken()
        owner.cancelForUserDeparture()
        val newRequest = owner.requestCategory(
            categoryId = "news",
            focusFirstItem = true,
            activatingChipFocused = true,
        )

        owner.completeFocus(oldToken, oldRequest.requestId, focused = true)

        assertEquals(newRequest, owner.categoryRequest)
        assertTrue(owner.focusRestorationPending)
    }

    @Test
    fun `failed category focus keeps the request for a later exact projection`() {
        val owner = LiveFocusRequestOwner()
        val request = owner.requestCategory(
            categoryId = "sport",
            focusFirstItem = true,
            activatingChipFocused = true,
        )
        val token = owner.attemptToken()

        owner.completeFocus(token, request.requestId, focused = false)

        assertEquals(request, owner.categoryRequest)
        assertTrue(owner.focusRestorationPending)
        assertEquals(
            LiveFocusDecision.Focus(index = 0, requestId = request.requestId),
            decision(owner, selectedCategoryId = "sport"),
        )
    }

    @Test
    fun `entry restoration still focuses the remembered channel once`() {
        val owner = LiveFocusRequestOwner()
        val token = owner.attemptToken()

        assertEquals(
            LiveFocusDecision.Focus(index = 4, requestId = null),
            decision(owner),
        )

        owner.completeFocus(token, requestId = null, focused = true)

        assertFalse(owner.focusRestorationPending)
        assertEquals(LiveFocusDecision.None, decision(owner))
    }
}
