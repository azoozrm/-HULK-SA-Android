package sa.hulksa.player.security

import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.FakeSharedPreferences
import sa.hulksa.player.model.Credentials

/**
 * Deterministic policy tests for the bounded existing-key recovery. No Android Keystore, owner
 * package, credential or data-clearing instrumentation is involved.
 */
class CredentialVaultKeyRecoveryTest {
    @Test
    fun `normal save and load create and reuse one synthetic key`() {
        val keyStore = FakeCredentialKeyStore(initialKey = null)
        val preferences = FakeSharedPreferences()
        val vault = CredentialVault(preferences, keyStore, JVM_BASE64)

        vault.save(CREDENTIALS)

        assertEquals(1, keyStore.createAttempts)
        assertEquals(0, keyStore.deleteCount)
        assertEquals(CREDENTIALS, vault.load())
        assertEquals(CREDENTIALS, CredentialVault(preferences.restart(), keyStore, JVM_BASE64).load())
    }

    @Test
    fun `normal save reuses a healthy existing key without rotation`() {
        val keyStore = FakeCredentialKeyStore(initialKey = syntheticKey(7))
        val preferences = FakeSharedPreferences()
        val vault = CredentialVault(preferences, keyStore, JVM_BASE64)

        vault.save(CREDENTIALS)

        assertEquals(0, keyStore.createAttempts)
        assertEquals(0, keyStore.deleteCount)
        assertEquals(CREDENTIALS, vault.load())
    }

    @Test
    fun `invalid existing key is replaced once and the encrypted save succeeds`() {
        val keyStore = FakeCredentialKeyStore()
        keyStore.getFailures += KeyStoreException("Invalid key blob")
        val preferences = FakeSharedPreferences()
        val vault = CredentialVault(preferences, keyStore, JVM_BASE64)

        vault.save(CREDENTIALS)

        assertEquals(1, keyStore.deleteCount)
        assertEquals(1, keyStore.createAttempts)
        assertEquals(CREDENTIALS, vault.load())
        assertEquals(CREDENTIALS, CredentialVault(preferences.restart(), keyStore, JVM_BASE64).load())
    }

    @Test
    fun `replacement key failure stops after one attempt and does not loop`() {
        val keyStore = FakeCredentialKeyStore()
        keyStore.getFailures += KeyStoreException("Invalid key blob")
        keyStore.createFailure = KeyStoreException("Invalid key blob")
        val vault = CredentialVault(FakeSharedPreferences(), keyStore, JVM_BASE64)

        assertThrows(KeyStoreException::class.java) {
            vault.save(CREDENTIALS)
        }

        assertEquals(1, keyStore.deleteCount)
        assertEquals(1, keyStore.createAttempts)
        assertEquals(0, keyStore.createSuccesses)
    }

    @Test
    fun `transient keystore operation failure does not delete the existing key`() {
        val keyStore = FakeCredentialKeyStore()
        keyStore.getFailures += InvalidKeyException("Keystore operation failed")
        val vault = CredentialVault(FakeSharedPreferences(), keyStore, JVM_BASE64)

        assertThrows(InvalidKeyException::class.java) {
            vault.save(CREDENTIALS)
        }

        assertEquals(0, keyStore.deleteCount)
        assertEquals(0, keyStore.createAttempts)
    }

    @Test
    fun `unrecoverable wrapper around a generic failure does not delete key or envelope`() {
        val keyStore = FakeCredentialKeyStore()
        keyStore.getFailures += UnrecoverableKeyException("Unable to recover key").apply {
            initCause(IllegalStateException("Keystore service unavailable"))
        }
        val preferences = FakeSharedPreferences().also(::seedEnvelope)
        val vault = CredentialVault(preferences, keyStore, JVM_BASE64)

        assertThrows(UnrecoverableKeyException::class.java) {
            vault.save(CREDENTIALS)
        }

        assertEquals(0, keyStore.deleteCount)
        assertEquals(0, keyStore.createAttempts)
        assertEquals(SEEDED_IV, preferences.durableValue(CredentialVault.KEY_IV))
        assertEquals(SEEDED_PAYLOAD, preferences.durableValue(CredentialVault.KEY_PAYLOAD))
    }

    @Test
    fun `unrecoverable wrapper with invalid blob cause recovers exactly once`() {
        val keyStore = FakeCredentialKeyStore()
        keyStore.getFailures += UnrecoverableKeyException("Unable to recover key").apply {
            initCause(KeyStoreException("Invalid key blob"))
        }
        val preferences = FakeSharedPreferences().also(::seedEnvelope)
        val vault = CredentialVault(preferences, keyStore, JVM_BASE64)

        vault.save(CREDENTIALS)

        assertEquals(1, keyStore.deleteCount)
        assertEquals(1, keyStore.createAttempts)
        assertEquals(CREDENTIALS, vault.load())
        assertEquals(CREDENTIALS, CredentialVault(preferences.restart(), keyStore, JVM_BASE64).load())
    }

