package sa.hulksa.player.data

import org.junit.Assert.*
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

private const val HOST_A = "https://first.example.test"
private const val HOST_B = "https://second.example.test"
private const val HOST_C = "https://third.example.test"
private const val USERNAME = "subscriber"

class TrustedHostSubscriberIdentityTest {
    @Test
    fun firstAuthenticationCreatesAndTrustsItsOwnScope() {
        val env = Environment()
        val saved = env.store.recordAuthenticated(session(HOST_A))
        assertEquals(stableAccountId(HOST_A, USERNAME),saved.accountId)
        assertEquals(AccountIdentityResolution.Known(saved.accountId),env.store.resolveAuthenticationIdentity(USERNAME,HOST_A))
    }

    @Test
    fun unknownHostAfterLogoutKeepsOriginalOwnerAndLocalDataWithoutDecision() {
        val env=Environment()
        val original=env.store.recordAuthenticated(session(HOST_A)).accountId
        env.scope.preferences("hulk_profiles_v1").edit().putString("profiles","existing-profile-data").commit()
        env.store.clearActiveSession()
        assertEquals(AccountIdentityResolution.Known(original),env.store.resolveAuthenticationIdentity(USERNAME,HOST_B))
        val restored=env.store.recordAuthenticated(session(HOST_B,"SECOND-CODE"))
        assertEquals(original,restored.accountId)
        assertEquals(HOST_B,restored.portalBaseUrl)
        assertEquals("existing-profile-data",env.scope.preferences("hulk_profiles_v1").getString("profiles",null))
    }

    @Test
    fun changedCodeAndProcessRecreationKeepOriginalAccount() {
        val env=Environment()
        val original=env.store.recordAuthenticated(session(HOST_A)).accountId
        env.store.clearActiveSession()
        val restarted=env.restart()
        assertEquals(original,restarted.store.recordAuthenticated(session(HOST_B,"THIRD-CODE")).accountId)
        assertEquals(original,restarted.restart().store.recordAuthenticated(session(HOST_C,"FOURTH-CODE")).accountId)
    }

    @Test
    fun previousExplicitlyIsolatedAccountsRetainTheirOwnEstablishedHosts() {
        val env=Environment()
        val a=env.store.recordAuthenticated(session(HOST_A),"historical-owner-a").accountId
        env.scope.preferences("hulk_user_library").edit().putString("data","A-ONLY").commit()
        val b=env.store.recordAuthenticated(session(HOST_B),"local-old-dialog-b").accountId
        env.scope.preferences("hulk_user_library").edit().putString("data","B-ONLY").commit()
        env.store.clearActiveSession()
        assertEquals(AccountIdentityResolution.Known(a),env.store.resolveAuthenticationIdentity(USERNAME,HOST_A))
        assertEquals(AccountIdentityResolution.Known(b),env.store.resolveAuthenticationIdentity(USERNAME,HOST_B))
        assertEquals(AccountIdentityResolution.Known(b),env.store.resolveAuthenticationIdentity(USERNAME,HOST_C))
        env.store.recordAuthenticated(session(HOST_C))
        assertEquals("B-ONLY",env.scope.preferences("hulk_user_library").getString("data",null))
        env.store.recordAuthenticated(session(HOST_A))
        assertEquals("A-ONLY",env.scope.preferences("hulk_user_library").getString("data",null))
        assertEquals(b,env.store.resolveAuthenticationIdentity(USERNAME,HOST_B).owner())
    }

    @Test
    fun differentUsernameNeverInheritsAnotherCustomersData() {
        val env=Environment()
        val a=env.store.recordAuthenticated(session(HOST_A)).accountId
        env.scope.preferences("hulk_profiles_v1").edit().putString("profiles","A-ONLY").commit()
        val b=env.store.recordAuthenticated(session(HOST_B,username="another-customer")).accountId
        assertNotEquals(a,b)
        assertNull(env.scope.preferences("hulk_profiles_v1").getString("profiles",null))
        assertEquals("A-ONLY",env.scope.preferences("hulk_profiles_v1",a).getString("profiles",null))
    }

    @Test
    fun legacyAliasOnlyAndMatchingLastOwnerOnlyRestoreTheirOriginalNamespaces() {
        for (alias in listOf(true,false)) {
            val env=Environment()
            if(alias) env.sessions.edit().putString(accountIdentityAliasKey(USERNAME),"legacy-owner").commit()
            else env.sessions.edit().putString("last_username",USERNAME).putString("last_account_id","legacy-owner").commit()
            env.scope.preferences("hulk_user_library","legacy-owner").edit().putString("data","LEGACY").commit()
            assertEquals("legacy-owner",env.store.recordAuthenticated(session(HOST_C)).accountId)
            assertEquals("LEGACY",env.scope.preferences("hulk_user_library").getString("data",null))
            assertEquals("legacy-owner",env.restart().store.resolveAuthenticationIdentity(USERNAME,HOST_A).owner())
        }
    }

