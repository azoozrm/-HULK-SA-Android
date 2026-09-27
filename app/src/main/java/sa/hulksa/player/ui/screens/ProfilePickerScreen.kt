package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sa.hulksa.player.data.ProfilePreferencesStore
import sa.hulksa.player.data.ProfileRoutingPreferences
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy
import sa.hulksa.player.ui.components.EntryActionButton
import sa.hulksa.player.ui.components.EntryPanelShape
import sa.hulksa.player.ui.components.EntrySurface
import sa.hulksa.player.ui.components.ProfileAvatar
import sa.hulksa.player.ui.theme.LocalHulkColors

internal data class ProfilePickerTvMetrics(
    val horizontalPaddingDp: Float,
    val verticalPaddingDp: Float,
    val rowPaddingDp: Float,
    val cardWidthDp: Float,
    val avatarSizeDp: Float,
    val cardGapDp: Float,
    val logoWidthDp: Float,
    val logoHeightDp: Float,
    val titleSizeSp: Float,
    val subtitleSizeSp: Float,
    val focusBorderDp: Float,
)

internal fun profilePickerTvMetrics(
    screenWidthDp: Int,
    screenHeightDp: Int,
): ProfilePickerTvMetrics {
    val width = screenWidthDp.coerceAtLeast(1)
    val height = screenHeightDp.coerceAtLeast(1)
    val policy = tvPremiumWindowPolicy(width, height)
    val compact = width <= 960 || height <= 540
    val large = width >= 1600 && height >= 900

    return ProfilePickerTvMetrics(
        horizontalPaddingDp = maxOf(
            policy.horizontalSafeInsetDp + 14f,
            when {
                compact -> 28f
                large -> 58f
                else -> 44f
            },
        ),
        verticalPaddingDp = maxOf(
            policy.verticalSafeInsetDp + 10f,
            when {
                compact -> 18f
                large -> 32f
                else -> 24f
            },
        ),
        rowPaddingDp = when {
            compact -> 18f
            large -> 46f
            else -> 34f
        },
        cardWidthDp = when {
            compact -> 154f
            large -> 190f
            else -> 176f
        },
        avatarSizeDp = when {
            compact -> 82f
            large -> 106f
            else -> 96f
        },
        cardGapDp = when {
            compact -> 14f
            large -> 24f
            else -> 20f
        },
        logoWidthDp = when {
            compact -> 106f
            large -> 136f
            else -> 122f
        },
        logoHeightDp = when {
            compact -> 62f
            large -> 80f
            else -> 72f
        },
        titleSizeSp = when {
            compact -> 27f
            large -> 34f
            else -> 31f
        },
        subtitleSizeSp = when {
            compact -> 12f
            large -> 15f
            else -> 14f
        },
        focusBorderDp = policy.focusBorderWidthDp,
    )
}

internal enum class ProfileCardVisualState {
    IDLE,
    ACTIVE,
    FOCUSED,
    ACTIVE_FOCUSED,
}

internal fun profileCardVisualState(active: Boolean, focused: Boolean): ProfileCardVisualState = when {
    active && focused -> ProfileCardVisualState.ACTIVE_FOCUSED
    focused -> ProfileCardVisualState.FOCUSED
    active -> ProfileCardVisualState.ACTIVE
    else -> ProfileCardVisualState.IDLE
}

