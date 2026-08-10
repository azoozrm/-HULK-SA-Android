package sa.hulksa.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sa.hulksa.player.R
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.components.FocusButton
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
    val profileIds = remember(profiles) { profiles.map(UserProfile::id) }
    val focusRequesters = remember(profileIds) {
        profiles.associate { it.id to FocusRequester() }
    }

    LaunchedEffect(isTv, activeProfileId, profileIds) {
        if (!isTv || profiles.isEmpty()) return@LaunchedEffect
        delay(180L)
        val requester = focusRequesters[activeProfileId] ?: focusRequesters[profiles.first().id]
        requester?.let { runCatching { it.requestFocus() } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to colors.background,
                    .55f to Color(0xFF080904),
                    1f to colors.background,
                ),
            )
            .padding(
                horizontal = if (isTv) 54.dp else 20.dp,
                vertical = if (isTv) 34.dp else 24.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.hulk_sa_logo),
                contentDescription = "HULK SA",
                modifier = Modifier
                    .width(if (isTv) 138.dp else 112.dp)
                    .height(if (isTv) 82.dp else 66.dp),
                contentScale = ContentScale.Fit,
            )

            Spacer(Modifier.height(if (isTv) 18.dp else 14.dp))

            Text(
                text = "من يشاهد الآن؟",
                color = colors.text,
                fontSize = if (isTv) 31.sp else 25.sp,
                lineHeight = if (isTv) 38.sp else 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(7.dp))

            Text(
                text = "اختر ملفك الشخصي للمتابعة",
                color = colors.textMuted,
                fontSize = if (isTv) 16.sp else 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(if (isTv) 30.dp else 24.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = if (isTv) 42.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    if (isTv) 24.dp else 14.dp,
                    Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(
                    items = profiles,
                    key = { _, profile -> profile.id },
                ) { _, profile ->
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

            Spacer(Modifier.height(if (isTv) 20.dp else 16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FocusButton(
                    text = "إدارة الملفات الشخصية",
                    onClick = onManageProfiles,
                    primary = false,
                    compact = true,
                    enabled = !isSwitching,
                )
            }

            Spacer(Modifier.height(if (isTv) 16.dp else 12.dp))

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
                    text = if (isTv) "استخدم الأسهم ثم زر الاختيار" else "المس الملف الشخصي للمتابعة",
                    color = colors.textMuted,
                    fontSize = if (isTv) 13.sp else 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
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
        targetValue = if (focused && isTv) 1.06f else 1f,
        label = "profilePickerScale",
    )
    val cardWidth = if (isTv) 172.dp else 136.dp
    val avatarSize = if (isTv) 102.dp else 78.dp
    val shape = RoundedCornerShape(if (isTv) 18.dp else 15.dp)
    val initial = profile.displayName.trim().firstOrNull()?.toString().orEmpty().ifBlank { "H" }

    Column(
        modifier = Modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused && isTv) 18.dp.toPx() else 0f
            }
            .clip(shape)
            .background(if (focused) colors.surfaceRaised else colors.surface)
            .border(
                width = when {
                    focused -> 3.dp
                    isActive -> 1.5.dp
                    else -> 1.dp
                },
                color = when {
                    focused -> colors.goldBright
                    isActive -> colors.gold.copy(alpha = .72f)
                    else -> Color.White.copy(alpha = .10f)
                },
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (isTv) 15.dp else 12.dp,
                vertical = if (isTv) 17.dp else 14.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.goldDeep.copy(alpha = .88f),
                            colors.surfaceRaised,
                        ),
                    ),
                )
                .border(
                    1.5.dp,
                    if (focused || isActive) colors.goldBright.copy(alpha = .72f) else Color.White.copy(alpha = .14f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                color = colors.text,
                fontSize = if (isTv) 38.sp else 29.sp,
                fontWeight = FontWeight.Black,
            )
        }

        Spacer(Modifier.height(if (isTv) 14.dp else 11.dp))

        Text(
            text = profile.displayName,
            color = colors.text,
            fontSize = if (isTv) 17.sp else 15.sp,
            lineHeight = if (isTv) 21.sp else 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = when {
                profile.kind == ProfileKind.KIDS -> "أطفال"
                isActive -> "الحالي"
                else -> "ملف شخصي"
            },
            color = if (isActive) colors.goldBright else colors.textMuted,
            fontSize = if (isTv) 11.sp else 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
