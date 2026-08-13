package sa.hulksa.player.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.model.Credentials

@RunWith(AndroidJUnit4::class)
class CredentialVaultInstrumentedTest {
    private val vault = CredentialVault(
        InstrumentationRegistry.getInstrumentation().targetContext,
    )

    @Before
    fun setUp() {
        vault.clear()
    }

    @After
    fun tearDown() {
        vault.clear()
    }

    @Test
    fun rememberAccountPersistsAccessCodeWithUsernameAndPassword() {
        val credentials = Credentials(
            accessCode = "HULK-ABCD-EFGH-JKMN-PQRS",
            username = "subscriber",
            password = "secret",
        )

        vault.save(credentials)

        assertEquals(credentials, vault.load())
    }

    @Test
    fun clearingRememberedAccountRemovesAccessCodeAndCredentials() {
        vault.save(
            Credentials(
                accessCode = "HULK-ABCD-EFGH-JKMN-PQRS",
                username = "subscriber",
                password = "secret",
            ),
        )

        vault.clear()

        assertNull(vault.load())
    }
}
