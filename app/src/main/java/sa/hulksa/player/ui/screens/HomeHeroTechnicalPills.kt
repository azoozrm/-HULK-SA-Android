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
    maxFacts: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    val store = remember(context) { HomeHeroMetadataStore.get(context) }
    val metadataOwner = store.currentOwner()
    var metadata by remember(item.type, item.id, metadataOwner, store) {
        mutableStateOf(store.cached(metadataOwner, item))
    }

    LaunchedEffect(item.type, item.id, metadataOwner, store) {
        val owner = metadataOwner ?: return@LaunchedEffect
        val loaded = store.metadata(owner, item)
        store.publishIfCurrent(owner) { metadata = loaded }
    }

    val facts = when (item.type) {
        ContentType.MOVIE -> listOfNotNull(
            metadata.quality?.trim()?.takeIf(String::isNotBlank),
            heroMovieDurationLabel(metadata.durationMs),
        )

        ContentType.SERIES -> listOfNotNull(
            metadata.quality?.trim()?.takeIf(String::isNotBlank),
            metadata.seasonCount?.takeIf { it > 0 }?.let { "$it موسم" },
            metadata.episodeCount?.takeIf { it > 0 }?.let { "$it حلقة" },
        )

        ContentType.LIVE -> emptyList()
    }
    facts.take(maxFacts.coerceAtLeast(0)).forEach { fact -> InfoPill(fact) }
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
