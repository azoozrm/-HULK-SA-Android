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
import sa.hulksa.player.data.AccountScopeStore
import sa.hulksa.player.data.HulkRepository
import sa.hulksa.player.data.OperationsServiceStatus
import sa.hulksa.player.data.OperationsUpdateDecision
import sa.hulksa.player.data.ProfilePinCredentialStore
import sa.hulksa.player.data.ProfileContentSearchHistoryStore
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
import sa.hulksa.player.ui.screens.ParentPinBootstrapScreen
import sa.hulksa.player.ui.screens.ProfilePickerScreen
import sa.hulksa.player.ui.screens.ProfilePinProtectionScreen
import sa.hulksa.player.ui.screens.ProfilePinUnlockScreen
import sa.hulksa.player.ui.screens.RequiredUpdateScreen
import sa.hulksa.player.ui.screens.removeLiveTvProProfileState

internal val LocalProfileSwitchRequester = staticCompositionLocalOf<() -> Unit> { {} }

private const val MISSING_PARENT_AUTHORIZATION_MESSAGE =
    "يلزم رمز الوالدين للانتقال من ملف الأطفال. لا يوجد رمز والدين صالح."
private const val PARENT_PIN_SETUP_REQUIRED_MESSAGE =
    "يجب إنشاء رمز الوالدين في الملف الرئيسي قبل استخدام ملفات الأطفال."

private sealed interface ParentPinBootstrapAction {
    data class SwitchProfile(val targetProfileId: String) : ParentPinBootstrapAction
    data class CreateKids(val name: String, val avatarKey: String) : ParentPinBootstrapAction
    data class OpenManagement(val startCreating: Boolean) : ParentPinBootstrapAction
}

