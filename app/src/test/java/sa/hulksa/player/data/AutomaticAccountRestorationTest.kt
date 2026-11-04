package sa.hulksa.player.data

import android.content.SharedPreferences
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.ManualParentAuthProofRegistry
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.security.CredentialBase64Codec
import sa.hulksa.player.security.CredentialKeyStore
import sa.hulksa.player.security.CredentialVault

private const val ORIGINAL_HOST = "https://old.synthetic.example.test"
private const val ROTATED_HOST = "https://new.synthetic.example.test"

class AutomaticAccountRestorationTest {
    @Before
    fun clearPriorProcessOwnership() = clearProcessOwnership()

    @After
    fun clearProcessOwnership() {
        AuthenticationAttemptRegistry.invalidate()
        AuthenticatedSessionRegistry.clear()
        ManualParentAuthProofRegistry.invalidateAll()
    }

    @Test
    fun `normal repository login restores original owner and seeded local data after host rotation`() = runBlocking {
        val fixture = AutomaticRestorationFixture()
        val original = fixture.seedExistingOwner()
        fixture.repository.logout()
        fixture.resolvedHost = ROTATED_HOST

        val authenticated = fixture.repository.login(fixture.credentials(), remember = false)

        assertEquals(ROTATED_HOST, authenticated.portal.baseUrl)
        fixture.assertOwnedData(original)
        assertNull(fixture.vault.load())
    }

    @Test
    fun `valid code changes on same and new hosts keep original account and encrypted remember semantics`() = runBlocking {
        for (remember in listOf(false,true)) {
            val fixture=AutomaticRestorationFixture()
            val original=fixture.seedExistingOwner()
            for (host in listOf(ORIGINAL_HOST,ROTATED_HOST)) {
                fixture.resolvedHost=host
                val credentials=fixture.credentials(code="SYNTHETIC-CHANGED-${host.length}")
                fixture.repository.login(credentials,remember)
                fixture.assertOwnedData(original)
                assertEquals(if(remember)credentials else null,fixture.vault.load())
                assertFalse(fixture.sessionPreferences.all.values.contains(credentials.password))
                fixture.repository.logout()
                assertNull(fixture.vault.load())
                assertNull(fixture.store.metadata())
            }
        }
    }

    @Test
    fun `remembered startup and logout login restore data after process recreation and update-like restart`() = runBlocking {
        val fixture=AutomaticRestorationFixture()
        val original=fixture.seedExistingOwner()
        fixture.repository.login(fixture.credentials(),remember=true)
        clearProcessOwnership()
        val restarted=fixture.restart()
        restarted.resolvedHost=ROTATED_HOST
        val remembered=checkNotNull(restarted.repository.savedCredentials())
        restarted.repository.beginAuthentication(remembered,remember=true)
        restarted.assertOwnedData(original)
        restarted.repository.logout()
        clearProcessOwnership()
        val afterLogout=restarted.restart()
        assertNull(afterLogout.repository.savedCredentials())
        afterLogout.resolvedHost=ORIGINAL_HOST
        afterLogout.repository.login(afterLogout.credentials(code="SYNTHETIC-NEW-CODE"),remember=false)
        afterLogout.assertOwnedData(original)
    }

    @Test
    fun `different customer cannot see original profile library preferences or PIN`() = runBlocking {
        val fixture=AutomaticRestorationFixture()
        val original=fixture.seedExistingOwner()
        fixture.repository.logout()
        fixture.resolvedHost=ROTATED_HOST
        fixture.repository.login(fixture.credentials(username="another-customer"),remember=false)
        assertNotEquals(original,fixture.store.metadata()?.accountId)
        assertTrue(fixture.library.favorites().isEmpty())
        assertTrue(fixture.library.history().isEmpty())
        assertFalse(fixture.pins.hasPin("primary"))
        assertNull(fixture.scope.preferences("hulk_profiles_v1").getString("profiles",null))
        fixture.repository.login(fixture.credentials(),remember=false)
        fixture.assertOwnedData(original)
    }