    @Test
    fun aGlobalLastOwnerForAnotherUsernameDoesNotClaimTheNewCustomer() {
        val env=Environment()
        env.sessions.edit().putString("last_username","foreign").putString("last_account_id","foreign-owner").commit()
        assertEquals(stableAccountId(HOST_C,USERNAME),env.store.recordAuthenticated(session(HOST_C)).accountId)
    }

    @Test
    fun legacyActiveOwnerContinuesAcrossUnknownHostEvenWithoutTrustIndex() {
        val env=Environment()
        env.store.recordAuthenticated(session(HOST_A),"legacy-active",trustHost=false)
        assertEquals("legacy-active",env.store.recordAuthenticated(session(HOST_B)).accountId)
    }

    @Test
    fun multipleEvidenceFreeHistoricalOwnersStaySeparateWithoutInventingAChoice() {
        val env=Environment()
        env.store.recordAuthenticated(session(HOST_A),"old-a")
        env.scope.preferences("hulk_user_library").edit().putString("data","A").commit()
        env.store.recordAuthenticated(session(HOST_B),"old-b")
        env.scope.preferences("hulk_user_library").edit().putString("data","B").commit()
        env.store.clearActiveSession()
        env.sessions.edit().remove(accountIdentityAliasKey(USERNAME)).remove("last_username").remove("last_account_id").commit()
        val resolved=env.store.resolveAuthenticationIdentity(USERNAME,HOST_C)
        assertTrue(resolved is AccountIdentityResolution.New)
        assertEquals(resolved,env.restart().store.resolveAuthenticationIdentity(USERNAME,HOST_C))
        val isolated=env.store.recordAuthenticated(session(HOST_C)).accountId
        assertTrue(isolated.startsWith("local-restore-"))
        assertNull(env.scope.preferences("hulk_user_library").getString("data",null))
        assertEquals("A",env.scope.preferences("hulk_user_library","old-a").getString("data",null))
        assertEquals("B",env.scope.preferences("hulk_user_library","old-b").getString("data",null))
        assertEquals("old-a",env.store.recordAuthenticated(session(HOST_A)).accountId)
    }

    @Test
    fun failedCommitRestoresAliasTrustAndOriginalOwnerAcrossRestart() {
        val env=Environment()
        val original=env.store.recordAuthenticated(session(HOST_A)).accountId
        val before=env.sessions.durableSnapshot()
        env.sessions.commitResult=false
        assertThrows(IllegalStateException::class.java){env.store.recordAuthenticated(session(HOST_B))}
        assertEquals(before,env.sessions.durableSnapshot())
        assertEquals(original,env.store.activeAccountId())
        assertEquals(original,env.restart().store.metadata()?.accountId)
    }

    @Test
    fun continuationRequiresTheActualCurrentSameUsernameOwnerAndPreservesForeignHostIsolation() {
        val env=Environment()
        env.store.recordAuthenticated(session(HOST_A),"old-a")
        val current=env.store.recordAuthenticated(session(HOST_B),"old-b").accountId
        assertEquals(current,env.store.continuationAccountId(USERNAME,HOST_C,current))
        assertNull(env.store.continuationAccountId(USERNAME,HOST_A,current))
        assertNull(env.store.continuationAccountId("foreign",HOST_C,current))
        assertNull(env.store.continuationAccountId(USERNAME,HOST_A,"old-a"))
    }

    private fun AccountIdentityResolution.owner():String=when(this){
        is AccountIdentityResolution.Known->accountId
        is AccountIdentityResolution.New->accountId
    }

    private fun session(host:String,code:String="SYNTHETIC-CODE",username:String=USERNAME)=AuthenticatedSession(
        PortalConfig(host,PortalConfig.Source.ACCESS_CODE),Credentials(code,username,"synthetic-password"),
        AccountInfo(username,"Active",null,0,1,false),
    )

    private class Environment(
        val state:FakeSharedPreferences=FakeSharedPreferences(),
        val sessions:FakeSharedPreferences=FakeSharedPreferences(),
        val scopes:MutableMap<String,FakeSharedPreferences> = mutableMapOf(),
    ) {
        val scope=AccountScopeStore(state){name->scopes.getOrPut(name){FakeSharedPreferences()}}
        val store=AccountSessionStore(sessions,scope)
        fun restart()=Environment(state.restart(),sessions.restart(),scopes.mapValuesTo(mutableMapOf()){it.value.restart()})
    }
}