@Composable
fun ProfileAwareHulkApp(
    viewModel: HulkViewModel,
    isTelevisionDevice: Boolean,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val accountScopeStore = remember(context) { AccountScopeStore(context) }
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
    var pinUnlockCredentialProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var profileManagementUnlockRequested by rememberSaveable { mutableStateOf(false) }
    var pinSecurityProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var postBootstrapTargetProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var profileRevision by rememberSaveable { mutableIntStateOf(0) }
    var pinRevision by rememberSaveable { mutableIntStateOf(0) }
    var kidsSourceRequest by rememberSaveable { mutableIntStateOf(0) }
    var kidsSnapshot by remember { mutableStateOf<VerifiedKidsCatalogSnapshot?>(null) }
    var kidsSourceLoading by remember { mutableStateOf(false) }
    var kidsSourceError by remember { mutableStateOf<String?>(null) }
    var kidsSnapshotAccount by remember { mutableStateOf<String?>(null) }
    var parentPinBootstrapAction by remember { mutableStateOf<ParentPinBootstrapAction?>(null) }

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
    val primaryAdultProfile = remember(profiles) {
        profiles.firstOrNull { profile ->
            profile.isPrimary && profile.kind == ProfileKind.STANDARD
        }
    }
    val primaryParentCredentialProfile = remember(primaryAdultProfile, protectedProfileIds) {
        primaryAdultProfile?.takeIf { it.id in protectedProfileIds }
    }
    val hasKidsProfiles = remember(profiles) { profiles.any { it.kind == ProfileKind.KIDS } }
    val activeNavigationMemory = remember(activeProfileId) {
        navigationMemoryByProfile.getOrPut(activeProfileId) { NavigationMemoryStore() }
    }
    val activeCatalogNavigationMemory = remember(activeProfileId) {
        catalogNavigationMemoryByProfile.getOrPut(activeProfileId) {
            ProfileCatalogNavigationMemory()
        }
    }
    val routingPreferences = profilePreferencesStore.routing()
    val directEntryCandidate = if (
        authenticated &&
        !resolvedForSession &&
        !switching &&
        parentPinBootstrapAction == null &&
        pinUnlockCredentialProfileId == null &&
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
    val directEntryTarget = directEntryCandidate?.let { candidate ->
        if (
            shouldRetainKidsProfileForDirectEntry(
                currentProfileId = activeProfileId,
                currentProfileKind = activeProfile?.kind,
                targetProfileId = candidate.id,
                targetProfileKind = candidate.kind,
            )
        ) {
            activeProfile ?: candidate
        } else {
            candidate
        }
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
            parentPinBootstrapAction is ParentPinBootstrapAction.CreateKids ||
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

        if (isTelevisionDevice) viewModel.beginTvPlatformProfileSwitch()
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

    fun beginPinAuthorization(
        credentialProfile: UserProfile,
        targetProfileId: String? = null,
        forProfileManagement: Boolean = false,
        startCreating: Boolean = false,
    ) {
        switchError = null
        managingProfiles = false
        createProfileRequested = startCreating
        profileManagementUnlockRequested = forProfileManagement
        pinUnlockTargetId = targetProfileId
        pinUnlockCredentialProfileId = credentialProfile.id
    }

    fun beginParentPinBootstrap(action: ParentPinBootstrapAction): Boolean {
        val parentProfile = primaryAdultProfile
        if (parentProfile == null) {
            switchError = MISSING_PARENT_AUTHORIZATION_MESSAGE
            return false
        }
        parentPinBootstrapAction = action
        switchError = null
        managingProfiles = false
        createProfileRequested = false
        pinSecurityProfileId = null
        return true
    }

    fun requestProfileSwitch(profile: UserProfile) {
        if (switching || pinUnlockCredentialProfileId != null || parentPinBootstrapAction != null) return
        val currentProfileId = profileStore.activeProfileId()
        val currentProfile = profiles.firstOrNull { it.id == currentProfileId }

        when (
            parentPinBootstrapDecision(
                currentProfileKind = currentProfile?.kind,
                targetProfileKind = profile.kind,
                primaryParentPinAvailable = primaryParentCredentialProfile != null,
                resolvedForSession = resolvedForSession,
            )
        ) {
            ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP -> {
                if (!beginParentPinBootstrap(ParentPinBootstrapAction.SwitchProfile(profile.id))) {
                    switchError = MISSING_PARENT_AUTHORIZATION_MESSAGE
                }
                return
            }
            ParentPinBootstrapDecision.DENY_FAIL_CLOSED -> {
                switchError = MISSING_PARENT_AUTHORIZATION_MESSAGE
                return
            }
            ParentPinBootstrapDecision.ALLOW -> Unit
        }

        val authorization = profileSwitchAuthorization(
            currentProfileId = currentProfileId,
            currentProfileKind = currentProfile?.kind,
            targetProfileId = profile.id,
            targetProfileKind = profile.kind,
            targetProtected = profile.id in protectedProfileIds,
            resolvedForSession = resolvedForSession,
            primaryParentPinAvailable = primaryParentCredentialProfile != null,
        )

        when (authorization) {
            ProfileSwitchAuthorization.ALLOW -> switchProfileUnlocked(profile)
            ProfileSwitchAuthorization.REQUIRE_TARGET_PIN -> beginPinAuthorization(
                credentialProfile = profile,
                targetProfileId = profile.id,
            )
            ProfileSwitchAuthorization.REQUIRE_PRIMARY_PARENT_PIN -> {
                val parentProfile = primaryParentCredentialProfile
                if (parentProfile == null) {
                    switchError = MISSING_PARENT_AUTHORIZATION_MESSAGE
                    return
                }
                beginPinAuthorization(
                    credentialProfile = parentProfile,
                    targetProfileId = profile.id,
                )
            }
            ProfileSwitchAuthorization.DENY_NO_PARENT_CREDENTIAL -> {
                switchError = MISSING_PARENT_AUTHORIZATION_MESSAGE
            }
        }
    }

    fun requestProfileManagement(startCreating: Boolean) {
        if (switching || pinUnlockCredentialProfileId != null || parentPinBootstrapAction != null) return
        pinSecurityProfileId = null

        if (activeProfile?.kind == ProfileKind.KIDS && primaryParentCredentialProfile == null) {
            when (
                parentPinBootstrapDecision(
                    currentProfileKind = activeProfile.kind,
                    targetProfileKind = ProfileKind.STANDARD,
                    primaryParentPinAvailable = false,
                    resolvedForSession = resolvedForSession,
                )
            ) {
                ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP -> {
                    beginParentPinBootstrap(ParentPinBootstrapAction.OpenManagement(startCreating))
                }
                ParentPinBootstrapDecision.DENY_FAIL_CLOSED -> {
                    switchError = MISSING_PARENT_AUTHORIZATION_MESSAGE
                    createProfileRequested = false
                }
                ParentPinBootstrapDecision.ALLOW -> Unit
            }
            return
        }

        if (requiresParentAuthorizationForProfileManagement(activeProfile?.kind)) {
            val parentProfile = primaryParentCredentialProfile
            if (parentProfile == null) {
                switchError = MISSING_PARENT_AUTHORIZATION_MESSAGE
                createProfileRequested = false
                return
            }
            beginPinAuthorization(
                credentialProfile = parentProfile,
                forProfileManagement = true,
                startCreating = startCreating,
            )
            return
        }

        switchError = null
        createProfileRequested = startCreating
        managingProfiles = true
    }

    fun completeParentPinBootstrap() {
        val action = parentPinBootstrapAction ?: return
        parentPinBootstrapAction = null
        switchError = null
        when (action) {
            is ParentPinBootstrapAction.SwitchProfile -> {
                postBootstrapTargetProfileId = action.targetProfileId
            }
            is ParentPinBootstrapAction.CreateKids -> {
                val created = if (kidsSnapshot?.isAvailable == true) {
                    profileStore.createProfile(
                        displayName = action.name,
                        avatarKey = action.avatarKey,
                        kind = ProfileKind.KIDS,
                    )
                } else {
                    null
                }
                if (created != null) {
                    profileRevision++
                    createProfileRequested = false
                } else {
                    switchError = "تعذر إنشاء ملف الأطفال. تحقق من مصدر الأطفال ثم حاول مرة أخرى."
                }
                managingProfiles = true
            }
            is ParentPinBootstrapAction.OpenManagement -> {
                createProfileRequested = action.startCreating
                managingProfiles = true
            }
        }
    }

    fun cancelParentPinBootstrap() {
        val action = parentPinBootstrapAction ?: return
        parentPinBootstrapAction = null
        postBootstrapTargetProfileId = null
        when {
            activeProfile?.kind == ProfileKind.KIDS && !resolvedForSession -> {
                pickerRequestedFromApp = true
                switchError = PARENT_PIN_SETUP_REQUIRED_MESSAGE
            }
            action is ParentPinBootstrapAction.CreateKids || action is ParentPinBootstrapAction.OpenManagement -> {
                managingProfiles = true
                createProfileRequested = false
                switchError = null
            }
            else -> {
                // Cancelling Adult -> Kids bootstrap must not immediately re-trigger direct entry.
                resolvedForSession = true
                switchError = null
            }
        }
    }

    LaunchedEffect(postBootstrapTargetProfileId, pinRevision, profiles) {
        val targetId = postBootstrapTargetProfileId ?: return@LaunchedEffect
        val target = profiles.firstOrNull { it.id == targetId } ?: run {
            postBootstrapTargetProfileId = null
            return@LaunchedEffect
        }
        postBootstrapTargetProfileId = null
        requestProfileSwitch(target)
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
        parentPinBootstrapAction,
        pinUnlockCredentialProfileId,
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
            pinUnlockCredentialProfileId = null
            profileManagementUnlockRequested = false
            pinSecurityProfileId = null
            parentPinBootstrapAction = null
            postBootstrapTargetProfileId = null
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
            parentPinBootstrapAction == null &&
            pinUnlockCredentialProfileId == null,
    ) {
        pickerRequestedFromApp = false
        switchError = null
    }

    val unlockCredentialProfile = pinUnlockCredentialProfileId
        ?.let { profileId -> profiles.firstOrNull { it.id == profileId } }
    val unlockTargetProfile = pinUnlockTargetId
        ?.let { targetId -> profiles.firstOrNull { it.id == targetId } }
    val securityProfile = pinSecurityProfileId
        ?.let { profileId -> profiles.firstOrNull { it.id == profileId } }

    LaunchedEffect(
        authenticated,
        resolvedForSession,
        showPicker,
        managingProfiles,
        parentPinBootstrapAction,
        unlockCredentialProfile?.id,
        securityProfile?.id,
        activeProfile?.id,
        activeProfile?.kind,
        kidsSnapshot?.isAvailable,
    ) {
        val profileReady = authenticated &&
            resolvedForSession &&
            !showPicker &&
            !managingProfiles &&
            parentPinBootstrapAction == null &&
            unlockCredentialProfile == null &&
            securityProfile == null &&
            (activeProfile?.kind != ProfileKind.KIDS || kidsSnapshot?.isAvailable == true)
        viewModel.setNotificationUiReady(ready = profileReady)
        if (isTelevisionDevice) viewModel.setTvPlatformProfileReady(ready = profileReady)
    }

    val appContentFocusRequester = remember { FocusRequester() }
    val notificationFocusScope = rememberCoroutineScope()
    val profilePinCleanupScope = rememberCoroutineScope()
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

            parentPinBootstrapAction != null && primaryAdultProfile != null -> ParentPinBootstrapScreen(
            primaryAdultProfile = primaryAdultProfile,
            isTv = isTelevisionDevice,
            onSetPin = { pin ->
                val stored = profilePinCredentialStore.setPin(primaryAdultProfile.id, pin)
                if (stored) pinRevision++
                stored
            },
            onCompleted = ::completeParentPinBootstrap,
            onCancel = ::cancelParentPinBootstrap,
        )

            unlockCredentialProfile != null -> ProfilePinUnlockScreen(
            profile = unlockCredentialProfile,
            isTv = isTelevisionDevice,
            onVerify = { pin ->
                profilePinCredentialStore.verifyPin(unlockCredentialProfile.id, pin)
            },
            onUnlocked = {
                val targetProfile = unlockTargetProfile
                val openManagement = profileManagementUnlockRequested
                pinUnlockTargetId = null
                pinUnlockCredentialProfileId = null
                profileManagementUnlockRequested = false

                when {
                    targetProfile != null -> {
                        createProfileRequested = false
                        switchProfileUnlocked(targetProfile)
                    }
                    openManagement -> {
                        managingProfiles = true
                    }
                    else -> {
                        createProfileRequested = false
                    }
                }
            },
            onCancel = {
                pinUnlockTargetId = null
                pinUnlockCredentialProfileId = null
                profileManagementUnlockRequested = false
                createProfileRequested = false
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
                if (stored) pinRevision++
                stored
            },
            onClearPin = {
                val isParentCredential = securityProfile.isPrimary && securityProfile.kind == ProfileKind.STANDARD
                if (isParentCredential && !canClearPrimaryParentPin(hasKidsProfiles)) {
                    Toast.makeText(
                        context,
                        "لا يمكن إزالة رمز الوالدين ما دامت هناك ملفات أطفال. احذف ملفات الأطفال أولًا.",
                        Toast.LENGTH_LONG,
                    ).show()
                    false
                } else {
                    val cleared = profilePinCredentialStore.clearPin(securityProfile.id)
                    if (cleared) pinRevision++
                    cleared
                }
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
                if (!kidsAllowed) {
                    false
                } else if (kind == ProfileKind.KIDS && primaryParentCredentialProfile == null) {
                    when (
                        parentPinBootstrapDecision(
                            currentProfileKind = activeProfile?.kind,
                            targetProfileKind = ProfileKind.KIDS,
                            primaryParentPinAvailable = false,
                            resolvedForSession = resolvedForSession,
                        )
                    ) {
                        ParentPinBootstrapDecision.REQUIRE_PARENT_PIN_SETUP -> {
                            beginParentPinBootstrap(
                                ParentPinBootstrapAction.CreateKids(
                                    name = name,
                                    avatarKey = avatarKey,
                                ),
                            )
                            false
                        }
                        ParentPinBootstrapDecision.DENY_FAIL_CLOSED -> {
                            switchError = MISSING_PARENT_AUTHORIZATION_MESSAGE
                            false
                        }
                        ParentPinBootstrapDecision.ALLOW -> false
                    }
                } else {
                    val created = profileStore.createProfile(name, avatarKey = avatarKey, kind = kind)
                    val success = created != null
                    if (success) {
                        createProfileRequested = false
                        profileRevision++
                    }
                    success
                }
            },
            onUpdate = { profileId, name, avatarKey ->
                val updated = profileStore.updateProfile(profileId, name, avatarKey)
                val success = updated != null
                if (success) profileRevision++
                success
            },
            onDelete = { profileId ->
                val accountId = accountScopeStore.activeAccountId()
                val deleted = profileStore.deleteProfile(profileId)
                if (deleted) {
                    if (accountId != null) {
                        ProfileContentSearchHistoryStore(context)
                            .removeProfileHistory(accountId, profileId)
                        context.removeLiveTvProProfileState(accountId, profileId)
                        profilePinCleanupScope.launch {
                            profilePinCredentialStore.clearCredential(accountId, profileId)
                        }
                    } else {
                        profilePinCleanupScope.launch {
                            profilePinCredentialStore.clearCredential(profileId)
                        }
                    }
                    profilePreferencesStore.removeProfilePreferences(profileId)
                    navigationMemoryByProfile.remove(profileId)
                    destinationMemoryByProfile.remove(profileId)
                    catalogNavigationMemoryByProfile.remove(profileId)
                    viewModel.removeNotificationProfileData(profileId)
                    viewModel.removeDownloadProfileData(profileId)
                    if (pinSecurityProfileId == profileId) pinSecurityProfileId = null
                    profileRevision++
                    pinRevision++
                }
                deleted
            },
            onSelect = ::requestProfileSwitch,
            onManagePin = { profile ->
                createProfileRequested = false
                if (
                    profile.isPrimary &&
                    profile.kind == ProfileKind.STANDARD &&
                    profile.id !in protectedProfileIds &&
                    hasKidsProfiles
                ) {
                    beginParentPinBootstrap(ParentPinBootstrapAction.OpenManagement(startCreating = false))
                } else {
                    pinSecurityProfileId = profile.id
                }
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
                    requestProfileManagement(startCreating = true)
                }
            },
            onManageProfiles = {
                requestProfileManagement(startCreating = false)
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
