package sa.hulksa.player.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import sa.hulksa.player.model.Credentials
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the account in an Android Keystore-backed AES/GCM envelope. The APK
 * never contains a test username or password and Android backup is disabled.
 */
class CredentialVault(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(credentials: Credentials) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val plaintext = CredentialPayloadCodec.encode(credentials)
        try {
            val encrypted = cipher.doFinal(plaintext)
            check(
                preferences.edit()
                    .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .putString(KEY_PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .commit(),
            ) { "Unable to persist credential vault" }
        } finally {
            plaintext.fill(0)
        }
    }

    fun load(): Credentials? {
        val credentials = runCatching {
            val iv = preferences.getString(KEY_IV, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
                ?: return@runCatching null
            val payload = preferences.getString(KEY_PAYLOAD, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
                ?: return@runCatching null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(payload)
            try {
                CredentialPayloadCodec.decode(plaintext)
            } finally {
                plaintext.fill(0)
            }
        }.getOrNull()
        if (credentials == null) clear()
        return credentials
    }

    fun clear() {
        check(preferences.edit().clear().commit()) { "Unable to clear credential vault" }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES = "hulk_secure_session"
        const val KEY_IV = "iv"
        const val KEY_PAYLOAD = "payload"
        const val KEY_ALIAS = "hulk_sa_credentials_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

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
