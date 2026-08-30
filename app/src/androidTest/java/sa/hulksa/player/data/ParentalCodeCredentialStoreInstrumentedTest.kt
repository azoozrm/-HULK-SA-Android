package sa.hulksa.player.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.model.ProfileKind

@RunWith(AndroidJUnit4::class)
class ParentalCodeCredentialStoreInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearTestState()
    }

    @After
    fun tearDown() {
        clearTestState()
    }

    @Test
    fun adultProfilePinNeverCreatesParentalCredentialAndBothValuesStayIndependent() = runBlocking {
        val accountScope = AccountScopeStore(context)
        assertTrue(accountScope.bind(ACCOUNT_A))
        val profiles = ProfileStore(context)
        val profilePins = ProfilePinCredentialStore(context)
        val parentalCodes = ParentalCodeCredentialStore(context)

        assertTrue(profilePins.setPin(ProfileStore.PRIMARY_PROFILE_ID, "5678"))
        assertFalse(parentalCodes.hasCode())
        assertFalse(parentalCodes.verifyCode("5678"))

        assertTrue(parentalCodes.setCode("1234"))
        requireNotNull(profiles.createProfile("أطفال", kind = ProfileKind.KIDS))
        assertTrue(parentalCodes.verifyCode("1234"))
        assertFalse(parentalCodes.verifyCode("5678"))
        assertTrue(profilePins.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "5678"))
        assertFalse(profilePins.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "1234"))

        assertTrue(profilePins.setPin(ProfileStore.PRIMARY_PROFILE_ID, "2468"))
        assertTrue(parentalCodes.verifyCode("1234"))
        assertTrue(parentalCodes.setCode("4321"))
        assertTrue(profilePins.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "2468"))
        assertFalse(profilePins.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "4321"))
    }

    @Test
    fun multipleKidsProfilesShareOneExplicitAccountCredentialWithoutProfileKeys() = runBlocking {
        val accountScope = AccountScopeStore(context)
        assertTrue(accountScope.bind(ACCOUNT_A))
        val profiles = ProfileStore(context)
        val firstKids = requireNotNull(profiles.createProfile("أطفال 1", kind = ProfileKind.KIDS))
        val secondKids = requireNotNull(profiles.createProfile("أطفال 2", kind = ProfileKind.KIDS))
        val parentalCodes = ParentalCodeCredentialStore(context)

        assertTrue(parentalCodes.setCode("2580"))
        assertTrue(parentalCodes.verifyCode("2580"))
        assertEquals(ProfileKind.KIDS, profiles.profiles().first { it.id == firstKids.id }.kind)
        assertEquals(ProfileKind.KIDS, profiles.profiles().first { it.id == secondKids.id }.kind)

        val stored = context.getSharedPreferences(
            accountScopedPreferencesName(
                ParentalCodeCredentialStore.PREFERENCES_NAME,
                ACCOUNT_A,
            ),
            Context.MODE_PRIVATE,
        ).all
        assertTrue(stored.keys.none { it.contains("profile:") })
        assertEquals(
            ParentalCodeCredentialStore.EXPLICIT_USER_CREATED_PROVENANCE,
            stored["credential_provenance"],
        )
    }

    @Test
    fun logoutAndAccountSwitchNeverReuseAnotherAccountsParentalCode() = runBlocking {
        val accountScope = AccountScopeStore(context)
        val parentalCodes = ParentalCodeCredentialStore(context)

        assertTrue(accountScope.bind(ACCOUNT_A))
        assertTrue(parentalCodes.setCode("1111"))
        assertTrue(parentalCodes.verifyCode("1111"))

        accountScope.clearActive()
        assertFalse(parentalCodes.hasCode())
        assertFalse(parentalCodes.verifyCode("1111"))

        assertTrue(accountScope.bind(ACCOUNT_B))
        assertFalse(parentalCodes.hasCode())
        assertFalse(parentalCodes.verifyCode("1111"))
        assertTrue(parentalCodes.setCode("2222"))
        assertTrue(parentalCodes.verifyCode("2222"))
        assertFalse(parentalCodes.verifyCode("1111"))

        assertTrue(accountScope.bind(ACCOUNT_A))
        assertTrue(parentalCodes.verifyCode("1111"))
        assertFalse(parentalCodes.verifyCode("2222"))
    }

    @Test
    fun legacyKidsAdultPinIsProofOnlyAndIsNeverCopiedIntoParentalStore() = runBlocking {
        val accountScope = AccountScopeStore(context)
        assertTrue(accountScope.bind(ACCOUNT_A))
        val profiles = ProfileStore(context)
        requireNotNull(profiles.createProfile("أطفال", kind = ProfileKind.KIDS))
        val profilePins = ProfilePinCredentialStore(context)
        val parentalCodes = ParentalCodeCredentialStore(context)
        assertTrue(profilePins.setPin(ProfileStore.PRIMARY_PROFILE_ID, "1357"))

        assertEquals(
            LegacyParentalCodeMigrationResult.LEGACY_PARENT_PROOF_REQUIRED,
            parentalCodes.ensureLegacyMigration(
                accountId = ACCOUNT_A,
                hadKidsProfiles = true,
                legacyPrimaryProfileId = ProfileStore.PRIMARY_PROFILE_ID,
                profilePinCredentialStore = profilePins,
            ),
        )
        assertFalse(parentalCodes.hasCode())
        assertFalse(parentalCodes.verifyCode("1357"))
        assertTrue(profilePins.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "1357"))

        assertTrue(parentalCodes.setCode("2468"))
        assertTrue(parentalCodes.verifyCode("2468"))
        assertFalse(parentalCodes.verifyCode("1357"))
        assertTrue(profilePins.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "1357"))
        assertFalse(profilePins.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "2468"))
    }

    @Test
    fun legacyKidsWithoutUsableAdultProofRemainsFailClosed() = runBlocking {
        val accountScope = AccountScopeStore(context)
        assertTrue(accountScope.bind(ACCOUNT_A))
        val profiles = ProfileStore(context)
        requireNotNull(profiles.createProfile("أطفال", kind = ProfileKind.KIDS))
        val profilePins = ProfilePinCredentialStore(context)
        val parentalCodes = ParentalCodeCredentialStore(context)

        assertEquals(
            LegacyParentalCodeMigrationResult.FAIL_CLOSED_NO_USABLE_PARENT_PROOF,
            parentalCodes.ensureLegacyMigration(
                accountId = ACCOUNT_A,
                hadKidsProfiles = true,
                legacyPrimaryProfileId = ProfileStore.PRIMARY_PROFILE_ID,
                profilePinCredentialStore = profilePins,
            ),
        )
        assertFalse(parentalCodes.hasCode())
        assertFalse(parentalCodes.verifyCode("0000"))
    }

    @Test
    fun accountWithoutLegacyKidsNeverPromotesFutureProfilePinToParentalCode() = runBlocking {
        val accountScope = AccountScopeStore(context)
        assertTrue(accountScope.bind(ACCOUNT_B))
        val profilePins = ProfilePinCredentialStore(context)
        val parentalCodes = ParentalCodeCredentialStore(context)

        assertEquals(
            LegacyParentalCodeMigrationResult.COMPLETED_WITHOUT_LEGACY_CREDENTIAL,
            parentalCodes.ensureLegacyMigration(
                accountId = ACCOUNT_B,
                hadKidsProfiles = false,
                legacyPrimaryProfileId = ProfileStore.PRIMARY_PROFILE_ID,
                profilePinCredentialStore = profilePins,
            ),
        )
        assertTrue(profilePins.setPin(ProfileStore.PRIMARY_PROFILE_ID, "8642"))
        requireNotNull(ProfileStore(context).createProfile("أطفال", kind = ProfileKind.KIDS))

        assertEquals(
            LegacyParentalCodeMigrationResult.ALREADY_COMPLETED,
            parentalCodes.ensureLegacyMigration(
                accountId = ACCOUNT_B,
                hadKidsProfiles = true,
                legacyPrimaryProfileId = ProfileStore.PRIMARY_PROFILE_ID,
                profilePinCredentialStore = profilePins,
            ),
        )
        assertFalse(parentalCodes.hasCode())
        assertFalse(parentalCodes.verifyCode("8642"))
        assertTrue(profilePins.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "8642"))
    }

    @Test
    fun preReleaseV1QualificationCredentialIsIgnoredWithoutDestructiveGuessing() = runBlocking {
        val accountScope = AccountScopeStore(context)
        assertTrue(accountScope.bind(ACCOUNT_A))
        val staleV1 = context.getSharedPreferences(
            accountScopedPreferencesName(PRE_RELEASE_PARENTAL_CODE_PREFERENCES, ACCOUNT_A),
            Context.MODE_PRIVATE,
        )
        assertTrue(
            staleV1.edit()
                .putInt("credential_version", 1)
                .putInt("iterations", 120_000)
                .putString("salt", "qualification-salt")
                .putString("verifier", "qualification-verifier")
                .putBoolean("legacy_profile_pin_migration_complete_v1", true)
                .commit(),
        )

        val parentalCodes = ParentalCodeCredentialStore(context)
        assertFalse(parentalCodes.hasCode())
        assertFalse(parentalCodes.verifyCode("5678"))
        assertTrue(staleV1.contains("verifier"))

        assertTrue(parentalCodes.setCode("1234"))
        assertTrue(parentalCodes.verifyCode("1234"))
        assertTrue(staleV1.contains("verifier"))
    }

    @Test
    fun parentalCredentialWithoutExplicitUserCreatedProvenanceFailsClosed() = runBlocking {
        val accountScope = AccountScopeStore(context)
        assertTrue(accountScope.bind(ACCOUNT_A))
        val parentalCodes = ParentalCodeCredentialStore(context)
        assertTrue(parentalCodes.setCode("9876"))
        assertTrue(parentalCodes.hasCode())

        val preferences = context.getSharedPreferences(
            accountScopedPreferencesName(
                ParentalCodeCredentialStore.PREFERENCES_NAME,
                ACCOUNT_A,
            ),
            Context.MODE_PRIVATE,
        )
        assertTrue(preferences.edit().remove("credential_provenance").commit())

        assertFalse(parentalCodes.hasCode())
        assertFalse(parentalCodes.verifyCode("9876"))
    }

    private fun clearTestState() {
        val baseNames = setOf(
            ACCOUNT_SCOPE_PREFERENCES,
            PROFILE_PREFERENCES,
            PROFILE_PIN_PREFERENCES,
            PROFILE_SECURITY_METADATA_PREFERENCES,
            PRE_RELEASE_PARENTAL_CODE_PREFERENCES,
            ParentalCodeCredentialStore.PREFERENCES_NAME,
        )
        val preferenceNames = baseNames.toMutableSet()
        listOf(ACCOUNT_A, ACCOUNT_B).forEach { accountId ->
            baseNames.forEach { baseName ->
                preferenceNames += accountScopedPreferencesName(baseName, accountId)
            }
        }
        preferenceNames.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private companion object {
        const val ACCOUNT_A = "parental-code-test-account-a"
        const val ACCOUNT_B = "parental-code-test-account-b"
        const val ACCOUNT_SCOPE_PREFERENCES = "hulk_account_scope_v1"
        const val PROFILE_PREFERENCES = "hulk_profiles_v1"
        const val PROFILE_PIN_PREFERENCES = "hulk_profile_pin_credentials_v1"
        const val PROFILE_SECURITY_METADATA_PREFERENCES = "hulk_profile_preferences_v1"
        const val PRE_RELEASE_PARENTAL_CODE_PREFERENCES = "hulk_parental_code_credentials_v1"
    }
}
