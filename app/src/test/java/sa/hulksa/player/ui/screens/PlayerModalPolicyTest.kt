package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.playback.RecoveryFailureClass

class PlayerModalPolicyTest {
    @Test
    fun `audio final error never offers source picker`() {
        assertFalse(
            canOfferPlayerErrorSourcePicker(
                failureClass = RecoveryFailureClass.AUDIO,
                candidateCount = 2,
            ),
        )
    }

    @Test
    fun `source final error offers source picker only when alternate exists`() {
        assertFalse(
            canOfferPlayerErrorSourcePicker(
                failureClass = RecoveryFailureClass.SOURCE,
                candidateCount = 1,
            ),
        )
        assertTrue(
            canOfferPlayerErrorSourcePicker(
                failureClass = RecoveryFailureClass.SOURCE,
                candidateCount = 2,
            ),
        )
    }

    @Test
    fun `retry is always the initial error modal action`() {
        assertEquals(
            PlayerErrorModalAction.RETRY,
            playerErrorModalActions(
                canChooseChannel = true,
                canChooseSource = true,
            ).first(),
        )
    }

    @Test
    fun `error source action only exists for source-class final failure`() {
        val audioActions = playerErrorModalActions(
            canChooseChannel = true,
            canChooseSource = canOfferPlayerErrorSourcePicker(
                RecoveryFailureClass.AUDIO,
                candidateCount = 2,
            ),
        )
        val sourceActions = playerErrorModalActions(
            canChooseChannel = true,
            canChooseSource = canOfferPlayerErrorSourcePicker(
                RecoveryFailureClass.SOURCE,
                candidateCount = 2,
            ),
        )

        assertFalse(PlayerErrorModalAction.CHOOSE_SOURCE in audioActions)
        assertTrue(PlayerErrorModalAction.CHOOSE_SOURCE in sourceActions)
    }

    @Test
    fun `up down channel and player commands are consumed by error modal`() {
        listOf(
            PlayerErrorModalInput.UP,
            PlayerErrorModalInput.DOWN,
            PlayerErrorModalInput.CHANNEL_UP,
            PlayerErrorModalInput.CHANNEL_DOWN,
            PlayerErrorModalInput.PLAYER_COMMAND,
        ).forEach { input ->
            assertEquals(
                PlayerErrorModalInputDisposition.CONSUME,
                playerErrorModalInputDisposition(input),
            )
        }
    }

    @Test
    fun `left right and select remain owned by focused modal actions`() {
        listOf(
            PlayerErrorModalInput.LEFT,
            PlayerErrorModalInput.RIGHT,
            PlayerErrorModalInput.SELECT,
        ).forEach { input ->
            assertEquals(
                PlayerErrorModalInputDisposition.PASS_TO_MODAL_ACTION,
                playerErrorModalInputDisposition(input),
            )
        }
    }

    @Test
    fun `back is owned exclusively by error modal policy`() {
        assertEquals(
            PlayerErrorModalInputDisposition.HANDLE_BACK,
            playerErrorModalInputDisposition(PlayerErrorModalInput.BACK),
        )
    }
}
