package sa.hulksa.player.data

import java.util.Locale
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

/**
 * Process-local expectation attached to one outgoing provider call. The tag never reaches the
 * wire; it lets the passive OkHttp observer compare each actual exchange with the authenticated
 * session that constructed the request.
 */
internal class ProviderExchangeContext(
    val attempt: ProviderAttempt?,
    val expectedPortal: PortalConfig,
    val expectedCredentials: Credentials,
    val action: String,
) {
    override fun toString(): String =
        "ProviderExchangeContext(attempt=${attempt?.id ?: "none"}, action=$action)"
}

internal class ProviderExchangeObserverFactory : EventListener.Factory {
    override fun create(call: Call): EventListener {
        val context = call.request().tag(ProviderExchangeContext::class.java)
            ?: return EventListener.NONE
        return ProviderExchangeObserver(context)
    }
}

/**
 * Observation-only. Reads request URLs and response headers after OkHttp has built them; it
 * never changes headers, redirects, retries, bodies, parsing or result handling. All emitted
 * fields are bounded booleans, closed enums, numeric counters or allowlisted sanitized headers.
 */
private class ProviderExchangeObserver(
    private val context: ProviderExchangeContext,
) : EventListener() {
    private var exchangeIndex = 0
    private var hasPreviousExchange = false
    private var previousEndpoint: EndpointIdentity? = null
    private var previousUsername: String? = null
    private var previousPassword: String? = null

    override fun requestHeadersEnd(call: Call, request: Request) {
        runCatching { recordRequest(request) }
    }

    private fun recordRequest(request: Request) {
        val index = exchangeIndex++
        val actual = request.url
        val expected = expectedEndpoint(context.expectedPortal)
        val actualEndpoint = EndpointIdentity.of(actual)
        val expectedIdentity = expected?.let(EndpointIdentity::of)
        val actualUsername = actual.queryParameter("username")
        val actualPassword = actual.queryParameter("password")
        val expectedUsername = context.expectedCredentials.username
        val expectedPassword = context.expectedCredentials.password
        val previous = previousEndpoint
        val changedEndpoint = previous != null && previous != actualEndpoint
        val changedScheme = previous != null && previous.scheme != actualEndpoint.scheme
        val changedHost = previous != null && previous.host != actualEndpoint.host
        val changedPort = previous != null && previous.port != actualEndpoint.port
        val changedPath = previous != null && previous.path != actualEndpoint.path
        val changedUsername = hasPreviousExchange && previousUsername != actualUsername
        val changedPassword = hasPreviousExchange && previousPassword != actualPassword
        previousEndpoint = actualEndpoint
        previousUsername = actualUsername
        previousPassword = actualPassword
        hasPreviousExchange = true

        ProviderRequestTrace.exchange(
            "request",
            context = context,
            index = index,
            fields = buildString {
                append("endpoint_equal=")
                append(expectedIdentity != null && expectedIdentity == actualEndpoint)
                append(" scheme_equal=")
                append(expectedIdentity?.scheme == actualEndpoint.scheme)
                append(" host_equal=")
                append(expectedIdentity?.host == actualEndpoint.host)
                append(" port_equal=")
                append(expectedIdentity?.port == actualEndpoint.port)
                append(" path_equal=")
                append(expectedIdentity?.path == actualEndpoint.path)
                append(" action_equal=")
                append(actual.queryParameter("action") == context.action.takeUnless { it == "auth" })
                append(" username_equal=")
                append(actualUsername != null && actualUsername == expectedUsername)
                append(" password_equal=")
                append(actualPassword != null && actualPassword == expectedPassword)
                append(" credentials_present=")
                append(!actualUsername.isNullOrEmpty() && !actualPassword.isNullOrEmpty())
                append(" username_missing=")
                append(actualUsername.isNullOrEmpty())
                append(" password_missing=")
                append(actualPassword.isNullOrEmpty())
                append(" endpoint_changed=")
                append(changedEndpoint)
                append(" scheme_changed=")
                append(changedScheme)
                append(" host_changed=")
                append(changedHost)
                append(" port_changed=")
                append(changedPort)
                append(" path_changed=")
                append(changedPath)
                append(" username_changed=")
                append(changedUsername)
                append(" password_changed=")
                append(changedPassword)
            },
        )
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        runCatching { recordResponse(response) }
    }

    private fun recordResponse(response: Response) {
        val index = (exchangeIndex - 1).coerceAtLeast(0)
        ProviderRequestTrace.exchange(
            "response",
            context = context,
            index = index,
            fields = buildString {
                append("code=")
                append(response.code)
                append(" retry_after=")
                append(sanitizedHeader(response, "Retry-After"))
                append(" date=")
                append(sanitizedHeader(response, "Date"))
                append(" server=")
                append(sanitizedHeader(response, "Server"))
                append(" cf_ray=")
                append(sanitizedHeader(response, "CF-Ray"))
                append(" cf_mitigated=")
                append(sanitizedHeader(response, "CF-Mitigated"))
            },
        )
    }

    override fun canceled(call: Call) {
        runCatching {
            ProviderRequestTrace.exchange(
                "terminal",
                context = context,
                index = (exchangeIndex - 1).coerceAtLeast(0),
                fields = "outcome=CANCELED exchanges=$exchangeIndex",
            )
        }
    }

    override fun callFailed(call: Call, ioe: java.io.IOException) {
        runCatching {
            ProviderRequestTrace.exchange(
                "terminal",
                context = context,
                index = (exchangeIndex - 1).coerceAtLeast(0),
                fields = "outcome=NETWORK exchanges=$exchangeIndex",
            )
        }
    }

    private fun expectedEndpoint(portal: PortalConfig): HttpUrl? {
        val base = portal.baseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        val path = base.encodedPath.trimEnd('/') + "/player_api.php"
        return base.newBuilder().encodedPath(path).query(null).fragment(null).build()
    }

    private fun sanitizedHeader(response: Response, name: String): String {
        val raw = response.header(name) ?: return "none"
        if (name == "Server" || name == "CF-Mitigated") {
            return when (name) {
                "Server" -> serverClassification(raw)
                else -> mitigationClassification(raw)
            }
        }
        if (raw.length > MAX_HEADER_CHARS || containsForbiddenMaterial(raw)) return "rejected"
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "invalid"
        val cleaned = trimmed.replace(Regex("[\\p{Cntrl}\\s]+"), "_")
        return when (name) {
            "Retry-After" -> when {
                RETRY_SECONDS.matches(trimmed) -> trimmed
                HTTP_DATE.matches(trimmed) -> cleaned
                else -> "invalid"
            }
            "Date" -> if (HTTP_DATE.matches(trimmed)) cleaned else "invalid"
            "CF-Ray" -> if (CF_RAY_VALUE.matches(trimmed)) cleaned else "invalid"
            else -> "invalid"
        }
    }

    private fun serverClassification(value: String): String {
        if (value.length > MAX_HEADER_CHARS || containsForbiddenMaterial(value)) return "other"
        return if (value.trim().equals("cloudflare", ignoreCase = true)) "cloudflare" else "other"
    }

    private fun mitigationClassification(value: String): String {
        if (value.length > MAX_HEADER_CHARS || containsForbiddenMaterial(value)) return "other"
        return when (value.trim().lowercase(Locale.US)) {
            "challenge" -> "challenge"
            "managed_challenge" -> "managed_challenge"
            "js_challenge" -> "js_challenge"
            else -> "other"
        }
    }

    private fun containsForbiddenMaterial(value: String): Boolean {
        val lowered = value.lowercase(Locale.US)
        if (
            "://" in value || '?' in value || '#' in value || '@' in value ||
            '&' in value || '=' in value || '%' in value
        ) {
            return true
        }
        val sensitive = listOf(
            context.expectedCredentials.accessCode,
            context.expectedCredentials.username,
            context.expectedCredentials.password,
            context.expectedPortal.baseUrl,
        )
        return sensitive.any { it.isNotBlank() && lowered.contains(it.lowercase(Locale.US)) }
    }

    private data class EndpointIdentity(
        val scheme: String,
        val host: String,
        val port: Int,
        val path: String,
    ) {
        companion object {
            fun of(url: HttpUrl) = EndpointIdentity(
                scheme = url.scheme.lowercase(Locale.US),
                host = url.host.lowercase(Locale.US),
                port = url.port,
                path = url.encodedPath,
            )
        }
    }

    private companion object {
        const val MAX_HEADER_CHARS = 160
        val RETRY_SECONDS = Regex("""^\d{1,10}$""")
        val HTTP_DATE = Regex("""^[A-Z][a-z]{2}, \d{2} [A-Z][a-z]{2} \d{4} \d{2}:\d{2}:\d{2} GMT$""")
        val CF_RAY_VALUE = Regex("""^[0-9A-Fa-f]{8,32}-[A-Z]{3}$""")
    }
}
