package sa.hulksa.player.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Executes a blocking OkHttp call while keeping the underlying transport owned by the caller's
 * coroutine. Cancellation closes the socket through [okhttp3.Call.cancel], including while
 * execute() or response-body reads are blocked.
 */
internal suspend fun <T> OkHttpClient.executeCancellable(
    request: Request,
    block: suspend (Response) -> T,
): T = coroutineScope {
    currentCoroutineContext().ensureActive()
    val call = newCall(request)
    val operationFinished = AtomicBoolean(false)
    val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!operationFinished.get()) call.cancel()
        }
    }

    try {
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            call.execute().use { response ->
                currentCoroutineContext().ensureActive()
                val value = block(response)
                currentCoroutineContext().ensureActive()
                value
            }
        }
    } catch (error: IOException) {
        // OkHttp reports Call.cancel() as IOException. If cancellation caused the failure,
        // preserve coroutine cancellation instead of allowing callers to map it as network I/O.
        currentCoroutineContext().ensureActive()
        throw error
    } finally {
        operationFinished.set(true)
        cancellationWatcher.cancel()
    }
}
