package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveCatalogErrorCopyTest {
    @Test
    fun `validated offline state selects the truthful live offline copy`() {
        val copy = liveCatalogErrorCopy(
            offline = true,
            serverMessage = "تعذر الاتصال بالخادم",
        )

        assertEquals("لا يوجد اتصال بالانترنت", copy.title)
        assertEquals("تعذر تحديث القنوات ، تحقق من الاتصال وحاول مرة اخرى", copy.body)
    }

    @Test
    fun `online live failure keeps the real server message`() {
        val copy = liveCatalogErrorCopy(
            offline = false,
            serverMessage = "خطا من الخادم",
        )

        assertEquals("تعذر تحديث القنوات", copy.title)
        assertEquals("خطا من الخادم", copy.body)
    }

    @Test
    fun `missing server message falls back without inventing offline state`() {
        val copy = liveCatalogErrorCopy(offline = false, serverMessage = "  ")

        assertEquals("تعذر تحديث القنوات", copy.title)
        assertEquals("حاول مرة اخرى", copy.body)
    }
}
