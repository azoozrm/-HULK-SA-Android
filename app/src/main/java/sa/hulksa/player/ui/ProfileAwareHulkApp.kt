package sa.hulksa.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.screens.ProfileManagementScreen
import sa.hulksa.player.ui.screens.ProfilePickerScreen

@Composable
fun ProfileAwareHulkApp(
    viewModel: HulkViewModel,
    isTelevisionDevice: Boolean,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val profileStore = remember(context) { ProfileStore(context) }

    var resolvedForSession by rememberSaveable { mutableStateOf(false) }
    var switching by rememberSaveable { mutableStateOf(false) }
    var switchError by rememberSaveable { mutableStateOf<String?>(null) }
    var managingProfiles by rememberSaveable { mutableStateOf(false) }
    var profileRevision by rememberSaveable { mutableStateOf(0) }

    val profiles = remember(profileRevision) { profileStore.profiles() }
    val activeProfileId = remember(profileRevision, switching) { profileStore.activeProfileId() }
    val authenticated = state.account != null && state.screen != HulkScreen.LOGIN
    val singleProfileNeedsResolution =
        profiles.size == 1 && authenticated && !resolvedForSession
    val showPicker = switching || singleProfileNeedsResolution || shouldShowProfilePicker(
        profileCount = profiles.size,
        authenticated = authenticated,
        resolvedForSession = resolvedForSession,
    )

    fun switchProfile(profile: UserProfile) {
        if (switching) return

        val currentProfileId = profileStore.activeProfileId()
        if (profile.id == currentProfileId) {
            switchError = null
            resolvedForSession = true
            managingProfiles = false
            return
        }

        switching = true
        managingProfiles = false
        switchError = null

        if (!profileStore.setActiveProfile(profile.id)) {
            switching = false
            switchError = "تعذر اختيار الملف الشخصي. حاول مرة اخرى."
            return
        }

        // Profiles are local viewing contexts only. Do not logout/login here:
        // re-authenticating the IPTV account creates a second server-side session
        // and can cancel the already-active single-connection account.
        profileRevision++
        switching = false
        resolvedForSession = true
    }

    LaunchedEffect(
        state.screen,
        state.isLoading,
        state.account,
    ) {
        if (
            state.account == null &&
            state.screen == HulkScreen.LOGIN &&
            !state.isLoading
        ) {
            resolvedForSession = false
            managingProfiles = false
            switching = false
            switchError = null
        }
    }

    BackHandler(enabled = managingProfiles) {
        managingProfiles = false
    }

    when {
        managingProfiles -> ProfileManagementScreen(
            profiles = profiles,
            activeProfileId = activeProfileId,
            isTv = isTelevisionDevice,
            onCreate = { name, avatarKey ->
                val created = profileStore.createProfile(name, avatarKey = avatarKey)
                val createdSuccessfully = created != null
                if (createdSuccessfully) profileRevision++
                createdSuccessfully
            },
            onUpdate = { profileId, name, avatarKey ->
                val updated = profileStore.updateProfile(profileId, name, avatarKey)
                val updatedSuccessfully = updated != null
                if (updatedSuccessfully) profileRevision++
                updatedSuccessfully
            },
            onDelete = { profileId ->
                val deleted = profileStore.deleteProfile(profileId)
                if (deleted) profileRevision++
                deleted
            },
            onSelect = ::switchProfile,
            onClose = {
                managingProfiles = false
                if (profiles.size <= 1) resolvedForSession = true
            },
        )

        showPicker -> ProfilePickerScreen(
            profiles = profiles,
            activeProfileId = activeProfileId,
            isTv = isTelevisionDevice,
            isSwitching = switching,
            errorMessage = switchError,
            onSelectProfile = ::switchProfile,
            onManageProfiles = { managingProfiles = true },
        )

        else -> HulkApp(
            viewModel = viewModel,
            isTelevisionDevice = isTelevisionDevice,
        )
    }
}
