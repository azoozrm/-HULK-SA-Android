package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountFoundationTest {
    @Test
    fun stableAccountIdIgnoresTrailingPortalSlash() {
        val first = stableAccountId("http://example.test:8080", "alpha")
        val second = stableAccountId("http://example.test:8080/", "alpha")

        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun stableAccountIdSeparatesAccountsWithoutUsingPassword() {
        val alpha = stableAccountId("http://example.test:8080", "alpha")
        val beta = stableAccountId("http://example.test:8080", "beta")
        val otherPortal = stableAccountId("http://other.example.test:8080", "alpha")

        assertNotEquals(alpha, beta)
        assertNotEquals(alpha, otherPortal)
    }

    @Test
    fun authenticationIdentityKeepsSameSubscriberScopeAcrossPortalChanges() {
        val existingAccountId = stableAccountId("http://first.example.test:8080", "subscriber")

        val resolved = resolveAccountIdForAuthentication(
            portalBaseUrl = "http://second.example.test:8080",
            username = "subscriber",
            aliasedAccountId = null,
            currentAccountId = null,
            currentUsername = null,
            lastAccountId = existingAccountId,
            lastUsername = "subscriber",
        )

        assertEquals(existingAccountId, resolved)
    }

    @Test
    fun authenticationIdentityDoesNotReuseDifferentUsernameScope() {
        val existingAccountId = stableAccountId("http://first.example.test:8080", "subscriber")
        val expected = stableAccountId("http://second.example.test:8080", "another-user")

        val resolved = resolveAccountIdForAuthentication(
            portalBaseUrl = "http://second.example.test:8080",
            username = "another-user",
            aliasedAccountId = null,
            currentAccountId = null,
            currentUsername = null,
            lastAccountId = existingAccountId,
            lastUsername = "subscriber",
        )

        assertEquals(expected, resolved)
        assertNotEquals(existingAccountId, resolved)
    }

    @Test
    fun usernameAliasIsCaseSensitive() {
        assertNotEquals(
            accountIdentityAliasKey("Subscriber"),
            accountIdentityAliasKey("subscriber"),
        )
    }

    @Test
    fun scopedPreferencesNameKeepsAccountStorageSeparate() {
        val accountA = stableAccountId("http://example.test:8080", "alpha")
        val accountB = stableAccountId("http://example.test:8080", "beta")

        assertNotEquals(
            accountScopedPreferencesName("hulk_profiles_v1", accountA),
            accountScopedPreferencesName("hulk_profiles_v1", accountB),
        )
    }

    @Test
    fun sessionExpiryHonorsMissingZeroAndExplicitExpiry() {
        assertFalse(isAccountSessionExpired(null, nowEpochSeconds = 1_000L))
        assertFalse(isAccountSessionExpired(0L, nowEpochSeconds = 1_000L))
        assertFalse(isAccountSessionExpired(2_000L, nowEpochSeconds = 1_999L))
        assertTrue(isAccountSessionExpired(2_000L, nowEpochSeconds = 2_000L))
        assertTrue(isAccountSessionExpired(2_000L, nowEpochSeconds = 2_001L))
    }

    @Test
    fun resumeRevalidationSkipsFreshServerValidation() {
        assertFalse(
            shouldRevalidateAccountOnResume(
                authenticatedAtEpochMs = 900_000L,
                lastAttemptElapsedMs = 0L,
                nowEpochMs = 1_000_000L,
                nowElapsedMs = 5_000L,
                minimumAgeMs = 600_000L,
            ),
        )
    }

    @Test
    fun resumeRevalidationRunsWhenServerValidationIsStale() {
        assertTrue(
            shouldRevalidateAccountOnResume(
                authenticatedAtEpochMs = 300_000L,
                lastAttemptElapsedMs = 0L,
                nowEpochMs = 1_000_000L,
                nowElapsedMs = 700_000L,
                minimumAgeMs = 600_000L,
            ),
        )
    }

    @Test
    fun resumeRevalidationThrottlesRecentFailedAttempt() {
        assertFalse(
            shouldRevalidateAccountOnResume(
                authenticatedAtEpochMs = 100_000L,
                lastAttemptElapsedMs = 650_000L,
                nowEpochMs = 1_000_000L,
                nowElapsedMs = 700_000L,
                minimumAgeMs = 600_000L,
            ),
        )
    }

    @Test
    fun resumeRevalidationRequiresServerCheckWhenAuthenticationMetadataIsMissing() {
        assertTrue(
            shouldRevalidateAccountOnResume(
                authenticatedAtEpochMs = 0L,
                lastAttemptElapsedMs = 0L,
                nowEpochMs = 1_000_000L,
                nowElapsedMs = 700_000L,
                minimumAgeMs = 600_000L,
            ),
        )
    }

    @Test
    fun resumeRevalidationRequiresServerCheckAfterWallClockRollback() {
        assertTrue(
            shouldRevalidateAccountOnResume(
                authenticatedAtEpochMs = 1_100_000L,
                lastAttemptElapsedMs = 0L,
                nowEpochMs = 1_000_000L,
                nowElapsedMs = 700_000L,
                minimumAgeMs = 600_000L,
            ),
        )
    }
}
