package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.ContentItem

/**
 * Approved shared Movies/Series VOD-player controls policy.
 *
 * Owns only deterministic decisions for the compact five-control strip, the More panel state
 * machine, Go to time validation, speed/picture options and the bounded seek-preview bucket.
 * Rendering stays in PlayerScreen.kt.
 */
internal enum class VodMorePanelView {
    MENU,
    SPEED,
    PICTURE_SIZE,
}

internal enum class VodMoreRow {
    GO_TO_TIME,
    SPEED,
    PICTURE_SIZE,
    RESTART,
    LOCK,
}

/**
 * Back inside the VOD More panel: a child view returns to MENU, MENU closes the panel.
 *
 * `null` nextView means the panel closes and the More trigger regains focus.
 */
internal fun vodMorePanelBack(currentView: VodMorePanelView): VodMorePanelView? =
    if (currentView == VodMorePanelView.MENU) null else VodMorePanelView.MENU

/**
 * The MENU row that must regain focus when a child view returns to MENU. Go to time is a dialog
 * owned by the MENU row, so it is not a panel view here.
 */
internal fun vodMorePanelOriginRow(currentView: VodMorePanelView): VodMoreRow? =
    when (currentView) {
        VodMorePanelView.MENU -> null
        VodMorePanelView.SPEED -> VodMoreRow.SPEED
        VodMorePanelView.PICTURE_SIZE -> VodMoreRow.PICTURE_SIZE
    }

internal data class VodFavoriteControl(
    val enabled: Boolean,
    val favorite: Boolean,
)

/**
 * Favorite wiring for the current non-live VOD item.
 *
 * The button is disabled when the playing stream has no authoritative catalog item, and it reads
 * the same observed profile-scoped favorites snapshot as the rest of the app so the heart reacts
 * on the owned state publication instead of an unrelated tick.
 */
internal fun vodFavoriteControl(
    item: ContentItem?,
    favoriteKeys: Set<String>,
): VodFavoriteControl {
    val key = item?.let { "${it.type.name}:${it.id}" }
    return VodFavoriteControl(
        enabled = item != null,
        favorite = key != null && key in favoriteKeys,
    )
}

/**
 * Movie-only compact strip reflow decision.
 *
 * The five approved tools stay on one physical row only when the current metrics allow a single
 * row and the measured complete captions really fit the available width. Otherwise the caller
 * reflows into the two-row arrangement; captions are never ellipsized or shrunk to force one row.
 */
internal fun vodCompactSingleRow(
    approvedSingleRow: Boolean,
    availableWidthPx: Int,
    requiredWidthPx: Int,
): Boolean = approvedSingleRow && requiredWidthPx in 1..availableWidthPx

/**
 * Closed vertical focus cycle for the Movie More panel.
 *
 * Node 0 is the panel header and nodes 1..count-1 are the body rows. UP/DOWN wrap around the
 * closed cycle so boundary arrows stay inside the panel instead of leaking to the strip, the
 * timeline or the player root.
 */
internal fun vodPanelVerticalNeighbor(
    nodeIndex: Int,
    nodeCount: Int,
    delta: Int,
): Int {
    if (nodeCount <= 0) return 0
    if (delta == 0) return nodeIndex.coerceIn(0, nodeCount - 1)
    return ((nodeIndex + delta) % nodeCount + nodeCount) % nodeCount
}

/**
 * Signed seek target for the strip buttons and timeline. `null` means the operation is not
 * available (unknown duration); otherwise the target is clamped to the valid media range.
 */
internal fun vodSeekTargetMs(
    currentMs: Long,
    deltaMs: Long,
    durationMs: Long,
): Long? {
    if (durationMs <= 0L) return null
    return (currentMs + deltaMs).coerceIn(0L, durationMs)
}

internal const val VOD_GO_TO_TIME_MAX_HOURS = 99

internal data class VodGoToTimeFields(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)

/**
 * Split a committed position into the three HH:MM:SS fields without overflow.
 */
