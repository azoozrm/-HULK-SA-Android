package sa.hulksa.player.data

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.util.ArrayDeque
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

internal sealed interface PrefixScanResult {
    data class Found(val value: Int) : PrefixScanResult
    data object Eof : PrefixScanResult
    data object LimitReached : PrefixScanResult
}

/**
 * Reads a top-level JSON array of provider items without materializing the complete array, a
 * complete JSON object graph, or the complete response text. Catalog objects are decoded directly
 * into one small [RawCatalogItem] and mapped before the next element is read.
 *
 * Scalar coercion follows the Android/AOSP `JSONObject.optString` policy for the fields used by the
 * catalog: quoted strings stay strings, numbers use Android's literal-to-string coercion, booleans
 * become `true`/`false`, null is `"null"` for defaulted strings and null for nullable strings, and
 * missing fields take the existing defaults. Complex values are parsed with the same tokenizer
 * rules and compacted only for the individual field that needs it.
 */
internal class BoundedJsonArrayItemReader(
    source: BufferedSource,
    private val maxBytes: Long,
    declaredLength: Long = -1L,
) : Closeable {
    private val counting: CountingSource
    private val buffered: BufferedSource
    private var chars: Utf8CharReader? = null

    init {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        if (declaredLength > maxBytes) {
            throw XtreamJsonGuardException.PayloadTooLarge(maxBytes, declaredLength)
        }
        counting = CountingSource(source, maxBytes)
        buffered = counting.buffer()
    }

    val bytesRead: Long
        get() = counting.totalBytes

    /**
     * Returns the first non-whitespace byte without consuming it, or null when EOF or the bounded
     * scan limit is reached. [maxSteps] is a deterministic progress guard for finite-source tests;
     * production uses the default.
     */
    fun peekFirstNonWhitespace(
        checkCancelled: () -> Unit = {},
        maxSteps: Int = Int.MAX_VALUE,
    ): Int? = when (val result = scanPrefix(checkCancelled, maxSteps)) {
        is PrefixScanResult.Found -> result.value
        PrefixScanResult.Eof, PrefixScanResult.LimitReached -> null
    }

    fun scanPrefix(
        checkCancelled: () -> Unit = {},
        maxSteps: Int = Int.MAX_VALUE,
    ): PrefixScanResult {
        val scanLimit = minOf(maxBytes, PEEK_SCAN_BYTES)
        var scanned = 0L
        var steps = 0
        while (scanned < scanLimit) {
            if (steps++ >= maxSteps) {
                throw IllegalStateException("prefix scan made no progress")
            }
            val target = minOf(scanned + PEEK_CHUNK_BYTES, scanLimit)
            val hasTarget = buffered.request(target)
            val buffer = buffered.buffer
            while (scanned < buffer.size) {
                val value = buffer[scanned].toInt() and 0xff
                if (!isJsonWhitespace(value)) return PrefixScanResult.Found(value)
                scanned += 1L
            }
            checkCancelled()
            if (!hasTarget && scanned >= buffer.size) return PrefixScanResult.Eof
        }

        val hasMore = buffered.request(scanLimit + 1L)
        return if (hasMore || buffered.buffer.size > scanLimit) {
            PrefixScanResult.LimitReached
        } else {
            PrefixScanResult.Eof
        }
    }

    fun readAllText(checkCancelled: () -> Unit = {}): String {
        val reader = charReader()
        val text = StringBuilder()
        var count = 0
        while (true) {
            val value = reader.read()
            if (value == -1) break
            text.append(value.toChar())
            count += 1
            if (count % CANCELLATION_CHECK_INTERVAL == 0) checkCancelled()
        }
        return text.toString()
    }

    /**
     * Reads the bounded body as text for challenge classification. Leading whitespace is skipped,
     * only the first 512 significant characters are retained, and the rest is drained through the
     * byte-counting source.
     */
    fun readChallengePrefix(checkCancelled: () -> Unit = {}): String {
        val reader = charReader()
        val prefix = StringBuilder()
        var started = false
        var count = 0
        while (true) {
            val value = reader.read()
            if (value == -1) break
            count += 1
            if (count % CANCELLATION_CHECK_INTERVAL == 0) checkCancelled()
            if (!started) {
                if (isJsonWhitespace(value)) continue
                started = true
            }
            if (prefix.length < CHALLENGE_PREFIX_CHARS) prefix.append(value.toChar())
        }
        return prefix.toString()
    }

    fun drain(checkCancelled: () -> Unit = {}) {
        val reader = charReader()
        var count = 0
        while (reader.read() != -1) {
            count += 1
            if (count % CANCELLATION_CHECK_INTERVAL == 0) checkCancelled()
        }
    }

    fun readCatalogItems(
        type: ContentType,
        portalBaseUrl: String,
        maxUniqueItems: Int,
        scope: String,
        checkCancelled: () -> Unit = {},
    ): List<ContentItem> {
        require(maxUniqueItems > 0) { "maxUniqueItems must be positive" }
        val idKey = if (type == ContentType.SERIES) "series_id" else "stream_id"
        val artworkBase = (portalBaseUrl.trimEnd('/') + "/").toHttpUrlOrNull()
        val reader = charReader()
        try {
            var value = reader.read()
            if (value == BOM_CODE) value = reader.read()
            value = reader.readNonWhitespace(value)
            if (value == -1) throw XtreamJsonGuardException.EmptyBody
            if (value != '['.code) {
                drainReader(reader, checkCancelled)
                throw XtreamJsonGuardException.InvalidJson()
            }

            val result = ArrayList<ContentItem>()
            val seen = HashSet<String>()
            var elementCount = 0

            value = reader.readNonWhitespace()
            if (value == ']'.code) {
                drainReader(reader, checkCancelled)
                checkCancelled()
                return result
            }

            while (true) {
                if (value == -1) throw XtreamJsonGuardException.InvalidJson()
                if (value == ']'.code) {
                    drainReader(reader, checkCancelled)
                    checkCancelled()
                    return result
                }
                if (value == '}'.code) throw XtreamJsonGuardException.InvalidJson()
                if (value == ','.code || value == ';'.code) {
                    // Legacy JSONTokener treats omitted array elements as null; catalog items are
                    // objects, so skip the omitted value and continue.
                    value = reader.readNonWhitespace()
                    continue
                }

                val item = if (value == '{'.code) {
                    readObject(reader)
                } else {
                    readValue(reader, value, allowNonFinite = true)
                    null
                }
                if (item != null) {
                    val idText = (if (idKey == "series_id") item.seriesId else item.streamId)
                        ?.optString()
                        ?.toIntOrNull()
                        ?.toString()
                    if (idText != null && seen.add(idText)) {
                        if (seen.size > maxUniqueItems) {
                            throw XtreamJsonGuardException.TooManyItems(scope, maxUniqueItems)
                        }
                        result += item.toContentItem(idText.toInt(), type, artworkBase)
                    }
                }

                elementCount += 1
                if (elementCount % CANCELLATION_CHECK_INTERVAL == 0) checkCancelled()

                value = reader.readNonWhitespace()
                if (value == ','.code || value == ';'.code) {
                    value = reader.readNonWhitespace()
                    if (value == ']'.code) {
                        drainReader(reader, checkCancelled)
                        checkCancelled()
                        return result
                    }
                    continue
                }
                if (value == ']'.code) {
                    drainReader(reader, checkCancelled)
                    checkCancelled()
                    return result
                }
                throw XtreamJsonGuardException.InvalidJson()
            }
        } finally {
            reader.close()
        }
    }

    override fun close() {
        chars?.close()
        buffered.close()
    }

    private fun charReader(): Utf8CharReader = chars ?: Utf8CharReader(buffered).also { chars = it }

    private fun readObject(reader: Utf8CharReader): RawCatalogItem {
        val item = RawCatalogItem()
        var value = reader.readNonWhitespace()
        if (value == '}'.code) return item
        while (true) {
            val key = readKey(reader, value)
            value = reader.readNonWhitespace()
            when (value) {
                ':'.code, '='.code -> value = readAfterSeparator(reader)
                else -> throw XtreamJsonGuardException.InvalidJson()
            }
            if (value == -1) throw XtreamJsonGuardException.InvalidJson()

            when (key) {
                "stream_id" -> item.streamId = readOptStringValue(reader, value, allowNonFinite = false)
                "series_id" -> item.seriesId = readOptStringValue(reader, value, allowNonFinite = false)
                "name" -> item.name = readOptStringValue(reader, value, allowNonFinite = false)
                "category_id" -> item.categoryId = readOptStringValue(reader, value, allowNonFinite = false)
                "stream_icon" -> item.streamIcon = readOptStringValue(reader, value, allowNonFinite = false)
                "cover" -> item.cover = readOptStringValue(reader, value, allowNonFinite = false)
                "rating" -> item.rating = readOptStringValue(reader, value, allowNonFinite = false)
                "rating_5based" -> item.rating5Based = readOptStringValue(reader, value, allowNonFinite = false)
                "year" -> item.year = readOptStringValue(reader, value, allowNonFinite = false)
                "container_extension" -> item.containerExtension = readOptStringValue(reader, value, allowNonFinite = false)
                "epg_channel_id" -> item.epgChannelId = readOptStringValue(reader, value, allowNonFinite = false)
                "added" -> item.added = readOptStringValue(reader, value, allowNonFinite = false)
                "last_modified" -> item.lastModified = readOptStringValue(reader, value, allowNonFinite = false)
                "plot" -> item.plot = readOptStringValue(reader, value, allowNonFinite = false)
                "genre" -> item.genre = readOptStringValue(reader, value, allowNonFinite = false)
                "backdrop_path" -> item.backdrop = readBackdrop(reader, value)
                else -> readValue(reader, value, allowNonFinite = false)
            }

            value = reader.readNonWhitespace()
            if (value == '}'.code) return item
            if (value == ','.code || value == ';'.code) {
                value = reader.readNonWhitespace()
                continue
            }
            throw XtreamJsonGuardException.InvalidJson()
        }
    }

    private fun readKey(reader: Utf8CharReader, first: Int): String {
        if (
            isStructuralDelimiter(first) || first == '{'.code || first == '['.code ||
            first == '/'.code || first == '\\'.code || first == '\u000C'.code
        ) {
            throw XtreamJsonGuardException.InvalidJson()
        }
        return when (first) {
            '"'.code, '\''.code -> reader.readStringBody(first.toChar())
            else -> reader.readBareKey(first)
        }
    }

    private fun readOptStringValue(
        reader: Utf8CharReader,
        first: Int,
        allowNonFinite: Boolean,
    ): RawValue? = readValue(reader, first, allowNonFinite).toRawValue()

    private fun readValue(reader: Utf8CharReader, first: Int, allowNonFinite: Boolean): JsonValue {
        if (
            isStructuralDelimiter(first) || first == '/'.code || first == '\\'.code ||
            first == '\u000C'.code
        ) {
            throw XtreamJsonGuardException.InvalidJson()
        }
        return when (first) {
            '{'.code -> readObjectValue(reader)
            '['.code -> readArrayValue(reader)
            '"'.code, '\''.code -> JsonValue.Str(reader.readStringBody(first.toChar()))
            else -> {
                val raw = reader.readBareValue(first)
                when {
                    raw.equals("null", ignoreCase = true) -> JsonValue.Null
                    raw.equals("true", ignoreCase = true) -> JsonValue.Bool(true)
                    raw.equals("false", ignoreCase = true) -> JsonValue.Bool(false)
                    else -> {
                        val number = androidNumberOrNull(raw)
                        if (number is Double && !number.isFinite() && !allowNonFinite) {
                            throw XtreamJsonGuardException.InvalidJson()
                        }
                        if (number != null) JsonValue.Num(number) else JsonValue.Str(raw)
                    }
                }
            }
        }
    }

    private fun readObjectValue(reader: Utf8CharReader): JsonValue.Obj {
        val entries = linkedMapOf<String, JsonValue>()
        var value = reader.readNonWhitespace()
        if (value == '}'.code) return JsonValue.Obj(entries)
        while (true) {
            val key = readKey(reader, value)
            value = reader.readNonWhitespace()
            when (value) {
                ':'.code, '='.code -> value = readAfterSeparator(reader)
                else -> throw XtreamJsonGuardException.InvalidJson()
            }
            if (value == -1) throw XtreamJsonGuardException.InvalidJson()
            entries[key] = readValue(reader, value, allowNonFinite = false)
            value = reader.readNonWhitespace()
            if (value == '}'.code) return JsonValue.Obj(entries)
            if (value == ','.code || value == ';'.code) {
                value = reader.readNonWhitespace()
                if (value == '}'.code) throw XtreamJsonGuardException.InvalidJson()
                continue
            }
            throw XtreamJsonGuardException.InvalidJson()
        }
    }

    private fun readArrayValue(reader: Utf8CharReader): JsonValue.Arr {
        val values = ArrayList<JsonValue>()
        var hasTrailingSeparator = false
        while (true) {
            val value = reader.readNonWhitespace()
            when (value) {
                -1 -> throw XtreamJsonGuardException.InvalidJson()
                ']'.code -> {
                    if (hasTrailingSeparator) values += JsonValue.Omitted
                    return JsonValue.Arr(values)
                }
                ','.code, ';'.code -> {
                    // AOSP JSONTokener stores an omitted slot as Java null, not JSONObject.NULL.
                    // The two must stay distinct for first-image selection.
                    values += JsonValue.Omitted
                    hasTrailingSeparator = true
                }
                '}'.code -> throw XtreamJsonGuardException.InvalidJson()
                else -> {
                    values += readValue(reader, value, allowNonFinite = true)
                    hasTrailingSeparator = false
                    when (val next = reader.readNonWhitespace()) {
                        ']'.code -> return JsonValue.Arr(values)
                        ','.code, ';'.code -> hasTrailingSeparator = true
                        else -> throw XtreamJsonGuardException.InvalidJson()
                    }
                }
            }
        }
    }

    private fun readAfterSeparator(reader: Utf8CharReader): Int {
        var next = reader.read()
        if (next == '>'.code) next = reader.read()
        return reader.readNonWhitespace(next)
    }

    private fun readBackdrop(reader: Utf8CharReader, first: Int): String? {
        if (first == '"'.code || first == '\''.code) {
            val text = reader.readStringBody(first.toChar())
            val clean = text.trim()
            if (clean.startsWith("[")) {
                // Legacy stringified-array path inspects element zero only. An empty/omitted first
                // slot returns no image even when a later slot contains a valid URL.
                val parsed = runCatching { parseJsonText(clean, allowNonFinite = true) }.getOrNull()
                val first = (parsed as? JsonValue.Arr)?.values?.firstOrNull() ?: return null
                return first.optString()?.trim()?.takeUnless(String::isBlank)
            }
            return clean.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        }
        val value = readValue(reader, first, allowNonFinite = false)
        return (value as? JsonValue.Arr)?.firstImage()
    }

    private fun parseJsonText(text: String, allowNonFinite: Boolean): JsonValue {
        val source = Buffer().writeUtf8(text)
        val reader = Utf8CharReader(source)
        try {
            val first = reader.readNonWhitespace()
            if (first == -1) throw XtreamJsonGuardException.InvalidJson()
            return readValue(reader, first, allowNonFinite)
        } finally {
            reader.close()
        }
    }

    private fun drainReader(reader: Utf8CharReader, checkCancelled: () -> Unit) {
        var count = 0
        while (reader.read() != -1) {
            count += 1
            if (count % CANCELLATION_CHECK_INTERVAL == 0) checkCancelled()
        }
    }

    private sealed interface JsonValue {
        data class Obj(val entries: Map<String, JsonValue>) : JsonValue
        data class Arr(val values: List<JsonValue>) : JsonValue
        data class Str(val value: String) : JsonValue
        data class Num(val number: Number) : JsonValue
        data class Bool(val value: Boolean) : JsonValue
        data object Null : JsonValue

        /**
         * An array slot omitted by a separator (`[,"x"]`, `[1,]`). AOSP stores this as Java null,
         * while an explicit JSON `null` is [Null]; `JSONArray.optString` returns "" for the former
         * and "null" for the latter.
         */
        data object Omitted : JsonValue

        fun optString(): String? = when (this) {
            is Str -> value
            is Num -> number.toString()
            is Bool -> if (value) "true" else "false"
            Null -> "null"
            Omitted -> ""
            is Arr, is Obj -> serialize()
        }

        fun serialize(): String? {
            return when (this) {
            is Str -> quote(value)
            is Num -> androidNumberToString(number)
            is Bool -> if (value) "true" else "false"
            Null -> "null"
            Omitted -> "null"
            is Arr -> {
                val parts = ArrayList<String>(values.size)
                for (element in values) {
                    parts += element.serialize() ?: return null
                }
                parts.joinToString(prefix = "[", postfix = "]", separator = ",")
            }
            is Obj -> {
                val parts = ArrayList<String>(entries.size)
                for ((key, value) in entries) {
                    val serialized = value.serialize() ?: return null
                    parts += "${quote(key)}:$serialized"
                }
                parts.joinToString(prefix = "{", postfix = "}", separator = ",")
            }
        }
        }

        /**
         * AOSP `xtreamFirstImageUrl` array path: omitted slots are Java null and `optString`
         * returns "", so they are skipped. An explicit JSON null returns "null" (not blank), which
         * becomes the selected value and normalizes to no image; later elements are not consulted.
         */
        fun firstImage(): String? {
            val array = this as? Arr ?: return null
            for (element in array.values) {
                if (element is Omitted) continue
                val text = element.optString()?.trim() ?: continue
                if (text.isNotEmpty()) return text
            }
            return null
        }
    }

    private fun JsonValue.toRawValue(): RawValue? = when (this) {
        JsonValue.Null, JsonValue.Omitted -> RawValue.Null
        is JsonValue.Str -> RawValue.Text(value)
        is JsonValue.Num -> RawValue.Text(number.toString())
        is JsonValue.Bool -> RawValue.Text(if (value) "true" else "false")
        is JsonValue.Arr, is JsonValue.Obj -> serialize()?.let { RawValue.Text(it) }
    }

    private sealed interface RawValue {
        data class Text(val value: String) : RawValue
        data object Null : RawValue

        fun optString(): String = when (this) {
            is Text -> value
            Null -> "null"
        }

        fun nullable(): String? = when (this) {
            is Text -> value.trim().takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
            Null -> null
        }
    }

    private class RawCatalogItem {
        var streamId: RawValue? = null
        var seriesId: RawValue? = null
        var name: RawValue? = null
        var categoryId: RawValue? = null
        var streamIcon: RawValue? = null
        var cover: RawValue? = null
        var rating: RawValue? = null
        var rating5Based: RawValue? = null
        var year: RawValue? = null
        var containerExtension: RawValue? = null
        var epgChannelId: RawValue? = null
        var added: RawValue? = null
        var lastModified: RawValue? = null
        var plot: RawValue? = null
        var genre: RawValue? = null
        var backdrop: String? = null

        fun toContentItem(id: Int, type: ContentType, artworkBase: HttpUrl?): ContentItem = ContentItem(
            id = id,
            name = name?.optString() ?: "بدون اسم",
            categoryId = categoryId?.optString() ?: "0",
            type = type,
            posterUrl = normalizeArtworkUrl(
                (if (type == ContentType.SERIES) cover else streamIcon)?.nullable(),
                artworkBase,
            ),
            rating = rating?.nullable() ?: rating5Based?.nullable(),
            year = year?.nullable(),
            containerExtension = containerExtension?.nullable(),
            nowPlaying = epgChannelId?.nullable(),
            addedAtEpochSeconds = when (type) {
                ContentType.SERIES -> lastModified?.nullable() ?: added?.nullable()
                else -> added?.nullable()
            }?.toLongOrNull(),
            plot = plot?.nullable(),
            genre = genre?.nullable(),
            backdropUrl = normalizeArtworkUrl(backdrop, artworkBase),
        )
    }

    private class Utf8CharReader(source: BufferedSource) : Closeable {
        private val reader = BufferedReader(InputStreamReader(source.inputStream(), Charsets.UTF_8), 8 * 1024)
        private val buffer = CharArray(8 * 1024)
        private var position = 0
        private var count = 0
        private var pushback = -1

        fun read(): Int {
            if (pushback != -1) {
                val value = pushback
                pushback = -1
                return value
            }
            if (position >= count) {
                count = reader.read(buffer)
                position = 0
                if (count <= 0) return -1
            }
            return buffer[position++].code
        }

        fun readNonWhitespace(): Int = readNonWhitespace(read())

        fun readNonWhitespace(first: Int): Int {
            var value = first
            while (true) {
                if (value == -1) return -1
                if (isJsonWhitespace(value)) {
                    value = read()
                    continue
                }
                when (value) {
                    '/'.code -> {
                        val next = read()
                        when (next) {
                            '/'.code -> {
                                skipToEndOfLine()
                                value = read()
                            }
                            '*'.code -> {
                                skipBlockComment()
                                value = read()
                            }
                            else -> {
                                pushback(next)
                                return value
                            }
                        }
                    }
                    '#'.code -> {
                        skipToEndOfLine()
                        value = read()
                    }
                    else -> return value
                }
            }
        }

        fun readStringBody(quote: Char): String {
            val result = StringBuilder()
            while (true) {
                val char = read()
                if (char == -1) throw XtreamJsonGuardException.InvalidJson()
                when (char) {
                    quote.code -> return result.toString()
                    '\\'.code -> {
                        val escaped = read()
                        if (escaped == -1) throw XtreamJsonGuardException.InvalidJson()
                        when (escaped.toChar()) {
                            '"', '\'', '\\', '/' -> result.append(escaped.toChar())
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> result.append(readUnicodeEscape())
                            else -> result.append(escaped.toChar())
                        }
                    }
                    else -> result.append(char.toChar())
                }
            }
        }

        fun readBareValue(first: Int): String {
            val raw = StringBuilder()
            raw.append(first.toChar())
            while (true) {
                val value = read()
                if (value == -1) throw XtreamJsonGuardException.InvalidJson()
                if (isBareDelimiter(value)) {
                    pushback(value)
                    break
                }
                raw.append(value.toChar())
            }
            return raw.toString()
        }

        fun readBareKey(first: Int): String {
            val raw = StringBuilder()
            raw.append(first.toChar())
            while (true) {
                val value = read()
                if (value == -1) throw XtreamJsonGuardException.InvalidJson()
                if (value == ':'.code || value == '='.code || isBareDelimiter(value)) {
                    pushback(value)
                    break
                }
                raw.append(value.toChar())
            }
            val key = raw.toString()
            if (key.isEmpty() || isNonStringLiteral(key)) {
                throw XtreamJsonGuardException.InvalidJson()
            }
            return key
        }

        override fun close() {
            reader.close()
        }

        private fun pushback(value: Int) {
            check(pushback == -1) { "Only one character of pushback is supported" }
            pushback = value
        }

        private fun skipToEndOfLine() {
            while (true) {
                val value = read()
                if (value == -1 || value == '\n'.code || value == '\r'.code) return
            }
        }

        private fun skipBlockComment() {
            var previous = -1
            while (true) {
                val value = read()
                if (value == -1) throw XtreamJsonGuardException.InvalidJson()
                if (previous == '*'.code && value == '/'.code) return
                previous = value
            }
        }

        private fun readUnicodeEscape(): Char {
            var code = 0
            repeat(4) {
                val value = read()
                if (value == -1) throw XtreamJsonGuardException.InvalidJson()
                val digit = when (value.toChar()) {
                    in '0'..'9' -> value.toChar() - '0'
                    in 'a'..'f' -> value.toChar() - 'a' + 10
                    in 'A'..'F' -> value.toChar() - 'A' + 10
                    else -> throw XtreamJsonGuardException.InvalidJson()
                }
                code = code * 16 + digit
            }
            return code.toChar()
        }
    }

    private class CountingSource(
        private val upstream: Source,
        private val maxBytes: Long,
    ) : Source {
        var totalBytes = 0L
            private set

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (totalBytes > maxBytes) {
                throw XtreamJsonGuardException.PayloadTooLarge(maxBytes, totalBytes)
            }
            val remaining = maxBytes - totalBytes
            val limit = if (remaining >= byteCount) byteCount else remaining + 1L
            val read = upstream.read(sink, limit)
            if (read > 0L) {
                totalBytes += read
                if (totalBytes > maxBytes) {
                    throw XtreamJsonGuardException.PayloadTooLarge(maxBytes, totalBytes)
                }
            }
            return read
        }

        override fun timeout(): Timeout = upstream.timeout()

        override fun close() = upstream.close()
    }

    private companion object {
        const val PEEK_CHUNK_BYTES = 4L * 1024L
        const val PEEK_SCAN_BYTES = 64L * 1024L
        const val CHALLENGE_PREFIX_CHARS = 512
        const val CANCELLATION_CHECK_INTERVAL = 256
        const val BOM_CODE = 0xFEFF

        fun quote(value: String): String {
            val out = StringBuilder(value.length + 2)
            out.append('"')
            for (char in value) {
                when (char) {
                    '"', '\\', '/' -> out.append('\\').append(char)
                    '\t' -> out.append("\\t")
                    '\b' -> out.append("\\b")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\u000C' -> out.append("\\f")
                    else -> {
                        if (char.code <= 0x1F) {
                            out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            out.append(char)
                        }
                    }
                }
            }
            out.append('"')
            return out.toString()
        }

        fun androidNumberToString(number: Number): String? {
            if (number is Double) {
                if (!number.isFinite()) return null
                if (number == 0.0 && 1.0 / number == Double.NEGATIVE_INFINITY) return "-0"
            }
            val doubleValue = number.toDouble()
            val longValue = number.toLong()
            return if (doubleValue == longValue.toDouble()) {
                longValue.toString()
            } else {
                number.toString()
            }
        }

        fun isJsonWhitespace(value: Int): Boolean =
            value == ' '.code || value == '\t'.code || value == '\n'.code || value == '\r'.code

        fun isBareDelimiter(value: Int): Boolean =
            isJsonWhitespace(value) || value == ','.code || value == ']'.code ||
                value == '}'.code || value == '{'.code || value == '['.code ||
                value == ':'.code || value == '='.code || value == ';'.code ||
                value == '#'.code || value == '/'.code || value == '\\'.code ||
                // AOSP readLiteral terminates unquoted literals at form feed. It is not skipped
                // as whitespace, so the following object/array separator check rejects it.
                value == '\u000C'.code

        fun androidLiteralToString(raw: String): String {
            val number = androidNumberOrNull(raw)
            if (number is Double && !number.isFinite()) {
                throw XtreamJsonGuardException.InvalidJson()
            }
            return number?.toString() ?: raw
        }

        fun androidNumberOrNull(raw: String): Number? {
            if (raw.isEmpty()) return null
            if (!raw.contains('.')) {
                val (number, base) = when {
                    raw.startsWith("0x", ignoreCase = true) && raw.length > 2 -> raw.substring(2) to 16
                    raw.startsWith("0") && raw.length > 1 -> raw.substring(1) to 8
                    else -> raw to 10
                }
                runCatching { java.lang.Long.parseLong(number, base) }.getOrNull()?.let { value ->
                    return if (value >= Int.MIN_VALUE.toLong() && value <= Int.MAX_VALUE.toLong()) {
                        value.toInt()
                    } else {
                        value
                    }
                }
            }
            return raw.toDoubleOrNull()
        }

        fun isStructuralDelimiter(value: Int): Boolean =
            value == '}'.code || value == ']'.code || value == ','.code ||
                value == ';'.code || value == ':'.code || value == '='.code

        fun isNonStringLiteral(raw: String): Boolean {
            if (raw.equals("null", ignoreCase = true)) return true
            if (raw.equals("true", ignoreCase = true)) return true
            if (raw.equals("false", ignoreCase = true)) return true
            return androidNumberOrNull(raw) != null
        }
    }
}