    @Test
    fun `legacy alias and last owner without active metadata restore through real authentication`() = runBlocking {
        for (alias in listOf(true,false)) {
            val fixture=AutomaticRestorationFixture()
            val original=fixture.seedExistingOwner()
            fixture.repository.logout()
            if (alias) fixture.sessionPreferences.edit().remove("last_username").remove("last_account_id").commit()
            else fixture.sessionPreferences.edit().remove(accountIdentityAliasKey("synthetic-customer")).commit()
            clearProcessOwnership()
            val restarted=fixture.restart()
            restarted.resolvedHost=ROTATED_HOST
            restarted.repository.login(restarted.credentials(code="SYNTHETIC-LEGACY"),remember=false)
            restarted.assertOwnedData(original)
        }
    }

    @Test
    fun `invalid credentials and expired provider result cannot publish a new owner or remove old data`() = runBlocking {
        for (expired in listOf(false,true)) {
            val fixture=AutomaticRestorationFixture()
            val original=fixture.seedExistingOwner()
            fixture.vault.save(fixture.credentials())
            val prior=fixture.store.metadata()
            fixture.authenticate={portal,credentials->
                if(!expired)throw XtreamException.InvalidCredentials
                fixture.session(portal.baseUrl,credentials).let { it.copy(account=it.account.copy(expiresAtEpochSeconds=1L)) }
            }
            val result=runCatching{fixture.repository.login(fixture.credentials(code="SYNTHETIC-INVALID"),remember=true)}
            assertTrue(result.isFailure)
            assertEquals(prior,fixture.store.metadata())
            assertEquals(fixture.credentials(),fixture.vault.load())
            fixture.assertOwnedData(original)
        }
    }

    @Test
    fun `invalid resolver code leaves current owner and remembered credentials untouched`() = runBlocking {
        val fixture=AutomaticRestorationFixture()
        val original=fixture.seedExistingOwner()
        fixture.vault.save(fixture.credentials())
        val prior=fixture.store.metadata()
        fixture.resolverFailure=PortalException.InvalidAccessCode
        assertTrue(runCatching{fixture.repository.login(fixture.credentials(code="INVALID-SYNTHETIC"),true)}.exceptionOrNull() is PortalException.InvalidAccessCode)
        assertEquals(prior,fixture.store.metadata())
        assertEquals(fixture.credentials(),fixture.vault.load())
        fixture.assertOwnedData(original)
    }

    @Test
    fun `stale supplied session cannot reauthenticate into a newer same username historical owner`() = runBlocking {
        val fixture=AutomaticRestorationFixture()
        val original=fixture.seedExistingOwner()
        val stale=checkNotNull(AuthenticatedSessionRegistry.current())
        val b=fixture.session("https://historical-other.example.test",fixture.credentials(code="OTHER-SYNTHETIC"))
        val current=fixture.store.recordAuthenticated(b,"local-historical-other")
        AuthenticatedSessionRegistry.update(b,current)
        fixture.vault.save(b.credentials)
        fixture.resolvedHost=ROTATED_HOST
        assertTrue(runCatching{fixture.repository.reauthenticate(stale)}.exceptionOrNull() is CancellationException)
        assertEquals(current,fixture.store.metadata())
        assertEquals(b.credentials,fixture.vault.load())
        assertEquals(current.sessionId,AuthenticatedSessionRegistry.currentOwner()?.sessionId)
        assertNotNull(fixture.scope.preferences("hulk_profiles_v1",original).getString("profiles",null))
    }

    @Test
    fun `metadata persistence failure preserves prior owner credentials and profile data across restart`() = runBlocking {
        for(remember in listOf(false,true)) {
            val fixture=AutomaticRestorationFixture()
            val original=fixture.seedExistingOwner()
            fixture.vault.save(fixture.credentials())
            val prior=fixture.store.metadata()
            fixture.resolvedHost=ROTATED_HOST
            fixture.sessionPreferences.commitResult=false
            assertTrue(runCatching{fixture.repository.login(fixture.credentials(code="SYNTHETIC-FAILED"),remember)}.isFailure)
            assertEquals(prior,fixture.store.metadata())
            assertEquals(prior?.sessionId,AuthenticatedSessionRegistry.currentOwner()?.sessionId)
            val restarted=fixture.restart()
            assertEquals(prior,restarted.store.metadata())
            assertEquals(fixture.credentials(),restarted.vault.load())
            restarted.assertOwnedData(original)
        }
    }

