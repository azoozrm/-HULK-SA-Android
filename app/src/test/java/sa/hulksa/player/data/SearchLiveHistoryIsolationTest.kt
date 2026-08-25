package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.screens.LIVE_TV_PRO_CONTEXT_ALL
import sa.hulksa.player.ui.screens.decodeLiveTvProRecentChannelIds
import sa.hulksa.player.ui.screens.encodeLiveTvProRecentChannelIds
import sa.hulksa.player.ui.screens.liveTvProLastChannel
import sa.hulksa.player.ui.screens.liveTvProUpdateRecentChannelIds

class SearchLiveHistoryIsolationTest {
    @Test
    fun `profile one live activity is invisible to profile two`() {
        val state = StateHarness(accountId = ACCOUNT_A, profileId = PROFILE_ONE)
        state.watch(10)
        state.watch(20)

        assertEquals(listOf(20, 10), state.recent())
        state.profileId = PROFILE_TWO

        assertTrue(state.recent().isEmpty())
        assertEquals(LIVE_TV_PRO_CONTEXT_ALL, state.context())
    }

    @Test
    fun `returning to profile one restores only its recent channels`() {
        val state = StateHarness(accountId = ACCOUNT_A, profileId = PROFILE_ONE)
        state.watch(10)
        state.watch(20)

        state.profileId = PROFILE_TWO
        state.watch(30)
        assertEquals(listOf(30), state.recent())

        state.profileId = PROFILE_ONE
        assertEquals(listOf(20, 10), state.recent())
        assertFalse(30 in state.recent())
    }

    @Test
    fun `last channel is resolved from the active profile history only`() {
        val channels = listOf(channel(10), channel(20), channel(30))
        val state = StateHarness(accountId = ACCOUNT_A, profileId = PROFILE_ONE)
        state.watch(10)
        state.watch(20)

        assertEquals(10, liveTvProLastChannel(channels, state.recent(), currentStreamId = 20)?.id)

        state.profileId = PROFILE_TWO
        state.watch(30)
        assertNull(liveTvProLastChannel(channels, state.recent(), currentStreamId = 30))
    }

    @Test
    fun `selected search cards stay isolated between profiles`() {
        val state = StateHarness(accountId = ACCOUNT_A, profileId = PROFILE_ONE)
        state.saveSearchCard(SEARCH_CARD_A)

        state.profileId = PROFILE_TWO
        assertNull(state.searchCard())

        state.saveSearchCard(SEARCH_CARD_B)
        state.profileId = PROFILE_ONE
        assertEquals(SEARCH_CARD_A, state.searchCard())
    }

    @Test
    fun `logout then another account with the same primary profile sees no prior state`() {
        val state = StateHarness(accountId = ACCOUNT_A, profileId = PRIMARY)
        state.saveSearchCard(SEARCH_CARD_A)
        state.watch(42)
        state.saveContext("sports")

        state.accountId = null
        assertNull(state.searchCard())
        assertTrue(state.recent().isEmpty())
        assertEquals(LIVE_TV_PRO_CONTEXT_ALL, state.context())

        state.accountId = ACCOUNT_B
        assertNull(state.searchCard())
        assertTrue(state.recent().isEmpty())
        assertEquals(LIVE_TV_PRO_CONTEXT_ALL, state.context())
    }

    @Test
    fun `account A to B to A restores each accounts own profile state`() {
        val state = StateHarness(accountId = ACCOUNT_A, profileId = PRIMARY)
        state.saveSearchCard(SEARCH_CARD_A)
        state.watch(10)
        state.saveContext("news")

        state.accountId = ACCOUNT_B
        state.saveSearchCard(SEARCH_CARD_B)
        state.watch(20)
        state.saveContext("sports")

        state.accountId = ACCOUNT_A
        assertEquals(SEARCH_CARD_A, state.searchCard())
        assertEquals(listOf(10), state.recent())
        assertEquals("news", state.context())

        state.accountId = ACCOUNT_B
        assertEquals(SEARCH_CARD_B, state.searchCard())
        assertEquals(listOf(20), state.recent())
        assertEquals("sports", state.context())
    }

