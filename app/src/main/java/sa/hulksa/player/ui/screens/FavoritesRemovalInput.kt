package sa.hulksa.player.ui.screens

import androidx.compose.ui.input.key.KeyEventType

/**
 * Shell-scoped owner for the Favorites long-press select gesture. It is armed by the card
 * long-press before the mutation and survives disposal of that card, the grid and the last item.
 *
 * While armed, every select press repeat and the final release are consumed at the shell
 * boundary, so a replacement card or filter can never receive the same physical hold. The guard
 * is cleared only by the real release, never by a mutation result or a timer.
 */
internal class FavoritesRemovalInputGuard {
    var active: Boolean = false
        private set

    fun begin() {
        active = true
    }

    fun onSelectKey(type: KeyEventType): Boolean {
        if (!active) return false
        if (type == KeyEventType.KeyUp) active = false
        return true
    }
}

/**
 * Arms the Favorites removal guard before the authoritative toggle. Returning the arm decision
 * keeps the callback order explicit and testable: guard first, exactly one targeted mutation.
 */
internal fun beginFavoritesRemovalLongPress(
    enabled: Boolean,
    beginGuard: () -> Unit,
    toggleFavorite: () -> Unit,
): Boolean {
    if (enabled) beginGuard()
    toggleFavorite()
    return enabled
}
