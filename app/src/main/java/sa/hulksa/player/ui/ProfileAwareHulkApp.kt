package sa.hulksa.player.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.MainDestination
import sa.hulksa.player.data.HulkRepository
import sa.hulksa.player.data.OperationsServiceStatus
import sa.hulksa.player.data.OperationsUpdateDecision
import sa.hulksa.player.data.ProfilePinCredentialStore
import sa.hulksa.player.data.ProfilePreferencesStore
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.data.VerifiedKidsCatalogSnapshot
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.screens.AdaptiveProfileManagementScreen
import sa.hulksa.player.ui.screens.LocalNotificationCenterScreen
import sa.hulksa.player.ui.screens.MaintenanceScreen
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.screens.NewEpisodeAlertOverlay
import sa.hulksa.player.ui.screens.OperationsAnnouncementOverlay
import sa.hulksa.player.ui.screens.OperationsStatusBanner
import sa.hulksa.player.ui.screens.OptionalUpdateOverlay
import sa.hulksa.player.ui.screens.ProfilePickerScreen
import sa.hulksa.player.ui.screens.ProfilePinProtectionScreen
import sa.hulksa.player.ui.screens.ProfilePinUnlockScreen
import sa.hulksa.player.ui.screens.RequiredUpdateScreen

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
    val hulkRepository = remember(context) { HulkRepository(context) }
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
    var profileRevision by rememberSaveable { mutableIntStateOf(0) }
    var pinRevision by rememberSaveable { mutableIntStateOf(0) }
    var kidsSourceRequest by rememberSaveable { mutableIntStateOf(0) }
    var kidsSnapshot by remember { mutableStateOf<VerifiedKidsCatalogSnapshot?>(null) }
    var kidsSourceLoading by remember { mutableStateOf(false) }
    var kidsSourceError by remember { mutableStateOf<String?>(null) }
    var kidsSnapshotAccount by remember { mutableStateOf<String?>(null) }

    val authenticated = state.account != null && state.screen != HulkScreen.LOGIN
    val profiles = remember(profileRevision, authenticated) { profileStore.profiles() }
    val activeProfileId = remember(profileRevision, switching, authenticated) {
        profileStore.activeProfileId()
    }
    val activeProfile = remember(profiles, activeProfileId) {
        profiles.firstOrNull { it.id == activeProfileId }
            ?: profiles.firstOrNull()
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
    val needsKidsSource = authenticated && (
        activeProfile?.kind == ProfileKind.KIDS ||
            managingProfiles ||
            createProfileRequested ||
            profiles.any { it.kind == ProfileKind.KIDS }
        )
    val kidsSourcePending =
        needsKidsSource && kidsSnapshot == null && kidsSourceError == null

    LaunchedEffect(
        authenticated,
        state.account?.username,
        needsKidsSource,
        kidsSourceRequest,
    ) {
        val accountKey = state.account?.username
        if (!authenticated || accountKey == null) {
            kidsSnapshot = null
            kidsSourceLoading = false
            kidsSourceError = null
            kidsSnapshotAccount = null
            return@LaunchedEffect
        }
        if (kidsSnapshotAccount != accountKey) {
            kidsSnapshot = null
            kidsSourceError = null
            kidsSnapshotAccount = accountKey
        }
        if (!needsKidsSource) return@LaunchedEffect

        val activeSession = hulkRepository.currentAuthenticatedSession()
        if (activeSession == null) {
            kidsSnapshot = null
            kidsSourceLoading = false
            kidsSourceError = "تعذر الوصول إلى الجلسة الموثّقة. أعد تسجيل الدخول ثم حاول مرة أخرى."
            return@LaunchedEffect
        }

        kidsSourceLoading = true
        kidsSourceError = null
        try {
            val verified = hulkRepository.verifiedKidsCatalog(activeSession)
            kidsSnapshot = verified
            kidsSourceError = when {
                verified.isAvailable -> null
                verified.blockedTypes.isNotEmpty() ->
                    "لم يعتمد التطبيق مصدر الأطفال لأن فلترة السيرفر لم تُثبت بأمان."
                else -> "لا توجد فئات أطفال صريحة متاحة لهذا الحساب."
            }
        } catch (_: Exception) {
            kidsSnapshot = null
            kidsSourceError = "تعذر التحقق من مصدر الأطفال. لم يتم عرض أي محتوى عام كبديل."
        } finally {
            kidsSourceLoading = false
        }
    }

    fun requestKidsSourceReload() {
        kidsSourceRequest++
    }

    fun openProfilePickerFromApp() {
        if (!switching) {
            switchError = null
            managingProfiles = false
            createProfileRequested = false
            pickerRequestedFromApp = true
        }
    }

    fun switchProfileUnlocked(profile: UserProfile) {
        if (switching) return

        val currentProfileId = profileStore.activeProfileId()
        if (profile.id == currentProfileId) {
            if (profile.kind == ProfileKind.KIDS) viewModel.selectDestination(MainDestination.HOME)
            viewModel.refreshProfileLibrary()
            viewModel.onProfileChanged()
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

        val targetDestination = if (profile.kind == ProfileKind.KIDS) {
            MainDestination.HOME
        } else {
            destinationMemoryByProfile[profile.id] ?: MainDestination.HOME
        }
        val targetCatalogMemory = catalogNavigationMemoryByProfile
            .getOrPut(profile.id) { ProfileCatalogNavigationMemory() }

        if (!profileStore.setActiveProfile(profile.id)) {
            switching = false
            switchError = "تعذر اختيار الملف الشخصي. حاول مرة اخرى."
            return
        }

        viewModel.selectDestination(targetDestination)
        if (profile.kind != ProfileKind.KIDS && targetDestination.isProfileCatalogDestination()) {
            viewModel.updateSearch(targetCatalogMemory.query(targetDestination))
            viewModel.selectCategory(targetCatalogMemory.category(targetDestination))
        } else if (profile.kind == ProfileKind.KIDS) {
            viewModel.updateSearch("")
            viewModel.selectCategory(null)
        }
        viewModel.refreshProfileLibrary()
        viewModel.onProfileChanged()
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
            kidsSnapshot = null
            kidsSourceLoading = false
            kidsSourceError = null
            kidsSnapshotAccount = null
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

    LaunchedEffect(
        authenticated,
        resolvedForSession,
        showPicker,
        managingProfiles,
        unlockProfile?.id,
        securityProfile?.id,
        activeProfile?.id,
        activeProfile?.kind,
        kidsSnapshot?.isAvailable,
    ) {
        viewModel.setNotificationUiReady(
            ready = authenticated &&
                resolvedForSession &&
                !showPicker &&
                !managingProfiles &&
                unlockProfile == null &&
                securityProfile == null &&
                (activeProfile?.kind != ProfileKind.KIDS || kidsSnapshot?.isAvailable == true),
        )
    }

    val appContentFocusRequester = remember { FocusRequester() }
    val notificationFocusScope = rememberCoroutineScope()
    val operationsBlocked =
        state.operations.updateDecision == OperationsUpdateDecision.REQUIRED ||
            state.operations.service.status == OperationsServiceStatus.MAINTENANCE
    val operationsAnnouncement = state.operations.announcementPopup

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(appContentFocusRequester)
                .focusRestorer()
                .focusGroup(),
        ) {
            when {
            state.operations.updateDecision == OperationsUpdateDecision.REQUIRED -> RequiredUpdateScreen(
                operations = state.operations,
                isTv = isTelevisionDevice,
                onUpdate = viewModel::startOperationsUpdate,
                onOpenUnknownSourcesSettings = {
                    viewModel.openOperationsInstallSettings { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
            )

            state.operations.service.status == OperationsServiceStatus.MAINTENANCE -> MaintenanceScreen(
                service = state.operations.service,
                isTv = isTelevisionDevice,
                onRetry = viewModel::retryOperations,
            )

            state.screen == HulkScreen.NOTIFICATION_CENTER -> LocalNotificationCenterScreen(
                notifications = state.localNotifications,
                unreadCount = state.unreadNotificationCount,
                isTv = isTelevisionDevice,
                onBack = viewModel::back,
                onOpen = { notification ->
                    viewModel.openNotification(notification.id) { message ->
                        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    }
                },
                onMarkRead = { notification -> viewModel.markNotificationRead(notification.id) },
                onReadAll = viewModel::markAllNotificationsRead,
                onDelete = { notification -> viewModel.deleteNotification(notification.id) },
                onClearAll = viewModel::clearNotifications,
            )

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

            managingProfiles -> AdaptiveProfileManagementScreen(
            profiles = profiles,
            activeProfileId = activeProfileId,
            isTv = isTelevisionDevice,
            startCreating = createProfileRequested,
            protectedProfileIds = protectedProfileIds,
            kidsSourceAvailable = kidsSnapshot?.isAvailable == true,
            kidsSourceLoading = kidsSourceLoading || kidsSourcePending,
            kidsSourceMessage = kidsSourceError,
            onRetryKidsSource = ::requestKidsSourceReload,
            onCreate = { name, avatarKey, kind ->
                val kidsAllowed = kind != ProfileKind.KIDS || kidsSnapshot?.isAvailable == true
                val created = if (kidsAllowed) {
                    profileStore.createProfile(name, avatarKey = avatarKey, kind = kind)
                } else {
                    null
                }
                val success = created != null
                if (success) {
                    createProfileRequested = false
                    profileRevision++
                }
                success
            },
            onUpdate = { profileId, name, avatarKey ->
                val updated = profileStore.updateProfile(profileId, name, avatarKey)
                val success = updated != null
                if (success) profileRevision++
                success
            },
            onDelete = { profileId ->
                val deleted = profileStore.deleteProfile(profileId)
                if (deleted) {
                    profilePinCredentialStore.clearPin(profileId)
                    profilePreferencesStore.removeProfilePreferences(profileId)
                    navigationMemoryByProfile.remove(profileId)
                    destinationMemoryByProfile.remove(profileId)
                    catalogNavigationMemoryByProfile.remove(profileId)
                    viewModel.removeNotificationProfileData(profileId)
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

            activeProfile?.kind == ProfileKind.KIDS -> KidsProfileExperience(
            viewModel = viewModel,
            isTelevisionDevice = isTelevisionDevice,
            profile = activeProfile,
            snapshot = kidsSnapshot,
            sourceLoading = kidsSourceLoading || kidsSourcePending,
            sourceError = kidsSourceError,
            onRetrySource = ::requestKidsSourceReload,
            onSwitchProfile = ::openProfilePickerFromApp,
        )

            else -> CompositionLocalProvider(
                LocalProfileSwitchRequester provides ::openProfilePickerFromApp,
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

        if (!operationsBlocked) {
            OperationsStatusBanner(
                operations = state.operations,
                isTv = isTelevisionDevice,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            when {
                state.operations.updateDecision == OperationsUpdateDecision.OPTIONAL ->
                    OptionalUpdateOverlay(
                        operations = state.operations,
                        isTv = isTelevisionDevice,
                        onUpdate = viewModel::startOperationsUpdate,
                        onLater = {
                            viewModel.dismissOptionalOperationsUpdate()
                            if (isTelevisionDevice) {
                                notificationFocusScope.launch {
                                    delay(90L)
                                    runCatching { appContentFocusRequester.requestFocus() }
                                }
                            }
                        },
                        onOpenUnknownSourcesSettings = {
                            viewModel.openOperationsInstallSettings { message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        },
                    )

                operationsAnnouncement != null -> {
                    OperationsAnnouncementOverlay(
                        announcement = operationsAnnouncement,
                        isTv = isTelevisionDevice,
                        onConfirm = {
                            viewModel.confirmOperationsAnnouncement()
                            if (isTelevisionDevice) {
                                notificationFocusScope.launch {
                                    delay(90L)
                                    runCatching { appContentFocusRequester.requestFocus() }
                                }
                            }
                        },
                    )
                }

                state.notificationPopup != null -> {
                    val popup = checkNotNull(state.notificationPopup)
                    NewEpisodeAlertOverlay(
                        popup = popup,
                        isTv = isTelevisionDevice,
                        onPresented = viewModel::confirmNotificationPopupPresented,
                        onPrimary = {
                            viewModel.activateNotificationPopup { message ->
                                message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            }
                        },
                        onLater = {
                            viewModel.dismissNotificationPopup()
                            if (isTelevisionDevice) {
                                notificationFocusScope.launch {
                                    delay(90L)
                                    runCatching { appContentFocusRequester.requestFocus() }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
