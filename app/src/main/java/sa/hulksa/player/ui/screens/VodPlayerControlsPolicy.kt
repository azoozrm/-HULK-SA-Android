package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.ContentItem
import kotlin.math.roundToInt

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
 * Movie controls keep one compact centered tool row. When the measured complete captions cannot
 * fit at their intrinsic width, the same five tools share the row width evenly instead of being
 * split into a different arrangement; captions wrap inside their slot.
 */
internal fun vodToolsUseWeightedSlots(
    availableWidthPx: Int,
    requiredWidthPx: Int,
): Boolean = requiredWidthPx > availableWidthPx

/**
 * Required intrinsic width of the five-tool row for the measured icon boxes, complete captions and
 * the SAME spacing expression that renders the row. Fit and render therefore agree.
 */
internal fun vodToolsRequiredWidthPx(
    iconBoxesPx: List<Int>,
    captionWidthsPx: List<Int>,
    spacingPx: Int,
): Int {
    if (iconBoxesPx.isEmpty() || iconBoxesPx.size != captionWidthsPx.size) return 0
    val tools = iconBoxesPx.indices.sumOf { maxOf(iconBoxesPx[it], captionWidthsPx[it]) }
    return tools + (iconBoxesPx.size - 1) * spacingPx.coerceAtLeast(0)
}

/**
 * Movies tool-row spacing derived from the rendered glyph size and the shared item spacing.
 *
 * This single expression feeds both the fit measurement and the rendered row gap, so a widened row
 * can never disagree with its own fit check.
 */
internal fun vodToolSpacingDp(movieGlyphDp: Int, itemSpacingDp: Int): Int =
    (movieGlyphDp.coerceAtLeast(0) * 1.33f).roundToInt().coerceAtLeast(itemSpacingDp)

/** Constant content clearance below the lowest Movies caption inside the real safe boundary. */
internal const val MOVIE_STRIP_BOTTOM_CLEARANCE_DP = 8f

/**
 * Movies-only bottom padding inside the real safe boundary.
 *
 * TV uses the repository overscan-safe boundary (`tvPremiumWindowPolicy.verticalSafeInsetDp`) plus
 * one constant content clearance; the extra Live presentation buffer in
 * `playerTvPremiumOverlayMetrics.safeBottomPaddingDp` is deliberately not applied. Touch layouts
 * already consume the system navigation inset once through `navigationBarsPadding()`, so only the
 * content clearance remains. The result never exceeds the Live base, so the Movies group can only
 * move down from the Live safe-window position, never up.
 */
internal fun vodStripBottomPaddingDp(
    baseBottomDp: Float,
    safeBottomInsetDp: Float,
    isTelevision: Boolean,
): Float {
    val targetDp = if (isTelevision) {
        safeBottomInsetDp.coerceAtLeast(0f) + MOVIE_STRIP_BOTTOM_CLEARANCE_DP
    } else {
        MOVIE_STRIP_BOTTOM_CLEARANCE_DP
    }
    return targetDp.coerceAtMost(baseBottomDp.coerceAtLeast(0f))
}

/**
 * Movies bottom-gradient stop fractions derived from the real measured row bounds inside the strip:
 * transparent at the strip top, black .50 at the timeline row top (so the track never sits above
 * its backing) and black .88 at the tools row top (so the whole glyph/caption zone has the strong
 * bottom backing). Returns null until the strip and both rows have been measured.
 */
internal data class VodStripGradientStops(
    val timelineTopFraction: Float,
    val toolsTopFraction: Float,
)

internal fun vodStripGradientStops(
    stripTopPx: Float,
    stripHeightPx: Float,
    timelineTopPx: Float,
    toolsTopPx: Float,
): VodStripGradientStops? {
    if (stripHeightPx <= 0f || timelineTopPx <= stripTopPx || toolsTopPx <= timelineTopPx) return null
    val timelineFraction = ((timelineTopPx - stripTopPx) / stripHeightPx).coerceIn(0f, 1f)
    val toolsFraction = ((toolsTopPx - stripTopPx) / stripHeightPx).coerceIn(0f, 1f)
    return if (toolsFraction > timelineFraction) {
        VodStripGradientStops(
            timelineTopFraction = timelineFraction,
            toolsTopFraction = toolsFraction,
        )
    } else {
        null
    }
}

