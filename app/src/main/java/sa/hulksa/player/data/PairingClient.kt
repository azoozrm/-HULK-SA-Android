package sa.hulksa.player.data

import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONObject
import sa.hulksa.player.model.Credentials

internal const val PAIRING_BASE_PATH = "/control-center/api/app/v1/pairing/"

private const val PAIRING_HOST = "hulksa.com"
private const val PAIRING_USER_CODE_PREFIX = "HULKTV-"
private const val PAIRING_USER_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
private const val PAIRING_USER_CODE_LENGTH = 6
private const val PAIRING_MAX_REQUEST_BYTES = 4_096
private const val PAIRING_MAX_RESPONSE_BYTES = 64L * 1_024L
private const val PAIRING_MIN_POLL_INTERVAL_SECONDS = 1
private const val PAIRING_MAX_POLL_INTERVAL_SECONDS = 60
private const val PAIRING_MIN_DELIVERIES = 1
private const val PAIRING_MAX_DELIVERIES = 100

/**
 * Canonical pairing discovery base URL. Only the exact reviewed HTTPS origin/path is accepted;
 * user-info, query, fragment and every non-default port are rejected. Returns the fixed canonical
 * string so derived endpoint paths can never be altered by a crafted discovery value.
 */
internal fun canonicalPairingBaseUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return null
    if (!parsed.scheme.equals("https", ignoreCase = true)) return null
    if (!parsed.host.equals(PAIRING_HOST, ignoreCase = true)) return null
    if (parsed.port != -1 && parsed.port != 443) return null
    if (parsed.userInfo != null || parsed.query != null || parsed.fragment != null) return null
    if (parsed.rawPath != PAIRING_BASE_PATH) return null
    return "https://$PAIRING_HOST$PAIRING_BASE_PATH"
}

/** Backend UUID v4 rule: lowercase canonical form. */
internal fun isPairingUuidV4(value: String): Boolean =
    value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))

internal fun isPairingUserCode(value: String): Boolean {
    if (!value.startsWith(PAIRING_USER_CODE_PREFIX)) return false
    val payload = value.removePrefix(PAIRING_USER_CODE_PREFIX)
    if (payload.length != PAIRING_USER_CODE_LENGTH) return false
    return payload.all { it in PAIRING_USER_CODE_ALPHABET }
}

/**
 * Canonical unpadded base64url token (client secret, claim token) of exactly 32 bytes. Validates
 * the trailing bits by decoding and re-encoding; alphabet/length alone would accept noncanonical
 * encodings that the backend's strict decoder rejects.
 */
internal fun isPairingCanonicalToken(value: String): Boolean {
    if (value.length != 43) return false
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val bytes = ByteArray(32)
    var buffer = 0
    var bits = 0
    var out = 0
    value.forEach { character ->
        val index = alphabet.indexOf(character)
        if (index < 0) return false
        buffer = (buffer shl 6) or index
        bits += 6
        if (bits >= 8) {
            bits -= 8
            bytes[out++] = ((buffer shr bits) and 0xff).toByte()
        }
    }
    if (bits != 2 || (buffer and 0x03) != 0) return false
    return canonicalBase64Url(bytes) == value
}

/**
 * Canonical unpadded base64url of exactly 32 random bytes. Matches the backend's strict canonical
 * check (alphabet, length and trailing-bit canonicalization) without padding or standard alphabet.
 */
internal fun canonicalBase64Url(secret: ByteArray): String {
    require(secret.size == 32) { "Pairing client secrets are 32 bytes" }
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val output = StringBuilder(43)
    var buffer = 0
    var bits = 0
    secret.forEach { byte ->
        buffer = (buffer shl 8) or (byte.toInt() and 0xff)
        bits += 8
        while (bits >= 6) {
            bits -= 6
            output.append(alphabet[(buffer shr bits) and 0x3f])
        }
    }
    if (bits > 0) {
        output.append(alphabet[(buffer shl (6 - bits)) and 0x3f])
    }
    return output.toString()
}

internal fun generatePairingSecret(secureRandom: SecureRandom = SecureRandom()): ByteArray {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return bytes
}

internal data class PairingDeviceInfo(
    val manufacturer: String?,
    val model: String?,
    val appVersion: String?,
)

internal data class PairingCreateInput(
    val clientRequestId: String,
    val clientSecret: String,
    val installationId: String,
    val requestedAtEpochMs: Long,
    val device: PairingDeviceInfo,
)

