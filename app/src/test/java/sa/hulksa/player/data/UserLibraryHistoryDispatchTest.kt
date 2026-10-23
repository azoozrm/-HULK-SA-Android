package sa.hulksa.player.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.model.ProfileKind

class UserLibraryHistoryDispatchTest {
    @Before
    fun clearOwner() {
        AuthenticatedSessionRegistry.clear()
    }

    @After
    fun clearOwnerAfter() {
        AuthenticatedSessionRegistry.clear()
    }

    @Test
    fun `record start recovery persistence runs off the caller thread`() = runBlocking {
        val fixture = Fixture()
        fixture.seedPartialHistory(position = 4_000L)
        var writeThreadId = Thread.currentThread().id
        fixture.userPreferences.beforeCommit = {
            writeThreadId = Thread.currentThread().id
        }
        val callerThreadId = Thread.currentThread().id

        val result = fixture.library.recordStartForOwner(
            expectedOwner = fixture.owner,
            expectedAccountId = ACCOUNT_ID,
            expectedProfileId = PRIMARY_PROFILE_ID,
            request = request("movie:1", id = 1),
            historyAttempt = fixture.library.currentHistoryAttempt(),
        )

        assertNotNull(result)
        assertNotEquals(callerThreadId, writeThreadId)
        assertEquals(4_000L, result!!.first { it.key == "movie:1" }.positionMs)
        assertNotNull(fixture.userPreferences.durableValue(HISTORY_KEY))
    }