    @Test
    fun `encrypted save failure never publishes candidate owner or destroys prior remembered owner`() = runBlocking {
        val fixture=AutomaticRestorationFixture()
        val original=fixture.seedExistingOwner()
        fixture.vault.save(fixture.credentials())
        val prior=fixture.store.metadata()
        fixture.envelope.commitResult=false
        assertTrue(runCatching{fixture.repository.login(fixture.credentials(username="different"),remember=true)}.isFailure)
        assertEquals(prior,fixture.store.metadata())
        assertEquals(prior?.sessionId,AuthenticatedSessionRegistry.currentOwner()?.sessionId)
        val restarted=fixture.restart()
        assertEquals(fixture.credentials(),restarted.vault.load())
        restarted.assertOwnedData(original)
    }

    @Test
    fun `late successful or failed old authentication cannot replace newer customer or parent proof`() = runBlocking {
        for(failOld in listOf(false,true)) {
            val fixture=AutomaticRestorationFixture()
            fixture.seedExistingOwner()
            fixture.repository.logout()
            val entered=CompletableDeferred<Unit>()
            val release=CompletableDeferred<Unit>()
            fixture.authenticate={portal,credentials->
                if(credentials.username=="synthetic-customer") {
                    entered.complete(Unit)
                    release.await()
                    if(failOld)throw XtreamException.InvalidCredentials
                }
                fixture.session(portal.baseUrl,credentials)
            }
            val old=async(Dispatchers.Default){runCatching{fixture.repository.login(fixture.credentials(),remember=true)}}
            entered.await()
            val newerCredentials=fixture.credentials(username="newer-customer")
            ManualParentAuthProofRegistry.noteManualLoginPreflight()
            ManualParentAuthProofRegistry.beginAuthenticationAttempt()
            fixture.repository.login(newerCredentials,remember=true)
            val newer=checkNotNull(fixture.store.metadata())
            release.complete(Unit)
            assertTrue(old.await().isFailure)
            assertEquals(newer,fixture.store.metadata())
            assertEquals(newer.sessionId,AuthenticatedSessionRegistry.currentOwner()?.sessionId)
            assertEquals(newerCredentials,fixture.vault.load())
            assertTrue(ManualParentAuthProofRegistry.hasValidProof())
            // Re-publishing the expected same owner must preserve the proof. A proof for
            // an older account/session would be invalidated by this real registry API.
            ManualParentAuthProofRegistry.onSessionReplacement(newer.accountId,newer.sessionId)
            assertTrue(ManualParentAuthProofRegistry.hasValidProof())
        }
    }

    @Test
    fun `late old authentication cannot replace a newer session for the same local account`() = runBlocking {
        val fixture=AutomaticRestorationFixture()
        val original=fixture.seedExistingOwner()
        fixture.repository.logout()
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        fixture.authenticate={portal,credentials->
            if(credentials.accessCode=="SYNTHETIC-1"){entered.complete(Unit);release.await()}
            fixture.session(portal.baseUrl,credentials)
        }
        val old=async(Dispatchers.Default){runCatching{fixture.repository.login(fixture.credentials(),remember=true)}}
        entered.await()
        fixture.resolvedHost=ROTATED_HOST
        val newerCredentials=fixture.credentials(code="SYNTHETIC-NEWER-SAME-OWNER")
        ManualParentAuthProofRegistry.noteManualLoginPreflight()
        ManualParentAuthProofRegistry.beginAuthenticationAttempt()
        fixture.repository.login(newerCredentials,remember=true)
        val newer=checkNotNull(fixture.store.metadata())
        assertEquals(original,newer.accountId)
        release.complete(Unit)
        assertTrue(old.await().exceptionOrNull() is CancellationException)
        assertEquals(newer,fixture.store.metadata())
        assertEquals(newer.sessionId,AuthenticatedSessionRegistry.currentOwner()?.sessionId)
        assertEquals(newerCredentials,fixture.vault.load())
        assertTrue(ManualParentAuthProofRegistry.hasValidProof())
            // Re-publishing the expected same owner must preserve the proof. A proof for
            // an older account/session would be invalidated by this real registry API.
            ManualParentAuthProofRegistry.onSessionReplacement(newer.accountId,newer.sessionId)
            assertTrue(ManualParentAuthProofRegistry.hasValidProof())
        fixture.assertOwnedData(original)
    }

