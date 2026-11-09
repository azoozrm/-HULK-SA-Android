package sa.hulksa.player.data

import okio.Buffer
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.ContentItem

/**
 * Desktop-only comparative benchmark. Skipped unless HULK_BENCHMARK=1.
 *
 * Both paths start from the same generated JSON string and include the complete read/parse/map
 * work for that path. The streaming path writes the string into an Okio buffer to model reading a
 * response body; the legacy path feeds the already-materialized string to JSONArray. Results are
 * desktop JVM evidence, not TV qualification.
 */
class XtreamCatalogReaderBenchmarkTest {
    @Test
    fun compareLegacyAndStreamingOnGeneratedCatalog() {
        assumeTrue("Set HULK_BENCHMARK=1 to run the desktop benchmark", System.getenv("HULK_BENCHMARK") == "1")

        val small = generateCatalog(2_000, "small")
        val client = XtreamClient()
        val smallLegacy = client.parseItems(JSONArray(small), ContentType.MOVIE, PORTAL)
        val smallStreaming = client.parseCatalogItemsStreaming(Buffer().writeUtf8(small), ContentType.MOVIE, PORTAL)
        assertEquals("small-catalog exact output equivalence", smallLegacy, smallStreaming)

        val json = generateCatalog(ITEM_COUNT, "large")
        println("benchmark catalog items=$ITEM_COUNT chars=${json.length}")

        // Warm both paths before measuring.
        val warmLegacy = runLegacy(client, json)
        val warmStreaming = runStreaming(client, json)
        assertEquals(ITEM_COUNT, warmLegacy.size)
        assertEquals(ITEM_COUNT, warmStreaming.size)
        assertEquals("large-catalog size equivalence", warmLegacy.size, warmStreaming.size)
        assertEquals("large-catalog hash equivalence", warmLegacy.hashCode(), warmStreaming.hashCode())
        assertEquals("first item equivalence", warmLegacy.first(), warmStreaming.first())
        assertEquals("last item equivalence", warmLegacy.last(), warmStreaming.last())

        val reference = warmLegacy
        val legacy = mutableListOf<Measurement>()
        val streaming = mutableListOf<Measurement>()
        repeat(REPEATS) { run ->
            legacy += measure("legacy", run + 1, reference) { runLegacy(client, json) }
            streaming += measure("streaming", run + 1, reference) { runStreaming(client, json) }
        }

        printSummary("legacy", legacy)
        printSummary("streaming", streaming)
        val legacyMedian = median(legacy.map { it.elapsedMs })
        val streamingMedian = median(streaming.map { it.elapsedMs })
        val legacyAllocMedian = median(legacy.map { it.allocatedBytes.toDouble() })
        val streamingAllocMedian = median(streaming.map { it.allocatedBytes.toDouble() })
        println(
            "benchmark summary path=legacy medianMs=${"%.1f".format(legacyMedian)} " +
                "medianAllocatedBytes=${legacyAllocMedian.toLong()} " +
                "path=streaming medianMs=${"%.1f".format(streamingMedian)} " +
                "medianAllocatedBytes=${streamingAllocMedian.toLong()}",
        )
        println(
            "benchmark delta medianMs=${"%.1f".format(streamingMedian - legacyMedian)} " +
                "medianAllocatedBytes=${streamingAllocMedian.toLong() - legacyAllocMedian.toLong()}",
        )
    }

    private fun runLegacy(client: XtreamClient, json: String): List<ContentItem> {
        // Model the current path's body read/materialization before JSONArray/parseItems.
        val text = Buffer().writeUtf8(json).readUtf8()
        return client.parseItems(JSONArray(text), ContentType.MOVIE, PORTAL)
    }

    private fun runStreaming(client: XtreamClient, json: String): List<ContentItem> =
        client.parseCatalogItemsStreaming(Buffer().writeUtf8(json), ContentType.MOVIE, PORTAL)

