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

class ParentalCodeBootstrapPolicyTest {

    @Test
    fun `adult to kids without parental code requires setup`() {
        assertEquals(
            ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP,
            decision(ProfileKind.STANDARD, ProfileKind.KIDS, parentalCode = false, resolved = true),
        )
    }

    @Test
    fun `adult to kids with parental code is allowed`() {
        assertEquals(
            ParentalCodeBootstrapDecision.ALLOW,
            decision(ProfileKind.STANDARD, ProfileKind.KIDS, parentalCode = true, resolved = true),
        )
    }

    @Test
    fun `unresolved legacy kids session without manual proof is denied`() {
        assertEquals(
            ParentalCodeBootstrapDecision.DENY_FAIL_CLOSED,
            decision(
                current = ProfileKind.KIDS,
                target = ProfileKind.STANDARD,
                parentalCode = false,
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
            parentalCode = false,
            resolved = false,
            manualProof = false,
        )
        val resolved = decision(
            ProfileKind.KIDS,
            ProfileKind.STANDARD,
            parentalCode = false,
            resolved = true,
            manualProof = false,
        )

        assertEquals(ParentalCodeBootstrapDecision.DENY_FAIL_CLOSED, unresolved)
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
            ParentalCodeBootstrapDecision.DENY_FAIL_CLOSED,
            decision(
                ProfileKind.KIDS,
                ProfileKind.STANDARD,
                parentalCode = false,
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
            ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP,
            decision(
                ProfileKind.KIDS,
                ProfileKind.STANDARD,
                parentalCode = false,
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
    fun `successful parental code bootstrap consumes manual proof`() {
        val tracker = successfulManualLoginProof("account-a", "session-1")

        assertEquals(
            ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP,
            decision(
                ProfileKind.KIDS,
                ProfileKind.STANDARD,
                parentalCode = false,
                resolved = false,
                manualProof = tracker.hasValidProof(),
            ),
        )
        assertTrue(tracker.consumeValidProof())
        assertFalse(tracker.hasValidProof())
    }

    @Test
    fun `kids to adult with parental code still requires verification`() {
        assertEquals(
            ParentalCodeBootstrapDecision.ALLOW,
            decision(ProfileKind.KIDS, ProfileKind.STANDARD, parentalCode = true, resolved = true),
        )
        assertEquals(
            ProfileSwitchAuthorization.REQUIRE_PARENTAL_CODE,
            profileSwitchAuthorization(
                currentProfileId = "kids",
                currentProfileKind = ProfileKind.KIDS,
                targetProfileId = "adult",
                targetProfileKind = ProfileKind.STANDARD,
                targetProtected = false,
                resolvedForSession = true,
                parentalCodeAvailable = true,
            ),
        )
    }

    @Test
    fun `wrong parental code cannot bypass kids to adult authorization requirement`() {
        val authorization = profileSwitchAuthorization(
            currentProfileId = "kids",
            currentProfileKind = ProfileKind.KIDS,
            targetProfileId = "adult",
            targetProfileKind = ProfileKind.STANDARD,
            targetProtected = false,
            resolvedForSession = true,
            parentalCodeAvailable = true,
        )

        assertEquals(ProfileSwitchAuthorization.REQUIRE_PARENTAL_CODE, authorization)
    }

    @Test
    fun `adult to adult remains unchanged without parental code`() {
        assertEquals(
            ParentalCodeBootstrapDecision.ALLOW,
            decision(ProfileKind.STANDARD, ProfileKind.STANDARD, parentalCode = false, resolved = true),
        )
    }

    @Test
    fun `kids to kids remains normal once parental code exists`() {
        assertEquals(
            ParentalCodeBootstrapDecision.ALLOW,
            decision(ProfileKind.KIDS, ProfileKind.KIDS, parentalCode = true, resolved = true),
        )
    }

    @Test
    fun `kids to kids does not require parental code`() {
        assertEquals(
            ParentalCodeBootstrapDecision.ALLOW,
            decision(ProfileKind.KIDS, ProfileKind.KIDS, parentalCode = false, resolved = true),
        )
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
        parentalCode: Boolean,
        resolved: Boolean,
        manualProof: Boolean = false,
    ) = parentalCodeBootstrapDecision(
        currentProfileKind = current,
        targetProfileKind = target,
        parentalCodeAvailable = parentalCode,
        resolvedForSession = resolved,
        manualAuthProofValid = manualProof,
    )
}
