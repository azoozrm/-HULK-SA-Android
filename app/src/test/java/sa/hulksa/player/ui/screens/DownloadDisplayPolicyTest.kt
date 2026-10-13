package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

class DownloadDisplayPolicyTest {
    @Test
    fun `episode identity renders the series once with independent season and episode`() {
        val identity = downloadDisplayIdentity(
            download(
                title = "روميو ويا ليت · روميو ويا ليت-S1.E2",
                seriesTitle = "روميو ويا ليت",
                season = 1,
                episodeNumber = 2,
            ),
        )

        assertEquals("روميو ويا ليت", identity.title)
        assertEquals("الموسم 1  •  الحلقة 2", identity.metadata)
        assertFalse(identity.title.contains("S1.E2"))
        assertFalse(identity.metadata.contains(identity.title))
    }

    @Test
    fun `movie identity renders its canonical title once with the film metadata`() {
        val identity = downloadDisplayIdentity(
            download(title = "The Batman", streamKind = "movie"),
        )

        assertEquals("The Batman", identity.title)
        assertEquals("فلم", identity.metadata)
        assertFalse(identity.metadata.contains(identity.title))
    }

    @Test
    fun `missing canonical fields fall back to the stored record title`() {
        val identity = downloadDisplayIdentity(
            download(
                title = "Some Episode",
                seriesTitle = null,
                season = 3,
                episodeNumber = 7,
            ),
        )

        assertEquals("Some Episode", identity.title)
        assertEquals("الموسم 3  •  الحلقة 7", identity.metadata)
    }

    @Test
    fun `blank series title falls back to the stored record title`() {
        val identity = downloadDisplayIdentity(
            download(title = "Record Title", seriesTitle = "   "),
        )

        assertEquals("Record Title", identity.title)
    }

    @Test
    fun `download notice display strips terminal decoration and normalizes alef only`() {
        assertEquals("تم حذف التنزيل", downloadNoticeDisplayText("تم حذف التنزيل."))
        assertEquals("تم ايقاف التحميل مؤقتا", downloadNoticeDisplayText("تم ايقاف التحميل مؤقتا..."))
        // Interior punctuation is content, not terminal decoration.
        assertEquals("تعذر التحميل. حاول مرة اخرى", downloadNoticeDisplayText("تعذر التحميل. حاول مرة اخرى"))
        assertEquals("تمت اضافة العمل الى المفضلة", downloadNoticeDisplayText("تمت إضافة العمل إلى المفضلة"))
        // ئ/ؤ/ء stay intact and decimals are preserved.
        assertEquals("مؤقتا 2.5 MB", downloadNoticeDisplayText("مؤقتا 2.5 MB."))
    }

    @Test
    fun `list layout uses the horizontal row only when its measured columns fit`() {
        assertTrue(downloadListUsesHorizontalRow(isTv = true, availableWidthDp = 384f, primaryActionWidthDp = 150f))
        assertFalse(downloadListUsesHorizontalRow(isTv = false, availableWidthDp = 384f, primaryActionWidthDp = 150f))
        assertFalse(downloadListUsesHorizontalRow(isTv = false, availableWidthDp = 720f, primaryActionWidthDp = 150f))
        assertTrue(downloadListUsesHorizontalRow(isTv = false, availableWidthDp = 860f, primaryActionWidthDp = 150f))
    }

    private fun download(
        title: String,
        seriesTitle: String? = null,
        season: Int? = null,
        episodeNumber: Int? = null,
        streamKind: String = "series",
    ): OfflineDownload = OfflineDownload(
        downloadId = 1L,
        historyKey = "SERIES:1",
        title = title,
        posterUrl = null,
        streamKind = streamKind,
        streamId = 1,
        extension = "mp4",
        seriesTitle = seriesTitle,
        season = season,
        episodeNumber = episodeNumber,
        status = OfflineStatus.DOWNLOADING,
    )
}
