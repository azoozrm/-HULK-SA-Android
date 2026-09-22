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

    @Test
    fun `error modal active prevents outer live OK ownership`() {
        assertFalse(
            playerLiveProLayerOwnsInput(
                isLive = true,
                liveTvProEnabled = true,
                errorModalInputActive = true,
                browserVisible = false,
            ),
        )
        assertEquals(
            PlayerErrorModalInputDisposition.PASS_TO_MODAL_ACTION,
            playerErrorModalInputDisposition(PlayerErrorModalInput.SELECT),
        )
    }

    @Test
    fun `error modal active prevents outer live up down zapping`() {
        assertFalse(
            playerLiveProLayerOwnsInput(
                isLive = true,
                liveTvProEnabled = true,
                errorModalInputActive = true,
                browserVisible = false,
            ),
        )
        listOf(PlayerErrorModalInput.UP, PlayerErrorModalInput.DOWN).forEach { input ->
            assertEquals(
                PlayerErrorModalInputDisposition.CONSUME,
                playerErrorModalInputDisposition(input),
            )
        }
    }

    @Test
    fun `error modal active prevents outer channel keys from zapping`() {
        assertFalse(
            playerLiveProLayerOwnsInput(
                isLive = true,
                liveTvProEnabled = true,
                errorModalInputActive = true,
                browserVisible = false,
            ),
        )
        listOf(PlayerErrorModalInput.CHANNEL_UP, PlayerErrorModalInput.CHANNEL_DOWN).forEach { input ->
            assertEquals(
                PlayerErrorModalInputDisposition.CONSUME,
                playerErrorModalInputDisposition(input),
            )
        }
    }

    @Test
    fun `error modal active prevents outer media next previous from zapping`() {
        assertFalse(
            playerLiveProLayerOwnsInput(
                isLive = true,
                liveTvProEnabled = true,
                errorModalInputActive = true,
                browserVisible = false,
            ),
        )
        assertEquals(
            PlayerErrorModalInputDisposition.CONSUME,
            playerErrorModalInputDisposition(PlayerErrorModalInput.PLAYER_COMMAND),
        )
    }

    @Test
    fun `outer live layer owns input again once the error modal is gone`() {
        assertTrue(
            playerLiveProLayerOwnsInput(
                isLive = true,
                liveTvProEnabled = true,
                errorModalInputActive = false,
                browserVisible = false,
            ),
        )
    }

    @Test
    fun `child live browser visible prevents outer live key ownership`() {
        assertFalse(
            playerLiveProLayerOwnsInput(
                isLive = true,
                liveTvProEnabled = true,
                errorModalInputActive = false,
                browserVisible = true,
            ),
        )
    }

    @Test
    fun `child browser visible never grants ownership outside live pro playback`() {
        assertFalse(
            playerLiveProLayerOwnsInput(
                isLive = false,
                liveTvProEnabled = true,
                errorModalInputActive = false,
                browserVisible = true,
            ),
        )
        assertFalse(
            playerLiveProLayerOwnsInput(
                isLive = true,
                liveTvProEnabled = false,
                errorModalInputActive = false,
                browserVisible = true,
            ),
        )
    }

    @Test
    fun `outer live layer never owns input outside live pro playback`() {
        assertFalse(
            playerLiveProLayerOwnsInput(
                isLive = false,
                liveTvProEnabled = true,
                errorModalInputActive = false,
                browserVisible = false,
            ),
        )
        assertFalse(
            playerLiveProLayerOwnsInput(
                isLive = true,
                liveTvProEnabled = false,
                errorModalInputActive = false,
                browserVisible = false,
            ),
        )
    }

    @Test
    fun `error focus prefers retry once it is placed`() {
        val actions = playerErrorModalActions(canChooseChannel = true, canChooseSource = true)

        val candidates = playerErrorFocusCandidates(
            actions = actions,
            placedActions = actions.toSet(),
            attemptedActions = emptySet(),
        )

        assertEquals(PlayerErrorModalAction.RETRY, candidates.first())
    }

    @Test
    fun `error focus falls back in deterministic action order when preferred is not placed`() {
        val actions = playerErrorModalActions(canChooseChannel = true, canChooseSource = true)
        val placed = setOf(
            PlayerErrorModalAction.CHOOSE_CHANNEL,
            PlayerErrorModalAction.BACK,
        )

        val candidates = playerErrorFocusCandidates(
            actions = actions,
            placedActions = placed,
            attemptedActions = emptySet(),
        )

        assertEquals(
            listOf(PlayerErrorModalAction.CHOOSE_CHANNEL, PlayerErrorModalAction.BACK),
            candidates,
        )
    }

    @Test
    fun `error focus never retries an already attempted action`() {
        val actions = playerErrorModalActions(canChooseChannel = true, canChooseSource = true)

        val candidates = playerErrorFocusCandidates(
            actions = actions,
            placedActions = actions.toSet(),
            attemptedActions = setOf(PlayerErrorModalAction.RETRY),
        )

        assertFalse(PlayerErrorModalAction.RETRY in candidates)
        assertEquals(PlayerErrorModalAction.CHOOSE_CHANNEL, candidates.first())
    }

    @Test
    fun `error focus is bounded to placed modal actions`() {
        val actions = playerErrorModalActions(canChooseChannel = true, canChooseSource = true)

        val candidates = playerErrorFocusCandidates(
            actions = actions,
            placedActions = actions.toSet(),
            attemptedActions = emptySet(),
        )

        assertEquals(actions.size, candidates.size)
        assertEquals(actions.toSet(), candidates.toSet())
        assertEquals(candidates.distinct(), candidates)
    }

    @Test
    fun `error focus returns nothing until an action is placed`() {
        val actions = playerErrorModalActions(canChooseChannel = true, canChooseSource = true)

        assertTrue(
            playerErrorFocusCandidates(
                actions = actions,
                placedActions = emptySet(),
                attemptedActions = emptySet(),
            ).isEmpty(),
        )
    }
}
