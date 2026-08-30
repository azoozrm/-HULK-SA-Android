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
import sa.hulksa.player.data.LegacyParentalCodeMigrationResult
import sa.hulksa.player.data.OperationsServiceStatus
import sa.hulksa.player.data.OperationsUpdateDecision
import sa.hulksa.player.data.ParentalCodeCredentialStore
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
import sa.hulksa.player.ui.screens.ParentalCodeBootstrapScreen
import sa.hulksa.player.ui.screens.ParentalCodeUnlockScreen
import sa.hulksa.player.ui.screens.ProfilePickerScreen
import sa.hulksa.player.ui.screens.ProfilePinProtectionScreen
import sa.hulksa.player.ui.screens.ProfilePinUnlockScreen
import sa.hulksa.player.ui.screens.RequiredUpdateScreen
import sa.hulksa.player.ui.screens.removeLiveTvProProfileState

internal val LocalProfileSwitchRequester = staticCompositionLocalOf<() -> Unit> { {} }

private const val MISSING_PARENTAL_CODE_MESSAGE =
    "يلزم رمز الوالدين للانتقال من ملف الأطفال. لا يوجد رمز والدين صالح."
private const val PARENTAL_CODE_SETUP_REQUIRED_MESSAGE =
    "يجب إنشاء رمز الوالدين قبل استخدام ملفات الأطفال."
private const val PARENTAL_CODE_MIGRATION_FAILED_MESSAGE =
    "تعذر تهيئة رمز الوالدين بأمان. أعد المحاولة بعد تسجيل الدخول."

private sealed interface ParentalCodeBootstrapAction {
    data class SwitchProfile(val targetProfileId: String) : ParentalCodeBootstrapAction
    data class CreateKids(val name: String, val avatarKey: String) : ParentalCodeBootstrapAction
    data class OpenManagement(val startCreating: Boolean) : ParentalCodeBootstrapAction
}

private sealed interface ParentalCodeAuthorizationAction {
    data class SwitchProfile(val targetProfileId: String) : ParentalCodeAuthorizationAction
    data class OpenManagement(val startCreating: Boolean) : ParentalCodeAuthorizationAction
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
    val parentalCodeCredentialStore = remember(context) { ParentalCodeCredentialStore(context) }
    val hulkRepository = remember(context) { HulkRepository(context) }
    val authenticated = state.account != null && state.screen != HulkScreen.LOGIN
    val activeAccountId = if (authenticated) accountScopeStore.activeAccountId() else null
    val navigationMemoryByProfile = remember { mutableMapOf<String, NavigationMemoryStore>() }
    val destinationMemoryByProfile = remember { mutableMapOf<String, MainDestination>() }
    val catalogNavigationMemoryByProfile = remember {
        mutableMapOf<String, ProfileCatalogNavigationMemory>()
    }

    var resolvedForSession by rememberSaveable(activeAccountId) { mutableStateOf(false) }
    var switching by rememberSaveable(activeAccountId) { mutableStateOf(false) }
    var switchError by rememberSaveable(activeAccountId) { mutableStateOf<String?>(null) }
    // Authorization-derived UI state must not survive process recreation.
    var managingProfiles by remember(activeAccountId) { mutableStateOf(false) }
    var createProfileRequested by remember(activeAccountId) { mutableStateOf(false) }
    var pickerRequestedFromApp by rememberSaveable(activeAccountId) { mutableStateOf(false) }
    var pinUnlockTargetId by remember(activeAccountId) { mutableStateOf<String?>(null) }
    var pinUnlockCredentialProfileId by remember(activeAccountId) { mutableStateOf<String?>(null) }
    var pinSecurityProfileId by remember(activeAccountId) { mutableStateOf<String?>(null) }
    var profileRevision by rememberSaveable(activeAccountId) { mutableIntStateOf(0) }
    var pinRevision by rememberSaveable(activeAccountId) { mutableIntStateOf(0) }
    var parentalCodeRevision by rememberSaveable(activeAccountId) { mutableIntStateOf(0) }
    var kidsSourceRequest by rememberSaveable(activeAccountId) { mutableIntStateOf(0) }
    var kidsSnapshot by remember { mutableStateOf<VerifiedKidsCatalogSnapshot?>(null) }
    var kidsSourceLoading by remember { mutableStateOf(false) }
    var kidsSourceError by remember { mutableStateOf<String?>(null) }
    var kidsSnapshotAccount by remember { mutableStateOf<String?>(null) }
    var parentalCodeBootstrapAction by remember(activeAccountId) {
        mutableStateOf<ParentalCodeBootstrapAction?>(null)
    }
    var parentalCodeAuthorizationAction by remember(activeAccountId) {
        mutableStateOf<ParentalCodeAuthorizationAction?>(null)
    }
    var parentalCodeMigrationResolvedAccountId by remember(activeAccountId) {
        mutableStateOf<String?>(null)
    }

