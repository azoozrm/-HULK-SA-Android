package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.AuthenticationAttemptGate
import sa.hulksa.player.ManualParentAuthProofTracker
import sa.hulksa.player.model.ProfileKind

class ParentPinBootstrapPolicyTest {

    @Test
    fun `adult to kids without parent pin requires setup`() {
        assertEquals(
            ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP,
            decision(ProfileKind.STANDARD, ProfileKind.KIDS, parentPin = false, resolved = true),
        )
    }

    @Test
    fun `adult to kids with parent pin is allowed`() {
        assertEquals(
            ParentPinBootstrapDecision.ALLOW,
            decision(ProfileKind.STANDARD, ProfileKind.KIDS, parentPin = true, resolved = true),
        )
    }

    @Test
    fun `unresolved legacy kids session without manual proof is denied`() {
        assertEquals(
            ParentPinBootstrapDecision.DENY_FAIL_CLOSED,
            decision(
                current = ProfileKind.KIDS,
                target = ProfileKind.STANDARD,
                parentPin = false,
                resolved = false,
                manualProof = false,
            ),
        )
    }

    @Test
    fun `resolvedForSession does not authorize legacy bootstrap`() {
        val unresolved = decision(
            ProfileKind.KIDS,
            ProfileKind.STANDARD,
            parentPin = false,
            resolved = false,
            manualProof = false,
        )
        val resolved = decision(
            ProfileKind.KIDS,
            ProfileKind.STANDARD,
            parentPin = false,
            resolved = true,
            manualProof = false,
        )

        assertEquals(ParentPinBootstrapDecision.DENY_FAIL_CLOSED, unresolved)
        assertEquals(unresolved, resolved)
    }

    @Test
    fun `auto restored authentication creates no manual proof and stays denied`() {
        val tracker = ManualParentAuthProofTracker()
        val gate = AuthenticationAttemptGate(tracker)

        val attempt = checkNotNull(gate.tryStart()) // restoreSession enters authenticate directly.
        tracker.completeAuthenticationSuccess("account-a", "session-1")
        assertTrue(gate.complete(attempt))

        assertFalse(tracker.hasValidProof())
        assertEquals(
            ParentPinBootstrapDecision.DENY_FAIL_CLOSED,
            decision(
                ProfileKind.KIDS,
                ProfileKind.STANDARD,
                parentPin = false,
                resolved = false,
                manualProof = tracker.hasValidProof(),
            ),
        )
    }

    @Test
    fun `manual login proof for same account and session permits legacy setup`() {
        val tracker = successfulManualLoginProof("account-a", "session-1")

        assertTrue(tracker.hasValidProofFor("account-a", "session-1"))
        assertEquals(
            ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP,
            decision(
                ProfileKind.KIDS,
                ProfileKind.STANDARD,
                parentPin = false,
                resolved = true,
                manualProof = tracker.hasValidProof(),
            ),
        )
    }

    @Test
    fun `manual proof for account A cannot authorize account B`() {
        val tracker = successfulManualLoginProof("account-a", "session-1")

        assertFalse(tracker.hasValidProofFor("account-b", "session-1"))
        tracker.onSessionReplacement("account-b", "session-1")
        assertFalse(tracker.hasValidProof())
    }

    @Test
    fun `manual proof for old session id is denied`() {
        val tracker = successfulManualLoginProof("account-a", "session-old")

        assertFalse(tracker.hasValidProofFor("account-a", "session-new"))
        tracker.onSessionReplacement("account-a", "session-new")
        assertFalse(tracker.hasValidProof())
    }

    @Test
    fun `consumed manual proof cannot be reused`() {
        val tracker = successfulManualLoginProof("account-a", "session-1")

        assertTrue(tracker.consumeValidProof())
        assertFalse(tracker.hasValidProof())
        assertFalse(tracker.consumeValidProof())
    }

    @Test
    fun `process recreation starts with no manual proof`() {
        val oldProcess = successfulManualLoginProof("account-a", "session-1")
        assertTrue(oldProcess.hasValidProof())

        val recreatedProcess = ManualParentAuthProofTracker()
        assertFalse(recreatedProcess.hasValidProof())
    }

    @Test
    fun `failed manual login creates no proof`() {
        val tracker = ManualParentAuthProofTracker()
        val gate = AuthenticationAttemptGate(tracker)

        assertFalse(gate.isActive()) // explicit Login submit preflight
        val attempt = checkNotNull(gate.tryStart())
        tracker.completeAuthenticationFailure()
        assertTrue(gate.complete(attempt))

        assertFalse(tracker.hasValidProof())
    }

    @Test
    fun `restoring session path never creates proof`() {
        val tracker = ManualParentAuthProofTracker()
        val gate = AuthenticationAttemptGate(tracker)

        val attempt = checkNotNull(gate.tryStart())
        tracker.completeAuthenticationSuccess("account-a", "session-restored")
        gate.complete(attempt)

        assertFalse(tracker.hasValidProof())
    }

    @Test
    fun `manual authentication failure after non restoring submit has no proof`() {
        val tracker = ManualParentAuthProofTracker()
        val gate = AuthenticationAttemptGate(tracker)

        assertFalse(gate.isActive())
        val attempt = checkNotNull(gate.tryStart())
        tracker.completeAuthenticationFailure()
        gate.complete(attempt)

        assertFalse(tracker.hasValidProofFor("account-a", "session-1"))
    }

