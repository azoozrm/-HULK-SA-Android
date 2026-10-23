package sa.hulksa.player.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ProfileKind

class ProfileStoreIntegrityTest {
    @Test
    fun `active kids profile survives a damaged sibling without manufacturing a standard profile`() {
        val fixture = Fixture()
        val original = JSONArray()
            .put(profileJson("kids-1", "Kids", "KIDS"))
            .put(JSONObject().put("id", "broken").put("displayName", "Broken"))
            .toString()
        fixture.seedProfiles(activeProfileId = "kids-1", raw = original)

        val loaded = load(fixture)

        assertNotNull(loaded)
        assertEquals(listOf("kids-1"), loaded!!.profiles.map { it.id })
        assertEquals(ProfileKind.KIDS, loaded.profiles.single().kind)
        assertEquals("kids-1", loaded.activeProfileId)
        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))
        assertTrue(
            fixture.scoped.durableKeys().none {
                it.startsWith(persistedRecoveryKeyPrefix(KEY_PROFILES))
            },
        )

        val created = fixture.store.createProfile("Second", kind = ProfileKind.STANDARD)
        assertNotNull(created)
        assertEquals(listOf("kids-1", created!!.id), fixture.store.profiles().map { it.id })
        assertTrue(
            fixture.scoped.durableKeys().any {
                it.startsWith(persistedRecoveryKeyPrefix(KEY_PROFILES))
            },
        )
        val restarted = load(fixture.restart())
        assertNotNull(restarted)
        assertEquals(listOf("kids-1", created.id), restarted!!.profiles.map { it.id })
    }

    @Test
    fun `damaged active kids record blocks startup and never falls back to an adult profile`() {
        val fixture = Fixture()
        val original = JSONArray()
            .put(profileJson("primary", "Adult", "STANDARD", primary = true))
            .put(JSONObject().put("id", "kids-1").put("displayName", "Kids"))
            .toString()
        fixture.seedProfiles(activeProfileId = "kids-1", raw = original)

        assertNull(load(fixture))
        assertTrue(fixture.store.profiles().isEmpty())
        assertEquals(ProfileKind.KIDS, fixture.store.activeProfile().kind)
        assertNull(fixture.store.createProfile("New"))
        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))

        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))
        assertNull(load(fixture.restart()))
        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))
        assertTrue(
            fixture.scoped.durableKeys().none {
                it.startsWith(persistedRecoveryKeyPrefix(KEY_PROFILES))
            },
        )
    }

    @Test
    fun `unreadable profile document is not replaced with first-use primary profile`() {
        val fixture = Fixture()
        val original = "{\"profiles\":["
        fixture.scoped.edit()
            .putString(KEY_PROFILES, original)
            .putString(KEY_ACTIVE_PROFILE_ID, "kids-1")
            .apply()

        assertNull(load(fixture))
        assertTrue(fixture.store.profiles().isEmpty())
        assertNull(fixture.store.createProfile("New"))
        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))
        assertNull(load(fixture.restart()))
        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))
    }

    @Test
    fun `present but empty profile collection is damaged not first-use`() {
        val fixture = Fixture()
        fixture.seedProfiles(activeProfileId = "primary", raw = "[]")

        assertNull(load(fixture))
        assertTrue(fixture.store.profiles().isEmpty())
        assertEquals("[]", fixture.scoped.durableValue(KEY_PROFILES))
    }

    @Test
    fun `genuine missing profiles still initialize exactly one primary profile`() {
        val fixture = Fixture()

        val loaded = load(fixture)

        assertNotNull(loaded)
        assertEquals(listOf(ProfileStore.PRIMARY_PROFILE_ID), loaded!!.profiles.map { it.id })
        assertEquals(ProfileKind.STANDARD, loaded.profiles.single().kind)
        assertEquals(ProfileStore.PRIMARY_PROFILE_ID, loaded.activeProfileId)
        val stored = fixture.scoped.durableValue(KEY_PROFILES) as String
        assertEquals(1, JSONArray(stored).length())

        val restarted = load(fixture.restart())
        assertNotNull(restarted)
        assertEquals(1, restarted!!.profiles.size)
        assertEquals(
            ProfileStore.PRIMARY_PROFILE_ID,
            fixture.scoped.durableValue(KEY_ACTIVE_PROFILE_ID),
        )
    }

    @Test
    fun `missing active id with multiple profiles is blocked instead of selecting primary`() {
        val fixture = Fixture()
        val original = JSONArray()
            .put(profileJson("kids-1", "Kids", "KIDS"))
            .put(profileJson("primary", "Adult", "STANDARD", primary = true))
            .toString()
        fixture.scoped.edit().putString(KEY_PROFILES, original).apply()

        assertNull(load(fixture))
        assertTrue(fixture.store.profiles().isEmpty())
        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))
    }

    @Test
    fun `missing active id with a damaged sibling is blocked instead of selecting an adult`() {
        val fixture = Fixture()
        val original = JSONArray()
            .put(profileJson("primary", "Adult", "STANDARD", primary = true))
            .put(JSONObject().put("id", "kids-1").put("displayName", "Kids"))
            .toString()
        fixture.scoped.edit().putString(KEY_PROFILES, original).apply()

        assertNull(load(fixture))
        assertTrue(fixture.store.profiles().isEmpty())
        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))
    }

    @Test
    fun `healthy single profile with missing active key repairs safely`() {
        val fixture = Fixture()
        val original = JSONArray()
            .put(profileJson("kids-1", "Kids", "KIDS"))
            .toString()
        fixture.scoped.edit().putString(KEY_PROFILES, original).apply()

        val loaded = load(fixture)

        assertNotNull(loaded)
        assertEquals(listOf("kids-1"), loaded!!.profiles.map { it.id })
        assertEquals("kids-1", loaded.activeProfileId)
        assertEquals("kids-1", fixture.scoped.durableValue(KEY_ACTIVE_PROFILE_ID))
    }

    @Test
    fun `unknown profile kind is treated as damage and never becomes standard`() {
        val fixture = Fixture()
        val original = JSONArray()
            .put(profileJson("kids-1", "Kids", "NOT_A_KIND"))
            .toString()
        fixture.seedProfiles(activeProfileId = "kids-1", raw = original)

        assertNull(load(fixture))
        assertTrue(fixture.store.profiles().isEmpty())
        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))
    }

    @Test
    fun `failed profile repair preserves the original and a later successful write does not lose it`() {
        val fixture = Fixture()
        val original = JSONArray()
            .put(profileJson("kids-1", "Kids", "KIDS"))
            .put(JSONObject().put("id", "broken").put("displayName", "Broken"))
            .toString()
        fixture.seedProfiles(activeProfileId = "kids-1", raw = original)
        assertNotNull(load(fixture))

        fixture.scoped.commitResult = false
        assertNull(fixture.store.createProfile("Second"))
        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))

        fixture.scoped.commitResult = true
        val created = fixture.store.createProfile("Second")
        assertNotNull(created)
        assertTrue(
            fixture.scoped.durableKeys().any {
                it.startsWith(persistedRecoveryKeyPrefix(KEY_PROFILES))
            },
        )
        val restarted = load(fixture.restart())
        assertNotNull(restarted)
        assertEquals(listOf("kids-1", created!!.id), restarted!!.profiles.map { it.id })
    }

    @Test
    fun `profile repair write failure keeps original durable until a later successful repair`() {
        val fixture = Fixture()
        val original = JSONArray()
            .put(profileJson("kids-1", "Kids", "KIDS"))
            .put(JSONObject().put("id", "broken").put("displayName", "Broken"))
            .toString()
        fixture.seedProfiles(activeProfileId = "kids-1", raw = original)
        assertNotNull(load(fixture))

        fixture.scoped.commitResults.addAll(listOf(true, true, false))
        assertNull(fixture.store.createProfile("Second"))
        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))
        assertTrue(
            fixture.scoped.durableKeys().any {
                it.startsWith(persistedRecoveryKeyPrefix(KEY_PROFILES))
            },
        )

        val created = fixture.store.createProfile("Second")
        assertNotNull(created)
        val restarted = load(fixture.restart())
        assertNotNull(restarted)
        assertEquals(listOf("kids-1", created!!.id), restarted!!.profiles.map { it.id })
    }

    @Test
    fun `routing does not delete a valid default when profile integrity is blocked`() = kotlinx.coroutines.runBlocking {
        val fixture = Fixture()
        val original = JSONArray()
            .put(profileJson("primary", "Adult", "STANDARD", primary = true))
            .put(JSONObject().put("id", "kids-1").put("displayName", "Kids"))
            .toString()
        fixture.seedProfiles(activeProfileId = "kids-1", raw = original)
        fixture.routingPreferences().edit()
            .putString("default_profile_id", "primary")
            .putBoolean("direct_entry_enabled", true)
            .apply()

        assertNull(load(fixture))
        val profileIds = fixture.store.profiles().mapTo(linkedSetOf()) { it.id }
        val routing = fixture.profilePreferencesStore.routingForAccount(ACCOUNT_ID, profileIds)

        assertNotNull(routing)
        assertTrue(routing!!.directEntryEnabled)
        assertNull(routing.defaultProfileId)
        assertEquals("primary", fixture.routingPreferences().durableValue("default_profile_id"))
    }

    private class Fixture(
        val scopeState: FakeSharedPreferences = FakeSharedPreferences(),
        val legacy: FakeSharedPreferences = FakeSharedPreferences(),
        val scopedPreferences: MutableMap<String, FakeSharedPreferences> = mutableMapOf(),
    ) {
        val scoped: FakeSharedPreferences
            get() = scopedPreferences.getOrPut(PROFILE_SCOPED_NAME) { FakeSharedPreferences() }

        val accountScope = AccountScopeStore(scopeState) { name ->
            if (name == ProfileStore.PREFERENCES_NAME) {
                legacy
            } else {
                scopedPreferences.getOrPut(name) { FakeSharedPreferences() }
            }
        }
        val store = ProfileStore(accountScope) { name ->
            if (name == ProfileStore.PREFERENCES_NAME) legacy else scopedPreferences.getOrPut(name) {
                FakeSharedPreferences()
            }
        }
        val profilePreferencesStore = ProfilePreferencesStore(accountScope, store)

        init {
            check(accountScope.bind(ACCOUNT_ID))
        }

        fun seedProfiles(activeProfileId: String, raw: String) {
            scoped.edit()
                .putString(KEY_PROFILES, raw)
                .putString(KEY_ACTIVE_PROFILE_ID, activeProfileId)
                .apply()
        }

        fun routingPreferences(): FakeSharedPreferences =
            scopedPreferences.getOrPut(PREFERENCES_SCOPED_NAME) { FakeSharedPreferences() }

        fun seedScopedResidual(activeProfileId: String, schemaVersion: Int) {
            scoped.edit()
                .putString(KEY_ACTIVE_PROFILE_ID, activeProfileId)
                .putInt(KEY_SCHEMA_VERSION, schemaVersion)
                .apply()
        }

        fun seedLegacyProfiles(activeProfileId: String, raw: String) {
            legacy.edit()
                .putString(KEY_PROFILES, raw)
                .putString(KEY_ACTIVE_PROFILE_ID, activeProfileId)
                .putInt(KEY_SCHEMA_VERSION, 1)
                .apply()
        }

        fun restart(): Fixture = Fixture(
            scopeState = scopeState.restart(),
            legacy = legacy.restart(),
            scopedPreferences = scopedPreferences.mapValuesTo(mutableMapOf()) { it.value.restart() },
        )
    }

    @Test
    fun `existing scoped profiles document blocks legacy active metadata stitching`() {
        listOf(false, true).forEach { loadFirst ->
            val fixture = Fixture()
            val scopedProfiles = JSONArray()
                .put(profileJson("primary", "Adult", "STANDARD", primary = true))
                .put(profileJson("kids-1", "Kids", "KIDS"))
                .toString()
            fixture.scoped.edit()
                .putString(KEY_PROFILES, scopedProfiles)
                .remove(KEY_ACTIVE_PROFILE_ID)
                .remove(KEY_SCHEMA_VERSION)
                .apply()
            fixture.seedLegacyProfiles(
                activeProfileId = "primary",
                raw = JSONArray()
                    .put(profileJson("primary", "Adult", "STANDARD", primary = true))
                    .toString(),
            )

            if (loadFirst) assertNull(load(fixture))
            assertTrue(fixture.store.profiles().isEmpty())
            assertNull(fixture.store.createProfile("New"))
            assertNull(fixture.scoped.durableValue(KEY_ACTIVE_PROFILE_ID))
            assertNull(fixture.scoped.durableValue(KEY_SCHEMA_VERSION))
            assertEquals(scopedProfiles, fixture.scoped.durableValue(KEY_PROFILES))
            assertNull(load(fixture.restart()))
            assertEquals(scopedProfiles, fixture.scoped.durableValue(KEY_PROFILES))
        }
    }

    @Test
    fun `recovery-only profile state blocks stale legacy standard resurrection`() {
        val fixture = Fixture()
        fixture.seedLegacyProfiles(
            activeProfileId = "primary",
            raw = JSONArray()
                .put(profileJson("primary", "Adult", "STANDARD", primary = true))
                .toString(),
        )
        assertTrue(
            preserveDamagedPersistedDocument(fixture.scoped, KEY_PROFILES, "damaged-profiles"),
        )

        assertBlockedWithoutRewrite(fixture, expectedOriginal = null)
        assertTrue(
            fixture.scoped.durableKeys().any {
                it.startsWith(persistedRecoveryKeyPrefix(KEY_PROFILES))
            },
        )
        assertEquals("primary", fixture.legacy.durableValue(KEY_ACTIVE_PROFILE_ID))
    }

    @Test
    fun `schema zero plus recovery evidence still blocks as established`() {
        val fixture = Fixture()
        fixture.seedScopedResidual(activeProfileId = "kids-1", schemaVersion = 0)
        assertTrue(
            preserveDamagedPersistedDocument(fixture.scoped, KEY_PROFILES, "damaged-profiles"),
        )

        assertBlockedWithoutRewrite(fixture, expectedOriginal = null)
        assertEquals(0, fixture.scoped.durableValue(KEY_SCHEMA_VERSION))
    }

    @Test
    fun `sync before async load and load first apply the same recovery-only decision`() {
        listOf(false, true).forEach { loadFirst ->
            val fixture = Fixture()
            fixture.seedLegacyProfiles(
                activeProfileId = "primary",
                raw = JSONArray()
                    .put(profileJson("primary", "Adult", "STANDARD", primary = true))
                    .toString(),
            )
            assertTrue(
                preserveDamagedPersistedDocument(fixture.scoped, KEY_PROFILES, "damaged-profiles"),
            )
            if (loadFirst) assertNull(load(fixture))
            assertTrue(fixture.store.profiles().isEmpty())
            assertNull(fixture.store.createProfile("New"))
            assertTrue(fixture.scoped.durableKeys().none { it == KEY_PROFILES })
            assertNull(load(fixture.restart()))
        }
    }

    @Test
    fun `missing profile document with residual kids state is blocked for legacy owner`() {
        val fixture = Fixture()
        val staleStandard = JSONArray()
            .put(profileJson("primary", "Adult", "STANDARD", primary = true))
            .toString()
        fixture.seedLegacyProfiles(activeProfileId = "primary", raw = staleStandard)
        fixture.seedScopedResidual(activeProfileId = "kids-1", schemaVersion = 1)

        assertBlockedWithoutRewrite(fixture, expectedOriginal = null)
        assertEquals(staleStandard, fixture.legacy.durableValue(KEY_PROFILES))
        assertEquals("primary", fixture.legacy.durableValue(KEY_ACTIVE_PROFILE_ID))
        assertEquals("kids-1", fixture.scoped.durableValue(KEY_ACTIVE_PROFILE_ID))
        assertEquals(1, fixture.scoped.durableValue(KEY_SCHEMA_VERSION))
    }

    @Test
    fun `missing profile document with residual kids state is blocked for non legacy owner`() {
        val fixture = Fixture()
        fixture.scopeState.edit().putString("legacy_owner_account_id", "other-account").apply()
        val staleStandard = JSONArray()
            .put(profileJson("primary", "Adult", "STANDARD", primary = true))
            .toString()
        fixture.seedLegacyProfiles(activeProfileId = "primary", raw = staleStandard)
        fixture.seedScopedResidual(activeProfileId = "kids-1", schemaVersion = 1)

        assertBlockedWithoutRewrite(fixture, expectedOriginal = null)
        assertEquals(staleStandard, fixture.legacy.durableValue(KEY_PROFILES))
        assertEquals("kids-1", fixture.scoped.durableValue(KEY_ACTIVE_PROFILE_ID))
    }

    @Test
    fun `missing profile document with residual kids state and no stale legacy remains blocked`() {
        val fixture = Fixture()
        fixture.seedScopedResidual(activeProfileId = "kids-1", schemaVersion = 1)

        assertBlockedWithoutRewrite(fixture, expectedOriginal = null)
        assertEquals("kids-1", fixture.scoped.durableValue(KEY_ACTIVE_PROFILE_ID))
        assertEquals(1, fixture.scoped.durableValue(KEY_SCHEMA_VERSION))
    }

    @Test
    fun `conflicting duplicate active profile identity is blocked in both orders`() {
        listOf(
            JSONArray()
                .put(profileJson("kids-1", "Kids", "STANDARD"))
                .put(profileJson("kids-1", "Kids", "KIDS"))
                .toString(),
            JSONArray()
                .put(profileJson("kids-1", "Kids", "KIDS"))
                .put(profileJson("kids-1", "Kids", "STANDARD"))
                .toString(),
        ).forEach { original ->
            val fixture = Fixture()
            fixture.seedProfiles(activeProfileId = "kids-1", raw = original)

            assertBlockedWithoutRewrite(fixture, expectedOriginal = original)
        }
    }

    @Test
    fun `malformed duplicate carrying the active id is blocked`() {
        val fixture = Fixture()
        val original = JSONArray()
            .put(profileJson("kids-1", "Kids", "KIDS"))
            .put(JSONObject().put("id", "kids-1").put("displayName", "Kids broken"))
            .toString()
        fixture.seedProfiles(activeProfileId = "kids-1", raw = original)

        assertBlockedWithoutRewrite(fixture, expectedOriginal = original)
    }

    @Test
    fun `conflicting duplicate non active identity is excluded without destructive rewrite`() {
        val fixture = Fixture()
        val original = JSONArray()
            .put(profileJson("primary", "Adult", "STANDARD", primary = true))
            .put(profileJson("kids-1", "Kids", "STANDARD"))
            .put(profileJson("kids-1", "Kids", "KIDS"))
            .toString()
        fixture.seedProfiles(activeProfileId = "primary", raw = original)

        val loaded = load(fixture)

        assertNotNull(loaded)
        assertEquals(listOf("primary"), loaded!!.profiles.map { it.id })
        assertEquals(original, fixture.scoped.durableValue(KEY_PROFILES))
        val created = fixture.store.createProfile("Second")
        assertNotNull(created)
        assertTrue(
            fixture.scoped.durableKeys().any {
                it.startsWith(persistedRecoveryKeyPrefix(KEY_PROFILES))
            },
        )
        val restarted = load(fixture.restart())
        assertNotNull(restarted)
        assertEquals(listOf("primary", created!!.id), restarted!!.profiles.map { it.id })
    }

    private fun assertBlockedWithoutRewrite(fixture: Fixture, expectedOriginal: String?) {
        assertNull(load(fixture))
        assertTrue(fixture.store.profiles().isEmpty())
        assertEquals(ProfileKind.KIDS, fixture.store.activeProfile().kind)
        assertNull(fixture.store.createProfile("New"))
        if (expectedOriginal != null) {
            assertEquals(expectedOriginal, fixture.scoped.durableValue(KEY_PROFILES))
        } else {
            assertNull(fixture.scoped.durableValue(KEY_PROFILES))
        }
        assertNull(load(fixture.restart()))
        if (expectedOriginal != null) {
            assertEquals(expectedOriginal, fixture.scoped.durableValue(KEY_PROFILES))
        } else {
            assertNull(fixture.scoped.durableValue(KEY_PROFILES))
        }
        assertEquals(ProfileKind.KIDS, fixture.store.activeProfile().kind)
    }

    private fun load(fixture: Fixture): ProfileStoreSnapshot? =
        kotlinx.coroutines.runBlocking { fixture.store.load(ACCOUNT_ID) }

    private fun profileJson(
        id: String,
        name: String,
        kind: String,
        primary: Boolean = false,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("displayName", name)
        .put("kind", kind)
        .put("avatarKey", "default")
        .put("createdAtEpochMs", 1_000L)
        .put("isPrimary", primary)

    private companion object {
        const val ACCOUNT_ID = "account-1"
        const val PROFILE_SCOPED_NAME = "hulk_profiles_v1.account.$ACCOUNT_ID"
        const val PREFERENCES_SCOPED_NAME = "hulk_profile_preferences_v1.account.$ACCOUNT_ID"
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        const val KEY_SCHEMA_VERSION = "schema_version"
    }
}
