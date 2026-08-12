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
import sa.hulksa.player.data.ProfilePinCredentialStore
import sa.hulksa.player.data.ProfilePreferencesStore
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.screens.ProfileManagementScreen
import sa.hulksa.player.ui.screens.ProfilePickerScreen
import sa.hulksa.player.ui.screens.ProfilePinProtectionScreen
import sa.hulksa.player.ui.screens.ProfilePinUnlockScreen

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
    val profilePinCredentialStore = remember(context) { ProfilePinCredentialStore(context) }
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
    var pinUnlockTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var pinSecurityProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var profileRevision by rememberSaveable { mutableStateOf(0) }
    var pinRevision by rememberSaveable { mutableStateOf(0) }

    val authenticated = state.account != null && state.screen != HulkScreen.LOGIN
    val profiles = remember(profileRevision, authenticated) { profileStore.profiles() }
    val activeProfileId = remember(profileRevision, switching, authenticated) {
        profileStore.activeProfileId()
    }
    val protectedProfileIds = remember(profiles, pinRevision) {
        profiles
            .filter { profilePinCredentialStore.hasPin(it.id) }
            .mapTo(linkedSetOf(), UserProfile::id)
    }
    val activeNavigationMemory = remember(activeProfileId) {
        navigationMemoryByProfile.getOrPut(activeProfileId) { NavigationMemoryStore() }
    }
    val activeCatalogNavigationMemory = remember(activeProfileId) {
        catalogNavigationMemoryByProfile.getOrPut(activeProfileId) {
            ProfileCatalogNavigationMemory()
        }
    }
    val routingPreferences = profilePreferencesStore.routing()
    val directEntryTarget = if (
        authenticated &&
        !resolvedForSession &&
        !switching &&
        pinUnlockTargetId == null &&
        pinSecurityProfileId == null &&
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

    fun switchProfileUnlocked(profile: UserProfile) {
        if (switching) return

        val currentProfileId = profileStore.activeProfileId()
        if (profile.id == currentProfileId) {
            viewModel.refreshProfileLibrary()
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

    fun requestProfileSwitch(profile: UserProfile) {
        if (switching || pinUnlockTargetId != null) return
        val currentProfileId = profileStore.activeProfileId()
        val needsPin =
            profile.id in protectedProfileIds &&
                (profile.id != currentProfileId || !resolvedForSession)

        if (needsPin) {
            switchError = null
            managingProfiles = false
            createProfileRequested = false
            pinUnlockTargetId = profile.id
            return
        }

        switchProfileUnlocked(profile)
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
        pinUnlockTargetId,
    ) {
        val target = directEntryTarget ?: return@LaunchedEffect
        requestProfileSwitch(target)
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
            pinUnlockTargetId = null
            pinSecurityProfileId = null
            navigationMemoryByProfile.clear()
            destinationMemoryByProfile.clear()
            catalogNavigationMemoryByProfile.clear()
        }
    }

    BackHandler(enabled = managingProfiles && pinSecurityProfileId == null) {
        managingProfiles = false
        createProfileRequested = false
    }

    BackHandler(
        enabled = pickerRequestedFromApp &&
            showPicker &&
            !managingProfiles &&
            !switching &&
            pinUnlockTargetId == null,
    ) {
        pickerRequestedFromApp = false
        switchError = null
    }

    val unlockProfile = pinUnlockTargetId
        ?.let { targetId -> profiles.firstOrNull { it.id == targetId } }
    val securityProfile = pinSecurityProfileId
        ?.let { profileId -> profiles.firstOrNull { it.id == profileId } }

    when {
        unlockProfile != null -> ProfilePinUnlockScreen(
            profile = unlockProfile,
            isTv = isTelevisionDevice,
            onVerify = { pin -> profilePinCredentialStore.verifyPin(unlockProfile.id, pin) },
            onUnlocked = {
                pinUnlockTargetId = null
                switchProfileUnlocked(unlockProfile)
            },
            onCancel = {
                pinUnlockTargetId = null
                switchError = null
            },
        )

        managingProfiles && securityProfile != null -> ProfilePinProtectionScreen(
            profile = securityProfile,
            isTv = isTelevisionDevice,
            isProtected = securityProfile.id in protectedProfileIds,
            onVerify = { pin -> profilePinCredentialStore.verifyPin(securityProfile.id, pin) },
            onSetPin = { pin ->
                val stored = profilePinCredentialStore.setPin(securityProfile.id, pin)
                if (!stored) {
                    false
                } else {
                    val metadata = profilePreferencesStore.setPinFoundation(
                        profileId = securityProfile.id,
                        enabled = true,
                        credentialVersion = ProfilePinCredentialStore.CURRENT_CREDENTIAL_VERSION,
                    )
                    if (metadata == null) {
                        profilePinCredentialStore.clearPin(securityProfile.id)
                        false
                    } else {
                        pinRevision++
                        true
                    }
                }
            },
            onClearPin = {
                val cleared = profilePinCredentialStore.clearPin(securityProfile.id)
                val metadata = profilePreferencesStore.setPinFoundation(
                    profileId = securityProfile.id,
                    enabled = false,
                    credentialVersion = 0,
                )
                val success = cleared && metadata != null
                if (success) pinRevision++
                success
            },
            onClose = {
                pinSecurityProfileId = null
            },
        )

        managingProfiles -> ProfileManagementScreen(
            profiles = profiles,
            activeProfileId = activeProfileId,
            isTv = isTelevisionDevice,
            startCreating = createProfileRequested,
            protectedProfileIds = protectedProfileIds,
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
                    profilePinCredentialStore.clearPin(profileId)
                    profilePreferencesStore.removeProfilePreferences(profileId)
                    navigationMemoryByProfile.remove(profileId)
                    destinationMemoryByProfile.remove(profileId)
                    catalogNavigationMemoryByProfile.remove(profileId)
                    if (pinSecurityProfileId == profileId) pinSecurityProfileId = null
                    profileRevision++
                    pinRevision++
                }
                deleted
            },
            onSelect = ::requestProfileSwitch,
            onManagePin = { profile ->
                createProfileRequested = false
                pinSecurityProfileId = profile.id
            },
            onClose = {
                managingProfiles = false
                createProfileRequested = false
                pinSecurityProfileId = null
                if (profiles.size <= 1) resolvedForSession = true
            },
        )

        showPicker -> ProfilePickerScreen(
            profiles = profiles,
            activeProfileId = activeProfileId,
            isTv = isTelevisionDevice,
            isSwitching = switching,
            errorMessage = switchError,
            onSelectProfile = ::requestProfileSwitch,
            onCreateProfile = {
                if (!switching && profiles.size < ProfileStore.MAX_PROFILES) {
                    createProfileRequested = true
                    managingProfiles = true
                }
            },
            onManageProfiles = {
                createProfileRequested = false
                pinSecurityProfileId = null
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