/**
 * Movies seek-thumb center: inset by the radius so the circle stays fully inside the track at both
 * endpoints (fraction 0 -> radius, fraction 1 -> width - radius) with no fixed offset.
 */
internal fun vodSeekThumbCenterPx(
    fraction: Float,
    trackWidthPx: Float,
    thumbDiameterPx: Float,
): Float {
    val diameter = thumbDiameterPx.coerceAtLeast(0f)
    val travel = (trackWidthPx - diameter).coerceAtLeast(0f)
    return diameter / 2f + fraction.coerceIn(0f, 1f) * travel
}

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

/** Clamped timeline fraction for a seek target; never produces an out-of-range thumb. */
internal fun vodPreviewCardFraction(previewMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (previewMs.toFloat() / durationMs).coerceIn(0f, 1f)

/**
 * Active-seek control visibility for the shared VOD strip.
 *
 * While the VOD TV timeline focus or the existing remote direct-seek interaction is active, the
 * controls stay usable; after the interaction exits, ordinary auto-hide resumes. Live callers keep
 * their existing behavior.
 */
internal fun vodActiveSeekHoldsControls(
    isVod: Boolean,
    remoteInput: Boolean,
    timelineFocused: Boolean,
    directSeekActive: Boolean,
): Boolean = isVod && remoteInput && (timelineFocused || directSeekActive)

/**
 * Whether VOD progress dispatches must stay blocked while the Resume decision is pending.
 *
 * A Movie or Series episode opened with a saved position and resume playback enabled prepares at
 * zero behind the decision, so video preparation, cancellation, lifecycle/disposal and late
 * callbacks must not overwrite the stored position or duration. Live and VOD entries without a
 * pending decision keep their existing progress-saving behavior.
 */
internal fun vodProgressPersistenceBlockedInitially(
    isVod: Boolean,
    resumePlaybackEnabled: Boolean,
    resumePositionMs: Long,
): Boolean = isVod && resumePlaybackEnabled && resumePositionMs > 0L

/** An explicit Resume/Restart acceptance clears the block permanently for the request. */
internal fun vodProgressPersistenceBlockedAfterDecision(
    blocked: Boolean,
    decisionAccepted: Boolean,
): Boolean = blocked && !decisionAccepted

/** What the pending Movie Resume dialog does with one key event. */
internal enum class MovieResumeBackDisposition {
    /** Cancel the pending Resume through the existing handler and consume the event. */
    HANDLE_AND_CONSUME,

    /** Leave the event to the platform BackHandler for exactly one touch/system cancellation. */
    PASS_TO_SYSTEM,

    /** Not a cancel press: background suppression stays and the dialog keeps its focus graph. */
    PASS_TO_DIALOG,
}

/**
 * Pending Resume input ownership for BACK/ESCAPE.
 *
 * A first remote/TV BACK (or any ESCAPE) must cancel through the existing handler before the
 * dialog focus can merely clear; touch-platform system Back stays with the screen BackHandler so
 * the cancellation happens exactly once. Key releases, repeats and every other key keep the
 * dialog's D-pad/OK focus graph and the suppressed background commands unchanged.
 */
internal fun movieResumeBackDisposition(
    isBackKey: Boolean,
    isEscapeKey: Boolean,
    keyDown: Boolean,
    isRepeat: Boolean,
    remoteInput: Boolean,
): MovieResumeBackDisposition = when {
    !keyDown || isRepeat -> MovieResumeBackDisposition.PASS_TO_DIALOG
    isEscapeKey -> MovieResumeBackDisposition.HANDLE_AND_CONSUME
    isBackKey && remoteInput -> MovieResumeBackDisposition.HANDLE_AND_CONSUME
    isBackKey -> MovieResumeBackDisposition.PASS_TO_SYSTEM
    else -> MovieResumeBackDisposition.PASS_TO_DIALOG
}
