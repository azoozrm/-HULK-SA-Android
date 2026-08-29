package sa.hulksa.player.ui

import sa.hulksa.player.model.ProfileKind

internal enum class ProfileSwitchAuthorization {
    ALLOW,
    REQUIRE_TARGET_PIN,
    REQUIRE_PARENTAL_CODE,
    DENY_NO_PARENT_CREDENTIAL,
}

internal fun profileSwitchAuthorization(
    currentProfileId: String,
    currentProfileKind: ProfileKind?,
    targetProfileId: String,
    targetProfileKind: ProfileKind,
    targetProtected: Boolean,
    resolvedForSession: Boolean,
    parentalCodeAvailable: Boolean,
    parentalAuthorizationGranted: Boolean = false,
): ProfileSwitchAuthorization {
    val switchingProfiles = currentProfileId != targetProfileId
    if (
        switchingProfiles &&
        currentProfileKind == ProfileKind.KIDS &&
        targetProfileKind == ProfileKind.STANDARD &&
        !parentalAuthorizationGranted
    ) {
        return if (parentalCodeAvailable) {
            ProfileSwitchAuthorization.REQUIRE_PARENTAL_CODE
        } else {
            ProfileSwitchAuthorization.DENY_NO_PARENT_CREDENTIAL
        }
    }

    if (targetProtected && (switchingProfiles || !resolvedForSession)) {
        return ProfileSwitchAuthorization.REQUIRE_TARGET_PIN
    }

    return ProfileSwitchAuthorization.ALLOW
}

internal fun shouldRetainKidsProfileForDirectEntry(
    currentProfileId: String,
    currentProfileKind: ProfileKind?,
    targetProfileId: String,
    targetProfileKind: ProfileKind,
): Boolean =
    currentProfileId != targetProfileId &&
        currentProfileKind == ProfileKind.KIDS &&
        targetProfileKind == ProfileKind.STANDARD

internal fun requiresParentAuthorizationForProfileManagement(
    currentProfileKind: ProfileKind?,
): Boolean = currentProfileKind == ProfileKind.KIDS

internal fun shouldShowProfilePicker(
    profileCount: Int,
    authenticated: Boolean,
    resolvedForSession: Boolean,
): Boolean = profileCount > 1 && authenticated && !resolvedForSession
