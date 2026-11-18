package sa.hulksa.player.data

import java.io.ByteArrayInputStream
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class EpgXmltvParserTest {
    private val now = 1_794_924_000L // 2026-11-17T14:00:00Z

    @Test
    fun mapsExactChannelIdAndKeepsSourceLanguageTitle() {
        val xml = xmltv(
            """
            <programme start="20261117150000 +0100" stop="20261117160000 +0100" channel="SkySports1.uk">
              <title lang="en">Live Match</title>
              <desc>ignored description</desc>
            </programme>
            <programme start="20261117150000 +0100" stop="20261117160000 +0100" channel="skysports1.uk">
              <title>Wrong case must not match</title>
            </programme>
            <programme start="20261117150000 +0100" stop="20261117160000 +0100" channel="Other.uk">
              <title>Other channel</title>
            </programme>
            """.trimIndent(),
        )

        val snapshot = parse(xml, setOf("SkySports1.uk"))
        val schedule = snapshot.schedule

        assertEquals(1, schedule.programmesByChannel.size)
        assertEquals("Live Match", schedule.programmesByChannel.getValue("SkySports1.uk").single().title)
        assertTrue(schedule.nowNext("SkySports1.uk", now).current != null)
        assertTrue(schedule.nowNext("skysports1.uk", now).isEmpty)
        assertTrue(schedule.nowNext(null, now).isEmpty)
    }

    @Test
    fun readsEachTimestampOffsetAndFormatsLocalTime() {
        assertEquals(now, parseXmltvTimestamp("20261117150000 +0100"))
        assertEquals(now, parseXmltvTimestamp("20261117090000 -0500"))
        assertEquals(
            "17:00",
            formatEpgLocalTime(now, TimeZone.getTimeZone("Asia/Riyadh")),
        )
        assertEquals("14:00", formatEpgLocalTime(now, TimeZone.getTimeZone("UTC")))

        val xml = xmltv(
            """
            <programme start="20261117150000 +0100" stop="20261117160000 +0100" channel="One">
              <title>Offset +0100</title>
            </programme>
            <programme start="20261117090000 -0500" stop="20261117100000 -0500" channel="Two">
              <title>Offset -0500</title>
            </programme>
            """.trimIndent(),
        )
        val schedule = parse(xml, setOf("One", "Two")).schedule
        assertEquals("Offset +0100", schedule.programmesByChannel.getValue("One").single().title)
        assertEquals("Offset -0500", schedule.programmesByChannel.getValue("Two").single().title)
        assertEquals(now, schedule.programmesByChannel.getValue("Two").single().startEpochSeconds)
    }

    @Test
    fun removesDuplicateStartWithDeterministicMoreDescriptiveTitle() {
        val xml = xmltv(
            """
            <programme start="20261117150000 +0100" stop="20261117160000 +0100" channel="Sports">
              <title>NBA</title>
            </programme>
            <programme start="20261117150000 +0100" stop="20261117160000 +0100" channel="Sports">
              <title>San Antonio Spurs - Detroit Pistons</title>
            </programme>
            <programme start="20261117150000 +0100" stop="20261117163000 +0100" channel="Sports">
              <title>Later stop wins duplicate</title>
            </programme>
            """.trimIndent(),
        )

        val programmes = parse(xml, setOf("Sports")).schedule
            .programmesByChannel.getValue("Sports")

        assertEquals(1, programmes.size)
        assertEquals("Later stop wins duplicate", programmes.single().title)
        assertEquals(now + 5_400L, programmes.single().stopEpochSeconds)
    }

    @Test
    fun expiredAndEmptyEntriesAreNeverCurrentOrNext() {
        val xml = xmltv(
            """
            <programme start="20261117130000 +0100" stop="20261117140000 +0100" channel="Expired">
              <title>Already finished</title>
            </programme>
            <programme start="20261117133000 +0100" stop="20261117140000 +0100" channel="Expired">
              <title>Also finished</title>
            </programme>
            """.trimIndent(),
        )

        val schedule = parse(xml, setOf("Expired", "Empty")).schedule
        assertEquals(EpgNowNext(), schedule.nowNext("Expired", now))
        assertTrue(schedule.programmesFor("Expired", now).isEmpty())
        assertEquals(EpgNowNext(), schedule.nowNext("Empty", now))
        assertTrue(schedule.programmesFor("Empty", now).isEmpty())

        val verifiedEmpty = LiveEpgUiState(
            verified = true,
            fetchedAtEpochSeconds = now,
            schedule = schedule,
        )
        assertTrue(verifiedEmpty.guideEnabled)
        assertTrue(verifiedEmpty.programmesFor("Empty", now).isEmpty())
        assertTrue(verifiedEmpty.nowNext("Expired", now).isEmpty)
    }

    @Test
    fun conflictingOverlapUsesLatestStartingCurrentAndNextAfterItEnds() {
        val xml = xmltv(
            """
            <programme start="20261117133000 +0100" stop="20261117160000 +0100" channel="Conflict">
              <title>Earlier long programme</title>
            </programme>
            <programme start="20261117150000 +0100" stop="20261117163000 +0100" channel="Conflict">
              <title>Latest starting programme</title>
            </programme>
            <programme start="20261117163000 +0100" stop="20261117170000 +0100" channel="Conflict">
              <title>Following programme</title>
            </programme>
            """.trimIndent(),
        )

        val nowNext = parse(xml, setOf("Conflict")).schedule.nowNext("Conflict", now)
        assertEquals("Latest starting programme", nowNext.current?.title)
        assertEquals("Following programme", nowNext.next?.title)
        assertFalse(nowNext.next?.title == "Earlier long programme")
    }

    @Test
    fun excludesProgrammesOutsideTheNext24Hours() {
        val xml = xmltv(
            """
            <programme start="20261118160000 +0100" stop="20261118170000 +0100" channel="Future">
              <title>Too far ahead</title>
            </programme>
            """.trimIndent(),
        )

        val schedule = parse(xml, setOf("Future")).schedule
        assertTrue(schedule.programmesByChannel.isEmpty())
    }

    @Test
    fun rejectsDocumentThatDoesNotDeclareTheProvenNeoProviderIdentity() {
        val mismatchedName = xmltv(
            programmeXml = "",
            generatorName = "HULK",
        )
        assertThrows(EpgException.ProviderMismatch::class.java) {
            parse(mismatchedName, setOf("Any"))
        }

        val mismatchedUrl = xmltv(
            programmeXml = "",
            generatorUrl = "hulk.example",
        )
        assertThrows(EpgException.ProviderMismatch::class.java) {
            parse(mismatchedUrl, setOf("Any"))
        }

        val generatorNameOnly = xmltv(
            programmeXml = "",
            generatorUrl = "",
        )
        assertThrows(EpgException.ProviderMismatch::class.java) {
            parse(generatorNameOnly, setOf("Any"))
        }
    }

    @Test
    fun cachedCatalogCompatibilityUsesNowPlayingOnlyAsTheExactEpgKey() {
        val item = ContentItem(
            id = 7,
            name = "Channel",
            categoryId = "1",
            type = ContentType.LIVE,
            posterUrl = null,
            rating = null,
            year = null,
            containerExtension = null,
            nowPlaying = "  SkySports1.uk  ",
        )
        assertEquals("SkySports1.uk", item.epgChannelId())
        assertNull(item.copy(nowPlaying = "   ").epgChannelId())
        assertNull(item.copy(nowPlaying = null).epgChannelId())
    }

    private fun parse(
        xml: String,
        requested: Set<String>,
    ): EpgSnapshot = EpgXmltvParser(
        nowEpochSeconds = now,
        requestedChannelIds = requested,
        providerHost = NeoEpgProviderIdentity.PROVIDER_HOST,
    ).parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    private fun xmltv(
        programmeXml: String,
        generatorName: String = NeoEpgProviderIdentity.XMLTV_GENERATOR,
        generatorUrl: String = NeoEpgProviderIdentity.PROVIDER_HOST,
    ): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        append("<!DOCTYPE tv SYSTEM \"xmltv.dtd\">\n")
        append("<tv generator-info-name=\"$generatorName\" generator-info-url=\"$generatorUrl\">\n")
        append(programmeXml)
        append("\n</tv>")
    }
}
