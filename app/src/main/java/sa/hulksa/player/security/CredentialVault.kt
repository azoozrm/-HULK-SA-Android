package sa.hulksa.player.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import sa.hulksa.player.model.Credentials
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the account in an Android Keystore-backed AES/GCM envelope. The APK
 * never contains a test username or password and Android backup is disabled.
 */
class CredentialVault internal constructor(
    private val preferences: SharedPreferences,
    private val keyStore: CredentialKeyStore = AndroidCredentialKeyStore(),
    private val base64: CredentialBase64Codec = AndroidCredentialBase64Codec(),
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE),
    )

    /**
     * Persists newly authenticated credentials. If an existing app-owned key is rejected as
     * invalid, the old envelope is durably removed, only [KEY_ALIAS] is deleted, and the save is
     * retried exactly once with a replacement key and a fresh cipher/IV.
     */
    fun save(credentials: Credentials) {
        synchronized(OPERATION_LOCK) {
            val plaintext = CredentialPayloadCodec.encode(credentials)
            try {
                val existingKey = keyStore.exists(KEY_ALIAS)
                try {
                    persistWithCurrentKey(plaintext)
                } catch (error: Throwable) {
                    if (!existingKey || !isRecoverableCredentialKeyFailure(error)) throw error
                    recoverInvalidExistingKey(plaintext, error)
                }
            } finally {
                plaintext.fill(0)
            }
        }
    }

    fun load(): Credentials? = synchronized(OPERATION_LOCK) {
        val credentials = runCatching {
            val iv = preferences.getString(KEY_IV, null)?.let(base64::decode)
                ?: return@runCatching null
            val payload = preferences.getString(KEY_PAYLOAD, null)?.let(base64::decode)
                ?: return@runCatching null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyStore.get(KEY_ALIAS) ?: keyStore.create(KEY_ALIAS),
                GCMParameterSpec(128, iv),
            )
            val plaintext = cipher.doFinal(payload)
            try {
                CredentialPayloadCodec.decode(plaintext)
            } finally {
                plaintext.fill(0)
            }
        }.getOrNull()
        if (credentials == null) clear()
        credentials
    }

    /**
     * Removes the encrypted credential envelope. Android applies the cleared editor to the
     * in-process map even when the durable write fails, so the commit result is checked: a failed
     * durable removal throws [CredentialEnvelopeRemovalException] instead of letting callers treat
     * the old envelope as removed while it stays restart-restorable.
     */
    fun clear() {
        synchronized(OPERATION_LOCK) {
            if (!preferences.edit().clear().commit()) {
                throw CredentialEnvelopeRemovalException()
            }
        }
    }

    private fun persistWithCurrentKey(plaintext: ByteArray) {
        val key = keyStore.get(KEY_ALIAS) ?: keyStore.create(KEY_ALIAS)
        encryptAndCommit(key, plaintext)
    }

    private fun recoverInvalidExistingKey(plaintext: ByteArray, original: Throwable) {
        // The envelope must be gone before its key is touched. A failed durable clear stops the
        // recovery and leaves the existing key available for the caller's fail-closed cleanup.
        clear()

        try {
            keyStore.delete(KEY_ALIAS)
        } catch (deleteFailure: Throwable) {
            deleteFailure.addSuppressed(original)
            throw deleteFailure
        }

        try {
            encryptAndCommit(keyStore.create(KEY_ALIAS), plaintext)
        } catch (recoveryFailure: Throwable) {
            recoveryFailure.addSuppressed(original)
            throw recoveryFailure
        }
    }

    private fun encryptAndCommit(key: SecretKey, plaintext: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val payload = cipher.doFinal(plaintext)
        try {
            check(
                preferences.edit()
                    .putString(KEY_IV, base64.encode(cipher.iv))
                    .putString(KEY_PAYLOAD, base64.encode(payload))
                    .commit(),
            ) { "Unable to persist credential envelope" }
        } finally {
            payload.fill(0)
        }
    }

    internal companion object {
        const val PREFERENCES = "hulk_secure_session"
        const val KEY_IV = "iv"
        const val KEY_PAYLOAD = "payload"
        const val KEY_ALIAS = "hulk_sa_credentials_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        private val OPERATION_LOCK = Any()
    }
}

