package sa.hulksa.player.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.security.CredentialVault

@RunWith(AndroidJUnit4::class)
class SessionLogoutRegistryRaceInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearSessionState()
    }

    @After
    fun tearDown() {
        clearSessionState()
    }

    @Test
    fun logoutRemainsFinalRegistryWriterAgainstInFlightSessionPersistence() = runBlocking {
        val authenticated = session()
        val loginOwnsPersistenceLock = CountDownLatch(1)
        val allowLoginPersistence = CountDownLatch(1)

        AuthenticatedSessionRegistry.update(authenticated)

        val loginPersistence = launch(Dispatchers.Default) {
            runSessionPersistenceTransaction {
                loginOwnsPersistenceLock.countDown()
                check(allowLoginPersistence.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting to release in-flight session persistence"
                }
                AccountSessionStore(context).recordAuthenticated(authenticated)
                CredentialVault(context).save(authenticated.credentials)
                AuthenticatedSessionRegistry.update(authenticated)
            }
        }

        assertTrue(loginOwnsPersistenceLock.await(5, TimeUnit.SECONDS))

        val logout = launch(start = CoroutineStart.UNDISPATCHED) {
            HulkRepository(context).logout()
        }

        // logout() must invalidate in-memory authentication immediately, before waiting for
        // the persistence lock currently owned by the in-flight login transaction.
        assertNull(AuthenticatedSessionRegistry.current())

        allowLoginPersistence.countDown()
        loginPersistence.join()
        logout.join()

        // The in-flight login is allowed to republish the registry before releasing the lock.
        // Logout must then acquire the same lock, durably clear session state, and clear the
        // registry again as the final authoritative writer.
        assertNull(AuthenticatedSessionRegistry.current())
    }

    private fun session(): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(HOST, PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials(
            accessCode = ACCESS_CODE,
            username = USERNAME,
            password = "secret",
        ),
        account = AccountInfo(
            username = USERNAME,
            status = "Active",
            expiresAtEpochSeconds = null,
            activeConnections = 0,
            maxConnections = 1,
            isTrial = false,
        ),
    )

    private fun clearSessionState() {
        AuthenticatedSessionRegistry.clear()
        CredentialVault(context).clear()
        AccountSessionStore(context).clearActiveSession()
    }

    private companion object {
        const val HOST = "http://registry-race.example.test:8080"
        const val ACCESS_CODE = "aB12Cd34Ef"
        const val USERNAME = "registry-race-subscriber"
    }
}
