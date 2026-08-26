package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ProfileKind

class ParentPinBootstrapPolicyTest {

    @Test
    fun `adult to kids with parent pin is allowed`() {
        assertEquals(
            ParentPinBootstrapDecision.ALLOW,
            decision(ProfileKind.STANDARD, ProfileKind.KIDS, parentPin = true, resolved = true),
        )
    }

    @Test
    fun `adult to kids without parent pin requires setup`() {
        assertEquals(
            ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP,
            decision(ProfileKind.STANDARD, ProfileKind.KIDS, parentPin = false, resolved = true),
        )
    }

    @Test
    fun `creating kids from adult context requires setup when missing`() {
        assertEquals(
            ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP,
            decision(ProfileKind.STANDARD, ProfileKind.KIDS, parentPin = false, resolved = true),
        )
    }

    @Test
    fun `cancel policy does not authorize kids implicitly`() {
        assertEquals(
            ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP,
            decision(ProfileKind.STANDARD, ProfileKind.KIDS, parentPin = false, resolved = true),
        )
    }

    @Test
    fun `resolved kids session without parent pin remains fail closed`() {
        assertEquals(
            ParentPinBootstrapDecision.DENY_FAIL_CLOSED,
            decision(ProfileKind.KIDS, ProfileKind.STANDARD, parentPin = false, resolved = true),
        )
        assertEquals(
            ParentPinBootstrapDecision.DENY_FAIL_CLOSED,
            decision(ProfileKind.KIDS, ProfileKind.KIDS, parentPin = false, resolved = true),
        )
    }

    @Test
    fun `fresh authenticated legacy kids session may bootstrap before profile resolution`() {
        assertEquals(
            ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP,
            decision(ProfileKind.KIDS, ProfileKind.STANDARD, parentPin = false, resolved = false),
        )
        assertEquals(
            ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP,
            decision(ProfileKind.KIDS, ProfileKind.KIDS, parentPin = false, resolved = false),
        )
    }

    @Test
    fun `adult to adult is unchanged without parent pin`() {
        assertEquals(
            ParentPinBootstrapDecision.ALLOW,
            decision(ProfileKind.STANDARD, ProfileKind.STANDARD, parentPin = false, resolved = true),
        )
    }

    @Test
    fun `kids transitions remain normal once parent pin exists`() {
        assertEquals(
            ParentPinBootstrapDecision.ALLOW,
            decision(ProfileKind.KIDS, ProfileKind.STANDARD, parentPin = true, resolved = true),
        )
        assertEquals(
            ParentPinBootstrapDecision.ALLOW,
            decision(ProfileKind.KIDS, ProfileKind.KIDS, parentPin = true, resolved = true),
        )
    }

    @Test
    fun `parent pin cannot be cleared while kids profiles exist`() {
        assertFalse(canClearPrimaryParentPin(hasKidsProfiles = true))
        assertTrue(canClearPrimaryParentPin(hasKidsProfiles = false))
    }

    private fun decision(
        current: ProfileKind?,
        target: ProfileKind,
        parentPin: Boolean,
        resolved: Boolean,
    ) = parentPinBootstrapDecision(
        currentProfileKind = current,
        targetProfileKind = target,
        primaryParentPinAvailable = parentPin,
        resolvedForSession = resolved,
    )
}