@Composable
fun ProfilePickerScreen(
    profiles: List<UserProfile>,
    activeProfileId: String,
    isTv: Boolean,
    isSwitching: Boolean,
    errorMessage: String?,
    onSelectProfile: (UserProfile) -> Unit,
    routingPreferences: ProfileRoutingPreferences,
    onRoutingChanged: (ProfileRoutingPreferences) -> Unit,
    onCreateProfile: () -> Unit = {},
    onManageProfiles: () -> Unit = {},
) {
    val colors = LocalHulkColors.current
    val context = LocalContext.current
    val adaptiveUi = LocalAdaptiveUi.current
    val profilePreferencesStore = remember(context) { ProfilePreferencesStore(context) }
    var showEntryOptions by remember { mutableStateOf(false) }
    val profileIds = remember(profiles) { profiles.map(UserProfile::id) }
    val focusRequesters = remember(profileIds) { profiles.associate { it.id to FocusRequester() } }
    val tvMetrics = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        profilePickerTvMetrics(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    }
    val mobileLandscape = !isTv && adaptiveUi.screenWidthDp > adaptiveUi.screenHeightDp
    val compactMobile = !isTv && (mobileLandscape || adaptiveUi.screenHeightDp < 620)

    LaunchedEffect(isTv, activeProfileId, profileIds, showEntryOptions) {
        if (!isTv || profiles.isEmpty() || showEntryOptions) return@LaunchedEffect
        delay(140L)
        val requester = focusRequesters[activeProfileId] ?: focusRequesters[profiles.first().id]
        requester?.let { runCatching { it.requestFocus() } }
    }

    BackHandler(enabled = showEntryOptions) { showEntryOptions = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(colors.goldDeep.copy(alpha = .12f), Color.Transparent),
                        radius = if (isTv) 980f else 620f,
                    ),
                ),
        )

        if (showEntryOptions) {
            ProfileEntryOptionsPanel(
                profiles = profiles,
                isTv = isTv,
                metrics = tvMetrics,
                directEntryEnabled = routingPreferences.directEntryEnabled,
                defaultProfileId = routingPreferences.defaultProfileId,
                onToggleDirectEntry = {
                    onRoutingChanged(
                        profilePreferencesStore.setRouting(
                            directEntryEnabled = !routingPreferences.directEntryEnabled,
                            defaultProfileId = routingPreferences.defaultProfileId,
                        ),
                    )
                },
                onSelectDefaultProfile = { profileId ->
                    onRoutingChanged(
                        profilePreferencesStore.setRouting(
                            directEntryEnabled = routingPreferences.directEntryEnabled,
                            defaultProfileId = profileId,
                        ),
                    )
                },
                onClose = { showEntryOptions = false },
            )
            return@Box
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val horizontalPadding = when {
                isTv -> tvMetrics.horizontalPaddingDp.dp
                mobileLandscape -> 12.dp
                else -> 8.dp
            }
            val verticalPadding = when {
                isTv -> tvMetrics.verticalPaddingDp.dp
                compactMobile -> 8.dp
                else -> 14.dp
            }
            val cardGap = when {
                isTv -> tvMetrics.cardGapDp.dp
                compactMobile -> 10.dp
                else -> 8.dp
            }
            val rowPadding = if (isTv) tvMetrics.rowPaddingDp.dp else 0.dp
            val mobilePickerItemCount = (
                profiles.size + if (profiles.size < ProfileStore.MAX_PROFILES) 1 else 0
            ).coerceAtLeast(1)
            val mobileVisibleCardCount = mobilePickerItemCount.coerceAtMost(3)
            val mobileCardWidth = when {
                mobileLandscape -> 126f
                else -> (
                    (adaptiveUi.screenWidthDp - 16f - ((mobileVisibleCardCount - 1) * 8f)) /
                        mobileVisibleCardCount
                    ).coerceIn(92f, 124f)
            }
            val mobileAvatarSize = when {
                mobileLandscape -> 68f
                else -> (mobileCardWidth * .57f).coerceIn(54f, 70f)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "من يشاهد الان ؟",
                    color = colors.text,
                    fontSize = if (isTv) tvMetrics.titleSizeSp.sp else if (compactMobile) 23.sp else 26.sp,
                    lineHeight = if (isTv) (tvMetrics.titleSizeSp + 7f).sp else if (compactMobile) 28.sp else 32.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(if (compactMobile) 3.dp else 6.dp))

                Text(
                    text = "اختر ملفك الشخصي للمتابعة",
                    color = colors.textMuted,
                    fontSize = if (isTv) tvMetrics.subtitleSizeSp.sp else if (compactMobile) 11.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(if (isTv) 24.dp else if (compactMobile) 12.dp else 22.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = rowPadding,
                        vertical = if (isTv) 6.dp else if (compactMobile) 4.dp else 2.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = cardGap,
                        alignment = Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(items = profiles, key = UserProfile::id) { profile ->
                        val mobileProfileAvatarSize = if (
                            profile.kind == ProfileKind.KIDS && profile.id != activeProfileId
                        ) {
                            (mobileAvatarSize * .82f).coerceAtLeast(44f)
                        } else {
                            mobileAvatarSize
                        }
                        ProfilePickerCard(
                            profile = profile,
                            isActive = profile.id == activeProfileId,
                            isTv = isTv,
                            enabled = !isSwitching,
                            focusRequester = focusRequesters.getValue(profile.id),
                            cardWidthDp = if (isTv) tvMetrics.cardWidthDp else mobileCardWidth,
                            avatarSizeDp = if (isTv) tvMetrics.avatarSizeDp else mobileProfileAvatarSize,
                            focusBorderDp = if (isTv) tvMetrics.focusBorderDp else 2f,
                            onClick = { onSelectProfile(profile) },
                        )
                    }
                    if (profiles.size < ProfileStore.MAX_PROFILES) {
                        item(key = "add-profile") {
                            AddProfileCard(
                                isTv = isTv,
                                enabled = !isSwitching,
                                cardWidthDp = if (isTv) tvMetrics.cardWidthDp else mobileCardWidth,
                                avatarSizeDp = if (isTv) tvMetrics.avatarSizeDp else mobileAvatarSize,
                                focusBorderDp = if (isTv) tvMetrics.focusBorderDp else 2f,
                                onClick = onCreateProfile,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(if (isTv) 24.dp else if (compactMobile) 10.dp else 18.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileFooterButton(
                        text = "إدارة الملفات",
                        isTv = isTv,
                        enabled = !isSwitching,
                        onClick = onManageProfiles,
                    )
                    ProfileFooterButton(
                        text = "خيارات الدخول",
                        isTv = isTv,
                        enabled = !isSwitching,
                        onClick = { showEntryOptions = true },
                    )
                }

                Spacer(Modifier.height(if (isTv) 13.dp else if (compactMobile) 6.dp else 10.dp))

                when {
                    isSwitching -> Text(
                        text = "جار تبديل الملف الشخصي...",
                        color = colors.goldBright,
                        fontSize = if (isTv) 14.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    !errorMessage.isNullOrBlank() -> Text(
                        text = errorMessage,
                        color = colors.danger,
                        fontSize = if (isTv) 14.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    else -> Text(
                        text = if (isTv) "حرّك بالأسهم واضغط OK مرة واحدة" else "المس الملف الشخصي للمتابعة",
                        color = colors.textMuted.copy(alpha = .78f),
                        fontSize = if (isTv) 12.sp else if (compactMobile) 10.sp else 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileEntryOptionsPanel(
    profiles: List<UserProfile>,
    isTv: Boolean,
    metrics: ProfilePickerTvMetrics,
    directEntryEnabled: Boolean,
    defaultProfileId: String?,
    onToggleDirectEntry: () -> Unit,
    onSelectDefaultProfile: (String?) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val firstFocusRequester = remember { FocusRequester() }
    val panelScrollState = rememberScrollState()
    val compactMobile = !isTv && (
        adaptiveUi.screenHeightDp < 620 || adaptiveUi.screenWidthDp > adaptiveUi.screenHeightDp
    )
    val defaultProfileName = defaultProfileId
        ?.let { id -> profiles.firstOrNull { it.id == id }?.displayName }
        ?: "آخر مستخدم"

    LaunchedEffect(isTv) {
        if (!isTv) return@LaunchedEffect
        delay(120L)
        runCatching { firstFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                horizontal = if (isTv) metrics.horizontalPaddingDp.dp else 16.dp,
                vertical = if (isTv) metrics.verticalPaddingDp.dp else if (compactMobile) 12.dp else 20.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        EntrySurface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = if (isTv) 720.dp else 560.dp),
            shape = EntryPanelShape,
            contentPadding = PaddingValues(
                horizontal = if (isTv) 32.dp else if (compactMobile) 18.dp else 24.dp,
                vertical = if (isTv) 28.dp else if (compactMobile) 16.dp else 22.dp,
            ),
            scrollState = if (compactMobile) panelScrollState else null,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "خيارات الدخول",
                color = colors.text,
                fontSize = if (isTv) metrics.titleSizeSp.sp else if (compactMobile) 22.sp else 25.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (compactMobile) 5.dp else 8.dp))
            Text(
                text = "حدد هل تريد اختيار المستخدم عند كل تشغيل أو الدخول مباشرة",
                color = colors.textMuted,
                fontSize = if (isTv) metrics.subtitleSizeSp.sp else 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (isTv) 22.dp else if (compactMobile) 12.dp else 18.dp))

            ProfilePreferenceButton(
                text = if (directEntryEnabled) "الدخول المباشر: مفعّل" else "الدخول المباشر: متوقف",
                selected = directEntryEnabled,
                isTv = isTv,
                focusRequester = firstFocusRequester,
                onClick = onToggleDirectEntry,
            )

            Spacer(Modifier.height(if (compactMobile) 6.dp else 10.dp))
            Text(
                text = if (directEntryEnabled) {
                    "عند تشغيل التطبيق سيتم تجاوز صفحة اختيار المستخدم."
                } else {
                    "سيستمر ظهور صفحة اختيار المستخدم عند تشغيل التطبيق."
                },
                color = colors.textMuted,
                fontSize = if (isTv) 13.sp else 11.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(if (isTv) 24.dp else if (compactMobile) 12.dp else 20.dp))
            Text(
                text = "المستخدم الافتراضي: $defaultProfileName",
                color = colors.text,
                fontSize = if (isTv) 17.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "اختر مستخدمًا محددًا، أو اختر آخر مستخدم للدخول بآخر ملف استُخدم.",
                color = colors.textMuted,
                fontSize = if (isTv) 12.sp else 11.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (isTv) 15.dp else if (compactMobile) 8.dp else 12.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = if (isTv) 8.dp else 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    space = if (isTv) 12.dp else 8.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "last-used") {
                    ProfilePreferenceButton(
                        text = "آخر مستخدم",
                        selected = defaultProfileId == null,
                        isTv = isTv,
                        onClick = { onSelectDefaultProfile(null) },
                    )
                }
                items(items = profiles, key = UserProfile::id) { profile ->
                    ProfilePreferenceButton(
                        text = profile.displayName,
                        selected = defaultProfileId == profile.id,
                        isTv = isTv,
                        onClick = { onSelectDefaultProfile(profile.id) },
                    )
                }
            }

            Spacer(Modifier.height(if (isTv) 26.dp else if (compactMobile) 12.dp else 22.dp))
            ProfileFooterButton(
                text = "رجوع",
                isTv = isTv,
                enabled = true,
                onClick = onClose,
            )
        }
    }
}

@Composable
private fun ProfilePreferenceButton(
    text: String,
    selected: Boolean,
    isTv: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    EntryActionButton(
        text = text,
        onClick = onClick,
        modifier = Modifier.then(
            if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
        ),
        selected = selected,
        minHeight = if (isTv) 42.dp else 40.dp,
        textSizeSp = if (isTv) 13 else 12,
        horizontalPadding = if (isTv) 18.dp else 14.dp,
        verticalPadding = if (isTv) 11.dp else 9.dp,
    )
}

@Composable
private fun ProfileFooterButton(
    text: String,
    isTv: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    EntryActionButton(
        text = text,
        onClick = onClick,
        modifier = Modifier.onPreviewKeyEvent { event ->
            val remoteSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
            if (!isTv || !remoteSelect) {
                false
            } else if (!enabled) {
                true
            } else {
                when (event.type) {
                    KeyEventType.KeyDown -> true
                    KeyEventType.KeyUp -> { onClick(); true }
                    else -> false
                }
            }
        },
        enabled = enabled,
        minHeight = if (isTv) 44.dp else 42.dp,
        textSizeSp = if (isTv) 14 else 13,
        horizontalPadding = if (isTv) 20.dp else 16.dp,
        verticalPadding = if (isTv) 11.dp else 9.dp,
    )
}

@Composable
private fun AddProfileCard(
    isTv: Boolean,
    enabled: Boolean,
    cardWidthDp: Float,
    avatarSizeDp: Float,
    focusBorderDp: Float,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 22.dp else 18.dp)

    Column(
        modifier = Modifier
            .width(cardWidthDp.dp)
            .clip(shape)
            .background(if (focused) colors.surfaceRaised else Color(0xFF0B0C08))
            .border(
                width = if (focused) focusBorderDp.dp else 1.dp,
                color = if (focused) colors.goldBright else Color.White.copy(alpha = .06f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val remoteSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!isTv || !remoteSelect) {
                    false
                } else if (!enabled) {
                    true
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> true
                        KeyEventType.KeyUp -> { onClick(); true }
                        else -> false
                    }
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (isTv) 16.dp else 12.dp,
                vertical = if (isTv) 18.dp else 14.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(avatarSizeDp.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (focused) .08f else .04f))
                .border(
                    if (focused) focusBorderDp.dp else 1.dp,
                    if (focused) colors.goldBright else Color.White.copy(alpha = .18f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = if (focused) colors.goldBright else colors.textMuted,
                fontSize = if (isTv) 46.sp else 38.sp,
                lineHeight = if (isTv) 50.sp else 42.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(if (isTv) 14.dp else 10.dp))
        Text(
            text = "إضافة ملف",
            color = if (focused) colors.text else colors.textMuted,
            fontSize = if (isTv) 17.sp else 15.sp,
            lineHeight = if (isTv) 21.sp else 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(if (isTv) 24.dp else 22.dp))
    }
}

@Composable
private fun ProfilePickerCard(
    profile: UserProfile,
    isActive: Boolean,
    isTv: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    cardWidthDp: Float,
    avatarSizeDp: Float,
    focusBorderDp: Float,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(profile.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 22.dp else 18.dp)
    val visualState = profileCardVisualState(active = isActive, focused = focused)

    Column(
        modifier = Modifier
            .width(cardWidthDp.dp)
            .clip(shape)
            .background(
                when (visualState) {
                    ProfileCardVisualState.FOCUSED,
                    ProfileCardVisualState.ACTIVE_FOCUSED,
                    -> colors.surfaceRaised

                    ProfileCardVisualState.ACTIVE -> Color(0xFF131408)
                    ProfileCardVisualState.IDLE -> Color(0xFF0B0C08)
                },
            )
            .border(
                width = when (visualState) {
                    ProfileCardVisualState.FOCUSED,
                    ProfileCardVisualState.ACTIVE_FOCUSED,
                    -> focusBorderDp.dp

                    ProfileCardVisualState.ACTIVE -> 1.dp
                    ProfileCardVisualState.IDLE -> 1.dp
                },
                color = when (visualState) {
                    ProfileCardVisualState.FOCUSED,
                    ProfileCardVisualState.ACTIVE_FOCUSED,
                    -> colors.goldBright

                    ProfileCardVisualState.ACTIVE -> colors.gold.copy(alpha = .40f)
                    ProfileCardVisualState.IDLE -> Color.White.copy(alpha = .06f)
                },
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val remoteSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!isTv || !remoteSelect) {
                    false
                } else if (!enabled) {
                    true
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> true
                        KeyEventType.KeyUp -> { onClick(); true }
                        else -> false
                    }
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (isTv) 16.dp else 12.dp,
                vertical = if (isTv) 18.dp else 14.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatar(
            avatarKey = profile.avatarKey,
            modifier = Modifier.size(avatarSizeDp.dp),
            highlighted = focused || isActive,
        )

        Spacer(Modifier.height(if (isTv) 14.dp else 10.dp))
        Text(
            text = profile.displayName,
            color = when (visualState) {
                ProfileCardVisualState.FOCUSED,
                ProfileCardVisualState.ACTIVE_FOCUSED,
                -> colors.goldBright

                else -> colors.text
            },
            fontSize = if (isTv) 17.sp else 15.sp,
            lineHeight = if (isTv) 21.sp else 19.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier.height(if (isTv) 20.dp else 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isActive -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colors.goldBright),
                    )
                    Text(
                        text = "الحالي",
                        color = colors.goldBright,
                        fontSize = if (isTv) 11.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                profile.kind == ProfileKind.KIDS -> Text(
                    text = "أطفال",
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
