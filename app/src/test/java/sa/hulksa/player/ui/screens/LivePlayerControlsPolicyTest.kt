package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class LivePlayerControlsPolicyTest {
    @Test
    fun `more panel back closes from menu and returns children to the originating row`() {
        assertNull(playerLiveMorePanelBack(PlayerLiveMorePanelView.MENU))
        assertEquals(
            PlayerLiveMorePanelView.MENU,
            playerLiveMorePanelBack(PlayerLiveMorePanelView.SOURCE),
        )
        assertEquals(
            PlayerLiveMorePanelView.MENU,
            playerLiveMorePanelBack(PlayerLiveMorePanelView.RESIZE),
        )

        assertNull(playerLiveMorePanelOriginRow(PlayerLiveMorePanelView.MENU))
        assertEquals(
            PlayerLiveMoreRow.SOURCE,
            playerLiveMorePanelOriginRow(PlayerLiveMorePanelView.SOURCE),
        )
        assertEquals(
            PlayerLiveMoreRow.RESIZE,
            playerLiveMorePanelOriginRow(PlayerLiveMorePanelView.RESIZE),
        )
    }

    @Test
    fun `more panel participates in child panel input ownership`() {
        assertFalse(playerChildPanelInputActive(hasActivePanel = false, hasLiveMorePanel = false))
        assertTrue(playerChildPanelInputActive(hasActivePanel = true, hasLiveMorePanel = false))
        assertTrue(playerChildPanelInputActive(hasActivePanel = false, hasLiveMorePanel = true))
        assertTrue(playerChildPanelInputActive(hasActivePanel = true, hasLiveMorePanel = true))
    }

    @Test
    fun `favorite control is disabled without the authoritative catalog channel`() {
        val channel = channel(id = 7)

        val enabled = livePlayerFavoriteControl(channel, isFavorite = true)
        assertTrue(enabled.enabled)
        assertTrue(enabled.favorite)

        val missing = livePlayerFavoriteControl(null, isFavorite = true)
        assertFalse(missing.enabled)
        assertFalse(missing.favorite)
    }

    @Test
    fun `last channel action stays inside the live pro gate and requires a target`() {
        assertTrue(
            playerProLiveLastChannelActionEnabled(
                isLive = true,
                liveTvProEnabled = true,
                hasLastChannel = true,
            ),
        )
        assertFalse(
            playerProLiveLastChannelActionEnabled(
                isLive = true,
                liveTvProEnabled = true,
                hasLastChannel = false,
            ),
        )
        assertFalse(
            playerProLiveLastChannelActionEnabled(
                isLive = false,
                liveTvProEnabled = true,
                hasLastChannel = true,
            ),
        )
        assertFalse(
            playerProLiveLastChannelActionEnabled(
                isLive = true,
                liveTvProEnabled = false,
                hasLastChannel = true,
            ),
        )
    }

    private fun channel(id: Int) = ContentItem(
        id = id,
        name = "Channel $id",
        categoryId = "news",
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "ts",
    )
}