    @Test
    fun `successful manual login creates proof only after authentication success`() {
        val tracker = ManualParentAuthProofTracker()
        val gate = AuthenticationAttemptGate(tracker)

        assertFalse(gate.isActive())
        val attempt = checkNotNull(gate.tryStart())
        assertFalse(tracker.hasValidProof())

        tracker.completeAuthenticationSuccess("account-a", "session-1")
        assertTrue(tracker.hasValidProofFor("account-a", "session-1"))
        assertTrue(gate.complete(attempt))
        assertTrue(tracker.hasValidProof())
    }

    @Test
    fun `logout invalidates manual proof`() {
        val tracker = successfulManualLoginProof("account-a", "session-1")
        val gate = AuthenticationAttemptGate(tracker)
        assertTrue(tracker.hasValidProof())

        gate.invalidate()

        assertFalse(tracker.hasValidProof())
    }

    @Test
    fun `session replacement invalidates manual proof`() {
        val tracker = successfulManualLoginProof("account-a", "session-1")

        tracker.onSessionReplacement("account-a", "session-2")

        assertFalse(tracker.hasValidProof())
    }

    @Test
    fun `successful parent pin bootstrap consumes manual proof`() {
        val tracker = successfulManualLoginProof("account-a", "session-1")

        assertEquals(
            ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP,
            decision(
                ProfileKind.KIDS,
                ProfileKind.STANDARD,
                parentPin = false,
                resolved = false,
                manualProof = tracker.hasValidProof(),
            ),
        )
        assertTrue(tracker.consumeValidProof())
        assertFalse(tracker.hasValidProof())
    }

    @Test
    fun `kids to adult with parent pin still requires existing parent pin verification`() {
        assertEquals(
            ParentPinBootstrapDecision.ALLOW,
            decision(ProfileKind.KIDS, ProfileKind.STANDARD, parentPin = true, resolved = true),
        )
        assertEquals(
            ProfileSwitchAuthorization.REQUIRE_PRIMARY_PARENT_PIN,
            profileSwitchAuthorization(
                currentProfileId = "kids",
                currentProfileKind = ProfileKind.KIDS,
                targetProfileId = "adult",
                targetProfileKind = ProfileKind.STANDARD,
                targetProtected = false,
                resolvedForSession = true,
                primaryParentPinAvailable = true,
            ),
        )
    }

    @Test
    fun `wrong parent pin cannot bypass kids to adult authorization requirement`() {
        val authorization = profileSwitchAuthorization(
            currentProfileId = "kids",
            currentProfileKind = ProfileKind.KIDS,
            targetProfileId = "adult",
            targetProfileKind = ProfileKind.STANDARD,
            targetProtected = false,
            resolvedForSession = true,
            primaryParentPinAvailable = true,
        )

        assertEquals(ProfileSwitchAuthorization.REQUIRE_PRIMARY_PARENT_PIN, authorization)
    }

    @Test
    fun `adult to adult remains unchanged without parent pin`() {
        assertEquals(
            ParentPinBootstrapDecision.ALLOW,
            decision(ProfileKind.STANDARD, ProfileKind.STANDARD, parentPin = false, resolved = true),
        )
    }

    @Test
    fun `kids to kids remains normal once parent pin exists`() {
        assertEquals(
            ParentPinBootstrapDecision.ALLOW,
            decision(ProfileKind.KIDS, ProfileKind.KIDS, parentPin = true, resolved = true),
        )
    }

    @Test
    fun `primary parent pin cannot be cleared while kids profiles exist`() {
        assertFalse(canClearPrimaryParentPin(hasKidsProfiles = true))
        assertTrue(canClearPrimaryParentPin(hasKidsProfiles = false))
    }

    @Test
    fun `duplicate manual login submit remains single flight`() {
        val tracker = ManualParentAuthProofTracker()
        val gate = AuthenticationAttemptGate(tracker)

        assertFalse(gate.isActive())
        val first = gate.tryStart()
        val duplicate = gate.tryStart()

        assertNotNull(first)
        assertNull(duplicate)
    }

    private fun successfulManualLoginProof(
        accountId: String,
        sessionId: String,
    ): ManualParentAuthProofTracker {
        val tracker = ManualParentAuthProofTracker()
        val gate = AuthenticationAttemptGate(tracker)
        assertFalse(gate.isActive()) // the explicit Login submit path
        val attempt = checkNotNull(gate.tryStart())
        assertFalse(tracker.hasValidProof())
        tracker.completeAuthenticationSuccess(accountId, sessionId)
        assertTrue(gate.complete(attempt))
        return tracker
    }

    private fun decision(
        current: ProfileKind?,
        target: ProfileKind,
        parentPin: Boolean,
        resolved: Boolean,
        manualProof: Boolean = false,
    ) = parentPinBootstrapDecision(
        currentProfileKind = current,
        targetProfileKind = target,
        primaryParentPinAvailable = parentPin,
        resolvedForSession = resolved,
        manualAuthProofValid = manualProof,
    )
}