    @Test
    fun `matching provider content and channel ids do not join account namespaces`() {
        val state = StateHarness(accountId = ACCOUNT_A, profileId = PRIMARY)
        state.saveSearchCard("contentId=99;title=A")
        state.watch(42)

        state.accountId = ACCOUNT_B
        assertNull(state.searchCard())
        assertTrue(state.recent().isEmpty())

        state.saveSearchCard("contentId=99;title=B")
        state.watch(42)
        assertEquals("contentId=99;title=B", state.searchCard())
        assertEquals(listOf(42), state.recent())

        state.accountId = ACCOUNT_A
        assertEquals("contentId=99;title=A", state.searchCard())
        assertEquals(listOf(42), state.recent())
    }

    @Test
    fun `profile deletion removes only the exact account and profile owner`() {
        val state = StateHarness(accountId = ACCOUNT_A, profileId = PROFILE_ONE)
        state.saveSearchCard("a-one")
        state.watch(10)
        state.saveContext("news")

        state.profileId = PROFILE_TWO
        state.saveSearchCard("a-two")
        state.watch(20)
        state.saveContext("sports")

        state.accountId = ACCOUNT_B
        state.saveSearchCard("b-two")
        state.watch(30)
        state.saveContext("kids")

        state.removeProfile(ACCOUNT_A, PROFILE_TWO)

        state.accountId = ACCOUNT_A
        assertNull(state.searchCard())
        assertTrue(state.recent().isEmpty())
        assertEquals(LIVE_TV_PRO_CONTEXT_ALL, state.context())

        state.profileId = PROFILE_ONE
        assertEquals("a-one", state.searchCard())
        assertEquals(listOf(10), state.recent())
        assertEquals("news", state.context())

        state.accountId = ACCOUNT_B
        state.profileId = PROFILE_TWO
        assertEquals("b-two", state.searchCard())
        assertEquals(listOf(30), state.recent())
        assertEquals("kids", state.context())
    }

    @Test
    fun `process recreation reads only the restored account and profile`() {
        var activeAccountId: String? = ACCOUNT_A
        var activeProfileId: String? = PROFILE_ONE
        val backend = FakeStateBackend()
        var core = newCore(
            backend = backend,
            accountId = { activeAccountId },
            profileId = { activeProfileId },
        )
        core.write(SEARCH_KEY, SEARCH_CARD_A)
        core.write(LIVE_IDS_KEY, encodeLiveTvProRecentChannelIds(listOf(20, 10)))

        core = newCore(
            backend = backend,
            accountId = { activeAccountId },
            profileId = { activeProfileId },
        )
        assertEquals(SEARCH_CARD_A, core.read(SEARCH_KEY))
        assertEquals(listOf(20, 10), decodeLiveTvProRecentChannelIds(core.read(LIVE_IDS_KEY)))

        activeProfileId = PROFILE_TWO
        assertNull(core.read(SEARCH_KEY))
        assertTrue(decodeLiveTvProRecentChannelIds(core.read(LIVE_IDS_KEY)).isEmpty())

        activeAccountId = ACCOUNT_B
        activeProfileId = PROFILE_ONE
        assertNull(core.read(SEARCH_KEY))
        assertTrue(decodeLiveTvProRecentChannelIds(core.read(LIVE_IDS_KEY)).isEmpty())
    }

    @Test
    fun `writes during logout are ignored instead of becoming a future accounts state`() {
        val state = StateHarness(accountId = ACCOUNT_A, profileId = PRIMARY)
        state.saveSearchCard(SEARCH_CARD_A)
        state.watch(10)

        state.accountId = null
        state.saveSearchCard("logout-card")
        state.watch(99)
        state.saveContext("logout-context")

        state.accountId = ACCOUNT_B
        assertNull(state.searchCard())
        assertTrue(state.recent().isEmpty())
        assertEquals(LIVE_TV_PRO_CONTEXT_ALL, state.context())
    }

