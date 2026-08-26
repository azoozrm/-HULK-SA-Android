package sa.hulksa.player.ui

import sa.hulksa.player.ManualParentAuthProofRegistry
import sa.hulksa.player.model.ProfileKind

internal enum class ParentPinBootstrapDecision {
    ALLOW,
    REQUIRE_PARENT_PIN_SETUP,
    DENY_FAIL_CLOSED,
}

/**
 * Parent PIN ownership remains the Primary Adult profile.
 *
 * resolvedForSession is deliberately not an authorization signal. A legacy active-Kids session
 * without a Parent PIN may bootstrap only when the current account/session owns an ephemeral proof
 * produced by a successful explicit Login submit. Auto-restored sessions therefore stay fail-closed.
 */
@Suppress("UNUSED_PARAMETER")
internal fun parentPinBootstrapDecision(
    currentProfileKind: ProfileKind?,
    targetProfileKind: ProfileKind,
    primaryParentPinAvailable: Boolean,
    resolvedForSession: Boolean,
    manualAuthProofValid: Boolean = ManualParentAuthProofRegistry.hasValidProof(),
): ParentPinBootstrapDecision {
    if (primaryParentPinAvailable) return ParentPinBootstrapDecision.ALLOW

    if (currentProfileKind == ProfileKind.KIDS) {
        return if (manualAuthProofValid) {
            ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP
        } else {
            ParentPinBootstrapDecision.DENY_FAIL_CLOSED
        }
    }

    return if (targetProfileKind == ProfileKind.KIDS) {
        ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP
    } else {
        ParentPinBootstrapDecision.ALLOW
    }
}

internal fun canClearPrimaryParentPin(hasKidsProfiles: Boolean): Boolean = !hasKidsProfiles
