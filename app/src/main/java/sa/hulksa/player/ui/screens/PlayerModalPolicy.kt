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

/**
 * Input ownership across the PlayerProScreen / PlayerScreen boundary.
 *
 * The child player error modal (and the channel/server pickers it opens) is the single input
 * owner while it is active, the child owns the single canonical Live channel browser visibility
 * state, and any active child Player panel (resize, source, audio, subtitles, speed, quality) owns
 * its own navigation keys. The outer Live TV Pro layer must not convert child-owned keys into Live
 * navigation or browser actions during that time. When the child owns none of those surfaces the
 * outer layer keeps its existing Live behavior.
 */
internal fun playerLiveProLayerOwnsInput(
    isLive: Boolean,
    liveTvProEnabled: Boolean,
    errorModalInputActive: Boolean,
    browserVisible: Boolean,
    panelInputActive: Boolean = false,
): Boolean = isLive && liveTvProEnabled && !errorModalInputActive && !browserVisible && !panelInputActive

internal fun canOfferPlayerErrorSourcePicker(
    failureClass: RecoveryFailureClass?,
    candidateCount: Int,
): Boolean = failureClass == RecoveryFailureClass.SOURCE && candidateCount > 1

internal fun canOfferPlayerLiveSourcePicker(candidateCount: Int): Boolean = candidateCount > 1

internal fun playerErrorModalActions(
    canChooseChannel: Boolean,
    canChooseSource: Boolean,
): List<PlayerErrorModalAction> = buildList {
    add(PlayerErrorModalAction.RETRY)
    if (canChooseChannel) add(PlayerErrorModalAction.CHOOSE_CHANNEL)
    if (canChooseSource) add(PlayerErrorModalAction.CHOOSE_SOURCE)
    add(PlayerErrorModalAction.BACK)
}

/**
 * Ordered, bounded focus-acquisition candidates for the final error modal.
 *
 * The order is preserved from [playerErrorModalActions], so RETRY (the recommended action) is
 * preferred whenever it is placed and has not been attempted yet. Only actions that have signalled
 * layout placement are eligible and each action is offered at most once, so the caller can request
 * focus deterministically without delay, polling or unbounded retries.
 */
internal fun playerErrorFocusCandidates(
    actions: List<PlayerErrorModalAction>,
    placedActions: Set<PlayerErrorModalAction>,
    attemptedActions: Set<PlayerErrorModalAction>,
): List<PlayerErrorModalAction> = actions
    .filter { it in placedActions && it !in attemptedActions }
    .distinct()
