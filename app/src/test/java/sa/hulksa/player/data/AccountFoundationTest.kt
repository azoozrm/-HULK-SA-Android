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
    fun scopedPreferencesNameKeepsAccountStorageSeparate() {
        val accountA = stableAccountId("http://example.test:8080", "alpha")
        val accountB = stableAccountId("http://example.test:8080", "beta")

        assertNotEquals(
            accountScopedPreferencesName("hulk_profiles_v1", accountA),
            accountScopedPreferencesName("hulk_profiles_v1", accountB),
        )
    }

    @Test
    fun sessionExpiryHonorsMissingAndExplicitExpiry() {
        assertFalse(isAccountSessionExpired(null, nowEpochSeconds = 1_000L))
        assertFalse(isAccountSessionExpired(2_000L, nowEpochSeconds = 1_999L))
        assertTrue(isAccountSessionExpired(2_000L, nowEpochSeconds = 2_000L))
        assertTrue(isAccountSessionExpired(2_000L, nowEpochSeconds = 2_001L))
    }
}
