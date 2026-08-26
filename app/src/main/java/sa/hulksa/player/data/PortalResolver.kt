package sa.hulksa.player.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.model.PortalConfig

class PortalResolver internal constructor(
    private val apiBaseUrl: String = BuildConfig.RESELLER_API_URL,
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun resolve(accessCode: String): PortalConfig = withContext(Dispatchers.IO) {
        val validatedCode = normalizeResellerAccessCode(accessCode)
            ?: throw PortalException.InvalidAccessCode
        val endpoint = resolveEndpoint()
        val requestBody = JSONObject()
            .put("code", validatedCode)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("Accept", "application/json")
            .header("User-Agent", "HULK-SA/${BuildConfig.VERSION_NAME}")
            .build()

        try {
            client.executeCancellable(request) { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw mapApiFailure(response.code, body)
                }

                val host = runCatching { JSONObject(body).optString("host") }
                    .getOrNull()
                    .normalizeIptvHost()
                    ?: throw PortalException.InvalidHost
                PortalConfig(host, PortalConfig.Source.ACCESS_CODE)
            }
        } catch (error: PortalException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            throw PortalException.ServiceUnavailable
        } catch (_: Exception) {
            throw PortalException.InvalidResponse
        }
    }

    private fun resolveEndpoint(): HttpUrl {
        val parsed = apiBaseUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw PortalException.ConfigurationMissing
        if (
            !parsed.isHttps ||
            parsed.username.isNotEmpty() ||
            parsed.password.isNotEmpty() ||
            parsed.querySize > 0 ||
            parsed.fragment != null
        ) {
            throw PortalException.ConfigurationMissing
        }
        return parsed.newBuilder()
            .addPathSegments("api/reseller/resolve")
            .addPathSegment("")
            .build()
    }

    private fun mapApiFailure(statusCode: Int, body: String): PortalException {
        val apiCode = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("code").orEmpty()
        }.getOrDefault("")
        return when {
            apiCode == "INVALID_CODE" || statusCode == 404 -> PortalException.InvalidAccessCode
            apiCode == "RESELLER_INACTIVE" || statusCode == 403 -> PortalException.ResellerInactive
            apiCode == "INVALID_HOST" || statusCode == 422 -> PortalException.InvalidHost
            statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599 ->
                PortalException.ServiceUnavailable
            else -> PortalException.InvalidResponse
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Access codes are opaque, case-sensitive identifiers owned by the reseller API.
 * Android only rejects an empty/blank field and otherwise forwards the exact text
 * entered by the user without trimming, case conversion, prefixing, grouping, or
 * character/length validation.
 */
internal fun normalizeResellerAccessCode(value: String): String? =
    value.takeUnless(String::isBlank)

internal fun String?.normalizeIptvHost(): String? {
    val parsed = this?.trim()?.trimEnd('/')?.toHttpUrlOrNull() ?: return null
    if (
        (parsed.scheme != "http" && parsed.scheme != "https") ||
        parsed.username.isNotEmpty() ||
        parsed.password.isNotEmpty() ||
        parsed.querySize > 0 ||
        parsed.fragment != null
    ) {
        return null
    }
    return parsed.toString().trimEnd('/')
}

sealed class PortalException(message: String) : Exception(message) {
    data object ConfigurationMissing : PortalException("تعذر الاتصال بخدمة HULK. تواصل مع الدعم.")
    data object InvalidAccessCode : PortalException("كود الدخول غير صحيح.")
    data object ResellerInactive : PortalException("حساب الموزع متوقف. تواصل مع الدعم.")
    data object InvalidHost : PortalException("هوست الموزع غير صالح. تواصل مع الموزع.")
    data object ServiceUnavailable : PortalException("تعذر الاتصال بخدمة HULK. حاول مرة اخرى.")
    data object InvalidResponse : PortalException("استجابة خدمة HULK غير صالحة. حاول مرة اخرى.")
}
