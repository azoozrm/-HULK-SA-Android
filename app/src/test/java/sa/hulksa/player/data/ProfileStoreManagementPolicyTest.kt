package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStoreManagementPolicyTest {
    @Test
    fun profileNameIsTrimmedCollapsedAndLimited() {
        assertEquals("عبد العزيز", normalizeProfileName("   عبد    العزيز   "))

        val long = "123456789012345678901234567890"
        assertEquals(ProfileStore.MAX_DISPLAY_NAME_LENGTH, normalizeProfileName(long)?.length)
    }

    @Test
    fun blankProfileNameIsRejected() {
        assertEquals(null, normalizeProfileName("   \n  "))
    }

    @Test
    fun deletePolicyProtectsPrimaryAndLastProfile() {
        assertFalse(canDeleteProfile(isPrimary = true, profileCount = 3))
        assertFalse(canDeleteProfile(isPrimary = false, profileCount = 1))
        assertTrue(canDeleteProfile(isPrimary = false, profileCount = 2))
    }
}
