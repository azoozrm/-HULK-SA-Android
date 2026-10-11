package sa.hulksa.player.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import sa.hulksa.player.data.HomeHeroTechnicalMetadata
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

/**
 * Home hero facts rendered through the authoritative Details hero metadata composables, so order,
 * `|` separators, icon placement, quality framing and typography match the accepted
 * Movie/Series Details pages. The metadata is loaded once by the hero owner (including the
 * bounded presentation backfill) and shared here, so facts and artwork never start competing
 * loads. A missing cached value is omitted until that authorized load resolves.
 *
 * The Home row wraps complete field groups on narrow windows instead of clipping; the accepted
 * Details callers keep their default centered single-line appearance.
 */
@Composable
internal fun HomeHeroFacts(
    item: ContentItem,
    isTv: Boolean,
    metadata: HomeHeroTechnicalMetadata,
    modifier: Modifier = Modifier,
) {
    val genre = metadata.genre?.takeIf(String::isNotBlank) ?: item.genre

    when (item.type) {
        ContentType.MOVIE -> MovieDetailsHeroMetadataRow(
            item = item,
            genre = genre,
            durationLabel = if (isTv) {
                detailsTvDuration(metadata.durationMs)
            } else {
                detailsProDurationLabel(metadata.durationMs)
            },
            qualityLabel = metadata.quality,
            ratingLabel = if (isTv) detailsTvRating(item.rating) else detailsProRatingLabel(item.rating),
            isTv = isTv,
            modifier = modifier,
            wrap = true,
        )

        ContentType.SERIES -> SeriesDetailsHeroMetadataRow(
            series = item,
            genre = genre,
            qualityLabel = metadata.quality,
            ratingLabel = if (isTv) detailsTvRating(item.rating) else detailsProRatingLabel(item.rating),
            episodeCount = metadata.episodeCount,
            seasonCount = metadata.seasonCount,
            isTv = isTv,
            modifier = modifier,
            wrap = true,
        )

        ContentType.LIVE -> Unit
    }
}
