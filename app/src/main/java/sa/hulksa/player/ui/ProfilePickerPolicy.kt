package sa.hulksa.player.ui

internal fun shouldShowProfilePicker(
    profileCount: Int,
    authenticated: Boolean,
    resolvedForSession: Boolean,
): Boolean = profileCount > 1 && authenticated && !resolvedForSession
