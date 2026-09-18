package sa.hulksa.player.data

import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONObject

internal enum class PresencePlatformClass {
    PHONE,
    TABLET,
    TV,
    OTHER,
}

internal data class PresenceDeviceMetadata(
    val platformClass: PresencePlatformClass,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
)

internal data class PresenceAppMetadata(
    val versionName: String,
    val versionCode: Int,
)

internal data class PresenceSessionSnapshot(
    val sessionId: String,
    val installationId: String,
    val accessCode: String,
    val iptvUsername: String,
    val iptvPassword: String,
    val host: String,
    val authenticatedAtEpochMs: Long,
    val device: PresenceDeviceMetadata,
    val app: PresenceAppMetadata,
)

internal data class PresenceStartAccepted(
    val sessionId: String,
    val token: String,
    val heartbeatSeconds: Int,
    val onlineTtlSeconds: Int,
    val serverTimeEpochSeconds: Long,
)

internal sealed interface PresenceStartResult {
    data class Accepted(val value: PresenceStartAccepted) : PresenceStartResult
    data object TerminalFailure : PresenceStartResult
    data object TransientFailure : PresenceStartResult
}

internal sealed interface PresenceCommandResult {
    data object Accepted : PresenceCommandResult
    data object TerminalFailure : PresenceCommandResult
    data object TransientFailure : PresenceCommandResult
}

internal interface PresenceTransport {
    suspend fun start(
        config: OperationsPresenceConfig,
        snapshot: PresenceSessionSnapshot,
    ): PresenceStartResult

    suspend fun heartbeat(
        config: OperationsPresenceConfig,
        sessionId: String,
        token: String,
    ): PresenceCommandResult

    suspend fun end(
        config: OperationsPresenceConfig,
        sessionId: String,
        token: String,
        reason: PresenceEndReason,
    ): PresenceCommandResult
}

internal enum class PresenceEndReason {
    LOGOUT,
    ACCOUNT_REPLACED,
    APP_SHUTDOWN,
}