    @Test
    fun `logout invalidates an earlier normal authentication before provider completion`() = runBlocking {
        val fixture=AutomaticRestorationFixture()
        val original=fixture.seedExistingOwner()
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        fixture.authenticate={portal,credentials->entered.complete(Unit);release.await();fixture.session(portal.baseUrl,credentials)}
        ManualParentAuthProofRegistry.noteManualLoginPreflight()
        ManualParentAuthProofRegistry.beginAuthenticationAttempt()
        val old=async(Dispatchers.Default){runCatching{fixture.repository.login(fixture.credentials(),remember=true)}}
        entered.await()
        fixture.repository.logout()
        release.complete(Unit)
        assertTrue(old.await().exceptionOrNull() is CancellationException)
        assertNull(fixture.store.metadata())
        assertNull(AuthenticatedSessionRegistry.currentOwner())
        assertNull(fixture.vault.load())
        assertFalse(ManualParentAuthProofRegistry.hasValidProof())
        assertNotNull(fixture.scope.preferences("hulk_profiles_v1",original).getString("profiles",null))
    }

    @Test
    fun `cancelled uncooperative provider cannot commit or discard saved local data`() = runBlocking {
        val fixture=AutomaticRestorationFixture()
        val original=fixture.seedExistingOwner()
        fixture.repository.logout()
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        fixture.authenticate={portal,credentials->withContext(NonCancellable){entered.complete(Unit);release.await();fixture.session(portal.baseUrl,credentials)}}
        val old=async(Dispatchers.Default){fixture.repository.login(fixture.credentials(),remember=true)}
        entered.await()
        old.cancel()
        release.complete(Unit)
        old.join()
        assertNull(fixture.store.metadata())
        assertNull(AuthenticatedSessionRegistry.currentOwner())
        assertNotNull(fixture.scope.preferences("hulk_user_library",original).getString("profile:primary:history",null))
        assertFalse(AuthenticationAttemptRegistry.hasActiveAttempt())
    }

    @Test
    fun `background restored session cannot overwrite a newer authenticated owner`() = runBlocking {
        val fixture=AutomaticRestorationFixture()
        fixture.seedExistingOwner()
        fixture.vault.save(fixture.credentials())
        AuthenticatedSessionRegistry.clear()
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        fixture.authenticate={portal,credentials->
            if(credentials.username=="synthetic-customer"){entered.complete(Unit);release.await()}
            fixture.session(portal.baseUrl,credentials)
        }
        val background=async(Dispatchers.Default){fixture.repository.currentAuthenticatedSession()}
        entered.await()
        val newerCredentials=fixture.credentials(username="newer-customer")
        fixture.repository.login(newerCredentials,remember=true)
        val newer=fixture.store.metadata()
        release.complete(Unit)
        assertNull(background.await())
        assertEquals(newer,fixture.store.metadata())
        assertEquals(newerCredentials,fixture.vault.load())
    }
}

