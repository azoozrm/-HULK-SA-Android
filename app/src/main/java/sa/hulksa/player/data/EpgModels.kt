package sa.hulksa.player.data

import java.util.TimeZone
import sa.hulksa.player.model.ContentItem

/** The provider XMLTV document covers roughly the next 24 hours; the UI never promises a week. */
internal const val EPG_WINDOW_SECONDS: Long = 24L * 60L * 60L

/**
 * The provider's `epg_channel_id` is stored in the legacy [ContentItem.nowPlaying] field by
 * XtreamClient and by cached catalog rows (`now_playing`). Keep that compatibility and treat the
 * value only as the exact XMLTV channel key; programme data never lives in the catalog item.
 */
internal fun ContentItem.epgChannelId(): String? =
    nowPlaying?.trim()?.takeIf(String::isNotEmpty)

/** One programme inside one channel's schedule. The owning map/list supplies the channel id. */
data class EpgProgramme(
    val startEpochSeconds: Long,
    val stopEpochSeconds: Long,
    val title: String,
) {
    val hasUsableSpan: Boolean
        get() = startEpochSeconds >= 0L &&
            stopEpochSeconds > startEpochSeconds &&
            title.isNotBlank()
}

data class EpgNowNext(
    val current: EpgProgramme? = null,
    val next: EpgProgramme? = null,
) {
    val isEmpty: Boolean get() = current == null && next == null
}

/**
 * Immutable snapshot of the provider schedule keyed by the exact XMLTV channel id.
 *
 * Query policy is deliberately shared by the Live preview and the guide so both surfaces agree:
 * - expired entries are never current or next;
 * - an exact duplicate start keeps one deterministic winner;
 * - an overlapping conflict resolves `current` to the latest starting entry that still covers now;
 * - `next` starts at/after the selected current entry's end, so a conflicting overlap is not shown
 *   as both current and next.
 */
data class EpgSchedule(
    val programmesByChannel: Map<String, List<EpgProgramme>> = emptyMap(),
) {
    val isEmpty: Boolean get() = programmesByChannel.isEmpty()

    val totalProgrammes: Int
        get() = programmesByChannel.values.sumOf { it.size }

    fun nowNext(channelId: String?, nowEpochSeconds: Long): EpgNowNext {
        val programmes = programmesForChannel(channelId) ?: return EpgNowNext()
        var current: EpgProgramme? = null
        for (programme in programmes) {
            if (!programme.hasUsableSpan) continue
            if (programme.startEpochSeconds > nowEpochSeconds) continue
            if (programme.stopEpochSeconds <= nowEpochSeconds) continue
            if (isPreferredCurrent(programme, current)) current = programme
        }
        val nextFloor = current?.stopEpochSeconds ?: nowEpochSeconds
        var next: EpgProgramme? = null
        for (programme in programmes) {
            if (!programme.hasUsableSpan) continue
            if (programme.stopEpochSeconds <= nowEpochSeconds) continue
            if (programme.startEpochSeconds < nextFloor) continue
            if (next == null || compareProgrammeStart(programme, next) < 0) next = programme
        }
        return EpgNowNext(current = current, next = next)
    }

    /** Up to 24 hours of usable entries; the current entry is retained, expired entries are not. */
    fun programmesFor(
        channelId: String?,
        nowEpochSeconds: Long,
        windowEndEpochSeconds: Long = nowEpochSeconds + EPG_WINDOW_SECONDS,
    ): List<EpgProgramme> {
        val programmes = programmesForChannel(channelId) ?: return emptyList()
        val boundedEnd = minOf(windowEndEpochSeconds, nowEpochSeconds + EPG_WINDOW_SECONDS)
        if (boundedEnd <= nowEpochSeconds) return emptyList()
        return programmes.asSequence()
            .filter {
                it.hasUsableSpan &&
                    it.stopEpochSeconds > nowEpochSeconds &&
                    it.startEpochSeconds < boundedEnd
            }
            .sortedWith(PROGRAMME_ORDER)
            .toList()
    }

    private fun programmesForChannel(channelId: String?): List<EpgProgramme>? {
        val key = channelId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return programmesByChannel[key]
    }

    companion object {
        val Empty = EpgSchedule()

        private val PROGRAMME_ORDER: Comparator<EpgProgramme> =
            compareBy<EpgProgramme> { it.startEpochSeconds }
                .thenByDescending { it.stopEpochSeconds }
                .thenBy { it.title }

        private fun isPreferredCurrent(
            candidate: EpgProgramme,
            existing: EpgProgramme?,
        ): Boolean {
            if (existing == null) return true
            if (candidate.startEpochSeconds != existing.startEpochSeconds) {
                return candidate.startEpochSeconds > existing.startEpochSeconds
            }
            if (candidate.stopEpochSeconds != existing.stopEpochSeconds) {
                return candidate.stopEpochSeconds > existing.stopEpochSeconds
            }
            return candidate.title < existing.title
        }

        private fun compareProgrammeStart(
            left: EpgProgramme,
            right: EpgProgramme,
        ): Int {
            val byStart = left.startEpochSeconds.compareTo(right.startEpochSeconds)
            if (byStart != 0) return byStart
            val byStop = right.stopEpochSeconds.compareTo(left.stopEpochSeconds)
            if (byStop != 0) return byStop
            return left.title.compareTo(right.title)
        }
    }
}

/** A successfully loaded, provider-identity-verified snapshot. */
internal data class EpgSnapshot(
    val fetchedAtEpochSeconds: Long,
    val schedule: EpgSchedule,
)

/**
 * Optional Live guide state. A default value is the fail-closed state for HULK/unidentified
 * sessions: no controls, no schedule and no EPG request.
 */
data class LiveEpgUiState(
    val enabled: Boolean = false,
    val verified: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val fetchedAtEpochSeconds: Long = 0L,
    val schedule: EpgSchedule = EpgSchedule.Empty,
) {
    val guideEnabled: Boolean get() = verified

    fun nowNext(channelId: String?, nowEpochSeconds: Long): EpgNowNext =
        if (verified) schedule.nowNext(channelId, nowEpochSeconds) else EpgNowNext()

    fun programmesFor(channelId: String?, nowEpochSeconds: Long): List<EpgProgramme> =
        if (verified) schedule.programmesFor(channelId, nowEpochSeconds) else emptyList()
}

/**
 * Local wall-clock label for the EPG. The provider offsets have already been applied to the stored
 * epoch seconds; this only converts UTC to the device's current local time zone. Latin digits keep
 * numeric order stable inside the Arabic RTL UI.
 */
internal fun formatEpgLocalTime(
    epochSeconds: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val localSeconds = epochSeconds + timeZone.getOffset(epochSeconds * 1_000L) / 1_000L
    val secondsOfDay = ((localSeconds % 86_400L) + 86_400L) % 86_400L
    val hour = (secondsOfDay / 3_600L).toInt()
    val minute = ((secondsOfDay % 3_600L) / 60L).toInt()
    return buildString(5) {
        if (hour < 10) append('0')
        append(hour)
        append(':')
        if (minute < 10) append('0')
        append(minute)
    }
}
