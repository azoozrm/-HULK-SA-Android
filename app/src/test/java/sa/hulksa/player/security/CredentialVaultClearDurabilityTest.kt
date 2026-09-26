package sa.hulksa.player.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import sa.hulksa.player.data.FakeSharedPreferences

/**
 * Deterministic durability proof for [CredentialVault.clear] using the existing
 * [FakeSharedPreferences] model: the in-process map reflects a cleared editor even when the
 * durable commit fails, and only a true commit result updates durable storage.
 */
class CredentialVaultClearDurabilityTest {
    @Test
    fun failedDurableClearIsReportedAndLeavesTheEnvelopeRestartRestorable() {
        val preferences = seededEnvelope()
        val vault = CredentialVault(preferences)
        preferences.commitResult = false

        assertThrows(CredentialEnvelopeRemovalException::class.java) {
            vault.clear()
        }

        // Process-visible state: the platform applies the cleared editor to the in-memory map.
        assertNull(preferences.getString(ENVELOPE_IV, null))
        assertNull(preferences.getString(ENVELOPE_PAYLOAD, null))
        // Durable state: the failed commit left the old envelope on disk.
        assertEquals(IV_VALUE, preferences.durableValue(ENVELOPE_IV))
        assertEquals(PAYLOAD_VALUE, preferences.durableValue(ENVELOPE_PAYLOAD))
        // Restart reads only durable state, so the old envelope is visible again.
        val restarted = preferences.restart()
        assertEquals(IV_VALUE, restarted.getString(ENVELOPE_IV, null))
        assertEquals(PAYLOAD_VALUE, restarted.getString(ENVELOPE_PAYLOAD, null))
    }

    @Test
    fun successfulDurableClearLeavesNoEnvelopeAfterRestart() {
        val preferences = seededEnvelope()
        val vault = CredentialVault(preferences)

        vault.clear()

        assertNull(preferences.durableValue(ENVELOPE_IV))
        assertNull(preferences.durableValue(ENVELOPE_PAYLOAD))
        val restarted = preferences.restart()
        assertNull(restarted.getString(ENVELOPE_IV, null))
        assertNull(restarted.getString(ENVELOPE_PAYLOAD, null))
    }

    @Test
    fun clearOfAnEmptyEnvelopeDurablySucceeds() {
        val preferences = FakeSharedPreferences()
        val vault = CredentialVault(preferences)

        vault.clear()

        assertNull(preferences.restart().durableValue(ENVELOPE_IV))
    }

    private fun seededEnvelope(): FakeSharedPreferences =
        FakeSharedPreferences().apply {
            edit()
                .putString(ENVELOPE_IV, IV_VALUE)
                .putString(ENVELOPE_PAYLOAD, PAYLOAD_VALUE)
                .commit()
        }

    private companion object {
        val ENVELOPE_IV = CredentialVault.KEY_IV
        val ENVELOPE_PAYLOAD = CredentialVault.KEY_PAYLOAD
        const val IV_VALUE = "synthetic-iv"
        const val PAYLOAD_VALUE = "synthetic-credential-envelope"
    }
}
