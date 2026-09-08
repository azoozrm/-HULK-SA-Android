package sa.hulksa.player.data

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class ContentMetadataOwnershipTest {
    @After
    fun clearRegistry() {
        AuthenticatedSessionRegistry.clear()
    }

    @Test
    fun sameNumericContentIdIsolatedAcrossProviderAccounts() {
        val providerA = ContentMetadataRequestKey(
            accountId = "account-a",
            providerId = "provider-a",
            sessionId = "session-a",
            type = ContentType.MOVIE,
            contentId = 42,
        )
        val providerB = providerA.copy(
            providerId = "provider-b",
            sessionId = "session-b",
        )

        assertNotEquals(providerA, providerB)
    }

    @Test
    fun reloginMakesOldMetadataAndKidsOwnerStaleEvenForSameAccount() {
        val firstSession = session("https://provider.example", "subscriber")
        AuthenticatedSessionRegistry.update(firstSession, metadata("account-a", "session-a"))
        val firstOwner = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
        val firstKey = firstOwner.contentMetadataKey(ContentType.SERIES, 42)

        val replacementSession = session("https://provider.example", "subscriber")
        AuthenticatedSessionRegistry.update(
            replacementSession,
            metadata("account-a", "session-b"),
        )
        val replacementOwner = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
        val replacementKey = replacementOwner.contentMetadataKey(ContentType.SERIES, 42)

        assertFalse(AuthenticatedSessionRegistry.isCurrent(firstOwner))
        assertTrue(AuthenticatedSessionRegistry.isCurrent(replacementOwner))
        assertSame(replacementSession, AuthenticatedSessionRegistry.current())
        assertNotEquals(firstKey, replacementKey)

        AuthenticatedSessionRegistry.clear()
        assertFalse(AuthenticatedSessionRegistry.isCurrent(replacementOwner))
    }

    private fun session(portal: String, username: String) = AuthenticatedSession(
        portal = PortalConfig(portal, PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials(
            accessCode = "abcdefghij",
            username = username,
            password = "secret",
        ),
        account = AccountInfo(
            username = username,
            status = "Active",
            expiresAtEpochSeconds = null,
            activeConnections = 0,
            maxConnections = 1,
            isTrial = false,
        ),
    )

    private fun metadata(accountId: String, sessionId: String) = AccountSessionMetadata(
        accountId = accountId,
        username = "subscriber",
        portalBaseUrl = "https://provider.example",
        authenticatedAtEpochMs = 1L,
        expiresAtEpochSeconds = null,
        status = "Active",
        installationId = "installation-a",
        sessionId = sessionId,
    )
}