internal data class PairingCreated(
    val requestId: String,
    val userCode: String,
    val claimToken: String,
    val expiresAtEpochMs: Long,
    val verificationUrl: String,
    val pollIntervalSeconds: Int,
    val maxDeliveries: Int,
)

internal sealed interface PairingClaimOutcome {
    data class Pending(
        val expiresAtEpochMs: Long,
        val retryAfterMs: Long,
    ) : PairingClaimOutcome

    data class Approved(
        val expiresAtEpochMs: Long,
        val retryAfterMs: Long,
        val deliveryCount: Int,
        val credentials: Credentials,
    ) : PairingClaimOutcome

    /** ACKED, DENIED, CANCELLED or EXPIRED. */
    data class Terminal(val state: String) : PairingClaimOutcome
}

internal sealed interface PairingResult<out T> {
    data class Success<T>(val value: T) : PairingResult<T>

    /** Controlled server/HTTP rejection; [code] is a safe enum-like server code or HTTP_<status>. */
    data class HttpFailure(
        val code: String,
        val httpStatus: Int,
        val retryAfterSeconds: Int?,
    ) : PairingResult<Nothing>

    /** Network/timeout/IO failure of a bounded attempt. */
    data object TransportFailure : PairingResult<Nothing>

    /** The discovery URL was rejected before any request bytes were created. */
    data object DiscoveryInvalid : PairingResult<Nothing>

    /** A successful HTTP response failed strict contract validation; never used for login. */
    data object InvalidResponse : PairingResult<Nothing>
}

internal interface PairingTransport {
    suspend fun create(baseUrl: String, input: PairingCreateInput): PairingResult<PairingCreated>

    suspend fun claim(
        baseUrl: String,
        requestId: String,
        claimToken: String,
    ): PairingResult<PairingClaimOutcome>

    suspend fun ack(baseUrl: String, requestId: String, claimToken: String): PairingResult<String>

    suspend fun cancel(baseUrl: String, requestId: String, claimToken: String): PairingResult<String>
}

/**
 * TV pairing JSON API transport. HTTPS/host/path are re-validated for every request; redirects,
 * automatic retries and caching are disabled; every response is read through a hard byte ceiling.
 * The injected client exists so tests can point the same validated production URL at a local mock
 * TLS server without changing production trust or transport behaviour.
 */
