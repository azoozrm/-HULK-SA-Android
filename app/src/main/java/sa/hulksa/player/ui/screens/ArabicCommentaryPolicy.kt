package sa.hulksa.player.ui.screens

import java.util.Locale

/**
 * Arabic commentary over a separate Live channel.
 *
 * The feature delays a source by holding that source while the other keeps playing. Each
 * completed hold adds to that source's output delay. The foreign video and the commentary audio
 * are independent Live streams, so this is a manual correction only; it never claims that two
 * unrelated provider timelines share a clock.
 *
 * Offset convention used internally and in the UI:
 * - `commentaryMs` is the total delay applied to the commentary audio source.
 * - `videoMs` is the total delay applied to the foreign video source.
 * The two values are reported separately because their sum/difference is not a trustworthy
 * statement about the provider's content timing.
 */
internal const val ARABIC_COMMENTARY_FINE_STEP_MS = 100L
internal const val ARABIC_COMMENTARY_COARSE_STEP_MS = 1_000L
internal const val ARABIC_COMMENTARY_MAX_SOURCE_DELAY_MS = 10_000L
internal const val ARABIC_COMMENTARY_MAX_AUTO_SOURCES = 3

internal enum class ArabicCommentarySymptom {
    /** The Arabic commentary is heard before the matching foreign picture. */
    COMMENTARY_EARLY,

    /** The Arabic commentary is heard after the matching foreign picture. */
    COMMENTARY_LATE,
}

internal enum class ArabicCommentaryDelayedSource {
    COMMENTARY,
    VIDEO,
}

internal data class ArabicCommentaryDelays(
    val commentaryMs: Long = 0L,
    val videoMs: Long = 0L,
) {
    init {
        require(commentaryMs >= 0L) { "commentary delay must be non-negative" }
        require(videoMs >= 0L) { "video delay must be non-negative" }
    }

    val hasDelay: Boolean get() = commentaryMs > 0L || videoMs > 0L
}

internal data class ArabicCommentaryStepResult(
    val delays: ArabicCommentaryDelays,
    val delayedSource: ArabicCommentaryDelayedSource,
    val appliedMs: Long,
    val limitReached: Boolean,
)

internal enum class ArabicCommentaryAudibleSource {
    ORIGINAL,
    COMMENTARY,
}

/**
 * The single audible-source decision used at activation, fallback and pause/resume. `null` means
 * "keep the current route" (an active adjustment owns the transition). The function can never
 * return both sources, and the owner applies the route by muting the previous source before
 * raising the next one.
 */
internal fun arabicCommentaryDesiredAudibleSource(
    commentaryActive: Boolean,
    adjusting: Boolean,
    videoPlaying: Boolean,
    commentaryPlaying: Boolean,
): ArabicCommentaryAudibleSource? = when {
    adjusting -> null
    commentaryActive && videoPlaying && commentaryPlaying -> ArabicCommentaryAudibleSource.COMMENTARY
    else -> ArabicCommentaryAudibleSource.ORIGINAL
}

/**
 * The two symptom buttons always delay exactly one source, matching the owner-facing wording:
 * early commentary delays the commentary audio; late commentary delays the foreign video.
 */
internal fun arabicCommentaryDelayedSource(
    symptom: ArabicCommentarySymptom,
): ArabicCommentaryDelayedSource = when (symptom) {
    ArabicCommentarySymptom.COMMENTARY_EARLY -> ArabicCommentaryDelayedSource.COMMENTARY
    ArabicCommentarySymptom.COMMENTARY_LATE -> ArabicCommentaryDelayedSource.VIDEO
}

internal fun arabicCommentaryDelayFor(
    delays: ArabicCommentaryDelays,
    source: ArabicCommentaryDelayedSource,
): Long = when (source) {
    ArabicCommentaryDelayedSource.COMMENTARY -> delays.commentaryMs
    ArabicCommentaryDelayedSource.VIDEO -> delays.videoMs
}

internal fun arabicCommentaryRemainingMs(
    delays: ArabicCommentaryDelays,
    symptom: ArabicCommentarySymptom,
    maxMs: Long = ARABIC_COMMENTARY_MAX_SOURCE_DELAY_MS,
): Long {
    val source = arabicCommentaryDelayedSource(symptom)
    return (maxMs.coerceAtLeast(0L) - arabicCommentaryDelayFor(delays, source)).coerceAtLeast(0L)
}