    @Test
    fun `preference commit failure does not delete the existing key`() {
        val keyStore = FakeCredentialKeyStore()
        val preferences = FakeSharedPreferences().apply { commitResult = false }
        val vault = CredentialVault(preferences, keyStore, JVM_BASE64)

        assertThrows(IllegalStateException::class.java) {
            vault.save(CREDENTIALS)
        }

        assertEquals(0, keyStore.deleteCount)
        assertEquals(0, keyStore.createAttempts)
    }

    @Test
    fun `failed durable envelope removal stops before key deletion`() {
        val keyStore = FakeCredentialKeyStore()
        keyStore.getFailures += KeyStoreException("Invalid key blob")
        val preferences = FakeSharedPreferences().apply { commitResult = false }
        val vault = CredentialVault(preferences, keyStore, JVM_BASE64)

        assertThrows(CredentialEnvelopeRemovalException::class.java) {
            vault.save(CREDENTIALS)
        }

        assertEquals(0, keyStore.deleteCount)
        assertEquals(0, keyStore.createAttempts)
    }

    @Test
    fun `direct durable clear failure preserves the existing key for logout semantics`() {
        val keyStore = FakeCredentialKeyStore()
        val preferences = FakeSharedPreferences().apply { commitResult = false }
        val vault = CredentialVault(preferences, keyStore, JVM_BASE64)

        assertThrows(CredentialEnvelopeRemovalException::class.java) {
            vault.clear()
        }

        assertEquals(0, keyStore.deleteCount)
        assertTrue(keyStore.exists(CredentialVault.KEY_ALIAS))
    }

    @Test
    fun `only confirmed invalid key failures are recoverable`() {
        assertTrue(isRecoverableCredentialKeyFailure(KeyStoreException("Invalid key blob")))
        assertTrue(
            isRecoverableCredentialKeyFailure(
                InvalidKeyException(
                    "Keystore operation failed",
                    KeyStoreException("Invalid key blob"),
                ),
            ),
        )
        assertTrue(isRecoverableCredentialKeyFailure(UnrecoverableKeyException("Invalid key blob")))
        assertTrue(
            isRecoverableCredentialKeyFailure(
                UnrecoverableKeyException("Unable to recover key").apply {
                    initCause(KeyStoreException("Invalid key blob"))
                },
            ),
        )
        assertFalse(
            isRecoverableCredentialKeyFailure(
                UnrecoverableKeyException("Unable to recover key").apply {
                    initCause(IllegalStateException("Keystore service unavailable"))
                },
            ),
        )
        assertFalse(isRecoverableCredentialKeyFailure(InvalidKeyException("Keystore operation failed")))
        assertFalse(isRecoverableCredentialKeyFailure(KeyStoreException("Keystore operation failed")))
        assertFalse(
            isRecoverableCredentialKeyFailure(
                IllegalStateException("Unable to persist credential envelope"),
            ),
        )
    }

    private companion object {
        val CREDENTIALS = Credentials(
            accessCode = "SYNTHETIC-CODE",
            username = "synthetic-user",
            password = "synthetic-password",
        )
    }
}

private const val SEEDED_IV = "synthetic-iv"
private const val SEEDED_PAYLOAD = "synthetic-payload"

private fun seedEnvelope(preferences: FakeSharedPreferences) {
    check(
        preferences.edit()
            .putString(CredentialVault.KEY_IV, SEEDED_IV)
            .putString(CredentialVault.KEY_PAYLOAD, SEEDED_PAYLOAD)
            .commit(),
    )
}

private class FakeCredentialKeyStore(
    initialKey: SecretKey? = syntheticKey(0),
) : CredentialKeyStore {
    private var key: SecretKey? = initialKey
    val getFailures = mutableListOf<Throwable>()
    var createFailure: Throwable? = null
    var createAttempts: Int = 0
        private set
    var createSuccesses: Int = 0
        private set
    var deleteCount: Int = 0
        private set

    override fun exists(alias: String): Boolean = key != null

    override fun get(alias: String): SecretKey? {
        if (getFailures.isNotEmpty()) throw getFailures.removeAt(0)
        return key
    }

    override fun create(alias: String): SecretKey {
        createAttempts += 1
        createFailure?.let { throw it }
        val replacement = syntheticKey(createAttempts)
        key = replacement
        createSuccesses += 1
        return replacement
    }

    override fun delete(alias: String) {
        deleteCount += 1
        key = null
    }
}

private fun syntheticKey(seed: Int): SecretKey =
    SecretKeySpec(ByteArray(32) { index -> (index + seed).toByte() }, "AES")

private object JVM_BASE64 : CredentialBase64Codec {
    override fun encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    override fun decode(value: String): ByteArray =
        Base64.getDecoder().decode(value)
}
