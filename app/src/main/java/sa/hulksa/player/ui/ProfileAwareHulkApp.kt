package sa.hulksa.player.ui

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
import sa.hulksa.player.data.HulkRepository
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.ui.screens.ProfilePickerScreen

@Composable
fun ProfileAwareHulkApp(
    viewModel: HulkViewModel,
    isTelevisionDevice: Boolean,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val profileStore = remember(context) { ProfileStore(context) }
    val sessionRepository = remember(context) { HulkRepository(context) }

    var resolvedForSession by rememberSaveable { mutableStateOf(false) }
    var switching by rememberSaveable { mutableStateOf(false) }
    var switchError by rememberSaveable { mutableStateOf<String?>(null) }

    val profiles = profileStore.profiles()
    val activeProfileId = profileStore.activeProfileId()
    val authenticated = state.account != null && state.screen != HulkScreen.LOGIN
    val showPicker = switching || shouldShowProfilePicker(
        profileCount = profiles.size,
        authenticated = authenticated,
        resolvedForSession = resolvedForSession,
    )

    LaunchedEffect(
        switching,
        state.screen,
        state.isLoading,
        state.account,
        state.errorMessage,
    ) {
        if (switching && state.screen == HulkScreen.MAIN && !state.isLoading && state.account != null) {
            switching = false
            resolvedForSession = true
            switchError = null
        } else if (
            switching &&
            state.screen == HulkScreen.LOGIN &&
            !state.isLoading &&
            !state.errorMessage.isNullOrBlank()
        ) {
            switching = false
            switchError = state.errorMessage
        }

        if (
            !switching &&
            state.account == null &&
            state.screen == HulkScreen.LOGIN &&
            !state.isLoading
        ) {
            resolvedForSession = false
        }
    }

    if (showPicker) {
        ProfilePickerScreen(
            profiles = profiles,
            activeProfileId = activeProfileId,
            isTv = isTelevisionDevice,
            isSwitching = switching,
            errorMessage = switchError,
            onSelectProfile = { profile ->
                if (switching) return@ProfilePickerScreen

                val currentProfileId = profileStore.activeProfileId()
                if (profile.id == currentProfileId) {
                    switchError = null
                    resolvedForSession = true
                    return@ProfilePickerScreen
                }

                val credentials = sessionRepository.savedCredentials()
                if (credentials == null) {
                    switchError = "تعذر تبديل الملف الشخصي بدون جلسة محفوظة. سجل الدخول من جديد ثم حاول مرة اخرى."
                    return@ProfilePickerScreen
                }

                if (!profileStore.setActiveProfile(profile.id)) {
                    switchError = "تعذر اختيار الملف الشخصي. حاول مرة اخرى."
                    return@ProfilePickerScreen
                }

                // Logout reloads the ViewModel's profile-scoped library snapshot from the
                // newly active profile. The captured credentials are then used immediately
                // to restore the authenticated account/session and account-scoped catalogs.
                viewModel.logout()
                viewModel.login(
                    username = credentials.username,
                    password = credentials.password,
                    remember = true,
                )
                switching = true
                switchError = null
            },
        )
    } else {
        HulkApp(
            viewModel = viewModel,
            isTelevisionDevice = isTelevisionDevice,
        )
    }
}
