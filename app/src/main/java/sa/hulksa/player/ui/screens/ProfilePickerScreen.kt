package sa.hulksa.player.ui.screens

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
        delay(140L)
        val requester = focusRequesters[activeProfileId] ?: focusRequesters[profiles.first().id]
        requester?.let { runCatching { it.requestFocus() } }
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

            FocusButton(
                text = "إدارة الملفات الشخصية",
                onClick = onManageProfiles,
                primary = false,
                compact = true,
                enabled = !isSwitching,
            )

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
    val initial = profile.displayName.trim().firstOrNull()?.uppercase() ?: "H"

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
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.goldDeep.copy(alpha = .94f),
                            colors.gold.copy(alpha = .66f),
                        ),
                    ),
                )
                .border(
                    1.5.dp,
                    if (focused || isActive) colors.goldBright.copy(alpha = .80f) else Color.White.copy(alpha = .16f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontSize = if (isTv) 37.sp else 28.sp,
                fontWeight = FontWeight.Black,
            )
        }

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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
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
            } else {
                Text(
                    text = if (profile.kind == ProfileKind.KIDS) "ملف أطفال" else "ملف شخصي",
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