internal class PresenceClient(
    private val client: OkHttpClient = defaultPresenceHttpClient(),
) : PresenceTransport {
    override suspend fun start(
        config: OperationsPresenceConfig,
        snapshot: PresenceSessionSnapshot,
    ): PresenceStartResult {
        if (!snapshot.isValid()) return PresenceStartResult.TerminalFailure
        val body = JSONObject()
            .put("contractVersion", 1)
            .put("sessionId", snapshot.sessionId)
            .put("installationId", snapshot.installationId)
            .put("accessCode", snapshot.accessCode)
            .put("iptvUsername", snapshot.iptvUsername)
            .put("iptvPassword", snapshot.iptvPassword)
            .put("host", snapshot.host)
            .put("authenticatedAtEpochMs", snapshot.authenticatedAtEpochMs)
            .put(
                "device",
                JSONObject()
                    .put("platformClass", snapshot.device.platformClass.name)
                    .put("manufacturer", snapshot.device.manufacturer)
                    .put("model", snapshot.device.model)
                    .put("androidRelease", snapshot.device.androidRelease)
                    .put("sdkInt", snapshot.device.sdkInt),
            )
            .put(
                "app",
                JSONObject()
                    .put("versionName", snapshot.app.versionName)
                    .put("versionCode", snapshot.app.versionCode),
            )
            .toString()
        if (body.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_BYTES) {
            return PresenceStartResult.TerminalFailure
        }

        return execute(
            request = post(config, "start", body),
            onSuccess = { response -> parseStart(response, snapshot.sessionId) },
            onFailure = { response -> startFailure(response.code) },
        ) ?: PresenceStartResult.TransientFailure
    }

    override suspend fun heartbeat(
        config: OperationsPresenceConfig,
        sessionId: String,
        token: String,
    ): PresenceCommandResult = command(
        config = config,
        operation = "heartbeat",
        token = token,
        body = JSONObject().put("sessionId", sessionId).toString(),
    )

    override suspend fun end(
        config: OperationsPresenceConfig,
        sessionId: String,
        token: String,
        reason: PresenceEndReason,
    ): PresenceCommandResult = command(
        config = config,
        operation = "end",
        token = token,
        body = JSONObject()
            .put("sessionId", sessionId)
            .put("reason", reason.name)
            .toString(),
    )

    private suspend fun command(
        config: OperationsPresenceConfig,
        operation: String,
        token: String,
        body: String,
    ): PresenceCommandResult {
        if (!TOKEN_PATTERN.matches(token) || body.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_BYTES) {
            return PresenceCommandResult.TerminalFailure
        }
        return execute(
            request = post(config, operation, body, token),
            onSuccess = { PresenceCommandResult.Accepted },
            onFailure = { response -> commandFailure(response.code) },
        ) ?: PresenceCommandResult.TransientFailure
    }

    private fun post(
        config: OperationsPresenceConfig,
        operation: String,
        body: String,
        token: String? = null,
    ): Request {
        val builder = Request.Builder()
            .url(config.baseUrl + operation + "/")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("User-Agent", "HULK-SA Presence")
        if (token != null) builder.header("Authorization", "Bearer $token")
        return builder.build()
    }

    private suspend fun <T> execute(
        request: Request,
        onSuccess: (Response) -> T,
        onFailure: (Response) -> T,
    ): T? = try {
        client.executeCancellable(request) { response ->
            if (response.isSuccessful) onSuccess(response) else onFailure(response)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    private fun parseStart(response: Response, expectedSessionId: String): PresenceStartResult {
        val raw = response.body?.readBounded(MAX_RESPONSE_BYTES)
            ?: return PresenceStartResult.TerminalFailure
        return runCatching {
            val json = JSONObject(raw)
            val sessionId = json.getString("sessionId")
            val token = json.getString("presenceToken")
            val heartbeatSeconds = json.getInt("heartbeatSeconds")
            val onlineTtlSeconds = json.getInt("onlineTtlSeconds")
            val serverTimeEpochSeconds = json.getLong("serverTimeEpochSeconds")
            require(sessionId == expectedSessionId)
            require(TOKEN_PATTERN.matches(token))
            require(heartbeatSeconds in 15..600)
            require(onlineTtlSeconds in (heartbeatSeconds * 2)..3_600)
            require(serverTimeEpochSeconds > 0L)
            PresenceStartResult.Accepted(
                PresenceStartAccepted(
                    sessionId = sessionId,
                    token = token,
                    heartbeatSeconds = heartbeatSeconds,
                    onlineTtlSeconds = onlineTtlSeconds,
                    serverTimeEpochSeconds = serverTimeEpochSeconds,
                ),
            )
        }.getOrElse { PresenceStartResult.TerminalFailure }
    }

    private fun startFailure(code: Int): PresenceStartResult = if (code.isTransientHttpFailure()) {
        PresenceStartResult.TransientFailure
    } else {
        PresenceStartResult.TerminalFailure
    }

    private fun commandFailure(code: Int): PresenceCommandResult = if (code.isTransientHttpFailure()) {
        PresenceCommandResult.TransientFailure
    } else {
        PresenceCommandResult.TerminalFailure
    }

    private fun PresenceSessionSnapshot.isValid(): Boolean =
        sessionId.isUuid() &&
            installationId.isUuid() &&
            accessCode.length in 1..64 &&
            iptvUsername.length in 1..255 &&
            iptvPassword.length in 1..512 &&
            host.length in 1..2_048 &&
            host.isValidHttpEndpoint() &&
            authenticatedAtEpochMs > 0L &&
            device.manufacturer.length in 1..100 &&
            device.model.length in 1..100 &&
            device.androidRelease.length in 1..32 &&
            device.sdkInt in 1..1_000 &&
            app.versionName.length in 1..32 &&
            app.versionCode > 0

    private fun String.isUuid(): Boolean = runCatching {
        UUID.fromString(this).toString().equals(this, ignoreCase = true)
    }.getOrDefault(false)

    private fun String.isValidHttpEndpoint(): Boolean = runCatching {
        val uri = URI(this)
        require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))
        require(!uri.host.isNullOrBlank())
        require(uri.userInfo == null)
    }.isSuccess

    private fun Int.isTransientHttpFailure(): Boolean = this == 408 || this == 425 || this == 429 || this >= 500

    companion object {
        private const val MAX_REQUEST_BYTES = 8 * 1_024
        private const val MAX_RESPONSE_BYTES = 16 * 1_024L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{43,128}")

        fun defaultPresenceHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}

private fun ResponseBody.readBounded(maxBytes: Long): String? {
    if (contentLength() > maxBytes) return null
    val output = ByteArrayOutputStream()
    byteStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
    }
    return output.toString(Charsets.UTF_8.name())
}
