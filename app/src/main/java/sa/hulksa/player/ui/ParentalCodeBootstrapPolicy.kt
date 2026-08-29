package sa.hulksa.player.ui

import sa.hulksa.player.ManualParentAuthProofRegistry
import sa.hulksa.player.model.ProfileKind

internal enum class ParentalCodeBootstrapDecision {
    ALLOW,
    REQUIRE_PARENTAL_CODE_SETUP,
    DENY_FAIL_CLOSED,
}

/**
 * Parental-code availability is account-scoped and independent of every profile PIN.
 *
 * resolvedForSession is deliberately not an authorization signal. A legacy active-Kids session
 * without a parental code may bootstrap only when the current account/session owns an ephemeral
 * proof produced by a successful explicit Login submit. Auto-restored sessions therefore stay
 * fail-closed.
 */
@Suppress("UNUSED_PARAMETER")
internal fun parentalCodeBootstrapDecision(
    currentProfileKind: ProfileKind?,
    targetProfileKind: ProfileKind,
    parentalCodeAvailable: Boolean,
    resolvedForSession: Boolean,
    manualAuthProofValid: Boolean = ManualParentAuthProofRegistry.hasValidProof(),
): ParentalCodeBootstrapDecision {
    if (parentalCodeAvailable) return ParentalCodeBootstrapDecision.ALLOW

    if (
        currentProfileKind == ProfileKind.KIDS &&
        targetProfileKind == ProfileKind.STANDARD
    ) {
        return if (manualAuthProofValid) {
            ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP
        } else {
            ParentalCodeBootstrapDecision.DENY_FAIL_CLOSED
        }
    }

    return if (
        currentProfileKind != ProfileKind.KIDS &&
        targetProfileKind == ProfileKind.KIDS
    ) {
        ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP
    } else {
        ParentalCodeBootstrapDecision.ALLOW
    }
}
