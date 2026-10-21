package sa.hulksa.player.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sa.hulksa.player.model.PlaybackRequest

internal data class NetworkProbeTarget(
    val host: String,
    val port: Int,
)

internal interface HostResolver {
    suspend fun resolve(host: String, timeoutMs: Long): List<InetAddress>
}

internal interface AddressConnector {
    suspend fun connect(address: InetAddress, port: Int, timeoutMs: Int): Boolean
}

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
 * Bounded reachability orchestration. DNS is resolved before connecting, and every target and
 * address checks the remaining deadline and coroutine cancellation. It never sends an HTTP request
 * and never logs host names.
 */
internal class BoundedReachabilityProbe(
    private val resolver: HostResolver,
    private val connector: AddressConnector,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    suspend fun isReachable(
        targets: List<NetworkProbeTarget>,
        perTargetTimeoutMs: Int = 1_200,
        totalTimeoutMs: Long = 3_000L,
    ): Boolean {
        if (targets.isEmpty() || perTargetTimeoutMs <= 0 || totalTimeoutMs <= 0L) return false
        return withTimeoutOrNull(totalTimeoutMs) {
            val startedMs = clockMs()
            for (target in targets.distinct()) {
                if (!currentCoroutineContext().isActive) return@withTimeoutOrNull false
                val remainingMs = totalTimeoutMs - (clockMs() - startedMs)
                if (remainingMs <= 0L) return@withTimeoutOrNull false
                val addresses = resolver.resolve(
                    host = target.host,
                    timeoutMs = minOf(perTargetTimeoutMs.toLong(), remainingMs),
                )
                if (!currentCoroutineContext().isActive) return@withTimeoutOrNull false
                for (address in addresses.distinct()) {
                    val remainingForConnectionMs = totalTimeoutMs - (clockMs() - startedMs)
                    if (remainingForConnectionMs <= 0L) return@withTimeoutOrNull false
                    val connectionTimeoutMs = minOf(
                        perTargetTimeoutMs.toLong(),
                        remainingForConnectionMs,
                    ).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
                    if (connector.connect(address, target.port, connectionTimeoutMs)) {
                        return@withTimeoutOrNull true
                    }
                    if (!currentCoroutineContext().isActive) return@withTimeoutOrNull false
                }
            }
            false
        } ?: false
    }
}

internal fun createNetworkReachabilityProbe(context: Context?): BoundedReachabilityProbe =
    BoundedReachabilityProbe(
        resolver = if (context == null) {
            LegacyHostResolver
        } else {
            AndroidHostResolver(context.applicationContext)
        },
        connector = SocketAddressConnector,
    )

/** Backward-compatible JVM/test entry point; the screen uses the Android resolver. */
internal suspend fun probeNetworkReachable(
    targets: List<NetworkProbeTarget>,
    perTargetTimeoutMs: Int = 1_200,
    totalTimeoutMs: Long = 3_000L,
): Boolean = createNetworkReachabilityProbe(null).isReachable(
    targets = targets,
    perTargetTimeoutMs = perTargetTimeoutMs,
    totalTimeoutMs = totalTimeoutMs,
)

private class AndroidHostResolver(context: Context) : HostResolver {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override suspend fun resolve(host: String, timeoutMs: Long): List<InetAddress> {
        if (timeoutMs <= 0L) return emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return LegacyHostResolver.resolve(host, timeoutMs)
        }
        val network = manager?.activeNetwork ?: return emptyList()
        val startedMs = SystemClock.elapsedRealtime()
        val ipv4 = query(network, host, DnsResolver.TYPE_A, timeoutMs)
        if (ipv4.isNotEmpty()) return ipv4
        val remainingMs = timeoutMs - (SystemClock.elapsedRealtime() - startedMs)
        if (remainingMs <= 0L) return emptyList()
        return query(network, host, DnsResolver.TYPE_AAAA, remainingMs)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun query(
        network: Network,
        host: String,
        type: Int,
        timeoutMs: Long,
    ): List<InetAddress> = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "HulkNetDns").apply { isDaemon = true }
            }
            val completed = AtomicBoolean(false)
            fun complete(answer: List<InetAddress>?) {
                if (!completed.compareAndSet(false, true)) return
                executor.shutdown()
                if (continuation.isActive) {
                    continuation.resume(answer ?: emptyList())
                }
            }
            val callback = object : DnsResolver.Callback<List<InetAddress>> {
                override fun onAnswer(answer: List<InetAddress>, rcode: Int) = complete(answer)
                override fun onError(error: DnsResolver.DnsException) = complete(null)
            }
            try {
                DnsResolver.getInstance().query(
                    network,
                    host,
                    type,
                    DnsResolver.FLAG_EMPTY,
                    executor,
                    signal,
                    callback,
                )
            } catch (error: Throwable) {
                complete(null)
            }
            continuation.invokeOnCancellation {
                signal.cancel()
                executor.shutdownNow()
            }
        }
    } ?: emptyList()
}

private object LegacyHostResolver : HostResolver {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HulkNetDnsLegacy").apply { isDaemon = true }
    }
    private val inFlight = AtomicBoolean(false)

    override suspend fun resolve(host: String, timeoutMs: Long): List<InetAddress> {
        if (timeoutMs <= 0L) return emptyList()
        // One legacy resolver task can be outstanding. A blocked platform resolver cannot be
        // interrupted reliably on old APIs, so a second task is never queued behind it.
        if (!inFlight.compareAndSet(false, true)) return emptyList()
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                try {
                    executor.execute {
                        val result = runCatching {
                            InetAddress.getAllByName(host).toList()
                        }.getOrDefault(emptyList())
                        inFlight.set(false)
                        if (continuation.isActive) continuation.resume(result)
                    }
                } catch (error: Throwable) {
                    inFlight.set(false)
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            }
        } ?: emptyList()
    }
}

private object SocketAddressConnector : AddressConnector {
    override suspend fun connect(
        address: InetAddress,
        port: Int,
        timeoutMs: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        val socket = Socket()
        val completion = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) runCatching { socket.close() }
        }
        try {
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            true
        } catch (error: Throwable) {
            false
        } finally {
            completion?.dispose()
            runCatching { socket.close() }
        }
    }
}

/**
 * Keep confirmed-outage monitoring alive for as long as the screen/request is eligible. The
 * per-check deadline is inside [BoundedReachabilityProbe]; this loop only controls the interval.
 */
internal fun networkProbeBackoffMs(attempt: Int): Long = when {
    attempt < 20 -> 3_000L
    attempt < 80 -> 5_000L
    else -> 15_000L
}

internal class NetworkRestoreMonitor(
    private val probe: suspend () -> Boolean,
    private val delayForAttemptMs: suspend (Long) -> Unit = { delay(it) },
    private val onAttempt: (Int, Long) -> Unit = { _, _ -> },
) {
    suspend fun run(onRestored: () -> Unit): Int {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            attempt += 1
            if (probe()) {
                if (currentCoroutineContext().isActive) onRestored()
                return attempt
            }
            if (!currentCoroutineContext().isActive) return attempt
            val backoffMs = networkProbeBackoffMs(attempt)
            onAttempt(attempt, backoffMs)
            delayForAttemptMs(backoffMs)
        }
        return attempt
    }
}
