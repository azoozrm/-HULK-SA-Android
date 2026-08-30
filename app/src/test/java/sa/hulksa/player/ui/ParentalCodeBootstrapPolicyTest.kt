package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import sa.hulksa.player.model.ProfileKind

class ParentalCodeBootstrapPolicyTest {

    @Test
    fun `adult to kids without parental code requires explicit setup`() {
        assertEquals(
            ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP,
            decision(ProfileKind.STANDARD, ProfileKind.KIDS, parentalCode = false),
        )
    }

    @Test
    fun `adult profile pin never changes parental code availability decision`() {
        assertEquals(
            ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP,
            decision(ProfileKind.STANDARD, ProfileKind.KIDS, parentalCode = false),
        )
    }

    @Test
    fun `adult to kids with explicit parental code is allowed`() {
        assertEquals(
            ParentalCodeBootstrapDecision.ALLOW,
            decision(ProfileKind.STANDARD, ProfileKind.KIDS, parentalCode = true),
        )
    }

    @Test
    fun `legacy kids to adult without parental code enters setup flow`() {
        assertEquals(
            ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP,
            decision(ProfileKind.KIDS, ProfileKind.STANDARD, parentalCode = false),
        )
    }

    @Test
    fun `legacy kids with adult pin requires one time profile pin proof`() {
        assertEquals(
            LegacyParentProofDecision.REQUIRE_PRIMARY_ADULT_PROFILE_PIN,
            legacyParentProofDecision(
                currentProfileKind = ProfileKind.KIDS,
                legacyPrimaryAdultProfilePinAvailable = true,
            ),
        )
    }

    @Test
    fun `legacy kids without usable adult pin fails closed`() {
        assertEquals(
            LegacyParentProofDecision.DENY_FAIL_CLOSED,
            legacyParentProofDecision(
                currentProfileKind = ProfileKind.KIDS,
                legacyPrimaryAdultProfilePinAvailable = false,
            ),
        )
    }

    @Test
    fun `adult context never requires legacy proof before creating parental code`() {
        assertEquals(
            LegacyParentProofDecision.NOT_REQUIRED,
            legacyParentProofDecision(
                currentProfileKind = ProfileKind.STANDARD,
                legacyPrimaryAdultProfilePinAvailable = true,
            ),
        )
    }

    @Test
    fun `kids to adult with parental code still requires parental verification first`() {
        assertEquals(
            ParentalCodeBootstrapDecision.ALLOW,
            decision(ProfileKind.KIDS, ProfileKind.STANDARD, parentalCode = true),
        )
        assertEquals(
            ProfileSwitchAuthorization.REQUIRE_PARENTAL_CODE,
            profileSwitchAuthorization(
                currentProfileId = "kids",
                currentProfileKind = ProfileKind.KIDS,
                targetProfileId = "adult",
                targetProfileKind = ProfileKind.STANDARD,
                targetProtected = true,
                resolvedForSession = true,
                parentalCodeAvailable = true,
            ),
        )
    }

    @Test
    fun `protected adult requires profile pin only after parental authorization`() {
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
    fun `wrong parental code never falls through to target profile pin`() {
        assertEquals(
            ProfileSwitchAuthorization.REQUIRE_PARENTAL_CODE,
            profileSwitchAuthorization(
                currentProfileId = "kids",
                currentProfileKind = ProfileKind.KIDS,
                targetProfileId = "adult",
                targetProfileKind = ProfileKind.STANDARD,
                targetProtected = true,
                resolvedForSession = true,
                parentalCodeAvailable = true,
                parentalAuthorizationGranted = false,
            ),
        )
    }

    @Test
    fun `adult to adult remains unchanged without parental code`() {
        assertEquals(
            ParentalCodeBootstrapDecision.ALLOW,
            decision(ProfileKind.STANDARD, ProfileKind.STANDARD, parentalCode = false),
        )
    }

    @Test
    fun `kids to kids does not create or require parental code`() {
        assertEquals(
            ParentalCodeBootstrapDecision.ALLOW,
            decision(ProfileKind.KIDS, ProfileKind.KIDS, parentalCode = false),
        )
    }

    private fun decision(
        current: ProfileKind?,
        target: ProfileKind,
        parentalCode: Boolean,
    ) = parentalCodeBootstrapDecision(
        currentProfileKind = current,
        targetProfileKind = target,
        parentalCodeAvailable = parentalCode,
        resolvedForSession = true,
    )
}
