package sa.hulksa.player.data

import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import sa.hulksa.player.BuildConfig

sealed interface OperationsFetchResult {
    data class Success(
        val config: OperationsConfig,
        val rawJson: String,
        val fetchedAtEpochMs: Long,
    ) : OperationsFetchResult

    data object Failure : OperationsFetchResult
}

class OperationsClient(
    private val endpoint: String = BuildConfig.OPERATIONS_CONFIG_URL,
    private val client: OkHttpClient = defaultOperationsHttpClient(),
) {
    suspend fun fetch(): OperationsFetchResult = withContext(Dispatchers.IO) {
        val parsedEndpoint = runCatching { URI(endpoint) }.getOrNull()
        if (
            parsedEndpoint == null ||
            !parsedEndpoint.scheme.equals("https", ignoreCase = true) ||
            parsedEndpoint.host.isNullOrBlank() ||
            parsedEndpoint.userInfo != null
        ) {
            return@withContext OperationsFetchResult.Failure
        }

        runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME} Operations")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use OperationsFetchResult.Failure
                val body = response.body ?: return@use OperationsFetchResult.Failure
                val contentLength = body.contentLength()
                if (contentLength > MAX_CONFIG_BYTES) return@use OperationsFetchResult.Failure
                val rawJson = readBoundedConfig(body) ?: return@use OperationsFetchResult.Failure
                val config = parseOperationsConfig(rawJson) ?: return@use OperationsFetchResult.Failure
                OperationsFetchResult.Success(
                    config = config,
                    rawJson = rawJson,
                    fetchedAtEpochMs = System.currentTimeMillis(),
                )
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            OperationsFetchResult.Failure
        }
    }

    companion object {
        private const val MAX_CONFIG_BYTES = 512L * 1_024L

        fun defaultOperationsHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        private fun readBoundedConfig(body: ResponseBody): String? {
            val output = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_CONFIG_BYTES) return null
                    output.write(buffer, 0, read)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}
