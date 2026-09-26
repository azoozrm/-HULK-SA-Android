package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

private const val HOST_A = "http://first.example.test:8080"
private const val HOST_B = "http://second.example.test:8080"
private const val HOST_C = "http://third.example.test:8080"
private const val HOST_D = "http://fourth.example.test:8080"
private const val USERNAME = "subscriber"
private const val OTHER_USERNAME = "other-subscriber"

class TrustedHostSubscriberIdentityTest {
    @Test
    fun firstLoginCreatesAndTrustsOnlyItsOwnAccount() {
        val environment = environment()
        val expected = stableAccountId(HOST_A, USERNAME)

        assertEquals(
            AccountIdentityResolution.New(expected),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_A),
        )

        val metadata = environment.store.recordAuthenticated(session(HOST_A, "CODE-1"), expected)

        assertEquals(expected, metadata.accountId)
        assertEquals(expected, environment.store.activeAccountId())
        assertEquals(
            AccountIdentityResolution.Known(expected),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_A),
        )
    }

    @Test
    fun trustedHostsResolveDirectlyWithoutAnyPrompt() {
        val environment = environment()
        val accountId = record(environment, HOST_A, "CODE-1")
        record(environment, HOST_B, "CODE-2", accountId)

        assertEquals(
            AccountIdentityResolution.Known(accountId),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_A),
        )
        assertEquals(
            AccountIdentityResolution.Known(accountId),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_B),
        )
    }

    @Test
    fun unknownHostWithSingleCandidateRequiresAnExplicitDecision() {
        val environment = environment()
        val accountId = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()

        assertEquals(
            AccountIdentityResolution.Ambiguous(
                listOf(TrustedAccountIdentity(accountId, setOf(HOST_A))),
            ),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_B),
        )
        assertNull(environment.store.metadata())
        assertNull(environment.store.activeAccountId())
    }

    @Test
    fun sameDecisionKeepsTheAccountScopeAndTrustsTheNewHost() {
        val environment = environment()
        val accountId = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()

        assertTrue(
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_B)
                is AccountIdentityResolution.Ambiguous,
        )

        environment.store.recordAuthenticated(session(HOST_B, "CODE-2"), accountId)

        assertEquals(accountId, environment.store.activeAccountId())
        assertEquals(
            AccountIdentityResolution.Known(accountId),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_A),
        )
        assertEquals(
            AccountIdentityResolution.Known(accountId),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_B),
        )
    }

    @Test
    fun differentDecisionCreatesAnIsolatedAccountAndNeverCopiesData() {
        val environment = environment()
        val accountA = record(environment, HOST_A, "CODE-1")
        val profilePreferencesA = environment.accountScope.preferences("hulk_profiles_v1")
        profilePreferencesA.edit().putString("profiles", "A-ONLY").commit()
        environment.store.clearActiveSession()

        val accountB = stableAccountId(HOST_B, USERNAME)
        environment.store.recordAuthenticated(session(HOST_B, "CODE-2"), accountB)

        assertNotEquals(accountA, accountB)
        assertEquals(accountB, environment.store.activeAccountId())
        assertEquals(
            AccountIdentityResolution.Known(accountA),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_A),
        )
        assertEquals(
            AccountIdentityResolution.Known(accountB),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_B),
        )

        val profilePreferencesB = environment.accountScope.preferences("hulk_profiles_v1")
        assertNull(profilePreferencesB.getString("profiles", null))
        assertEquals("A-ONLY", profilePreferencesA.getString("profiles", null))
    }

    @Test
    fun returningToTrustedHostsResolvesEachAccountWithoutCrossover() {
        val environment = environment()
        val accountA = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()
        val accountB = stableAccountId(HOST_B, USERNAME)
        environment.store.recordAuthenticated(session(HOST_B, "CODE-2"), accountB)
        environment.store.clearActiveSession()

        assertEquals(
            AccountIdentityResolution.Known(accountA),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_A),
        )
        assertEquals(
            AccountIdentityResolution.Known(accountB),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_B),
        )

        environment.store.recordAuthenticated(session(HOST_A, "CODE-3"), accountA)

        assertEquals(accountA, environment.store.activeAccountId())
        assertEquals(
            AccountIdentityResolution.Known(accountB),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_B),
        )
    }

    @Test
    fun differentUsernameNeverInheritsAnotherAccountScope() {
        val environment = environment()
        val accountA = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()

        assertEquals(
            AccountIdentityResolution.New(stableAccountId(HOST_A, OTHER_USERNAME)),
            environment.store.resolveAuthenticationIdentity(OTHER_USERNAME, HOST_A),
        )
        assertEquals(
            AccountIdentityResolution.Known(accountA),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_A),
        )
    }

    @Test
    fun accessCodeChangeAloneDoesNotCreateANewAccount() {
        val environment = environment()
        val accountId = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()

        assertEquals(
            AccountIdentityResolution.Known(accountId),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_A),
        )

        val recorded = environment.store.recordAuthenticated(session(HOST_A, "CODE-ROTATED"))

        assertEquals(accountId, recorded.accountId)
        assertEquals("CODE-ROTATED", environment.store.lastAccessCode())
    }

    @Test
    fun pendingAmbiguityAndCancelWriteNothing() {
        val environment = environment()
        record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()
        val restarted = environment.restart()
        val before = restarted.sessionPreferences.durableSnapshot()

        assertTrue(
            restarted.store.resolveAuthenticationIdentity(USERNAME, HOST_B)
                is AccountIdentityResolution.Ambiguous,
        )

        assertEquals(before, restarted.sessionPreferences.durableSnapshot())
        assertNull(restarted.store.metadata())
        assertNull(restarted.store.activeAccountId())
    }

    @Test
    fun processRecreationDoesNotResolveAmbiguityByItself() {
        val environment = environment()
        val accountA = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()

        val restarted = environment.restart()

        assertTrue(
            restarted.store.resolveAuthenticationIdentity(USERNAME, HOST_B)
                is AccountIdentityResolution.Ambiguous,
        )
        assertEquals(
            AccountIdentityResolution.Known(accountA),
            restarted.store.resolveAuthenticationIdentity(USERNAME, HOST_A),
        )
        assertNull(restarted.store.metadata())
        assertNull(restarted.store.activeAccountId())
    }

    @Test
    fun legacyActiveSessionHostIsMigratedAsTrustworthyEvidenceOnly() {
        val environment = environment()
        val accountId = stableAccountId(HOST_A, USERNAME)
        seedLegacyActiveSession(environment, accountId)

        assertEquals(
            AccountIdentityResolution.Known(accountId),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_A),
        )

        environment.store.recordAuthenticated(session(HOST_A, "CODE-1"))
        val restarted = environment.restart()

        assertEquals(
            AccountIdentityResolution.Known(accountId),
            restarted.store.resolveAuthenticationIdentity(USERNAME, HOST_A),
        )
        assertTrue(
            restarted.store.resolveAuthenticationIdentity(USERNAME, HOST_B)
                is AccountIdentityResolution.Ambiguous,
        )
    }

    @Test
    fun legacyAliasWithoutHostEvidenceDoesNotInventATrustedHost() {
        val environment = environment()
        val accountId = stableAccountId(HOST_A, USERNAME)
        environment.sessionPreferences.edit()
            .putString(accountIdentityAliasKey(USERNAME), accountId)
            .putString("last_username", USERNAME)
            .putString("last_account_id", accountId)
            .commit()

        assertEquals(
            AccountIdentityResolution.Ambiguous(
                listOf(TrustedAccountIdentity(accountId, emptySet())),
            ),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_B),
        )
        assertTrue(
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_A)
                is AccountIdentityResolution.Ambiguous,
        )
    }

    @Test
    fun alreadyCollidedScopedDataIsNeverRewrittenByMigration() {
        val environment = environment()
        val accountA = stableAccountId(HOST_A, USERNAME)
        val accountB = stableAccountId(HOST_B, USERNAME)
        val preferencesA = environment.scopedPreferences.getOrPut(
            accountScopedPreferencesName("hulk_user_library", accountA),
        ) { FakeSharedPreferences() }
        val preferencesB = environment.scopedPreferences.getOrPut(
            accountScopedPreferencesName("hulk_user_library", accountB),
        ) { FakeSharedPreferences() }
        preferencesA.edit().putStringSet("profile:primary:favorites", setOf("MOVIE:7")).commit()
        preferencesB.edit().putStringSet("profile:primary:favorites", setOf("MOVIE:9")).commit()

        environment.store.recordAuthenticated(session(HOST_A, "CODE-1"), accountA)
        environment.store.clearActiveSession()
        environment.store.recordAuthenticated(session(HOST_B, "CODE-2"), accountB)

        assertEquals(
            setOf("MOVIE:7"),
            preferencesA.getStringSet("profile:primary:favorites", mutableSetOf()),
        )
        assertEquals(
            setOf("MOVIE:9"),
            preferencesB.getStringSet("profile:primary:favorites", mutableSetOf()),
        )
    }

    @Test
    fun trustedHostCommitFailureLeavesNoPartialOwnership() {
        val environment = environment()
        environment.sessionPreferences.commitResult = false
        val accountId = stableAccountId(HOST_A, USERNAME)

        assertThrows(IllegalStateException::class.java) {
            environment.store.recordAuthenticated(session(HOST_A, "CODE-1"), accountId)
        }

        val restarted = environment.restart()
        restarted.store.clearActiveSession()
        val durable = restarted.restart().sessionPreferences.durableSnapshot()

        assertFalse(
            durable.keys.any {
                it.startsWith("trusted_accounts_") || it.startsWith("trusted_hosts_")
            },
        )
        assertFalse(durable.containsKey("account_id"))
        assertFalse(durable.containsKey("last_access_code"))
    }

    @Test
    fun twoSameUsernameAccountsProduceADeterministicExplicitCandidateSet() {
        val environment = environment()
        val accountA = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()
        val accountB = stableAccountId(HOST_B, USERNAME)
        environment.store.recordAuthenticated(session(HOST_B, "CODE-2"), accountB)
        environment.store.clearActiveSession()

        val first = ambiguous(environment, HOST_C)
        val second = ambiguous(environment, HOST_C)

        assertEquals(setOf(accountA, accountB), first.candidates.map { it.accountId }.toSet())
        assertEquals(first, second)
        assertEquals(
            mapOf(accountA to setOf(HOST_A), accountB to setOf(HOST_B)),
            first.candidates.associate { it.accountId to it.trustedHosts },
        )
    }

    @Test
    fun selectingOneCandidateTrustsOnlyThatCandidate() {
        val environment = environment()
        val accountA = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()
        val accountB = stableAccountId(HOST_B, USERNAME)
        environment.store.recordAuthenticated(session(HOST_B, "CODE-2"), accountB)
        environment.store.clearActiveSession()

        environment.store.recordAuthenticated(session(HOST_C, "CODE-3"), accountA)

        assertEquals(
            AccountIdentityResolution.Known(accountA),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_C),
        )
        val remaining = ambiguous(environment, HOST_D).candidates.associateBy { it.accountId }
        assertEquals(setOf(HOST_A, HOST_C), remaining.getValue(accountA).trustedHosts)
        assertEquals(setOf(HOST_B), remaining.getValue(accountB).trustedHosts)
    }

    @Test
    fun selectingTheOtherCandidateTrustsOnlyThatCandidate() {
        val environment = environment()
        val accountA = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()
        val accountB = stableAccountId(HOST_B, USERNAME)
        environment.store.recordAuthenticated(session(HOST_B, "CODE-2"), accountB)
        environment.store.clearActiveSession()

        environment.store.recordAuthenticated(session(HOST_C, "CODE-3"), accountB)

        assertEquals(
            AccountIdentityResolution.Known(accountB),
            environment.store.resolveAuthenticationIdentity(USERNAME, HOST_C),
        )
        val remaining = ambiguous(environment, HOST_D).candidates.associateBy { it.accountId }
        assertEquals(setOf(HOST_A), remaining.getValue(accountA).trustedHosts)
        assertEquals(setOf(HOST_B, HOST_C), remaining.getValue(accountB).trustedHosts)
    }

    @Test
    fun differentAfterTwoCandidatesCreatesAnIsolatedThirdAccount() {
        val environment = environment()
        val accountA = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()
        val accountB = stableAccountId(HOST_B, USERNAME)
        environment.store.recordAuthenticated(session(HOST_B, "CODE-2"), accountB)
        environment.store.clearActiveSession()
        val accountC = stableAccountId(HOST_C, USERNAME)
        environment.store.recordAuthenticated(session(HOST_C, "CODE-3"), accountC)
        environment.store.clearActiveSession()

        val candidates = ambiguous(environment, HOST_D).candidates.associateBy { it.accountId }

        assertEquals(setOf(accountA, accountB, accountC), candidates.keys)
        assertEquals(setOf(HOST_A), candidates.getValue(accountA).trustedHosts)
        assertEquals(setOf(HOST_B), candidates.getValue(accountB).trustedHosts)
        assertEquals(setOf(HOST_C), candidates.getValue(accountC).trustedHosts)
    }

    @Test
    fun candidateLabelsExposeNoAccountIdCredentialOrSecretUrlParts() {
        val environment = environment()
        val accountA = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()
        val accountB = stableAccountId(HOST_B, USERNAME)
        environment.store.recordAuthenticated(session(HOST_B, "CODE-2"), accountB)
        environment.store.clearActiveSession()

        val labels = accountIdentityCandidateLabels(ambiguous(environment, HOST_C).candidates)
        val rendered = labels.joinToString(" | ") { it.label }

        assertTrue(rendered.contains("first.example.test"))
        assertTrue(rendered.contains("second.example.test"))
        labels.forEach { label -> assertFalse(label.label.contains(label.accountId)) }
        assertFalse(rendered.contains("CODE-"))
        assertFalse(rendered.contains("pass-"))
        assertFalse(rendered.contains("http"))
        assertFalse(rendered.contains("@"))
    }

    @Test
    fun continuationKeepsTheCurrentOwnerAndFailsClosedOnAForeignHost() {
        val environment = environment()
        val accountA = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()
        val accountB = stableAccountId(HOST_B, USERNAME)
        environment.store.recordAuthenticated(session(HOST_B, "CODE-2"), accountB)

        assertEquals(
            accountB,
            environment.store.continuationAccountId(USERNAME, HOST_C, accountB),
        )
        assertNull(environment.store.continuationAccountId(USERNAME, HOST_A, accountB))
        assertEquals(
            accountA,
            environment.store.continuationAccountId(USERNAME, HOST_A, accountA),
        )
    }

    @Test
    fun trustedAssociationsAndCandidatesSurviveRestart() {
        val environment = environment()
        val accountA = record(environment, HOST_A, "CODE-1")
        environment.store.clearActiveSession()
        val accountB = stableAccountId(HOST_B, USERNAME)
        environment.store.recordAuthenticated(session(HOST_B, "CODE-2"), accountB)
        environment.store.clearActiveSession()
        environment.store.recordAuthenticated(session(HOST_C, "CODE-3"), accountA)

        val restarted = environment.restart()

        assertEquals(
            AccountIdentityResolution.Known(accountA),
            restarted.store.resolveAuthenticationIdentity(USERNAME, HOST_C),
        )
        assertEquals(
            setOf(accountA, accountB),
            (restarted.store.resolveAuthenticationIdentity(USERNAME, HOST_D)
                as AccountIdentityResolution.Ambiguous)
                .candidates
                .map { it.accountId }
                .toSet(),
        )
    }

    private fun seedLegacyActiveSession(environment: Environment, accountId: String) {
        environment.scopePreferences.edit()
            .putString("active_account_id", accountId)
            .putString("legacy_owner_account_id", accountId)
            .commit()
        environment.sessionPreferences.edit()
            .putString("account_id", accountId)
            .putString("username", USERNAME)
            .putString("portal_base_url", HOST_A)
            .putLong("authenticated_at_epoch_ms", 1L)
            .putLong("expires_at_epoch_seconds", -1L)
            .putString("status", "Active")
            .putString("installation_id", "installation-a")
            .putString("session_id", "session-a")
            .putString(accountIdentityAliasKey(USERNAME), accountId)
            .commit()
    }

    private fun record(
        environment: Environment,
        host: String,
        accessCode: String,
        accountId: String? = null,
    ): String {
        val resolvedAccountId = accountId ?: stableAccountId(host, USERNAME)
        return environment.store.recordAuthenticated(session(host, accessCode), resolvedAccountId)
            .accountId
    }

    private fun ambiguous(
        environment: Environment,
        host: String,
    ): AccountIdentityResolution.Ambiguous =
        environment.store.resolveAuthenticationIdentity(USERNAME, host)
            as AccountIdentityResolution.Ambiguous

    private fun session(
        host: String,
        accessCode: String,
        username: String = USERNAME,
    ): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(host, PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials(
            accessCode = accessCode,
            username = username,
            password = "pass-$username",
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

    private fun environment(): Environment = Environment(
        scopePreferences = FakeSharedPreferences(),
        sessionPreferences = FakeSharedPreferences(),
        scopedPreferences = mutableMapOf(),
    )

    private class Environment(
        val scopePreferences: FakeSharedPreferences,
        val sessionPreferences: FakeSharedPreferences,
        val scopedPreferences: MutableMap<String, FakeSharedPreferences>,
    ) {
        val accountScope = AccountScopeStore(scopePreferences) { name ->
            scopedPreferences.getOrPut(name) { FakeSharedPreferences() }
        }
        val store = AccountSessionStore(sessionPreferences, accountScope)

        fun restart(): Environment = Environment(
            scopePreferences = scopePreferences.restart(),
            sessionPreferences = sessionPreferences.restart(),
            scopedPreferences = scopedPreferences.mapValuesTo(mutableMapOf()) { it.value.restart() },
        )
    }
}
