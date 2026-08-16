package sa.hulksa.player.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import sa.hulksa.player.data.HomeHeroMetadataStore
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.components.InfoPill
import java.util.Locale

@Composable
internal fun HomeHeroTechnicalPills(
    item: ContentItem,
    isTv: Boolean,
) {
    // Keep the already-approved phone hero untouched; these extra metadata pills
    // are the TV hero treatment requested during v1.4 field validation.
    if (!isTv) return

    val context = LocalContext.current
    val store = remember(context) { HomeHeroMetadataStore.get(context) }
    var metadata by remember(item.type, item.id, store) {
        mutableStateOf(store.cached(item))
    }

    LaunchedEffect(item.type, item.id, store) {
        metadata = store.metadata(item)
    }

    when (item.type) {
        ContentType.MOVIE -> {
            heroMovieDurationLabel(metadata.durationMs)?.let { InfoPill(it) }
            metadata.quality
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { InfoPill(it) }
        }

        ContentType.SERIES -> {
            metadata.episodeCount
                ?.takeIf { it > 0 }
                ?.let { InfoPill("الحلقات $it") }
            metadata.seasonCount
                ?.takeIf { it > 0 }
                ?.let { InfoPill("المواسم $it") }
            metadata.quality
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { InfoPill(it) }
        }

        ContentType.LIVE -> Unit
    }
}

private fun heroMovieDurationLabel(durationMs: Long?): String? {
    val totalMinutes = durationMs
        ?.takeIf { it > 0L }
        ?.div(60_000L)
        ?: return null
    if (totalMinutes <= 0L) return null

    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> String.format(Locale.US, "%dh %02dm", hours, minutes)
        hours > 0L -> String.format(Locale.US, "%dh", hours)
        else -> String.format(Locale.US, "%dm", minutes)
    }
}
