package sa.hulksa.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class EpgProviderGateTest {
    @Test
    fun hulkAndUnknownSessionsAreRejectedEvenWithNeoLookingLabels() {
        val hulk = session(
            baseUrl = "http://stotre5.online:8080",
            username = "NEO 4K PRO",
        )
        assertFalse(epgRequestAllowed(hulk))
        assertFalse(NeoEpgProviderIdentity.isVerifiedSession(hulk))

        val unknown = session(baseUrl = "http://example.invalid")
        assertFalse(epgRequestAllowed(unknown))
        assertFalse(NeoEpgProviderIdentity.isVerifiedSession(unknown))
    }

    @Test
    fun onlyTheExactResolvedNeoProviderHostIsAccepted() {
        assertTrue(epgRequestAllowed(session("http://nowapp-www12.store")))
        assertTrue(epgRequestAllowed(session("https://nowapp-www12.store/")))
        assertFalse(epgRequestAllowed(session("http://www.nowapp-www12.store")))
        assertFalse(epgRequestAllowed(session("http://nowapp-www12.store.evil.example")))
        assertFalse(epgRequestAllowed(session("http://nowapp-www13.store")))
    }

    @Test
    fun accountUsernameOrChannelLikeTextCannotSubstituteForProviderIdentity() {
        // The gate has no catalog/channel input at all; the resolved host is the only session fact.
        val wrongHostWithNeoAccount = session(
            baseUrl = "http://stotre5.online:8080",
            username = "NEO",
        )
        assertFalse(epgRequestAllowed(wrongHostWithNeoAccount))
    }

    @Test
    fun xmltvIdentityRequiresBothExactGeneratorAndProviderHost() {
        assertTrue(
            NeoEpgProviderIdentity.isVerifiedDocument(
                generatorName = "NEO 4K PRO",
                generatorUrl = "nowapp-www12.store",
            ),
        )
        assertTrue(
            NeoEpgProviderIdentity.isVerifiedDocument(
                generatorName = "NEO 4K PRO",
                generatorUrl = "https://nowapp-www12.store/",
            ),
        )
        assertFalse(
            NeoEpgProviderIdentity.isVerifiedDocument(
                generatorName = "neo 4k pro",
                generatorUrl = "nowapp-www12.store",
            ),
        )
        assertFalse(
            NeoEpgProviderIdentity.isVerifiedDocument(
                generatorName = "NEO 4K PRO",
                generatorUrl = "hulk.example",
            ),
        )
        assertFalse(
            NeoEpgProviderIdentity.isVerifiedDocument(
                generatorName = "NEO 4K PRO",
                generatorUrl = null,
            ),
        )
        assertFalse(
            NeoEpgProviderIdentity.isVerifiedDocument(
                generatorName = null,
                generatorUrl = "nowapp-www12.store",
            ),
        )
    }

    private fun session(
        baseUrl: String,
        username: String = "synthetic-user",
    ): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(baseUrl, PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials(
            accessCode = "SYNTHETIC-CODE",
            username = username,
            password = "SYNTHETIC-PASSWORD",
        ),
        account = AccountInfo(
            username = username,
            status = "Active",
            expiresAtEpochSeconds = null,
            activeConnections = 1,
            maxConnections = 2,
            isTrial = false,
        ),
    )
}
