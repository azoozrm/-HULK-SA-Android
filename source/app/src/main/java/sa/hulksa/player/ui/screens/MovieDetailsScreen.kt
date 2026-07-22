package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.InfoPill
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun MovieDetailsScreen(
    item: ContentItem,
    details: ContentDetails?,
    isLoading: Boolean,
    errorMessage: String?,
    isTv: Boolean,
    isFavorite: Boolean,
    download: OfflineDownload?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val backdrop = details?.backdropUrl ?: item.backdropUrl ?: item.posterUrl
    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(
                model = backdrop,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            BrandLogo(Modifier.align(Alignment.Center).size(230.dp).graphicsLayer { alpha = .22f })
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .22f),
                    .53f to Color.Black.copy(alpha = .16f),
                    1f to colors.background,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .24f), colors.background.copy(alpha = .96f)),
                ),
            ),
        )

        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(if (isTv) 26.dp else 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandBadge(Modifier.size(if (isTv) 64.dp else 49.dp))
            Spacer(Modifier.weight(1f))
            FocusButton("رجوع", onBack, primary = false, compact = true)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (isTv) .62f else .9f)
                .padding(start = if (isTv) 34.dp else 17.dp, end = if (isTv) 34.dp else 17.dp, bottom = if (isTv) 42.dp else 24.dp),
        ) {
            Text("فيلم", color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                text = item.name,
                color = Color.White,
                fontSize = if (isTv) 43.sp else 29.sp,
                lineHeight = if (isTv) 51.sp else 35.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item.year?.let { InfoPill(it) }
                item.rating?.let { InfoPill("★ $it") }
                details?.duration?.let { InfoPill(it) }
                (details?.genre ?: item.genre)?.takeIf(String::isNotBlank)?.let { InfoPill(it.take(30)) }
            }
            val plot = details?.plot ?: item.plot
            if (!plot.isNullOrBlank()) {
                Spacer(Modifier.height(15.dp))
                Text(
                    text = plot,
                    color = Color(0xFFD8D4C9),
                    fontSize = if (isTv) 14.sp else 12.sp,
                    lineHeight = if (isTv) 23.sp else 19.sp,
                    maxLines = if (isTv) 4 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!details?.director.isNullOrBlank()) {
                Spacer(Modifier.height(9.dp))
                Text("الإخراج  ${details?.director}", color = colors.textMuted, fontSize = 10.sp, maxLines = 1)
            }
            if (!details?.cast.isNullOrBlank()) {
                Text("البطولة  ${details?.cast}", color = colors.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(19.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusButton("▶ تشغيل الفيلم", onPlay)
                FocusButton(if (isFavorite) "★ في قائمتي" else "+ قائمتي", onToggleFavorite, primary = false)
                FocusButton(
                    text = when (download?.status) {
                        OfflineStatus.COMPLETED -> "✓ تم التحميل"
                        OfflineStatus.DOWNLOADING,
                        OfflineStatus.QUEUED,
                        OfflineStatus.PAUSED,
                        -> "↓ جاري التحميل"
                        OfflineStatus.FAILED -> "↻ إعادة التحميل"
                        null -> "↓ تحميل الفيلم"
                    },
                    onClick = onDownload,
                    primary = false,
                    enabled = download == null || download.status == OfflineStatus.FAILED,
                )
            }
            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorNotice(errorMessage)
            }
        }

        if (isLoading) {
            Box(
                Modifier.align(Alignment.TopStart).padding(top = if (isTv) 28.dp else 18.dp, start = if (isTv) 112.dp else 85.dp),
            ) {
                LoadingRing()
            }
        }
    }
}