/**
 * Narrow key-operation seam so the recovery policy can be tested with deterministic synthetic
 * failures without running data-clearing instrumentation against an owner package.
 */
internal interface CredentialKeyStore {
    fun exists(alias: String): Boolean

    fun get(alias: String): SecretKey?

    fun create(alias: String): SecretKey

    fun delete(alias: String)
}

internal interface CredentialBase64Codec {
    fun encode(bytes: ByteArray): String

    fun decode(value: String): ByteArray
}

private class AndroidCredentialBase64Codec : CredentialBase64Codec {
    override fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    override fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)
}

private class AndroidCredentialKeyStore : CredentialKeyStore {
    override fun exists(alias: String): Boolean = keyStore().containsAlias(alias)

    override fun get(alias: String): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    override fun create(alias: String): SecretKey =
        KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            CredentialVault.ANDROID_KEYSTORE,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }

    override fun delete(alias: String) {
        keyStore().deleteEntry(alias)
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(CredentialVault.ANDROID_KEYSTORE).apply { load(null) }
}

/**
 * Only a confirmed invalidated/corrupt existing key may trigger replacement. Generic
 * "Keystore operation failed", provider setup, preference-write, runtime failures and an
 * UnrecoverableKeyException whose cause chain has no invalid-blob evidence stay non-recoverable so
 * a transient fault never destroys a usable key.
 */
internal fun isRecoverableCredentialKeyFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    repeat(8) {
        val failure = current ?: return false
        if (failure.javaClass.name == ANDROID_KEYSTORE_EXCEPTION_CLASS &&
            isInvalidKeyBlobMessage(failure.message)
        ) {
            return true
        }
        when (failure) {
            is KeyPermanentlyInvalidatedException -> return true
            is UnrecoverableKeyException -> {
                // UnrecoverableKeyException can wrap a generic/system/transient failure. Its own
                // invalid-blob message is evidence, but the exception type alone is not.
                if (isInvalidKeyBlobMessage(failure.message)) return true
            }
            is KeyStoreException -> if (isInvalidKeyBlobMessage(failure.message)) return true
            is InvalidKeyException -> if (isInvalidKeyBlobMessage(failure.message)) return true
        }
        current = failure.cause ?: return false
    }
    return false
}

// android.security.KeyStoreException is present at runtime on the affected Android 12 device
// but is only public SDK API since 33. Name-based matching preserves minSdk 23 compatibility.
private const val ANDROID_KEYSTORE_EXCEPTION_CLASS = "android.security.KeyStoreException"

private fun isInvalidKeyBlobMessage(message: String?): Boolean {
    val normalized = message?.lowercase(Locale.US) ?: return false
    return normalized.contains("invalid key blob") ||
        normalized.contains("invalid keyblob") ||
        normalized.contains("key blob corrupted") ||
        normalized.contains("key blob is invalid") ||
        normalized.contains("failed to decrypt key blob")
}

/**
 * Raised when the encrypted credential envelope could not be durably removed. Callers must fail
 * the enclosing logout / no-remember transition instead of reporting it as completed, because the
 * old envelope can still be restored after a process restart.
 */
internal class CredentialEnvelopeRemovalException :
    IllegalStateException("Unable to durably remove the remembered credential envelope")

internal object CredentialPayloadCodec {
    private const val VERSION = "2"

    fun encode(credentials: Credentials): ByteArray = listOf(
        VERSION,
        credentials.accessCode,
        credentials.username,
        credentials.password,
    ).joinToString("\u0000").toByteArray(Charsets.UTF_8)

    fun decode(payload: ByteArray): Credentials? {
        val parts = payload.toString(Charsets.UTF_8).split('\u0000', limit = 4)
        if (parts.size != 4 || parts[0] != VERSION) return null
        val accessCode = parts[1]
        val username = parts[2]
        val password = parts[3]
        if (accessCode.isBlank() || username.isBlank() || password.isEmpty()) return null
        return Credentials(
            accessCode = accessCode,
            username = username,
            password = password,
        )
    }
}
