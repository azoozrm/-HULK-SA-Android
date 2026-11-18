package sa.hulksa.player.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sa.hulksa.player.model.AuthenticatedSession

/**
 * NEO provider identity proven by the completed XMLTV assessment and the resolved NEO session.
 *
 * - The assessment fetched the XMLTV document from the NEO provider host `nowapp-www12.store`.
 * - The document declares `generator-info-name="NEO 4K PRO"` and
 *   `generator-info-url="nowapp-www12.store"`.
 *
 * A portal/server label, a channel name, or the mere presence of an `epg_channel_id` is never
 * sufficient. HULK and any unidentified host fail closed before network access.
 */
internal object NeoEpgProviderIdentity {
    const val PROVIDER_HOST = "nowapp-www12.store"
    const val XMLTV_GENERATOR = "NEO 4K PRO"

    fun isVerifiedSession(
        session: AuthenticatedSession,
        providerHost: String = PROVIDER_HOST,
    ): Boolean {
        val host = session.portal.baseUrl.toHttpUrlOrNull()?.host ?: return false
        return host.equals(providerHost, ignoreCase = true)
    }

    fun isVerifiedDocument(
        generatorName: String?,
        generatorUrl: String?,
        providerHost: String = PROVIDER_HOST,
    ): Boolean {
        if (generatorName?.trim() != XMLTV_GENERATOR) return false
        val documentHost = providerHostFrom(generatorUrl) ?: return false
        return documentHost.equals(providerHost, ignoreCase = true)
    }

    internal fun providerHostFrom(rawValue: String?): String? {
        val value = rawValue?.trim().orEmpty()
        if (value.isEmpty()) return null
        val withScheme = if (
            value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
        ) {
            value
        } else {
            "http://$value"
        }
        return withScheme.toHttpUrlOrNull()?.host
    }
}

/** Single production gate used before any EPG network work is admitted. */
internal fun epgRequestAllowed(session: AuthenticatedSession): Boolean =
    NeoEpgProviderIdentity.isVerifiedSession(session)

/** Account/provider scope; profiles do not change the provider schedule. */
internal data class EpgScope(
    val accountId: String,
    val providerId: String,
)
