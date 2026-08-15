package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.Episode
import java.util.Locale
import kotlin.math.roundToInt

internal data class DetailsProMetrics(
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val heroHeightDp: Int,
    val heroPosterWidthDp: Int,
    val relatedCardWidthDp: Int,
    val titleSizeSp: Int,
    val plotSizeSp: Int,
    val episodeColumns: Int,
    val compactHeight: Boolean,
    val wideLayout: Boolean,
)

internal fun detailsProMetrics(
    screenWidthDp: Int,
    screenHeightDp: Int,
    isTv: Boolean,
): DetailsProMetrics {
    val width = screenWidthDp.coerceAtLeast(1)
    val height = screenHeightDp.coerceAtLeast(1)
    val landscape = width > height
    val compactHeight = height < if (isTv) 500 else 520
    val wideLayout = isTv || width >= 700

    val horizontalPadding = when {
        isTv -> (width / 34f).roundToInt().coerceIn(22, 48)
        width >= 840 -> 30
        width >= 600 -> 24
        else -> 16
    }
    val verticalPadding = when {
        isTv -> (height / 38f).roundToInt().coerceIn(12, 24)
        compactHeight -> 8
        else -> 14
    }
    val heroHeight = when {
        isTv -> (height * .67f).roundToInt().coerceIn(340, 620)
        landscape -> (height * .84f).roundToInt().coerceIn(270, 430)
        width >= 600 -> (height * .56f).roundToInt().coerceIn(380, 560)
        else -> (height * .58f).roundToInt().coerceIn(350, 520)
    }
    val heroPosterWidth = when {
        isTv -> (width * .17f).roundToInt().coerceIn(150, 230)
        width >= 840 -> 174
        width >= 600 -> 152
        landscape -> 118
        else -> 112
    }
    val relatedCardWidth = when {
        isTv -> (width * .145f).roundToInt().coerceIn(128, 176)
        width >= 840 -> 132
        width >= 600 -> 122
        else -> 108
    }
    val titleSize = when {
        isTv && width >= 1280 -> 42
        isTv -> 36
        width >= 840 -> 31
        width >= 600 -> 28
        compactHeight -> 23
        else -> 25
    }
    val plotSize = when {
        isTv -> 13
        width >= 600 -> 13
        else -> 11
    }

    return DetailsProMetrics(
        horizontalPaddingDp = horizontalPadding,
        verticalPaddingDp = verticalPadding,
        heroHeightDp = heroHeight,
        heroPosterWidthDp = heroPosterWidth,
        relatedCardWidthDp = relatedCardWidth,
        titleSizeSp = titleSize,
        plotSizeSp = plotSize,
        episodeColumns = detailsProEpisodeColumns(width, isTv),
        compactHeight = compactHeight,
        wideLayout = wideLayout,
    )
}

internal fun detailsProEpisodeColumns(screenWidthDp: Int, isTv: Boolean): Int {
    val width = screenWidthDp.coerceAtLeast(1)
    return when {
        isTv && width >= 1600 -> 5
        isTv && width >= 1120 -> 4
        isTv -> 3
        width >= 1100 -> 4
        width >= 720 -> 3
        width >= 340 -> 2
        else -> 1
    }
}

internal fun detailsProRelatedItems(
    source: ContentItem,
    candidates: List<ContentItem>,
    limit: Int = 12,
): List<ContentItem> {
    if (limit <= 0) return emptyList()
    val sourceGenres = detailsProGenreTokens(source.genre)
    val sourceCategory = source.categoryId.trim().takeUnless { it.isEmpty() || it == "0" }
    val sourceYear = source.year?.trim()?.takeIf(String::isNotEmpty)

    return candidates
        .asSequence()
        .filter { it.type == source.type && it.id != source.id }
        .map { candidate ->
            val candidateGenres = detailsProGenreTokens(candidate.genre)
            val sharedGenres = sourceGenres.intersect(candidateGenres).size
            val sameCategory = sourceCategory != null && candidate.categoryId == sourceCategory
            val sameYear = sourceYear != null && candidate.year?.trim() == sourceYear
            val score =
                (if (sameCategory) 60 else 0) +
                    sharedGenres * 24 +
                    (if (sameYear) 4 else 0) +
                    (if (!candidate.backdropUrl.isNullOrBlank()) 2 else 0)
            candidate to score
        }
        .sortedWith(
            compareByDescending<Pair<ContentItem, Int>> { it.second }
                .thenByDescending { it.first.rating?.toDoubleOrNull() ?: 0.0 }
                .thenByDescending { it.first.addedAtEpochSeconds ?: 0L }
                .thenBy { it.first.name.lowercase(Locale.ROOT) }
                .thenBy { it.first.id },
        )
        .take(limit)
        .map(Pair<ContentItem, Int>::first)
        .toList()
}

internal fun detailsProAdjacentEpisode(
    orderedEpisodes: List<Episode>,
    currentEpisodeId: Int?,
    offset: Int,
): Episode? {
    if (orderedEpisodes.isEmpty() || currentEpisodeId == null || offset == 0) return null
    val index = orderedEpisodes.indexOfFirst { it.id == currentEpisodeId }
    if (index < 0) return null
    return orderedEpisodes.getOrNull(index + offset)
}

private fun detailsProGenreTokens(raw: String?): Set<String> {
    val clean = raw
        ?.lowercase(Locale.ROOT)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return emptySet()
    return clean
        .split(Regex("[,،/|&;·]+"))
        .asSequence()
        .map { it.trim() }
        .filter { it.length >= 2 }
        .toSet()
}