    @Test
    fun `proven legacy search owner migrates once to that account only`() {
        val backend = FakeStateBackend(
            legacy = mutableMapOf(profileStateKey(PRIMARY, SEARCH_KEY) to SEARCH_CARD_A),
        )
        val core = newCore(backend, accountId = { ACCOUNT_A }, profileId = { PRIMARY })

        assertEquals(
            LegacyProfileStateResult.MIGRATED,
            core.handleLegacy(
                policy = LegacyProfileStatePolicy.MIGRATE_TO_PROVEN_ACCOUNT,
                provenOwnerAccountId = ACCOUNT_A,
            ),
        )
        assertEquals(SEARCH_CARD_A, core.read(SEARCH_KEY))
        assertTrue(backend.legacy.isEmpty())

        val accountBCore = newCore(backend, accountId = { ACCOUNT_B }, profileId = { PRIMARY })
        assertNull(accountBCore.read(SEARCH_KEY))
        assertEquals(
            LegacyProfileStateResult.ALREADY_HANDLED,
            accountBCore.handleLegacy(
                policy = LegacyProfileStatePolicy.MIGRATE_TO_PROVEN_ACCOUNT,
                provenOwnerAccountId = ACCOUNT_B,
            ),
        )
    }

    @Test
    fun `unknown legacy search owner is cleared without assigning the active account`() {
        val backend = FakeStateBackend(
            legacy = mutableMapOf(profileStateKey(PRIMARY, SEARCH_KEY) to SEARCH_CARD_A),
        )
        val core = newCore(backend, accountId = { ACCOUNT_A }, profileId = { PRIMARY })

        assertEquals(
            LegacyProfileStateResult.CLEARED,
            core.handleLegacy(
                policy = LegacyProfileStatePolicy.MIGRATE_TO_PROVEN_ACCOUNT,
                provenOwnerAccountId = null,
            ),
        )
        assertNull(core.read(SEARCH_KEY))
        assertTrue(backend.legacy.isEmpty())
    }

    @Test
    fun `global legacy live state is cleared even when an account owner is known`() {
        val backend = FakeStateBackend(
            legacy = mutableMapOf(
                LIVE_IDS_KEY to "20,10",
                LIVE_CONTEXT_KEY to "sports",
            ),
        )
        val core = newCore(backend, accountId = { ACCOUNT_A }, profileId = { PRIMARY })

        assertEquals(
            LegacyProfileStateResult.CLEARED,
            core.handleLegacy(
                policy = LegacyProfileStatePolicy.CLEAR,
                provenOwnerAccountId = ACCOUNT_A,
            ),
        )
        assertNull(core.read(LIVE_IDS_KEY))
        assertNull(core.read(LIVE_CONTEXT_KEY))
        assertTrue(backend.legacy.isEmpty())
    }

    @Test
    fun `legacy raw query state is cleared instead of being revived`() {
        val backend = FakeStateBackend(
            legacy = mutableMapOf(profileStateKey(PRIMARY, "queries") to "private query"),
        )
        val core = newCore(backend, accountId = { ACCOUNT_A }, profileId = { PRIMARY })

        assertEquals(
            LegacyProfileStateResult.CLEARED,
            core.handleLegacy(
                policy = LegacyProfileStatePolicy.CLEAR,
                provenOwnerAccountId = ACCOUNT_A,
            ),
        )
        assertNull(core.read("queries"))
        assertTrue(backend.legacy.isEmpty())
    }

    @Test
    fun `new scoped state contains no credential or media source fields`() {
        val state = StateHarness(accountId = ACCOUNT_A, profileId = PRIMARY)
        state.saveSearchCard(SEARCH_CARD_A)
        state.watch(42)
        state.saveContext("sports")

        val persisted = state.persistedSnapshot().entries
            .flatMap { (key, value) -> listOf(key, value) }
            .joinToString("\n")
            .lowercase()

        listOf(
            "username",
            "password",
            "access_code",
            "token",
            "media_source",
            "http://",
            "https://",
        ).forEach { forbidden -> assertFalse(forbidden in persisted) }
    }

