package sa.hulksa.player.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.CapabilityFinding
import sa.hulksa.player.model.CapabilityStatus
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.DiagnosticIssue
import sa.hulksa.player.model.DiagnosticSeverity
import sa.hulksa.player.model.DiagnosticsState
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.model.DownloadSettings
import sa.hulksa.player.model.FeatureRecommendation
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.ServerDiagnosticsReport
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.ChannelLogo
import sa.hulksa.player.ui.components.ChannelListItem
import sa.hulksa.player.ui.components.CompactPosterCard
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HistoryCard
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.InfoPill
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.layout.navigationBarsPadding

private const val WEBSITE_URL = "https://hulksa.com/"
private const val ACCOUNT_URL = "https://hulksa.com/account/login.php"
private const val APPS_URL = "https://hulksa.com/hulk-app/"
private const val SUPPORT_URL = "https://wa.me/966506349935"
private const val FAVORITES_CATEGORY_ID = "__hulk_favorites__"
private const val CONTINUE_CATEGORY_ID = "__hulk_continue__"
private val TV_PAGE_GUTTER = 8.dp
private val TV_LIVE_ACTION_INSET = 8.dp

internal fun tvRailLogoSizeDp(screenWidthDp: Int): Float =
    (screenWidthDp.coerceAtLeast(1) / 32f).coerceIn(28f, 60f)

internal fun tvDownloadCardHeightDp(screenHeightDp: Int): Float =
    if (screenHeightDp <= 600) 196f else 188f


internal enum class DownloadFocusSlot {
    WIFI,
    SCHEDULE,
    CONCURRENT,
    PRIMARY,
    PRIORITY,
    CANCEL,
}

internal enum class DownloadFocusDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

internal data class DownloadFocusNode(
    val rowIndex: Int,
    val slot: DownloadFocusSlot,
)

internal fun nextDownloadFocusNode(
    current: DownloadFocusNode,
    rowCount: Int,
    direction: DownloadFocusDirection,
): DownloadFocusNode? {
    if (rowCount < 0) return null
    if (current.rowIndex < 0) {
        return when (direction) {
            DownloadFocusDirection.UP -> null
            DownloadFocusDirection.DOWN -> when (current.slot) {
                DownloadFocusSlot.WIFI -> DownloadFocusNode(0, DownloadFocusSlot.CANCEL)
                DownloadFocusSlot.SCHEDULE -> DownloadFocusNode(0, DownloadFocusSlot.PRIORITY)
                DownloadFocusSlot.CONCURRENT -> DownloadFocusNode(0, DownloadFocusSlot.PRIMARY)
                else -> null
            }.takeIf { rowCount > 0 }
            DownloadFocusDirection.LEFT -> when (current.slot) {
                DownloadFocusSlot.WIFI -> DownloadFocusNode(-1, DownloadFocusSlot.SCHEDULE)
                DownloadFocusSlot.SCHEDULE -> DownloadFocusNode(-1, DownloadFocusSlot.CONCURRENT)
                else -> null
            }
            DownloadFocusDirection.RIGHT -> when (current.slot) {
                DownloadFocusSlot.CONCURRENT -> DownloadFocusNode(-1, DownloadFocusSlot.SCHEDULE)
                DownloadFocusSlot.SCHEDULE -> DownloadFocusNode(-1, DownloadFocusSlot.WIFI)
                else -> null
            }
        }
    }

    if (
        current.rowIndex >= rowCount ||
        current.slot !in setOf(
            DownloadFocusSlot.PRIMARY,
            DownloadFocusSlot.PRIORITY,
            DownloadFocusSlot.CANCEL,
        )
    ) {
        return null
    }

    return when (direction) {
        DownloadFocusDirection.LEFT -> when (current.slot) {
            DownloadFocusSlot.PRIMARY -> current.copy(slot = DownloadFocusSlot.PRIORITY)
            DownloadFocusSlot.PRIORITY -> current.copy(slot = DownloadFocusSlot.CANCEL)
            else -> null
        }
        DownloadFocusDirection.RIGHT -> when (current.slot) {
            DownloadFocusSlot.CANCEL -> current.copy(slot = DownloadFocusSlot.PRIORITY)
            DownloadFocusSlot.PRIORITY -> current.copy(slot = DownloadFocusSlot.PRIMARY)
            else -> null
        }
        DownloadFocusDirection.UP -> if (current.rowIndex > 0) {
            current.copy(rowIndex = current.rowIndex - 1)
        } else {
            DownloadFocusNode(
                rowIndex = -1,
                slot = when (current.slot) {
                    DownloadFocusSlot.PRIMARY -> DownloadFocusSlot.CONCURRENT
                    DownloadFocusSlot.PRIORITY -> DownloadFocusSlot.SCHEDULE
                    DownloadFocusSlot.CANCEL -> DownloadFocusSlot.WIFI
                    else -> return null
                },
            )
        }
        DownloadFocusDirection.DOWN -> if (current.rowIndex + 1 < rowCount) {
            current.copy(rowIndex = current.rowIndex + 1)
        } else {
            null
        }
    }
}

private fun Modifier.downloadFocusNavigation(
    isTv: Boolean,
    node: DownloadFocusNode,
    rowCount: Int,
    requesters: Map<DownloadFocusNode, FocusRequester>,
): Modifier {
    if (!isTv) return this
    val requester = requesters[node] ?: return this
    val upRequester = nextDownloadFocusNode(node, rowCount, DownloadFocusDirection.UP)
        ?.let(requesters::get)
    val downRequester = nextDownloadFocusNode(node, rowCount, DownloadFocusDirection.DOWN)
        ?.let(requesters::get)
    val leftRequester = nextDownloadFocusNode(node, rowCount, DownloadFocusDirection.LEFT)
        ?.let(requesters::get)
    val rightRequester = nextDownloadFocusNode(node, rowCount, DownloadFocusDirection.RIGHT)
        ?.let(requesters::get)
    return focusRequester(requester).focusProperties {
        upRequester?.let { up = it }
        downRequester?.let { down = it }
        leftRequester?.let { left = it }
        rightRequester?.let { right = it }
    }
}
