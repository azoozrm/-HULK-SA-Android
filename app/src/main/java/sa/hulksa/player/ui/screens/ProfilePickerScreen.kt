package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sa.hulksa.player.R
import sa.hulksa.player.data.ProfilePreferencesStore
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy
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

@Composable
fun ProfilePickerScreen(
    profiles: List<UserProfile>,
    activeProfileId: String,
    isTv: Boolean,
    isSwitching: Boolean,
    errorMessage: String?,
    onSelectProfile: (UserProfile) -> Unit,
    onCreateProfile: () -> Unit = {},
    onManageProfiles: () -> Unit = {},
) {
    val colors = LocalHulkColors.current
    val context = LocalContext.current
    val adaptiveUi = LocalAdaptiveUi.current
    val profilePreferencesStore = remember(context) { ProfilePreferencesStore(context) }
    var routingPreferences by remember(context) { mutableStateOf(profilePreferencesStore.routing()) }
    var showEntryOptions by remember { mutableStateOf(false) }
    val profileIds = remember(profiles) { profiles.map(UserProfile::id) }
    val focusRequesters = remember(profileIds) { profiles.associate { it.id to FocusRequester() } }
    val tvMetrics = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        profilePickerTvMetrics(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    }
    val mobileLandscape = !isTv && adaptiveUi.screenWidthDp > adaptiveUi.screenHeightDp
    val compactMobile = !isTv && (mobileLandscape || adaptiveUi.screenHeightDp < 620)

    LaunchedEffect(profiles) {
        routingPreferences = profilePreferencesStore.routing()
    }

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
                    routingPreferences = profilePreferencesStore.setRouting(
                        directEntryEnabled = !routingPreferences.directEntryEnabled,
                        defaultProfileId = routingPreferences.defaultProfileId,
                    )
                },
                onSelectDefaultProfile = { profileId ->
                    routingPreferences = profilePreferencesStore.setRouting(
                        directEntryEnabled = routingPreferences.directEntryEnabled,
                        defaultProfileId = profileId,
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
                Image(
                    painter = painterResource(R.drawable.hulk_sa_logo),
                    contentDescription = "HULK SA",
                    modifier = Modifier
                        .width(
                            if (isTv) tvMetrics.logoWidthDp.dp
                            else if (compactMobile) 88.dp else 104.dp,
                        )
                        .height(
                            if (isTv) tvMetrics.logoHeightDp.dp
                            else if (compactMobile) 52.dp else 62.dp,
                        ),
                    contentScale = ContentScale.Fit,
                )

                Spacer(Modifier.height(if (isTv) 13.dp else if (compactMobile) 6.dp else 10.dp))

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
                        focusBorderDp = if (isTv) tvMetrics.focusBorderDp else 2f,
                        onClick = onManageProfiles,
                    )
                    ProfileFooterButton(
                        text = "خيارات الدخول",
                        isTv = isTv,
                        enabled = !isSwitching,
                        focusBorderDp = if (isTv) tvMetrics.focusBorderDp else 2f,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                horizontal = if (isTv) metrics.horizontalPaddingDp.dp else 16.dp,
                vertical = if (isTv) metrics.verticalPaddingDp.dp else if (compactMobile) 12.dp else 20.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
            focusBorderDp = if (isTv) metrics.focusBorderDp else 2f,
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
            contentPadding = PaddingValues(horizontal = if (isTv) metrics.rowPaddingDp.dp else 4.dp, vertical = 4.dp),
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
                    focusBorderDp = if (isTv) metrics.focusBorderDp else 2f,
                    onClick = { onSelectDefaultProfile(null) },
                )
            }
            items(items = profiles, key = UserProfile::id) { profile ->
                ProfilePreferenceButton(
                    text = profile.displayName,
                    selected = defaultProfileId == profile.id,
                    isTv = isTv,
                    focusBorderDp = if (isTv) metrics.focusBorderDp else 2f,
                    onClick = { onSelectDefaultProfile(profile.id) },
                )
            }
        }

        Spacer(Modifier.height(if (isTv) 26.dp else if (compactMobile) 12.dp else 22.dp))
        ProfileFooterButton(
            text = "رجوع",
            isTv = isTv,
            enabled = true,
            focusBorderDp = if (isTv) metrics.focusBorderDp else 2f,
            onClick = onClose,
        )
    }
}

@Composable
private fun ProfilePreferenceButton(
    text: String,
    selected: Boolean,
    isTv: Boolean,
    focusRequester: FocusRequester? = null,
    focusBorderDp: Float,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 14.dp else 12.dp)

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(
                when {
                    selected -> colors.gold
                    focused -> colors.gold.copy(alpha = .18f)
                    else -> colors.surfaceRaised
                },
            )
            .border(
                if (focused) focusBorderDp.dp else 1.dp,
                when {
                    focused -> colors.goldBright
                    selected -> colors.gold.copy(alpha = .65f)
                    else -> Color.White.copy(alpha = .10f)
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val remoteSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!isTv || !remoteSelect) {
                    false
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> true
                        KeyEventType.KeyUp -> { onClick(); true }
                        else -> false
                    }
                }
            }
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (isTv) 18.dp else 14.dp,
                vertical = if (isTv) 11.dp else 9.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else if (focused) colors.goldBright else colors.text,
            fontSize = if (isTv) 13.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileFooterButton(
    text: String,
    isTv: Boolean,
    enabled: Boolean,
    focusBorderDp: Float,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 15.dp else 13.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (focused) colors.gold.copy(alpha = .22f) else colors.surfaceRaised)
            .border(
                if (focused) focusBorderDp.dp else 1.dp,
                if (focused) colors.goldBright else Color.White.copy(alpha = .10f),
                shape,
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
                horizontal = if (isTv) 20.dp else 16.dp,
                vertical = if (isTv) 11.dp else 9.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (focused) colors.goldBright else colors.text,
            fontSize = if (isTv) 14.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
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
            .background(if (focused) colors.surfaceRaised else colors.surface.copy(alpha = .94f))
            .border(
                width = if (focused) focusBorderDp.dp else 1.dp,
                color = if (focused) colors.goldBright else Color.White.copy(alpha = .10f),
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
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = if (focused) .08f else .04f))
                .border(
                    if (focused) focusBorderDp.dp else 1.dp,
                    if (focused) colors.goldBright else Color.White.copy(alpha = .22f),
                    RoundedCornerShape(50),
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

    Column(
        modifier = Modifier
            .width(cardWidthDp.dp)
            .clip(shape)
            .background(
                when {
                    focused -> colors.surfaceRaised
                    isActive -> colors.surfaceRaised
                    else -> colors.surface.copy(alpha = .94f)
                },
            )
            .border(
                width = when {
                    focused -> focusBorderDp.dp
                    isActive -> 1.5.dp
                    else -> 1.dp
                },
                color = when {
                    focused -> colors.goldBright
                    isActive -> colors.gold.copy(alpha = .60f)
                    else -> Color.White.copy(alpha = .10f)
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
            color = colors.text,
            fontSize = if (isTv) 17.sp else 15.sp,
            lineHeight = if (isTv) 21.sp else 19.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(7.dp))
        if (isActive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.gold.copy(alpha = .15f))
                    .border(1.dp, colors.gold.copy(alpha = .35f), RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "الحالي",
                    color = colors.goldBright,
                    fontSize = if (isTv) 11.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else if (profile.kind == ProfileKind.KIDS) {
            Text(
                text = "أطفال",
                color = colors.textMuted,
                fontSize = if (isTv) 11.sp else 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        } else {
            Spacer(Modifier.height(if (isTv) 17.dp else 15.dp))
        }
    }
}
