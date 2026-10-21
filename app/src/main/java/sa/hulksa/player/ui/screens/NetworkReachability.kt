package sa.hulksa.player.ui.screens

import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sa.hulksa.player.model.PlaybackRequest

internal data class NetworkProbeTarget(
    val host: String,
    val port: Int,
)

private val STABLE_PROBE_TARGETS = listOf(
    NetworkProbeTarget(host = "hulksa.com", port = 443),
    NetworkProbeTarget(host = "www.google.com", port = 443),
)

/**
 * Targets used only to distinguish a real internet outage from an online source/content failure.
 * Candidate hosts are reduced to host/port and are never logged.
 */
internal fun networkProbeTargets(request: PlaybackRequest): List<NetworkProbeTarget> {
    val candidateTargets = request.candidates.mapNotNull { candidate ->
        runCatching {
            val uri = URI(candidate.trim())
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return@runCatching null
            val host = uri.host?.takeIf(String::isNotBlank) ?: return@runCatching null
            val port = when {
                uri.port > 0 -> uri.port
                scheme == "http" -> 80
                else -> 443
            }
            NetworkProbeTarget(host = host, port = port)
        }.getOrNull()
    }
    return (STABLE_PROBE_TARGETS + candidateTargets).distinct()
}

/**
 * Bounded TCP reachability probe. It deliberately does not send HTTP requests or log host names.
 */
internal suspend fun probeNetworkReachable(
    targets: List<NetworkProbeTarget>,
    perTargetTimeoutMs: Int = 1_200,
    totalTimeoutMs: Long = 3_000L,
): Boolean {
    if (targets.isEmpty()) return false
    return withTimeoutOrNull(totalTimeoutMs) {
        withContext(Dispatchers.IO) {
            targets.any { target ->
                runCatching {
                    Socket().use { socket ->
                        socket.connect(
                            InetSocketAddress(target.host, target.port),
                            perTargetTimeoutMs,
                        )
                        true
                    }
                }.getOrDefault(false)
            }
        }
    } ?: false
}
