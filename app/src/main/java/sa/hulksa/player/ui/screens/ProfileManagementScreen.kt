package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun ProfileManagementScreen(
    profiles: List<UserProfile>,
    activeProfileId: String,
    isTv: Boolean,
    onCreate: (name: String, avatarKey: String) -> Boolean,
    onUpdate: (profileId: String, name: String, avatarKey: String) -> Boolean,
    onDelete: (profileId: String) -> Boolean,
    onSelect: (UserProfile) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var editingProfile by remember { mutableStateOf<UserProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var avatarKey by remember { mutableStateOf(PROFILE_AVATARS.first()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun normalizedAvatarKey(raw: String): String =
        raw.takeIf { it in PROFILE_AVATARS } ?: PROFILE_AVATARS.first()

    fun openCreate() {
        editingProfile = null
        creating = true
        name = ""
        avatarKey = PROFILE_AVATARS.first()
        error = null
    }

    fun openEdit(profile: UserProfile) {
        editingProfile = profile
        creating = false
        name = profile.displayName
        avatarKey = normalizedAvatarKey(profile.avatarKey)
        error = null
    }

    BackHandler(enabled = creating || editingProfile != null) {
        creating = false
        editingProfile = null
        error = null
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
                            colors.goldDeep.copy(alpha = .10f),
                            Color.Transparent,
                        ),
                        radius = if (isTv) 1050f else 650f,
                    ),
                ),
        )

        if (creating || editingProfile != null) {
            ProfileEditor(
                creating = creating,
                editingProfile = editingProfile,
                name = name,
                avatarKey = avatarKey,
                error = error,
                isTv = isTv,
                onNameChange = { name = it.take(ProfileStore.MAX_DISPLAY_NAME_LENGTH) },
                onAvatarChange = { avatarKey = it },
                onSave = {
                    val ok = if (creating) {
                        onCreate(name, avatarKey)
                    } else {
                        val profile = editingProfile ?: return@ProfileEditor
                        onUpdate(profile.id, name, avatarKey)
                    }
                    if (ok) {
                        creating = false
                        editingProfile = null
                        error = null
                    } else {
                        error = "تعذر الحفظ. تأكد من الاسم وعدد الملفات الشخصية."
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isTv) 58.dp else 18.dp,
                    vertical = if (isTv) 38.dp else 20.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (isTv) 1120.dp else 760.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "الملفات الشخصية",
                        color = colors.text,
                        fontSize = if (isTv) 32.sp else 25.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "أنشئ وعدّل واختر الملف المناسب لكل مشاهدة",
                        color = colors.textMuted,
                        fontSize = if (isTv) 14.sp else 13.sp,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (profiles.size < ProfileStore.MAX_PROFILES) {
                        ActionButton(
                            text = "+ إضافة ملف",
                            isTv = isTv,
                            onClick = ::openCreate,
                        )
                    }
                    ActionButton(
                        text = "رجوع",
                        secondary = true,
                        isTv = isTv,
                        onClick = onClose,
                    )
                }
            }

            Spacer(Modifier.height(if (isTv) 28.dp else 20.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (isTv) 1120.dp else 760.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
            ) {
                items(profiles, key = UserProfile::id) { profile ->
                    val active = profile.id == activeProfileId
                    ProfileManagementCard(
                        profile = profile,
                        active = active,
                        profilesCount = profiles.size,
                        isTv = isTv,
                        onSelect = { onSelect(profile) },
                        onEdit = { openEdit(profile) },
                        onDelete = {
                            if (!onDelete(profile.id)) {
                                error = "تعذر حذف الملف الشخصي."
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileEditor(
    creating: Boolean,
    editingProfile: UserProfile?,
    name: String,
    avatarKey: String,
    error: String?,
    isTv: Boolean,
    onNameChange: (String) -> Unit,
    onAvatarChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalHulkColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isTv) 64.dp else 18.dp,
                vertical = if (isTv) 42.dp else 22.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = if (isTv) 760.dp else 620.dp)
                .clip(RoundedCornerShape(if (isTv) 26.dp else 20.dp))
                .background(colors.surface.copy(alpha = .97f))
                .border(
                    1.dp,
                    colors.gold.copy(alpha = .32f),
                    RoundedCornerShape(if (isTv) 26.dp else 20.dp),
                )
                .padding(
                    horizontal = if (isTv) 42.dp else 22.dp,
                    vertical = if (isTv) 34.dp else 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (creating) "إضافة ملف شخصي" else "تعديل الملف الشخصي",
                color = colors.text,
                fontSize = if (isTv) 31.sp else 24.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (creating) {
                    "أنشئ مساحة مشاهدة مستقلة للمفضلة وسجل المشاهدة"
                } else {
                    "حدّث اسم الملف أو مظهره بدون فقدان بياناته"
                },
                color = colors.textMuted,
                fontSize = if (isTv) 14.sp else 12.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(if (isTv) 28.dp else 20.dp))

            HulkTextField(
                value = name,
                onValueChange = onNameChange,
                label = "اسم الملف الشخصي",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(if (isTv) 24.dp else 18.dp))

            Text(
                text = "اختر شخصية الملف",
                color = colors.text,
                fontSize = if (isTv) 16.sp else 14.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PROFILE_AVATARS.forEach { key ->
                    AvatarChoice(
                        key = key,
                        selected = avatarKey == key,
                        isTv = isTv,
                        onClick = { onAvatarChange(key) },
                    )
                }
            }

            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error,
                    color = colors.danger,
                    fontSize = if (isTv) 13.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(if (isTv) 28.dp else 22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = "حفظ",
                    isTv = isTv,
                    onClick = onSave,
                )
                ActionButton(
                    text = "إلغاء",
                    secondary = true,
                    isTv = isTv,
                    onClick = onCancel,
                )
            }
        }
    }
}

@Composable
private fun ProfileManagementCard(
    profile: UserProfile,
    active: Boolean,
    profilesCount: Int,
    isTv: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalHulkColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isTv) 20.dp else 16.dp))
            .background(if (active) colors.gold.copy(alpha = .08f) else colors.surface.copy(alpha = .96f))
            .border(
                1.dp,
                if (active) colors.gold.copy(alpha = .52f) else Color.White.copy(alpha = .09f),
                RoundedCornerShape(if (isTv) 20.dp else 16.dp),
            )
            .padding(
                horizontal = if (isTv) 20.dp else 14.dp,
                vertical = if (isTv) 16.dp else 13.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatarArtwork(
            avatarKey = profile.avatarKey,
            displayName = profile.displayName,
            size = if (isTv) 66.dp else 56.dp,
            highlighted = active,
        )

        Spacer(Modifier.width(if (isTv) 18.dp else 12.dp))

        Column(
            modifier = Modifier.widthIn(min = if (isTv) 230.dp else 130.dp),
        ) {
            Text(
                text = profile.displayName,
                color = colors.text,
                fontSize = if (isTv) 17.sp else 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    active -> "الملف المستخدم الآن"
                    profile.isPrimary -> "الملف الأساسي"
                    else -> "ملف شخصي مستقل"
                },
                color = if (active) colors.goldBright else colors.textMuted,
                fontSize = if (isTv) 12.sp else 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!active) {
                ActionButton(
                    text = "اختيار",
                    secondary = true,
                    isTv = isTv,
                    onClick = onSelect,
                )
            }
            ActionButton(
                text = "تعديل",
                secondary = true,
                isTv = isTv,
                onClick = onEdit,
            )
            if (!profile.isPrimary && profilesCount > 1) {
                ActionButton(
                    text = "حذف",
                    danger = true,
                    isTv = isTv,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun AvatarChoice(
    key: String,
    selected: Boolean,
    isTv: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember(key) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.10f else 1f,
        label = "profileAvatarScale",
    )
    val size = if (isTv) 72.dp else 58.dp

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ProfileAvatarArtwork(
            avatarKey = key,
            displayName = "",
            size = size,
            highlighted = focused || selected,
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    secondary: Boolean = false,
    danger: Boolean = false,
    isTv: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.04f else 1f,
        label = "profileActionScale",
    )
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
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
                horizontal = if (isTv) 17.dp else 14.dp,
                vertical = if (isTv) 11.dp else 9.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = foreground,
            fontWeight = FontWeight.Bold,
            fontSize = if (isTv) 13.sp else 12.sp,
            maxLines = 1,
        )
    }
}