    private class StateHarness(
        var accountId: String?,
        var profileId: String?,
        private val backend: FakeStateBackend = FakeStateBackend(),
    ) {
        private val core = newCore(
            backend = backend,
            accountId = { accountId },
            profileId = { profileId },
        )

        fun watch(channelId: Int) {
            val updated = liveTvProUpdateRecentChannelIds(
                existingIds = recent(),
                currentStreamId = channelId,
            )
            core.write(LIVE_IDS_KEY, encodeLiveTvProRecentChannelIds(updated))
        }

        fun recent(): List<Int> = decodeLiveTvProRecentChannelIds(core.read(LIVE_IDS_KEY))

        fun saveSearchCard(value: String) = core.write(SEARCH_KEY, value)

        fun searchCard(): String? = core.read(SEARCH_KEY)

        fun saveContext(value: String) = core.write(LIVE_CONTEXT_KEY, value)

        fun context(): String = core.read(LIVE_CONTEXT_KEY)
            .orEmpty()
            .ifBlank { LIVE_TV_PRO_CONTEXT_ALL }

        fun removeProfile(accountId: String, profileId: String) {
            core.remove(accountId, profileId, SEARCH_KEY, LIVE_IDS_KEY, LIVE_CONTEXT_KEY)
        }

        fun persistedSnapshot(): Map<String, String> = backend.scopedSnapshot()
    }

    private class FakeStateBackend(
        val legacy: MutableMap<String, String> = mutableMapOf(),
    ) : AccountProfileStateBackend {
        private val scoped = mutableMapOf<String, MutableMap<String, String>>()
        private var legacyHandled = false

        override fun read(accountId: String, key: String): String? = scoped[accountId]?.get(key)

        override fun write(accountId: String, key: String, value: String) {
            scoped.getOrPut(accountId) { mutableMapOf() }[key] = value
        }

        override fun remove(accountId: String, key: String) {
            scoped[accountId]?.remove(key)
        }

        override fun isLegacyHandled(): Boolean = legacyHandled

        override fun hasLegacyState(): Boolean = legacy.isNotEmpty()

        override fun copyLegacyState(accountId: String): Boolean {
            val target = scoped.getOrPut(accountId) { mutableMapOf() }
            legacy.forEach { (key, value) -> target.putIfAbsent(key, value) }
            return true
        }

        override fun clearLegacyState(): Boolean {
            legacy.clear()
            return true
        }

        override fun markLegacyHandled(): Boolean {
            legacyHandled = true
            return true
        }

        fun scopedSnapshot(): Map<String, String> = buildMap {
            scoped.forEach { (accountId, values) ->
                values.forEach { (key, value) -> put("$accountId/$key", value) }
            }
        }
    }

    private companion object {
        const val ACCOUNT_A = "account-a"
        const val ACCOUNT_B = "account-b"
        const val PROFILE_ONE = "profile-1"
        const val PROFILE_TWO = "profile-2"
        const val PRIMARY = "primary"
        const val SEARCH_KEY = "entries"
        const val LIVE_IDS_KEY = "ids"
        const val LIVE_CONTEXT_KEY = "category_id"
        const val SEARCH_CARD_A = "contentId=99;title=Alpha;poster=poster-a;year=2026"
        const val SEARCH_CARD_B = "contentId=99;title=Beta;poster=poster-b;year=2025"

        fun newCore(
            backend: AccountProfileStateBackend,
            accountId: () -> String?,
            profileId: () -> String?,
        ): AccountProfileStateCore = AccountProfileStateCore(
            activeAccountId = accountId,
            activeProfileId = profileId,
            backend = backend,
        )

        fun channel(id: Int): ContentItem = ContentItem(
            id = id,
            name = "Channel $id",
            categoryId = "news",
            type = ContentType.LIVE,
            posterUrl = null,
            rating = null,
            year = null,
            containerExtension = "ts",
        )
    }
}
