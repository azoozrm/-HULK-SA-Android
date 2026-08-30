package sa.hulksa.player.ui

import sa.hulksa.player.model.ProfileKind

internal enum class ParentalCodeBootstrapDecision {
    ALLOW,
    REQUIRE_PARENTAL_CODE_SETUP,
    DENY_FAIL_CLOSED,
}

internal enum class LegacyParentProofDecision {
    NOT_REQUIRED,
    REQUIRE_PRIMARY_ADULT_PROFILE_PIN,
    DENY_FAIL_CLOSED,
}

/**
 * Parental-code availability is account-scoped and independent of every profile PIN.
 *
 * resolvedForSession is deliberately not an authorization signal. When a legacy Kids session has
 * no dedicated parental code, the bootstrap UI is responsible for requiring one-time proof of the
 * Primary Adult Profile PIN before it allows explicit parental-code creation.
 */
@Suppress("UNUSED_PARAMETER")
internal fun parentalCodeBootstrapDecision(
    currentProfileKind: ProfileKind?,
    targetProfileKind: ProfileKind,
    parentalCodeAvailable: Boolean,
    resolvedForSession: Boolean,
): ParentalCodeBootstrapDecision {
    if (parentalCodeAvailable) return ParentalCodeBootstrapDecision.ALLOW

    if (
        currentProfileKind == ProfileKind.KIDS &&
        targetProfileKind == ProfileKind.STANDARD
    ) {
        return ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP
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

internal fun legacyParentProofDecision(
    currentProfileKind: ProfileKind?,
    legacyPrimaryAdultProfilePinAvailable: Boolean,
): LegacyParentProofDecision = when {
    currentProfileKind != ProfileKind.KIDS -> LegacyParentProofDecision.NOT_REQUIRED
    legacyPrimaryAdultProfilePinAvailable ->
        LegacyParentProofDecision.REQUIRE_PRIMARY_ADULT_PROFILE_PIN
    else -> LegacyParentProofDecision.DENY_FAIL_CLOSED
}