internal fun vodGoToTimeFields(positionMs: Long): VodGoToTimeFields {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    return VodGoToTimeFields(
        hours = (totalSeconds / 3_600L).toInt().coerceAtMost(VOD_GO_TO_TIME_MAX_HOURS),
        minutes = ((totalSeconds % 3_600L) / 60L).toInt(),
        seconds = (totalSeconds % 60L).toInt(),
    )
}

/**
 * Validate the numeric components before building a target. Returns the target in milliseconds or
 * `null` when any component is out of range or the media duration is unknown.
 */
internal fun vodGoToTimeTargetMs(
    hours: Int,
    minutes: Int,
    seconds: Int,
    durationMs: Long,
): Long? {
    if (durationMs <= 0L) return null
    if (hours !in 0..VOD_GO_TO_TIME_MAX_HOURS) return null
    if (minutes !in 0..59) return null
    if (seconds !in 0..59) return null
    val target = (hours * 3_600L + minutes * 60L + seconds) * 1_000L
    return target.takeIf { it <= durationMs }
}

internal fun vodGoToTimeConfirmEnabled(
    hours: Int,
    minutes: Int,
    seconds: Int,
    durationMs: Long,
): Boolean = vodGoToTimeTargetMs(hours, minutes, seconds, durationMs) != null

internal enum class VodTimeField { HOURS, MINUTES, SECONDS }

/**
 * Step one HH:MM:SS field up/down, carrying naturally and clamping to the real duration so no
 * invalid, negative or out-of-range seek can be produced.
 */
internal fun vodGoToTimeFieldsStepped(
    fields: VodGoToTimeFields,
    field: VodTimeField,
    delta: Int,
    durationMs: Long,
): VodGoToTimeFields {
    if (durationMs <= 0L || delta == 0) return fields
    val currentMs = vodGoToTimeTargetMs(
        hours = fields.hours,
        minutes = fields.minutes,
        seconds = fields.seconds,
        durationMs = Long.MAX_VALUE,
    ) ?: return fields
    val stepMs = when (field) {
        VodTimeField.HOURS -> 3_600_000L
        VodTimeField.MINUTES -> 60_000L
        VodTimeField.SECONDS -> 1_000L
    }
    val stepped = (currentMs + stepMs * delta).coerceIn(0L, durationMs)
    return vodGoToTimeFields(stepped)
}

internal val VOD_PLAYER_SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun vodSpeedLabel(speed: Float): String = when {
    kotlin.math.abs(speed - 0.75f) < 0.001f -> "0.75x"
    kotlin.math.abs(speed - 1f) < 0.001f -> "1x"
    kotlin.math.abs(speed - 1.25f) < 0.001f -> "1.25x"
    kotlin.math.abs(speed - 1.5f) < 0.001f -> "1.5x"
    kotlin.math.abs(speed - 2f) < 0.001f -> "2x"
    else -> "${speed}x"
}

/** Fresh items start at the normal 1x speed; an unknown stored value is not a new owner. */
internal fun vodNormalizedSpeed(speed: Float): Float =
    VOD_PLAYER_SPEED_OPTIONS.firstOrNull { kotlin.math.abs(it - speed) < 0.001f } ?: 1f

/**
 * The bounded seek preview only attempts real frame decoding for direct, authorized media sources.
 * Playlists (.m3u/.m3u8) keep the timestamp-only fallback instead of pretending an image exists.
 */
internal fun vodPreviewDecodableCandidate(candidate: String?): Boolean {
    val source = candidate?.trim().orEmpty()
    if (source.isBlank()) return false
    val lower = source.lowercase()
    if (lower.contains(".m3u8") || lower.contains(".m3u")) return false
    return lower.startsWith("http://") ||
        lower.startsWith("https://") ||
        lower.startsWith("file://") ||
        lower.startsWith("content://") ||
        (!lower.contains("://") && source.contains('.'))
}

/** Quantize preview frames so a bounded cache can reuse them while scrubbing. */
internal fun vodPreviewBucketMs(timeMs: Long, bucketMs: Long = 5_000L): Long =
    (timeMs.coerceAtLeast(0L) / bucketMs) * bucketMs

internal fun vodPreviewCardFraction(previewMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (previewMs.toFloat() / durationMs).coerceIn(0f, 1f)
