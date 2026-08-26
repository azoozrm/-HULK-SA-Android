package sa.hulksa.player.ui

import sa.hulksa.player.model.ProfileKind

internal enum class ParentPinBootstrapDecision {
    ALLOW,
    REQUIRE_PARENT_PIN_SETUP,
    DENY_FAIL_CLOSED,
}

/**
 * Parent PIN ownership remains the Primary Adult profile.
 *
 * A resolved Kids session without that credential must stay fail-closed: a child already inside
 * Kids cannot bootstrap the credential. An unresolved session is immediately after account
 * authentication, so it is the recovery proof used for legacy installs that were already left on
 * a Kids profile before a Parent PIN existed.
 */
internal fun parentPinBootstrapDecision(
    currentProfileKind: ProfileKind?,
    targetProfileKind: ProfileKind,
    primaryParentPinAvailable: Boolean,
    resolvedForSession: Boolean,
): ParentPinBootstrapDecision {
    if (primaryParentPinAvailable) return ParentPinBootstrapDecision.ALLOW

    if (currentProfileKind == ProfileKind.KIDS) {
        return if (resolvedForSession) {
            ParentPinBootstrapDecision.DENY_FAIL_CLOSED
        } else {
            ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP
        }
    }

    return if (targetProfileKind == ProfileKind.KIDS) {
        ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP
    } else {
        ParentPinBootstrapDecision.ALLOW
    }
}

internal fun canClearPrimaryParentPin(hasKidsProfiles: Boolean): Boolean = !hasKidsProfiles