/**
 * Applies one bounded step to the source named by the symptom. A step that does not fit the
 * remaining supported range is clamped to the remaining room and reports `limitReached`; when no
 * room remains the delays are unchanged.
 */
internal fun applyArabicCommentaryStep(
    delays: ArabicCommentaryDelays,
    symptom: ArabicCommentarySymptom,
    stepMs: Long,
    maxMs: Long = ARABIC_COMMENTARY_MAX_SOURCE_DELAY_MS,
): ArabicCommentaryStepResult {
    val source = arabicCommentaryDelayedSource(symptom)
    val step = stepMs.coerceAtLeast(0L)
    val remaining = arabicCommentaryRemainingMs(delays, symptom, maxMs)
    val applied = minOf(step, remaining)
    val next = when (source) {
        ArabicCommentaryDelayedSource.COMMENTARY -> delays.copy(
            commentaryMs = delays.commentaryMs + applied,
        )
        ArabicCommentaryDelayedSource.VIDEO -> delays.copy(
            videoMs = delays.videoMs + applied,
        )
    }
    return ArabicCommentaryStepResult(
        delays = next,
        delayedSource = source,
        appliedMs = applied,
        limitReached = applied < step,
    )
}

/** One explicitly chosen feed, with a finite queue and a new callback identity per player. */
internal class ArabicCommentaryFallback(
    feed: ArabicCommentaryFeed,
    preferred: ArabicCommentaryCatalogSource,
) {
    private val sources = (listOfNotNull(feed.sources.find { it.key == preferred.key }) + feed.sources)
        .distinctBy { it.item.id }
        .take(ARABIC_COMMENTARY_MAX_AUTO_SOURCES)
        .iterator()
    var currentAttempt: Any? = null
        private set

    fun nextSource(): ArabicCommentaryCatalogSource? = if (sources.hasNext()) sources.next() else null

    fun beginAttempt(): Any = Any().also { currentAttempt = it }

    fun accepts(token: Any): Boolean = token === currentAttempt

    fun endAttempt() {
        currentAttempt = null
    }
}

internal enum class ArabicCommentaryBufferingTransition { START, CANCEL, KEEP }

internal fun arabicCommentaryBufferingTransition(
    wasBuffering: Boolean,
    videoPlaying: Boolean,
    commentaryBuffering: Boolean,
    adjusting: Boolean,
): ArabicCommentaryBufferingTransition {
    val shouldWatch = videoPlaying && commentaryBuffering && !adjusting
    return when {
        shouldWatch == wasBuffering -> ArabicCommentaryBufferingTransition.KEEP
        shouldWatch -> ArabicCommentaryBufferingTransition.START
        else -> ArabicCommentaryBufferingTransition.CANCEL
    }
}

/** Completed hold steps for the current players. A replacement discards only its own steps. */
internal data class ArabicCommentaryAlignment(
    val delays: ArabicCommentaryDelays = ArabicCommentaryDelays(),
    val needsReadjustment: Boolean = false,
    val pictureHoldIncomplete: Boolean = false,
) {
    val hasPictureAdjustment: Boolean get() = delays.videoMs > 0 || pictureHoldIncomplete

    fun holdInterrupted(source: ArabicCommentaryDelayedSource): ArabicCommentaryAlignment = copy(
        needsReadjustment = true,
        pictureHoldIncomplete = pictureHoldIncomplete || source == ArabicCommentaryDelayedSource.VIDEO,
    )

    fun canResetPicture(isLive: Boolean, seekable: Boolean, defaultSeekAvailable: Boolean): Boolean =
        hasPictureAdjustment && isLive && seekable && defaultSeekAvailable

    fun sourceReplaced(recheck: Boolean): ArabicCommentaryAlignment = copy(
        delays = delays.copy(commentaryMs = 0L),
        needsReadjustment = needsReadjustment || recheck,
    )

    fun pictureRepositioned(): ArabicCommentaryAlignment = copy(
        delays = delays.copy(videoMs = 0L),
        needsReadjustment = true,
        pictureHoldIncomplete = false,
    )
}