    @Test
    fun `record start caller can progress while recovery IO is blocked and stale account cannot write`() = runBlocking {
        val fixture = Fixture()
        fixture.seedPartialHistory(position = 4_000L)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.userPreferences.beforeCommit = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val expectedHistory = fixture.userPreferences.durableValue(HISTORY_KEY)

        val work = async(Dispatchers.Default) {
            fixture.library.recordStartForOwner(
                expectedOwner = fixture.owner,
                expectedAccountId = ACCOUNT_ID,
                expectedProfileId = PRIMARY_PROFILE_ID,
                request = request("movie:1", id = 1),
                historyAttempt = fixture.library.currentHistoryAttempt(),
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertFalse(work.isCompleted)
        fixture.accountScope.bind("account-2")
        release.countDown()

        assertNull(work.await())
        assertEquals(expectedHistory, fixture.userPreferences.durableValue(HISTORY_KEY))
    }

    @Test
    fun `record start stale profile cannot publish while recovery IO is pending`() = runBlocking {
        val fixture = Fixture()
        fixture.seedPartialHistory(position = 4_000L)
        fixture.seedSecondProfile()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.userPreferences.beforeCommit = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val expectedHistory = fixture.userPreferences.durableValue(HISTORY_KEY)

        val work = async(Dispatchers.Default) {
            fixture.library.recordStartForOwner(
                expectedOwner = fixture.owner,
                expectedAccountId = ACCOUNT_ID,
                expectedProfileId = PRIMARY_PROFILE_ID,
                request = request("movie:1", id = 1),
                historyAttempt = fixture.library.currentHistoryAttempt(),
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertTrue(fixture.profileStore.setActiveProfile(SECONDARY_PROFILE_ID))
        release.countDown()

        assertNull(work.await())
        assertEquals(expectedHistory, fixture.userPreferences.durableValue(HISTORY_KEY))
    }

    @Test
    fun `cancelled record start does not write history after recovery unblocks`() = runBlocking {
        val fixture = Fixture()
        fixture.seedPartialHistory(position = 4_000L)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.userPreferences.beforeCommit = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val expectedHistory = fixture.userPreferences.durableValue(HISTORY_KEY)
        val work = async(Dispatchers.Default) {
            fixture.library.recordStartForOwner(
                expectedOwner = fixture.owner,
                expectedAccountId = ACCOUNT_ID,
                expectedProfileId = PRIMARY_PROFILE_ID,
                request = request("movie:1", id = 1),
                historyAttempt = fixture.library.currentHistoryAttempt(),
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        work.cancel()
        release.countDown()
        runCatching { work.await() }

        assertEquals(expectedHistory, fixture.userPreferences.durableValue(HISTORY_KEY))
    }

    @Test
    fun `remove history dispatches off main and stale account cannot remove`() = runBlocking {
        val fixture = Fixture()
        fixture.seedPartialHistory(position = 4_000L)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.userPreferences.beforeCommit = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val expectedHistory = fixture.userPreferences.durableValue(HISTORY_KEY)
        val attempt = fixture.library.beginHistoryMutation()
        val work = async(Dispatchers.Default) {
            fixture.library.removeHistoryForOwner(
                expectedOwner = fixture.owner,
                expectedAccountId = ACCOUNT_ID,
                expectedProfileId = PRIMARY_PROFILE_ID,
                key = "movie:1",
                historyAttempt = attempt,
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        fixture.accountScope.bind("account-2")
        release.countDown()

        assertNull(work.await())
        assertEquals(expectedHistory, fixture.userPreferences.durableValue(HISTORY_KEY))
    }

    @Test
    fun `newer removal invalidates a pending record start before it writes`() = runBlocking {
        val fixture = Fixture()
        fixture.seedPartialHistory(position = 4_000L)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.userPreferences.beforeCommit = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val recordAttempt = fixture.library.currentHistoryAttempt()
        val work = async(Dispatchers.Default) {
            fixture.library.recordStartForOwner(
                expectedOwner = fixture.owner,
                expectedAccountId = ACCOUNT_ID,
                expectedProfileId = PRIMARY_PROFILE_ID,
                request = request("movie:1", id = 1),
                historyAttempt = recordAttempt,
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        fixture.library.beginHistoryMutation()
        release.countDown()

        assertNull(work.await())
    }

    @Test
    fun `replaced authenticated owner cannot write or publish either history operation`() = runBlocking {
        val fixture = Fixture()
        fixture.seedPartialHistory(position = 4_000L)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.userPreferences.beforeCommit = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val oldOwner = fixture.owner
        val expectedHistory = fixture.userPreferences.durableValue(HISTORY_KEY)
        val work = async(Dispatchers.Default) {
            fixture.library.recordStartForOwner(
                expectedOwner = oldOwner,
                expectedAccountId = ACCOUNT_ID,
                expectedProfileId = PRIMARY_PROFILE_ID,
                request = request("movie:1", id = 1),
                historyAttempt = fixture.library.currentHistoryAttempt(),
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        fixture.replaceOwner()
        release.countDown()

        assertNull(work.await())
        assertEquals(expectedHistory, fixture.userPreferences.durableValue(HISTORY_KEY))

        fixture.userPreferences.beforeCommit = null
        val currentResult = fixture.library.recordStartForOwner(
            expectedOwner = requireNotNull(AuthenticatedSessionRegistry.currentOwner()),
            expectedAccountId = ACCOUNT_ID,
            expectedProfileId = PRIMARY_PROFILE_ID,
            request = request("movie:1", id = 1),
            historyAttempt = fixture.library.currentHistoryAttempt(),
        )
        assertNotNull(currentResult)
    }

    @Test
    fun `replaced authenticated owner cannot remove history`() = runBlocking {
        val fixture = Fixture()
        fixture.seedPartialHistory(position = 4_000L)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.userPreferences.beforeCommit = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val oldOwner = fixture.owner
        val attempt = fixture.library.beginHistoryMutation()
        val expectedHistory = fixture.userPreferences.durableValue(HISTORY_KEY)
        val work = async(Dispatchers.Default) {
            fixture.library.removeHistoryForOwner(
                expectedOwner = oldOwner,
                expectedAccountId = ACCOUNT_ID,
                expectedProfileId = PRIMARY_PROFILE_ID,
                key = "movie:1",
                historyAttempt = attempt,
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        fixture.replaceOwner()
        release.countDown()

        assertNull(work.await())
        assertEquals(expectedHistory, fixture.userPreferences.durableValue(HISTORY_KEY))
    }

    @Test
    fun `blocked progress recovery does not hold UI mutation monitor and stale write is discarded`() = runBlocking {
        val fixture = Fixture()
        fixture.seedPartialHistory(position = 4_000L)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.userPreferences.beforeCommit = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val progress = fixture.library.beginProgressMutation()
        val work = async(Dispatchers.Default) {
            fixture.library.updateProgressForOwner(
                expectedOwner = fixture.owner,
                expectedProfileId = PRIMARY_PROFILE_ID,
                request = request("movie:1", id = 1),
                positionMs = 55_000L,
                durationMs = 60_000L,
                historyAttempt = progress,
                isCurrentAttempt = { fixture.library.isCurrentProgressMutation(progress) },
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        val startedAt = System.nanoTime()
        fixture.library.beginHistoryMutation()
        fixture.library.clearHistory()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        assertTrue("UI mutation blocked for ${elapsedMs}ms", elapsedMs < 1_000L)

        release.countDown()
        assertNull(work.await())
        assertNull(fixture.userPreferences.durableValue(HISTORY_KEY))
    }

    @Test
    fun `clear wins over a progress final apply paused after validation`() = runBlocking {
        val fixture = Fixture()
        fixture.seedValidHistory(position = 4_000L)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val clearApplied = CountDownLatch(1)
        var writerPaused = false
        fixture.userPreferences.beforeApply = {
            if (!writerPaused) {
                writerPaused = true
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            } else {
                clearApplied.countDown()
            }
        }
        val progress = fixture.library.beginProgressMutation()
        val work = async(Dispatchers.Default) {
            fixture.library.updateProgressForOwner(
                expectedOwner = fixture.owner,
                expectedProfileId = PRIMARY_PROFILE_ID,
                request = request("movie:1", id = 1),
                positionMs = 55_000L,
                durationMs = 60_000L,
                historyAttempt = progress,
                isCurrentAttempt = { fixture.library.isCurrentProgressMutation(progress) },
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val clear = async(Dispatchers.Default) { fixture.library.clearHistory() }
        assertFalse("clear must wait for the final write boundary", clearApplied.await(1, TimeUnit.SECONDS))
        assertFalse(clear.isCompleted)
        release.countDown()
        clear.await()
        work.await()

        assertNull(fixture.userPreferences.durableValue(HISTORY_KEY))
        assertTrue(fixture.library.history().isEmpty())
    }

    @Test
    fun `obsolete history snapshot cannot publish after a newer clear`() = runBlocking {
        val fixture = Fixture()
        val attempt = fixture.library.currentHistoryAttempt()
        val expectedProfile = PRIMARY_PROFILE_ID
        assertTrue(
            historyPublicationAllowed(
                attemptCurrent = fixture.library.isCurrentHistoryMutation(attempt),
                sessionCurrent = AuthenticatedSessionRegistry.isCurrent(fixture.owner),
                expectedAccountId = ACCOUNT_ID,
                activeAccountId = ACCOUNT_ID,
                expectedProfileId = expectedProfile,
                activeProfileId = expectedProfile,
            ),
        )

        fixture.library.beginHistoryMutation()
        fixture.library.clearHistory()

        assertFalse(
            historyPublicationAllowed(
                attemptCurrent = fixture.library.isCurrentHistoryMutation(attempt),
                sessionCurrent = AuthenticatedSessionRegistry.isCurrent(fixture.owner),
                expectedAccountId = ACCOUNT_ID,
                activeAccountId = ACCOUNT_ID,
                expectedProfileId = expectedProfile,
                activeProfileId = expectedProfile,
            ),
        )
        assertTrue(fixture.library.history().isEmpty())
    }

    private class Fixture {
        val scopeState = FakeSharedPreferences()
        val profilePreferences = FakeSharedPreferences()
        val legacyProfilePreferences = FakeSharedPreferences()
        val userPreferences = FakeSharedPreferences()
        private val otherPreferences = mutableMapOf<String, FakeSharedPreferences>()
        val accountScope = AccountScopeStore(scopeState) { name ->
            when {
                name.startsWith("hulk_profiles_v1.account.") -> profilePreferences
                name.startsWith("hulk_user_library.account.") -> userPreferences
                else -> otherPreferences.getOrPut(name) { FakeSharedPreferences() }
            }
        }
        val profileStore = ProfileStore(accountScope) { name ->
            if (name == ProfileStore.PREFERENCES_NAME) {
                legacyProfilePreferences
            } else {
                profilePreferences
            }
        }
        private val context = TestAppContext(otherPreferences)
        val library = UserLibrary(accountScope, profileStore, KidsContentFilterStore(context))
        val owner: AuthenticatedSessionOwner

        init {
            check(accountScope.bind(ACCOUNT_ID))
            userPreferences.edit().putBoolean("__account_scope_migrated_v1", true).apply()
            profilePreferences.edit().putBoolean("__account_scope_migrated_v1", true).apply()
            val session = AuthenticatedSession(
                portal = PortalConfig("https://provider.example.test", PortalConfig.Source.ACCESS_CODE),
                credentials = Credentials("HULK-ABCD-EFGH-JKMN-PQRS", "user", "pass"),
                account = AccountInfo("user", "Active", null, 0, 1, false),
            )
            val metadata = AccountSessionMetadata(
                accountId = ACCOUNT_ID,
                username = "user",
                portalBaseUrl = "https://provider.example.test",
                authenticatedAtEpochMs = 1L,
                expiresAtEpochSeconds = Long.MAX_VALUE,
                status = "Active",
                installationId = "installation",
                sessionId = "session-1",
            )
            AuthenticatedSessionRegistry.update(session, metadata)
            owner = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
            profilePreferences.edit()
                .putString(
                    "profiles",
                    JSONArray().put(profileJson(PRIMARY_PROFILE_ID, "Adult", "STANDARD")).toString(),
                )
                .putString("active_profile_id", PRIMARY_PROFILE_ID)
                .putInt("schema_version", 1)
                .apply()
        }

        fun seedPartialHistory(position: Long) {
            userPreferences.edit()
                .putString(
                    HISTORY_KEY,
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("key", "movie:1")
                                .put("title", "Movie 1")
                                .put("poster", "")
                                .put("kind", "movie")
                                .put("id", 1)
                                .put("extension", "mp4")
                                .put("live", false)
                                .put("position", position)
                                .put("duration", 60_000L)
                                .put("updated", position),
                        )
                        .put(JSONObject().put("key", "broken"))
                        .toString(),
                )
                .apply()
        }

        fun replaceOwner() {
            val session = AuthenticatedSession(
                portal = PortalConfig("https://provider.example.test", PortalConfig.Source.ACCESS_CODE),
                credentials = Credentials("HULK-ABCD-EFGH-JKMN-PQRS", "user", "pass"),
                account = AccountInfo("user", "Active", null, 0, 1, false),
            )
            val metadata = AccountSessionMetadata(
                accountId = ACCOUNT_ID,
                username = "user",
                portalBaseUrl = "https://provider.example.test",
                authenticatedAtEpochMs = 2L,
                expiresAtEpochSeconds = Long.MAX_VALUE,
                status = "Active",
                installationId = "installation",
                sessionId = "session-2",
            )
            AuthenticatedSessionRegistry.update(session, metadata)
        }

        fun seedValidHistory(position: Long) {
            userPreferences.edit()
                .putString(
                    HISTORY_KEY,
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("key", "movie:1")
                                .put("title", "Movie 1")
                                .put("poster", "")
                                .put("kind", "movie")
                                .put("id", 1)
                                .put("extension", "mp4")
                                .put("live", false)
                                .put("position", position)
                                .put("duration", 60_000L)
                                .put("updated", position),
                        )
                        .toString(),
                )
                .apply()
        }

        fun seedSecondProfile() {
            profilePreferences.edit()
                .putString(
                    "profiles",
                    JSONArray()
                        .put(profileJson(PRIMARY_PROFILE_ID, "Adult", "STANDARD"))
                        .put(profileJson(SECONDARY_PROFILE_ID, "Kids", "KIDS"))
                        .toString(),
                )
                .apply()
        }
    }

    private fun request(key: String, id: Int): PlaybackRequest = PlaybackRequest(
        title = "Movie $id",
        posterUrl = null,
        candidates = listOf("https://cdn.example.test/movie/$id.mp4"),
        isLive = false,
        historyKey = key,
        streamKind = "movie",
        streamId = id,
        extension = "mp4",
    )

    private companion object {
        const val ACCOUNT_ID = "account-1"
        const val PRIMARY_PROFILE_ID = "primary"
        const val SECONDARY_PROFILE_ID = "kids-1"
        const val HISTORY_KEY = "profile:$PRIMARY_PROFILE_ID:history"
    }
}

private fun profileJson(id: String, name: String, kind: String): JSONObject = JSONObject()
    .put("id", id)
    .put("displayName", name)
    .put("kind", kind)
    .put("avatarKey", "default")
    .put("createdAtEpochMs", 1_000L)
    .put("isPrimary", id == "primary")
