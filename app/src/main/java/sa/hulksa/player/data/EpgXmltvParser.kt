package sa.hulksa.player.data

import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory
import kotlinx.coroutines.CancellationException
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

internal const val EPG_MAX_PROGRAMMES: Int = 80_000
internal const val EPG_MAX_PROGRAMMES_PER_CHANNEL: Int = 768
internal const val EPG_MAX_TITLE_CHARS: Int = 160
internal const val EPG_MAX_UNCOMPRESSED_BYTES: Long = 64L * 1024L * 1024L
internal const val EPG_MAX_COMPRESSED_BYTES: Long = 48L * 1024L * 1024L

internal sealed class EpgException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    data object ProviderNotVerified : EpgException("EPG provider not verified")
    data object ProviderMismatch : EpgException("EPG provider identity mismatch")
    data object InvalidResponse : EpgException("EPG response is not valid XMLTV")
    data object PayloadLimitExceeded : EpgException("EPG payload limit exceeded")
    data class Http(val statusCode: Int) : EpgException("EPG HTTP failure")
    class Network(cause: Throwable) : EpgException("EPG network failure", cause)
}

internal class EpgPayloadLimitException : IOException("EPG payload limit exceeded")

/**
 * Streaming XMLTV parser.
 *
 * Only requested catalog channel ids and programmes intersecting the next 24-hour window are
 * retained. Descriptions and unrelated metadata are discarded immediately. The provider identity
 * is validated from the XMLTV root before any programme is accepted; a mismatched document aborts
 * parsing and is never presented as NEO data.
 */