internal enum class ArabicCommentaryFocusTarget {
    CLOSE,
    SEARCH,
    FIRST_CHANNEL,
    RETRY,
    EARLY,
    RETURN,
}

/**
 * Deterministic focus target for the commentary surface.
 *
 * While the picker is visible the search field owns focus and the IME unless the owner deliberately
 * entered the picker (opened it, or returned to it through «تغيير قناة التعليق»). Search-result
 * changes alone must never pull focus out of the field. A list request is only produced when the
 * caller reports an attached eligible row.
 */
internal fun arabicCommentaryFocusTarget(
    sessionAvailable: Boolean,
    pickerVisible: Boolean,
    channelsAvailable: Boolean,
    phase: ArabicCommentaryPhase,
    deliberatePickerEntry: Boolean,
    canRetry: Boolean = true,
): ArabicCommentaryFocusTarget = when {
    !sessionAvailable -> ArabicCommentaryFocusTarget.CLOSE
    pickerVisible && deliberatePickerEntry && channelsAvailable ->
        ArabicCommentaryFocusTarget.FIRST_CHANNEL
    pickerVisible -> ArabicCommentaryFocusTarget.SEARCH
    phase == ArabicCommentaryPhase.ERROR || phase == ArabicCommentaryPhase.INTERRUPTED ->
        if (canRetry) ArabicCommentaryFocusTarget.RETRY else ArabicCommentaryFocusTarget.CLOSE
    phase == ArabicCommentaryPhase.ACTIVE -> ArabicCommentaryFocusTarget.EARLY
    phase == ArabicCommentaryPhase.PREPARING -> ArabicCommentaryFocusTarget.RETURN
    else -> ArabicCommentaryFocusTarget.CLOSE
}

/**
 * Whether a finished adjustment may resume the source it held. An interrupted, stale, released or
 * no-longer-active adjustment never resumes: the owner that interrupted it (stop, return to
 * original, release, user playback intent or lifecycle change) owns the final playback state.
 */
internal fun arabicCommentaryShouldResumeAfterAdjustment(
    interrupted: Boolean,
    generationCurrent: Boolean,
    phaseActive: Boolean,
    released: Boolean,
): Boolean = !interrupted && generationCurrent && phaseActive && !released

/**
 * The value shown on the Live More row. It reports the actual phase and never implies that the
 * commentary is audible when only the source is prepared.
 */
internal fun arabicCommentaryMenuValue(
    phase: ArabicCommentaryPhase,
    commentaryAudible: Boolean,
): String? = when (phase) {
    ArabicCommentaryPhase.PREPARING -> "يجهز"
    ArabicCommentaryPhase.ACTIVE -> if (commentaryAudible) "مفعل" else "جاهز"
    ArabicCommentaryPhase.ERROR,
    ArabicCommentaryPhase.INTERRUPTED,
    -> "متوقف"
    ArabicCommentaryPhase.INACTIVE -> null
}

internal fun formatArabicCommentaryDuration(ms: Long): String {
    val tenths = (ms.coerceAtLeast(0L) + 50L) / 100L
    return if (tenths % 10L == 0L) {
        (tenths / 10L).toString()
    } else {
        String.format(Locale.US, "%d.%d", tenths / 10L, tenths % 10L)
    }
}

internal fun arabicCommentaryStepLabel(stepMs: Long): String =
    "${formatArabicCommentaryDuration(stepMs)} ث"

internal fun arabicCommentaryDelaySummary(
    delays: ArabicCommentaryDelays,
    pictureRetained: Boolean = false,
): String {
    val pictureLabel = if (pictureRetained) "تأخير الصورة المحتفظ به" else "تأخير الصورة"
    return when {
        delays.commentaryMs > 0L && delays.videoMs > 0L ->
            "تأخير التعليق: ${formatArabicCommentaryDuration(delays.commentaryMs)} ث" +
                " · $pictureLabel: ${formatArabicCommentaryDuration(delays.videoMs)} ث"
        delays.commentaryMs > 0L ->
            "تأخير التعليق: ${formatArabicCommentaryDuration(delays.commentaryMs)} ث"
        delays.videoMs > 0L ->
            "$pictureLabel: ${formatArabicCommentaryDuration(delays.videoMs)} ث"
        else -> "لا توجد خطوات تأخير مطبقة"
    }
}
