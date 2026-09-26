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
    fun trustedHostResolvesDirectlyToItsAccount() {
        val accountId = stableAccountId("http://first.example.test:8080", "subscriber")

        val resolution = resolveAccountIdentity(
            username = "subscriber",
            portalBaseUrl = "http://first.example.test:8080/",
            snapshot = AccountIdentitySnapshot(
                trustedAccountIds = setOf(accountId),
                trustedHostsByAccountId = mapOf(
                    accountId to setOf("http://first.example.test:8080"),
                ),
                legacyCandidateAccountIds = emptySet(),
                activeAccountId = null,
                activeUsername = null,
                activePortalBaseUrl = null,
            ),
        )

        assertEquals(AccountIdentityResolution.Known(accountId), resolution)
    }

    @Test
    fun unknownHostWithExistingCandidateStaysAmbiguous() {
        val accountId = stableAccountId("http://first.example.test:8080", "subscriber")
        val knownHost = "http://first.example.test:8080"

        val resolution = resolveAccountIdentity(
            username = "subscriber",
            portalBaseUrl = "http://second.example.test:8080",
            snapshot = AccountIdentitySnapshot(
                trustedAccountIds = setOf(accountId),
                trustedHostsByAccountId = mapOf(accountId to setOf(knownHost)),
                legacyCandidateAccountIds = emptySet(),
                activeAccountId = null,
                activeUsername = null,
                activePortalBaseUrl = null,
            ),
        )

        assertEquals(
            AccountIdentityResolution.Ambiguous(
                listOf(TrustedAccountIdentity(accountId, setOf(knownHost))),
            ),
            resolution,
        )
    }

    @Test
    fun unknownHostWithoutCandidatesStartsNewIsolatedAccount() {
        val resolution = resolveAccountIdentity(
            username = "subscriber",
            portalBaseUrl = "http://second.example.test:8080",
            snapshot = AccountIdentitySnapshot(
                trustedAccountIds = emptySet(),
                trustedHostsByAccountId = emptyMap(),
                legacyCandidateAccountIds = emptySet(),
                activeAccountId = null,
                activeUsername = null,
                activePortalBaseUrl = null,
            ),
        )

        assertEquals(
            AccountIdentityResolution.New(
                stableAccountId("http://second.example.test:8080", "subscriber"),
            ),
            resolution,
        )
    }

    @Test
    fun activeSessionHostMatchIsTrustworthySavedEvidence() {
        val accountId = stableAccountId("http://first.example.test:8080", "subscriber")

        val resolution = resolveAccountIdentity(
            username = "subscriber",
            portalBaseUrl = "http://first.example.test:8080",
            snapshot = AccountIdentitySnapshot(
                trustedAccountIds = emptySet(),
                trustedHostsByAccountId = emptyMap(),
                legacyCandidateAccountIds = setOf(accountId),
                activeAccountId = accountId,
                activeUsername = "subscriber",
                activePortalBaseUrl = "http://first.example.test:8080",
            ),
        )

        assertEquals(AccountIdentityResolution.Known(accountId), resolution)
    }

    @Test
    fun duplicateTrustedHostClaimsFailClosedToAmbiguous() {
        val first = "first-account"
        val second = "second-account"
        val sharedHost = "http://shared.example.test:8080"

        val resolution = resolveAccountIdentity(
            username = "subscriber",
            portalBaseUrl = sharedHost,
            snapshot = AccountIdentitySnapshot(
                trustedAccountIds = setOf(first, second),
                trustedHostsByAccountId = mapOf(
                    first to setOf(sharedHost),
                    second to setOf(sharedHost),
                ),
                legacyCandidateAccountIds = emptySet(),
                activeAccountId = null,
                activeUsername = null,
                activePortalBaseUrl = null,
            ),
        )

        assertTrue(resolution is AccountIdentityResolution.Ambiguous)
    }

    @Test
    fun candidateLabelsExposeOnlyPrivacySafeHostIdentity() {
        val hostAccount = "a".repeat(64)
        val legacyAccount = "b".repeat(64)
        val labels = accountIdentityCandidateLabels(
            listOf(
                TrustedAccountIdentity(
                    accountId = hostAccount,
                    trustedHosts = setOf(
                        "https://user:secret@first.example.test:8080/path?token=hidden",
                    ),
                ),
                TrustedAccountIdentity(accountId = legacyAccount, trustedHosts = emptySet()),
            ),
        )

        assertEquals(2, labels.size)
        assertEquals(
            "first.example.test:8080",
            labels.first { it.accountId == hostAccount }.label,
        )
        assertEquals(
            ACCOUNT_DECISION_LEGACY_ACCOUNT_LABEL,
            labels.first { it.accountId == legacyAccount }.label,
        )
        labels.forEach { label ->
            assertFalse(label.label.contains("http"))
            assertFalse(label.label.contains("secret"))
            assertFalse(label.label.contains("hidden"))
            assertFalse(label.label.contains(label.accountId))
        }
    }

    @Test
    fun candidateLabelsDisambiguateSchemeOnlyCollisions() {
        val secure = TrustedAccountIdentity("secure-account", setOf("https://same.example.test:8443"))
        val plain = TrustedAccountIdentity("plain-account", setOf("http://same.example.test:8443"))

        val labels = accountIdentityCandidateLabels(listOf(secure, plain))

        assertEquals(2, labels.map(AccountIdentityCandidateLabel::label).distinct().size)
    }

    @Test
    fun trustedHostDisplayLabelStripsSecretBearingParts() {
        assertEquals(
            "host.example.test:8080",
            trustedHostDisplayLabel("https://user:secret@host.example.test:8080/path?q=1#frag"),
        )
    }

    @Test
    fun decisionResolutionNeverSilentlySelectsAmongCandidates() {
        val first = "first-account"
        val second = "second-account"
        val ambiguous = AccountIdentityResolution.Ambiguous(
            listOf(
                TrustedAccountIdentity(first, setOf("http://first.example.test:8080")),
                TrustedAccountIdentity(second, setOf("http://second.example.test:8080")),
            ),
        )

        assertEquals(
            second,
            resolveDecisionAccountId(
                resolution = ambiguous,
                selectedAccountId = second,
                isolatedAccountIdProvider = { "new-account" },
            ),
        )
        assertThrows(StaleAccountIdentityDecisionException::class.java) {
            resolveDecisionAccountId(
                resolution = ambiguous,
                selectedAccountId = "unknown-account",
                isolatedAccountIdProvider = { "new-account" },
            )
        }
    }

    @Test
    fun staleDecisionCannotRebindAnAlreadyKnownHost() {
        val known = "known-account"
        assertThrows(StaleAccountIdentityDecisionException::class.java) {
            resolveDecisionAccountId(
                resolution = AccountIdentityResolution.Known(known),
                selectedAccountId = null,
                isolatedAccountIdProvider = { "different-account" },
            )
        }
        assertThrows(StaleAccountIdentityDecisionException::class.java) {
            resolveDecisionAccountId(
                resolution = AccountIdentityResolution.Known(known),
                selectedAccountId = "other-account",
                isolatedAccountIdProvider = { "different-account" },
            )
        }
    }

    @Test
    fun differentDecisionUsesANewIsolatedAccountIdOnly() {
        val ambiguous = AccountIdentityResolution.Ambiguous(
            listOf(TrustedAccountIdentity("existing-account", setOf("http://first.example.test:8080"))),
        )

        assertEquals(
            "new-account",
            resolveDecisionAccountId(
                resolution = ambiguous,
                selectedAccountId = null,
                isolatedAccountIdProvider = { "new-account" },
            ),
        )
    }

    @Test
    fun differentDecisionRejectsAGeneratedIdThatCollidesWithACandidate() {
        val ambiguous = AccountIdentityResolution.Ambiguous(
            listOf(TrustedAccountIdentity("legacy-account", trustedHosts = emptySet())),
        )

        assertThrows(StaleAccountIdentityDecisionException::class.java) {
            resolveDecisionAccountId(
                resolution = ambiguous,
                selectedAccountId = null,
                isolatedAccountIdProvider = { "legacy-account" },
            )
        }
    }

    @Test
    fun generatedIsolatedAccountIdCanNeverEqualAStableAccountId() {
        repeat(32) {
            assertNotEquals(
                stableAccountId("http://example.test:8080", "subscriber"),
                generateIsolatedAccountId(),
            )
        }
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