internal class PairingClient(
    private val client: OkHttpClient = defaultPairingHttpClient(),
) : PairingTransport {
    override suspend fun create(
        baseUrl: String,
        input: PairingCreateInput,
    ): PairingResult<PairingCreated> {
        val endpoint = endpoint(baseUrl, "create") ?: return PairingResult.DiscoveryInvalid
        if (
            !isPairingUuidV4(input.clientRequestId) ||
            !isPairingUuidV4(input.installationId) ||
            !isPairingCanonicalToken(input.clientSecret)
        ) {
            return PairingResult.InvalidResponse
        }
        val device = JSONObject().put("platform", "TV")
        input.device.manufacturer?.takeIf(String::isNotBlank)?.let { device.put("manufacturer", it) }
        input.device.model?.takeIf(String::isNotBlank)?.let { device.put("model", it) }
        input.device.appVersion?.takeIf(String::isNotBlank)?.let { device.put("appVersion", it) }
        val body = JSONObject()
            .put("contractVersion", 1)
            .put("clientRequestId", input.clientRequestId)
            .put("clientSecret", input.clientSecret)
            .put("installationId", input.installationId)
            .put("device", device)
            .put("requestedAtEpochMs", input.requestedAtEpochMs)
            .toString()
        if (body.toByteArray(Charsets.UTF_8).size > PAIRING_MAX_REQUEST_BYTES) {
            return PairingResult.InvalidResponse
        }
        return execute(request = post(endpoint, body), successCodes = CREATE_SUCCESS_CODES) { response ->
            val json = response.readPairingJson() ?: return@execute PairingResult.InvalidResponse
            val created = parseCreated(json) ?: return@execute PairingResult.InvalidResponse
            PairingResult.Success(created)
        }
    }

    override suspend fun claim(
        baseUrl: String,
        requestId: String,
        claimToken: String,
    ): PairingResult<PairingClaimOutcome> {
        val endpoint = endpoint(baseUrl, "claim") ?: return PairingResult.DiscoveryInvalid
        if (!isPairingUuidV4(requestId) || !isPairingCanonicalToken(claimToken)) {
            return PairingResult.InvalidResponse
        }
        return execute(
            request = post(endpoint, sessionBody(requestId), claimToken),
            successCodes = setOf(200),
        ) { response ->
            val json = response.readPairingJson() ?: return@execute PairingResult.InvalidResponse
            val outcome = parseClaim(json) ?: return@execute PairingResult.InvalidResponse
            PairingResult.Success(outcome)
        }
    }

    override suspend fun ack(
        baseUrl: String,
        requestId: String,
        claimToken: String,
    ): PairingResult<String> = sessionCommand(baseUrl, "ack", requestId, claimToken) { state ->
        state == "ACKED"
    }

    override suspend fun cancel(
        baseUrl: String,
        requestId: String,
        claimToken: String,
    ): PairingResult<String> = sessionCommand(baseUrl, "cancel", requestId, claimToken) { state ->
        state == "ACKED" || state == "DENIED" || state == "CANCELLED" || state == "EXPIRED"
    }

    private suspend fun sessionCommand(
        baseUrl: String,
        operation: String,
        requestId: String,
        claimToken: String,
        acceptedState: (String) -> Boolean,
    ): PairingResult<String> {
        val endpoint = endpoint(baseUrl, operation) ?: return PairingResult.DiscoveryInvalid
        if (!isPairingUuidV4(requestId) || !isPairingCanonicalToken(claimToken)) {
            return PairingResult.InvalidResponse
        }
        return execute(
            request = post(endpoint, sessionBody(requestId), claimToken),
            successCodes = setOf(200),
        ) { response ->
            val json = response.readPairingJson() ?: return@execute PairingResult.InvalidResponse
            if (json.strictInt("contractVersion") != 1) return@execute PairingResult.InvalidResponse
            val state = json.strictString("state") ?: return@execute PairingResult.InvalidResponse
            if (acceptedState(state)) PairingResult.Success(state) else PairingResult.InvalidResponse
        }
    }

    private fun sessionBody(requestId: String): String = JSONObject()
        .put("contractVersion", 1)
        .put("requestId", requestId)
        .toString()

    private fun endpoint(baseUrl: String, operation: String): String? {
        val canonical = canonicalPairingBaseUrl(baseUrl) ?: return null
        return canonical + operation + "/"
    }

    private fun post(endpoint: String, body: String, claimToken: String? = null): Request {
        val builder = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("User-Agent", "HULK-SA Pairing")
            .header("Cache-Control", "no-store")
        if (claimToken != null) builder.header("Authorization", "Bearer $claimToken")
        return builder.build()
    }

    private suspend fun <T> execute(
        request: Request,
        successCodes: Set<Int>,
        onSuccess: (Response) -> PairingResult<T>,
    ): PairingResult<T> = try {
        client.executeCancellable(request) { response ->
            if (response.code !in successCodes) {
                response.readError(response.code)
            } else {
                onSuccess(response)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        PairingResult.TransportFailure
    }

    private fun Response.readError(httpStatus: Int): PairingResult<Nothing> {
        val raw = body?.readBounded(PAIRING_MAX_RESPONSE_BYTES)
        val parsed = raw?.let { text -> runCatching { JSONObject(text).optJSONObject("error") }.getOrNull() }
        val code = parsed?.strictString("code")?.trim()?.takeIf(String::isNotEmpty) ?: "HTTP_$httpStatus"
        val bodyRetry = parsed?.strictInt("retryAfterSeconds")?.takeIf { it > 0 }
        val headerRetry = header("Retry-After")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val retryAfter = listOfNotNull(bodyRetry, headerRetry).maxOrNull()
        return PairingResult.HttpFailure(code = code, httpStatus = httpStatus, retryAfterSeconds = retryAfter)
    }

    private fun Response.readPairingJson(): JSONObject? {
        val raw = body?.readBounded(PAIRING_MAX_RESPONSE_BYTES) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun parseCreated(json: JSONObject): PairingCreated? = runCatching {
        require(json.strictInt("contractVersion") == 1)
        val requestId = json.strictString("requestId") ?: error("requestId")
        require(isPairingUuidV4(requestId))
        val userCode = json.strictString("userCode") ?: error("userCode")
        require(isPairingUserCode(userCode))
        val claimToken = json.strictString("claimToken") ?: error("claimToken")
        require(isPairingCanonicalToken(claimToken))
        val expiresAtEpochMs = json.strictLong("expiresAtEpochMs") ?: error("expiresAtEpochMs")
        require(expiresAtEpochMs > 0L)
        val verificationUrl = json.strictString("verificationUrl") ?: error("verificationUrl")
        // Existing R04 clients and the preserved API output use the legacy route; the R07 public
        // site uses /pair/. Accept only these two exact strings bound to the same validated code
        // and expose the canonical /pair/ URL constructed from that code.
        val legacyVerificationUrl = "https://hulksa.com/control-center/pair/#C=$userCode"
        val canonicalVerificationUrl = "https://hulksa.com/pair/#C=$userCode"
        require(verificationUrl == legacyVerificationUrl || verificationUrl == canonicalVerificationUrl)
        val pollIntervalSeconds = json.strictInt("pollIntervalSeconds") ?: error("pollIntervalSeconds")
        require(pollIntervalSeconds in PAIRING_MIN_POLL_INTERVAL_SECONDS..PAIRING_MAX_POLL_INTERVAL_SECONDS)
        val maxDeliveries = json.strictInt("maxDeliveries") ?: error("maxDeliveries")
        require(maxDeliveries in PAIRING_MIN_DELIVERIES..PAIRING_MAX_DELIVERIES)
        PairingCreated(
            requestId = requestId,
            userCode = userCode,
            claimToken = claimToken,
            expiresAtEpochMs = expiresAtEpochMs,
            verificationUrl = canonicalVerificationUrl,
            pollIntervalSeconds = pollIntervalSeconds,
            maxDeliveries = maxDeliveries,
        )
    }.getOrNull()

    private fun parseClaim(json: JSONObject): PairingClaimOutcome? = runCatching {
        require(json.strictInt("contractVersion") == 1)
        when (val state = json.strictString("state")) {
            "PENDING" -> PairingClaimOutcome.Pending(
                expiresAtEpochMs = requireServerExpiry(json),
                retryAfterMs = requireRetryAfter(json),
            )
            "APPROVED" -> {
                val credentials = json.opt("credentials") as? JSONObject ?: error("credentials")
                val accessCode = credentials.strictString("accessCode") ?: error("accessCode")
                val username = credentials.strictString("username") ?: error("username")
                val password = credentials.strictString("password") ?: error("password")
                require(accessCode.isNotBlank() && accessCode.length <= 64)
                require(username.isNotBlank() && username.length <= 255)
                require(password.isNotEmpty() && password.length <= 255)
                PairingClaimOutcome.Approved(
                    expiresAtEpochMs = requireServerExpiry(json),
                    retryAfterMs = requireRetryAfter(json),
                    deliveryCount = (json.strictInt("deliveryCount") ?: error("deliveryCount"))
                        .also { require(it >= 1) },
                    credentials = Credentials(
                        accessCode = accessCode,
                        username = username,
                        password = password,
                    ),
                )
            }
            "ACKED", "DENIED", "CANCELLED", "EXPIRED" -> PairingClaimOutcome.Terminal(state)
            else -> throw IllegalArgumentException("Unknown pairing state")
        }
    }.getOrNull()

    private fun requireServerExpiry(json: JSONObject): Long =
        (json.strictLong("expiresAtEpochMs") ?: error("expiresAtEpochMs")).also { require(it > 0L) }

    private fun requireRetryAfter(json: JSONObject): Long =
        (json.strictLong("retryAfterMs") ?: error("retryAfterMs")).also { require(it > 0L) }

    /**
     * Raw-type JSON accessors: no string/number coercion, no fractional truncation and no null
     * sentinel. Android's coercive getString/getInt/getLong and JSON-java's lenient paths are both
     * bypassed so decoder semantics match on device and under JVM tests.
     */
    private fun JSONObject.strictInt(key: String): Int? = when (val raw = opt(key)) {
        is Int -> raw
        is Long -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        else -> null
    }

    private fun JSONObject.strictLong(key: String): Long? = when (val raw = opt(key)) {
        is Int -> raw.toLong()
        is Long -> raw
        else -> null
    }

    private fun JSONObject.strictString(key: String): String? = opt(key) as? String

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val CREATE_SUCCESS_CODES = setOf(200, 201)

        fun defaultPairingHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
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
