package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Test
import sa.hulksa.player.ui.screens.EPG_EMPTY_GUIDE_MESSAGE
import sa.hulksa.player.ui.screens.EPG_GUIDE_ACTION_LABEL
import sa.hulksa.player.ui.screens.EPG_NEXT_LABEL
import sa.hulksa.player.ui.screens.EPG_NOW_LABEL

class EpgUiCopyTest {
    @Test
    fun approvedLiveGuideCopyIsExact() {
        assertEquals("الآن", EPG_NOW_LABEL)
        assertEquals("التالي", EPG_NEXT_LABEL)
        assertEquals("دليل البرامج", EPG_GUIDE_ACTION_LABEL)
        assertEquals("لا يوجد دليل متاح لهذه القناة", EPG_EMPTY_GUIDE_MESSAGE)
    }
}
