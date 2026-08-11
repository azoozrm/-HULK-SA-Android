package sa.hulksa.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.MainDestination
import sa.hulksa.player.data.ProfilePreferencesStore
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.screens.ProfileManagementScreen
import sa.hulksa.player.ui.screens.ProfilePickerScreen

internal val LocalProfileSwitchRequester = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun ProfileAwareHulkApp(
    viewModel: HulkViewModel,
    isTelevisionDevice: Boolean,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val profileStore = remember(context) { ProfileStore(context) }
    val profilePreferencesStore = remember(context) { ProfilePreferencesStore(context) }
    val navigationMemoryByProfile = remember { mutableMapOf<String, NavigationMemoryStore>() }
    val destinationMemoryByProfile = remember { mutableMapOf<String, MainDestination>() }
    val catalogNavigationMemoryByProfile = remember {
        mutableMapOf<String, ProfileCatalogNavigationMemory>()
    }

    var resolvedForSession by rememberSaveable { mutableStateOf(false) }
    var switching by rememberSaveable { mutableStateOf(false) }
    var switchError by rememberSaveable { mutableStateOf<String?>(null) }
    var managingProfiles by rememberSaveable { mutableStateOf(false) }
    var createProfileRequested by rememberSaveable { mutableStateOf(false) }
    var pickerRequestedFromApp by rememberSaveable { mutableStateOf(false) }
    var profileRevision by rememberSaveable { mutableStateOf(0) }

    val profiles = remember(profileRevision) { profileStore.profiles() }
    val activeProfileId = remember(profileRevision, switching) { profileStore.activeProfileId() }
    val activeNavigationMemory = remember(activeProfileId) {
        navigationMemoryByProfile.getOrPut(activeProfileId) { NavigationMemoryStore() }
    }
    val activeCatalogNavigationMemory = remember(activeProfileId) {
        catalogNavigationMemoryByProfile.getOrPut(activeProfileId) {
            ProfileCatalogNavigationMemory()
        }
    }
    val routingPreferences = profilePreferencesStore.routing()
    val authenticated = state.account != null && state.screen != HulkScreen.LOGIN
    val directEntryTarget = if (
        authenticated &&
        !resolvedForSession &&
        !switching &&
        !pickerRequestedFromApp &&
        !managingProfiles &&
        routingPreferences.directEntryEnabled
    ) {
        routingPreferences.defaultProfileId
            ?.let { defaultId -> profiles.firstOrNull { it.id == defaultId } }
            ?: profiles.firstOrNull { it.id == activeProfileId }
    } else {
        null
    }
    val singleProfileNeedsResolution =
        profiles.size == 1 && authenticated && !resolvedForSession && directEntryTarget == null
    val showPicker = pickerRequestedFromApp || switching || (
        directEntryTarget == null && (
            singleProfileNeedsResolution || shouldShowProfilePicker(
                profileCount = profiles.size,
                authenticated = authenticated,
                resolvedForSession = resolvedForSession,
            )
        )
    )

    fun switchProfile(profile: UserProfile) {
        if (switching) return

        val currentProfileId = profileStore.activeProfileId()
        if (profile.id == currentProfileId) {
            switchError = null
            resolvedForSession = true
            managingProfiles = false
            createProfileRequested = false
            pickerRequestedFromApp = false
            return
        }

        switching = true
        managingProfiles = false
        createProfileRequested = false
        switchError = null

        // Capture both the top-level destination and the active catalog context
        // before changing profiles. Catalog context is transient and stays local
        // to each stable profile ID for this authenticated app session.
        destinationMemoryByProfile[currentProfileId] = state.destination
        catalogNavigationMemoryByProfile
            .getOrPut(currentProfileId) { ProfileCatalogNavigationMemory() }
            .save(
                destination = state.destination,
                categoryId = state.selectedCategoryId,
                query = state.searchQuery,
            )

        val targetDestination = destinationMemoryByProfile[profile.id] ?: MainDestination.HOME
        val targetCatalogMemory = catalogNavigationMemoryByProfile
            .getOrPut(profile.id) { ProfileCatalogNavigationMemory() }

        if (!profileStore.setActiveProfile(profile.id)) {
            switching = false
            switchError = "تعذر اختيار الملف الشخصي. حاول مرة اخرى."
            return
        }

        // Profiles are local viewing contexts only. Keep the authenticated IPTV
        // session intact, restore the target profile's own destination and catalog
        // category/query, then refresh profile-scoped library data.
        viewModel.selectDestination(targetDestination)
        if (targetDestination.isProfileCatalogDestination()) {
            viewModel.updateSearch(targetCatalogMemory.query(targetDestination))
            viewModel.selectCategory(targetCatalogMemory.category(targetDestination))
        }
        viewModel.refreshProfileLibrary()
        profileRevision++
        switching = false
        resolvedForSession = true
        pickerRequestedFromApp = false
    }

    LaunchedEffect(
        authenticated,
        activeProfileId,
        state.screen,
        state.destination,
        state.selectedCategoryId,
        state.searchQuery,
        switching,
    ) {
        if (authenticated && state.screen == HulkScreen.MAIN && !switching) {
            destinationMemoryByProfile[activeProfileId] = state.destination
            catalogNavigationMemoryByProfile
                .getOrPut(activeProfileId) { ProfileCatalogNavigationMemory() }
                .save(
                    destination = state.destination,
                    categoryId = state.selectedCategoryId,
                    query = state.searchQuery,
                )
        }
    }

    LaunchedEffect(
        authenticated,
        resolvedForSession,
        routingPreferences.directEntryEnabled,
        routingPreferences.defaultProfileId,
        activeProfileId,
        profiles,
        pickerRequestedFromApp,
        managingProfiles,
    ) {
        val target = directEntryTarget ?: return@LaunchedEffect
        switchProfile(target)
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
            createProfileRequested = false
            switching = false
            switchError = null
            pickerRequestedFromApp = false
            navigationMemoryByProfile.clear()
            destinationMemoryByProfile.clear()
            catalogNavigationMemoryByProfile.clear()
        }
    }

    BackHandler(enabled = managingProfiles) {
        managingProfiles = false
        createProfileRequested = false
    }

    BackHandler(
        enabled = pickerRequestedFromApp && showPicker && !managingProfiles && !switching,
    ) {
        pickerRequestedFromApp = false
        switchError = null
    }

    when {
        managingProfiles -> ProfileManagementScreen(
            profiles = profiles,
            activeProfileId = activeProfileId,
            isTv = isTelevisionDevice,
            startCreating = createProfileRequested,
            onCreate = { name, avatarKey ->
                val created = profileStore.createProfile(name, avatarKey = avatarKey)
                val createdSuccessfully = created != null
                if (createdSuccessfully) {
                    createProfileRequested = false
                    profileRevision++
                }
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
                if (deleted) {
                    navigationMemoryByProfile.remove(profileId)
                    destinationMemoryByProfile.remove(profileId)
                    catalogNavigationMemoryByProfile.remove(profileId)
                    profileRevision++
                }
                deleted
            },
            onSelect = ::switchProfile,
            onClose = {
                managingProfiles = false
                createProfileRequested = false
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
            onCreateProfile = {
                if (!switching && profiles.size < ProfileStore.MAX_PROFILES) {
                    createProfileRequested = true
                    managingProfiles = true
                }
            },
            onManageProfiles = {
                createProfileRequested = false
                managingProfiles = true
            },
        )

        else -> CompositionLocalProvider(
            LocalProfileSwitchRequester provides {
                if (!switching) {
                    switchError = null
                    managingProfiles = false
                    createProfileRequested = false
                    pickerRequestedFromApp = true
                }
            },
        ) {
            HulkApp(
                viewModel = viewModel,
                isTelevisionDevice = isTelevisionDevice,
                navigationMemory = activeNavigationMemory,
                catalogNavigationMemory = activeCatalogNavigationMemory,
            )
        }
    }
}
