package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.data.ProfilePreferencesStore
import sa.hulksa.player.data.ProfileRoutingPreferences
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.EntryActionButton
import sa.hulksa.player.ui.components.EntryBackAction
import sa.hulksa.player.ui.components.EntryNotice
import sa.hulksa.player.ui.components.EntrySwitchVisual
import sa.hulksa.player.ui.components.ProfileAvatar
import sa.hulksa.player.ui.components.entryPanelMaxWidthDp
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

internal const val PROFILE_PICKER_PHONE_MIN_CARD_FOOTPRINT_DP = 132f

/**
 * Bounded phone layout policy for the real profile collection: two complete card footprints plus
 * the bounded gap must fit the usable width, otherwise the collection uses one column. TV keeps
 * its own stable-ID row and never uses this policy.
 */
internal fun profilePickerPhoneColumnCount(
    usableWidthDp: Float,
    gapDp: Float,
    minCardFootprintDp: Float = PROFILE_PICKER_PHONE_MIN_CARD_FOOTPRINT_DP,
): Int = if (usableWidthDp >= minCardFootprintDp * 2f + gapDp) 2 else 1

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
    var returnFocusToEntryOptions by remember { mutableStateOf(false) }
    val entryOptionsRequester = remember { FocusRequester() }
    val profileIds = remember(profiles) { profiles.map(UserProfile::id) }
    val focusRequesters = remember(profileIds) { profiles.associate { it.id to FocusRequester() } }
    val tvMetrics = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        profilePickerTvMetrics(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    }
    val mobileLandscape = !isTv && adaptiveUi.screenWidthDp > adaptiveUi.screenHeightDp
    val compactMobile = !isTv && (mobileLandscape || adaptiveUi.screenHeightDp < 620)
    val actionFloorDp = movieActionHeightDp(isTv = isTv, compactHeight = !isTv && compactMobile)
    val headerMarkSize = (adaptiveUi.screenWidthDp * .20f).coerceIn(68f, 88f).dp
    // Both original logo resources have square canvases, so the TV header must use a SQUARE side.
    // The preferred side comes from the existing TV metrics logoWidthDp (106/122/136dp), bounded
    // by the real safe window and the space left after the unchanged title/subtitle/circles/footer
    // neighbours. This narrowly supersedes R03's height-limited rectangular slot for this caller.
    val tvLogoSide = minOf(
        tvMetrics.logoWidthDp.dp,
        (adaptiveUi.screenWidthDp * .22f).dp,
        (adaptiveUi.screenHeightDp * .24f).dp,
    )
    val tvLogoWidth = tvLogoSide
    val tvLogoHeight = tvLogoSide
    val expandedPanel = isTv || adaptiveUi.screenWidthDp >= 720

    LaunchedEffect(isTv, activeProfileId, profileIds, showEntryOptions) {
        if (!isTv || profiles.isEmpty() || showEntryOptions) return@LaunchedEffect
        val returningToTrigger = returnFocusToEntryOptions
        if (returningToTrigger) returnFocusToEntryOptions = false
        val requester = if (returningToTrigger) {
            entryOptionsRequester
        } else {
            focusRequesters[activeProfileId] ?: focusRequesters[profiles.first().id]
        }
        withFrameNanos { }
        requester?.let { runCatching { it.requestFocus() } }
    }

    val closeEntryOptions: () -> Unit = {
        returnFocusToEntryOptions = true
        showEntryOptions = false
    }
    BackHandler(enabled = showEntryOptions, onBack = closeEntryOptions)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (showEntryOptions) {
            ProfileEntryOptionsPanel(
                profiles = profiles,
                isTv = isTv,
                expandedPanel = expandedPanel,
                directEntryEnabled = routingPreferences.directEntryEnabled,
                defaultProfileId = routingPreferences.defaultProfileId,
                panelHorizontalPaddingDp = if (isTv) tvMetrics.horizontalPaddingDp else 16f,
                panelVerticalPaddingDp = if (isTv) tvMetrics.verticalPaddingDp else if (compactMobile) 12f else 20f,
                actionFloorDp = actionFloorDp,
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
                onClose = closeEntryOptions,
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

            if (isTv) {
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val widestLabelDp = remember(profiles, tvMetrics) {
                    val style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Black)
                    val widestPx = (profiles.map { it.displayName } + "اضافة ملف")
                        .maxOf { textMeasurer.measure(it, style).size.width }
                    with(density) { widestPx.toDp().value }
                }
                val tvItemWidthDp = maxOf(tvMetrics.avatarSizeDp, widestLabelDp + 6f)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ProfilePickerHeading(
                        isTv = true,
                        compactMobile = false,
                        tvMetrics = tvMetrics,
                        headerMarkSize = headerMarkSize,
                        tvLogoWidth = tvLogoWidth,
                        tvLogoHeight = tvLogoHeight,
                    )

                    Spacer(Modifier.height(24.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = rowPadding, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            space = cardGap,
                            alignment = Alignment.CenterHorizontally,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(items = profiles, key = UserProfile::id) { profile ->
                            ProfilePickerCard(
                                profile = profile,
                                isActive = profile.id == activeProfileId,
                                isTv = true,
                                enabled = !isSwitching,
                                focusRequester = focusRequesters.getValue(profile.id),
                                cardWidthDp = tvItemWidthDp,
                                avatarSizeDp = tvMetrics.avatarSizeDp,
                                focusBorderDp = tvMetrics.focusBorderDp,
                                onClick = { onSelectProfile(profile) },
                            )
                        }
                        if (profiles.size < ProfileStore.MAX_PROFILES) {
                            item(key = "add-profile") {
                                AddProfileCard(
                                    isTv = true,
                                    enabled = !isSwitching,
                                    cardWidthDp = tvItemWidthDp,
                                    avatarSizeDp = tvMetrics.avatarSizeDp,
                                    focusBorderDp = tvMetrics.focusBorderDp,
                                    onClick = onCreateProfile,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    ProfilePickerFooter(
                        isTv = true,
                        actionFloorDp = actionFloorDp,
                        enabled = !isSwitching,
                        entryOptionsRequester = entryOptionsRequester,
                        onManageProfiles = onManageProfiles,
                        onOpenEntryOptions = { showEntryOptions = true },
                    )

                    Spacer(Modifier.height(13.dp))

                    ProfilePickerStatus(
                        isSwitching = isSwitching,
                        errorMessage = errorMessage,
                        isTv = true,
                        compactMobile = false,
                        expandedPanel = expandedPanel,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProfilePickerHeading(
                        isTv = false,
                        compactMobile = compactMobile,
                        tvMetrics = tvMetrics,
                        headerMarkSize = headerMarkSize,
                    )

                    Spacer(Modifier.height(if (compactMobile) 12.dp else 22.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val minCardFootprint = PROFILE_PICKER_PHONE_MIN_CARD_FOOTPRINT_DP.dp
                        val columnCount = profilePickerPhoneColumnCount(
                            usableWidthDp = maxWidth.value,
                            gapDp = cardGap.value,
                        )
                        val twoColumns = columnCount == 2
                        val gridMaxWidth = minOf(
                            maxWidth,
                            if (twoColumns) (minCardFootprint * 2 + cardGap) * 1.5f else 220.dp,
                        )
                        val gridCardWidth = if (twoColumns) {
                            (gridMaxWidth - cardGap) / 2
                        } else {
                            gridMaxWidth
                        }
                        val gridAvatarSize = (gridCardWidth.value * .57f).coerceIn(54f, 70f)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columnCount),
                            modifier = Modifier
                                .widthIn(max = gridMaxWidth)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(cardGap),
                            verticalArrangement = Arrangement.spacedBy(cardGap),
                        ) {
                            gridItems(items = profiles, key = UserProfile::id) { profile ->
                                ProfilePickerCard(
                                    profile = profile,
                                    isActive = profile.id == activeProfileId,
                                    isTv = false,
                                    enabled = !isSwitching,
                                    focusRequester = focusRequesters.getValue(profile.id),
                                    cardWidthDp = gridCardWidth.value,
                                    avatarSizeDp = gridAvatarSize,
                                    focusBorderDp = 2f,
                                    onClick = { onSelectProfile(profile) },
                                )
                            }
                            if (profiles.size < ProfileStore.MAX_PROFILES) {
                                item(key = "add-profile") {
                                    AddProfileCard(
                                        isTv = false,
                                        enabled = !isSwitching,
                                        cardWidthDp = gridCardWidth.value,
                                        avatarSizeDp = gridAvatarSize,
                                        focusBorderDp = 2f,
                                        onClick = onCreateProfile,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(if (compactMobile) 10.dp else 18.dp))

                    ProfilePickerFooter(
                        isTv = false,
                        actionFloorDp = actionFloorDp,
                        enabled = !isSwitching,
                        entryOptionsRequester = entryOptionsRequester,
                        onManageProfiles = onManageProfiles,
                        onOpenEntryOptions = { showEntryOptions = true },
                    )

                    Spacer(Modifier.height(if (compactMobile) 6.dp else 10.dp))

                    ProfilePickerStatus(
                        isSwitching = isSwitching,
                        errorMessage = errorMessage,
                        isTv = false,
                        compactMobile = compactMobile,
                        expandedPanel = expandedPanel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfilePickerHeading(
    isTv: Boolean,
    compactMobile: Boolean,
    tvMetrics: ProfilePickerTvMetrics,
    headerMarkSize: Dp,
    tvLogoWidth: Dp = headerMarkSize,
    tvLogoHeight: Dp = headerMarkSize,
) {
    val colors = LocalHulkColors.current
    BrandLogo(
        modifier = Modifier
            .size(
                width = if (isTv) tvLogoWidth else headerMarkSize,
                height = if (isTv) tvLogoHeight else headerMarkSize,
            )
            .clearAndSetSemantics { },
        contentScale = ContentScale.Fit,
    )
    Spacer(Modifier.height(if (compactMobile) 6.dp else 10.dp))
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
}

@Composable
private fun ProfilePickerFooter(
    isTv: Boolean,
    actionFloorDp: Int,
    enabled: Boolean,
    entryOptionsRequester: FocusRequester,
    onManageProfiles: () -> Unit,
    onOpenEntryOptions: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryActionButton(
            text = "ادارة الملفات",
            onClick = onManageProfiles,
            outlined = true,
            enabled = enabled,
            minHeightDp = actionFloorDp,
        )
        EntryActionButton(
            text = "خيارات الدخول",
            onClick = onOpenEntryOptions,
            outlined = true,
            enabled = enabled,
            minHeightDp = actionFloorDp,
            modifier = Modifier.focusRequester(entryOptionsRequester),
        )
    }
}

@Composable
private fun ProfilePickerStatus(
    isSwitching: Boolean,
    errorMessage: String?,
    isTv: Boolean,
    compactMobile: Boolean,
    expandedPanel: Boolean,
) {
    val colors = LocalHulkColors.current
    when {
        isSwitching -> Text(
            text = "جار تبديل الملف الشخصي...",
            color = colors.goldBright,
            fontSize = if (isTv) 14.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        !errorMessage.isNullOrBlank() -> EntryNotice(
            message = errorMessage,
            compact = compactMobile,
            modifier = Modifier.widthIn(max = entryPanelMaxWidthDp(expandedPanel).dp),
        )
        else -> Text(
            text = if (isTv) "حرك بالاسهم واضغط OK مرة واحدة" else "المس الملف الشخصي للمتابعة",
            color = colors.textMuted.copy(alpha = .78f),
            fontSize = if (isTv) 12.sp else if (compactMobile) 10.sp else 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProfileEntryOptionsPanel(
    profiles: List<UserProfile>,
    isTv: Boolean,
    expandedPanel: Boolean,
    directEntryEnabled: Boolean,
    defaultProfileId: String?,
    panelHorizontalPaddingDp: Float,
    panelVerticalPaddingDp: Float,
    actionFloorDp: Int,
    onToggleDirectEntry: () -> Unit,
    onSelectDefaultProfile: (String?) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val firstFocusRequester = remember { FocusRequester() }
    val panelShape = RoundedCornerShape(if (isTv) 24.dp else 20.dp)

    LaunchedEffect(isTv) {
        if (!isTv) return@LaunchedEffect
        withFrameNanos { }
        runCatching { firstFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                horizontal = panelHorizontalPaddingDp.dp,
                vertical = panelVerticalPaddingDp.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val availableWidth = maxWidth
            Column(
                modifier = Modifier
                    .widthIn(
                        max = if (expandedPanel) {
                            minOf(availableWidth, 880.dp)
                        } else {
                            entryPanelMaxWidthDp(expandedPanel).dp
                        },
                    )
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .clip(panelShape)
                    .background(colors.surface.copy(alpha = .98f))
                    .border(1.dp, colors.gold.copy(alpha = .30f), panelShape)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (isTv) 24.dp else 18.dp,
                        vertical = if (isTv) 20.dp else 16.dp,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
                ) {
                    EntryBackAction(
                        onClick = onClose,
                        minHeightDp = actionFloorDp,
                    )
                    Text(
                        text = "خيارات الدخول",
                        color = colors.text,
                        fontSize = if (isTv) 26.sp else 22.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "حدد هل تريد اختيار المستخدم عند كل تشغيل او الدخول مباشرة",
                    color = colors.textMuted,
                    fontSize = if (isTv) 13.sp else 12.sp,
                    textAlign = if (expandedPanel) TextAlign.Start else TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(if (isTv) 18.dp else 14.dp))

                val body: @Composable ColumnScope.() -> Unit = {
                    EntryToggleRow(
                        text = "الدخول المباشر",
                        checked = directEntryEnabled,
                        isTv = isTv,
                        focusRequester = firstFocusRequester,
                        onToggle = onToggleDirectEntry,
                        modifier = if (expandedPanel) Modifier.fillMaxWidth() else Modifier,
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (directEntryEnabled) {
                            "عند تشغيل التطبيق سيتم تجاوز صفحة اختيار المستخدم"
                        } else {
                            "سيستمر ظهور صفحة اختيار المستخدم عند تشغيل التطبيق"
                        },
                        color = colors.textMuted,
                        fontSize = if (isTv) 12.sp else 11.sp,
                        textAlign = if (expandedPanel) TextAlign.Start else TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(if (isTv) 20.dp else 16.dp))
                    Text(
                        text = "المستخدم الافتراضي",
                        color = colors.text,
                        fontSize = if (isTv) 17.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = if (expandedPanel) TextAlign.Start else TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "اختر مستخدما محددا، او اختر اخر مستخدم للدخول باخر ملف استخدم",
                        color = colors.textMuted,
                        fontSize = if (isTv) 12.sp else 11.sp,
                        textAlign = if (expandedPanel) TextAlign.Start else TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(if (isTv) 12.dp else 10.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        EntryPreferenceRow(
                            text = "اخر مستخدم",
                            avatarKey = null,
                            selected = defaultProfileId == null,
                            onClick = { onSelectDefaultProfile(null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        profiles.forEach { profile ->
                            EntryPreferenceRow(
                                text = profile.displayName,
                                avatarKey = profile.avatarKey,
                                selected = defaultProfileId == profile.id,
                                onClick = { onSelectDefaultProfile(profile.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                }

                if (expandedPanel) {
                    val contextColumnWidth = (availableWidth * .32f).coerceIn(240.dp, 340.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Column(
                            modifier = Modifier.width(contextColumnWidth),
                        ) {
                            Text(
                                text = "الدخول الى التطبيق",
                                color = colors.text,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "حدد طريقة اختيار الملف عند تشغيل التطبيق",
                                color = colors.textMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(colors.line.copy(alpha = .35f)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            body()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        body()
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryToggleRow(
    text: String,
    checked: Boolean,
    isTv: Boolean,
    focusRequester: FocusRequester,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .focusRequester(focusRequester)
            .clip(shape)
            .background(if (focused) colors.gold.copy(alpha = .08f) else colors.surfaceRaised.copy(alpha = .55f))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) colors.goldBright else Color.White.copy(alpha = .08f),
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
                        KeyEventType.KeyUp -> {
                            onToggle()
                            true
                        }
                        else -> false
                    }
                }
            }
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (checked) colors.text else colors.textMuted,
            fontSize = if (isTv) 14.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        Spacer(Modifier.width(10.dp))
        EntrySwitchVisual(checked = checked)
    }
}

@Composable
private fun EntryPreferenceRow(
    text: String,
    avatarKey: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                when {
                    selected -> colors.gold.copy(alpha = .12f)
                    focused -> colors.gold.copy(alpha = .08f)
                    else -> colors.surfaceRaised.copy(alpha = .55f)
                },
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> colors.goldBright
                    selected -> colors.gold.copy(alpha = .55f)
                    else -> Color.White.copy(alpha = .08f)
                },
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val remoteSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!remoteSelect) {
                    false
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> true
                        KeyEventType.KeyUp -> {
                            if (!selected) onClick()
                            true
                        }
                        else -> false
                    }
                }
            }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (selected) colors.text else colors.textMuted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        if (avatarKey != null) {
            Spacer(Modifier.width(10.dp))
            ProfileAvatar(
                avatarKey = avatarKey,
                modifier = Modifier.size(40.dp),
                highlighted = selected,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.gold,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
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
    ProfilePickerItemFrame(
        isTv = isTv,
        enabled = enabled,
        cardWidthDp = cardWidthDp,
        avatarSizeDp = avatarSizeDp,
        focusBorderDp = focusBorderDp,
        focused = focused,
        active = false,
        focusRequester = null,
        onFocusChanged = { focused = it },
        onClick = onClick,
        avatar = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (focused) .08f else .04f)),
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
        },
        label = {
            Text(
                text = "اضافة ملف",
                color = if (focused) colors.text else colors.textMuted,
                fontSize = if (isTv) 17.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        },
        badge = {},
    )
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
    ProfilePickerItemFrame(
        isTv = isTv,
        enabled = enabled,
        cardWidthDp = cardWidthDp,
        avatarSizeDp = avatarSizeDp,
        focusBorderDp = focusBorderDp,
        focused = focused,
        active = isActive,
        focusRequester = focusRequester,
        onFocusChanged = { focused = it },
        onClick = onClick,
        avatar = {
            ProfileAvatar(
                avatarKey = profile.avatarKey,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                highlighted = false,
            )
        },
        label = {
            Text(
                text = profile.displayName,
                color = colors.text,
                fontSize = if (isTv) 17.sp else 15.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        badge = {
            when {
                isActive -> Box(
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

                profile.kind == ProfileKind.KIDS -> Text(
                    text = "اطفال",
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        },
    )
}

/**
 * One equal-footprint circular identity slot shared by profiles and Add. The avatar/add circle
 * keeps identical bounds and the label/badge slots are reserved for every item, so focus,
 * current/Kids state or the Add affordance can never change the item geometry.
 */
@Composable
private fun ProfilePickerItemFrame(
    isTv: Boolean,
    enabled: Boolean,
    cardWidthDp: Float,
    avatarSizeDp: Float,
    focusBorderDp: Float,
    focused: Boolean,
    active: Boolean,
    focusRequester: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    avatar: @Composable () -> Unit,
    label: @Composable () -> Unit,
    badge: @Composable () -> Unit,
) {
    val colors = LocalHulkColors.current
    val badgeSlot = if (isTv) 22.dp else 20.dp
    Column(
        modifier = Modifier
            .width(cardWidthDp.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .onPreviewKeyEvent { event ->
                val remoteSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                when {
                    !isTv || !remoteSelect -> false
                    !enabled -> true
                    event.type == KeyEventType.KeyDown -> true
                    event.type == KeyEventType.KeyUp -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = if (isTv) 8.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(avatarSizeDp.dp)
                .clip(CircleShape)
                .background(colors.surface.copy(alpha = .72f))
                .border(
                    width = when {
                        focused -> focusBorderDp.dp
                        active -> 2.dp
                        else -> 1.dp
                    },
                    color = when {
                        focused -> colors.goldBright
                        active -> colors.gold.copy(alpha = .65f)
                        else -> Color.White.copy(alpha = .12f)
                    },
                    shape = CircleShape,
                )
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            avatar()
        }
        Spacer(Modifier.height(if (isTv) 10.dp else 8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            label()
        }
        Spacer(Modifier.height(if (isTv) 8.dp else 6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = badgeSlot),
            contentAlignment = Alignment.Center,
        ) {
            badge()
        }
    }
}
