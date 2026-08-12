package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun AdaptiveProfileManagementScreen(
    profiles: List<UserProfile>,
    activeProfileId: String,
    isTv: Boolean,
    startCreating: Boolean = false,
    protectedProfileIds: Set<String> = emptySet(),
    kidsSourceAvailable: Boolean,
    kidsSourceLoading: Boolean,
    kidsSourceMessage: String?,
    onRetryKidsSource: () -> Unit,
    onCreate: (name: String, avatarKey: String, kind: ProfileKind) -> Boolean,
    onUpdate: (profileId: String, name: String, avatarKey: String) -> Boolean,
    onDelete: (profileId: String) -> Boolean,
    onSelect: (UserProfile) -> Unit,
    onManagePin: (UserProfile) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var editingProfile by remember { mutableStateOf<UserProfile?>(null) }
    var creating by remember(startCreating) { mutableStateOf(startCreating) }
    var name by remember { mutableStateOf("") }
    var avatarKey by remember { mutableStateOf(PROFILE_AVATARS.first()) }
    var profileKind by remember { mutableStateOf(ProfileKind.STANDARD) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val activeFocusRequester = remember(activeProfileId) { FocusRequester() }

    fun normalizedAvatar(raw: String) = raw.takeIf { it in PROFILE_AVATARS } ?: PROFILE_AVATARS.first()

    fun openCreate() {
        editingProfile = null
        creating = true
        name = ""
        avatarKey = PROFILE_AVATARS.first()
        profileKind = ProfileKind.STANDARD
        error = null
    }

    fun openEdit(profile: UserProfile) {
        editingProfile = profile
        creating = false
        name = profile.displayName
        avatarKey = normalizedAvatar(profile.avatarKey)
        profileKind = profile.kind
        error = null
    }

    LaunchedEffect(isTv, activeProfileId, profiles, creating, editingProfile) {
        if (!isTv || creating || editingProfile != null) return@LaunchedEffect
        val index = profiles.indexOfFirst { it.id == activeProfileId }
        if (index < 0) return@LaunchedEffect
        delay(70L)
        listState.scrollToItem(index)
        delay(90L)
        runCatching { activeFocusRequester.requestFocus() }
    }

    BackHandler(enabled = creating || editingProfile != null) {
        creating = false
        editingProfile = null
        error = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(colors.goldDeep.copy(alpha = .10f), Color.Transparent),
                        radius = if (isTv) 1100f else 720f,
                    ),
                ),
        )

        if (creating || editingProfile != null) {
            AdaptiveProfileEditor(
                creating = creating,
                editingKind = editingProfile?.kind,
                name = name,
                avatarKey = avatarKey,
                selectedKind = profileKind,
                isTv = isTv,
                kidsSourceAvailable = kidsSourceAvailable,
                kidsSourceLoading = kidsSourceLoading,
                error = error,
                onNameChange = { name = it.take(ProfileStore.MAX_DISPLAY_NAME_LENGTH) },
                onAvatarChange = { avatarKey = it },
                onKindChange = { profileKind = it },
                onRetryKidsSource = onRetryKidsSource,
                onSave = {
                    val ok = if (creating) {
                        if (profileKind == ProfileKind.KIDS && !kidsSourceAvailable) {
                            false
                        } else {
                            onCreate(name, avatarKey, profileKind)
                        }
                    } else {
                        val profile = editingProfile ?: return@AdaptiveProfileEditor
                        onUpdate(profile.id, name, avatarKey)
                    }
                    if (ok) {
                        creating = false
                        editingProfile = null
                        error = null
                    } else {
                        error = if (profileKind == ProfileKind.KIDS && !kidsSourceAvailable) {
                            "وضع الأطفال غير متاح حاليًا. حاول مرة أخرى بعد قليل."
                        } else {
                            "تعذر الحفظ. تأكد من الاسم وعدد الملفات الشخصية."
                        }
                    }
                },
                onCancel = {
                    creating = false
                    editingProfile = null
                    error = null
                },
            )
            return@Box
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            val phone = !isTv && maxWidth < 600.dp
            val tablet = !isTv && maxWidth >= 600.dp
            val horizontal = when {
                isTv -> 48.dp
                phone -> 14.dp
                else -> 26.dp
            }
            val vertical = when {
                isTv -> 30.dp
                phone -> 16.dp
                else -> 22.dp
            }
            val maxContentWidth = when {
                isTv -> 1060.dp
                tablet -> 900.dp
                else -> 760.dp
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontal, vertical = vertical),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (phone) {
                    Column(Modifier.fillMaxWidth().widthIn(max = maxContentWidth)) {
                        ManagementHeading(isTv = false)
                        Spacer(Modifier.height(13.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            if (profiles.size < ProfileStore.MAX_PROFILES) {
                                ManagementAction("+ إضافة ملف", false, onClick = ::openCreate)
                            }
                            ManagementAction("رجوع", false, secondary = true, onClick = onClose)
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ManagementHeading(isTv = isTv)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (profiles.size < ProfileStore.MAX_PROFILES) {
                                ManagementAction("+ إضافة ملف", isTv, onClick = ::openCreate)
                            }
                            ManagementAction("رجوع", isTv, secondary = true, onClick = onClose)
                        }
                    }
                }

                Spacer(Modifier.height(if (isTv) 20.dp else 15.dp))

                if (
                    profiles.any { it.kind == ProfileKind.KIDS } &&
                    (kidsSourceLoading || !kidsSourceAvailable)
                ) {
                    KidsStatusNotice(
                        isTv = isTv,
                        loading = kidsSourceLoading,
                        onRetry = onRetryKidsSource,
                    )
                    Spacer(Modifier.height(if (isTv) 12.dp else 9.dp))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    verticalArrangement = Arrangement.spacedBy(if (isTv) 13.dp else 10.dp),
                ) {
                    items(profiles, key = UserProfile::id) { profile ->
                        ManagementProfileCard(
                            profile = profile,
                            active = profile.id == activeProfileId,
                            protected = profile.id in protectedProfileIds,
                            profileCount = profiles.size,
                            isTv = isTv,
                            focusRequester = if (profile.id == activeProfileId) activeFocusRequester else null,
                            onSelect = { onSelect(profile) },
                            onEdit = { openEdit(profile) },
                            onPin = { onManagePin(profile) },
                            onDelete = { if (!onDelete(profile.id)) error = "تعذر حذف الملف الشخصي." },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementHeading(isTv: Boolean) {
    val colors = LocalHulkColors.current
    Column {
        Text(
            "الملفات الشخصية",
            color = colors.text,
            fontSize = if (isTv) 31.sp else 25.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "أنشئ ملفًا مناسبًا لكل فرد من العائلة",
            color = colors.textMuted,
            fontSize = if (isTv) 14.sp else 12.sp,
        )
    }
}

@Composable
private fun KidsStatusNotice(
    isTv: Boolean,
    loading: Boolean,
    onRetry: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(if (isTv) 15.dp else 13.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised.copy(alpha = .88f))
            .border(1.dp, Color.White.copy(alpha = .08f), shape)
            .padding(horizontal = if (isTv) 17.dp else 13.dp, vertical = if (isTv) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (loading) "جار تجهيز وضع الأطفال…" else "وضع الأطفال غير متاح حاليًا",
            modifier = Modifier.weight(1f),
            color = colors.textMuted,
            fontSize = if (isTv) 13.sp else 11.sp,
            fontWeight = FontWeight.Medium,
        )
        if (!loading) {
            Spacer(Modifier.width(10.dp))
            ManagementAction("إعادة المحاولة", isTv, secondary = true, compact = true, onClick = onRetry)
        }
    }
}

@Composable
private fun AdaptiveProfileEditor(
    creating: Boolean,
    editingKind: ProfileKind?,
    name: String,
    avatarKey: String,
    selectedKind: ProfileKind,
    isTv: Boolean,
    kidsSourceAvailable: Boolean,
    kidsSourceLoading: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onAvatarChange: (String) -> Unit,
    onKindChange: (ProfileKind) -> Unit,
    onRetryKidsSource: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val nameFocus = remember(creating) { FocusRequester() }

    LaunchedEffect(isTv, creating) {
        if (!isTv) return@LaunchedEffect
        delay(120L)
        runCatching { nameFocus.requestFocus() }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = if (isTv) 48.dp else 14.dp, vertical = if (isTv) 28.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val phoneLandscape = !isTv && maxWidth > maxHeight && maxHeight < 520.dp
        val compactPhone = !isTv && maxWidth < 600.dp
        val cardMax = when {
            isTv -> 720.dp
            compactPhone -> 620.dp
            else -> 700.dp
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = cardMax)
                .clip(RoundedCornerShape(if (isTv) 24.dp else 20.dp))
                .background(colors.surface.copy(alpha = .98f))
                .border(1.dp, colors.gold.copy(alpha = .26f), RoundedCornerShape(if (isTv) 24.dp else 20.dp))
                .padding(
                    horizontal = if (isTv) 34.dp else 20.dp,
                    vertical = if (phoneLandscape) 14.dp else if (isTv) 25.dp else 20.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    if (creating) "إضافة ملف شخصي" else "تعديل الملف الشخصي",
                    color = colors.text,
                    fontSize = if (isTv) 29.sp else 23.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (creating) "اختر نوع الملف ثم الاسم والصورة" else "يمكنك تعديل الاسم والصورة",
                    color = colors.textMuted,
                    fontSize = if (isTv) 13.sp else 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(if (phoneLandscape) 10.dp else 17.dp))

                if (creating) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProfileKindChoice(
                            modifier = Modifier.weight(1f),
                            title = "عادي",
                            subtitle = "التجربة الكاملة",
                            selected = selectedKind == ProfileKind.STANDARD,
                            enabled = true,
                            isTv = isTv,
                            onClick = { onKindChange(ProfileKind.STANDARD) },
                        )
                        ProfileKindChoice(
                            modifier = Modifier.weight(1f),
                            title = "أطفال",
                            subtitle = when {
                                kidsSourceLoading -> "جار التجهيز…"
                                kidsSourceAvailable -> "واجهة مخصصة للأطفال"
                                else -> "غير متاح حاليًا"
                            },
                            selected = selectedKind == ProfileKind.KIDS,
                            enabled = kidsSourceAvailable,
                            isTv = isTv,
                            onClick = { onKindChange(ProfileKind.KIDS) },
                        )
                    }
                    if (!kidsSourceAvailable) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                if (kidsSourceLoading) "جار تجهيز وضع الأطفال…" else "وضع الأطفال غير متاح حاليًا.",
                                modifier = Modifier.weight(1f),
                                color = colors.textMuted,
                                fontSize = if (isTv) 12.sp else 10.sp,
                            )
                            if (!kidsSourceLoading) {
                                Spacer(Modifier.width(8.dp))
                                ManagementAction("إعادة المحاولة", isTv, secondary = true, compact = true, onClick = onRetryKidsSource)
                            }
                        }
                    }
                } else {
                    val kind = editingKind ?: ProfileKind.STANDARD
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (kind == ProfileKind.KIDS) colors.gold.copy(alpha = .14f) else colors.surfaceRaised)
                            .border(
                                1.dp,
                                if (kind == ProfileKind.KIDS) colors.gold.copy(alpha = .34f) else Color.White.copy(alpha = .09f),
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            if (kind == ProfileKind.KIDS) "ملف أطفال" else "ملف عادي",
                            color = if (kind == ProfileKind.KIDS) colors.goldBright else colors.textMuted,
                            fontSize = if (isTv) 13.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(if (phoneLandscape) 10.dp else if (isTv) 19.dp else 15.dp))
                HulkTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = "اسم الملف الشخصي",
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )
                Spacer(Modifier.height(if (phoneLandscape) 10.dp else if (isTv) 18.dp else 15.dp))
                Text(
                    "اختر صورة الملف",
                    color = colors.text,
                    fontSize = if (isTv) 16.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 13.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PROFILE_AVATARS.forEach { key ->
                        ManagementAvatarChoice(
                            key = key,
                            selected = avatarKey == key,
                            isTv = isTv,
                            compact = phoneLandscape,
                            onClick = { onAvatarChange(key) },
                        )
                    }
                }

                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        error,
                        color = colors.danger,
                        fontSize = if (isTv) 13.sp else 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(if (phoneLandscape) 12.dp else if (isTv) 20.dp else 18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ManagementAction("حفظ", isTv, onClick = onSave)
                    ManagementAction("إلغاء", isTv, secondary = true, onClick = onCancel)
                }
            }
        }
    }
}

