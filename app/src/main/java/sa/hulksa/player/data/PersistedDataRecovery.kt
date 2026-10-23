package sa.hulksa.player.data

import android.content.SharedPreferences
import sa.hulksa.player.security.containsCredentialBearingIptvMaterial
import java.security.MessageDigest

private const val RECOVERY_KEY_PREFIX = "__integrity_recovery__:"
private const val RECOVERY_NEXT_SLOT_SUFFIX = ":__next_slot__"
private const val RECOVERY_SLOT_COUNT = 2
private const val MAX_CONFIRMED_RECOVERY_SLOTS = 128
private const val UNSAFE_LEGACY_MIGRATION_PREFIX = "__integrity_unsafe_legacy__:"

private val confirmedRecoverySlots = HashMap<String, String>()
private val recoveryLock = Any()

/**
 * Preserves one damaged persisted document in the same account-scoped, app-private
 * SharedPreferences before any repair replaces it.
 *
 * Confirmation is bound to the actual slot that was durably written, not just to a payload
 * token. Overwriting a slot replaces its confirmation, so an evicted payload can never be
 * accepted as already preserved. A failed commit is rolled back in process memory because the
 * platform mutates memory even when the durable write fails.
 */
internal fun preserveDamagedPersistedDocument(
    preferences: SharedPreferences,
    sourceKey: String,
    raw: String,
): Boolean {
    if (raw.isEmpty()) return true
    // Never create a new plaintext recovery copy for a payload that still contains
    // credential-bearing material anywhere in its structure.
    if (containsCredentialBearingIptvMaterial(raw)) return false
    val payloadHash = sha256(raw)
    val preferencesId = System.identityHashCode(preferences)
    val confirmationPrefix = "$preferencesId:$sourceKey:"
    synchronized(recoveryLock) {
        if (confirmedRecoverySlots.any { (key, hash) ->
                key.startsWith(confirmationPrefix) && hash == payloadHash
            }
        ) {
            return true
        }

        val slotPrefix = "$RECOVERY_KEY_PREFIX$sourceKey:"
        val nextSlotKey = "$slotPrefix$RECOVERY_NEXT_SLOT_SUFFIX"
        val nextSlot = runCatching { preferences.getInt(nextSlotKey, 0) }
            .getOrDefault(0)
            .coerceIn(0, RECOVERY_SLOT_COUNT - 1)
        val slotKey = "$slotPrefix$nextSlot"
        val previousSlot = readPreference(preferences, slotKey)
        val previousNextSlot = readPreference(preferences, nextSlotKey)

        val persisted = runCatching {
            preferences.edit()
                .putString(slotKey, raw)
                .putInt(nextSlotKey, (nextSlot + 1) % RECOVERY_SLOT_COUNT)
                .commit()
        }.getOrDefault(false)
        if (!persisted) {
            restorePreference(preferences, slotKey, previousSlot)
            restorePreference(preferences, nextSlotKey, previousNextSlot)
            return false
        }

        confirmedRecoverySlots["$confirmationPrefix$slotKey"] = payloadHash
        while (confirmedRecoverySlots.size > MAX_CONFIRMED_RECOVERY_SLOTS) {
            confirmedRecoverySlots.remove(confirmedRecoverySlots.keys.first())
        }
        return true
    }
}

internal fun persistedRecoveryKeyPrefix(sourceKey: String): String =
    "$RECOVERY_KEY_PREFIX$sourceKey:"

internal fun unsafeLegacyMigrationMarkerKey(baseName: String): String =
    "$UNSAFE_LEGACY_MIGRATION_PREFIX$baseName"

internal fun hasUnsafeLegacyMigrationMarker(
    preferences: SharedPreferences,
    baseName: String,
): Boolean = runCatching {
    preferences.contains(unsafeLegacyMigrationMarkerKey(baseName))
}.getOrDefault(true)

private data class StoredPreference(
    val present: Boolean,
    val value: Any?,
)

private fun readPreference(preferences: SharedPreferences, key: String): StoredPreference =
    runCatching {
        if (preferences.contains(key)) {
            StoredPreference(present = true, value = preferences.all[key])
        } else {
            StoredPreference(present = false, value = null)
        }
    }.getOrDefault(StoredPreference(present = false, value = null))

private fun restorePreference(
    preferences: SharedPreferences,
    key: String,
    previous: StoredPreference,
) {
    runCatching {
        val editor = preferences.edit()
        when {
            !previous.present -> editor.remove(key)
            previous.value is String -> editor.putString(key, previous.value)
            previous.value is Int -> editor.putInt(key, previous.value)
            previous.value is Long -> editor.putLong(key, previous.value)
            previous.value is Boolean -> editor.putBoolean(key, previous.value)
            previous.value is Float -> editor.putFloat(key, previous.value)
            previous.value is Set<*> -> editor.putStringSet(
                key,
                previous.value.filterIsInstance<String>().toSet(),
            )
            else -> editor.remove(key)
        }
        // A failed restore commit still restores in-process memory, which is the goal here.
        editor.commit()
    }
}

private fun sha256(raw: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
