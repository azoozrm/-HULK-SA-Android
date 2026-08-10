package sa.hulksa.player.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.theme.LocalHulkColors

private val PROFILE_AVATARS = listOf("default", "gold", "dark", "classic", "kids")

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
    var avatarKey by remember { mutableStateOf(UserProfile.DEFAULT_AVATAR_KEY) }
    var error by remember { mutableStateOf<String?>(null) }

    fun openCreate() {
        editingProfile = null
        creating = true
        name = ""
        avatarKey = UserProfile.DEFAULT_AVATAR_KEY
        error = null
    }

    fun openEdit(profile: UserProfile) {
        editingProfile = profile
        creating = false
        name = profile.displayName
        avatarKey = profile.avatarKey
        error = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = if (isTv) 52.dp else 18.dp, vertical = if (isTv) 32.dp else 20.dp),
    ) {
        if (creating || editingProfile != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = if (isTv) 28.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (creating) "إضافة ملف شخصي" else "تعديل الملف الشخصي",
                    color = colors.text,
                    fontSize = if (isTv) 30.sp else 24.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(24.dp))
                HulkTextField(
                    value = name,
                    onValueChange = { name = it.take(ProfileStore.MAX_DISPLAY_NAME_LENGTH) },
                    label = "اسم الملف الشخصي",
                    modifier = Modifier.fillMaxWidth(if (isTv) .48f else 1f),
                )
                Spacer(Modifier.height(18.dp))
                Text("اختر الصورة", color = colors.textMuted, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PROFILE_AVATARS.forEach { key ->
                        AvatarChoice(
                            key = key,
                            selected = avatarKey == key,
                            onClick = { avatarKey = key },
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = colors.danger, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ActionButton("حفظ") {
                        val ok = if (creating) {
                            onCreate(name, avatarKey)
                        } else {
                            val profile = editingProfile ?: return@ActionButton
                            onUpdate(profile.id, name, avatarKey)
                        }
                        if (ok) {
                            creating = false
                            editingProfile = null
                            error = null
                        } else {
                            error = "تعذر الحفظ. تأكد من الاسم وعدد الملفات الشخصية."
                        }
                    }
                    ActionButton("إلغاء", secondary = true) {
                        creating = false
                        editingProfile = null
                        error = null
                    }
                }
            }
            return@Box
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "الملفات الشخصية",
                        color = colors.text,
                        fontSize = if (isTv) 30.sp else 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "أنشئ وعدّل واختر ملفك الشخصي",
                        color = colors.textMuted,
                        fontSize = if (isTv) 14.sp else 13.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (profiles.size < ProfileStore.MAX_PROFILES) {
                        ActionButton("إضافة ملف", onClick = ::openCreate)
                    }
                    ActionButton("رجوع", secondary = true, onClick = onClose)
                }
            }

            Spacer(Modifier.height(24.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(profiles, key = UserProfile::id) { profile ->
                    val active = profile.id == activeProfileId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(
                                1.dp,
                                if (active) colors.goldBright.copy(alpha = .65f) else Color.White.copy(alpha = .10f),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AvatarBubble(profile.displayName, profile.avatarKey, active)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.displayName, color = colors.text, fontWeight = FontWeight.Bold)
                            Text(
                                if (active) "الحالي" else if (profile.isPrimary) "الأساسي" else "ملف شخصي",
                                color = if (active) colors.goldBright else colors.textMuted,
                                fontSize = 12.sp,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!active) ActionButton("اختيار", secondary = true) { onSelect(profile) }
                            ActionButton("تعديل", secondary = true) { openEdit(profile) }
                            if (!profile.isPrimary && profiles.size > 1) {
                                ActionButton("حذف", danger = true) {
                                    if (!onDelete(profile.id)) error = "تعذر حذف الملف الشخصي."
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarChoice(key: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalHulkColors.current
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (selected) colors.goldDeep else colors.surfaceRaised)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.goldBright else Color.White.copy(.12f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(avatarGlyph(key), color = colors.text, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AvatarBubble(name: String, avatarKey: String, active: Boolean) {
    val colors = LocalHulkColors.current
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(if (active) colors.goldDeep else colors.surfaceRaised)
            .border(1.dp, if (active) colors.goldBright else Color.White.copy(.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (avatarKey == "default") name.trim().firstOrNull()?.toString().orEmpty().ifBlank { "H" } else avatarGlyph(avatarKey),
            color = colors.text,
            fontSize = 21.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

private fun avatarGlyph(key: String): String = when (key) {
    "gold" -> "★"
    "dark" -> "H"
    "classic" -> "◆"
    "kids" -> "☺"
    else -> "H"
}

@Composable
private fun ActionButton(
    text: String,
    secondary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val background = when {
        danger -> colors.danger.copy(alpha = .15f)
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
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, if (danger) colors.danger.copy(.5f) else Color.White.copy(.08f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