@Composable
private fun ProfileKindChoice(
    modifier: Modifier,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    isTv: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(title) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && isTv) 1.025f else 1f, label = "kindScale")
    val shape = RoundedCornerShape(if (isTv) 16.dp else 13.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) colors.gold.copy(alpha = .14f) else colors.surfaceRaised)
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> colors.goldBright
                    selected -> colors.gold.copy(alpha = .50f)
                    else -> Color.White.copy(alpha = .09f)
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled)
            .padding(horizontal = if (isTv) 17.dp else 13.dp, vertical = if (isTv) 13.dp else 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            color = if (!enabled) colors.textMuted.copy(alpha = .45f) else if (selected || focused) colors.goldBright else colors.text,
            fontWeight = FontWeight.Black,
            fontSize = if (isTv) 16.sp else 14.sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            color = colors.textMuted.copy(alpha = if (enabled) 1f else .45f),
            fontSize = if (isTv) 11.sp else 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun ManagementProfileCard(
    profile: UserProfile,
    active: Boolean,
    protected: Boolean,
    profileCount: Int,
    isTv: Boolean,
    focusRequester: FocusRequester?,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(if (isTv) 19.dp else 16.dp)
    val canDelete = !profile.isPrimary && profileCount > 1

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = !isTv && maxWidth < 700.dp
        val card = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) colors.gold.copy(alpha = .07f) else colors.surface.copy(alpha = .97f))
            .border(1.dp, if (active) colors.gold.copy(alpha = .46f) else Color.White.copy(alpha = .09f), shape)
            .padding(horizontal = if (isTv) 19.dp else 14.dp, vertical = if (isTv) 14.dp else 13.dp)

        if (compact) {
            Column(card) {
                ManagementIdentity(profile, active, protected, false)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                ) {
                    if (!active) ManagementAction("اختيار", false, secondary = true, compact = true, onClick = onSelect)
                    ManagementAction(
                        if (protected) "PIN مفعّل" else "حماية",
                        false,
                        secondary = true,
                        compact = true,
                        focusRequester = focusRequester,
                        onClick = onPin,
                    )
                    ManagementAction("تعديل", false, secondary = true, compact = true, onClick = onEdit)
                    if (canDelete) ManagementAction("حذف", false, danger = true, compact = true, onClick = onDelete)
                }
            }
        } else {
            Row(card, verticalAlignment = Alignment.CenterVertically) {
                ManagementIdentity(profile, active, protected, isTv, Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!active) ManagementAction("اختيار", isTv, secondary = true, onClick = onSelect)
                    ManagementAction(
                        if (protected) "PIN مفعّل" else "حماية",
                        isTv,
                        secondary = true,
                        focusRequester = focusRequester,
                        onClick = onPin,
                    )
                    ManagementAction("تعديل", isTv, secondary = true, onClick = onEdit)
                    if (canDelete) ManagementAction("حذف", isTv, danger = true, onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun ManagementIdentity(
    profile: UserProfile,
    active: Boolean,
    protected: Boolean,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatarArtwork(
            avatarKey = profile.avatarKey,
            displayName = profile.displayName,
            size = if (isTv) 64.dp else 56.dp,
            highlighted = active || profile.kind == ProfileKind.KIDS,
        )
        Spacer(Modifier.width(if (isTv) 17.dp else 12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.displayName,
                    color = colors.text,
                    fontSize = if (isTv) 17.sp else 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.kind == ProfileKind.KIDS) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.gold.copy(alpha = .14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "أطفال",
                            color = colors.goldBright,
                            fontSize = if (isTv) 10.sp else 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val status = buildString {
                append(
                    when {
                        active -> "الملف المستخدم الآن"
                        profile.isPrimary -> "الملف الأساسي"
                        profile.kind == ProfileKind.KIDS -> "ملف أطفال"
                        else -> "ملف شخصي مستقل"
                    },
                )
                if (protected) append(" · محمي برمز PIN")
            }
            Text(
                status,
                color = if (active || protected || profile.kind == ProfileKind.KIDS) colors.goldBright else colors.textMuted,
                fontSize = if (isTv) 12.sp else 11.sp,
                fontWeight = if (active || protected || profile.kind == ProfileKind.KIDS) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ManagementAvatarChoice(
    key: String,
    selected: Boolean,
    isTv: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember(key) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && isTv) 1.08f else 1f, label = "managementAvatarScale")
    val size = when {
        compact -> 44.dp
        isTv -> 68.dp
        else -> 54.dp
    }
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val select = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!isTv || !select) false else when (event.type) {
                    KeyEventType.KeyDown -> true
                    KeyEventType.KeyUp -> { onClick(); true }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        ProfileAvatarArtwork(key, "", size, focused || selected)
    }
}

@Composable
private fun ManagementAction(
    text: String,
    isTv: Boolean,
    secondary: Boolean = false,
    danger: Boolean = false,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && isTv) 1.03f else 1f, label = "managementActionScale")
    val shape = RoundedCornerShape(if (isTv) 13.dp else 11.dp)
    val background = when {
        danger -> colors.danger.copy(alpha = if (focused) .24f else .14f)
        secondary && focused -> colors.gold.copy(alpha = .16f)
        secondary -> colors.surfaceRaised
        else -> colors.gold
    }
    val foreground = when {
        danger -> colors.danger
        secondary -> colors.text
        else -> Color.Black
    }
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(background)
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> colors.goldBright
                    danger -> colors.danger.copy(alpha = .42f)
                    secondary -> Color.White.copy(alpha = .08f)
                    else -> colors.gold.copy(alpha = .35f)
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val select = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!isTv || !select) false else when (event.type) {
                    KeyEventType.KeyDown -> true
                    KeyEventType.KeyUp -> { onClick(); true }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .focusable()
            .padding(
                horizontal = when { compact -> 9.dp; isTv -> 16.dp; else -> 14.dp },
                vertical = when { compact -> 8.dp; isTv -> 10.dp; else -> 9.dp },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = foreground,
            fontWeight = FontWeight.Bold,
            fontSize = when { compact -> 10.sp; isTv -> 13.sp; else -> 12.sp },
            maxLines = 1,
        )
    }
}
