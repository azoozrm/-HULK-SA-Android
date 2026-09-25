package sa.hulksa.player.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

private const val PORTAL_ALPHA = "https://alpha.example.test:8080"
private const val PORTAL_BETA = "https://beta.example.test:8080"
private const val USERNAME_ALPHA = "alpha"
private const val USERNAME_BETA = "beta"

class AccountSessionMetadataCommitAtomicityTest {
    @Test
    fun `successful metadata commit records the authenticated account`() {
        val environment = environment()
        val expectedAccountId = stableAccountId(PORTAL_ALPHA, USERNAME_ALPHA)

        val recorded = environment.store.recordAuthenticated(alphaSession())

        assertEquals(expectedAccountId, recorded.accountId)
        assertEquals(expectedAccountId, environment.store.activeAccountId())
        assertEquals(expectedAccountId, environment.store.metadata()?.accountId)
        assertEquals(USERNAME_ALPHA, environment.store.metadata()?.username)
        assertEquals("CODE-ALPHA", environment.store.lastAccessCode())

        val restarted = environment.restart()
        assertEquals(expectedAccountId, restarted.store.activeAccountId())
        assertEquals(expectedAccountId, restarted.store.metadata()?.accountId)
    }

    @Test
    fun `metadata commit failure fails the authentication persistence operation`() {
        val environment = environment()
        environment.sessionPreferences.commitResult = false

        assertThrows(IllegalStateException::class.java) {
            environment.store.recordAuthenticated(alphaSession())
        }

        assertNull(environment.sessionPreferences.durableValue("account_id"))
    }

    @Test
    fun `metadata commit failure plus caller cleanup leaves no durable account split`() {
        val environment = environment()
        val accountB = stableAccountId(PORTAL_BETA, USERNAME_BETA)
        environment.store.recordAuthenticated(alphaSession())
        environment.sessionPreferences.commitResult = false

        assertThrows(IllegalStateException::class.java) {
            environment.store.recordAuthenticated(betaSession())
        }

        // A restart at this point observes the durable scope bound to B while metadata
        // still belongs to A, which is exactly the split the checked commit rejects.
        val splitReader = environment.restart()
        assertEquals(accountB, splitReader.store.activeAccountId())
        assertNull(splitReader.store.metadata())

        // The existing login catch path clears the active session when recordAuthenticated throws.
        environment.sessionPreferences.commitResult = true
        environment.store.clearActiveSession()

        val coherentReader = environment.restart()
        assertNull(coherentReader.store.activeAccountId())
        assertNull(coherentReader.store.metadata())
    }

    @Test
    fun `retained access code survives logout after a successful authentication`() {
        val environment = environment()
        environment.store.recordAuthenticated(alphaSession())

        environment.store.clearActiveSession()

        assertNull(environment.store.metadata())
        assertEquals("CODE-ALPHA", environment.store.lastAccessCode())
        assertEquals("CODE-ALPHA", environment.restart().store.lastAccessCode())
    }

    private fun environment(): TestEnvironment =
        TestEnvironment(FakeSharedPreferences(), FakeSharedPreferences())

    private fun alphaSession(): AuthenticatedSession = session(
        portal = PORTAL_ALPHA,
        username = USERNAME_ALPHA,
        accessCode = "CODE-ALPHA",
    )

    private fun betaSession(): AuthenticatedSession = session(
        portal = PORTAL_BETA,
        username = USERNAME_BETA,
        accessCode = "CODE-BETA",
    )

    private fun session(
        portal: String,
        username: String,
        accessCode: String,
    ): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(baseUrl = portal, source = PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials(
            accessCode = accessCode,
            username = username,
            password = "password-$username",
        ),
        account = AccountInfo(
            username = username,
            status = "Active",
            expiresAtEpochSeconds = 2_000_000_000L,
            activeConnections = 1,
            maxConnections = 1,
            isTrial = false,
        ),
    )
}

private class TestEnvironment(
    val scopePreferences: FakeSharedPreferences,
    val sessionPreferences: FakeSharedPreferences,
) {
    val accountScope: AccountScopeStore = AccountScopeStore(scopePreferences) { name ->
        throw AssertionError("Unexpected scoped preferences lookup: $name")
    }
    val store: AccountSessionStore = AccountSessionStore(sessionPreferences, accountScope)

    fun restart(): TestEnvironment = TestEnvironment(
        scopePreferences.restart(),
        sessionPreferences.restart(),
    )
}

/**
 * In-memory SharedPreferences that mirrors the platform contract this regression
 * depends on: commit applies the editor to the process map even when the durable
 * write fails, and only a true result updates the durable file.
 */
private class FakeSharedPreferences private constructor(
    private val durable: MutableMap<String, Any?>,
) : SharedPreferences {
    private val memory: MutableMap<String, Any?> = durable.toMutableMap()

    var commitResult: Boolean = true

    constructor() : this(mutableMapOf())

    fun restart(): FakeSharedPreferences = FakeSharedPreferences(durable.toMutableMap())

    fun durableValue(key: String): Any? = durable[key]

    override fun getAll(): MutableMap<String, *> = memory.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        memory[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (memory[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = memory[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = memory[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = memory[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        memory[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = memory.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clearPending = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            put(key, value)

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = put(key, values?.toSet())

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            removed += key.orEmpty()
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearPending = true
            return this
        }

        override fun commit(): Boolean {
            applyPending()
            if (commitResult) {
                durable.clear()
                durable.putAll(memory)
            }
            return commitResult
        }

        override fun apply() {
            applyPending()
            durable.clear()
            durable.putAll(memory)
        }

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            pending[key.orEmpty()] = value
            return this
        }

        private fun applyPending() {
            if (clearPending) memory.clear()
            removed.forEach(memory::remove)
            pending.forEach { (key, value) ->
                if (value == null) memory.remove(key) else memory[key] = value
            }
        }
    }
}
