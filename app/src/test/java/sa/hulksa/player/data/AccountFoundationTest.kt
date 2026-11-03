package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
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
    fun trustedHostPreservesDistinctHistoricalOwnerBeforeContinuation() {
        assertEquals(AccountIdentityResolution.Known("owner-a"), resolve(
            host = "https://a.test", accounts = setOf("owner-a", "owner-b"),
            hosts = mapOf("owner-a" to setOf("https://a.test")), alias = "owner-b",
        ))
    }

    @Test
    fun unknownHostWithSoleHistoricalOwnerContinuesAutomatically() {
        assertEquals(AccountIdentityResolution.Known("owner-a"), resolve(accounts = setOf("owner-a")))
    }

    @Test
    fun currentUsernameOwnerContinuesAcrossHostChangesBeforeAliasOrLast() {
        assertEquals(AccountIdentityResolution.Known("current"), resolve(
            accounts = setOf("current", "alias", "last"), current = "current", activeUsername = "user",
            alias = "alias", last = "last", lastUsername = "user",
        ))
    }

    @Test
    fun usernameAliasThenMatchingLastOwnerGiveDeterministicContinuation() {
        assertEquals(AccountIdentityResolution.Known("alias"), resolve(
            accounts = setOf("old", "alias", "last"), alias = "alias", last = "last", lastUsername = "user",
        ))
        assertEquals(AccountIdentityResolution.Known("last"), resolve(
            accounts = setOf("old", "last"), last = "last", lastUsername = "user",
        ))
    }

    @Test
    fun anotherUsernameCannotSupplyCurrentOrLastOwner() {
        assertEquals(AccountIdentityResolution.New(stableAccountId("https://new.test", "user")), resolve(
            current = "foreign", activeUsername = "another", last = "foreign", lastUsername = "another",
        ))
    }

    @Test
    fun evidenceFreeMultipleScopesArePreservedWithoutArbitrarySelection() {
        val first = resolve(accounts = linkedSetOf("a", "b")) as AccountIdentityResolution.New
        val reversed = resolve(accounts = linkedSetOf("b", "a")) as AccountIdentityResolution.New
        assertEquals(first, reversed)
        assertTrue(first.accountId.startsWith("local-restore-"))
        assertTrue(first.accountId !in setOf("a", "b"))
    }

    @Test
    fun conflictingHostClaimsUseAssociatedContinuationOrIsolateWithoutChoosingFirst() {
        val hosts = mapOf("a" to setOf("https://new.test"), "b" to setOf("https://new.test"))
        assertEquals(AccountIdentityResolution.Known("b"), resolve(accounts=setOf("a","b"),hosts=hosts,alias="b"))
        val unresolved = resolve(accounts=setOf("a","b"),hosts=hosts) as AccountIdentityResolution.New
        assertTrue(unresolved.accountId !in setOf("a","b"))
    }

    @Test
    fun legacyDerivedHostOwnerIsRetainedWhenItsAssociationIndexIsMissing() {
        val original = stableAccountId("https://new.test", "user")
        assertEquals(AccountIdentityResolution.Known(original),resolve(accounts=setOf(original,"other")))
    }

    @Test
    fun noHistoricalOwnerCreatesANewIsolatedNamespace() {
        assertEquals(AccountIdentityResolution.New(stableAccountId("https://new.test","user")),resolve())
    }

    private fun resolve(
        host: String = "https://new.test",
        accounts: Set<String> = emptySet(),
        hosts: Map<String, Set<String>> = emptyMap(),
        current: String? = null,
        activeUsername: String? = null,
        alias: String? = null,
        last: String? = null,
        lastUsername: String? = null,
    ): AccountIdentityResolution = resolveAccountIdentity("user",host,AccountIdentitySnapshot(
        trustedAccountIds=accounts, trustedHostsByAccountId=hosts, legacyCandidateAccountIds=emptySet(),
        activeAccountId=current, activeUsername=activeUsername, activePortalBaseUrl="https://old.test",
        aliasAccountId=alias, lastAccountId=last, lastUsername=lastUsername,
    ))

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
