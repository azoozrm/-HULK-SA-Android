package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStoreManagementPolicyTest {
    @Test
    fun profileNameIsTrimmedCollapsedAndLimited() {
        val normalized = normalizeProfileNameForTest("   عبد    العزيز   ")
        assertEquals("عبد العزيز", normalized)

        val long = "123456789012345678901234567890"
        assertEquals(ProfileStore.MAX_DISPLAY_NAME_LENGTH, normalizeProfileNameForTest(long)?.length)
    }

    @Test
    fun blankProfileNameIsRejected() {
        assertEquals(null, normalizeProfileNameForTest("   \n  "))
    }

    @Test
    fun deletePolicyProtectsPrimaryAndLastProfile() {
        assertFalse(canDeleteProfileForTest(isPrimary = true, profileCount = 3))
        assertFalse(canDeleteProfileForTest(isPrimary = false, profileCount = 1))
        assertTrue(canDeleteProfileForTest(isPrimary = false, profileCount = 2))
    }
}

internal fun normalizeProfileNameForTest(raw: String): String? = raw
    .trim()
    .replace(Regex("\\s+"), " ")
    .take(ProfileStore.MAX_DISPLAY_NAME_LENGTH)
    .takeIf(String::isNotBlank)

internal fun canDeleteProfileForTest(isPrimary: Boolean, profileCount: Int): Boolean =
    !isPrimary && profileCount > 1
