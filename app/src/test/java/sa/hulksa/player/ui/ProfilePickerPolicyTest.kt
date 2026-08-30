package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ProfileKind

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

    @Test
    fun adultToAdultUnprotectedRemainsAllowed() {
        assertEquals(
            ProfileSwitchAuthorization.ALLOW,
            authorization(
                currentKind = ProfileKind.STANDARD,
                targetKind = ProfileKind.STANDARD,
            ),
        )
    }

    @Test
    fun adultToAdultProtectedKeepsTargetPinBehavior() {
        assertEquals(
            ProfileSwitchAuthorization.REQUIRE_TARGET_PIN,
            authorization(
                currentKind = ProfileKind.STANDARD,
                targetKind = ProfileKind.STANDARD,
                targetProtected = true,
            ),
        )
    }

    @Test
    fun adultToKidsRemainsAllowedWhenTargetIsUnprotected() {
        assertEquals(
            ProfileSwitchAuthorization.ALLOW,
            authorization(
                currentKind = ProfileKind.STANDARD,
                targetKind = ProfileKind.KIDS,
            ),
        )
    }

    @Test
    fun kidsToProtectedAdultRequiresParentalCodeBeforeTargetPin() {
        assertEquals(
            ProfileSwitchAuthorization.REQUIRE_PARENTAL_CODE,
            authorization(
                currentKind = ProfileKind.KIDS,
                targetKind = ProfileKind.STANDARD,
                targetProtected = true,
                parentalCodeAvailable = true,
            ),
        )
    }

    @Test
    fun kidsToProtectedAdultRequiresTargetPinAfterParentalAuthorization() {
        assertEquals(
            ProfileSwitchAuthorization.REQUIRE_TARGET_PIN,
            profileSwitchAuthorization(
                currentProfileId = "kids",
                currentProfileKind = ProfileKind.KIDS,
                targetProfileId = "adult",
                targetProfileKind = ProfileKind.STANDARD,
                targetProtected = true,
                resolvedForSession = true,
                parentalCodeAvailable = true,
                parentalAuthorizationGranted = true,
            ),
        )
    }

    @Test
    fun kidsToUnprotectedAdultUsesParentalCode() {
        assertEquals(
            ProfileSwitchAuthorization.REQUIRE_PARENTAL_CODE,
            authorization(
                currentKind = ProfileKind.KIDS,
                targetKind = ProfileKind.STANDARD,
                parentalCodeAvailable = true,
            ),
        )
    }

    @Test
    fun kidsToUnprotectedAdultFailsClosedWithoutParentalCode() {
        assertEquals(
            ProfileSwitchAuthorization.DENY_NO_PARENT_CREDENTIAL,
            authorization(
                currentKind = ProfileKind.KIDS,
                targetKind = ProfileKind.STANDARD,
                parentalCodeAvailable = false,
            ),
        )
    }

    @Test
    fun kidsToKidsRemainsAllowedWhenTargetIsUnprotected() {
        assertEquals(
            ProfileSwitchAuthorization.ALLOW,
            authorization(
                currentKind = ProfileKind.KIDS,
                targetKind = ProfileKind.KIDS,
            ),
        )
    }

    @Test
    fun protectedKidsTargetKeepsExistingPinRequirement() {
        assertEquals(
            ProfileSwitchAuthorization.REQUIRE_TARGET_PIN,
            authorization(
                currentKind = ProfileKind.KIDS,
                targetKind = ProfileKind.KIDS,
                targetProtected = true,
            ),
        )
    }

    @Test
    fun unresolvedProtectedCurrentProfileKeepsExistingPinRequirement() {
        assertEquals(
            ProfileSwitchAuthorization.REQUIRE_TARGET_PIN,
            profileSwitchAuthorization(
                currentProfileId = "same",
                currentProfileKind = ProfileKind.KIDS,
                targetProfileId = "same",
                targetProfileKind = ProfileKind.KIDS,
                targetProtected = true,
                resolvedForSession = false,
                parentalCodeAvailable = false,
            ),
        )
    }

    @Test
    fun resolvedProtectedCurrentProfileDoesNotRequirePinAgain() {
        assertEquals(
            ProfileSwitchAuthorization.ALLOW,
            profileSwitchAuthorization(
                currentProfileId = "same",
                currentProfileKind = ProfileKind.KIDS,
                targetProfileId = "same",
                targetProfileKind = ProfileKind.KIDS,
                targetProtected = true,
                resolvedForSession = true,
                parentalCodeAvailable = false,
            ),
        )
    }

    @Test
    fun automaticDirectEntryCannotExitKidsToAdult() {
        assertTrue(
            shouldRetainKidsProfileForDirectEntry(
                currentProfileId = "kids",
                currentProfileKind = ProfileKind.KIDS,
                targetProfileId = "adult",
                targetProfileKind = ProfileKind.STANDARD,
            ),
        )
    }

    @Test
    fun automaticDirectEntryKeepsExistingAdultAndKidsBehavior() {
        assertFalse(
            shouldRetainKidsProfileForDirectEntry(
                currentProfileId = "adult-a",
                currentProfileKind = ProfileKind.STANDARD,
                targetProfileId = "adult-b",
                targetProfileKind = ProfileKind.STANDARD,
            ),
        )
        assertFalse(
            shouldRetainKidsProfileForDirectEntry(
                currentProfileId = "kids-a",
                currentProfileKind = ProfileKind.KIDS,
                targetProfileId = "kids-b",
                targetProfileKind = ProfileKind.KIDS,
            ),
        )
    }

    @Test
    fun kidsProfileManagementRequiresParentAuthorization() {
        assertTrue(requiresParentAuthorizationForProfileManagement(ProfileKind.KIDS))
        assertFalse(requiresParentAuthorizationForProfileManagement(ProfileKind.STANDARD))
    }

    private fun authorization(
        currentKind: ProfileKind,
        targetKind: ProfileKind,
        targetProtected: Boolean = false,
        parentalCodeAvailable: Boolean = false,
    ): ProfileSwitchAuthorization = profileSwitchAuthorization(
        currentProfileId = "current",
        currentProfileKind = currentKind,
        targetProfileId = "target",
        targetProfileKind = targetKind,
        targetProtected = targetProtected,
        resolvedForSession = true,
        parentalCodeAvailable = parentalCodeAvailable,
    )
}
