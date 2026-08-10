package sa.hulksa.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePickerPolicyTest {
    @Test
    fun singleProfileIsSkipped() {
        assertFalse(shouldShowProfilePicker(1, authenticated = true, resolvedForSession = false))
    }

    @Test
    fun multipleProfilesShowAfterAuthentication() {
        assertTrue(shouldShowProfilePicker(2, authenticated = true, resolvedForSession = false))
    }

    @Test
    fun resolvedSessionDoesNotShowAgain() {
        assertFalse(shouldShowProfilePicker(2, authenticated = true, resolvedForSession = true))
    }

    @Test
    fun unauthenticatedSessionDoesNotShowPicker() {
        assertFalse(shouldShowProfilePicker(2, authenticated = false, resolvedForSession = false))
    }
}
