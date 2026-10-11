package sa.hulksa.player.data

import org.json.JSONObject

/**
 * Narrow presentation fields carried by the bounded card-metadata payloads. The hero reuses the
 * already-loaded get_vod_info/get_series_info response for its real plot and artwork candidate
 * instead of issuing a second request or assuming a field name is landscape.
 */
internal data class CardPresentationMetadata(
    val plot: String? = null,
    val genre: String? = null,
    val artworkUrl: String? = null,
)

/**
 * Shared presentation parsing for the card-metadata clients: ordered candidate sources, the
 * same plot/description null policy and the shared Xtream artwork normalization. Only real
 * values pass; blanks and null strings stay null.
 */
internal fun cardPresentationMetadataFrom(
    sources: List<JSONObject?>,
    portalBaseUrl: String,
): CardPresentationMetadata {
    val present = sources.filterNotNull()
    return CardPresentationMetadata(
        plot = present.firstNotNullOfOrNull { source ->
            source.xtreamNullableString("plot") ?: source.xtreamNullableString("description")
        },
        genre = present.firstNotNullOfOrNull { source ->
            source.xtreamNullableString("genre")
        },
        artworkUrl = present.firstNotNullOfOrNull { source ->
            source.xtreamPresentationArtworkUrl(portalBaseUrl)
        },
    )
}

/**
 * Shared presentation-backfill policy for the movie and series stores. A technically complete
 * cache entry runs one bounded backfill only when the active hero asks for presentation, the
 * fields are still missing and the entry has not already been settled by a SUCCESSFUL validated
 * payload. A legacy marker alone never settles.
 */
internal fun presentationBackfillShouldRun(
    technicalComplete: Boolean,
    requirePresentation: Boolean,
    hasPresentation: Boolean,
    presentationSettled: Boolean,
): Boolean = technicalComplete && requirePresentation && !hasPresentation && !presentationSettled

/** Only a successfully parsed valid provider payload may settle the presentation attempt. */
internal fun presentationSettlesAfterFetch(fetchSucceeded: Boolean): Boolean = fetchSucceeded