internal class EpgXmltvParser(
    private val nowEpochSeconds: Long,
    private val requestedChannelIds: Set<String>,
    private val providerHost: String = NeoEpgProviderIdentity.PROVIDER_HOST,
    private val maxProgrammes: Int = EPG_MAX_PROGRAMMES,
    private val maxProgrammesPerChannel: Int = EPG_MAX_PROGRAMMES_PER_CHANNEL,
    private val maxTitleChars: Int = EPG_MAX_TITLE_CHARS,
    private val checkCancelled: () -> Unit = {},
) {
    fun parse(input: InputStream): EpgSnapshot {
        val handler = Handler()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            runCatching {
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/validation", false)
            }
        }
        val parser = try {
            factory.newSAXParser()
        } catch (_: Exception) {
            throw EpgException.InvalidResponse
        }
        try {
            parser.parse(input, handler)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (abort: EpgParseAbort) {
            throw abort.reason
        } catch (error: EpgPayloadLimitException) {
            throw EpgException.PayloadLimitExceeded
        } catch (error: SAXException) {
            findParseAbort(error)?.let { throw it.reason }
            val cause = error.cause
            if (cause is EpgPayloadLimitException) throw EpgException.PayloadLimitExceeded
            throw EpgException.InvalidResponse
        } catch (error: IOException) {
            if (error is EpgPayloadLimitException) throw EpgException.PayloadLimitExceeded
            throw EpgException.Network(error)
        } catch (_: Exception) {
            throw EpgException.InvalidResponse
        }
        if (!handler.identityVerified) throw EpgException.ProviderMismatch
        return EpgSnapshot(
            fetchedAtEpochSeconds = nowEpochSeconds,
            schedule = EpgSchedule(handler.buildSchedule()),
        )
    }

    private inner class Handler : DefaultHandler() {
        var identityVerified: Boolean = false
            private set

        private val programmesByChannel =
            LinkedHashMap<String, LinkedHashMap<Long, EpgProgramme>>()
        private var totalProgrammes = 0
        private var inProgramme = false
        private var programmeChannel: String? = null
        private var programmeStart: Long? = null
        private var programmeStop: Long? = null
        private var programmeTitle: String? = null
        private var titleBuilder: StringBuilder? = null
        private var titleCaptured = false

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String,
            attributes: Attributes,
        ) {
            checkCancelled()
            when (qName) {
                "tv" -> {
                    identityVerified = NeoEpgProviderIdentity.isVerifiedDocument(
                        generatorName = attributes.getValue("generator-info-name"),
                        generatorUrl = attributes.getValue("generator-info-url"),
                        providerHost = providerHost,
                    )
                    if (!identityVerified) throw EpgParseAbort(EpgException.ProviderMismatch)
                }
                "programme" -> {
                    inProgramme = true
                    programmeChannel = attributes.getValue("channel")?.trim()
                    programmeStart = parseXmltvTimestamp(attributes.getValue("start"))
                    programmeStop = parseXmltvTimestamp(attributes.getValue("stop"))
                    programmeTitle = null
                    titleBuilder = null
                    titleCaptured = false
                }
                "title" -> {
                    if (inProgramme && !titleCaptured) titleBuilder = StringBuilder()
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            val builder = titleBuilder ?: return
            if (builder.length >= maxTitleChars) return
            val take = minOf(length, maxTitleChars - builder.length)
            if (take > 0) builder.append(ch, start, take)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            checkCancelled()
            when (qName) {
                "title" -> {
                    val builder = titleBuilder
                    if (inProgramme && builder != null && !titleCaptured) {
                        val captured = builder.toString().trim()
                        if (captured.isNotEmpty()) {
                            programmeTitle = captured
                            titleCaptured = true
                        }
                    }
                    titleBuilder = null
                }
                "programme" -> {
                    commitProgramme()
                    inProgramme = false
                    programmeChannel = null
                    programmeStart = null
                    programmeStop = null
                    programmeTitle = null
                    titleBuilder = null
                    titleCaptured = false
                }
            }
        }

        private fun commitProgramme() {
            if (!inProgramme) return
            if (totalProgrammes >= maxProgrammes) return
            val channel = programmeChannel ?: return
            if (channel !in requestedChannelIds) return
            val start = programmeStart ?: return
            val stop = programmeStop ?: return
            val title = programmeTitle ?: return
            if (start < 0L || stop <= start || title.isBlank()) return
            if (stop <= nowEpochSeconds) return
            if (start >= nowEpochSeconds + EPG_WINDOW_SECONDS) return
            val programme = EpgProgramme(start, stop, title)
            if (!programme.hasUsableSpan) return
            val channelProgrammes = programmesByChannel.getOrPut(channel) {
                LinkedHashMap()
            }
            val existing = channelProgrammes[start]
            if (existing == null) {
                if (channelProgrammes.size >= maxProgrammesPerChannel) return
                channelProgrammes[start] = programme
                totalProgrammes += 1
            } else if (isPreferredDuplicate(programme, existing)) {
                channelProgrammes[start] = programme
            }
        }

        fun buildSchedule(): Map<String, List<EpgProgramme>> =
            programmesByChannel.mapValues { (_, programmes) ->
                programmes.values.sortedWith(
                    compareBy<EpgProgramme> { it.startEpochSeconds }
                        .thenByDescending { it.stopEpochSeconds }
                        .thenBy { it.title },
                )
            }
    }
}

private class EpgParseAbort(val reason: EpgException) : SAXException(reason.message)

private fun findParseAbort(error: Throwable): EpgParseAbort? {
    var current: Throwable? = error
    var guard = 0
    while (current != null && guard < 8) {
        if (current is EpgParseAbort) return current
        current = current.cause
        guard += 1
    }
    return null
}

private fun isPreferredDuplicate(
    candidate: EpgProgramme,
    existing: EpgProgramme,
): Boolean {
    if (candidate.stopEpochSeconds != existing.stopEpochSeconds) {
        return candidate.stopEpochSeconds > existing.stopEpochSeconds
    }
    if (candidate.title.length != existing.title.length) {
        return candidate.title.length > existing.title.length
    }
    return candidate.title < existing.title
}

/**
 * Parses XMLTV `YYYYMMDDhhmmss +HHMM` timestamps and applies each timestamp's own offset.
 * UTC is the fallback only when the offset field is absent; a malformed or impossible value is
 * rejected rather than guessed.
 */
internal fun parseXmltvTimestamp(rawValue: String?): Long? {
    val value = rawValue?.trim().orEmpty()
    if (value.length < 14) return null
    val stamp = value.substring(0, 14)
    if (stamp.any { !it.isDigit() }) return null
    val rest = value.substring(14).trim()
    val offsetSeconds = when {
        rest.isEmpty() -> 0
        rest.length == 5 && (rest[0] == '+' || rest[0] == '-') &&
            rest[1].isDigit() && rest[2].isDigit() && rest[3].isDigit() && rest[4].isDigit() -> {
            val hours = rest.substring(1, 3).toInt()
            val minutes = rest.substring(3, 5).toInt()
            if (hours > 23 || minutes > 59) return null
            val magnitude = hours * 3_600L + minutes * 60L
            if (rest[0] == '-') -magnitude else magnitude
        }
        else -> return null
    }
    val year = stamp.substring(0, 4).toInt()
    val month = stamp.substring(4, 6).toInt()
    val day = stamp.substring(6, 8).toInt()
    val hour = stamp.substring(8, 10).toInt()
    val minute = stamp.substring(10, 12).toInt()
    val second = stamp.substring(12, 14).toInt()
    if (month !in 1..12) return null
    if (day !in 1..daysInMonth(year, month)) return null
    if (hour > 23 || minute > 59 || second > 59) return null
    return utcEpochSeconds(
        year = year,
        month = month,
        day = day,
        hour = hour,
        minute = minute,
        second = second,
    ) - offsetSeconds
}

/** Civil-date -> Unix seconds, independent of the device default time zone. */
internal fun utcEpochSeconds(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int,
): Long {
    var adjustedYear = year.toLong()
    if (month <= 2) adjustedYear -= 1L
    val era = if (adjustedYear >= 0L) {
        adjustedYear / 400L
    } else {
        (adjustedYear - 399L) / 400L
    }
    val yearOfEra = adjustedYear - era * 400L
    val monthPrime = (month + 9) % 12
    val dayOfYear = (153L * monthPrime + 2L) / 5L + day - 1L
    val dayOfEra = yearOfEra * 365L + yearOfEra / 4L - yearOfEra / 100L + dayOfYear
    val daysSinceEpoch = era * 146_097L + dayOfEra - 719_468L
    return daysSinceEpoch * 86_400L +
        hour * 3_600L +
        minute * 60L +
        second
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 0
}

private fun isLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