internal class AutomaticRestorationFixture(
    val sessionPreferences: FakeSharedPreferences = FakeSharedPreferences(),
    val scopePreferences: FakeSharedPreferences = FakeSharedPreferences(),
    val scoped: MutableMap<String, FakeSharedPreferences> = mutableMapOf(),
    val envelope: FakeSharedPreferences = FakeSharedPreferences(),
    private val keyStore: CredentialKeyStore = SyntheticRestorationKeyStore(),
) {
    val scope = AccountScopeStore(scopePreferences) { name -> scoped.getOrPut(name) { FakeSharedPreferences() } }
    val store = AccountSessionStore(sessionPreferences, scope)
    val vault = CredentialVault(envelope, keyStore, SyntheticRestorationBase64)
    val profiles=ProfileStore(scope){name->scoped.getOrPut(name){FakeSharedPreferences()}}
    val profilePreferences=ProfilePreferencesStore(scope,profiles)
    val library=UserLibrary(scope,profiles,KidsContentFilterStore(TestAppContext()))
    val pins=ProfilePinCredentialStore(
        accountScope=scope,profilePreferencesStore=profilePreferences,secureRandom=SecureRandom(),
        attemptProtection=SyntheticPinAttemptProtection,codec=PinCredentialCodec(
            encode={java.util.Base64.getEncoder().encodeToString(it)},
            decode={runCatching{java.util.Base64.getDecoder().decode(it)}.getOrNull()},
        ),
        scopedPreferencesForAccount={accountId->scoped.getOrPut(accountScopedPreferencesName(ProfilePinCredentialStore.PREFERENCES_NAME,accountId)){FakeSharedPreferences()}},
        cpuDispatcher=Dispatchers.Default,ioDispatcher=Dispatchers.IO,
    )
    var resolvedHost=ORIGINAL_HOST
    var resolverFailure:Throwable?=null
    var authenticate:suspend(PortalConfig,Credentials)->AuthenticatedSession={portal,credentials->session(portal.baseUrl,credentials)}
    val repository=HulkRepository(
        context=TestAppContext(),vault=vault,accountSessionStore=store,suspendDownloadOwner={},
        portalResolver=object:PortalResolver(){override suspend fun resolve(accessCode:String):PortalConfig {
            resolverFailure?.let { throw it }
            return PortalConfig(resolvedHost,PortalConfig.Source.ACCESS_CODE)
        }},
        client=object:XtreamClient(){override suspend fun authenticate(portal:PortalConfig,credentials:Credentials)=this@AutomaticRestorationFixture.authenticate.invoke(portal,credentials)},
    )

    fun credentials(code:String="SYNTHETIC-1",username:String="synthetic-customer")=Credentials(code,username,"synthetic-password")

    suspend fun seedExistingOwner():String {
        val initial=session(ORIGINAL_HOST,credentials())
        val metadata=store.recordAuthenticated(initial)
        AuthenticatedSessionRegistry.update(initial,metadata)
        scope.preferences("hulk_profiles_v1").edit()
            .putString("profiles","""[{"id":"primary","displayName":"Retained profile","kind":"STANDARD","avatarKey":"default","createdAtEpochMs":1790000000000,"isPrimary":true}]""")
            .putString("active_profile_id","primary").putInt("schema_version",1).commit()
        scope.preferences("hulk_user_library").edit()
            .putStringSet("profile:primary:favorites",setOf("movie:1"))
            .putString("profile:primary:history",encodeHistoryDocument(listOf(HistoryEntry("movie:1","Synthetic movie",null,"movie",1,"mp4",false,42_000L,300_000L,1790000000000L)))).commit()
        profilePreferences.setRouting(true,"primary")
        profilePreferences.setViewing("primary",ProfileViewingPreferences(preferredAudioLanguage="ar",autoplayNextEpisode=false))
        check(pins.setPin("primary","1234"))
        return metadata.accountId
    }

    suspend fun assertOwnedData(accountId:String) {
        assertEquals(accountId,store.metadata()?.accountId)
        assertEquals(accountId,scope.activeAccountId())
        assertEquals("Retained profile",profiles.profiles().single().displayName)
        assertEquals(setOf("movie:1"),library.favorites())
        assertEquals(42_000L,library.history().single().positionMs)
        assertTrue(profilePreferences.routing().directEntryEnabled)
        assertEquals("ar",profilePreferences.viewing("primary").preferredAudioLanguage)
        assertTrue(pins.verifyPin("primary","1234"))
    }

    fun restart()=AutomaticRestorationFixture(sessionPreferences.restart(),scopePreferences.restart(),scoped.mapValuesTo(mutableMapOf()){it.value.restart()},envelope.restart(),keyStore)

    fun session(host:String,credentials:Credentials)=AuthenticatedSession(
        PortalConfig(host,PortalConfig.Source.ACCESS_CODE),credentials,AccountInfo(credentials.username,"Active",null,0,1,false),
    )
}

private class SyntheticRestorationKeyStore:CredentialKeyStore {
    private var key:SecretKey?=null
    override fun exists(alias:String)=key!=null
    override fun get(alias:String):SecretKey?=key
    override fun create(alias:String):SecretKey=SecretKeySpec(ByteArray(32){7},"AES").also{key=it}
    override fun delete(alias:String){key=null}
}

private object SyntheticRestorationBase64:CredentialBase64Codec {
    override fun encode(bytes:ByteArray):String=java.util.Base64.getEncoder().encodeToString(bytes)
    override fun decode(value:String):ByteArray=java.util.Base64.getDecoder().decode(value)
}

private object SyntheticPinAttemptProtection:CredentialAttemptProtection {
    override fun check(preferences:SharedPreferences,prefix:String)=FourDigitCredentialAttemptDecision.Allowed
    override fun recordFailure(preferences:SharedPreferences,prefix:String)=FourDigitCredentialAttemptDecision.Allowed
    override fun reset(preferences:SharedPreferences,prefix:String)=true
    override fun removeFromEditor(editor:SharedPreferences.Editor,prefix:String):SharedPreferences.Editor=editor
}