    val profiles = remember(profileRevision, authenticated, activeAccountId) {
        profileStore.profiles()
    }
    val activeProfileId = remember(profileRevision, switching, authenticated, activeAccountId) {
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
    val legacyPrimaryAdultProfile = remember(profiles) {
        profiles.firstOrNull { profile ->
            profile.isPrimary && profile.kind == ProfileKind.STANDARD
        }
    }
    val hasKidsProfiles = remember(profiles) { profiles.any { it.kind == ProfileKind.KIDS } }
    val parentalCodeAvailable = remember(activeAccountId, parentalCodeRevision) {
        activeAccountId != null && parentalCodeCredentialStore.hasCode()
    }
    val parentalCodeStateReady = !authenticated || (
        activeAccountId != null &&
            parentalCodeMigrationResolvedAccountId == activeAccountId
    )

    LaunchedEffect(
        authenticated,
        activeAccountId,
        hasKidsProfiles,
        legacyPrimaryAdultProfile?.id,
    ) {
        if (!authenticated || activeAccountId == null) {
            parentalCodeMigrationResolvedAccountId = null
            return@LaunchedEffect
        }

        parentalCodeMigrationResolvedAccountId = null
        val migration = parentalCodeCredentialStore.ensureLegacyMigration(
            accountId = activeAccountId,
            hadKidsProfiles = hasKidsProfiles,
            legacyPrimaryProfileId = legacyPrimaryAdultProfile?.id,
            profilePinCredentialStore = profilePinCredentialStore,
        )
        if (accountScopeStore.activeAccountId() == activeAccountId) {
            parentalCodeRevision++
            parentalCodeMigrationResolvedAccountId = activeAccountId
            if (migration == LegacyParentalCodeMigrationResult.FAILED && hasKidsProfiles) {
                switchError = PARENTAL_CODE_MIGRATION_FAILED_MESSAGE
            }
        }
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
    val directEntryCandidate = if (
        authenticated &&
        parentalCodeStateReady &&
        !resolvedForSession &&
        !switching &&
        parentalCodeBootstrapAction == null &&
        parentalCodeAuthorizationAction == null &&
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
            parentalCodeBootstrapAction is ParentalCodeBootstrapAction.CreateKids ||
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

    fun beginProfilePinAuthorization(
        credentialProfile: UserProfile,
        targetProfileId: String? = null,
    ) {
        switchError = null
        managingProfiles = false
        createProfileRequested = false
        pinUnlockTargetId = targetProfileId
        pinUnlockCredentialProfileId = credentialProfile.id
    }

    fun beginParentalCodeBootstrap(action: ParentalCodeBootstrapAction): Boolean {
        if (activeAccountId == null || !parentalCodeStateReady) {
            switchError = PARENTAL_CODE_MIGRATION_FAILED_MESSAGE
            return false
        }
        parentalCodeBootstrapAction = action
        parentalCodeAuthorizationAction = null
        switchError = null
        managingProfiles = false
        createProfileRequested = false
        pinSecurityProfileId = null
        return true
    }

    fun beginParentalCodeAuthorization(action: ParentalCodeAuthorizationAction): Boolean {
        if (activeAccountId == null || !parentalCodeStateReady || !parentalCodeAvailable) {
            switchError = MISSING_PARENTAL_CODE_MESSAGE
            return false
        }
        parentalCodeAuthorizationAction = action
        switchError = null
        managingProfiles = false
        createProfileRequested = false
        pinSecurityProfileId = null
        return true
    }

    fun continueProfileSwitchAuthorization(
        profile: UserProfile,
        parentalAuthorizationGranted: Boolean = false,
    ) {
        val currentProfileId = profileStore.activeProfileId()
        val currentProfile = profiles.firstOrNull { it.id == currentProfileId }
        val authorization = profileSwitchAuthorization(
            currentProfileId = currentProfileId,
            currentProfileKind = currentProfile?.kind,
            targetProfileId = profile.id,
            targetProfileKind = profile.kind,
            targetProtected = profile.id in protectedProfileIds,
            resolvedForSession = resolvedForSession,
            parentalCodeAvailable = parentalCodeAvailable,
            parentalAuthorizationGranted = parentalAuthorizationGranted,
        )

        when (authorization) {
            ProfileSwitchAuthorization.ALLOW -> switchProfileUnlocked(profile)
            ProfileSwitchAuthorization.REQUIRE_TARGET_PIN -> beginProfilePinAuthorization(
                credentialProfile = profile,
                targetProfileId = profile.id,
            )
            ProfileSwitchAuthorization.REQUIRE_PARENTAL_CODE -> {
                beginParentalCodeAuthorization(
                    ParentalCodeAuthorizationAction.SwitchProfile(profile.id),
                )
            }
            ProfileSwitchAuthorization.DENY_NO_PARENT_CREDENTIAL -> {
                switchError = MISSING_PARENTAL_CODE_MESSAGE
            }
        }
    }

    fun requestProfileSwitch(profile: UserProfile) {
        val currentProfileId = profileStore.activeProfileId()
        val currentProfile = profiles.firstOrNull { it.id == currentProfileId }
        val parentalStateRequired = profileSelectionRequiresResolvedParentalState(
            currentProfileId = currentProfileId,
            currentProfileKind = currentProfile?.kind,
            targetProfileId = profile.id,
            targetProfileKind = profile.kind,
            parentalCodeAvailable = parentalCodeAvailable,
        )
        if (
            switching ||
            (!parentalCodeStateReady && parentalStateRequired) ||
            pinUnlockCredentialProfileId != null ||
            parentalCodeBootstrapAction != null ||
            parentalCodeAuthorizationAction != null
        ) return

        when (
            parentalCodeBootstrapDecision(
                currentProfileKind = currentProfile?.kind,
                targetProfileKind = profile.kind,
                parentalCodeAvailable = parentalCodeAvailable,
                resolvedForSession = resolvedForSession,
            )
        ) {
            ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP -> {
                if (!beginParentalCodeBootstrap(ParentalCodeBootstrapAction.SwitchProfile(profile.id))) {
                    switchError = MISSING_PARENTAL_CODE_MESSAGE
                }
                return
            }
            ParentalCodeBootstrapDecision.DENY_FAIL_CLOSED -> {
                switchError = MISSING_PARENTAL_CODE_MESSAGE
                return
            }
            ParentalCodeBootstrapDecision.ALLOW -> Unit
        }

        continueProfileSwitchAuthorization(profile)
    }

    fun requestProfileManagement(startCreating: Boolean) {
        if (
            switching ||
            !parentalCodeStateReady ||
            pinUnlockCredentialProfileId != null ||
            parentalCodeBootstrapAction != null ||
            parentalCodeAuthorizationAction != null
        ) return
        pinSecurityProfileId = null

        if (activeProfile?.kind == ProfileKind.KIDS && !parentalCodeAvailable) {
            when (
                parentalCodeBootstrapDecision(
                    currentProfileKind = activeProfile.kind,
                    targetProfileKind = ProfileKind.STANDARD,
                    parentalCodeAvailable = false,
                    resolvedForSession = resolvedForSession,
                )
            ) {
                ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP -> {
                    beginParentalCodeBootstrap(
                        ParentalCodeBootstrapAction.OpenManagement(startCreating),
                    )
                }
                ParentalCodeBootstrapDecision.DENY_FAIL_CLOSED -> {
                    switchError = MISSING_PARENTAL_CODE_MESSAGE
                    createProfileRequested = false
                }
                ParentalCodeBootstrapDecision.ALLOW -> Unit
            }
            return
        }

        if (requiresParentAuthorizationForProfileManagement(activeProfile?.kind)) {
            beginParentalCodeAuthorization(
                ParentalCodeAuthorizationAction.OpenManagement(startCreating),
            )
            return
        }

        switchError = null
        createProfileRequested = startCreating
        managingProfiles = true
    }

    fun completeParentalCodeBootstrap() {
        val action = parentalCodeBootstrapAction ?: return
        parentalCodeBootstrapAction = null
        switchError = null
        when (action) {
            is ParentalCodeBootstrapAction.SwitchProfile -> {
                val target = profiles.firstOrNull { it.id == action.targetProfileId }
                if (target == null) {
                    switchError = "تعذر العثور على الملف الشخصي المطلوب."
                } else {
                    // Creating and confirming the code authorizes this pending action once.
                    continueProfileSwitchAuthorization(
                        profile = target,
                        parentalAuthorizationGranted = true,
                    )
                }
            }
            is ParentalCodeBootstrapAction.CreateKids -> {
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
            is ParentalCodeBootstrapAction.OpenManagement -> {
                createProfileRequested = action.startCreating
                managingProfiles = true
            }
        }
    }

    fun cancelParentalCodeBootstrap() {
        val action = parentalCodeBootstrapAction ?: return
        parentalCodeBootstrapAction = null
        when {
            activeProfile?.kind == ProfileKind.KIDS && !resolvedForSession -> {
                pickerRequestedFromApp = true
                switchError = PARENTAL_CODE_SETUP_REQUIRED_MESSAGE
            }
            action is ParentalCodeBootstrapAction.CreateKids ||
                action is ParentalCodeBootstrapAction.OpenManagement -> {
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

    fun completeParentalCodeAuthorization() {
        val action = parentalCodeAuthorizationAction ?: return
        parentalCodeAuthorizationAction = null
        switchError = null
        when (action) {
            is ParentalCodeAuthorizationAction.SwitchProfile -> {
                val target = profiles.firstOrNull { it.id == action.targetProfileId }
                if (target == null) {
                    switchError = "تعذر العثور على الملف الشخصي المطلوب."
                } else {
                    continueProfileSwitchAuthorization(
                        profile = target,
                        parentalAuthorizationGranted = true,
                    )
                }
            }
            is ParentalCodeAuthorizationAction.OpenManagement -> {
                createProfileRequested = action.startCreating
                managingProfiles = true
            }
        }
    }

    fun cancelParentalCodeAuthorization() {
        parentalCodeAuthorizationAction = null
        createProfileRequested = false
        switchError = null
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
        parentalCodeStateReady,
        parentalCodeBootstrapAction,
        parentalCodeAuthorizationAction,
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
            pinSecurityProfileId = null
            parentalCodeBootstrapAction = null
            parentalCodeAuthorizationAction = null
            parentalCodeMigrationResolvedAccountId = null
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
            parentalCodeBootstrapAction == null &&
            parentalCodeAuthorizationAction == null &&
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
        parentalCodeStateReady,
        parentalCodeBootstrapAction,
        parentalCodeAuthorizationAction,
        unlockCredentialProfile?.id,
        securityProfile?.id,
        activeProfile?.id,
        activeProfile?.kind,
        kidsSnapshot?.isAvailable,
    ) {
        val profileReady = authenticated &&
            parentalCodeStateReady &&
            resolvedForSession &&
            !showPicker &&
            !managingProfiles &&
            parentalCodeBootstrapAction == null &&
            parentalCodeAuthorizationAction == null &&
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

            parentalCodeBootstrapAction != null && activeAccountId != null -> ParentalCodeBootstrapScreen(
            credentialScopeKey = activeAccountId,
            isTv = isTelevisionDevice,
            onSetCode = { code ->
                val stored = parentalCodeCredentialStore.setCode(code)
                if (stored) parentalCodeRevision++
                stored
            },
            onCompleted = ::completeParentalCodeBootstrap,
            onCancel = ::cancelParentalCodeBootstrap,
        )

            parentalCodeAuthorizationAction != null && activeAccountId != null -> ParentalCodeUnlockScreen(
            credentialScopeKey = activeAccountId,
            isTv = isTelevisionDevice,
            onVerify = parentalCodeCredentialStore::verifyCode,
            onUnlocked = ::completeParentalCodeAuthorization,
            onCancel = ::cancelParentalCodeAuthorization,
        )

            unlockCredentialProfile != null -> ProfilePinUnlockScreen(
            profile = unlockCredentialProfile,
            isTv = isTelevisionDevice,
            onVerify = { pin ->
                profilePinCredentialStore.verifyPin(unlockCredentialProfile.id, pin)
            },
            onUnlocked = {
                val targetProfile = unlockTargetProfile
                pinUnlockTargetId = null
                pinUnlockCredentialProfileId = null

                if (targetProfile != null) {
                    createProfileRequested = false
                    switchProfileUnlocked(targetProfile)
                } else {
                    createProfileRequested = false
                }
            },
            onCancel = {
                pinUnlockTargetId = null
                pinUnlockCredentialProfileId = null
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
                val cleared = profilePinCredentialStore.clearPin(securityProfile.id)
                if (cleared) pinRevision++
                cleared
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
                } else if (kind == ProfileKind.KIDS && !parentalCodeAvailable) {
                    when (
                        parentalCodeBootstrapDecision(
                            currentProfileKind = activeProfile?.kind,
                            targetProfileKind = ProfileKind.KIDS,
                            parentalCodeAvailable = false,
                            resolvedForSession = resolvedForSession,
                        )
                    ) {
                        ParentalCodeBootstrapDecision.REQUIRE_PARENTAL_CODE_SETUP -> {
                            beginParentalCodeBootstrap(
                                ParentalCodeBootstrapAction.CreateKids(
                                    name = name,
                                    avatarKey = avatarKey,
                                ),
                            )
                            false
                        }
                        ParentalCodeBootstrapDecision.DENY_FAIL_CLOSED -> {
                            switchError = MISSING_PARENTAL_CODE_MESSAGE
                            false
                        }
                        ParentalCodeBootstrapDecision.ALLOW -> false
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
