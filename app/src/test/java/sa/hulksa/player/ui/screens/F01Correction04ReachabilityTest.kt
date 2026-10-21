package sa.hulksa.player.ui.screens

import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.playback.RecoveryFailureClass

/**
 * F01 correction 04: the observed C03 terminal SOURCE error happened while platform connectivity
 * still looked usable. The offline decision must be confirmed by an actual reachability probe, and
 * restoration must use the same reachability evidence rather than a stale flag transition.
 */
class F01Correction04ReachabilityTest {
    @Test
    fun `terminal network source error with stale usable flags requires a reachability check`() {
        listOf("live", "movie", "episode").forEach { label ->
            assertTrue(
                label,
                shouldProbeForTerminalSourceError(
                    failureClass = RecoveryFailureClass.SOURCE,
                    localPlayback = false,
                    networkAvailable = true,
                ),
            )
            assertTrue(
                label,
                shouldEnterOfflineAfterReachabilityCheck(
                    failureClass = RecoveryFailureClass.SOURCE,
                    localPlayback = false,
                    networkAvailable = true,
                    reachable = false,
                ),
            )
        }
    }

    @Test
    fun `online source failure stays generic when reachability succeeds`() {
        assertFalse(
            shouldEnterOfflineAfterReachabilityCheck(
                failureClass = RecoveryFailureClass.SOURCE,
                localPlayback = false,
                networkAvailable = true,
                reachable = true,
            ),
        )
        assertFalse(
            shouldProbeForTerminalSourceError(
                failureClass = RecoveryFailureClass.AUDIO,
                localPlayback = false,
                networkAvailable = true,
            ),
        )
        assertFalse(
            shouldProbeForTerminalSourceError(
                failureClass = RecoveryFailureClass.SOURCE,
                localPlayback = true,
                networkAvailable = true,
            ),
        )
        assertFalse(
            shouldProbeForTerminalSourceError(
                failureClass = RecoveryFailureClass.SOURCE,
                localPlayback = false,
                networkAvailable = false,
            ),
        )
    }

    @Test
    fun `platform-unusable source error enters offline without reachability`() {
        assertTrue(
            shouldEnterOfflineAfterReachabilityCheck(
                failureClass = RecoveryFailureClass.SOURCE,
                localPlayback = false,
                networkAvailable = false,
                reachable = true,
            ),
        )
        assertFalse(
            shouldEnterOfflineAfterReachabilityCheck(
                failureClass = RecoveryFailureClass.SOURCE,
                localPlayback = true,
                networkAvailable = false,
                reachable = false,
            ),
        )
        assertFalse(
            shouldEnterOfflineAfterReachabilityCheck(
                failureClass = RecoveryFailureClass.AUDIO,
                localPlayback = false,
                networkAvailable = false,
                reachable = false,
            ),
        )
    }

    @Test
    fun `restoration requires reachability evidence not stale platform flags`() {
        assertFalse(
            shouldBeginOutageRestoration(
                confirmedOutage = true,
                restorationEvidence = false,
                localPlayback = false,
            ),
        )
        assertTrue(
            shouldBeginOutageRestoration(
                confirmedOutage = true,
                restorationEvidence = true,
                localPlayback = false,
            ),
        )
        assertFalse(
            shouldBeginOutageRestoration(
                confirmedOutage = true,
                restorationEvidence = true,
                localPlayback = true,
            ),
        )
    }

    @Test
    fun `probe targets reduce remote candidates to hosts without retaining query data`() {
        val request = PlaybackRequest(
            title = "F01 correction probe",
            posterUrl = null,
            candidates = listOf(
                "https://cdn.example/show.m3u8?token=not-retained",
                "http://backup.example:8080/live.ts",
                "file:///local/show.mp4",
            ),
            isLive = false,
            historyKey = "MOVIE:991",
            streamKind = "movie",
            streamId = 991,
            extension = "mp4",
        )

        val targets = networkProbeTargets(request)

        assertTrue(targets.contains(NetworkProbeTarget("hulksa.com", 443)))
        assertTrue(targets.contains(NetworkProbeTarget("www.google.com", 443)))
        assertTrue(targets.contains(NetworkProbeTarget("cdn.example", 443)))
        assertTrue(targets.contains(NetworkProbeTarget("backup.example", 8080)))
        assertFalse(targets.any { it.host == "local" })
        assertFalse(targets.any { it.host.contains("token", ignoreCase = true) })
    }

    @Test
    fun `bounded probe distinguishes a reachable listener from a closed port`() = runBlocking {
        ServerSocket(0).use { listener ->
            assertTrue(
                probeNetworkReachable(
                    targets = listOf(NetworkProbeTarget("127.0.0.1", listener.localPort)),
                    perTargetTimeoutMs = 500,
                    totalTimeoutMs = 1_000,
                ),
            )
        }
        val closedPort = ServerSocket(0).use { it.localPort }
        assertFalse(
            probeNetworkReachable(
                targets = listOf(NetworkProbeTarget("127.0.0.1", closedPort)),
                perTargetTimeoutMs = 300,
                totalTimeoutMs = 600,
            ),
        )
        assertFalse(probeNetworkReachable(emptyList()))
    }
}
