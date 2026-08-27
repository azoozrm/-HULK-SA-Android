package sa.hulksa.player.ui.screens

import sa.hulksa.player.playback.RecoveryFailureClass

internal enum class PlayerErrorModalInput {
    BACK,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    SELECT,
    CHANNEL_UP,
    CHANNEL_DOWN,
    PLAYER_COMMAND,
    OTHER,
}

internal enum class PlayerErrorModalInputDisposition {
    HANDLE_BACK,
    PASS_TO_MODAL_ACTION,
    CONSUME,
    PASS_TO_SYSTEM,
}

internal enum class PlayerErrorModalAction {
    RETRY,
    CHOOSE_CHANNEL,
    CHOOSE_SOURCE,
    BACK,
}

internal fun playerErrorModalInputDisposition(
    input: PlayerErrorModalInput,
): PlayerErrorModalInputDisposition = when (input) {
    PlayerErrorModalInput.BACK -> PlayerErrorModalInputDisposition.HANDLE_BACK
    PlayerErrorModalInput.LEFT,
    PlayerErrorModalInput.RIGHT,
    PlayerErrorModalInput.SELECT,
    -> PlayerErrorModalInputDisposition.PASS_TO_MODAL_ACTION
    PlayerErrorModalInput.UP,
    PlayerErrorModalInput.DOWN,
    PlayerErrorModalInput.CHANNEL_UP,
    PlayerErrorModalInput.CHANNEL_DOWN,
    PlayerErrorModalInput.PLAYER_COMMAND,
    -> PlayerErrorModalInputDisposition.CONSUME
    PlayerErrorModalInput.OTHER -> PlayerErrorModalInputDisposition.PASS_TO_SYSTEM
}

internal fun canOfferPlayerErrorSourcePicker(
    failureClass: RecoveryFailureClass?,
    candidateCount: Int,
): Boolean = failureClass == RecoveryFailureClass.SOURCE && candidateCount > 1

internal fun playerErrorModalActions(
    canChooseChannel: Boolean,
    canChooseSource: Boolean,
): List<PlayerErrorModalAction> = buildList {
    add(PlayerErrorModalAction.RETRY)
    if (canChooseChannel) add(PlayerErrorModalAction.CHOOSE_CHANNEL)
    if (canChooseSource) add(PlayerErrorModalAction.CHOOSE_SOURCE)
    add(PlayerErrorModalAction.BACK)
}
