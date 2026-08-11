package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
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
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.components.ProfileAvatar
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun ProfilePickerScreen(
    profiles: List<UserProfile>,
    activeProfileId: String,
    isTv: Boolean,
    isSwitching: Boolean,
    errorMessage: String?,
    onSelectProfile: (UserProfile) -> Unit,
    onManageProfiles: () -> Unit = {},
) {
    val colors = LocalHulkColors.current
    val context = LocalContext.current
    val profilePreferencesStore = remember(context) { ProfilePreferencesStore(context) }
    var routingPreferences by remember(context) {
        mutableStateOf(profilePreferencesStore.routing())
    }
    var showEntryOptions by remember { mutableStateOf(false) }
    val profileIds = remember(profiles) { profiles.map(UserProfile::id) }
    val focusRequesters = remember(profileIds) {
        profiles.associate { it.id to FocusRequester() }
    }

    LaunchedEffect(profiles) {
        routingPreferences = profilePreferencesStore.routing()
    }

    LaunchedEffect(isTv, activeProfileId, profileIds, showEntryOptions) {
        if (!isTv || profiles.isEmpty() || showEntryOptions) return@LaunchedEffect
        delay(140L)
        val requester = focusRequesters[activeProfileId] ?: focusRequesters[profiles.first().id]
        requester?.let { runCatching { it.requestFocus() } }
    }

    BackHandler(enabled = showEntryOptions) {
        showEntryOptions = false
    }

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
                        colors = listOf(
                            colors.goldDeep.copy(alpha = .12f),
                            Color.Transparent,
                        ),
                        radius = if (isTv) 980f else 620f,
                    ),
                ),
        )

        if (showEntryOptions) {
            ProfileEntryOptionsPanel(
                profiles = profiles,
                isTv = isTv,
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isTv) 54.dp else 18.dp,
                    vertical = if (isTv) 30.dp else 20.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.hulk_sa_logo),
                contentDescription = "HULK SA",
                modifier = Modifier
                    .width(if (isTv) 126.dp else 104.dp)
                    .height(if (isTv) 74.dp else 62.dp),
                contentScale = ContentScale.Fit,
            )

            Spacer(Modifier.height(if (isTv) 14.dp else 10.dp))

            Text(
                text = "من يشاهد الآن؟",
                color = colors.text,
                fontSize = if (isTv) 32.sp else 26.sp,
                lineHeight = if (isTv) 39.sp else 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "اختر ملفك الشخصي للمتابعة",
                color = colors.textMuted,
                fontSize = if (isTv) 15.sp else 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(if (isTv) 28.dp else 22.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = if (isTv) 42.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    space = if (isTv) 24.dp else 14.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(
                    items = profiles,
                    key = UserProfile::id,
                ) { profile ->
                    ProfilePickerCard(
                        profile = profile,
                        isActive = profile.id == activeProfileId,
                        isTv = isTv,
                        enabled = !isSwitching,
                        focusRequester = focusRequesters.getValue(profile.id),
                        onClick = { onSelectProfile(profile) },
                    )
                }
            }

            Spacer(Modifier.height(if (isTv) 26.dp else 18.dp))

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

            Spacer(Modifier.height(if (isTv) 14.dp else 10.dp))

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
                    fontSize = if (isTv) 12.sp else 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ProfileEntryOptionsPanel(
    profiles: List<UserProfile>,
    isTv: Boolean,
    directEntryEnabled: Boolean,
    defaultProfileId: String?,
    onToggleDirectEntry: () -> Unit,
    onSelectDefaultProfile: (String?) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val firstFocusRequester = remember { FocusRequester() }
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
            .padding(
                horizontal = if (isTv) 64.dp else 20.dp,
                vertical = if (isTv) 40.dp else 24.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "خيارات الدخول",
            color = colors.text,
            fontSize = if (isTv) 32.sp else 25.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "حدد هل تريد اختيار المستخدم عند كل تشغيل أو الدخول مباشرة",
            color = colors.textMuted,
            fontSize = if (isTv) 14.sp else 12.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(if (isTv) 24.dp else 18.dp))

        ProfilePreferenceButton(
            text = if (directEntryEnabled) "الدخول المباشر: مفعّل" else "الدخول المباشر: متوقف",
            selected = directEntryEnabled,
            isTv = isTv,
            focusRequester = firstFocusRequester,
            onClick = onToggleDirectEntry,
        )

        Spacer(Modifier.height(10.dp))

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

        Spacer(Modifier.height(if (isTv) 26.dp else 20.dp))

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

        Spacer(Modifier.height(if (isTv) 16.dp else 12.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = if (isTv) 32.dp else 4.dp),
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
            items(
                items = profiles,
                key = UserProfile::id,
            ) { profile ->
                ProfilePreferenceButton(
                    text = profile.displayName,
                    selected = defaultProfileId == profile.id,
                    isTv = isTv,
                    onClick = { onSelectDefaultProfile(profile.id) },
                )
            }
        }

        Spacer(Modifier.height(if (isTv) 28.dp else 22.dp))

        ProfileFooterButton(
            text = "رجوع",
            isTv = isTv,
            enabled = true,
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
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.05f else 1f,
        label = "profilePreferenceButtonScale",
    )
    val shape = RoundedCornerShape(if (isTv) 14.dp else 12.dp)

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused && isTv) 14.dp.toPx() else 0f
            }
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier,
            )
            .clip(shape)
            .background(
                when {
                    selected -> colors.gold
                    focused -> colors.gold.copy(alpha = .18f)
                    else -> colors.surfaceRaised
                },
            )
            .border(
                if (focused) 2.dp else 1.dp,
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
                        KeyEventType.KeyUp -> {
                            onClick()
                            true
                        }
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
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.06f else 1f,
        label = "profileFooterButtonScale",
    )
    val shape = RoundedCornerShape(if (isTv) 15.dp else 13.dp)

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused && isTv) 16.dp.toPx() else 0f
            }
            .clip(shape)
            .background(
                if (focused) colors.gold.copy(alpha = .22f)
                else colors.surfaceRaised,
            )
            .border(
                if (focused) 2.5.dp else 1.dp,
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
                        KeyEventType.KeyUp -> {
                            onClick()
                            true
                        }
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
private fun ProfilePickerCard(
    profile: UserProfile,
    isActive: Boolean,
    isTv: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(profile.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.055f else 1f,
        label = "profilePickerScale",
    )
    val cardWidth = if (isTv) 180.dp else 140.dp
    val avatarSize = if (isTv) 98.dp else 76.dp
    val shape = RoundedCornerShape(if (isTv) 22.dp else 18.dp)

    Column(
        modifier = Modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused && isTv) 22.dp.toPx() else 4.dp.toPx()
            }
            .clip(shape)
            .background(
                when {
                    focused -> colors.gold.copy(alpha = .12f)
                    isActive -> colors.surfaceRaised
                    else -> colors.surface.copy(alpha = .94f)
                },
            )
            .border(
                width = when {
                    focused -> 3.dp
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
                        KeyEventType.KeyUp -> {
                            onClick()
                            true
                        }
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
            modifier = Modifier.size(avatarSize),
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
