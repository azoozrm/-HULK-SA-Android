package sa.hulksa.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.data.HomeHeroMetadataStore
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.util.Locale

/**
 * Approved Home hero metadata: one quiet line that preserves every real fact (rating, genre,
 * quality, duration / seasons / episodes).
 *
 * Every fact is rendered from explicit single-run text nodes in a left-to-right row so Arabic
 * words and Latin digits always keep their approved visual order ("★ 10", "موسم 1"); the row is
 * anchored to the copy side (right in the RTL page) through Arrangement.End.
 */
@Composable
internal fun HomeHeroMetadataLine(
    item: ContentItem,
    isTv: Boolean,
    leadingFacts: List<String>,
) {
    val colors = LocalHulkColors.current
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

    val technicalFacts: List<List<String>> = when (item.type) {
        ContentType.MOVIE -> listOfNotNull(
            metadata.quality?.trim()?.takeIf(String::isNotBlank)?.let(::listOf),
            heroMovieDurationLabel(metadata.durationMs)?.let(::listOf),
        )

        ContentType.SERIES -> listOfNotNull(
            metadata.quality?.trim()?.takeIf(String::isNotBlank)?.let(::listOf),
            metadata.seasonCount?.takeIf { it > 0 }?.let { listOf("موسم", latinInt(it)) },
            metadata.episodeCount?.takeIf { it > 0 }?.let { listOf("حلقة", latinInt(it)) },
        )

        ContentType.LIVE -> emptyList()
    }
    val facts = (leadingFacts.map(::listOf) + technicalFacts)
        .map { parts -> parts.map(String::trim).filter(String::isNotBlank) }
        .filter { it.isNotEmpty() }
    if (facts.isEmpty()) return

    val fontSize = if (isTv) 13.sp else 12.sp
    val lineHeight = if (isTv) 17.sp else 16.sp
    val factColor = colors.text.copy(alpha = if (isTv) .96f else .92f)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            facts.forEachIndexed { index, parts ->
                if (index > 0) {
                    Text(
                        text = "·",
                        color = colors.textMuted,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        maxLines = 1,
                    )
                }
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    parts.forEach { part ->
                        Text(
                            text = part,
                            color = factColor,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun latinInt(value: Int): String = String.format(Locale.US, "%d", value)

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
