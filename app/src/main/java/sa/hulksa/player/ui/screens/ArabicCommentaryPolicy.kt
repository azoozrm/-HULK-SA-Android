package sa.hulksa.player.ui.screens

import java.util.Locale

/**
 * Arabic commentary over a separate Live channel.
 *
 * The prototype delays a source by holding that source while the other keeps playing. Each
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

/**
 * Account metadata is advisory. A provider may count the existing playback plus the commentary
 * source as two connections, but metadata alone never proves simultaneous playback works.
 */
internal fun arabicCommentaryConnectionNotice(
    activeConnections: Int,
    maxConnections: Int,
): String? {
    val active = activeConnections.coerceAtLeast(0)
    val max = maxConnections.coerceAtLeast(0)
    return when {
        max <= 0 -> "حد الاتصالات غير متاح من بيانات الحساب."
        max == 1 -> "تنبيه: باقة الحساب تشير الى اتصال واحد. قد لا يعمل مصدران في الوقت نفسه."
        active >= max -> "تنبيه: الاتصالات الحالية $active من $max. قد يستهلك التعليق اتصالا اضافيا."
        else -> "اتصالات الحساب: $active من $max."
    }
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

internal fun arabicCommentaryDelaySummary(delays: ArabicCommentaryDelays): String = when {
    delays.commentaryMs > 0L && delays.videoMs > 0L ->
        "تأخير التعليق: ${formatArabicCommentaryDuration(delays.commentaryMs)} ث" +
            " · تأخير الصورة: ${formatArabicCommentaryDuration(delays.videoMs)} ث"
    delays.commentaryMs > 0L ->
        "تأخير التعليق: ${formatArabicCommentaryDuration(delays.commentaryMs)} ث"
    delays.videoMs > 0L ->
        "تأخير الصورة: ${formatArabicCommentaryDuration(delays.videoMs)} ث"
    else -> "لا يوجد تأخير"
}