    private fun measure(
        path: String,
        run: Int,
        reference: List<ContentItem>,
        block: () -> List<ContentItem>,
    ): Measurement {
        val allocationBefore = allocatedBytes()
        val heapBefore = usedHeapBytes()
        val startedNs = System.nanoTime()
        val result = block()
        val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000.0
        val allocationAfter = allocatedBytes()
        val heapAfter = usedHeapBytes()
        assertEquals("$path run $run size", reference.size, result.size)
        assertEquals("$path run $run hash", reference.hashCode(), result.hashCode())
        val measurement = Measurement(
            path = path,
            run = run,
            elapsedMs = elapsedMs,
            allocatedBytes = if (allocationBefore >= 0 && allocationAfter >= 0) {
                allocationAfter - allocationBefore
            } else {
                -1L
            },
            heapUsedAfterBytes = heapAfter,
        )
        println(
            "benchmark path=$path run=$run elapsedMs=${"%.1f".format(elapsedMs)} " +
                "allocatedBytes=${measurement.allocatedBytes} heapUsedAfterBytes=$heapAfter " +
                "heapBeforeBytes=$heapBefore items=${result.size}",
        )
        return measurement
    }

    private fun printSummary(path: String, values: List<Measurement>) {
        val elapsed = values.map { it.elapsedMs }
        val allocated = values.map { it.allocatedBytes }
        println(
            "benchmark path=$path runs=${values.size} " +
                "minMs=${"%.1f".format(elapsed.min())} medianMs=${"%.1f".format(median(elapsed))} " +
                "maxMs=${"%.1f".format(elapsed.max())} " +
                "minAllocatedBytes=${allocated.min()} medianAllocatedBytes=${median(allocated.map { it.toDouble() }).toLong()} " +
                "maxAllocatedBytes=${allocated.max()}",
        )
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
    }

    private fun allocatedBytes(): Long = runCatching {
        val factory = Class.forName("java.lang.management.ManagementFactory")
        val threadBean = factory.getMethod("getThreadMXBean").invoke(null)
        val threadMxBean = Class.forName("com.sun.management.ThreadMXBean")
        val method = threadMxBean.getMethod(
            "getThreadAllocatedBytes",
            Long::class.javaPrimitiveType,
        )
        method.invoke(threadBean, Thread.currentThread().id) as Long
    }.getOrDefault(-1L)

    private fun usedHeapBytes(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private fun generateCatalog(count: Int, label: String): String = buildString(count * 380) {
        append('[')
        for (index in 1..count) {
            if (index > 1) append(',')
            append("{\"num\":").append(index)
            append(",\"name\":\"عنوان ").append(label).append(' ').append(index)
            append(" - Movie Title\"")
            append(",\"stream_type\":\"movie\",\"stream_id\":").append(index)
            append(",\"stream_icon\":\"https://cdn.example/poster/").append(index).append(".jpg\"")
            append(",\"rating\":").append(6 + (index % 40) / 10.0)
            append(",\"rating_5based\":").append(3 + (index % 20) / 10.0)
            append(",\"added\":\"1700").append(100000 + (index % 100000)).append('"')
            append(",\"category_id\":").append(index % 200)
            append(",\"container_extension\":\"mp4\",\"custom_sid\":\"\",\"direct_source\":\"\"")
            append(",\"tmdb_id\":").append(index)
            append(",\"plot\":\"Plot for movie ").append(index)
            append(" with realistic provider text\",\"genre\":\"Drama\"")
            append(",\"backdrop_path\":[\"https://cdn.example/back/").append(index).append(".jpg\"]}")
        }
        append(']')
    }

    private data class Measurement(
        val path: String,
        val run: Int,
        val elapsedMs: Double,
        val allocatedBytes: Long,
        val heapUsedAfterBytes: Long,
    )

    private companion object {
        const val ITEM_COUNT = 68_478
        const val REPEATS = 3
        const val PORTAL = "https://portal.example/base"
    }
}
