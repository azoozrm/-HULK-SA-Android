package sa.hulksa.player.ui.screens

internal enum class NetworkCallbackKind {
    AVAILABLE,
    CAPABILITIES,
    LOST,
    UNAVAILABLE,
}

internal data class NetworkCallbackSignal(
    val kind: NetworkCallbackKind,
    val networkToken: Int? = null,
    val hasInternet: Boolean = false,
    val validated: Boolean = false,
)

internal data class NetworkAvailabilityState(
    val available: Boolean,
    val networkToken: Int?,
)

/**
 * Callback-derived connectivity. Capabilities are authoritative when supplied; a network becoming
 * available is not usable until its capabilities are validated. Events from a superseded network
 * token cannot overwrite the current default network, and a lost current network closes availability
 * immediately.
 */
internal fun reduceNetworkAvailability(
    state: NetworkAvailabilityState,
    signal: NetworkCallbackSignal,
): NetworkAvailabilityState = when (signal.kind) {
    NetworkCallbackKind.AVAILABLE ->
        if (state.networkToken == signal.networkToken) {
            state
        } else {
            NetworkAvailabilityState(available = false, networkToken = signal.networkToken)
        }
    NetworkCallbackKind.CAPABILITIES ->
        if (
            state.networkToken == null ||
            signal.networkToken == null ||
            state.networkToken == signal.networkToken
        ) {
            NetworkAvailabilityState(
                available = signal.hasInternet && signal.validated,
                networkToken = signal.networkToken ?: state.networkToken,
            )
        } else {
            state
        }
    NetworkCallbackKind.LOST ->
        if (
            signal.networkToken == null ||
            state.networkToken == null ||
            signal.networkToken == state.networkToken
        ) {
            NetworkAvailabilityState(available = false, networkToken = null)
        } else {
            state
        }
    NetworkCallbackKind.UNAVAILABLE ->
        NetworkAvailabilityState(available = false, networkToken = null)
}
